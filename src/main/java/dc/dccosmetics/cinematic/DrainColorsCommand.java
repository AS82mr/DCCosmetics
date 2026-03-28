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

            player.sendMessage(ChatColor.YELLOW + "Applying Master Cinematic Drain (Preserving Block States)...");

            // Use FAWE's native highly optimized EditSession wrapper
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(wePlayer.getWorld())) {
                int replaced = 0;
                int skipped = 0;

                for (BlockVector3 pt : region) {
                    com.sk89q.worldedit.world.block.BlockState oldFaweState = editSession.getBlock(pt);
                    String id = oldFaweState.getBlockType().getId(); // e.g., "minecraft:red_wool"
                    if (!id.contains(":")) continue;

                    String namespace = id.split(":")[0];
                    String name = id.split(":")[1];
                    String newName = name;
                    boolean skipReplacement = false;

                    // 1. LEAVES: Only oak_leaves are tinted by our datapack, so convert all others to oak!
                    if (name.endsWith("_leaves")) {
                        if (!name.equals("oak_leaves")) newName = "oak_leaves";
                        else skipReplacement = true;
                    }
                    // 2. NATURAL GROUND (Skipped so the datapack tints them)
                    else if (name.equals("grass_block") || name.equals("short_grass") || name.equals("tall_grass") || name.equals("fern") || name.equals("large_fern") || name.equals("vine")) {
                        skipped++;
                        skipReplacement = true;
                    }

                    // 3. RANDOM CREEPY SOUL LANTERNS ON THE GROUND (1.5% Chance)
                    if (name.equals("grass_block") || name.equals("mud") || name.endsWith("dirt") || name.equals("podzol") || name.equals("coarse_dirt")) {
                        if (Math.random() < 0.015) {
                            try {
                                BlockVector3 up = pt.add(0, 1, 0);
                                if (editSession.getBlock(up).getBlockType().getId().equals("minecraft:air")) {
                                    editSession.setBlock(up, BlockTypes.get("minecraft:soul_lantern").getDefaultState());
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    if (!skipReplacement) {
                        // 4. DICTIONARY MAPPINGS
                        // Woods (Including trapdoors, doors, signs, pressure plates, buttons!)
                        if (name.endsWith("_log") || name.endsWith("_wood") || name.endsWith("_planks") || 
                                 name.endsWith("_stairs") || name.endsWith("_slab") || name.endsWith("_fence") || 
                                 name.endsWith("_fence_gate") || name.endsWith("_trapdoor") || name.endsWith("_door") || 
                                 name.endsWith("_button") || name.endsWith("_pressure_plate") || name.endsWith("_sign") || name.endsWith("_hanging_sign")) {
                            String[] woodTypes = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped"};
                            for (String w : woodTypes) {
                                if (name.startsWith(w + "_")) {
                                    newName = name.replaceFirst(w, "pale_oak");
                                    break;
                                }
                            }
                        }
                        // Chests
                        else if (name.equals("chest") || name.equals("trapped_chest") || name.equals("barrel")) newName = "ender_chest";
                        // Flowers
                        else if (name.equals("dandelion") || name.equals("poppy") || name.equals("blue_orchid") || name.equals("allium") || name.equals("azure_bluet") || name.equals("red_tulip") || name.equals("orange_tulip") || name.equals("white_tulip") || name.equals("pink_tulip") || name.equals("oxeye_daisy") || name.equals("cornflower") || name.equals("lily_of_the_valley") || name.equals("wither_rose") || name.equals("torchflower") || name.equals("peony") || name.equals("sunflower") || name.equals("lilac") || name.equals("rose_bush") || name.equals("pitcher_plant")) {
                            newName = Math.random() < 0.5 ? "closed_eyeblossom" : "open_eyeblossom";
                        }
                        // Lights
                        else if (name.equals("torch")) newName = "soul_torch";
                        else if (name.equals("wall_torch")) newName = "soul_wall_torch";
                        else if (name.equals("lantern")) newName = "soul_lantern";
                        else if (name.equals("campfire")) newName = "soul_campfire";
                        else if (name.equals("glowstone") || name.equals("shroomlight") || name.equals("ochre_froglight") || name.equals("verdant_froglight") || name.equals("pearlescent_froglight")) {
                            newName = "sea_lantern"; 
                        }
                        // Dirts & Podzol
                        else if (name.equals("dirt") || name.equals("coarse_dirt") || name.equals("rooted_dirt") || name.equals("podzol") || name.equals("farmland") || name.equals("dirt_path")) {
                            newName = "mud";
                        }
                        // Terracottas that look brown
                        else if (name.equals("terracotta") || name.equals("gray_terracotta") || name.equals("light_gray_terracotta") || name.equals("black_terracotta") || name.equals("brown_terracotta") || name.equals("red_terracotta")) {
                            newName = "cyan_terracotta"; // Moody dark grayish blue
                        }
                        // Brown blocks
                        else if (name.contains("brown_mushroom_block") || name.contains("red_mushroom_block") || name.contains("mushroom_stem")) newName = "pale_oak_wood";
                        else if (name.equals("note_block") || name.equals("jukebox")) newName = "polished_basalt";
                        else if (name.equals("granite")) newName = "tuff";
                        else if (name.equals("polished_granite")) newName = "polished_tuff";
                        else if (name.contains("granite_stairs")) newName = "tuff_stairs";
                        else if (name.contains("granite_slab")) newName = "tuff_slab";
                        else if (name.contains("granite_wall")) newName = "tuff_wall";
                        // Bricks
                        else if (name.equals("bricks") || name.equals("brick_block")) newName = "polished_blackstone_bricks";
                        else if (name.equals("brick_stairs")) newName = "polished_blackstone_brick_stairs";
                        else if (name.equals("brick_slab")) newName = "polished_blackstone_brick_slab";
                        else if (name.equals("brick_wall")) newName = "polished_blackstone_brick_wall";
                        // Nether blocks
                        else if (name.contains("nether_wart_block")) newName = "blackstone";
                        else if (name.contains("netherrack")) newName = "tuff";
                        else if (name.contains("nether_brick")) newName = name.replace("nether_brick", "deepslate_brick");
                        else if (name.equals("sand") || name.equals("red_sand")) newName = "gravel";
                        else if (name.contains("magma")) newName = "basalt";
                        // Intelligent Palette Mapping
                        else {
                            String[] mapToBlack = {"red_", "purple_", "pink_", "magenta_", "brown_"};
                            String[] mapToGray = {"orange_", "yellow_", "lime_", "green_"};
                            String[] mapToCyan = {"blue_", "light_blue_"};
                            
                            boolean colorMatched = false;
                            for (String c : mapToBlack) {
                                if (name.startsWith(c)) { newName = name.replaceFirst(c, "black_"); colorMatched = true; break; }
                            }
                            if (!colorMatched) {
                                for (String c : mapToGray) {
                                    if (name.startsWith(c)) { newName = name.replaceFirst(c, "gray_"); colorMatched = true; break; }
                                }
                            }
                            if (!colorMatched) {
                                for (String c : mapToCyan) {
                                    if (name.startsWith(c)) { newName = name.replaceFirst(c, "cyan_"); break; }
                                }
                            }
                        }
                    }

                    // 6. SAFE PLACEMENT
                    if (!name.equals(newName)) {
                        BlockType newType = BlockTypes.get(namespace + ":" + newName);
                        
                        // FAILSAFE: If pale_oak doesn't exist on this exact server build, fallback safely!
                        if (newType == null && newName.startsWith("pale_oak")) {
                            newType = BlockTypes.get(namespace + ":" + newName.replace("pale_oak", "dark_oak"));
                        }
                        // FAILSAFE: If eyeblossoms don't exist
                        if (newType == null && newName.contains("eyeblossom")) {
                            newType = BlockTypes.get("minecraft:wither_rose");
                        }

                        if (newType != null) {
                            com.sk89q.worldedit.world.block.BlockState finalState = newType.getDefaultState();
                            
                            // INTELLIGENT BLOCK DATA TRANSFER (Preserves Facing, Open states, Stairs, Slabs perfectly)
                            try {
                                org.bukkit.block.data.BlockData baseData = org.bukkit.Bukkit.createBlockData(newType.getId());
                                org.bukkit.block.data.BlockData oldData = org.bukkit.Bukkit.createBlockData(oldFaweState.getAsString());
                                
                                if (baseData instanceof org.bukkit.block.data.Directional b1 && oldData instanceof org.bukkit.block.data.Directional o1) {
                                    b1.setFacing(o1.getFacing());
                                }
                                if (baseData instanceof org.bukkit.block.data.Bisected b2 && oldData instanceof org.bukkit.block.data.Bisected o2) {
                                    b2.setHalf(o2.getHalf());
                                }
                                if (baseData instanceof org.bukkit.block.data.Openable b3 && oldData instanceof org.bukkit.block.data.Openable o3) {
                                    b3.setOpen(o3.isOpen());
                                }
                                if (baseData instanceof org.bukkit.block.data.type.Slab b4 && oldData instanceof org.bukkit.block.data.type.Slab o4) {
                                    b4.setType(o4.getType());
                                }
                                if (baseData instanceof org.bukkit.block.data.type.Stairs b5 && oldData instanceof org.bukkit.block.data.type.Stairs o5) {
                                    b5.setShape(o5.getShape());
                                }
                                if (baseData instanceof org.bukkit.block.data.Waterlogged b6 && oldData instanceof org.bukkit.block.data.Waterlogged o6) {
                                    b6.setWaterlogged(o6.isWaterlogged());
                                }
                                
                                finalState = BukkitAdapter.adapt(baseData);
                            } catch (Exception ignored) {
                                // Fallback to default state if Bukkit adaptation fails
                            }
                            
                            editSession.setBlock(pt, finalState);
                            replaced++;
                        }
                    }
                }
                player.sendMessage(ChatColor.GREEN + "Cinematic Drain complete! Modified " + replaced + " blocks. (Skipped " + skipped + " natural blocks).");
            }
        } catch (Exception e) { player.sendMessage(ChatColor.RED + "Error: Make sure you have a WorldEdit selection first!"); }
        return true;
    }
}