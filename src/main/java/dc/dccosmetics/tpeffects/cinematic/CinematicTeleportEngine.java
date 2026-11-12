package dc.dccosmetics.tpeffects.cinematic;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.tpeffects.config.TpEffectConfig;
import dc.dccosmetics.tpeffects.model.TpEffectEntry;
import dc.dccosmetics.wardrobe.WardrobeManager;
import dc.dccosmetics.wardrobe.WardrobeSession;
import dc.dccosmetics.wardrobe.camera.CameraController;
import dc.dccosmetics.wardrobe.camera.CinematicCameraEngine;
import dc.dccosmetics.wardrobe.camera.WardrobeCinematic;
import dc.dccosmetics.wardrobe.npc.WardrobeMannequin;
import dc.dcessentials.core.util.ColorUtil;
import dev.xqedii.ultimateUI.api.UiBuilder;
import dev.xqedii.ultimateUI.api.UltimateUIAPI;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Argon 1:1 Pixel-Replica & Camera Engine for Legendary (Obsidian Dissolution) and
 * Mastery (Chrono Singularity) teleportation cinematics, plus Wardrobe / stationary showcases.
 */
public final class CinematicTeleportEngine {

    private static final String SEQ_META_KEY = "dc_tpeffect_seq_token";

    private final DCCosmetics plugin;
    private final TpEffectConfig config;
    private final SkinVoxelizer voxelizer;

    private final Map<UUID, ActiveTeleportSequence> activeSequences = new ConcurrentHashMap<>();
    private final Map<UUID, StationaryPreviewTask> activePreviews = new ConcurrentHashMap<>();
    private final Set<UUID> pendingLaunches = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> pendingPreviewTasks = new ConcurrentHashMap<>();

    public CinematicTeleportEngine(DCCosmetics plugin, TpEffectConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.voxelizer = new SkinVoxelizer(plugin);
    }

    public SkinVoxelizer getVoxelizer() {
        return voxelizer;
    }

    public void warmSkinCache(Player player) {
        voxelizer.warmCache(player, 1);
    }

    public boolean isInSequence(Player player) {
        return player != null && activeSequences.containsKey(player.getUniqueId());
    }

    public boolean isBusy(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        return activeSequences.containsKey(uuid) || pendingLaunches.contains(uuid);
    }

    public void markPending(UUID uuid) {
        if (uuid != null) {
            pendingLaunches.add(uuid);
        }
    }

    public void clearPending(UUID uuid) {
        if (uuid != null) {
            pendingLaunches.remove(uuid);
        }
    }

    public boolean isStyleSupported(TpEffectEntry entry) {
        return entry != null && entry.isCinematic();
    }

    // =========================================================================
    // FULL 3D CAMERA TELEPORT SEQUENCE (LEGENDARY & MASTERY)
    // =========================================================================

    public void playTeleportSequence(Player player, Location fromLoc, Location toLoc, TpEffectEntry entry) {
        launchSequence(player, fromLoc, toLoc, entry, false);
    }

    public void playArrivalSequence(Player player, Location toLoc, TpEffectEntry entry) {
        if (toLoc == null) {
            if (player != null) pendingLaunches.remove(player.getUniqueId());
            return;
        }
        launchSequence(player, toLoc, toLoc, entry, true);
    }

    private void launchSequence(Player player, Location fromLoc, Location toLoc, TpEffectEntry entry, boolean arrivalOnly) {
        if (player != null) {
            pendingLaunches.remove(player.getUniqueId());
        }
        if (player == null || !player.isOnline() || fromLoc == null || toLoc == null
                || fromLoc.getWorld() == null || toLoc.getWorld() == null || entry == null) {
            return;
        }

        cancelSequence(player.getUniqueId(), false);
        stopPreview(player);

        TpEffectEntry.CinematicStyle style = entry.getCinematicStyle() != null
                ? entry.getCinematicStyle()
                : TpEffectEntry.CinematicStyle.OBSIDIAN_DISSOLUTION;
        int durationTicks = Math.max(20, entry.getDurationTicks());

        ActiveTeleportSequence seq = new ActiveTeleportSequence(player, fromLoc.clone(), toLoc.clone(), style, durationTicks, arrivalOnly);
        activeSequences.put(player.getUniqueId(), seq);
        seq.start();
    }

    public void cancelSequence(UUID uuid, boolean teleportToFinal) {
        if (uuid == null) return;
        pendingLaunches.remove(uuid);
        BukkitTask pendingPreview = pendingPreviewTasks.remove(uuid);
        if (pendingPreview != null) {
            pendingPreview.cancel();
        }
        ActiveTeleportSequence seq = activeSequences.remove(uuid);
        if (seq != null) {
            seq.forceCleanup(teleportToFinal);
        }
    }

    // =========================================================================
    // WARDROBE & STATIONARY SHOWCASE PREVIEWS (NO PLAYER TELEPORT / CAMERA HIJACK)
    // =========================================================================

    public void playWardrobePreview(WardrobeSession session, TpEffectEntry entry) {
        if (session == null || entry == null) return;
        Player viewer = session.getPlayer();
        WardrobeManager wm = session.getManager();
        if (viewer == null || !viewer.isOnline()) return;

        Location tpPreview = (wm != null && wm.getRoom() != null) ? wm.getRoom().getTpPreview() : null;
        if (tpPreview != null && tpPreview.getWorld() != null) {
            launchWardrobeFullPreview(viewer, session, wm, tpPreview, entry);
            return;
        }

        if (viewer.hasPermission("dccosmetics.admin")) {
            viewer.sendMessage("§7[Wardrobe] Set an open-sky preview spot with §e/wardrobe settppreview §7to preview the full 3D camera effect.");
        }

        WardrobeMannequin mannequin = session.getMannequin();
        if (mannequin == null || mannequin.getEntity() == null) {
            return;
        }

        Location baseLoc = mannequin.getEntity().getLocation().clone();
        // Face cleanly toward the Wardrobe camera so the hero silhouette isn't caught mid-spin facing away
        Location camLoc = session.getManager() != null && session.getManager().getRoom() != null
                ? session.getManager().getRoom().getCamera("teleport")
                : null;
        if (camLoc == null) camLoc = session.getCurrentCameraLocation();
        if (camLoc != null && camLoc.getWorld() != null && camLoc.getWorld().equals(baseLoc.getWorld())) {
            Vector toCam = camLoc.toVector().subtract(baseLoc.toVector());
            toCam.setY(0.0);
            if (toCam.lengthSquared() > 1e-4) {
                float faceYaw = (float) Math.toDegrees(Math.atan2(-toCam.getX(), toCam.getZ()));
                baseLoc.setYaw(Location.normalizeYaw(faceYaw));
                baseLoc.setPitch(0.0f);
            }
        }

        SkinVoxelizer.EquipmentSnapshot wardrobeEquip = SkinVoxelizer.EquipmentSnapshot.forWardrobe(
                session.getSavedArmor(), session.getSavedOffHand()
        );
        runStationaryShowcase(viewer, baseLoc, mannequin, entry, wardrobeEquip, true);
    }

    private record PreviewRestoreState(
            String category,
            String rarity,
            int selectedIndex,
            int scrollOffset,
            String previewingTeleportId
    ) {}

    private void launchWardrobeFullPreview(
            Player viewer,
            WardrobeSession session,
            WardrobeManager wm,
            Location tpPreview,
            TpEffectEntry entry
    ) {
        if (isBusy(viewer) || session.isInTpPreview()) return;
        stopPreview(viewer);

        PreviewRestoreState restoreState = new PreviewRestoreState(
                session.getSelectedCategory(),
                session.getSelectedRarity(),
                session.getSelectedIndex(),
                session.getScrollWindowOffset(),
                entry.getId()
        );

        UUID uuid = viewer.getUniqueId();
        pendingLaunches.add(uuid);
        session.setInCinematic(true);
        session.setInTpPreview(true);
        session.setPreviewingTeleportId(entry.getId());

        if (session.getMannequin() != null) {
            session.getMannequin().clearPreview();
            session.getMannequin().setRotationPaused(true);
        }

        if (session.getCameraController() != null) {
            if (session.getCameraController().getCameraEntity() != null) {
                wm.unregisterPrivateEntity(session.getCameraController().getCameraEntity().getEntityId());
            }
            session.getCameraController().unlockCamera(viewer);
        }

        if (wm.getUltimateUIManager() != null) {
            wm.getUltimateUIManager().closeStudio(viewer);
        }
        try {
            if (UltimateUIAPI.isAvailable()) {
                UltimateUIAPI.get().closeGui(viewer);
            }
        } catch (Throwable ignored) {}

        // Temporarily re-equip saved armor/offhand/mainhand onto player so 1:1 rig and summoned shot match real gear
        applySavedEquipmentForPreview(viewer, session);

        viewer.setMetadata("no_tpeffect", new FixedMetadataValue(plugin, true));
        viewer.setGameMode(GameMode.ADVENTURE);
        viewer.setAllowFlight(true);
        viewer.setFlying(true);
        viewer.setWalkSpeed(0.0f);
        viewer.setFlySpeed(0.0f);
        viewer.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 32, 0, false, false, false));
        viewer.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 22, 0, false, false, false));
        viewer.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 48, 0, false, false, false));

        viewer.sendTitle("§b§l✦ Teleport Preview ✦",
                "§7Loading " + ColorUtil.colorize(entry.getRawDisplayName()) + "§7...", 2, 18, 4);
        viewer.playSound(viewer, Sound.BLOCK_BEACON_ACTIVATE, 0.55f, 1.45f);

        tpPreview.getChunk().load(true);
        viewer.teleport(tpPreview);

        BukkitTask launchTask = new BukkitRunnable() {
            @Override
            public void run() {
                pendingPreviewTasks.remove(uuid);
                pendingLaunches.remove(uuid);
                if (!viewer.isOnline() || !wm.isInWardrobe(viewer)) {
                    session.setInTpPreview(false);
                    session.setInCinematic(false);
                    return;
                }
                TpEffectEntry.CinematicStyle style = entry.getCinematicStyle() != null
                        ? entry.getCinematicStyle()
                        : TpEffectEntry.CinematicStyle.OBSIDIAN_DISSOLUTION;
                int entryTicks = Math.max(40, entry.getDurationTicks());
                ActiveTeleportSequence seq = new ActiveTeleportSequence(
                        viewer, tpPreview.clone(), tpPreview.clone(), style, entryTicks, false, session, restoreState
                );
                activeSequences.put(uuid, seq);
                seq.start();
            }
        }.runTaskLater(plugin, 10L);
        pendingPreviewTasks.put(uuid, launchTask);
    }

    private void applySavedEquipmentForPreview(Player player, WardrobeSession session) {
        if (player == null || session == null) return;
        ItemStack[] savedArmor = session.getSavedArmor();
        if (savedArmor != null) {
            ItemStack[] copyArmor = new ItemStack[savedArmor.length];
            for (int i = 0; i < savedArmor.length; i++) {
                copyArmor[i] = savedArmor[i] != null ? savedArmor[i].clone() : null;
            }
            player.getInventory().setArmorContents(copyArmor);
        }
        ItemStack savedOff = session.getSavedOffHand();
        player.getInventory().setItemInOffHand(savedOff != null ? savedOff.clone() : new ItemStack(Material.AIR));

        ItemStack[] savedInv = session.getSavedInventory();
        int heldSlot = session.getSavedHeldSlot();
        if (savedInv != null && heldSlot >= 0 && heldSlot < savedInv.length && heldSlot < 9) {
            player.getInventory().setHeldItemSlot(heldSlot);
            player.getInventory().setItem(heldSlot, savedInv[heldSlot] != null ? savedInv[heldSlot].clone() : new ItemStack(Material.AIR));
        }
    }

    private void restoreWardrobeAfterPreview(Player player, WardrobeSession session, PreviewRestoreState restoreState) {
        if (player == null || !player.isOnline() || session == null) return;
        WardrobeManager wm = session.getManager();
        if (wm == null || !wm.isInWardrobe(player)) return;

        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        ItemStack controller = WardrobeCinematic.createControllerItem(wm);
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, controller.clone());
        }

        player.setMetadata("no_tpeffect", new FixedMetadataValue(plugin, true));
        player.setGameMode(GameMode.ADVENTURE);
        sendClientGameModePacket(player, 2.0f);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 99999, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 99999, 128, false, false, false));

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) continue;
            online.hidePlayer(plugin, player);
            if (wm.isInWardrobe(online)) {
                player.hidePlayer(plugin, online);
            }
        }

        Location spawnLoc = wm.getRoom().getSpawn();
        if (spawnLoc != null && spawnLoc.getWorld() != null) {
            spawnLoc.getChunk().load(true);
            player.teleport(spawnLoc);
        }

        session.setInTpPreview(false);

        Location camLoc = wm.getRoom().getCamera("teleport");
        if (camLoc == null) camLoc = wm.getRoom().getCamera("overview");
        CameraController cc = session.getCameraController();
        if (cc == null) {
            cc = new CameraController();
            session.setCameraController(cc);
        }
        if (camLoc != null) {
            cc.spawnAndLockCamera(player, camLoc, session);
        }

        WardrobeMannequin mannequin = session.getMannequin();
        if (mannequin != null) {
            if (mannequin.getEntity() == null || !mannequin.getEntity().isValid()) {
                mannequin.destroy();
                WardrobeMannequin fresh = new WardrobeMannequin(session, wm.getRoom().getNpcStand());
                fresh.spawn();
                session.setMannequin(fresh);
                if (fresh.getEntity() != null) {
                    wm.registerPrivateEntity(fresh.getEntity().getEntityId(), player.getUniqueId());
                }
            } else {
                mannequin.setRotationPaused(false);
                player.showEntity(plugin, mannequin.getEntity());
            }
        }

        if (restoreState != null) {
            session.setSelectedCategory(restoreState.category());
            session.setSelectedRarity(restoreState.rarity());
            session.setSelectedIndex(restoreState.selectedIndex());
            session.setScrollWindowOffset(restoreState.scrollOffset());
            session.setPreviewingTeleportId(restoreState.previewingTeleportId());
        }

        if (wm.getUltimateUIManager() != null) {
            wm.getUltimateUIManager().openCosmetics(player, session, true);
        }
        session.setInCinematic(false);
    }

    public void playStationaryPreview(Player viewer, Location baseLoc, TpEffectEntry entry) {
        if (viewer == null || !viewer.isOnline() || baseLoc == null || entry == null) return;
        runStationaryShowcase(viewer, baseLoc.clone(), null, entry, null, false);
    }

    public void stopPreview(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        BukkitTask pending = pendingPreviewTasks.remove(uuid);
        if (pending != null) {
            pending.cancel();
            pendingLaunches.remove(uuid);
        }
        StationaryPreviewTask prev = activePreviews.remove(uuid);
        if (prev != null) {
            prev.cancelAndRestore();
        }
    }

    public void cleanupAll() {
        for (UUID uuid : new ArrayList<>(pendingPreviewTasks.keySet())) {
            BukkitTask pending = pendingPreviewTasks.remove(uuid);
            if (pending != null) pending.cancel();
        }
        pendingLaunches.clear();
        for (UUID uuid : new ArrayList<>(activeSequences.keySet())) {
            cancelSequence(uuid, false);
        }
        for (UUID uuid : new ArrayList<>(activePreviews.keySet())) {
            StationaryPreviewTask prev = activePreviews.remove(uuid);
            if (prev != null) prev.cancelAndRestore();
        }
        voxelizer.clearCache();
    }

    private void runStationaryShowcase(
            Player viewer,
            Location baseLoc,
            WardrobeMannequin mannequin,
            TpEffectEntry entry,
            SkinVoxelizer.EquipmentSnapshot equipmentOverride,
            boolean privateToViewer
    ) {
        stopPreview(viewer);
        WardrobeManager wm = plugin.getWardrobeManager();

        StationaryPreviewTask preview = new StationaryPreviewTask(
                viewer, baseLoc, mannequin, wm, entry, equipmentOverride, privateToViewer
        );
        activePreviews.put(viewer.getUniqueId(), preview);
        preview.start();
    }

    private static SkinVoxelizer.FlakeStyle resolveFlakeStyle(TpEffectEntry.CinematicStyle style) {
        if (style == null) return SkinVoxelizer.FlakeStyle.LEGACY_OBSIDIAN;
        return switch (style) {
            case CHRONO_SINGULARITY -> SkinVoxelizer.FlakeStyle.LEGACY_CHRONO;
            case UMBRAL_DISSOLUTION -> SkinVoxelizer.FlakeStyle.UMBRAL_SUBTLE_GLOW;
            case SHADOW_DRIFT -> SkinVoxelizer.FlakeStyle.SHADOW_TRANSLUCENT;
            case SKYLINE_SWITCH -> SkinVoxelizer.FlakeStyle.GTA_SKY_BLACK;
            case OBSIDIAN_DISSOLUTION -> SkinVoxelizer.FlakeStyle.LEGACY_OBSIDIAN;
        };
    }

    // =========================================================================
    // ACTIVE 5-STYLE CINEMATIC CAMERA ENGINE (UNIFIED DISPLAY INTERPOLATION)
    // =========================================================================

    private final class ActiveTeleportSequence {
        private final Player player;
        private final Location fromLoc;
        private final Location toLoc;
        private final TpEffectEntry.CinematicStyle style;
        private final SkinVoxelizer.FlakeStyle flakeStyle;
        private final boolean chrono;
        private final boolean arrivalOnly;

        private final GameMode savedGameMode;
        private final boolean savedAllowFlight;
        private final boolean savedFlying;
        private final float savedWalkSpeed;
        private final float savedFlySpeed;

        private final List<Entity> extraProps = new ArrayList<>();
        private final Set<UUID> hiddenFromPlayers = new HashSet<>();

        private final String seqToken = UUID.randomUUID().toString();
        private SkinVoxelizer.VoxelCharacterRig activeRig;
        private TextDisplay cameraDisplay;
        private BukkitTask task;
        private int cameraSpawnTick = -99;
        private int currentTick = 0;

        private boolean openedUltimateUiBlackout = false;
        private boolean finished = false;

        // Scaled phase markers derived from configured entry duration
        private final int totalTicks;
        private final int peelStartTick;
        private final int depEndTick;
        private final int cutStartTick;
        private final int warpTick;
        private final int arrOpenTick;
        private final int reconEndTick;

        // Track GTA beat sounds already fired so each of 3+3 beats chimes cleanly once
        private int lastGtaOutBeat = -1;
        private int lastGtaInBeat = -1;

        // Optional Option A Wardrobe preview mode (Arrival summon -> Hero hold -> Departure -> Wardrobe return)
        private final WardrobeSession wardrobeSession;
        private final PreviewRestoreState wardrobeRestore;
        private final int previewArrOpenTick;
        private final int previewArrReconEnd;
        private final int previewHoldEnd;
        private final int previewDepStart;
        private final int previewDepPeelStart;
        private final int previewDepCutTick;
        private final int previewDepEnd;
        private Location previewArrHoldPose;
        private Location previewDepStartPose;

        // Legacy & Subtle Departure/Arrival vantages
        private Location depStartPos;
        private Location depEndPos;
        private Location arrFarPos;
        private Location arrNearPos;
        private Location arrEyeTarget;

        ActiveTeleportSequence(
                Player player,
                Location fromLoc,
                Location toLoc,
                TpEffectEntry.CinematicStyle style,
                int durationTicks,
                boolean arrivalOnly
        ) {
            this(player, fromLoc, toLoc, style, durationTicks, arrivalOnly, null, null);
        }

        ActiveTeleportSequence(
                Player player,
                Location fromLoc,
                Location toLoc,
                TpEffectEntry.CinematicStyle style,
                int durationTicks,
                boolean arrivalOnly,
                WardrobeSession wardrobeSession,
                PreviewRestoreState wardrobeRestore
        ) {
            this.player = player;
            this.fromLoc = fromLoc;
            this.toLoc = toLoc;
            this.style = style != null ? style : TpEffectEntry.CinematicStyle.OBSIDIAN_DISSOLUTION;
            this.flakeStyle = resolveFlakeStyle(this.style);
            this.chrono = (this.style == TpEffectEntry.CinematicStyle.CHRONO_SINGULARITY);
            this.arrivalOnly = arrivalOnly;
            this.wardrobeSession = wardrobeSession;
            this.wardrobeRestore = wardrobeRestore;

            if (wardrobeSession != null) {
                int baseEntryTicks = Math.max(40, durationTicks);
                int arrDur = Math.max(26, Math.min(50, (int) Math.round(baseEntryTicks * 0.45)));
                int holdDur = 20;
                int depDur = Math.max(26, Math.min(50, (int) Math.round(baseEntryTicks * 0.45)));

                this.previewArrOpenTick = 6;
                this.previewArrReconEnd = this.previewArrOpenTick + arrDur;
                this.previewHoldEnd = this.previewArrReconEnd + holdDur;
                this.previewDepStart = this.previewHoldEnd + 1;
                this.previewDepPeelStart = this.previewDepStart + 2;
                this.previewDepCutTick = this.previewDepStart + depDur;
                this.previewDepEnd = this.previewDepCutTick + 4;
                this.totalTicks = this.previewDepEnd + 1;

                this.peelStartTick = 0;
                this.cutStartTick = 0;
                this.depEndTick = 0;
                this.warpTick = 0;
                this.arrOpenTick = 0;
                this.reconEndTick = 0;
            } else {
                this.previewArrOpenTick = 0;
                this.previewArrReconEnd = 0;
                this.previewHoldEnd = 0;
                this.previewDepStart = 0;
                this.previewDepPeelStart = 0;
                this.previewDepCutTick = 0;
                this.previewDepEnd = 0;

                this.totalTicks = Math.max(20, durationTicks);
                boolean crossWorld = fromLoc.getWorld() != null && toLoc.getWorld() != null
                        && !fromLoc.getWorld().equals(toLoc.getWorld());
                int settleTicks = crossWorld ? 8 : 5;

                if (this.style == TpEffectEntry.CinematicStyle.OBSIDIAN_DISSOLUTION
                        || this.style == TpEffectEntry.CinematicStyle.CHRONO_SINGULARITY) {
                    this.peelStartTick = Math.max(2, (int) Math.round(totalTicks * (12.0 / 210.0)));
                    this.cutStartTick = Math.max(peelStartTick + 6, (int) Math.round(totalTicks * (78.0 / 210.0)));
                    this.depEndTick = Math.max(cutStartTick + 2, (int) Math.round(totalTicks * (82.0 / 210.0)));
                    this.warpTick = Math.max(depEndTick + 2, (int) Math.round(totalTicks * (86.0 / 210.0)));
                    this.arrOpenTick = Math.max(warpTick + 4, (int) Math.round(totalTicks * (114.0 / 210.0)));
                    this.reconEndTick = Math.max(arrOpenTick + 10, (int) Math.round(totalTicks * (182.0 / 210.0)));
                } else if (this.style == TpEffectEntry.CinematicStyle.SKYLINE_SWITCH) {
                    this.peelStartTick = Math.max(2, (int) Math.round(totalTicks * 0.05));
                    this.cutStartTick = Math.max(peelStartTick + 10, (int) Math.round(totalTicks * 0.44));
                    this.depEndTick = cutStartTick + 2;
                    this.warpTick = depEndTick + 2;
                    this.arrOpenTick = Math.min(totalTicks - 14, warpTick + settleTicks);
                    this.reconEndTick = Math.max(arrOpenTick + 10, (int) Math.round(totalTicks * 0.92));
                } else {
                    // UMBRAL_DISSOLUTION & SHADOW_DRIFT: punchy, responsive, low-delay teleport timing
                    this.peelStartTick = Math.max(2, (int) Math.round(totalTicks * 0.05));
                    this.cutStartTick = Math.max(peelStartTick + 8, (int) Math.round(totalTicks * 0.37));
                    this.depEndTick = cutStartTick + 2;
                    this.warpTick = depEndTick + 2;
                    this.arrOpenTick = Math.min(totalTicks - 12, warpTick + settleTicks);
                    this.reconEndTick = Math.max(arrOpenTick + 10, (int) Math.round(totalTicks * 0.90));
                }
            }

            this.savedGameMode = player.getGameMode();
            this.savedAllowFlight = player.getAllowFlight();
            this.savedFlying = player.isFlying();
            this.savedWalkSpeed = player.getWalkSpeed();
            this.savedFlySpeed = player.getFlySpeed();
        }

        void start() {
            player.setMetadata("no_tpeffect", new FixedMetadataValue(plugin, true));
            player.setMetadata(SEQ_META_KEY, new FixedMetadataValue(plugin, seqToken));

            toLoc.getChunk().load(true);
            computeWallSafeVantages();

            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setWalkSpeed(0.0f);
            player.setFlySpeed(0.0f);
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, totalTicks + 60, 0, false, false, false));

            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player) && online.canSee(player)) {
                    online.hidePlayer(plugin, player);
                    hiddenFromPlayers.add(online.getUniqueId());
                }
            }

            // Suppress HUD (hotbar, hearts, crosshair) cleanly
            sendClientGameModePacket(player, 3.0f);

            final int startTick;
            if (wardrobeSession != null) {
                currentTick = 0;
                Location startArrPose = computeInitialArrivalPose();
                parkPlayerBehindCamera(startArrPose, true);
                spawnCameraDisplay(startArrPose);
                spawnArrivalRig();
                startTick = 0;
            } else if (arrivalOnly) {
                currentTick = warpTick;
                triggerBlackout(2, 6, 4);
                startTick = warpTick;
            } else {
                currentTick = 0;
                triggerBlackout(2, 3, 3);
                playDepartureStartSounds();

                Location initialPose = aimAt(depStartPos, fromLoc.clone().add(0, 1.05, 0));
                // Keep real player entity outside camera view BEFORE spawning camera/rig so tracking never drops on teleport
                parkPlayerBehindCamera(initialPose, true);
                spawnCameraDisplay(initialPose);

                activeRig = voxelizer.spawnCharacterRig(
                        player,
                        fromLoc,
                        null,
                        plugin.getWardrobeManager(),
                        false,
                        0.0,
                        flakeStyle,
                        false,
                        null
                );
                startTick = 0;
            }

            task = new BukkitRunnable() {
                int tick = startTick;

                @Override
                public void run() {
                    if (!player.isOnline()) {
                        forceCleanup(false);
                        cancel();
                        return;
                    }
                    currentTick = tick;
                    try {
                        step(tick);
                    } catch (Exception ex) {
                        plugin.getLogger().warning("[TpEffects] Cinematic tick failure for " + player.getName() + ": " + ex.getMessage());
                        forceCleanup(true);
                        cancel();
                        return;
                    }
                    tick++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }

        private void playDepartureStartSounds() {
            if (style == TpEffectEntry.CinematicStyle.SKYLINE_SWITCH) {
                player.playSound(fromLoc, Sound.BLOCK_BEACON_DEACTIVATE, 0.55f, 1.45f);
                return;
            }
            if (style == TpEffectEntry.CinematicStyle.UMBRAL_DISSOLUTION || style == TpEffectEntry.CinematicStyle.SHADOW_DRIFT) {
                player.playSound(fromLoc, Sound.BLOCK_CONDUIT_AMBIENT, 0.65f, 0.68f);
                return;
            }
            player.playSound(fromLoc, Sound.BLOCK_CONDUIT_AMBIENT, 0.85f, chrono ? 0.95f : 0.70f);
            player.playSound(fromLoc, Sound.BLOCK_PORTAL_TRIGGER, 0.35f, chrono ? 1.55f : 1.25f);
        }

        private void computeWallSafeVantages() {
            double cfgDist = config.getCinematicCameraDistance();
            double cfgHeight = config.getCinematicCameraHeightOffset();

            float depYaw = Location.normalizeYaw(fromLoc.getYaw());
            Location depPivot = fromLoc.clone().add(0, 1.08, 0);

            if (style == TpEffectEntry.CinematicStyle.UMBRAL_DISSOLUTION || style == TpEffectEntry.CinematicStyle.SHADOW_DRIFT) {
                double baseDist = style == TpEffectEntry.CinematicStyle.SHADOW_DRIFT ? 2.75 : 2.95;
                double baseH = 1.48;
                double startRad = Math.toRadians(depYaw + 10.0);
                double endRad = Math.toRadians(depYaw + 26.0);

                depStartPos = clampCameraClearOfWalls(depPivot, fromLoc.clone().add(
                        Math.sin(startRad) * baseDist, baseH, -Math.cos(startRad) * baseDist
                ));
                depEndPos = clampCameraClearOfWalls(depPivot, fromLoc.clone().add(
                        Math.sin(endRad) * (baseDist + 0.90), baseH - 0.28, -Math.cos(endRad) * (baseDist + 0.90)
                ));

                float arrYaw = Location.normalizeYaw(toLoc.getYaw());
                Location arrPivot = toLoc.clone().add(0, 1.08, 0);
                double arrFarDist = style == TpEffectEntry.CinematicStyle.SHADOW_DRIFT ? 4.55 : 4.95;
                double arrNearDist = style == TpEffectEntry.CinematicStyle.SHADOW_DRIFT ? 1.85 : 2.35;
                double arrFarRad = Math.toRadians(arrYaw - 18.0);
                double arrNearRad = Math.toRadians(arrYaw - 6.0);

                arrFarPos = clampCameraClearOfWalls(arrPivot, toLoc.clone().add(
                        Math.sin(arrFarRad) * arrFarDist, 1.52, -Math.cos(arrFarRad) * arrFarDist
                ));
                arrNearPos = clampCameraClearOfWalls(arrPivot, toLoc.clone().add(
                        Math.sin(arrNearRad) * arrNearDist, 1.58, -Math.cos(arrNearRad) * arrNearDist
                ));
            } else if (style == TpEffectEntry.CinematicStyle.SKYLINE_SWITCH) {
                double depRad = Math.toRadians(depYaw + 12.0);
                depStartPos = clampCameraClearOfWalls(depPivot, fromLoc.clone().add(
                        Math.sin(depRad) * 2.8, 1.45, -Math.cos(depRad) * 2.8
                ));
                depEndPos = fromLoc.clone().add(Math.sin(depRad) * 8.5, 44.0, -Math.cos(depRad) * 8.5);

                float arrYaw = Location.normalizeYaw(toLoc.getYaw());
                double arrRad = Math.toRadians(arrYaw - 10.0);
                arrFarPos = toLoc.clone().add(Math.sin(arrRad) * 8.5, 44.0, -Math.cos(arrRad) * 8.5);
                arrNearPos = clampCameraClearOfWalls(toLoc.clone().add(0, 1.08, 0), toLoc.clone().add(
                        Math.sin(arrRad) * 2.3, 1.72, -Math.cos(arrRad) * 2.3
                ));
            } else {
                // Legacy OBSIDIAN_DISSOLUTION / CHRONO_SINGULARITY kept intact for A/B comparison
                double depRad = Math.toRadians(depYaw + 24.0f);
                depStartPos = clampCameraClearOfWalls(depPivot, fromLoc.clone().add(
                        Math.sin(depRad) * cfgDist, cfgHeight, -Math.cos(depRad) * cfgDist
                ));
                depEndPos = depStartPos.clone().add(0, 0.32, 0);

                float arrYaw = Location.normalizeYaw(toLoc.getYaw());
                Location arrPivot = toLoc.clone().add(0, 1.08, 0);
                double arrRad = Math.toRadians(arrYaw - 22.0f);
                arrFarPos = clampCameraClearOfWalls(arrPivot, toLoc.clone().add(
                        Math.sin(arrRad) * cfgDist, cfgHeight, -Math.cos(arrRad) * cfgDist
                ));
                arrNearPos = arrFarPos.clone();
            }

            arrEyeTarget = toLoc.clone().add(0, 1.62, 0);
            arrEyeTarget.setYaw(Location.normalizeYaw(toLoc.getYaw()));
            arrEyeTarget.setPitch(toLoc.getPitch());
        }

        private void step(int tick) {
            if (cameraDisplay != null && cameraDisplay.isValid() && tick <= cameraSpawnTick + 3) {
                player.showEntity(plugin, cameraDisplay);
            }
            if (wardrobeSession != null) {
                stepWardrobePreview(tick);
                return;
            }
            if (tick <= depEndTick) {
                stepDeparturePhase(tick, peelStartTick, depEndTick, cutStartTick);
            } else if (tick == warpTick) {
                stepBlackoutWarp();
            } else if (tick > warpTick && tick < arrOpenTick) {
                if (cameraDisplay != null && cameraDisplay.isValid()) {
                    CinematicCameraEngine.applyCameraPose(
                            player, cameraDisplay, computeInitialArrivalPose(), tick <= cameraSpawnTick + 2
                    );
                    lockCameraPacket(player, cameraDisplay.getEntityId());
                }
            } else if (tick >= arrOpenTick && tick < totalTicks) {
                stepArrivalPhase(tick, arrOpenTick, reconEndTick, totalTicks);
            } else if (tick >= totalTicks) {
                finished = true;
                activeSequences.remove(player.getUniqueId());
                forceCleanup(true);
                if (task != null) task.cancel();
            }
        }

        private void stepWardrobePreview(int tick) {
            if (tick < previewArrOpenTick) {
                // Hold behind loading blackout for 6 ticks so cross-world chunks, AddEntity & SetCamera attach before pixel 1
                if (cameraDisplay != null && cameraDisplay.isValid()) {
                    CinematicCameraEngine.applyCameraPose(
                            player, cameraDisplay, computeInitialArrivalPose(), tick <= cameraSpawnTick + 2
                    );
                    lockCameraPacket(player, cameraDisplay.getEntityId());
                }
                return;
            }

            if (tick <= previewArrReconEnd) {
                stepArrivalPhase(tick, previewArrOpenTick, previewArrReconEnd, previewArrReconEnd);
                if (tick == previewArrReconEnd) {
                    double holdLookY = (style == TpEffectEntry.CinematicStyle.SKYLINE_SWITCH
                            || style == TpEffectEntry.CinematicStyle.UMBRAL_DISSOLUTION
                            || style == TpEffectEntry.CinematicStyle.SHADOW_DRIFT) ? 1.25 : 1.15;
                    previewArrHoldPose = aimAt(arrNearPos, toLoc.clone().add(0, holdLookY, 0));
                    previewDepStartPose = aimAt(depStartPos, fromLoc.clone().add(0, 1.05, 0));
                }
            } else if (tick <= previewHoldEnd) {
                if (tick == previewArrReconEnd + 1) {
                    // Lock 100% of voxel pixels crisp & solid before Hero Hold camera pivot to departure vantage
                    voxelizer.stepReconstructSweep(activeRig, 2.35, flakeStyle, toLoc, null);
                    player.playSound(toLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.45f, 1.45f);
                    player.sendTitle("", "§8✦ §aSummoned! §7Departing to Wardrobe... §8✦", 2, 18, 4);
                }
                if (cameraDisplay != null && cameraDisplay.isValid()) {
                    double holdT = (double) (tick - previewArrReconEnd) / Math.max(1, previewHoldEnd - previewArrReconEnd);
                    Location startHold = previewArrHoldPose != null ? previewArrHoldPose : computeInitialArrivalPose();
                    Location endHold = previewDepStartPose != null ? previewDepStartPose : aimAt(depStartPos, fromLoc.clone().add(0, 1.05, 0));
                    Location camPose = CinematicCameraEngine.lerpLocation(
                            startHold, endHold, holdT, CinematicCameraEngine.Easing.EASE_IN_OUT_SINE
                    );
                    CinematicCameraEngine.applyCameraPose(player, cameraDisplay, camPose, tick <= cameraSpawnTick + 2);
                    lockCameraPacket(player, cameraDisplay.getEntityId());
                    parkPlayerBehindCamera(camPose, false);
                }
            } else if (tick <= previewDepCutTick) {
                if (tick == previewDepStart) {
                    lastGtaOutBeat = -1;
                    if (activeRig == null || activeRig.bandGroups.isEmpty()) {
                        activeRig = voxelizer.spawnCharacterRig(
                                player, fromLoc, null, plugin.getWardrobeManager(),
                                false, 0.0, flakeStyle, false, null
                        );
                    } else {
                        activeRig.activeFlakes.removeIf(d -> d == null || !d.isValid());
                    }
                    playDepartureStartSounds();
                }
                stepDeparturePhase(tick, previewDepPeelStart, previewDepCutTick + 5, previewDepCutTick);
            } else if (tick < totalTicks) {
                if (cameraDisplay != null && cameraDisplay.isValid()) {
                    lockCameraPacket(player, cameraDisplay.getEntityId());
                }
            } else {
                finished = true;
                activeSequences.remove(player.getUniqueId());
                forceCleanup(true);
                if (task != null) task.cancel();
            }
        }

        private void stepDeparturePhase(int tick, int peelStart, int depEnd, int cutTick) {
            int peelDur = Math.max(6, depEnd - peelStart - 6);
            if (tick >= peelStart && tick <= peelStart + peelDur) {
                double sweepT = (double) (tick - peelStart) / peelDur;
                double horizonY = 1.92 - (sweepT * 2.02);
                voxelizer.stepDissolveSweep(activeRig, horizonY, flakeStyle, sweepT);

                if (tick % 4 == 0) {
                    player.playSound(fromLoc, Sound.BLOCK_SAND_PLACE, 0.25f, 0.65f + (float) (sweepT * 0.55f));
                }
            }

            if (tick >= peelStart + 1 && tick <= depEnd - 4 && tick % 2 == 0) {
                int count = chrono ? 5 : 4;
                extraProps.addAll(voxelizer.spawnColumnAccentShards(
                        fromLoc, count, true, flakeStyle, null, plugin.getWardrobeManager()
                ));
            }

            Location pose;
            if (style == TpEffectEntry.CinematicStyle.SKYLINE_SWITCH) {
                pose = computeGtaAscentPose(tick, peelStart + 2, Math.min(depEnd, cutTick));
                parkPlayerBehindCamera(pose, false);
            } else if (style == TpEffectEntry.CinematicStyle.UMBRAL_DISSOLUTION
                    || style == TpEffectEntry.CinematicStyle.SHADOW_DRIFT) {
                double t = tick <= peelStart ? 0.0 : Math.min(1.0, (double) (tick - peelStart) / Math.max(1, Math.min(depEnd, cutTick) - peelStart));
                Location camPos = CinematicCameraEngine.lerpLocation(
                        depStartPos, depEndPos, t, CinematicCameraEngine.Easing.EASE_IN_OUT_SINE
                );
                // Keep natural eye/torso focus so pitch stays gentle & level instead of craning to sky
                Location lookTarget = fromLoc.clone().add(0, 1.05 + t * 0.18, 0);
                pose = aimAt(camPos, lookTarget);
            } else {
                // Legacy OBSIDIAN / CHRONO sky-gaze preserved for comparison
                double pitchProgress = tick <= peelStart
                        ? 0.0
                        : Math.min(1.0, (double) (tick - peelStart) / Math.max(1, Math.min(depEnd, cutTick) - peelStart));
                double easedPitch = CinematicCameraEngine.Easing.EASE_IN_OUT_CUBIC.apply(pitchProgress);
                Location camPos = depStartPos.clone().add(0, easedPitch * 0.32, 0);
                Location lookTarget = fromLoc.clone().add(0, 1.05 + easedPitch * 8.4, 0);
                pose = aimAt(camPos, lookTarget);
            }

            CinematicCameraEngine.applyCameraPose(player, cameraDisplay, pose, currentTick <= cameraSpawnTick + 2);
            lockCameraPacket(player, cameraDisplay.getEntityId());

            if (tick == cutTick) {
                int cutStay = wardrobeSession != null ? 10 : Math.max(6, (arrOpenTick - cutStartTick) + 2);
                triggerBlackout(2, cutStay, 4);
                player.playSound(fromLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.55f,
                        style == TpEffectEntry.CinematicStyle.SKYLINE_SWITCH ? 0.55f : 0.72f);
            }
        }

        private Location computeGtaAscentPose(int tick, int rampStart, int rampEnd) {
            if (tick <= rampStart) {
                return aimAt(depStartPos, fromLoc.clone().add(0, 1.05, 0));
            }
            int span = Math.max(6, rampEnd - rampStart);
            double progress = Math.min(1.0, (double) (tick - rampStart) / span);

            // 3 exponential GTA beats: [0..1/3], [1/3..2/3], [2/3..1]
            int beat = Math.min(2, (int) (progress * 3.0));
            double localT = (progress * 3.0) - beat;
            double snapT = CinematicCameraEngine.Easing.EASE_OUT_CUBIC.apply(localT);

            if (beat > lastGtaOutBeat) {
                lastGtaOutBeat = beat;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.95f, 0.52f + beat * 0.14f);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, 0.65f + beat * 0.18f);
            }

            double[] heights = {1.5, 7.5, 20.0, 44.0};
            double[] dists = {2.8, 4.6, 6.8, 8.5};
            double[] lookY = {1.05, 0.85, 0.50, 0.0};

            double curH = heights[beat] + (heights[beat + 1] - heights[beat]) * snapT;
            double curD = dists[beat] + (dists[beat + 1] - dists[beat]) * snapT;
            double curLookY = lookY[beat] + (lookY[beat + 1] - lookY[beat]) * snapT;

            float depYaw = Location.normalizeYaw(fromLoc.getYaw());
            double rad = Math.toRadians(depYaw + 12.0 + beat * 4.0 + snapT * 4.0);
            Location camPos = fromLoc.clone().add(Math.sin(rad) * curD, curH, -Math.cos(rad) * curD);
            return aimAt(camPos, fromLoc.clone().add(0, curLookY, 0));
        }

        private Location computeGtaDescentPose(int tick, int openTick, int reconEnd) {
            int span = Math.max(6, reconEnd - openTick);
            double progress = Math.min(1.0, (double) (tick - openTick) / span);
            int beat = Math.min(2, (int) (progress * 3.0));
            double localT = (progress * 3.0) - beat;
            double snapT = CinematicCameraEngine.Easing.EASE_OUT_CUBIC.apply(localT);

            if (beat > lastGtaInBeat) {
                lastGtaInBeat = beat;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.95f, 0.82f - beat * 0.14f);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f, 0.95f - beat * 0.15f);
            }

            double[] heights = {44.0, 20.0, 7.5, 1.72};
            double[] dists = {8.5, 6.2, 4.0, 2.3};
            double[] lookY = {0.0, 0.50, 0.95, 1.25};

            double curH = heights[beat] + (heights[beat + 1] - heights[beat]) * snapT;
            double curD = dists[beat] + (dists[beat + 1] - dists[beat]) * snapT;
            double curLookY = lookY[beat] + (lookY[beat + 1] - lookY[beat]) * snapT;

            float arrYaw = Location.normalizeYaw(toLoc.getYaw());
            double rad = Math.toRadians(arrYaw - 10.0 - (2 - beat) * 3.0);
            Location desired = toLoc.clone().add(Math.sin(rad) * curD, curH, -Math.cos(rad) * curD);
            if (curH < 5.0) {
                desired = clampCameraClearOfWalls(toLoc.clone().add(0, 1.15, 0), desired);
            }
            return aimAt(desired, toLoc.clone().add(0, curLookY, 0));
        }

        private Location computeInitialArrivalPose() {
            if (style == TpEffectEntry.CinematicStyle.OBSIDIAN_DISSOLUTION
                    || style == TpEffectEntry.CinematicStyle.CHRONO_SINGULARITY) {
                return aimAt(arrFarPos.clone().add(0, 0.30, 0), toLoc.clone().add(0, 10.5, 0));
            } else if (style == TpEffectEntry.CinematicStyle.SKYLINE_SWITCH) {
                return aimAt(arrFarPos, toLoc.clone());
            } else {
                return aimAt(arrFarPos, toLoc.clone().add(0, 1.05, 0));
            }
        }

        private void spawnArrivalRig() {
            double skyOffset = (style == TpEffectEntry.CinematicStyle.OBSIDIAN_DISSOLUTION
                    || style == TpEffectEntry.CinematicStyle.CHRONO_SINGULARITY) ? 9.0 : 5.5;
            activeRig = voxelizer.spawnCharacterRig(
                    player,
                    toLoc,
                    null,
                    plugin.getWardrobeManager(),
                    true,
                    skyOffset,
                    flakeStyle,
                    false,
                    null
            );
        }

        private void stepBlackoutWarp() {
            clearTransientProps();

            Location startArrPose = computeInitialArrivalPose();

            // Park real player behind arrival camera BEFORE spawning/updating camera & rig so Paper entity tracking never drops
            parkPlayerBehindCamera(startArrPose, true);
            player.setAllowFlight(true);
            player.setFlying(true);

            if (cameraDisplay != null && cameraDisplay.isValid() && toLoc.getWorld() != null
                    && toLoc.getWorld().equals(cameraDisplay.getWorld())) {
                cameraSpawnTick = currentTick;
                player.showEntity(plugin, cameraDisplay);
                CinematicCameraEngine.applyCameraPose(player, cameraDisplay, startArrPose, true);
                lockCameraPacket(player, cameraDisplay.getEntityId());
            } else {
                if (cameraDisplay != null) {
                    if (plugin.getWardrobeManager() != null) {
                        plugin.getWardrobeManager().unregisterPrivateEntity(cameraDisplay.getEntityId());
                    }
                    if (cameraDisplay.isValid()) cameraDisplay.remove();
                    cameraDisplay = null;
                }
                spawnCameraDisplay(startArrPose);
            }
            sendClientGameModePacket(player, 3.0f);

            spawnArrivalRig();
        }

        private void stepArrivalPhase(int tick, int openTick, int reconEnd, int finalTick) {
            if (tick == openTick) {
                clearBlackout();
                player.playSound(toLoc, chrono ? Sound.BLOCK_RESPAWN_ANCHOR_CHARGE : Sound.BLOCK_BEACON_ACTIVATE, 0.65f, 1.35f);
            }

            if (tick <= reconEnd) {
                int arrTicks = Math.max(1, reconEnd - openTick);
                double arrT = Math.min(1.0, (double) (tick - openTick) / arrTicks);

                if (tick <= openTick + (int) Math.round(arrTicks * 0.65) && tick % 2 == 0) {
                    extraProps.addAll(voxelizer.spawnColumnAccentShards(
                            toLoc, chrono ? 5 : 4, false, flakeStyle, null, plugin.getWardrobeManager()
                    ));
                }

                int reconStart = (wardrobeSession != null)
                        ? (openTick + 1)
                        : (openTick + Math.max(2, (int) Math.round(arrTicks * 0.10)));
                int reconDuration = Math.max(6, reconEnd - reconStart);
                if (tick >= reconStart && tick <= reconEnd) {
                    double rT = Math.min(1.0, (double) (tick - reconStart) / reconDuration);
                    double horizonY = -0.05 + (rT * 2.02);
                    voxelizer.stepReconstructSweep(activeRig, horizonY, flakeStyle, toLoc, null);
                    if (tick % 4 == 0) {
                        player.playSound(toLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.28f, 0.85f + (float) (rT * 0.65f));
                    }
                }

                Location camPose;
                if (style == TpEffectEntry.CinematicStyle.SKYLINE_SWITCH) {
                    camPose = computeGtaDescentPose(tick, openTick, reconEnd);
                    parkPlayerBehindCamera(camPose, false);
                } else if (style == TpEffectEntry.CinematicStyle.UMBRAL_DISSOLUTION
                        || style == TpEffectEntry.CinematicStyle.SHADOW_DRIFT) {
                    // Start further back & smoothly glide closer onto the reforming silhouette (no extreme sky pitch!)
                    Location camPos = CinematicCameraEngine.lerpLocation(
                            arrFarPos, arrNearPos, arrT, CinematicCameraEngine.Easing.EASE_IN_OUT_SINE
                    );
                    Location lookTarget = toLoc.clone().add(0, 1.10 + arrT * 0.16, 0);
                    camPose = aimAt(camPos, lookTarget);
                } else {
                    double easedPitchDown = CinematicCameraEngine.Easing.EASE_OUT_CUBIC.apply(arrT);
                    Location camPos = arrFarPos.clone().add(0, (1.0 - easedPitchDown) * 0.30, 0);
                    Location lookTarget = toLoc.clone().add(0, 10.5 - easedPitchDown * 9.35, 0);
                    camPose = aimAt(camPos, lookTarget);
                }

                CinematicCameraEngine.applyCameraPose(player, cameraDisplay, camPose, currentTick <= cameraSpawnTick + 2);
                lockCameraPacket(player, cameraDisplay.getEntityId());
            } else {
                // Smooth eye zoom-in straight into 1st-person player eyes
                double zoomT = Math.min(1.0, (double) (tick - reconEnd) / Math.max(1, finalTick - reconEnd));
                Location startZoom = (style == TpEffectEntry.CinematicStyle.SKYLINE_SWITCH
                        || style == TpEffectEntry.CinematicStyle.UMBRAL_DISSOLUTION
                        || style == TpEffectEntry.CinematicStyle.SHADOW_DRIFT)
                        ? aimAt(arrNearPos, toLoc.clone().add(0, 1.25, 0))
                        : aimAt(arrNearPos, toLoc.clone().add(0, 1.15, 0));

                Location camPose = CinematicCameraEngine.lerpLocation(
                        startZoom, arrEyeTarget, zoomT, CinematicCameraEngine.Easing.EASE_IN_OUT_CUBIC
                );

                CinematicCameraEngine.applyCameraPose(player, cameraDisplay, camPose, currentTick <= cameraSpawnTick + 2);
                lockCameraPacket(player, cameraDisplay.getEntityId());

                if (tick >= finalTick - 2) {
                    if (tick == finalTick - 2) {
                        player.playSound(toLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.55f, 1.40f);
                    }
                    // Pre-position real player at destination right before camera unlock
                    if (!player.getWorld().equals(toLoc.getWorld()) || player.getLocation().distanceSquared(toLoc) > 0.25) {
                        player.teleport(toLoc);
                    }
                }
            }
        }

        private void parkPlayerBehindCamera(Location camPose, boolean force) {
            if (camPose == null || camPose.getWorld() == null || !player.isOnline()) return;
            Vector back = camPose.getDirection().multiply(-2.6);
            Location behind = camPose.clone().add(back);
            Location safePark = clampCameraClearOfWalls(camPose, behind);
            if (!force && player.getWorld().equals(safePark.getWorld())
                    && player.getLocation().distanceSquared(safePark) < 9.0) {
                return;
            }
            player.teleport(safePark);
        }

        private void spawnCameraDisplay(Location loc) {
            World world = loc.getWorld();
            if (world == null) return;
            cameraSpawnTick = currentTick;
            cameraDisplay = world.spawn(loc, TextDisplay.class, td -> {
                td.setText("");
                td.setDefaultBackground(false);
                td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                td.setTeleportDuration(2);
                td.setViewRange(8.0f);
                td.setShadowRadius(0f);
                td.setShadowStrength(0f);
                td.setVisibleByDefault(false);
                td.setRotation(Location.normalizeYaw(loc.getYaw()), Math.max(-90.0f, Math.min(90.0f, loc.getPitch())));
            });
            player.showEntity(plugin, cameraDisplay);
            if (plugin.getWardrobeManager() != null) {
                plugin.getWardrobeManager().registerPrivateEntity(cameraDisplay.getEntityId(), player.getUniqueId());
            }
            CinematicCameraEngine.applyCameraPose(player, cameraDisplay, loc, true);
            lockCameraPacket(player, cameraDisplay.getEntityId());
        }

        private Location aimAt(Location from, Location target) {
            Vector dir = target.toVector().subtract(from.toVector());
            if (dir.lengthSquared() < 1e-6) return from;
            Location aimed = from.clone();
            aimed.setDirection(dir);
            return aimed;
        }

        private Location clampCameraClearOfWalls(Location pivot, Location desired) {
            if (pivot.getWorld() == null || !pivot.getWorld().equals(desired.getWorld())) return desired;
            Vector ray = desired.toVector().subtract(pivot.toVector());
            double len = ray.length();
            if (len < 0.4) return desired;
            RayTraceResult hit = pivot.getWorld().rayTraceBlocks(
                    pivot, ray.normalize(), len, FluidCollisionMode.NEVER, true
            );
            if (hit != null && hit.getHitPosition() != null) {
                Vector safe = hit.getHitPosition().subtract(ray.normalize().multiply(0.35));
                double safeDist = safe.distance(pivot.toVector());
                if (safeDist >= 0.50 && safeDist < len) {
                    return new Location(desired.getWorld(), safe.getX(), safe.getY(), safe.getZ(), desired.getYaw(), desired.getPitch());
                }
            }
            return desired;
        }

        private void triggerBlackout(int fadeIn, int stay, int fadeOut) {
            TpEffectConfig.BlackoutMode mode = config.getBlackoutMode();
            String uuiHud = config.getBlackoutUltimateUiHud();
            boolean uuiAvail = UltimateUIAPI.isAvailable();

            if ((mode == TpEffectConfig.BlackoutMode.AUTO && uuiAvail && uuiHud != null && !uuiHud.isEmpty() && UltimateUIAPI.get().guiExists(uuiHud))
                    || (mode == TpEffectConfig.BlackoutMode.ULTIMATEUI_HUD && uuiAvail && uuiHud != null && !uuiHud.isEmpty())) {
                try {
                    UltimateUIAPI.get().openGuiHud(player, uuiHud);
                    openedUltimateUiBlackout = true;
                    return;
                } catch (Exception ignored) {}
            }

            if (mode == TpEffectConfig.BlackoutMode.ULTIMATEUI_PANEL && uuiAvail) {
                try {
                    String elem = config.getBlackoutUltimateUiElement();
                    if (elem == null || elem.isEmpty()) elem = "blackout_rect";
                    UiBuilder builder = new UiBuilder("dc_tpeffect_blackout")
                            .screenSize(1920, 1080)
                            .addBlock(elem, 0, 0, 1920, 1080, "#000000");
                    UltimateUIAPI.get().openGuiHud(player, builder, false);
                    openedUltimateUiBlackout = true;
                    return;
                } catch (Exception ignored) {}
            }

            if (mode == TpEffectConfig.BlackoutMode.TITLE_GLYPH
                    && config.getBlackoutTitleGlyph() != null && !config.getBlackoutTitleGlyph().isEmpty()) {
                player.sendTitle(config.getBlackoutTitleGlyph(), "", fadeIn, stay, fadeOut);
                return;
            }

            if (config.isBlackoutUsePotionDarkness() || mode == TpEffectConfig.BlackoutMode.POTION_DARKNESS) {
                int duration = fadeIn + stay + fadeOut + 4;
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, duration, 0, false, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Math.max(8, stay + 2), 0, false, false, false));
            }
        }

        private void clearBlackout() {
            if (openedUltimateUiBlackout && UltimateUIAPI.isAvailable()) {
                try {
                    UltimateUIAPI.get().closeGui(player);
                } catch (Exception ignored) {}
                openedUltimateUiBlackout = false;
            }
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.DARKNESS);
        }

        private void clearTransientProps() {
            if (activeRig != null) {
                activeRig.unregisterAndRemove(plugin.getWardrobeManager());
                activeRig = null;
            }
            for (Entity e : extraProps) {
                if (e != null) {
                    if (plugin.getWardrobeManager() != null) {
                        plugin.getWardrobeManager().unregisterPrivateEntity(e.getEntityId());
                    }
                    if (e.isValid()) e.remove();
                }
            }
            extraProps.clear();
        }

        void forceCleanup(boolean ensureAtDestination) {
            if (task != null) {
                task.cancel();
                task = null;
            }
            clearTransientProps();

            WardrobeManager wm = plugin.getWardrobeManager();
            boolean returningToWardrobe = plugin.isEnabled() && wardrobeSession != null && player.isOnline() && wm != null && wm.isInWardrobe(player);

            if (!returningToWardrobe) {
                for (UUID viewerId : hiddenFromPlayers) {
                    Player viewer = Bukkit.getPlayer(viewerId);
                    if (viewer != null && viewer.isOnline()) {
                        viewer.showPlayer(plugin, player);
                    }
                }
            }
            hiddenFromPlayers.clear();

            if (player.isOnline()) {
                clearBlackout();
                lockCameraPacket(player, player.getEntityId());
                if (cameraDisplay != null) {
                    if (wm != null) {
                        wm.unregisterPrivateEntity(cameraDisplay.getEntityId());
                    }
                    if (cameraDisplay.isValid()) cameraDisplay.remove();
                    cameraDisplay = null;
                }

                if (returningToWardrobe) {
                    restoreWardrobeAfterPreview(player, wardrobeSession, wardrobeRestore);
                } else {
                    if (ensureAtDestination && toLoc != null && toLoc.getWorld() != null
                            && (!player.getWorld().equals(toLoc.getWorld()) || player.getLocation().distanceSquared(toLoc) > 0.04)) {
                        player.teleport(toLoc);
                    }

                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                    player.setGameMode(savedGameMode);
                    float restoreGmVal = toProtocolGameModeValue(savedGameMode);
                    sendClientGameModePacket(player, restoreGmVal);
                    player.setAllowFlight(savedAllowFlight);
                    player.setFlying(savedFlying && savedAllowFlight);
                    player.setWalkSpeed(savedWalkSpeed > 0.001f ? savedWalkSpeed : 0.2f);
                    player.setFlySpeed(savedFlySpeed > 0.001f ? savedFlySpeed : 0.1f);

                    if (plugin.isEnabled()) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (player.isOnline() && !isBusy(player) && (wm == null || !wm.isInWardrobe(player))) {
                                lockCameraPacket(player, player.getEntityId());
                                sendClientGameModePacket(player, restoreGmVal);
                                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                            }
                        }, 1L);

                        final String ownedToken = this.seqToken;
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (player.isOnline() && !isBusy(player) && (wm == null || !wm.isInWardrobe(player))) {
                                String activeToken = null;
                                for (MetadataValue mv : player.getMetadata(SEQ_META_KEY)) {
                                    if (plugin.equals(mv.getOwningPlugin())) {
                                        activeToken = mv.asString();
                                        break;
                                    }
                                }
                                if (ownedToken.equals(activeToken)) {
                                    player.removeMetadata("no_tpeffect", plugin);
                                    player.removeMetadata(SEQ_META_KEY, plugin);
                                }
                            }
                        }, 4L);
                    } else {
                        lockCameraPacket(player, player.getEntityId());
                        sendClientGameModePacket(player, restoreGmVal);
                        player.removePotionEffect(PotionEffectType.INVISIBILITY);
                        player.removeMetadata("no_tpeffect", plugin);
                        player.removeMetadata(SEQ_META_KEY, plugin);
                    }
                }
            } else if (cameraDisplay != null && cameraDisplay.isValid()) {
                cameraDisplay.remove();
                cameraDisplay = null;
            }

            if (!finished) {
                activeSequences.remove(player.getUniqueId());
            }
        }
    }

    // =========================================================================
    // STATIONARY / WARDROBE SHOWCASE LOOP
    // =========================================================================

    private final class StationaryPreviewTask {
        private final Player viewer;
        private final Location baseLoc;
        private final WardrobeMannequin mannequin;
        private final WardrobeManager wm;
        private final TpEffectEntry.CinematicStyle style;
        private final SkinVoxelizer.FlakeStyle flakeStyle;
        private final SkinVoxelizer.EquipmentSnapshot equipmentOverride;
        private final boolean chrono;
        private final int totalTicks;
        private final boolean privateOnly;

        private final List<Entity> transientEntities = new ArrayList<>();
        private SkinVoxelizer.VoxelCharacterRig rig;
        private BukkitTask task;
        private boolean mannequinHidden = false;

        StationaryPreviewTask(
                Player viewer,
                Location baseLoc,
                WardrobeMannequin mannequin,
                WardrobeManager wm,
                TpEffectEntry entry,
                SkinVoxelizer.EquipmentSnapshot equipmentOverride,
                boolean privateOnly
        ) {
            this.viewer = viewer;
            this.baseLoc = baseLoc;
            this.mannequin = mannequin;
            this.wm = wm;
            this.style = entry != null && entry.getCinematicStyle() != null
                    ? entry.getCinematicStyle()
                    : TpEffectEntry.CinematicStyle.OBSIDIAN_DISSOLUTION;
            this.flakeStyle = resolveFlakeStyle(this.style);
            this.equipmentOverride = equipmentOverride;
            this.chrono = (this.style == TpEffectEntry.CinematicStyle.CHRONO_SINGULARITY);
            // Keep preview concise (capped around 90t) while still scaling feel by entry duration
            int entryTicks = entry != null ? entry.getDurationTicks() : 80;
            this.totalTicks = Math.max(48, Math.min(96, entryTicks));
            this.privateOnly = privateOnly;
        }

        void start() {
            hideMannequin();

            Player pViewer = privateOnly ? viewer : null;
            rig = voxelizer.spawnCharacterRig(
                    viewer, baseLoc, pViewer, wm, false, 0.0, flakeStyle, false, equipmentOverride
            );
            viewer.playSound(baseLoc, Sound.BLOCK_CONDUIT_AMBIENT, 0.75f, chrono ? 1.15f : 0.75f);

            int peelStart = 3;
            int peelEnd = Math.max(peelStart + 14, (int) Math.round(totalTicks * 0.43));
            int swapTick = Math.max(peelEnd + 3, (int) Math.round(totalTicks * 0.48));
            int reconStart = swapTick + 3;
            int reconEnd = Math.max(reconStart + 14, (int) Math.round(totalTicks * 0.90));

            task = new BukkitRunnable() {
                int tick = 0;

                @Override
                public void run() {
                    if (!viewer.isOnline() || baseLoc.getWorld() == null) {
                        cancelAndRestore();
                        cancel();
                        return;
                    }

                    if (tick >= peelStart && tick <= peelEnd) {
                        double t = (double) (tick - peelStart) / Math.max(1, peelEnd - peelStart);
                        double horizonY = 1.92 - (t * 2.02);
                        voxelizer.stepDissolveSweep(rig, horizonY, flakeStyle, t);
                        if (tick % 3 == 0) {
                            transientEntities.addAll(voxelizer.spawnColumnAccentShards(
                                    baseLoc, style == TpEffectEntry.CinematicStyle.SKYLINE_SWITCH ? 5 : 3,
                                    true, flakeStyle, privateOnly ? viewer : null, wm
                            ));
                            if (style == TpEffectEntry.CinematicStyle.SKYLINE_SWITCH && tick % 9 == 0) {
                                viewer.playSound(baseLoc, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.80f, 0.60f + (float) t * 0.25f);
                            } else {
                                viewer.playSound(baseLoc, Sound.BLOCK_SAND_PLACE, 0.25f, 0.75f + (float) t * 0.45f);
                            }
                        }
                    } else if (tick == swapTick) {
                        if (rig != null) {
                            rig.unregisterAndRemove(wm);
                        }
                        rig = voxelizer.spawnCharacterRig(
                                viewer,
                                baseLoc,
                                privateOnly ? viewer : null,
                                wm,
                                true,
                                5.5,
                                flakeStyle,
                                false,
                                equipmentOverride
                        );
                        viewer.playSound(baseLoc,
                                style == TpEffectEntry.CinematicStyle.SKYLINE_SWITCH
                                        ? Sound.ENTITY_ENDERMAN_TELEPORT
                                        : (chrono ? Sound.BLOCK_RESPAWN_ANCHOR_CHARGE : Sound.BLOCK_BEACON_ACTIVATE),
                                0.70f, 1.35f);
                    } else if (tick >= reconStart && tick <= reconEnd) {
                        double rt = (double) (tick - reconStart) / Math.max(1, reconEnd - reconStart);
                        double horizonY = -0.05 + (rt * 2.02);
                        voxelizer.stepReconstructSweep(rig, horizonY, flakeStyle, baseLoc, privateOnly ? viewer : null);
                        if (tick % 3 == 0) {
                            transientEntities.addAll(voxelizer.spawnColumnAccentShards(
                                    baseLoc, 3, false, flakeStyle, privateOnly ? viewer : null, wm
                            ));
                            if (style == TpEffectEntry.CinematicStyle.SKYLINE_SWITCH && tick % 9 == 0) {
                                viewer.playSound(baseLoc, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.80f, 0.85f - (float) rt * 0.25f);
                            }
                        }
                    } else if (tick >= totalTicks) {
                        viewer.playSound(baseLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.55f, 1.45f);
                        activePreviews.remove(viewer.getUniqueId());
                        cancelAndRestore();
                        cancel();
                        return;
                    }
                    tick++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }

        private void hideMannequin() {
            if (mannequin == null || mannequin.getEntity() == null) return;
            mannequin.setRotationPaused(true);
            LivingEntity entity = mannequin.getEntity();
            if (entity.isValid() && viewer.isOnline()) {
                viewer.hideEntity(plugin, entity);
                mannequinHidden = true;
            }
        }

        private void showMannequin() {
            if (mannequin != null) {
                mannequin.setRotationPaused(false);
                LivingEntity entity = mannequin.getEntity();
                if (mannequinHidden && entity != null && entity.isValid() && viewer.isOnline()) {
                    viewer.showEntity(plugin, entity);
                }
            }
            mannequinHidden = false;
        }

        void cancelAndRestore() {
            if (task != null) {
                task.cancel();
                task = null;
            }
            if (rig != null) {
                rig.unregisterAndRemove(wm);
                rig = null;
            }
            for (Entity e : transientEntities) {
                if (e != null) {
                    if (wm != null) wm.unregisterPrivateEntity(e.getEntityId());
                    if (e.isValid()) e.remove();
                }
            }
            transientEntities.clear();
            showMannequin();
        }
    }

    private void lockCameraPacket(Player player, int entityId) {
        try {
            PacketContainer cameraPacket = ProtocolLibrary.getProtocolManager()
                    .createPacket(PacketType.Play.Server.CAMERA);
            cameraPacket.getIntegers().write(0, entityId);
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, cameraPacket);
        } catch (Exception ignored) {}
    }

    private void sendClientGameModePacket(Player player, float modeValue) {
        try {
            PacketContainer gmPacket = ProtocolLibrary.getProtocolManager()
                    .createPacket(PacketType.Play.Server.GAME_STATE_CHANGE);
            // Reason 3 = CHANGE_GAME_MODE
            if (gmPacket.getGameStateIDs().size() > 0) {
                gmPacket.getGameStateIDs().write(0, 3);
            } else if (gmPacket.getBytes().size() > 0) {
                gmPacket.getBytes().write(0, (byte) 3);
            }
            gmPacket.getFloat().write(0, modeValue);
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, gmPacket);
        } catch (Exception ignored) {}
    }

    private static float toProtocolGameModeValue(GameMode mode) {
        if (mode == null) return 0.0f;
        return switch (mode) {
            case SURVIVAL -> 0.0f;
            case CREATIVE -> 1.0f;
            case ADVENTURE -> 2.0f;
            case SPECTATOR -> 3.0f;
        };
    }
}
