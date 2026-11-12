package dc.dccosmetics.wardrobe.command;

import dc.dccosmetics.wardrobe.WardrobeManager;
import dc.dcessentials.core.util.DCMessage;
import dc.dccosmetics.wardrobe.WardrobeSession;
import dc.dccosmetics.wardrobe.hud.HudConfig;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /wardrobe studio — Interactive Studio configuration.
 *
 * In V2 (UltimateUI):
 *   Allows configuring camera angles (XYZ, yaw, pitch) per category (overview, head, chest, boots, teleport).
 *   Features:
 *     - Select category camera: [OVERVIEW] [HEAD] [CHEST] [BOOTS] [TELEPORT]
 *     - Set camera to current player location: [📌 SET TO MY POSITION]
 *     - Live preview camera: [👁 PREVIEW VIEWPOINT]
 *     - Fine-tuning sliders: X, Y, Z, Yaw, Pitch
 *     - Save & Reload from wardrobe.yml
 *
 * In V1 (Legacy 3D HUD):
 *   Allows tuning 3D TextDisplay hologram offsets (scale, forward, right, y).
 */
public class WardrobeStudioCommand {

    private static final double STEP_OFFSET = 0.05;
    private static final double STEP_SCALE  = 0.01;

    // Per-player state for V1
    private final Map<UUID, String> selectedAngle = new HashMap<>();
    private final Map<UUID, String> selectedPanel = new HashMap<>();

    // Per-player state for V2
    private final Map<UUID, String> selectedCategory = new HashMap<>();
    private final Map<UUID, Location> previewReturnLocations = new HashMap<>();

    private final WardrobeManager manager;

    public WardrobeStudioCommand(WardrobeManager manager) {
        this.manager = manager;
    }

    // ─────────────────────────────────────────────────────────────
    //  ENTRY
    // ─────────────────────────────────────────────────────────────

    public boolean handle(Player player, String[] args) {
        if (manager.getWardrobeVersion() == 2) {
            return handleV2(player, args);
        } else {
            return handleV1(player, args);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  V2: CAMERA STUDIO
    // ─────────────────────────────────────────────────────────────

    private boolean handleV2(Player player, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase() : "show";

        switch (sub) {
            case "cam":
            case "category":
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /wardrobe studio cam <name>");
                    return true;
                }
                selectedCategory.put(player.getUniqueId(), args[2].toLowerCase());
                sendCameraPanel(player);
                break;

            case "setcam":
                String targetCat = args.length >= 3 ? args[2].toLowerCase() : selectedCategory.get(player.getUniqueId());
                if (targetCat == null) {
                    player.sendMessage("§cUsage: /wardrobe studio setcam <category>");
                    return true;
                }
                manager.getRoom().saveCamera(targetCat, player.getLocation());
                selectedCategory.put(player.getUniqueId(), targetCat);
                DCMessage.sendWardrobe(player, "Camera for " + targetCat.toUpperCase() + " saved to current position!");
                sendCameraPanel(player);
                break;

            case "preview":
                String prevCat = args.length >= 3 ? args[2].toLowerCase() : selectedCategory.get(player.getUniqueId());
                if (prevCat == null) {
                    DCMessage.sendError(player, "Select a category camera to preview first.");
                    return true;
                }
                Location cam = manager.getRoom().getCamera(prevCat);
                if (cam == null) {
                    DCMessage.sendError(player, "Camera for " + prevCat + " not found!");
                    return true;
                }
                previewReturnLocations.put(player.getUniqueId(), player.getLocation());
                player.teleport(cam);
                DCMessage.sendWardrobe(player, "Previewing " + prevCat.toUpperCase() + " camera viewpoint.");
                ComponentBuilder exitCb = new ComponentBuilder("  ");
                TextComponent exitBtn = new TextComponent("§c§l[ CLICK TO EXIT PREVIEW ]");
                exitBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wardrobe studio exitpreview"));
                exitCb.append(exitBtn);
                player.spigot().sendMessage(exitCb.create());
                break;

            case "exitpreview":
                Location ret = previewReturnLocations.remove(player.getUniqueId());
                if (ret != null) {
                    player.teleport(ret);
                    DCMessage.sendWardrobe(player, "Returned from camera preview.");
                } else {
                    DCMessage.sendWarning(player, "No preview return location found.");
                }
                sendCameraPanel(player);
                break;

            case "set":
                // /wardrobe studio set <param> <value>
                if (args.length < 4) {
                    DCMessage.sendHelp(player, "Usage: /wardrobe studio set <param> <value>");
                    return true;
                }
                handleSetV2(player, args[2].toLowerCase(), args[3]);
                break;

            case "save":
                DCMessage.sendWardrobe(player, "Saved cameras to wardrobe.yml.");
                break;

            case "reload":
                manager.getRoom().load();
                DCMessage.sendWardrobe(player, "Reloaded cameras from wardrobe.yml.");
                sendCameraPanel(player);
                break;

            default:
                sendCameraPanel(player);
                break;
        }
        return true;
    }

    private void handleSetV2(Player player, String param, String rawValue) {
        String cat = selectedCategory.get(player.getUniqueId());
        if (cat == null) {
            player.sendMessage("§cSelect a category camera first.");
            return;
        }

        Location loc = manager.getRoom().getCamera(cat);
        if (loc == null) {
            loc = player.getLocation().clone();
        }

        try {
            double v = Double.parseDouble(rawValue);
            switch (param) {
                case "x":     loc.setX(round(v)); break;
                case "y":     loc.setY(round(v)); break;
                case "z":     loc.setZ(round(v)); break;
                case "yaw":   loc.setYaw((float) round(v)); break;
                case "pitch": loc.setPitch((float) round(v)); break;
                default:
                    player.sendMessage("§cUnknown param. Use: x, y, z, yaw, pitch");
                    return;
            }
            manager.getRoom().saveCamera(cat, loc);
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid number: " + rawValue);
            return;
        }

        sendCameraPanel(player);
    }

    private void sendCameraPanel(Player player) {
        String selCat = selectedCategory.get(player.getUniqueId());
        if (selCat == null) {
            selCat = "overview";
            selectedCategory.put(player.getUniqueId(), "overview");
        }

        player.sendMessage("");
        player.spigot().sendMessage(txt("  §b§l◈  WARDROBE CAMERA STUDIO (V2)  ◈"));
        player.sendMessage("");

        // ── Row 1: Camera Category selector ───────────────────────
        player.spigot().sendMessage(txt("  §7Select Camera Angle:"));
        ComponentBuilder catRow = new ComponentBuilder("  ");

        List<String> categories = manager.getEnabledCategories();
        String[] defaultKeys = {"overview", "head", "chest", "boots", "teleport"};

        for (String key : defaultKeys) {
            boolean sel = key.equalsIgnoreCase(selCat);
            String label = getCategoryBadge(key);
            TextComponent btn = new TextComponent((sel ? "§a§l" : "§7") + "[" + label + (sel ? " §a✔" : "") + "§7] ");
            btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wardrobe studio cam " + key));
            btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("§7Configure §e" + key.toUpperCase() + " §7camera").create()));
            catRow.append(btn, ComponentBuilder.FormatRetention.NONE);
        }
        player.spigot().sendMessage(catRow.create());
        player.sendMessage("");

        // ── Row 2: Selected Camera Coordinates & Actions ───────────
        Location loc = manager.getRoom().getCamera(selCat);
        if (loc != null) {
            player.spigot().sendMessage(txt("  §e§lEditing Camera: §f" + selCat.toUpperCase()));
            player.spigot().sendMessage(txt(String.format("  §7Position: §fX: §a%.2f  §fY: §a%.2f  §fZ: §a%.2f", loc.getX(), loc.getY(), loc.getZ())));
            player.spigot().sendMessage(txt(String.format("  §7Rotation: §fYaw: §e%.1f°  §fPitch: §e%.1f°", loc.getYaw(), loc.getPitch())));
            player.sendMessage("");

            ComponentBuilder actionRow = new ComponentBuilder("  ");
            TextComponent setBtn = new TextComponent("§a§l[ 📌 SET TO MY POSITION ]  ");
            setBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wardrobe studio setcam " + selCat));
            setBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("§aSet " + selCat.toUpperCase() + " camera to where you are standing now").create()));

            TextComponent prevBtn = new TextComponent("§b§l[ 👁 PREVIEW VIEWPOINT ]");
            prevBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wardrobe studio preview " + selCat));
            prevBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("§bTeleport to " + selCat.toUpperCase() + " camera to test view").create()));

            actionRow.append(setBtn, ComponentBuilder.FormatRetention.NONE);
            actionRow.append(prevBtn, ComponentBuilder.FormatRetention.NONE);
            player.spigot().sendMessage(actionRow.create());
            player.sendMessage("");

            // ── Sliders for Fine Tuning ─────────────────────────────
            player.spigot().sendMessage(txt("  §8── Fine-Tune Adjustments ──────────"));
            player.spigot().sendMessage(sliderRow("X",     "x",     loc.getX(),     0.1, 0.5));
            player.spigot().sendMessage(sliderRow("Y",     "y",     loc.getY(),     0.1, 0.5));
            player.spigot().sendMessage(sliderRow("Z",     "z",     loc.getZ(),     0.1, 0.5));
            player.spigot().sendMessage(sliderRow("Yaw",   "yaw",   loc.getYaw(),   1.0, 5.0));
            player.spigot().sendMessage(sliderRow("Pitch", "pitch", loc.getPitch(), 1.0, 5.0));
            player.sendMessage("");
        }

        // ── Footer ────────────────────────────────────────────────
        ComponentBuilder footer = new ComponentBuilder("  ");
        footer.append(footerBtn("/wardrobe studio save",   "§a§l[ SAVE ]",   "§aPersist cameras to wardrobe.yml"));
        footer.append(new TextComponent("  "), ComponentBuilder.FormatRetention.NONE);
        footer.append(footerBtn("/wardrobe studio reload", "§e§l[ RELOAD ]", "§eLoad cameras from wardrobe.yml"));
        player.spigot().sendMessage(footer.create());
        player.sendMessage("");
    }

    private BaseComponent[] sliderRow(String label, String param, double current, double fineStep, double coarseStep) {
        double fMinus = round(current - fineStep);
        double fPlus  = round(current + fineStep);
        double cMinus = round(current - coarseStep);
        double cPlus  = round(current + coarseStep);

        ComponentBuilder cb = new ComponentBuilder("  §7" + padRight(label, 7));

        TextComponent fMin = new TextComponent("§c§l[−]");
        fMin.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wardrobe studio set " + param + " " + fMinus));
        fMin.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§c− " + fineStep).create()));

        TextComponent val = new TextComponent(" §f§l" + fmt(current) + " ");

        TextComponent fPls = new TextComponent("§a§l[+]");
        fPls.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wardrobe studio set " + param + " " + fPlus));
        fPls.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a+ " + fineStep).create()));

        TextComponent cMin = new TextComponent("  §4[−−]");
        cMin.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wardrobe studio set " + param + " " + cMinus));
        cMin.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§4− " + coarseStep).create()));

        TextComponent cPls = new TextComponent("§2[++]");
        cPls.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wardrobe studio set " + param + " " + cPlus));
        cPls.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§2+ " + coarseStep).create()));

        cb.append(fMin,  ComponentBuilder.FormatRetention.NONE);
        cb.append(val,   ComponentBuilder.FormatRetention.NONE);
        cb.append(fPls,  ComponentBuilder.FormatRetention.NONE);
        cb.append(cMin,  ComponentBuilder.FormatRetention.NONE);
        cb.append(cPls,  ComponentBuilder.FormatRetention.NONE);
        return cb.create();
    }

    private String getCategoryBadge(String cat) {
        switch (cat.toLowerCase()) {
            case "overview": return "§6🌐 OVERVIEW";
            case "head":     return "§e⛑ HEAD";
            case "chest":    return "§b🛡 CHEST";
            case "boots":    return "§a👢 BOOTS";
            case "teleport": return "§d🌀 TELEPORT";
            default:         return "§f" + cat.toUpperCase();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  V1: LEGACY 3D HUD STUDIO
    // ─────────────────────────────────────────────────────────────

    private boolean handleV1(Player player, String[] args) {
        String sub = args.length >= 2 ? args[1].toLowerCase() : "show";

        switch (sub) {
            case "angle":
                if (args.length < 3) { player.sendMessage("§cUsage: /wardrobe studio angle <name>"); return true; }
                selectedAngle.put(player.getUniqueId(), args[2].toLowerCase());
                selectedPanel.remove(player.getUniqueId());
                sendPanelV1(player);
                break;

            case "panel":
                if (args.length < 3) { player.sendMessage("§cUsage: /wardrobe studio panel <name>"); return true; }
                selectedPanel.put(player.getUniqueId(), args[2].toLowerCase());
                sendPanelV1(player);
                break;

            case "set":
                if (args.length < 4) { player.sendMessage("§cUsage: /wardrobe studio set <param> <value>"); return true; }
                handleSetV1(player, args[2].toLowerCase(), args[3]);
                break;

            case "save":
                HudConfig.get().save();
                DCMessage.sendWardrobe(player, "Saved to wardrobe.yml.");
                break;

            case "reload":
                HudConfig.get().load();
                applyToAll();
                DCMessage.sendWardrobe(player, "Reloaded from wardrobe.yml.");
                sendPanelV1(player);
                break;

            case "apply":
                applyToAll();
                DCMessage.sendWardrobe(player, "Repositioned all active sessions.");
                break;

            default:
                sendPanelV1(player);
        }
        return true;
    }

    private void handleSetV1(Player player, String param, String rawValue) {
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
        sendPanelV1(player);
    }

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

    private void sendPanelV1(Player player) {
        String selAngle = selectedAngle.get(player.getUniqueId());
        String selPanel = selectedPanel.get(player.getUniqueId());

        player.sendMessage("");
        player.spigot().sendMessage(txt("  §b§l◈  WARDROBE HUD STUDIO (V1)  ◈"));
        player.sendMessage("");

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

        if (selAngle != null) {
            player.spigot().sendMessage(txt("  §7Panel:"));
            ComponentBuilder panelRow = new ComponentBuilder("  ");
            String[][] panels = {{"left", "§3LEFT"}, {"modular", "§dMODULAR"}, {"info", "§5INFO"}, {"keybind", "§cKEYBIND"}};
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

        if (selAngle != null && selPanel != null) {
            HudConfig.PanelConfig p = HudConfig.get().panelByKey(selAngle, selPanel);
            if (p != null) {
                player.spigot().sendMessage(txt("  §e§lEditing: §f" + selAngle.toUpperCase() + " §8→ §f" + selPanel.toUpperCase()));
                player.spigot().sendMessage(txt("  §8──────────────────────────────"));
                player.spigot().sendMessage(sliderRow("Scale",    "scale",   p.scale,       STEP_SCALE, 0.1));
                player.spigot().sendMessage(sliderRow("Forward",  "forward", p.forward,     STEP_OFFSET, 0.5));
                player.spigot().sendMessage(sliderRow("Right X",  "right",   p.rightOffset, STEP_OFFSET, 0.5));
                player.spigot().sendMessage(sliderRow("Up/Down Y","y",       p.yOffset,     STEP_OFFSET, 0.5));
                player.sendMessage("");
            }
        }

        ComponentBuilder footer = new ComponentBuilder("  ");
        footer.append(footerBtn("/wardrobe studio save",   "§a§l[ SAVE ]",   "§aPersist to wardrobe.yml"));
        footer.append(new TextComponent("  "), ComponentBuilder.FormatRetention.NONE);
        footer.append(footerBtn("/wardrobe studio reload", "§e§l[ RELOAD ]", "§eLoad from wardrobe.yml"));
        footer.append(new TextComponent("  "), ComponentBuilder.FormatRetention.NONE);
        footer.append(footerBtn("/wardrobe studio apply",  "§b§l[ APPLY ]",  "§bReposition live sessions"));
        player.spigot().sendMessage(footer.create());
        player.sendMessage("");
    }

    private TextComponent footerBtn(String cmd, String label, String hover) {
        TextComponent btn = new TextComponent(label);
        btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
        btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(hover).create()));
        return btn;
    }

    private BaseComponent[] txt(String t) { return new ComponentBuilder(t).create(); }

    private String padRight(String s, int n)  { return String.format("%-" + n + "s", s); }
    private String fmt(double v)               { return String.format("%.2f", v); }
    private double round(double v)             { return Math.round(v * 100.0) / 100.0; }
    private double clamp(double v, double mn, double mx) { return Math.max(mn, Math.min(mx, v)); }
    private float  clampF(float v, float mn, float mx)   { return Math.max(mn, Math.min(mx, v)); }
}
