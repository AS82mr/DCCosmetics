package dc.dccosmetics.wardrobe;

import dc.dccosmetics.DCCosmetics;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WardrobeRegionSelector implements Listener {
    
    private final WardrobeRoom roomConfig;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    
    public WardrobeRegionSelector(WardrobeRoom roomConfig) {
        this.roomConfig = roomConfig;
        DCCosmetics.getInstance().getServer().getPluginManager().registerEvents(this, DCCosmetics.getInstance());
    }

    public void giveWand(Player player) {
        ItemStack wand = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Wardrobe Region Wand");
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(ChatColor.GRAY + "Left Click: Position 1");
        lore.add(ChatColor.GRAY + "Right Click: Position 2");
        lore.add(ChatColor.GRAY + "Run /wardrobe setup to save.");
        meta.setLore(lore);
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        player.sendMessage(ChatColor.GREEN + "Select two corners of the wardrobe room using the wand.");
    }
    
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || item.getType() != Material.GOLDEN_AXE) return;
        if (!item.hasItemMeta() || !ChatColor.stripColor(item.getItemMeta().getDisplayName()).equals("Wardrobe Region Wand")) return;
        
        if (event.getClickedBlock() == null) return;
        
        event.setCancelled(true);
        Location clicked = event.getClickedBlock().getLocation();
        
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            pos1.put(player.getUniqueId(), clicked);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Position 1 set: " + clicked.getBlockX() + ", " + clicked.getBlockY() + ", " + clicked.getBlockZ());
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            pos2.put(player.getUniqueId(), clicked);
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Position 2 set: " + clicked.getBlockX() + ", " + clicked.getBlockY() + ", " + clicked.getBlockZ());
        }
    }
    
    public void saveRegion(Player player) {
        Location p1 = pos1.get(player.getUniqueId());
        Location p2 = pos2.get(player.getUniqueId());
        
        if (p1 == null || p2 == null) {
            player.sendMessage(ChatColor.RED + "You must select both points first!");
            return;
        }
        
        if (!p1.getWorld().equals(p2.getWorld())) {
            player.sendMessage(ChatColor.RED + "Points must be in the same world!");
            return;
        }
        
        roomConfig.saveRegion(p1, p2);
        player.sendMessage(ChatColor.GREEN + "Wardrobe region saved successfully!");
        
        pos1.remove(player.getUniqueId());
        pos2.remove(player.getUniqueId());
        
        player.getInventory().remove(Material.GOLDEN_AXE); // Crude removal, but effective for admin tool
    }
}
