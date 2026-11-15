package dc.dcessentials.modules.npc.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Out-of-storyline Decoration / Ambient DCNPC.
 * Has full visual/animation/patrol/skin/multi-world feature parity with storyline DCNPCs
 * without quests, stages, prerequisites, or rewards.
 */
public class DecorationNpc {

    private final String id;
    private String displayName;
    private boolean showNameplate = true;
    private boolean syncSpawnWorlds = true;
    private String skinId; // Directory name under /plugins/DCEssentials/npcs/ (defaults to id)
    private String fallbackSkinName = "Steve";
    private Location anchorLocation;
    private final List<String> ambientLines = new ArrayList<>();
    private final NpcBehaviorProfile profile = new NpcBehaviorProfile();

    public DecorationNpc(String id, String displayName, Location anchorLocation) {
        this.id = id.toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
        this.displayName = (displayName != null && !displayName.isEmpty()) ? displayName : "<#E2F6A8>" + this.id;
        this.skinId = "deco_" + this.id;
        this.anchorLocation = anchorLocation != null ? anchorLocation.clone() : null;
    }

    public String getId() { return id; }
    public String getCitizensTag() { return "dcnpc_deco:" + id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public boolean isShowNameplate() { return showNameplate; }
    public void setShowNameplate(boolean showNameplate) { this.showNameplate = showNameplate; }

    public boolean isSyncSpawnWorlds() { return syncSpawnWorlds; }
    public void setSyncSpawnWorlds(boolean syncSpawnWorlds) { this.syncSpawnWorlds = syncSpawnWorlds; }

    public String getSkinId() { return (skinId != null && !skinId.isEmpty()) ? skinId : ("deco_" + id); }
    public void setSkinId(String skinId) { this.skinId = skinId; }

    public String getFallbackSkinName() { return fallbackSkinName; }
    public void setFallbackSkinName(String fallbackSkinName) { this.fallbackSkinName = fallbackSkinName; }

    public Location getAnchorLocation() { return anchorLocation; }
    public void setAnchorLocation(Location anchorLocation) {
        this.anchorLocation = anchorLocation != null ? anchorLocation.clone() : null;
    }

    public List<String> getAmbientLines() { return ambientLines; }

    public NpcBehaviorProfile getProfile() { return profile; }

    public static DecorationNpc loadFrom(String id, ConfigurationSection sec) {
        if (sec == null) return null;
        String worldName = sec.getString("world", "spawn_main");
        World w = Bukkit.getWorld(worldName);
        if (w == null && (worldName.equalsIgnoreCase("spawn_main") || worldName.equalsIgnoreCase("spawn"))) {
            w = Bukkit.getWorld("main");
        }
        Location loc = null;
        if (w != null && sec.contains("x")) {
            loc = new Location(
                    w,
                    sec.getDouble("x"),
                    sec.getDouble("y"),
                    sec.getDouble("z"),
                    (float) sec.getDouble("yaw", 0.0),
                    (float) sec.getDouble("pitch", 0.0)
            );
        }

        DecorationNpc deco = new DecorationNpc(id, sec.getString("display_name", "<#E2F6A8>" + id), loc);
        deco.setShowNameplate(sec.getBoolean("show_nameplate", true));
        deco.setSyncSpawnWorlds(sec.getBoolean("sync_spawn_worlds", true));
        deco.setSkinId(sec.getString("skin_id", "deco_" + id));
        deco.setFallbackSkinName(sec.getString("fallback_skin", "Steve"));
        deco.getAmbientLines().addAll(sec.getStringList("ambient_lines"));
        deco.getProfile().loadFrom(sec.getConfigurationSection("behavior"));
        return deco;
    }

    public void saveTo(ConfigurationSection sec) {
        if (sec == null) return;
        sec.set("display_name", displayName);
        sec.set("show_nameplate", showNameplate);
        sec.set("sync_spawn_worlds", syncSpawnWorlds);
        sec.set("skin_id", getSkinId());
        sec.set("fallback_skin", fallbackSkinName);
        sec.set("ambient_lines", ambientLines);
        if (anchorLocation != null && anchorLocation.getWorld() != null) {
            sec.set("world", anchorLocation.getWorld().getName());
            sec.set("x", anchorLocation.getX());
            sec.set("y", anchorLocation.getY());
            sec.set("z", anchorLocation.getZ());
            sec.set("yaw", anchorLocation.getYaw());
            sec.set("pitch", anchorLocation.getPitch());
        }
        ConfigurationSection behSec = sec.createSection("behavior");
        profile.saveTo(behSec);
    }
}
