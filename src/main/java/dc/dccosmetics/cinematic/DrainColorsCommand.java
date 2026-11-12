package dc.dccosmetics.cinematic;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Hangable;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Wall;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"removal", "deprecation"})
public class DrainColorsCommand implements CommandExecutor {

    private static final Set<String> SINGLE_FLOWERS = new HashSet<>(Arrays.asList(
            "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet",
            "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
            "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose", "torchflower"
    ));

    private static final Set<String> TALL_FLOWERS = new HashSet<>(Arrays.asList(
            "peony", "sunflower", "lilac", "rose_bush", "pitcher_plant"
    ));

    private static final Set<String> NATURAL_TINTED_PLANTS = new HashSet<>(Arrays.asList(
            "short_grass", "grass", "tall_grass", "fern", "large_fern", "vine"
    ));

    private static final Set<String> SAND_DEPENDENT_PLANTS = new HashSet<>(Arrays.asList(
            "dead_bush", "cactus", "sugar_cane", "kelp", "kelp_plant", "seagrass", "tall_seagrass"
    ));

    private static final Set<String> FARMLAND_DEPENDENT_PLANTS = new HashSet<>(Arrays.asList(
            "wheat", "carrots", "potatoes", "beetroots", "melon_stem", "pumpkin_stem",
            "attached_melon_stem", "attached_pumpkin_stem", "torchflower_crop", "pitcher_crop"
    ));

    public static class DrainResult {
        public final int replaced;
        public final int decorationsPreserved;
        public final int skippedNatural;

        public DrainResult(int replaced, int decorationsPreserved, int skippedNatural) {
            this.replaced = replaced;
            this.decorationsPreserved = decorationsPreserved;
            this.skippedNatural = skippedNatural;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can run this command.");
            return true;
        }
        if (!player.hasPermission("dccosmetics.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        try {
            com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);
            Region region = session.getSelection(wePlayer.getWorld());

            player.sendMessage(ChatColor.YELLOW + "Applying Master Cinematic Drain (Preserving Decorations & Block States)...");

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(wePlayer.getWorld())) {
                DrainResult result = drainRegion(editSession, region);
                player.sendMessage(ChatColor.GREEN + "Cinematic Drain complete! Modified " + result.replaced +
                        " blocks (Preserved " + result.decorationsPreserved + " decorations & " + result.skippedNatural + " natural blocks).");
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error: Make sure you have a WorldEdit selection first!");
        }
        return true;
    }

    /**
     * Reusable 2-Pass Physics-Safe Color Draining Engine.
     * Can be invoked on a massive WorldEdit selection or a tiny Git-Sync dirty bounding box.
     */
    public static DrainResult drainRegion(EditSession editSession, Region region) {
        editSession.setFastMode(true);

        int replaced = 0;
        int skipped = 0;

        Map<BlockVector3, BlockState> decorationRestoreQueue = new HashMap<>();

        for (BlockVector3 pt : region) {
            BlockState oldFaweState = editSession.getBlock(pt);
            String id = oldFaweState.getBlockType().getId();
            if (!id.contains(":")) continue;

            String namespace = id.split(":")[0];
            String name = id.split(":")[1];

            if (name.equals("air") || name.equals("cave_air") || name.equals("void_air")) {
                continue;
            }

            BlockVector3 up = pt.add(0, 1, 0);
            String upId = editSession.getBlock(up).getBlockType().getId();
            String upName = upId.contains(":") ? upId.split(":")[1] : upId;

            // 1. LEAVES: Convert all leaves to oak_leaves AND force persistent=true
            if (name.endsWith("_leaves")) {
                BlockState leafState = createPersistentLeafState(namespace + ":oak_leaves", oldFaweState);
                if (leafState != null) {
                    editSession.setBlock(pt, leafState);
                    if (!name.equals("oak_leaves")) replaced++;
                }
                continue;
            }

            // 2. NATURAL GROUND & TINTED VEGETATION
            if (name.equals("grass_block")) {
                skipped++;
                maybePlaceSoulLantern(editSession, pt, up, upName);
                continue;
            }

            if (NATURAL_TINTED_PLANTS.contains(name)) {
                skipped++;
                decorationRestoreQueue.put(pt, oldFaweState);
                continue;
            }

            // 3. TWO-BLOCK TALL FLOWERS (Clear upper half, convert lower half to 1-block flower)
            if (TALL_FLOWERS.contains(name)) {
                if (isUpperHalf(oldFaweState)) {
                    BlockType airType = BlockTypes.get("minecraft:air");
                    if (airType != null) {
                        decorationRestoreQueue.put(pt, airType.getDefaultState());
                    }
                } else {
                    String flowerTarget = Math.random() < 0.5 ? "closed_eyeblossom" : "open_eyeblossom";
                    BlockType flowerType = resolveFlowerType(namespace, flowerTarget);
                    if (flowerType != null) {
                        decorationRestoreQueue.put(pt, flowerType.getDefaultState());
                        replaced++;
                    } else {
                        decorationRestoreQueue.put(pt, oldFaweState);
                    }
                }
                continue;
            }

            // 4. SINGLE-BLOCK FLOWERS
            if (SINGLE_FLOWERS.contains(name)) {
                String flowerTarget = Math.random() < 0.5 ? "closed_eyeblossom" : "open_eyeblossom";
                BlockType flowerType = resolveFlowerType(namespace, flowerTarget);
                if (flowerType != null) {
                    decorationRestoreQueue.put(pt, flowerType.getDefaultState());
                    replaced++;
                } else {
                    decorationRestoreQueue.put(pt, oldFaweState);
                }
                continue;
            }

            // Preserve fragile plants/decorations in Pass 2
            if (isFragileDecoration(name)) {
                String mappedDeco = mapBlockName(name, upName);
                BlockState targetDecoState = oldFaweState;
                if (!mappedDeco.equals(name)) {
                    BlockState adapted = buildAdaptedState(namespace, mappedDeco, oldFaweState);
                    if (adapted != null) {
                        targetDecoState = adapted;
                        replaced++;
                    }
                }
                decorationRestoreQueue.put(pt, targetDecoState);
                continue;
            }

            // 5. SOLID BLOCKS & PALETTE MAPPING
            String newName = mapBlockName(name, upName);

            if (!name.equals(newName)) {
                BlockState finalState = buildAdaptedState(namespace, newName, oldFaweState);
                if (finalState != null) {
                    editSession.setBlock(pt, finalState);
                    replaced++;
                }
            }

            // 6. RANDOM CREEPY SOUL LANTERNS (1.5% Chance)
            if (name.equals("mud") || name.endsWith("dirt") || name.equals("podzol")) {
                maybePlaceSoulLantern(editSession, pt, up, upName);
            }
        }

        // PASS 2: Restore and apply decorations bottom-to-top
        decorationRestoreQueue.entrySet().stream()
                .sorted((a, b) -> Integer.compare(a.getKey().y(), b.getKey().y()))
                .forEach(entry -> {
                    try {
                        editSession.setBlock(entry.getKey(), entry.getValue());
                    } catch (Exception ignored) {
                    }
                });

        return new DrainResult(replaced, decorationRestoreQueue.size(), skipped);
    }

    private static String mapBlockName(String name, String upName) {
        // Woods
        if (name.endsWith("_log") || name.endsWith("_wood") || name.endsWith("_planks") ||
                name.endsWith("_stairs") || name.endsWith("_slab") || name.endsWith("_fence") ||
                name.endsWith("_fence_gate") || name.endsWith("_trapdoor") || name.endsWith("_door") ||
                name.endsWith("_button") || name.endsWith("_pressure_plate") || name.endsWith("_sign") ||
                name.endsWith("_hanging_sign")) {
            String[] woodTypes = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped"};
            for (String w : woodTypes) {
                if (name.startsWith(w + "_")) {
                    return name.replaceFirst(w, "pale_oak");
                }
            }
        }

        // Chests
        if (name.equals("chest") || name.equals("trapped_chest") || name.equals("barrel")) {
            return "ender_chest";
        }

        // Lights
        if (name.equals("torch")) return "soul_torch";
        if (name.equals("wall_torch")) return "soul_wall_torch";
        if (name.equals("lantern")) return "soul_lantern";
        if (name.equals("campfire")) return "soul_campfire";
        if (name.equals("glowstone") || name.equals("shroomlight") ||
                name.equals("ochre_froglight") || name.equals("verdant_froglight") || name.equals("pearlescent_froglight")) {
            return "sea_lantern";
        }

        // Dirts & Podzol
        if (name.equals("farmland")) {
            if (FARMLAND_DEPENDENT_PLANTS.contains(upName)) {
                return "farmland";
            }
            return "mud";
        }
        if (name.equals("dirt_path")) {
            if (!upName.equals("air") && !upName.equals("cave_air") && !upName.equals("void_air")) {
                return "coarse_dirt";
            }
            return "mud";
        }
        if (name.equals("dirt") || name.equals("coarse_dirt") || name.equals("rooted_dirt") || name.equals("podzol")) {
            return "mud";
        }

        // Sand
        if (name.equals("sand") || name.equals("red_sand")) {
            if (SAND_DEPENDENT_PLANTS.contains(upName)) {
                return "sand";
            }
            return "gravel";
        }

        // Terracottas
        if (name.equals("terracotta") || name.equals("gray_terracotta") || name.equals("light_gray_terracotta") ||
                name.equals("black_terracotta") || name.equals("brown_terracotta") || name.equals("red_terracotta")) {
            return "cyan_terracotta";
        }

        // Stone & Bricks
        if (name.contains("brown_mushroom_block") || name.contains("red_mushroom_block") || name.contains("mushroom_stem")) {
            return "pale_oak_wood";
        }
        if (name.equals("note_block") || name.equals("jukebox")) return "polished_basalt";
        if (name.equals("granite")) return "tuff";
        if (name.equals("polished_granite")) return "polished_tuff";
        if (name.contains("granite_stairs")) return "tuff_stairs";
        if (name.contains("granite_slab")) return "tuff_slab";
        if (name.contains("granite_wall")) return "tuff_wall";
        if (name.equals("bricks") || name.equals("brick_block")) return "polished_blackstone_bricks";
        if (name.equals("brick_stairs")) return "polished_blackstone_brick_stairs";
        if (name.equals("brick_slab")) return "polished_blackstone_brick_slab";
        if (name.equals("brick_wall")) return "polished_blackstone_brick_wall";

        // Nether blocks
        if (name.contains("nether_wart_block")) return "blackstone";
        if (name.contains("netherrack")) return "tuff";
        if (name.contains("nether_brick")) return name.replace("nether_brick", "deepslate_brick");
        if (name.contains("magma")) return "basalt";

        // Color Palette Mapping
        String[] mapToBlack = {"red_", "purple_", "pink_", "magenta_", "brown_"};
        String[] mapToGray = {"orange_", "yellow_", "lime_", "green_"};
        String[] mapToCyan = {"blue_", "light_blue_"};

        for (String c : mapToBlack) {
            if (name.startsWith(c)) return name.replaceFirst(c, "black_");
        }
        for (String c : mapToGray) {
            if (name.startsWith(c)) return name.replaceFirst(c, "gray_");
        }
        for (String c : mapToCyan) {
            if (name.startsWith(c)) return name.replaceFirst(c, "cyan_");
        }

        return name;
    }

    private static boolean isFragileDecoration(String name) {
        return name.endsWith("_sapling") || name.endsWith("_mushroom") || name.endsWith("_fungus") ||
                name.endsWith("_roots") || name.endsWith("_sprouts") || name.endsWith("_carpet") ||
                name.endsWith("_torch") || name.endsWith("_lantern") || name.endsWith("_sign") ||
                name.endsWith("_hanging_sign") || name.endsWith("_button") || name.endsWith("_pressure_plate") ||
                name.endsWith("_door") || name.endsWith("_banner") || name.endsWith("rail") ||
                name.startsWith("potted_") || name.equals("dead_bush") || name.equals("cactus") ||
                name.equals("sugar_cane") || name.equals("bamboo") || name.equals("lily_pad") ||
                name.equals("pink_petals") || name.equals("wildflowers") || name.equals("leaf_litter") ||
                name.equals("spore_blossom") || name.equals("hanging_roots") || name.equals("glow_lichen") ||
                name.equals("ladder") || name.equals("lever") || name.equals("tripwire_hook") ||
                name.equals("sweet_berry_bush") || name.equals("azalea") || name.equals("flowering_azalea") ||
                FARMLAND_DEPENDENT_PLANTS.contains(name) || SAND_DEPENDENT_PLANTS.contains(name);
    }

    private static boolean isUpperHalf(BlockState state) {
        try {
            BlockData data = Bukkit.createBlockData(state.getAsString());
            if (data instanceof Bisected bisected) {
                return bisected.getHalf() == Bisected.Half.TOP;
            }
        } catch (Exception ignored) {
        }
        return state.getAsString().contains("half=upper") || state.getAsString().contains("half=top");
    }

    private static BlockType resolveFlowerType(String namespace, String flowerTarget) {
        BlockType type = BlockTypes.get(namespace + ":" + flowerTarget);
        if (type == null) {
            type = BlockTypes.get("minecraft:azure_bluet");
        }
        if (type == null) {
            type = BlockTypes.get("minecraft:wither_rose");
        }
        return type;
    }

    private static BlockState createPersistentLeafState(String leafId, BlockState oldFaweState) {
        try {
            BlockData baseData = Bukkit.createBlockData(leafId);
            BlockData oldData = Bukkit.createBlockData(oldFaweState.getAsString());
            if (baseData instanceof Leaves leaves) {
                leaves.setPersistent(true);
            }
            if (baseData instanceof Waterlogged bWater && oldData instanceof Waterlogged oWater) {
                bWater.setWaterlogged(oWater.isWaterlogged());
            }
            return BukkitAdapter.adapt(baseData);
        } catch (Exception e) {
            BlockType type = BlockTypes.get(leafId);
            return type != null ? type.getDefaultState() : null;
        }
    }

    private static BlockState buildAdaptedState(String namespace, String newName, BlockState oldFaweState) {
        BlockType newType = BlockTypes.get(namespace + ":" + newName);

        if (newType == null && newName.startsWith("pale_oak")) {
            newType = BlockTypes.get(namespace + ":" + newName.replace("pale_oak", "dark_oak"));
        }
        if (newType == null) {
            return null;
        }

        BlockState finalState = newType.getDefaultState();
        try {
            BlockData baseData = Bukkit.createBlockData(newType.getId());
            BlockData oldData = Bukkit.createBlockData(oldFaweState.getAsString());

            if (baseData instanceof Directional b1 && oldData instanceof Directional o1) {
                if (b1.getFaces().contains(o1.getFacing())) {
                    b1.setFacing(o1.getFacing());
                }
            }
            if (baseData instanceof Orientable bAxis && oldData instanceof Orientable oAxis) {
                if (bAxis.getAxes().contains(oAxis.getAxis())) {
                    bAxis.setAxis(oAxis.getAxis());
                }
            }
            if (baseData instanceof Bisected b2 && oldData instanceof Bisected o2) {
                b2.setHalf(o2.getHalf());
            }
            if (baseData instanceof Openable b3 && oldData instanceof Openable o3) {
                b3.setOpen(o3.isOpen());
            }
            if (baseData instanceof Slab b4 && oldData instanceof Slab o4) {
                b4.setType(o4.getType());
            }
            if (baseData instanceof Stairs b5 && oldData instanceof Stairs o5) {
                b5.setShape(o5.getShape());
            }
            if (baseData instanceof Waterlogged b6 && oldData instanceof Waterlogged o6) {
                b6.setWaterlogged(o6.isWaterlogged());
            }
            if (baseData instanceof MultipleFacing bMulti && oldData instanceof MultipleFacing oMulti) {
                for (org.bukkit.block.BlockFace face : oMulti.getFaces()) {
                    if (bMulti.getAllowedFaces().contains(face)) {
                        bMulti.setFace(face, true);
                    }
                }
            }
            if (baseData instanceof Wall bWall && oldData instanceof Wall oWall) {
                bWall.setUp(oWall.isUp());
                for (org.bukkit.block.BlockFace face : Arrays.asList(
                        org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                        org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST)) {
                    bWall.setHeight(face, oWall.getHeight(face));
                }
            }
            if (baseData instanceof FaceAttachable bAttach && oldData instanceof FaceAttachable oAttach) {
                bAttach.setAttachedFace(oAttach.getAttachedFace());
            }
            if (baseData instanceof Hangable bHang && oldData instanceof Hangable oHang) {
                bHang.setHanging(oHang.isHanging());
            }
            if (baseData instanceof Rotatable bRot && oldData instanceof Rotatable oRot) {
                bRot.setRotation(oRot.getRotation());
            }
            if (baseData instanceof Lightable bLight && oldData instanceof Lightable oLight) {
                bLight.setLit(oLight.isLit());
            }
            if (baseData instanceof Rail bRail && oldData instanceof Rail oRail) {
                if (bRail.getShapes().contains(oRail.getShape())) {
                    bRail.setShape(oRail.getShape());
                }
            }
            if (baseData instanceof Leaves bLeaves) {
                bLeaves.setPersistent(true);
            }

            finalState = BukkitAdapter.adapt(baseData);
        } catch (Exception ignored) {
        }
        return finalState;
    }

    private static void maybePlaceSoulLantern(EditSession editSession, BlockVector3 pt, BlockVector3 up, String upName) {
        if (!upName.equals("air")) return;
        if (Math.random() < 0.015) {
            try {
                BlockType lanternType = BlockTypes.get("minecraft:soul_lantern");
                if (lanternType != null) {
                    editSession.setBlock(up, lanternType.getDefaultState());
                }
            } catch (Exception ignored) {
            }
        }
    }
}