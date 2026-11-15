package dc.dcessentials.modules.pets.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Progressive passive or reactive perk belonging to a pet species.
 */
public class PetPerk {
    private final String name;
    private final List<String> descriptionLines;
    private final int unlockLevel;
    private final PetPerkEffectType effectType;
    private final double minValue;
    private final double maxValue;

    public PetPerk(String name, List<String> descriptionLines, int unlockLevel) {
        this(name, descriptionLines, unlockLevel, PetPerkEffectType.NONE, 0.0, 0.0);
    }

    public PetPerk(String name,
                   List<String> descriptionLines,
                   int unlockLevel,
                   PetPerkEffectType effectType,
                   double minValue,
                   double maxValue) {
        this.name = name;
        this.descriptionLines = descriptionLines != null ? new ArrayList<>(descriptionLines) : new ArrayList<>();
        this.unlockLevel = Math.max(1, unlockLevel);
        this.effectType = effectType != null ? effectType : PetPerkEffectType.NONE;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public String getName() {
        return name;
    }

    public List<String> getDescriptionLines() {
        return descriptionLines;
    }

    public int getUnlockLevel() {
        return unlockLevel;
    }

    public PetPerkEffectType getEffectType() {
        return effectType;
    }

    public double getMinValue() {
        return minValue;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public boolean isUnlockedAt(int level) {
        return level >= unlockLevel;
    }

    /**
     * Computes scaled perk value at the given pet level (interpolating from unlockLevel -> maxLevel).
     */
    public double getEffectiveValue(int level, int maxLevel) {
        int effMax = Math.max(unlockLevel + 1, maxLevel);
        int clamped = Math.max(unlockLevel, Math.min(effMax, level));
        double t = (double) (clamped - unlockLevel) / (double) (effMax - unlockLevel);
        double val = minValue + (maxValue - minValue) * t;
        if (clamped >= effMax) {
            return (double) Math.round(val);
        }
        return Math.round(val * 10.0) / 10.0;
    }

    public List<String> formatDescriptionLines(int level, int maxLevel) {
        double eff = getEffectiveValue(level, maxLevel);
        String valStr = formatNum(eff);
        String intStr = String.valueOf(Math.round(eff));
        String minStr = formatNum(minValue);
        String maxStr = formatNum(maxValue);

        List<String> formatted = new ArrayList<>(descriptionLines.size());
        for (String line : descriptionLines) {
            formatted.add(line
                    .replace("{val}", valStr)
                    .replace("{val_int}", intStr)
                    .replace("{min}", minStr)
                    .replace("{max}", maxStr)
                    .replace("{unlock}", String.valueOf(unlockLevel)));
        }
        return formatted;
    }

    private static String formatNum(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-4) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(Locale.US, "%.1f", v);
    }
}
