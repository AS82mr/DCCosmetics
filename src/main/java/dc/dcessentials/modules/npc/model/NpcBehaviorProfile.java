package dc.dcessentials.modules.npc.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared appearance, ModelEngine animation, Smart Mode, patrol path, and baked-skin
 * configuration shared identically between storyline DCNPCs and out-of-storyline Decoration NPCs.
 */
public class NpcBehaviorProfile {

    public static final String ENGINE_CITIZENS = "CITIZENS";
    public static final String ENGINE_MODEL_ENGINE = "MODEL_ENGINE";

    public static final String ANIM_DEFAULT = "DEFAULT";
    public static final String ANIM_FIXED = "FIXED";
    public static final String ANIM_SMART = "SMART";
    public static final String ANIM_SMART_PATROL = "SMART_PATROL";

    private String engineType = ENGINE_CITIZENS;
    private String modelId = "scene_townsfolk_vol3";
    private String bakedModelId;
    private String bakedModelBwId;

    private String animationMode = ANIM_DEFAULT;
    private String fixedAnimation = "idle";
    private int smartCycleSeconds = 12;
    private int patrolRestSeconds = 180;
    private int skinRevision = 1;

    private final List<Location> patrolWaypoints = new ArrayList<>();

    public String getEngineType() { return engineType; }
    public void setEngineType(String engineType) {
        this.engineType = (engineType != null && engineType.equalsIgnoreCase(ENGINE_MODEL_ENGINE))
                ? ENGINE_MODEL_ENGINE : ENGINE_CITIZENS;
    }

    public boolean isModelEngine() {
        return ENGINE_MODEL_ENGINE.equalsIgnoreCase(engineType);
    }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) {
        if (modelId != null && !modelId.isEmpty()) {
            this.modelId = modelId;
        }
    }

    public String getBakedModelId() { return bakedModelId; }
    public void setBakedModelId(String bakedModelId) { this.bakedModelId = bakedModelId; }

    public String getBakedModelBwId() { return bakedModelBwId; }
    public void setBakedModelBwId(String bakedModelBwId) { this.bakedModelBwId = bakedModelBwId; }

    public String resolveEffectiveModelId(boolean isBwWorld) {
        if (isBwWorld && bakedModelBwId != null && !bakedModelBwId.isEmpty()) {
            return bakedModelBwId;
        }
        if (bakedModelId != null && !bakedModelId.isEmpty()) {
            return bakedModelId;
        }
        return modelId;
    }

    public String getAnimationMode() { return animationMode; }
    public void setAnimationMode(String animationMode) {
        if (animationMode == null) {
            this.animationMode = ANIM_DEFAULT;
            return;
        }
        String norm = animationMode.toUpperCase();
        if (norm.equals(ANIM_FIXED) || norm.equals(ANIM_SMART) || norm.equals(ANIM_SMART_PATROL)) {
            this.animationMode = norm;
        } else {
            this.animationMode = ANIM_DEFAULT;
        }
    }

    public String getFixedAnimation() { return fixedAnimation; }
    public void setFixedAnimation(String fixedAnimation) { this.fixedAnimation = fixedAnimation; }

    public int getSmartCycleSeconds() { return smartCycleSeconds; }
    public void setSmartCycleSeconds(int smartCycleSeconds) {
        this.smartCycleSeconds = Math.max(4, Math.min(300, smartCycleSeconds));
    }

    public int getPatrolRestSeconds() { return patrolRestSeconds; }
    public void setPatrolRestSeconds(int patrolRestSeconds) {
        this.patrolRestSeconds = Math.max(10, Math.min(1800, patrolRestSeconds));
    }

    public int getSkinRevision() { return skinRevision; }
    public void setSkinRevision(int skinRevision) { this.skinRevision = Math.max(1, skinRevision); }
    public int bumpSkinRevision() { return ++this.skinRevision; }

    public List<Location> getPatrolWaypoints() { return patrolWaypoints; }
    public void setPatrolWaypoints(List<Location> waypoints) {
        this.patrolWaypoints.clear();
        if (waypoints != null) {
            for (Location loc : waypoints) {
                if (loc != null) this.patrolWaypoints.add(loc.clone());
            }
        }
    }

    public void loadFrom(ConfigurationSection sec) {
        if (sec == null) return;
        if (sec.contains("engine_type")) setEngineType(sec.getString("engine_type"));
        if (sec.contains("model_id")) setModelId(sec.getString("model_id"));
        if (sec.contains("baked_model_id")) setBakedModelId(sec.getString("baked_model_id"));
        if (sec.contains("baked_model_bw_id")) setBakedModelBwId(sec.getString("baked_model_bw_id"));
        if (sec.contains("animation_mode")) setAnimationMode(sec.getString("animation_mode"));
        if (sec.contains("fixed_animation")) setFixedAnimation(sec.getString("fixed_animation", "idle"));
        if (sec.contains("smart_cycle_seconds")) setSmartCycleSeconds(sec.getInt("smart_cycle_seconds", 12));
        if (sec.contains("patrol_rest_seconds")) setPatrolRestSeconds(sec.getInt("patrol_rest_seconds", 180));
        if (sec.contains("skin_revision")) setSkinRevision(sec.getInt("skin_revision", 1));

        patrolWaypoints.clear();
        List<String> rawWps = sec.getStringList("patrol_waypoints");
        for (String line : rawWps) {
            Location parsed = parseWaypoint(line);
            if (parsed != null) patrolWaypoints.add(parsed);
        }
    }

    public void saveTo(ConfigurationSection sec) {
        if (sec == null) return;
        sec.set("engine_type", engineType);
        sec.set("model_id", modelId);
        sec.set("baked_model_id", bakedModelId);
        sec.set("baked_model_bw_id", bakedModelBwId);
        sec.set("animation_mode", animationMode);
        sec.set("fixed_animation", fixedAnimation);
        sec.set("smart_cycle_seconds", smartCycleSeconds);
        sec.set("patrol_rest_seconds", patrolRestSeconds);
        sec.set("skin_revision", skinRevision);

        List<String> serialized = new ArrayList<>();
        for (Location loc : patrolWaypoints) {
            if (loc != null && loc.getWorld() != null) {
                serialized.add(String.format(java.util.Locale.US, "%s;%.3f;%.3f;%.3f;%.2f",
                        loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw()));
            }
        }
        sec.set("patrol_waypoints", serialized);
    }

    private static Location parseWaypoint(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String[] parts = raw.split(";");
        if (parts.length < 4) return null;
        World w = Bukkit.getWorld(parts[0]);
        if (w == null && !Bukkit.getWorlds().isEmpty()) {
            w = Bukkit.getWorlds().get(0);
        }
        if (w == null) return null;
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length >= 5 ? Float.parseFloat(parts[4]) : 0.0f;
            return new Location(w, x, y, z, yaw, 0.0f);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
