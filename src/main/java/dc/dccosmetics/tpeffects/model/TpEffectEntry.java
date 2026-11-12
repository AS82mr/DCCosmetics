package dc.dccosmetics.tpeffects.model;

import dc.dcessentials.core.util.ColorUtil;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Immutable definition (with mutable runtime enable/hide toggles synced to YAML)
 * for a configurable teleportation effect loaded from plugins/DCEssentials/tpeffects/*.yml.
 */
public final class TpEffectEntry {

    public enum EffectType {
        MYTHIC_MOB,
        CINEMATIC;

        public static EffectType fromString(String raw) {
            if (raw == null) return MYTHIC_MOB;
            try {
                return EffectType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return MYTHIC_MOB;
            }
        }
    }

    public enum CinematicStyle {
        OBSIDIAN_DISSOLUTION(210),
        CHRONO_SINGULARITY(210),
        UMBRAL_DISSOLUTION(80),
        SHADOW_DRIFT(64),
        SKYLINE_SWITCH(96);

        private final int defaultDurationTicks;

        CinematicStyle(int defaultDurationTicks) {
            this.defaultDurationTicks = defaultDurationTicks;
        }

        public int getDefaultDurationTicks() {
            return defaultDurationTicks;
        }

        public static CinematicStyle fromString(String raw) {
            if (raw == null || raw.isBlank()) return OBSIDIAN_DISSOLUTION;
            String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace("CINE:", "");
            try {
                return CinematicStyle.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return OBSIDIAN_DISSOLUTION;
            }
        }
    }

    private final String id;
    private final String rarity;
    private final String sourceFile;
    private final String displayName;
    private volatile boolean enabled;
    private volatile boolean hidden;
    private final String permission;
    private final EffectType type;
    private final String mythicMob;
    private final CinematicStyle cinematicStyle;
    private final int durationTicks;
    private final Material material;
    private final int customModelData;
    private final List<String> lore;

    public TpEffectEntry(
            String id,
            String rarity,
            String sourceFile,
            String displayName,
            boolean enabled,
            boolean hidden,
            String permission,
            EffectType type,
            String mythicMob,
            CinematicStyle cinematicStyle,
            Material material,
            int customModelData,
            List<String> lore
    ) {
        this(
                id,
                rarity,
                sourceFile,
                displayName,
                enabled,
                hidden,
                permission,
                type,
                mythicMob,
                cinematicStyle,
                cinematicStyle != null ? cinematicStyle.getDefaultDurationTicks() : 80,
                material,
                customModelData,
                lore
        );
    }

    public TpEffectEntry(
            String id,
            String rarity,
            String sourceFile,
            String displayName,
            boolean enabled,
            boolean hidden,
            String permission,
            EffectType type,
            String mythicMob,
            CinematicStyle cinematicStyle,
            int durationTicks,
            Material material,
            int customModelData,
            List<String> lore
    ) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.rarity = rarity.toLowerCase(Locale.ROOT);
        this.sourceFile = sourceFile;
        this.displayName = displayName != null && !displayName.isEmpty() ? displayName : id;
        this.enabled = enabled;
        this.hidden = hidden;
        this.permission = permission != null && !permission.isEmpty() ? permission : "dc.tpeffect." + this.id;
        this.type = type != null ? type : EffectType.MYTHIC_MOB;
        this.mythicMob = mythicMob;
        this.cinematicStyle = cinematicStyle;
        this.durationTicks = Math.max(20, durationTicks);
        this.material = material != null ? material : Material.ENDER_PEARL;
        this.customModelData = customModelData;
        this.lore = lore != null ? Collections.unmodifiableList(lore) : Collections.emptyList();
    }

    public String getId() {
        return id;
    }

    public String getRarity() {
        return rarity;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public String getRawDisplayName() {
        return displayName;
    }

    public String getColoredDisplayName() {
        return ColorUtil.colorize(displayName);
    }

    public String getStrippedDisplayName() {
        String stripped = ChatColor.stripColor(getColoredDisplayName());
        return stripped != null ? stripped : id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isVisibleInMenu() {
        return enabled && !hidden;
    }

    public String getPermission() {
        return permission;
    }

    public EffectType getType() {
        return type;
    }

    public boolean isCinematic() {
        return type == EffectType.CINEMATIC;
    }

    public String getMythicMob() {
        return mythicMob;
    }

    public CinematicStyle getCinematicStyle() {
        return cinematicStyle;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    /**
     * Backward-compatible key consumed by legacy UI/command code paths.
     */
    public String getLegacyEffectKey() {
        if (isCinematic()) {
            return "CINE:" + (cinematicStyle != null ? cinematicStyle.name() : id.toUpperCase(Locale.ROOT));
        }
        return mythicMob != null ? mythicMob : "";
    }

    public Material getMaterial() {
        return material;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public List<String> getRawLore() {
        return lore;
    }

    public List<String> getColoredLore() {
        return ColorUtil.colorize(lore);
    }

    /**
     * Builds an ItemStack configured with this effect's icon, display name, custom model data,
     * and configured voucher lore so it can be granted or handed cleanly to DCEItems integration.
     */
    public ItemStack buildVoucherItem(Plugin plugin) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(getColoredDisplayName());
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            if (!lore.isEmpty()) {
                meta.setLore(getColoredLore());
            }
            if (plugin != null) {
                meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "dc_tpeffect_voucher"),
                        PersistentDataType.STRING,
                        id
                );
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
