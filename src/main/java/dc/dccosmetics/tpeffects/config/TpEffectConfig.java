package dc.dccosmetics.tpeffects.config;

import dc.dccosmetics.tpeffects.model.TpEffectEntry;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Loads and persists plugins/DCEssentials/tpeffects/config.yml and per-rarity effect files
 * (uncommon.yml, rare.yml, epic.yml, legendary.yml, mastery.yml, plus any custom *.yml).
 */
public final class TpEffectConfig {

    public enum BlackoutMode {
        AUTO,
        ULTIMATEUI_HUD,
        ULTIMATEUI_PANEL,
        TITLE_GLYPH,
        POTION_DARKNESS;

        public static BlackoutMode fromString(String raw) {
            if (raw == null) return AUTO;
            try {
                return BlackoutMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return AUTO;
            }
        }
    }

    private static final List<String> DEFAULT_RESOURCE_FILES = List.of(
            "config.yml",
            "uncommon.yml",
            "rare.yml",
            "epic.yml",
            "legendary.yml",
            "mastery.yml"
    );

    private final Plugin plugin;
    private final File folder;

    private boolean enabled = true;
    private double cooldownSeconds = 1.5;
    private int arrivalDelayTicks = 2;
    private double minDistanceNonCommand = 15.0;
    private double minDistanceCommand = 3.0;
    private final Set<TeleportCause> ignoredCauses = EnumSet.noneOf(TeleportCause.class);
    private final Set<String> disabledWorlds = new HashSet<>();

    private boolean abilityFallbackEnabled = true;
    private boolean abilityFallbackDebug = false;
    private final Set<String> ignoredMetadataKeys = new LinkedHashSet<>();
    private final Set<String> ignoredScoreboardTags = new LinkedHashSet<>();
    private final List<String> ignoredCallerPatterns = new ArrayList<>();
    private boolean abilityFallbackSuppressCombat = true;
    private double abilityFallbackCombatWindowSeconds = 4.0;
    private double abilityFallbackCombatMaxDistance = 64.0;

    private boolean syncLuckPermsSelectedPermission = true;
    private boolean migrateLegacyLuckPermsOnJoin = true;
    private boolean wardrobeClickToPreviewThenEquip = true;
    private List<String> rarityOrder = new ArrayList<>(List.of("uncommon", "rare", "epic", "legendary", "mastery"));

    private int cinematicSkinSampleStep = 2;
    private double cinematicCameraDistance = 3.45;
    private double cinematicCameraHeightOffset = 1.65;

    private BlackoutMode blackoutMode = BlackoutMode.AUTO;
    private String blackoutUltimateUiHud = "tpeffect_blackout";
    private String blackoutUltimateUiElement = "blackout_rect";
    private String blackoutTitleGlyph = "";
    private boolean blackoutUsePotionDarkness = true;

    public TpEffectConfig(Plugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "tpeffects");
    }

    public List<TpEffectEntry> loadAll() {
        ensureDefaults();
        loadMainConfig();
        return loadRarityFiles();
    }

    private void ensureDefaults() {
        if (!folder.exists()) {
            folder.mkdirs();
        }
        for (String name : DEFAULT_RESOURCE_FILES) {
            File target = new File(folder, name);
            if (!target.exists()) {
                try {
                    plugin.saveResource("tpeffects/" + name, false);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("[TpEffects] Missing bundled default resource tpeffects/" + name + ": " + ex.getMessage());
                }
            }
        }
    }

    private void loadMainConfig() {
        File cfgFile = new File(folder, "config.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(cfgFile);

        enabled = cfg.getBoolean("enabled", true);
        cooldownSeconds = Math.max(0.0, cfg.getDouble("cooldown-seconds", 1.5));
        arrivalDelayTicks = Math.max(0, cfg.getInt("arrival-delay-ticks", 2));
        minDistanceNonCommand = Math.max(0.0, cfg.getDouble("min-distance.non-command", 15.0));
        minDistanceCommand = Math.max(0.0, cfg.getDouble("min-distance.command", 3.0));

        ignoredCauses.clear();
        List<String> causes = cfg.getStringList("ignored-causes");
        if (causes.isEmpty() && !cfg.contains("ignored-causes")) {
            causes = List.of("SPECTATE", "DISMOUNT", "EXIT_BED", "UNKNOWN");
        }
        for (String rawCause : causes) {
            if (rawCause == null || rawCause.isBlank()) continue;
            String normalized = rawCause.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            try {
                ignoredCauses.add(TeleportCause.valueOf(normalized));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("[TpEffects] Unknown teleport cause in ignored-causes: " + rawCause);
            }
        }

        disabledWorlds.clear();
        for (String world : cfg.getStringList("disabled-worlds")) {
            if (world != null && !world.isBlank()) {
                disabledWorlds.add(world.trim().toLowerCase(Locale.ROOT));
            }
        }

        abilityFallbackEnabled = cfg.getBoolean("ability-fallback.enabled", true);
        abilityFallbackDebug = cfg.getBoolean("ability-fallback.debug", false);
        populateConfiguredStrings(
                cfg,
                "ability-fallback.ignored-metadata",
                List.of("no_tpeffect", "in_wardrobe", "dc_ability_tp"),
                ignoredMetadataKeys,
                false
        );
        populateConfiguredStrings(
                cfg,
                "ability-fallback.ignored-scoreboard-tags",
                List.of("no_tpeffect", "ability_teleport", "boss_skill"),
                ignoredScoreboardTags,
                true
        );
        populateConfiguredStrings(
                cfg,
                "ability-fallback.ignored-caller-patterns",
                List.of(
                        "dc.dcessentials.modules.items.",
                        "dc.dcessentials.modules.pets.",
                        "dc.dcessentials.modules.boss.",
                        "io.lumine.mythic.",
                        "io.lumine.mythiccrucible.",
                        "net.indyuce.mmoitems.",
                        "net.indyuce.mmocore.",
                        "com.ssomar.score.",
                        "dev.aurelium.auraskills.",
                        "su.nightexpress.excellentenchants.",
                        "me.frep.vulcan."
                ),
                ignoredCallerPatterns,
                true
        );
        abilityFallbackSuppressCombat = cfg.getBoolean("ability-fallback.suppress-during-combat.enabled", true);
        abilityFallbackCombatWindowSeconds = Math.max(0.0, cfg.getDouble("ability-fallback.suppress-during-combat.window-seconds", 4.0));
        abilityFallbackCombatMaxDistance = Math.max(0.0, cfg.getDouble("ability-fallback.suppress-during-combat.max-distance", 64.0));

        syncLuckPermsSelectedPermission = cfg.getBoolean("storage.sync-luckperms-selected-permission", true);
        migrateLegacyLuckPermsOnJoin = cfg.getBoolean("storage.migrate-legacy-luckperms-on-join", true);
        wardrobeClickToPreviewThenEquip = cfg.getBoolean("wardrobe.click-to-preview-then-equip", true);

        List<String> configuredOrder = cfg.getStringList("rarity-order");
        rarityOrder = new ArrayList<>();
        if (configuredOrder.isEmpty()) {
            rarityOrder.addAll(List.of("uncommon", "rare", "epic", "legendary", "mastery"));
        } else {
            for (String r : configuredOrder) {
                if (r != null && !r.isBlank()) {
                    rarityOrder.add(r.trim().toLowerCase(Locale.ROOT));
                }
            }
        }

        cinematicSkinSampleStep = Math.max(1, Math.min(4, cfg.getInt("cinematic.skin-sample-step", 2)));
        cinematicCameraDistance = Math.max(2.0, Math.min(7.0, cfg.getDouble("cinematic.camera-distance", 3.45)));
        cinematicCameraHeightOffset = Math.max(0.8, Math.min(3.0, cfg.getDouble("cinematic.camera-height-offset", 1.65)));

        blackoutMode = BlackoutMode.fromString(cfg.getString("cinematic.blackout.mode", "AUTO"));
        blackoutUltimateUiHud = cfg.getString("cinematic.blackout.ultimateui-hud", "tpeffect_blackout");
        blackoutUltimateUiElement = cfg.getString("cinematic.blackout.ultimateui-element", "blackout_rect");
        blackoutTitleGlyph = cfg.getString("cinematic.blackout.title-glyph", "");
        blackoutUsePotionDarkness = cfg.getBoolean("cinematic.blackout.use-potion-darkness", true);
    }

    private List<TpEffectEntry> loadRarityFiles() {
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml") && !name.equalsIgnoreCase("config.yml"));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        List<File> orderedFiles = new ArrayList<>(Arrays.asList(files));
        orderedFiles.sort((a, b) -> {
            int idxA = orderIndexForFile(a.getName());
            int idxB = orderIndexForFile(b.getName());
            if (idxA != idxB) return Integer.compare(idxA, idxB);
            return a.getName().compareToIgnoreCase(b.getName());
        });

        List<TpEffectEntry> entries = new ArrayList<>();
        for (File file : orderedFiles) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            String baseName = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
            String rarity = yml.getString("rarity", baseName).trim().toLowerCase(Locale.ROOT);

            ConfigurationSection effectsSec = yml.getConfigurationSection("effects");
            if (effectsSec == null) continue;

            for (String key : effectsSec.getKeys(false)) {
                ConfigurationSection sec = effectsSec.getConfigurationSection(key);
                if (sec == null) continue;

                String id = key.trim().toLowerCase(Locale.ROOT);
                boolean effEnabled = sec.getBoolean("enabled", true);
                boolean hidden = sec.getBoolean("hidden", false);
                String displayName = sec.getString("display-name", id);
                String permission = sec.getString("permission", "dc.tpeffect." + id);
                TpEffectEntry.EffectType type = TpEffectEntry.EffectType.fromString(sec.getString("type", "MYTHIC_MOB"));
                String mythicMob = sec.getString("mythic-mob", "");
                TpEffectEntry.CinematicStyle style = type == TpEffectEntry.EffectType.CINEMATIC
                        ? TpEffectEntry.CinematicStyle.fromString(sec.getString("cinematic-style", id))
                        : null;
                int defaultTicks = style != null ? style.getDefaultDurationTicks() : 80;
                int durationTicks = parseDurationTicks(sec.get("duration"), defaultTicks);

                String matName = sec.getString("material", "ENDER_PEARL").trim().toUpperCase(Locale.ROOT);
                Material material = Material.matchMaterial(matName);
                if (material == null) {
                    plugin.getLogger().warning("[TpEffects] Invalid material '" + matName + "' in " + file.getName() + " for '" + id + "', falling back to ENDER_PEARL.");
                    material = Material.ENDER_PEARL;
                }

                int cmd = Math.max(0, sec.getInt("custom-model-data", 0));
                List<String> lore = sec.getStringList("lore");

                entries.add(new TpEffectEntry(
                        id,
                        rarity,
                        file.getName(),
                        displayName,
                        effEnabled,
                        hidden,
                        permission,
                        type,
                        mythicMob,
                        style,
                        durationTicks,
                        material,
                        cmd,
                        lore
                ));
            }
        }
        return entries;
    }

    private int orderIndexForFile(String filename) {
        String base = filename.substring(0, filename.length() - 4).toLowerCase(Locale.ROOT);
        int idx = rarityOrder.indexOf(base);
        return idx >= 0 ? idx : Integer.MAX_VALUE;
    }

    /**
     * Parses YAML duration values such as "4s", "3.5s", "4500ms", "80t", or bare numeric seconds (e.g. 4.0)
     * into server ticks (clamped between 20 and 400 ticks).
     */
    public static int parseDurationTicks(Object raw, int defaultTicks) {
        if (raw == null) return defaultTicks;
        double seconds;
        if (raw instanceof Number num) {
            seconds = num.doubleValue();
        } else {
            String s = raw.toString().trim().toLowerCase(Locale.ROOT);
            if (s.isEmpty()) return defaultTicks;
            try {
                if (s.endsWith("ms")) {
                    seconds = Double.parseDouble(s.substring(0, s.length() - 2).trim()) / 1000.0;
                } else if (s.endsWith("ticks")) {
                    int ticks = (int) Math.round(Double.parseDouble(s.substring(0, s.length() - 5).trim()));
                    return Math.max(20, Math.min(400, ticks));
                } else if (s.endsWith("t")) {
                    int ticks = (int) Math.round(Double.parseDouble(s.substring(0, s.length() - 1).trim()));
                    return Math.max(20, Math.min(400, ticks));
                } else if (s.endsWith("s")) {
                    seconds = Double.parseDouble(s.substring(0, s.length() - 1).trim());
                } else {
                    seconds = Double.parseDouble(s);
                }
            } catch (NumberFormatException ignored) {
                return defaultTicks;
            }
        }
        int ticks = (int) Math.round(seconds * 20.0);
        return Math.max(20, Math.min(400, ticks));
    }

    /**
     * Persists a toggle ('enabled' or 'hidden') for an entry back to its source rarity YAML file.
     */
    public boolean saveEffectBoolean(TpEffectEntry entry, String property, boolean value) {
        File file = new File(folder, entry.getSourceFile());
        if (!file.exists()) return false;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection effects = yml.getConfigurationSection("effects");
        if (effects == null) return false;

        // Match case-insensitively in YAML in case an admin edited casing manually
        String matchedKey = null;
        for (String key : effects.getKeys(false)) {
            if (key.equalsIgnoreCase(entry.getId())) {
                matchedKey = key;
                break;
            }
        }
        if (matchedKey == null) return false;

        yml.set("effects." + matchedKey + "." + property, value);
        try {
            yml.save(file);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("[TpEffects] Failed to save " + file.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    public File getFolder() {
        return folder;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getCooldownSeconds() {
        return cooldownSeconds;
    }

    public int getArrivalDelayTicks() {
        return arrivalDelayTicks;
    }

    public double getMinDistanceNonCommand() {
        return minDistanceNonCommand;
    }

    public double getMinDistanceCommand() {
        return minDistanceCommand;
    }

    public boolean isCauseIgnored(TeleportCause cause) {
        return cause != null && ignoredCauses.contains(cause);
    }

    public boolean isWorldDisabled(String worldName) {
        return worldName != null && disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public boolean isSyncLuckPermsSelectedPermission() {
        return syncLuckPermsSelectedPermission;
    }

    public boolean isMigrateLegacyLuckPermsOnJoin() {
        return migrateLegacyLuckPermsOnJoin;
    }

    public boolean isWardrobeClickToPreviewThenEquip() {
        return wardrobeClickToPreviewThenEquip;
    }

    public List<String> getRarityOrder() {
        return Collections.unmodifiableList(rarityOrder);
    }

    public int getCinematicSkinSampleStep() {
        return cinematicSkinSampleStep;
    }

    public double getCinematicCameraDistance() {
        return cinematicCameraDistance;
    }

    public double getCinematicCameraHeightOffset() {
        return cinematicCameraHeightOffset;
    }

    public BlackoutMode getBlackoutMode() {
        return blackoutMode;
    }

    public String getBlackoutUltimateUiHud() {
        return blackoutUltimateUiHud;
    }

    public String getBlackoutUltimateUiElement() {
        return blackoutUltimateUiElement;
    }

    public String getBlackoutTitleGlyph() {
        return blackoutTitleGlyph;
    }

    private void populateConfiguredStrings(
            YamlConfiguration cfg,
            String path,
            List<String> defaults,
            Collection<String> out,
            boolean lowercase
    ) {
        out.clear();
        List<String> raw = cfg.contains(path) ? cfg.getStringList(path) : defaults;
        for (String val : raw) {
            if (val == null || val.isBlank()) continue;
            String trimmed = val.trim();
            out.add(lowercase ? trimmed.toLowerCase(Locale.ROOT) : trimmed);
        }
    }

    public boolean isBlackoutUsePotionDarkness() {
        return blackoutUsePotionDarkness;
    }

    public boolean isAbilityFallbackEnabled() {
        return abilityFallbackEnabled;
    }

    public boolean isAbilityFallbackDebug() {
        return abilityFallbackDebug;
    }

    public Set<String> getIgnoredMetadataKeys() {
        return Collections.unmodifiableSet(ignoredMetadataKeys);
    }

    public Set<String> getIgnoredScoreboardTags() {
        return Collections.unmodifiableSet(ignoredScoreboardTags);
    }

    public List<String> getIgnoredCallerPatterns() {
        return Collections.unmodifiableList(ignoredCallerPatterns);
    }

    public boolean isAbilityFallbackSuppressCombat() {
        return abilityFallbackSuppressCombat;
    }

    public double getAbilityFallbackCombatWindowSeconds() {
        return abilityFallbackCombatWindowSeconds;
    }

    public double getAbilityFallbackCombatMaxDistance() {
        return abilityFallbackCombatMaxDistance;
    }
}
