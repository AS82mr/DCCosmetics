package dc.dccosmetics.listener;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.api.DisplayWrapper;
import dc.dccosmetics.model.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.ChatColor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

record HitNode(dc.dccosmetics.api.DisplayWrapper display, org.joml.Vector3f baseScale, org.joml.Vector3f currentTrans, org.joml.Vector3f velocity, org.joml.Quaternionf baseRot, org.joml.Vector3f rotSpeed, int delay, double baseOpacity) {}

public class PlayerListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Load their saved cosmetics from the files!
        // (This includes our 2-second crash-loop protection delay)
        DCCosmetics.getInstance().getProfileManager().loadProfile(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Save their equipped cosmetics and despawn the holograms
        DCCosmetics.getInstance().getProfileManager().unloadProfile(event.getPlayer());

        // Cleanup the tracking map to prevent memory leaks
        dc.dccosmetics.nms.ProtocolLibAdapter.playerCosmeticPassengers.remove(event.getPlayer().getEntityId());
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        DCCosmetics.getInstance().getProfileManager().unloadProfile(player);
        DCCosmetics.getInstance().getProfileManager().loadProfile(player);
    }

    private boolean isCompatible(String slot, Material mat) {
        String name = mat.name();
        switch (slot.toLowerCase()) {
            case "head": return name.endsWith("_HELMET") || name.endsWith("_SKULL") || name.endsWith("_HEAD") || mat == Material.CARVED_PUMPKIN;
            case "chest": return name.endsWith("_CHESTPLATE") || mat == Material.ELYTRA;
            case "waist": return name.endsWith("_LEGGINGS");
            case "boots": return name.endsWith("_BOOTS");
            case "sword": return name.endsWith("_SWORD") || name.endsWith("_AXE") || mat == Material.MACE || mat == Material.TRIDENT || mat == Material.BOW || mat == Material.CROSSBOW;
            default: return true;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getCursor() == null || event.getCurrentItem() == null) return;
        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();

        if (cursor.getType() == Material.AIR || clicked.getType() == Material.AIR) return;

        if (cursor.hasItemMeta()) {
            PersistentDataContainer pdc = cursor.getItemMeta().getPersistentDataContainer();
            NamespacedKey scrollKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_scroll_id");
            if (pdc.has(scrollKey, PersistentDataType.STRING)) {
                event.setCancelled(true);
                
                String cosmeticId = pdc.get(scrollKey, PersistentDataType.STRING);
                NamespacedKey colorKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_scroll_color");

                dc.dccosmetics.model.CosmeticTemplate template = DCCosmetics.getInstance().getTemplateRegistry().getTemplate(cosmeticId);
                if (template == null) {
                    DCCosmetics.getInstance().getLanguageManager().sendMessage(event.getWhoClicked(), "scroll_not_exists");
                    return;
                }
                String color = pdc.has(colorKey, PersistentDataType.STRING) ? pdc.get(colorKey, PersistentDataType.STRING) : DCCosmetics.getInstance().getSafeColor(template, null);

                if (!isCompatible(template.getEquipmentSlot(), clicked.getType())) {
                    String msg = DCCosmetics.getInstance().getLanguageManager().getMessage("scroll_incompatible").replace("{slot}", template.getEquipmentSlot().toUpperCase());
                    event.getWhoClicked().sendMessage(msg);
                    return;
                }

                // Apply to clicked item
                org.bukkit.inventory.meta.ItemMeta clickedMeta = clicked.getItemMeta();
                NamespacedKey idKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_id");
                NamespacedKey cKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_color");
                NamespacedKey origKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_original_name");

                // SWAP SCROLL FIX: Prevent accidental overwrites! Tell them to detach!
                if (clickedMeta.getPersistentDataContainer().has(idKey, PersistentDataType.STRING)) {
                    DCCosmetics.getInstance().getLanguageManager().sendMessage(event.getWhoClicked(), "scroll_already_applied");
                    return;
                }
                
                clickedMeta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, cosmeticId);
                clickedMeta.getPersistentDataContainer().set(cKey, PersistentDataType.STRING, color);
                
                // Grab the original item name so we can wrap the cosmetic around it cleanly
                String origName;
                if (clickedMeta.getPersistentDataContainer().has(origKey, PersistentDataType.STRING)) {
                    origName = clickedMeta.getPersistentDataContainer().get(origKey, PersistentDataType.STRING);
                } else {
                    origName = clickedMeta.hasDisplayName() ? clickedMeta.getDisplayName() : DCCosmetics.getInstance().formatMaterialName(clicked.getType());
                    clickedMeta.getPersistentDataContainer().set(origKey, PersistentDataType.STRING, origName);
                }

                clicked.setItemMeta(clickedMeta);
                DCCosmetics.getInstance().updateCosmeticItem(clicked);

                // Consume scroll
                cursor.setAmount(cursor.getAmount() - 1);
                event.getWhoClicked().setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
                
                if (event.getWhoClicked() instanceof Player p) {
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
                    String msg = DCCosmetics.getInstance().getLanguageManager().getMessage("scroll_applied").replace("{cosmetic}", template.getItemName());
                    p.sendMessage(msg);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!(event.getEntity() instanceof org.bukkit.entity.LivingEntity target)) return;

        dc.dccosmetics.model.PlayerProfile profile = DCCosmetics.getInstance().getProfileManager().getProfile(damager);
        if (profile == null) return;

        dc.dccosmetics.model.ActiveCosmetic sword = profile.getActiveCosmetic("sword");
        if (sword == null || sword.getTemplate() == null) return;

        String attackType = "regular";
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            attackType = "sweep";
        } else if (event.isCritical()) {
            attackType = "crit";
        }

        // DESTROY VANILLA PARTICLES INSTANTLY (Cancel for 100ms)
        dc.dccosmetics.nms.ProtocolLibAdapter.suppressParticles(target.getLocation(), 100);

        spawnHitPhysicsEffect(target.getLocation().add(0, target.getHeight() / 2.0, 0), sword.getTemplate(), sword.getColorHex(), attackType, damager.getLocation().getYaw());
    }

    public static void spawnHitPhysicsEffect(Location baseLoc, dc.dccosmetics.model.CosmeticTemplate template, String hexColor, String attackType, float attackerYaw) {
        List<Player> viewers = new ArrayList<>(baseLoc.getWorld().getPlayers()); // Safe mutable copy!
        
        List<HitNode> effectNodes = new ArrayList<>();
        
        // Fallback logic: If they didn't map a specific hit type, default to regular!
        boolean foundAny = false;
        for (String key : template.getNodes().keySet()) {
            if (key.startsWith(attackType + "_")) { foundAny = true; break; }
        }
        String fallbackType = foundAny ? attackType : "regular";

        for (java.util.Map.Entry<String, dc.dccosmetics.model.CosmeticNode> entry : template.getNodes().entrySet()) {
            if (!entry.getKey().startsWith(fallbackType + "_") && !entry.getKey().startsWith(attackType + "_")) continue;

            dc.dccosmetics.model.CosmeticNode nodeData = entry.getValue();
            
            // Auto-override White #FFFFFF so users can use the Customise GUI!
            String finalColor = nodeData.getColor();
            if (finalColor == null || finalColor.trim().isEmpty() || finalColor.equalsIgnoreCase("#FFFFFF")) {
                finalColor = hexColor;
            }

            int duration = template.getAttackDurations().getOrDefault(fallbackType, 10);
            
            org.joml.Vector3f vel;
            org.joml.Vector3f rotSpeed;
            int delay = 0;

            // THE 3-TIER COMBAT PHYSICS ENGINE
            if (attackType.equals("sweep")) {
                vel = new org.joml.Vector3f(0, 0, 0); // Stays perfectly anchored to the impact point
                // Sweep rapidly across the Y-axis!
                rotSpeed = new org.joml.Vector3f(0, 45, 0); 
                
                // PROGRESSIVE DRAWING: Stagger the spawn delay based on the Y-Rotation angle!
                float angle = nodeData.getRotation().y() % 360;
                if (angle < 0) angle += 360;
                delay = (int) ((angle / 360.0f) * (duration * 0.8f));
            } else if (attackType.equals("crit")) {
                vel = new org.joml.Vector3f(
                    (float)(Math.random() - 0.5) * 0.6f,
                    (float)(Math.random() * 0.4) + 0.2f,
                    (float)(Math.random() - 0.5) * 0.6f
                );
                rotSpeed = new org.joml.Vector3f(
                    (float)(Math.random() * 50 - 25),
                    (float)(Math.random() * 50 - 25),
                    (float)(Math.random() * 50 - 25)
                );
            } else { // regular
                vel = new org.joml.Vector3f(
                    (float)(Math.random() - 0.5) * 0.2f,
                    (float)(Math.random() * 0.2) + 0.1f,
                    (float)(Math.random() - 0.5) * 0.2f
                );
                rotSpeed = new org.joml.Vector3f(0, (float)(Math.random() * 20 - 10), 0);
            }

            dc.dccosmetics.api.DisplayWrapper display = DCCosmetics.getInstance().getPacketAdapter().createBlockDisplay(viewers, baseLoc);
            effectNodes.add(setupPhysicsNode(display, nodeData, finalColor, template, false, vel, rotSpeed, attackerYaw, delay));
            
            if (nodeData.isTwoSided()) {
                dc.dccosmetics.api.DisplayWrapper backDisplay = DCCosmetics.getInstance().getPacketAdapter().createBlockDisplay(viewers, baseLoc);
                effectNodes.add(setupPhysicsNode(backDisplay, nodeData, finalColor, template, true, vel, rotSpeed, attackerYaw, delay));
            }
        }
        
        int duration = template.getAttackDurations().getOrDefault(fallbackType, 10);
        int fade = template.getAttackFades().getOrDefault(fallbackType, 10);
        int maxTicks = duration + fade;

        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks > maxTicks) {
                    for (HitNode n : effectNodes) n.display().destroy();
                    this.cancel();
                    return;
                }
                
                for (HitNode n : effectNodes) {
                    if (ticks < n.delay()) continue;

                    if (n.display() instanceof dc.dccosmetics.nms.ProtocolDisplayWrapper pNode) {
                        
                        if (!attackType.equals("sweep")) {
                            // Apply Gravity & Movement purely via Translation Matrix! (NO TELEPORT PACKETS!)
                            n.velocity().y -= 0.02f; 
                            n.currentTrans().add(n.velocity());
                            pNode.setTranslation(n.currentTrans());
                        }
                        
                        // Apply erratic spin
                        n.baseRot().rotateXYZ(
                            (float) Math.toRadians(n.rotSpeed().x),
                            (float) Math.toRadians(n.rotSpeed().y),
                            (float) Math.toRadians(n.rotSpeed().z)
                        );
                        pNode.setRawQuaternion(n.baseRot());

                        // Calculate visual progress based strictly on time alive since delay
                        int aliveTicks = ticks - n.delay();
                        int localMax = maxTicks - n.delay();
                        float progress = localMax > 0 ? (float) aliveTicks / localMax : 1.0f;
                        
                        float scaleMult;
                        if (attackType.equals("sweep")) {
                            scaleMult = Math.max(0.001f, (float) Math.sin(progress * Math.PI)); // Pop out, hold, snap back!
                        } else {
                            scaleMult = Math.max(0.001f, 1.0f - progress); // Shrink out
                        }
                        pNode.setScale(new org.joml.Vector3f(n.baseScale()).mul(scaleMult));

                        // Smooth Alpha Fade-Out!
                        if (aliveTicks > duration && fade > 0) {
                            float fadeProgress = Math.min(1.0f, (float) (aliveTicks - duration) / fade);
                            pNode.setOpacity(n.baseOpacity() * (1.0 - fadeProgress));
                        } else {
                            pNode.setOpacity(n.baseOpacity());
                        }
                        
                        pNode.update();
                    }
                }
                ticks++;
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 0L, 1L);
    }

    private static HitNode setupPhysicsNode(dc.dccosmetics.api.DisplayWrapper display, dc.dccosmetics.model.CosmeticNode nodeData, String color, dc.dccosmetics.model.CosmeticTemplate template, boolean isBack, org.joml.Vector3f velocity, org.joml.Vector3f rotSpeed, float attackerYaw, int delay) {
        dc.dccosmetics.nms.ProtocolDisplayWrapper pNode = (dc.dccosmetics.nms.ProtocolDisplayWrapper) display;
        pNode.setBlockbenchMode(template.isBlockbench());
        
        org.joml.Quaternionf playerYawRot = new org.joml.Quaternionf().rotationY((float) Math.toRadians(-attackerYaw));
        
        org.joml.Quaternionf localQ = nodeData.getOrientation() != null ? new org.joml.Quaternionf(nodeData.getOrientation()) 
                : new org.joml.Quaternionf().rotationXYZ((float) Math.toRadians(nodeData.getRotation().x()), (float) Math.toRadians(nodeData.getRotation().y()), (float) Math.toRadians(nodeData.getRotation().z()));
        if (isBack) localQ.rotateLocalY((float) Math.PI);
        
        org.joml.Vector3f baseScale = new org.joml.Vector3f(nodeData.getScale()).mul(template.getGlobalScale());
        org.joml.Vector3f newTrans = new org.joml.Vector3f(nodeData.getTranslation()).mul(template.getGlobalScale());

        if (template.getGlobalRotation().lengthSquared() > 0) {
            org.joml.Quaternionf gRot = new org.joml.Quaternionf().rotationXYZ((float) Math.toRadians(template.getGlobalRotation().x()), (float) Math.toRadians(template.getGlobalRotation().y()), (float) Math.toRadians(template.getGlobalRotation().z()));
            newTrans.rotate(gRot);
            gRot.mul(localQ, localQ);
        }
        
        newTrans.add(template.getGlobalOffset());

        // Align entire effect to face the same way the attacker is facing!
        newTrans.rotate(playerYawRot);
        playerYawRot.mul(localQ, localQ);
        
        velocity.rotateY((float) Math.toRadians(-attackerYaw));

        pNode.setRawQuaternion(localQ);
        pNode.setScale(new org.joml.Vector3f(0.001f, 0.001f, 0.001f)); // Wait for delay!
        pNode.setTranslation(newTrans); 
        pNode.setColor(color);
        pNode.update();
        
        return new HitNode(display, baseScale, newTrans, new org.joml.Vector3f(velocity), localQ, new org.joml.Vector3f(rotSpeed), delay, nodeData.getOpacity());
    }
}