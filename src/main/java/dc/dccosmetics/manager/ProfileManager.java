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
        playersFolder = new File(DCCosmetics.getInstance().getDataFolder(), "players");
        if (!playersFolder.exists()) playersFolder.mkdirs();
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
        return profile;
    }

    public void loadProfile(Player player) {
        PlayerProfile profile = new PlayerProfile(player);
        profiles.put(player.getUniqueId(), profile);
        
        // NO MORE DISK LOADING! 
        // The dynamic scanner will seamlessly pick up the items in their inventory instantly!
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

    public void unloadProfile(Player player) {
        PlayerProfile profile = profiles.get(player.getUniqueId());
        if (profile != null) {
            String[] slots = {"head", "chest", "waist", "boots"};
            // 2. Save to file
            File file = new File(playersFolder, player.getUniqueId() + ".yml");
            YamlConfiguration config = new YamlConfiguration();

            for (String s : slots) {
                // 1. Despawn all holograms safely
                if (profile.getActiveCosmetic(s) != null) profile.getActiveCosmetic(s).despawn();
            }

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