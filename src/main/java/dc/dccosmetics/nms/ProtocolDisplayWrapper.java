package dc.dccosmetics.nms;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.api.DisplayWrapper;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class ProtocolDisplayWrapper implements DisplayWrapper {

    private final Logger logger = DCCosmetics.getInstance().getLogger();
    private final ProtocolManager protocolManager;
    private final int entityId;
    private final UUID uuid;
    private final List<Player> viewers;
    private Location location;

    // Transform State
    private Vector3f scale = new Vector3f(1, 1, 1);
    private Vector3f translation = new Vector3f(0, 0, 0);
    private Vector3f rotation = new Vector3f(0, 0, 0);
    private String hexColor = "#FFFFFF"; // Default to White
    private org.bukkit.entity.TextDisplay cachedDummy = null;
    private String textShape = null;
    private boolean blockbenchMode = false;
    private org.joml.Quaternionf rawQuaternion = null;

    public ProtocolDisplayWrapper(List<Player> viewers, Location location, ProtocolManager protocolManager) {
        this.protocolManager = protocolManager;
        this.viewers = viewers;
        this.location = location;

        this.entityId = ProtocolLibAdapter.ENTITY_ID_COUNTER.getAndIncrement();
        this.uuid = UUID.randomUUID();

        spawnFakeEntity();
    }

    private void spawnFakeEntity() {
        PacketContainer spawnPacket = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawnPacket.getIntegers().write(0, entityId);
        spawnPacket.getUUIDs().write(0, uuid);

        spawnPacket.getEntityTypeModifier().write(0, EntityType.TEXT_DISPLAY);

        spawnPacket.getDoubles().write(0, location.getX());
        spawnPacket.getDoubles().write(1, location.getY());
        spawnPacket.getDoubles().write(2, location.getZ());

        sendPacketToViewers(spawnPacket);
    }

    public int getEntityId() { return entityId; }
    
    public Location getLocation() { return location; }

    @Override
    public void setScale(Vector3f scale) { this.scale = scale; }

    @Override
    public void setTranslation(Vector3f translation) { this.translation = translation; }

    @Override
    public void setRotation(Vector3f rotation) { this.rotation = rotation; }

    @Override
    public void setColor(String hexColor) {
        if (hexColor != null && !hexColor.isEmpty()) {
            this.hexColor = hexColor;
        }
    }

    public void setAsTextShape(String shape) {
        this.textShape = shape;
    }
    
    public void setBlockbenchMode(boolean mode) { this.blockbenchMode = mode; }

    public void setRawQuaternion(org.joml.Quaternionf q) { this.rawQuaternion = q; }

    @Override
    public void update() {
        PacketContainer metadataPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        metadataPacket.getIntegers().write(0, entityId);

        org.joml.Quaternionf qRot;
        if (this.rawQuaternion != null) {
            qRot = new org.joml.Quaternionf(this.rawQuaternion); // Gimbal-Lock Free Matrix!
        } else {
            // Legacy Fallback
            qRot = new org.joml.Quaternionf().rotationYXZ(
                    (float) Math.toRadians(rotation.y()), 
                    (float) Math.toRadians(rotation.x()), 
                    (float) Math.toRadians(rotation.z())  
            );
        }

        if (this.cachedDummy == null) {
            this.cachedDummy = location.getWorld().createEntity(location, org.bukkit.entity.TextDisplay.class);
        }
        org.bukkit.entity.TextDisplay dummy = this.cachedDummy;

        org.joml.Vector3f normalizedScale;
        org.joml.Vector3f pivotShift;

        if (this.textShape != null) {
            // Text shapes should NEVER be stretched vertically. Keep raw scale!
            normalizedScale = new org.joml.Vector3f(scale);
            pivotShift = new org.joml.Vector3f(0, 0, 0); // Text centers natively
        } else if (this.blockbenchMode) {
            // PERFECT PIXEL-TO-BLOCK ASPECT RATIO: 
            // 10 spaces = 42px width, 11px height. 0.92f and 3.5f forces height/width to equal exact 1:1 squares!
            // This mechanically forces the planes to extend exactly to the corners and seals all gaps!
            normalizedScale = new org.joml.Vector3f(scale.x * 0.92f, scale.y * 3.5f, scale.z);
            pivotShift = new org.joml.Vector3f(0, scale.y / 2.0f, 0);
        } else {
            // LEGACY PROCEDURAL SHAPES MATRIX
            normalizedScale = new org.joml.Vector3f(scale.x * 0.4f, scale.y * 4.0f, scale.z);
            // Shift the TextDisplay pivot point (bottom-center) down by half the PHYSICAL target height to dead center!
            // THE FIX: We MUST use scale.y (the true block size), NOT the multiplied normalizedScale.y!
            pivotShift = new org.joml.Vector3f(0, scale.y / 2.0f, 0);
        }
        
        pivotShift.rotate(qRot);
        org.joml.Vector3f adjustedTranslation = new org.joml.Vector3f(translation).sub(pivotShift);

        org.bukkit.util.Transformation t = new org.bukkit.util.Transformation(
                adjustedTranslation, qRot, normalizedScale, new org.joml.Quaternionf()
        );
        dummy.setTransformation(t);

        // Billboard setting so it rotates with the player
        dummy.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);

        // 3. THE PINWHEEL FIX: Force Minecraft to draw the shape from the dead center
        dummy.setAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);

        org.bukkit.Color finalColor;
        
        if (this.textShape != null) {
            // TEXT-SHAPE ENGINE: Uses raw text characters colored via ChatColor, absolutely no background!
            dummy.setText(net.md_5.bungee.api.ChatColor.of(this.hexColor) + this.textShape);
            dummy.setDefaultBackground(false);
            finalColor = org.bukkit.Color.fromARGB(0, 0, 0, 0); // Transparent background
        } else {
            // STANDARD GEOMETRY ENGINE
            dummy.setText("          ");
            dummy.setDefaultBackground(false);
            try {
                java.awt.Color javaColor = java.awt.Color.decode(this.hexColor);
                finalColor = org.bukkit.Color.fromARGB(180, javaColor.getRed(), javaColor.getGreen(), javaColor.getBlue());
            } catch (Exception e) {
                finalColor = org.bukkit.Color.fromARGB(180, 255, 255, 255);
            }
        }
        
        // FAST INTERPOLATION: 1 tick instantly locks backpacks and wings to the player's back without dragging!
        dummy.setTeleportDuration(1);
        dummy.setInterpolationDuration(1);
        dummy.setInterpolationDelay(0);

        dummy.setBackgroundColor(finalColor);

        com.comphenix.protocol.wrappers.WrappedDataWatcher watcher = com.comphenix.protocol.wrappers.WrappedDataWatcher.getEntityWatcher(dummy);
        java.util.List<com.comphenix.protocol.wrappers.WrappedDataValue> dataValues = new java.util.ArrayList<>();

        for (com.comphenix.protocol.wrappers.WrappedWatchableObject obj : watcher.getWatchableObjects()) {
            dataValues.add(new com.comphenix.protocol.wrappers.WrappedDataValue(
                    obj.getIndex(),
                    obj.getWatcherObject().getSerializer(),
                    obj.getRawValue()
            ));
        }

        metadataPacket.getDataValueCollectionModifier().write(0, dataValues);
        sendPacketToViewers(metadataPacket);

        // NO MORE dummy.remove(); ! We cache the detached entity to prevent memory leaks and Entity ID exhaustion!
    }

    public void mountToEntity(int targetId) {
        dc.dccosmetics.nms.ProtocolLibAdapter.playerCosmeticPassengers.computeIfAbsent(targetId, k -> new java.util.ArrayList<>());
        if (!dc.dccosmetics.nms.ProtocolLibAdapter.playerCosmeticPassengers.get(targetId).contains(this.entityId)) {
            dc.dccosmetics.nms.ProtocolLibAdapter.playerCosmeticPassengers.get(targetId).add(this.entityId);
        }

        PacketContainer mountPacket = protocolManager.createPacket(PacketType.Play.Server.MOUNT);
        mountPacket.getIntegers().write(0, targetId);
        
        // THE CRITICAL FIX: Send the actual array of passengers, NOT an empty array!
        int[] passArray = dc.dccosmetics.nms.ProtocolLibAdapter.playerCosmeticPassengers.get(targetId).stream().mapToInt(i -> i).toArray();
        mountPacket.getIntegerArrays().write(0, passArray);

        sendPacketToViewers(mountPacket);
    }

    @Override
    public void mount(Player target) {
        mountToEntity(target.getEntityId());
    }

    public void unmountFromEntity(int targetId) {
        dc.dccosmetics.nms.ProtocolLibAdapter.playerCosmeticPassengers.putIfAbsent(targetId, new java.util.ArrayList<>());
        dc.dccosmetics.nms.ProtocolLibAdapter.playerCosmeticPassengers.get(targetId).remove((Integer) this.entityId);
        PacketContainer mountPacket = protocolManager.createPacket(PacketType.Play.Server.MOUNT);
        mountPacket.getIntegers().write(0, targetId);
        int[] passArray = dc.dccosmetics.nms.ProtocolLibAdapter.playerCosmeticPassengers.get(targetId).stream().mapToInt(i -> i).toArray();
        mountPacket.getIntegerArrays().write(0, passArray);
        sendPacketToViewers(mountPacket);
    }

    public void teleport(Location loc) {
        this.location = loc;
        try {
            PacketContainer teleportPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
            
            // 1. Entity ID is always the first integer
            if (teleportPacket.getIntegers().size() > 0) {
                teleportPacket.getIntegers().write(0, entityId);
            }
            
            // 2. Safely write XYZ Coordinates
            if (teleportPacket.getDoubles().size() >= 3) {
                teleportPacket.getDoubles().write(0, loc.getX());
                teleportPacket.getDoubles().write(1, loc.getY());
                teleportPacket.getDoubles().write(2, loc.getZ());
            }
            
            // 3. Safely write Yaw and Pitch
            if (teleportPacket.getBytes().size() >= 2) {
                teleportPacket.getBytes().write(0, (byte) (loc.getYaw() * 256.0F / 360.0F));
                teleportPacket.getBytes().write(1, (byte) (loc.getPitch() * 256.0F / 360.0F));
            }
            
            // 4. Safely write onGround (Fixes the 1.21.2+ crash!)
            if (teleportPacket.getBooleans().size() > 0) {
                teleportPacket.getBooleans().write(0, false);
            }
            
            sendPacketToViewers(teleportPacket);
        } catch (Exception e) {
            logger.severe("[DEBUG-COSMETICS] Error sending ENTITY_TELEPORT packet for entity " + entityId);
            e.printStackTrace();
        }
    }

    @Override
    public void unmount(Player target) {
        unmountFromEntity(target.getEntityId());
    }

    @Override
    public void destroy() {
        PacketContainer destroyPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroyPacket.getIntLists().write(0, List.of(entityId));
        sendPacketToViewers(destroyPacket);
    }

    private void sendPacketToViewers(PacketContainer packet) {
        for (Player viewer : viewers) {
            protocolManager.sendServerPacket(viewer, packet);
        }
    }
}