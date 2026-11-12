package dc.dcessentials.modules.pets.config;

import dc.dcessentials.modules.items.model.ItemRarity;
import dc.dcessentials.modules.pets.model.PetCategory;
import dc.dcessentials.modules.pets.model.SkillDomain;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Global configuration manager for the DCEPets engine (plugins/DCEssentials/pets_config.yml).
 */
public class PetsConfig {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    // Progression
    private int defaultMaxLevel = 100;
    private String xpFormula = "POLYNOMIAL";
    private double polyBase = 120.0;
    private double polyLinear = 40.0;
    private double polyQuadratic = 3.2;
    private double expBase = 200.0;
    private double expCoeff = 1.06;
    private double defaultStatCurveExponent = 1.0;

    // Skill XP integration
    private boolean skillsXpEnabled = true;
    private int skillsPollIntervalTicks = 10;
    private double globalSkillXpMultiplier = 1.0;
    private double sameCategoryXpMultiplier = 1.0;
    private double otherCategoryXpMultiplier = 0.25;
    private double omniCategoryXpMultiplier = 1.0;
    private boolean fallbackMobKillXp = true;
    private double fallbackMobKillAmount = 18.0;
    private boolean excludeScrollBonusXp = false;

    private final EnumMap<PetCategory, Set<String>> categorySkillMap = new EnumMap<>(PetCategory.class);
    private final Map<String, PetCategory> reverseSkillLookup = new HashMap<>();
    private final Map<String, EnumMap<SkillDomain, Double>> domainWeights = new HashMap<>();
    private final EnumMap<PetCategory, Set<SkillDomain>> categoryDomains = new EnumMap<>(PetCategory.class);

    // Rarity Perk Rules
    private final EnumMap<ItemRarity, Integer> rarityMaxPerks = new EnumMap<>(ItemRarity.class);
    private final EnumMap<ItemRarity, List<Integer>> rarityUnlockLevels = new EnumMap<>(ItemRarity.class);

    // Starter & Menu
    private boolean starterEnabled = true;
    private String starterPetId = "spark";
    private boolean starterAutoSummon = false;
    private boolean hideLockedForPlayers = true;

    // Combat & Target Tracker Ring
    private boolean trackerEnabled = true;
    private int trackerIntervalTicks = 4;
    private double trackerMaxLookDistance = 28.0;
    private int trackerRingPoints = 18;
    private double trackerRadiusPadding = 0.35;
    private String trackerEnemyColorFrom = "#E11717";
    private String trackerEnemyColorTo = "#FC5AA4";
    private String trackerAllyColorFrom = "#8EE6EC";
    private String trackerAllyColorTo = "#EAFD47";
    private boolean trackerOutOfRangeRing = true;
    private boolean modelEngineVfxEnabled = true;
    private int defaultVfxDurationTicks = 70;
    private boolean cooldownNotifyActionBar = false;

    public PetsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pets_config.yml");
        initDefaults();
    }

    private void initDefaults() {
        categorySkillMap.clear();
        categorySkillMap.put(PetCategory.COMBAT, new LinkedHashSet<>(Arrays.asList(
                "swords", "axes", "ranged", "unarmed", "hunter", "tragoul", "blocking")));
        categorySkillMap.put(PetCategory.MINING, new LinkedHashSet<>(Arrays.asList(
                "pickaxe", "excavation", "nether", "architect")));
        categorySkillMap.put(PetCategory.FARMING, new LinkedHashSet<>(Arrays.asList(
                "herbalism", "taming", "crafting")));
        categorySkillMap.put(PetCategory.FORAGING, new LinkedHashSet<>(Arrays.asList(
                "kinetics", "agility", "stealth")));
        categorySkillMap.put(PetCategory.FISHING, new LinkedHashSet<>(Arrays.asList(
                "seaborne", "discovery")));
        categorySkillMap.put(PetCategory.ALCHEMY, new LinkedHashSet<>(Arrays.asList(
                "brewing", "chronos")));
        categorySkillMap.put(PetCategory.ENCHANTING, new LinkedHashSet<>(Arrays.asList(
                "enchanting", "rift")));
        categorySkillMap.put(PetCategory.UTILITY, new LinkedHashSet<>(Arrays.asList(
                "agility", "stealth", "kinetics", "architect", "crafting", "discovery", "chronos", "rift", "brewing", "enchanting")));
        categorySkillMap.put(PetCategory.OMNI, new LinkedHashSet<>());

        categoryDomains.clear();
        categoryDomains.put(PetCategory.COMBAT, EnumSet.of(SkillDomain.MELEE, SkillDomain.RANGED, SkillDomain.DEFENSE));
        categoryDomains.put(PetCategory.MINING, EnumSet.of(SkillDomain.MINING, SkillDomain.EXCAVATION));
        categoryDomains.put(PetCategory.FARMING, EnumSet.of(SkillDomain.HARVEST));
        categoryDomains.put(PetCategory.FORAGING, EnumSet.of(SkillDomain.FORAGING));
        categoryDomains.put(PetCategory.FISHING, EnumSet.of(SkillDomain.FISHING));
        categoryDomains.put(PetCategory.ALCHEMY, EnumSet.of(SkillDomain.ALCHEMY, SkillDomain.MOBILITY, SkillDomain.UTILITY));
        categoryDomains.put(PetCategory.ENCHANTING, EnumSet.of(SkillDomain.ARCANE, SkillDomain.UTILITY));
        categoryDomains.put(PetCategory.UTILITY, EnumSet.of(SkillDomain.ARCANE, SkillDomain.ALCHEMY, SkillDomain.MOBILITY, SkillDomain.UTILITY));
        categoryDomains.put(PetCategory.OMNI, EnumSet.allOf(SkillDomain.class));

        initDefaultDomainWeights();

        rarityMaxPerks.put(ItemRarity.COMMON, 1);
        rarityMaxPerks.put(ItemRarity.UNCOMMON, 2);
        rarityMaxPerks.put(ItemRarity.RARE, 3);
        rarityMaxPerks.put(ItemRarity.EPIC, 4);
        rarityMaxPerks.put(ItemRarity.LEGENDARY, 5);
        rarityMaxPerks.put(ItemRarity.MASTERY, 5);

        rarityUnlockLevels.put(ItemRarity.COMMON, Collections.singletonList(1));
        rarityUnlockLevels.put(ItemRarity.UNCOMMON, Arrays.asList(1, 25));
        rarityUnlockLevels.put(ItemRarity.RARE, Arrays.asList(1, 25, 60));
        rarityUnlockLevels.put(ItemRarity.EPIC, Arrays.asList(1, 20, 50, 80));
        rarityUnlockLevels.put(ItemRarity.LEGENDARY, Arrays.asList(1, 20, 45, 70, 90));
        rarityUnlockLevels.put(ItemRarity.MASTERY, Arrays.asList(1, 15, 35, 65, 90));
    }

    public void load() {
        if (!file.exists()) {
            saveDefaults();
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        applyMissingDefaults();

        defaultMaxLevel = Math.max(10, config.getInt("progression.default-max-level", 100));
        xpFormula = config.getString("progression.xp-formula", "POLYNOMIAL").toUpperCase(Locale.ROOT);
        polyBase = Math.max(10.0, config.getDouble("progression.polynomial.base", 120.0));
        polyLinear = Math.max(0.0, config.getDouble("progression.polynomial.linear", 40.0));
        polyQuadratic = Math.max(0.0, config.getDouble("progression.polynomial.quadratic", 3.2));
        expBase = Math.max(10.0, config.getDouble("progression.exponential.base", 200.0));
        expCoeff = Math.max(1.01, config.getDouble("progression.exponential.coeff", 1.06));
        defaultStatCurveExponent = Math.max(0.25, config.getDouble("progression.stat-curve-exponent", 1.0));

        skillsXpEnabled = config.getBoolean("skills-xp.enabled", true);
        skillsPollIntervalTicks = Math.max(2, config.getInt("skills-poll-interval-ticks",
                config.getInt("skills-xp.poll-interval-ticks", 10)));
        globalSkillXpMultiplier = Math.max(0.0, config.getDouble("skills-xp.global-multiplier", 1.0));
        sameCategoryXpMultiplier = Math.max(0.0, config.getDouble("skills-xp.same-category-multiplier", 1.0));
        otherCategoryXpMultiplier = Math.max(0.0, config.getDouble("skills-xp.other-category-multiplier", 0.25));
        omniCategoryXpMultiplier = Math.max(0.0, config.getDouble("skills-xp.omni-category-multiplier", 1.0));
        fallbackMobKillXp = config.getBoolean("skills-xp.fallback-mob-kill-xp", true);
        fallbackMobKillAmount = Math.max(0.0, config.getDouble("skills-xp.fallback-mob-kill-amount", 18.0));
        excludeScrollBonusXp = config.getBoolean("skills-xp.exclude-scroll-bonus-xp",
                config.getBoolean("pets.exclude-scroll-bonus-xp", false));

        initDefaultDomainWeights();
        ConfigurationSection dwSec = config.getConfigurationSection("skills-xp.domain-weights");
        if (dwSec != null) {
            for (String skillKey : dwSec.getKeys(false)) {
                ConfigurationSection sSec = dwSec.getConfigurationSection(skillKey);
                if (sSec == null) continue;
                EnumMap<SkillDomain, Double> weights = new EnumMap<>(SkillDomain.class);
                for (String domKey : sSec.getKeys(false)) {
                    SkillDomain dom = SkillDomain.fromString(domKey);
                    double w = sSec.getDouble(domKey, 0.0);
                    if (dom != null && w > 0.0) {
                        weights.put(dom, w);
                    }
                }
                if (!weights.isEmpty()) {
                    domainWeights.put(skillKey.trim().toLowerCase(Locale.ROOT), weights);
                }
            }
        }

        ConfigurationSection catSec = config.getConfigurationSection("skills-xp.category-skills");
        if (catSec != null) {
            for (PetCategory cat : PetCategory.values()) {
                if (cat == PetCategory.OMNI) continue;
                if (catSec.isList(cat.name())) {
                    Set<String> mapped = new LinkedHashSet<>();
                    for (String s : catSec.getStringList(cat.name())) {
                        if (s != null && !s.isBlank()) {
                            mapped.add(s.trim().toLowerCase(Locale.ROOT));
                        }
                    }
                    categorySkillMap.put(cat, mapped);
                }
            }
        }
        rebuildReverseSkillLookup();

        ConfigurationSection rpSec = config.getConfigurationSection("rarity-perks");
        if (rpSec != null) {
            for (ItemRarity r : ItemRarity.values()) {
                ConfigurationSection rSec = rpSec.getConfigurationSection(r.name());
                if (rSec != null) {
                    rarityMaxPerks.put(r, Math.max(1, rSec.getInt("max-perks", getMaxPerksForRarity(r))));
                    if (rSec.isList("unlock-levels")) {
                        List<Integer> lvls = rSec.getIntegerList("unlock-levels");
                        if (!lvls.isEmpty()) {
                            rarityUnlockLevels.put(r, new ArrayList<>(lvls));
                        }
                    }
                }
            }
        }

        starterEnabled = config.getBoolean("starter.enabled", true);
        starterPetId = config.getString("starter.pet-id", "spark").trim().toLowerCase(Locale.ROOT);
        starterAutoSummon = config.getBoolean("starter.auto-summon", false);
        hideLockedForPlayers = config.getBoolean("menu.hide-locked-for-players", true);

        trackerEnabled = config.getBoolean("combat.target-tracker.enabled", true);
        trackerIntervalTicks = Math.max(2, config.getInt("combat.target-tracker.interval-ticks", 4));
        trackerMaxLookDistance = Math.max(6.0, config.getDouble("combat.target-tracker.look-max-distance", 28.0));
        trackerRingPoints = Math.max(8, config.getInt("combat.target-tracker.ring-points", 18));
        trackerRadiusPadding = Math.max(0.1, config.getDouble("combat.target-tracker.radius-padding", 0.35));
        trackerEnemyColorFrom = config.getString("combat.target-tracker.enemy-color-from", "#E11717");
        trackerEnemyColorTo = config.getString("combat.target-tracker.enemy-color-to", "#FC5AA4");
        trackerAllyColorFrom = config.getString("combat.target-tracker.ally-color-from", "#8EE6EC");
        trackerAllyColorTo = config.getString("combat.target-tracker.ally-color-to", "#EAFD47");
        trackerOutOfRangeRing = config.getBoolean("combat.target-tracker.out-of-range-ring", true);
        modelEngineVfxEnabled = config.getBoolean("combat.vfx.model-engine-enabled", true);
        defaultVfxDurationTicks = Math.max(10, config.getInt("combat.vfx.duration-ticks", 70));
        cooldownNotifyActionBar = config.getBoolean("combat.cooldown-notify-actionbar", false);
    }

    private void initDefaultDomainWeights() {
        domainWeights.clear();
        putDomainWeight("swords", SkillDomain.MELEE, 1.0);
        putDomainWeight("unarmed", SkillDomain.MELEE, 1.0);
        putDomainWeight("tragoul", SkillDomain.MELEE, 1.0);
        putDomainWeight("ranged", SkillDomain.RANGED, 1.0);
        putDomainWeight("hunter", SkillDomain.RANGED, 1.0);
        putDomainWeight("blocking", SkillDomain.DEFENSE, 1.0);
        putDomainWeight("pickaxe", SkillDomain.MINING, 1.0);
        putDomainWeight("nether", SkillDomain.MINING, 1.0);
        putDomainWeight("excavation", SkillDomain.EXCAVATION, 1.0);
        putDomainWeight("herbalism", SkillDomain.HARVEST, 1.0);
        putDomainWeight("taming", SkillDomain.HARVEST, 1.0);
        putDomainWeight("axes", SkillDomain.MELEE, 0.50, SkillDomain.FORAGING, 0.50);
        putDomainWeight("axe", SkillDomain.MELEE, 0.50, SkillDomain.FORAGING, 0.50);
        putDomainWeight("seaborne", SkillDomain.FISHING, 1.0);
        putDomainWeight("enchanting", SkillDomain.ARCANE, 1.0);
        putDomainWeight("rift", SkillDomain.ARCANE, 1.0);
        putDomainWeight("brewing", SkillDomain.ALCHEMY, 1.0);
        putDomainWeight("chronos", SkillDomain.ALCHEMY, 1.0);
        putDomainWeight("agility", SkillDomain.MOBILITY, 1.0);
        putDomainWeight("stealth", SkillDomain.MOBILITY, 1.0);
        putDomainWeight("kinetics", SkillDomain.MOBILITY, 1.0);
        putDomainWeight("architect", SkillDomain.UTILITY, 1.0);
        putDomainWeight("crafting", SkillDomain.UTILITY, 1.0);
        putDomainWeight("discovery", SkillDomain.UTILITY, 1.0);
    }

    private void putDomainWeight(String skill, SkillDomain d1, double w1) {
        EnumMap<SkillDomain, Double> m = new EnumMap<>(SkillDomain.class);
        m.put(d1, w1);
        domainWeights.put(skill, m);
    }

    private void putDomainWeight(String skill, SkillDomain d1, double w1, SkillDomain d2, double w2) {
        EnumMap<SkillDomain, Double> m = new EnumMap<>(SkillDomain.class);
        m.put(d1, w1);
        m.put(d2, w2);
        domainWeights.put(skill, m);
    }

    private void rebuildReverseSkillLookup() {
        reverseSkillLookup.clear();
        for (Map.Entry<PetCategory, Set<String>> e : categorySkillMap.entrySet()) {
            for (String skill : e.getValue()) {
                reverseSkillLookup.putIfAbsent(skill.toLowerCase(Locale.ROOT), e.getKey());
            }
        }
    }

    private void saveDefaults() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        YamlConfiguration yml = new YamlConfiguration();
        writeDefaultValues(yml);
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[DCEPets] Failed saving default pets_config.yml: " + e.getMessage());
        }
    }

    private void applyMissingDefaults() {
        boolean changed = false;
        YamlConfiguration defaults = new YamlConfiguration();
        writeDefaultValues(defaults);
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !config.contains(key)) {
                config.set(key, defaults.get(key));
                changed = true;
            }
        }
        if (changed) {
            try {
                config.save(file);
            } catch (IOException ignored) {}
        }
    }

    private void writeDefaultValues(YamlConfiguration yml) {
        yml.set("progression.default-max-level", 100);
        yml.set("progression.xp-formula", "POLYNOMIAL");
        yml.set("progression.polynomial.base", 120.0);
        yml.set("progression.polynomial.linear", 40.0);
        yml.set("progression.polynomial.quadratic", 3.2);
        yml.set("progression.exponential.base", 200.0);
        yml.set("progression.exponential.coeff", 1.06);
        yml.set("progression.stat-curve-exponent", 1.0);

        yml.set("skills-xp.enabled", true);
        yml.set("skills-xp.poll-interval-ticks", 10);
        yml.set("skills-xp.global-multiplier", 1.0);
        yml.set("skills-xp.same-category-multiplier", 1.0);
        yml.set("skills-xp.other-category-multiplier", 0.25);
        yml.set("skills-xp.omni-category-multiplier", 1.0);
        yml.set("skills-xp.fallback-mob-kill-xp", true);
        yml.set("skills-xp.fallback-mob-kill-amount", 18.0);
        for (PetCategory cat : PetCategory.values()) {
            if (cat == PetCategory.OMNI) continue;
            yml.set("skills-xp.category-skills." + cat.name(), new ArrayList<>(categorySkillMap.get(cat)));
        }

        for (ItemRarity r : ItemRarity.values()) {
            yml.set("rarity-perks." + r.name() + ".max-perks", rarityMaxPerks.getOrDefault(r, 1));
            yml.set("rarity-perks." + r.name() + ".unlock-levels", rarityUnlockLevels.getOrDefault(r, Collections.singletonList(1)));
        }

        yml.set("starter.enabled", true);
        yml.set("starter.pet-id", "spark");
        yml.set("starter.auto-summon", false);
        yml.set("menu.hide-locked-for-players", true);

        yml.set("combat.target-tracker.enabled", true);
        yml.set("combat.target-tracker.interval-ticks", 4);
        yml.set("combat.target-tracker.look-max-distance", 28.0);
        yml.set("combat.target-tracker.ring-points", 18);
        yml.set("combat.target-tracker.radius-padding", 0.35);
        yml.set("combat.target-tracker.enemy-color-from", "#E11717");
        yml.set("combat.target-tracker.enemy-color-to", "#FC5AA4");
        yml.set("combat.target-tracker.ally-color-from", "#8EE6EC");
        yml.set("combat.target-tracker.ally-color-to", "#EAFD47");
        yml.set("combat.target-tracker.out-of-range-ring", true);
        yml.set("combat.vfx.model-engine-enabled", true);
        yml.set("combat.vfx.duration-ticks", 70);
        yml.set("combat.cooldown-notify-actionbar", false);
    }

    public double computeXpRequired(int currentLevel, int maxLevel, double petXpBase, double petXpCoeff) {
        if (currentLevel >= maxLevel) return 0.0;
        int lvl = Math.max(1, currentLevel);
        if ("EXPONENTIAL".equalsIgnoreCase(xpFormula)) {
            double b = petXpBase > 0 ? petXpBase : expBase;
            double c = petXpCoeff > 1.0 ? petXpCoeff : expCoeff;
            return Math.round(b * Math.pow(c, lvl - 1));
        }
        double offset = lvl - 1;
        double base = petXpBase > 0 ? petXpBase : polyBase;
        return Math.round(base + (polyLinear * offset) + (polyQuadratic * offset * offset));
    }

    public double getCategoryXpMultiplier(PetCategory petCategory, String skillName) {
        if (petCategory == null) return sameCategoryXpMultiplier * globalSkillXpMultiplier;
        if (petCategory == PetCategory.OMNI) return omniCategoryXpMultiplier * globalSkillXpMultiplier;
        String cleanSkill = skillName != null ? skillName.trim().toLowerCase(Locale.ROOT) : "";

        // 1. Weighted SkillDomain lookup (Section 8.6 / P3-09)
        EnumMap<SkillDomain, Double> weights = domainWeights.get(cleanSkill);
        Set<SkillDomain> petDoms = categoryDomains.get(petCategory);
        if (weights != null && !weights.isEmpty() && petDoms != null) {
            double weightedSum = 0.0;
            for (Map.Entry<SkillDomain, Double> entry : weights.entrySet()) {
                double w = entry.getValue() != null ? entry.getValue() : 0.0;
                double mult = petDoms.contains(entry.getKey()) ? sameCategoryXpMultiplier : otherCategoryXpMultiplier;
                weightedSum += w * mult;
            }
            return weightedSum * globalSkillXpMultiplier;
        }

        // 2. Legacy category-skills fallback
        Set<String> direct = categorySkillMap.get(petCategory);
        if (direct != null && direct.contains(cleanSkill)) {
            return sameCategoryXpMultiplier * globalSkillXpMultiplier;
        }
        PetCategory matched = reverseSkillLookup.get(cleanSkill);
        if (matched == petCategory) {
            return sameCategoryXpMultiplier * globalSkillXpMultiplier;
        }
        return otherCategoryXpMultiplier * globalSkillXpMultiplier;
    }

    public boolean isExcludeScrollBonusXp() {
        return excludeScrollBonusXp;
    }

    public int getMaxPerksForRarity(ItemRarity rarity) {
        if (rarity == null) return 1;
        return rarityMaxPerks.getOrDefault(rarity, 1);
    }

    public List<Integer> getDefaultUnlockLevels(ItemRarity rarity) {
        if (rarity == null) return Collections.singletonList(1);
        return rarityUnlockLevels.getOrDefault(rarity, Collections.singletonList(1));
    }

    public int getDefaultMaxLevel() { return defaultMaxLevel; }
    public String getXpFormula() { return xpFormula; }
    public double getDefaultStatCurveExponent() { return defaultStatCurveExponent; }
    public boolean isSkillsXpEnabled() { return skillsXpEnabled; }
    public int getSkillsPollIntervalTicks() { return skillsPollIntervalTicks; }
    public double getSameCategoryXpMultiplier() { return sameCategoryXpMultiplier; }
    public double getOtherCategoryXpMultiplier() { return otherCategoryXpMultiplier; }
    public boolean isFallbackMobKillXp() { return fallbackMobKillXp; }
    public double getFallbackMobKillAmount() { return fallbackMobKillAmount; }
    public boolean isStarterEnabled() { return starterEnabled; }
    public String getStarterPetId() { return starterPetId; }
    public boolean isStarterAutoSummon() { return starterAutoSummon; }
    public boolean isHideLockedForPlayers() { return hideLockedForPlayers; }
    public boolean isTrackerEnabled() { return trackerEnabled; }
    public int getTrackerIntervalTicks() { return trackerIntervalTicks; }
    public double getTrackerMaxLookDistance() { return trackerMaxLookDistance; }
    public int getTrackerRingPoints() { return trackerRingPoints; }
    public double getTrackerRadiusPadding() { return trackerRadiusPadding; }
    public String getTrackerEnemyColorFrom() { return trackerEnemyColorFrom; }
    public String getTrackerEnemyColorTo() { return trackerEnemyColorTo; }
    public String getTrackerAllyColorFrom() { return trackerAllyColorFrom; }
    public String getTrackerAllyColorTo() { return trackerAllyColorTo; }
    public boolean isTrackerOutOfRangeRing() { return trackerOutOfRangeRing; }
    public boolean isModelEngineVfxEnabled() { return modelEngineVfxEnabled; }
    public int getDefaultVfxDurationTicks() { return defaultVfxDurationTicks; }
    public boolean isCooldownNotifyActionBar() { return cooldownNotifyActionBar; }
}
