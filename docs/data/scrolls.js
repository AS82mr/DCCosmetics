window.WIKI_DATA = window.WIKI_DATA || {};

window.WIKI_DATA.SCROLLS_REGISTRY = [
  // Keystone Augments (KEYSTONE)
  { id: "keystone_heatsink", rarity: "EPIC", category: "Keystone", target: "KEYSTONE", effect: "KEYSTONE_HEAT (Legacy ID preserved): -35% Heat gain and +50% passive Heat cooling rate.", audience: "player" },
  { id: "keystone_tesseract_flux", rarity: "LEGENDARY", category: "Keystone", target: "KEYSTONE", effect: "KEYSTONE_TESSERACT (Legacy ID preserved): -25% Microcharge warp & ability cost.", audience: "player" },
  { id: "ks_thermal_rare", rarity: "RARE", category: "Keystone", target: "KEYSTONE", effect: "KEYSTONE_HEAT: -15% Heat gain and +25% cooling rate.", audience: "player" },
  { id: "ks_thermal_epic", rarity: "EPIC", category: "Keystone", target: "KEYSTONE", effect: "KEYSTONE_HEAT: -25% Heat gain and +40% cooling rate.", audience: "player" },
  { id: "ks_thermal_legendary", rarity: "LEGENDARY", category: "Keystone", target: "KEYSTONE", effect: "KEYSTONE_HEAT: -35% Heat gain and +60% cooling rate.", audience: "player" },
  { id: "ks_flux_rare", rarity: "RARE", category: "Keystone", target: "KEYSTONE", effect: "KEYSTONE_TESSERACT: -10% Microcharge warp & ability cost.", audience: "player" },
  { id: "ks_flux_epic", rarity: "EPIC", category: "Keystone", target: "KEYSTONE", effect: "KEYSTONE_TESSERACT: -18% Microcharge warp & ability cost.", audience: "player" },
  { id: "ks_capacitor_rare", rarity: "RARE", category: "Keystone", target: "KEYSTONE", effect: "KEYSTONE_CAPACITOR: +50 max Tesseract Energy capacity (hard cap 800).", audience: "player" },
  { id: "ks_capacitor_epic", rarity: "EPIC", category: "Keystone", target: "KEYSTONE", effect: "KEYSTONE_CAPACITOR: +100 max Tesseract Energy capacity (hard cap 800).", audience: "player" },
  { id: "ks_capacitor_legendary", rarity: "LEGENDARY", category: "Keystone", target: "KEYSTONE", effect: "KEYSTONE_CAPACITOR: +150 max Tesseract Energy capacity (hard cap 800).", audience: "player" },
  { id: "ks_anchor_epic", rarity: "EPIC", category: "Keystone", target: "KEYSTONE", effect: "KEYSTONE_ANCHOR: Negates Nether 2.0x dimension Heat penalty.", audience: "player" },
  { id: "ks_anchor_legendary", rarity: "LEGENDARY", category: "Keystone", target: "KEYSTONE", effect: "KEYSTONE_ANCHOR: Negates Nether penalty and suppresses 80+ Heat warp jitter.", audience: "player" },
  { id: "ks_anchor_mastery", rarity: "MASTERY", category: "Keystone", target: "KEYSTONE", effect: "KEYSTONE_ANCHOR: Negates Nether penalty, suppresses jitter, and stabilizes Heat at 99 to block Overload.", audience: "player" },
  { id: "ks_reclaim_epic", rarity: "EPIC", category: "Keystone", target: "KEYSTONE", effect: "KEYSTONE_RECLAIM: Refunds 40% of /tpa Microcharge cost back into your buffer.", audience: "player" },

  // Emperor & Combat Scrolls
  { id: "chrono_slip", rarity: "MYTHIC", category: "Emperor", target: "MACE", effect: "Builds charge (0/5); 5th smash bypasses The Emperor's Telekinetic Barrier (mace_bypassed).", audience: "player" },
  { id: "slayer_rare", rarity: "RARE", category: "Emperor", target: "WEAPON", effect: "Sovereign Bane I: +15% exclusive damage vs The Emperor (1.15x).", audience: "player" },
  { id: "slayer_epic", rarity: "EPIC", category: "Emperor", target: "WEAPON", effect: "Sovereign Bane II: +20% exclusive damage vs The Emperor (1.20x).", audience: "player" },
  { id: "slayer_mythic", rarity: "MYTHIC", category: "Emperor", target: "WEAPON", effect: "Sovereign Bane III: +35% exclusive damage vs The Emperor (1.35x).", audience: "player" },
  { id: "stasis_core", rarity: "TRANSCENDENT", category: "Emperor", target: "UTILITY", effect: "Right-click artifact: Freezes The Emperor (frozen_stasis) for 15s (AI and shields disabled). 60s cooldown.", audience: "player" },

  // Mastery & Adapt Boon Scrolls
  { id: "mastery_boon_swords", rarity: "RARE", category: "Mastery", target: "SWORD", effect: "Grants +20% Adapt Swords XP from sword combat (1.20x).", audience: "player" },
  { id: "mastery_boon_mining", rarity: "RARE", category: "Mastery", target: "TOOL", effect: "Grants +20% Adapt Pickaxe XP when mining ores (1.20x; mapped to Adapt pickaxe skill).", audience: "player" },
  { id: "adapt_swords_mastery", rarity: "MASTERY", category: "Mastery", target: "SWORD", effect: "+35% Adapt Swords XP gain & +10% Crit chance.", audience: "player" },
  { id: "adapt_axes_mastery", rarity: "MASTERY", category: "Mastery", target: "WEAPON", effect: "+35% Adapt Axes XP gain & +1.5m Cleave radius.", audience: "player" },
  { id: "adapt_blocking_mastery", rarity: "MASTERY", category: "Mastery", target: "WEAPON", effect: "+35% Adapt Blocking XP gain & +15% Ward absorption.", audience: "player" },
  { id: "adapt_ranged_mastery", rarity: "MASTERY", category: "Mastery", target: "WEAPON", effect: "+35% Adapt Ranged XP gain & +20% projectile velocity.", audience: "player" },
  { id: "adapt_unarmed_mastery", rarity: "MASTERY", category: "Mastery", target: "WEAPON", effect: "+35% Adapt Unarmed XP gain & 0.5s crit stun.", audience: "player" },
  { id: "adapt_agility_mastery", rarity: "MASTERY", category: "Mastery", target: "ARMOR", effect: "+35% Adapt Agility XP gain & +10% sprint speed.", audience: "player" },
  { id: "adapt_pickaxe_mastery", rarity: "MASTERY", category: "Mastery", target: "TOOL", effect: "+35% Adapt Pickaxe XP gain.", audience: "player" },
  { id: "adapt_excavation_mastery", rarity: "MASTERY", category: "Mastery", target: "TOOL", effect: "+35% Adapt Excavation XP gain.", audience: "player" },
  { id: "adapt_herbalism_mastery", rarity: "MASTERY", category: "Mastery", target: "TOOL", effect: "+35% Adapt Herbalism XP gain.", audience: "player" },
  { id: "adapt_chronos_mastery", rarity: "MASTERY", category: "Mastery", target: "ACCESSORY", effect: "+35% Adapt Chronos XP gain.", audience: "player" },
  { id: "adapt_rift_mastery", rarity: "MASTERY", category: "Mastery", target: "ACCESSORY", effect: "+35% Adapt Rift XP gain.", audience: "player" },
  { id: "adapt_tragoul_mastery", rarity: "MASTERY", category: "Mastery", target: "ACCESSORY", effect: "+35% Adapt Tragoul XP gain.", audience: "player" }
];
