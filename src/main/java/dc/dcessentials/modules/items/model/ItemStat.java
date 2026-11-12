package dc.dcessentials.modules.items.model;

public enum ItemStat {
    RESONANCE("Resonance", "🎶", "<#84E2CD>", StatCategory.OFFENSE,
            "Acoustic Cadence Burst",
            "Every Nth strike unleashes a sonic shockwave",
            "Every N consecutive hits triggers a sonic Resonance shockwave, dealing bonus area damage and disrupting enemy defenses.",
            2.0, 8.0, 0),

    SURGE("Surge", "⚡", "<#FCD34D>", StatCategory.OFFENSE,
            "Kinetic Momentum & Swiftness",
            "Stacking hit momentum & attack speed burst",
            "+X% damage momentum. Successive rapid hits amplify weapon damage and grant brief bursts of movement speed.",
            60.0, 100.0, 1),

    STASIS("Stasis", "⏱", "<#06B6D4>", StatCategory.OFFENSE,
            "Chronon Temporal Freeze",
            "Slows target movement & attack cadence",
            "+X% duration temporal freeze. Freezes enemy movement, disables mobility abilities, and slows attack cooldowns.",
            75.0, 75.0, 1),

    CATALYST("Catalyst", "🌀", "<#A855F7>", StatCategory.OFFENSE,
            "Alchemical Potency & Amplification",
            "Amplifies damage per active status effect",
            "+X% damage amplification from active elemental and potion effects, converting enemy debuffs into bonus attack power.",
            60.0, 60.0, 1),

    EXECUTE("Execute", "💀", "<#EF4444>", StatCategory.OFFENSE,
            "Finisher & Desperation Cull",
            "Bonus finisher damage vs low-HP targets",
            "+X% damage dealt to enemies below 35% health, executing wounded targets with immense bonus critical damage.",
            50.0, 50.0, 1),

    CLEAVE("Cleave", "⚔", "<#F97316>", StatCategory.OFFENSE,
            "Armor-Piercing Sweep Attack",
            "Sweeping arc damage to nearby enemies",
            "+X% wide sweep damage that bypasses blocking shields, slicing through all nearby hostile targets in a wide arc.",
            100.0, 100.0, 1),

    SIPHON("Siphon", "🩸", "<#E11D48>", StatCategory.DEFENSE,
            "Vampiric Life Leech",
            "Leeches a % of damage dealt as health",
            "Converts +X% of all physical damage dealt directly into player health restoration, overflowing into temporary absorption.",
            50.0, 50.0, 1),

    WARD("Ward", "🛡", "<#3B82F6>", StatCategory.DEFENSE,
            "Tenacity & Damage Mitigation",
            "Reduces incoming physical & projectile damage",
            "+X% incoming damage reduction and guard barrier, mitigating true damage and reducing knockback momentum.",
            60.0, 60.0, 1),

    BASTION("Bastion", "⛨", "<#60A5FA>", StatCategory.DEFENSE,
            "Fortified Poise & Single-Hit Ceiling",
            "Single-hit HP ceiling & poise",
            "Caps maximum single-hit health loss while above 60% HP and grants passive knockback resistance.",
            60.0, 60.0, 1),

    DEFLECT("Deflect", "↺", "<#38BDF8>", StatCategory.DEFENSE,
            "Glancing Parry",
            "Chance to glance hits (-50% dmg)",
            "+X% chance for incoming melee or projectile attacks to glance off your guard for 50% reduced damage.",
            35.0, 35.0, 1),

    AEGIS("Aegis", "✚", "<#34D399>", StatCategory.DEFENSE,
            "Elemental & Hazard Ward",
            "Hazard, magic & elemental ward",
            "-X% damage taken from fire, lava, magic, wither, poison, explosions, lightning, freeze, and sonic booms.",
            60.0, 60.0, 1),

    PROSPERITY("Prosperity", "❖", "<#FBBF24>", StatCategory.UTILITY,
            "Bountiful Harvest & Fortune",
            "Chance to duplicate gathered drops",
            "+X% chance to duplicate natural ore, mature crop, log, and fishing catches.",
            60.0, 60.0, 1),

    TEMPO("Tempo", "➤", "<#A3E635>", StatCategory.UTILITY,
            "Gathering & Lure Cadence",
            "Mining break speed & lure cadence",
            "+X% block break speed and -X% fishing wait time.",
            50.0, 50.0, 1),

    REFINEMENT("Refinement", "✧", "<#C084FC>", StatCategory.UTILITY,
            "Artisan Efficiency & Insight",
            "Bonus craft/brew/enchant XP & refund",
            "+X% Adapt XP from crafting, brewing, and enchanting, plus X% chance to refund enchantment level costs.",
            40.0, 40.0, 1),

    CONDUIT("Conduit", "⚛", "<#22D3EE>", StatCategory.UTILITY,
            "Tesseract Channeling & Siphon",
            "Reduces mC cost & siphons charge",
            "-X% Microcharge cost for abilities and warps, plus X% chance on mob kill to siphon 25-100 mC.",
            40.0, 40.0, 1),

    THERMAL("Thermal", "❄", "<#7DD3FC>", StatCategory.UTILITY,
            "Cryogenic Heat Dissipation",
            "Reduces Heat gain & speeds cooling",
            "-X% Keystone Heat accumulation and +floor(X/20) passive Heat cooling per 30s cycle.",
            60.0, 60.0, 1);

    private final String displayName;
    private final String icon;
    private final String color;
    private final StatCategory category;
    private final String summary;
    private final String brief;
    private final String description;
    private final double softCap;
    private final double hardCap;
    private final int decimals;

    ItemStat(String displayName, String icon, String color, StatCategory category,
             String summary, String brief, String description,
             double softCap, double hardCap, int decimals) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
        this.category = category;
        this.summary = summary;
        this.brief = brief;
        this.description = description;
        this.softCap = softCap;
        this.hardCap = hardCap;
        this.decimals = decimals;
    }

    public String getDisplayName() { return displayName; }
    public String getIcon() { return icon; }
    public String getColor() { return color; }
    public StatCategory getCategory() { return category; }
    public String getSummary() { return summary; }
    public String getBrief() { return brief; }
    public String getDescription() { return description; }
    public double getSoftCap() { return softCap; }
    public double getHardCap() { return hardCap; }
    public int getDecimals() { return decimals; }

    public static ItemStat fromString(String str) {
        if (str == null) return null;
        try {
            return ItemStat.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            for (ItemStat stat : values()) {
                if (stat.displayName.equalsIgnoreCase(str)) return stat;
            }
            return null;
        }
    }
}
