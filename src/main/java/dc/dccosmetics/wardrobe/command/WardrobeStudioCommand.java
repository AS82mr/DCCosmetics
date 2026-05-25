package dc.dccosmetics.wardrobe.command;

import dc.dccosmetics.wardrobe.WardrobeManager;
import dc.dccosmetics.wardrobe.WardrobeSession;
import dc.dccosmetics.wardrobe.hud.HudConfig;
import net.md_5.bungee.api.chat.*;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /wardrobe studio — per-angle, per-panel HUD layout editor.
 *
 * UI flow:
 *   1. Select an angle:  [FULL BODY] [HEAD] [CHEST] [WAIST] [BOOTS]
 *   2. Select a panel:   [LEFT] [INFO] [KEYBIND]
 *   3. Adjust sliders:   scale / forward / right-x / up-down-y
 *   4. Footer:           [SAVE] [RELOAD] [APPLY]
 *
 * Internal commands (run by clicking chat links):
 *   /wardrobe studio angle <angleName>
 *   /wardrobe studio panel <panelName>
 *   /wardrobe studio set <param> <value>
 *   /wardrobe studio save | reload | apply
 */
public class WardrobeStudioCommand {

    private static final double STEP_OFFSET = 0.05;
    private static final double STEP_SCALE  = 0.01;

    // Per-player state
    private final Map<UUID, String> selectedAngle = new HashMap<>();
    private final Map<UUID, String> selectedPanel = new HashMap<>();

    private final WardrobeManager manager;

    public WardrobeStudioCommand(WardrobeManager manager) {
        this.manager = manager;
    }

    // ─────────────────────────────────────────────────────────────
    //  ENTRY
    // ─────────────────────────────────────────────────────────────

    public boolean handle(Player player, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase() : "show";

        switch (sub) {
            case "angle":
                if (args.length < 3) { player.sendMessage("§cUsage: /wardrobe studio angle <name>"); return true; }
                selectedAngle.put(player.getUniqueId(), args[2].toLowerCase());
                selectedPanel.remove(player.getUniqueId()); // reset panel when angle changes
                sendPanel(player);
                break;

            case "panel":
                if (args.length < 3) { player.sendMessage("§cUsage: /wardrobe studio panel <name>"); return true; }
                selectedPanel.put(player.getUniqueId(), args[2].toLowerCase());
                sendPanel(player);
                break;

            case "set":
                // /wardrobe studio set <param> <value>
                if (args.length < 4) { player.sendMessage("§cUsage: /wardrobe studio set <param> <value>"); return true; }
                handleSet(player, args[2].toLowerCase(), args[3]);
                break;

            case "save":
                HudConfig.get().save();
                player.sendMessage("§a[Studio] Saved to wardrobe.yml ✔");
                break;

            case "reload":
                HudConfig.get().load();
                applyToAll();
                player.sendMessage("§e[Studio] Reloaded from wardrobe.yml.");
                sendPanel(player);
                break;

            case "apply":
                applyToAll();
                player.sendMessage("§b[Studio] Repositioned all active sessions.");
                break;

            default:
                sendPanel(player);
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────
    //  SET
    // ─────────────────────────────────────────────────────────────

    private void handleSet(Player player, String param, String rawValue) {
        String angle = selectedAngle.get(player.getUniqueId());
        String panel = selectedPanel.get(player.getUniqueId());
        if (angle == null || panel == null) {
            player.sendMessage("§cSelect an angle AND a panel first.");
            return;
        }

        HudConfig.PanelConfig p = HudConfig.get().panelByKey(angle, panel);
        if (p == null) { player.sendMessage("§cInvalid angle/panel combination."); return; }

        try {
            double v = Double.parseDouble(rawValue);
            switch (param) {
                case "scale":   p.scale       = clampF((float) v, 0.02f, 3.0f); break;
                case "forward": p.forward     = clamp(v, 0.2, 8.0);  break;
                case "right":   p.rightOffset = clamp(v, -8.0, 8.0); break;
                case "y":       p.yOffset     = clamp(v, -5.0, 5.0); break;
                default: player.sendMessage("§cUnknown param. Use: scale, forward, right, y"); return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid number: " + rawValue);
            return;
        }

        applyToAll();
        sendPanel(player);
    }

    // ─────────────────────────────────────────────────────────────
    //  APPLY
    // ─────────────────────────────────────────────────────────────

    private void applyToAll() {
        for (WardrobeSession session : manager.getActiveSessions()) {
            if (session.getHud() != null
                    && session.getCurrentCameraLocation() != null
                    && session.getHud().getLastMannequinLoc() != null) {
                session.getHud().repositionForCamera(
                        session.getCurrentCameraLocation(),
                        session.getHud().getLastMannequinLoc()
                );
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  CHAT PANEL
    // ─────────────────────────────────────────────────────────────

    private void sendPanel(Player player) {
        String selAngle = selectedAngle.get(player.getUniqueId());
        String selPanel = selectedPanel.get(player.getUniqueId());

        player.sendMessage("");
        player.spigot().sendMessage(txt("  §b§l◈  WARDROBE HUD STUDIO  ◈"));
        player.sendMessage("");

        // ── Row 1: Angle selector ─────────────────────────────────
        player.spigot().sendMessage(txt("  §7Camera Angle:"));
        ComponentBuilder angleRow = new ComponentBuilder("  ");
        String[] angleKeys   = {"full-body", "head", "chest", "waist", "boots"};
        String[] angleLabels = {"§6FULL BODY", "§e⛑ HEAD", "§b🛡 CHEST", "§d🎽 WAIST", "§a👢 BOOTS"};
        for (int i = 0; i < angleKeys.length; i++) {
            boolean sel = angleKeys[i].equals(selAngle);
            TextComponent btn = new TextComponent((sel ? "§a§l" : "§7") + "[" + angleLabels[i] + (sel ? " §a§l✔" : "") + "§7] ");
            btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wardrobe studio angle " + angleKeys[i]));
            btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("§7Edit panels for §e" + angleKeys[i] + " §7view").create()));
            angleRow.append(btn, ComponentBuilder.FormatRetention.NONE);
        }
        player.spigot().sendMessage(angleRow.create());

        player.sendMessage("");

        // ── Row 2: Panel selector (only shown once angle is selected) ──
        if (selAngle != null) {
            player.spigot().sendMessage(txt("  §7Panel:"));
            ComponentBuilder panelRow = new ComponentBuilder("  ");
            String[][] panels = {{"left", "§3LEFT"}, {"info", "§5INFO"}, {"keybind", "§cKEYBIND"}};
            for (String[] p : panels) {
                boolean sel = p[0].equals(selPanel);
                TextComponent btn = new TextComponent((sel ? "§a§l" : "§7") + "[" + p[1] + (sel ? " §a§l✔" : "") + "§7]  ");
                btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wardrobe studio panel " + p[0]));
                btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder("§7Edit §e" + p[0] + " §7panel for §e" + selAngle).create()));
                panelRow.append(btn, ComponentBuilder.FormatRetention.NONE);
            }
            player.spigot().sendMessage(panelRow.create());
            player.sendMessage("");
        }

        // ── Sliders (only shown once angle + panel are both selected) ──
        if (selAngle != null && selPanel != null) {
            HudConfig.PanelConfig p = HudConfig.get().panelByKey(selAngle, selPanel);
            if (p != null) {
                player.spigot().sendMessage(txt("  §e§lEditing: §f" + selAngle.toUpperCase() + " §8→ §f" + selPanel.toUpperCase()));
                player.spigot().sendMessage(txt("  §8──────────────────────────────"));
                player.spigot().sendMessage(row("Scale",    "scale",   p.scale,       STEP_SCALE));
                player.spigot().sendMessage(row("Forward",  "forward", p.forward,     STEP_OFFSET));
                player.spigot().sendMessage(row("Right X",  "right",   p.rightOffset, STEP_OFFSET));
                player.spigot().sendMessage(row("Up/Down Y","y",       p.yOffset,     STEP_OFFSET));
                player.sendMessage("");
            }
        }

        // ── Footer ────────────────────────────────────────────────
        player.spigot().sendMessage(footer());
        player.sendMessage("");
    }

    // ─────────────────────────────────────────────────────────────
    //  COMPONENT HELPERS
    // ─────────────────────────────────────────────────────────────

    private BaseComponent[] row(String label, String param, double current, double fineStep) {
        double coarseStep = (fineStep < 0.05) ? 0.1 : 0.5; // scale→0.1, offsets→0.5
        double fMinus = round(current - fineStep);
        double fPlus  = round(current + fineStep);
        double cMinus = round(current - coarseStep);
        double cPlus  = round(current + coarseStep);

        ComponentBuilder cb = new ComponentBuilder("  §7" + padRight(label, 10));

        // Fine step buttons
        TextComponent fMin = new TextComponent("§c§l[−]");
        fMin.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/wardrobe studio set " + param + " " + fMinus));
        fMin.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§c− " + fineStep).create()));

        TextComponent val = new TextComponent(" §f§l" + fmt(current) + " ");

        TextComponent fPls = new TextComponent("§a§l[+]");
        fPls.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/wardrobe studio set " + param + " " + fPlus));
        fPls.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§a+ " + fineStep).create()));

        // Coarse step buttons
        TextComponent cMin = new TextComponent("  §4[−−]");
        cMin.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/wardrobe studio set " + param + " " + cMinus));
        cMin.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§4− " + coarseStep).create()));

        TextComponent cPls = new TextComponent("§2[++]");
        cPls.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/wardrobe studio set " + param + " " + cPlus));
        cPls.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("§2+ " + coarseStep).create()));

        cb.append(fMin,  ComponentBuilder.FormatRetention.NONE);
        cb.append(val,   ComponentBuilder.FormatRetention.NONE);
        cb.append(fPls,  ComponentBuilder.FormatRetention.NONE);
        cb.append(cMin,  ComponentBuilder.FormatRetention.NONE);
        cb.append(cPls,  ComponentBuilder.FormatRetention.NONE);
        return cb.create();
    }

    private BaseComponent[] footer() {
        ComponentBuilder cb = new ComponentBuilder("  ");
        cb.append(footerBtn("/wardrobe studio save",   "§a§l[ SAVE ]",   "§aPersist to wardrobe.yml"));
        cb.append(new TextComponent("  "), ComponentBuilder.FormatRetention.NONE);
        cb.append(footerBtn("/wardrobe studio reload", "§e§l[ RELOAD ]", "§eLoad from wardrobe.yml"));
        cb.append(new TextComponent("  "), ComponentBuilder.FormatRetention.NONE);
        cb.append(footerBtn("/wardrobe studio apply",  "§b§l[ APPLY ]",  "§bReposition live sessions"));
        return cb.create();
    }

    private TextComponent footerBtn(String cmd, String label, String hover) {
        TextComponent btn = new TextComponent(label);
        btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
        btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(hover).create()));
        return btn;
    }

    private BaseComponent[] txt(String t) { return new ComponentBuilder(t).create(); }

    // ─────────────────────────────────────────────────────────────
    //  UTILS
    // ─────────────────────────────────────────────────────────────

    private String padRight(String s, int n)  { return String.format("%-" + n + "s", s); }
    private String fmt(double v)               { return String.format("%.3f", v); }
    private double round(double v)             { return Math.round(v * 1000.0) / 1000.0; }
    private double clamp(double v, double mn, double mx) { return Math.max(mn, Math.min(mx, v)); }
    private float  clampF(float v, float mn, float mx)   { return Math.max(mn, Math.min(mx, v)); }
}
