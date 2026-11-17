window.WIKI_DATA = window.WIKI_DATA || {};

window.WIKI_DATA.STATS_REGISTRY = [
  // Offense (6)
  {
    id: "RESONANCE",
    name: "Resonance",
    icon: "🎶",
    color: "#84E2CD",
    category: "OFFENSE",
    brief: "Every Nth strike unleashes a sonic shockwave",
    effect: "Every N consecutive hits (400ms–1400ms cadence window) triggers Harmonic Shatter: 1.25x damage, sonic boom VFX, and 3s (60 ticks) shield disable. Stacks by taking lowest base cadence minus 1 per extra Resonance source.",
    cap: "2 – 8 Hits",
    audience: "player"
  },
  {
    id: "SURGE",
    name: "Surge",
    icon: "⚡",
    color: "#FCD34D",
    category: "OFFENSE",
    brief: "Stacking hit momentum & attack speed burst",
    effect: "Each hit within 3.5s builds +4% Surge damage momentum per stack up to your soft cap (100% hard cap across all sources). Taking an incoming hit resets active Surge stacks.",
    cap: "Soft 60% · Hard 100%",
    audience: "player"
  },
  {
    id: "STASIS",
    name: "Stasis",
    icon: "⏱",
    color: "#06B6D4",
    category: "OFFENSE",
    brief: "Slows target movement & attack cadence",
    effect: "Converts X% of post-mitigation damage into Chronon Debt held for 2.5s (50 ticks) before detonating, dealing 1.25x bonus if the target moves more than 2 blocks.",
    cap: "75%",
    audience: "player"
  },
  {
    id: "CATALYST",
    name: "Catalyst",
    icon: "🌀",
    color: "#A855F7",
    category: "OFFENSE",
    brief: "Amplifies damage per active status effect",
    effect: "Converts active debuffs on the attacker (Weakness, Fire, Poison, Wither, Slowness, Blindness, Mining Fatigue, Hunger, Nausea, Levitation) into bonus damage amplification and Poison/Wither sustain.",
    cap: "60%",
    audience: "player"
  },
  {
    id: "EXECUTE",
    name: "Execute",
    icon: "💀",
    color: "#EF4444",
    category: "OFFENSE",
    brief: "Bonus finisher damage vs low-HP targets",
    effect: "Grants +35% desperation damage when attacker HP <= 30%, and culls non-boss mobs cleanly if their remaining health drops below X% post-mitigation.",
    cap: "50%",
    audience: "player"
  },
  {
    id: "CLEAVE",
    name: "Cleave",
    icon: "⚔",
    color: "#F97316",
    category: "OFFENSE",
    brief: "Sweeping arc damage to nearby enemies",
    effect: "Sweeps nearby hostiles within a 3.5x2x3.5 arc for X% damage (skips ModelEngine hitboxes and prevents recursion) and disables blocking shields for 50 ticks.",
    cap: "100%",
    audience: "player"
  },

  // Defense (5)
  {
    id: "SIPHON",
    name: "Siphon",
    icon: "🩸",
    color: "#E11D48",
    category: "DEFENSE",
    brief: "Leeches a % of damage dealt as health",
    effect: "Restores health equal to X% of post-mitigation melee damage dealt; excess healing above max HP converts at 50% efficiency into temporary Absorption hearts (up to 12 HP).",
    cap: "50% (12 Abs HP)",
    audience: "player"
  },
  {
    id: "WARD",
    name: "Ward",
    icon: "🛡",
    color: "#3B82F6",
    category: "DEFENSE",
    brief: "Reduces incoming physical & projectile damage",
    effect: "Reduces incoming ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, and PROJECTILE damage by X% inside StatCombatEngine before combined reduction flooring and Bastion capping.",
    cap: "60%",
    audience: "player"
  },
  {
    id: "BASTION",
    name: "Bastion",
    icon: "⛨",
    color: "#60A5FA",
    category: "DEFENSE",
    brief: "Single-hit HP ceiling & poise",
    effect: "Caps maximum single-hit damage at maxHP * max(0.40, 1 - X/100) while your HP >= 60% of max, plus grants a transient knockback-resistance modifier min(0.30, X/200) (dce_bastion_kb).",
    cap: "60%",
    audience: "player"
  },
  {
    id: "DEFLECT",
    name: "Deflect",
    icon: "↺",
    color: "#38BDF8",
    category: "DEFENSE",
    brief: "Chance to glance hits (-50% dmg)",
    effect: "Gives X% chance for an incoming entity strike to glance off your guard, cutting incoming damage by 50% with parry spark feedback.",
    cap: "35%",
    audience: "player"
  },
  {
    id: "AEGIS",
    name: "Aegis",
    icon: "✚",
    color: "#34D399",
    category: "DEFENSE",
    brief: "Hazard, magic & elemental ward",
    effect: "Reduces incoming damage by X% from hazard and elemental causes (FIRE, FIRE_TICK, LAVA, HOT_FLOOR, MAGIC, POISON, WITHER, DRAGON_BREATH, EXPLOSION, LIGHTNING, FREEZE, SONIC_BOOM).",
    cap: "60%",
    audience: "player"
  },

  // Utility (5)
  {
    id: "PROSPERITY",
    name: "Prosperity",
    icon: "❖",
    color: "#FBBF24",
    category: "UTILITY",
    brief: "Chance to duplicate gathered drops",
    effect: "Grants X% chance (additive with pet GATHERING_FORTUNE perks) to duplicate actual dropped items on ores, mature crops, logs, and fishing catches. Never triggers on Silk Touch, Creative, or player-placed blocks.",
    cap: "60%",
    audience: "player"
  },
  {
    id: "TEMPO",
    name: "Tempo",
    icon: "➤",
    color: "#A3E635",
    category: "UTILITY",
    brief: "Mining break speed & lure cadence",
    effect: "Applies +X% block break speed via transient Attribute.BLOCK_BREAK_SPEED (dce_tempo_break) and reduces FishHook wait times by -X%.",
    cap: "50%",
    audience: "player"
  },
  {
    id: "REFINEMENT",
    name: "Refinement",
    icon: "✧",
    color: "#C084FC",
    category: "UTILITY",
    brief: "Bonus craft/brew/enchant XP & refund",
    effect: "Grants +X% bonus Skill XP on crafting, brewing, and enchanting actions, plus X% chance to refund enchantment level cost on EnchantItemEvent.",
    cap: "40%",
    audience: "player"
  },
  {
    id: "CONDUIT",
    name: "Conduit",
    icon: "⚛",
    color: "#22D3EE",
    category: "UTILITY",
    brief: "Reduces ↯ cost & siphons charge",
    effect: "Reduces Keystone Microcharge warp and ability costs (sharing the 70% cost-reduction cap), plus gives min(25%, X%) chance on hostile kill to siphon 25–100 ↯ (capped at 400 ↯/min/player).",
    cap: "40% (25% Siphon)",
    audience: "player"
  },
  {
    id: "THERMAL",
    name: "Thermal",
    icon: "❄",
    color: "#7DD3FC",
    category: "UTILITY",
    brief: "Reduces Heat gain & speeds cooling",
    effect: "Scales Keystone Heat accumulation by (1 - min(60, X)/100) and adds +floor(X/20) passive Heat dissipation per 30-second cycle.",
    cap: "60%",
    audience: "player"
  }
];

window.WIKI_DATA.STAT_SOURCES = [
  { id: "MAIN_HAND", label: "Main Hand", desc: "Held weapon or tool (scrolls, vouchers, unequipped keystones, armor, and pet tokens are ignored in main hand).", audience: "player" },
  { id: "OFF_HAND", label: "Offhand", desc: "Shields, totems, or items explicitly tagged OFF_HAND / ACCESSORY.", audience: "player" },
  { id: "ARMOR", label: "Armor", desc: "Equipped Helmet, Chestplate, Leggings, and Boots.", audience: "player" },
  { id: "ACCESSORIES", label: "Accessories", desc: "Profile Column 2 slots: Amulet (20), Cloak (29), and Gloves (38).", audience: "player" },
  { id: "KEYSTONE", label: "Keystone", desc: "Equipped Keystone item in Profile slot 11.", audience: "player" },
  { id: "ACTIVE_PET", label: "Active Pet", desc: "Level-scaled passive stats contributed by your summoned companion.", audience: "player" },
  { id: "SCROLLS", label: "Scrolls", desc: "Socketed augment bonuses attached to equipped gear and Keystones.", audience: "player" }
];
