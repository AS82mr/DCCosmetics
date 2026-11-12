package dc.dccosmetics.wardrobe.camera;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.wardrobe.WardrobeManager;
import dc.dccosmetics.wardrobe.WardrobeSession;
import dc.dccosmetics.wardrobe.camera.CctvCameraRig;
import dc.dccosmetics.wardrobe.hud.WardrobeHUD;
import dc.dccosmetics.wardrobe.npc.WardrobeMannequin;
import dc.dcessentials.core.util.ColorUtil;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Logger;

public class WardrobeCinematic {

    private final WardrobeManager manager;
    private final WardrobeSession session;
    private final Player player;
    private static final Logger log = DCCosmetics.getInstance().getLogger();

    private static final boolean DEBUG = true;

    private static void debug(Player player, String step) {
        if (DEBUG) {
            log.info("[Wardrobe][DEBUG] " + player.getName() + " | " + step);
        }
    }

    public WardrobeCinematic(WardrobeManager manager, WardrobeSession session) {
        this.manager = manager;
        this.session = session;
        this.player = session.getPlayer();
    }

    public void playIntro() {
        session.setInCinematic(true);
        debug(player, "playIntro START");

        int maxStep = 6;
        try {
            java.io.File f = new java.io.File(DCCosmetics.getInstance().getDataFolder(), "wardrobe.yml");
            if (f.exists()) {
                maxStep = org.bukkit.configuration.file.YamlConfiguration
                        .loadConfiguration(f).getInt("hud.diagnostic-max-step", 6);
            }
        } catch (Exception ignored) {}
        final int diagnosticMaxStep = maxStep;
        debug(player, "diagnostic-max-step=" + diagnosticMaxStep);

        Location spawnLoc = manager.getRoom().getSpawn();
        Location overviewCam = manager.getRoom().getCamera("overview");
        Location initialCam = overviewCam != null ? overviewCam : (spawnLoc != null ? spawnLoc.clone().add(0, 1.62, 0) : null);
        debug(player, "spawnLoc=" + locStr(spawnLoc) + " overviewCam=" + locStr(overviewCam));

        // ── 1. Teleport player firmly to floor FIRST ────────────────────────
        if (spawnLoc != null) {
            player.teleport(spawnLoc);
            debug(player, "teleported to studio spawn OK");
        }

        // ── 2. Set GameMode FIRST, then enable flight so abilities aren't wiped ──
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);

        // Lock camera perspective at tick 0
        if (initialCam != null) {
            CameraController cameraController = new CameraController();
            cameraController.spawnAndLockCamera(player, initialCam, session);
            session.setCameraController(cameraController);
            session.setCurrentCameraLocation(initialCam);
            if (cameraController.getCameraEntity() != null) {
                manager.registerPrivateEntity(cameraController.getCameraEntity().getEntityId(), player.getUniqueId());
            }
            debug(player, "camera locked at tick 0 OK");
        }

        // ── 3. Initial fade / Title & Player State Freeze ───────────────────
        player.sendTitle(ChatColor.DARK_GRAY + "Entering...", "", 10, 40, 10);
        player.playSound(player, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.4f);
        player.playSound(player, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, 0.5f);

        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.INVISIBILITY, 99999, 0, false, false));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.JUMP_BOOST, 99999, 128, false, false, false));
        player.setWalkSpeed(0.0f);
        player.setFlySpeed(0.0f);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

        // Save full inventory & armor (including held slot BEFORE equipping the Wardrobe controller)
        session.setSavedHeldSlot(player.getInventory().getHeldItemSlot());
        org.bukkit.inventory.ItemStack[] currentInv = player.getInventory().getContents();
        org.bukkit.inventory.ItemStack[] clonedInv = new org.bukkit.inventory.ItemStack[currentInv.length];
        for (int i = 0; i < currentInv.length; i++) {
            clonedInv[i] = currentInv[i] != null ? currentInv[i].clone() : null;
        }
        session.setSavedInventory(clonedInv);

        org.bukkit.inventory.ItemStack[] currentArmor = player.getInventory().getArmorContents();
        org.bukkit.inventory.ItemStack[] clonedArmor = new org.bukkit.inventory.ItemStack[currentArmor.length];
        for (int i = 0; i < currentArmor.length; i++) {
            clonedArmor[i] = currentArmor[i] != null ? currentArmor[i].clone() : null;
        }
        session.setSavedArmor(clonedArmor);

        org.bukkit.inventory.ItemStack offHand = player.getInventory().getItemInOffHand();
        session.setSavedOffHand(offHand != null ? offHand.clone() : null);
        player.getInventory().setItemInOffHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR));
        player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);

        // Equip controller item in all 9 hotbar slots for navigation
        org.bukkit.inventory.ItemStack controller = createControllerItem(manager);
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, controller.clone());
        }
        debug(player, "gamemode + invisibility + movement freeze + controller equipped");

        // ── 4. Deferred setup — complete UI and Mannequin after screen settles ──
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !manager.isInWardrobe(player)) {
                    debug(player, "ABORTED — player offline or no longer in wardrobe");
                    return;
                }

                try {
                    player.setAllowFlight(true);
                    player.setFlying(true);

                    // STEP 1: Title + Chime
                    debug(player, "STEP 1 — title + chime");
                    player.sendTitle(
                        ChatColor.GOLD + "" + ChatColor.BOLD + "THE WARDROBE",
                        ChatColor.GRAY + "Select your style",
                        10, 60, 20);
                    player.playSound(player, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.8f, 1.4f);

                    if (diagnosticMaxStep < 2) {
                        session.setInCinematic(false);
                        return;
                    }

                    // STEP 2: Legacy HUD (if v1)
                    WardrobeHUD hud = null;
                    if (manager.getWardrobeVersion() == 1) {
                        debug(player, "STEP 2 — spawning HUD (v1 3D TextDisplays)");
                        hud = new WardrobeHUD(session);
                        hud.spawnAll();
                        session.setHud(hud);
                    } else {
                        debug(player, "STEP 2 — skipping 3D HUD (v2 uses UltimateUI canvas)");
                    }

                    if (diagnosticMaxStep < 3) {
                        session.setInCinematic(false);
                        return;
                    }

                    // STEP 3: Spawn Mannequin
                    debug(player, "STEP 3 — spawning mannequin");
                    Location npcBase = manager.getRoom().getNpcStand();
                    Location npcLoc = (manager.getWardrobeVersion() == 1) ? offsetForSession(npcBase) : npcBase;
                    WardrobeMannequin mannequin = new WardrobeMannequin(session, npcLoc);
                    mannequin.spawn();
                    session.setMannequin(mannequin);
                    if (mannequin.getEntity() != null) {
                        manager.registerPrivateEntity(mannequin.getEntity().getEntityId(), player.getUniqueId());
                    }

                    if (diagnosticMaxStep < 4) {
                        session.setInCinematic(false);
                        return;
                    }

                    // STEP 4: Camera Verification
                    debug(player, "STEP 4 — camera verify");
                    if (session.getCameraController() == null || session.getCameraController().getCameraEntity() == null) {
                        CameraController cCtrl = new CameraController();
                        cCtrl.spawnAndLockCamera(player, initialCam, session);
                        session.setCameraController(cCtrl);
                        session.setCurrentCameraLocation(initialCam);
                        if (cCtrl.getCameraEntity() != null) {
                            manager.registerPrivateEntity(cCtrl.getCameraEntity().getEntityId(), player.getUniqueId());
                        }
                    } else if (session.getCameraController().getCameraEntity().isValid()) {
                        session.getCameraController().lockCameraToEntityId(
                                player, session.getCameraController().getCameraEntity().getEntityId());
                    }

                    if (diagnosticMaxStep < 5) {
                        session.setInCinematic(false);
                        return;
                    }

                    // STEP 5: Spawn CCTV Rig (v1)
                    if (manager.getWardrobeVersion() == 1) {
                        debug(player, "STEP 5 — spawning CCTV rig (v1)");
                        try {
                            CctvCameraRig rig = new CctvCameraRig(player);
                            rig.spawnAt(spawnLoc, npcLoc);
                            session.setCctvRig(rig);
                        } catch (Exception e) {
                            log.warning("[Wardrobe][DEBUG] STEP 5 FAILED (CCTV rig): " + e);
                        }
                    } else {
                        debug(player, "STEP 5 — skipping CCTV rig (v2 direct camera perspective)");
                    }

                    if (diagnosticMaxStep < 6) {
                        session.setInCinematic(false);
                        return;
                    }

                    // STEP 6: Finalize HUD & Activate Session
                    debug(player, "STEP 6 — finalizing interface");
                    if (manager.getWardrobeVersion() == 1) {
                        if (hud != null) {
                            hud.renderAll();
                        }
                    } else {
                        manager.getUltimateUIManager().openOverview(player, session);
                        if (session.getCameraController() != null
                                && session.getCameraController().getCameraEntity() != null
                                && session.getCameraController().getCameraEntity().isValid()) {
                            session.getCameraController().lockCameraToEntityId(
                                    player, session.getCameraController().getCameraEntity().getEntityId());
                        }
                    }

                    session.setInCinematic(false);
                    debug(player, "playIntro COMPLETE — cinematic ended (all 6 steps)");

                } catch (Exception e) {
                    log.severe("[Wardrobe][DEBUG] FATAL during wardrobe setup for " + player.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    manager.exitWardrobe(player);
                }
            }
        }.runTaskLater(DCCosmetics.getInstance(), 30L);
    }

    public static org.bukkit.inventory.ItemStack createControllerItem(WardrobeManager manager) {
        Material ctrlMat = manager.getControllerMaterial();
        org.bukkit.inventory.ItemStack controller = new org.bukkit.inventory.ItemStack(ctrlMat);
        org.bukkit.inventory.meta.ItemMeta meta = controller.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(manager.getControllerName()));
            if (manager.getControllerCustomModelData() > 0) {
                meta.setCustomModelData(manager.getControllerCustomModelData());
            }
            java.util.List<String> rawLore = manager.getControllerLore();
            java.util.List<String> coloredLore = new java.util.ArrayList<>();
            for (String l : rawLore) {
                coloredLore.add(ColorUtil.colorize(l));
            }
            meta.setLore(coloredLore);
            controller.setItemMeta(meta);
        }
        return controller;
    }

    private static String locStr(Location loc) {
        if (loc == null) return "null";
        return String.format("%s @ %.1f,%.1f,%.1f",
            loc.getWorld() != null ? loc.getWorld().getName() : "?",
            loc.getX(), loc.getY(), loc.getZ());
    }

    private Location offsetForSession(Location base) {
        if (base == null) return null;

        int slot = 0;
        for (WardrobeSession s : manager.getActiveSessions()) {
            if (s.getPlayer().equals(player)) continue;
            slot++;
        }

        if (slot == 0) return base.clone();

        double side = (slot % 2 == 0 ? 1 : -1) * ((slot + 1) / 2) * 3.0;
        org.bukkit.util.Vector forward = base.getDirection().normalize();
        org.bukkit.util.Vector right = new org.bukkit.util.Vector(-forward.getZ(), 0, forward.getX()).normalize();
        Location offset = base.clone().add(right.multiply(side));
        offset.setYaw(base.getYaw());
        offset.setPitch(base.getPitch());
        return offset;
    }
}