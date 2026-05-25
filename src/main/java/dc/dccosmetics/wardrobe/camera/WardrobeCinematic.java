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

public class WardrobeCinematic {

    private final WardrobeManager manager;
    private final WardrobeSession session;
    private final Player player;

    public WardrobeCinematic(WardrobeManager manager, WardrobeSession session) {
        this.manager = manager;
        this.session = session;
        this.player = session.getPlayer();
    }

    public void playIntro() {
        session.setInCinematic(true);

        // ── 1. Teleport player to the safe room BEFORE any rendering ─────────
        // This fixes: (a) wrong-world bug when /wardrobe is run from another world,
        //             (b) player body floating in the open while invisible.
        Location safeRoom = manager.getRoom().getSafeRoom();
        if (safeRoom != null) {
            player.teleport(safeRoom);
        }

        // ── 2. Initial fade / Title ────────────────────────────────────────
        player.sendTitle(ChatColor.DARK_GRAY + "Entering...", "", 10, 40, 10);
        // Cyberpunk system boot: heavy low-frequency power-up
        player.playSound(player, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.4f);
        player.playSound(player, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, 0.5f);

        // Set to ADVENTURE — keeps ARM_ANIMATION (left-click) functional
        player.setGameMode(GameMode.ADVENTURE);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.INVISIBILITY, 99999, 0, false, false));
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

        // ── 3. Deferred setup — wait for fade-to-black ────────────────────
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !manager.isInWardrobe(player)) return;

                player.sendTitle(
                    ChatColor.GOLD + "" + ChatColor.BOLD + "THE WARDROBE",
                    ChatColor.GRAY + "Select your style",
                    10, 60, 20);
                // Cyberpunk: HUD boot-up chime (high-pitch respawn anchor)
                player.playSound(player, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.8f, 1.4f);

                // Spawn HUD
                WardrobeHUD hud = new WardrobeHUD(session);
                hud.spawnAll();
                session.setHud(hud);

                // Spawn Mannequin — offset per session slot so multiple users don't collide
                Location npcBase = manager.getRoom().getNpcStand();
                Location npcLoc  = offsetForSession(npcBase);
                WardrobeMannequin mannequin = new WardrobeMannequin(session, npcLoc);
                mannequin.spawn();
                session.setMannequin(mannequin);

                // Lock camera
                Location spawnLoc = manager.getRoom().getSpawn();
                CameraController cameraController = new CameraController();
                cameraController.spawnAndLockCamera(player, spawnLoc);
                session.setCameraController(cameraController);
                session.setCurrentCameraLocation(spawnLoc);

                // Spawn CCTV rig at the camera marker position (visible in F5 mode)
                CctvCameraRig rig = new CctvCameraRig(player);
                rig.spawnAt(spawnLoc);
                session.setCctvRig(rig);

                if (npcLoc != null) {
                    hud.repositionForCamera(spawnLoc, npcLoc);
                }

                session.setInCinematic(false);
            }
        }.runTaskLater(DCCosmetics.getInstance(), 40L);
    }

    /**
     * Offsets the NPC spawn location slightly for each concurrent wardrobe session,
     * so multiple users' mannequins don't overlap at the exact same block.
     * Offset is applied sideways (perpendicular to the NPC's facing direction).
     */
    private Location offsetForSession(Location base) {
        if (base == null) return null;

        // Count how many sessions are already active to get a slot index
        int slot = 0;
        for (WardrobeSession s : manager.getActiveSessions()) {
            if (s.getPlayer().equals(player)) continue; // skip ourselves (just added)
            slot++;
        }

        if (slot == 0) return base.clone(); // first user — no offset needed

        // Spread users sideways: 3 blocks apart, alternating left/right
        // slot 1 → +1.5, slot 2 → -1.5, slot 3 → +3.0, ...
        double side = (slot % 2 == 0 ? 1 : -1) * ((slot + 1) / 2) * 3.0;

        // Perpendicular vector (right of the NPC's facing direction)
        org.bukkit.util.Vector forward = base.getDirection().normalize();
        org.bukkit.util.Vector right = new org.bukkit.util.Vector(-forward.getZ(), 0, forward.getX()).normalize();

        Location offset = base.clone().add(right.multiply(side));
        offset.setYaw(base.getYaw());
        offset.setPitch(base.getPitch());
        return offset;
    }
}
