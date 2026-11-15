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
    // Creative Conditional Perks
    BLOODLUST_FRENZY(null),        // Combat: +X% bonus damage vs targets <= 50% HP or for 6s after a kill
    SUBTERRANEAN_WARD(null),       // Mining: -X% damage taken underground/in caves (50% effect on surface)
    VEIN_CHAIN_SURGE(null),        // Mining: 3+ ore veins grant Haste II + X% chance to chain-break adjacent ore
    AUTO_SMELT_FORTUNE(null),      // Mining: Raw ores auto-smelt on mine w/ X% chance for +1 bonus ingot
    MINER_AEGIS(null),             // Mining: Underground/Pickaxe grants +X% KB resist & periodic Absorption hearts
    AUTO_REPLANT_BLOOM(null),      // Farming: Auto-replants mature crops + X% chance to pulse growth in 3x3
    SUNLIT_HARVEST(null),          // Farming: Open daylight harvest restores saturation + X% bonus Farming XP
    TIMBER_FELLER(null),           // Foraging: X% chance on chopping natural trunks to topple up to 4 connected logs
    SPRINT_DODGE(null),            // Foraging: X% chance while sprinting/airborne to evade incoming hits
    RAIN_ANGLER_HASTE(null),       // Fishing: In rain or water, -X% bite wait time & Conduit Power
    CHILL_STRIKE(null),            // Fishing: Attacks in rain/water or vs chilled foes inflict Slowness II & +X% Frost DMG
    TREASURE_FISHER(null),         // Fishing: X% chance on catch to hook bonus gold/XP treasure
    KEYSTONE_LOW_HEAT_SURGE(null), // Enchanting: When Keystone Heat < 30, +X% ability/XP efficiency & teleports can skip cost
    ENCHANT_REFUND(null),          // Enchanting: X% chance at Enchanting Table to refund spent XP levels & Lapis
    DEBUFF_PUNISHER(null),         // Alchemy: +X% bonus combat DMG when target (or you) is burning/poisoned/withered/slowed/weak
    POTION_EXTENDER(null),         // Alchemy: +X% extended duration & extra Regeneration when drinking beneficial potions
    THERMAL_COMBAT_AURA(null),     // Alchemy: -X% Fire/Lava damage & ignites attackers while buffed or near heat
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
