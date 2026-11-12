package dc.dccosmetics.manager;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.ActiveCosmetic;
import dc.dccosmetics.model.CosmeticTemplate;
import dc.dccosmetics.model.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

public class ProfileManager {
    private final Map<UUID, PlayerProfile> profiles = new HashMap<>();
    private final File playersFolder;

    public ProfileManager() {
        File dataDir = new File(DCCosmetics.getInstance().getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        File newFolder = new File(dataDir, "players");
        if (!newFolder.exists()) newFolder.mkdirs();

        // One-time cleanup & consolidation of old root players/ folder
        File oldFolder = new File(DCCosmetics.getInstance().getDataFolder(), "players");
        if (oldFolder.exists() && oldFolder.isDirectory()) {
            File[] oldFiles = oldFolder.listFiles();
            if (oldFiles != null) {
                for (File f : oldFiles) {
                    File target = new File(newFolder, f.getName());
                    if (!target.exists()) {
                        f.renameTo(target);
                    } else {
                        f.delete();
                    }
                }
            }
            oldFolder.delete();
        }
        playersFolder = newFolder;
    }

    public PlayerProfile getProfile(Player player) {
        return profiles.get(player.getUniqueId());
    }

    public PlayerProfile getOfflineProfile(OfflinePlayer target) {
        // If they are online, return the live cached profile
        if (target.isOnline() && profiles.containsKey(target.getUniqueId())) {
            return profiles.get(target.getUniqueId());
        }
        
        // If offline, create a temporary profile just to read their data for the GUI
        PlayerProfile profile = new PlayerProfile(target);
        File file = new File(playersFolder, target.getUniqueId() + ".yml");
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            org.bukkit.configuration.ConfigurationSection equipSec = config.getConfigurationSection("equipment");
            if (equipSec != null) {
                for (String key : equipSec.getKeys(false)) {
                    ItemStack item = equipSec.getItemStack(key);
                    if (item != null) profile.setEquipment(key, item);
                }
            }
        }
        return profile;
    }

    public void loadProfile(Player player) {
        PlayerProfile profile = new PlayerProfile(player);
        profiles.put(player.getUniqueId(), profile);
        
        // Load equipment from file
        File file = new File(playersFolder, player.getUniqueId() + ".yml");
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            org.bukkit.configuration.ConfigurationSection equipSec = config.getConfigurationSection("equipment");
            if (equipSec != null) {
                for (String key : equipSec.getKeys(false)) {
                    ItemStack item = equipSec.getItemStack(key);
                    if (item != null) profile.setEquipment(key, item);
                }
            }
        }
        // Synchronize Keystone tier from equipped item and database (after async load completes)
        var teleManager = DCCosmetics.getInstance().getTeleportationManager();
        if (teleManager != null) {
            teleManager.getPersistence().loadAsync(player.getUniqueId(), keystoneProfile -> {
                ItemStack keystoneItem = profile.getEquipment("keystone");
                if (keystoneItem != null && keystoneItem.getType() != org.bukkit.Material.AIR) {
                    var converted = dc.dcessentials.modules.teleportation.LegacyKeystoneConverter.convert(keystoneItem);
                    if (converted.isPresent()) {
                        keystoneItem = converted.get();
                        profile.setEquipment("keystone", keystoneItem);
                        saveProfile(player);
                    }
                    var detectedTier = dc.dcessentials.modules.teleportation.KeystoneDetector.tierOf(keystoneItem);
                    if (keystoneProfile.getTier() != detectedTier) {
                        keystoneProfile.setTier(detectedTier);
                        teleManager.getPersistence().saveNow(keystoneProfile);
                    }
                } else if (keystoneProfile.getTier() != dc.dcessentials.modules.teleportation.KeystoneTier.NONE) {
                    keystoneProfile.setTier(dc.dcessentials.modules.teleportation.KeystoneTier.NONE);
                    teleManager.getPersistence().saveNow(keystoneProfile);
                }
            });
        }

        var petMgr = DCCosmetics.getInstance().getPetManager();
        if (petMgr != null) {
            petMgr.loadPlayer(player);
        }

        var rngMgr = DCCosmetics.getInstance().getRngManager();
        if (rngMgr != null) {
            DCCosmetics.getInstance().getDatabaseManager().loadRngPlayer(player.getUniqueId(), rngMgr);
        }

        var pityMgr = DCCosmetics.getInstance().getPityManager();
        if (pityMgr != null) {
            DCCosmetics.getInstance().getDatabaseManager().loadPityPlayer(player.getUniqueId(), pityMgr);
        }

        DCCosmetics.getInstance().getLogger().info("[DEBUG] Loaded fresh session profile for " + player.getName());

        // CRASH LOOP PROTECTION: Wait 40 ticks (2 seconds) before spawning holograms
        Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
            if (player.isOnline()) {
                String[] slots = {"head", "chest", "waist", "boots"};
                for (String s : slots) {
                    if (profile.getEquippedCosmetic(s) != null) {
                        equipCosmetic(player, s, profile.getEquippedCosmetic(s), profile.getEquippedColor(s));
                    }
                }
            }
        }, 40L);
    }

    public void saveProfile(Player player) {
        PlayerProfile profile = profiles.get(player.getUniqueId());
        if (profile == null) return;
        File file = new File(playersFolder, player.getUniqueId() + ".yml");
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        config.set("equipment", null);
        for (Map.Entry<String, ItemStack> entry : profile.getAllEquipment().entrySet()) {
            if (entry.getValue() != null && entry.getValue().getType() != org.bukkit.Material.AIR) {
                config.set("equipment." + entry.getKey(), entry.getValue());
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            DCCosmetics.getInstance().getLogger().severe("Failed to save profile for " + player.getName() + ": " + e.getMessage());
        }
    }

    public void unloadProfile(Player player) {
        PlayerProfile profile = profiles.get(player.getUniqueId());
        if (profile != null) {
            String[] slots = {"head", "chest", "waist", "boots"};
            for (String s : slots) {
                // 1. Despawn all holograms safely
                if (profile.getActiveCosmetic(s) != null) profile.getActiveCosmetic(s).despawn();
            }

            // 2. Save equipment to file
            saveProfile(player);

            profiles.remove(player.getUniqueId());
            DCCosmetics.getInstance().getLogger().info("[DEBUG] Unloaded and saved profile for " + player.getName());
        }
    }

    public void refreshAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerProfile profile = getProfile(player);
            if (profile == null) continue;
            String[] slots = {"head", "chest", "waist", "boots"};
            for (String s : slots) {
                String equipped = profile.getEquippedCosmetic(s);
                if (equipped != null) {
                    equipCosmetic(player, s, equipped, profile.getEquippedColor(s));
                }
            }
        }
    }

    public void unequipCosmetic(Player player, String slot) {
        PlayerProfile profile = getProfile(player);
        if (profile == null) return;

        ActiveCosmetic old = profile.getActiveCosmetic(slot);
        if (old != null) {
            old.despawn();
            profile.removeActiveCosmetic(slot);
        }
        profile.removeEquipped(slot);
    }

    public void startEquipmentScanner() {
        Bukkit.getScheduler().runTaskTimer(DCCosmetics.getInstance(), () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerProfile profile = getProfile(p);
                if (profile != null) {
                    checkSlot(p, profile, "sword", p.getInventory().getItemInMainHand());
                    checkSlot(p, profile, "head", p.getInventory().getHelmet());
                    checkSlot(p, profile, "chest", p.getInventory().getChestplate());
                    checkSlot(p, profile, "waist", p.getInventory().getLeggings());
                    checkSlot(p, profile, "boots", p.getInventory().getBoots());
                }
            }
        }, 5L, 5L); // Scan every quarter of a second
    }

    private void checkSlot(Player p, PlayerProfile profile, String slot, ItemStack item) {
        // Auto-update the display names if config changed!
        DCCosmetics.getInstance().updateCosmeticItem(item);

        String targetId = profile.getEquippedCosmetic(slot); // GUI Override Admin checks!
        String targetColor = profile.getEquippedColor(slot);

        // If the admin didn't force a GUI cosmetic, check the physical item!
        if (targetId == null && item != null && item.hasItemMeta()) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            NamespacedKey idKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_id");
            NamespacedKey colorKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_color");
            if (pdc.has(idKey, PersistentDataType.STRING)) {
                targetId = pdc.get(idKey, PersistentDataType.STRING);
                targetColor = pdc.has(colorKey, PersistentDataType.STRING) ? pdc.get(colorKey, PersistentDataType.STRING) : "#FFFFFF"; // Safely catch missing colors!
            }
        }

        // Enforce Slot Rules: If you hold Boots in your hand, do NOT render them as a Hand item!
        if (targetId != null) {
            CosmeticTemplate t = DCCosmetics.getInstance().getTemplateRegistry().getTemplate(targetId);
            if (t == null || !t.getEquipmentSlot().equalsIgnoreCase(slot)) {
                targetId = null; 
            }
        }

        ActiveCosmetic active = profile.getActiveCosmetic(slot);
        String currentId = active != null ? active.getTemplate().getId() : null;
        String currentColor = active != null ? active.getColorHex() : null;

        // If target changed, swap cosmetics instantly
        if (targetId != null && (!targetId.equals(currentId) || !java.util.Objects.equals(targetColor, currentColor))) {
            if (active != null) active.despawn();
            CosmeticTemplate temp = DCCosmetics.getInstance().getTemplateRegistry().getTemplate(targetId);
            if (temp != null) {
                ActiveCosmetic newActive = new ActiveCosmetic(p, temp, targetColor != null ? targetColor : "#FFFFFF");
                newActive.spawn();
                profile.setActiveCosmetic(slot, newActive);
                // We explicitly DO NOT call profile.setEquipped() here. That is strictly for Admin GUI overrides!
            }
        } else if (targetId == null && active != null) {
            active.despawn();
            profile.removeActiveCosmetic(slot);
        }
    }

    public void equipCosmetic(Player player, String slot, String cosmeticId, String hexColor) {
        PlayerProfile profile = getProfile(player);
        if (profile == null) return;

        // Despawn old one if it exists
        ActiveCosmetic old = profile.getActiveCosmetic(slot);
        if (old != null) {
            old.despawn();
            profile.removeActiveCosmetic(slot);
        }

        CosmeticTemplate template = DCCosmetics.getInstance().getTemplateRegistry().getTemplate(cosmeticId);
        if (template == null) return;

        // Spawn the new one and save it!
        ActiveCosmetic active = new ActiveCosmetic(player, template, hexColor);
        active.spawn();

        profile.setActiveCosmetic(slot, active);
        profile.setEquipped(slot, cosmeticId, hexColor);
    }
}