package dc.dcessentials.modules.items.gather;

import dc.dccosmetics.DCCosmetics;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class PlacedBlockTracker implements Listener {

    private static final NamespacedKey PLACED_KEY = new NamespacedKey(DCCosmetics.getInstance(), "placed_blocks");

    public static boolean isTrackableMaterial(Material mat) {
        if (mat == null) return false;
        String n = mat.name();
        return n.endsWith("_ORE")
                || mat == Material.ANCIENT_DEBRIS
                || mat == Material.AMETHYST_CLUSTER
                || mat == Material.GILDED_BLACKSTONE
                || n.endsWith("_LOG")
                || n.endsWith("_STEM")
                || n.endsWith("_WOOD")
                || n.endsWith("_HYPHAE")
                || mat == Material.MELON
                || mat == Material.PUMPKIN
                || mat == Material.CARVED_PUMPKIN
                || mat == Material.SUGAR_CANE
                || mat == Material.CACTUS
                || mat == Material.BAMBOO;
    }

    private static int pack(int x, int y, int z) {
        return ((y + 1024) << 8) | ((x & 15) << 4) | (z & 15);
    }

    public boolean isPlayerPlaced(Block block) {
        if (block == null) return false;
        Chunk chunk = block.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        int[] arr = pdc.get(PLACED_KEY, PersistentDataType.INTEGER_ARRAY);
        if (arr == null || arr.length == 0) return false;
        int key = pack(block.getX(), block.getY(), block.getZ());
        for (int v : arr) {
            if (v == key) return true;
        }
        return false;
    }

    public void markPlaced(Block block) {
        if (block == null) return;
        Chunk chunk = block.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        int[] arr = pdc.get(PLACED_KEY, PersistentDataType.INTEGER_ARRAY);
        int key = pack(block.getX(), block.getY(), block.getZ());
        if (arr == null || arr.length == 0) {
            pdc.set(PLACED_KEY, PersistentDataType.INTEGER_ARRAY, new int[]{key});
            return;
        }
        for (int v : arr) {
            if (v == key) return;
        }
        int[] next = Arrays.copyOf(arr, arr.length + 1);
        next[arr.length] = key;
        pdc.set(PLACED_KEY, PersistentDataType.INTEGER_ARRAY, next);
    }

    public void unmark(Block block) {
        if (block == null) return;
        Chunk chunk = block.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        int[] arr = pdc.get(PLACED_KEY, PersistentDataType.INTEGER_ARRAY);
        if (arr == null || arr.length == 0) return;
        int key = pack(block.getX(), block.getY(), block.getZ());
        int idx = -1;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == key) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return;
        if (arr.length == 1) {
            pdc.remove(PLACED_KEY);
            return;
        }
        int[] next = new int[arr.length - 1];
        if (idx > 0) System.arraycopy(arr, 0, next, 0, idx);
        if (idx < arr.length - 1) System.arraycopy(arr, idx + 1, next, idx, arr.length - idx - 1);
        pdc.set(PLACED_KEY, PersistentDataType.INTEGER_ARRAY, next);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (isTrackableMaterial(block.getType())) {
            markPlaced(block);
            return;
        }
        if (block.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge()) {
            markPlaced(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (isTrackableMaterial(b.getType())) {
                markPlaced(b.getRelative(event.getDirection()));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (isTrackableMaterial(b.getType())) {
                markPlaced(b.getRelative(event.getDirection()));
            }
        }
    }
}
