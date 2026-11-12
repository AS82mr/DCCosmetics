package dc.dcessentials.modules.items.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.tracker.ItemTrackerManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemDeleteConfirmGUI implements Listener {

    private final DCEItem item;
    private final ItemRegistry itemRegistry;

    public static class ConfirmHolder implements InventoryHolder {
        private final DCEItem item;

        public ConfirmHolder(DCEItem item) {
            this.item = item;
        }

        public DCEItem getItem() { return item; }

        @Override
        public Inventory getInventory() { return null; }
    }

    public ItemDeleteConfirmGUI(DCEItem item, ItemRegistry itemRegistry) {
        this.item = item;
        this.itemRegistry = itemRegistry;
    }

    public void open(Player player) {
        if (item == null) return;

        String title = ColorUtil.colorize("<#1E293B>&lConfirm Deletion &8| &c" + item.getId());
        Inventory gui = Bukkit.createInventory(new ConfirmHolder(item), 45, title);

        ItemStack border = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "&7", null);
        for (int i = 0; i < 45; i++) {
            gui.setItem(i, border);
        }

        // Preview of Item in Slot 13
        ItemStack preview = itemRegistry.createItemStack(item.getId(), 1);
        gui.setItem(13, preview);

        // Option 1: Permanent Global Wipe (Slot 20)
        gui.setItem(20, createGuiItem(Material.RED_CONCRETE, "<#EF4444>&lPERMANENT GLOBAL WIPE", List.of(
                "&7Completely purges this item from:",
                "&7• The registry & disk file",
                "&7• All player inventories & enderchests",
                "&7• The global tracking ledger",
                "&f",
                "<#EF4444>&l▶ Click to Permanently Wipe Everywhere"
        )));

        // Option 2: Wipe with Reward Compensation (Slot 22)
        gui.setItem(22, createGuiItem(Material.GOLD_BLOCK, "<#F59E0B>&lWIPE WITH REWARD COMPENSATION", List.of(
                "&7Purges the item globally, but compensates",
                "&7each affected online player with &e$5,000 &7coins",
                "&7or compensation economy reward.",
                "&f",
                "<#FCD34D>&l▶ Click to Compensate & Wipe"
        )));

        // Option 3: Cancel (Slot 24)
        gui.setItem(24, createGuiItem(Material.IRON_DOOR, "<#38BDF8>&lCANCEL & RETURN", List.of(
                "&7Aborts deletion without making any changes.",
                "&f",
                "<#38BDF8>&l▶ Click to Safely Cancel"
        )));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof ConfirmHolder holder)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 45) return;

        DCEItem targetItem = holder.getItem();
        var itemModule = DCCosmetics.getInstance().getItemModule();

        if (slot == 20) { // Permanent Global Wipe
            int removed = itemRegistry.wipeItemGlobally(targetItem.getId(), null);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.7f, 1.2f);
            DCMessage.sendWarning(player, "Globally wiped item &#EF4444" + targetItem.getId() + "! (Purged " + removed + " active instances from server)");
            if (itemModule != null) {
                itemModule.getItemBrowserGUI().open(player);
            } else {
                player.closeInventory();
            }
        } else if (slot == 22) { // Wipe with Reward Compensation
            int removed = itemRegistry.wipeItemGlobally(targetItem.getId(), "eco give %player% 5000");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            DCMessage.sendGeneral(player, "Globally wiped &#EF4444" + targetItem.getId() + " &#0CBF93and compensated affected players! (" + removed + " instances purged)");
            if (itemModule != null) {
                itemModule.getItemBrowserGUI().open(player);
            } else {
                player.closeInventory();
            }
        } else if (slot == 24) { // Cancel
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            if (itemModule != null) {
                itemModule.getItemEditorGUI().open(player, targetItem);
            } else {
                player.closeInventory();
            }
        }
    }

    private ItemStack createGuiItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            if (lore != null) {
                meta.setLore(lore.stream().map(ColorUtil::colorize).toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
