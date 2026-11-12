package dc.dcessentials.modules.pets.model;

/**
 * Explicit activation condition ("catch") for an active pet ability.
 */
public enum PetSkillTrigger {
    ON_ATTACK("Combat Strike", "Triggers when you strike a living enemy within range"),
    ON_TARGET_LOCK("Focused Target", "Triggers when looking at a tracked enemy and striking in range"),
    ON_DAMAGED("Defensive Counter", "Triggers automatically when you take combat damage"),
    ON_COMBAT_ANY("Combat Action", "Triggers when attacking a mob or taking combat damage"),
    ON_LOW_HP("Critical Threshold", "Triggers when injured below threshold health during combat");

    private final String displayLabel;
    private final String defaultDescription;

    PetSkillTrigger(String displayLabel, String defaultDescription) {
        this.displayLabel = displayLabel;
        this.defaultDescription = defaultDescription;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public String getDefaultDescription() {
        return defaultDescription;
    }

    public static PetSkillTrigger fromString(String raw) {
        if (raw == null || raw.isBlank()) return ON_ATTACK;
        String clean = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        try {
            return valueOf(clean);
        } catch (IllegalArgumentException ignored) {
            return ON_ATTACK;
        }
    }
}
