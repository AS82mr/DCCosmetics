package dc.dccosmetics.wardrobe.camera;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.wardrobe.WardrobeManager;
import dc.dccosmetics.wardrobe.WardrobeSession;
import dc.dccosmetics.wardrobe.camera.CctvCameraRig;
import dc.dccosmetics.wardrobe.hud.WardrobeHUD;
import dc.dccosmetics.wardrobe.npc.WardrobeMannequin;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Logger;

public class WardrobeCinematic {

    private final WardrobeManager manager;
    private final WardrobeSession session;
    private final Player player;
    private static final Logger log = DCCosmetics.getInstance().getLogger();

    // Toggle this to false once everything is stable to reduce log spam
    private static final boolean DEBUG = true;

    private static void debug(Player player, String step) {
        if (DEBUG) {
            log.info("[Wardrobe][DEBUG] " + player.getName() + " | " + step);
        }
    }

    public WardrobeCinematic(WardrobeManager manager, WardrobeSession session) {
        this.manager = manager;
        this.session = session;
        this.player = session.getPlayer();
    }

    public void playIntro() {
        session.setInCinematic(true);
        debug(player, "playIntro START");

        // Read diagnostic step limiter from config
        int maxStep = 6; // default: all steps
        try {
            java.io.File f = new java.io.File(DCCosmetics.getInstance().getDataFolder(), "wardrobe.yml");
            if (f.exists()) {
                maxStep = org.bukkit.configuration.file.YamlConfiguration
                        .loadConfiguration(f).getInt("hud.diagnostic-max-step", 6);
            }
        } catch (Exception ignored) {}
        final int diagnosticMaxStep = maxStep;
        debug(player, "diagnostic-max-step=" + diagnosticMaxStep);

        // ── 1. Teleport player to the safe room BEFORE any rendering ─────────
        Location safeRoom = manager.getRoom().getSafeRoom();
        debug(player, "safeRoom=" + locStr(safeRoom));
        if (safeRoom != null) {
            player.teleport(safeRoom);
            debug(player, "teleported to safe room OK");
        }

        // ── 2. Initial fade / Title ────────────────────────────────────────
        player.sendTitle(ChatColor.DARK_GRAY + "Entering...", "", 10, 40, 10);
        player.playSound(player, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.4f);
        player.playSound(player, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, 0.5f);
        player.setGameMode(GameMode.ADVENTURE);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.INVISIBILITY, 99999, 0, false, false));
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        debug(player, "gamemode + invisibility applied");

        // ── 3. Deferred setup — wait for fade-to-black ────────────────────
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !manager.isInWardrobe(player)) {
                    debug(player, "ABORTED — player offline or no longer in wardrobe");
                    return;
                }

                try {
                    // ── STEP 1: Title + Chime (always runs) ──────────────────
                    debug(player, "STEP 1 — title + chime");
                    player.sendTitle(
                        ChatColor.GOLD + "" + ChatColor.BOLD + "THE WARDROBE",
                        ChatColor.GRAY + "Select your style",
                        10, 60, 20);
                    player.playSound(player, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.8f, 1.4f);

                    if (diagnosticMaxStep < 2) {
                        debug(player, "DIAGNOSTIC STOP — max step is 1, skipping entity spawning");
                        session.setInCinematic(false);
                        debug(player, "playIntro COMPLETE (diagnostic step 1)");
                        return;
                    }

                    // ── STEP 2: Spawn HUD ────────────────────────────────────
                    debug(player, "STEP 2 — spawning HUD");
                    WardrobeHUD hud = new WardrobeHUD(session);
                    hud.spawnAll();
                    session.setHud(hud);
                    debug(player, "STEP 2 — HUD spawned OK");

                    if (diagnosticMaxStep < 3) {
                        debug(player, "DIAGNOSTIC STOP — max step is 2");
                        session.setInCinematic(false);
                        debug(player, "playIntro COMPLETE (diagnostic step 2)");
                        return;
                    }

                    // ── STEP 3: Spawn Mannequin ──────────────────────────────
                    debug(player, "STEP 3 — spawning mannequin");
                    Location npcBase = manager.getRoom().getNpcStand();
                    Location npcLoc  = offsetForSession(npcBase);
                    debug(player, "npcBase=" + locStr(npcBase) + " npcLoc=" + locStr(npcLoc));
                    WardrobeMannequin mannequin = new WardrobeMannequin(session, npcLoc);
                    mannequin.spawn();
                    session.setMannequin(mannequin);
                    debug(player, "STEP 3 — mannequin spawned OK");

                    if (diagnosticMaxStep < 4) {
                        debug(player, "DIAGNOSTIC STOP — max step is 3");
                        session.setInCinematic(false);
                        debug(player, "playIntro COMPLETE (diagnostic step 3)");
                        return;
                    }

                    // ── STEP 4: Lock Camera ──────────────────────────────────
                    debug(player, "STEP 4 — locking camera");
                    Location spawnLoc = manager.getRoom().getSpawn();
                    debug(player, "spawnLoc=" + locStr(spawnLoc));
                    CameraController cameraController = new CameraController();
                    cameraController.spawnAndLockCamera(player, spawnLoc);
                    session.setCameraController(cameraController);
                    session.setCurrentCameraLocation(spawnLoc);
                    debug(player, "STEP 4 — camera locked OK");

                    if (diagnosticMaxStep < 5) {
                        debug(player, "DIAGNOSTIC STOP — max step is 4");
                        session.setInCinematic(false);
                        debug(player, "playIntro COMPLETE (diagnostic step 4)");
                        return;
                    }

                    // ── STEP 5: Spawn CCTV Rig ───────────────────────────────
                    debug(player, "STEP 5 — spawning CCTV rig");
                    try {
                        CctvCameraRig rig = new CctvCameraRig(player);
                        rig.spawnAt(spawnLoc, npcLoc);
                        session.setCctvRig(rig);
                        debug(player, "STEP 5 — CCTV rig spawned OK");
                    } catch (Exception e) {
                        log.warning("[Wardrobe][DEBUG] STEP 5 FAILED (CCTV rig): " + e);
                        // Non-fatal — continue without rig
                    }

                    if (diagnosticMaxStep < 6) {
                        debug(player, "DIAGNOSTIC STOP — max step is 5");
                        session.setInCinematic(false);
                        debug(player, "playIntro COMPLETE (diagnostic step 5)");
                        return;
                    }

                    // ── STEP 6: Reposition HUD for Camera ────────────────────
                    debug(player, "STEP 6 — repositioning HUD for camera");
                    if (npcLoc != null) {
                        hud.repositionForCamera(spawnLoc, npcLoc);
                    }
                    debug(player, "STEP 6 — HUD repositioned OK");

                    session.setInCinematic(false);
                    debug(player, "playIntro COMPLETE — cinematic ended (all 6 steps)");

                } catch (Exception e) {
                    log.severe("[Wardrobe][DEBUG] FATAL during wardrobe setup for " + player.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    // Safely eject the player if something blew up
                    manager.exitWardrobe(player);
                }
            }
        }.runTaskLater(DCCosmetics.getInstance(), 40L);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String locStr(Location loc) {
        if (loc == null) return "null";
        return String.format("%s @ %.1f,%.1f,%.1f",
            loc.getWorld() != null ? loc.getWorld().getName() : "?",
            loc.getX(), loc.getY(), loc.getZ());
    }

    /**
     * Offsets the NPC spawn location slightly for each concurrent wardrobe session,
     * so multiple users' mannequins don't overlap at the exact same block.
     */
    private Location offsetForSession(Location base) {
        if (base == null) return null;

        int slot = 0;
        for (WardrobeSession s : manager.getActiveSessions()) {
            if (s.getPlayer().equals(player)) continue;
            slot++;
        }

        if (slot == 0) return base.clone();

        double side = (slot % 2 == 0 ? 1 : -1) * ((slot + 1) / 2) * 3.0;
        org.bukkit.util.Vector forward = base.getDirection().normalize();
        org.bukkit.util.Vector right = new org.bukkit.util.Vector(-forward.getZ(), 0, forward.getX()).normalize();
        Location offset = base.clone().add(right.multiply(side));
        offset.setYaw(base.getYaw());
        offset.setPitch(base.getPitch());
        return offset;
    }
}
