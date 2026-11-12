package dc.dccosmetics.tpeffects.cinematic;

import com.destroystokyo.paper.profile.PlayerProfile;
import dc.dccosmetics.wardrobe.WardrobeManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Argon 1:1 Pixel-Replica Character Compositor & Dissolve Engine.
 *
 * Pre-baked canonical ItemDisplay band models (dragoncote:tp_rig/<part>_b<0..3>) split each body surface
 * into true 1/16-block skin/armor/elytra pixels controlled individually via CustomModelData flags (visibility)
 * and CustomModelData colors (24-bit RGB tint).
 *
 * Supports:
 * - Full 64x64 / 64x32 player skin (Inner + 2nd-layer Hat/Jacket/Sleeves/Pants with empty-band culling)
 * - Slim (3px) vs Classic (4px) arms
 * - Equipped Helmet, Chestplate, Leggings, Boots (Leather dye + 1.21 ArmorTrim pattern & material palette)
 * - Equipped Elytra wings (or sleek cape wing fallback) posed on the back
 * - Equipped MainHand & OffHand items
 * - Top-down irregular pixel peel into a tight vertical +Y sky column of dark pixel slivers, and
 *   sky-plunge -Y arrival reconstruction.
 */
public final class SkinVoxelizer {

    private static final String NAMESPACE = "dragoncote";
    private static final float PLAYER_SCALE = 0.9375f; // 15/16 exact Mojang PlayerRenderer scale
    private static final int MAX_ACTIVE_FLAKES = 160;
    // ItemDisplayRenderer unconditionally applies PoseStack.rotate(Axis.YP, PI) before submitting models;
    // setting Transformation rightRotation = R_Y(PI) cancels it cleanly without inverting leftRotation pose angles.
    private static final Quaternionf ITEM_DISPLAY_FLIP_FIX = new Quaternionf().rotateY((float) Math.PI);

    public enum FlakeStyle {
        LEGACY_OBSIDIAN(false, false, 15, 0.35f),
        LEGACY_CHRONO(true, false, 15, 0.35f),
        UMBRAL_SUBTLE_GLOW(false, false, 9, 0.85f),
        SHADOW_TRANSLUCENT(false, true, 13, 0.90f),
        GTA_SKY_BLACK(false, false, 11, 0.88f);

        public final boolean chronoHelix;
        public final boolean translucentText;
        public final int brightnessLevel;
        public final float startDarkBlend;

        FlakeStyle(boolean chronoHelix, boolean translucentText, int brightnessLevel, float startDarkBlend) {
            this.chronoHelix = chronoHelix;
            this.translucentText = translucentText;
            this.brightnessLevel = brightnessLevel;
            this.startDarkBlend = startDarkBlend;
        }
    }

    public record EquipmentSnapshot(
            ItemStack helmet,
            ItemStack chestplate,
            ItemStack leggings,
            ItemStack boots,
            ItemStack mainHand,
            ItemStack offHand
    ) {
        public static EquipmentSnapshot fromPlayer(Player player) {
            if (player == null) return new EquipmentSnapshot(null, null, null, null, null, null);
            EntityEquipment equip = player.getEquipment();
            if (equip == null) return new EquipmentSnapshot(null, null, null, null, null, null);
            return new EquipmentSnapshot(
                    equip.getHelmet(),
                    equip.getChestplate(),
                    equip.getLeggings(),
                    equip.getBoots(),
                    equip.getItemInMainHand(),
                    equip.getItemInOffHand()
            );
        }

        public static EquipmentSnapshot forWardrobe(ItemStack[] savedArmor, ItemStack savedOffHand) {
            ItemStack boots = savedArmor != null && savedArmor.length > 0 ? savedArmor[0] : null;
            ItemStack legs = savedArmor != null && savedArmor.length > 1 ? savedArmor[1] : null;
            ItemStack chest = savedArmor != null && savedArmor.length > 2 ? savedArmor[2] : null;
            ItemStack helm = savedArmor != null && savedArmor.length > 3 ? savedArmor[3] : null;
            return new EquipmentSnapshot(helm, chest, legs, boots, null, savedOffHand);
        }
    }

    // Charcoal / Obsidian target colors for detached rising/falling pixel slivers
    private static final int[] OBSIDIAN_STREAM_COLORS = {
            0x101014, 0x16161D, 0x1D1D26, 0x0B0B0E, 0x24242E
    };
    private static final int[] CHRONO_STREAM_COLORS = {
            0x12101B, 0x181526, 0x1E1B2E, 0x38BDF8, 0xA855F7, 0xFACC15
    };
    private static final int[] PURE_SHADOW_COLORS = {
            0x060609, 0x0B0B10, 0x101018, 0x151520, 0x040406
    };

    private record CachedSkin(BufferedImage skin, BufferedImage cape, boolean slim) {}

    public static final class PixelSlot {
        public final int indexInBand;
        public final float worldOffsetX;
        public final float worldOffsetY;
        public final float worldOffsetZ;
        public final float peelThresholdY;
        public final int rgb;

        PixelSlot(int indexInBand, float worldOffsetX, float worldOffsetY, float worldOffsetZ, float peelThresholdY, int rgb) {
            this.indexInBand = indexInBand;
            this.worldOffsetX = worldOffsetX;
            this.worldOffsetY = worldOffsetY;
            this.worldOffsetZ = worldOffsetZ;
            this.peelThresholdY = peelThresholdY;
            this.rgb = rgb;
        }
    }

    public static final class BandDisplayGroup {
        public final ItemDisplay display;
        public final NamespacedKey modelKey;
        public final List<PixelSlot> activePixels;
        public final List<Boolean> flags;
        public final List<Color> colors;
        public final float minThresholdY;
        public final float maxThresholdY;
        public int visibleCount;
        public boolean fullyDissolved = false;
        public boolean fullyReconstructed = false;

        BandDisplayGroup(
                ItemDisplay display,
                NamespacedKey modelKey,
                List<PixelSlot> activePixels,
                List<Boolean> flags,
                List<Color> colors,
                float minThresholdY,
                float maxThresholdY,
                int visibleCount
        ) {
            this.display = display;
            this.modelKey = modelKey;
            this.activePixels = activePixels;
            this.flags = flags;
            this.colors = colors;
            this.minThresholdY = minThresholdY;
            this.maxThresholdY = maxThresholdY;
            this.visibleCount = visibleCount;
        }
    }

    public static final class HeldItemProp {
        public final ItemDisplay display;
        public final ItemStack originalStack;
        public final float localX;
        public final float localY;
        public final float localZ;
        public final Quaternionf rotation;
        public final Vector3f restingScale;
        public final int[] sampledPalette;
        public boolean dissolved = false;
        public boolean reconstructed = false;

        HeldItemProp(
                ItemDisplay display,
                ItemStack originalStack,
                float localX,
                float localY,
                float localZ,
                Quaternionf rotation,
                Vector3f restingScale,
                int[] sampledPalette
        ) {
            this.display = display;
            this.originalStack = originalStack;
            this.localX = localX;
            this.localY = localY;
            this.localZ = localZ;
            this.rotation = rotation;
            this.restingScale = restingScale;
            this.sampledPalette = sampledPalette;
        }
    }

    public static final class VoxelCharacterRig {
        public final Location baseLoc;
        public final float bodyYaw;
        public final double yawRad;
        public final Player privateViewer;
        public final List<BandDisplayGroup> bandGroups = new ArrayList<>(48);
        public final List<HeldItemProp> heldProps = new ArrayList<>(2);
        public final Deque<Display> activeFlakes = new ArrayDeque<>(MAX_ACTIVE_FLAKES);

        VoxelCharacterRig(Location baseLoc, float bodyYaw, double yawRad, Player privateViewer) {
            this.baseLoc = baseLoc.clone();
            this.bodyYaw = bodyYaw;
            this.yawRad = yawRad;
            this.privateViewer = privateViewer;
        }

        public void registerPrivateTo(WardrobeManager wm, UUID owner) {
            if (wm == null || owner == null) return;
            for (BandDisplayGroup g : bandGroups) {
                if (g.display != null) wm.registerPrivateEntity(g.display.getEntityId(), owner);
            }
            for (HeldItemProp hp : heldProps) {
                if (hp.display != null) wm.registerPrivateEntity(hp.display.getEntityId(), owner);
            }
            for (Display flake : activeFlakes) {
                if (flake != null) wm.registerPrivateEntity(flake.getEntityId(), owner);
            }
        }

        public void unregisterAndRemove(WardrobeManager wm) {
            for (BandDisplayGroup g : bandGroups) {
                if (g.display != null) {
                    if (wm != null) wm.unregisterPrivateEntity(g.display.getEntityId());
                    if (g.display.isValid()) g.display.remove();
                }
            }
            bandGroups.clear();
            for (HeldItemProp hp : heldProps) {
                if (hp.display != null) {
                    if (wm != null) wm.unregisterPrivateEntity(hp.display.getEntityId());
                    if (hp.display.isValid()) hp.display.remove();
                }
            }
            heldProps.clear();
            for (Display flake : activeFlakes) {
                if (flake != null) {
                    if (wm != null) wm.unregisterPrivateEntity(flake.getEntityId());
                    if (flake.isValid()) flake.remove();
                }
            }
            activeFlakes.clear();
        }
    }

    private final Plugin plugin;
    private final Map<UUID, CachedSkin> skinCache = new ConcurrentHashMap<>();
    private final Set<UUID> pendingFetches = ConcurrentHashMap.newKeySet();
    private final Map<String, BufferedImage> bundledTextureCache = new ConcurrentHashMap<>();

    public SkinVoxelizer(Plugin plugin) {
        this.plugin = plugin;
    }

    public void warmCache(Player player, int sampleStep) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        if (skinCache.containsKey(uuid) || !pendingFetches.add(uuid)) return;

        PlayerProfile profile = player.getPlayerProfile();
        PlayerTextures textures = profile.getTextures();
        URL skinUrl = textures.getSkin();
        URL capeUrl = textures.getCape();
        boolean slim = textures.getSkinModel() == PlayerTextures.SkinModel.SLIM;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                BufferedImage skinImg = skinUrl != null ? ImageIO.read(skinUrl) : null;
                BufferedImage capeImg = capeUrl != null ? ImageIO.read(capeUrl) : null;
                if (skinImg != null) {
                    skinCache.put(uuid, new CachedSkin(skinImg, capeImg, slim));
                }
            } catch (Exception ignored) {
            } finally {
                pendingFetches.remove(uuid);
            }
        });
    }

    public void invalidate(UUID uuid) {
        if (uuid != null) {
            skinCache.remove(uuid);
        }
    }

    public void clearCache() {
        skinCache.clear();
        pendingFetches.clear();
    }

    // =========================================================================
    // 1:1 CHARACTER + ARMOR + ELYTRA + HELD WEAPON RIG SPAWNER
    // =========================================================================

    public VoxelCharacterRig spawnCharacterRig(
            Player owner,
            Location baseLoc,
            Player privateViewer,
            WardrobeManager wm,
            boolean startAsSkyColumn,
            double skyStartOffsetY,
            boolean chronoStyle,
            boolean includeDefaultCape
    ) {
        return spawnCharacterRig(
                owner,
                baseLoc,
                privateViewer,
                wm,
                startAsSkyColumn,
                skyStartOffsetY,
                chronoStyle ? FlakeStyle.LEGACY_CHRONO : FlakeStyle.LEGACY_OBSIDIAN,
                includeDefaultCape,
                null
        );
    }

    public VoxelCharacterRig spawnCharacterRig(
            Player owner,
            Location baseLoc,
            Player privateViewer,
            WardrobeManager wm,
            boolean startAsSkyColumn,
            double skyStartOffsetY,
            FlakeStyle flakeStyle,
            boolean includeDefaultCape,
            EquipmentSnapshot equipmentOverride
    ) {
        FlakeStyle effectiveFlakeStyle = flakeStyle != null ? flakeStyle : FlakeStyle.LEGACY_OBSIDIAN;
        float bodyYaw = Location.normalizeYaw(baseLoc.getYaw());
        double yawRad = Math.toRadians(bodyYaw);
        VoxelCharacterRig rig = new VoxelCharacterRig(baseLoc, bodyYaw, yawRad, privateViewer);
        World world = baseLoc.getWorld();
        if (world == null || owner == null) return rig;

        CachedSkin cached = skinCache.get(owner.getUniqueId());
        if (cached == null) {
            warmCache(owner, 1);
        }
        boolean slim = cached != null
                ? cached.slim()
                : (owner.getPlayerProfile().getTextures().getSkinModel() == PlayerTextures.SkinModel.SLIM);
        BufferedImage skinImg = cached != null ? cached.skin() : null;
        boolean has64Height = skinImg == null || skinImg.getHeight() >= 64;

        EquipmentSnapshot equip = equipmentOverride != null ? equipmentOverride : EquipmentSnapshot.fromPlayer(owner);
        ItemStack helmet = equip.helmet();
        ItemStack chestplate = equip.chestplate();
        ItemStack leggings = equip.leggings();
        ItemStack boots = equip.boots();
        ItemStack mainHand = equip.mainHand();
        ItemStack offHand = equip.offHand();

        // Pre-composite armor layer images (64x32) if worn
        BufferedImage helmetImg = composeArmorTexture(helmet, false);
        BufferedImage chestImg = (chestplate != null && chestplate.getType() != Material.ELYTRA)
                ? composeArmorTexture(chestplate, false)
                : null;
        BufferedImage leggingsImg = composeArmorTexture(leggings, true);
        BufferedImage bootsImg = composeArmorTexture(boots, false);

        String armPart = slim ? "limb3" : "limb4";
        int armW = slim ? 3 : 4;
        float armCenterX = slim ? 0.328f : 0.352f;

        // Slight natural heroic pose angles
        Quaternionf headPose = new Quaternionf().rotateX((float) Math.toRadians(-3.5));
        Quaternionf torsoPose = new Quaternionf();
        boolean hasMainHand = mainHand != null && mainHand.getType() != Material.AIR;
        boolean hasOffHand = offHand != null && offHand.getType() != Material.AIR;
        Quaternionf rightArmPose = new Quaternionf()
                .rotateX((float) Math.toRadians(hasMainHand ? -28.0 : -4.0))
                .rotateZ((float) Math.toRadians(-4.0));
        Quaternionf leftArmPose = new Quaternionf()
                .rotateX((float) Math.toRadians(hasOffHand ? -18.0 : 3.5))
                .rotateZ((float) Math.toRadians(4.0));
        Quaternionf rightLegPose = new Quaternionf().rotateZ((float) Math.toRadians(-1.5));
        Quaternionf leftLegPose = new Quaternionf().rotateZ((float) Math.toRadians(1.5));

        // --- 1. HEAD (center Y = 1.641m = (24+4)*15/16/16, 8x8x8) ---
        spawnBoxPartBands(rig, world, wm, "head", 8, 8, 8,
                0.0f, 1.641f, 0.0f, PLAYER_SCALE, headPose,
                skinImg, 8, 8, 24, 8, 0, 8, 16, 8, 8, 0, 16, 0,
                startAsSkyColumn, false);
        if (skinImg != null) {
            spawnBoxPartBands(rig, world, wm, "head", 8, 8, 8,
                    0.0f, 1.641f, 0.0f, PLAYER_SCALE * 1.09f, headPose,
                    skinImg, 40, 8, 56, 8, 32, 8, 48, 8, 40, 0, 48, 0,
                    startAsSkyColumn, true);
        }
        if (helmetImg != null) {
            spawnBoxPartBands(rig, world, wm, "head", 8, 8, 8,
                    0.0f, 1.641f, 0.0f, PLAYER_SCALE * 1.17f, headPose,
                    helmetImg, 8, 8, 24, 8, 0, 8, 16, 8, 8, 0, 16, 0,
                    startAsSkyColumn, true);
        }

        // --- 2. TORSO (center Y = 1.055m = (12+6)*15/16/16, 8x12x4) ---
        spawnBoxPartBands(rig, world, wm, "torso", 8, 12, 4,
                0.0f, 1.055f, 0.0f, PLAYER_SCALE, torsoPose,
                skinImg, 20, 20, 32, 20, 16, 20, 28, 20, 20, 16, 28, 16,
                startAsSkyColumn, false);
        if (skinImg != null && has64Height) {
            spawnBoxPartBands(rig, world, wm, "torso", 8, 12, 4,
                    0.0f, 1.055f, 0.0f, PLAYER_SCALE * 1.05f, torsoPose,
                    skinImg, 20, 36, 32, 36, 16, 36, 28, 36, 20, 32, 28, 32,
                    startAsSkyColumn, true);
        }
        if (leggingsImg != null) {
            spawnBoxPartBands(rig, world, wm, "torso", 8, 12, 4,
                    0.0f, 1.055f, 0.0f, PLAYER_SCALE * 1.08f, torsoPose,
                    leggingsImg, 20, 20, 32, 20, 16, 20, 28, 20, 20, 16, 28, 16,
                    startAsSkyColumn, true);
        }
        if (chestImg != null) {
            spawnBoxPartBands(rig, world, wm, "torso", 8, 12, 4,
                    0.0f, 1.055f, 0.0f, PLAYER_SCALE * 1.15f, torsoPose,
                    chestImg, 20, 20, 32, 20, 16, 20, 28, 20, 20, 16, 28, 16,
                    startAsSkyColumn, true);
        }

        // --- 3. RIGHT ARM (center X = -armCenterX, Y = 1.055m) ---
        spawnBoxPartBands(rig, world, wm, armPart, armW, 12, 4,
                -armCenterX, 1.055f, 0.0f, PLAYER_SCALE, rightArmPose,
                skinImg, 44, 20, 44 + armW + 4, 20, 40, 20, 44 + armW, 20, 44, 16, 44 + armW, 16,
                startAsSkyColumn, false);
        if (skinImg != null && has64Height) {
            spawnBoxPartBands(rig, world, wm, armPart, armW, 12, 4,
                    -armCenterX, 1.055f, 0.0f, PLAYER_SCALE * 1.06f, rightArmPose,
                    skinImg, 44, 36, 44 + armW + 4, 36, 40, 36, 44 + armW, 36, 44, 32, 44 + armW, 32,
                    startAsSkyColumn, true);
        }
        if (chestImg != null) {
            // Armor uses 4px-wide UV (44,20), sampled cleanly across armW
            spawnBoxPartBands(rig, world, wm, armPart, armW, 12, 4,
                    -armCenterX, 1.055f, 0.0f, PLAYER_SCALE * 1.14f, rightArmPose,
                    chestImg, 44, 20, 52, 20, 40, 20, 48, 20, 44, 16, 48, 16,
                    startAsSkyColumn, true);
        }

        // --- 4. LEFT ARM (center X = +armCenterX, Y = 1.055m) ---
        int laFU = has64Height ? 36 : 44;
        int laFV = has64Height ? 52 : 20;
        int laBU = has64Height ? (36 + armW + 4) : (44 + armW + 4);
        int laRU = has64Height ? 32 : 40;
        int laLU = has64Height ? (36 + armW) : (44 + armW);
        int laTU = has64Height ? 36 : 44;
        int laTV = has64Height ? 48 : 16;
        spawnBoxPartBands(rig, world, wm, armPart, armW, 12, 4,
                armCenterX, 1.055f, 0.0f, PLAYER_SCALE, leftArmPose,
                skinImg, laFU, laFV, laBU, laFV, laRU, laFV, laLU, laFV, laTU, laTV, laTU + armW, laTV,
                startAsSkyColumn, false);
        if (skinImg != null && has64Height) {
            spawnBoxPartBands(rig, world, wm, armPart, armW, 12, 4,
                    armCenterX, 1.055f, 0.0f, PLAYER_SCALE * 1.06f, leftArmPose,
                    skinImg, 52, 52, 52 + armW + 4, 52, 48, 52, 52 + armW, 52, 52, 48, 52 + armW, 48,
                    startAsSkyColumn, true);
        }
        if (chestImg != null) {
            spawnBoxPartBands(rig, world, wm, armPart, armW, 12, 4,
                    armCenterX, 1.055f, 0.0f, PLAYER_SCALE * 1.14f, leftArmPose,
                    chestImg, 44, 20, 52, 20, 40, 20, 48, 20, 44, 16, 48, 16,
                    startAsSkyColumn, true);
        }

        // --- 5. RIGHT LEG (center X = -0.118m, Y = 0.352m, 4x12x4) ---
        spawnBoxPartBands(rig, world, wm, "limb4", 4, 12, 4,
                -0.118f, 0.352f, 0.0f, PLAYER_SCALE, rightLegPose,
                skinImg, 4, 20, 12, 20, 0, 20, 8, 20, 4, 16, 8, 16,
                startAsSkyColumn, false);
        if (skinImg != null && has64Height) {
            spawnBoxPartBands(rig, world, wm, "limb4", 4, 12, 4,
                    -0.118f, 0.352f, 0.0f, PLAYER_SCALE * 1.05f, rightLegPose,
                    skinImg, 4, 36, 12, 36, 0, 36, 8, 36, 4, 32, 8, 32,
                    startAsSkyColumn, true);
        }
        if (leggingsImg != null) {
            spawnBoxPartBands(rig, world, wm, "limb4", 4, 12, 4,
                    -0.118f, 0.352f, 0.0f, PLAYER_SCALE * 1.09f, rightLegPose,
                    leggingsImg, 4, 20, 12, 20, 0, 20, 8, 20, 4, 16, 8, 16,
                    startAsSkyColumn, true);
        }
        if (bootsImg != null) {
            spawnBoxPartBands(rig, world, wm, "limb4", 4, 12, 4,
                    -0.118f, 0.352f, 0.0f, PLAYER_SCALE * 1.15f, rightLegPose,
                    bootsImg, 4, 20, 12, 20, 0, 20, 8, 20, 4, 16, 8, 16,
                    startAsSkyColumn, true);
        }

        // --- 6. LEFT LEG (center X = +0.118m, Y = 0.352m, 4x12x4) ---
        int llFU = has64Height ? 20 : 4;
        int llFV = has64Height ? 52 : 20;
        int llBU = has64Height ? 28 : 12;
        int llRU = has64Height ? 16 : 0;
        int llLU = has64Height ? 24 : 8;
        int llTU = has64Height ? 20 : 4;
        int llTV = has64Height ? 48 : 16;
        spawnBoxPartBands(rig, world, wm, "limb4", 4, 12, 4,
                0.118f, 0.352f, 0.0f, PLAYER_SCALE, leftLegPose,
                skinImg, llFU, llFV, llBU, llFV, llRU, llFV, llLU, llFV, llTU, llTV, llTU + 4, llTV,
                startAsSkyColumn, false);
        if (skinImg != null && has64Height) {
            spawnBoxPartBands(rig, world, wm, "limb4", 4, 12, 4,
                    0.118f, 0.352f, 0.0f, PLAYER_SCALE * 1.05f, leftLegPose,
                    skinImg, 4, 52, 12, 52, 0, 52, 8, 52, 4, 48, 8, 48,
                    startAsSkyColumn, true);
        }
        if (leggingsImg != null) {
            spawnBoxPartBands(rig, world, wm, "limb4", 4, 12, 4,
                    0.118f, 0.352f, 0.0f, PLAYER_SCALE * 1.09f, leftLegPose,
                    leggingsImg, 4, 20, 12, 20, 0, 20, 8, 20, 4, 16, 8, 16,
                    startAsSkyColumn, true);
        }
        if (bootsImg != null) {
            spawnBoxPartBands(rig, world, wm, "limb4", 4, 12, 4,
                    0.118f, 0.352f, 0.0f, PLAYER_SCALE * 1.15f, leftLegPose,
                    bootsImg, 4, 20, 12, 20, 0, 20, 8, 20, 4, 16, 8, 16,
                    startAsSkyColumn, true);
        }

        // --- 7. ELYTRA WINGS (when wearing Elytra OR sleek back wings like the reference video) ---
        boolean wearingElytra = chestplate != null && chestplate.getType() == Material.ELYTRA;
        if (wearingElytra || includeDefaultCape) {
            BufferedImage elytraImg = loadBundledTexture("elytra.png");
            BufferedImage capeImg = cached != null ? cached.cape() : null;
            BufferedImage wingSource = (wearingElytra || capeImg == null) ? elytraImg : capeImg;
            boolean useCapeUv = (!wearingElytra && capeImg != null);

            // Right wing & Left wing angled on the back exactly like reference video
            Quaternionf rightWingRot = new Quaternionf()
                    .rotateX((float) Math.toRadians(16.0))
                    .rotateZ((float) Math.toRadians(-14.0))
                    .rotateY((float) Math.toRadians(8.0));
            Quaternionf leftWingRot = new Quaternionf()
                    .rotateX((float) Math.toRadians(16.0))
                    .rotateZ((float) Math.toRadians(14.0))
                    .rotateY((float) Math.toRadians(-8.0));

            spawnElytraWingBands(rig, world, wm, -0.15f, 0.90f, -0.22f, PLAYER_SCALE * 0.90f,
                    rightWingRot, wingSource, useCapeUv, false, startAsSkyColumn);
            spawnElytraWingBands(rig, world, wm, 0.15f, 0.90f, -0.22f, PLAYER_SCALE * 0.90f,
                    leftWingRot, wingSource, useCapeUv, true, startAsSkyColumn);
        }

        // --- 8. HELD MAINHAND & OFFHAND ITEMS ---
        if (hasMainHand) {
            spawnHeldProp(rig, world, wm, mainHand, -armCenterX - 0.02f, 0.80f, 0.36f,
                    ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND,
                    new Quaternionf().rotateX((float) Math.toRadians(18.0)),
                    new Vector3f(0.85f, 0.85f, 0.85f),
                    startAsSkyColumn);
        }
        if (hasOffHand) {
            spawnHeldProp(rig, world, wm, offHand, armCenterX + 0.02f, 0.78f, 0.28f,
                    ItemDisplay.ItemDisplayTransform.THIRDPERSON_LEFTHAND,
                    new Quaternionf().rotateX((float) Math.toRadians(12.0)),
                    new Vector3f(0.80f, 0.80f, 0.80f),
                    startAsSkyColumn);
        }

        // If spawning for arrival (startAsSkyColumn == true), pre-seed a dense sky stream of falling dark flakes
        if (startAsSkyColumn) {
            spawnColumnAccentShards(baseLoc, 42, false, effectiveFlakeStyle, privateViewer, wm)
                    .forEach(rig.activeFlakes::addLast);
        }

        return rig;
    }

    // =========================================================================
    // TOP-DOWN DISSOLVE SWEEP & SKY-PLUNGE RECONSTRUCT SWEEP
    // =========================================================================

    public void stepDissolveSweep(
            VoxelCharacterRig rig,
            double horizonY,
            boolean chronoHelix,
            double elapsedProgress
    ) {
        stepDissolveSweep(
                rig,
                horizonY,
                chronoHelix ? FlakeStyle.LEGACY_CHRONO : FlakeStyle.LEGACY_OBSIDIAN,
                elapsedProgress
        );
    }

    public void stepDissolveSweep(
            VoxelCharacterRig rig,
            double horizonY,
            FlakeStyle flakeStyle,
            double elapsedProgress
    ) {
        if (rig == null || rig.baseLoc.getWorld() == null) return;
        FlakeStyle effectiveStyle = flakeStyle != null ? flakeStyle : FlakeStyle.LEGACY_OBSIDIAN;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (BandDisplayGroup band : rig.bandGroups) {
            if (band.fullyDissolved || band.display == null || !band.display.isValid()) continue;
            if (band.maxThresholdY < horizonY - 0.02f) {
                // Peel wave hasn't reached this band yet -> 0 packet updates!
                continue;
            }

            boolean changed = false;
            for (PixelSlot slot : band.activePixels) {
                if (Boolean.TRUE.equals(band.flags.get(slot.indexInBand)) && slot.peelThresholdY >= horizonY) {
                    band.flags.set(slot.indexInBand, Boolean.FALSE);
                    band.visibleCount--;
                    changed = true;

                    // Spawn rising pixel sliver from this exact skin/armor pixel coordinate
                    if (rng.nextInt(10) < 7) {
                        spawnDetachedFlake(rig, slot.worldOffsetX, slot.worldOffsetY, slot.worldOffsetZ,
                                slot.rgb, true, effectiveStyle, elapsedProgress);
                    }
                }
            }

            if (changed) {
                if (band.visibleCount <= 0) {
                    band.fullyDissolved = true;
                    band.display.remove();
                } else {
                    applyCustomModelData(band.display, band.modelKey, band.flags, band.colors);
                }
            }
        }

        for (HeldItemProp hp : rig.heldProps) {
            if (!hp.dissolved && hp.display != null && hp.display.isValid() && horizonY <= hp.localY + 0.08f) {
                hp.dissolved = true;
                for (int c : hp.sampledPalette) {
                    float jx = hp.localX + (rng.nextFloat() - 0.5f) * 0.18f;
                    float jy = hp.localY + (rng.nextFloat() - 0.5f) * 0.22f;
                    float jz = hp.localZ + (rng.nextFloat() - 0.5f) * 0.18f;
                    spawnDetachedFlake(rig, jx, jy, jz, c, true, effectiveStyle, elapsedProgress);
                }
                hp.display.remove();
            }
        }
    }

    public void stepReconstructSweep(
            VoxelCharacterRig rig,
            double horizonY,
            boolean chronoHelix,
            Location baseLoc,
            Player particleViewer
    ) {
        stepReconstructSweep(
                rig,
                horizonY,
                chronoHelix ? FlakeStyle.LEGACY_CHRONO : FlakeStyle.LEGACY_OBSIDIAN,
                baseLoc,
                particleViewer
        );
    }

    public void stepReconstructSweep(
            VoxelCharacterRig rig,
            double horizonY,
            FlakeStyle flakeStyle,
            Location baseLoc,
            Player particleViewer
    ) {
        if (rig == null || rig.baseLoc.getWorld() == null) return;
        FlakeStyle effectiveStyle = flakeStyle != null ? flakeStyle : FlakeStyle.LEGACY_OBSIDIAN;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (BandDisplayGroup band : rig.bandGroups) {
            if (band.fullyReconstructed || band.display == null || !band.display.isValid()) continue;
            if (band.minThresholdY > horizonY + 0.02f) {
                continue;
            }

            boolean changed = false;
            for (PixelSlot slot : band.activePixels) {
                if (Boolean.FALSE.equals(band.flags.get(slot.indexInBand)) && slot.peelThresholdY <= horizonY) {
                    band.flags.set(slot.indexInBand, Boolean.TRUE);
                    band.visibleCount++;
                    changed = true;

                    // Occasional incoming dark sliver snapping into the locking pixel
                    if (rng.nextInt(12) == 0) {
                        spawnIncomingConvergeFlake(rig, slot.worldOffsetX, slot.worldOffsetY, slot.worldOffsetZ,
                                slot.rgb, effectiveStyle);
                    }
                }
            }

            if (changed) {
                applyCustomModelData(band.display, band.modelKey, band.flags, band.colors);
                if (band.visibleCount >= band.activePixels.size()) {
                    band.fullyReconstructed = true;
                }
            }
        }

        for (HeldItemProp hp : rig.heldProps) {
            if (!hp.reconstructed && hp.display != null && hp.display.isValid() && horizonY >= hp.localY) {
                hp.reconstructed = true;
                hp.display.setItemStack(hp.originalStack.clone());
                hp.display.setInterpolationDelay(0);
                hp.display.setInterpolationDuration(5);
                hp.display.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new Quaternionf(hp.rotation),
                        new Vector3f(hp.restingScale),
                        new Quaternionf(ITEM_DISPLAY_FLIP_FIX)
                ));
            }
        }
    }

    /**
     * Spawns a tight vertical sky stream of crisp 1/16 pixel flakes (opaque subtle-lit or translucent ARGB)
     * rising (+Y) or plunging (-Y) directly along the character's vertical axis.
     */
    public List<Display> spawnColumnAccentShards(
            Location baseLoc,
            int count,
            boolean ascending,
            boolean chronoHelix,
            Player privateViewer,
            WardrobeManager wm
    ) {
        return spawnColumnAccentShards(
                baseLoc,
                count,
                ascending,
                chronoHelix ? FlakeStyle.LEGACY_CHRONO : FlakeStyle.LEGACY_OBSIDIAN,
                privateViewer,
                wm
        );
    }

    public List<Display> spawnColumnAccentShards(
            Location baseLoc,
            int count,
            boolean ascending,
            FlakeStyle flakeStyle,
            Player privateViewer,
            WardrobeManager wm
    ) {
        List<Display> flakes = new ArrayList<>(count);
        World world = baseLoc.getWorld();
        if (world == null) return flakes;
        FlakeStyle effectiveStyle = flakeStyle != null ? flakeStyle : FlakeStyle.LEGACY_OBSIDIAN;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        NamespacedKey flakeKey = new NamespacedKey(NAMESPACE, "tp_rig/flake");

        for (int i = 0; i < count; i++) {
            double r = rng.nextDouble() * (effectiveStyle.chronoHelix ? 0.28 : 0.20);
            double theta = rng.nextDouble() * Math.PI * 2.0;
            double startY = ascending
                    ? (1.15 + rng.nextDouble() * 3.8)
                    : (3.0 + rng.nextDouble() * 8.0);
            Location loc = baseLoc.clone().add(Math.cos(theta) * r, startY, Math.sin(theta) * r);
            loc.setYaw(baseLoc.getYaw());
            loc.setPitch(0f);

            int streamRgb = pickStreamColor(effectiveStyle, rng);
            Display flake = spawnFlakeDisplay(world, loc, flakeKey, streamRgb, effectiveStyle, 1,
                    0f, 0f, 0f, false, privateViewer);

            if (wm != null && privateViewer != null) {
                wm.registerPrivateEntity(flake.getEntityId(), privateViewer.getUniqueId());
            }
            flakes.add(flake);

            float dy = ascending
                    ? (float) (8.5 + rng.nextDouble() * 6.5)
                    : (float) -Math.max(1.8, startY - (0.2 + rng.nextDouble() * 1.4));
            float dx = effectiveStyle.chronoHelix
                    ? (float) (Math.cos(theta + 1.8) * 0.22 - Math.cos(theta) * r)
                    : (float) ((rng.nextDouble() - 0.5) * 0.10);
            float dz = effectiveStyle.chronoHelix
                    ? (float) (Math.sin(theta + 1.8) * 0.22 - Math.sin(theta) * r)
                    : (float) ((rng.nextDouble() - 0.5) * 0.10);

            int duration = ascending ? (14 + rng.nextInt(7)) : (12 + rng.nextInt(6));
            scheduleFlakeTransformAndCleanup(flake, flakeKey, -1, effectiveStyle, 1L, duration, dx, dy, dz, true);
        }
        return flakes;
    }

    // =========================================================================
    // INTERNAL PIXEL-BAND BUILDERS & TEXTURE COMPOSITOR
    // =========================================================================

    private void spawnBoxPartBands(
            VoxelCharacterRig rig,
            World world,
            WardrobeManager wm,
            String partName,
            int W, int H, int D,
            float partCenterX, float partCenterY, float partCenterZ,
            float scale,
            Quaternionf poseRot,
            BufferedImage tex,
            int frontU, int frontV,
            int backU, int backV,
            int rightU, int rightV,
            int leftU, int leftV,
            int topU, int topV,
            int botU, int botV,
            boolean startHiddenForArrival,
            boolean skipTransparent
    ) {
        int rowsPerBand = H / 4;
        float xMin = -W / 2.0f;
        float yMin = -H / 2.0f;
        float zMin = -D / 2.0f;
        float pixelWorldScale = scale / 16.0f;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        Location partWorldLoc = rotateLocalOffset(rig.baseLoc, partCenterX, partCenterY, partCenterZ, rig.yawRad, rig.bodyYaw);

        for (int b = 0; b < 4; b++) {
            int yStart = b * rowsPerBand;
            int yEnd = (b + 1) * rowsPerBand;

            List<Boolean> flags = new ArrayList<>(96);
            List<Color> colors = new ArrayList<>(96);
            List<PixelSlot> activePixels = new ArrayList<>(96);
            float minThresh = Float.MAX_VALUE;
            float maxThresh = -Float.MAX_VALUE;

            int idx = 0;
            for (int py = yStart; py < yEnd; py++) {
                int vOff = (H - 1) - py;
                float localPy = (yMin + py + 0.5f) * pixelWorldScale;

                // 1. FRONT (+Z)
                for (int px = 0; px < W; px++) {
                    int argb = samplePixel(tex, frontU + px, frontV + vOff, partName, skipTransparent);
                    idx = appendPixelSlot(idx, argb, skipTransparent, startHiddenForArrival,
                            partCenterX + (xMin + px + 0.5f) * pixelWorldScale,
                            partCenterY + localPy,
                            partCenterZ + (D / 2.0f) * pixelWorldScale,
                            rng, flags, colors, activePixels);
                }
                // 2. BACK (-Z)
                for (int px = 0; px < W; px++) {
                    int argb = samplePixel(tex, backU + (W - 1 - px), backV + vOff, partName, skipTransparent);
                    idx = appendPixelSlot(idx, argb, skipTransparent, startHiddenForArrival,
                            partCenterX + (xMin + px + 0.5f) * pixelWorldScale,
                            partCenterY + localPy,
                            partCenterZ - (D / 2.0f) * pixelWorldScale,
                            rng, flags, colors, activePixels);
                }
                // 3. RIGHT (-X)
                for (int pz = 0; pz < D; pz++) {
                    int argb = samplePixel(tex, rightU + pz, rightV + vOff, partName, skipTransparent);
                    idx = appendPixelSlot(idx, argb, skipTransparent, startHiddenForArrival,
                            partCenterX - (W / 2.0f) * pixelWorldScale,
                            partCenterY + localPy,
                            partCenterZ + (zMin + pz + 0.5f) * pixelWorldScale,
                            rng, flags, colors, activePixels);
                }
                // 4. LEFT (+X)
                for (int pz = 0; pz < D; pz++) {
                    int argb = samplePixel(tex, leftU + (D - 1 - pz), leftV + vOff, partName, skipTransparent);
                    idx = appendPixelSlot(idx, argb, skipTransparent, startHiddenForArrival,
                            partCenterX + (W / 2.0f) * pixelWorldScale,
                            partCenterY + localPy,
                            partCenterZ + (zMin + pz + 0.5f) * pixelWorldScale,
                            rng, flags, colors, activePixels);
                }
            }

            // TOP cap on band 3
            if (b == 3) {
                float topY = partCenterY + (H / 2.0f) * pixelWorldScale;
                for (int pz = 0; pz < D; pz++) {
                    for (int px = 0; px < W; px++) {
                        int argb = samplePixel(tex, topU + px, topV + pz, partName, skipTransparent);
                        idx = appendPixelSlot(idx, argb, skipTransparent, startHiddenForArrival,
                                partCenterX + (xMin + px + 0.5f) * pixelWorldScale,
                                topY,
                                partCenterZ + (zMin + pz + 0.5f) * pixelWorldScale,
                                rng, flags, colors, activePixels);
                    }
                }
            }

            // BOTTOM cap on band 0
            if (b == 0) {
                float botY = partCenterY - (H / 2.0f) * pixelWorldScale;
                for (int pz = 0; pz < D; pz++) {
                    for (int px = 0; px < W; px++) {
                        int argb = samplePixel(tex, botU + px, botV + (D - 1 - pz), partName, skipTransparent);
                        idx = appendPixelSlot(idx, argb, skipTransparent, startHiddenForArrival,
                                partCenterX + (xMin + px + 0.5f) * pixelWorldScale,
                                botY,
                                partCenterZ + (zMin + pz + 0.5f) * pixelWorldScale,
                                rng, flags, colors, activePixels);
                    }
                }
            }

            if (activePixels.isEmpty()) {
                // Opaque culling: skip spawning empty 2nd-layer or armor bands completely!
                continue;
            }

            for (PixelSlot s : activePixels) {
                if (s.peelThresholdY < minThresh) minThresh = s.peelThresholdY;
                if (s.peelThresholdY > maxThresh) maxThresh = s.peelThresholdY;
            }

            NamespacedKey modelKey = new NamespacedKey(NAMESPACE, "tp_rig/" + partName + "_b" + b);
            ItemStack stack = new ItemStack(Material.PAPER);
            applyStackModelAndCmd(stack, modelKey, flags, colors);

            ItemDisplay display = world.spawn(partWorldLoc, ItemDisplay.class, d -> {
                d.setItemStack(stack);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(1);
                d.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new Quaternionf(poseRot),
                        new Vector3f(scale, scale, scale),
                        new Quaternionf(ITEM_DISPLAY_FLIP_FIX)
                ));
                if (rig.privateViewer != null) d.setVisibleByDefault(false);
            });

            if (rig.privateViewer != null) {
                rig.privateViewer.showEntity(plugin, display);
                if (wm != null) wm.registerPrivateEntity(display.getEntityId(), rig.privateViewer.getUniqueId());
            }

            int initVis = startHiddenForArrival ? 0 : activePixels.size();
            rig.bandGroups.add(new BandDisplayGroup(display, modelKey, activePixels, flags, colors, minThresh, maxThresh, initVis));
        }
    }

    private void spawnElytraWingBands(
            VoxelCharacterRig rig,
            World world,
            WardrobeManager wm,
            float wingCenterX, float wingCenterY, float wingCenterZ,
            float scale,
            Quaternionf wingRot,
            BufferedImage wingSource,
            boolean useCapeUv,
            boolean mirrorX,
            boolean startHiddenForArrival
    ) {
        int W = 10, H = 20;
        int rowsPerBand = 5;
        float xMin = -W / 2.0f;
        float yMin = -H / 2.0f;
        float pixelWorldScale = scale / 16.0f;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        Location wingLoc = rotateLocalOffset(rig.baseLoc, wingCenterX, wingCenterY, wingCenterZ, rig.yawRad, rig.bodyYaw);

        for (int b = 0; b < 4; b++) {
            int yStart = b * rowsPerBand;
            int yEnd = (b + 1) * rowsPerBand;

            List<Boolean> flags = new ArrayList<>(50);
            List<Color> colors = new ArrayList<>(50);
            List<PixelSlot> activePixels = new ArrayList<>(50);
            float minThresh = Float.MAX_VALUE;
            float maxThresh = -Float.MAX_VALUE;

            int idx = 0;
            for (int py = yStart; py < yEnd; py++) {
                int vOff = (H - 1) - py;
                float localPy = (yMin + py + 0.5f) * pixelWorldScale;
                for (int px = 0; px < W; px++) {
                    int samplePx = mirrorX ? (W - 1 - px) : px;
                    int u = useCapeUv ? (1 + Math.min(9, samplePx)) : (34 + samplePx);
                    int v = useCapeUv ? (1 + (vOff * 15) / 19) : (2 + vOff);
                    int argb = (wingSource != null && u < wingSource.getWidth() && v < wingSource.getHeight())
                            ? wingSource.getRGB(u, v)
                            : 0xFF474754;
                    idx = appendPixelSlot(idx, argb, true, startHiddenForArrival,
                            wingCenterX + (xMin + px + 0.5f) * pixelWorldScale,
                            wingCenterY + localPy,
                            wingCenterZ,
                            rng, flags, colors, activePixels);
                }
            }

            if (activePixels.isEmpty()) continue;
            for (PixelSlot s : activePixels) {
                if (s.peelThresholdY < minThresh) minThresh = s.peelThresholdY;
                if (s.peelThresholdY > maxThresh) maxThresh = s.peelThresholdY;
            }

            NamespacedKey modelKey = new NamespacedKey(NAMESPACE, "tp_rig/elytra_b" + b);
            ItemStack stack = new ItemStack(Material.PAPER);
            applyStackModelAndCmd(stack, modelKey, flags, colors);

            ItemDisplay display = world.spawn(wingLoc, ItemDisplay.class, d -> {
                d.setItemStack(stack);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(1);
                d.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new Quaternionf(wingRot),
                        new Vector3f(scale, scale, scale),
                        new Quaternionf(ITEM_DISPLAY_FLIP_FIX)
                ));
                if (rig.privateViewer != null) d.setVisibleByDefault(false);
            });

            if (rig.privateViewer != null) {
                rig.privateViewer.showEntity(plugin, display);
                if (wm != null) wm.registerPrivateEntity(display.getEntityId(), rig.privateViewer.getUniqueId());
            }

            int initVis = startHiddenForArrival ? 0 : activePixels.size();
            rig.bandGroups.add(new BandDisplayGroup(display, modelKey, activePixels, flags, colors, minThresh, maxThresh, initVis));
        }
    }

    private void spawnHeldProp(
            VoxelCharacterRig rig,
            World world,
            WardrobeManager wm,
            ItemStack item,
            float localX, float localY, float localZ,
            ItemDisplay.ItemDisplayTransform displayContext,
            Quaternionf rot,
            Vector3f scale,
            boolean startHiddenForArrival
    ) {
        Location handLoc = rotateLocalOffset(rig.baseLoc, localX, localY, localZ, rig.yawRad, rig.bodyYaw);
        ItemStack cloned = item.clone();
        Vector3f initScale = startHiddenForArrival ? new Vector3f(0.001f, 0.001f, 0.001f) : new Vector3f(scale);

        ItemDisplay display = world.spawn(handLoc, ItemDisplay.class, d -> {
            d.setItemStack(startHiddenForArrival ? new ItemStack(Material.AIR) : cloned);
            d.setItemDisplayTransform(displayContext);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(1);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(rot), initScale, new Quaternionf(ITEM_DISPLAY_FLIP_FIX)));
            if (rig.privateViewer != null) d.setVisibleByDefault(false);
        });

        if (rig.privateViewer != null) {
            rig.privateViewer.showEntity(plugin, display);
            if (wm != null) wm.registerPrivateEntity(display.getEntityId(), rig.privateViewer.getUniqueId());
        }

        int[] palette = inferItemPalette(item.getType());
        rig.heldProps.add(new HeldItemProp(display, cloned, localX, localY, localZ, rot, scale, palette));
    }

    private int appendPixelSlot(
            int idx,
            int argb,
            boolean skipTransparent,
            boolean startHiddenForArrival,
            float wx, float wy, float wz,
            ThreadLocalRandom rng,
            List<Boolean> flags,
            List<Color> colors,
            List<PixelSlot> activePixels
    ) {
        int alpha = (argb >>> 24) & 0xFF;
        int rgb = argb & 0xFFFFFF;
        if (skipTransparent && alpha < 80) {
            flags.add(Boolean.FALSE);
            colors.add(Color.fromRGB(0));
            return idx + 1;
        }
        boolean initialVisible = !startHiddenForArrival;
        flags.add(initialVisible);
        colors.add(Color.fromRGB(rgb));
        float jitter = (rng.nextFloat() - 0.5f) * 0.085f;
        activePixels.add(new PixelSlot(idx, wx, wy, wz, wy + jitter, rgb));
        return idx + 1;
    }

    private void spawnDetachedFlake(
            VoxelCharacterRig rig,
            float localX, float localY, float localZ,
            int originalRgb,
            boolean ascending,
            FlakeStyle flakeStyle,
            double elapsedProgress
    ) {
        World world = rig.baseLoc.getWorld();
        if (world == null) return;

        // Enforce active flake cap by recycling oldest flake
        if (rig.activeFlakes.size() >= MAX_ACTIVE_FLAKES) {
            Display oldest = rig.activeFlakes.pollFirst();
            if (oldest != null && oldest.isValid()) oldest.remove();
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location spawnLoc = rotateLocalOffset(rig.baseLoc, localX, localY, localZ, rig.yawRad, rig.bodyYaw);
        NamespacedKey flakeKey = new NamespacedKey(NAMESPACE, "tp_rig/flake");

        int startRgb = blendRgb(originalRgb, 0x0A0A0F, flakeStyle.startDarkBlend);
        int streamRgb = pickStreamColor(flakeStyle, rng);

        Display flake = spawnFlakeDisplay(world, spawnLoc, flakeKey, startRgb, flakeStyle, 2,
                0f, 0f, 0f, false, rig.privateViewer);
        rig.activeFlakes.addLast(flake);

        // Pull radially inward toward a tight column above the character's center and launch along Y
        float dx;
        float dz;
        if (flakeStyle.chronoHelix) {
            double angle = localY * 4.5 + elapsedProgress * 6.0 + rng.nextDouble() * 0.9;
            double rad = 0.14 + rng.nextDouble() * 0.16;
            dx = (float) (Math.cos(angle) * rad - localX * 0.65);
            dz = (float) (Math.sin(angle) * rad - localZ * 0.65);
        } else {
            dx = (float) (-localX * 0.62 + (rng.nextDouble() - 0.5) * 0.16);
            dz = (float) (-localZ * 0.62 + (rng.nextDouble() - 0.5) * 0.16);
        }
        float riseY = ascending ? (float) (7.5 + rng.nextDouble() * 7.0) : (float) -(4.5 + rng.nextDouble() * 4.0);
        int travelTicks = 14 + rng.nextInt(6);

        scheduleFlakeTransformAndCleanup(flake, flakeKey, streamRgb, flakeStyle, 2L, travelTicks, dx, riseY, dz, true);
    }

    private void spawnIncomingConvergeFlake(
            VoxelCharacterRig rig,
            float targetLocalX, float targetLocalY, float targetLocalZ,
            int finalRgb,
            FlakeStyle flakeStyle
    ) {
        World world = rig.baseLoc.getWorld();
        if (world == null) return;
        if (rig.activeFlakes.size() >= MAX_ACTIVE_FLAKES) {
            Display oldest = rig.activeFlakes.pollFirst();
            if (oldest != null && oldest.isValid()) oldest.remove();
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        float startY = targetLocalY + 3.2f + rng.nextFloat() * 2.6f;
        Location targetLoc = rotateLocalOffset(rig.baseLoc, targetLocalX, targetLocalY, targetLocalZ, rig.yawRad, rig.bodyYaw);
        NamespacedKey flakeKey = new NamespacedKey(NAMESPACE, "tp_rig/flake");
        int streamRgb = pickStreamColor(flakeStyle, rng);

        Display flake = spawnFlakeDisplay(world, targetLoc, flakeKey, streamRgb, flakeStyle, 1,
                (rng.nextFloat() - 0.5f) * 0.14f, startY - targetLocalY, (rng.nextFloat() - 0.5f) * 0.14f,
                true, rig.privateViewer);
        rig.activeFlakes.addLast(flake);

        scheduleFlakeTransformAndCleanup(flake, flakeKey, -1, flakeStyle, 1L, 5, 0f, 0f, 0f, false);
    }

    private Display spawnFlakeDisplay(
            World world,
            Location loc,
            NamespacedKey flakeKey,
            int rgb,
            FlakeStyle style,
            int interpDuration,
            float tx, float ty, float tz,
            boolean elongated,
            Player privateViewer
    ) {
        Display display;
        if (style.translucentText) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            Vector3f sc = elongated ? new Vector3f(0.32f, 0.54f, 0.32f) : new Vector3f(0.36f, 0.44f, 0.36f);
            display = world.spawn(loc, TextDisplay.class, td -> {
                td.setText(" ");
                td.setLineWidth(16);
                td.setShadowed(false);
                td.setSeeThrough(false);
                td.setDefaultBackground(false);
                td.setBackgroundColor(Color.fromARGB(182, r, g, b));
                td.setBillboard(Display.Billboard.VERTICAL);
                td.setBrightness(new Display.Brightness(style.brightnessLevel, style.brightnessLevel));
                td.setShadowRadius(0f);
                td.setShadowStrength(0f);
                td.setInterpolationDelay(0);
                td.setInterpolationDuration(interpDuration);
                td.setTransformation(new Transformation(
                        new Vector3f(tx, ty, tz),
                        new Quaternionf(),
                        sc,
                        new Quaternionf()
                ));
                if (privateViewer != null) td.setVisibleByDefault(false);
            });
        } else {
            Vector3f sc = elongated ? new Vector3f(0.85f, 1.45f, 0.85f) : new Vector3f(0.95f, 1.15f, 0.95f);
            display = world.spawn(loc, ItemDisplay.class, d -> {
                d.setItemStack(buildFlakeItemStack(flakeKey, rgb));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setBrightness(new Display.Brightness(style.brightnessLevel, style.brightnessLevel));
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(interpDuration);
                d.setTransformation(new Transformation(
                        new Vector3f(tx, ty, tz),
                        new Quaternionf(),
                        sc,
                        new Quaternionf()
                ));
                if (privateViewer != null) d.setVisibleByDefault(false);
            });
        }
        if (privateViewer != null) {
            privateViewer.showEntity(plugin, display);
        }
        return display;
    }

    private void scheduleFlakeTransformAndCleanup(
            Display flake,
            NamespacedKey flakeKey,
            int updatedRgb,
            FlakeStyle style,
            long startDelayTicks,
            int travelTicks,
            float targetDx, float targetDy, float targetDz,
            boolean endElongated
    ) {
        Vector3f targetScale = style.translucentText
                ? (endElongated ? new Vector3f(0.32f, 0.54f, 0.32f) : new Vector3f(0.36f, 0.42f, 0.36f))
                : (endElongated ? new Vector3f(0.85f, 1.45f, 0.85f) : new Vector3f(0.95f, 0.95f, 0.95f));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (flake.isValid()) {
                if (updatedRgb >= 0) {
                    if (flake instanceof ItemDisplay itemDisp) {
                        itemDisp.setItemStack(buildFlakeItemStack(flakeKey, updatedRgb));
                    } else if (flake instanceof TextDisplay textDisp) {
                        textDisp.setBackgroundColor(Color.fromARGB(
                                182, (updatedRgb >> 16) & 0xFF, (updatedRgb >> 8) & 0xFF, updatedRgb & 0xFF
                        ));
                    }
                }
                flake.setInterpolationDelay(0);
                flake.setInterpolationDuration(travelTicks);
                flake.setTransformation(new Transformation(
                        new Vector3f(targetDx, targetDy, targetDz),
                        new Quaternionf(),
                        targetScale,
                        new Quaternionf()
                ));
            }
        }, startDelayTicks);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (flake.isValid()) {
                flake.remove();
            }
        }, startDelayTicks + travelTicks + 2L);
    }

    // =========================================================================
    // ARMOR & TRIM COMPOSITING (HUMAN / LEGGINGS / LEATHER / TRIM PALETTES)
    // =========================================================================

    private BufferedImage composeArmorTexture(ItemStack item, boolean leggingsLayer) {
        if (item == null || item.getType() == Material.AIR) return null;
        String tier = resolveArmorTier(item.getType());
        if (tier == null) return null;

        String folder = leggingsLayer ? "humanoid_leggings/" : "humanoid/";
        BufferedImage base = loadBundledTexture(folder + tier + ".png");
        if (base == null) return null;

        BufferedImage canvas = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        ItemMeta meta = item.getItemMeta();

        // 1. Base armor layer (with Leather dye tint if leather)
        int leatherTint = 0xFFA06540;
        if ("leather".equals(tier) && meta instanceof LeatherArmorMeta lm) {
            leatherTint = lm.getColor().asRGB();
        }

        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                int argb = base.getRGB(x, y);
                if ("leather".equals(tier) && ((argb >>> 24) & 0xFF) > 0) {
                    argb = multiplyRgb(argb, leatherTint);
                }
                canvas.setRGB(x, y, argb);
            }
        }

        // Leather undyed overlay
        if ("leather".equals(tier)) {
            BufferedImage overlay = loadBundledTexture(folder + "leather_overlay.png");
            if (overlay != null) {
                alphaComposite(canvas, overlay);
            }
        }

        // 2. ArmorTrim pattern + material color palette
        if (meta instanceof ArmorMeta am && am.hasTrim() && am.getTrim() != null) {
            ArmorTrim trim = am.getTrim();
            String patternKey = trim.getPattern().getKey().getKey().toLowerCase(Locale.ROOT);
            String matKey = trim.getMaterial().getKey().getKey().toLowerCase(Locale.ROOT);
            if ("gold".equals(matKey) && loadBundledTexture("trims/color_palettes/gold.png") == null) {
                matKey = "gold";
            }
            String trimFolder = leggingsLayer ? "trims/humanoid_leggings/" : "trims/humanoid/";
            BufferedImage patternImg = loadBundledTexture(trimFolder + patternKey + ".png");
            BufferedImage basePalette = loadBundledTexture("trims/color_palettes/trim_palette.png");
            BufferedImage matPalette = loadBundledTexture("trims/color_palettes/" + matKey + ".png");
            if (matPalette == null && "gold".equals(matKey)) {
                matPalette = loadBundledTexture("trims/color_palettes/gold_darker.png");
            }
            if (patternImg != null && basePalette != null && matPalette != null) {
                applyTrimOverlay(canvas, patternImg, basePalette, matPalette);
            }
        }

        return canvas;
    }

    private void applyTrimOverlay(
            BufferedImage target,
            BufferedImage patternImg,
            BufferedImage basePalette,
            BufferedImage matPalette
    ) {
        Map<Integer, Integer> lut = new HashMap<>(16);
        int len = Math.min(basePalette.getWidth(), matPalette.getWidth());
        for (int i = 0; i < len; i++) {
            lut.put(basePalette.getRGB(i, 0) & 0xFFFFFF, matPalette.getRGB(i, 0));
        }

        int w = Math.min(target.getWidth(), patternImg.getWidth());
        int h = Math.min(target.getHeight(), patternImg.getHeight());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pArgb = patternImg.getRGB(x, y);
                int alpha = (pArgb >>> 24) & 0xFF;
                if (alpha >= 64) {
                    int mapped = lut.getOrDefault(pArgb & 0xFFFFFF, pArgb);
                    target.setRGB(x, y, (alpha << 24) | (mapped & 0xFFFFFF));
                }
            }
        }
    }

    private void alphaComposite(BufferedImage dst, BufferedImage src) {
        int w = Math.min(dst.getWidth(), src.getWidth());
        int h = Math.min(dst.getHeight(), src.getHeight());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int s = src.getRGB(x, y);
                if (((s >>> 24) & 0xFF) >= 80) {
                    dst.setRGB(x, y, s);
                }
            }
        }
    }

    private String resolveArmorTier(Material mat) {
        String name = mat.name();
        if (name.startsWith("NETHERITE_")) return "netherite";
        if (name.startsWith("DIAMOND_")) return "diamond";
        if (name.startsWith("IRON_")) return "iron";
        if (name.startsWith("GOLDEN_")) return "gold";
        if (name.startsWith("CHAINMAIL_")) return "chainmail";
        if (name.startsWith("LEATHER_")) return "leather";
        if (name.startsWith("COPPER_")) return "copper";
        if (mat == Material.TURTLE_HELMET) return "turtle_scute";
        return null;
    }

    private BufferedImage loadBundledTexture(String relPath) {
        return bundledTextureCache.computeIfAbsent(relPath, key -> {
            try (InputStream in = plugin.getResource("tp_textures/" + key)) {
                if (in != null) {
                    return ImageIO.read(in);
                }
            } catch (Exception ignored) {}
            return null;
        });
    }

    private int samplePixel(BufferedImage tex, int u, int v, String partName, boolean skipTransparent) {
        if (tex != null && u >= 0 && v >= 0 && u < tex.getWidth() && v < tex.getHeight()) {
            return tex.getRGB(u, v);
        }
        if (skipTransparent) {
            return 0x00000000;
        }
        // Clean default Steve/Alex fallback palette before async skin arrives
        return switch (partName) {
            case "head" -> (v < 3) ? 0xFF3B2719 : 0xFFD6A582;
            case "torso" -> 0xFF28829E;
            default -> (v >= 26) ? 0xFF3B3678 : 0xFFD6A582;
        };
    }

    private int[] inferItemPalette(Material mat) {
        String n = mat.name();
        if (n.contains("DIAMOND")) return new int[]{0x4AEDD9, 0x2BC7B5, 0x8CF4E2, 0x3D2E1E};
        if (n.contains("NETHERITE")) return new int[]{0x443A3B, 0x31292A, 0x654740, 0x262021};
        if (n.contains("GOLD") || n.contains("GOLDEN")) return new int[]{0xFCEE4B, 0xD9A31B, 0xFFF789};
        if (n.contains("IRON")) return new int[]{0xD8D8D8, 0xA8A8A8, 0xECECEC};
        if (n.contains("FIREWORK")) return new int[]{0xC83232, 0xE8E4D8, 0x7A5C3E};
        return new int[]{0x94A3B8, 0x475569, 0x1E293B};
    }

    private int pickStreamColor(FlakeStyle flakeStyle, ThreadLocalRandom rng) {
        if (flakeStyle == FlakeStyle.LEGACY_CHRONO) {
            return CHRONO_STREAM_COLORS[rng.nextInt(CHRONO_STREAM_COLORS.length)];
        }
        if (flakeStyle == FlakeStyle.LEGACY_OBSIDIAN) {
            return OBSIDIAN_STREAM_COLORS[rng.nextInt(OBSIDIAN_STREAM_COLORS.length)];
        }
        return PURE_SHADOW_COLORS[rng.nextInt(PURE_SHADOW_COLORS.length)];
    }

    private static int multiplyRgb(int argb, int tintRgb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (((argb >> 16) & 0xFF) * ((tintRgb >> 16) & 0xFF)) / 255;
        int g = (((argb >> 8) & 0xFF) * ((tintRgb >> 8) & 0xFF)) / 255;
        int b = ((argb & 0xFF) * (tintRgb & 0xFF)) / 255;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blendRgb(int rgbA, int rgbB, float t) {
        int r = (int) (((rgbA >> 16) & 0xFF) * (1f - t) + ((rgbB >> 16) & 0xFF) * t);
        int g = (int) (((rgbA >> 8) & 0xFF) * (1f - t) + ((rgbB >> 8) & 0xFF) * t);
        int b = (int) ((rgbA & 0xFF) * (1f - t) + (rgbB & 0xFF) * t);
        return (r << 16) | (g << 8) | b;
    }

    private ItemStack buildFlakeItemStack(NamespacedKey flakeKey, int rgb) {
        ItemStack stack = new ItemStack(Material.PAPER);
        applyStackModelAndCmd(stack, flakeKey, List.of(Boolean.TRUE), List.of(Color.fromRGB(rgb & 0xFFFFFF)));
        return stack;
    }

    private void applyCustomModelData(ItemDisplay display, NamespacedKey modelKey, List<Boolean> flags, List<Color> colors) {
        ItemStack stack = new ItemStack(Material.PAPER);
        applyStackModelAndCmd(stack, modelKey, flags, colors);
        display.setItemStack(stack);
    }

    private void applyStackModelAndCmd(ItemStack stack, NamespacedKey modelKey, List<Boolean> flags, List<Color> colors) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        meta.setItemModel(modelKey);
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setFlags(flags);
        cmd.setColors(colors);
        meta.setCustomModelDataComponent(cmd);
        stack.setItemMeta(meta);
    }

    private Location rotateLocalOffset(Location origin, float localX, float localY, float localZ, double yawRad, float yawDeg) {
        double forward = localZ;
        double lateralLeft = localX;
        double wx = (-Math.sin(yawRad) * forward) + (Math.cos(yawRad) * lateralLeft);
        double wz = (Math.cos(yawRad) * forward) + (Math.sin(yawRad) * lateralLeft);
        Location loc = origin.clone().add(wx, localY, wz);
        loc.setYaw(yawDeg);
        loc.setPitch(0.0f);
        return loc;
    }
}
