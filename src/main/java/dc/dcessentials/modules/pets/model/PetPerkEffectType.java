package dc.dcessentials.modules.pets.model;

import dc.dcessentials.modules.items.model.ItemStat;

/**
 * Functional gameplay effect types supported by progressive pet perks.
 */
public enum PetPerkEffectType {
    STAT_SURGE(ItemStat.SURGE),
    STAT_STASIS(ItemStat.STASIS),
    STAT_CATALYST(ItemStat.CATALYST),
    STAT_EXECUTE(ItemStat.EXECUTE),
    STAT_CLEAVE(ItemStat.CLEAVE),
    STAT_SIPHON(ItemStat.SIPHON),
    STAT_WARD(ItemStat.WARD),
    STAT_BASTION(ItemStat.BASTION),
    STAT_DEFLECT(ItemStat.DEFLECT),
    STAT_AEGIS(ItemStat.AEGIS),
    STAT_PROSPERITY(ItemStat.PROSPERITY),
    STAT_TEMPO(ItemStat.TEMPO),
    STAT_REFINEMENT(ItemStat.REFINEMENT),
    STAT_CONDUIT(ItemStat.CONDUIT),
    STAT_THERMAL(ItemStat.THERMAL),
    SKILL_COOLDOWN_HASTE(null),
    SKILL_POWER_BOOST(null),
    PET_XP_BOOST(null),
    BONUS_MOB_DAMAGE(null),
    BOSS_SLAYER_DAMAGE(null),
    DAMAGE_REDUCTION(null),
    VAMPIRIC_HEAL(null),
    MOVEMENT_SPEED(null),
    GATHERING_FORTUNE(null),
    ALLY_AURA_REGEN(null),
    NONE(null);

    private final ItemStat mappedStat;

    PetPerkEffectType(ItemStat mappedStat) {
        this.mappedStat = mappedStat;
    }

    public ItemStat getMappedStat() {
        return mappedStat;
    }

    public static PetPerkEffectType fromString(String raw) {
        if (raw == null || raw.isBlank()) return NONE;
        String clean = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        try {
            return valueOf(clean);
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
