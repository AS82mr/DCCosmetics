package dc.dcessentials.modules.pets.model;

import java.util.Locale;

public enum SkillDomain {
    MELEE,
    RANGED,
    DEFENSE,
    MINING,
    EXCAVATION,
    HARVEST,
    FORAGING,
    FISHING,
    ARCANE,
    ALCHEMY,
    MOBILITY,
    UTILITY;

    public static SkillDomain fromString(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
