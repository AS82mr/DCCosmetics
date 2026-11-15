package dc.dcessentials.modules.npc.model;

import org.bukkit.Material;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a canon NPC character across the Dragoncote multiverse.
 */
public class NpcCharacter {
    private final String id;
    private final String shortName;
    private final String fullName;
    private final String hexColor;
    private String skin;
    private final int sector;
    private final String faction;
    private final String role;
    private final String quote;
    private final Material icon;
    private final String deployAdvice;
    private String zoneId;
    private String serviceCommand;
    private String serviceTitle;
    private String completionBehavior = "STAY_IN_WORLD"; // STAY_IN_WORLD, WALK_PATROL_PATH, POOF_DESPAWN
    private String skinSnow;
    private String skinBw;
    private String engineType = "CITIZENS"; // CITIZENS, MODEL_ENGINE
    private String modelId;
    private final NpcBehaviorProfile behaviorProfile = new NpcBehaviorProfile();
    private String boundWarpAlias;

    private final List<String> companionSceneMobIds = new ArrayList<>();
    private final List<String> ambientLines = new ArrayList<>();
    private final Map<Integer, NpcStage> stages = new HashMap<>();

    public NpcCharacter(String id, String shortName, String fullName, String hexColor, String skin,
                        int sector, String faction, String role, String quote, Material icon,
                        String deployAdvice, String zoneId) {
        this.id = id.toLowerCase();
        this.shortName = shortName;
        this.fullName = fullName;
        this.hexColor = hexColor;
        this.skin = skin;
        this.sector = sector;
        this.faction = faction;
        this.role = role;
        this.quote = quote;
        this.icon = icon != null ? icon : Material.PLAYER_HEAD;
        this.deployAdvice = deployAdvice;
        this.zoneId = zoneId;
    }

    public String getId() { return id; }
    public String getShortName() { return shortName; }
    public String getFullName() { return fullName; }
    public String getHexColor() { return hexColor; }
    public String getSkin() { return skin; }
    public void setSkin(String skin) { this.skin = skin; }
    public int getSector() { return sector; }
    public String getFaction() { return faction; }
    public String getRole() { return role; }
    public String getQuote() { return quote; }
    public Material getIcon() { return icon; }
    public String getDeployAdvice() { return deployAdvice; }
    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }

    public String getServiceCommand() { return serviceCommand; }
    public void setServiceCommand(String serviceCommand) { this.serviceCommand = serviceCommand; }

    public String getServiceTitle() { return serviceTitle; }
    public void setServiceTitle(String serviceTitle) { this.serviceTitle = serviceTitle; }

    public List<String> getAmbientLines() { return ambientLines; }
    public void addAmbientLine(String line) { if (line != null && !line.isEmpty()) ambientLines.add(line); }

    public List<String> getCompanionSceneMobIds() { return companionSceneMobIds; }
    public void addCompanionSceneMobId(String mobId) {
        if (mobId != null && !mobId.trim().isEmpty() && !companionSceneMobIds.contains(mobId)) {
            companionSceneMobIds.add(mobId);
        }
    }

    public String getCompletionBehavior() { return completionBehavior != null ? completionBehavior : "STAY_IN_WORLD"; }
    public void setCompletionBehavior(String behavior) { this.completionBehavior = behavior; }

    public String getSkinSnow() { return skinSnow != null ? skinSnow : skin; }
    public void setSkinSnow(String skinSnow) { this.skinSnow = skinSnow; }

    public String getSkinBw() { return skinBw != null ? skinBw : skin; }
    public void setSkinBw(String skinBw) { this.skinBw = skinBw; }

    public String getEngineType() { return behaviorProfile.getEngineType(); }
    public void setEngineType(String engineType) {
        this.engineType = engineType;
        behaviorProfile.setEngineType(engineType);
    }

    public String getModelId() { return modelId != null ? modelId : behaviorProfile.getModelId(); }
    public void setModelId(String modelId) {
        this.modelId = modelId;
        behaviorProfile.setModelId(modelId);
    }

    public NpcBehaviorProfile getBehaviorProfile() { return behaviorProfile; }

    public String getBoundWarpAlias() { return boundWarpAlias; }
    public void setBoundWarpAlias(String boundWarpAlias) { this.boundWarpAlias = boundWarpAlias; }

    public Map<Integer, NpcStage> getStages() { return stages; }
    public void addStage(NpcStage stage) { stages.put(stage.getStageNumber(), stage); }
    public void removeStage(int stageNumber) { stages.remove(stageNumber); }
    public NpcStage getStage(int stageNumber) { return stages.get(stageNumber); }
}
