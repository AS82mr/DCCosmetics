package dc.dcessentials.modules.pets.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Specification for a pet's automatic active combat ability, including explicit
 * trigger condition, scaled Lv 1 -> Lv 100 cooldown & power, range, and ModelEngine VFX.
 */
public class PetSkillSpec {
    private final String skillId;
    private final String displayName;
    private final PetSkillTrigger trigger;
    private final String triggerConditionText;
    private final double activationThreshold;
    private final long maxCooldownMs;
    private final long minCooldownMs;
    private final double minPower;
    private final double maxPower;
    private final double range;
    private final String vfxModelId;
    private final String vfxAnimation;
    private final List<String> descriptionLines;

    public PetSkillSpec(String skillId,
                        String displayName,
                        long baseCooldownMs,
                        long cooldownReductionPerLevelMs,
                        double basePower,
                        double powerPerLevel,
                        double range,
                        String vfxModelId) {
        this(
                skillId,
                displayName,
                PetSkillTrigger.ON_ATTACK,
                PetSkillTrigger.ON_ATTACK.getDefaultDescription(),
                0.75,
                baseCooldownMs,
                Math.max(1500L, baseCooldownMs - cooldownReductionPerLevelMs * 99L),
                basePower,
                basePower + powerPerLevel * 99.0,
                range,
                vfxModelId,
                "execute",
                List.of("Deals &f{pwr} &7damage within &f{range}m &7every &f{cd}s&7.")
        );
    }

    public PetSkillSpec(String skillId,
                        String displayName,
                        PetSkillTrigger trigger,
                        String triggerConditionText,
                        double activationThreshold,
                        long maxCooldownMs,
                        long minCooldownMs,
                        double minPower,
                        double maxPower,
                        double range,
                        String vfxModelId,
                        String vfxAnimation,
                        List<String> descriptionLines) {
        this.skillId = skillId != null ? skillId.toLowerCase(Locale.ROOT) : "unknown";
        this.displayName = displayName != null ? displayName : "Signature Skill";
        this.trigger = trigger != null ? trigger : PetSkillTrigger.ON_ATTACK;
        this.triggerConditionText = (triggerConditionText != null && !triggerConditionText.isBlank())
                ? triggerConditionText
                : this.trigger.getDefaultDescription();
        this.activationThreshold = activationThreshold;
        this.maxCooldownMs = Math.max(1500L, maxCooldownMs);
        this.minCooldownMs = Math.max(1200L, Math.min(this.maxCooldownMs, minCooldownMs));
        this.minPower = minPower;
        this.maxPower = Math.max(minPower, maxPower);
        this.range = Math.max(3.0, range);
        this.vfxModelId = vfxModelId != null ? vfxModelId : "";
        this.vfxAnimation = (vfxAnimation != null && !vfxAnimation.isBlank()) ? vfxAnimation : "execute";
        this.descriptionLines = descriptionLines != null ? new ArrayList<>(descriptionLines) : new ArrayList<>();
    }

    public String getSkillId() { return skillId; }
    public String getDisplayName() { return displayName; }
    public PetSkillTrigger getTrigger() { return trigger; }
    public String getTriggerConditionText() { return triggerConditionText; }
    public double getActivationThreshold() { return activationThreshold; }
    public long getMaxCooldownMs() { return maxCooldownMs; }
    public long getMinCooldownMs() { return minCooldownMs; }
    public double getMinPower() { return minPower; }
    public double getMaxPower() { return maxPower; }
    public double getRange() { return range; }
    public String getVfxModelId() { return vfxModelId; }
    public String getVfxAnimation() { return vfxAnimation; }
    public List<String> getDescriptionLines() { return descriptionLines; }

    public long getEffectiveCooldownMs(int level) {
        return getEffectiveCooldownMs(level, 100);
    }

    public long getEffectiveCooldownMs(int level, int maxLevel) {
        double t = progress(level, maxLevel);
        double cd = maxCooldownMs - (maxCooldownMs - minCooldownMs) * t;
        return Math.max(1200L, Math.round(cd));
    }

    public double getEffectivePower(int level) {
        return getEffectivePower(level, 100);
    }

    public double getEffectivePower(int level, int maxLevel) {
        double t = progress(level, maxLevel);
        double pwr = minPower + (maxPower - minPower) * t;
        return Math.round(pwr * 10.0) / 10.0;
    }

    public List<String> formatDescriptionLines(int level, int maxLevel, double cdMult, double powerMult) {
        double effPower = Math.round(getEffectivePower(level, maxLevel) * powerMult * 10.0) / 10.0;
        double effCdSec = Math.round((getEffectiveCooldownMs(level, maxLevel) * cdMult) / 100.0) / 10.0;
        String pwrStr = formatNum(effPower);
        String cdStr = formatNum(effCdSec);
        String rngStr = formatNum(range);
        String threshStr = String.valueOf(Math.round(activationThreshold * 100.0));
        String halfPwrStr = formatNum(Math.round(effPower * 0.55 * 10.0) / 10.0);
        String healStr = formatNum(Math.round(Math.max(4.0, effPower * 0.35) * 10.0) / 10.0);

        List<String> out = new ArrayList<>(descriptionLines.size());
        for (String line : descriptionLines) {
            out.add(line
                    .replace("{pwr}", pwrStr)
                    .replace("{half_pwr}", halfPwrStr)
                    .replace("{heal}", healStr)
                    .replace("{cd}", cdStr)
                    .replace("{range}", rngStr)
                    .replace("{thresh}", threshStr));
        }
        return out;
    }

    private static double progress(int level, int maxLevel) {
        int clampedMax = Math.max(2, maxLevel);
        int clamped = Math.max(1, Math.min(clampedMax, level));
        return (double) (clamped - 1) / (double) (clampedMax - 1);
    }

    private static String formatNum(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-4) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(Locale.US, "%.1f", v);
    }
}
