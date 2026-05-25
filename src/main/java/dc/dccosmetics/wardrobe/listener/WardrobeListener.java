package dc.dccosmetics.wardrobe.listener;

import dc.dccosmetics.wardrobe.WardrobeManager;
import dc.dccosmetics.wardrobe.WardrobeSession;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class WardrobeListener implements Listener {

    private final WardrobeManager manager;

    public WardrobeListener(WardrobeManager manager) {
        this.manager = manager;
    }

    // ─────────────────────────────────────────────────────────────
    //  WARDROBE ENTRY (WorldGuard region trigger)
    // ─────────────────────────────────────────────────────────────

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (manager.isInWardrobe(player)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;

        try {
            com.sk89q.worldedit.util.Location weLoc = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(to);
            com.sk89q.worldguard.protection.ApplicableRegionSet regions =
                com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().getApplicableRegions(weLoc);
            for (com.sk89q.worldguard.protection.regions.ProtectedRegion region : regions) {
                if (region.getId().equalsIgnoreCase("wardrobe")) {
                    manager.enterWardrobe(player);
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    // ─────────────────────────────────────────────────────────────
    //  EXIT — shift to leave
    // ─────────────────────────────────────────────────────────────

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (manager.isInWardrobe(player) && event.isSneaking()) {
            player.sendMessage(ChatColor.YELLOW + "Exiting wardrobe...");
            manager.exitWardrobe(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (manager.isInWardrobe(event.getPlayer())) {
            manager.exitWardrobe(event.getPlayer());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  BLOCK DAMAGE / INVENTORY
    // ─────────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && manager.isInWardrobe(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && manager.isInWardrobe(player)) {
            event.setCancelled(true);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  RIGHT CLICK → BACK (Bukkit fallback — main path is USE_ENTITY/USE_ITEM in packet listener)
    // ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInWardrobe(player)) return;

        org.bukkit.event.block.Action action = event.getAction();
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);
        manager.handleBackNavigation(player);
    }

    // ─────────────────────────────────────────────────────────────
    //  Q KEY → BACK (drop key fallback)
    // ─────────────────────────────────────────────────────────────

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInWardrobe(player)) return;
        event.setCancelled(true);
        manager.handleBackNavigation(player);
    }

    // ─────────────────────────────────────────────────────────────
    //  SCROLL WHEEL — item-slot switch → navigate list
    // ─────────────────────────────────────────────────────────────

    @EventHandler
    public void onItemSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInWardrobe(player)) return;

        event.setCancelled(true);
        WardrobeSession session = manager.getSession(player);
        if (session == null || session.isInCinematic()) return;

        int prev = event.getPreviousSlot();
        int next = event.getNewSlot();
        int delta = next - prev;
        if (prev == 8 && next == 0) delta = 1;
        if (prev == 0 && next == 8) delta = -1;

        int maxIndex;
        switch (session.getCurrentLevel()) {
            case CATEGORIES: maxIndex = session.getCategoriesList().size() - 1;        break;
            case RARITIES:   maxIndex = session.getCurrentRaritiesList().size() - 1;   break;
            case COSMETICS:
            case ACTION:     maxIndex = session.getCurrentCosmeticsList().size() - 1;  break;
            default:         maxIndex = 0;
        }
        if (maxIndex < 0) maxIndex = 0;

        int currentIndex = session.getSelectedIndex();
        if (delta > 0) {
            currentIndex = (currentIndex >= maxIndex) ? 0 : currentIndex + 1;
        } else if (delta < 0) {
            currentIndex = (currentIndex <= 0) ? maxIndex : currentIndex - 1;
        }

        if (currentIndex != session.getSelectedIndex()) {
            session.setSelectedIndex(currentIndex);
            // Cyberpunk: sharp digital navigation tick
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 0.7f, 1.8f);
            session.getHud().renderAll();
        }
    }
}
