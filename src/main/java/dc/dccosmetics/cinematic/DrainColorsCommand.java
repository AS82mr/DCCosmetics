package dc.dccosmetics.cinematic;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class DrainColorsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("dccosmetics.admin")) return true;

        try {
            com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
            com.sk89q.worldedit.LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);
            Region region = session.getSelection(wePlayer.getWorld());

            player.sendMessage(ChatColor.YELLOW + "Draining colors from selection asynchronously...");

            // Use FAWE's native highly optimized EditSession wrapper
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(wePlayer.getWorld())) {
                int replaced = 0;

                for (BlockVector3 pt : region) {
                    BlockType blockType = editSession.getBlock(pt).getBlockType();
                    String id = blockType.getId(); // e.g., "minecraft:red_wool"
                    if (!id.contains(":")) continue;

                    String namespace = id.split(":")[0];
                    String name = id.split(":")[1];
                    String newName = name;

                    // Creative Swaps
                    if (name.endsWith("_leaves")) {
                        newName = "dead_tube_coral_block";
                    } else if (name.contains("stripped_") && (name.endsWith("_log") || name.endsWith("_wood"))) {
                        newName = "polished_basalt";
                    } else if (name.endsWith("_log") || name.endsWith("_wood")) {
                        newName = "basalt";
                    } else {
                        // Smart String Palette Mapping
                        String[] dark = {"red_", "blue_", "green_", "brown_", "black_", "purple_", "cyan_", "magenta_"};
                        String[] light = {"white_", "orange_", "light_blue_", "yellow_", "lime_", "pink_"};
                        
                        boolean colorMatched = false;
                        for (String c : dark) { if (name.startsWith(c)) { newName = name.replaceFirst(c, "gray_"); colorMatched = true; break; } }
                        if (!colorMatched) {
                            for (String c : light) { if (name.startsWith(c)) { newName = name.replaceFirst(c, "light_gray_"); break; } }
                        }
                    }

                    if (!name.equals(newName)) {
                        BlockType newType = BlockTypes.get(namespace + ":" + newName);
                        if (newType != null) {
                            editSession.setBlock(pt, newType.getDefaultState());
                            replaced++;
                        }
                    }
                }
                player.sendMessage(ChatColor.GREEN + "Cinematic conversion complete! Drained " + replaced + " blocks.");
            }
        } catch (Exception e) { player.sendMessage(ChatColor.RED + "Error: Make sure you have a WorldEdit selection first!"); }
        return true;
    }
}