package dc.dccosmetics.wardrobe;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.CosmeticTemplate;
import dc.dccosmetics.wardrobe.camera.CameraController;
import dc.dccosmetics.wardrobe.camera.WardrobeCinematic;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class WardrobeManager {

    private final WardrobeRoom room;
    private final CameraController cameraController;
    private final Map<UUID, WardrobeSession> activeSessions = new HashMap<>();

    // Debounce navigation inputs so rapid right-clicks cannot skip menus or trigger accidental exit
    private final Map<UUID, Long> lastNavTime = new HashMap<>();

    // Multi-User Private Entity Tracking
    private final Map<Integer, UUID> privateEntities = new java.util.concurrent.ConcurrentHashMap<>();

    // Wardrobe Version: 1 = legacy 3D TextDisplays & controller, 2 = Modern UltimateUI
    private int wardrobeVersion = 2;
    private final dc.dccosmetics.wardrobe.ui.WardrobeUltimateUIManager ultimateUIManager;

    // Category & Cosmetic filters
    private final List<String> enabledCategories = new ArrayList<>();
    private final Set<String> hiddenCosmetics = new HashSet<>();

    // Remote controller styling
    private Material controllerMaterial = Material.STONE_BUTTON;
    private int controllerCustomModelData = 0;
    private String controllerName = "&#84E2CD&l✦ Wardrobe Controller ✦";
    private final List<String> controllerLore = new ArrayList<>();

    public WardrobeManager(WardrobeRoom room) {
        this.room = room;
        this.cameraController = new CameraController();
        this.ultimateUIManager = new dc.dccosmetics.wardrobe.ui.WardrobeUltimateUIManager(this);
        loadConfig();
    }

    public static File getWardrobeConfigFile() {
        File wardrobeDir = new File(DCCosmetics.getInstance().getDataFolder(), "wardrobe");
        if (!wardrobeDir.exists()) wardrobeDir.mkdirs();
        File newFile = new File(wardrobeDir, "wardrobe.yml");
        File oldFile = new File(DCCosmetics.getInstance().getDataFolder(), "wardrobe.yml");
        if (!newFile.exists() && oldFile.exists()) {
            oldFile.renameTo(newFile);
        }
        if (!newFile.exists()) {
            try {
                DCCosmetics.getInstance().saveResource("wardrobe.yml", false);
                if (oldFile.exists()) oldFile.renameTo(newFile);
            } catch (Exception ignored) {}
        }
        return newFile.exists() ? newFile : (oldFile.exists() ? oldFile : newFile);
    }

    public void loadConfig() {
        File file = getWardrobeConfigFile();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        this.wardrobeVersion = config.getInt("wardrobe_version", 2);

        enabledCategories.clear();
        List<String> cats = config.getStringList("categories.enabled");
        if (cats == null || cats.isEmpty()) {
            enabledCategories.addAll(Arrays.asList("Head", "Chest", "Boots", "Teleport"));
        } else {
            enabledCategories.addAll(cats);
        }

        hiddenCosmetics.clear();
        List<String> hidden = config.getStringList("cosmetics.hidden");
        if (hidden != null) {
            for (String h : hidden) {
                if (h != null && !h.trim().isEmpty()) {
                    hiddenCosmetics.add(h.trim().toLowerCase());
                }
            }
        }

        String matStr = config.getString("controller.material", "STONE_BUTTON");
        Material mat = Material.matchMaterial(matStr);
        this.controllerMaterial = (mat != null) ? mat : Material.STONE_BUTTON;

        this.controllerCustomModelData = config.getInt("controller.custom-model-data", 0);
        this.controllerName = config.getString("controller.name", "&#84E2CD&l✦ Wardrobe Controller ✦");

        controllerLore.clear();
        List<String> lore = config.getStringList("controller.lore");
        if (lore == null || lore.isEmpty()) {
            controllerLore.add("&8• &bScroll &8» &7Navigate Items");
            controllerLore.add("&8• &bLeft-Click &8» &aEquip &7/ &fSelect");
            controllerLore.add("&8• &bRight-Click &8» &eBack");
            controllerLore.add("&8• &bSneak &8» &cExit Wardrobe");
        } else {
            controllerLore.addAll(lore);
        }
    }

    public boolean isCosmeticVisible(CosmeticTemplate template) {
        if (template == null) return false;
        if (!template.isEnabled() || template.isHidden()) return false;
        if (hiddenCosmetics.contains(template.getId().toLowerCase())) return false;
        return true;
    }

    public List<String> getEnabledCategories() {
        return enabledCategories;
    }

    public Set<String> getHiddenCosmetics() {
        return hiddenCosmetics;
    }

    public Material getControllerMaterial() {
        return controllerMaterial;
    }

    public int getControllerCustomModelData() {
        return controllerCustomModelData;
    }

    public String getControllerName() {
        return controllerName;
    }

    public List<String> getControllerLore() {
        return controllerLore;
    }

    public int getWardrobeVersion() {
        return wardrobeVersion;
    }

    public dc.dccosmetics.wardrobe.ui.WardrobeUltimateUIManager getUltimateUIManager() {
        return ultimateUIManager;
    }

    public void registerPrivateEntity(int entityId, UUID owner) {
        privateEntities.put(entityId, owner);
    }

    public void unregisterPrivateEntity(int entityId) {
        privateEntities.remove(entityId);
    }

    public void unregisterAllPrivateEntities(UUID owner) {
        privateEntities.entrySet().removeIf(entry -> entry.getValue().equals(owner));
    }

    public boolean isPrivateEntityHiddenFrom(int entityId, UUID viewer) {
        UUID owner = privateEntities.get(entityId);
        if (owner == null) return false;
        return !owner.equals(viewer);
    }

    public WardrobeRoom getRoom() {
        return room;
    }

    public CameraController getCameraController() {
        return cameraController;
    }

    public WardrobeSession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    public java.util.Collection<WardrobeSession> getActiveSessions() {
        return activeSessions.values();
    }

    public boolean isInWardrobe(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public void enterWardrobe(Player player) {
        if (isInWardrobe(player)) {
            player.sendMessage(ChatColor.RED + "You are already in the wardrobe!");
            return;
        }

        if (room.getSpawn() == null || room.getNpcStand() == null) {
            player.sendMessage(ChatColor.RED + "The wardrobe room has not been properly configured yet.");
            return;
        }

        player.setMetadata("in_wardrobe", new FixedMetadataValue(DCCosmetics.getInstance(), true));
        player.setMetadata("no_tpeffect", new FixedMetadataValue(DCCosmetics.getInstance(), true));

        WardrobeSession session = new WardrobeSession(this, player, player.getLocation());
        activeSessions.put(player.getUniqueId(), session);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) continue;
            online.hidePlayer(DCCosmetics.getInstance(), player);
            if (isInWardrobe(online)) {
                player.hidePlayer(DCCosmetics.getInstance(), online);
            }
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab scoreboard off " + player.getName());

        if (dc.dcessentials.modules.glow.GlowManager.getInstance() != null) {
            dc.dcessentials.modules.glow.GlowManager.getInstance().suppressGlow(player);
        }
        player.setGlowing(false);

        WardrobeCinematic cinematic = new WardrobeCinematic(this, session);
        cinematic.playIntro();
    }

    public void exitWardrobe(Player player) {
        WardrobeSession session = activeSessions.remove(player.getUniqueId());
        lastNavTime.remove(player.getUniqueId());

        if (session != null) {
            dc.dccosmetics.tpeffects.TpEffectManager tpMgr = DCCosmetics.getInstance().getTpEffectManager();
            if (tpMgr != null) {
                tpMgr.getCinematicEngine().stopPreview(player);
            }
            session.setPreviewingTeleportId(null);
            session.cleanup();

            // Unlock camera via the session's active controller instance
            if (session.getCameraController() != null) {
                session.getCameraController().unlockCamera(player);
            } else {
                cameraController.unlockCamera(player);
            }

            // Close UltimateUI immediately
            if (ultimateUIManager != null) {
                ultimateUIManager.closeStudio(player);
            }
            try {
                Class<?> apiClass = Class.forName("dev.xqedii.ultimateUI.api.UltimateUIAPI");
                Object apiInstance = apiClass.getMethod("get").invoke(null);
                apiClass.getMethod("closeGui", org.bukkit.entity.Player.class).invoke(apiInstance, player);
            } catch (Throwable ignored) {}

            unregisterAllPrivateEntities(player.getUniqueId());

            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.equals(player)) continue;
                online.showPlayer(DCCosmetics.getInstance(), player);
                if (!isInWardrobe(online)) {
                    player.showPlayer(DCCosmetics.getInstance(), online);
                }
            }

            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST);

            if (session.getSavedInventory() != null) {
                player.getInventory().setContents(session.getSavedInventory());
            }
            if (session.getSavedArmor() != null) {
                player.getInventory().setArmorContents(session.getSavedArmor());
            }
            if (session.getSavedOffHand() != null) {
                player.getInventory().setItemInOffHand(session.getSavedOffHand());
            }

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab scoreboard on " + player.getName());

            Location exitLoc = room.isReturnToPrevious() ? session.getPreviousLocation() : room.getFallbackExit();
            if (exitLoc != null) {
                player.teleport(exitLoc);
            }
            player.setGameMode(session.getPreviousGameMode());
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);

            if (dc.dcessentials.modules.glow.GlowManager.getInstance() != null) {
                dc.dcessentials.modules.glow.GlowManager.getInstance().restoreGlow(player);
            }

            player.removeMetadata("in_wardrobe", DCCosmetics.getInstance());

            // Post-exit safety sweep: closes any residual UI even after teleportation packets
            Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
                if (player.isOnline()) {
                    if (ultimateUIManager != null) {
                        ultimateUIManager.closeStudio(player);
                    }
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "uui close " + player.getName());
                    player.removeMetadata("no_tpeffect", DCCosmetics.getInstance());
                }
            }, 2L);

            player.sendMessage(ChatColor.GREEN + "Exited the wardrobe.");
        }
    }

    public void cleanupAll() {
        for (WardrobeSession session : activeSessions.values()) {
            Player p = session.getPlayer();
            if (p != null && p.isOnline()) {
                if (session.getCameraController() != null) {
                    session.getCameraController().unlockCamera(p);
                } else {
                    cameraController.unlockCamera(p);
                }
                if (ultimateUIManager != null) {
                    ultimateUIManager.closeStudio(p);
                }
                try {
                    Class<?> apiClass = Class.forName("dev.xqedii.ultimateUI.api.UltimateUIAPI");
                    Object apiInstance = apiClass.getMethod("get").invoke(null);
                    apiClass.getMethod("closeGui", org.bukkit.entity.Player.class).invoke(apiInstance, p);
                } catch (Throwable ignored) {}
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "uui close " + p.getName());

                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.equals(p)) continue;
                    online.showPlayer(DCCosmetics.getInstance(), p);
                    p.showPlayer(DCCosmetics.getInstance(), online);
                }
                p.setWalkSpeed(0.2f);
                p.setFlySpeed(0.1f);
                p.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST);
                if (session.getSavedInventory() != null) {
                    p.getInventory().setContents(session.getSavedInventory());
                }
                if (session.getSavedArmor() != null) {
                    p.getInventory().setArmorContents(session.getSavedArmor());
                }
                if (session.getSavedOffHand() != null) {
                    p.getInventory().setItemInOffHand(session.getSavedOffHand());
                }
                if (dc.dcessentials.modules.glow.GlowManager.getInstance() != null) {
                    dc.dcessentials.modules.glow.GlowManager.getInstance().restoreGlow(p);
                }
                p.removeMetadata("in_wardrobe", DCCosmetics.getInstance());
                p.removeMetadata("no_tpeffect", DCCosmetics.getInstance());
            }
            session.cleanup();
        }
        activeSessions.clear();
        privateEntities.clear();
        lastNavTime.clear();
    }

    // ─────────────────────────────────────────────────────────────
    //  BACK NAVIGATION (with 300ms debounce protection)
    // ─────────────────────────────────────────────────────────────

    public void handleBackNavigation(Player player) {
        WardrobeSession session = getSession(player);
        if (session == null || session.isInCinematic()) return;

        long now = System.currentTimeMillis();
        if (now - lastNavTime.getOrDefault(player.getUniqueId(), 0L) < 300L) {
            return;
        }
        lastNavTime.put(player.getUniqueId(), now);

        if (wardrobeVersion == 2) {
            if (session.getCurrentLevel() == WardrobeSession.MenuLevel.COSMETICS) {
                player.playSound(player, org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.5f);
                dc.dccosmetics.tpeffects.TpEffectManager tpMgr = DCCosmetics.getInstance().getTpEffectManager();
                if (tpMgr != null) {
                    tpMgr.getCinematicEngine().stopPreview(player);
                }
                session.setPreviewingTeleportId(null);
                if (session.getMannequin() != null) {
                    session.getMannequin().clearPreview();
                }
                session.setCurrentLevel(WardrobeSession.MenuLevel.RARITIES);
                session.setSelectedIndex(0);
                session.setScrollWindowOffset(0);

                // Keep camera locked on the category angle when backing out of cosmetics
                String activeCat = session.getSelectedCategory() != null ? session.getSelectedCategory().toLowerCase() : "overview";
                Location catCam = room.getCamera(activeCat);
                if (catCam != null && session.getCameraController() != null) {
                    session.getCameraController().moveCamera(player, catCam, session);
                }

                ultimateUIManager.renderRarities(player, session);
            } else if (session.getCurrentLevel() == WardrobeSession.MenuLevel.RARITIES) {
                player.playSound(player, org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.5f);
                zoomToSpawn(player, session);
                session.setCurrentLevel(WardrobeSession.MenuLevel.CATEGORIES);
                session.setSelectedIndex(0);
                session.setScrollWindowOffset(0);
                ultimateUIManager.openOverview(player, session);
            } else if (session.getCurrentLevel() == WardrobeSession.MenuLevel.CATEGORIES) {
                player.playSound(player, org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.2f);
                exitWardrobe(player);
            }
            return;
        }

        if (session.getHud() == null) return;

        player.playSound(player, org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.5f);

        switch (session.getCurrentLevel()) {
            case COSMETICS:
                if (session.getPreviewingCosmeticId() != null) {
                    session.setPreviewingCosmeticId(null);
                    if (session.getMannequin() != null) session.getMannequin().clearPreview();
                    session.getHud().renderAll();
                } else {
                    session.setCurrentLevel(WardrobeSession.MenuLevel.RARITIES);
                    session.setSelectedIndex(0);
                    session.setScrollWindowOffset(0);
                    session.getHud().renderAll();
                }
                break;

            case RARITIES:
                session.setCurrentLevel(WardrobeSession.MenuLevel.CATEGORIES);
                session.setSelectedIndex(0);
                session.setScrollWindowOffset(0);
                zoomToSpawn(player, session);
                break;

            case CATEGORIES:
                break;

            default:
                break;
        }
    }

    private void zoomToSpawn(Player player, WardrobeSession session) {
        try {
            Location overviewCam = room.getCamera("overview");
            if (overviewCam != null) {
                if (session.getCameraController() != null) {
                    session.getCameraController().moveCamera(player, overviewCam, session);
                } else {
                    cameraController.moveCamera(player, overviewCam, session);
                }
            }
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[Wardrobe] Camera back failed: " + e.getMessage());
        }
    }
}