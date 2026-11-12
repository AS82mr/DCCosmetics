package dc.dcessentials.modules.items.model;

public enum StatCategory {
    OFFENSE("Offense"),
    DEFENSE("Defense"),
    UTILITY("Utility");

    private final String displayName;

    StatCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
