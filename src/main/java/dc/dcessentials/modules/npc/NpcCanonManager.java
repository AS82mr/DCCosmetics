package dc.dcessentials.modules.npc;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.npc.model.NpcCharacter;
import dc.dcessentials.modules.npc.model.NpcStage;
import dc.dcessentials.modules.npc.model.NpcZone;
import dc.dcessentials.modules.npc.util.DragoncoteColor;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages both the Real-Life Live Batch (multi-world replicated across Spawn dimensions & LowerCity)
 * and the Testing Batch (lined up for rapid progression/claim testing).
 */
public class NpcCanonManager {

    private final NpcRegistry registry;
    private final NpcZoneManager zoneManager;

    private final File liveLocationsFile;
    private final File testLocationsFile;

    // Hardcoded canonical testing line coordinates from dcnpc.sk
    private final Map<String, CanonLoc> testLineDefaults = new LinkedHashMap<>();
    // Loaded live locations (npc_locations_live.yml)
    private final Map<String, Location> liveLocations = new LinkedHashMap<>();
    // Loaded test locations (npc_locations_test.yml)
    private final Map<String, Location> testLocations = new LinkedHashMap<>();

    public static class CanonLoc {
        public final String worldName;
        public final double x, y, z;
        public final float yaw, pitch;

        public CanonLoc(String worldName, double x, double y, double z, float yaw, float pitch) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public Location toLocation() {
            World w = Bukkit.getWorld(worldName);
            if (w == null) {
                List<World> worlds = Bukkit.getWorlds();
                w = worlds.isEmpty() ? null : worlds.get(0);
            }
            if (w == null) return null;
            return new Location(w, x, y, z, yaw, pitch);
        }
    }

    public NpcCanonManager(NpcRegistry registry, NpcZoneManager zoneManager) {
        this.registry = registry;
        this.zoneManager = zoneManager;

        File npcDir = new File(DCCosmetics.getInstance().getDataFolder(), "npc");
        if (!npcDir.exists()) npcDir.mkdirs();

        File newLive = new File(npcDir, "locations_live.yml");
        File oldLive = new File(DCCosmetics.getInstance().getDataFolder(), "npc_locations_live.yml");
        if (!newLive.exists() && oldLive.exists()) oldLive.renameTo(newLive);
        this.liveLocationsFile = newLive.exists() ? newLive : (oldLive.exists() ? oldLive : newLive);

        File newTest = new File(npcDir, "locations_test.yml");
        File oldTest = new File(DCCosmetics.getInstance().getDataFolder(), "npc_locations_test.yml");
        if (!newTest.exists() && oldTest.exists()) oldTest.renameTo(newTest);
        this.testLocationsFile = newTest.exists() ? newTest : (oldTest.exists() ? oldTest : newTest);

        initializeTestLineDefaults();
        loadLocations();
    }

    private void initializeTestLineDefaults() {
        // LowerCity Sector II Test Line Coordinates (from dcnpc.sk testing platform)
        testLineDefaults.put("corvus", new CanonLoc("lowercity", 130.5, 131.0, 272.5, 45.0f, 0.0f));
        testLineDefaults.put("kristoph", new CanonLoc("lowercity", 133.5, 131.0, 276.5, 105.0f, 0.0f));
        testLineDefaults.put("lyra", new CanonLoc("lowercity", 128.5, 131.0, 276.5, 161.0f, 0.0f));
        testLineDefaults.put("echo", new CanonLoc("lowercity", 131.5, 131.0, 280.5, 147.0f, 0.0f));
        testLineDefaults.put("prisoner404", new CanonLoc("lowercity", 126.5, 131.0, 280.5, -165.0f, 0.0f));
        testLineDefaults.put("nadia", new CanonLoc("lowercity", 129.5, 131.0, 285.5, 172.0f, 0.0f));
        testLineDefaults.put("valerio", new CanonLoc("lowercity", 124.5, 131.0, 284.5, -160.0f, 0.0f));
        testLineDefaults.put("shade", new CanonLoc("lowercity", 127.5, 131.0, 290.5, 160.0f, 0.0f));
        testLineDefaults.put("vector", new CanonLoc("lowercity", 123.5, 131.0, 287.5, -163.0f, 0.0f));
        testLineDefaults.put("emperor", new CanonLoc("lowercity", 125.5, 131.0, 293.5, -167.0f, 0.0f));
        testLineDefaults.put("vane", new CanonLoc("lowercity", 120.5, 131.0, 290.5, -153.0f, 0.0f));
        testLineDefaults.put("jax", new CanonLoc("lowercity", 123.5, 131.0, 296.5, -168.0f, 0.0f));
        testLineDefaults.put("sylas", new CanonLoc("lowercity", 118.5, 131.1, 294.5, -150.0f, 0.0f));
        testLineDefaults.put("ronald", new CanonLoc("lowercity", 122.5, 131.0, 301.5, -174.0f, 0.0f));
        testLineDefaults.put("hesper", new CanonLoc("lowercity", 117.5, 131.0, 297.5, -152.0f, 0.0f));
        testLineDefaults.put("balthazar", new CanonLoc("lowercity", 119.5, 131.0, 305.5, -167.0f, 0.0f));
        testLineDefaults.put("malakor", new CanonLoc("lowercity", 115.5, 131.0, 304.5, -156.0f, 0.0f));
        testLineDefaults.put("kael", new CanonLoc("lowercity", 117.5, 131.0, 310.5, -170.0f, 0.0f));
        testLineDefaults.put("thorne", new CanonLoc("lowercity", 113.5, 131.0, 308.5, -156.0f, 0.0f));
        testLineDefaults.put("vael", new CanonLoc("lowercity", 116.5, 131.0, 314.5, 81.0f, 0.0f));
        testLineDefaults.put("ignis", new CanonLoc("lowercity", 111.5, 131.0, 313.5, -157.0f, 0.0f));
        testLineDefaults.put("theta", new CanonLoc("lowercity", 112.5, 131.0, 318.5, 80.5f, 0.0f));
        testLineDefaults.put("gideon", new CanonLoc("lowercity", 108.5, 131.0, 317.5, -163.0f, 0.0f));
        testLineDefaults.put("sorsha", new CanonLoc("lowercity", 108.5, 131.0, 321.5, 82.0f, 0.0f));
        testLineDefaults.put("garrick", new CanonLoc("lowercity", 103.5, 131.0, 319.5, -150.0f, 0.0f));
        testLineDefaults.put("twitch", new CanonLoc("lowercity", 105.5, 131.0, 323.5, 87.0f, 0.0f));
        testLineDefaults.put("brutus", new CanonLoc("lowercity", 101.5, 131.0, 321.5, -148.0f, 0.0f));
        testLineDefaults.put("finch", new CanonLoc("lowercity", 102.5, 131.0, 326.5, 90.0f, 0.0f));
        testLineDefaults.put("rats", new CanonLoc("lowercity", 106.5, 131.0, 325.5, -90.0f, 0.0f));
        testLineDefaults.put("roxy", new CanonLoc("lowercity", 107.5, 131.0, 328.5, 0.0f, 0.0f));
        testLineDefaults.put("slick", new CanonLoc("lowercity", 105.5, 131.0, 330.5, 180.0f, 0.0f));
        testLineDefaults.put("nyx", new CanonLoc("lowercity", 104.5, 131.0, 328.5, 90.0f, 0.0f));
        testLineDefaults.put("krux", new CanonLoc("lowercity", 109.5, 131.0, 323.5, 163.0f, 0.0f));
    }

    public String getPrimeSpawnWorldName() {
        if (Bukkit.getWorld("main") != null) return "main";
        if (Bukkit.getWorld("spawn_main") != null) return "spawn_main";
        List<String> active = getActiveSpawnWorlds();
        return active.isEmpty() ? "spawn_main" : active.get(0);
    }

    public Location getLiveLocation(String charId) {
        String id = charId.toLowerCase();
        if (liveLocations.containsKey(id)) {
            return liveLocations.get(id);
        }
        NpcCharacter c = registry.getCharacter(id);
        if (c != null && c.getSector() == 1) {
            NPC mainNpc = zoneManager.findCitizensNpc(id, getPrimeSpawnWorldName());
            if (mainNpc == null) mainNpc = zoneManager.findCitizensNpc(id, null);
            if (mainNpc != null) {
                Location loc = (mainNpc.isSpawned() && mainNpc.getEntity() != null) ?
                        mainNpc.getEntity().getLocation() : mainNpc.getStoredLocation();
                if (loc != null) {
                    liveLocations.put(id, loc);
                    saveLocations();
                    return loc;
                }
            }
        }
        if (c != null && c.getZoneId() != null) {
            NpcZone z = zoneManager.getZone(c.getZoneId());
            if (z != null && z.getWarpLocation() != null) {
                return z.getWarpLocation();
            }
        }
        if (testLineDefaults.containsKey(id)) {
            return testLineDefaults.get(id).toLocation();
        }
        return null;
    }

    public Location getTestLocation(String charId) {
        String id = charId.toLowerCase();
        if (testLocations.containsKey(id)) {
            return testLocations.get(id);
        }
        if (testLineDefaults.containsKey(id)) {
            return testLineDefaults.get(id).toLocation();
        }
        return null;
    }

    public void setLiveLocation(String charId, Location loc) {
        liveLocations.put(charId.toLowerCase(), loc);
        saveLocations();
    }

    public void setLocation(String charId, Location loc) {
        setLiveLocation(charId, loc);
    }

    public void purgeAll(CommandSender sender) {
        purge(sender, "all");
    }

    public void respawnAllCanon(CommandSender sender) {
        respawnAllLive(sender);
    }

    public void loadLocations() {
        if (liveLocationsFile.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(liveLocationsFile);
            ConfigurationSection sec = cfg.getConfigurationSection("locations");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    try {
                        Location loc = sec.getLocation(key);
                        if (loc != null) liveLocations.put(key.toLowerCase(), loc);
                    } catch (Throwable ignored) {}
                }
            }
        }
        if (testLocationsFile.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(testLocationsFile);
            ConfigurationSection sec = cfg.getConfigurationSection("locations");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    try {
                        Location loc = sec.getLocation(key);
                        if (loc != null) testLocations.put(key.toLowerCase(), loc);
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    public void saveLocations() {
        YamlConfiguration liveCfg = new YamlConfiguration();
        for (Map.Entry<String, Location> entry : liveLocations.entrySet()) {
            liveCfg.set("locations." + entry.getKey(), entry.getValue());
        }
        try { liveCfg.save(liveLocationsFile); } catch (IOException ignored) {}
    }

    /**
     * Purges canonical NPCs (mode = "all", "test", "live", or "cliff").
     */
    public int purge(CommandSender sender, String mode) {
        if (CitizensAPI.getPlugin() == null) {
            DCMessage.sendError(sender, "Citizens plugin is not active.");
            return 0;
        }

        if (mode.equalsIgnoreCase("cliff")) {
            return purgeCliffAndMisplacedNpcs(sender);
        }

        int count = 0;
        List<NPC> toRemove = new ArrayList<>();

        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            boolean isCanonId = (npc.getId() >= 400 && npc.getId() <= 500);
            boolean hasTag = npc.data().has("dcnpc_id");
            String npcMode = npc.data().has("dcnpc_mode") ? npc.data().get("dcnpc_mode").toString() : "all";

            if (isCanonId || hasTag) {
                if (mode.equalsIgnoreCase("all") || mode.equalsIgnoreCase("canon") || npcMode.equalsIgnoreCase(mode)) {
                    toRemove.add(npc);
                }
            }
        }

        for (NPC npc : toRemove) {
            try {
                npc.destroy();
                count++;
            } catch (Throwable ignored) {}
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens save");
        DCMessage.sendGeneral(sender, "Purged &#FFFFFF" + count + " &#0CBF93canonical NPCs (Mode: &#FFFFFF" + mode.toUpperCase() + "&#0CBF93).");
        return count;
    }

    /**
     * Actively scans and destroys misplaced cliff NPCs (X: 90..140, Z: 260..340 in spawn worlds)
     * as well as NPCs in the obsolete 'spawn' world or Sector 2+ in spawn worlds.
     */
    public int purgeCliffAndMisplacedNpcs(CommandSender sender) {
        if (CitizensAPI.getPlugin() == null || !CitizensAPI.hasImplementation()) return 0;

        int destroyedCount = 0;
        List<NPC> toDestroy = new ArrayList<>();

        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            Location loc = (npc.isSpawned() && npc.getEntity() != null) ? npc.getEntity().getLocation() : npc.getStoredLocation();
            if (loc == null || loc.getWorld() == null) continue;

            String wName = loc.getWorld().getName().toLowerCase();
            double x = loc.getX();
            double z = loc.getZ();

            // 1. NPC in invalid "spawn" world
            if (wName.equals("spawn")) {
                toDestroy.add(npc);
                continue;
            }

            // 2. Check if inside spawn dimensions: spawn_main, spawn_snow, spawn_bw
            if (wName.equals("spawn_main") || wName.equals("spawn_snow") || wName.equals("spawn_bw")) {
                // If it's located on the cliff area where lowercity NPCs were duplicated (X: 80..150, Z: 240..360):
                if (x >= 80.0 && x <= 150.0 && z >= 240.0 && z <= 360.0) {
                    toDestroy.add(npc);
                    continue;
                }

                // If it has ID in the cliff duplication range (720..804)
                if (npc.getId() >= 720 && npc.getId() <= 804) {
                    toDestroy.add(npc);
                    continue;
                }

                // Or if it belongs to Sector 2, 3, or 4
                String charId = resolveCanonId(npc);
                if (charId != null) {
                    NpcCharacter c = registry.getCharacter(charId);
                    if (c != null && c.getSector() > 1) {
                        toDestroy.add(npc);
                        continue;
                    }
                }
            }
        }

        for (NPC npc : toDestroy) {
            try {
                npc.destroy();
                destroyedCount++;
            } catch (Throwable ignored) {}
        }

        // Also clean up any orphan non-player entities/armor stands in the cliff bounding box across all spawn worlds
        for (String wName : Arrays.asList("spawn_main", "spawn_snow", "spawn_bw")) {
            World w = Bukkit.getWorld(wName);
            if (w != null) {
                try {
                    for (org.bukkit.entity.Entity e : w.getEntities()) {
                        if (e instanceof Player) continue;
                        Location eLoc = e.getLocation();
                        if (eLoc.getX() >= 80.0 && eLoc.getX() <= 150.0 && eLoc.getZ() >= 240.0 && eLoc.getZ() <= 360.0) {
                            if (!CitizensAPI.getNPCRegistry().isNPC(e)) {
                                e.remove();
                                destroyedCount++;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (destroyedCount > 0) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens save");
            if (sender != null) {
                DCMessage.sendWarning(sender, "Purged &#FFFFFF" + destroyedCount + " &#FDE047misplaced cliff NPCs from spawn worlds.");
            } else {
                DCCosmetics.getInstance().getLogger().info("Auto-purged " + destroyedCount + " misplaced cliff NPCs from spawn worlds.");
            }
        }
        return destroyedCount;
    }

    /**
     * Deploys the Testing Batch (30 NPCs lined up on the LowerCity platform).
     */
    public int respawnAllTest(CommandSender sender) {
        purge(sender, "test");
        DCMessage.sendInfo(sender, "Deploying Testing Batch across LowerCity test line...");

        int count = 0;
        for (NpcCharacter c : registry.getAllCharacters()) {
            Location loc = getTestLocation(c.getId());
            if (loc == null || loc.getWorld() == null) continue;

            spawnCitizensNpc(c, loc, "test");
            count++;
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens save");
        DCMessage.sendGeneral(sender, "Deployed &#FFFFFF" + count + " &#0CBF93Testing Batch NPCs in LowerCity.");
        return count;
    }

    /**
     * Deploys the Real-Life Live Batch:
     * - Sector 1 (Sanctuary Rebellion): Replicated across all 3 spawn dimensions (spawn_main, spawn_bw, spawn_snow).
     * - Sector 2, 3, 4 (LowerCity / Underworld / Breach): Deployed in lowercity once.
     */
    public int respawnAllLive(CommandSender sender) {
        purge(sender, "live");
        DCMessage.sendInfo(sender, "Deploying Live Batch across Sanctuary and LowerCity...");

        int count = 0;
        List<String> spawnWorlds = getActiveSpawnWorlds();

        for (NpcCharacter c : registry.getAllCharacters()) {
            Location loc = getLiveLocation(c.getId());
            if (loc == null || loc.getWorld() == null) continue;

            if (c.getSector() == 1) {
                // Multi-World 3-Dimension Replication for Sanctuary Rebellion
                for (String wName : spawnWorlds) {
                    World w = Bukkit.getWorld(wName);
                    if (w == null) continue;

                    Location multiLoc = new Location(w, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
                    spawnCitizensNpc(c, multiLoc, "live");
                    count++;
                }
            } else {
                // LowerCity once and only once
                World wLower = Bukkit.getWorld("lowercity");
                Location lowerLoc = (wLower != null) ?
                        new Location(wLower, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()) : loc;
                spawnCitizensNpc(c, lowerLoc, "live");
                count++;
            }
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens save");
        DCMessage.sendGeneral(sender, "Deployed &#FFFFFF" + count + " &#0CBF93Live Batch NPCs across Sanctuary and LowerCity.");
        return count;
    }

    /**
     * Spawns a configured Citizens NPC with traits, world skins, and overhead tags.
     */
    public void configureHologramTag(NPC npc, String rawDisplayName, String worldName) {
        if (npc == null) return;
        npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, false);
        net.citizensnpcs.trait.HologramTrait holo = npc.getOrAddTrait(net.citizensnpcs.trait.HologramTrait.class);
        holo.clear();
        if (rawDisplayName == null || rawDisplayName.isEmpty()) {
            return;
        }
        String lower = worldName != null ? worldName.toLowerCase() : "";
        boolean isBw = lower.contains("bw") || lower.contains("void") || lower.contains("drain");
        String line = isBw
                ? dc.dcessentials.modules.npc.util.DragoncoteColor.stripToGrayscale(rawDisplayName)
                : dc.dcessentials.modules.npc.util.DragoncoteColor.colorize(rawDisplayName);
        holo.addLine(line);
    }

    public void applyProfileEngine(NPC npc, dc.dcessentials.modules.npc.model.NpcBehaviorProfile profile,
                                   String tagId, String skinId, String fallbackName) {
        if (npc == null || profile == null || !npc.isSpawned() || npc.getEntity() == null) return;
        String wName = npc.getEntity().getWorld().getName().toLowerCase();
        boolean isBw = wName.contains("bw") || wName.contains("void") || wName.contains("drain");

        if (!profile.isModelEngine()) {
            boolean hadModelEngine = false;
            try {
                com.ticxo.modelengine.api.model.ModeledEntity existing =
                        com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(npc.getEntity());
                if (existing != null) {
                    hadModelEngine = true;
                    for (com.ticxo.modelengine.api.model.ActiveModel actModel : new ArrayList<>(existing.getModels().values())) {
                        try { actModel.destroy(); } catch (Throwable ignored) {}
                    }
                    try { existing.getModels().clear(); } catch (Throwable ignored) {}
                    try { existing.setBaseEntityVisible(true); } catch (Throwable ignored) {}
                    existing.destroy();
                    com.ticxo.modelengine.api.ModelEngineAPI.getAPI().getModelUpdaters().forceRemoveModeledEntity(existing);
                }
            } catch (Throwable ignored) {}
            if (hadModelEngine && npc.isSpawned()) {
                Location loc = (npc.getEntity() != null) ? npc.getEntity().getLocation() : npc.getStoredLocation();
                if (loc != null) {
                    npc.despawn();
                    npc.spawn(loc);
                }
            }
            if (npc.isSpawned() && npc.getEntity() != null) {
                try { npc.getEntity().setInvisible(false); } catch (Throwable ignored) {}
                if (npc.getEntity() instanceof org.bukkit.entity.LivingEntity le) {
                    try { le.setInvisible(false); } catch (Throwable ignored) {}
                }
            }
            DCCosmetics.getInstance().getSkinProcessor().applyProfileSkin(
                    npc, wName, skinId, fallbackName, profile.getSkinRevision());
            return;
        }

        String effectiveModelId = profile.resolveEffectiveModelId(isBw);
        if (effectiveModelId == null || effectiveModelId.isEmpty()) return;
        try {
            com.ticxo.modelengine.api.model.ActiveModel activeModel =
                    com.ticxo.modelengine.api.ModelEngineAPI.createActiveModel(effectiveModelId);
            if (activeModel == null) {
                DCCosmetics.getInstance().getLogger().warning("[DCNPC] ModelEngine blueprint missing: '" + effectiveModelId + "' for " + tagId + "; falling back to Citizens.");
                return;
            }
            com.ticxo.modelengine.api.model.ModeledEntity existing =
                    com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(npc.getEntity());
            if (existing != null) {
                for (com.ticxo.modelengine.api.model.ActiveModel oldModel : new java.util.ArrayList<>(existing.getModels().values())) {
                    try { oldModel.destroy(); } catch (Throwable ignored) {}
                }
                try { existing.getModels().clear(); } catch (Throwable ignored) {}
                existing.destroy();
                com.ticxo.modelengine.api.ModelEngineAPI.getAPI().getModelUpdaters().forceRemoveModeledEntity(existing);
            }
            com.ticxo.modelengine.api.model.ModeledEntity modeled =
                    com.ticxo.modelengine.api.ModelEngineAPI.getOrCreateModeledEntity(npc.getEntity());
            modeled.setBaseEntityVisible(false);
            modeled.addModel(activeModel, true);
            if (DCCosmetics.getInstance().getNpcBehaviorController() != null) {
                DCCosmetics.getInstance().getNpcBehaviorController().applyInitialModelAnimation(npc, profile);
            }
        } catch (Throwable t) {
            DCCosmetics.getInstance().getLogger().warning("[DCNPC] Failed applying ModelEngine model '" + effectiveModelId + "' to " + tagId + ": " + t.getMessage());
        }
    }

    public NPC spawnCitizensNpc(NpcCharacter c, Location loc, String mode) {
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, c.getShortName());
        npc.data().setPersistent("dcnpc_id", c.getId());
        npc.data().setPersistent("dcnpc_mode", mode);
        npc.spawn(loc);

        // Configure LookClose
        npc.getOrAddTrait(LookClose.class).lookClose(true);

        // Apply world-specific skin directly WITHOUT triggering Mojang Knight/Steve async lookup overwrite
        DCCosmetics.getInstance().getSkinProcessor().applyProfileSkin(
                npc, loc.getWorld().getName(), c.getId(), c.getSkin(), c.getBehaviorProfile().getSkinRevision());

        // Configure single-line overhead hologram & hide vanilla nameplate
        configureNpcOverhead(npc, c, loc.getWorld().getName());

        // Apply engine mode (CITIZENS or MODEL_ENGINE rig)
        applyEngineMode(npc, c);

        return npc;
    }

    public void configureNpcOverhead(NPC npc, NpcCharacter c, String worldName) {
        if (npc == null || c == null) return;
        DCCosmetics.debugNpc("configureNpcOverhead: NPC #" + npc.getId() + " (" + npc.getName() + ") world=" + worldName + " char=" + c.getId());
        try {
            npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, false);
            npc.data().setPersistent("nameplate-visible", false);
        } catch (Throwable ignored) {}

        // Hide vanilla entity custom name & suppress scoreboard nametag visibility
        if (npc.isSpawned() && npc.getEntity() != null) {
            npc.getEntity().setCustomNameVisible(false);
            try {
                org.bukkit.scoreboard.Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
                org.bukkit.scoreboard.Team team = sb.getTeam("dcnpc_hide");
                if (team == null) {
                    team = sb.registerNewTeam("dcnpc_hide");
                    team.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
                }
                String entry = npc.getName();
                if (entry != null && !team.hasEntry(entry)) {
                    team.addEntry(entry);
                }
            } catch (Throwable ignored) {}
        }

        try {
            String rawTag = registry.getOverheadTag(c.getId(), worldName);
            String coloredTag = ColorUtil.colorize(rawTag);
            net.citizensnpcs.trait.HologramTrait holo = npc.getOrAddTrait(net.citizensnpcs.trait.HologramTrait.class);
            List<String> existingLines = holo.getLines();
            if (existingLines.size() == 1 && existingLines.get(0).equals(coloredTag)) {
                return;
            }
            holo.clear();
            holo.addLine(coloredTag);
            if (npc.isSpawned()) {
                npc.scheduleUpdate(NPC.NPCUpdate.PACKET);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Finds active spawn dimensions on the server.
     */
    public List<String> getActiveSpawnWorlds() {
        List<String> list = new ArrayList<>();
        // Priority 1: Main world (main or spawn_main) - NEVER use "spawn"
        if (Bukkit.getWorld("main") != null) list.add("main");
        else if (Bukkit.getWorld("spawn_main") != null) list.add("spawn_main");

        // Priority 2: Snow world (spawn_snow or snow)
        if (Bukkit.getWorld("spawn_snow") != null) list.add("spawn_snow");
        else if (Bukkit.getWorld("snow") != null) list.add("snow");

        // Priority 3: Black & White world (spawn_bw or bw)
        if (Bukkit.getWorld("spawn_bw") != null) list.add("spawn_bw");
        else if (Bukkit.getWorld("bw") != null) list.add("bw");

        if (list.isEmpty()) {
            for (String candidate : Arrays.asList("main", "spawn_main", "spawn_snow", "spawn_bw")) {
                World w = getOrLoadWorld(candidate);
                if (w != null && !candidate.equalsIgnoreCase("spawn")) {
                    list.add(candidate);
                }
            }
        }
        return list;
    }

    public World getOrLoadWorld(String worldName) {
        if (worldName == null || worldName.equalsIgnoreCase("spawn")) return null;
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            try {
                w = Bukkit.createWorld(new org.bukkit.WorldCreator(worldName));
            } catch (Throwable ignored) {}
        }
        return w;
    }

    public void syncSector1Instances(NpcCharacter c) {
        if (c == null || c.getSector() != 1) return;
        Location primeLoc = getLiveLocation(c.getId());
        List<String> targetWorlds = getActiveSpawnWorlds();

        if (primeLoc == null) {
            NPC mainNpc = zoneManager.findCitizensNpc(c.getId(), "spawn_main");
            if (mainNpc != null) {
                primeLoc = (mainNpc.isSpawned() && mainNpc.getEntity() != null) ?
                        mainNpc.getEntity().getLocation() : mainNpc.getStoredLocation();
                setLiveLocation(c.getId(), primeLoc);
            }
        }
        if (primeLoc == null) return;

        for (String wName : targetWorlds) {
            World w = getOrLoadWorld(wName);
            if (w == null) continue;

            Location targetLoc = new Location(w, primeLoc.getX(), primeLoc.getY(), primeLoc.getZ(), primeLoc.getYaw(), primeLoc.getPitch());
            NPC existing = zoneManager.findCitizensNpc(c.getId(), wName);
            if (existing != null) {
                if (existing.isSpawned()) {
                    if (existing.getNavigator() != null) {
                        existing.getNavigator().cancelNavigation();
                    }
                    existing.teleport(targetLoc, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                } else {
                    existing.spawn(targetLoc);
                }
                DCCosmetics.getInstance().getSkinProcessor().applyWorldSkin(existing, wName, c.getId());
                configureNpcOverhead(existing, c, wName);
                applyEngineMode(existing, c);
            } else {
                spawnCitizensNpc(c, targetLoc, "live");
            }
        }

        syncStageInstances(c);
    }

    public void syncStageInstances(NpcCharacter c) {
        if (c == null) return;
        List<String> targetWorlds = getActiveSpawnWorlds();

        for (Map.Entry<Integer, NpcStage> entry : c.getStages().entrySet()) {
            int stageNum = entry.getKey();
            NpcStage stage = entry.getValue();
            if (stageNum <= 1 || stage.getStageLocation() == null) continue;

            Location sLoc = stage.getStageLocation();
            String stageId = c.getId() + ":" + stageNum;

            for (String wName : targetWorlds) {
                World w = getOrLoadWorld(wName);
                if (w == null) continue;

                Location targetLoc = new Location(w, sLoc.getX(), sLoc.getY(), sLoc.getZ(), sLoc.getYaw(), sLoc.getPitch());
                NPC existing = zoneManager.findCitizensNpc(stageId, wName);
                if (existing != null) {
                    if (existing.isSpawned()) {
                        if (existing.getNavigator() != null) {
                            existing.getNavigator().cancelNavigation();
                        }
                        existing.teleport(targetLoc, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                    } else {
                        existing.spawn(targetLoc);
                    }
                    DCCosmetics.getInstance().getSkinProcessor().applyWorldSkin(existing, wName, c.getId());
                    configureNpcOverhead(existing, c, wName);
                    applyEngineMode(existing, c);
                } else {
                    NPC stageNpc = spawnCitizensNpc(c, targetLoc, "live");
                    if (stageNpc != null) {
                        stageNpc.data().setPersistent("dcnpc_id", stageId);
                        stageNpc.setName(c.getShortName());
                        DCCosmetics.getInstance().getSkinProcessor().applyWorldSkin(stageNpc, wName, c.getId());
                        configureNpcOverhead(stageNpc, c, wName);
                        applyEngineMode(stageNpc, c);
                    }
                }
            }
        }
    }

    /**
     * Batch refreshes LookClose, world skins, overhead tags, and syncs Sector 1 across spawn dimensions.
     */
    public int fixNames(CommandSender sender) {
        if (CitizensAPI.getPlugin() == null) {
            DCMessage.sendError(sender, "Citizens plugin is not active.");
            return 0;
        }

        // 1. First automatically purge any misplaced cliff NPCs or illegal 'spawn' entities
        int cliffPurged = purgeCliffAndMisplacedNpcs(null);

        DCMessage.sendInfo(sender, "Refreshing nameplates, world skins, overhead badges, and engine models...");
        int count = 0;
        int destroyedSpawnNpcs = 0;
        int duplicateNpcsRemoved = 0;
        Set<String> processedChars = new HashSet<>();
        Set<String> seenCharPerWorld = new HashSet<>();

        List<NPC> allNpcs = new ArrayList<>();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            allNpcs.add(npc);
        }

        for (NPC npc : allNpcs) {
            Location loc = (npc.isSpawned() && npc.getEntity() != null) ? npc.getEntity().getLocation() : npc.getStoredLocation();
            if (loc == null || loc.getWorld() == null) continue;

            String worldName = loc.getWorld().getName();
            String charId = resolveCanonId(npc);
            if (charId == null) continue;

            NpcCharacter c = registry.getCharacter(charId);
            if (worldName.equalsIgnoreCase("spawn")) {
                npc.destroy();
                destroyedSpawnNpcs++;
                continue;
            }

            // Remove misplaced Sector 2, 3, 4 NPCs if they accidentally ended up in a spawn world
            if (c != null && c.getSector() > 1 && (worldName.equalsIgnoreCase("spawn_main") || worldName.equalsIgnoreCase("spawn_snow") || worldName.equalsIgnoreCase("spawn_bw"))) {
                npc.destroy();
                duplicateNpcsRemoved++;
                continue;
            }

            // Deduplicate: If another instance of this NPC already exists in this same world, destroy it
            String worldKey = charId.toLowerCase() + "@" + worldName.toLowerCase();
            if (seenCharPerWorld.contains(worldKey)) {
                npc.destroy();
                duplicateNpcsRemoved++;
                continue;
            }
            seenCharPerWorld.add(worldKey);

            if (c != null) {
                npc.data().setPersistent("dcnpc_id", charId);
                npc.getOrAddTrait(LookClose.class).lookClose(true);

                DCCosmetics.getInstance().getSkinProcessor().applyWorldSkin(npc, worldName, c.getId());
                configureNpcOverhead(npc, c, worldName);
                applyEngineMode(npc, c);
            }

            count++;
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens save");
        if (cliffPurged > 0) {
            DCMessage.sendWarning(sender, "Purged &#FFFFFF" + cliffPurged + " &#FDE047misplaced cliff NPCs.");
        }
        if (destroyedSpawnNpcs > 0) {
            DCMessage.sendWarning(sender, "Purged &#FFFFFF" + destroyedSpawnNpcs + " &#FDE047invalid NPC(s) from 'spawn' world.");
        }
        if (duplicateNpcsRemoved > 0) {
            DCMessage.sendWarning(sender, "Purged &#FFFFFF" + duplicateNpcsRemoved + " &#FDE047duplicate NPC instance(s) overlapping in the same world.");
        }
        DCMessage.sendGeneral(sender, "Refreshed &#FFFFFF" + count + " &#0CBF93NPCs with world-sensitive tags, skins, and 3D rigs.");
        return count;
    }

    public String resolveCanonId(NPC npc) {
        if (npc == null) return null;
        if (npc.data().has("dcnpc_id")) {
            return npc.data().get("dcnpc_id").toString();
        }
        String stripped = org.bukkit.ChatColor.stripColor(npc.getName()).trim();
        for (NpcCharacter c : registry.getAllCharacters()) {
            if (c.getId().equalsIgnoreCase(stripped) || c.getShortName().equalsIgnoreCase(stripped)) {
                return c.getId();
            }
        }
        return null;
    }

    public void applyEngineMode(NPC npc, NpcCharacter c) {
        if (npc == null || !npc.isSpawned() || c == null || npc.getEntity() == null) return;
        DCCosmetics.debugNpc("applyEngineMode: NPC #" + npc.getId() + " (" + npc.getName() + ") engine=" + c.getEngineType() + " model=" + c.getModelId());

        if (npc != null && c != null) {
            applyProfileEngine(npc, c.getBehaviorProfile(), c.getId(), c.getSkin(), c.getSkin());
            return;
        }
        if (c.getEngineType().equalsIgnoreCase("MODEL_ENGINE")) {
            try {
                Class<?> apiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
                java.lang.reflect.Method getModeledEntity = apiClass.getMethod("getModeledEntity", org.bukkit.entity.Entity.class);
                java.lang.reflect.Method createModel = apiClass.getMethod("createActiveModel", String.class);
                java.lang.reflect.Method getOrCreate = apiClass.getMethod("getOrCreateModeledEntity", org.bukkit.entity.Entity.class);

                // Validate blueprint existence BEFORE hiding base entity
                Object activeModel = null;
                try {
                    activeModel = createModel.invoke(null, c.getModelId());
                } catch (Throwable modelErr) {
                    DCCosmetics.getInstance().getLogger().warning("[DCNpc] ModelEngine blueprint '" + c.getModelId() + "' not found for " + c.getId() + "; keeping standard NPC visible.");
                }

                if (activeModel == null) {
                    Object existing = getModeledEntity.invoke(null, npc.getEntity());
                    if (existing != null) {
                        existing.getClass().getMethod("setBaseEntityVisible", boolean.class).invoke(existing, true);
                    }
                    return;
                }

                Object modeledEntity = getOrCreate.invoke(null, npc.getEntity());
                if (modeledEntity != null) {
                    try {
                        java.lang.reflect.Method getModels = modeledEntity.getClass().getMethod("getModels");
                        java.util.Map<?, ?> modelsMap = (java.util.Map<?, ?>) getModels.invoke(modeledEntity);
                        if (modelsMap != null) {
                            for (Object actModel : new ArrayList<>(modelsMap.values())) {
                                try { actModel.getClass().getMethod("destroy").invoke(actModel); } catch (Throwable ignored) {}
                            }
                            modelsMap.clear();
                        }
                    } catch (Throwable ignored) {}

                    for (java.lang.reflect.Method m : modeledEntity.getClass().getMethods()) {
                        if (m.getName().equals("addModel") && m.getParameterCount() == 2) {
                            m.invoke(modeledEntity, activeModel, true);
                            break;
                        }
                    }

                    modeledEntity.getClass().getMethod("setBaseEntityVisible", boolean.class).invoke(modeledEntity, false);

                    try {
                        modeledEntity.getClass().getMethod("detectPlayers").invoke(modeledEntity);
                    } catch (Throwable ignored) {}

                    Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
                        if (!npc.isSpawned() || npc.getEntity() == null) return;
                        try {
                            Object me = getModeledEntity.invoke(null, npc.getEntity());
                            if (me != null) {
                                me.getClass().getMethod("detectPlayers").invoke(me);
                            }
                        } catch (Throwable ignored) {}
                    }, 2L);
                }
            } catch (Throwable ignored) {}
        } else {
            // Only strip ModelEngine and respawn if this entity ACTUALLY had active ModelEngine 3D models attached!
            boolean hadModelEngine = false;
            try {
                Class<?> apiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
                java.lang.reflect.Method getModeledEntity = apiClass.getMethod("getModeledEntity", org.bukkit.entity.Entity.class);
                java.lang.reflect.Method removeModeledEntity = apiClass.getMethod("removeModeledEntity", org.bukkit.entity.Entity.class);

                Object existing = getModeledEntity.invoke(null, npc.getEntity());
                if (existing != null) {
                    try {
                        java.lang.reflect.Method getModels = existing.getClass().getMethod("getModels");
                        java.util.Map<?, ?> modelsMap = (java.util.Map<?, ?>) getModels.invoke(existing);
                        if (modelsMap != null && !modelsMap.isEmpty()) {
                            hadModelEngine = true;
                            for (Object actModel : new ArrayList<>(modelsMap.values())) {
                                try { actModel.getClass().getMethod("destroy").invoke(actModel); } catch (Throwable ignored) {}
                            }
                        }
                        existing.getClass().getMethod("setBaseEntityVisible", boolean.class).invoke(existing, true);
                    } catch (Throwable ignored) {}
                    try { existing.getClass().getMethod("destroy").invoke(existing); } catch (Throwable ignored) {}
                    try { removeModeledEntity.invoke(null, npc.getEntity()); } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            if (hadModelEngine && npc.isSpawned()) {
                Location loc = (npc.getEntity() != null) ? npc.getEntity().getLocation() : npc.getStoredLocation();
                npc.despawn();
                npc.spawn(loc);
                if (npc.getEntity() != null) {
                    String wName = npc.getEntity().getWorld().getName();
                    DCCosmetics.getInstance().getSkinProcessor().applyWorldSkin(npc, wName, c.getId());
                    configureNpcOverhead(npc, c, wName);
                }
            }
        }
    }

    public void hideNpcFromPlayer(Player player, NPC npc) {
        if (player == null || npc == null) return;
        DCCosmetics.debugNpc("hideNpcFromPlayer: " + player.getName() + " -> NPC #" + npc.getId() + " (" + npc.getName() + ")");
        try {
            net.citizensnpcs.api.trait.trait.PlayerFilter filter = npc.getOrAddTrait(net.citizensnpcs.api.trait.trait.PlayerFilter.class);
            filter.setDenylist();
            filter.addPlayer(player.getUniqueId());
            filter.recalculate();
        } catch (Throwable ignored) {}

        if (npc.isSpawned() && npc.getEntity() != null) {
            try {
                player.hideEntity(DCCosmetics.getInstance(), npc.getEntity());
            } catch (Throwable ignored) {}
        }
    }

    public void showNpcToPlayer(Player player, NPC npc) {
        if (player == null || npc == null) return;
        DCCosmetics.debugNpc("showNpcToPlayer: " + player.getName() + " -> NPC #" + npc.getId() + " (" + npc.getName() + ")");
        try {
            if (npc.hasTrait(net.citizensnpcs.api.trait.trait.PlayerFilter.class)) {
                net.citizensnpcs.api.trait.trait.PlayerFilter filter = npc.getTrait(net.citizensnpcs.api.trait.trait.PlayerFilter.class);
                filter.removePlayer(player.getUniqueId());
                filter.recalculate();
            }
        } catch (Throwable ignored) {}

        if (npc.isSpawned() && npc.getEntity() != null) {
            try {
                player.showEntity(DCCosmetics.getInstance(), npc.getEntity());
                for (org.bukkit.entity.Entity passenger : npc.getEntity().getPassengers()) {
                    player.showEntity(DCCosmetics.getInstance(), passenger);
                }
                npc.scheduleUpdate(NPC.NPCUpdate.PACKET);
            } catch (Throwable ignored) {}
        }
    }

    public void resetQuestlineNpcsForPlayer(Player player) {
        if (player == null) return;
        if (CitizensAPI.getPlugin() != null) {
            for (NPC npc : CitizensAPI.getNPCRegistry()) {
                String charId = resolveCanonId(npc);
                if (charId != null) {
                    String baseId = charId.contains(":") ? charId.split(":")[0] : charId;
                    NpcCharacter c = registry.getCharacter(baseId);
                    if (c != null && npc.isSpawned() && npc.getEntity() != null) {
                        String wName = npc.getEntity().getWorld().getName();
                        configureNpcOverhead(npc, c, wName);
                        DCCosmetics.getInstance().getSkinProcessor().applyWorldSkin(npc, wName, c.getId());
                    }
                }
            }
        }
        DCCosmetics.getInstance().getNpcDialogueEngine().updatePlayerNpcVisibility(player);
    }

    public boolean teleportToNpc(Player player, String charId) {
        NPC npc = zoneManager.findCitizensNpc(charId, null);
        if (npc != null && npc.isSpawned() && npc.getEntity() != null) {
            player.teleport(npc.getEntity().getLocation());
            DCMessage.sendInfo(player, "Teleported to NPC &#FFFFFF" + npc.getName() + " &8(Citizens #" + npc.getId() + ") &7in &#FFFFFF" + npc.getEntity().getWorld().getName() + "&#BAE6FD.");
            return true;
        }

        Location loc = getLiveLocation(charId);
        if (loc != null && loc.getWorld() != null) {
            player.teleport(loc);
            DCMessage.sendInfo(player, "Teleported to live anchor for &#FFFFFF" + charId + " &7in &#FFFFFF" + loc.getWorld().getName() + "&#BAE6FD.");
            return true;
        }

        DCMessage.sendError(player, "Could not locate NPC or canonical coordinates for: &#FFFFFF" + charId);
        return false;
    }
}
