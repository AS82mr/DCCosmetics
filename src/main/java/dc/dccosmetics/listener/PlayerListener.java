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

record HitNode(dc.dccosmetics.api.DisplayWrapper display, org.joml.Vector3f baseScale, Location currentLoc, org.joml.Vector3f velocity, org.joml.Quaternionf baseRot, org.joml.Vector3f rotSpeed) {}

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

        spawnHitPhysicsEffect(target.getLocation().add(0, target.getHeight() / 2.0, 0), sword.getTemplate(), sword.getColorHex());
    }

    public static void spawnHitPhysicsEffect(Location baseLoc, dc.dccosmetics.model.CosmeticTemplate template, String hexColor) {
        List<Player> viewers = new ArrayList<>(baseLoc.getWorld().getPlayers()); // Safe mutable copy!
        
        List<HitNode> effectNodes = new ArrayList<>();
        
        for (java.util.Map.Entry<String, dc.dccosmetics.model.CosmeticNode> entry : template.getNodes().entrySet()) {
            dc.dccosmetics.model.CosmeticNode nodeData = entry.getValue();
            String finalColor = (nodeData.getColor() != null && !nodeData.getColor().trim().isEmpty()) ? nodeData.getColor() : hexColor;
            
            // Create physics trajectory for each component (scatter outward and up)
            org.joml.Vector3f vel = new org.joml.Vector3f(
                (float)(Math.random() - 0.5) * 0.4f,
                (float)(Math.random() * 0.3) + 0.1f,
                (float)(Math.random() - 0.5) * 0.4f
            );
            
            // Create erratic spin logic
            org.joml.Vector3f rotSpeed = new org.joml.Vector3f(
                (float)(Math.random() * 30 - 15),
                (float)(Math.random() * 30 - 15),
                (float)(Math.random() * 30 - 15)
            );

            dc.dccosmetics.api.DisplayWrapper display = DCCosmetics.getInstance().getPacketAdapter().createBlockDisplay(viewers, baseLoc);
            effectNodes.add(setupPhysicsNode(display, nodeData, finalColor, template, false, vel, rotSpeed));
            
            if (nodeData.isTwoSided()) {
                dc.dccosmetics.api.DisplayWrapper backDisplay = DCCosmetics.getInstance().getPacketAdapter().createBlockDisplay(viewers, baseLoc);
                effectNodes.add(setupPhysicsNode(backDisplay, nodeData, finalColor, template, true, vel, rotSpeed));
            }
        }
        
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks > 25) { // Physics animation lasts 1.25 seconds!
                    for (HitNode n : effectNodes) n.display().destroy();
                    this.cancel();
                    return;
                }
                
                float scaleMult = Math.max(0.001f, 1.0f - (ticks / 25.0f)); // Shrink as they fall
                
                for (HitNode n : effectNodes) {
                    if (n.display() instanceof dc.dccosmetics.nms.ProtocolDisplayWrapper pNode) {
                        // Apply Gravity & Movement
                        n.velocity().y -= 0.04f; 
                        n.currentLoc().add(n.velocity().x, n.velocity().y, n.velocity().z);
                        pNode.teleport(n.currentLoc());
                        
                        // Apply Shrink
                        pNode.setScale(new org.joml.Vector3f(n.baseScale()).mul(scaleMult));
                        
                        // Apply erratic spin
                        n.baseRot().rotateXYZ(
                            (float) Math.toRadians(n.rotSpeed().x),
                            (float) Math.toRadians(n.rotSpeed().y),
                            (float) Math.toRadians(n.rotSpeed().z)
                        );
                        pNode.setRawQuaternion(n.baseRot());
                        
                        pNode.update();
                    }
                }
                ticks++;
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 0L, 1L);
    }

    private static HitNode setupPhysicsNode(dc.dccosmetics.api.DisplayWrapper display, dc.dccosmetics.model.CosmeticNode nodeData, String color, dc.dccosmetics.model.CosmeticTemplate template, boolean isBack, org.joml.Vector3f velocity, org.joml.Vector3f rotSpeed) {
        dc.dccosmetics.nms.ProtocolDisplayWrapper pNode = (dc.dccosmetics.nms.ProtocolDisplayWrapper) display;
        pNode.setBlockbenchMode(template.isBlockbench());
        pNode.setOpacity(nodeData.getOpacity());
        
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

        pNode.setRawQuaternion(localQ);
        pNode.setScale(baseScale); // Spawn instantly at full size!
        pNode.setTranslation(newTrans); // THE FIX: ACTUALLY APPLY THE SHAPE MATH!
        pNode.setColor(color);
        pNode.update();
        
        // Offset initial spawn location by the translation matrix to preserve the 3D shape!
        Location startLoc = pNode.getLocation().clone().add(newTrans.x, newTrans.y, newTrans.z);
        return new HitNode(display, baseScale, startLoc, new org.joml.Vector3f(velocity), localQ, new org.joml.Vector3f(rotSpeed));
    }
}