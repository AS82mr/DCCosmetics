package dc.dccosmetics.manager;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.api.DisplayWrapper;
import dc.dccosmetics.model.CosmeticNode;
import dc.dccosmetics.model.CosmeticTemplate;
import dc.dccosmetics.nms.ProtocolDisplayWrapper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WaypointManager {
    private final Map<UUID, ActiveWaypoint> waypoints = new ConcurrentHashMap<>();
    private final Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();

    // Configurable tuning parameters
    private float forwardDistance = 1.5f;
    private float heightOffset = -0.95f;
    private float globalScale = 0.3f;
    private float hoverSpeed = 0.15f;
    private float hoverAmplitude = 0.04f;
    private float maxPitchDegrees = 60.0f;
    private double arrivalDistance = 3.0;

    public WaypointManager() {
        reloadConfig();
    }

    public void reloadConfig() {
        var mainCfg = DCCosmetics.getInstance().getConfig();
        if (mainCfg.isConfigurationSection("waypoints")) {
            this.forwardDistance = (float) mainCfg.getDouble("waypoints.forward-distance", 1.5);
            this.heightOffset = (float) mainCfg.getDouble("waypoints.height-offset", -0.95);
            this.globalScale = (float) mainCfg.getDouble("waypoints.scale", 0.3);
            this.hoverSpeed = (float) mainCfg.getDouble("waypoints.hover-speed", 0.15);
            this.hoverAmplitude = (float) mainCfg.getDouble("waypoints.hover-amplitude", 0.04);
            this.maxPitchDegrees = (float) mainCfg.getDouble("waypoints.max-pitch", 60.0);
            this.arrivalDistance = mainCfg.getDouble("waypoints.arrival-distance", 3.0);
            return;
        }

        File wpFile = new File(DCCosmetics.getInstance().getDataFolder(), "waypoints.yml");
        if (wpFile.exists()) {
            var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(wpFile);
            this.forwardDistance = (float) cfg.getDouble("waypoints.forward-distance", 1.5);
            this.heightOffset = (float) cfg.getDouble("waypoints.height-offset", -0.95);
            this.globalScale = (float) cfg.getDouble("waypoints.scale", 0.3);
            this.hoverSpeed = (float) cfg.getDouble("waypoints.hover-speed", 0.15);
            this.hoverAmplitude = (float) cfg.getDouble("waypoints.hover-amplitude", 0.04);
            this.maxPitchDegrees = (float) cfg.getDouble("waypoints.max-pitch", 60.0);
            this.arrivalDistance = cfg.getDouble("waypoints.arrival-distance", 3.0);
        }
    }

    public boolean isVisible(Player player) {
        return !hiddenPlayers.contains(player.getUniqueId());
    }

    public boolean toggleVisibility(Player player) {
        UUID u = player.getUniqueId();
        if (hiddenPlayers.contains(u)) {
            hiddenPlayers.remove(u);
            ActiveWaypoint wp = waypoints.get(u);
            if (wp != null) {
                wp.show();
            }
            return true;
        } else {
            hiddenPlayers.add(u);
            ActiveWaypoint wp = waypoints.get(u);
            if (wp != null) {
                wp.hide();
            }
            return false;
        }
    }

    public void setWaypoint(Player player, Location targetLoc) {
        setWaypoint(player, targetLoc, false);
    }

    public void setWaypoint(Player player, Location targetLoc, boolean forceShow) {
        if (player == null || !player.isOnline()) return;
        if (targetLoc == null) {
            clearWaypoint(player, false);
            return;
        }

        UUID u = player.getUniqueId();
        if (forceShow) {
            hiddenPlayers.remove(u);
        }

        boolean isHidden = hiddenPlayers.contains(u);

        ActiveWaypoint existing = waypoints.get(u);
        if (existing != null) {
            existing.setTarget(targetLoc);
            if (!isHidden) {
                existing.show();
            }
            return;
        }

        CosmeticTemplate arrowTemplate = DCCosmetics.getInstance().getTemplateRegistry().getTemplate("arrow");
        if (arrowTemplate == null) {
            DCCosmetics.getInstance().getLogger().warning("[Waypoint] 'arrow' template not found in DCCosmetics!");
            return;
        }

        ActiveWaypoint wp = new ActiveWaypoint(this, player, targetLoc, arrowTemplate);
        wp.start();
        if (isHidden) {
            wp.hide();
        }
        waypoints.put(u, wp);
    }

    public void clearWaypoint(Player player) {
        clearWaypoint(player, false);
    }

    public void clearWaypoint(Player player, boolean animateShrink) {
        if (player == null) return;
        ActiveWaypoint wp = waypoints.get(player.getUniqueId());
        if (wp == null) return;

        if (animateShrink && player.isOnline()) {
            wp.shrinkAndStop(() -> waypoints.remove(player.getUniqueId()));
        } else {
            waypoints.remove(player.getUniqueId());
            wp.stop();
        }
    }

    public void clearAll() {
        for (ActiveWaypoint wp : waypoints.values()) {
            wp.stop();
        }
        waypoints.clear();
    }

    public Location getTarget(Player player) {
        if (player == null) return null;
        ActiveWaypoint wp = waypoints.get(player.getUniqueId());
        return wp != null ? wp.targetLoc : null;
    }

    public String getTargetCoords(Player player) {
        Location loc = getTarget(player);
        if (loc == null) return "&7None";
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    public static class ActiveWaypoint {
        private final WaypointManager manager;
        private final Player player;
        private Location targetLoc;
        private final CosmeticTemplate template;
        private final Map<String, DisplayWrapper> activeNodes = new HashMap<>();
        private BukkitTask task;
        private long tickCounter = 0;
        private boolean isShrinking = false;
        private float currentScaleFactor = 1.0f;
        private boolean isHidden = false;

        public ActiveWaypoint(WaypointManager manager, Player player, Location targetLoc, CosmeticTemplate template) {
            this.manager = manager;
            this.player = player;
            this.targetLoc = targetLoc;
            this.template = template;
        }

        public void setTarget(Location target) {
            this.targetLoc = target;
        }

        public void hide() {
            this.isHidden = true;
            for (DisplayWrapper w : activeNodes.values()) {
                w.setScale(new Vector3f(0.001f, 0.001f, 0.001f));
                w.update();
            }
        }

        public void show() {
            this.isHidden = false;
        }

        public void start() {
            List<Player> viewers = Collections.singletonList(player);
            boolean isBlockbench = template.isBlockbench();

            // Spawn all template nodes
            for (Map.Entry<String, CosmeticNode> entry : template.getNodes().entrySet()) {
                CosmeticNode nodeData = entry.getValue();
                String color = nodeData.getColor() != null && !nodeData.getColor().isEmpty() ? nodeData.getColor() : "#14b8a6";
                Vector3f frontRot = new Vector3f(nodeData.getRotation());
                spawnNode(nodeData, frontRot, color, entry.getKey() + "_front", viewers, isBlockbench);

                if (nodeData.isTwoSided()) {
                    Vector3f backRot = new Vector3f(-nodeData.getRotation().x(), nodeData.getRotation().y() + 180, -nodeData.getRotation().z());
                    spawnNode(nodeData, backRot, color, entry.getKey() + "_back", viewers, isBlockbench);
                }
            }

            // Start tracking loop
            task = Bukkit.getScheduler().runTaskTimer(DCCosmetics.getInstance(), () -> {
                if (!player.isOnline() || !player.isValid()) {
                    stop();
                    return;
                }

                if (isHidden) {
                    return;
                }

                Location pLoc = player.getLocation();
                if (targetLoc == null || !pLoc.getWorld().equals(targetLoc.getWorld())) {
                    for (DisplayWrapper w : activeNodes.values()) {
                        w.setScale(new Vector3f(0.001f, 0.001f, 0.001f));
                        w.update();
                    }
                    return;
                }

                // Check arrival distance for smooth shrinking animation
                double distToTarget = pLoc.distance(targetLoc);
                if (distToTarget <= manager.arrivalDistance && !isShrinking) {
                    shrinkAndStop(() -> manager.clearWaypoint(player, false));
                    return;
                }

                tickCounter++;
                float pYaw = pLoc.getYaw();

                // Forward vector in look direction
                double yawRad = Math.toRadians(pYaw);
                float fwdX = (float) (-Math.sin(yawRad) * manager.forwardDistance);
                float fwdZ = (float) (Math.cos(yawRad) * manager.forwardDistance);
                float fwdY = manager.heightOffset;

                // Gentle floating breath
                float hover = (float) (Math.sin(tickCounter * manager.hoverSpeed) * manager.hoverAmplitude);

                // World coordinate of the arrow center for accurate direction calculation
                Location pEye = player.getEyeLocation();
                Location arrowWorldLoc = pEye.clone().add(fwdX, fwdY + hover, fwdZ);
                double toX = (targetLoc.getX() + 0.5) - arrowWorldLoc.getX();
                double toY = (targetLoc.getY() + 1.2) - arrowWorldLoc.getY();
                double toZ = (targetLoc.getZ() + 0.5) - arrowWorldLoc.getZ();
                double horizDist = Math.sqrt(toX * toX + toZ * toZ);

                float toYaw = (float) Math.toDegrees(Math.atan2(-toX, toZ));
                float toPitch = (float) -Math.toDegrees(Math.atan2(toY, horizDist));
                toPitch = Math.max(-manager.maxPitchDegrees, Math.min(manager.maxPitchDegrees, toPitch));

                // Unified rigid body rotation for the entire arrow
                Quaternionf arrowRotation = new Quaternionf()
                        .rotationY((float) Math.toRadians(-toYaw))
                        .rotateX((float) Math.toRadians(toPitch));

                Vector3f globalOffset = template.getGlobalOffset() != null ? template.getGlobalOffset() : new Vector3f(0, 0, 0);
                float finalScaleMult = manager.globalScale * currentScaleFactor;

                // Passenger mount offset relative to head (~1.8m above player feet)
                float baseY = (1.62f - 1.8f) + fwdY + hover;

                // Update each node as part of the rigid body
                for (Map.Entry<String, DisplayWrapper> e : activeNodes.entrySet()) {
                    String id = e.getKey();
                    DisplayWrapper node = e.getValue();
                    String origId = id.replace("_front", "").replace("_back", "");
                    CosmeticNode origNode = template.getNodes().get(origId);
                    if (origNode == null) continue;

                    boolean isBack = id.endsWith("_back");
                    Quaternionf nodeLocalQ;
                    if (origNode.getOrientation() != null) {
                        nodeLocalQ = new Quaternionf(origNode.getOrientation());
                    } else {
                        nodeLocalQ = new Quaternionf().rotationXYZ(
                                (float) Math.toRadians(origNode.getRotation().x()),
                                (float) Math.toRadians(origNode.getRotation().y()),
                                (float) Math.toRadians(origNode.getRotation().z())
                        );
                    }
                    if (isBack) {
                        nodeLocalQ.rotateLocalY((float) Math.PI);
                    }

                    // 1. Calculate node translation relative to model origin
                    Vector3f nodePos = new Vector3f(origNode.getTranslation()).add(globalOffset);
                    nodePos.mul(template.getGlobalScale());
                    nodePos.mul(finalScaleMult);

                    // 2. Rotate the node offset by the arrow's rigid orientation
                    nodePos.rotate(arrowRotation);

                    // 3. Compose world-aligned offset relative to passenger mount point
                    Vector3f totalTrans = new Vector3f(fwdX, baseY, fwdZ).add(nodePos);

                    // 4. Final node orientation = arrowRotation * nodeLocalQ
                    Quaternionf finalQ = new Quaternionf(arrowRotation).mul(nodeLocalQ);

                    node.setTranslation(totalTrans);
                    if (node instanceof ProtocolDisplayWrapper pNode) {
                        pNode.setRawQuaternion(finalQ);
                    }
                    node.setScale(new Vector3f(origNode.getScale()).mul(template.getGlobalScale()).mul(finalScaleMult));
                    node.update();
                }
            }, 0L, 1L);
        }

        public void shrinkAndStop(Runnable onComplete) {
            if (isShrinking) return;
            isShrinking = true;

            player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 0.8f, 1.8f);

            new BukkitRunnable() {
                int step = 0;
                final int totalSteps = 8;

                @Override
                public void run() {
                    step++;
                    currentScaleFactor = Math.max(0.001f, 1.0f - ((float) step / totalSteps));

                    if (step >= totalSteps) {
                        cancel();
                        stop();
                        if (onComplete != null) onComplete.run();
                    }
                }
            }.runTaskTimer(DCCosmetics.getInstance(), 0L, 1L);
        }

        private void spawnNode(CosmeticNode nodeData, Vector3f rot, String color, String id, List<Player> viewers, boolean isBlockbench) {
            var packetAdapter = DCCosmetics.getInstance().getPacketAdapter();
            Location spawnLoc = player.getLocation();
            DisplayWrapper nodeDisplay = packetAdapter.createBlockDisplay(viewers, spawnLoc);
            if (nodeDisplay instanceof ProtocolDisplayWrapper pNode) {
                pNode.setBlockbenchMode(isBlockbench);
                pNode.setOpacity(nodeData.getOpacity());

                // Mount to player with 2-tick delay to prevent packet race condition
                Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
                    if (!player.isValid() || !player.isOnline()) return;
                    pNode.mountToEntity(player.getEntityId());
                }, 2L);
            }
            nodeDisplay.setScale(new Vector3f(nodeData.getScale()).mul(template.getGlobalScale()).mul(manager.globalScale));
            nodeDisplay.setColor(color);
            nodeDisplay.update();

            activeNodes.put(id, nodeDisplay);
        }

        public void stop() {
            if (task != null) task.cancel();
            for (DisplayWrapper node : activeNodes.values()) {
                if (node instanceof ProtocolDisplayWrapper pNode) {
                    pNode.unmountFromEntity(player.getEntityId());
                }
                node.destroy();
            }
            activeNodes.clear();
        }
    }
}
