package dc.dcessentials.core.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Global GUI protection listener for all {@link DceGui} inventories (Task P2-01).
 */
public class GuiGuardListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof DceGui)) {
            return;
        }
        int topSize = top.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof DceGui dceGui)) {
            return;
        }
        if (dceGui.isActionWhitelisted(event)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        int topSize = top.getSize();
        boolean inTop = rawSlot >= 0 && rawSlot < topSize;

        ClickType click = event.getClick();
        InventoryAction action = event.getAction();

        // Always block cross-inventory / multi-slot actions while a DceGui is open
        if (action == InventoryAction.COLLECT_TO_CURSOR
                || action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || click == ClickType.DOUBLE_CLICK
                || click == ClickType.SHIFT_LEFT
                || click == ClickType.SHIFT_RIGHT
                || click == ClickType.NUMBER_KEY
                || click == ClickType.SWAP_OFFHAND) {
            event.setCancelled(true);
            return;
        }

        if (inTop) {
            // Every top-slot click (including DROP/CONTROL_DROP/LEFT/RIGHT) is cancelled by default
            event.setCancelled(true);
            return;
        }

        // Bottom inventory click
        if (rawSlot >= topSize && !dceGui.allowBottomInventoryPickup()) {
            event.setCancelled(true);
        }
    }
}
