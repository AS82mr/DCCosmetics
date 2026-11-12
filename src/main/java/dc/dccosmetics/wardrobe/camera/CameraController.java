package dc.dccosmetics.wardrobe.camera;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.wardrobe.WardrobeSession;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Controls the Wardrobe Studio perspective camera using shortest-arc yaw interpolation,
 * full-float TextDisplay position sync, and orbital Bezier curves around the mannequin.
 */
public class CameraController {

    private Entity cameraEntity;
    private BukkitTask activeTransitionTask;
    private BukkitTask lockRetryTask;

    public Entity getCameraEntity() {
        return cameraEntity;
    }

    public void spawnAndLockCamera(Player player, Location eyeLoc, WardrobeSession session) {
        if (eyeLoc == null || eyeLoc.getWorld() == null) return;
        cancelActiveTransition();
        cancelLockRetry();
        eyeLoc.getChunk().load(true);

        float cleanYaw = Location.normalizeYaw(eyeLoc.getYaw());
        float cleanPitch = Math.max(-90.0f, Math.min(90.0f, eyeLoc.getPitch()));
        Location cleanLoc = new Location(eyeLoc.getWorld(), eyeLoc.getX(), eyeLoc.getY(), eyeLoc.getZ(), cleanYaw, cleanPitch);

        if (cameraEntity != null && cameraEntity.isValid() && cameraEntity.getWorld().equals(cleanLoc.getWorld())) {
            CinematicCameraEngine.applyCameraPose(player, cameraEntity, cleanLoc, true);
        } else {
            if (cameraEntity != null && cameraEntity.isValid()) {
                if (session != null && session.getManager() != null) {
                    session.getManager().unregisterPrivateEntity(cameraEntity.getEntityId());
                }
                cameraEntity.remove();
            }
            TextDisplay td = cleanLoc.getWorld().spawn(cleanLoc, TextDisplay.class, display -> {
                display.setText("");
                display.setTeleportDuration(2);
                display.setViewRange(8.0f);
                display.setShadowRadius(0f);
                display.setShadowStrength(0f);
                display.setVisibleByDefault(false);
                display.setRotation(cleanYaw, cleanPitch);
            });
            player.showEntity(DCCosmetics.getInstance(), td);
            cameraEntity = td;
            CinematicCameraEngine.applyCameraPose(player, cameraEntity, cleanLoc, true);
        }

        if (session != null) {
            session.setCurrentCameraLocation(cleanLoc);
            if (session.getManager() != null && cameraEntity != null) {
                session.getManager().registerPrivateEntity(cameraEntity.getEntityId(), player.getUniqueId());
            }
        }

        lockCameraToEntityId(player, cameraEntity.getEntityId());
        player.setAllowFlight(true);
        player.setFlying(true);

        // Cross-world teleports flush dimension Respawn + AddEntity a few ticks after tick 0;
        // re-send SetCamera cleanly so Overview never stays unlocked on client.
        final Location retryPose = cleanLoc.clone();
        lockRetryTask = new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (cameraEntity == null || !cameraEntity.isValid() || !player.isOnline() || tick > 32) {
                    lockRetryTask = null;
                    this.cancel();
                    return;
                }
                if (tick == 1 || tick == 2 || tick == 4 || tick == 8 || tick == 15 || tick == 25 || tick == 32) {
                    player.showEntity(DCCosmetics.getInstance(), cameraEntity);
                    CinematicCameraEngine.applyCameraPose(player, cameraEntity, retryPose);
                    lockCameraToEntityId(player, cameraEntity.getEntityId());
                    player.setAllowFlight(true);
                    player.setFlying(true);
                }
                tick++;
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 1L, 1L);
    }

    public void moveCamera(Player player, Location eyeLoc, WardrobeSession session) {
        if (eyeLoc == null) return;
        float cleanYaw = Location.normalizeYaw(eyeLoc.getYaw());
        float cleanPitch = Math.max(-90.0f, Math.min(90.0f, eyeLoc.getPitch()));
        Location cleanEnd = new Location(eyeLoc.getWorld(), eyeLoc.getX(), eyeLoc.getY(), eyeLoc.getZ(), cleanYaw, cleanPitch);

        if (session != null && session.getCameraController() != null && session.getCurrentCameraLocation() != null) {
            Location start = cameraEntity != null && cameraEntity.isValid()
                    ? cameraEntity.getLocation()
                    : session.getCurrentCameraLocation();
            Location pivot = session.getManager() != null && session.getManager().getRoom() != null
                    ? session.getManager().getRoom().getNpcStand()
                    : null;

            smoothTransition(player, start, cleanEnd, pivot, 12);
            session.setCurrentCameraLocation(cleanEnd);
        } else {
            spawnAndLockCamera(player, cleanEnd, session);
        }
    }

    /**
     * Smoothly interpolates the camera along an orbital Bezier curve with EASE_IN_OUT_CUBIC
     * and shortest-arc yaw math so it never spins 360 degrees.
     */
    private void smoothTransition(Player player, Location start, Location end, Location pivot, int ticks) {
        cancelActiveTransition();
        cancelLockRetry();
        if (cameraEntity == null || !cameraEntity.isValid()) {
            spawnAndLockCamera(player, end, null);
            return;
        }

        final Location cleanStart = new Location(
                start.getWorld(), start.getX(), start.getY(), start.getZ(),
                Location.normalizeYaw(start.getYaw()), start.getPitch()
        );
        final Location controlPoint = CinematicCameraEngine.computeOrbitalControlPoint(cleanStart, end, pivot, 0.06);

        activeTransitionTask = new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (cameraEntity == null || !cameraEntity.isValid() || !player.isOnline() || step > ticks) {
                    activeTransitionTask = null;
                    this.cancel();
                    return;
                }

                double rawT = (double) step / (double) ticks;
                Location pose = CinematicCameraEngine.evaluateBezier(
                        cleanStart, controlPoint, end, rawT, CinematicCameraEngine.Easing.EASE_IN_OUT_CUBIC
                );

                CinematicCameraEngine.applyCameraPose(player, cameraEntity, pose);
                lockCameraToEntityId(player, cameraEntity.getEntityId());
                player.setAllowFlight(true);
                player.setFlying(true);
                step++;
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 0L, 1L);
    }

    private void cancelActiveTransition() {
        if (activeTransitionTask != null) {
            activeTransitionTask.cancel();
            activeTransitionTask = null;
        }
    }

    private void cancelLockRetry() {
        if (lockRetryTask != null) {
            lockRetryTask.cancel();
            lockRetryTask = null;
        }
    }

    // Overloads for legacy V1 callers
    public void spawnAndLockCamera(Player player, Location loc) {
        Location eyeLoc = loc.clone().add(0, 1.62, 0);
        eyeLoc.setYaw(loc.getYaw());
        eyeLoc.setPitch(loc.getPitch());
        spawnAndLockCamera(player, eyeLoc, null);
    }

    public void spawnAndLockCameraEye(Player player, Location eyeLoc) {
        spawnAndLockCamera(player, eyeLoc, null);
    }

    public void moveCameraEye(Player player, Location eyeLoc, WardrobeSession session, Location mannequinLoc) {
        moveCamera(player, eyeLoc, session);
    }

    public void moveCamera(Player player, Location loc, WardrobeSession session, Location mannequinLoc) {
        Location eyeLoc = loc.clone().add(0, 1.62, 0);
        eyeLoc.setYaw(loc.getYaw());
        eyeLoc.setPitch(loc.getPitch());
        moveCamera(player, eyeLoc, session);
    }

    public void lockCameraToEntityId(Player player, int entityId) {
        try {
            PacketContainer cameraPacket = ProtocolLibrary.getProtocolManager()
                    .createPacket(PacketType.Play.Server.CAMERA);
            cameraPacket.getIntegers().write(0, entityId);
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, cameraPacket);
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[Wardrobe] Failed to lock camera: " + e.getMessage());
        }
    }

    public void unlockCamera(Player player) {
        cancelActiveTransition();
        cancelLockRetry();
        lockCameraToEntityId(player, player.getEntityId());

        if (cameraEntity != null && cameraEntity.isValid()) {
            cameraEntity.remove();
            cameraEntity = null;
        }

        player.setAllowFlight(false);
        player.setFlying(false);
        player.setGravity(true);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
    }
}