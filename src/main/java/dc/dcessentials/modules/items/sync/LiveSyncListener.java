package dc.dcessentials.modules.items.sync;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.gui.DceGui;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.teleportation.KeystoneDetector;
import dc.dcessentials.modules.teleportation.KeystoneLoreTokens;
import dc.dcessentials.modules.teleportation.KeystoneProfile;
import dc.dcessentials.modules.teleportation.KeystoneTier;
import dc.dcessentials.modules.teleportation.LegacyKeystoneConverter;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * LiveSyncListener: Updates existing DCE items to their latest schema/version,
 * converts legacy ExecutableItems keystones/cubes to native DCE items, and enforces
 * player-inventory-only dynamic {mc:...} lore display vs base rate on world/containers/AH/trade.
 */
public class LiveSyncListener implements Listener {

    private static final Set<String> EXTERNAL_ECON_COMMANDS = Set.of(
            "/ah", "/auc", "/auction", "/auctionhouse", "/zauctionhouse",
            "/trade", "/trademe", "/sell", "/gift"
    );

    private static final Set<String> ENCHANT_COMMANDS = Set.of(
            "/enchant", "/ench", "/enchants", "/disenchant", "/unenchant"
    );

    private final ItemRegistry itemRegistry;

    public LiveSyncListener(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        KeystoneLoreTokens.scheduleInventoryRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item == null || item.getType().isAir()) return;

        Optional<ItemStack> converted = LegacyKeystoneConverter.convert(item);
        if (converted.isPresent()) {
            player.getInventory().setItem(event.getNewSlot(), converted.get());
            KeystoneLoreTokens.scheduleInventoryRefresh(player);
            return;
        }
        if (itemRegistry.isDCEItem(item)) {
            itemRegistry.updateItemToLatest(item);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        LegacyKeystoneConverter.convertPlayerInventories(player);
        boolean degradedMode = !Bukkit.getPluginManager().isPluginEnabled("ProtocolLib");
        KeystoneProfile kProfile = DCCosmetics.getInstance().getTeleportationManager() != null
                ? DCCosmetics.getInstance().getTeleportationManager().getProfile(player)
                : null;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir() && itemRegistry.isDCEItem(item)) {
                itemRegistry.updateItemToLatest(item);
                if (degradedMode && kProfile != null && KeystoneDetector.tierOf(item) != KeystoneTier.NONE) {
                    KeystoneLoreTokens.applyToStack(item, player, kProfile);
                }
            }
        }

        Inventory top = event.getView().getTopInventory();
        if (top != null && isPersistentContainer(top, event.getView().getTitle())) {
            LegacyKeystoneConverter.convertInventory(top);
            revertContainerToBaseRate(top);
        }
        KeystoneLoreTokens.scheduleInventoryRefresh(player);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        boolean externalTop = isExternalTopInventory(top);

        // Avoid mutating items synchronously inside InventoryClickEvent, which causes Paper 1.21
        // HashedStack cursor desync (ghost items stuck to mouse cursor when moving slots).
        Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
            if (externalTop && top != null) {
                revertContainerToBaseRate(top);
            }
            if (!player.isOnline()) return;
            if (!player.getItemOnCursor().getType().isAir()) {
                return;
            }
            LegacyKeystoneConverter.convertPlayerInventories(player);
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && !item.getType().isAir() && itemRegistry.isDCEItem(item)) {
                    itemRegistry.updateItemToLatest(item);
                }
            }
            KeystoneLoreTokens.scheduleInventoryRefresh(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        boolean externalTop = isExternalTopInventory(top);
        Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
            if (externalTop && top != null) {
                revertContainerToBaseRate(top);
            }
            if (player.isOnline()) {
                KeystoneLoreTokens.scheduleInventoryRefresh(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        Optional<ItemStack> converted = LegacyKeystoneConverter.convert(item);
        if (converted.isPresent()) {
            event.getItem().setItemStack(converted.get());
        } else if (itemRegistry.isDCEItem(item)) {
            if (itemRegistry.updateItemToLatest(item)) {
                event.getItem().setItemStack(item);
            }
        }
        if (event.getEntity() instanceof Player player) {
            KeystoneLoreTokens.scheduleInventoryRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack drop = event.getItemDrop().getItemStack();
        if (KeystoneLoreTokens.revertToBaseRate(drop)) {
            event.getItemDrop().setItemStack(drop);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        for (ItemStack drop : event.getDrops()) {
            KeystoneLoreTokens.revertToBaseRate(drop);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExternalEconomyCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (msg == null || msg.isEmpty()) return;
        int space = msg.indexOf(' ');
        String root = (space > 0 ? msg.substring(0, space) : msg).toLowerCase(Locale.ROOT);
        int colon = root.indexOf(':');
        if (colon > 0) {
            root = "/" + root.substring(colon + 1);
        }
        if (ENCHANT_COMMANDS.contains(root)) {
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
                if (!player.isOnline()) return;
                var inv = player.getInventory();
                boolean changed = false;
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack item = inv.getItem(i);
                    if (item == null || item.getType().isAir()) continue;
                    if (itemRegistry.isDCEItem(item)) {
                        if (itemRegistry.updateItemToLatest(item)) {
                            inv.setItem(i, item);
                            changed = true;
                        }
                    } else if (ItemRegistry.isEligibleEquipment(item.getType())
                            && item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
                        if (itemRegistry.initializeDefaultEquipment(item)) {
                            inv.setItem(i, item);
                            changed = true;
                        }
                    }
                }
                if (changed) {
                    player.updateInventory();
                }
                KeystoneLoreTokens.scheduleInventoryRefresh(player);
            });
            return;
        }
        if (!EXTERNAL_ECON_COMMANDS.contains(root)) return;

        Player player = event.getPlayer();
        KeystoneLoreTokens.revertInventoryDynamicMc(player);
        Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
            if (player.isOnline()) {
                KeystoneLoreTokens.scheduleInventoryRefresh(player);
            }
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDisplayInteract(PlayerInteractEntityEvent event) {
        var entity = event.getRightClicked();
        if (!(entity instanceof ItemFrame) && !(entity instanceof ArmorStand)) {
            return;
        }
        Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
            if (!entity.isValid()) return;
            if (entity instanceof ItemFrame frame) {
                ItemStack framed = frame.getItem();
                if (!framed.getType().isAir() && KeystoneLoreTokens.revertToBaseRate(framed)) {
                    frame.setItem(framed);
                }
            } else if (entity instanceof ArmorStand stand && stand.getEquipment() != null) {
                var eq = stand.getEquipment();
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    ItemStack piece = eq.getItem(slot);
                    if (!piece.getType().isAir() && KeystoneLoreTokens.revertToBaseRate(piece)) {
                        eq.setItem(slot, piece);
                    }
                }
            }
        });
    }

    private boolean isPersistentContainer(Inventory top, String title) {
        if (top == null) return false;
        return top.getType() == InventoryType.ENDER_CHEST
                || top.getHolder() instanceof org.bukkit.block.Container
                || (title != null && title.toLowerCase(Locale.ROOT).contains("vault"));
    }

    private boolean isExternalTopInventory(Inventory top) {
        if (top == null) return false;
        if (top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.PLAYER) return false;
        return !(top.getHolder() instanceof DceGui);
    }

    private void revertContainerToBaseRate(Inventory inv) {
        if (inv == null) return;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && !item.getType().isAir() && KeystoneLoreTokens.revertToBaseRate(item)) {
                inv.setItem(i, item);
            }
        }
    }
}
