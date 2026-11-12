package dc.dccosmetics.wardrobe.listener;

import dc.dccosmetics.wardrobe.WardrobeManager;
import dc.dccosmetics.wardrobe.WardrobeSession;
import dc.dccosmetics.wardrobe.camera.CinematicTeleportController;
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (manager.isInWardrobe(player)) {
            WardrobeSession session = manager.getSession(player);
            if (session != null && session.isInTpPreview()) {
                return;
            }
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) return;

            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
            }
            return;
        }

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

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (manager.isInWardrobe(player) && event.isSneaking()) {
            WardrobeSession session = manager.getSession(player);
            if (session != null && (session.isInCinematic() || session.isInTpPreview())) {
                return;
            }
            player.sendMessage(ChatColor.YELLOW + "Exiting wardrobe...");
            manager.exitWardrobe(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        CinematicTeleportController.getInstance().cancelForPlayer(event.getPlayer());
        dc.dccosmetics.tpeffects.TpEffectManager tpMgr = dc.dccosmetics.DCCosmetics.getInstance().getTpEffectManager();
        if (tpMgr != null) {
            tpMgr.getCinematicEngine().cancelSequence(event.getPlayer().getUniqueId(), false);
        }
        if (manager.isInWardrobe(event.getPlayer())) {
            manager.exitWardrobe(event.getPlayer());
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && manager.isInWardrobe(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && manager.isInWardrobe(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHand(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        if (manager.isInWardrobe(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && (manager.isInWardrobe(player) || CinematicTeleportController.getInstance().isInSequence(player))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInWardrobe(player)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInWardrobe(player)) return;
        event.setCancelled(true);
        manager.handleBackNavigation(player);
    }

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
        if (manager.getWardrobeVersion() == 2) {
            if (session.getCurrentLevel() == WardrobeSession.MenuLevel.CATEGORIES) {
                maxIndex = session.getCategoriesList().size() - 1;
            } else if (session.getCurrentLevel() == WardrobeSession.MenuLevel.RARITIES) {
                maxIndex = session.getCurrentRaritiesList().size() - 1;
            } else {
                if ("teleport".equalsIgnoreCase(session.getSelectedCategory())) {
                    maxIndex = session.getCurrentTeleportList().size() - 1;
                } else {
                    maxIndex = session.getCurrentCosmeticsList().size() - 1;
                }
            }
        } else {
            switch (session.getCurrentLevel()) {
                case CATEGORIES: maxIndex = session.getCategoriesList().size() - 1;        break;
                case RARITIES:   maxIndex = session.getCurrentRaritiesList().size() - 1;   break;
                case COSMETICS:
                case ACTION:     maxIndex = session.getCurrentCosmeticsList().size() - 1;  break;
                default:         maxIndex = 0;
            }
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
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BIT, 0.7f, 1.8f);

            if (manager.getWardrobeVersion() == 2) {
                if (session.getCurrentLevel() == WardrobeSession.MenuLevel.CATEGORIES) {
                    manager.getUltimateUIManager().renderOverview(player, session);
                } else if (session.getCurrentLevel() == WardrobeSession.MenuLevel.RARITIES) {
                    manager.getUltimateUIManager().renderRarities(player, session);
                } else {
                    if ("teleport".equalsIgnoreCase(session.getSelectedCategory())) {
                        // Hover/scroll only highlights the teleport card cleanly without auto-playing effects
                        session.setPreviewingTeleportId(null);
                        dc.dccosmetics.tpeffects.TpEffectManager tpMgr =
                                dc.dccosmetics.DCCosmetics.getInstance().getTpEffectManager();
                        if (tpMgr != null) {
                            tpMgr.getCinematicEngine().stopPreview(player);
                        }
                    } else {
                        if (session.getMannequin() != null && currentIndex < session.getCurrentCosmeticsList().size()) {
                            session.getMannequin().previewCosmetic(session.getCurrentCosmeticsList().get(currentIndex));
                        }
                    }
                    manager.getUltimateUIManager().renderCosmetics(player, session);
                }
            } else if (session.getHud() != null) {
                session.getHud().renderAll();
            }
        }
    }
}