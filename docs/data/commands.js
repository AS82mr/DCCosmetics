window.WIKI_DATA = window.WIKI_DATA || {};

window.WIKI_DATA.COMMANDS_REGISTRY = [
  // Core
  { cmd: "/dce status", aliases: ["/dcessentials", "/dcess"], perm: "Default / dcessentials.admin", module: "Core", audience: "player", desc: "Prints loaded DCEssentials modules and system engine status." },
  { cmd: "/dce reload", aliases: ["/dcessentials reload"], perm: "dcessentials.admin", module: "Core", audience: "admin", desc: "Hot-reloads main configs, ItemModule, TeleportationManager, RngManager, EventManager, and PetManager." },

  // Items, Stats & Tooltips
  { cmd: "/tooltip [compact|expanded|toggle]", aliases: ["/tooltips", "/loreview"], perm: "Default", module: "Items & Stats", audience: "player", desc: "Toggles your persistent item tooltip mode between COMPACT and EXPANDED (detailed stat briefs & socketed scroll effects)." },
  { cmd: "/dce inspect", aliases: ["/dcessentials inspect"], perm: "Default", module: "Items & Stats", audience: "player", desc: "Opens a read-only inspection GUI showing the full EXPANDED breakdown of your held DCE item." },
  { cmd: "/dce give <player> <itemOrScrollId> [amount]", aliases: ["/dcessentials give"], perm: "dcessentials.admin", module: "Items & Stats", audience: "admin", desc: "Gives a registered DCEItem, Keystone, Tesseract Cube, or Scroll with full PDC identity." },
  { cmd: "/dce update <itemId>", aliases: ["/dcessentials update"], perm: "dcessentials.admin", module: "Items & Stats", audience: "admin", desc: "Bumps item template version and live-syncs online player inventories while preserving sockets, dyes, and UUIDs." },
  { cmd: "/dce items [scroll give <player> <scrollId> [amount]]", aliases: ["/dce item", "/dce itemstat"], perm: "dcessentials.admin", module: "Items & Stats", audience: "admin", desc: "Opens the DCEItems browser GUI or issues a physical scroll item directly." },
  { cmd: "/dce editor [itemId]", aliases: ["/dcessentials editor"], perm: "dcessentials.admin", module: "Items & Stats", audience: "admin", desc: "Opens ItemEditorGUI with Manage Stats (ItemStatEditorMenu across Offense/Defense/Utility tabs)." },
  { cmd: "/dce import", aliases: ["/dcessentials import"], perm: "dcessentials.admin", module: "Items & Stats", audience: "admin", desc: "Opens the Mass Item Importer GUI to convert held/inventory items into DCEItems YAML files." },
  { cmd: "/dce unbind", aliases: ["/dce strip"], perm: "dcessentials.admin", module: "Items & Stats", audience: "admin", desc: "Strips DCE PDC identity tags from the held item cleanly." },
  { cmd: "/dce tester <spawn|clear|remove>", aliases: ["/dce dummy"], perm: "dcessentials.admin", module: "Items & Stats", audience: "admin", desc: "Spawns an indestructible Netherite Stat Tester ArmorStand routed through StatCombatEngine for live telemetry." },

  // Keystones & Teleportation
  { cmd: "/keystone [gui|open|matrix]", aliases: ["/ks", "/tesseract", "/dce gui"], perm: "Default", module: "Keystones & Events", audience: "player", desc: "Opens the native 54-slot Keystone Matrix GUI (Energy bar, Microcharge buffer, Heat gauge, Cube deposit/withdraw, Scroll sockets)." },
  { cmd: "/keystone status", aliases: ["/ks status"], perm: "Default", module: "Keystones & Events", audience: "player", desc: "Prints your Keystone tier, usable/parked Energy, Microcharge buffer (0-1,000 mC), Heat (0-100), and Overload status." },
  { cmd: "/keystone clearheat [player]", aliases: ["/ks resetheat"], perm: "dragoncote.admin", module: "Keystones & Events", audience: "admin", desc: "Admin-only: purges a player's Timeline Heat back to 0/100 and clears Overload." },
  { cmd: "/keystone heat <player> <delta>", aliases: ["/ks heat"], perm: "dragoncote.admin", module: "Keystones & Events", audience: "admin", desc: "Admin-only: adds or subtracts Timeline Heat on a player (used by Chrono Drone coolant items)." },
  { cmd: "/keystone microcharge <player> <delta>", aliases: ["/ks micro"], perm: "dragoncote.admin", module: "Keystones & Events", audience: "admin", desc: "Admin-only: refunds or spends Microcharges directly against a player's buffer." },
  { cmd: "/keystone charge <player> <amount>", aliases: ["/ks addenergy"], perm: "dragoncote.admin", module: "Keystones & Events", audience: "admin", desc: "Admin-only: adds whole Tesseract Energy charges to a player's Keystone." },
  { cmd: "/keystone tier <player> <NONE|RARE|EPIC|LEGENDARY>", aliases: ["/ks settier"], perm: "dragoncote.admin", module: "Keystones & Events", audience: "admin", desc: "Admin-only: overrides a player's Keystone Tier." },
  { cmd: "/keystone debugspend <WARP|ABILITY> <baseCostMicro>", aliases: [], perm: "dragoncote.admin", module: "Keystones & Events", audience: "admin", desc: "Admin-only: simulates a spend transaction through KeystoneEnergyService and prints cracked charges, shortfall, and Heat." },
  { cmd: "/keystone debugtp", aliases: [], perm: "dragoncote.admin", module: "Keystones & Events", audience: "admin", desc: "Admin-only: toggles PlayerTeleportEvent cause and context debug logging in console." },

  // Pets
  { cmd: "/pet", aliases: ["/pets", "/dcepet", "/dcepets"], perm: "Default", module: "Pets", audience: "player", desc: "Opens your Companions Wardrobe GUI (owned pets, category filter, Auto-Cast, Target Ring, Visibility)." },
  { cmd: "/pet menu", aliases: ["/pet gui", "/pet wardrobe"], perm: "Default", module: "Pets", audience: "player", desc: "Explicitly opens the Companions Wardrobe GUI." },
  { cmd: "/pet summon <petId>", aliases: ["/pet equip <petId>"], perm: "Default", module: "Pets", audience: "player", desc: "Summons an unlocked companion (regular players tab-complete owned companions only)." },
  { cmd: "/pet dismiss", aliases: ["/pet hide"], perm: "Default", module: "Pets", audience: "player", desc: "Dismisses your active companion and invalidates StatAggregator cache." },
  { cmd: "/pet visibility [all|own_only|hidden]", aliases: ["/pet vis"], perm: "Default", module: "Pets", audience: "player", desc: "Cycles or sets ProtocolLib companion visibility while keeping passive stats active." },
  { cmd: "/pet autocast [on|off]", aliases: ["/pet skill [on|off]"], perm: "Default", module: "Pets", audience: "player", desc: "Toggles automatic casting of your active companion's combat ability." },
  { cmd: "/pet tracker [on|off]", aliases: ["/pet ring [on|off]"], perm: "Default", module: "Pets", audience: "player", desc: "Toggles the glowing mob-look target tracker ring under looked-at enemies/allies." },
  { cmd: "/pet ally <player>", aliases: ["/pets ally"], perm: "Default", module: "Pets", audience: "player", desc: "Toggles a player on your Companion Ally Whitelist (immune to AoE skills, receives healing)." },
  { cmd: "/pet give <player> <petId> [level] [rarity]", aliases: ["/pets give"], perm: "dce.pet.admin", module: "Pets", audience: "admin", desc: "Grants a physical right-click claimable Companion Token with custom level and rarity." },
  { cmd: "/pet unlockall <player> [level]", aliases: ["/pets unlockall"], perm: "dce.pet.admin", module: "Pets", audience: "admin", desc: "Unlocks all 15 registered companions directly in the player's database profile." },
  { cmd: "/pet addxp <player> <amount>", aliases: ["/pets addxp"], perm: "dce.pet.admin", module: "Pets", audience: "admin", desc: "Grants XP to the player's summoned companion, scaling levels up to 100." },
  { cmd: "/pet reload", aliases: ["/pets reload"], perm: "dce.pet.admin", module: "Pets", audience: "admin", desc: "Reloads plugins/DCEssentials/pets_config.yml and pets/*.yml, refreshing active companions." },

  // Emperor Boss
  { cmd: "/dce emperor <scale|reset|testheal|heal|clean|info>", aliases: ["/dce emp", "/dce boss", "/emperortest"], perm: "dcessentials.admin", module: "Emperor", audience: "admin", desc: "Controls Emperor scaling override, BossBar simulation, and floating TextDisplay heal popups." },
  { cmd: "/emperoritems [give <player> <itemOrScrollId> [amount]]", aliases: ["/emperoritem", "/dceitems"], perm: "dcessentials.admin", module: "Emperor", audience: "admin", desc: "Opens Emperor items helper menu or gives Emperor combat scrolls/items (chrono_slip, slayer_*, stasis_core)." },

  // Cosmetics, Wardrobe & TpEffects
  { cmd: "/cosmetics [help|sound]", aliases: ["/dcc", "/dccosmetics"], perm: "Default", module: "Cosmetics & Wardrobe", audience: "player", desc: "Opens the Cosmetics menu, prints help, or toggles local footstep sound effects." },
  { cmd: "/cosmetics reload", aliases: ["/dcc reload"], perm: "dccosmetics.admin", module: "Cosmetics & Wardrobe", audience: "admin", desc: "Hot-reloads templates, cosmetic YAMLs, profile config, and plugin resources." },
  { cmd: "/cosmetics import <file> [head|chest|waist|boots|sword]", aliases: ["/dcc import"], perm: "dccosmetics.admin", module: "Cosmetics & Wardrobe", audience: "admin", desc: "Converts .bbmodel or .json files from plugins/DCEssentials/imports/ into TextDisplay YAML geometry." },
  { cmd: "/cosmetics scroll [give] <player> <cosmeticId> [hexColor]", aliases: ["/dcc scroll"], perm: "dccosmetics.admin", module: "Cosmetics & Wardrobe", audience: "admin", desc: "Issues a drag-and-drop Cosmetic Scroll item to apply 3D models onto armor or swords." },
  { cmd: "/cosmetics sculpt <cosmeticId|editor|toggle|clear>", aliases: ["/dcc sculpt"], perm: "dccosmetics.admin", module: "Cosmetics & Wardrobe", audience: "admin", desc: "Spawns a live dummy mannequin, opens the GUI editor, or removes the dummy." },
  { cmd: "/cosmetics debughit|debugnode|debugbb ...", aliases: ["/dcc debughit"], perm: "dccosmetics.admin", module: "Cosmetics & Wardrobe", audience: "admin", desc: "Developer VFX & Blockbench aspect-ratio calibration diagnostics." },
  { cmd: "/customise", aliases: ["/customize"], perm: "Default", module: "Cosmetics & Wardrobe", audience: "player", desc: "Opens the Customise GUI for the held cosmetic item to dye #FFFFFF channels or detach a scroll." },
  { cmd: "/profile [player]", aliases: ["/prof"], perm: "Default", module: "Cosmetics & Wardrobe", audience: "player", desc: "Opens the 54-slot Profile GUI with sliding cosmetic drawers, live Keystone & Pet summary, and Tooltip toggle." },
  { cmd: "/wardrobe", aliases: [], perm: "Default", module: "Cosmetics & Wardrobe", audience: "player", desc: "Enters the packet-based 3D camera Wardrobe room." },
  { cmd: "/wardrobe <wand|setup|setspawn|setnpc|setexit|setsaferoom|settppreview|admin|reload|refresh>", aliases: [], perm: "dccosmetics.admin", module: "Cosmetics & Wardrobe", audience: "admin", desc: "Configures Wardrobe bounds, anchors, mannequin NPC, camera safe-room, open-sky teleport preview location, and admin GUI." },
  { cmd: "/wardrobe studio <cam|setcam|preview|exitpreview|set|angle|panel|save|reload|apply>", aliases: [], perm: "dccosmetics.admin", module: "Cosmetics & Wardrobe", audience: "admin", desc: "V2 Camera Studio (XYZ/Yaw/Pitch per category) or V1 3D HUD panel offset tuning." },
  { cmd: "/tpeffect <list|set|clear|preview|info|mute|unmute>", aliases: ["/tpeffects", "/tpe"], perm: "Default (dc.tpeffect.<id>)", module: "Cosmetics & Wardrobe", audience: "player", desc: "Lists rarities, previews effects in-world, equips unlocked teleport effects, or mutes personal playback." },
  { cmd: "/tpeffect <test|give|enable|disable|hide|show|reload> ...", aliases: ["/tpeffects", "/tpe"], perm: "dccosmetics.admin", module: "Cosmetics & Wardrobe", audience: "admin", desc: "Admin full-warp cinematic tester, permission/voucher granter, YAML enabled/hidden toggles, and config hot-reload." },
  { cmd: "/glow [color|off] [player]", aliases: [], perm: "glow.<color> / dragoncote.admin", module: "Cosmetics & Wardrobe", audience: "player", desc: "Opens the Glow menu or applies/clears a team-based outline glow effect." },

  // Events, DCNPC, Warps & RNG
  { cmd: "/events [page|schedule|spawn]", aliases: ["/calendar", "/event", "/dcevent", "/schedule", "/dce event"], perm: "Default", module: "Keystones & Events", audience: "player", desc: "Opens the Events Calendar GUI or teleports to the active world cycle hub spawn." },
  { cmd: "/events <list|swap|time|start|stop|reload>", aliases: [], perm: "dcessentials.admin", module: "Keystones & Events", audience: "admin", desc: "Forces dimensional hub swaps (sanctuary_dawn, void_convergence, glacial_grand_prix) or toggles mini-events." },
  { cmd: "/dcnpc [gui|talk|claim|track|waypoint|warp|timeline]", aliases: ["/lorenpc", "/dce_npc"], perm: "Default", module: "DCNPC & Warps", audience: "player", desc: "Quest interaction, 3D HUD tracking, district warps, and /timeline access." },
  { cmd: "/dcnpc <admin|zones|canon|wand|editor|scene|skin|spawn|reload>", aliases: [], perm: "dccosmetics.admin", module: "DCNPC & Warps", audience: "admin", desc: "Admin canon pipeline (canon live|test, Setup/Editor wands, scene placement, skin filter generation)." },
  { cmd: "/warp [zone|alias]", aliases: ["/warps"], perm: "Default", module: "DCNPC & Warps", audience: "player", desc: "Opens the 54-slot Fast Travel District Warp Menu or warps directly to calibrated district anchors." },
  { cmd: "/setwarp <zone|alias>", aliases: [], perm: "dccosmetics.admin", module: "DCNPC & Warps", audience: "admin", desc: "Calibrates a district warp anchor at your current location." },
  { cmd: "/waypoint [toggle|clear|next|npcId]", aliases: ["/wp", "/dce waypoint"], perm: "Default / dragoncote.admin", module: "DCNPC & Warps", audience: "player", desc: "Locks, cycles, toggles, or clears the 3D HUD directional waypoint arrow." },
  { cmd: "/lore [sector]", aliases: ["/dossier", "/personnel"], perm: "Default", module: "DCNPC & Warps", audience: "player", desc: "Opens visual personnel dossiers across Sectors 1-4." },
  { cmd: "/timeline", aliases: ["/storyline", "/questline"], perm: "Default", module: "DCNPC & Warps", audience: "player", desc: "Opens the 25-node storyline quest progression track." },
  { cmd: "/rng [category]", aliases: ["/rngmeter", "/droprates", "/luck"], perm: "Default", module: "DCNPC & Warps", audience: "player", desc: "Opens the RNG Meter hub or category RNG meter menu." },
  { cmd: "/pity [category]", aliases: ["/pitycore", "/milestones"], perm: "Default", module: "DCNPC & Warps", audience: "player", desc: "Opens the Pity Tracker GUI." },
  { cmd: "/dce givexp <player> <amount> [skill]", aliases: ["/dce addxp", "/dce xp"], perm: "dcessentials.admin", module: "DCNPC & Warps", audience: "admin", desc: "Grants Adapt skill XP to a player directly via AdaptHook." },
  { cmd: "/dce selectdrop <player> <category> <dropId>", aliases: [], perm: "dcessentials.admin", module: "DCNPC & Warps", audience: "admin", desc: "Sets a player's active RNG Meter target drop." },
  { cmd: "/rnghook <player> <category> <boostPct> <xpAmount> [combat|casino]", aliases: [], perm: "dcessentials.admin", module: "DCNPC & Warps", audience: "admin", desc: "Script/MythicMobs bridge for triggering RNG/Pity rolls and Adapt XP rewards." },

  // World Tools
  { cmd: "/setupcinematicworld <datapack|create|fix|setbiome> [world] [arg]", aliases: [], perm: "dccosmetics.admin", module: "World Tools", audience: "admin", desc: "Generates monochrome/cinematic datapacks and void-biome worlds for spawn_bw and studio rooms." },
  { cmd: "/draincolors", aliases: [], perm: "dccosmetics.admin", module: "World Tools", audience: "admin", desc: "Converts a WorldEdit selection into monochrome grayscale blocks." },
  { cmd: "/spawnsync [wand|tools|push|commit|status|discard|stagewe|snow]", aliases: ["/sync", "/gitsync"], perm: "dccosmetics.admin", module: "World Tools", audience: "admin", desc: "Synchronizes schematic edits across the tri-world spawn dimensions." }
];
