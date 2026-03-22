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
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            if (config.contains("equipped")) {
                for (String slot : config.getConfigurationSection("equipped").getKeys(false)) {
                    String cosmeticId = config.getString("equipped." + slot + ".id");
                    String color = config.getString("equipped." + slot + ".color", "#FFFFFF");
                    profile.setEquipped(slot, cosmeticId, color);
                }
            }
        }
        return profile;
    }

    public void loadProfile(Player player) {
        PlayerProfile profile = new PlayerProfile(player);
        profiles.put(player.getUniqueId(), profile);

        File file = new File(playersFolder, player.getUniqueId() + ".yml");
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            if (config.contains("equipped")) {
                for (String slot : config.getConfigurationSection("equipped").getKeys(false)) {
                    String cosmeticId = config.getString("equipped." + slot + ".id");
                    String color = config.getString("equipped." + slot + ".color", "#FFFFFF");
                    profile.setEquipped(slot, cosmeticId, color);
                }
            }
        }
        DCCosmetics.getInstance().getLogger().info("[DEBUG] Loaded profile for " + player.getName());

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
                
                if (profile.getEquippedCosmetic(s) != null) {
                    config.set("equipped." + s + ".id", profile.getEquippedCosmetic(s));
                    config.set("equipped." + s + ".color", profile.getEquippedColor(s));
                }
            }

            try {
                config.save(file);
            } catch (IOException e) {
                e.printStackTrace();
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