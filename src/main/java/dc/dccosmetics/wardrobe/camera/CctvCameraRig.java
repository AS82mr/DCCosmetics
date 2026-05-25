package dc.dccosmetics.wardrobe.camera;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.nms.ProtocolLibAdapter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * CctvCameraRig — A physical "camera" object visible in F5 / third-person view.
 *
 * Spawned at the same coordinates as the invisible Marker that locks the player's
 * view, so when the player switches to F5, they see a camera model instead of
 * floating in empty air.
 *
 * Structure (client-side entities only, not spawned on the server):
 *   1. ITEM_DISPLAY — player head with the custom camera skin (the "lens")
 *   2. BLOCK_DISPLAY — coal block slightly below, offset back  (the "body")
 *   3. BLOCK_DISPLAY — coal block further back (the "mount/tripod top")
 *
 * All three entities are destroyed and respawned whenever the camera moves.
 */
public class CctvCameraRig {

    // The custom head texture provided (camera skin)
    private static final String CAMERA_SKIN_VALUE =
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDlkZGFjNDA5Zjk2NTc1ZjliMjc2ZjIxNzE3ZGZhZDZiZWU4N2FiNTgyZjdlOTQ0MjlmOTg5NDlmYTMyMTRlMyJ9fX0=";

    private final Player viewer;
    private final List<Integer> entityIds = new ArrayList<>();

    // Serializer references — shared with HudTextDisplay
    private static WrappedDataWatcher.Serializer VEC3_SER  = null;
    private static WrappedDataWatcher.Serializer QUAT_SER  = null;
    private static WrappedDataWatcher.Serializer BYTE_SER  = null;
    private static WrappedDataWatcher.Serializer INT_SER   = null;
    private static WrappedDataWatcher.Serializer SLOT_SER  = null; // for item display
    private static WrappedDataWatcher.Serializer BLOCK_SER = null; // for block display (BlockData)
    private static boolean serialisersReady = false;

    public CctvCameraRig(Player viewer) {
        this.viewer = viewer;
        ensureSerializers();
    }

    // ── Serializer init ───────────────────────────────────────────────────────

    private static synchronized void ensureSerializers() {
        if (serialisersReady) return;
        try {
            BYTE_SER = WrappedDataWatcher.Registry.get(Byte.class);
            INT_SER  = WrappedDataWatcher.Registry.get(Integer.class);

            Class<?> nmsSerializers = com.comphenix.protocol.utility.MinecraftReflection
                    .getMinecraftClass("network.syncher.EntityDataSerializers");
            java.lang.reflect.Field vec3Field  = nmsSerializers.getDeclaredField("VECTOR3");
            java.lang.reflect.Field quatField  = nmsSerializers.getDeclaredField("QUATERNION");
            java.lang.reflect.Field slotField  = nmsSerializers.getDeclaredField("ITEM_STACK");
            java.lang.reflect.Field blockField = nmsSerializers.getDeclaredField("BLOCK_STATE");
            vec3Field.setAccessible(true);
            quatField.setAccessible(true);
            slotField.setAccessible(true);
            blockField.setAccessible(true);
            VEC3_SER  = WrappedDataWatcher.Registry.fromHandle(vec3Field.get(null));
            QUAT_SER  = WrappedDataWatcher.Registry.fromHandle(quatField.get(null));
            SLOT_SER  = WrappedDataWatcher.Registry.fromHandle(slotField.get(null));
            BLOCK_SER = WrappedDataWatcher.Registry.fromHandle(blockField.get(null));

            serialisersReady = true;
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[CctvCameraRig] Serializer init failed: " + e.getMessage());
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Spawns the camera rig at the given location.
     */
    public void spawnAt(Location loc) {
        destroyAll();

        // 1. Item display (camera head) — at the exact marker position
        spawnItemDisplay(loc, CAMERA_SKIN_VALUE);

        // 2. Coal block body — 0.3 blocks down + 0.2 blocks behind the lens
        org.bukkit.util.Vector backward = loc.getDirection().clone().multiply(-0.25);
        Location bodyLoc = loc.clone().add(backward).add(0, -0.28, 0);
        spawnBlockDisplay(bodyLoc, Material.COAL_BLOCK, 0.45f);

        // 3. Coal block mount — further back and lower (tripod top)
        Location mountLoc = loc.clone().add(backward.clone().multiply(2.5)).add(0, -0.5, 0);
        spawnBlockDisplay(mountLoc, Material.COAL_BLOCK, 0.3f);
    }

    /**
     * Moves the rig to a new location (destroys old, spawns new).
     */
    public void moveTo(Location loc) {
        spawnAt(loc);
    }

    /**
     * Destroys all rig entities.
     */
    public void destroy() {
        destroyAll();
    }

    // ── Spawn helpers ─────────────────────────────────────────────────────────

    private void spawnItemDisplay(Location loc, String skinValue) {
        int id = ProtocolLibAdapter.ENTITY_ID_COUNTER.getAndIncrement();
        entityIds.add(id);

        PacketContainer spawn = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawn.getIntegers().write(0, id);
        spawn.getUUIDs().write(0, UUID.randomUUID());
        spawn.getEntityTypeModifier().write(0, EntityType.ITEM_DISPLAY);
        spawn.getDoubles().write(0, loc.getX());
        spawn.getDoubles().write(1, loc.getY());
        spawn.getDoubles().write(2, loc.getZ());
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, spawn);
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[CctvCameraRig] ItemDisplay spawn failed: " + e.getMessage());
            return;
        }

        // Build a player head ItemStack with the custom skin
        ItemStack head = buildCustomHead(skinValue);

        // Metadata: scale=0.5, billboard=NONE (fixed in world), item=head
        List<WrappedDataValue> values = new ArrayList<>();
        if (VEC3_SER != null && QUAT_SER != null) {
            try {
                org.joml.Vector3f scaleVec = new org.joml.Vector3f(0.5f, 0.5f, 0.5f);
                org.joml.Quaternionf identity = new org.joml.Quaternionf(0f, 0f, 0f, 1f);
                values.add(new WrappedDataValue(12, VEC3_SER, scaleVec));
                values.add(new WrappedDataValue(13, QUAT_SER, identity));
                values.add(new WrappedDataValue(14, QUAT_SER, identity));
            } catch (Exception ignored) {}
        }
        // Billboard = 0 (FIXED) so the camera head doesn't spin around
        if (BYTE_SER != null) values.add(new WrappedDataValue(15, BYTE_SER, (byte) 0));

        // Item display item (index 22)
        if (SLOT_SER != null && head != null) {
            try {
                Object nmsStack = com.comphenix.protocol.utility.MinecraftReflection
                        .getMinecraftItemStack(head);
                values.add(new WrappedDataValue(22, SLOT_SER, nmsStack));
            } catch (Exception ignored) {}
        }

        sendMeta(id, values);
    }

    private void spawnBlockDisplay(Location loc, Material material, float scale) {
        int id = ProtocolLibAdapter.ENTITY_ID_COUNTER.getAndIncrement();
        entityIds.add(id);

        PacketContainer spawn = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawn.getIntegers().write(0, id);
        spawn.getUUIDs().write(0, UUID.randomUUID());
        spawn.getEntityTypeModifier().write(0, EntityType.BLOCK_DISPLAY);
        spawn.getDoubles().write(0, loc.getX());
        spawn.getDoubles().write(1, loc.getY());
        spawn.getDoubles().write(2, loc.getZ());
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, spawn);
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[CctvCameraRig] BlockDisplay spawn failed: " + e.getMessage());
            return;
        }

        List<WrappedDataValue> values = new ArrayList<>();
        if (VEC3_SER != null && QUAT_SER != null) {
            try {
                org.joml.Vector3f scaleVec = new org.joml.Vector3f(scale, scale, scale);
                org.joml.Quaternionf identity = new org.joml.Quaternionf(0f, 0f, 0f, 1f);
                values.add(new WrappedDataValue(12, VEC3_SER, scaleVec));
                values.add(new WrappedDataValue(13, QUAT_SER, identity));
                values.add(new WrappedDataValue(14, QUAT_SER, identity));
            } catch (Exception ignored) {}
        }
        if (BYTE_SER != null) values.add(new WrappedDataValue(15, BYTE_SER, (byte) 0)); // FIXED billboard

        // Block display block state (index 22)
        if (BLOCK_SER != null) {
            try {
                Object nmsBlockData = com.comphenix.protocol.utility.MinecraftReflection
                        .getMinecraftClass("world.level.block.state.BlockState");
                // Use CraftBlockData to get NMS block state
                org.bukkit.block.data.BlockData bdata = material.createBlockData();
                Object craftBd = bdata;
                // Reflection: CraftBlockData.getState()
                java.lang.reflect.Method getState = craftBd.getClass().getMethod("getState");
                getState.setAccessible(true);
                Object state = getState.invoke(craftBd);
                values.add(new WrappedDataValue(22, BLOCK_SER, state));
            } catch (Exception e) {
                DCCosmetics.getInstance().getLogger().warning("[CctvCameraRig] BlockState metadata failed: " + e.getMessage());
            }
        }

        sendMeta(id, values);
    }

    private void sendMeta(int entityId, List<WrappedDataValue> values) {
        if (values.isEmpty()) return;
        PacketContainer meta = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.ENTITY_METADATA);
        meta.getIntegers().write(0, entityId);
        meta.getDataValueCollectionModifier().write(0, values);
        DCCosmetics.getInstance().getServer().getScheduler().runTaskLater(
                DCCosmetics.getInstance(),
                () -> {
                    try {
                        ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, meta);
                    } catch (Exception ignored) {}
                }, 1L
        );
    }

    private void destroyAll() {
        if (entityIds.isEmpty()) return;
        PacketContainer destroy = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroy.getIntLists().write(0, new ArrayList<>(entityIds));
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, destroy);
        } catch (Exception ignored) {}
        entityIds.clear();
    }

    // ── Head item builder ──────────────────────────────────────────────────────

    private static ItemStack buildCustomHead(String base64Value) {
        try {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta == null) return head;

            // Use a fake UUID consistent with this skin value to avoid duplicate skull profiles
            UUID fakeUUID = UUID.nameUUIDFromBytes(base64Value.getBytes());
            org.bukkit.profile.PlayerProfile profile = DCCosmetics.getInstance().getServer()
                    .createPlayerProfile(fakeUUID, "CctvCamera");
            org.bukkit.profile.PlayerTextures textures = profile.getTextures();

            try {
                java.net.URL skinUrl = new java.net.URL(
                    new String(java.util.Base64.getDecoder().decode(base64Value))
                        .replaceAll(".*\"url\":\"([^\"]+)\".*", "$1"));
                textures.setSkin(skinUrl);
            } catch (Exception e) {
                // Fallback: decode the base64 value directly
                byte[] decoded = java.util.Base64.getDecoder().decode(base64Value);
                String json = new String(decoded);
                // Extract URL with simple substring
                int urlStart = json.indexOf("\"url\":\"") + 7;
                int urlEnd   = json.indexOf("\"", urlStart);
                if (urlStart > 7 && urlEnd > urlStart) {
                    textures.setSkin(new java.net.URL(json.substring(urlStart, urlEnd)));
                }
            }

            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
            head.setItemMeta(meta);
            return head;
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[CctvCameraRig] Head build failed: " + e.getMessage());
            return new ItemStack(Material.PLAYER_HEAD);
        }
    }
}
