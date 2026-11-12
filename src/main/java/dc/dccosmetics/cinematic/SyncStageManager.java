package dc.dccosmetics.cinematic;

import dc.dcessentials.core.util.DCMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
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
import org.bukkit.block.data.Snowable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.Wall;
import org.bukkit.block.sign.Side;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Git-Style Staging Buffer & Single-Pass Atomic Multi-World Commit Engine.
 * Converts blocks directly to cinematic grayscale/cyan palette in-memory during sync,
 * guaranteeing zero duplicate layers and perfect coordinate alignment across dimensions.
 */
@SuppressWarnings({"removal", "deprecation"})
public class SyncStageManager {

    public static final String MASTER_WORLD = "spawn_main";
    public static final String BW_WORLD = "spawn_bw";
    public static final String SNOW_WORLD = "spawn_snow";

    private static final int MERGE_MARGIN = 4;
    private static final long MAX_MERGED_VOLUME = 250_000L;

    private static final Set<String> SINGLE_FLOWERS = new HashSet<>(Arrays.asList(
            "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet",
            "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
            "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose", "torchflower"
    ));

    private static final Set<String> TALL_FLOWERS = new HashSet<>(Arrays.asList(
            "peony", "sunflower", "lilac", "rose_bush", "pitcher_plant"
    ));

    private static final Set<String> SENSITIVE_PLANTS = new HashSet<>(Arrays.asList(
            "short_grass", "grass", "tall_grass", "fern", "large_fern", "vine",
            "dead_bush", "cactus", "sugar_cane", "bamboo", "sweet_berry_bush",
            "wheat", "carrots", "potatoes", "beetroots"
    ));

    private final List<DirtyBox> stagedBoxes = new ArrayList<>();
    private int commitCounter = 0;
    private boolean autoSnowOnPush = false; // Disabled by default for clean block placement

    public static class DirtyBox {
        public int minX, minY, minZ;
        public int maxX, maxY, maxZ;

        public DirtyBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
        }

        public boolean isNearOrOverlapping(DirtyBox other, int margin) {
            return this.minX - margin <= other.maxX && this.maxX + margin >= other.minX &&
                   this.minY - margin <= other.maxY && this.maxY + margin >= other.minY &&
                   this.minZ - margin <= other.maxZ && this.maxZ + margin >= other.minZ;
        }

        public long getMergedVolumeWith(DirtyBox other) {
            long dx = (long) Math.max(this.maxX, other.maxX) - Math.min(this.minX, other.minX) + 1;
            long dy = (long) Math.max(this.maxY, other.maxY) - Math.min(this.minY, other.minY) + 1;
            long dz = (long) Math.max(this.maxZ, other.maxZ) - Math.min(this.minZ, other.minZ) + 1;
            return dx * dy * dz;
        }

        public void absorb(DirtyBox other) {
            this.minX = Math.min(this.minX, other.minX);
            this.minY = Math.min(this.minY, other.minY);
            this.minZ = Math.min(this.minZ, other.minZ);
            this.maxX = Math.max(this.maxX, other.maxX);
            this.maxY = Math.max(this.maxY, other.maxY);
            this.maxZ = Math.max(this.maxZ, other.maxZ);
        }

        public long getVolume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }

        @Override
        public String toString() {
            return String.format("(%d, %d, %d) -> (%d, %d, %d) [%d blocks]",
                    minX, minY, minZ, maxX, maxY, maxZ, getVolume());
        }
    }

    public synchronized void recordPoint(int x, int y, int z) {
        recordBox(x, y, z, x, y, z);
    }

    public synchronized void recordBox(int x1, int y1, int z1, int x2, int y2, int z2) {
        DirtyBox incoming = new DirtyBox(x1, y1, z1, x2, y2, z2);

        boolean merged = true;
        while (merged) {
            merged = false;
            for (int i = 0; i < stagedBoxes.size(); i++) {
                DirtyBox existing = stagedBoxes.get(i);
                if (existing.isNearOrOverlapping(incoming, MERGE_MARGIN) &&
                        existing.getMergedVolumeWith(incoming) <= MAX_MERGED_VOLUME) {
                    incoming.absorb(existing);
                    stagedBoxes.remove(i);
                    merged = true;
                    break;
                }
            }
        }
        stagedBoxes.add(incoming);
    }

    public synchronized List<DirtyBox> getStagedBoxesSnapshot() {
        List<DirtyBox> copy = new ArrayList<>(stagedBoxes.size());
        for (DirtyBox b : stagedBoxes) {
            copy.add(new DirtyBox(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ));
        }
        return copy;
    }

    public synchronized int getStagedBoxCount() {
        return stagedBoxes.size();
    }

    public synchronized long getTotalStagedVolume() {
        long total = 0;
        for (DirtyBox b : stagedBoxes) {
            total += b.getVolume();
        }
        return total;
    }

    public synchronized void clearStage() {
        stagedBoxes.clear();
    }

    public boolean isAutoSnowOnPush() {
        return autoSnowOnPush;
    }

    public void setAutoSnowOnPush(boolean autoSnowOnPush) {
        this.autoSnowOnPush = autoSnowOnPush;
    }

    /**
     * Executes the Single-Pass Atomic Commit & Push.
     */
    public void commitAndPush(CommandSender sender) {
        List<DirtyBox> boxesToPush;
        synchronized (this) {
            if (stagedBoxes.isEmpty()) {
                DCMessage.sendSync(sender, "Nothing to commit! Working tree in '" + MASTER_WORLD + "' is clean.");
                if (sender instanceof Player p) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
                }
                return;
            }
            boxesToPush = new ArrayList<>(stagedBoxes);
            stagedBoxes.clear();
            commitCounter++;
        }

        World mainWorld = Bukkit.getWorld(MASTER_WORLD);
        World bwWorld = Bukkit.getWorld(BW_WORLD);
        World snowWorld = Bukkit.getWorld(SNOW_WORLD);

        if (mainWorld == null) {
            DCMessage.sendError(sender, "Master world '" + MASTER_WORLD + "' is not loaded!");
            return;
        }
        if (bwWorld == null && snowWorld == null) {
            DCMessage.sendError(sender, "Neither '" + BW_WORLD + "' nor '" + SNOW_WORLD + "' is loaded!");
            return;
        }

        long startTime = System.currentTimeMillis();
        long totalBlocksCopied = 0;
        int totalDrained = 0;
        int totalSnowAdjusted = 0;

        int minWorldY = mainWorld.getMinHeight();
        int maxWorldY = mainWorld.getMaxHeight() - 1;

        try {
            for (DirtyBox box : boxesToPush) {
                int yStart = Math.max(minWorldY, box.minY);
                int yEnd = Math.min(maxWorldY, box.maxY);

                for (int x = box.minX; x <= box.maxX; x++) {
                    for (int z = box.minZ; z <= box.maxZ; z++) {
                        for (int y = yStart; y <= yEnd; y++) {
                            Block srcBlock = mainWorld.getBlockAt(x, y, z);
                            BlockData srcData = srcBlock.getBlockData();

                            // 1. Sync directly to spawn_snow
                            if (snowWorld != null) {
                                Block destSnow = snowWorld.getBlockAt(x, y, z);
                                destSnow.setBlockData(srcData, false);
                                syncTileEntity(srcBlock, destSnow);
                            }

                            // 2. Sync directly to spawn_bw WITH IN-MEMORY COLOR CONVERSION
                            if (bwWorld != null) {
                                Block destBw = bwWorld.getBlockAt(x, y, z);
                                BlockData bwData = convertToCinematic(srcBlock);
                                destBw.setBlockData(bwData, false);
                                syncTileEntity(srcBlock, destBw);

                                if (bwData.getMaterial() != srcData.getMaterial()) {
                                    totalDrained++;
                                }
                            }
                            totalBlocksCopied++;
                        }
                    }
                }

                if (snowWorld != null && autoSnowOnPush) {
                    totalSnowAdjusted += adaptSnowDirect(snowWorld, box.minX, yStart, box.minZ, box.maxX, yEnd, box.maxZ);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            DCMessage.sendError(sender, "Error during commit push: " + e.getMessage());
            return;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        DCMessage.sendSync(sender, "Commit #" + commitCounter + " pushed in " + elapsed + "ms! Synced " +
                boxesToPush.size() + " region(s) (" + totalBlocksCopied + " blocks, " + totalDrained + " B&W converted, " + totalSnowAdjusted + " snow updates).");

        if (sender instanceof Player p) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.3f);
            p.sendTitle("", ChatColor.AQUA + "✓ Commit #" + commitCounter + " Pushed (" + elapsed + "ms)", 2, 25, 8);
        }
    }

    // =========================================================================
    // IN-MEMORY CINEMATIC PALETTE & BLOCK DATA CONVERSION ENGINE
    // =========================================================================

    public static BlockData convertToCinematic(Block srcBlock) {
        BlockData srcData = srcBlock.getBlockData();
        Material mat = srcData.getMaterial();
        String name = mat.name().toLowerCase();

        // 1. Air, fluids, barrier, void
        if (mat.isAir() || name.contains("water") || name.contains("lava") || name.equals("barrier")) {
            return srcData;
        }

        // 2. Natural vegetation (preserved so custom cinematic datapack biomes tint them gray/cyan)
        if (name.equals("grass_block") || name.equals("short_grass") || name.equals("grass") ||
                name.equals("tall_grass") || name.equals("fern") || name.equals("large_fern") ||
                name.equals("vine")) {
            return srcData;
        }

        // 3. Leaves: Convert to oak_leaves and enforce persistent=true so leaves never decay
        if (name.endsWith("_leaves")) {
            try {
                BlockData oakLeaves = Bukkit.createBlockData(Material.OAK_LEAVES);
                if (oakLeaves instanceof Leaves l) {
                    l.setPersistent(true);
                }
                if (oakLeaves instanceof Waterlogged w && srcData instanceof Waterlogged sw) {
                    w.setWaterlogged(sw.isWaterlogged());
                }
                return oakLeaves;
            } catch (Exception e) {
                return srcData;
            }
        }

        // 4. Flowers: 2-block tall flowers clear top half, bottom half becomes eyeblossom/wither rose
        if (TALL_FLOWERS.contains(name)) {
            if (srcData instanceof Bisected b && b.getHalf() == Bisected.Half.TOP) {
                return Material.AIR.createBlockData();
            }
            return resolveFlowerData();
        }
        if (SINGLE_FLOWERS.contains(name)) {
            return resolveFlowerData();
        }

        // Check whether a plant sits directly on top of this block before altering ground
        boolean hasPlantAbove = false;
        if (srcBlock.getY() + 1 < srcBlock.getWorld().getMaxHeight()) {
            String upName = srcBlock.getRelative(0, 1, 0).getType().name().toLowerCase();
            hasPlantAbove = SENSITIVE_PLANTS.contains(upName) || SINGLE_FLOWERS.contains(upName) || TALL_FLOWERS.contains(upName);
        }

        // 5. Palette translation
        String newName = mapBlockName(name, hasPlantAbove);
        if (newName.equals(name)) {
            return srcData;
        }

        Material newMat = Material.matchMaterial(newName.toUpperCase());
        if (newMat == null && newName.startsWith("pale_oak")) {
            newMat = Material.matchMaterial(newName.replace("pale_oak", "dark_oak").toUpperCase());
        }
        if (newMat == null) {
            return srcData;
        }

        try {
            BlockData newData = Bukkit.createBlockData(newMat);
            copyBlockProperties(srcData, newData);
            return newData;
        } catch (Exception e) {
            return srcData;
        }
    }

    private static String mapBlockName(String name, boolean hasPlantAbove) {
        // Woods (Dark Oak tested BEFORE Oak, and Stripped tested BEFORE normal!)
        if (name.endsWith("_log") || name.endsWith("_wood") || name.endsWith("_planks") ||
                name.endsWith("_stairs") || name.endsWith("_slab") || name.endsWith("_fence") ||
                name.endsWith("_fence_gate") || name.endsWith("_trapdoor") || name.endsWith("_door") ||
                name.endsWith("_button") || name.endsWith("_pressure_plate") || name.endsWith("_sign") ||
                name.endsWith("_hanging_sign")) {

            String[] woodTypes = {
                    "stripped_dark_oak", "stripped_oak", "stripped_spruce", "stripped_birch",
                    "stripped_jungle", "stripped_acacia", "stripped_mangrove", "stripped_cherry",
                    "stripped_bamboo", "stripped_crimson", "stripped_warped",
                    "dark_oak", "oak", "spruce", "birch", "jungle", "acacia", "mangrove",
                    "cherry", "bamboo", "crimson", "warped"
            };

            for (String w : woodTypes) {
                if (name.startsWith(w + "_")) {
                    if (w.startsWith("stripped_")) {
                        return name.replaceFirst(w, "stripped_pale_oak");
                    } else {
                        return name.replaceFirst(w, "pale_oak");
                    }
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

        // Soil: Do not turn dirt into mud if plants or crops sit on top (prevents plant breakage)
        if (name.equals("farmland") || name.equals("dirt_path")) {
            return hasPlantAbove ? name : "mud";
        }
        if (name.equals("dirt") || name.equals("coarse_dirt") || name.equals("rooted_dirt") || name.equals("podzol")) {
            return hasPlantAbove ? "coarse_dirt" : "mud";
        }

        // Sand
        if (name.equals("sand") || name.equals("red_sand")) {
            return hasPlantAbove ? "sand" : "gravel";
        }

        // Terracotta
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

    private static void copyBlockProperties(BlockData oldData, BlockData baseData) {
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
    }

    private static BlockData resolveFlowerData() {
        Material m = Math.random() < 0.5 ? Material.matchMaterial("CLOSED_EYEBLOSSOM") : Material.matchMaterial("OPEN_EYEBLOSSOM");
        if (m == null) m = Material.matchMaterial("AZURE_BLUET");
        if (m == null) m = Material.WITHER_ROSE;
        return Bukkit.createBlockData(m);
    }

    private void syncTileEntity(Block srcBlock, Block destBlock) {
        try {
            org.bukkit.block.BlockState srcState = srcBlock.getState();
            org.bukkit.block.BlockState destState = destBlock.getState();

            if (srcState instanceof Container srcContainer && destState instanceof Container destContainer) {
                destContainer.getInventory().setContents(srcContainer.getInventory().getContents());
                destContainer.update(true, false);
            } else if (srcState instanceof Sign srcSign && destState instanceof Sign destSign) {
                copySignSide(srcSign.getSide(Side.FRONT), destSign.getSide(Side.FRONT));
                copySignSide(srcSign.getSide(Side.BACK), destSign.getSide(Side.BACK));
                destSign.update(true, false);
            }
        } catch (Exception ignored) {
        }
    }

    private void copySignSide(org.bukkit.block.sign.SignSide src, org.bukkit.block.sign.SignSide dest) {
        for (int i = 0; i < 4; i++) {
            dest.setLine(i, src.getLine(i));
        }
        dest.setColor(src.getColor());
        dest.setGlowingText(src.isGlowingText());
    }

    private int adaptSnowDirect(World snowWorld, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int adjustments = 0;
        int maxWorldY = snowWorld.getMaxHeight() - 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (maxY + 1 <= maxWorldY) {
                    Block above = snowWorld.getBlockAt(x, maxY + 1, z);
                    Block atTop = snowWorld.getBlockAt(x, maxY, z);
                    if (above.getType() == Material.SNOW && atTop.getType().isAir()) {
                        above.setType(Material.AIR, false);
                        adjustments++;
                    }
                }

                if (!autoSnowOnPush) continue;

                for (int y = maxY; y >= minY; y--) {
                    if (y + 1 > maxWorldY) continue;
                    Block current = snowWorld.getBlockAt(x, y, z);
                    Block up = snowWorld.getBlockAt(x, y + 1, z);

                    if (current.getType().isAir() || current.getType() == Material.SNOW) {
                        continue;
                    }

                    if (up.getType().isAir() && isSnowEligible(current.getType()) && isDirectSkyExposed(snowWorld, x, y + 1, z)) {
                        up.setType(Material.SNOW, false);
                        adjustments++;

                        BlockData bd = current.getBlockData();
                        if (bd instanceof Snowable snowable) {
                            snowable.setSnowy(true);
                            current.setBlockData(bd, false);
                        }
                    }
                    break;
                }
            }
        }
        return adjustments;
    }

    private boolean isDirectSkyExposed(World world, int x, int startY, int z) {
        int maxY = Math.min(world.getMaxHeight() - 1, startY + 64);
        for (int y = startY; y <= maxY; y++) {
            Material m = world.getBlockAt(x, y, z).getType();
            if (!m.isAir() && m != Material.SNOW) {
                return false;
            }
        }
        return true;
    }

    private boolean isSnowEligible(Material mat) {
        String name = mat.name().toLowerCase();
        if (name.endsWith("_stairs") || name.endsWith("_slab") || name.endsWith("_fence") ||
                name.endsWith("_fence_gate") || name.endsWith("_wall") || name.endsWith("_trapdoor") ||
                name.endsWith("_door") || name.endsWith("_button") || name.endsWith("_pressure_plate") ||
                name.endsWith("_sign") || name.endsWith("_hanging_sign") || name.endsWith("_carpet") ||
                name.endsWith("_pane") || name.contains("glass") || name.contains("ice") ||
                name.contains("torch") || name.contains("lantern") || name.contains("campfire") ||
                name.contains("water") || name.contains("lava") || name.equals("magma_block") ||
                name.equals("glowstone") || name.equals("shroomlight") || name.endsWith("_froglight") ||
                name.equals("dirt_path") || name.equals("farmland") || name.equals("chest") ||
                name.equals("trapped_chest") || name.equals("ender_chest") || name.equals("barrel")) {
            return false;
        }
        return name.equals("grass_block") || name.equals("dirt") || name.equals("coarse_dirt") ||
                name.equals("podzol") || name.equals("mud") || name.endsWith("_leaves") ||
                name.endsWith("_log") || name.endsWith("_wood") || name.endsWith("_planks") ||
                name.endsWith("_bricks") || name.endsWith("stone") || name.endsWith("deepslate") ||
                name.endsWith("tuff") || name.endsWith("andesite") || name.endsWith("diorite") ||
                name.endsWith("granite") || name.endsWith("terracotta") || name.endsWith("concrete") ||
                name.endsWith("wool") || name.equals("sand") || name.equals("gravel") ||
                name.equals("calcite") || name.equals("basalt") || name.equals("polished_basalt");
    }
}