package dc.dcessentials.modules.pets.model;

import dc.dcessentials.modules.items.model.ItemRarity;
import dc.dcessentials.modules.items.model.ItemStat;
import dc.dcessentials.modules.pets.config.PetsConfig;

import java.util.*;

/**
 * Full blueprint specification for a collectible Dragoncote Pet companion.
 */
public class PetSpec {
    private final String id;
    private String displayName;
    private String family;
    private PetCategory category;
    private ItemRarity rarity;
    private String headBase64;
    private int maxLevel;
    private double xpBase;
    private double xpCoeff;
    private double statCurveExponent = 1.0;

    private final EnumMap<ItemStat, Double> minStats = new EnumMap<>(ItemStat.class);
    private final EnumMap<ItemStat, Double> maxStats = new EnumMap<>(ItemStat.class);

    private final List<PetPerk> perks = new ArrayList<>();
    private final List<String> lore = new ArrayList<>();
    private PetSkillSpec activeSkill;
    private String ambientParticle;
    private String particleColorFrom = "#0CBF93";
    private String particleColorTo = "#19E6FF";
    private double offsetForward = -0.35;
    private double offsetUp = 0.78;
    private double offsetRight = 0.75;
    private float headScale = 0.78f;

    public PetSpec(String id, String displayName, String family, ItemRarity rarity, String headBase64, int maxLevel) {
        this(id, displayName, family, PetCategory.COMBAT, rarity, headBase64, maxLevel);
    }

    public PetSpec(String id,
                   String displayName,
                   String family,
                   PetCategory category,
                   ItemRarity rarity,
                   String headBase64,
                   int maxLevel) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.displayName = displayName;
        this.family = family != null ? family : "Companion";
        this.category = category != null ? category : PetCategory.COMBAT;
        this.rarity = rarity != null ? rarity : ItemRarity.COMMON;
        this.headBase64 = headBase64;
        this.maxLevel = Math.max(1, maxLevel);
        this.xpBase = 120.0;
        this.xpCoeff = 1.06;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getFamily() { return family; }
    public void setFamily(String family) { this.family = family; }
    public PetCategory getCategory() { return category; }
    public void setCategory(PetCategory category) { this.category = category != null ? category : PetCategory.COMBAT; }
    public ItemRarity getRarity() { return rarity; }
    public void setRarity(ItemRarity rarity) { this.rarity = rarity != null ? rarity : ItemRarity.COMMON; }
    public String getHeadBase64() { return headBase64; }
    public void setHeadBase64(String headBase64) { this.headBase64 = headBase64; }
    public int getMaxLevel() { return maxLevel; }
    public void setMaxLevel(int maxLevel) { this.maxLevel = Math.max(1, maxLevel); }
    public double getXpBase() { return xpBase; }
    public void setXpBase(double xpBase) { this.xpBase = xpBase; }
    public double getXpCoeff() { return xpCoeff; }
    public void setXpCoeff(double xpCoeff) { this.xpCoeff = xpCoeff; }
    public double getStatCurveExponent() { return statCurveExponent; }
    public void setStatCurveExponent(double statCurveExponent) { this.statCurveExponent = Math.max(0.25, statCurveExponent); }

    public Map<ItemStat, Double> getMinStats() { return minStats; }
    public Map<ItemStat, Double> getMaxStats() { return maxStats; }

    /**
     * Defines progressive stat range from Lv 1 (minAtLv1) up to Max Level (maxAtCap).
     */
    public void setStatRange(ItemStat stat, double minAtLv1, double maxAtCap) {
        if (stat == null) return;
        minStats.put(stat, minAtLv1);
        maxStats.put(stat, maxAtCap);
    }

    /**
     * Legacy helper mapping base + perLevel into Lv 1 -> maxLevel range.
     */
    public void setStatScaling(ItemStat stat, double base, double perLevel) {
        if (stat == null) return;
        double span = Math.max(1, maxLevel - 1);
        if (stat == ItemStat.RESONANCE) {
            setStatRange(stat, base, Math.max(2.0, Math.round(base - Math.abs(perLevel) * span)));
        } else {
            setStatRange(stat, base, base + perLevel * span);
        }
    }

    public EnumMap<ItemStat, Double> getStatsAtLevel(int level) {
        int clampedLvl = Math.max(1, Math.min(maxLevel, level));
        double rawT = maxLevel > 1 ? (double) (clampedLvl - 1) / (double) (maxLevel - 1) : 1.0;
        double t = Math.pow(rawT, statCurveExponent);

        EnumMap<ItemStat, Double> map = new EnumMap<>(ItemStat.class);
        for (ItemStat stat : ItemStat.values()) {
            boolean hasMin = minStats.containsKey(stat);
            boolean hasMax = maxStats.containsKey(stat);
            if (!hasMin && !hasMax) continue;

            double min = minStats.getOrDefault(stat, 0.0);
            double max = maxStats.getOrDefault(stat, min);
            double val = min + (max - min) * t;
            if (stat == ItemStat.RESONANCE) {
                double rounded = Math.max(2.0, Math.round(val));
                if (rounded > 0) map.put(stat, rounded);
            } else if (val > 0.0) {
                map.put(stat, Math.round(val * 10.0) / 10.0);
            }
        }
        return map;
    }

    public double getXpRequiredForNextLevel(int currentLevel, PetsConfig config) {
        if (currentLevel >= maxLevel) return 0.0;
        if (config != null) {
            return config.computeXpRequired(currentLevel, maxLevel, xpBase, xpCoeff);
        }
        return getXpRequiredForNextLevel(currentLevel);
    }

    public double getXpRequiredForNextLevel(int currentLevel) {
        if (currentLevel >= maxLevel) return 0.0;
        int offset = Math.max(0, currentLevel - 1);
        return Math.round(xpBase + (40.0 * offset) + (3.2 * offset * offset));
    }

    public List<PetPerk> getPerks() { return perks; }
    public void addPerk(PetPerk perk) { if (perk != null) perks.add(perk); }
    public List<String> getLore() { return lore; }
    public PetSkillSpec getActiveSkill() { return activeSkill; }
    public void setActiveSkill(PetSkillSpec activeSkill) { this.activeSkill = activeSkill; }
    public String getAmbientParticle() { return ambientParticle; }
    public void setAmbientParticle(String ambientParticle) { this.ambientParticle = ambientParticle; }
    public String getParticleColorFrom() { return particleColorFrom; }
    public void setParticleColorFrom(String particleColorFrom) { this.particleColorFrom = particleColorFrom; }
    public String getParticleColorTo() { return particleColorTo; }
    public void setParticleColorTo(String particleColorTo) { this.particleColorTo = particleColorTo; }

    public double getOffsetForward() { return offsetForward; }
    public void setOffsetForward(double offsetForward) { this.offsetForward = offsetForward; }
    public double getOffsetUp() { return offsetUp; }
    public void setOffsetUp(double offsetUp) { this.offsetUp = offsetUp; }
    public double getOffsetRight() { return offsetRight; }
    public void setOffsetRight(double offsetRight) { this.offsetRight = offsetRight; }
    public float getHeadScale() { return headScale; }
    public void setHeadScale(float headScale) { this.headScale = headScale; }
}
