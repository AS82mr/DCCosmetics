package dc.dccosmetics.wardrobe.camera;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Universal Kinematics & Shortest-Arc Camera Interpolation Engine.
 * Eliminates 360-degree Euler wrap-around spins and provides non-linear easing,
 * Quadratic Bezier curves, orbital arcs, and full-float Display entity camera sync.
 */
public final class CinematicCameraEngine {

    private static Constructor<?> vec3Ctor;
    private static Object vec3Zero;
    private static Constructor<?> posMoveRotCtor;
    private static Constructor<?> posSyncPacketCtor;
    private static boolean posSyncChecked = false;

    private CinematicCameraEngine() {}

    public enum Easing {
        LINEAR,
        EASE_IN_QUAD,
        EASE_OUT_QUAD,
        EASE_IN_OUT_QUAD,
        EASE_IN_CUBIC,
        EASE_OUT_CUBIC,
        EASE_IN_OUT_CUBIC,
        EASE_IN_OUT_SINE,
        EASE_OUT_BACK;

        public double apply(double t) {
            t = Math.max(0.0, Math.min(1.0, t));
            switch (this) {
                case EASE_IN_QUAD:
                    return t * t;
                case EASE_OUT_QUAD:
                    return 1.0 - (1.0 - t) * (1.0 - t);
                case EASE_IN_OUT_QUAD:
                    return t < 0.5 ? 2.0 * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 2) / 2.0;
                case EASE_IN_CUBIC:
                    return t * t * t;
                case EASE_OUT_CUBIC:
                    return 1.0 - Math.pow(1.0 - t, 3);
                case EASE_IN_OUT_CUBIC:
                    return t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3) / 2.0;
                case EASE_IN_OUT_SINE:
                    return -(Math.cos(Math.PI * t) - 1.0) / 2.0;
                case EASE_OUT_BACK: {
                    double c1 = 1.70158;
                    double c3 = c1 + 1.0;
                    return 1.0 + c3 * Math.pow(t - 1.0, 3) + c1 * Math.pow(t - 1.0, 2);
                }
                case LINEAR:
                default:
                    return t;
            }
        }
    }

    /**
     * Shortest-arc circular interpolation for yaw in degrees.
     * Prevents the 360-degree spin when crossing between -180/180 or 0/360.
     */
    public static float lerpYaw(float startYaw, float endYaw, double t) {
        float delta = ((endYaw - startYaw) % 360.0f + 540.0f) % 360.0f - 180.0f;
        return Location.normalizeYaw(startYaw + (float) (delta * t));
    }

    /**
     * Clamped pitch interpolation in degrees [-90, 90].
     */
    public static float lerpPitch(float startPitch, float endPitch, double t) {
        float pitch = (float) (startPitch + (endPitch - startPitch) * t);
        return Math.max(-90.0f, Math.min(90.0f, pitch));
    }

    /**
     * Standard 3D linear position interpolation with shortest-arc yaw and pitch.
     */
    public static Location lerpLocation(Location start, Location end, double rawT, Easing easing) {
        double t = easing.apply(rawT);
        World world = end.getWorld() != null ? end.getWorld() : start.getWorld();
        double x = start.getX() + (end.getX() - start.getX()) * t;
        double y = start.getY() + (end.getY() - start.getY()) * t;
        double z = start.getZ() + (end.getZ() - start.getZ()) * t;
        float yaw = lerpYaw(start.getYaw(), end.getYaw(), t);
        float pitch = lerpPitch(start.getPitch(), end.getPitch(), t);
        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Evaluates a 3D Quadratic Bezier curve B(t) = (1-t)^2 * P0 + 2(1-t)t * P1 + t^2 * P2
     * with shortest-arc rotation between start and end.
     */
    public static Location evaluateBezier(Location start, Location controlPoint, Location end, double rawT, Easing easing) {
        double t = easing.apply(rawT);
        double u = 1.0 - t;
        double tt = t * t;
        double uu = u * u;
        double ut2 = 2.0 * u * t;

        World world = end.getWorld() != null ? end.getWorld() : start.getWorld();
        double x = uu * start.getX() + ut2 * controlPoint.getX() + tt * end.getX();
        double y = uu * start.getY() + ut2 * controlPoint.getY() + tt * end.getY();
        double z = uu * start.getZ() + ut2 * controlPoint.getZ() + tt * end.getZ();

        float yaw = lerpYaw(start.getYaw(), end.getYaw(), t);
        float pitch = lerpPitch(start.getPitch(), end.getPitch(), t);
        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Computes an outward control point so the camera arcs smoothly around a central pivot (such as a mannequin)
     * instead of cutting straight through the model.
     */
    public static Location computeOrbitalControlPoint(Location start, Location end, Location pivot, double subtleLiftY) {
        World world = start.getWorld();
        double midX = (start.getX() + end.getX()) * 0.5;
        double midY = (start.getY() + end.getY()) * 0.5 + subtleLiftY;
        double midZ = (start.getZ() + end.getZ()) * 0.5;

        if (pivot == null || !pivot.getWorld().equals(world)) {
            return new Location(world, midX, midY, midZ);
        }

        Vector vStart = new Vector(start.getX() - pivot.getX(), 0, start.getZ() - pivot.getZ());
        Vector vEnd = new Vector(end.getX() - pivot.getX(), 0, end.getZ() - pivot.getZ());
        double avgRadius = (vStart.length() + vEnd.length()) * 0.5;

        Vector vMid = new Vector(midX - pivot.getX(), 0, midZ - pivot.getZ());
        if (vMid.lengthSquared() > 0.001 && avgRadius > 0.1) {
            vMid.normalize().multiply(avgRadius);
            return new Location(world, pivot.getX() + vMid.getX(), midY, pivot.getZ() + vMid.getZ());
        }

        return new Location(world, midX, midY, midZ);
    }

    /**
     * Evaluates a multi-point Catmull-Rom spline for complex camera tracks.
     */
    public static Location evaluateCatmullRom(List<Location> points, double rawT, Easing easing) {
        if (points == null || points.isEmpty()) return null;
        if (points.size() == 1) return points.get(0).clone();
        if (points.size() == 2) return lerpLocation(points.get(0), points.get(1), rawT, easing);

        double t = easing.apply(rawT);
        int numSections = points.size() - 1;
        double scaledT = t * numSections;
        int i = Math.min((int) Math.floor(scaledT), numSections - 1);
        double localT = scaledT - i;

        Location p0 = points.get(Math.max(0, i - 1));
        Location p1 = points.get(i);
        Location p2 = points.get(Math.min(points.size() - 1, i + 1));
        Location p3 = points.get(Math.min(points.size() - 1, i + 2));

        double t2 = localT * localT;
        double t3 = t2 * localT;

        double x = 0.5 * ((2.0 * p1.getX()) +
                (-p0.getX() + p2.getX()) * localT +
                (2.0 * p0.getX() - 5.0 * p1.getX() + 4.0 * p2.getX() - p3.getX()) * t2 +
                (-p0.getX() + 3.0 * p1.getX() - 3.0 * p2.getX() + p3.getX()) * t3);

        double y = 0.5 * ((2.0 * p1.getY()) +
                (-p0.getY() + p2.getY()) * localT +
                (2.0 * p0.getY() - 5.0 * p1.getY() + 4.0 * p2.getY() - p3.getY()) * t2 +
                (-p0.getY() + 3.0 * p1.getY() - 3.0 * p2.getY() + p3.getY()) * t3);

        double z = 0.5 * ((2.0 * p1.getZ()) +
                (-p0.getZ() + p2.getZ()) * localT +
                (2.0 * p0.getZ() - 5.0 * p1.getZ() + 4.0 * p2.getZ() - p3.getZ()) * t2 +
                (-p0.getZ() + 3.0 * p1.getZ() - 3.0 * p2.getZ() + p3.getZ()) * t3);

        float yaw = lerpYaw(p1.getYaw(), p2.getYaw(), localT);
        float pitch = lerpPitch(p1.getPitch(), p2.getPitch(), localT);

        return new Location(p1.getWorld(), x, y, z, yaw, pitch);
    }

    /**
     * Updates the camera entity pose.
     * For {@link Display} entities, sends full 32-bit float {@code ClientboundEntityPositionSyncPacket}
     * directly to the viewer so the client's unified InterpolationHandler glides at full monitor refresh rate
     * without 1.40625-degree byte quantization or ArmorStand head/body desync.
     */
    public static void applyCameraPose(Player viewer, Entity cameraEntity, Location pose) {
        applyCameraPose(viewer, cameraEntity, pose, false);
    }

    public static void applyCameraPose(Player viewer, Entity cameraEntity, Location pose, boolean syncServerLocation) {
        if (cameraEntity == null || !cameraEntity.isValid() || pose == null) return;

        float cleanYaw = Location.normalizeYaw(pose.getYaw());
        float cleanPitch = Math.max(-90.0f, Math.min(90.0f, pose.getPitch()));
        Location normalized = new Location(pose.getWorld(), pose.getX(), pose.getY(), pose.getZ(), cleanYaw, cleanPitch);

        if (cameraEntity instanceof Display) {
            if (syncServerLocation) {
                cameraEntity.teleport(normalized);
                cameraEntity.setRotation(cleanYaw, cleanPitch);
            }
            if (viewer != null && viewer.isOnline()) {
                if (!sendFullPrecisionDisplaySync(viewer, cameraEntity.getEntityId(), normalized, cleanYaw, cleanPitch)) {
                    cameraEntity.teleport(normalized);
                    cameraEntity.setRotation(cleanYaw, cleanPitch);
                }
            }
            return;
        }

        cameraEntity.teleport(normalized);
        cameraEntity.setRotation(cleanYaw, cleanPitch);

        if (viewer != null && viewer.isOnline()) {
            try {
                byte yawByte = (byte) ((int) (cleanYaw * 256.0f / 360.0f));
                byte pitchByte = (byte) ((int) (cleanPitch * 256.0f / 360.0f));

                PacketContainer headPacket = ProtocolLibrary.getProtocolManager()
                        .createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
                headPacket.getIntegers().write(0, cameraEntity.getEntityId());
                headPacket.getBytes().write(0, yawByte);

                PacketContainer lookPacket = ProtocolLibrary.getProtocolManager()
                        .createPacket(PacketType.Play.Server.ENTITY_LOOK);
                lookPacket.getIntegers().write(0, cameraEntity.getEntityId());
                lookPacket.getBytes().write(0, yawByte);
                lookPacket.getBytes().write(1, pitchByte);
                lookPacket.getBooleans().write(0, false);

                ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, headPacket);
                ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, lookPacket);
            } catch (Exception ignored) {}
        }
    }

    private static boolean sendFullPrecisionDisplaySync(Player viewer, int entityId, Location loc, float yaw, float pitch) {
        ensurePositionSyncHandles();
        if (posSyncPacketCtor == null || posMoveRotCtor == null || vec3Ctor == null || vec3Zero == null) {
            return false;
        }
        try {
            Object posVec = vec3Ctor.newInstance(loc.getX(), loc.getY(), loc.getZ());
            Object pmr = posMoveRotCtor.newInstance(posVec, vec3Zero, yaw, pitch);
            Object nmsPacket = posSyncPacketCtor.newInstance(entityId, pmr, false);
            PacketContainer container = PacketContainer.fromPacket(nmsPacket);
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, container);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void ensurePositionSyncHandles() {
        if (posSyncChecked) return;
        posSyncChecked = true;
        try {
            Class<?> vec3Cls = Class.forName("net.minecraft.world.phys.Vec3");
            vec3Ctor = vec3Cls.getConstructor(double.class, double.class, double.class);
            try {
                Field zeroField = vec3Cls.getField("ZERO");
                vec3Zero = zeroField.get(null);
            } catch (NoSuchFieldException ex) {
                vec3Zero = vec3Ctor.newInstance(0.0, 0.0, 0.0);
            }
            Class<?> pmrCls = Class.forName("net.minecraft.world.entity.PositionMoveRotation");
            posMoveRotCtor = pmrCls.getConstructor(vec3Cls, vec3Cls, float.class, float.class);
            Class<?> pktCls = Class.forName("net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket");
            posSyncPacketCtor = pktCls.getConstructor(int.class, pmrCls, boolean.class);
        } catch (Throwable ignored) {
            vec3Ctor = null;
            posMoveRotCtor = null;
            posSyncPacketCtor = null;
        }
    }
}