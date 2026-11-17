package dc.dcessentials.modules.items.model;

public enum ItemRarity {
    COMMON("Common", "<#A0A0A0>", ":lztooltips:common:", "minecraft:common"),
    UNCOMMON("Uncommon", "<#55FF55>", ":lztooltips:uncommon:", "minecraft:uncommon"),
    RARE("Rare", "<#55FFFF>", ":lztooltips:rare:", "minecraft:rare"),
    EPIC("Epic", "<#AA00AA>", ":lztooltips:epic:", "minecraft:epic"),
    LEGENDARY("Legendary", "<#FFAA00>", ":lztooltips:legendary:", "minecraft:legendary"),
    MASTERY("Mastery", "<#FF5555>", ":lztooltips:mastery:", "lztooltips:artifact");

    private final String displayName;
    private final String color;
    private final String fontGlyph;
    private final String tooltipStyle;

    ItemRarity(String displayName, String color, String fontGlyph, String tooltipStyle) {
        this.displayName = displayName;
        this.color = color;
        this.fontGlyph = fontGlyph;
        this.tooltipStyle = tooltipStyle;
    }

    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }
    public String getColorHex() { return color; }
    public String getFontGlyph() { return fontGlyph; }
    public String getTooltipStyle() { return tooltipStyle; }

    public static ItemRarity fromString(String str) {
        if (str == null) return COMMON;
        String upper = str.toUpperCase().trim();
        if (upper.equals("ARTIFACT") || upper.equals("MYTHIC") || upper.equals("TRANSCENDENT")) {
            return MASTERY;
        }
        try {
            return ItemRarity.valueOf(upper);
        } catch (IllegalArgumentException e) {
            for (ItemRarity r : values()) {
                if (r.displayName.equalsIgnoreCase(str)) return r;
            }
            return COMMON;
        }
    }
}
