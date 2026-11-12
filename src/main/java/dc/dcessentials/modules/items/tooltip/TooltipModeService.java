package dc.dcessentials.modules.items.tooltip;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.gui.DceGui;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.model.ItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TooltipModeService implements Listener {

    private final ItemRegistry itemRegistry;
    private final Map<UUID, TooltipMode> persistentModes = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> sneakAtOpen = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> openTopSizes = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> openTopIsDceGui = new ConcurrentHashMap<>();

    public TooltipModeService(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
    }

    public void loadOnlinePlayers() {
        var db = DCCosmetics.getInstance().getDatabaseManager();
        if (db == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            db.loadPlayerTooltipMode(uuid, modeStr -> persistentModes.put(uuid, TooltipMode.fromString(modeStr)));
        }
    }

    public TooltipMode getPersistentMode(UUID uuid) {
        if (uuid == null) return TooltipMode.COMPACT;
        return persistentModes.getOrDefault(uuid, TooltipMode.COMPACT);
    }

    public TooltipMode getEffectiveMode(UUID uuid) {
        if (uuid == null) return TooltipMode.COMPACT;
        if (Boolean.TRUE.equals(sneakAtOpen.get(uuid))) {
            return TooltipMode.EXPANDED;
        }
        return persistentModes.getOrDefault(uuid, TooltipMode.COMPACT);
    }

    public int getCachedOpenTopSize(UUID uuid) {
        if (uuid == null) return 0;
        return openTopSizes.getOrDefault(uuid, 0);
    }

    public boolean isCachedOpenTopDceGui(UUID uuid) {
        if (uuid == null) return false;
        return Boolean.TRUE.equals(openTopIsDceGui.get(uuid));
    }

    public void setMode(Player player, TooltipMode mode, boolean notify) {
        if (player == null || mode == null) return;
        UUID uuid = player.getUniqueId();
        persistentModes.put(uuid, mode);
        if (mode == TooltipMode.COMPACT) {
            sneakAtOpen.remove(uuid);
        }
        var db = DCCosmetics.getInstance().getDatabaseManager();
        if (db != null) {
            db.savePlayerTooltipMode(uuid, mode.name());
        }
        scheduleInventoryRefresh(player);
        if (notify) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, mode == TooltipMode.EXPANDED ? 1.4f : 1.0f);
            DCMessage.sendGeneral(player, "Item Tooltip Mode set to &#38BDF8" + mode.name()
                    + "&#0CBF93. &8(Shift+Right-Click any DCE item in inventory or use /tooltip to toggle)");
        }
    }

    public TooltipMode toggleMode(Player player, boolean notify) {
        TooltipMode cur = getEffectiveMode(player.getUniqueId());
        TooltipMode next = cur.toggle();
        setMode(player, next, notify);
        return next;
    }

    public void scheduleInventoryRefresh(Player player) {
        if (player == null || !DCCosmetics.getInstance().isEnabled()) return;
        Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
            if (player.isOnline()) {
                player.updateInventory();
            }
        }, 1L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        var db = DCCosmetics.getInstance().getDatabaseManager();
        if (db != null) {
            db.loadPlayerTooltipMode(uuid, modeStr -> persistentModes.put(uuid, TooltipMode.fromString(modeStr)));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        persistentModes.remove(uuid);
        sneakAtOpen.remove(uuid);
        openTopSizes.remove(uuid);
        openTopIsDceGui.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        Inventory top = event.getView().getTopInventory();
        openTopSizes.put(uuid, top != null ? top.getSize() : 0);
        openTopIsDceGui.put(uuid, top != null && top.getHolder() instanceof DceGui);
        if (player.isSneaking()) {
            sneakAtOpen.put(uuid, true);
            scheduleInventoryRefresh(player);
        } else {
            sneakAtOpen.remove(uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        openTopSizes.remove(uuid);
        openTopIsDceGui.remove(uuid);
        if (Boolean.TRUE.equals(sneakAtOpen.remove(uuid))) {
            scheduleInventoryRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onShiftRightToggle(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClick() != ClickType.SHIFT_RIGHT) return;

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir() || itemRegistry == null || !itemRegistry.isDCEItem(current)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        boolean isPlayerInventoryView = top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.PLAYER;
        boolean isDceGui = top.getHolder() instanceof DceGui;
        if (!isPlayerInventoryView && !isDceGui) {
            // Do not hijack shift-right-click when player is moving items into vanilla chests/furnaces/etc.
            return;
        }
        // Only trigger on bottom inventory or non-interactive DceGui preview slots
        if (isDceGui && event.getRawSlot() < top.getSize()) {
            return;
        }

        event.setCancelled(true);
        toggleMode(player, true);
    }
}
