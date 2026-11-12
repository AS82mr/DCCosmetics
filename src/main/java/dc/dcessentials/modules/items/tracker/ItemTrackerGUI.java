package dc.dcessentials.modules.items.tracker;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.*;

public class ItemTrackerGUI implements Listener {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private final ItemTrackerManager trackerManager;
    private final ItemRegistry itemRegistry;

    public static class TrackerHolder implements InventoryHolder {
        private final String itemId;
        private final int page;
        private final ItemTrackerManager.SortMode sortMode;

        public TrackerHolder(String itemId, int page, ItemTrackerManager.SortMode sortMode) {
            this.itemId = itemId;
            this.page = page;
            this.sortMode = sortMode;
        }

        public String getItemId() { return itemId; }
        public int getPage() { return page; }
        public ItemTrackerManager.SortMode getSortMode() { return sortMode; }

        @Override
        public Inventory getInventory() { return null; }
    }

    public ItemTrackerGUI(ItemTrackerManager trackerManager, ItemRegistry itemRegistry) {
        this.trackerManager = trackerManager;
        this.itemRegistry = itemRegistry;
    }

    public void open(Player player, String itemId, int page, ItemTrackerManager.SortMode sortMode) {
        if (itemId == null) return;
        if (sortMode == null) sortMode = ItemTrackerManager.SortMode.MOST_AMOUNT;

        DCEItem dce = itemRegistry.getItem(itemId);
        String itemName = dce != null ? dce.getDisplayName() : itemId;

        List<ItemTrackerManager.UserItemSummary> summaries = trackerManager.getPlayerSummaries(itemId, sortMode);
        int totalPlayers = summaries.size();
        int maxPages = Math.max(1, (int) Math.ceil((double) totalPlayers / 45));
        int currentPage = Math.max(0, Math.min(page, maxPages - 1));

        String title = ColorUtil.colorize("<#1E293B>&lDCE Tracker &8| &f" + itemId + (maxPages > 1 ? " &8(" + (currentPage + 1) + "/" + maxPages + ")" : ""));
        TrackerHolder holder = new TrackerHolder(itemId, currentPage, sortMode);
        Inventory gui = Bukkit.createInventory(holder, 54, title);

        // Bottom border
        ItemStack border = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "&7", null);
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, border);
        }

        int start = currentPage * 45;
        int end = Math.min(start + 45, totalPlayers);
        int totalCirculating = 0;
        for (ItemTrackerManager.UserItemSummary s : summaries) {
            totalCirculating += s.getTotalAmount();
        }

        for (int i = start; i < end; i++) {
            ItemTrackerManager.UserItemSummary s = summaries.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                if (s.getUuid() != null) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(s.getUuid());
                    meta.setOwningPlayer(op);
                }
                meta.setDisplayName(ColorUtil.colorize("<#FCD34D>&l" + s.getUsername()));

                List<String> lore = new ArrayList<>();
                lore.add("&7Total Quantity: <#FCD34D>&l" + s.getTotalAmount() + "x");
                lore.add("&7• Inventory: &f" + s.getInvAmount() + "x");
                lore.add("&7• Chests / Containers: &f" + s.getChestAmount() + "x");
                lore.add("&7• Player Vaults: &f" + s.getVaultAmount() + "x");
                lore.add("&7Last Seen: &8" + DATE_FORMAT.format(new Date(s.getLastSeen())));
                lore.add("&f");
                lore.add("<#10B981>&l▶ Left-Click: Inspect All Locations");
                lore.add("<#EF4444>&l▶ Shift-Right-Click: Wipe All from Player");

                meta.setLore(lore.stream().map(ColorUtil::colorize).toList());
                head.setItemMeta(meta);
            }
            gui.setItem(i - start, head);
        }

        // Pagination
        if (currentPage > 0) {
            gui.setItem(47, createGuiItem(Material.ARROW, "<#38BDF8>&l◀ Previous Page", List.of("&7Go to page " + currentPage)));
        }
        if (currentPage < maxPages - 1) {
            gui.setItem(51, createGuiItem(Material.ARROW, "<#38BDF8>&lNext Page ▶", List.of("&7Go to page " + (currentPage + 2))));
        }

        // Controls
        gui.setItem(45, createGuiItem(Material.OAK_DOOR, "<#E2E8F0>&l⬅ Back to Editor", List.of("&7Return to item editor.")));
        gui.setItem(48, createGuiItem(Material.HOPPER, "<#F59E0B>&lSort: <#FCD34D>" + sortMode.getDisplay(), List.of(
                "&7Click to cycle sorting modes:",
                "&7• Most Amount (Dupe Detection)",
                "&7• Newest Activity",
                "&7• Highest Margin",
                "&f",
                "<#FCD34D>&l▶ Click to Cycle"
        )));
        gui.setItem(49, createGuiItem(Material.NETHER_STAR, "<#38BDF8>&lCirculation Ledger", List.of(
                "&7Item: &#FFFFFF" + itemName,
                "&7Total Recorded: <#10B981>&l" + totalCirculating + "x",
                "&7Active Holders: &e" + totalPlayers,
                "&f",
                "&8Zero-lag async ledger index"
        )));
        gui.setItem(53, createGuiItem(Material.BARRIER, "<#EF4444>&lClose & Exit", List.of("&7Close tracker.")));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof TrackerHolder holder)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 45) { // Back
            DCEItem dce = itemRegistry.getItem(holder.getItemId());
            if (dce != null && DCCosmetics.getInstance().getItemModule() != null) {
                DCCosmetics.getInstance().getItemModule().getItemEditorGUI().open(player, dce);
            } else {
                player.closeInventory();
            }
            return;
        } else if (slot == 48) { // Cycle sort mode
            ItemTrackerManager.SortMode cur = holder.getSortMode();
            ItemTrackerManager.SortMode next = switch (cur) {
                case MOST_AMOUNT -> ItemTrackerManager.SortMode.NEWEST;
                case NEWEST -> ItemTrackerManager.SortMode.HIGHEST_MARGIN;
                case HIGHEST_MARGIN -> ItemTrackerManager.SortMode.MOST_AMOUNT;
            };
            open(player, holder.getItemId(), 0, next);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
            return;
        } else if (slot == 47 && holder.getPage() > 0) {
            open(player, holder.getItemId(), holder.getPage() - 1, holder.getSortMode());
            return;
        } else if (slot == 51) {
            open(player, holder.getItemId(), holder.getPage() + 1, holder.getSortMode());
            return;
        } else if (slot == 53) {
            player.closeInventory();
            return;
        }

        if (slot >= 0 && slot < 45) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;

            List<ItemTrackerManager.UserItemSummary> summaries = trackerManager.getPlayerSummaries(holder.getItemId(), holder.getSortMode());
            int targetIdx = holder.getPage() * 45 + slot;
            if (targetIdx >= summaries.size()) return;

            ItemTrackerManager.UserItemSummary target = summaries.get(targetIdx);

            // Shift-Right-Click: Wipe all instances from this player
            if (event.isRightClick() && event.isShiftClick()) {
                ItemTrackerManager.PurgeResult result = trackerManager.deleteItemForPlayer(holder.getItemId(), target.getUuid());
                player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.8f, 1.2f);
                DCMessage.sendWarning(player, "Purge summary for &#FCD34D" + target.getUsername() + " &#7(Item: &#FFFFFF" + holder.getItemId() + "&7):");
                if (result.getPlayerInventoryRemoved() > 0) {
                    DCMessage.sendGeneral(player, "  • Inventory: &#10B981" + result.getPlayerInventoryRemoved() + "x removed");
                }
                if (result.getPlayerVaultsRemoved() > 0) {
                    DCMessage.sendGeneral(player, "  • PlayerVaults: &#A855F7" + result.getPlayerVaultsRemoved() + "x removed");
                }
                if (result.getContainerRemoved() > 0) {
                    DCMessage.sendGeneral(player, "  • Containers: &#38BDF8" + result.getContainerRemoved() + "x removed");
                }
                if (result.getTotalRemoved() == 0 && result.getFailedContainers().isEmpty()) {
                    DCMessage.sendGeneral(player, "  • No active items found in storage.");
                }
                if (!result.getFailedContainers().isEmpty()) {
                    DCMessage.sendError(player, "  ⚠ Failed to access " + result.getFailedContainers().size() + " container(s):");
                    for (String failLoc : result.getFailedContainers()) {
                        DCMessage.sendError(player, "    - " + failLoc);
                    }
                    DCMessage.sendWarning(player, "Records for failed containers have been preserved.");
                }
                open(player, holder.getItemId(), holder.getPage(), holder.getSortMode());
                return;
            }

            // Left-Click: Open detailed locations view
            new PlayerLocationsGUI(trackerManager, itemRegistry, this).open(player, holder.getItemId(), target.getUuid(), target.getUsername(), 0);
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
