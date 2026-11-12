package dc.dccosmetics.wardrobe;

import dc.dccosmetics.DCCosmetics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class WardrobeRoom {

    private Location spawn;
    private Location npcStand;
    private Location fallbackExit;
    private Location safeRoom;
    private Location tpPreview;
    private boolean returnToPrevious;

    private Location regionMin;
    private Location regionMax;

    // V2 Category Cameras (keyed by category name, e.g. "overview", "head", "chest", "boots", "teleport")
    private final Map<String, Location> cameras = new LinkedHashMap<>();

    public void load() {
        File file = WardrobeManager.getWardrobeConfigFile();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String worldName = config.getString("room.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            DCCosmetics.getInstance().getLogger().warning("[Wardrobe] World '" + worldName + "' not found! Wardrobe might not function properly.");
            return;
        }

        spawn        = loadLoc(world, config, "room.spawn");
        npcStand     = loadLoc(world, config, "room.npc");
        fallbackExit = loadLoc(world, config, "room.fallback_exit");
        safeRoom     = config.contains("room.safe_room") ? loadLoc(world, config, "room.safe_room") : null;
        if (config.contains("room.tp_preview.x")) {
            String tpWorldName = config.getString("room.tp_preview.world", worldName);
            World tpWorld = Bukkit.getWorld(tpWorldName);
            tpPreview = tpWorld != null ? loadLoc(tpWorld, config, "room.tp_preview") : null;
        } else {
            tpPreview = null;
        }

        returnToPrevious = config.getBoolean("room.return_to_previous_location", true);

        if (config.contains("room.region.min.x")) {
            regionMin = new Location(world,
                config.getDouble("room.region.min.x"),
                config.getDouble("room.region.min.y"),
                config.getDouble("room.region.min.z"));
        }
        if (config.contains("room.region.max.x")) {
            regionMax = new Location(world,
                config.getDouble("room.region.max.x"),
                config.getDouble("room.region.max.y"),
                config.getDouble("room.region.max.z"));
        }

        // Load V2 Category Cameras
        cameras.clear();
        if (config.isConfigurationSection("room.cameras")) {
            for (String key : config.getConfigurationSection("room.cameras").getKeys(false)) {
                Location loc = loadLoc(world, config, "room.cameras." + key);
                if (loc != null) {
                    cameras.put(key.toLowerCase(), loc);
                }
            }
        }
    }

    private Location loadLoc(World world, YamlConfiguration config, String path) {
        if (!config.contains(path + ".x")) return null;
        double x     = config.getDouble(path + ".x");
        double y     = config.getDouble(path + ".y");
        double z     = config.getDouble(path + ".z");
        float  yaw   = (float) config.getDouble(path + ".yaw");
        float  pitch = (float) config.getDouble(path + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    // ── Savers ────────────────────────────────────────────────────

    public void saveSpawn(Location loc) {
        this.spawn = loc;
        saveLoc("room.spawn", loc);
    }

    public void saveNpcStand(Location loc) {
        this.npcStand = loc;
        saveLoc("room.npc", loc);
    }

    public void saveFallbackExit(Location loc) {
        this.fallbackExit = loc;
        saveLoc("room.fallback_exit", loc);
    }

    public void saveSafeRoom(Location loc) {
        this.safeRoom = loc;
        saveLoc("room.safe_room", loc);
    }

    public void saveTpPreview(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        this.tpPreview = loc.clone();
        File file = WardrobeManager.getWardrobeConfigFile();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("room.tp_preview.world", loc.getWorld().getName());
        config.set("room.tp_preview.x",     loc.getX());
        config.set("room.tp_preview.y",     loc.getY());
        config.set("room.tp_preview.z",     loc.getZ());
        config.set("room.tp_preview.yaw",   loc.getYaw());
        config.set("room.tp_preview.pitch", loc.getPitch());
        try { config.save(file); } catch (Exception e) { e.printStackTrace(); }
    }

    public void saveCamera(String category, Location loc) {
        if (category == null || loc == null) return;
        cameras.put(category.toLowerCase(), loc.clone());
        saveLoc("room.cameras." + category.toLowerCase(), loc);
    }

    public void saveRegion(Location min, Location max) {
        this.regionMin = min;
        this.regionMax = max;
        File file = WardrobeManager.getWardrobeConfigFile();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        config.set("room.region.min.x", min.getX());
        config.set("room.region.min.y", min.getY());
        config.set("room.region.min.z", min.getZ());
        config.set("room.region.max.x", max.getX());
        config.set("room.region.max.y", max.getY());
        config.set("room.region.max.z", max.getZ());

        try { config.save(file); } catch (Exception e) { e.printStackTrace(); }
    }

    private void saveLoc(String path, Location loc) {
        File file = WardrobeManager.getWardrobeConfigFile();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        config.set("room.world", loc.getWorld().getName());
        config.set(path + ".x",     loc.getX());
        config.set(path + ".y",     loc.getY());
        config.set(path + ".z",     loc.getZ());
        config.set(path + ".yaw",   loc.getYaw());
        config.set(path + ".pitch", loc.getPitch());

        try { config.save(file); } catch (Exception e) { e.printStackTrace(); }
    }

    // ── Getters ───────────────────────────────────────────────────

    public Location getSpawn() { return spawn; }

    public Location getNpcStand() {
        if (npcStand != null) return npcStand;
        if (spawn != null) {
            Vector forward = spawn.getDirection().normalize();
            Location loc = spawn.clone().add(forward.multiply(4));
            loc.setDirection(forward.multiply(-1));
            loc.setPitch(0);
            return loc;
        }
        return null;
    }

    /** Where players' bodies are held during camera mode. Falls back to spawn if not set. */
    public Location getSafeRoom() {
        return safeRoom != null ? safeRoom : spawn;
    }

    /** Dedicated open-sky preview location for full 3D camera teleport effects. */
    public Location getTpPreview() {
        return tpPreview != null ? tpPreview.clone() : null;
    }

    public Location getFallbackExit() { return fallbackExit; }
    public boolean isReturnToPrevious() { return returnToPrevious; }

    public Map<String, Location> getCameras() {
        return Collections.unmodifiableMap(cameras);
    }

    /**
     * Retrieves camera location for a given category.
     * If configured in wardrobe.yml under room.cameras.<cat>, returns that location.
     * Otherwise dynamically computes a precise camera location facing the mannequin.
     */
    public Location getCamera(String category) {
        if (category == null) category = "overview";
        String cat = category.toLowerCase();

        if (cameras.containsKey(cat)) {
            return cameras.get(cat).clone();
        }

        // Dynamic fallback calculations relative to NPC
        Location npc = getNpcStand();
        Location root = getSpawn();
        if (root == null) return null;

        if ("overview".equals(cat)) {
            if (npc != null && root.getWorld() != null && root.getWorld().equals(npc.getWorld())) {
                Vector toNpc = new Vector(npc.getX() - root.getX(), 0, npc.getZ() - root.getZ());
                if (toNpc.lengthSquared() > 0.25) {
                    Vector fwd = toNpc.normalize();
                    double nudge = Math.min(0.85, toNpc.length() * 0.25);
                    Location eye = root.clone().add(fwd.clone().multiply(nudge)).add(0, 1.62, 0);
                    Location target = npc.clone().add(0, 1.15, 0);
                    eye.setDirection(target.toVector().subtract(eye.toVector()).normalize());
                    return eye;
                }
            }
            Location eye = root.clone().add(0, 1.62, 0);
            eye.setYaw(root.getYaw());
            eye.setPitch(root.getPitch());
            return eye;
        }

        if (npc == null) {
            return root.clone().add(0, 1.62, 0);
        }

        Vector forward = new Vector(npc.getX() - root.getX(), 0, npc.getZ() - root.getZ()).normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize();

        Location targetPoint;
        Location cameraEye;

        switch (cat) {
            case "head":
                targetPoint = npc.clone().add(0, 1.65, 0);
                cameraEye = targetPoint.clone()
                    .subtract(forward.clone().multiply(1.8))
                    .add(right.clone().multiply(0.25))
                    .add(0, 0.05, 0);
                break;
            case "chest":
                targetPoint = npc.clone().add(0, 1.20, 0);
                cameraEye = targetPoint.clone()
                    .subtract(forward.clone().multiply(2.2))
                    .add(right.clone().multiply(0.20));
                break;
            case "boots":
                targetPoint = npc.clone().add(0, 0.35, 0);
                cameraEye = targetPoint.clone()
                    .subtract(forward.clone().multiply(2.0))
                    .add(right.clone().multiply(0.25))
                    .add(0, 0.45, 0);
                break;
            case "teleport":
                targetPoint = npc.clone().add(0, 0.10, 0);
                cameraEye = targetPoint.clone()
                    .subtract(forward.clone().multiply(3.0))
                    .add(0, 1.10, 0);
                break;
            default:
                Location eye = root.clone().add(0, 1.62, 0);
                eye.setYaw(root.getYaw());
                eye.setPitch(root.getPitch());
                return eye;
        }

        Vector look = targetPoint.toVector().subtract(cameraEye.toVector()).normalize();
        cameraEye.setDirection(look);
        return cameraEye;
    }

    public boolean isInsideRegion(Location loc) {
        if (regionMin == null || regionMax == null) return false;
        if (loc.getWorld() != regionMin.getWorld()) return false;
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        double minX = Math.min(regionMin.getX(), regionMax.getX());
        double maxX = Math.max(regionMin.getX(), regionMax.getX());
        double minY = Math.min(regionMin.getY(), regionMax.getY());
        double maxY = Math.max(regionMin.getY(), regionMax.getY());
        double minZ = Math.min(regionMin.getZ(), regionMax.getZ());
        double maxZ = Math.max(regionMin.getZ(), regionMax.getZ());
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
