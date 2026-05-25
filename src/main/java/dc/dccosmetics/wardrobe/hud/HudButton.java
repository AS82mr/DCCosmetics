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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A single standalone TextDisplay entity representing one menu button row.
 *
 * Each button lives in its own entity so it can be individually scaled,
 * highlighted (on scroll selection), and animated independently.
 *
 * Metadata indices for Minecraft 1.21.x Display entity:
 *   10 = interpolation delay    (int)
 *   11 = interpolation duration (int)
 *   12 = scale                  (Vector3f)
 *   13 = leftRotation           (Quaternionf)
 *   14 = rightRotation          (Quaternionf)
 *   15 = billboard              (byte, 3 = CENTER)
 *   23 = text                   (Component)
 *   24 = line width             (int)
 *   25 = background color       (int ARGB)
 *   26 = text opacity           (byte)
 *   27 = flags                  (byte: bit0=shadow, bit1=see-through, bit2=default bg, bit3=align)
 */
public class HudButton {

    /** Button states for visual feedback */
    public enum State { NORMAL, SELECTED, PREVIEWING }

    private final int      entityId;
    private final UUID     uuid;
    private final Player   viewer;
    private Location       location;  // mutable — updated when panel repositions

    private String  text           = "";
    private float   baseScale      = 0.18f;
    private State   state          = State.NORMAL;

    // Shared serializer references (initialised once by HudTextDisplay)
    private static WrappedDataWatcher.Serializer VEC3_SER  = null;
    private static WrappedDataWatcher.Serializer QUAT_SER  = null;
    private static WrappedDataWatcher.Serializer BYTE_SER  = null;
    private static WrappedDataWatcher.Serializer INT_SER   = null;
    private static WrappedDataWatcher.Serializer CHAT_SER  = null;
    private static boolean serialisersReady = false;

    // ── Per-state visual config ──────────────────────────────────────────────
    // Normal:    dark semi-transparent bg, dim text
    // Selected:  neon cyan bg tint, full white text, 1.12× scale
    // Previewing: green tint, checkmark text

    private static final int BG_NORMAL     = 0x50000000; // very dark semi-transparent
    private static final int BG_SELECTED   = 0x9000274D; // neon midnight blue
    private static final int BG_PREVIEWING = 0x90002800; // dark green

    private static final float SCALE_NORMAL   = 1.00f;
    private static final float SCALE_SELECTED = 1.10f; // 10% pop

    // Animation interpolation settings
    private static final int INTERP_DELAY    = 0;
    private static final int INTERP_DURATION = 5; // ticks for the pop animation

    public HudButton(Player viewer, Location location, float baseScale) {
        this.viewer    = viewer;
        this.location  = location;
        this.baseScale = baseScale;
        this.entityId  = ProtocolLibAdapter.ENTITY_ID_COUNTER.getAndIncrement();
        this.uuid      = UUID.randomUUID();
        ensureSerializers();
    }

    // ── Serializer bootstrap ─────────────────────────────────────────────────

    private static synchronized void ensureSerializers() {
        if (serialisersReady) return;
        try {
            BYTE_SER = WrappedDataWatcher.Registry.get(Byte.class);
            INT_SER  = WrappedDataWatcher.Registry.get(Integer.class);
            CHAT_SER = WrappedDataWatcher.Registry.getChatComponentSerializer(false);

            Class<?> nmsSerializers = MinecraftReflection
                    .getMinecraftClass("network.syncher.EntityDataSerializers");
            java.lang.reflect.Field vec3Field = nmsSerializers.getDeclaredField("VECTOR3");
            java.lang.reflect.Field quatField = nmsSerializers.getDeclaredField("QUATERNION");
            vec3Field.setAccessible(true);
            quatField.setAccessible(true);
            VEC3_SER = WrappedDataWatcher.Registry.fromHandle(vec3Field.get(null));
            QUAT_SER = WrappedDataWatcher.Registry.fromHandle(quatField.get(null));

            serialisersReady = true;
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[HudButton] Serializer init failed: " + e.getMessage());
        }
    }

    // ── Spawn ────────────────────────────────────────────────────────────────

    /**
     * Spawns the entity at its stored location.
     * Starts at scale 0 (invisible), then animates to full scale via pop-in.
     */
    public void spawn() {
        PacketContainer spawnPacket = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawnPacket.getIntegers().write(0, entityId);
        spawnPacket.getUUIDs().write(0, uuid);
        spawnPacket.getEntityTypeModifier().write(0, EntityType.TEXT_DISPLAY);
        spawnPacket.getDoubles().write(0, location.getX());
        spawnPacket.getDoubles().write(1, location.getY());
        spawnPacket.getDoubles().write(2, location.getZ());
        spawnPacket.getBytes().write(0, (byte)(location.getPitch() * 256.0f / 360.0f));
        spawnPacket.getBytes().write(1, (byte)(location.getYaw()   * 256.0f / 360.0f));
        spawnPacket.getBytes().write(2, (byte)(location.getYaw()   * 256.0f / 360.0f));
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, spawnPacket);
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[HudButton] Spawn failed: " + e.getMessage());
            return;
        }

        // Send initial metadata (scale=0) immediately, then pop-in 1 tick later
        sendMetadata(0.0f, false);
        DCCosmetics.getInstance().getServer().getScheduler().runTaskLater(
                DCCosmetics.getInstance(),
                () -> sendMetadata(resolveScale(), true),
                1L
        );
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public void setText(String text)         { this.text = text; }
    public void setBaseScale(float scale)    { this.baseScale = scale; }
    public String getText()                  { return text; }
    public State getState()                  { return state; }

    /**
     * Changes the visual state of this button and pushes a metadata update.
     * Uses interpolation so the pop animation is smooth.
     */
    public void setState(State newState) {
        this.state = newState;
        sendMetadata(resolveScale(), true);
    }

    /**
     * Updates text + state atomically.
     */
    public void update(String newText, State newState) {
        this.text  = newText;
        this.state = newState;
        sendMetadata(resolveScale(), true);
    }

    /**
     * Moves the entity to a new world location (teleport packet).
     */
    public void teleportTo(Location loc) {
        this.location = loc;
        PacketContainer tp = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
        tp.getIntegers().write(0, entityId);
        tp.getDoubles().write(0, loc.getX());
        tp.getDoubles().write(1, loc.getY());
        tp.getDoubles().write(2, loc.getZ());
        tp.getBytes().write(0, (byte)(loc.getYaw()   * 256.0f / 360.0f));
        tp.getBytes().write(1, (byte)(loc.getPitch() * 256.0f / 360.0f));
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, tp);
        } catch (Exception ignored) {}
        // After teleport, resend metadata so scale/bg is correct for new position
        sendMetadata(resolveScale(), false);
    }

    /**
     * Pop-out: animate scale to 0, then schedule entity destruction 1 tick later.
     */
    public void destroyAnimated() {
        sendMetadata(0.0f, true);
        DCCosmetics.getInstance().getServer().getScheduler().runTaskLater(
                DCCosmetics.getInstance(), this::destroyImmediate, 6L);
    }

    public void destroyImmediate() {
        PacketContainer destroyPacket = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroyPacket.getIntLists().write(0, Collections.singletonList(entityId));
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, destroyPacket);
        } catch (Exception ignored) {}
    }

    public int getEntityId() { return entityId; }
    public Location getLocation() { return location; }

    // ── Internals ────────────────────────────────────────────────────────────

    private float resolveScale() {
        float multiplier = (state == State.SELECTED) ? SCALE_SELECTED : SCALE_NORMAL;
        return baseScale * multiplier;
    }

    private int resolveBackground() {
        switch (state) {
            case SELECTED:   return BG_SELECTED;
            case PREVIEWING: return BG_PREVIEWING;
            default:         return BG_NORMAL;
        }
    }

    private void sendMetadata(float scale, boolean interpolate) {
        if (BYTE_SER == null) return;

        PacketContainer meta = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.ENTITY_METADATA);
        meta.getIntegers().write(0, entityId);

        List<WrappedDataValue> values = new ArrayList<>();

        // ── Interpolation ─────────────────────────────────────────
        if (interpolate && INT_SER != null) {
            values.add(new WrappedDataValue(10, INT_SER, INTERP_DELAY));
            values.add(new WrappedDataValue(11, INT_SER, INTERP_DURATION));
        }

        // ── Scale + Rotation ──────────────────────────────────────
        if (VEC3_SER != null && QUAT_SER != null) {
            try {
                org.joml.Quaternionf identity = new org.joml.Quaternionf(0f, 0f, 0f, 1f);
                org.joml.Vector3f    scaleVec = new org.joml.Vector3f(scale, scale, scale);
                values.add(new WrappedDataValue(12, VEC3_SER, scaleVec));
                values.add(new WrappedDataValue(13, QUAT_SER, identity));
                values.add(new WrappedDataValue(14, QUAT_SER, identity));
            } catch (Exception ignored) {}
        }

        // ── Billboard: CENTER ──────────────────────────────────────
        values.add(new WrappedDataValue(15, BYTE_SER, (byte) 3));

        // ── Text ──────────────────────────────────────────────────
        values.add(new WrappedDataValue(23, CHAT_SER,
                WrappedChatComponent.fromLegacyText(text).getHandle()));

        // ── Background colour ──────────────────────────────────────
        if (INT_SER != null) {
            values.add(new WrappedDataValue(25, INT_SER, resolveBackground()));
        }

        meta.getDataValueCollectionModifier().write(0, values);
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, meta);
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[HudButton] Metadata failed: " + e.getMessage());
        }
    }
}
