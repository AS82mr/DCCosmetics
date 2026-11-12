package dc.dcessentials.modules.event.model;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a scheduled server event (World Cycle, KotH, Adapt Boost, or Race).
 */
public class ServerEvent {

    private final String id;
    private String title;
    private String subtitle;
    private List<String> lore = new ArrayList<>();
    private List<String> perks = new ArrayList<>();
    private String worldName;
    private Material icon;
    private int customModelData = 0;
    private int durationMinutes = 60;
    private int intervalMinutes = 240;
    private boolean worldCycle = false;
    private String evacuationWorld = "lowercity";
    private String warpTarget = "";
    private String startCommand = "none";
    private String endCommand = "none";
    private String rewardSummary = "Exclusive Event Tokens & Mastery XP";
    private String soundEffect = "UI_TOAST_CHALLENGE_COMPLETE";

    // Runtime state tracking
    private boolean running = false;
    private long startTimeMillis = 0;
    private long endTimeMillis = 0;
    private long nextRunTimeMillis = 0;

    public ServerEvent(String id, String title, String subtitle, String worldName, Material icon,
                       int durationMinutes, int intervalMinutes, boolean worldCycle) {
        this.id = id.toLowerCase();
        this.title = title;
        this.subtitle = subtitle;
        this.worldName = worldName;
        this.icon = icon != null ? icon : Material.NETHER_STAR;
        this.durationMinutes = durationMinutes;
        this.intervalMinutes = intervalMinutes;
        this.worldCycle = worldCycle;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public List<String> getLore() { return lore; }
    public void setLore(List<String> lore) { this.lore = lore; }
    public List<String> getPerks() { return perks; }
    public void setPerks(List<String> perks) { this.perks = perks; }
    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }
    public Material getIcon() { return icon; }
    public void setIcon(Material icon) { this.icon = icon; }
    public int getCustomModelData() { return customModelData; }
    public void setCustomModelData(int customModelData) { this.customModelData = customModelData; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public int getIntervalMinutes() { return intervalMinutes; }
    public void setIntervalMinutes(int intervalMinutes) { this.intervalMinutes = intervalMinutes; }
    public boolean isWorldCycle() { return worldCycle; }
    public void setWorldCycle(boolean worldCycle) { this.worldCycle = worldCycle; }
    public String getEvacuationWorld() { return evacuationWorld; }
    public void setEvacuationWorld(String evacuationWorld) { this.evacuationWorld = evacuationWorld; }
    public String getWarpTarget() { return warpTarget; }
    public void setWarpTarget(String warpTarget) { this.warpTarget = warpTarget; }
    public String getStartCommand() { return startCommand; }
    public void setStartCommand(String startCommand) { this.startCommand = startCommand; }
    public String getEndCommand() { return endCommand; }
    public void setEndCommand(String endCommand) { this.endCommand = endCommand; }
    public String getRewardSummary() { return rewardSummary; }
    public void setRewardSummary(String rewardSummary) { this.rewardSummary = rewardSummary; }
    public String getSoundEffect() { return soundEffect; }
    public void setSoundEffect(String soundEffect) { this.soundEffect = soundEffect; }

    // Runtime state getters/setters
    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }
    public long getStartTimeMillis() { return startTimeMillis; }
    public void setStartTimeMillis(long startTimeMillis) { this.startTimeMillis = startTimeMillis; }
    public long getEndTimeMillis() { return endTimeMillis; }
    public void setEndTimeMillis(long endTimeMillis) { this.endTimeMillis = endTimeMillis; }
    public long getNextRunTimeMillis() { return nextRunTimeMillis; }
    public void setNextRunTimeMillis(long nextRunTimeMillis) { this.nextRunTimeMillis = nextRunTimeMillis; }

    public long getRemainingSeconds() {
        if (!running) return 0;
        long diff = (endTimeMillis - System.currentTimeMillis()) / 1000L;
        return Math.max(0, diff);
    }

    public long getSecondsUntilStart() {
        if (running) return 0;
        long diff = (nextRunTimeMillis - System.currentTimeMillis()) / 1000L;
        return Math.max(0, diff);
    }

    public String getFormattedRemainingTime() {
        long sec = getRemainingSeconds();
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) {
            return String.format("%02dh %02dm %02ds", h, m, s);
        } else {
            return String.format("%02dm %02ds", m, s);
        }
    }

    public String getFormattedUntilStartTime() {
        long sec = getSecondsUntilStart();
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) {
            return String.format("%02dh %02dm %02ds", h, m, s);
        } else {
            return String.format("%02dm %02ds", m, s);
        }
    }
}
