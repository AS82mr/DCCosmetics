package dc.dccosmetics.wardrobe.hud;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.wardrobe.WardrobeSession;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-angle HUD layout configuration.
 *
 * There are 5 camera angles:
 *   full-body  — CATEGORIES view (all slots visible)
 *   head       — zoomed on head slot
 *   chest      — zoomed on chest slot
 *   waist      — zoomed on waist slot
 *   boots      — zoomed on boots slot
 *
 * Each angle has 3 independent panels:
 *   left    — scrollable cosmetic list
 *   info    — item name / action info
 *   keybind — keybind hints
 *
 * That gives 5 × 3 = 15 individually adjustable PanelConfigs.
 */
public class HudConfig {

    // ── Per-panel config ─────────────────────────────────────────
    public static class PanelConfig {
        public float  scale       = 0.18f;
        public double forward     = 1.5;
        public double rightOffset = 0.0;   // + = right, − = left of camera centre
        public double yOffset     = 0.0;   // + = up, − = down from camera eye level

        public PanelConfig() {}

        public PanelConfig(float scale, double forward, double rightOffset, double yOffset) {
            this.scale       = scale;
            this.forward     = forward;
            this.rightOffset = rightOffset;
            this.yOffset     = yOffset;
        }
    }

    // ── Per-angle config (4 panels) ──────────────────────────────
    public static class AngleConfig {
        public PanelConfig left    = new PanelConfig(0.18f, 1.5, -2.6,  0.0);
        public PanelConfig modular = new PanelConfig(0.18f, 1.5, -2.6,  0.0);
        public PanelConfig info    = new PanelConfig(0.18f, 1.5,  0.0, -0.15);
        public PanelConfig keybind = new PanelConfig(0.18f, 1.5,  0.0, -0.35);
    }

    // ── 5 angle configs ──────────────────────────────────────────
    public Map<String, AngleConfig> angles = new LinkedHashMap<>();

    public static final String[] ANGLE_NAMES = {"full-body", "head", "chest", "waist", "boots"};
    private static final String FILE = "wardrobe.yml";

    // ── Singleton ────────────────────────────────────────────────
    private static HudConfig instance;
    public static HudConfig get() {
        if (instance == null) instance = new HudConfig();
        return instance;
    }

    private HudConfig() {
        for (String angle : ANGLE_NAMES) {
            angles.put(angle, new AngleConfig());
        }
        load();
    }

    // ── Resolve current angle from session ───────────────────────

    /**
     * Returns the AngleConfig appropriate for the player's current wardrobe state.
     */
    public AngleConfig angleFor(WardrobeSession session) {
        String key;
        if (session.getCurrentLevel() == WardrobeSession.MenuLevel.CATEGORIES) {
            key = "full-body";
        } else {
            String cat = session.getSelectedCategory();
            key = (cat != null) ? cat.toLowerCase() : "full-body";
        }
        return angles.getOrDefault(key, angles.get("full-body"));
    }

    // ── Studio accessor: panel by "angle:panel" key ──────────────
    public PanelConfig panelByKey(String angleKey, String panelKey) {
        AngleConfig ac = angles.get(angleKey);
        if (ac == null) return null;
        switch (panelKey) {
            case "left":    return ac.left;
            case "modular": return ac.modular;
            case "info":    return ac.info;
            case "keybind": return ac.keybind;
        }
        return null;
    }

    // ── Load ─────────────────────────────────────────────────────
    public void load() {
        File file = new File(DCCosmetics.getInstance().getDataFolder(), FILE);
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        for (String angle : ANGLE_NAMES) {
            AngleConfig ac = angles.getOrDefault(angle, new AngleConfig());
            String base = "hud.angles." + angle + ".";
            ac.left    = readPanel(cfg, base + "left",    ac.left);
            ac.modular = readPanel(cfg, base + "modular", ac.left); // fallback to left if modular not present
            ac.info    = readPanel(cfg, base + "info",    ac.info);
            ac.keybind = readPanel(cfg, base + "keybind", ac.keybind);
            angles.put(angle, ac);
        }
    }

    private PanelConfig readPanel(YamlConfiguration cfg, String path, PanelConfig def) {
        if (!cfg.contains(path)) return def;
        return new PanelConfig(
            (float) cfg.getDouble(path + ".scale",        def.scale),
                    cfg.getDouble(path + ".forward",       def.forward),
                    cfg.getDouble(path + ".right-offset",  def.rightOffset),
                    cfg.getDouble(path + ".y-offset",      def.yOffset)
        );
    }

    // ── Save ─────────────────────────────────────────────────────
    public void save() {
        File file = new File(DCCosmetics.getInstance().getDataFolder(), FILE);
        YamlConfiguration cfg = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();

        for (String angle : ANGLE_NAMES) {
            AngleConfig ac = angles.getOrDefault(angle, new AngleConfig());
            String base = "hud.angles." + angle + ".";
            writePanel(cfg, base + "left",    ac.left);
            writePanel(cfg, base + "modular", ac.modular);
            writePanel(cfg, base + "info",    ac.info);
            writePanel(cfg, base + "keybind", ac.keybind);
        }

        try { cfg.save(file); }
        catch (IOException e) {
            DCCosmetics.getInstance().getLogger().warning("[HudConfig] Save failed: " + e.getMessage());
        }
    }

    private void writePanel(YamlConfiguration cfg, String path, PanelConfig p) {
        cfg.set(path + ".scale",        (double) p.scale);
        cfg.set(path + ".forward",      p.forward);
        cfg.set(path + ".right-offset", p.rightOffset);
        cfg.set(path + ".y-offset",     p.yOffset);
    }
}
