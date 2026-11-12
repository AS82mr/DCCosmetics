package dc.dcessentials.modules.items.tracker;

import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.model.ItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.*;

public class PlayerLocationsGUI implements Listener {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final ItemTrackerManager trackerManager;
    private final ItemRegistry itemRegistry;
    private final ItemTrackerGUI parentGUI;

    public static class LocationsHolder implements InventoryHolder {
        private final String itemId;
        private final UUID targetUuid;
        private final String targetName;
        private final int page;

        public LocationsHolder(String itemId, UUID targetUuid, String targetName, int page) {
            this.itemId = itemId;
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.page = page;
        }

        public String getItemId() { return itemId; }
        public UUID getTargetUuid() { return targetUuid; }
        public String getTargetName() { return targetName; }
        public int getPage() { return page; }

        @Override
        public Inventory getInventory() { return null; }
    }

    public PlayerLocationsGUI(ItemTrackerManager trackerManager, ItemRegistry itemRegistry, ItemTrackerGUI parentGUI) {
        this.trackerManager = trackerManager;
        this.itemRegistry = itemRegistry;
        this.parentGUI = parentGUI;
    }

    public void open(Player player, String itemId, UUID targetUuid, String targetName, int page) {
        List<ItemLocationRecord> records = trackerManager.getRecordsForPlayer(itemId, targetUuid);
        int total = records.size();
        int maxPages = Math.max(1, (int) Math.ceil((double) total / 45));
        int currentPage = Math.max(0, Math.min(page, maxPages - 1));

        String title = ColorUtil.colorize("<#1E293B>&lStorage: &f" + targetName + " &8(" + total + " spots)");
        LocationsHolder holder = new LocationsHolder(itemId, targetUuid, targetName, currentPage);
        Inventory gui = Bukkit.createInventory(holder, 54, title);

        // Bottom border
        ItemStack border = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "&7", null);
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, border);
        }

        int start = currentPage * 45;
        int end = Math.min(start + 45, total);

        for (int i = start; i < end; i++) {
            ItemLocationRecord rec = records.get(i);
            Material icon = switch (rec.getLocationType()) {
                case INVENTORY -> Material.CHEST_MINECART;
                case CHEST -> Material.CHEST;
                case SHULKER -> Material.SHULKER_BOX;
                case PLAYER_VAULT -> Material.ENDER_CHEST;
            };

            String typeDisplay = switch (rec.getLocationType()) {
                case INVENTORY -> "<#38BDF8>&lLive Player Inventory";
                case CHEST -> "<#FCD34D>&lChest / Storage Container";
                case SHULKER -> "<#D946EF>&lShulker Box";
                case PLAYER_VAULT -> "<#A855F7>&lPlayerVault #" + rec.getVaultNumber();
            };

            List<String> lore = new ArrayList<>();
            lore.add("&7Quantity: <#FCD34D>&l" + rec.getAmount() + "x");
            lore.add("&7Instance UUID: &8" + rec.getInstanceUuid());
            if (rec.getLocationType() == ItemLocationRecord.LocationType.CHEST || rec.getLocationType() == ItemLocationRecord.LocationType.SHULKER) {
                lore.add("&7Location: &f" + rec.getWorld() + " &7[&e" + rec.getX() + ", " + rec.getY() + ", " + rec.getZ() + "&7]");
                lore.add("&f");
                lore.add("<#10B981>&l▶ Left-Click: Teleport to Container");
                lore.add("<#EF4444>&l▶ Right-Click: Seize / Remove Item");
            } else if (rec.getLocationType() == ItemLocationRecord.LocationType.INVENTORY) {
                lore.add("&7Status: &aPlayer Personal Inventory");
                lore.add("&f");
                lore.add("<#EF4444>&l▶ Click to Seize / Remove Item");
            } else {
                lore.add("&7Status: &dPlayerVault Storage #" + rec.getVaultNumber());
                lore.add("&f");
                lore.add("<#EF4444>&l▶ Click to Seize / Remove from Vault");
            }
            lore.add("&8Last seen: " + DATE_FORMAT.format(new Date(rec.getLastSeenTimestamp())));

            gui.setItem(i - start, createGuiItem(icon, typeDisplay, lore));
        }

        // Pagination
        if (currentPage > 0) {
            gui.setItem(47, createGuiItem(Material.ARROW, "<#38BDF8>&l◀ Previous Page", List.of("&7Go to page " + currentPage)));
        }
        if (currentPage < maxPages - 1) {
            gui.setItem(51, createGuiItem(Material.ARROW, "<#38BDF8>&lNext Page ▶", List.of("&7Go to page " + (currentPage + 2))));
        }

        gui.setItem(45, createGuiItem(Material.OAK_DOOR, "<#E2E8F0>&l⬅ Back to Player List", List.of("&7Return to item tracker overview.")));
        gui.setItem(49, createGuiItem(Material.TNT, "<#EF4444>&lPURGE ALL FOR THIS PLAYER", List.of(
                "&7Wipes every single instance of this item",
                "&7from &f" + targetName + "&7's storage.",
                "&f",
                "<#EF4444>&l▶ Click to Purge All"
        )));
        gui.setItem(53, createGuiItem(Material.BARRIER, "<#EF4444>&lClose & Exit", List.of("&7Close.")));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof LocationsHolder holder)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 45) { // Back
            parentGUI.open(player, holder.getItemId(), 0, ItemTrackerManager.SortMode.MOST_AMOUNT);
            return;
        } else if (slot == 47 && holder.getPage() > 0) {
            open(player, holder.getItemId(), holder.getTargetUuid(), holder.getTargetName(), holder.getPage() - 1);
            return;
        } else if (slot == 51) {
            open(player, holder.getItemId(), holder.getTargetUuid(), holder.getTargetName(), holder.getPage() + 1);
            return;
        } else if (slot == 49) { // Purge all
            ItemTrackerManager.PurgeResult result = trackerManager.deleteItemForPlayer(holder.getItemId(), holder.getTargetUuid());
            player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.8f, 1.2f);
            DCMessage.sendWarning(player, "Purge summary for &#FCD34D" + holder.getTargetName() + " &#7(Item: &#FFFFFF" + holder.getItemId() + "&7):");
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
            parentGUI.open(player, holder.getItemId(), 0, ItemTrackerManager.SortMode.MOST_AMOUNT);
            return;
        } else if (slot == 53) {
            player.closeInventory();
            return;
        }

        if (slot >= 0 && slot < 45) {
            List<ItemLocationRecord> records = trackerManager.getRecordsForPlayer(holder.getItemId(), holder.getTargetUuid());
            int targetIdx = holder.getPage() * 45 + slot;
            if (targetIdx >= records.size()) return;

            ItemLocationRecord rec = records.get(targetIdx);

            if (rec.getLocationType() == ItemLocationRecord.LocationType.CHEST || rec.getLocationType() == ItemLocationRecord.LocationType.SHULKER) {
                if (event.isLeftClick()) {
                    // Teleport admin directly to container
                    World w = Bukkit.getWorld(rec.getWorld());
                    if (w != null) {
                        Location dest = new Location(w, rec.getX() + 0.5, rec.getY() + 1.0, rec.getZ() + 0.5);
                        player.teleport(dest);
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                        DCMessage.sendGeneral(player, "Teleported to container at &#FCD34D" + rec.getWorld() + " " + rec.getX() + ", " + rec.getY() + ", " + rec.getZ() + "&#0CBF93!");
                        player.closeInventory();
                    } else {
                        DCMessage.sendError(player, "World " + rec.getWorld() + " is currently not loaded!");
                    }
                    return;
                } else if (event.isRightClick()) {
                    // Seize single item from container
                    int seized = trackerManager.seizeItemFromContainer(holder.getItemId(), rec.getInstanceUuid(), rec.getWorld(), rec.getX(), rec.getY(), rec.getZ());
                    if (seized > 0) {
                        trackerManager.removeSingleRecord(holder.getItemId(), rec.getInstanceUuid());
                        player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.8f, 1.2f);
                        DCMessage.sendGeneral(player, "Seized &#10B981" + seized + "x &#FFFFFF" + holder.getItemId() + " from container!");
                    } else if (seized == 0) {
                        trackerManager.removeSingleRecord(holder.getItemId(), rec.getInstanceUuid());
                        player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.8f, 1.2f);
                        DCMessage.sendWarning(player, "Item was not found in container (cleared stale ledger record).");
                    } else if (seized == -1) {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                        DCMessage.sendError(player, "Cannot seize: World &#FCD34D" + rec.getWorld() + " &#EF4444is not loaded! Record preserved.");
                        return;
                    } else {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                        DCMessage.sendError(player, "Cannot seize: Container block at &#FCD34D" + rec.getWorld() + " " + rec.getX() + ", " + rec.getY() + ", " + rec.getZ() + " &#EF4444no longer exists! Record preserved.");
                        return;
                    }
                    open(player, holder.getItemId(), holder.getTargetUuid(), holder.getTargetName(), holder.getPage());
                    return;
                }
            } else if (rec.getLocationType() == ItemLocationRecord.LocationType.INVENTORY) {
                Player target = Bukkit.getPlayer(holder.getTargetUuid());
                if (target != null && target.isOnline()) {
                    for (int i = 0; i < target.getInventory().getSize(); i++) {
                        ItemStack st = target.getInventory().getItem(i);
                        if (st != null && itemRegistry.isDCEItem(st) &&
                                holder.getItemId().equalsIgnoreCase(itemRegistry.getItemId(st))) {
                            String instUuid = st.getItemMeta() != null
                                    ? st.getItemMeta().getPersistentDataContainer().get(ItemTrackerManager.INSTANCE_UUID_KEY, org.bukkit.persistence.PersistentDataType.STRING)
                                    : null;
                            if (rec.getInstanceUuid().equals(instUuid) || instUuid == null) {
                                target.getInventory().setItem(i, null);
                                break;
                            }
                        }
                    }
                    target.updateInventory();
                }
                trackerManager.removeSingleRecord(holder.getItemId(), rec.getInstanceUuid());
                player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.8f, 1.2f);
                DCMessage.sendGeneral(player, "Seized item instance from player's inventory!");
                open(player, holder.getItemId(), holder.getTargetUuid(), holder.getTargetName(), holder.getPage());
                return;
            } else if (rec.getLocationType() == ItemLocationRecord.LocationType.PLAYER_VAULT) {
                int removed = dc.dcessentials.modules.items.hook.PlayerVaultsHook.removeSingleItemFromVault(
                        rec.getLastUserUuid(), rec.getVaultNumber(), rec.getItemId(), rec.getInstanceUuid(), itemRegistry);
                trackerManager.removeSingleRecord(holder.getItemId(), rec.getInstanceUuid());
                player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.8f, 1.2f);
                if (removed > 0) {
                    DCMessage.sendGeneral(player, "Seized item instance from PlayerVault #" + rec.getVaultNumber() + "!");
                } else {
                    DCMessage.sendWarning(player, "Item removed from ledger (stale or already cleared from vault).");
                }
                open(player, holder.getItemId(), holder.getTargetUuid(), holder.getTargetName(), holder.getPage());
                return;
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
