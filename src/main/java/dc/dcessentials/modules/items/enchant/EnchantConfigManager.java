package dc.dcessentials.modules.items.enchant;

import dc.dccosmetics.DCCosmetics;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads vanilla enchantment max level caps from DCEssentials/enchants.yml.
 * Only vanilla (minecraft:) enchantments are supported.
 *
 * Raising a cap above vanilla (e.g. sharpness: 6) marks level >= cap as maxed (&c)
 * in tooltips and permits anvil/drag-and-drop application of reward books up to that
 * cap without unlocking over-cap levels in vanilla enchanting tables.
 */
public final class EnchantConfigManager {

    private static final Map<String, Integer> maxLevels = new LinkedHashMap<>();

    private EnchantConfigManager() {}

    public static void load() {
        DCCosmetics plugin = DCCosmetics.getInstance();
        if (plugin == null) return;

        File file = new File(plugin.getDataFolder(), "enchants.yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                try (InputStream in = plugin.getResource("enchants.yml")) {
                    if (in != null) {
                        Files.copy(in, file.toPath());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[DCEssentials] Could not save default enchants.yml: " + e.getMessage());
            }
        }

        maxLevels.clear();
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("max_levels");
        if (section == null) {
            section = config;
        }

        for (String rawKey : section.getKeys(false)) {
            if (!section.isInt(rawKey)) continue;
            String normalized = normalizeKey(rawKey);
            NamespacedKey nk = NamespacedKey.minecraft(normalized);
            Enchantment ench = org.bukkit.Registry.ENCHANTMENT.get(nk);
            if (ench == null) {
                plugin.getLogger().warning("[DCEssentials] Ignoring non-vanilla or unknown enchantment in enchants.yml: " + rawKey);
                continue;
            }
            int cap = Math.max(1, section.getInt(rawKey));
            maxLevels.put(normalized, cap);
        }
    }

    private static String normalizeKey(String key) {
        String s = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("minecraft:")) {
            s = s.substring("minecraft:".length());
        }
        return s;
    }

    public static boolean isVanilla(Enchantment enchantment) {
        return enchantment != null && NamespacedKey.MINECRAFT.equals(enchantment.getKey().getNamespace());
    }

    public static int getMaxLevel(Enchantment enchantment) {
        if (enchantment == null) return 1;
        String key = enchantment.getKey().getKey().toLowerCase(Locale.ROOT);
        Integer configured = maxLevels.get(key);
        if (configured != null) {
            return Math.max(1, configured);
        }
        return Math.max(1, enchantment.getMaxLevel());
    }

    public static boolean isMaxed(Enchantment enchantment, int level) {
        return level >= getMaxLevel(enchantment);
    }

    public static Map<String, Integer> getConfiguredCaps() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(maxLevels));
    }
}
