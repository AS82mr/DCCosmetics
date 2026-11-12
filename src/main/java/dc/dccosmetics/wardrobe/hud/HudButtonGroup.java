package dc.dccosmetics.wardrobe.hud;

import dc.dccosmetics.DCCosmetics;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * HudButtonGroup — a vertically stacked set of {@link HudButton} entities.
 *
 * All buttons share the same plane (same forward distance + rightOffset from
 * the camera) and are laid out top-to-bottom with configurable line spacing.
 * The group inherits its position from the PanelConfig it replaces so existing
 * studio tuning is preserved — just decomposed into individual entities.
 *
 * Usage:
 *   1. Create with player, initial camera location, and PanelConfig.
 *   2. Call {@link #setRows(List)} to provide the text rows.
 *   3. Call {@link #spawn()} to spawn all button entities.
 *   4. Call {@link #setSelected(int)} when the scroll index changes.
 *   5. Call {@link #reposition(Location, Vector, Vector, Vector, HudConfig.PanelConfig)}
 *      whenever the camera moves.
 *   6. Call {@link #destroy()} when exiting.
 */
public class HudButtonGroup {

    /** Gap between button centres in world units (Y). */
    private static final double LINE_SPACING = 0.085;

    private final Player  viewer;
    private final List<HudButton> buttons = new ArrayList<>();

    // Last known layout — needed to resend on reposition without full respawn
    private Location  lastAnchorLoc;
    private Vector    lastForward;
    private Vector    lastRight;
    private Vector    lastUp;
    private HudConfig.PanelConfig lastCfg;

    private List<String>          currentRows     = new ArrayList<>();
    private List<HudButton.State> currentStates   = new ArrayList<>();
    private int                   selectedIndex   = 0;

    public HudButtonGroup(Player viewer) {
        this.viewer = viewer;
    }

    // ── Row Data ─────────────────────────────────────────────────────────────

    /**
     * Sets (or replaces) the text rows. Does NOT automatically re-render.
     * Call after setRows() to control when the visual update happens.
     */
    public void setRows(List<String> rows) {
        this.currentRows = new ArrayList<>(rows);
        // Preserve states list size
        while (currentStates.size() < rows.size()) currentStates.add(HudButton.State.NORMAL);
        while (currentStates.size() > rows.size()) currentStates.remove(currentStates.size() - 1);
    }

    public void setRowState(int index, HudButton.State state) {
        if (index >= 0 && index < currentStates.size()) {
            currentStates.set(index, state);
        }
    }

    public void setSelected(int index) {
        this.selectedIndex = index;
        for (int i = 0; i < buttons.size(); i++) {
            HudButton btn = buttons.get(i);
            HudButton.State s = currentStates.size() > i ? currentStates.get(i) : HudButton.State.NORMAL;
            // Selected overrides other states unless PREVIEWING
            if (s != HudButton.State.PREVIEWING) {
                s = (i == index) ? HudButton.State.SELECTED : HudButton.State.NORMAL;
            }
            btn.setState(s);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Spawns all buttons at positions derived from the camera layout.
     * Camera vectors (forward, right, up) and PanelConfig are used to compute
     * individual button locations in the same plane as the legacy unified panel.
     */
    public void spawn(Location camLoc, Vector forward, Vector right, Vector up,
                      HudConfig.PanelConfig cfg) {
        this.lastAnchorLoc = camLoc;
        this.lastForward   = forward;
        this.lastRight     = right;
        this.lastUp        = up;
        this.lastCfg       = cfg;

        destroyImmediate(); // clear any previous

        for (int i = 0; i < currentRows.size(); i++) {
            Location btnLoc = buttonLocation(camLoc, forward, right, up, cfg, i, currentRows.size());
            HudButton btn = new HudButton(viewer, btnLoc, cfg.scale);
            btn.setText(currentRows.get(i));
            HudButton.State s = (currentStates.size() > i) ? currentStates.get(i) : HudButton.State.NORMAL;
            if (s != HudButton.State.PREVIEWING && i == selectedIndex) s = HudButton.State.SELECTED;
            buttons.add(btn);
            btn.spawn(); // spawns at scale 0, pops in after 1 tick
            // Delay state update slightly so pop-in and state colour arrive together
            final int fi = i;
            final HudButton.State fs = s;
            DCCosmetics.getInstance().getServer().getScheduler().runTaskLater(
                    DCCosmetics.getInstance(),
                    () -> { if (fi < buttons.size()) buttons.get(fi).setState(fs); },
                    2L
            );
        }
    }

    /**
     * Rebuilds the button list with new rows and states in-place.
     * More efficient than destroy+spawn for same-count menu updates.
     */
    public void refresh(List<String> rows, List<HudButton.State> states) {
        this.currentRows   = new ArrayList<>(rows);
        this.currentStates = new ArrayList<>(states);

        // If count changed, full respawn
        if (rows.size() != buttons.size()) {
            if (lastAnchorLoc != null) {
                spawn(lastAnchorLoc, lastForward, lastRight, lastUp, lastCfg);
            }
            return;
        }

        // Same count — just update text + state on each button
        for (int i = 0; i < buttons.size(); i++) {
            HudButton.State s = states.size() > i ? states.get(i) : HudButton.State.NORMAL;
            buttons.get(i).update(rows.get(i), s);
        }
    }

    /**
     * Moves all buttons to their new positions without destroying/respawning.
     * Called when the camera moves (zoomCamera / back-nav).
     */
    public void reposition(Location camLoc, Vector forward, Vector right, Vector up,
                           HudConfig.PanelConfig cfg) {
        // Display entities are sometimes stubborn with teleport packets depending on interpolation.
        // It's 100% reliable to destroy and respawn them (the same way we do for info/keybind panels).
        spawn(camLoc, forward, right, up, cfg);
    }

    /** Animates all buttons out (scale→0) then destroys. */
    public void destroy() {
        for (HudButton btn : buttons) btn.destroyAnimated();
        buttons.clear();
    }

    /** Instant destroy without animation (used internally before respawn). */
    private void destroyImmediate() {
        for (HudButton btn : buttons) btn.destroyImmediate();
        buttons.clear();
    }

    // ── Layout Math ──────────────────────────────────────────────────────────

    /**
     * Computes the world Location for button at index {@code i} within a group
     * of {@code total} buttons, centred around the PanelConfig's anchor point.
     *
     * Vertical arrangement: centred on yOffset, spread upward/downward.
     */
    private Location buttonLocation(Location camLoc, Vector forward, Vector right, Vector up,
                                    HudConfig.PanelConfig cfg, int index, int total) {
        // Anchor point (same as old unified panel)
        Location anchor = camLoc.clone()
                .add(forward.clone().multiply(cfg.forward))
                .add(right.clone().multiply(cfg.rightOffset))
                .add(up.clone().multiply(cfg.yOffset));

        // Compute line spacing relative to scale (0.28 * scale matches standard text line height)
        double spacing = 0.28 * cfg.scale;
        
        // Centre the stack: offset from centre by (index - midIndex) * spacing
        double midIndex = (total - 1) / 2.0;
        double yDelta = (midIndex - index) * spacing;
        Location loc = anchor.clone().add(up.clone().multiply(yDelta));

        // Force all buttons to share the exact same yaw/pitch as the camera (inverted to face it)
        // Combined with Billboard.FIXED, this makes the entire group act as a single perfectly flat plane!
        loc.setYaw(camLoc.getYaw() - 180f);
        loc.setPitch(-camLoc.getPitch());
        return loc;
    }
}
