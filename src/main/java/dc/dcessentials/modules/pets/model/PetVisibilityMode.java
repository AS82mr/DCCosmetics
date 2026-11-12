package dc.dcessentials.modules.pets.model;

public enum PetVisibilityMode {
    ALL("All Pets Visible", "&aAll Pets"),
    OWN_ONLY("Own Pet Only", "&eOwn Pet Only"),
    HIDDEN("All Pets Hidden", "&cHidden");

    private final String description;
    private final String badge;

    PetVisibilityMode(String description, String badge) {
        this.description = description;
        this.badge = badge;
    }

    public String getDescription() {
        return description;
    }

    public String getBadge() {
        return badge;
    }

    public PetVisibilityMode next() {
        return switch (this) {
            case ALL -> OWN_ONLY;
            case OWN_ONLY -> HIDDEN;
            case HIDDEN -> ALL;
        };
    }

    public static PetVisibilityMode fromString(String str) {
        if (str == null) return ALL;
        try {
            return PetVisibilityMode.valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ALL;
        }
    }
}
