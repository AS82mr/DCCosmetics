package dc.dcessentials.modules.items.stats;

public enum StatSource {
    MAIN_HAND("Main Hand"),
    OFF_HAND("Off Hand"),
    ARMOR("Armor"),
    ACCESSORIES("Accessories"),
    KEYSTONE("Keystone"),
    ACTIVE_PET("Active Pet"),
    SCROLLS("Scrolls");

    private final String displayName;

    StatSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static StatSource fromString(String input) {
        if (input == null) return null;
        String clean = input.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        return switch (clean) {
            case "MAIN_HAND", "MAINHAND", "WEAPON", "HAND" -> MAIN_HAND;
            case "OFF_HAND", "OFFHAND", "SHIELD" -> OFF_HAND;
            case "ARMOR", "ARMOUR" -> ARMOR;
            case "ACCESSORIES", "ACCESSORY", "AMULET", "CLOAK", "GLOVES" -> ACCESSORIES;
            case "KEYSTONE", "TESSERACT" -> KEYSTONE;
            case "ACTIVE_PET", "PET", "PETS" -> ACTIVE_PET;
            case "SCROLLS", "SCROLL", "SOCKETS" -> SCROLLS;
            default -> {
                try {
                    yield StatSource.valueOf(clean);
                } catch (IllegalArgumentException e) {
                    yield null;
                }
            }
        };
    }
}
