package dc.dccosmetics.wardrobe.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.wardrobe.WardrobeRoom;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;

public class WardrobeAdminGUI implements Listener {

    private static final String TITLE = ChatColor.DARK_PURPLE + "Wardrobe Admin Setup";
    private final WardrobeRoom room;

    public WardrobeAdminGUI(WardrobeRoom room) {
        this.room = room;
        DCCosmetics.getInstance().getServer().getPluginManager().registerEvents(this, DCCosmetics.getInstance());
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        inv.setItem(10, createItem(Material.ENDER_PEARL, ChatColor.AQUA + "Set Wardrobe Spawn",
            ChatColor.GRAY + "Click to set the player (camera) spawn",
            ChatColor.GRAY + "to your current location."));

        inv.setItem(11, createItem(Material.BLUE_BED, ChatColor.LIGHT_PURPLE + "Set Safe Room",
            ChatColor.GRAY + "Click to set the safe room where",
            ChatColor.GRAY + "players wait during camera mode."));

        inv.setItem(12, createItem(Material.ARMOR_STAND, ChatColor.GOLD + "Set NPC Location",
            ChatColor.GRAY + "Click to set the mannequin NPC",
            ChatColor.GRAY + "stand to your current location."));

        inv.setItem(13, createItem(Material.IRON_DOOR, ChatColor.GREEN + "Set Fallback Exit",
            ChatColor.GRAY + "Click to set the exit point",
            ChatColor.GRAY + "to your current location."));

        inv.setItem(14, createItem(Material.GLOWSTONE_DUST, ChatColor.YELLOW + "Highlight Region",
            ChatColor.GRAY + "Click to spawn particles around",
            ChatColor.GRAY + "the configured wardrobe region boundaries."));

        inv.setItem(15, createItem(Material.COMMAND_BLOCK, ChatColor.GRAY + "Studio (Chat)",
            ChatColor.GRAY + "Opens the HUD studio panel",
            ChatColor.GRAY + "in chat for fine-tuning panel positions.",
            ChatColor.DARK_GRAY + "Command: /wardrobe studio"));

        player.openInventory(inv);
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        switch (name) {
            case "Set Wardrobe Spawn":
                room.saveSpawn(player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Wardrobe spawn (camera) location set to your position!");
                player.closeInventory();
                break;
            case "Set Safe Room":
                room.saveSafeRoom(player.getLocation());
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Safe room location set. Players will be held here during camera mode.");
                player.closeInventory();
                break;
            case "Set NPC Location":
                room.saveNpcStand(player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Wardrobe NPC location set to your position!");
                player.closeInventory();
                break;
            case "Set Fallback Exit":
                room.saveFallbackExit(player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Wardrobe exit location set to your position!");
                player.closeInventory();
                break;
            case "Highlight Region":
                player.closeInventory();
                highlightRegion(player);
                break;
            case "Studio (Chat)":
                player.closeInventory();
                player.performCommand("wardrobe studio");
                break;
        }
    }

    private void highlightRegion(Player player) {
        if (!room.isInsideRegion(room.getSpawn() != null ? room.getSpawn() : player.getLocation())) {
            // It might throw null if region isn't fully set, let's just do a safe check
            player.sendMessage(ChatColor.YELLOW + "Spawning region particles (if configured)...");
        }
        
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > 20 * 10) { // 10 seconds
                    this.cancel();
                    return;
                }
                
                // Assuming regionMin and regionMax are loaded inside the class or we can grab them
                // We don't have direct getter for min/max, but we can just use the config
                try {
                    org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.File(DCCosmetics.getInstance().getDataFolder(), "wardrobe.yml"));
                    
                    double minX = config.getDouble("room.region.min.x");
                    double minY = config.getDouble("room.region.min.y");
                    double minZ = config.getDouble("room.region.min.z");
                    double maxX = config.getDouble("room.region.max.x");
                    double maxY = config.getDouble("room.region.max.y");
                    double maxZ = config.getDouble("room.region.max.z");
                    org.bukkit.World w = Bukkit.getWorld(config.getString("room.world", "world"));
                    
                    if (w != null) {
                        spawnParticleLine(w, minX, minY, minZ, maxX, minY, minZ);
                        spawnParticleLine(w, minX, minY, minZ, minX, maxY, minZ);
                        spawnParticleLine(w, minX, minY, minZ, minX, minY, maxZ);
                        spawnParticleLine(w, maxX, maxY, maxZ, minX, maxY, maxZ);
                        spawnParticleLine(w, maxX, maxY, maxZ, maxX, minY, maxZ);
                        spawnParticleLine(w, maxX, maxY, maxZ, maxX, maxY, minZ);
                    }
                } catch (Exception e) {}
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 0L, 10L);
    }
    
    private void spawnParticleLine(org.bukkit.World w, double x1, double y1, double z1, double x2, double y2, double z2) {
        double distance = Math.sqrt(Math.pow(x2-x1, 2) + Math.pow(y2-y1, 2) + Math.pow(z2-z1, 2));
        int points = (int) (distance * 2);
        for (int i = 0; i <= points; i++) {
            double x = x1 + (x2 - x1) * i / points;
            double y = y1 + (y2 - y1) * i / points;
            double z = z1 + (z2 - z1) * i / points;
            w.spawnParticle(Particle.FLAME, x, y, z, 1, 0, 0, 0, 0);
        }
    }
}
