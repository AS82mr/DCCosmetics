package dc.dcessentials.modules.items.sync;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.teleportation.KeystoneDetector;
import dc.dcessentials.modules.teleportation.KeystoneLoreTokens;
import dc.dcessentials.modules.teleportation.KeystoneProfile;
import dc.dcessentials.modules.teleportation.KeystoneTier;
import dc.dcessentials.modules.teleportation.LegacyKeystoneConverter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * LiveSyncListener: Updates existing DCE items to their latest schema/version
 * and converts legacy ExecutableItems keystones/cubes to native DCE items (P1-04).
 */
public class LiveSyncListener implements Listener {

    private final ItemRegistry itemRegistry;

    public LiveSyncListener(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item == null || item.getType().isAir()) return;

        Optional<ItemStack> converted = LegacyKeystoneConverter.convert(item);
        if (converted.isPresent()) {
            player.getInventory().setItem(event.getNewSlot(), converted.get());
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
        if (top != null && (top.getType() == InventoryType.ENDER_CHEST
                || top.getHolder() instanceof org.bukkit.block.Container
                || event.getView().getTitle().toLowerCase().contains("vault"))) {
            LegacyKeystoneConverter.convertInventory(top);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack current = event.getCurrentItem();
        if (current != null && !current.getType().isAir()) {
            if (event.getClickedInventory() == player.getInventory()) {
                Optional<ItemStack> conv = LegacyKeystoneConverter.convert(current);
                if (conv.isPresent()) {
                    event.setCurrentItem(conv.get());
                    current = conv.get();
                }
            }
            if (itemRegistry.isDCEItem(current)) {
                itemRegistry.updateItemToLatest(current);
            }
        }
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            Optional<ItemStack> conv = LegacyKeystoneConverter.convert(cursor);
            if (conv.isPresent()) {
                player.setItemOnCursor(conv.get());
                cursor = conv.get();
            }
            if (itemRegistry.isDCEItem(cursor)) {
                itemRegistry.updateItemToLatest(cursor);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        Optional<ItemStack> converted = LegacyKeystoneConverter.convert(item);
        if (converted.isPresent()) {
            event.getItem().setItemStack(converted.get());
            return;
        }
        if (itemRegistry.isDCEItem(item)) {
            if (itemRegistry.updateItemToLatest(item)) {
                event.getItem().setItemStack(item);
            }
        }
    }
}
