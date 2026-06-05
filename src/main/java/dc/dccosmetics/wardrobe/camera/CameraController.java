package dc.dccosmetics.wardrobe.camera;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.wardrobe.WardrobeSession;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.UUID;
import dc.dccosmetics.nms.ProtocolLibAdapter;

public class CameraController {

    private int cameraEntityId = -1;

    /**
     * Spawns a fake Marker entity at {@code loc} and locks the player's camera to it.
     * Also records the camera location in the session so the HUD can reposition.
     */
    public void spawnAndLockCamera(Player player, Location loc) {
        this.cameraEntityId = ProtocolLibAdapter.ENTITY_ID_COUNTER.getAndIncrement();

        PacketContainer spawnPacket = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawnPacket.getIntegers().write(0, cameraEntityId);
        spawnPacket.getUUIDs().write(0, UUID.randomUUID());
        spawnPacket.getEntityTypeModifier().write(0, EntityType.ARMOR_STAND);
        spawnPacket.getDoubles().write(0, loc.getX());
        spawnPacket.getDoubles().write(1, loc.getY()); // Armor stands naturally have an eye height
        spawnPacket.getDoubles().write(2, loc.getZ());
        spawnPacket.getBytes().write(0, (byte) (loc.getPitch() * 256.0F / 360.0F));
        spawnPacket.getBytes().write(1, (byte) (loc.getYaw() * 256.0F / 360.0F));
        spawnPacket.getBytes().write(2, (byte) (loc.getYaw() * 256.0F / 360.0F));

        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, spawnPacket);
            
            // Send metadata to make armor stand invisible (index 0, bit 5 = 0x20)
            PacketContainer meta = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_METADATA);
            meta.getIntegers().write(0, cameraEntityId);
            java.util.List<com.comphenix.protocol.wrappers.WrappedDataValue> values = new java.util.ArrayList<>();
            values.add(new com.comphenix.protocol.wrappers.WrappedDataValue(
                0, com.comphenix.protocol.wrappers.WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x20
            ));
            meta.getDataValueCollectionModifier().write(0, values);
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, meta);
            
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[Wardrobe] Failed to spawn camera entity: " + e.getMessage());
        }

        lockCameraToEntityId(player, cameraEntityId);
    }

    /**
     * Moves the camera to a new location by destroying the old entity and spawning a fresh one.
     * Then repositions the HUD panels in front of the new camera position.
     */
    public void moveCamera(Player player, Location loc, WardrobeSession session, Location mannequinLoc) {
        // Destroy the old camera entity
        if (cameraEntityId != -1) {
            try {
                PacketContainer destroyPacket = ProtocolLibrary.getProtocolManager()
                        .createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                destroyPacket.getIntLists().write(0, Collections.singletonList(cameraEntityId));
                ProtocolLibrary.getProtocolManager().sendServerPacket(player, destroyPacket);
            } catch (Exception ignored) {}
            cameraEntityId = -1;
        }

        // Spawn new camera at the new location
        spawnAndLockCamera(player, loc);

        // Store the new camera location in the session
        if (session != null) {
            session.setCurrentCameraLocation(loc);
        }

        // Move CCTV camera rig to new location
        if (session != null && session.getCctvRig() != null && mannequinLoc != null) {
            session.getCctvRig().moveTo(loc, mannequinLoc);
        }

        // Reposition HUD panels in front of the new camera view
        if (session != null && session.getHud() != null && mannequinLoc != null) {
            session.getHud().repositionForCamera(loc, mannequinLoc);
        }
    }

    /** Overload without session/mannequin for simple unlock/lock operations */
    public void moveCamera(Player player, Location loc) {
        moveCamera(player, loc, null, null);
    }

    private void lockCameraToEntityId(Player player, int entityId) {
        PacketContainer cameraPacket = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.CAMERA);
        cameraPacket.getIntegers().write(0, entityId);
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, cameraPacket);
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[Wardrobe] Failed to lock camera: " + e.getMessage());
        }
    }

    public void unlockCamera(Player player) {
        lockCameraToEntityId(player, player.getEntityId());

        if (cameraEntityId != -1) {
            try {
                PacketContainer destroyPacket = ProtocolLibrary.getProtocolManager()
                        .createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                destroyPacket.getIntLists().write(0, Collections.singletonList(cameraEntityId));
                ProtocolLibrary.getProtocolManager().sendServerPacket(player, destroyPacket);
            } catch (Exception ignored) {}
            cameraEntityId = -1;
        }
    }
}
