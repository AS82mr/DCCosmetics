package dc.dccosmetics.cinematic;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import dc.dccosmetics.DCCosmetics;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Captures all changes in 'spawn_main' and handles the Git-Sync Controller and Eraser tools.
 */
@SuppressWarnings({"removal", "deprecation"})
public class SyncListener implements Listener {

    public static final String SYNC_WAND_TAG = "git_sync_controller";
    public static final String ERASER_WAND_TAG = "multi_world_eraser";

    private static final Set<String> WE_REGION_COMMANDS = new HashSet<>(Arrays.asList(
            "//set", "//replace", "//re", "//rep", "//cut", "//walls", "//faces", "//outline",
            "//hollow", "//smooth", "//naturalize", "//deform", "//regen", "//fill", "//drain",
            "//overlay", "//center", "//middle", "//line", "//curve", "//flora", "//forest"
    ));

    private final SyncStageManager stageManager;
    private final Map<UUID, Long> wandCooldowns = new HashMap<>();
    private Object weBusSubscriber;

    public SyncListener(SyncStageManager stageManager) {
        this.stageManager = stageManager;
    }

    public void registerWorldEditHook() {
        try {
            this.weBusSubscriber = new Object() {
                @Subscribe
                public void onEditSession(EditSessionEvent event) {
                    if (event.getWorld() == null) return;
                    if (!event.getWorld().getName().equalsIgnoreCase(SyncStageManager.MASTER_WORLD)) return;

                    if (event.getStage() == com.sk89q.worldedit.EditSession.Stage.BEFORE_CHANGE) {
                        event.setExtent(new GitStagingExtent(event.getExtent(), stageManager));
                    }
                }
            };
            WorldEdit.getInstance().getEventBus().register(this.weBusSubscriber);
            DCCosmetics.getInstance().getLogger().info("[Git-Sync] Hooked into WorldEdit/FAWE EditSession bus for '" + SyncStageManager.MASTER_WORLD + "'.");
        } catch (Throwable t) {
            DCCosmetics.getInstance().getLogger().warning("[Git-Sync] Could not hook WorldEdit EventBus: " + t.getMessage());
        }
    }

    public void unregisterWorldEditHook() {
        if (this.weBusSubscriber != null) {
            try {
                WorldEdit.getInstance().getEventBus().unregister(this.weBusSubscriber);
            } catch (Throwable ignored) {
            }
            this.weBusSubscriber = null;
        }
    }

    private static class GitStagingExtent extends AbstractDelegateExtent {
        private final SyncStageManager stageManager;

        protected GitStagingExtent(Extent extent, SyncStageManager stageManager) {
            super(extent);
            this.stageManager = stageManager;
        }

        @Override
        public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 location, T block) throws WorldEditException {
            stageManager.recordPoint(location.x(), location.y(), location.z());
            return super.setBlock(location, block);
        }

        public <T extends BlockStateHolder<T>> boolean setBlock(int x, int y, int z, T block) throws WorldEditException {
            stageManager.recordPoint(x, y, z);
            return super.setBlock(BlockVector3.at(x, y, z), block);
        }
    }

    // Hand Building in spawn_main
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (!b.getWorld().getName().equalsIgnoreCase(SyncStageManager.MASTER_WORLD)) return;
        if (isTool(event.getPlayer().getInventory().getItemInMainHand(), ERASER_WAND_TAG)) return;

        stageManager.recordBox(b.getX(), b.getY(), b.getZ(), b.getX(), b.getY() + 1, b.getZ());
        sendStageActionBar(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block b = event.getBlock();
        if (!b.getWorld().getName().equalsIgnoreCase(SyncStageManager.MASTER_WORLD)) return;

        stageManager.recordPoint(b.getX(), b.getY(), b.getZ());
        sendStageActionBar(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockMultiPlace(BlockMultiPlaceEvent event) {
        if (!event.getBlock().getWorld().getName().equalsIgnoreCase(SyncStageManager.MASTER_WORLD)) return;
        for (BlockState state : event.getReplacedBlockStates()) {
            Location loc = state.getLocation();
            stageManager.recordPoint(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }
        sendStageActionBar(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block b = event.getBlock();
        if (!b.getWorld().getName().equalsIgnoreCase(SyncStageManager.MASTER_WORLD)) return;
        stageManager.recordPoint(b.getX(), b.getY(), b.getZ());
        sendStageActionBar(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block b = event.getBlock();
        if (!b.getWorld().getName().equalsIgnoreCase(SyncStageManager.MASTER_WORLD)) return;
        stageManager.recordPoint(b.getX(), b.getY(), b.getZ());
        sendStageActionBar(event.getPlayer());
    }

    // FAWE Command Fallbacks
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldEditCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equalsIgnoreCase(SyncStageManager.MASTER_WORLD)) return;
        if (!player.hasPermission("dccosmetics.admin")) return;

        String raw = event.getMessage().trim();
        String cmd = raw.split(" ")[0].toLowerCase();

        if (WE_REGION_COMMANDS.contains(cmd)) {
            try {
                com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
                LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);
                Region sel = session.getSelection(wePlayer.getWorld());
                BlockVector3 min = sel.getMinimumPoint();
                BlockVector3 max = sel.getMaximumPoint();
                stageManager.recordBox(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());

                Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> sendStageActionBar(player), 10L);
            } catch (Exception ignored) {
            }
            return;
        }

        if (cmd.equals("//paste") || cmd.equals("/paste")) {
            try {
                com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
                LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);
                ClipboardHolder holder = session.getClipboard();
                Clipboard clipboard = holder.getClipboard();
                BlockVector3 to = session.getPlacementPosition(wePlayer);
                Transform transform = holder.getTransform();

                BlockVector3 origin = clipboard.getOrigin();
                BlockVector3 cMin = clipboard.getMinimumPoint();
                BlockVector3 cMax = clipboard.getMaximumPoint();

                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

                int[] xs = {cMin.x(), cMax.x()};
                int[] ys = {cMin.y(), cMax.y()};
                int[] zs = {cMin.z(), cMax.z()};

                for (int x : xs) {
                    for (int y : ys) {
                        for (int z : zs) {
                            BlockVector3 offset = BlockVector3.at(x, y, z).subtract(origin);
                            BlockVector3 transformed = transform.apply(offset.toVector3()).toBlockPoint().add(to);
                            minX = Math.min(minX, transformed.x());
                            minY = Math.min(minY, transformed.y());
                            minZ = Math.min(minZ, transformed.z());
                            maxX = Math.max(maxX, transformed.x());
                            maxY = Math.max(maxY, transformed.y());
                            maxZ = Math.max(maxZ, transformed.z());
                        }
                    }
                }

                stageManager.recordBox(minX, minY, minZ, maxX, maxY, maxZ);
                Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> sendStageActionBar(player), 15L);
            } catch (Exception ignored) {
            }
        }
    }

    // Git-Sync Controller & Eraser Wand Interactions
    @EventHandler(priority = EventPriority.HIGH)
    public void onWandInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;

        // A. GIT-SYNC CONTROLLER
        if (isTool(item, SYNC_WAND_TAG)) {
            if (!player.hasPermission("dccosmetics.admin")) return;
            event.setCancelled(true);

            long now = System.currentTimeMillis();
            if (now - wandCooldowns.getOrDefault(player.getUniqueId(), 0L) < 350L) return;
            wandCooldowns.put(player.getUniqueId(), now);

            Action action = event.getAction();

            // Right-Click: Commit & Push (or Shift + Right-Click for Status)
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                if (player.isSneaking()) {
                    SyncCommand.sendStatus(player, stageManager);
                } else {
                    stageManager.commitAndPush(player);
                }
                return;
            }

            // Left-Click: Shift + Left-Click stages active WorldEdit selection
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                if (player.isSneaking()) {
                    SyncCommand.stageWorldEditSelection(player, stageManager);
                } else if (event.getClickedBlock() != null) {
                    Block b = event.getClickedBlock();
                    stageManager.recordPoint(b.getX(), b.getY(), b.getZ());
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.6f);
                    sendStageActionBar(player);
                }
            }
            return;
        }

        // B. MULTI-WORLD ERASER
        if (isTool(item, ERASER_WAND_TAG)) {
            if (!player.hasPermission("dccosmetics.admin")) return;
            if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null) return;

            event.setCancelled(true);
            Block clicked = event.getClickedBlock();
            int x = clicked.getX();
            int y = clicked.getY();
            int z = clicked.getZ();
            String blockName = clicked.getType().name();

            List<String> targetWorlds = Arrays.asList(
                    SyncStageManager.MASTER_WORLD,
                    SyncStageManager.BW_WORLD,
                    SyncStageManager.SNOW_WORLD
            );

            int erasedInWorlds = 0;
            for (String wName : targetWorlds) {
                World w = Bukkit.getWorld(wName);
                if (w == null) continue;

                Block targetBlock = w.getBlockAt(x, y, z);
                if (targetBlock.getState() instanceof Container container) {
                    container.getInventory().clear();
                }
                targetBlock.setType(Material.AIR, false);

                if (y + 1 < w.getMaxHeight()) {
                    Block above = w.getBlockAt(x, y + 1, z);
                    if (above.getType() == Material.SNOW) {
                        above.setType(Material.AIR, false);
                    }
                }
                erasedInWorlds++;
            }

            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.5f);
            sendActionBar(player, ChatColor.RED + "✦ Erased " + ChatColor.YELLOW + blockName +
                    ChatColor.GRAY + " at (" + x + ", " + y + ", " + z + ") across " + erasedInWorlds + " worlds!");
        }
    }

    private boolean isTool(ItemStack item, String tagValue) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        NamespacedKey key = new NamespacedKey(DCCosmetics.getInstance(), "sync_tool_type");
        String val = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return tagValue.equals(val);
    }

    private void sendStageActionBar(Player player) {
        int count = stageManager.getStagedBoxCount();
        long vol = stageManager.getTotalStagedVolume();
        String msg = ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "Git-Sync" + ChatColor.DARK_GRAY + "] " +
                ChatColor.YELLOW + count + " staged region(s) " + ChatColor.GRAY + "(" + vol + " blocks) " +
                ChatColor.DARK_GRAY + "» " + ChatColor.GREEN + "Right-Click Sync Wand to Push";
        sendActionBar(player, msg);
    }

    private void sendActionBar(Player player, String message) {
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        } catch (Exception ignored) {
        }
    }
}