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
            case "offhand": return mat == Material.SHIELD || mat == Material.TOTEM_OF_UNDYING || mat == Material.BOW || mat == Material.CROSSBOW;
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
                    event.getWhoClicked().sendMessage(ChatColor.RED + "This scroll's cosmetic no longer exists!");
                    return;
                }
                String color = pdc.has(colorKey, PersistentDataType.STRING) ? pdc.get(colorKey, PersistentDataType.STRING) : DCCosmetics.getInstance().getSafeColor(template, null);

                if (!isCompatible(template.getEquipmentSlot(), clicked.getType())) {
                    event.getWhoClicked().sendMessage(ChatColor.RED + "You can only apply this to " + template.getEquipmentSlot().toUpperCase() + " items!");
                    return;
                }

                // Apply to clicked item
                org.bukkit.inventory.meta.ItemMeta clickedMeta = clicked.getItemMeta();
                NamespacedKey idKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_id");
                NamespacedKey cKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_color");
                NamespacedKey origKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_original_name");

                // SWAP SCROLL FIX: Prevent accidental overwrites! Tell them to detach!
                if (clickedMeta.getPersistentDataContainer().has(idKey, PersistentDataType.STRING)) {
                    event.getWhoClicked().sendMessage(ChatColor.RED + "This item already has a cosmetic!");
                    event.getWhoClicked().sendMessage(ChatColor.GRAY + "Please detach the current one via " + ChatColor.YELLOW + "/customise" + ChatColor.GRAY + " first.");
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
                    p.sendMessage(ChatColor.GREEN + "Successfully bound the " + template.getId() + " cosmetic to your item!");
                }
            }
        }
    }
}