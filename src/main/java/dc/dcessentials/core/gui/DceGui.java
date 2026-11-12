package dc.dcessentials.core.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker and policy interface for native DCEssentials/DCCosmetics inventories (Task P2-01).
 * Protected globally by {@link GuiGuardListener} against drag-duplication, collect-to-cursor,
 * hotbar number swaps, offhand swaps, and unintended shift-transfers.
 */
public interface DceGui extends InventoryHolder {

    /**
     * Allows a specific GUI implementation to whitelist a raw click event if needed.
     */
    default boolean isActionWhitelisted(InventoryClickEvent event) {
        return false;
    }

    /**
     * Whether players may pick up/place items inside their own bottom inventory while this GUI is open
     * (e.g., to pick up a scroll, energy cube, or equipment item onto the cursor).
     * Shift-clicks and collect-to-cursor remain blocked so items never auto-transfer into the top GUI.
     */
    default boolean allowBottomInventoryPickup() {
        return true;
    }
}
