package dc.dccosmetics.wardrobe;

import dc.dccosmetics.DCCosmetics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class WardrobeRoom {

    private Location spawn;
    private Location npcStand;
    private Location fallbackExit;
    private Location safeRoom;     // ← NEW: where players are teleported during camera mode
    private boolean returnToPrevious;

    private Location regionMin;
    private Location regionMax;

    public void load() {
        File file = new File(DCCosmetics.getInstance().getDataFolder(), "wardrobe.yml");
        if (!file.exists()) {
            DCCosmetics.getInstance().saveResource("wardrobe.yml", false);
        }

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
    }

    private Location loadLoc(World world, YamlConfiguration config, String path) {
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

    public void saveRegion(Location min, Location max) {
        this.regionMin = min;
        this.regionMax = max;
        File file = new File(DCCosmetics.getInstance().getDataFolder(), "wardrobe.yml");
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
        File file = new File(DCCosmetics.getInstance().getDataFolder(), "wardrobe.yml");
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
            org.bukkit.util.Vector forward = spawn.getDirection().normalize();
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

    public Location getFallbackExit() { return fallbackExit; }
    public boolean isReturnToPrevious() { return returnToPrevious; }

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
