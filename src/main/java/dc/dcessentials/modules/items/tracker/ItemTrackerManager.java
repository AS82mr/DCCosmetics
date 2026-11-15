package dc.dcessentials.modules.items.tracker;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.items.model.ItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ItemTrackerManager implements Listener {

    public static final NamespacedKey INSTANCE_UUID_KEY = new NamespacedKey(DCCosmetics.getInstance(), "instance_uuid");
    public static final NamespacedKey LAST_USER_UUID_KEY = new NamespacedKey(DCCosmetics.getInstance(), "last_user_uuid");
    public static final NamespacedKey LAST_USER_NAME_KEY = new NamespacedKey(DCCosmetics.getInstance(), "last_user_name");

    public enum SortMode {
        MOST_AMOUNT("Most Amount (Dupe Check)"),
        NEWEST("Newest Activity"),
        HIGHEST_MARGIN("Highest Margin");

        private final String display;
        SortMode(String display) { this.display = display; }
        public String getDisplay() { return display; }
    }

    public static class UserItemSummary {
        private final UUID uuid;
        private final String username;
        private int totalAmount;
        private int invAmount;
        private int chestAmount;
        private int vaultAmount;
        private long lastSeen;

        public UserItemSummary(UUID uuid, String username) {
            this.uuid = uuid;
            this.username = username;
        }

        public UUID getUuid() { return uuid; }
        public String getUsername() { return username; }
        public int getTotalAmount() { return totalAmount; }
        public int getInvAmount() { return invAmount; }
        public int getChestAmount() { return chestAmount; }
        public int getVaultAmount() { return vaultAmount; }
        public long getLastSeen() { return lastSeen; }

        public void add(ItemLocationRecord.LocationType type, int amount, long timestamp) {
            this.totalAmount += amount;
            if (timestamp > this.lastSeen) this.lastSeen = timestamp;
            switch (type) {
                case INVENTORY -> this.invAmount += amount;
                case CHEST, SHULKER -> this.chestAmount += amount;
                case PLAYER_VAULT -> this.vaultAmount += amount;
            }
        }
    }

    private final ItemRegistry itemRegistry;
    private final File dataFile;
    // Map: itemId -> (instanceUuid -> ItemLocationRecord)
    private final Map<String, Map<String, ItemLocationRecord>> ledger = new ConcurrentHashMap<>();
    private final java.util.concurrent.BlockingQueue<ItemLocationRecord> dbSaveQueue = new java.util.concurrent.LinkedBlockingQueue<>();

    public ItemTrackerManager(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
        File dataFolder = new File(DCCosmetics.getInstance().getDataFolder(), "data");
        dataFolder.mkdirs();
        this.dataFile = new File(dataFolder, "item_tracker_ledger.yml");
        loadLedger();
        startAsyncDbWorker();
    }

    public String recordItem(ItemStack stack, Player player, ItemLocationRecord.LocationType type, Location loc, int vaultNum) {
        if (stack == null || stack.getType().isAir() || !itemRegistry.isDCEItem(stack)) return null;

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemId = itemRegistry.getItemId(stack);
        if (itemId == null) return null;

        // Do not stamp per-instance tracking UUIDs onto stackable items/scrolls (breaks vanilla stacking)
        if (itemRegistry.isStackableItem(stack)) {
            return null;
        }

        String instanceUuid = pdc.get(INSTANCE_UUID_KEY, PersistentDataType.STRING);
        if (instanceUuid == null) {
            instanceUuid = UUID.randomUUID().toString();
            pdc.set(INSTANCE_UUID_KEY, PersistentDataType.STRING, instanceUuid);
        }

        UUID userUuid = player != null ? player.getUniqueId() : null;
        String userName = player != null ? player.getName() : "Unknown";

        if (userUuid != null) {
            pdc.set(LAST_USER_UUID_KEY, PersistentDataType.STRING, userUuid.toString());
            pdc.set(LAST_USER_NAME_KEY, PersistentDataType.STRING, userName);
        } else {
            String recorded = pdc.get(LAST_USER_UUID_KEY, PersistentDataType.STRING);
            if (recorded != null) {
                try { userUuid = UUID.fromString(recorded); } catch (Exception ignored) {}
            }
            String recName = pdc.get(LAST_USER_NAME_KEY, PersistentDataType.STRING);
            if (recName != null) userName = recName;
        }

        stack.setItemMeta(meta);

        String world = loc != null && loc.getWorld() != null ? loc.getWorld().getName() : (player != null ? player.getWorld().getName() : "world");
        int x = loc != null ? loc.getBlockX() : (player != null ? player.getLocation().getBlockX() : 0);
        int y = loc != null ? loc.getBlockY() : (player != null ? player.getLocation().getBlockY() : 0);
        int z = loc != null ? loc.getBlockZ() : (player != null ? player.getLocation().getBlockZ() : 0);

        ItemLocationRecord record = new ItemLocationRecord(
                itemId.toLowerCase(),
                instanceUuid,
                userUuid,
                userName,
                type,
                world,
                x,
                y,
                z,
                vaultNum,
                stack.getAmount(),
                System.currentTimeMillis()
        );

        ledger.computeIfAbsent(itemId.toLowerCase(), k -> new ConcurrentHashMap<>()).put(instanceUuid, record);
        dbSaveQueue.offer(record);
        return instanceUuid;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (current != null && itemRegistry.isDCEItem(current)) {
            recordItem(current, player, ItemLocationRecord.LocationType.INVENTORY, player.getLocation(), 0);
        }
        if (cursor != null && itemRegistry.isDCEItem(cursor)) {
            recordItem(cursor, player, ItemLocationRecord.LocationType.INVENTORY, player.getLocation(), 0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack stack = event.getItem().getItemStack();
            if (itemRegistry.isDCEItem(stack)) {
                recordItem(stack, player, ItemLocationRecord.LocationType.INVENTORY, player.getLocation(), 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (itemRegistry.isDCEItem(stack)) {
            recordItem(stack, event.getPlayer(), ItemLocationRecord.LocationType.INVENTORY, event.getPlayer().getLocation(), 0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();

        // 1. Check Container Closures (Chests, Shulkers, Barrels, Hoppers, etc.)
        if (holder instanceof Container container) {
            Location loc = container.getLocation();
            ItemLocationRecord.LocationType locType = container.getType().name().contains("SHULKER")
                    ? ItemLocationRecord.LocationType.SHULKER
                    : ItemLocationRecord.LocationType.CHEST;

            for (int i = 0; i < top.getSize(); i++) {
                ItemStack item = top.getItem(i);
                if (item != null && itemRegistry.isDCEItem(item)) {
                    recordItem(item, player, locType, loc, 0);
                    top.setItem(i, item);
                }
            }
        } else if (holder instanceof DoubleChest doubleChest) {
            Location loc = (doubleChest.getLeftSide() instanceof org.bukkit.block.Chest leftChest)
                    ? leftChest.getLocation()
                    : doubleChest.getLocation();
            for (int i = 0; i < top.getSize(); i++) {
                ItemStack item = top.getItem(i);
                if (item != null && itemRegistry.isDCEItem(item)) {
                    recordItem(item, player, ItemLocationRecord.LocationType.CHEST, loc, 0);
                    top.setItem(i, item);
                }
            }
        } else {
            // Check for PlayerVaults inventory
            String title = event.getView().getTitle().toLowerCase();
            if (title.contains("vault") || (holder != null && holder.getClass().getName().toLowerCase().contains("vault"))) {
                int vaultNum = 1;
                try {
                    if (holder != null) {
                        for (java.lang.reflect.Method m : holder.getClass().getMethods()) {
                            if ((m.getName().equalsIgnoreCase("getNumber") || m.getName().equalsIgnoreCase("getVault") ||
                                    m.getName().equalsIgnoreCase("getVaultNumber") || m.getName().equalsIgnoreCase("getId"))
                                    && m.getParameterCount() == 0 && (m.getReturnType() == int.class || m.getReturnType() == Integer.class)) {
                                Object res = m.invoke(holder);
                                if (res instanceof Integer num && num > 0) {
                                    vaultNum = num;
                                    break;
                                }
                            }
                        }
                    }
                    if (vaultNum == 1) {
                        String clean = org.bukkit.ChatColor.stripColor(event.getView().getTitle()).replaceAll("<[^>]*>", "").replaceAll("[§&][0-9a-fk-orA-FK-ORxX]", "");
                        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)vault.*?(\\d+)").matcher(clean);
                        if (matcher.find()) {
                            vaultNum = Integer.parseInt(matcher.group(1));
                        } else {
                            java.util.regex.Matcher numMatcher = java.util.regex.Pattern.compile("#?(\\d+)").matcher(clean);
                            if (numMatcher.find()) {
                                vaultNum = Integer.parseInt(numMatcher.group(1));
                            }
                        }
                    }
                } catch (Exception ignored) {}

                for (int i = 0; i < top.getSize(); i++) {
                    ItemStack item = top.getItem(i);
                    if (item != null && itemRegistry.isDCEItem(item)) {
                        recordItem(item, player, ItemLocationRecord.LocationType.PLAYER_VAULT, null, vaultNum);
                        top.setItem(i, item);
                    }
                }
            }
        }

        // 2. Refresh Player's Own Inventory DCE Items and reconcile ghost multiples
        Set<String> activeUuids = new HashSet<>();
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && itemRegistry.isDCEItem(item)) {
                String instanceUuid = recordItem(item, player, ItemLocationRecord.LocationType.INVENTORY, player.getLocation(), 0);
                if (instanceUuid != null) {
                    activeUuids.add(instanceUuid);
                }
                player.getInventory().setItem(i, item); // Persist UUID back into slot
            }
        }
        reconcilePlayerInventory(player.getUniqueId(), activeUuids);
    }

    public void reconcilePlayerInventory(UUID playerUuid, Set<String> activeUuids) {
        if (playerUuid == null) return;
        for (Map<String, ItemLocationRecord> itemRecords : ledger.values()) {
            Iterator<Map.Entry<String, ItemLocationRecord>> it = itemRecords.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, ItemLocationRecord> entry = it.next();
                ItemLocationRecord rec = entry.getValue();
                if (playerUuid.equals(rec.getLastUserUuid()) && rec.getLocationType() == ItemLocationRecord.LocationType.INVENTORY) {
                    if (!activeUuids.contains(rec.getInstanceUuid())) {
                        it.remove();
                        deleteRecordFromDbAsync(rec.getInstanceUuid());
                    }
                }
            }
        }
    }

    public boolean removeSingleRecord(String itemId, String instanceUuid) {
        if (itemId == null || instanceUuid == null) return false;
        Map<String, ItemLocationRecord> map = ledger.get(itemId.toLowerCase());
        if (map != null) {
            ItemLocationRecord removed = map.remove(instanceUuid);
            if (removed != null) {
                deleteRecordFromDbAsync(instanceUuid);
                return true;
            }
        }
        return false;
    }

    public List<UserItemSummary> getPlayerSummaries(String itemId, SortMode sortMode) {
        if (itemId == null) return Collections.emptyList();
        Map<String, ItemLocationRecord> records = ledger.get(itemId.toLowerCase());
        if (records == null || records.isEmpty()) return Collections.emptyList();

        Map<UUID, UserItemSummary> summaries = new HashMap<>();
        for (ItemLocationRecord rec : records.values()) {
            UUID u = rec.getLastUserUuid() != null ? rec.getLastUserUuid() : new UUID(0, 0);
            UserItemSummary summary = summaries.computeIfAbsent(u, k -> new UserItemSummary(u, rec.getLastUserName()));
            summary.add(rec.getLocationType(), rec.getAmount(), rec.getLastSeenTimestamp());
        }

        List<UserItemSummary> list = new ArrayList<>(summaries.values());
        switch (sortMode) {
            case MOST_AMOUNT -> list.sort((a, b) -> Integer.compare(b.getTotalAmount(), a.getTotalAmount()));
            case NEWEST -> list.sort((a, b) -> Long.compare(b.getLastSeen(), a.getLastSeen()));
            case HIGHEST_MARGIN -> list.sort((a, b) -> {
                int marginA = a.getChestAmount() + a.getVaultAmount();
                int marginB = b.getChestAmount() + b.getVaultAmount();
                return Integer.compare(marginB, marginA);
            });
        }
        return list;
    }

    public List<ItemLocationRecord> getRecordsForPlayer(String itemId, UUID playerUuid) {
        if (itemId == null || playerUuid == null) return Collections.emptyList();
        Map<String, ItemLocationRecord> records = ledger.get(itemId.toLowerCase());
        if (records == null) return Collections.emptyList();

        List<ItemLocationRecord> list = new ArrayList<>();
        for (ItemLocationRecord rec : records.values()) {
            if (playerUuid.equals(rec.getLastUserUuid())) {
                list.add(rec);
            }
        }
        list.sort((a, b) -> Long.compare(b.getLastSeenTimestamp(), a.getLastSeenTimestamp()));
        return list;
    }

    public static class PurgeResult {
        private int playerInventoryRemoved = 0;
        private int playerVaultsRemoved = 0;
        private int containerRemoved = 0;
        private final List<String> failedContainers = new ArrayList<>();

        public int getPlayerInventoryRemoved() { return playerInventoryRemoved; }
        public int getPlayerVaultsRemoved() { return playerVaultsRemoved; }
        public int getContainerRemoved() { return containerRemoved; }
        public int getTotalRemoved() { return playerInventoryRemoved + playerVaultsRemoved + containerRemoved; }
        public List<String> getFailedContainers() { return failedContainers; }
    }

    /**
     * Seizes item instances from a world container block (Chest, DoubleChest, Shulker, Barrel, etc.)
     * @return Number of items seized (> 0), 0 if container checked and item not found,
     *         -1 if world not loaded, -2 if no container found at location.
     */
    public int seizeItemFromContainer(String itemId, String instanceUuid, String worldName, int x, int y, int z) {
        if (itemId == null || worldName == null) return -2;
        org.bukkit.World w = Bukkit.getWorld(worldName);
        if (w == null) return -1;

        int cx = x >> 4;
        int cz = z >> 4;
        if (!w.isChunkLoaded(cx, cz)) {
            w.getChunkAt(cx, cz).load();
        }

        org.bukkit.block.Block block = w.getBlockAt(x, y, z);
        BlockState state = block.getState();

        // Check adjacent horizontal blocks if block itself isn't a container (e.g. DoubleChest midpoint rounding)
        if (!(state instanceof Container)) {
            org.bukkit.block.BlockFace[] faces = {
                    org.bukkit.block.BlockFace.NORTH,
                    org.bukkit.block.BlockFace.SOUTH,
                    org.bukkit.block.BlockFace.EAST,
                    org.bukkit.block.BlockFace.WEST
            };
            for (org.bukkit.block.BlockFace face : faces) {
                org.bukkit.block.Block adj = block.getRelative(face);
                if (adj.getState() instanceof Container) {
                    block = adj;
                    state = adj.getState();
                    break;
                }
            }
        }

        if (!(state instanceof Container container)) {
            return -2;
        }

        Inventory liveInv = container.getInventory();
        Inventory snapshotInv = container.getSnapshotInventory();

        return seizeFromInventory(container, liveInv, snapshotInv, itemId, instanceUuid);
    }

    private int seizeFromInventory(Container container, Inventory liveInv, Inventory snapshotInv, String itemId, String instanceUuid) {
        if (liveInv == null) return 0;
        int removed = 0;

        // Pass 1: Try exact matching by instance UUID
        if (instanceUuid != null) {
            for (int i = 0; i < liveInv.getSize(); i++) {
                ItemStack st = liveInv.getItem(i);
                if (st == null || st.getType().isAir()) continue;

                if (itemRegistry.isDCEItem(st) && itemId.equalsIgnoreCase(itemRegistry.getItemId(st))) {
                    String instUuid = st.getItemMeta() != null
                            ? st.getItemMeta().getPersistentDataContainer().get(INSTANCE_UUID_KEY, PersistentDataType.STRING)
                            : null;
                    if (instanceUuid.equals(instUuid)) {
                        removed += st.getAmount();
                        liveInv.setItem(i, null);
                        if (snapshotInv != null && i < snapshotInv.getSize()) snapshotInv.setItem(i, null);
                        if (container instanceof org.bukkit.block.Chest && liveInv.getHolder() instanceof DoubleChest dc) {
                            if (dc.getLeftSide() instanceof Container c1 && i < c1.getSnapshotInventory().getSize()) c1.getSnapshotInventory().setItem(i, null);
                            if (dc.getRightSide() instanceof Container c2 && (i - 27) >= 0 && (i - 27) < c2.getSnapshotInventory().getSize()) c2.getSnapshotInventory().setItem(i - 27, null);
                        }
                        break;
                    }
                }

                // Check nested shulkers inside the container
                if (st.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta bsm &&
                        bsm.getBlockState() instanceof org.bukkit.block.ShulkerBox shulkerBox) {
                    Inventory sInv = shulkerBox.getInventory();
                    boolean shulkerMod = false;
                    for (int s = 0; s < sInv.getSize(); s++) {
                        ItemStack sItem = sInv.getItem(s);
                        if (sItem != null && itemRegistry.isDCEItem(sItem) && itemId.equalsIgnoreCase(itemRegistry.getItemId(sItem))) {
                            String instUuid = sItem.getItemMeta() != null
                                    ? sItem.getItemMeta().getPersistentDataContainer().get(INSTANCE_UUID_KEY, PersistentDataType.STRING)
                                    : null;
                            if (instanceUuid.equals(instUuid)) {
                                removed += sItem.getAmount();
                                sInv.setItem(s, null);
                                shulkerMod = true;
                                break;
                            }
                        }
                    }
                    if (shulkerMod) {
                        bsm.setBlockState(shulkerBox);
                        st.setItemMeta(bsm);
                        liveInv.setItem(i, st);
                        if (snapshotInv != null && i < snapshotInv.getSize()) snapshotInv.setItem(i, st);
                        break;
                    }
                }
            }
        }

        // Pass 2: Fallback matching by Item ID if instance UUID didn't match (e.g. stack splits / merges)
        if (removed == 0) {
            for (int i = 0; i < liveInv.getSize(); i++) {
                ItemStack st = liveInv.getItem(i);
                if (st == null || st.getType().isAir()) continue;

                if (itemRegistry.isDCEItem(st) && itemId.equalsIgnoreCase(itemRegistry.getItemId(st))) {
                    removed += st.getAmount();
                    liveInv.setItem(i, null);
                    if (snapshotInv != null && i < snapshotInv.getSize()) snapshotInv.setItem(i, null);
                    if (container instanceof org.bukkit.block.Chest && liveInv.getHolder() instanceof DoubleChest dc) {
                        if (dc.getLeftSide() instanceof Container c1 && i < c1.getSnapshotInventory().getSize()) c1.getSnapshotInventory().setItem(i, null);
                        if (dc.getRightSide() instanceof Container c2 && (i - 27) >= 0 && (i - 27) < c2.getSnapshotInventory().getSize()) c2.getSnapshotInventory().setItem(i - 27, null);
                    }
                    break;
                }

                // Nested shulker fallback
                if (st.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta bsm &&
                        bsm.getBlockState() instanceof org.bukkit.block.ShulkerBox shulkerBox) {
                    Inventory sInv = shulkerBox.getInventory();
                    boolean shulkerMod = false;
                    for (int s = 0; s < sInv.getSize(); s++) {
                        ItemStack sItem = sInv.getItem(s);
                        if (sItem != null && itemRegistry.isDCEItem(sItem) && itemId.equalsIgnoreCase(itemRegistry.getItemId(sItem))) {
                            removed += sItem.getAmount();
                            sInv.setItem(s, null);
                            shulkerMod = true;
                            break;
                        }
                    }
                    if (shulkerMod) {
                        bsm.setBlockState(shulkerBox);
                        st.setItemMeta(bsm);
                        liveInv.setItem(i, st);
                        if (snapshotInv != null && i < snapshotInv.getSize()) snapshotInv.setItem(i, st);
                        break;
                    }
                }
            }
        }

        if (removed > 0) {
            for (org.bukkit.entity.HumanEntity viewer : liveInv.getViewers()) {
                if (viewer instanceof Player p) {
                    p.updateInventory();
                }
            }
        }

        return removed;
    }

    public PurgeResult deleteItemForPlayer(String itemId, UUID playerUuid) {
        if (itemId == null || playerUuid == null) return new PurgeResult();
        PurgeResult result = new PurgeResult();

        // 1. Remove from online player inventory if online
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack != null && itemRegistry.isDCEItem(stack) &&
                        itemId.equalsIgnoreCase(itemRegistry.getItemId(stack))) {
                    result.playerInventoryRemoved += stack.getAmount();
                    player.getInventory().setItem(i, null);
                }
            }
            player.updateInventory();
        }

        // 2. Clear from containers and vaults belonging to player
        Map<String, ItemLocationRecord> records = ledger.get(itemId.toLowerCase());
        if (records != null) {
            Iterator<Map.Entry<String, ItemLocationRecord>> it = records.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, ItemLocationRecord> entry = it.next();
                ItemLocationRecord rec = entry.getValue();
                if (playerUuid.equals(rec.getLastUserUuid())) {
                    if (rec.getLocationType() == ItemLocationRecord.LocationType.CHEST || rec.getLocationType() == ItemLocationRecord.LocationType.SHULKER) {
                        int seized = seizeItemFromContainer(itemId, rec.getInstanceUuid(), rec.getWorld(), rec.getX(), rec.getY(), rec.getZ());
                        if (seized > 0) {
                            result.containerRemoved += seized;
                            it.remove();
                            deleteRecordFromDbAsync(rec.getInstanceUuid());
                        } else if (seized == 0) {
                            // Container was inspected and item is already absent (stale record)
                            it.remove();
                            deleteRecordFromDbAsync(rec.getInstanceUuid());
                        } else {
                            // Container could not be accessed (-1 unloaded world, -2 container block missing)
                            // Preserve record so admin doesn't lose tracking!
                            result.failedContainers.add(rec.getWorld() + " [" + rec.getX() + ", " + rec.getY() + ", " + rec.getZ() + "]");
                        }
                    } else if (rec.getLocationType() == ItemLocationRecord.LocationType.PLAYER_VAULT) {
                        int vaultRemoved = dc.dcessentials.modules.items.hook.PlayerVaultsHook.removeAllItemFromVault(playerUuid, rec.getVaultNumber(), itemId, itemRegistry);
                        result.playerVaultsRemoved += vaultRemoved;
                        it.remove();
                        deleteRecordFromDbAsync(rec.getInstanceUuid());
                    } else if (rec.getLocationType() == ItemLocationRecord.LocationType.INVENTORY) {
                        it.remove();
                        deleteRecordFromDbAsync(rec.getInstanceUuid());
                    }
                }
            }
        }

        return result;
    }

    public int deleteItemGlobally(String itemId) {
        if (itemId == null) return 0;
        int removed = 0;

        // 1. Strip from all online player inventories, armor, and ender chests
        for (Player online : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < online.getInventory().getSize(); i++) {
                ItemStack stack = online.getInventory().getItem(i);
                if (stack != null && itemRegistry.isDCEItem(stack) &&
                        itemId.equalsIgnoreCase(itemRegistry.getItemId(stack))) {
                    removed += stack.getAmount();
                    online.getInventory().setItem(i, null);
                }
            }
            for (int i = 0; i < online.getEnderChest().getSize(); i++) {
                ItemStack stack = online.getEnderChest().getItem(i);
                if (stack != null && itemRegistry.isDCEItem(stack) &&
                        itemId.equalsIgnoreCase(itemRegistry.getItemId(stack))) {
                    removed += stack.getAmount();
                    online.getEnderChest().setItem(i, null);
                }
            }
            online.updateInventory();
        }

        // 2. Seize from all containers and player vaults tracked in ledger
        Map<String, ItemLocationRecord> map = ledger.remove(itemId.toLowerCase());
        if (map != null) {
            for (ItemLocationRecord rec : map.values()) {
                if (rec.getLocationType() == ItemLocationRecord.LocationType.CHEST || rec.getLocationType() == ItemLocationRecord.LocationType.SHULKER) {
                    int seized = seizeItemFromContainer(itemId, rec.getInstanceUuid(), rec.getWorld(), rec.getX(), rec.getY(), rec.getZ());
                    if (seized > 0) {
                        removed += seized;
                    }
                } else if (rec.getLocationType() == ItemLocationRecord.LocationType.PLAYER_VAULT) {
                    int vaultRemoved = dc.dcessentials.modules.items.hook.PlayerVaultsHook.removeAllItemFromVault(
                            rec.getLastUserUuid(), rec.getVaultNumber(), itemId, itemRegistry);
                    if (vaultRemoved > 0) {
                        removed += vaultRemoved;
                    }
                }
            }
        }

        // 3. Clear from DB
        Bukkit.getScheduler().runTaskAsynchronously(DCCosmetics.getInstance(), () -> {
            try (java.sql.PreparedStatement ps = DCCosmetics.getInstance().getDatabaseManager().getConnection()
                    .prepareStatement("DELETE FROM dce_item_tracking WHERE item_id = ?")) {
                ps.setString(1, itemId.toLowerCase());
                ps.executeUpdate();
            } catch (Throwable t) {
                DCCosmetics.getInstance().getLogger().log(Level.WARNING, "[DCEssentials] Error deleting global tracking records: " + t.getMessage());
            }
        });

        return removed;
    }

    private void deleteRecordFromDbAsync(String instanceUuid) {
        if (instanceUuid == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(DCCosmetics.getInstance(), () -> {
            try (java.sql.PreparedStatement ps = DCCosmetics.getInstance().getDatabaseManager().getConnection()
                    .prepareStatement("DELETE FROM dce_item_tracking WHERE instance_uuid = ?")) {
                ps.setString(1, instanceUuid);
                ps.executeUpdate();
            } catch (Throwable ignored) {}
        });
    }

    public void saveLedger() {
        flushDatabaseBatch();
    }

    public synchronized void flushDatabaseBatch() {
        List<ItemLocationRecord> batch = new ArrayList<>();
        dbSaveQueue.drainTo(batch);
        if (batch.isEmpty()) return;

        String sql = "INSERT INTO dce_item_tracking (instance_uuid, item_id, user_uuid, user_name, location_type, world, x, y, z, vault_number, amount, last_seen) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(instance_uuid) DO UPDATE SET " +
                "user_uuid=excluded.user_uuid, user_name=excluded.user_name, location_type=excluded.location_type, " +
                "world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z, vault_number=excluded.vault_number, " +
                "amount=excluded.amount, last_seen=excluded.last_seen";

        try (java.sql.PreparedStatement ps = DCCosmetics.getInstance().getDatabaseManager().getConnection().prepareStatement(sql)) {
            for (ItemLocationRecord rec : batch) {
                ps.setString(1, rec.getInstanceUuid());
                ps.setString(2, rec.getItemId().toLowerCase());
                ps.setString(3, rec.getLastUserUuid() != null ? rec.getLastUserUuid().toString() : null);
                ps.setString(4, rec.getLastUserName());
                ps.setString(5, rec.getLocationType().name());
                ps.setString(6, rec.getWorld());
                ps.setInt(7, rec.getX());
                ps.setInt(8, rec.getY());
                ps.setInt(9, rec.getZ());
                ps.setInt(10, rec.getVaultNumber());
                ps.setInt(11, rec.getAmount());
                ps.setLong(12, rec.getLastSeenTimestamp());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Throwable t) {
            DCCosmetics.getInstance().getLogger().log(Level.WARNING, "[DCEssentials] Error batch updating item tracker records: " + t.getMessage());
        }
    }

    private void startAsyncDbWorker() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(DCCosmetics.getInstance(), this::flushDatabaseBatch, 100L, 100L); // Every 5 seconds flush DB batch
    }

    private synchronized void loadLedger() {
        ledger.clear();

        // 1. One-time Migration from legacy item_tracker_ledger.yml into SQLite
        if (dataFile.exists()) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
                for (String itemId : config.getKeys(false)) {
                    var itemSec = config.getConfigurationSection(itemId);
                    if (itemSec == null) continue;

                    for (String instanceUuid : itemSec.getKeys(false)) {
                        var sec = itemSec.getConfigurationSection(instanceUuid);
                        if (sec == null) continue;

                        String userUuidStr = sec.getString("userUuid", "none");
                        UUID userUuid = null;
                        if (!"none".equals(userUuidStr)) {
                            try { userUuid = UUID.fromString(userUuidStr); } catch (Exception ignored) {}
                        }
                        String userName = sec.getString("userName", "Unknown");
                        ItemLocationRecord.LocationType type = ItemLocationRecord.LocationType.valueOf(sec.getString("type", "INVENTORY"));
                        String world = sec.getString("world", "world");
                        int x = sec.getInt("x");
                        int y = sec.getInt("y");
                        int z = sec.getInt("z");
                        int vault = sec.getInt("vault");
                        int amount = sec.getInt("amount", 1);
                        long lastSeen = sec.getLong("lastSeen", System.currentTimeMillis());

                        ItemLocationRecord rec = new ItemLocationRecord(itemId, instanceUuid, userUuid, userName, type, world, x, y, z, vault, amount, lastSeen);
                        dbSaveQueue.offer(rec);
                    }
                }
                File migrated = new File(dataFile.getParentFile(), "item_tracker_ledger.yml.migrated");
                dataFile.renameTo(migrated);
                DCCosmetics.getInstance().getLogger().info("[DCEssentials] Migrated legacy item_tracker_ledger.yml into database storage.");
            } catch (Throwable t) {
                DCCosmetics.getInstance().getLogger().log(Level.WARNING, "[DCEssentials] Error migrating ledger YAML: " + t.getMessage());
            }
        }

        // 2. Load from SQLite into memory for fast GUI access
        try (java.sql.Statement s = DCCosmetics.getInstance().getDatabaseManager().getConnection().createStatement();
             java.sql.ResultSet rs = s.executeQuery("SELECT instance_uuid, item_id, user_uuid, user_name, location_type, world, x, y, z, vault_number, amount, last_seen FROM dce_item_tracking")) {
            int count = 0;
            while (rs.next()) {
                String instanceUuid = rs.getString("instance_uuid");
                String itemId = rs.getString("item_id");
                String userUuidStr = rs.getString("user_uuid");
                UUID userUuid = null;
                if (userUuidStr != null && !userUuidStr.isEmpty() && !"none".equalsIgnoreCase(userUuidStr)) {
                    try { userUuid = UUID.fromString(userUuidStr); } catch (Exception ignored) {}
                }
                String userName = rs.getString("user_name");
                ItemLocationRecord.LocationType type = ItemLocationRecord.LocationType.valueOf(rs.getString("location_type"));
                String world = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                int vault = rs.getInt("vault_number");
                int amount = rs.getInt("amount");
                long lastSeen = rs.getLong("last_seen");

                ItemLocationRecord rec = new ItemLocationRecord(itemId, instanceUuid, userUuid, userName, type, world, x, y, z, vault, amount, lastSeen);
                ledger.computeIfAbsent(itemId.toLowerCase(), k -> new ConcurrentHashMap<>()).put(instanceUuid, rec);
                count++;
            }
            DCCosmetics.getInstance().getLogger().info("[DCEssentials] Loaded " + count + " item tracking records from database.");
        } catch (Throwable t) {
            DCCosmetics.getInstance().getLogger().log(Level.WARNING, "[DCEssentials] Error loading tracking records from DB: " + t.getMessage());
        }
    }
}
