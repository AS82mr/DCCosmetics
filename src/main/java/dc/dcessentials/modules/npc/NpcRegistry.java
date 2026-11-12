package dc.dcessentials.modules.npc;

import dc.dcessentials.modules.npc.model.NpcCharacter;
import dc.dcessentials.modules.npc.model.NpcStage;
import dc.dcessentials.modules.npc.util.DragoncoteColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;

import java.util.*;

/**
 * Canon registry of all Dragoncote NPCs, timelines, quest chains, ambient lore, and wand placement order.
 */
public class NpcRegistry {

    private final Map<String, NpcCharacter> characters = new LinkedHashMap<>();
    private final List<String> timelineOrder = new ArrayList<>();
    private final List<String> wandOrder = new ArrayList<>();

    public void initializeDefaults() {
        characters.clear();
        timelineOrder.clear();
        wandOrder.clear();

        // ==============================================================
        // SECTOR 1: SANCTUARY REBELLION HUB (MAP DISTRICTS & CANON CORE)
        // ==============================================================

        // 1. Central Command / Spawn
        registerChar("corvus", "Corvus", "&#38BDF8Corvus", "#38BDF8", "Knight", 1, "Resistance", "Vanguard Leader",
                "I break limits, Agent. Not laws.", Material.BEACON, "Sanctuary Spawn Hub - Central Plaza Drop", "command");
        regAmbient("corvus",
                "Look around you, Agent. The Empire thinks they own every timeline. We prove them wrong daily.",
                "Keep your keystone calibrated. A shattered matrix leaves you trapped between ticks.",
                "If you see Inquisitor Malakor at the lower gate, do not engage alone.",
                "The Syndicate isn't just an army. It's the only firewall keeping reality from collapsing.");

        // 2. The Tech Forge (Kristoph + Apprentices)
        registerChar("kristoph", "Kristoph", "&#0098FFKristoph", "#0098FF", "Notch", 1, "Resistance", "Matrix Engineer",
                "Equip your Keystone and watch your heat.", Material.AMETHYST_SHARD, "Sanctuary Forge House - Master Matrix Workstation", "forge");
        registerService("kristoph", "warp forge", "Forge");
        registerCompanionSceneMobs("kristoph", "scene_blacksmith_1", "scene_blacksmith_2", "scene_blacksmith_3", "scene_blacksmith_4");
        regAmbient("kristoph",
                "Hold still while I align your phase harmonics. Teleporting without insulation will incinerate your soul.",
                "The LowerCity reactor bleeds raw chrono-flux into the atmosphere. You can smell the ozone.",
                "Bring me void matter from the deep chasm. I can forge matrices that ignore planar friction.",
                "You want zero heat? Keep your Tesseract battery charged, or jump into cryo basins.");

        // 3. Spacetime Archives
        registerChar("lyra", "Lyra", "&#60A5FALyra", "#60A5FA", "Alex", 1, "Resistance", "Spacetime Archivist",
                "Water and powder snow purge timeline heat.", Material.CLOCK, "Spacetime Archives - Ground Floor Tomes", "archives");
        registerService("lyra", "warp archives", "Archives");
        regAmbient("lyra",
                "Every time you leap through space, your molecular anchor loosens.",
                "Powder snow cools the matrix instantaneously. Remember that if the Inquisitors corner you.",
                "Our history books don't match the Empire's records. Someone erased forty years of war.",
                "The timeline isn't a river. It's an intricate mechanism.");

        // 4. Glitched Corridor
        registerChar("echo", "Echo", "&#A855F7Echo", "#A855F7", "MHF_Enderman", 1, "Resistance", "Glitched Courier",
                "01000101... The Emperor has Corvus's face...", Material.ECHO_SHARD, "Sanctuary Central Plaza - Shaded Glitched Alleyway", "command");
        regAmbient("echo",
                "I saw... thirty-seven versions of myself die on the LowerCity bridge yesterday...",
                "Static in the signal... Can you hear the Emperor breathing behind the frequency?",
                "Don't touch the void tears near the docks! They remember what you were before you were born.",
                "Error 0x404: Timeline anchor not verified...");

        // 5. North Gate Recon
        registerChar("vane", "Vane", "&#38BDF8Agent Vane", "#38BDF8", "Steve", 1, "Resistance", "Recon Scout",
                "Imperial patrols are doubling along the bridge.", Material.COMPASS, "Sanctuary Central Plaza - North Gate Overlook", "command");
        regAmbient("vane",
                "Imperial sentinel drones spotted near quadrant four.",
                "Keep your head down out there.",
                "The perimeter shields are holding, barely.",
                "Report any spatial anomalies directly to command.");

        // 6. Armory
        registerChar("jax", "Jax", "&#0284C7Jax", "#0284C7", "jeb_", 1, "Resistance", "Armory Master",
                "Reinforced alloys to pierce void shielding.", Material.NETHERITE_SWORD, "Sanctuary Markets - Legal Armory Stall", "market");
        registerService("jax", "shop", "Armory");

        // 7. Rift Cartographer
        registerChar("sylas", "Sylas", "&#818CF8Sylas", "#818CF8", "Dinnerbone", 1, "Resistance", "Rift Cartographer",
                "Stabilized warp anchors across the stars.", Material.RECOVERY_COMPASS, "Terraced Farms - Supply Depot Outpost", "farms");
        registerService("sylas", "warps", "Warps");

        // 8. Supply Officer
        registerChar("ronald", "Ronald", "&#F97316Ronald", "#F97316", "Grumm", 1, "Resistance", "Supply Officer",
                "Sanctuary provisions for long timeline jumps.", Material.CHEST, "Sanctuary Central Plaza - High Tribunal Dais", "command");
        registerService("ronald", "rtp", "RTP");

        // 9. Medical Bay & Observatory
        registerChar("hesper", "Dr. Hesper", "&#EC4899Dr. Hesper", "#EC4899", "MHF_Villager", 1, "Resistance", "Field Medic",
                "Cellular drift within safe tolerances.", Material.GLISTERING_MELON_SLICE, "Celestial Observatory - Medical & Astrolabe Chamber", "observatory");
        registerService("hesper", "warp observatory", "Observatory");

        // 10. Perimeter Tower
        registerChar("balthazar", "Balthazar", "&#94A3B8Balthazar", "#94A3B8", "MHF_Golem", 1, "Resistance", "Timeline Veteran",
                "Courage alone won't break an imperial loop.", Material.SHIELD, "Sanctuary Central Plaza - Perimeter Gate Tower", "command");

        // 11. Auction House
        registerChar("valen", "Valen", "&#F59E0BMaster Auctioneer Valen", "#F59E0B", "MHF_Villager", 1, "Resistance", "Master Auctioneer",
                "Every relic has a buyer, Agent. Even forbidden imperial schematics.", Material.GOLD_INGOT, "The Grand Auction House - Trading Podium", "auction");
        registerService("valen", "ah", "Auction");
        regAmbient("valen",
                "Bid fast, trade smart. The zAuctionHouse network never sleeps.",
                "Rare keystone blueprints fetch millions on the open floor.",
                "Legitimate trade funds the rebellion better than ten strikes.");

        // 12. Bank House (Guilds Plugin Integration)
        registerChar("aurelius", "Aurelius", "&#EAB308Vault Overseer Aurelius", "#EAB308", "MHF_Golem", 1, "Resistance", "Guild Treasurer",
                "Guild treasuries power our war against the Empire. Store your gold safely.", Material.GOLD_BLOCK, "Bank House - Central Guild Treasury Vault", "bank");
        registerService("aurelius", "guild bank", "Guild Vault");
        regAmbient("aurelius",
                "Deposit your syndicate dividends into the Guild Vault via /guild bank.",
                "Even the Empire's treasury cannot breach our encrypted vaults.",
                "Wealth is ammunition in a protracted multi-planar siege.");

        // 13. Blacksmith Apprentice
        registerChar("marion", "Marion", "&#F97316Marion", "#F97316", "MHF_Alex", 1, "Resistance", "Blacksmith Apprentice",
                "I keep the fires hot while Kristoph aligns the quantum matrices.", Material.ANVIL, "Sanctuary Forge House - Apprentice Anvil Station", "forge");
        registerService("marion", "warp forge", "Forge");
        regAmbient("marion",
                "Careful near the quench basin. The steam will blister right through kevlar.",
                "Kristoph taught me how to temper chrono-alloys. They bend time before they break.",
                "Always check your heat meter before jumping dimensions!");

        // 14. Colosseum (Trial Master)
        registerChar("valerius", "Valerius", "&#EF4444Combat Master Valerius", "#EF4444", "MHF_Gladiator", 1, "Resistance", "Trial Master",
                "Step onto the frost arena if you dare test your blade against Syndicate champions.", Material.DIAMOND_SWORD, "Snow Colosseum - Glacial Duel Grounds", "colosseum");
        registerService("valerius", "battlepass", "BattlePass");
        regAmbient("valerius",
                "The cold numbs your fingers, but sharpens your reflexes.",
                "Prove your worth in duels, and the Syndicate BattlePass will reward you handsomely.",
                "The Empire's sentinels bleed just like anyone else.");

        // 15. Fisherman House
        registerChar("finn", "Finn", "&#06B6D4Angler Finn", "#06B6D4", "MHF_Fisherman", 1, "Resistance", "Swamp Angler",
                "The marshes whisper of deep currents running under the void bedrock.", Material.FISHING_ROD, "Fisherman House - Swamp Bayou Docks", "fisherman");
        registerService("finn", "warp fish", "Bayou Docks");
        registerCompanionSceneMobs("finn", "scene_fisherman_standing", "scene_fisherman_sitting", "scene_fisherman_stick", "scene_fisherman_boat");

        // 16. Botanical & Wardrobe Salon
        registerChar("vivienne", "Vivienne", "&#EC4899High Stylist Vivienne", "#EC4899", "MHF_Alex", 1, "Resistance", "Wardrobe Specialist",
                "War is no excuse for lack of elegance. Discover your signature look.", Material.DIAMOND, "Sanctuary Markets - Botanical Flower Stalls", "market");
        registerService("vivienne", "wardrobe", "Wardrobe");
        registerCompanionSceneMobs("vivienne", "scene_market_stand", "scene_market_merchant_interactive_1", "scene_market_appleseller", "scene_market_bookseller");
        regAmbient("vivienne",
                "Use /wardrobe to step into the 3D dressing chamber.",
                "Pair your glowing scoreboard outline with custom teleportation particles from /tpeffects.",
                "A true resistance operative strikes terror with impeccable style.");

        // 17. Tech Spire & Academy
        registerChar("orion", "Orion", "&#8B5CF6Chronomancer Orion", "#8B5CF6", "MHF_Mage", 1, "Resistance", "Academy Chronomancer",
                "Probability is merely physics waiting to be bent. Master your Adapt skills.", Material.BEACON, "Celestial Observatory - Chronomancer Spire", "observatory");
        registerService("orion", "rng", "RNG");
        regAmbient("orion",
                "Explore your drop probability curves with /rng and track milestones via /pity.",
                "Leveling your Adapt mastery unlocks latent dimensional synergies.",
                "Luck is the weapon of the amateur. Determinism belongs to the Syndicate.");

        // 18. Mines Outpost
        registerChar("thrum", "Thrum", "&#78716CForeman Thrum", "#78716C", "MHF_Miner", 1, "Resistance", "Mining Foreman",
                "Dig deep, dig true. The bedrock hides ancient Imperial conduit lines.", Material.NETHERITE_PICKAXE, "Spruce Foothills - Deep Mining Quarry", "mines");
        registerService("thrum", "warp mines", "Mines");

        // 19. Bakery House
        registerChar("clara", "Clara", "&#F97316Baker Clara", "#F97316", "MHF_Farmer", 1, "Resistance", "Head Baker",
                "Warm bread and dense caloric rations keep our fighters standing.", Material.BREAD, "Bakery House - Hearthstone Oven", "bakery");
        registerService("clara", "warp bakery", "Bakery");
        registerCompanionSceneMobs("clara", "scene_market_cookieseller");
        regAmbient("clara",
                "Fresh sourdough, baked with solar wheat from the valley farms.",
                "Unlike the poisonous rations sold in the LowerCity slums, our bread heals.",
                "Take a honey roll before you jump into the cold dimensions.");

        // 20. Ancient Ruins Outpost
        registerChar("elden", "Elden", "&#A855F7Archaeologist Elden", "#A855F7", "MHF_Scholar", 1, "Resistance", "Abyss Archaeologist",
                "The dead ruins echo with frequencies from before the Emperor was crowned.", Material.WRITABLE_BOOK, "Ancient Dead Ruins - Forgotten Obelisk Site", "ruins");
        registerService("elden", "warp ruins", "Ruins");

        // ==============================================================
        // SECTOR 2: LOWERCITY (EMPIRE OCCUPATION ZONE)
        // ==============================================================
        registerChar("prisoner404", "Prisoner 404", "&#A1A1AAPrisoner 404", "#A1A1AA", "MHF_Skeleton", 2, "Exiled", "Slum Fugitive",
                "Look beneath the sewer grate near Section B...", Material.IRON_BARS, "LowerCity Slums - Sewer Grate Drainage B", "slums");
        registerChar("nadia", "Nadia", "&#06B6D4Defector Nadia", "#06B6D4", "MHF_Alex", 2, "Resistance", "Undercover Spy",
                "The Emperor isn't destroying Corvus—he's testing him.", Material.ENDER_EYE, "LowerCity Slums - Administrative Outpost", "slums");
        registerChar("valerio", "Valerio", "&#F59E0B'Lucky' Valerio", "#F59E0B", "MHF_Present1", 3, "Underworld", "The Pit Boss",
                "Forget ideals and rules. Here, coin talks, darling.", Material.GOLD_BLOCK, "The Black Drift Casino - Central Floor", "casino");
        registerService("valerio", "casino", "Casino");
        registerChar("shade", "Shade", "&#8B5CF6Shade", "#8B5CF6", "MHF_Witch", 3, "Underworld", "Energy Smuggler",
                "Unmetered Tesseract cubes. No registries, no questions.", Material.NETHER_STAR, "Underworld Black Market - Shaded Alcove", "blackmarket");
        registerChar("vector", "Vector", "&#0EA5E9Vector", "#0EA5E9", "MHF_Guardian", 4, "Resistance", "EMP Scrapper",
                "EMP charges to drop the Emperor's laser grids clean.", Material.REDSTONE_TORCH, "Underworld Chop Shop - EMP Workshop", "blackmarket");
        registerChar("emperor", "Emperor", "&#EF4444The Emperor", "#EF4444", "MHF_Herobrine", 2, "Empire", "Masked Sovereign",
                "I am the one who saw what happens when you go too far.", Material.NETHERITE_HELMET, "Imperial Spire - Throne Room Gateway", "spire");
        registerChar("malakor", "Malakor", "&#B91C1CInq. Malakor", "#B91C1C", "MHF_WSkeleton", 2, "Empire", "Chief Gatekeeper",
                "Only LEGENDARY matrices can withstand the chamber.", Material.REDSTONE, "Imperial Citadel - High Bastion Gates", "citadel");
        registerChar("kael", "Kael", "&#991B1BCaptain Kael", "#991B1B", "MHF_Blaze", 2, "Empire", "Purge Commander",
                "Let your heat hit 100, and our hounds will hunt you.", Material.IRON_SWORD, "Imperial Citadel - Interrogation Wing", "citadel");
        registerChar("thorne", "Thorne", "&#78716COverseer Thorne", "#78716C", "MHF_PigZombie", 2, "Empire", "Reactor Overseer",
                "A single fluctuation here detonates three dimensions.", Material.BLAST_FURNACE, "Imperial Citadel - Reactor Manifold", "citadel");
        registerChar("vael", "Vael", "&#64748BArchivist Vael", "#64748B", "MHF_Evoker", 2, "Empire", "Imperial Archivist",
                "In every timeline Corvus won, reality burned away.", Material.BOOK, "Imperial Citadel - Spire Archive Wing", "citadel");
        registerChar("ignis", "Ignis", "&#EA580CMaster Ignis", "#EA580C", "MHF_LavaSlime", 2, "Neutral", "Matrix Smith",
                "Collapsed void matter gives Zero-Heat home transit.", Material.ANVIL, "Imperial Citadel - Thermal Furnace", "citadel");
        registerChar("theta", "Theta", "&#52525BUnit Theta", "#52525B", "MHF_IronGolem", 2, "Empire", "Automaton Enforcer",
                "SCANNING OPERATIVE... PASS ID VALID.", Material.IRON_BLOCK, "Imperial Citadel - Security Gate Alpha", "citadel");
        registerChar("gideon", "Gideon", "&#D97706Gideon", "#D97706", "MHF_CaveSpider", 2, "Neutral", "Abyss Excavator",
                "The chasm below has tears into raw eternity.", Material.NETHERITE_PICKAXE, "LowerCity Slums - Deep Chasm Elevator", "slums");

        // ==============================================================
        // SECTOR 3: UNDERWORLD & SECTOR 4: THE BREACH
        // ==============================================================
        registerChar("sorsha", "Sorsha", "&#D946EFMadam Sorsha", "#D946EF", "MHF_Ocelot", 3, "Underworld", "Relic Broker",
                "Vanity and cloaks smuggled directly from the Upper Spires.", Material.ELYTRA, "LowerCity Slums - The Rusty Pipe Tavern", "slums");
        registerService("sorsha", "cosmetics", "Cosmetics");
        registerChar("garrick", "Garrick", "&#DC2626Garrick", "#DC2626", "MHF_Illager", 3, "Underworld", "Loan Shark",
                "Debts in the Drift are paid in coin... or timeline years.", Material.GOLD_INGOT, "The Black Drift Casino - Loan Cage", "casino");
        registerChar("twitch", "Twitch", "&#06B6D4Twitch", "#06B6D4", "MHF_Creeper", 3, "Underworld", "Glitched Gambler",
                "I rolled the dice in 2042... left my arm in another dimension!", Material.ENDER_PEARL, "The Black Drift Casino - Roulette Tables", "casino");
        registerChar("brutus", "Brutus", "&#71717ABouncer Brutus", "#71717A", "MHF_Golem", 3, "Underworld", "The Muscle",
                "You break the tables, I break you.", Material.IRON_CHESTPLATE, "The Black Drift Casino - Main Portal Gate", "casino");
        registerChar("finch", "Finch", "&#10B981Finch", "#10B981", "MHF_Zombie", 3, "Underworld", "Card Dealer",
                "Cards on the table. Red or black? The wheel doesn't lie.", Material.EMERALD, "LowerCity Slums - Backroom Card Table", "slums");
        registerChar("rats", "Rats", "&#78716CRats", "#78716C", "MHF_Spider", 3, "Underworld", "Tunnel Guide",
                "The drainage tunnels run under the whole Imperial base.", Material.TRIPWIRE_HOOK, "LowerCity Slums - Sewer Drainage Junction", "slums");
        registerChar("roxy", "Roxy", "&#EC4899Roxy", "#EC4899", "MHF_Ghast", 3, "Underworld", "Neon Songstress",
                "Singing songs of timelines that never existed.", Material.JUKEBOX, "Underworld Black Market - Neon Bar", "blackmarket");
        registerChar("slick", "Slick", "&#FBBF24Slick", "#FBBF24", "MHF_Endermite", 3, "Underworld", "The Forger",
                "Counterfeit clearance badges and spoofed transponders.", Material.SUNFLOWER, "The Black Drift Casino - Document Forger Booth", "casino");
        registerChar("nyx", "Nyx", "&#8B5CF6Nyx", "#8B5CF6", "MHF_Slime", 4, "Resistance", "Dead-Drop Specialist",
                "Smuggled blueprints from the Emperor's private sanctum.", Material.WRITABLE_BOOK, "Underworld Black Market - Slums Dead-Drop", "blackmarket");
        registerChar("krux", "Krux", "&#EF4444Krux", "#EF4444", "MHF_Wither", 4, "Underworld", "The Under-Fence",
                "The Inquisitors don't patrol the dead zone. What are you buying?", Material.CRYING_OBSIDIAN, "Underworld Black Market - Slums Contraband Exchange", "blackmarket");

        // ==============================================================
        // SETUP WAND SEQUENTIAL ORDER (1 to 43)
        // ==============================================================
        wandOrder.addAll(Arrays.asList(
            "corvus", "kristoph", "lyra", "echo", "vane", "jax", "sylas", "ronald", "hesper", "balthazar",
            "valen", "aurelius", "marion", "valerius", "finn", "vivienne", "orion", "thrum", "clara", "elden",
            "prisoner404", "nadia", "valerio", "shade", "vector", "emperor", "malakor", "kael", "thorne", "vael",
            "ignis", "theta", "gideon", "sorsha", "garrick", "twitch", "brutus", "finch", "rats", "roxy",
            "slick", "nyx", "krux"
        ));

        // ==============================================================
        // QUEST TIMELINE CHAIN (CANON CHAPTER PROGRESSION)
        // ==============================================================
        timelineOrder.add("corvus:1");
        timelineOrder.add("kristoph:1");
        timelineOrder.add("lyra:1");
        timelineOrder.add("corvus:2");
        timelineOrder.add("echo:1");
        timelineOrder.add("kristoph:2");
        timelineOrder.add("prisoner404:1");
        timelineOrder.add("nadia:1");
        timelineOrder.add("valerio:1");
        timelineOrder.add("shade:1");
        timelineOrder.add("kristoph:3");
        timelineOrder.add("vector:1");
        timelineOrder.add("nyx:1");
        timelineOrder.add("krux:1");
        timelineOrder.add("corvus:3");
        timelineOrder.add("kristoph:4");
        timelineOrder.add("malakor:1");
        timelineOrder.add("kael:1");
        timelineOrder.add("thorne:1");
        timelineOrder.add("vael:1");
        timelineOrder.add("corvus:4");
        timelineOrder.add("kristoph:5");
        timelineOrder.add("vector:2");
        timelineOrder.add("emperor:1");
        timelineOrder.add("emperor:5");

        // Key Stages
        regStage("corvus", 1, "The Rift Oath", "None", "Calibrate Keystone matrix with Kristoph at Tech Lab.",
                "Eyes up, Agent. You survived the spatial jump. Down in the LowerCity, the Emperor cages reality with 'laws'—I break limits. Take your directive and let's calibrate your matrix with Kristoph.",
                "Good work. Our foundation is laid. Kristoph has your Keystone ready.", "kristoph", 1000, "Corvus Directive", "Operational parchment granting 3-day timeline shielding.", Material.PAPER, "ei give %%player%% corvus_directive 1", false);

        regStage("kristoph", 1, "Keystone Attunement", "corvus:1", "Equip your Keystone and attune spatial matrix.",
                "Welcome to the Tech Lab. Spacetime friction boils the blood of unprepared travelers. Take these Tesseract cubes to charge your matrix.",
                "Matrix synchronized at nominal frequencies. Report to Lyra at the Archives to study cooling.", "lyra", 1200, "Tesseract Charge", "Direct injection of +25 Tesseract Energy into your Keystone.", Material.AMETHYST_SHARD, "none", false);

        regStage("lyra", 1, "Spacetime Thermodynamics", "kristoph:1", "Learn heat purging protocols in water and powder snow.",
                "Greetings, traveler. In our archives, thousands of operatives dissolved because they ignored timeline heat. Remember: submersion in water or powder snow resets your thermal build-up.",
                "Your understanding of timeline cooling is solid. Return to Corvus for armor plating.", "corvus", 1000, "Timeline Compass", "Ancient brass chronometer calibrated to Sanctuary timelines.", Material.CLOCK, "none", false);

        regStage("corvus", 2, "Vanguard Plating", "lyra:1", "Reinforce armor with Vanguard titanium alloy.",
                "Kristoph calibrated your matrix? Excellent. You cannot face Imperial sentries in civilian clothes. Take this reinforced alloy and fit your armor.",
                "Armor fitted and secured. Now go investigate the glitched transmission in the side corridor.", "echo", 1500, "Reinforced Alloy", "Ten high-tensile alloy plates to reinforce combat armor.", Material.IRON_INGOT, "none", false);

        regStage("echo", 1, "Glitched Transmission", "corvus:2", "Decode glitched transmission in the shaded alleyway.",
                "01000101... You hear the hum? The Emperor's mask cracked during the last void rift... Underneath, his face is Corvus! An older, scarred Corvus! Go back to Kristoph!",
                "Static fades... but the truth remains carved in the copper cables.", "kristoph", 2000, "Echo Shards", "Pair of resonant echo shards vibrating at Spire frequencies.", Material.ECHO_SHARD, "none", false);

        regStage("kristoph", 2, "Cryo-Immersion Science", "echo:1", "Receive Liquid Cryo-Cell for rapid thermal cooling.",
                "Echo reported timeline turbulence near the archives? You need thermodynamic insulation. Take this cooling cell—deploy it in water to dump heat instantly.",
                "Thermal dampeners online. Prisoner 404 in the LowerCity holding cells needs contact.", "prisoner404", 1800, "Cooling Cell", "Submersible cryogenic cell that purges 50 Heat Points.", Material.WATER_BUCKET, "none", false);

        regStage("prisoner404", 1, "Subterranean Breach", "kristoph:2", "Locate secret sewer grate into LowerCity Section B.",
                "Pssst! Down here behind the laser grate! The guards never check the sewage intake. Defector Nadia has high-command clearance codes.",
                "Keep low and move fast before the sentinels reset the patrol route.", "nadia", 2500, "Infiltration Key", "Corroded bronze key opening LowerCity maintenance vents.", Material.TRIPWIRE_HOOK, "none", false);

        regStage("nadia", 1, "Undercover Telemetry", "prisoner404:1", "Deliver high-command Imperial telemetry to Valerio.",
                "Act natural! Sentinel Unit Theta is scanning the corridor. The Emperor is intentionally letting the vanguard advance—he is testing Corvus! Take this telemetry to Valerio at the Casino.",
                "Telemetry safely dispatched. The Drift Boss will know what to do.", "valerio", 3000, "Telemetry Chip", "Encrypted Imperial high-command data chip.", Material.ENDER_EYE, "none", false);

        regStage("valerio", 1, "The Drift Stake", "nadia:1", "Acquire black market energy cells from Shade.",
                "Welcome to the Black Drift, darling! The Empire runs on laws, but reality runs on coin. If you want unmetered power, go speak to Shade in the VIP lounge.",
                "The dice are cast. Shade has the raw goods waiting.", "shade", 3500, "Drift Credit Chip", "High-roller chip redeemable at the Black Drift tables.", Material.GOLD_BLOCK, "none", false);

        regStage("shade", 1, "Unmetered Fuel", "valerio:1", "Smuggle raw Tesseract cubes to Kristoph for drone assembly.",
                "Keep your voice down. The Spire monitors every joule of energy. These unmetered cubes bypassed their meters clean. Take them to Kristoph for your drone.",
                "Unmetered fuel delivered. Kristoph is waiting at the Tech Lab.", "kristoph", 4000, "Energy Blueprint", "Schematic for synthesizing unregistered Tesseract fuel.", Material.NETHER_STAR, "none", false);

        regStage("kristoph", 3, "Drone Synthesis", "shade:1", "Synthesize Vanguard Recon Drone chassis at the Forge.",
                "The unmetered cubes are stabilized. I can construct your recon drone. Hand over the components to Vector.",
                "Recon drone operational. Vector in the Casino lounge has the encrypted transponder.", "vector", 4200, "Quantum Drone Chassis", "Lightweight recon drone housing.", Material.ARMOR_STAND, "none", false);

        regStage("vector", 1, "Encrypted Transponder", "kristoph:3", "Acquire spoofed transponder chip from Vector.",
                "Drone looks slick. But without Imperial transponder signatures, the Citadel cannons will vaporize it in 2 seconds. Take this chip to Nyx at the dead-drop.",
                "Transponder encrypted and spoofed. Head down into the Black Market to rendezvous with Nyx.", "nyx", 3800, "Spoofed Transponder", "Encrypted transponder bypass chip.", Material.DAYLIGHT_DETECTOR, "none", false);

        regStage("nyx", 1, "Dead-Drop Acquisition", "vector:1", "Retrieve Spire security blueprints from the Black Market dead-drop.",
                "Right on time. Sentinels swept the alley two minutes ago. The Spire blueprints are inside this encrypted cadence disc. Krux is waiting to fence the decoy.",
                "Decoy handed off. Krux has the route cleared to Corvus.", "krux", 4000, "Imperial Cadence Disc", "Encrypted disc containing Spire surveillance routes.", Material.MUSIC_DISC_13, "none", false);

        regStage("krux", 1, "Contraband Liquidation", "nyx:1", "Foil Inquisitor patrols with contraband decoy.",
                "The Inquisitors bought the bait! The dead zone is clear. Get those blueprints back to Corvus immediately.",
                "Corvus is reviewing the schematics at Command.", "corvus", 4500, "Black Market Clearance", "Underworld pass granting immune status in the slums.", Material.CRYING_OBSIDIAN, "none", false);

        regStage("corvus", 3, "Imperial Blueprint Decryption", "krux:1", "Deliver Spire schematics to Corvus.",
                "You brought the schematics! Look at this thermal exhaust... The Citadel reactor bleeds heat straight through the LowerCity. Kristoph needs to forge thermal containment.",
                "Thermal containment specs approved. Kristoph is ready at the Forge.", "kristoph", 4500, "Decrypted Datapad", "Detailed blueprints of the Spire's inner sanctum.", Material.RECOVERY_COMPASS, "none", false);

        regStage("kristoph", 4, "Thermal Containment", "corvus:3", "Forge Phase-Cell for extreme temperature resistance.",
                "The Spire reactor runs at 5000 degrees chrono-friction. Take this insulated phase cell. You must pass Inquisitor Malakor at the High Gate.",
                "Phase cell equipped. Malakor controls the gate.", "malakor", 5000, "Insulated Phase Cell", "Heavy cryogenic shielding against Spire reactor radiation.", Material.LAPIS_BLOCK, "none", false);

        regStage("malakor", 1, "Bastion Gate Clearance", "kristoph:4", "Challenge Inquisitor Malakor's security protocol at the Citadel Gate.",
                "Halt, traveler! The Emperor's domain is closed to timeline wanderers. Your matrix... it resists the void scanner? Pass, before I reconsider.",
                "High Gate cleared. Captain Kael patrols the Interrogation Wing.", "kael", 5500, "High-Gate Authorization", "Imperial military clearance badge.", Material.REDSTONE_BLOCK, "none", false);

        regStage("kael", 1, "Thermal Interrogation", "malakor:1", "Withstand Purge Commander Kael's thermal scan.",
                "You reek of Sanctuary ozone! Our thermal hounds smell timeline jumpers a mile away. If you value your skull, bypass Overseer Thorne's reactor quickly.",
                "Thermal scan deceived. Overseer Thorne is at the Reactor Manifold.", "thorne", 6000, "Interrogation Bypass Code", "Imperial cipher code.", Material.TRIPWIRE_HOOK, "none", false);

        regStage("thorne", 1, "Reactor Manifold Bypass", "kael:1", "Divert reactor power manifold at the Imperial Bastion.",
                "Warning: Reactor coolant failing! A single slip here detonates three worlds. Re-route the coolant pipes so Archivist Vael's wing stays powered.",
                "Coolant stabilized. Archivist Vael holds the forbidden archives.", "vael", 6500, "Chrono-Manifold Key", "Magnetic valve override key.", Material.BLAST_FURNACE, "none", false);

        regStage("vael", 1, "Spire Archives Extraction", "thorne:1", "Retrieve forbidden timeline codex from Archivist Vael.",
                "You sought the truth? Look upon these records. In timeline 7, Corvus ruled the Empire. In timeline 9, the Void consumed everything. Take the codex to Corvus.",
                "Truth revealed. Return to Corvus for the final breach.", "corvus", 7000, "Forbidden Timeline Codex", "Ancient tome detailing the convergence of all timelines.", Material.BOOK, "none", false);

        regStage("corvus", 4, "Spire Assault Infiltration", "vael:1", "Plan the breach of the Imperial Throne Room with Corvus.",
                "Vael showed you the records? Then you know... The Emperor isn't just an enemy. He is what remains of my future if we fail. Kristoph, calibrate the Master Matrix!",
                "Master Matrix calibration underway at the Forge.", "kristoph", 7500, "Vanguard Breach Charge", "Controlled antimatter charge.", Material.TNT, "none", false);

        regStage("kristoph", 5, "Master Spatial Matrix", "corvus:4", "Forge the Prime Spatial Matrix for timeline convergence.",
                "This is the pinnacle of chrono-engineering. The Prime Spatial Matrix will allow you to breach the Emperor's throne chamber. Vector is overclocking the portal.",
                "Gateway overclocked. Vector is standing by.", "vector", 8000, "Prime Spatial Matrix", "Legendary Keystone matrix vibrating across dimensions.", Material.NETHER_STAR, "none", false);

        regStage("vector", 2, "Overclocked Gateway", "kristoph:5", "Step through the overclocked portal into the Spire Throne Room.",
                "The portal frequency is pinned! Once you jump, there is no recall beacon. Face the Emperor and break the cycle!",
                "Portal jumped. You stand before the Throne Room.", "emperor", 8500, "Planar Overclock Key", "Charged gateway key.", Material.ENDER_PEARL, "none", false);

        regStage("emperor", 1, "Mask of Eternity", "vector:2", "Confront the Masked Sovereign at the Imperial Spire.",
                "You finally arrived, Agent. Corvus sent you thinking you can save this world. Look beneath this mask... I am the one who survived eternity.",
                "The Emperor's mask shattered! The Multiverse convergence begins!", "emperor", 10000, "Imperial Fragment", "Shard of the sovereign mask.", Material.NETHERITE_HELMET, "none", false);

        regStage("emperor", 5, "Convergence of Realities", "emperor:1", "Unify the three dimensions and forge eternal peace.",
                "The timelines merge into one harmony. Sanctuary, the Snow Glaciers, and the Monochromatic Void are balanced forever.",
                "THE MULTIVERSE CONVERGENCE IS COMPLETE! YOU ARE ETERNAL CHAMPION OF DRAGONCOTE!", "none", 25000, "Heart of the Multiverse", "Supreme artifact containing the balance of reality.", Material.HEART_OF_THE_SEA, "ei give %%player%% multiverse_heart 1", false);

        // Ensure every character has at least Chapter 1
        for (NpcCharacter ch : characters.values()) {
            if (ch.getStages().isEmpty()) {
                regStage(ch.getId(), 1, "Introduction to " + ch.getShortName(), "none",
                        "Consult with " + ch.getShortName() + " in " + (ch.getZoneId() != null ? ch.getZoneId().toUpperCase() : "Sanctuary"),
                        ch.getQuote() != null ? ch.getQuote() : "Welcome to Dragoncote, Agent.",
                        "Thank you for consulting with me. Together we defend the timeline.",
                        "none", 500, "Sanctuary Token", "A proof of friendship token.", Material.SUNFLOWER, "none", false);
            }
        }
    }

    public static java.io.File getCustomStagesFile(java.io.File dataFolder) {
        java.io.File npcDir = new java.io.File(dataFolder, "npc");
        if (!npcDir.exists()) npcDir.mkdirs();
        java.io.File newFile = new java.io.File(npcDir, "custom_stages.yml");
        java.io.File oldFile = new java.io.File(dataFolder, "custom_npc_stages.yml");
        if (!newFile.exists() && oldFile.exists()) {
            oldFile.renameTo(newFile);
        }
        return newFile.exists() ? newFile : (oldFile.exists() ? oldFile : newFile);
    }

    public void loadCustomStages(java.io.File dataFolder) {
        java.io.File file = getCustomStagesFile(dataFolder);
        if (!file.exists()) return;

        try {
            org.bukkit.configuration.file.YamlConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            org.bukkit.configuration.ConfigurationSection charsSec = cfg.getConfigurationSection("characters");
            if (charsSec == null) return;

            for (String charId : charsSec.getKeys(false)) {
                NpcCharacter c = characters.get(charId.toLowerCase());
                if (c == null) continue;

                org.bukkit.configuration.ConfigurationSection sec = charsSec.getConfigurationSection(charId);
                if (sec == null) continue;

                if (sec.contains("skin")) c.setSkin(sec.getString("skin"));
                if (sec.contains("skin_snow")) c.setSkinSnow(sec.getString("skin_snow"));
                if (sec.contains("skin_bw")) c.setSkinBw(sec.getString("skin_bw"));
                if (sec.contains("completion_behavior")) c.setCompletionBehavior(sec.getString("completion_behavior"));
                if (sec.contains("engine_type")) c.setEngineType(sec.getString("engine_type"));
                if (sec.contains("model_id")) c.setModelId(sec.getString("model_id"));
                if (sec.contains("bound_warp")) c.setBoundWarpAlias(sec.getString("bound_warp"));

                org.bukkit.configuration.ConfigurationSection stagesSec = sec.getConfigurationSection("stages");
                if (stagesSec != null) {
                    for (String stgKey : stagesSec.getKeys(false)) {
                        try {
                            int stgNum = Integer.parseInt(stgKey);
                            org.bukkit.configuration.ConfigurationSection sSec = stagesSec.getConfigurationSection(stgKey);
                            if (sSec == null) continue;

                            String title = sSec.getString("title", "Chapter " + stgNum);
                            String req = sSec.getString("requires", "none");
                            String obj = sSec.getString("objective", "Complete directive");
                            String dial = sSec.getString("dialogue", "Greetings Agent.");
                            String comp = sSec.getString("completion", "Directive completed.");
                            String nextId = sSec.getString("next", "none");
                            double cash = sSec.getDouble("cash_reward", 1000.0);
                            String item = sSec.getString("reward_item", "Reward Cache");
                            String itemDesc = sSec.getString("reward_desc", "Supply cache");
                            String iconStr = sSec.getString("icon", "WRITABLE_BOOK");
                            Material mat = Material.matchMaterial(iconStr);
                            if (mat == null) mat = Material.WRITABLE_BOOK;
                            String cmd = sSec.getString("command", "none");
                            boolean walk = sSec.getBoolean("walk", false);

                            NpcStage loadedStage = new NpcStage(charId, stgNum, title, req, obj, dial, comp, nextId, cash, item, itemDesc, mat, cmd, walk);
                            if (sSec.contains("custom_item")) {
                                loadedStage.setCustomRewardItem(sSec.getItemStack("custom_item"));
                            }
                            if (sSec.contains("completion_behavior")) {
                                loadedStage.setCompletionBehavior(sSec.getString("completion_behavior"));
                            }
                            if (sSec.contains("bound_warp")) {
                                loadedStage.setBoundWarpAlias(sSec.getString("bound_warp"));
                            }
                            if (sSec.contains("loc_world")) {
                                String wName = sSec.getString("loc_world");
                                double x = sSec.getDouble("loc_x");
                                double y = sSec.getDouble("loc_y");
                                double z = sSec.getDouble("loc_z");
                                float yaw = (float) sSec.getDouble("loc_yaw");
                                org.bukkit.World w = org.bukkit.Bukkit.getWorld(wName);
                                if (w == null && (wName.equalsIgnoreCase("spawn_main") || wName.equalsIgnoreCase("spawn"))) {
                                    w = org.bukkit.Bukkit.getWorld("main");
                                }
                                if (w == null && !org.bukkit.Bukkit.getWorlds().isEmpty()) {
                                    w = org.bukkit.Bukkit.getWorlds().get(0);
                                }
                                if (w != null) {
                                    loadedStage.setStageLocation(new org.bukkit.Location(w, x, y, z, yaw, 0.0f));
                                }
                            }
                            c.addStage(loadedStage);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            org.bukkit.Bukkit.getLogger().warning("[NpcRegistry] Failed to load custom_npc_stages.yml: " + e.getMessage());
        }
    }

    public void saveCustomStages(java.io.File dataFolder) {
        java.io.File file = getCustomStagesFile(dataFolder);
        try {
            org.bukkit.configuration.file.YamlConfiguration cfg = new org.bukkit.configuration.file.YamlConfiguration();
            for (NpcCharacter c : characters.values()) {
                String path = "characters." + c.getId() + ".";
                cfg.set(path + "skin", c.getSkin());
                cfg.set(path + "skin_snow", c.getSkinSnow());
                cfg.set(path + "skin_bw", c.getSkinBw());
                cfg.set(path + "completion_behavior", c.getCompletionBehavior());
                cfg.set(path + "engine_type", c.getEngineType());
                cfg.set(path + "model_id", c.getModelId());
                cfg.set(path + "bound_warp", c.getBoundWarpAlias());

                for (Map.Entry<Integer, NpcStage> e : c.getStages().entrySet()) {
                    String stgPath = path + "stages." + e.getKey() + ".";
                    NpcStage st = e.getValue();
                    cfg.set(stgPath + "title", st.getTitle());
                    cfg.set(stgPath + "requires", st.getRequires());
                    cfg.set(stgPath + "objective", st.getObjective());
                    cfg.set(stgPath + "dialogue", st.getDialogueLine());
                    cfg.set(stgPath + "completion", st.getCompletionLine());
                    cfg.set(stgPath + "next", st.getNextCharacterId());
                    cfg.set(stgPath + "cash_reward", st.getCashReward());
                    cfg.set(stgPath + "reward_item", st.getRewardItemName());
                    cfg.set(stgPath + "reward_desc", st.getRewardItemDesc());
                    cfg.set(stgPath + "icon", st.getIcon() != null ? st.getIcon().name() : "WRITABLE_BOOK");
                    cfg.set(stgPath + "command", st.getExecutionCommand());
                    cfg.set(stgPath + "walk", st.isWalkOnComplete());
                    cfg.set(stgPath + "completion_behavior", st.getCompletionBehavior());
                    cfg.set(stgPath + "bound_warp", st.getBoundWarpAlias());
                    if (st.getCustomRewardItem() != null) {
                        cfg.set(stgPath + "custom_item", st.getCustomRewardItem());
                    }
                    if (st.getStageLocation() != null && st.getStageLocation().getWorld() != null) {
                        cfg.set(stgPath + "loc_world", st.getStageLocation().getWorld().getName());
                        cfg.set(stgPath + "loc_x", st.getStageLocation().getX());
                        cfg.set(stgPath + "loc_y", st.getStageLocation().getY());
                        cfg.set(stgPath + "loc_z", st.getStageLocation().getZ());
                        cfg.set(stgPath + "loc_yaw", st.getStageLocation().getYaw());
                    }
                }
            }
            cfg.save(file);
        } catch (Exception e) {
            org.bukkit.Bukkit.getLogger().warning("[NpcRegistry] Failed to save custom_npc_stages.yml: " + e.getMessage());
        }
    }

    private void registerChar(String id, String shortName, String fullName, String hexColor, String skin,
                              int sector, String faction, String role, String quote, Material icon,
                              String deployAdvice, String zoneId) {
        characters.put(id.toLowerCase(), new NpcCharacter(id, shortName, fullName, hexColor, skin,
                sector, faction, role, quote, icon, deployAdvice, zoneId));
    }

    private void registerService(String id, String command, String title) {
        NpcCharacter c = characters.get(id.toLowerCase());
        if (c != null) {
            c.setServiceCommand(command);
            c.setServiceTitle(title);
        }
    }

    private void registerCompanionSceneMobs(String id, String... mobIds) {
        NpcCharacter c = characters.get(id.toLowerCase());
        if (c != null) {
            for (String m : mobIds) c.addCompanionSceneMobId(m);
        }
    }

    private void regAmbient(String id, String... lines) {
        NpcCharacter c = characters.get(id.toLowerCase());
        if (c != null) {
            for (String l : lines) c.addAmbientLine(l);
        }
    }

    private void regStage(String charId, int stageNum, String title, String requires, String obj,
                          String dialogue, String comp, String nextId, double cash, String itemName,
                          String itemDesc, Material icon, String cmd, boolean walk) {
        NpcCharacter c = characters.get(charId.toLowerCase());
        if (c != null) {
            c.addStage(new NpcStage(charId, stageNum, title, requires, obj, dialogue, comp, nextId, cash, itemName, itemDesc, icon, cmd, walk));
        }
    }

    public NpcCharacter getCharacter(String id) {
        if (id == null) return null;
        String clean = id.contains(":") ? id.split(":")[0] : id;
        return characters.get(clean.toLowerCase());
    }

    public Collection<NpcCharacter> getAllCharacters() {
        return characters.values();
    }

    public List<String> getTimelineOrder() {
        return Collections.unmodifiableList(timelineOrder);
    }

    public String getNextTimelineCharacterId(String currentCharId) {
        if (currentCharId == null) return "corvus";
        for (int i = 0; i < timelineOrder.size(); i++) {
            String node = timelineOrder.get(i);
            String[] parts = node.split(":");
            if (parts[0].equalsIgnoreCase(currentCharId) && i + 1 < timelineOrder.size()) {
                return timelineOrder.get(i + 1).split(":")[0];
            }
        }
        return "corvus";
    }

    public List<String> getWandOrder() {
        return Collections.unmodifiableList(wandOrder);
    }

    public String getFactionIcon(String faction) {
        if (faction == null) return "🔷";
        switch (faction.toLowerCase()) {
            case "empire": return "🔺";
            case "underworld": return "♠";
            case "exiled": return "⛓";
            case "neutral": return "⚖";
            default: return "🔷";
        }
    }

    public String getFactionHex(String faction) {
        if (faction == null) return "<#BADEA0>";
        switch (faction.toLowerCase()) {
            case "empire": return "<#F87171>";
            case "underworld": return "<#FBBF24>";
            case "exiled": return "<#A1A1AA>";
            case "neutral": return "<#6EE7B7>";
            default: return "<#BADEA0>";
        }
    }

    public String getFactionCaps(String faction) {
        if (faction == null) return "ʀᴇꜱɪꜱᴛᴀɴᴄᴇ";
        switch (faction.toLowerCase()) {
            case "empire": return "ᴇᴍᴘɪʀᴇ";
            case "underworld": return "ᴜɴᴅᴇʀᴡᴏʀʟᴅ";
            case "exiled": return "ᴇxɪʟᴇᴅ";
            case "neutral": return "ɴᴇᴜᴛʀᴀʟ";
            default: return "ʀᴇꜱɪꜱᴛᴀɴᴄᴇ";
        }
    }

    /**
     * Generates a world-sensitive overhead single-line hologram tag.
     */
    public String getOverheadTag(String charId, String worldName) {
        String baseId = (charId != null && charId.contains(":")) ? charId.split(":")[0] : charId;
        NpcCharacter c = characters.get(baseId.toLowerCase());
        if (c == null) return "§7Unknown NPC";

        String icon = getFactionIcon(c.getFaction());
        String facHex = getFactionHex(c.getFaction());
        String facCaps = getFactionCaps(c.getFaction());
        String charHex = c.getHexColor();
        if (charHex != null && !charHex.startsWith("<") && !charHex.startsWith("&")) {
            charHex = "<" + (charHex.startsWith("#") ? charHex : "#" + charHex) + ">";
        }
        return DragoncoteColor.colorize(charHex + "&l" + c.getShortName() + " &r<#DECDA0>" + icon + " &7<" + facHex + facCaps + "&7>");
    }

    /**
     * Generates the styled chat header component matching dcnpc.sk.
     */
    public TextComponent getChatHeader(String charId, String worldName) {
        String baseId = (charId != null && charId.contains(":")) ? charId.split(":")[0] : charId;
        NpcCharacter c = characters.get(baseId.toLowerCase());
        if (c == null) return new TextComponent("§7Unknown NPC");

        String icon = getFactionIcon(c.getFaction());
        String facHex = getFactionHex(c.getFaction());
        String facCaps = getFactionCaps(c.getFaction());
        String charHex = c.getHexColor();
        if (charHex != null && !charHex.startsWith("<") && !charHex.startsWith("&")) {
            charHex = "<" + (charHex.startsWith("#") ? charHex : "#" + charHex) + ">";
        }

        String headerStr = DragoncoteColor.colorize(charHex + "&l" + c.getShortName() + " <#DECDA0>" + icon + " &7<" + facHex + facCaps + "&7>");
        BaseComponent[] parsed = TextComponent.fromLegacyText(headerStr);
        TextComponent comp = new TextComponent();
        for (BaseComponent b : parsed) comp.addExtra(b);
        comp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lore"));
        comp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§aClick to view Personnel Dossier").create()));
        return comp;
    }
}
