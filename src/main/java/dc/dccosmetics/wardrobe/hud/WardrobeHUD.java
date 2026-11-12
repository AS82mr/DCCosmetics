package dc.dccosmetics.wardrobe.hud;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.wardrobe.WardrobeSession;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wardrobe HUD — Cyberpunk Holographic UI
 *
 * Design principle: ALL panels are spawned relative to the current CAMERA position,
 * not the player spawn point. Whenever the camera moves, repositionForCamera() must
 * be called so panels follow the view.
 *
 * Layout (in camera-local space):
 *   LEFT panel   = categories / rarity / item list   → right * (-2.5), forward * 3
 *   CENTER panel = info / equip prompt                → center,         forward * 3, y -0.3
 *   BOTTOM panel = keybind hints                      → center,         forward * 3, y -1.0
 *
 * Modular button mode (toggled by wardrobe.yml hud.modular-buttons):
 *   LEFT panel is decomposed into individual HudButton entities (one per menu row),
 *   all in the same plane as before.  Info + keybind panels remain unified TextDisplays.
 */
public class WardrobeHUD {

    private final WardrobeSession session;

    // Legacy unified panels (always used for info + keybind; optionally for left panel)
    private final Map<String, HudTextDisplay> panels = new HashMap<>();

    // Modular button group (replaces unified left panel when modular mode is on)
    private HudButtonGroup buttonGroup;
    private final boolean  modularMode;

    // Track the last camera / mannequin locs so renderAll can reposition without extra args
    private Location lastCamLoc;
    private Location lastMannequinLoc;

    // Cached camera-local vectors (recalculated on reposition)
    private Vector lastForward;
    private Vector lastRight;
    private Vector lastTrueUp;

    public WardrobeHUD(WardrobeSession session) {
        this.session = session;
        this.modularMode = loadModularSetting();
    }

    private boolean loadModularSetting() {
        try {
            File f = new File(DCCosmetics.getInstance().getDataFolder(), "wardrobe.yml");
            if (!f.exists()) return false;
            return YamlConfiguration.loadConfiguration(f).getBoolean("hud.modular-buttons", false);
        } catch (Exception e) {
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  SPAWN + REPOSITION
    // ─────────────────────────────────────────────────────────────

    public void spawnAll() {
        dc.dccosmetics.wardrobe.WardrobeRoom room = new dc.dccosmetics.wardrobe.WardrobeRoom();
        room.load();
        Location spawn = room.getSpawn();
        if (spawn == null) spawn = session.getPlayer().getLocation();

        Location mannequin = room.getNpcStand();
        if (mannequin == null) mannequin = spawn;

        lastCamLoc = spawn;
        lastMannequinLoc = mannequin;
        computeVectors(spawn, mannequin);

        HudConfig.AngleConfig ac = HudConfig.get().angleFor(session);

        if (modularMode) {
            buttonGroup = new HudButtonGroup(session.getPlayer());
            // rows will be set by renderAll before spawn is called
        } else {
            // Legacy: spawn unified left panel
            HudTextDisplay leftPanel = new HudTextDisplay(session.getPlayer(), spawn);
            leftPanel.spawn();
            panels.put("left", leftPanel);
        }

        // Info + keybind always use unified TextDisplay
        HudTextDisplay infoPanel = new HudTextDisplay(session.getPlayer(), spawn);
        HudTextDisplay keyPanel  = new HudTextDisplay(session.getPlayer(), spawn);
        infoPanel.spawn();
        keyPanel.spawn();
        panels.put("info",    infoPanel);
        panels.put("keybind", keyPanel);

        repositionForCamera(spawn, mannequin);
        renderAll();
    }

    /**
     * Destroys all panels and respawns them in front of the new camera position.
     * Called every time the camera moves.
     */
    public void repositionForCamera(Location camLoc, Location mannequinLoc) {
        if (panels.isEmpty() && buttonGroup == null) return;
        lastCamLoc      = camLoc;
        lastMannequinLoc = mannequinLoc;
        computeVectors(camLoc, mannequinLoc);

        HudConfig.AngleConfig ac = HudConfig.get().angleFor(session);

        Location infoLoc = panelLoc(camLoc, lastForward, lastRight, lastTrueUp, ac.info);
        Location keyLoc  = panelLoc(camLoc, lastForward, lastRight, lastTrueUp, ac.keybind);

        repositionPanel("info",    infoLoc,  ac.info.scale);
        repositionPanel("keybind", keyLoc,   ac.keybind.scale);

        if (modularMode) {
            if (buttonGroup != null) {
                buttonGroup.reposition(camLoc, lastForward, lastRight, lastTrueUp, ac.modular);
            }
        } else {
            Location leftLoc = panelLoc(camLoc, lastForward, lastRight, lastTrueUp, ac.left);
            repositionPanel("left", leftLoc, ac.left.scale);
        }

        renderAll();
    }

    private void computeVectors(Location camLoc, Location mannequinLoc) {
        lastForward = mannequinLoc.toVector().subtract(camLoc.toVector()).normalize();
        Vector up   = new Vector(0, 1, 0);
        lastRight   = lastForward.clone().crossProduct(up).normalize();
        lastTrueUp  = lastRight.clone().crossProduct(lastForward).normalize();
    }

    /**
     * Computes the world-space Location for a panel given camera vectors and its PanelConfig.
     */
    private Location panelLoc(Location camLoc, Vector forward, Vector right, Vector trueUp,
                               HudConfig.PanelConfig p) {
        Location loc = camLoc.clone()
                .add(forward.clone().multiply(p.forward))
                .add(right.clone().multiply(p.rightOffset))
                .add(trueUp.clone().multiply(p.yOffset));
        loc.setDirection(camLoc.toVector().subtract(loc.toVector()));
        loc.setPitch(0);
        return loc;
    }

    /** Destroys the named panel and spawns a fresh one at the given location with given scale. */
    private void repositionPanel(String name, Location loc, float scale) {
        HudTextDisplay old = panels.get(name);
        if (old != null) old.destroyAnimated();

        HudTextDisplay fresh = new HudTextDisplay(session.getPlayer(), loc);
        fresh.spawn();           // sends scale=0, interp=false
        fresh.animateTo(scale);  // 2-tick deferred pop-in via interp=true
        panels.put(name, fresh);
    }

    // ─────────────────────────────────────────────────────────────
    //  RENDER
    // ─────────────────────────────────────────────────────────────

    public void renderAll() {
        WardrobeSession.MenuLevel level = session.getCurrentLevel();
        int selectedIndex = session.getSelectedIndex();

        switch (level) {
            case CATEGORIES: renderCategories(selectedIndex); break;
            case RARITIES:   renderRarities(selectedIndex);   break;
            case COSMETICS:  renderCosmetics(selectedIndex);  break;
            case ACTION:     renderAction(selectedIndex);      break;
        }

        renderKeybinds(level);
    }

    // ── CATEGORIES (slot selection) ───────────────────────────────
    private void renderCategories(int sel) {
        List<String> cats = session.getCategoriesList();

        if (modularMode) {
            List<String>           rows   = new ArrayList<>();
            List<HudButton.State>  states = new ArrayList<>();

            // Header + divider as first two non-selectable rows
            rows.add(cyber("◈ SELECT SLOT ◈", "b"));
            states.add(HudButton.State.NORMAL);
            rows.add(divider("b"));
            states.add(HudButton.State.NORMAL);

            for (int i = 0; i < cats.size(); i++) {
                String icon = slotIcon(cats.get(i));
                if (i == sel) {
                    rows.add("§e§l▶ " + icon + " §f§l" + cats.get(i).toUpperCase());
                    states.add(HudButton.State.SELECTED);
                } else {
                    rows.add("§8   " + icon + " §7" + cats.get(i));
                    states.add(HudButton.State.NORMAL);
                }
            }

            spawnOrRefreshButtonGroup(rows, states);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(cyber("◈ SELECT SLOT ◈", "6")).append("\n");
            sb.append(divider("6")).append("\n");
            for (int i = 0; i < cats.size(); i++) {
                String icon = slotIcon(cats.get(i));
                if (i == sel)
                    sb.append("§e§l▶ ").append(icon).append(" §f§l").append(cats.get(i).toUpperCase()).append("\n");
                else
                    sb.append("§8   ").append(icon).append(" §7").append(cats.get(i)).append("\n");
            }
            if (panels.containsKey("left")) panels.get("left").setText(sb.toString());
        }

        if (panels.containsKey("info")) panels.get("info").setText("");
    }

    // ── RARITIES ─────────────────────────────────────────────────
    private void renderRarities(int sel) {
        String cat = session.getSelectedCategory().toUpperCase();
        List<String> rar = session.getCurrentRaritiesList();

        if (modularMode) {
            List<String>          rows   = new ArrayList<>();
            List<HudButton.State> states = new ArrayList<>();

            rows.add(cyber("◈ " + cat + " ◈", "b")); states.add(HudButton.State.NORMAL);
            rows.add(divider("b"));                   states.add(HudButton.State.NORMAL);
            rows.add("§7Select Rarity");               states.add(HudButton.State.NORMAL);
            rows.add(divider("8"));                   states.add(HudButton.State.NORMAL);

            for (int i = 0; i < rar.size(); i++) {
                String r    = rar.get(i);
                String col  = rarityCode(r);
                String emoji = rarityEmoji(r);
                if (i == sel) {
                    rows.add("§f§l▶ " + col + emoji + " §f§l" + r.toUpperCase());
                    states.add(HudButton.State.SELECTED);
                } else {
                    rows.add("§8   " + col + emoji + " §7" + r);
                    states.add(HudButton.State.NORMAL);
                }
            }

            spawnOrRefreshButtonGroup(rows, states);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(cyber("◈ " + cat + " ◈", "b")).append("\n");
            sb.append(divider("b")).append("\n");
            sb.append("§7Select Rarity\n");
            sb.append(divider("8")).append("\n");
            for (int i = 0; i < rar.size(); i++) {
                String r = rar.get(i);
                String col = rarityCode(r);
                String emoji = rarityEmoji(r);
                if (i == sel)
                    sb.append("§f§l▶ ").append(col).append(emoji).append(" §f§l").append(r.toUpperCase()).append("\n");
                else
                    sb.append("§8   ").append(col).append(emoji).append(" §7").append(r).append("\n");
            }
            if (panels.containsKey("left")) panels.get("left").setText(sb.toString());
        }

        if (panels.containsKey("info")) {
            panels.get("info").setText(
                cyber("◈ " + cat + " ◈", "b") + "\n" +
                "§7Choose a rarity to browse."
            );
        }
    }

    // ── COSMETICS LIST ────────────────────────────────────────────
    private void renderCosmetics(int sel) {
        String cat     = session.getSelectedCategory().toUpperCase();
        String rar     = session.getSelectedRarity() != null ? session.getSelectedRarity() : "";
        String rarCol  = rarityCode(rar);
        String rarEmoji = rarityEmoji(rar);

        List<dc.dccosmetics.model.CosmeticTemplate> cosmetics = session.getCurrentCosmeticsList();

        int maxVisible = 7;
        int offset     = session.getScrollWindowOffset();
        if (sel < offset)               offset = sel;
        if (sel >= offset + maxVisible) offset = sel - maxVisible + 1;
        session.setScrollWindowOffset(offset);

        if (modularMode) {
            List<String>          rows   = new ArrayList<>();
            List<HudButton.State> states = new ArrayList<>();

            rows.add(cyber("◈ " + cat + " ◈", "b")); states.add(HudButton.State.NORMAL);
            rows.add(rarCol + rarEmoji + " §l" + rar.toUpperCase()); states.add(HudButton.State.NORMAL);
            rows.add(divider("8")); states.add(HudButton.State.NORMAL);

            if (cosmetics.isEmpty()) {
                rows.add("§c  No items found."); states.add(HudButton.State.NORMAL);
            } else {
                if (offset > 0) { rows.add("§8  ▲ more..."); states.add(HudButton.State.NORMAL); }
                for (int i = offset; i < Math.min(cosmetics.size(), offset + maxVisible); i++) {
                    dc.dccosmetics.model.CosmeticTemplate t = cosmetics.get(i);
                    String rCol   = rarityCode(t.getRarity());
                    String rEmoji = rarityEmoji(t.getRarity());
                    boolean isPreviewing = t.getId().equals(session.getPreviewingCosmeticId());
                    boolean isSelected   = (i == sel);

                    if (isPreviewing) {
                        rows.add("§a§l▶ " + rEmoji + " §f§l" + t.getItemName() + " §a✔");
                        states.add(HudButton.State.PREVIEWING);
                    } else if (isSelected) {
                        rows.add("§e§l▶ " + rEmoji + " " + rCol + "§l" + t.getItemName());
                        states.add(HudButton.State.SELECTED);
                    } else {
                        rows.add("§8   " + rEmoji + " §7" + t.getItemName());
                        states.add(HudButton.State.NORMAL);
                    }
                }
                if (offset + maxVisible < cosmetics.size()) { rows.add("§8  ▼ more..."); states.add(HudButton.State.NORMAL); }
            }

            spawnOrRefreshButtonGroup(rows, states);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(cyber("◈ " + cat + " ◈", "b")).append("\n");
            sb.append(rarCol).append(rarEmoji).append(" §l").append(rar.toUpperCase()).append("\n");
            sb.append(divider("8")).append("\n");

            if (cosmetics.isEmpty()) {
                sb.append("§c  No items found.\n");
            } else {
                if (offset > 0) sb.append("§8  ▲ more...\n");
                for (int i = offset; i < Math.min(cosmetics.size(), offset + maxVisible); i++) {
                    dc.dccosmetics.model.CosmeticTemplate t = cosmetics.get(i);
                    String rCol   = rarityCode(t.getRarity());
                    String rEmoji = rarityEmoji(t.getRarity());
                    boolean isPreviewing = t.getId().equals(session.getPreviewingCosmeticId());
                    boolean isSelected   = (i == sel);
                    if (isPreviewing)
                        sb.append("§a§l▶ ").append(rEmoji).append(" §f§l").append(t.getItemName()).append(" §a✔\n");
                    else if (isSelected)
                        sb.append("§e§l▶ ").append(rEmoji).append(" ").append(rCol).append("§l").append(t.getItemName()).append("\n");
                    else
                        sb.append("§8   ").append(rEmoji).append(" §7").append(t.getItemName()).append("\n");
                }
                if (offset + maxVisible < cosmetics.size()) sb.append("§8  ▼ more...\n");
            }
            if (panels.containsKey("left")) panels.get("left").setText(sb.toString());
        }

        // Info panel
        if (!cosmetics.isEmpty() && sel < cosmetics.size()) {
            dc.dccosmetics.model.CosmeticTemplate t = cosmetics.get(sel);
            if (panels.containsKey("info")) {
                panels.get("info").setText(
                    cyber(t.getItemName(), "b") + "\n" +
                    rarityCode(t.getRarity()) + rarityEmoji(t.getRarity()) + " " + rar + "\n" +
                    "§7Left-click to preview"
                );
            }
        } else {
            if (panels.containsKey("info")) panels.get("info").setText("");
        }
    }

    // ── ACTION (confirm equip) ────────────────────────────────────
    private void renderAction(int sel) {
        renderCosmetics(session.getSelectedIndex());

        if (session.getPreviewingCosmeticId() != null) {
            dc.dccosmetics.model.CosmeticTemplate t =
                DCCosmetics.getInstance().getTemplateRegistry().getTemplate(session.getPreviewingCosmeticId());
            if (t != null && panels.containsKey("info")) {
                panels.get("info").setText(
                    cyber(t.getItemName(), "a") + "\n" +
                    rarityCode(t.getRarity()) + rarityEmoji(t.getRarity()) + " " +
                    (t.getRarity() != null ? t.getRarity() : "") + "\n\n" +
                    "§e§l▶ §f§lLeft-click to §a§lEQUIP\n" +
                    "§8Right-click to go back"
                );
            }
        }
    }

    // ── KEYBINDS ──────────────────────────────────────────────────
    private void renderKeybinds(WardrobeSession.MenuLevel level) {
        String back = level == WardrobeSession.MenuLevel.CATEGORIES ? "§8[none]" : "§c§l◄ Back";
        if (panels.containsKey("keybind")) {
            panels.get("keybind").setText(
                "§8" + "─".repeat(20) + "\n" +
                "§b⟨ SCROLL ⟩ §7Navigate\n" +
                "§e⟨ LEFT CLICK ⟩ §7Select / Equip\n" +
                "§c⟨ RIGHT CLICK ⟩ " + back + "\n" +
                "§7⟨ SHIFT ⟩ §cExit wardrobe"
            );
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  BUTTON GROUP HELPER
    // ─────────────────────────────────────────────────────────────

    /**
     * Spawns the button group for the first time (if not already spawned),
     * or refreshes it in-place if already spawned.
     */
    private void spawnOrRefreshButtonGroup(List<String> rows, List<HudButton.State> states) {
        if (buttonGroup == null) return;
        if (lastCamLoc == null || lastForward == null) return;

        HudConfig.AngleConfig ac = HudConfig.get().angleFor(session);

        if (!buttonGroupSpawned) {
            buttonGroupSpawned = true;
            buttonGroup.setRows(rows);
            buttonGroup.spawn(lastCamLoc, lastForward, lastRight, lastTrueUp, ac.modular);
        } else {
            buttonGroup.refresh(rows, states);
        }
    }

    private boolean isButtonGroupSpawned() {
        return buttonGroupSpawned;
    }

    private boolean buttonGroupSpawned = false;

    // ─────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────

    private String cyber(String text, String colorCode) {
        return "§" + colorCode + "§l【 " + text + " 】";
    }

    private String divider(String colorCode) {
        return "§" + colorCode + "§m" + "─".repeat(18);
    }

    private String rarityCode(String rarity) {
        if (rarity == null) return "§7";
        switch (rarity.toLowerCase()) {
            case "uncommon":  return "§a";
            case "rare":      return "§b";
            case "epic":      return "§5";
            case "legendary": return "§6";
            default: return "§7";
        }
    }

    private String rarityEmoji(String rarity) {
        if (rarity == null) return "⬤";
        switch (rarity.toLowerCase()) {
            case "uncommon":  return "⬤";
            case "rare":      return "✦";
            case "epic":      return "⭐";
            case "legendary": return "🔥";
            default: return "⬤";
        }
    }

    private String slotIcon(String slot) {
        switch (slot.toLowerCase()) {
            case "head":  return "⛑";
            case "chest": return "🛡";
            case "waist": return "🎽";
            case "boots": return "👢";
            case "sword": return "⚔";
            default:      return "◈";
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  DESTROY
    // ─────────────────────────────────────────────────────────────

    public void destroy() {
        for (HudTextDisplay panel : panels.values()) {
            panel.destroyAnimated();
        }
        panels.clear();
        if (buttonGroup != null) {
            buttonGroup.destroy();
            buttonGroup = null;
        }
    }

    public Map<String, HudTextDisplay> getPanels()      { return panels; }
    public Location getLastCamLoc()                      { return lastCamLoc; }
    public Location getLastMannequinLoc()                { return lastMannequinLoc; }
}
