package dc.dcessentials.modules.pets;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.modules.items.ItemModule;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemCategory;
import dc.dcessentials.modules.items.model.ItemRarity;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.model.ItemStat;
import dc.dcessentials.modules.items.tooltip.TooltipRenderer;
import dc.dcessentials.modules.pets.config.PetsConfig;
import dc.dcessentials.modules.pets.model.*;
import dc.dccosmetics.util.HeadUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads, migrates, and indexes all DCEPets companion blueprints from plugins/DCEssentials/pets/.
 */
public class PetRegistry {

    private static final int SCHEMA_VERSION = 2;
    private static final Set<String> LEGACY_FILENAMES = Set.of(
            "cubee_air_dragon.yml", "cubee_darkness_dragon.yml", "cubee_earth_dragon.yml",
            "cubee_fire_dragon.yml", "cubee_galaxy_dragon.yml", "cubee_ice_dragon.yml",
            "cubee_light_dragon.yml", "cubee_nature_dragon.yml",
            "drone_combat.yml", "drone_flame.yml", "drone_repair.yml",
            "drone_rocket.yml", "drone_shield.yml", "drako_dragon.yml",
            "astral.yml", "bastion.yml", "crimson.yml", "ember.yml", "glacies.yml",
            "mend.yml", "pyre.yml", "salvo.yml", "solis.yml", "spark.yml",
            "striker.yml", "terra.yml", "umbra.yml", "verdant.yml", "zephyr.yml"
    );

    private final Map<String, PetSpec> pets = new LinkedHashMap<>();
    private final File petsFolder;
    private final PetsConfig config;

    public PetRegistry(PetsConfig config) {
        this.config = config;
        this.petsFolder = new File(DCCosmetics.getInstance().getDataFolder(), "pets");
    }

    public void loadPets() {
        pets.clear();
        if (!petsFolder.exists()) {
            petsFolder.mkdirs();
        }

        cleanupLegacyFiles(petsFolder);
        if (!hasAnyYamlFiles(petsFolder)) {
            generateDefaults();
        }
        scanDirectory(petsFolder);

        ItemModule itemModule = DCCosmetics.getInstance().getItemModule();
        if (itemModule != null && itemModule.getItemRegistry() != null) {
            ItemRegistry reg = itemModule.getItemRegistry();
            for (PetSpec spec : pets.values()) {
                registerInItemRegistry(reg, spec);
            }
        }
        DCCosmetics.getInstance().getLogger().info("[DCEPets] Loaded " + pets.size() + " companion specifications.");
    }

    private void cleanupLegacyFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                cleanupLegacyFiles(f);
            } else if (LEGACY_FILENAMES.contains(f.getName().toLowerCase(Locale.ROOT))) {
                f.delete();
            }
        }
    }

    private boolean hasAnyYamlFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isDirectory()) {
                if (hasAnyYamlFiles(f)) return true;
            } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) {
                return true;
            }
        }
        return false;
    }

    private void scanDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) {
            if (f.isDirectory()) {
                scanDirectory(f);
            } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) {
                loadPetFile(f);
            }
        }
    }

    private void loadPetFile(File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String rawId = cfg.getString("id", file.getName().replace(".yml", ""));
        String id = PlayerPetData.canonicalizePetId(rawId);
        if (id == null) return;

        String name = cfg.getString("display-name", id);
        String family = cfg.getString("family", "Companion");
        PetCategory category = PetCategory.fromString(cfg.getString("category", "COMBAT"));
        ItemRarity rarity = ItemRarity.fromString(cfg.getString("rarity", "COMMON"));
        String headBase64 = cfg.getString("head-base64", "");
        int defaultMax = config != null ? config.getDefaultMaxLevel() : 100;
        int maxLevel = Math.max(10, cfg.getInt("max-level", defaultMax));

        PetSpec spec = new PetSpec(id, name, family, category, rarity, headBase64, maxLevel);
        spec.setXpBase(cfg.getDouble("xp-curve.base", 120.0));
        spec.setXpCoeff(cfg.getDouble("xp-curve.coeff", 1.06));
        spec.setStatCurveExponent(cfg.getDouble("stat-curve-exponent",
                config != null ? config.getDefaultStatCurveExponent() : 1.0));
        spec.setAmbientParticle(cfg.getString("ambient-particle", "ENCHANT"));
        spec.setParticleColorFrom(cfg.getString("ambient-colors.from", "#0CBF93"));
        spec.setParticleColorTo(cfg.getString("ambient-colors.to", "#19E6FF"));
        spec.setOffsetForward(cfg.getDouble("offset.forward", -0.35));
        spec.setOffsetUp(cfg.getDouble("offset.up", 0.78));
        spec.setOffsetRight(cfg.getDouble("offset.right", 0.75));
        spec.setHeadScale((float) cfg.getDouble("offset.scale", 0.78));

        ConfigurationSection statsSec = cfg.getConfigurationSection("stats");
        if (statsSec != null) {
            for (String statKey : statsSec.getKeys(false)) {
                ItemStat st = ItemStat.fromString(statKey);
                if (st == null) continue;
                if (statsSec.contains(statKey + ".min") || statsSec.contains(statKey + ".max")) {
                    double min = statsSec.getDouble(statKey + ".min", 0.0);
                    double max = statsSec.getDouble(statKey + ".max", min);
                    spec.setStatRange(st, min, max);
                } else {
                    double base = statsSec.getDouble(statKey + ".base", 0.0);
                    double perLvl = statsSec.getDouble(statKey + ".per-level", 0.0);
                    spec.setStatScaling(st, base, perLvl);
                }
            }
        }

        ConfigurationSection skillSec = cfg.getConfigurationSection("active-skill");
        if (skillSec != null) {
            String skillId = skillSec.getString("type", "NONE");
            String skillName = skillSec.getString("name", "Active Ability");
            PetSkillTrigger trigger = PetSkillTrigger.fromString(skillSec.getString("trigger", "ON_ATTACK"));
            String triggerText = skillSec.getString("trigger-condition", trigger.getDefaultDescription());
            double threshold = skillSec.getDouble("activation-threshold", 0.80);
            long maxCd = skillSec.getLong("max-cooldown-ms", skillSec.getLong("base-cooldown-ms", 12000L));
            long minCd = skillSec.contains("min-cooldown-ms")
                    ? skillSec.getLong("min-cooldown-ms")
                    : Math.max(2000L, maxCd - skillSec.getLong("cooldown-reduction-per-level-ms", 65L) * 99L);
            double minPwr = skillSec.getDouble("min-power", skillSec.getDouble("base-power", 20.0));
            double maxPwr = skillSec.contains("max-power")
                    ? skillSec.getDouble("max-power")
                    : minPwr + skillSec.getDouble("power-per-level", 0.6) * 99.0;
            double range = skillSec.getDouble("range", 18.0);
            String vfxModel = skillSec.getString("vfx-model", "");
            String vfxAnim = skillSec.getString("vfx-animation", "execute");
            List<String> descLines = skillSec.getStringList("description");
            spec.setActiveSkill(new PetSkillSpec(
                    skillId, skillName, trigger, triggerText, threshold,
                    maxCd, minCd, minPwr, maxPwr, range, vfxModel, vfxAnim, descLines));
        }

        ConfigurationSection perksSec = cfg.getConfigurationSection("perks");
        int maxPerksAllowed = config != null ? config.getMaxPerksForRarity(rarity) : getDefaultPerkCount(rarity);
        List<Integer> defaultUnlocks = config != null ? config.getDefaultUnlockLevels(rarity) : List.of(1);
        if (perksSec != null) {
            int pIdx = 0;
            for (String pKey : perksSec.getKeys(false)) {
                if (spec.getPerks().size() >= maxPerksAllowed) break;
                String pName = perksSec.getString(pKey + ".name", pKey);
                int fallbackUnlock = pIdx < defaultUnlocks.size() ? defaultUnlocks.get(pIdx) : Math.min(95, 1 + pIdx * 20);
                int unlockLvl = perksSec.getInt(pKey + ".unlock-level", fallbackUnlock);
                PetPerkEffectType effectType = PetPerkEffectType.fromString(perksSec.getString(pKey + ".effect", "NONE"));
                double minVal = perksSec.getDouble(pKey + ".min-value", 0.0);
                double maxVal = perksSec.getDouble(pKey + ".max-value", minVal);
                List<String> lines = perksSec.getStringList(pKey + ".description");
                spec.addPerk(new PetPerk(pName, lines, unlockLvl, effectType, minVal, maxVal));
                pIdx++;
            }
        }

        spec.getLore().addAll(cfg.getStringList("lore"));
        pets.put(id, spec);
    }

    private int getDefaultPerkCount(ItemRarity r) {
        if (r == null) return 1;
        return switch (r) {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            case RARE -> 3;
            case EPIC -> 4;
            case LEGENDARY, MASTERY -> 5;
        };
    }

    private void registerInItemRegistry(ItemRegistry itemRegistry, PetSpec spec) {
        String itemId = "pet_" + spec.getId();
        DCEItem dceItem = new DCEItem(itemId, 1, Material.PLAYER_HEAD, "Lv 1 " + spec.getDisplayName(), spec.getRarity(), ItemCategory.PET);
        ItemStack template = HeadUtil.getCustomHead(spec.getHeadBase64());
        ItemMeta meta = template.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ItemRegistry.PET_ID_KEY, PersistentDataType.STRING, spec.getId());
            pdc.set(ItemRegistry.PET_LEVEL_KEY, PersistentDataType.INTEGER, 1);
            pdc.set(ItemRegistry.PET_XP_KEY, PersistentDataType.DOUBLE, 0.0);
            pdc.set(ItemRegistry.PET_RARITY_KEY, PersistentDataType.STRING, spec.getRarity().name());
            template.setItemMeta(meta);
        }
        dceItem.setTemplate(template);
        EnumMap<ItemStat, Double> lv1Stats = spec.getStatsAtLevel(1);
        for (Map.Entry<ItemStat, Double> entry : lv1Stats.entrySet()) {
            dceItem.setStat(entry.getKey(), entry.getValue());
        }
        dceItem.setCustomLore(buildPetTooltipLore(spec, 1, 0.0, 1.0, 1.0));
        itemRegistry.registerRuntimeItem(dceItem);
    }

    public ItemStack createPetTokenItem(PetSpec spec, int level, double xp, ItemRarity rarityOverride) {
        if (spec == null) return new ItemStack(Material.PLAYER_HEAD);
        int clampedLevel = Math.max(1, Math.min(spec.getMaxLevel(), level));
        ItemRarity effRarity = rarityOverride != null ? rarityOverride : spec.getRarity();

        ItemStack stack = HeadUtil.getCustomHead(spec.getHeadBase64());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ItemRegistry.ITEM_ID_KEY, PersistentDataType.STRING, "pet_" + spec.getId());
            pdc.set(ItemRegistry.CATEGORY_KEY, PersistentDataType.STRING, ItemCategory.PET.name());
            pdc.set(ItemRegistry.RARITY_KEY, PersistentDataType.STRING, effRarity.name());
            pdc.set(ItemRegistry.PET_ID_KEY, PersistentDataType.STRING, spec.getId());
            pdc.set(ItemRegistry.PET_LEVEL_KEY, PersistentDataType.INTEGER, clampedLevel);
            pdc.set(ItemRegistry.PET_XP_KEY, PersistentDataType.DOUBLE, xp);
            pdc.set(ItemRegistry.PET_RARITY_KEY, PersistentDataType.STRING, effRarity.name());
            stack.setItemMeta(meta);
        }

        DCEItem tempDce = new DCEItem(
                "pet_" + spec.getId(),
                1,
                Material.PLAYER_HEAD,
                "&7Lv " + clampedLevel + " " + effRarity.getColorHex() + spec.getDisplayName(),
                effRarity,
                ItemCategory.PET
        );
        tempDce.setTemplate(stack);
        for (Map.Entry<ItemStat, Double> entry : spec.getStatsAtLevel(clampedLevel).entrySet()) {
            tempDce.setStat(entry.getKey(), entry.getValue());
        }
        tempDce.setCustomLore(buildPetTooltipLore(spec, clampedLevel, xp, 1.0, 1.0));

        var itemMod = DCCosmetics.getInstance().getItemModule();
        TooltipRenderer.renderItem(tempDce, stack, itemMod != null ? itemMod.getScrollRegistry() : null);
        return stack;
    }

    public List<String> buildPetTooltipLore(PetSpec spec, int level, double xp) {
        return buildPetTooltipLore(spec, level, xp, 1.0, 1.0);
    }

    public List<String> buildPetTooltipLore(PetSpec spec, int level, double xp, double cdMult, double powerMult) {
        List<String> lines = new ArrayList<>();
        PetCategory cat = spec.getCategory();
        lines.add(cat.getFormattedBadge() + " &8· &7" + spec.getFamily() + " Companion");
        if (cat == PetCategory.OMNI) {
            lines.add("&8Gains 100% XP from all Skills");
        } else {
            lines.add("&8Gains 100% XP from " + cat.getDisplayName() + " Skills &7(25% other)");
        }
        lines.add("");

        for (PetPerk perk : spec.getPerks()) {
            boolean unlocked = perk.isUnlockedAt(level);
            String badge = unlocked
                    ? "<#FCD34D>✦ " + perk.getName()
                    : "&8✖ " + perk.getName() + " &7(Unlocks at Lv " + perk.getUnlockLevel() + ")";
            lines.add(badge);
            for (String desc : perk.formatDescriptionLines(level, spec.getMaxLevel())) {
                lines.add((unlocked ? "&7  " : "&8  ") + ColorUtil.colorize(desc));
            }
            lines.add("");
        }

        if (spec.getActiveSkill() != null) {
            PetSkillSpec sk = spec.getActiveSkill();
            double effCdSec = Math.round((sk.getEffectiveCooldownMs(level, spec.getMaxLevel()) * cdMult) / 100.0) / 10.0;
            String cdTxt = Math.abs(effCdSec - Math.rint(effCdSec)) < 1e-4 ? String.valueOf((long) Math.rint(effCdSec)) : String.valueOf(effCdSec);
            lines.add("<#38BDF8>⚡ Auto Ability: &f" + sk.getDisplayName());
            lines.add("&7  Trigger: <#FDE047>" + sk.getTriggerConditionText());
            lines.add("&7  Cooldown: <#67E8F9>" + cdTxt + "s &8| &7Range: <#67E8F9>" + (int) Math.round(sk.getRange()) + "m");
            for (String sLine : sk.formatDescriptionLines(level, spec.getMaxLevel(), cdMult, powerMult)) {
                lines.add("&7  " + sLine);
            }
            lines.add("");
        }

        if (level >= spec.getMaxLevel()) {
            lines.add("<#10B981>MAX LEVEL &8(" + spec.getMaxLevel() + "/" + spec.getMaxLevel() + ")");
        } else {
            double req = spec.getXpRequiredForNextLevel(level, config);
            int pct = req > 0 ? (int) Math.min(100, Math.round((xp / req) * 100.0)) : 0;
            lines.add("&7Progress to Lv " + (level + 1) + ": &e" + pct + "% &8(&f" + Math.round(xp) + "&7/&f" + Math.round(req) + " XP&8)");
        }
        lines.add("");
        lines.add("<#FCD34D>▶ Right-Click to claim into your /pet Wardrobe");
        return lines;
    }

    public PetSpec getPet(String id) {
        String key = PlayerPetData.canonicalizePetId(id);
        return key != null ? pets.get(key) : null;
    }

    public Collection<PetSpec> getAllPets() {
        return Collections.unmodifiableCollection(pets.values());
    }

    private static String encodeTextureUrl(String hash) {
        String url = "http://textures.minecraft.net/texture/" + hash;
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private void generateDefaults() {
        // 1. COMMON (1 Perk) - Beginner Starter Companion
        writeDefault("spark", "Spark", "Emberkin", PetCategory.COMBAT, ItemRarity.COMMON,
                encodeTextureUrl("e7c11f1f6b6dcebcd2d3e209d18f1136b1d2b6d654c49fa111f78c281eeb6484"),
                100, "FLAME", "#FF9A3C", "#FFE066",
                stats(stat(ItemStat.SURGE, 1.5, 12.0), stat(ItemStat.CLEAVE, 0.5, 6.0)),
                new PetSkillSpec("spark_cinder", "Cinder Snap", PetSkillTrigger.ON_ATTACK,
                        "Fires automatically when striking a mob within 14m", 1.0,
                        12000L, 6000L, 16.0, 44.0, 14.0, "", "",
                        List.of("Lobs a blazing cinder orb dealing &c{pwr} &7damage & igniting target.")),
                List.of(
                        perk("Kindling Instinct", 1, PetPerkEffectType.BONUS_MOB_DAMAGE, 3.0, 14.0,
                                List.of("Increases combat damage dealt to mobs by &a+{val}%&7."))
                ));

        // 2. UNCOMMON (2 Perks)
        writeDefault("striker", "Striker", "Vanguard", PetCategory.COMBAT, ItemRarity.UNCOMMON,
                encodeTextureUrl("39ac7b385930184145547910099d042009b88b6d5b20d71601b95322628b"),
                100, "CRIT", "#38BDF8", "#60A5FA",
                stats(stat(ItemStat.SURGE, 2.0, 16.0), stat(ItemStat.EXECUTE, 1.5, 15.0)),
                new PetSkillSpec("striker_burst", "Tactical Salvo", PetSkillTrigger.ON_ATTACK,
                        "Fires rapid lock-on bolts when striking an enemy within 18m", 1.0,
                        10500L, 4800L, 20.0, 56.0, 18.0, "", "",
                        List.of("Fires a 4-shot pulse barrage dealing &c{pwr} &7total damage.")),
                List.of(
                        perk("Target Lock", 1, PetPerkEffectType.STAT_EXECUTE, 2.0, 10.0,
                                List.of("Grants &c+{val}% Execute &7while summoned.")),
                        perk("Rapid Cycle", 25, PetPerkEffectType.SKILL_COOLDOWN_HASTE, 5.0, 20.0,
                                List.of("Reduces pet ability cooldowns by &b{val}%&7."))
                ));

        writeDefault("ember", "Ember", "Furnace", PetCategory.MINING, ItemRarity.UNCOMMON,
                encodeTextureUrl("f937e1c4565a29d7c58969a7f3126b1704540f3b5f8e1a662796a64c561"),
                100, "SMALL_FLAME", "#FB923C", "#FACC15",
                stats(stat(ItemStat.SURGE, 1.8, 15.0), stat(ItemStat.CLEAVE, 1.5, 12.0)),
                new PetSkillSpec("ember_stream", "Magma Sweep", PetSkillTrigger.ON_ATTACK,
                        "Erupts in a flame arc when hitting a foe within 16m", 1.0,
                        11000L, 5000L, 21.0, 58.0, 16.0, "", "",
                        List.of("Sweeps molten fire in a cone dealing &c{pwr} &7damage & igniting mobs.")),
                List.of(
                        perk("Molten Vein", 1, PetPerkEffectType.GATHERING_FORTUNE, 5.0, 30.0,
                                List.of("&b{val}% &7chance to yield bonus drops when mining ores.")),
                        perk("Forge Temper", 25, PetPerkEffectType.BONUS_MOB_DAMAGE, 3.0, 15.0,
                                List.of("Deals &a+{val}% &7bonus damage against combat targets."))
                ));

        // 3. RARE (3 Perks)
        writeDefault("mend", "Mend", "Biofield", PetCategory.FARMING, ItemRarity.RARE,
                encodeTextureUrl("8a39196215c1372c7f34b98d9f818786d8b35e77243d708469f47b785f"),
                100, "HAPPY_VILLAGER", "#4ADE80", "#A7F3D0",
                stats(stat(ItemStat.SIPHON, 2.0, 18.0), stat(ItemStat.WARD, 2.0, 16.0)),
                new PetSkillSpec("mend_pulse", "Vital Resonance", PetSkillTrigger.ON_LOW_HP,
                        "Pulses when below 85% HP in combat (within 12m)", 0.85,
                        14000L, 6500L, 24.0, 64.0, 12.0, "", "",
                        List.of("Restores &a{heal} HP &7to you & allies and blasts foes for &c{half_pwr}&7.")),
                List.of(
                        perk("Verdant Extract", 1, PetPerkEffectType.STAT_SIPHON, 2.0, 12.0,
                                List.of("Grants &a+{val}% Siphon &7for life recovery.")),
                        perk("Bountiful Crop", 25, PetPerkEffectType.GATHERING_FORTUNE, 8.0, 38.0,
                                List.of("&b{val}% &7chance to double Farming crop harvests.")),
                        perk("Renewal Aura", 60, PetPerkEffectType.ALLY_AURA_REGEN, 1.5, 6.0,
                                List.of("Periodically heals you & whitelisted allies for &a{val} HP&7."))
                ));

        writeDefault("salvo", "Salvo", "Interceptor", PetCategory.FORAGING, ItemRarity.RARE,
                encodeTextureUrl("b41a2b13b671f66f9d2b578161a14a4484d67c6a128d1861d1477293c7"),
                100, "FIREWORK", "#F472B6", "#FB7185",
                stats(stat(ItemStat.EXECUTE, 2.5, 20.0), stat(ItemStat.CLEAVE, 2.0, 16.0), stat(ItemStat.RESONANCE, 8.0, 4.0)),
                new PetSkillSpec("salvo_barrage", "Cluster Barrage", PetSkillTrigger.ON_TARGET_LOCK,
                        "Fires seeking missiles when targeting an enemy within 24m", 1.0,
                        12500L, 5500L, 26.0, 72.0, 24.0, "", "",
                        List.of("Launches arcing projectiles dealing &c{pwr} &7AoE blast damage.")),
                List.of(
                        perk("Canopy Glide", 1, PetPerkEffectType.MOVEMENT_SPEED, 4.0, 18.0,
                                List.of("Grants &e+{val}% &7movement speed while summoned.")),
                        perk("Timber Bounty", 25, PetPerkEffectType.GATHERING_FORTUNE, 8.0, 38.0,
                                List.of("&b{val}% &7chance for extra drops when chopping logs.")),
                        perk("Heavy Warheads", 60, PetPerkEffectType.SKILL_POWER_BOOST, 8.0, 28.0,
                                List.of("Boosts active ability damage by &c+{val}%&7."))
                ));

        writeDefault("bastion", "Bastion", "Sentinel", PetCategory.MINING, ItemRarity.RARE,
                encodeTextureUrl("1b353277a941a2b073146a901c4166d1786522e078c9c70f745c39c3c8"),
                100, "ELECTRIC_SPARK", "#2DD4BF", "#38BDF8",
                stats(stat(ItemStat.WARD, 3.0, 24.0), stat(ItemStat.STASIS, 2.0, 18.0)),
                new PetSkillSpec("bastion_aegis", "Aegis Shockwave", PetSkillTrigger.ON_DAMAGED,
                        "Deploys automatically whenever you take combat damage", 1.0,
                        13500L, 6000L, 24.0, 66.0, 14.0, "drone_shieldvfx", "shield",
                        List.of("Projects an absorption dome, knocks attackers away & deals &c{pwr}&7.")),
                List.of(
                        perk("Plated Core", 1, PetPerkEffectType.DAMAGE_REDUCTION, 4.0, 18.0,
                                List.of("Reduces incoming damage by &b{val}%&7.")),
                        perk("Deep Quarry", 25, PetPerkEffectType.GATHERING_FORTUNE, 8.0, 40.0,
                                List.of("&b{val}% &7chance for bonus stone & ore drops underground.")),
                        perk("Citadel Ward", 60, PetPerkEffectType.STAT_WARD, 3.0, 14.0,
                                List.of("Grants &3+{val}% Ward &7defense."))
                ));

        // 4. EPIC (4 Perks)
        writeDefault("zephyr", "Zephyr", "Galeborn", PetCategory.FORAGING, ItemRarity.EPIC,
                encodeTextureUrl("f25b96f6b4799ef67ff9a2fb1238cbab1bedaa82a101fb471a9ca550c122f93"),
                100, "CLOUD", "#7DD3FC", "#E0F2FE",
                stats(stat(ItemStat.SURGE, 3.0, 26.0), stat(ItemStat.RESONANCE, 8.0, 3.0)),
                new PetSkillSpec("zephyr_tempest", "Cyclone Vortex", PetSkillTrigger.ON_TARGET_LOCK,
                        "Unleashes a pulling hurricane on looked-at enemy hit within 24m", 1.0,
                        11500L, 5000L, 28.0, 84.0, 24.0, "dce_vfx_zephyr", "execute",
                        List.of("Draws enemies inward, launches them & deals &c{pwr} &7wind damage.")),
                List.of(
                        perk("Tailwind", 1, PetPerkEffectType.MOVEMENT_SPEED, 5.0, 22.0,
                                List.of("Grants &e+{val}% &7movement speed across terrain.")),
                        perk("Storm Capacitor", 20, PetPerkEffectType.STAT_SURGE, 3.0, 14.0,
                                List.of("Grants &b+{val}% Surge &7while active.")),
                        perk("Sylvan Gust", 50, PetPerkEffectType.GATHERING_FORTUNE, 10.0, 45.0,
                                List.of("&b{val}% &7chance for bonus Foraging resource yields.")),
                        perk("Eye of the Gale", 80, PetPerkEffectType.SKILL_COOLDOWN_HASTE, 8.0, 25.0,
                                List.of("Reduces active skill cooldown by &b{val}%&7."))
                ));

        writeDefault("terra", "Terra", "Monolith", PetCategory.MINING, ItemRarity.EPIC,
                encodeTextureUrl("a722a6bf4f199525122ea6b11ca2491b4ca3d36de0d1f775be4deabcc2652"),
                100, "CRIT", "#D97706", "#F59E0B",
                stats(stat(ItemStat.WARD, 3.5, 26.0), stat(ItemStat.STASIS, 3.0, 24.0)),
                new PetSkillSpec("terra_rupture", "Tectonic Fissure", PetSkillTrigger.ON_ATTACK,
                        "Splits the earth along your strike line within 22m", 1.0,
                        13000L, 6000L, 32.0, 92.0, 22.0, "dce_vfx_terra", "execute",
                        List.of("Launches tectonic boulders dealing &c{pwr} &7damage & Heavy Slow.")),
                List.of(
                        perk("Basalt Armor", 1, PetPerkEffectType.DAMAGE_REDUCTION, 5.0, 22.0,
                                List.of("Reduces incoming combat damage by &b{val}%&7.")),
                        perk("Mantle Vein", 20, PetPerkEffectType.GATHERING_FORTUNE, 10.0, 48.0,
                                List.of("&b{val}% &7chance to duplicate mined ores.")),
                        perk("Seismic Anchor", 50, PetPerkEffectType.STAT_STASIS, 4.0, 16.0,
                                List.of("Grants &9+{val}% Stasis &7crowd control.")),
                        perk("Titan Breaker", 80, PetPerkEffectType.BOSS_SLAYER_DAMAGE, 8.0, 28.0,
                                List.of("Deals &c+{val}% &7extra damage to bosses & elites."))
                ));

        writeDefault("verdant", "Verdant", "Thornbloom", PetCategory.FARMING, ItemRarity.EPIC,
                encodeTextureUrl("52e15a56d02f4d996f6b4b383d592836ef3c431ef854c21506101ff1d2cf"),
                100, "SPORE_BLOSSOM_AIR", "#34D399", "#A3E635",
                stats(stat(ItemStat.SIPHON, 3.5, 26.0), stat(ItemStat.STASIS, 2.5, 20.0)),
                new PetSkillSpec("verdant_bloom", "Solar Verdure Beam", PetSkillTrigger.ON_COMBAT_ANY,
                        "Channels a piercing beam during combat within 24m", 1.0,
                        12500L, 5800L, 30.0, 86.0, 24.0, "dce_vfx_verdant", "execute",
                        List.of("Pierces foes with solar flora for &c{pwr} &7damage & roots targets.")),
                List.of(
                        perk("Lush Harvest", 1, PetPerkEffectType.GATHERING_FORTUNE, 10.0, 50.0,
                                List.of("&b{val}% &7chance for bonus Farming harvests.")),
                        perk("Sap Leech", 20, PetPerkEffectType.VAMPIRIC_HEAL, 2.0, 8.0,
                                List.of("Restores &a{val}% &7of combat damage dealt as health.")),
                        perk("Grove Sanctuary", 50, PetPerkEffectType.ALLY_AURA_REGEN, 2.0, 7.0,
                                List.of("Periodically heals you & allies for &a{val} HP&7.")),
                        perk("Ancient Growth", 80, PetPerkEffectType.PET_XP_BOOST, 10.0, 35.0,
                                List.of("Increases Skill XP gained by this pet by &a+{val}%&7."))
                ));

        writeDefault("glacies", "Glacies", "Permafrost", PetCategory.FISHING, ItemRarity.EPIC,
                encodeTextureUrl("b1d6d0e3c7b4f7c148ca39c07bc910d9ff21ef4b78c73f05988783e266be1b"),
                100, "SNOWFLAKE", "#93C5FD", "#E0F2FE",
                stats(stat(ItemStat.STASIS, 4.0, 30.0), stat(ItemStat.WARD, 2.5, 20.0)),
                new PetSkillSpec("glacies_permafrost", "Permafrost Nova", PetSkillTrigger.ON_ATTACK,
                        "Encases targets in ice when striking within 22m", 1.0,
                        13500L, 6000L, 28.0, 84.0, 22.0, "dce_vfx_glacies", "execute",
                        List.of("Locks nearby enemies in glacial frost & deals &c{pwr} &7shatter damage.")),
                List.of(
                        perk("Rime Shackles", 1, PetPerkEffectType.STAT_STASIS, 4.0, 18.0,
                                List.of("Grants &9+{val}% Stasis &7slow & freeze control.")),
                        perk("Tidal Fortune", 20, PetPerkEffectType.GATHERING_FORTUNE, 10.0, 45.0,
                                List.of("&b{val}% &7bonus fortune on Fishing & aquatic hauls.")),
                        perk("Cryo Carapace", 50, PetPerkEffectType.DAMAGE_REDUCTION, 4.0, 18.0,
                                List.of("Reduces incoming combat damage by &b{val}%&7.")),
                        perk("Shatter Spire", 80, PetPerkEffectType.SKILL_POWER_BOOST, 10.0, 32.0,
                                List.of("Increases Permafrost Nova damage by &c+{val}%&7."))
                ));

        // 5. LEGENDARY (5 Perks)
        writeDefault("pyre", "Pyre", "Infernal", PetCategory.COMBAT, ItemRarity.LEGENDARY,
                encodeTextureUrl("40e2f7f161b6f36f1c52d10a6d2a4eb63992fa6b1b7a7f14b211a8b1a9cf2"),
                100, "FLAME", "#F97316", "#EF4444",
                stats(stat(ItemStat.CLEAVE, 4.0, 32.0), stat(ItemStat.EXECUTE, 3.0, 26.0)),
                new PetSkillSpec("pyre_cataclysm", "Solar Inferno Volley", PetSkillTrigger.ON_TARGET_LOCK,
                        "Launches 3 homing fireballs on targeted enemy hit within 26m", 1.0,
                        11500L, 4800L, 36.0, 108.0, 26.0, "dce_vfx_pyre", "execute",
                        List.of("Barrages targets with 3 fireballs totaling &c{pwr} &7explosive damage.")),
                List.of(
                        perk("Blazing Fangs", 1, PetPerkEffectType.BONUS_MOB_DAMAGE, 5.0, 24.0,
                                List.of("Deals &c+{val}% &7bonus damage against mobs.")),
                        perk("Wildfire Arc", 20, PetPerkEffectType.STAT_CLEAVE, 4.0, 18.0,
                                List.of("Grants &6+{val}% Cleave &7splash damage.")),
                        perk("Scorched Verdict", 45, PetPerkEffectType.STAT_EXECUTE, 4.0, 16.0,
                                List.of("Grants &c+{val}% Execute &7against weakened enemies.")),
                        perk("Solar Ignition", 70, PetPerkEffectType.SKILL_POWER_BOOST, 10.0, 35.0,
                                List.of("Amplifies Inferno Volley damage by &c+{val}%&7.")),
                        perk("Calamity Flame", 90, PetPerkEffectType.BOSS_SLAYER_DAMAGE, 12.0, 38.0,
                                List.of("Deals &c+{val}% &7bonus damage against bosses."))
                ));

        writeDefault("umbra", "Umbra", "Voidborn", PetCategory.ALCHEMY, ItemRarity.LEGENDARY,
                encodeTextureUrl("21ff3cd135b8a4ca1c0b3cefa95e933fa2dc3ed39da1bf31b00eb765cf38fe"),
                100, "PORTAL", "#A855F7", "#4C1D95",
                stats(stat(ItemStat.CATALYST, 4.5, 36.0), stat(ItemStat.EXECUTE, 3.0, 24.0)),
                new PetSkillSpec("umbra_eclipse", "Abyssal Eclipse", PetSkillTrigger.ON_TARGET_LOCK,
                        "Implodes around your targeted foe on hit within 24m", 1.0,
                        13000L, 5500L, 34.0, 104.0, 24.0, "dce_vfx_umbra", "execute",
                        List.of("Sinks & weakens enemies in an abyssal vortex for &c{pwr} &7damage.")),
                List.of(
                        perk("Alchemical Void", 1, PetPerkEffectType.STAT_CATALYST, 4.0, 18.0,
                                List.of("Grants &d+{val}% Catalyst &7amplification.")),
                        perk("Soul Draught", 20, PetPerkEffectType.VAMPIRIC_HEAL, 2.5, 10.0,
                                List.of("Drains &a{val}% &7of combat damage dealt as health.")),
                        perk("Night Shroud", 45, PetPerkEffectType.DAMAGE_REDUCTION, 4.0, 18.0,
                                List.of("Reduces incoming damage by &b{val}%&7.")),
                        perk("Dark Elixir", 70, PetPerkEffectType.PET_XP_BOOST, 10.0, 40.0,
                                List.of("Increases Skill XP earned by this pet by &a+{val}%&7.")),
                        perk("Executioner's Eclipse", 90, PetPerkEffectType.BOSS_SLAYER_DAMAGE, 12.0, 36.0,
                                List.of("Deals &c+{val}% &7bonus damage against bosses."))
                ));

        writeDefault("solis", "Solis", "Aurelian", PetCategory.OMNI, ItemRarity.LEGENDARY,
                encodeTextureUrl("d5c6dc2bbf51c36cfc7714585a6a5683ef2b14d47d8ff714654a893f5da622"),
                100, "END_ROD", "#FDE047", "#F59E0B",
                stats(stat(ItemStat.WARD, 3.5, 28.0), stat(ItemStat.SURGE, 3.0, 26.0), stat(ItemStat.SIPHON, 2.5, 20.0)),
                new PetSkillSpec("solis_zenith", "Radiant Zenith", PetSkillTrigger.ON_COMBAT_ANY,
                        "Summons 8 solar spears + final zenith burst in combat within 26m", 1.0,
                        12500L, 5200L, 35.0, 106.0, 26.0, "dce_vfx_solis", "execute",
                        List.of("Strikes enemies for &c{pwr} &7radiant damage & heals allies for &a{heal}&7.")),
                List.of(
                        perk("Polymath Grace", 1, PetPerkEffectType.PET_XP_BOOST, 8.0, 35.0,
                                List.of("Gains 100% XP from all Skills plus &a+{val}% &7bonus XP.")),
                        perk("Solar Sanctum", 20, PetPerkEffectType.ALLY_AURA_REGEN, 2.0, 8.0,
                                List.of("Periodically heals you & allies for &a{val} HP&7.")),
                        perk("Dawn Bulwark", 45, PetPerkEffectType.STAT_WARD, 4.0, 16.0,
                                List.of("Grants &3+{val}% Ward &7defense.")),
                        perk("Meridian Haste", 70, PetPerkEffectType.SKILL_COOLDOWN_HASTE, 8.0, 28.0,
                                List.of("Reduces pet ability cooldowns by &b{val}%&7.")),
                        perk("Zenith Majesty", 90, PetPerkEffectType.BONUS_MOB_DAMAGE, 10.0, 32.0,
                                List.of("Increases combat damage dealt to mobs by &c+{val}%&7."))
                ));

        writeDefault("astral", "Astral", "Celestial", PetCategory.ENCHANTING, ItemRarity.LEGENDARY,
                encodeTextureUrl("815a3f1a6f7f508fb6ff36e2f5b10f61f8cb4b72e40741f6cf5cc758c9fc32f"),
                100, "WITCH", "#C084FC", "#60A5FA",
                stats(stat(ItemStat.SURGE, 4.0, 32.0), stat(ItemStat.CATALYST, 3.5, 28.0), stat(ItemStat.RESONANCE, 7.0, 3.0)),
                new PetSkillSpec("astral_supernova", "Cosmic Supernova", PetSkillTrigger.ON_TARGET_LOCK,
                        "Warps through enemies 4 times on targeted hit within 28m", 1.0,
                        13500L, 5500L, 38.0, 114.0, 28.0, "dce_vfx_astral", "execute",
                        List.of("4-hop warp strikes culminating in a &c{pwr} &7supernova detonation.")),
                List.of(
                        perk("Starlight Conduit", 1, PetPerkEffectType.STAT_SURGE, 4.0, 18.0,
                                List.of("Grants &b+{val}% Surge &7arcane scaling.")),
                        perk("Astral Catalyst", 20, PetPerkEffectType.STAT_CATALYST, 4.0, 16.0,
                                List.of("Grants &d+{val}% Catalyst &7intensity.")),
                        perk("Chrono Warp", 45, PetPerkEffectType.SKILL_COOLDOWN_HASTE, 10.0, 30.0,
                                List.of("Reduces active skill cooldowns by &b{val}%&7.")),
                        perk("Runic Savant", 70, PetPerkEffectType.PET_XP_BOOST, 12.0, 45.0,
                                List.of("Increases Skill XP earned by &a+{val}%&7.")),
                        perk("Supernova Core", 90, PetPerkEffectType.SKILL_POWER_BOOST, 15.0, 42.0,
                                List.of("Amplifies Cosmic Supernova power by &c+{val}%&7."))
                ));

        // 6. MASTERY (5 Perks)
        writeDefault("crimson", "Crimson", "Sovereign", PetCategory.COMBAT, ItemRarity.MASTERY,
                encodeTextureUrl("26e6fb16de32d959f1f385f3c8fc64743881114b7af56f7dc7483fb2fee0"),
                100, "DRAGON_BREATH", "#EF4444", "#FCA5A5",
                stats(stat(ItemStat.SURGE, 5.0, 38.0), stat(ItemStat.EXECUTE, 5.0, 36.0),
                        stat(ItemStat.CLEAVE, 4.0, 32.0), stat(ItemStat.SIPHON, 3.0, 22.0)),
                new PetSkillSpec("crimson_dominion", "Crimson Sovereign Nova", PetSkillTrigger.ON_ATTACK,
                        "Detonates crimson shockwaves & lightning on combat hit within 26m", 1.0,
                        11000L, 4200L, 44.0, 136.0, 26.0, "dce_vfx_pyre", "execute",
                        List.of("Calls crimson lightning & nova rings dealing &c{pwr} &7execution damage.")),
                List.of(
                        perk("Sovereign Blood", 1, PetPerkEffectType.BONUS_MOB_DAMAGE, 8.0, 32.0,
                                List.of("Increases all combat damage against mobs by &c+{val}%&7.")),
                        perk("Predator Siphon", 15, PetPerkEffectType.VAMPIRIC_HEAL, 3.0, 12.0,
                                List.of("Heals you for &a{val}% &7of all combat damage dealt.")),
                        perk("Apex Cadence", 35, PetPerkEffectType.SKILL_COOLDOWN_HASTE, 10.0, 32.0,
                                List.of("Reduces pet ability cooldown by &b{val}%&7.")),
                        perk("Wyrm Guillotine", 65, PetPerkEffectType.STAT_EXECUTE, 5.0, 22.0,
                                List.of("Grants &c+{val}% Execute &7finishing power.")),
                        perk("Dominion Breaker", 90, PetPerkEffectType.BOSS_SLAYER_DAMAGE, 15.0, 48.0,
                                List.of("Deals &c+{val}% &7devastating bonus damage to bosses."))
                ));
    }

    private static Map<ItemStat, double[]> stats(Object... pairs) {
        Map<ItemStat, double[]> m = new EnumMap<>(ItemStat.class);
        for (Object p : pairs) {
            if (p instanceof Map.Entry<?, ?> entry && entry.getKey() instanceof ItemStat st && entry.getValue() instanceof double[] range) {
                m.put(st, range);
            }
        }
        return m;
    }

    private static Map.Entry<ItemStat, double[]> stat(ItemStat st, double minLv1, double maxLv100) {
        return new AbstractMap.SimpleEntry<>(st, new double[]{minLv1, maxLv100});
    }

    private static PetPerk perk(String name, int unlockLvl, PetPerkEffectType eff, double minVal, double maxVal, List<String> desc) {
        return new PetPerk(name, desc, unlockLvl, eff, minVal, maxVal);
    }

    private void writeDefault(String id,
                              String displayName,
                              String family,
                              PetCategory category,
                              ItemRarity rarity,
                              String headBase64,
                              int maxLevel,
                              String ambientParticle,
                              String colorFrom,
                              String colorTo,
                              Map<ItemStat, double[]> stats,
                              PetSkillSpec skill,
                              List<PetPerk> perks) {
        File f = new File(petsFolder, id + ".yml");
        if (f.exists()) {
            YamlConfiguration existing = YamlConfiguration.loadConfiguration(f);
            if (existing.getInt("config-version", 1) >= SCHEMA_VERSION) {
                return;
            }
        }

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("config-version", SCHEMA_VERSION);
        cfg.set("id", id);
        cfg.set("display-name", displayName);
        cfg.set("family", family);
        cfg.set("category", category.name());
        cfg.set("rarity", rarity.name());
        cfg.set("head-base64", headBase64);
        cfg.set("max-level", maxLevel);
        cfg.set("ambient-particle", ambientParticle);
        cfg.set("ambient-colors.from", colorFrom);
        cfg.set("ambient-colors.to", colorTo);
        cfg.set("stat-curve-exponent", 1.0);
        cfg.set("xp-curve.base", 120.0);
        cfg.set("xp-curve.coeff", 1.06);
        cfg.set("offset.forward", -0.35);
        cfg.set("offset.up", 0.78);
        cfg.set("offset.right", 0.75);
        cfg.set("offset.scale", 0.78);

        for (Map.Entry<ItemStat, double[]> entry : stats.entrySet()) {
            cfg.set("stats." + entry.getKey().name() + ".min", entry.getValue()[0]);
            cfg.set("stats." + entry.getKey().name() + ".max", entry.getValue()[1]);
        }

        if (skill != null) {
            cfg.set("active-skill.type", skill.getSkillId());
            cfg.set("active-skill.name", skill.getDisplayName());
            cfg.set("active-skill.trigger", skill.getTrigger().name());
            cfg.set("active-skill.trigger-condition", skill.getTriggerConditionText());
            cfg.set("active-skill.activation-threshold", skill.getActivationThreshold());
            cfg.set("active-skill.max-cooldown-ms", skill.getMaxCooldownMs());
            cfg.set("active-skill.min-cooldown-ms", skill.getMinCooldownMs());
            cfg.set("active-skill.min-power", skill.getMinPower());
            cfg.set("active-skill.max-power", skill.getMaxPower());
            cfg.set("active-skill.range", skill.getRange());
            cfg.set("active-skill.vfx-model", skill.getVfxModelId());
            cfg.set("active-skill.vfx-animation", skill.getVfxAnimation());
            cfg.set("active-skill.description", skill.getDescriptionLines());
        }

        int idx = 1;
        for (PetPerk perk : perks) {
            String key = "perks.perk_" + idx++;
            cfg.set(key + ".name", perk.getName());
            cfg.set(key + ".unlock-level", perk.getUnlockLevel());
            cfg.set(key + ".effect", perk.getEffectType().name());
            cfg.set(key + ".min-value", perk.getMinValue());
            cfg.set(key + ".max-value", perk.getMaxValue());
            cfg.set(key + ".description", perk.getDescriptionLines());
        }

        try {
            cfg.save(f);
        } catch (IOException e) {
            DCCosmetics.getInstance().getLogger().warning("[DCEPets] Could not save default pet config " + id + ": " + e.getMessage());
        }
    }
}
