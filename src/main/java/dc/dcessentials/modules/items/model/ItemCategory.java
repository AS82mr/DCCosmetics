package dc.dcessentials.modules.items.model;

import org.bukkit.Material;

public enum ItemCategory {
    WEAPON("Weapon", ":lztooltips:weapon:", true),
    ARMOR("Armor", ":lztooltips:armor:", true),
    TOOL("Tool", ":lztooltips:tool:", true),
    ACCESSORY("Accessory", ":lztooltips:accessory:", true),
    COSMETIC("Cosmetic", ":lztooltips:cosmetic:", true),
    PET("Pet", null, false),
    KEYSTONE("Keystone", null, false),
    VOUCHER("Voucher", null, false),
    SCROLL("Gameplay Scroll", null, false),
    COSMETIC_SCROLL("Cosmetic Scroll", null, false),
    VANILLA("Vanilla Base Gear", ":lztooltips:item:", true),
    INGREDIENT("Ingredient", null, false),
    ITEM("Item", ":lztooltips:item:", true);

    private final String displayName;
    private final String fontGlyph;
    private final boolean visibleTag;

    ItemCategory(String displayName, String fontGlyph, boolean visibleTag) {
        this.displayName = displayName;
        this.fontGlyph = fontGlyph;
        this.visibleTag = visibleTag;
    }

    public String getDisplayName() { return displayName; }
    public String getFontGlyph() { return fontGlyph; }
    public boolean isVisibleTag() { return visibleTag; }

    public static ItemCategory detectFromItem(org.bukkit.inventory.ItemStack item) {
        if (item == null) return ITEM;
        if (item.hasItemMeta()) {
            var meta = item.getItemMeta();
            var pdc = meta.getPersistentDataContainer();
            if (pdc.has(ItemRegistry.PET_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING)) {
                return PET;
            }
            if (pdc.has(new org.bukkit.NamespacedKey(dc.dccosmetics.DCCosmetics.getInstance(), "cosmetic_scroll_id"), org.bukkit.persistence.PersistentDataType.STRING)
                    || pdc.has(new org.bukkit.NamespacedKey("dcessentials", "cosmetic_scroll_id"), org.bukkit.persistence.PersistentDataType.STRING)) {
                return COSMETIC_SCROLL;
            }
            if (pdc.has(dc.dcessentials.modules.items.scroll.ScrollRegistry.SCROLL_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING)) {
                String type = pdc.get(dc.dcessentials.modules.items.scroll.ScrollRegistry.SCROLL_TYPE_KEY, org.bukkit.persistence.PersistentDataType.STRING);
                if ("COSMETIC".equalsIgnoreCase(type)) {
                    return COSMETIC_SCROLL;
                }
                return SCROLL;
            }
        }
        String name = "";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            name = org.bukkit.ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
        }
        if (name.contains("voucher") || name.contains("ticket") || name.contains("pass") || name.contains("rank")
                || name.contains("chat color") || name.contains("chatcolor") || name.contains("tag")) {
            return VOUCHER;
        }
        if ((name.contains("cosmetic") && (name.contains("scroll") || name.contains("skin"))) ||
                (name.contains("scroll") && (name.contains("kick") || name.contains("halo") || name.contains("wing") || name.contains("hat") || name.contains("aura")))) {
            return COSMETIC_SCROLL;
        }
        if (name.contains("scroll") || name.contains("elixir") || name.contains("infusion") || name.contains("draught") || name.contains("essence")) {
            return SCROLL;
        }
        return detectFromMaterial(item.getType());
    }

    public static ItemCategory detectFromMaterial(Material material) {
        if (material == null) return ITEM;
        String name = material.name();

        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || material == Material.MACE ||
                material == Material.TRIDENT || material == Material.BOW || material == Material.CROSSBOW) {
            return WEAPON;
        }

        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") ||
                name.endsWith("_BOOTS") || material == Material.ELYTRA || material == Material.CARVED_PUMPKIN) {
            return ARMOR;
        }

        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE") ||
                material == Material.FISHING_ROD || material == Material.SHEARS) {
            return TOOL;
        }

        if (material == Material.COMPASS || material == Material.RECOVERY_COMPASS ||
                material == Material.NETHER_STAR || material == Material.ECHO_SHARD) {
            return KEYSTONE;
        }

        if (material == Material.PAPER) {
            return SCROLL;
        }

        if (material == Material.HEART_OF_THE_SEA || material == Material.DRAGON_BREATH || material == Material.TOTEM_OF_UNDYING) {
            return ACCESSORY;
        }

        return ITEM;
    }

    public static ItemCategory fromString(String str) {
        if (str == null) return INGREDIENT;
        try {
            return ItemCategory.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            for (ItemCategory cat : values()) {
                if (cat.displayName.equalsIgnoreCase(str)) return cat;
            }
            return INGREDIENT;
        }
    }
}
