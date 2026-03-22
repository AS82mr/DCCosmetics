package dc.dccosmetics.model;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.api.DisplayWrapper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActiveCosmetic {
    private final LivingEntity owner;
    private final CosmeticTemplate template;
    private final String colorHex;

    private final Map<String, DisplayWrapper> activeNodes = new HashMap<>();
    private final List<Player> viewers = new java.util.concurrent.CopyOnWriteArrayList<>();
    private BukkitTask trackingTask;

    private float currentYaw = 0;
    private long tickCounter = 0;
    private Location lastWalkLoc = null;
    private float proceduralWalkPhase = 0f;
    private float wingFlapPhase = 0f;
    private float currentBodyYaw = 0f;
    private int stationaryTicks = 0;
    public static boolean DEBUG_BOOTS = false;
    public static boolean DEBUG_BB = false;

    public ActiveCosmetic(LivingEntity owner, CosmeticTemplate template, String colorHex) {
        this.owner = owner;
        this.template = template;
        this.colorHex = colorHex;
    }

    public void spawn() {
        if (template == null) return;
        viewers.addAll(owner.getWorld().getPlayers());

        currentBodyYaw = owner.getLocation().getYaw();

        boolean isBoots = "boots".equalsIgnoreCase(template.getEquipmentSlot().trim());
        boolean isChest = "chest".equalsIgnoreCase(template.getEquipmentSlot().trim());
        boolean isWaist = "waist".equalsIgnoreCase(template.getEquipmentSlot().trim());
        boolean isHead = "head".equalsIgnoreCase(template.getEquipmentSlot().trim());
        boolean isVanillaHead = isHead && !DCCosmetics.getInstance().getTemplateRegistry().isCustomHeadTracking(template.getId());
        boolean isBlockbench = template.isBlockbench();



        for (Map.Entry<String, CosmeticNode> entry : template.getNodes().entrySet()) {
            CosmeticNode nodeData = entry.getValue();

            String finalColor = this.colorHex;
            if (nodeData.getColor() != null && !nodeData.getColor().trim().isEmpty()) {
                finalColor = nodeData.getColor();
            }

            Vector3f frontRot = new Vector3f(nodeData.getRotation());
            spawnNode(nodeData, frontRot, finalColor, entry.getKey() + "_front", isVanillaHead, isBlockbench);

            // THE FIX: FORCE TWO-SIDED HOLOS ON EVERYTHING! 
            // This paints the inside walls of the 3D cubes so they are perfectly solid from all angles, never paper-thin!
            Vector3f backRot = new Vector3f(-nodeData.getRotation().x(), nodeData.getRotation().y() + 180, -nodeData.getRotation().z());
            spawnNode(nodeData, backRot, finalColor, entry.getKey() + "_back", isVanillaHead, isBlockbench);
        }

        startTracking(isBoots, isChest, isWaist, isHead, isVanillaHead);
    }

    private void spawnNode(CosmeticNode nodeData, Vector3f rot, String color, String id, boolean isVanillaHead, boolean isBlockbench) {
        var packetAdapter = DCCosmetics.getInstance().getPacketAdapter();
        DisplayWrapper nodeDisplay = packetAdapter.createBlockDisplay(viewers, owner.getLocation());

        if (nodeDisplay instanceof dc.dccosmetics.nms.ProtocolDisplayWrapper pNode) {
            pNode.setBlockbenchMode(isBlockbench);
        }

        nodeDisplay.setScale(nodeData.getScale());

        Vector3f finalTranslation = new Vector3f(nodeData.getTranslation());
        finalTranslation.add(template.getGlobalOffset()); // Fully respect user's YAML offsets!
        nodeDisplay.setTranslation(finalTranslation);

        nodeDisplay.setRotation(rot);
        nodeDisplay.setColor(color);
        nodeDisplay.update();

        if (nodeDisplay instanceof dc.dccosmetics.nms.ProtocolDisplayWrapper pNode) {
            // DELAY THE MOUNT! Solves the vanishing packet race condition instantly!
            Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
                if (!owner.isValid()) return;
                pNode.mountToEntity(owner.getEntityId()); // NO MORE ANCHOR! All cosmetics ride the player to prevent chunk culling completely!
            }, 2L);
        }

        activeNodes.put(id, nodeDisplay);
    }

    private void startTracking(boolean isBoots, boolean isChest, boolean isWaist, boolean isHead, boolean isVanillaHead) {
        boolean hasNodeAnim = template.getNodes().values().stream().anyMatch(CosmeticNode::isAnimated);
        boolean isDummy = DCCosmetics.getInstance().getSculptManager().getActiveDummies().containsValue(owner);
        boolean isBlockbench = template.isBlockbench();

        // Synchronous loop because we are cleanly teleporting a Bukkit Anchor (0% CPU impact)
        trackingTask = Bukkit.getScheduler().runTaskTimer(DCCosmetics.getInstance(), () -> {
            if (!owner.isValid()) {
                despawn();
                return;
            }

            tickCounter++;
            Location pLoc = owner.getLocation();

            float pYaw = pLoc.getYaw() % 360;
            if (pYaw < 0) pYaw += 360;

            // --- FLIGHT DISABLER ---
            boolean isHidden = (owner instanceof Player p) && (p.isFlying() || p.isGliding() || (!p.isOnGround() && p.getVelocity().lengthSquared() > 0.8));

            // --- 1. PROCEDURAL WALK ENGINE ---
            float legSwing = 0f;
            double dist = (lastWalkLoc != null && pLoc.getWorld().equals(lastWalkLoc.getWorld())) ? pLoc.distance(lastWalkLoc) : 0;

            if (isBoots) {
                if (dist > 0.02 && owner.isOnGround()) {
                    proceduralWalkPhase += (float) (dist * 4.0f);
                } else {
                    float nearestZero = (float) (Math.round(proceduralWalkPhase / Math.PI) * Math.PI);
                    if (Math.abs(nearestZero - proceduralWalkPhase) > 0.01f) {
                        proceduralWalkPhase += (nearestZero - proceduralWalkPhase) * 0.4f;
                    }
                }
                legSwing = (float) Math.sin(proceduralWalkPhase);
            }

            // --- 2. BODY YAW SIMULATION (Prevents Turntable Spinning) ---
            if (isBlockbench) {
                // UNIFIED RIGID BODY: Blockbench items do not drift or delay! They lock perfectly to the camera!
                currentBodyYaw = pYaw;
            } else {
                // Legacy smooth shoulder tracking for procedural shapes
                if (dist > 0.02) {
                    float diff = (pYaw - currentBodyYaw) % 360;
                    if (diff < -180) diff += 360;
                    if (diff > 180) diff -= 360;
                    currentBodyYaw += diff * 0.4f;
                    stationaryTicks = 0;
                } else {
                    stationaryTicks++;
                    float yawDiff = (pYaw - currentBodyYaw) % 360;
                    if (yawDiff < -180) yawDiff += 360;
                    if (yawDiff > 180) yawDiff -= 360;
                    if (Math.abs(yawDiff) > 50) {
                        float targetBodyYaw = pYaw - (yawDiff > 0 ? 50 : -50);
                        float catchUp = (targetBodyYaw - currentBodyYaw) % 360;
                        if (catchUp < -180) catchUp += 360;
                        if (catchUp > 180) catchUp -= 360;
                        currentBodyYaw += catchUp * 0.2f;
                    }
                }
            }
            currentBodyYaw %= 360;
            if (currentBodyYaw < 0) currentBodyYaw += 360;
            if (isDummy) currentBodyYaw = pLoc.getYaw();

            // --- 3. PROCEDURAL WING ENGINE ---
            if (isChest) {
                float yVel = (float) owner.getVelocity().getY();
                boolean isFalling = !owner.isOnGround() && yVel < -0.1;
                boolean isJumping = !owner.isOnGround() && yVel > 0.1;
                float targetWingFlap;

                if (isJumping) targetWingFlap = 1.0f;
                else if (isFalling) targetWingFlap = 0.6f + (float) (Math.sin(tickCounter * 0.4) * 0.4f);
                else if (owner instanceof Player p && p.isSprinting()) targetWingFlap = 0.3f + (float) (Math.sin(tickCounter * 0.8) * 0.3f);
                else targetWingFlap = (float) (Math.sin(tickCounter * 0.1) * 0.1f);

                wingFlapPhase += (targetWingFlap - wingFlapPhase) * 0.2f;
            }

            lastWalkLoc = pLoc;

            // --- 4. CALCULATE LOCAL MOUNT TRANSLATION ---
            float targetY = 0f;
            if (isHead) targetY = 1.6f;
            else if (isChest) targetY = 1.1f;
            else if (isWaist) targetY = 0.7f;
            else if (isBoots) targetY = 0.0f; // PERFECTLY ON THE FLOOR

            float mountOffset = (owner instanceof Player) ? 1.8f : 1.975f; // Push down from head to feet natively!
            float sneakCompensation = (owner instanceof Player p && p.isSneaking()) ? 0.3f : 0f; // Keep on floor when shifting
            float verticalTranslation = targetY - mountOffset + sneakCompensation;
            float pitchOffset = isHead ? pLoc.getPitch() : 0f;

            if (DEBUG_BOOTS && tickCounter % 20 == 0) {
                DCCosmetics.getInstance().getLogger().info("[DEBUG-COSMETICS] " + template.getId() + " (" + template.getEquipmentSlot() + ") is running.");
                DCCosmetics.getInstance().getLogger().info("  -> Applied Vertical Drop: " + verticalTranslation + " (Sneak: " + sneakCompensation + ")");
            }

            // Calculate global animation offsets if it's blockbench
            Vector3f globalAnimTrans = new Vector3f();
            float globalAnimYaw = 0f;

            if (isBlockbench && template.isAnimated()) {
                String type = template.getAnimationType();
                float speed = template.getAnimationSpeed();
                if ("float".equalsIgnoreCase(type)) {
                    globalAnimTrans.add(0, (float) Math.sin(tickCounter * speed * 0.1f) * 0.2f, 0);
                } else if ("glitch".equalsIgnoreCase(type)) {
                    if (Math.random() < 0.1) {
                        globalAnimTrans.add((float)(Math.random() * 0.2 - 0.1), (float)(Math.random() * 0.2 - 0.1), (float)(Math.random() * 0.2 - 0.1));
                    }
                } else if ("spin".equalsIgnoreCase(type)) {
                    globalAnimYaw = (tickCounter * speed) % 360;
                }
            } else if (template.isAnimated()) {
                // Legacy support for non-blockbench global spin
                globalAnimYaw = (tickCounter * template.getAnimationSpeed()) % 360;
            }

            for (Map.Entry<String, DisplayWrapper> entry : activeNodes.entrySet()) {
                String id = entry.getKey();
                DisplayWrapper node = entry.getValue();

                String originalId = id.replace("_front", "").replace("_back", "");
                CosmeticNode originalNode = template.getNodes().get(originalId);

                if (originalNode != null) {
                    Vector3f newRot = new Vector3f(originalNode.getRotation());
                    Vector3f newTrans = new Vector3f(originalNode.getTranslation());
                    
                    if (isBlockbench) {
                        // Apply global unified Blockbench animation translations FIRST, before orbiting!
                        newTrans.add(globalAnimTrans);
                    }

                    // Fully respect user's YAML global-offset! Allows fixing sunk boots via config!
                    newTrans.add(template.getGlobalOffset());
                    
                    // Apply master vertical push to bring it from the mount point down to the correct slot!
                    newTrans.add(0, verticalTranslation, 0);
                    boolean isBack = id.endsWith("_back");

                    // Use 0.001f instead of 0 to prevent the client from breaking the matrix and despawning it permanently!
                    if (isHidden && !isDummy) {
                        node.setScale(new Vector3f(0.001f, 0.001f, 0.001f));
                    } else {
                        node.setScale(originalNode.getScale());
                    }

                    if (!isBlockbench) {
                        // --- 5. LOCAL PROCEDURAL ANIMATIONS (Apply before global orbit) ---
                        if (isBoots && originalId.toLowerCase().contains("left")) {
                        newTrans.add(0, Math.max(0, legSwing * 0.2f), legSwing * 0.35f);
                    } else if (isBoots && originalId.toLowerCase().contains("right")) {
                        newTrans.add(0, Math.max(0, -legSwing * 0.2f), -legSwing * 0.35f);
                    }

                    if (isChest && originalId.toLowerCase().contains("wing_left")) {
                        newRot.add(0, -wingFlapPhase * 40f, wingFlapPhase * 20f);
                    } else if (isChest && originalId.toLowerCase().contains("wing_right")) {
                        newRot.add(0, wingFlapPhase * 40f, -wingFlapPhase * 20f);
                    }
                    }

                    if (isHead && !isVanillaHead) {
                        newRot.add(pitchOffset, 0, 0); // Allow custom hats to look up and down naturally!
                    }

                    // --- 6. APPLY GLOBAL ROTATION ORBIT ---
                    // Players do not sync yaw to passengers natively, so we must matrix-orbit the components!
                    // THE FIX: Blockbench items bypass the delayed "Body Yaw" catchup so they feel perfectly rigid and static!
                    float targetYaw = (isHead || isBlockbench) ? pLoc.getYaw() : currentBodyYaw;
                    
                    // THE FIX: If the global model is spinning, we MUST orbit the translation as well, 
                    // otherwise the nodes will spin in place and tear the box apart!
                    float globalOrbitYaw = targetYaw + globalAnimYaw;
                    newTrans.rotateY((float) Math.toRadians(-globalOrbitYaw));

                    // THE FIX: We MUST subtract the yaw instead of adding it. 
                    // JOML rotates CCW, but Minecraft Yaw rotates CW. Subtracting ensures translation and rotation move together!
                    if (isBlockbench) {
                        if (isBack) newRot.set(-newRot.x, newRot.y - globalOrbitYaw + 180, -newRot.z);
                        else newRot.add(0, -globalOrbitYaw, 0);
                    } else if (template.isAnimated()) {
                        if (isBack) newRot.set(-newRot.x, newRot.y - globalOrbitYaw + 180, -newRot.z);
                        else newRot.add(0, -globalOrbitYaw, 0);
                    } else if (originalNode.isAnimated()) {
                        String type = originalNode.getAnimationType();
                        float speed = originalNode.getAnimationSpeed();

                        if ("spin".equalsIgnoreCase(type)) {
                            float nodeYaw = (tickCounter * speed) % 360;
                            if (isBack) newRot.set(-newRot.x, newRot.y - nodeYaw - targetYaw + 180, -newRot.z);
                            else newRot.add(0, -nodeYaw - targetYaw, 0);
                        } else if ("float".equalsIgnoreCase(type)) {
                            float offset = (float) Math.sin(tickCounter * speed * 0.1f) * 0.2f;
                            newTrans.add(0, offset, 0);
                            if (isBack) newRot.set(-newRot.x, newRot.y - targetYaw + 180, -newRot.z);
                            else newRot.add(0, -targetYaw, 0);
                        } else if ("glitch".equalsIgnoreCase(type)) {
                            if (Math.random() < 0.1) {
                                newTrans.add((float)(Math.random() * 0.2 - 0.1), (float)(Math.random() * 0.2 - 0.1), (float)(Math.random() * 0.2 - 0.1));
                            }
                            if (isBack) newRot.set(-newRot.x, newRot.y - targetYaw + 180, -newRot.z);
                            else newRot.add(0, -targetYaw, 0);
                        }
                    } else {
                        if (isBack) newRot.set(-newRot.x, newRot.y - targetYaw + 180, -newRot.z);
                        else newRot.add(0, -targetYaw, 0);
                    }

                    node.setRotation(newRot);
                    node.setTranslation(newTrans);
                    node.update();

                    // 3D SKELETON DETECTOR: Spawns particles at the mathematical center of every face!
                    if (DEBUG_BB && isBlockbench && tickCounter % 5 == 0 && !isBack) {
                        Location particleLoc = pLoc.clone().add(newTrans.x, newTrans.y, newTrans.z);
                        owner.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);
                    }
                }
            }
        }, 0L, 1L);
    }

    public void despawn() {
        if (trackingTask != null) trackingTask.cancel();
        for (DisplayWrapper node : activeNodes.values()) {
            if (node instanceof dc.dccosmetics.nms.ProtocolDisplayWrapper pNode) {
                pNode.unmountFromEntity(owner.getEntityId());
            }
            node.destroy();
        }
        activeNodes.clear();
    }

    public CosmeticTemplate getTemplate() { return template; }
}