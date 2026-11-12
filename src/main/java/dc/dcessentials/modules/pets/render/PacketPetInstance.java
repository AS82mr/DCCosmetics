package dc.dcessentials.modules.pets.render;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.nms.ProtocolLibAdapter;
import dc.dccosmetics.util.HeadUtil;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.modules.items.model.ItemRarity;
import dc.dcessentials.modules.pets.PetManager;
import dc.dcessentials.modules.pets.model.PetSpec;
import dc.dcessentials.modules.pets.model.PetVisibilityMode;
import dc.dcessentials.modules.pets.model.PlayerPetData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hypixel-style client-side floating skull & nameplate pet renderer using ProtocolLib 1.21.11.
 * Zero server-side entity tick cost, zero collision, and per-viewer visibility filtering (ALL / OWN_ONLY / HIDDEN).
 */
public class PacketPetInstance {

    private static final double VIEW_DISTANCE_SQ = 48.0 * 48.0;

    private final UUID ownerUuid;
    private final PetSpec spec;
    private int level;
    private ItemRarity rarity;

    private final int headEntityId;
    private final UUID headEntityUuid;
    private final int labelEntityId;
    private final UUID labelEntityUuid;

    private final ItemStack headStack;
    private Location currentLocation;
    private Location labelLocation;
    private long tickCounter = 0;

    private final Set<UUID> activeViewers = ConcurrentHashMap.newKeySet();

    public PacketPetInstance(Player owner, PetSpec spec, int level, ItemRarity rarity) {
        this.ownerUuid = owner.getUniqueId();
        this.spec = spec;
        this.level = level;
        this.rarity = rarity != null ? rarity : spec.getRarity();

        this.headEntityId = ProtocolLibAdapter.ENTITY_ID_COUNTER.getAndIncrement();
        this.headEntityUuid = UUID.randomUUID();
        this.labelEntityId = ProtocolLibAdapter.ENTITY_ID_COUNTER.getAndIncrement();
        this.labelEntityUuid = UUID.randomUUID();

        this.headStack = HeadUtil.getCustomHead(spec.getHeadBase64());
        this.currentLocation = computeTargetLocation(owner, 0);
        this.labelLocation = this.currentLocation.clone().add(0, 0.48, 0);
    }

    public PetSpec getSpec() { return spec; }
    public int getLevel() { return level; }
    public ItemRarity getRarity() { return rarity; }
    public Location getCurrentLocation() { return currentLocation != null ? currentLocation.clone() : null; }

    public void updateLevelAndRarity(Player owner, int newLevel, ItemRarity newRarity) {
        this.level = newLevel;
        if (newRarity != null) this.rarity = newRarity;
        if (owner != null && owner.isOnline() && currentLocation != null) {
            PacketContainer labelMeta = buildLabelMetadataPacket(owner, currentLocation.getWorld());
            if (labelMeta != null) {
                for (UUID vUuid : activeViewers) {
                    Player viewer = Bukkit.getPlayer(vUuid);
                    if (viewer != null && viewer.isOnline()) {
                        ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, labelMeta);
                    }
                }
            }
        }
    }

    public void tick(Player owner, PetManager petManager) {
        if (owner == null || !owner.isOnline() || owner.isDead()) {
            destroyForAll();
            return;
        }

        tickCounter++;
        Location targetHead = computeTargetLocation(owner, tickCounter);

        if (currentLocation == null || !Objects.equals(currentLocation.getWorld(), targetHead.getWorld())
                || currentLocation.distanceSquared(targetHead) > 64.0) {
            // Instant snap on world change or teleport
            destroyForAll();
            currentLocation = targetHead.clone();
        } else {
            // Smooth spring-lerp follow + face movement or owner yaw
            double lerp = 0.35;
            double nx = currentLocation.getX() + (targetHead.getX() - currentLocation.getX()) * lerp;
            double ny = currentLocation.getY() + (targetHead.getY() - currentLocation.getY()) * lerp;
            double nz = currentLocation.getZ() + (targetHead.getZ() - currentLocation.getZ()) * lerp;
            currentLocation.setX(nx);
            currentLocation.setY(ny);
            currentLocation.setZ(nz);
            currentLocation.setYaw(targetHead.getYaw());
            currentLocation.setPitch(targetHead.getPitch());
        }

        labelLocation = currentLocation.clone().add(0, 0.46, 0);

        // Reconcile viewers according to distance, world, and each viewer's PetVisibilityMode
        Set<UUID> eligibleNow = new HashSet<>();
        World world = owner.getWorld();
        for (Player candidate : world.getPlayers()) {
            if (!candidate.isOnline()) continue;
            if (candidate.getLocation().distanceSquared(currentLocation) > VIEW_DISTANCE_SQ) continue;
            if (!shouldViewerSee(candidate, petManager)) continue;
            eligibleNow.add(candidate.getUniqueId());
        }

        // Remove viewers who left range, changed world, or toggled visibility off
        Iterator<UUID> it = activeViewers.iterator();
        while (it.hasNext()) {
            UUID vUuid = it.next();
            if (!eligibleNow.contains(vUuid)) {
                it.remove();
                Player viewer = Bukkit.getPlayer(vUuid);
                if (viewer != null && viewer.isOnline()) {
                    sendDestroyPacket(viewer);
                }
            }
        }

        // Spawn for newly eligible viewers
        for (UUID vUuid : eligibleNow) {
            if (!activeViewers.contains(vUuid)) {
                Player viewer = Bukkit.getPlayer(vUuid);
                if (viewer != null && viewer.isOnline()) {
                    activeViewers.add(vUuid);
                    sendSpawnAndMetadataPackets(viewer, owner, world);
                }
            }
        }

        // Broadcast smooth teleport update to all active viewers
        if (!activeViewers.isEmpty()) {
            PacketContainer tpHead = buildTeleportPacket(headEntityId, currentLocation);
            PacketContainer tpLabel = buildTeleportPacket(labelEntityId, labelLocation);
            for (UUID vUuid : activeViewers) {
                Player viewer = Bukkit.getPlayer(vUuid);
                if (viewer != null && viewer.isOnline()) {
                    if (tpHead != null) ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, tpHead);
                    if (tpLabel != null) ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, tpLabel);
                }
            }

            // Ambient particle every 6 ticks (~0.3s) sent only to active viewers
            if (tickCounter % 6 == 0 && spec.getAmbientParticle() != null) {
                try {
                    Particle p = Particle.valueOf(spec.getAmbientParticle().toUpperCase());
                    Location pLoc = currentLocation.clone().add(0, 0.05, 0);
                    for (UUID vUuid : activeViewers) {
                        Player viewer = Bukkit.getPlayer(vUuid);
                        if (viewer != null && viewer.isOnline()) {
                            viewer.spawnParticle(p, pLoc, 1, 0.14, 0.14, 0.14, 0.01);
                        }
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private boolean shouldViewerSee(Player viewer, PetManager petManager) {
        if (petManager == null) return true;
        PlayerPetData vData = petManager.getData(viewer.getUniqueId());
        PetVisibilityMode mode = vData != null ? vData.getVisibilityMode() : PetVisibilityMode.ALL;
        return switch (mode) {
            case ALL -> true;
            case OWN_ONLY -> viewer.getUniqueId().equals(ownerUuid);
            case HIDDEN -> false;
        };
    }

    private Location computeTargetLocation(Player owner, long tick) {
        Location base = owner.getLocation();
        float yawRad = (float) Math.toRadians(base.getYaw());

        // Forward and right horizontal vectors from owner's body yaw
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = -forwardZ;
        double rightZ = forwardX;

        double bob = Math.sin(tick * 0.18) * 0.11;
        double dx = (forwardX * spec.getOffsetForward()) + (rightX * spec.getOffsetRight());
        double dz = (forwardZ * spec.getOffsetForward()) + (rightZ * spec.getOffsetRight());
        double dy = 1.05 + spec.getOffsetUp() + bob;

        Location target = base.clone().add(dx, dy, dz);
        target.setYaw(base.getYaw());
        target.setPitch(0f);
        return target;
    }

    private void sendSpawnAndMetadataPackets(Player viewer, Player owner, World world) {
        try {
            // 1. Spawn Head ITEM_DISPLAY
            PacketContainer spawnHead = buildSpawnPacket(headEntityId, headEntityUuid, EntityType.ITEM_DISPLAY, currentLocation);
            PacketContainer metaHead = buildHeadMetadataPacket(world);

            // 2. Spawn Label TEXT_DISPLAY
            PacketContainer spawnLabel = buildSpawnPacket(labelEntityId, labelEntityUuid, EntityType.TEXT_DISPLAY, labelLocation);
            PacketContainer metaLabel = buildLabelMetadataPacket(owner, world);

            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, spawnHead);
            if (metaHead != null) ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, metaHead);
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, spawnLabel);
            if (metaLabel != null) ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, metaLabel);
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[DCEPet] Error spawning packet pet: " + e.getMessage());
        }
    }

    private PacketContainer buildSpawnPacket(int entityId, UUID uuid, EntityType type, Location loc) {
        PacketContainer spawn = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawn.getIntegers().write(0, entityId);
        spawn.getUUIDs().write(0, uuid);
        spawn.getEntityTypeModifier().write(0, type);
        spawn.getDoubles()
                .write(0, loc.getX())
                .write(1, loc.getY())
                .write(2, loc.getZ());
        // MC 1.21.9+ / Paper 26.2 encodes ClientboundAddEntityPacket velocity via LpVec3; leaving Vec3 null crashes Netty encoder.
        if (spawn.getVectors().size() > 0) {
            spawn.getVectors().write(0, new Vector(0, 0, 0));
        }
        if (spawn.getBytes().size() >= 2) {
            spawn.getBytes().write(0, (byte) (loc.getPitch() * 256.0F / 360.0F));
            spawn.getBytes().write(1, (byte) (loc.getYaw() * 256.0F / 360.0F));
            if (spawn.getBytes().size() >= 3) {
                spawn.getBytes().write(2, (byte) (loc.getYaw() * 256.0F / 360.0F));
            }
        }
        return spawn;
    }

    private PacketContainer buildHeadMetadataPacket(World world) {
        try {
            ItemDisplay dummy = world.createEntity(currentLocation, ItemDisplay.class);
            dummy.setItemStack(headStack);
            dummy.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
            dummy.setBillboard(Display.Billboard.FIXED);
            dummy.setTeleportDuration(2);
            dummy.setInterpolationDuration(2);
            float s = spec.getHeadScale();
            dummy.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(),
                    new Vector3f(s, s, s),
                    new Quaternionf()
            ));

            PacketContainer meta = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_METADATA);
            meta.getIntegers().write(0, headEntityId);
            List<WrappedDataValue> values = extractDataValues(dummy);
            meta.getDataValueCollectionModifier().write(0, values);
            try { dummy.remove(); } catch (Throwable ignored) {}
            return meta;
        } catch (Throwable t) {
            return null;
        }
    }

    private PacketContainer buildLabelMetadataPacket(Player owner, World world) {
        try {
            TextDisplay dummy = world.createEntity(labelLocation, TextDisplay.class);
            String rarityColor = rarity != null ? rarity.getColorHex() : "&d";
            String line1 = rarityColor + "✦ " + spec.getDisplayName() + " &8· &7Lv " + level;
            String line2 = "&7" + owner.getName() + "'s Companion";
            dummy.text(ColorUtil.toItemComponent(line1 + "\n" + line2));
            dummy.setBillboard(Display.Billboard.CENTER);
            dummy.setBackgroundColor(Color.fromARGB(90, 0, 0, 0));
            dummy.setShadowed(true);
            dummy.setSeeThrough(false);
            dummy.setTeleportDuration(2);
            dummy.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(),
                    new Vector3f(0.82f, 0.82f, 0.82f),
                    new Quaternionf()
            ));

            PacketContainer meta = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_METADATA);
            meta.getIntegers().write(0, labelEntityId);
            List<WrappedDataValue> values = extractDataValues(dummy);
            meta.getDataValueCollectionModifier().write(0, values);
            try { dummy.remove(); } catch (Throwable ignored) {}
            return meta;
        } catch (Throwable t) {
            return null;
        }
    }

    private List<WrappedDataValue> extractDataValues(org.bukkit.entity.Entity dummy) {
        WrappedDataWatcher watcher = WrappedDataWatcher.getEntityWatcher(dummy);
        List<WrappedDataValue> dataValues = new ArrayList<>();
        for (WrappedWatchableObject entry : watcher.getWatchableObjects()) {
            if (entry == null) continue;
            WrappedDataWatcher.WrappedDataWatcherObject obj = entry.getWatcherObject();
            dataValues.add(new WrappedDataValue(obj.getIndex(), obj.getSerializer(), entry.getRawValue()));
        }
        return dataValues;
    }

    private PacketContainer buildTeleportPacket(int entityId, Location loc) {
        try {
            PacketContainer tp = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
            tp.getIntegers().write(0, entityId);

            if (tp.getDoubles().size() >= 3) {
                tp.getDoubles()
                        .write(0, loc.getX())
                        .write(1, loc.getY())
                        .write(2, loc.getZ());
                if (tp.getBytes().size() >= 2) {
                    tp.getBytes()
                            .write(0, (byte) (loc.getYaw() * 256.0F / 360.0F))
                            .write(1, (byte) (loc.getPitch() * 256.0F / 360.0F));
                }
            } else {
                boolean written = false;
                try {
                    Class<?> wrapClass = Class.forName("com.comphenix.protocol.wrappers.WrappedPositionMoveRotation");
                    var createMethod = wrapClass.getMethod(
                            "create", Vector.class, Vector.class, float.class, float.class
                    );
                    Object wrapped = createMethod.invoke(
                            null,
                            new Vector(loc.getX(), loc.getY(), loc.getZ()),
                            new Vector(0, 0, 0),
                            loc.getYaw(),
                            loc.getPitch()
                    );
                    Object handle = wrapClass.getMethod("getHandle").invoke(wrapped);
                    if (handle != null && tp.getModifier().size() > 1) {
                        tp.getModifier().write(1, handle);
                        written = true;
                    }
                } catch (Throwable ignored) {}

                if (!written && tp.getStructures().size() >= 1) {
                    InternalStructure posStruct = tp.getStructures().read(0);
                    if (posStruct != null) {
                        if (posStruct.getVectors().size() >= 2) {
                            posStruct.getVectors().write(0, new Vector(loc.getX(), loc.getY(), loc.getZ()));
                            posStruct.getVectors().write(1, new Vector(0, 0, 0));
                        }
                        if (posStruct.getFloat().size() >= 2) {
                            posStruct.getFloat().write(0, loc.getYaw());
                            posStruct.getFloat().write(1, loc.getPitch());
                        }
                        tp.getStructures().write(0, posStruct);
                    }
                }
            }
            if (tp.getBooleans().size() >= 1) {
                tp.getBooleans().write(0, false);
            }
            return tp;
        } catch (Throwable t) {
            return null;
        }
    }

    public void sendDestroyPacket(Player viewer) {
        try {
            PacketContainer destroy = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroy.getIntLists().write(0, List.of(headEntityId, labelEntityId));
            ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, destroy);
        } catch (Exception ignored) {}
    }

    public void destroyForAll() {
        for (UUID vUuid : activeViewers) {
            Player viewer = Bukkit.getPlayer(vUuid);
            if (viewer != null && viewer.isOnline()) {
                sendDestroyPacket(viewer);
            }
        }
        activeViewers.clear();
    }
}
