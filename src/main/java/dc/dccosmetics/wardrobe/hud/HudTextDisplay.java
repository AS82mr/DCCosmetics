package dc.dccosmetics.wardrobe.hud;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.nms.ProtocolLibAdapter;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fake TEXT_DISPLAY entity — sent only to the viewing player.
 *
 * No INTERACTION entity is spawned. Clicks are handled via PlayerInteractEvent.
 *
 * Scale is applied using NMS EntityDataSerializers.VECTOR3/QUATERNION obtained
 * via MinecraftReflection to avoid the silent failure of the deprecated
 * WrappedDataWatcher.Registry.get(Class) lookup.
 *
 * Metadata indices for Minecraft 1.21.x Display entity:
 *   12 = scale        (Vector3f)
 *   13 = leftRotation (Quaternionf)
 *   14 = rightRotation(Quaternionf)
 *   15 = billboard     (byte, 3 = CENTER)
 *   23 = text          (Component)
 *   25 = background    (int ARGB)
 */
public class HudTextDisplay {

    private final int entityId;
    private final UUID uuid;
    private final Location location;
    private final Player viewer;

    private String text           = "";
    private int    backgroundColor = 0x40000000;
    private byte   billboardMode   = 3;    // CENTER
    private float  scale           = 0.18f;
    private boolean destroyed      = false; // guard against stale scheduler tasks

    // Cached NMS serializers (found once, reused)
    private static WrappedDataWatcher.Serializer VEC3_SER      = null;
    private static WrappedDataWatcher.Serializer QUAT_SER      = null;
    private static WrappedDataWatcher.Serializer BYTE_SER      = null;
    private static WrappedDataWatcher.Serializer INT_SER       = null;
    private static WrappedDataWatcher.Serializer CHAT_SER      = null;
    private static boolean serialisersInitialised = false;

    // Animation
    private static final int INTERP_DELAY    = 0; // scheduler handles the delay; start immediately when packet arrives
    private static final int INTERP_DURATION = 5; // ticks

    // Correct metadata indices for 1.21.x Display entity:
    //   8  = interpolation delay    (int)
    //   9  = teleport duration      (int) [added 1.20.2]
    //   10 = interpolation duration (int)
    //   11 = translation            (Vector3f)  ← DO NOT write int here
    //   12 = scale                  (Vector3f)
    //   13 = left rotation          (Quaternionf)
    //   14 = right rotation         (Quaternionf)
    //   15 = billboard              (byte)
    //   23 = text                   (Component)
    //   25 = background color       (int ARGB)

    public HudTextDisplay(Player viewer, Location location) {
        this.viewer   = viewer;
        this.location = location;
        this.entityId = ProtocolLibAdapter.ENTITY_ID_COUNTER.getAndIncrement();
        this.uuid     = UUID.randomUUID();
        initSerializers();
    }

    // ─────────────────────────────────────────────────────────────
    //  SERIALIZER INIT (once per JVM lifetime)
    // ─────────────────────────────────────────────────────────────

    private static synchronized void initSerializers() {
        if (serialisersInitialised) return;
        serialisersInitialised = true;

        try {
            // Primitive serializers — these always work
            BYTE_SER = WrappedDataWatcher.Registry.get(Byte.class);
            INT_SER  = WrappedDataWatcher.Registry.get(Integer.class);
            CHAT_SER = WrappedDataWatcher.Registry.getChatComponentSerializer(false);

            // NMS reflection for Vector3f / Quaternionf
            Class<?> nmsSerializers = MinecraftReflection
                    .getMinecraftClass("network.syncher.EntityDataSerializers");

            Field vec3Field = nmsSerializers.getDeclaredField("VECTOR3");
            Field quatField = nmsSerializers.getDeclaredField("QUATERNION");
            vec3Field.setAccessible(true);
            quatField.setAccessible(true);

            Object nmsVec3 = vec3Field.get(null);
            Object nmsQuat = quatField.get(null);

            VEC3_SER = WrappedDataWatcher.Registry.fromHandle(nmsVec3);
            QUAT_SER = WrappedDataWatcher.Registry.fromHandle(nmsQuat);

            // Obtain INT serializer via NMS reflection — same handle the server uses
            // for interpolation fields (indices 8, 9). Registry.get(Integer.class) can
            // return the wrong type-ID in some ProtocolLib builds.
            Field intField = nmsSerializers.getDeclaredField("INT");
            intField.setAccessible(true);
            INT_SER = WrappedDataWatcher.Registry.fromHandle(intField.get(null));

            DCCosmetics.getInstance().getLogger().info(
                    "[HudTextDisplay] Serializers initialized. Scale support: " + (VEC3_SER != null)
                    + " INT via NMS: " + (INT_SER != null));

        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning(
                    "[HudTextDisplay] Serializer init failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  SPAWN
    // ─────────────────────────────────────────────────────────────

    public void spawn() {
        PacketContainer spawnPacket = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawnPacket.getIntegers().write(0, entityId);
        spawnPacket.getUUIDs().write(0, uuid);
        spawnPacket.getEntityTypeModifier().write(0, EntityType.TEXT_DISPLAY);
        spawnPacket.getDoubles().write(0, location.getX());
        spawnPacket.getDoubles().write(1, location.getY());
        spawnPacket.getDoubles().write(2, location.getZ());
        spawnPacket.getBytes().write(0, (byte) (location.getPitch() * 256.0F / 360.0F));
        spawnPacket.getBytes().write(1, (byte) (location.getYaw()   * 256.0F / 360.0F));
        spawnPacket.getBytes().write(2, (byte) (location.getYaw()   * 256.0F / 360.0F));

        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, spawnPacket);
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[HudTextDisplay] Spawn failed: " + e.getMessage());
            return;
        }

        // Always send scale=0, interp=false first — lets the client initialize
        // entity state before any interpolation is attempted.
        sendMetadataWithScale(0.0f, false);
    }

    /**
     * Snaps to {@code targetScale} after a 2-tick delay (lets the client initialise
     * entity state after spawn). Single step — no visible scaling animation.
     */
    public void animateTo(float targetScale) {
        if (destroyed) return;
        this.scale = targetScale;
        DCCosmetics.getInstance().getServer().getScheduler().runTaskLater(
                DCCosmetics.getInstance(),
                () -> {
                    if (!destroyed) {
                        this.scale = targetScale;
                        sendMetadataWithScale(targetScale, false);
                    }
                },
                2L);
    }

    // ─────────────────────────────────────────────────────────────
    //  SETTERS
    // ─────────────────────────────────────────────────────────────

    public void setText(String text) {
        this.text = text;
        sendMetadataWithScale(scale, false);
    }

    public void setScale(float scale) {
        this.scale = scale;
        sendMetadataWithScale(scale, false); // interp=false — interp=true crashes client
    }

    public void setBackgroundColor(int argb) {
        this.backgroundColor = argb;
        sendMetadataWithScale(scale, false);
    }

    private void sendMetadataWithScale(float targetScale, boolean interpolate) {
        if (destroyed) return;         // entity already removed — stale scheduler task, ignore
        if (BYTE_SER == null) return;  // serializers not ready yet

        DCCosmetics.getInstance().getLogger().info(
            "[Wardrobe][DEBUG] metadata entity=" + entityId +
            " scale=" + targetScale + " interp=" + interpolate +
            " viewer=" + viewer.getName());
        PacketContainer meta = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.ENTITY_METADATA);
        meta.getIntegers().write(0, entityId);

        List<WrappedDataValue> values = new ArrayList<>();

        // NOTE: Display entity transformation interpolation (indices 8/9) is intentionally
        // NOT used — sending interp=true consistently crashes this client (Fabric + Sodium).
        // Animation is achieved via Bukkit scheduler step-animation in animateTo() instead.

        // ── Scale + Rotation (indices 12–14) ──────────────────────
        if (VEC3_SER != null && QUAT_SER != null) {
            try {
                org.joml.Quaternionf identity = new org.joml.Quaternionf(0f, 0f, 0f, 1f);
                org.joml.Vector3f    scaleVec = new org.joml.Vector3f(targetScale, targetScale, targetScale);

                values.add(new WrappedDataValue(12, VEC3_SER, scaleVec));    // scale
                values.add(new WrappedDataValue(13, QUAT_SER, identity));    // leftRotation
                values.add(new WrappedDataValue(14, QUAT_SER, identity));    // rightRotation
            } catch (Exception e) {
                DCCosmetics.getInstance().getLogger().warning(
                        "[HudTextDisplay] Scale metadata error: " + e.getMessage());
            }
        }

        // ── Billboard (index 15) ──────────────────────────────────
        values.add(new WrappedDataValue(15, BYTE_SER, billboardMode));

        // ── Text content (index 23) ───────────────────────────────
        values.add(new WrappedDataValue(23, CHAT_SER,
                WrappedChatComponent.fromLegacyText(text).getHandle()));

        // ── Background colour (index 25) ─────────────────────────
        values.add(new WrappedDataValue(25, INT_SER, backgroundColor));

        meta.getDataValueCollectionModifier().write(0, values);

        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, meta);
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning(
                    "[HudTextDisplay] Metadata update failed: " + e.getMessage());
        }
    }

    /**
     * Hides scale immediately (interp=false — interp=true crashes this client)
     * then destroys the entity after 5 ticks.
     */
    public void destroyAnimated() {
        sendMetadataWithScale(0.0f, false); // snap to scale=0 first (must come before destroyed=true)
        destroyed = true;                   // THEN block stale scheduler tasks from firing
        DCCosmetics.getInstance().getServer().getScheduler().runTaskLater(
                DCCosmetics.getInstance(), this::destroy, 5L);
    }

    // ─────────────────────────────────────────────────────────────
    //  DESTROY
    // ─────────────────────────────────────────────────────────────

    public void destroy() {
        destroyed = true;  // prevent any future metadata sends to this entity ID
        PacketContainer destroyPacket = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroyPacket.getIntLists().write(0, java.util.Collections.singletonList(entityId));
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, destroyPacket);
        } catch (Exception ignored) {}
    }

    public Location getLocation() { return location; }
}
