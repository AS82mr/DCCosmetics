package dc.dcessentials.modules.npc.model;

import org.bukkit.Material;

/**
 * Represents a storyline quest stage tied to an NPC.
 */
public class NpcStage {
    private final String characterId;
    private final int stageNumber;
    private String title;
    private String requires;
    private String objective;
    private String dialogueLine;
    private String completionLine;
    private String nextCharacterId;
    private double cashReward;
    private String rewardItemName;
    private String rewardItemDesc;
    private Material icon;
    private String executionCommand;
    private boolean walkOnComplete;
    private org.bukkit.inventory.ItemStack customRewardItem;
    private String completionBehavior = "STAY"; // STAY, POOF, WALK
    private org.bukkit.Location stageLocation;
    private String boundWarpAlias;

    public NpcStage(String characterId, int stageNumber, String title, String requires,
                    String objective, String dialogueLine, String completionLine,
                    String nextCharacterId, double cashReward, String rewardItemName,
                    String rewardItemDesc, Material icon, String executionCommand,
                    boolean walkOnComplete) {
        this.characterId = characterId;
        this.stageNumber = stageNumber;
        this.title = title;
        this.requires = requires;
        this.objective = objective;
        this.dialogueLine = dialogueLine;
        this.completionLine = completionLine;
        this.nextCharacterId = nextCharacterId;
        this.cashReward = cashReward;
        this.rewardItemName = rewardItemName;
        this.rewardItemDesc = rewardItemDesc;
        this.icon = icon != null ? icon : Material.AMETHYST_SHARD;
        this.executionCommand = executionCommand;
        this.walkOnComplete = walkOnComplete;
    }

    public String getCharacterId() { return characterId; }
    public int getStageNumber() { return stageNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getRequires() { return requires; }
    public void setRequires(String requires) { this.requires = requires; }
    public String getObjective() { return objective; }
    public void setObjective(String objective) { this.objective = objective; }
    public String getDialogueLine() { return dialogueLine; }
    public void setDialogueLine(String dialogueLine) { this.dialogueLine = dialogueLine; }
    public String getCompletionLine() { return completionLine; }
    public void setCompletionLine(String completionLine) { this.completionLine = completionLine; }
    public String getNextCharacterId() { return nextCharacterId; }
    public void setNextCharacterId(String nextCharacterId) { this.nextCharacterId = nextCharacterId; }
    public double getCashReward() { return cashReward; }
    public void setCashReward(double cashReward) { this.cashReward = cashReward; }
    public String getRewardItemName() { return rewardItemName; }
    public void setRewardItemName(String rewardItemName) { this.rewardItemName = rewardItemName; }
    public String getRewardItemDesc() { return rewardItemDesc; }
    public void setRewardItemDesc(String rewardItemDesc) { this.rewardItemDesc = rewardItemDesc; }
    public Material getIcon() { return icon; }
    public void setIcon(Material icon) { this.icon = icon; }
    public String getExecutionCommand() { return executionCommand; }
    public void setExecutionCommand(String executionCommand) { this.executionCommand = executionCommand; }
    public boolean isWalkOnComplete() { return walkOnComplete; }
    public void setWalkOnComplete(boolean walkOnComplete) { this.walkOnComplete = walkOnComplete; }

    public org.bukkit.inventory.ItemStack getCustomRewardItem() { return customRewardItem; }
    public void setCustomRewardItem(org.bukkit.inventory.ItemStack customRewardItem) { this.customRewardItem = customRewardItem; }

    public String getCompletionBehavior() { return completionBehavior != null ? completionBehavior : "STAY"; }
    public void setCompletionBehavior(String completionBehavior) { this.completionBehavior = completionBehavior; }

    public org.bukkit.Location getStageLocation() { return stageLocation; }
    public void setStageLocation(org.bukkit.Location stageLocation) { this.stageLocation = stageLocation; }

    public String getBoundWarpAlias() { return boundWarpAlias; }
    public void setBoundWarpAlias(String boundWarpAlias) { this.boundWarpAlias = boundWarpAlias; }
}
