package dc.dcessentials.modules.pets.model;

/**
 * Hypixel-style pet skill categories mapped cleanly to player Skill XP sources.
 * Player-facing text always refers to "Skills" rather than internal plugin names.
 */
public enum PetCategory {
    COMBAT("Combat", "&#E05252", "⚔"),
    MINING("Mining", "&#6FB6D6", "⛏"),
    FARMING("Farming", "&#74C365", "❖"),
    FORAGING("Foraging", "&#5CA852", "♣"),
    FISHING("Fishing", "&#4FA8D8", "≈"),
    ALCHEMY("Alchemy", "&#C47AD9", "⚗"),
    ENCHANTING("Enchanting", "&#6B8EFF", "✦"),
    OMNI("Omni-Skill", "&#F5C242", "★");

    private final String displayName;
    private final String color;
    private final String icon;

    PetCategory(String displayName, String color, String icon) {
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public String getIcon() {
        return icon;
    }

    public String getFormattedBadge() {
        return color + icon + " " + displayName;
    }

    public static PetCategory fromString(String raw) {
        if (raw == null || raw.isBlank()) return COMBAT;
        String clean = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        if (clean.equals("ALL") || clean.equals("ALL_SKILLS") || clean.equals("OMNI_SKILL")) {
            return OMNI;
        }
        try {
            return valueOf(clean);
        } catch (IllegalArgumentException ignored) {
            return COMBAT;
        }
    }
}
