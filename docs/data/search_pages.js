window.WIKI_DATA = window.WIKI_DATA || {};

window.WIKI_DATA.NAV_GROUPS = [
  {
    name: "Core Systems",
    items: [
      { id: "index", file: "index.html", label: "Overview", audience: "player" },
      { id: "items-stats", file: "items-stats.html", label: "DCEItems & 16 Stats", audience: "player" },
      { id: "keystones", file: "keystones.html", label: "Keystones & Energy", audience: "player" },
      { id: "pets", file: "pets.html", label: "DCEPets & Domains", audience: "player" },
      { id: "cosmetics", file: "cosmetics.html", label: "Cosmetics & Profile", audience: "player" },
      { id: "tpeffects", file: "tpeffects.html", label: "Teleport Effects", audience: "player" }
    ]
  },
  {
    name: "World & Boss",
    items: [
      { id: "emperor", file: "emperor.html", label: "The Emperor", audience: "player" },
      { id: "world-events", file: "world-events.html", label: "World Events & NPCs", audience: "player" }
    ]
  },
  {
    name: "Reference",
    items: [
      { id: "commands", file: "commands.html", label: "Commands", audience: "player" },
      { id: "placeholders", file: "placeholders.html", label: "Placeholders", audience: "player" },
      { id: "admin", file: "admin.html", label: "Staff & Internal Docs", audience: "admin" }
    ]
  }
];

window.WIKI_DATA.SEARCH_SECTIONS = [
  { file: "index.html", secId: "sec-arch", page: "Overview", title: "DCEssentials v2 Architecture", audience: "player", text: "System layers DCEItems 16 stats StatCombatEngine Keystones Microcharges DCEPets Cosmetics Emperor Events DCNPC" },
  { file: "index.html", secId: "sec-changelog", page: "Overview", title: "Keystone v2 & Phase 1-3 Updates", audience: "player", text: "16 stats BASTION DEFLECT AEGIS PROSPERITY TEMPO REFINEMENT CONDUIT THERMAL native Tesseract Chronolith Singularity 1000 mC Microcharge buffer expandable tooltips 2x socket badges stationary perch SkillDomain" },

  { file: "items-stats.html", secId: "sec-stats", page: "Items & Stats", title: "All 16 ItemStats (Offense / Defense / Utility)", audience: "player", text: "RESONANCE SURGE STASIS CATALYST EXECUTE CLEAVE SIPHON WARD BASTION DEFLECT AEGIS PROSPERITY TEMPO REFINEMENT CONDUIT THERMAL" },
  { file: "items-stats.html", secId: "sec-pipeline", page: "Items & Stats", title: "StatCombatEngine & Damage Pipeline", audience: "player", text: "UnstableVulnerability +15% DEFLECT glance -50% WARD AEGIS 80% combined floor BASTION single-hit HP ceiling Conduit siphon" },
  { file: "items-stats.html", secId: "sec-sources", page: "Items & Stats", title: "7 StatSources & Stack Calculator", audience: "player", text: "MAIN_HAND OFF_HAND ARMOR ACCESSORIES KEYSTONE ACTIVE_PET SCROLLS stack calculator surge resonance" },
  { file: "items-stats.html", secId: "sec-gathering", page: "Items & Stats", title: "Gathering & Artisan Bonuses", audience: "player", text: "PROSPERITY crop ore log fishing fortune PlacedBlockTracker Silk Touch guard TEMPO break speed lure wait REFINEMENT enchant refund Adapt XP" },
  { file: "items-stats.html", secId: "sec-tooltips", page: "Items & Stats", title: "Expandable Tooltips & 2x Socket Icons", audience: "player", text: "/tooltip compact expanded toggle Shift Right Click sneak open /dce inspect ItemInspectGUI ProtocolLib packet rewrite 20px socket badges" },
  { file: "items-stats.html", secId: "sec-warper", page: "Items & Stats", title: "Void Warper & Microcharge Costs", audience: "player", text: "Void Bounce 75 mC Sonic Boom 250 mC hyperspace dash overdraw heat actionbar death cancel" },
  { file: "items-stats.html", secId: "sec-scrolls", page: "Items & Stats", title: "Complete Scrolls & Augments Catalog", audience: "player", text: "chrono_slip slayer stasis_core ks_thermal ks_flux ks_capacitor ks_anchor ks_reclaim mastery adapt scrolls" },

  { file: "keystones.html", secId: "sec-tiers", page: "Keystones", title: "Native Keystones (Tesseract / Chronolith / Singularity)", audience: "player", text: "Rare Tesseract 200 max 1 socket Epic Chronolith 400 max 2 sockets -15% cost 2 cooling Legendary Singularity 600 max 3 sockets -30% cost 4 cooling profile slot 11 right click equip" },
  { file: "keystones.html", secId: "sec-micro", page: "Keystones", title: "1,000 mC Microcharge Buffer & Overdraw", audience: "player", text: "1 Tesseract charge equals 1000 Microcharges crack charge warp cost 1000-3000 mC ability cost 75 mC 250 mC overdraw parked energy non-destructive swaps" },
  { file: "keystones.html", secId: "sec-heat", page: "Keystones", title: "Thermodynamics, Unstable Heat & Overload", audience: "player", text: "80-99 heat unstable jitter +15% hazard boss damage 100 heat Overload Nexus Incursion sentinel cools at <= 50 cryo water powder snow 20 heat nether 2.0x" },
  { file: "keystones.html", secId: "sec-sockets", page: "Keystones", title: "Keystone GUI & Multi-Socket Scrolls", audience: "player", text: "54 slot KeystoneMenu deposit +1 +10 All cursor withdraw cubes slots 20 21 22 socket_keystone_slots KEYSTONE_HEAT TESSERACT CAPACITOR ANCHOR RECLAIM" },

  { file: "pets.html", secId: "sec-pet-arch", page: "Pets", title: "DCEPets Architecture & Stationary Perch", audience: "player", text: "Lv 1-100 perks 1/2/3/4/5 ProtocolLib ITEM_DISPLAY TEXT_DISPLAY 0.06 0.03 hysteresis stationary frozen anchor 3.2m re-anchor 15 deg idle gaze" },
  { file: "pets.html", secId: "sec-domains", page: "Pets", title: "SkillDomain & Weighted Skill XP Mapping", audience: "player", text: "SkillDomain MELEE RANGED DEFENSE MINING EXCAVATION HARVEST FORAGING FISHING ARCANE ALCHEMY MOBILITY UTILITY axes 50/50 golemancy pickaxe exclude-scroll-bonus-xp" },
  { file: "pets.html", secId: "sec-pet-catalog", page: "Pets", title: "All 15 Registered Companions", audience: "player", text: "spark striker ember mend salvo bastion zephyr terra verdant glacies pyre umbra solis astral crimson" },

  { file: "cosmetics.html", secId: "sec-profile", page: "Cosmetics", title: "Profile GUI Layout (Slot 11 Keystone, Slot 51 Tooltip)", audience: "player", text: "/profile drawers helmet chest waist boots main hand offhand slot 11 keystone 20 amulet 29 cloak 38 gloves 51 tooltipToggle" },
  { file: "cosmetics.html", secId: "sec-engines", page: "Cosmetics", title: "Cosmetic Engine, Wardrobe & /glow", audience: "player", text: "Blockbench bbmodel left_ right_ wing_left_ footsteps sword slash crit sweep wardrobe studio v2 glow" },

  { file: "tpeffects.html", secId: "sec-tp-arch", page: "Teleport Effects", title: "TpEffects Storage & Triggers", audience: "player", text: "tpeffects folder uncommon rare epic legendary mastery YAML PDC selected_tpeffect muted_tpeffect LuckPerms sync" },
  { file: "tpeffects.html", secId: "sec-tp-tiers", page: "Teleport Effects", title: "35 Teleport Effects & 1:1 Pixel-Replica Cinematics", audience: "player", text: "obsidian_dissolution umbral_dissolution chrono_singularity shadow_drift skyline_switch MythicMobs KFX" },
  { file: "tpeffects.html", secId: "sec-tp-ux", page: "Teleport Effects", title: "Wardrobe Preview UX & Blackout", audience: "player", text: "Hover click preview equip dragoncote_tp TextDisplay camera rayTraceBlocks" },

  { file: "emperor.html", secId: "sec-emp-java", page: "Emperor", title: "Emperor Java Engine & Scaling Simulator", audience: "player", text: "EmperorBossManager helly lowercity 80m radius NOTCHED_20 angerScale +0.33 per extra player heal popups" },
  { file: "emperor.html", secId: "sec-emp-phases", page: "Emperor", title: "The 5 Emperor Boss Phases", audience: "player", text: "Phase 1-5 Fallen Defender Defendress Fallen Mauler Sovereign Wrath Rebirth 999 HP" },
  { file: "emperor.html", secId: "sec-emp-voidholders", page: "Emperor", title: "Void Holders & Mace/Spear Counters", audience: "player", text: "Void Holder 5 hit sever CRIMSON_MINION chrono_slip stasis_core slayer scrolls" },

  { file: "world-events.html", secId: "sec-events", page: "World Events & NPCs", title: "3-Realm World Cycle & Mini-Events", audience: "player", text: "Sanctuary Dawn spawn_main Void Convergence spawn_bw Glacial Frost Grand Prix spawn_snow KOTH adapt_exp_surge iceboat_rally" },
  { file: "world-events.html", secId: "sec-dcnpc", page: "World Events & NPCs", title: "DCNPC Pipeline, Warps, Waypoints & RNG", audience: "player", text: "43 steps canon live /warp /waypoint /lore /timeline /rng /pity" },

  { file: "commands.html", secId: "sec-cmd-list", page: "Commands", title: "Full Commands & Permissions Reference", audience: "player", text: "dce keystone tooltip inspect pet emperor cosmetics wardrobe tpeffect glow dcnpc warp waypoint lore timeline rng pity" },
  { file: "placeholders.html", secId: "sec-ph-list", page: "Placeholders", title: "PlaceholderAPI & TAB Identifiers", audience: "player", text: "%dragoncote_% tesseract microcharges heat stability overloaded tooltip_mode stat_ pet_ emperor_ active_events_ tpeffect" },

  { file: "admin.html", secId: "sec-admin-db", page: "Staff & Admin", title: "SQLite / MySQL Schema v2 & Tables", audience: "admin", text: "dce_keystones micro overloaded updated_at dce_meta schema_version dce_player_settings tooltip_mode dce_cube_drops dce_pets_player dce_item_tracking" },
  { file: "admin.html", secId: "sec-admin-pdc", page: "Staff & Admin", title: "PDC Keys, Config & Packet Threading", audience: "admin", text: "dccosmetics socket_keystone_slots ks_converted dce_gui_op StatCombatEngine ItemStatEditorMenu ProtocolLib SET_SLOT WINDOW_ITEMS" },
  { file: "admin.html", secId: "sec-admin-tools", page: "Staff & Admin", title: "Staff Debug Commands & Internal Paths", audience: "admin", text: "/dce editor /dce tester /keystone debugspend debugtp clearheat charge heat microcharge tier /cosmetics debughit debugnode debugbb" }
];
