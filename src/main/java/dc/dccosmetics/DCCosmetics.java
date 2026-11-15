package dc.dccosmetics;

import dc.dccosmetics.model.CosmeticTemplate;
import dc.dccosmetics.api.PacketAdapter;
import dc.dccosmetics.command.CosmeticsCommand;
import dc.dccosmetics.command.ProfileCommand;
import dc.dccosmetics.command.CustomiseCommand;
import dc.dccosmetics.gui.GuiManager;
import dc.dccosmetics.listener.PlayerListener;
import dc.dccosmetics.listener.EnchantmentDragListener;
import dc.dccosmetics.manager.ProfileManager;
import dc.dccosmetics.manager.TemplateRegistry;
import dc.dccosmetics.manager.SculptManager;
import dc.dccosmetics.manager.DialogEditorManager;
import dc.dccosmetics.manager.BlockbenchImporter;
import dc.dccosmetics.manager.CosmeticWatcher;
import dc.dccosmetics.manager.LanguageManager;
import dc.dccosmetics.manager.WaypointManager;
import dc.dccosmetics.nms.ProtocolLibAdapter;
import dc.dccosmetics.wardrobe.WardrobeManager;
import dc.dccosmetics.wardrobe.WardrobeRoom;
import dc.dccosmetics.wardrobe.WardrobeRegionSelector;
import dc.dccosmetics.wardrobe.camera.CinematicTeleportController;
import dc.dccosmetics.wardrobe.command.CinematicTpCommand;
import dc.dccosmetics.wardrobe.command.WardrobeCommand;
import dc.dccosmetics.wardrobe.command.WardrobeTabCompleter;
import dc.dccosmetics.wardrobe.gui.WardrobeAdminGUI;
import dc.dccosmetics.wardrobe.listener.WardrobeListener;
import dc.dccosmetics.wardrobe.listener.WardrobePacketListener;
import dc.dccosmetics.cinematic.SyncStageManager;
import dc.dccosmetics.cinematic.SyncListener;
import dc.dccosmetics.cinematic.SyncCommand;
import dc.dccosmetics.cinematic.SyncTabCompleter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import dc.dccosmetics.listener.FootstepListener;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import dc.dcessentials.core.database.DatabaseManager;
import dc.dcessentials.modules.teleportation.TeleportationManager;
import dc.dcessentials.modules.teleportation.listener.TeleportListener;
import dc.dcessentials.modules.teleportation.gui.KeystoneGUI;
import dc.dcessentials.modules.rng.RngManager;
import dc.dcessentials.modules.rng.listener.RngCombatListener;
import dc.dcessentials.modules.rng.gui.RngGUI;
import dc.dcessentials.modules.pity.PityManager;
import dc.dcessentials.modules.pity.gui.PityGUI;
import dc.dcessentials.modules.npc.NpcRegistry;
import dc.dcessentials.modules.npc.NpcZoneManager;
import dc.dcessentials.modules.npc.NpcDialogueEngine;
import dc.dcessentials.modules.npc.NpcSetupWand;
import dc.dcessentials.modules.npc.skin.SkinProcessor;
import dc.dcessentials.modules.npc.gui.NpcTrackerGUI;
import dc.dcessentials.modules.npc.gui.DossierGUI;
import dc.dcessentials.modules.npc.command.DCNpcCommand;
import dc.dcessentials.modules.npc.listener.CitizensNpcListener;
import dc.dcessentials.modules.npc.listener.ModelEngineNpcListener;
import dc.dcessentials.commands.DCEssentialsCommand;
import dc.dcessentials.commands.KeystoneCommand;
import dc.dcessentials.commands.RngCommand;
import dc.dcessentials.commands.PityCommand;
import dc.dcessentials.commands.WaypointCommand;
import dc.dcessentials.commands.RngHookCommand;
import java.io.File;

public final class DCCosmetics extends JavaPlugin {

    private static DCCosmetics instance;

    // Core Managers
    private TemplateRegistry templateRegistry;
    private ProfileManager profileManager;
    private GuiManager guiManager;
    private PacketAdapter packetAdapter;
    private YamlConfiguration profileConfig;
    private YamlConfiguration customiseConfig;
    private SculptManager sculptManager;
    private DialogEditorManager dialogEditorManager;
    private BlockbenchImporter blockbenchImporter;
    private CosmeticWatcher watcher;
    private LanguageManager languageManager;
    private WardrobeManager wardrobeManager;
    private WaypointManager waypointManager;

    // DCEssentials Integrated Core Modules
    private DatabaseManager databaseManager;
    private TeleportationManager teleportationManager;
    private RngManager rngManager;
    private PityManager pityManager;
    private dc.dcessentials.modules.glow.GlowManager glowManager;

    // Git-Style Spawn Sync Modules
    private SyncStageManager syncStageManager;
    private SyncListener syncListener;

    // Dragoncote Canon NPC & District System
    private SkinProcessor skinProcessor;
    private NpcRegistry npcRegistry;
    private NpcZoneManager npcZoneManager;
    private dc.dcessentials.modules.npc.NpcCanonManager npcCanonManager;
    private NpcDialogueEngine npcDialogueEngine;
    private NpcSetupWand npcSetupWand;
    private NpcTrackerGUI npcTrackerGUI;
    private dc.dcessentials.modules.npc.scene.SceneManager sceneManager;
    private dc.dcessentials.modules.npc.gui.NpcEditorGUI npcEditorGUI;
    private dc.dcessentials.modules.npc.gui.SceneEditorGUI sceneEditorGUI;
    private dc.dcessentials.modules.npc.gui.NpcStageEditorGUI npcStageEditorGUI;
    private dc.dcessentials.modules.npc.gui.NpcSingleStageGUI npcSingleStageGUI;
    private dc.dcessentials.modules.npc.gui.NpcEngineSelectorGUI npcEngineSelectorGUI;
    private dc.dcessentials.modules.npc.gui.WarpMenuGUI warpMenuGUI;
    private dc.dcessentials.modules.npc.gui.MissionRewardGUI missionRewardGUI;
    private dc.dcessentials.modules.npc.scene.SceneCatalog sceneCatalog;
    private dc.dcessentials.modules.npc.skin.BbmodelSkinBaker bbmodelSkinBaker;
    private dc.dcessentials.modules.npc.behavior.NpcBehaviorController npcBehaviorController;
    private dc.dcessentials.modules.npc.scene.DecorationManager decorationManager;
    private dc.dcessentials.modules.npc.gui.SkinBakeGUI skinBakeGUI;
    private dc.dcessentials.modules.npc.gui.DecorationListGUI decorationListGUI;
    private dc.dcessentials.modules.npc.gui.DecorationEditorGUI decorationEditorGUI;

    // Dragoncote Server Events & World Cycle System
    private dc.dcessentials.modules.event.EventManager eventManager;

    // Dragoncote Unified Item, Stat & Scroll System
    private dc.dcessentials.modules.items.ItemModule itemModule;

    // Dragoncote Packet-Based Floating Skull Pet System
    private dc.dcessentials.modules.pets.PetManager petManager;

    // Dragoncote Emperor Boss Engine & TAB Integration
    private dc.dcessentials.modules.boss.EmperorBossManager emperorBossManager;
    private dc.dcessentials.modules.boss.EmperorTabHook emperorTabHook;
    private dc.dcessentials.modules.boss.EmperorCommand emperorCommand;

    // Dragoncote Teleportation Effects & 3D Voxel Camera Module
    private dc.dccosmetics.tpeffects.TpEffectManager tpEffectManager;

    // TEMPORARY Model & Texturepack Curator Module (safe to remove after cleanup)
    private dc.dcessentials.modules.curator.CuratorManager curatorManager;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("----------------------------------------");
        getLogger().info("DCEssentials (v2.0) is enabling...");

        File legacyFolder = new File(getDataFolder().getParentFile(), "DCCosmetics");
        if (legacyFolder.exists() && (!getDataFolder().exists() || getDataFolder().list() == null || getDataFolder().list().length == 0)) {
            copyDirectory(legacyFolder, getDataFolder());
            getLogger().info("[Migration] Automatically imported configurations and cosmetics from legacy DCCosmetics folder.");
        }

        try {
            this.packetAdapter = new ProtocolLibAdapter();
        } catch (Throwable t) {
            getLogger().warning("[DCEssentials] ProtocolLib adapter failed to initialize: " + t.getMessage());
        }

        this.templateRegistry = new TemplateRegistry();
        this.profileManager = new ProfileManager();
        this.guiManager = new GuiManager();
        this.sculptManager = new SculptManager();
        this.dialogEditorManager = new DialogEditorManager();
        this.blockbenchImporter = new BlockbenchImporter();
        this.languageManager = new LanguageManager();
        this.languageManager.load();
        this.waypointManager = new WaypointManager();

        this.databaseManager = new DatabaseManager();
        this.databaseManager.init();

        this.teleportationManager = new TeleportationManager();
        this.teleportationManager.start();

        this.rngManager = new RngManager();
        this.rngManager.registerDefaults();

        this.pityManager = new PityManager();
        this.pityManager.registerDefaults();

        this.glowManager = new dc.dcessentials.modules.glow.GlowManager(this);

        try {
            this.syncStageManager = new SyncStageManager();
            this.syncListener = new SyncListener(this.syncStageManager);
            getServer().getPluginManager().registerEvents(this.syncListener, this);

            Bukkit.getScheduler().runTask(this, () -> {
                if (this.syncListener != null) {
                    this.syncListener.registerWorldEditHook();
                }
            });
        } catch (Throwable t) {
            getLogger().warning("[DCEssentials] Failed to initialize SyncListener: " + t.getMessage());
        }

        WardrobeRoom wardrobeRoom = new WardrobeRoom();
        wardrobeRoom.load();
        this.tpEffectManager = new dc.dccosmetics.tpeffects.TpEffectManager(this);
        this.wardrobeManager = new WardrobeManager(wardrobeRoom);
        WardrobeRegionSelector regionSelector = new WardrobeRegionSelector(wardrobeRoom);
        WardrobeAdminGUI adminGUI = new WardrobeAdminGUI(wardrobeRoom);

        saveDefaultConfig();

        File profileFile = new File(getDataFolder(), "profile.yml");
        if (!profileFile.exists()) {
            try {
                saveResource("profile.yml", false);
            } catch (IllegalArgumentException e) {
                try {
                    profileFile.getParentFile().mkdirs();
                    profileFile.createNewFile();
                } catch (java.io.IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        File customiseFile = new File(getDataFolder(), "customise.yml");
        if (!customiseFile.exists()) {
            try {
                saveResource("customise.yml", false);
            } catch (IllegalArgumentException e) {
                try {
                    customiseFile.getParentFile().mkdirs();
                    customiseFile.createNewFile();
                } catch (java.io.IOException ex) {
                    ex.printStackTrace();
                }
            }
        }

        File teleportationFile = new File(getDataFolder(), "teleportation.yml");
        if (!teleportationFile.exists()) {
            try {
                saveResource("teleportation.yml", false);
            } catch (Exception ignored) {}
        }

        File waypointsFile = new File(getDataFolder(), "waypoints.yml");
        if (!waypointsFile.exists()) {
            try {
                saveResource("waypoints.yml", false);
            } catch (Exception ignored) {}
        }

        File guiFile = new File(getDataFolder(), "gui.yml");
        if (!guiFile.exists()) {
            try {
                saveResource("gui.yml", false);
            } catch (Exception ignored) {}
        }

        File cameraTemplate = new File(getDataFolder(), "cosmetics" + File.separator + "camera" + File.separator + "default_camera.yml");
        if (!cameraTemplate.exists()) {
            try {
                saveResource("cosmetics/camera/default_camera.yml", false);
            } catch (IllegalArgumentException ignored) {}
        }

        if (getCommand("customise") != null) {
            getCommand("customise").setExecutor(new CustomiseCommand(this.guiManager));
        }
        this.profileConfig = YamlConfiguration.loadConfiguration(profileFile);
        this.customiseConfig = YamlConfiguration.loadConfiguration(customiseFile);
        this.templateRegistry.loadAll();

        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        getServer().getPluginManager().registerEvents(new EnchantmentDragListener(), this);
        getServer().getPluginManager().registerEvents(new FootstepListener(), this);
        getServer().getPluginManager().registerEvents(new WardrobeListener(this.wardrobeManager), this);
        getServer().getPluginManager().registerEvents(new dc.dccosmetics.tpeffects.listener.TpEffectListener(this, this.tpEffectManager), this);
        new WardrobePacketListener(this.wardrobeManager).register();

        if (Bukkit.getPluginManager().isPluginEnabled("UltimateUI") && this.wardrobeManager.getUltimateUIManager() != null) {
            getServer().getPluginManager().registerEvents(
                new dc.dccosmetics.wardrobe.ui.WardrobeUltimateUIListener(this.wardrobeManager, this.wardrobeManager.getUltimateUIManager()), this);
            getLogger().info("[Wardrobe] Hooked into UltimateUI for Modern Wardrobe UI (v2).");
        }

        getServer().getPluginManager().registerEvents(new dc.dcessentials.core.gui.GuiGuardListener(), this);
        getServer().getPluginManager().registerEvents(new TeleportListener(this.teleportationManager), this);
        getServer().getPluginManager().registerEvents(new KeystoneGUI(), this);
        getServer().getPluginManager().registerEvents(new dc.dcessentials.modules.teleportation.listener.KeystoneItemListener(), this);
        getServer().getPluginManager().registerEvents(new RngCombatListener(), this);
        getServer().getPluginManager().registerEvents(new RngGUI(), this);
        getServer().getPluginManager().registerEvents(new PityGUI(), this);

        if (getCommand("cosmetics") != null) {
            getCommand("cosmetics").setExecutor(new CosmeticsCommand(this.guiManager));
            getCommand("cosmetics").setTabCompleter(new dc.dccosmetics.command.CosmeticsTabCompleter());
        }

        if (getCommand("profile") != null) {
            getCommand("profile").setExecutor(new ProfileCommand(this.guiManager));
            getCommand("profile").setTabCompleter(new dc.dccosmetics.command.ProfileTabCompleter());
        }

        if (getCommand("setupcinematicworld") != null) {
            getCommand("setupcinematicworld").setExecutor(new dc.dccosmetics.cinematic.CinematicWorldCommand());
            getCommand("setupcinematicworld").setTabCompleter(new dc.dccosmetics.cinematic.CinematicTabCompleter());
        }
        if (getCommand("draincolors") != null) {
            getCommand("draincolors").setExecutor(new dc.dccosmetics.cinematic.DrainColorsCommand());
        }
        if (getCommand("spawnsync") != null) {
            getCommand("spawnsync").setExecutor(new SyncCommand(this.syncStageManager));
            getCommand("spawnsync").setTabCompleter(new SyncTabCompleter());
        } else {
            registerDynamicCommand("spawnsync", new SyncCommand(this.syncStageManager), new SyncTabCompleter(), java.util.Arrays.asList("sync", "gitsync"));
        }
        if (getCommand("wardrobe") != null) {
            getCommand("wardrobe").setExecutor(new WardrobeCommand(this.wardrobeManager, wardrobeRoom, regionSelector, adminGUI));
            getCommand("wardrobe").setTabCompleter(new WardrobeTabCompleter());
        }

        // Register Cinematic Teleport Command (/cinetp)
        CinematicTpCommand cineTpCmd = new CinematicTpCommand();
        if (getCommand("cinetp") != null) {
            getCommand("cinetp").setExecutor(cineTpCmd);
            getCommand("cinetp").setTabCompleter(cineTpCmd);
        } else {
            registerDynamicCommand("cinetp", cineTpCmd, cineTpCmd, java.util.Arrays.asList("cinematicteleport", "voxelwarp"));
        }

        // Register Teleportation Effects Command (/tpeffect)
        dc.dccosmetics.tpeffects.command.TpEffectCommand tpEffCmd = new dc.dccosmetics.tpeffects.command.TpEffectCommand(this, this.tpEffectManager);
        if (getCommand("tpeffect") != null) {
            getCommand("tpeffect").setExecutor(tpEffCmd);
            getCommand("tpeffect").setTabCompleter(tpEffCmd);
        } else {
            registerDynamicCommand("tpeffect", tpEffCmd, tpEffCmd, java.util.Arrays.asList("tpeffects", "tpe"));
        }

        if (getCommand("dcessentials") != null) {
            getCommand("dcessentials").setExecutor(new DCEssentialsCommand());
            getCommand("dcessentials").setTabCompleter(new dc.dcessentials.commands.DCEssentialsTabCompleter());
        }
        if (getCommand("keystone") != null) {
            getCommand("keystone").setExecutor(new KeystoneCommand());
            getCommand("keystone").setTabCompleter(new dc.dcessentials.commands.KeystoneTabCompleter());
        }
        if (getCommand("rng") != null) {
            getCommand("rng").setExecutor(new RngCommand());
            getCommand("rng").setTabCompleter(new dc.dcessentials.commands.RngTabCompleter());
        }
        if (getCommand("pity") != null) {
            getCommand("pity").setExecutor(new PityCommand());
            getCommand("pity").setTabCompleter(new dc.dcessentials.commands.PityTabCompleter());
        }
        if (getCommand("waypoint") != null) {
            getCommand("waypoint").setExecutor(new WaypointCommand());
            getCommand("waypoint").setTabCompleter(new dc.dcessentials.commands.WaypointTabCompleter());
        }
        if (getCommand("rnghook") != null) {
            getCommand("rnghook").setExecutor(new RngHookCommand());
            getCommand("rnghook").setTabCompleter(new dc.dcessentials.commands.RngHookTabCompleter());
        }
        dc.dcessentials.commands.GlowCommand glowCmd = new dc.dcessentials.commands.GlowCommand();
        if (getCommand("glow") != null) {
            getCommand("glow").setExecutor(glowCmd);
            getCommand("glow").setTabCompleter(glowCmd);
        } else {
            registerDynamicCommand("glow", glowCmd, glowCmd, java.util.Arrays.asList("glows", "glowing", "dcglow"));
        }

        registerPlaceholderExpansions();
        Bukkit.getScheduler().runTask(this, this::registerPlaceholderExpansions);
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPluginEnable(org.bukkit.event.server.PluginEnableEvent event) {
                if ("PlaceholderAPI".equalsIgnoreCase(event.getPlugin().getName())) {
                    registerPlaceholderExpansions();
                }
            }
        }, this);

        // Dragoncote Multi-World NPC, District & Setup Wand Engine
        this.skinProcessor = new SkinProcessor();
        this.npcRegistry = new NpcRegistry();
        this.npcRegistry.initializeDefaults();
        this.npcRegistry.loadCustomStages(getDataFolder());
        this.skinProcessor.ensureAllNpcDirectories(this.npcRegistry.getAllCharacters());

        this.npcZoneManager = new NpcZoneManager();
        this.npcCanonManager = new dc.dcessentials.modules.npc.NpcCanonManager(this.npcRegistry, this.npcZoneManager);
        this.npcDialogueEngine = new NpcDialogueEngine(this.npcRegistry, this.npcZoneManager);
        this.npcTrackerGUI = new NpcTrackerGUI(this.npcZoneManager, this.npcRegistry);
        this.sceneManager = new dc.dcessentials.modules.npc.scene.SceneManager();
        this.npcEditorGUI = new dc.dcessentials.modules.npc.gui.NpcEditorGUI(this.npcRegistry, this.npcZoneManager, this.npcCanonManager);
        this.sceneEditorGUI = new dc.dcessentials.modules.npc.gui.SceneEditorGUI(this.sceneManager);
        this.npcStageEditorGUI = new dc.dcessentials.modules.npc.gui.NpcStageEditorGUI(this.npcRegistry);
        this.npcSingleStageGUI = new dc.dcessentials.modules.npc.gui.NpcSingleStageGUI(this.npcRegistry);
        this.npcEngineSelectorGUI = new dc.dcessentials.modules.npc.gui.NpcEngineSelectorGUI(this.npcRegistry);
        this.warpMenuGUI = new dc.dcessentials.modules.npc.gui.WarpMenuGUI(this.npcZoneManager);
        this.missionRewardGUI = new dc.dcessentials.modules.npc.gui.MissionRewardGUI(this.npcRegistry, this.npcDialogueEngine, this.npcZoneManager);
        this.npcDialogueEngine.setMissionRewardGUI(this.missionRewardGUI);

        // Dynamic Scenes Catalog, .bbmodel Skin Baker, Behavior/Patrol Engine & Decoration DCNPCs
        this.sceneCatalog = new dc.dcessentials.modules.npc.scene.SceneCatalog();
        this.sceneCatalog.reload();
        this.bbmodelSkinBaker = new dc.dcessentials.modules.npc.skin.BbmodelSkinBaker();
        this.npcBehaviorController = new dc.dcessentials.modules.npc.behavior.NpcBehaviorController(this.npcRegistry, this.sceneCatalog);
        this.decorationManager = new dc.dcessentials.modules.npc.scene.DecorationManager();
        this.decorationManager.setManagers(this.npcCanonManager, this.skinProcessor, this.npcBehaviorController);
        this.npcBehaviorController.setDecorationManager(this.decorationManager);
        this.decorationManager.load();
        this.skinBakeGUI = new dc.dcessentials.modules.npc.gui.SkinBakeGUI(
                this.npcRegistry, this.skinProcessor, this.bbmodelSkinBaker, this.sceneCatalog, this.npcZoneManager, this.npcCanonManager);
        this.decorationListGUI = new dc.dcessentials.modules.npc.gui.DecorationListGUI(this.decorationManager);
        this.decorationEditorGUI = new dc.dcessentials.modules.npc.gui.DecorationEditorGUI(this.decorationManager);

        // Cross-wiring GUI references
        this.npcTrackerGUI.setManagers(this.npcCanonManager, this.npcEditorGUI);
        this.npcTrackerGUI.setDecorationListGUI(this.decorationListGUI);
        this.npcEditorGUI.setStageEditorGUI(this.npcStageEditorGUI);
        this.npcEditorGUI.setTrackerGUI(this.npcTrackerGUI);
        this.npcEditorGUI.setEngineSelectorGUI(this.npcEngineSelectorGUI);
        this.npcEditorGUI.setWarpMenuGUI(this.warpMenuGUI);
        this.npcEditorGUI.setSkinBakeGUI(this.skinBakeGUI);
        this.npcStageEditorGUI.setNpcEditorGUI(this.npcEditorGUI);
        this.npcStageEditorGUI.setSingleStageGUI(this.npcSingleStageGUI);
        this.npcSingleStageGUI.setStageListGUI(this.npcStageEditorGUI);
        this.npcSingleStageGUI.setWarpMenuGUI(this.warpMenuGUI);
        this.npcEngineSelectorGUI.setNpcEditorGUI(this.npcEditorGUI);
        this.npcEngineSelectorGUI.setSceneCatalog(this.sceneCatalog);
        this.npcEngineSelectorGUI.setBehaviorController(this.npcBehaviorController);
        this.npcEngineSelectorGUI.setDecorationManager(this.decorationManager);
        this.npcEngineSelectorGUI.setDecorationEditorGUI(this.decorationEditorGUI);
        this.npcEngineSelectorGUI.setCanonManager(this.npcCanonManager);
        this.npcEngineSelectorGUI.setZoneManager(this.npcZoneManager);
        this.skinBakeGUI.setDecorationManager(this.decorationManager);
        this.skinBakeGUI.setNpcEditorGUI(this.npcEditorGUI);
        this.skinBakeGUI.setDecorationEditorGUI(this.decorationEditorGUI);
        this.decorationListGUI.setDecorationEditorGUI(this.decorationEditorGUI);
        this.decorationListGUI.setTrackerGUI(this.npcTrackerGUI);
        this.decorationEditorGUI.setDecorationListGUI(this.decorationListGUI);
        this.decorationEditorGUI.setEngineSelectorGUI(this.npcEngineSelectorGUI);
        this.decorationEditorGUI.setSkinBakeGUI(this.skinBakeGUI);
        this.decorationEditorGUI.setBehaviorController(this.npcBehaviorController);
        this.warpMenuGUI.setTrackerGUI(this.npcTrackerGUI);
        this.warpMenuGUI.setNpcEditorGUI(this.npcEditorGUI);
        this.warpMenuGUI.setSingleStageGUI(this.npcSingleStageGUI);

        this.npcBehaviorController.start();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (this.sceneCatalog != null) this.sceneCatalog.reload();
            if (this.decorationManager != null) this.decorationManager.syncAllDecorations();
        }, 60L);

        this.npcSetupWand = new NpcSetupWand(this.npcZoneManager, this.npcRegistry, this.npcTrackerGUI, this.npcCanonManager, this.sceneManager, this.npcEditorGUI, this.sceneEditorGUI);

        getServer().getPluginManager().registerEvents(this.npcTrackerGUI, this);
        getServer().getPluginManager().registerEvents(this.npcSetupWand, this);
        getServer().getPluginManager().registerEvents(this.npcEditorGUI, this);
        getServer().getPluginManager().registerEvents(this.sceneEditorGUI, this);
        getServer().getPluginManager().registerEvents(this.npcStageEditorGUI, this);
        getServer().getPluginManager().registerEvents(this.npcSingleStageGUI, this);
        getServer().getPluginManager().registerEvents(this.npcEngineSelectorGUI, this);
        getServer().getPluginManager().registerEvents(this.warpMenuGUI, this);
        getServer().getPluginManager().registerEvents(this.missionRewardGUI, this);
        getServer().getPluginManager().registerEvents(this.sceneManager, this);
        getServer().getPluginManager().registerEvents(this.skinBakeGUI, this);
        getServer().getPluginManager().registerEvents(this.decorationListGUI, this);
        getServer().getPluginManager().registerEvents(this.decorationEditorGUI, this);
        if (Bukkit.getPluginManager().getPlugin("Citizens") != null) {
            getServer().getPluginManager().registerEvents(new CitizensNpcListener(this.npcRegistry, this.npcDialogueEngine, this.npcSetupWand, this.npcEditorGUI), this);
            getLogger().info("[DCEssentials] Registered Citizens interaction listener.");
        }
        if (Bukkit.getPluginManager().getPlugin("ModelEngine") != null) {
            getServer().getPluginManager().registerEvents(new ModelEngineNpcListener(this.npcRegistry, this.npcDialogueEngine, this.npcSetupWand, this.npcEditorGUI), this);
            getLogger().info("[DCEssentials] Registered ModelEngine 3D Rig interaction listener.");
        }

        DCNpcCommand dcnpcCmd = new DCNpcCommand(this.npcRegistry, this.npcZoneManager, this.npcCanonManager, this.npcDialogueEngine, this.npcSetupWand, this.npcTrackerGUI, this.sceneManager, this.npcEditorGUI);
        dcnpcCmd.setGuis(this.npcStageEditorGUI, this.warpMenuGUI);
        if (getCommand("dcnpc") != null) {
            getCommand("dcnpc").setExecutor(dcnpcCmd);
            getCommand("dcnpc").setTabCompleter(dcnpcCmd);
        } else {
            registerDynamicCommand("dcnpc", dcnpcCmd, dcnpcCmd, java.util.Arrays.asList("lorenpc", "dce_npc"));
        }

        dc.dcessentials.modules.npc.command.WarpCommand warpCmd = new dc.dcessentials.modules.npc.command.WarpCommand(this.npcZoneManager, this.warpMenuGUI);
        if (getCommand("warp") != null) {
            getCommand("warp").setExecutor(warpCmd);
            getCommand("warp").setTabCompleter(warpCmd);
        } else {
            registerDynamicCommand("warp", warpCmd, warpCmd, java.util.Arrays.asList("warps", "dcwarp", "dcwarps", "fasttravel"));
        }
        if (getCommand("setwarp") != null) {
            getCommand("setwarp").setExecutor(warpCmd);
            getCommand("setwarp").setTabCompleter(warpCmd);
        } else {
            registerDynamicCommand("setwarp", warpCmd, warpCmd, java.util.Arrays.asList("dcsetwarp"));
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (this.npcCanonManager != null) {
                this.npcCanonManager.purgeCliffAndMisplacedNpcs(null);
            }
        }, 60L);

        DossierGUI dossierGUI = new DossierGUI(this.npcRegistry, this.npcDialogueEngine);
        if (getCommand("lore") != null) {
            getCommand("lore").setExecutor(dossierGUI);
            getCommand("lore").setTabCompleter(dossierGUI);
        } else {
            registerDynamicCommand("lore", dossierGUI, dossierGUI, java.util.Arrays.asList("dossier", "personnel"));
        }
        if (getCommand("timeline") != null) {
            getCommand("timeline").setExecutor(dossierGUI);
            getCommand("timeline").setTabCompleter(dossierGUI);
        } else {
            registerDynamicCommand("timeline", dossierGUI, dossierGUI, java.util.Arrays.asList("storyline", "questline"));
        }
        getServer().getPluginManager().registerEvents(dossierGUI, this);

        // Initialize Dragoncote Server Events Engine
        this.eventManager = new dc.dcessentials.modules.event.EventManager(this);
        this.eventManager.init();

        dc.dcessentials.commands.EventCommand eventCmd = new dc.dcessentials.commands.EventCommand(this.eventManager);
        dc.dcessentials.commands.EventTabCompleter eventTab = new dc.dcessentials.commands.EventTabCompleter(this.eventManager);
        if (getCommand("events") != null) {
            getCommand("events").setExecutor(eventCmd);
            getCommand("events").setTabCompleter(eventTab);
        } else {
            registerDynamicCommand("events", eventCmd, eventTab, java.util.Arrays.asList("calendar", "event", "dcevent", "schedule"));
        }
        if (getCommand("calendar") != null) {
            getCommand("calendar").setExecutor(eventCmd);
            getCommand("calendar").setTabCompleter(eventTab);
        }
        if (getCommand("event") != null) {
            getCommand("event").setExecutor(eventCmd);
            getCommand("event").setTabCompleter(eventTab);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            profileManager.loadProfile(player);
            if (packetAdapter != null) packetAdapter.injectPlayer(player);
            CinematicTeleportController.getInstance().warmSkinCache(player);
        }

        this.watcher = new CosmeticWatcher(this);
        this.watcher.runTaskTimerAsynchronously(this, 60L, 40L);

        this.profileManager.startEquipmentScanner();
        try {
            dc.dcessentials.modules.rng.listener.VegasListener.registerIfAvailable();
        } catch (Throwable t) {
            getLogger().warning("[DCEssentials] Vegas integration skipped: " + t.getMessage());
        }

        // Auto-purge any misplaced cliff NPCs from Citizens memory 20 ticks after boot
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (this.npcCanonManager != null) {
                this.npcCanonManager.purgeCliffAndMisplacedNpcs(null);
            }
        }, 20L);

        // Unified Items, Stats, and Scrolls System
        this.itemModule = new dc.dcessentials.modules.items.ItemModule(this);
        this.itemModule.start();
        dc.dcessentials.commands.EmperorItemsCommand empCmd = new dc.dcessentials.commands.EmperorItemsCommand(this.itemModule);
        registerDynamicCommand("emperoritems", empCmd, empCmd, java.util.Arrays.asList("emperoritem", "dceitems"));
        dc.dcessentials.commands.TooltipCommand tooltipCmd = new dc.dcessentials.commands.TooltipCommand();
        if (getCommand("tooltip") != null) {
            getCommand("tooltip").setExecutor(tooltipCmd);
            getCommand("tooltip").setTabCompleter(tooltipCmd);
        } else {
            registerDynamicCommand("tooltip", tooltipCmd, tooltipCmd, java.util.Arrays.asList("tooltips", "loreview"));
        }

        // Packet-Based Floating Skull Pet System
        this.petManager = new dc.dcessentials.modules.pets.PetManager(this);
        this.petManager.start();
        dc.dcessentials.modules.pets.command.PetCommand petCmd = new dc.dcessentials.modules.pets.command.PetCommand(this.petManager);
        if (getCommand("pet") != null) {
            getCommand("pet").setExecutor(petCmd);
            getCommand("pet").setTabCompleter(petCmd);
        } else {
            registerDynamicCommand("pet", petCmd, petCmd, java.util.Arrays.asList("pets", "dcepet", "dcepets"));
        }

        // Emperor Boss Scaling, Adventure BossBar, TextDisplays & TAB Scoreboard Engine
        this.emperorBossManager = new dc.dcessentials.modules.boss.EmperorBossManager(this);
        this.emperorTabHook = new dc.dcessentials.modules.boss.EmperorTabHook(this, this.emperorBossManager, this.eventManager);
        this.emperorBossManager.setTabHook(this.emperorTabHook);
        this.emperorBossManager.init();
        this.emperorTabHook.init();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (this.emperorTabHook != null) {
                this.emperorTabHook.init();
            }
        }, 40L);
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
            getServer().getPluginManager().registerEvents(
                    new dc.dcessentials.modules.boss.EmperorListener(this, this.emperorBossManager, this.emperorTabHook), this);
        } else {
            getLogger().warning("[EmperorBoss] MythicMobs plugin not found; boss combat listener not registered.");
        }
        this.emperorCommand = new dc.dcessentials.modules.boss.EmperorCommand(this, this.emperorBossManager);
        registerDynamicCommand("emperortest", this.emperorCommand, this.emperorCommand,
                java.util.Arrays.asList("emperorscale", "emperorheal", "emperoractive", "emperordeath"));

        // TEMPORARY Model & Texturepack Curator Module (replaces vfx_preview_wand.sk)
        this.curatorManager = new dc.dcessentials.modules.curator.CuratorManager(this);
        this.curatorManager.start();

        getLogger().info("DCEssentials (v2.0) successfully enabled!");
        getLogger().info("----------------------------------------");
    }

    private void copyDirectory(File source, File destination) {
        if (source.isDirectory()) {
            if (!destination.exists()) destination.mkdirs();
            String[] files = source.list();
            if (files != null) {
                for (String file : files) {
                    File srcFile = new File(source, file);
                    File destFile = new File(destination, file);
                    copyDirectory(srcFile, destFile);
                }
            }
        } else {
            try {
                java.nio.file.Files.copy(source.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.io.IOException e) {
                getLogger().warning("Failed to copy " + source.getName() + ": " + e.getMessage());
            }
        }
    }

    public void reloadConfigs() {
        reloadConfig();
        File profileFile = new File(getDataFolder(), "profile.yml");
        if (profileFile.exists()) {
            this.profileConfig = YamlConfiguration.loadConfiguration(profileFile);
        }
        File customiseFile = new File(getDataFolder(), "customise.yml");
        if (customiseFile.exists()) {
            this.customiseConfig = YamlConfiguration.loadConfiguration(customiseFile);
        }
        this.templateRegistry.loadAll();
        this.languageManager.load();
        if (this.teleportationManager != null) this.teleportationManager.loadConfig();
        if (this.waypointManager != null) this.waypointManager.reloadConfig();
        if (this.rngManager != null) this.rngManager.registerDefaults();
        if (this.pityManager != null) this.pityManager.registerDefaults();
        if (this.eventManager != null) this.eventManager.loadConfig();
        if (this.npcRegistry != null) this.npcRegistry.loadCustomStages(getDataFolder());
        if (this.npcZoneManager != null) this.npcZoneManager.loadConfig();
        if (this.npcCanonManager != null) this.npcCanonManager.loadLocations();
        if (this.sceneManager != null) this.sceneManager.loadScenes();
        if (this.sceneCatalog != null) this.sceneCatalog.reload();
        if (this.decorationManager != null) {
            this.decorationManager.load();
            this.decorationManager.syncAllDecorations();
        }
        if (this.wardrobeManager != null) {
            this.wardrobeManager.loadConfig();
            if (this.wardrobeManager.getRoom() != null) this.wardrobeManager.getRoom().load();
        }
        if (this.itemModule != null) this.itemModule.reload();
        if (this.petManager != null) this.petManager.reload();
        if (this.tpEffectManager != null) this.tpEffectManager.reload();
        dc.dccosmetics.wardrobe.hud.HudConfig.get().load();
        this.profileManager.refreshAllOnlinePlayers();
        this.sculptManager.refreshAllDummies();
    }

    @Override
    public void onDisable() {
        getLogger().info("DCEssentials (v2.0) is disabling...");

        if (this.tpEffectManager != null) {
            this.tpEffectManager.shutdown();
        }
        if (this.npcBehaviorController != null) {
            this.npcBehaviorController.stop();
        }
        CinematicTeleportController.getInstance().cleanupAll();
        if (this.wardrobeManager != null) {
            this.wardrobeManager.cleanupAll();
        }
        if (this.syncListener != null) {
            this.syncListener.unregisterWorldEditHook();
        }

        if (profileManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                profileManager.unloadProfile(player);
            }
        }
        if (waypointManager != null) {
            waypointManager.clearAll();
        }
        if (teleportationManager != null) {
            teleportationManager.stop();
        }
        if (itemModule != null) {
            itemModule.shutdown();
        }
        if (petManager != null) {
            petManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (eventManager != null) {
            eventManager.shutdown();
        }
        if (emperorBossManager != null) {
            emperorBossManager.shutdown();
        }
        if (emperorTabHook != null) {
            emperorTabHook.shutdown();
        }
        if (curatorManager != null) {
            curatorManager.shutdown();
        }

        Bukkit.getScheduler().cancelTasks(this);
        org.bukkit.event.HandlerList.unregisterAll(this);

        getLogger().info("DCEssentials (v2.0) successfully disabled!");
    }

    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        return new dc.dccosmetics.worldgen.VoidGenerator(id);
    }

    public String parseColors(String text) {
        return dc.dcessentials.core.util.ColorUtil.colorize(text);
    }

    public String formatMaterialName(Material mat) {
        String[] words = mat.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            sb.append(w.substring(0, 1).toUpperCase()).append(w.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }

    public String getSafeColor(CosmeticTemplate temp, String requestedColor) {
        if (temp != null && temp.getAllowedColors() != null && !temp.getAllowedColors().isEmpty()) {
            boolean isValid = false;
            if (requestedColor != null && requestedColor.matches("^#[0-9a-fA-F]{6}$")) {
                for (String c : temp.getAllowedColors()) {
                    if (c.toUpperCase().startsWith(requestedColor.toUpperCase())) {
                        isValid = true; break;
                    }
                }
            }
            if (!isValid) {
                String first = temp.getAllowedColors().get(0);
                if (first.contains(":")) return first.split(":")[0];
                return first;
            }
            return requestedColor;
        }
        if (requestedColor != null && requestedColor.matches("^#[0-9a-fA-F]{6}$")) return requestedColor;
        return "#FFFFFF";
    }

    public ItemStack createScroll(String id, String color) {
        CosmeticTemplate temp = getTemplateRegistry().getTemplate(id);
        if (temp == null) return new ItemStack(Material.PAPER);

        color = getSafeColor(temp, color);

        ItemStack scroll;
        if (temp.getGuiIconBase64() != null && !temp.getGuiIconBase64().isEmpty()) {
            scroll = dc.dccosmetics.util.HeadUtil.getCustomHead(temp.getGuiIconBase64());
        } else {
            scroll = new ItemStack(Material.PAPER);
        }
        ItemMeta meta = scroll.getItemMeta();
        if (meta == null) return scroll;

        dc.dcessentials.modules.items.model.ItemRarity rarity = dc.dcessentials.modules.items.model.ItemRarity.fromString(temp.getRarity());

        String rawName = temp.getItemName();
        String skinName;
        if (rawName != null && !rawName.trim().isEmpty() && !rawName.equalsIgnoreCase(temp.getId())) {
            skinName = rawName.trim();
        } else {
            skinName = dc.dcessentials.modules.items.tooltip.TooltipRenderer.formatSkinName(temp.getId());
        }
        String dispName = skinName + " Scroll";

        java.util.List<String> loreLines = new java.util.ArrayList<>();
        String slotName = temp.getEquipmentSlot() != null ? temp.getEquipmentSlot().toUpperCase() : "EQUIPMENT";
        loreLines.add("&7Type: <#B996E2>Cosmetic Infusion");
        loreLines.add("&7Target Slot: &f" + slotName);
        loreLines.add("&f");
        if (temp.getLore() != null && !temp.getLore().isEmpty()) {
            for (String l : temp.getLore()) {
                loreLines.add(l);
            }
            loreLines.add("&f");
        }
        loreLines.add("<#FCD34D>&l▶ Drag & drop onto " + slotName.toLowerCase() + " to infuse!");

        meta.getPersistentDataContainer().set(new NamespacedKey(this, "cosmetic_scroll_id"), PersistentDataType.STRING, id);
        meta.getPersistentDataContainer().set(new NamespacedKey(this, "cosmetic_scroll_color"), PersistentDataType.STRING, color);
        meta.getPersistentDataContainer().set(dc.dcessentials.modules.items.scroll.ScrollRegistry.SCROLL_ID_KEY, PersistentDataType.STRING, id);
        meta.getPersistentDataContainer().set(dc.dcessentials.modules.items.scroll.ScrollRegistry.SCROLL_TYPE_KEY, PersistentDataType.STRING, "COSMETIC");

        meta.setEnchantmentGlintOverride(true);

        scroll.setItemMeta(meta);

        dc.dcessentials.modules.items.tooltip.TooltipRenderer.renderScroll(scroll, dispName, rarity, slotName, loreLines);

        return scroll;
    }

    public void updateCosmeticItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        NamespacedKey idKey = new NamespacedKey(this, "cosmetic_id");
        NamespacedKey cKey = new NamespacedKey(this, "cosmetic_color");
        NamespacedKey origKey = new NamespacedKey(this, "cosmetic_original_name");
        NamespacedKey scrollKey = new NamespacedKey(this, "cosmetic_scroll_id");

        if (pdc.has(idKey, PersistentDataType.STRING)) {
            String id = pdc.get(idKey, PersistentDataType.STRING);
            String origName = pdc.has(origKey, PersistentDataType.STRING) ? pdc.get(origKey, PersistentDataType.STRING) : formatMaterialName(item.getType());

            CosmeticTemplate temp = getTemplateRegistry().getTemplate(id);
            if (temp != null) {
                String color = pdc.has(cKey, PersistentDataType.STRING) ? pdc.get(cKey, PersistentDataType.STRING) : getSafeColor(temp, null);
                color = getSafeColor(temp, color);
                String rarity = temp.getRarity().toLowerCase();
                String emoji = getConfig().getString("scrolls." + rarity + ".emoji", "&f⬤");
                String format = getConfig().getString("applied_item_format", "{emoji} &r{item_name} {color}[{cosmetic_name}]");

                String newName = parseColors(format.replace("{emoji}", emoji).replace("{item_name}", origName).replace("{color}", net.md_5.bungee.api.ChatColor.of(color).toString()).replace("{cosmetic_name}", temp.getItemName()));

                boolean updateNeeded = false;
                if (!newName.equals(meta.getDisplayName())) {
                    meta.setDisplayName(newName);
                    updateNeeded = true;
                }

                java.util.List<String> lore = meta.hasLore() ? meta.getLore() : new java.util.ArrayList<>();
                boolean removed = lore.removeIf(l -> ChatColor.stripColor(l).contains("✦ Cosmetic:"));
                String newLoreLine = net.md_5.bungee.api.ChatColor.of(color) + "✦ Cosmetic: " + temp.getItemName();

                if (removed || !lore.contains(newLoreLine)) {
                    lore.add(newLoreLine);
                    meta.setLore(lore);
                    updateNeeded = true;
                }

                if (updateNeeded) item.setItemMeta(meta);
            }
        } else if (pdc.has(scrollKey, PersistentDataType.STRING)) {
            String id = pdc.get(scrollKey, PersistentDataType.STRING);
            CosmeticTemplate temp = getTemplateRegistry().getTemplate(id);
            String color = pdc.has(new NamespacedKey(this, "cosmetic_scroll_color"), PersistentDataType.STRING) ? pdc.get(new NamespacedKey(this, "cosmetic_scroll_color"), PersistentDataType.STRING) : getSafeColor(temp, null);
            ItemStack freshScroll = createScroll(id, color);
            if (!meta.getDisplayName().equals(freshScroll.getItemMeta().getDisplayName()) || !meta.getLore().equals(freshScroll.getItemMeta().getLore())) {
                item.setItemMeta(freshScroll.getItemMeta());
            }
        }
    }

    public static DCCosmetics getInstance() { return instance; }
    public TemplateRegistry getTemplateRegistry() { return templateRegistry; }
    public ProfileManager getProfileManager() { return profileManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public PacketAdapter getPacketAdapter() { return packetAdapter; }
    public SculptManager getSculptManager() { return sculptManager; }
    public DialogEditorManager getDialogEditorManager() { return dialogEditorManager; }
    public BlockbenchImporter getBlockbenchImporter() { return blockbenchImporter; }
    public YamlConfiguration getProfileConfig() { return profileConfig; }
    public YamlConfiguration getCustomiseConfig() { return customiseConfig; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public WardrobeManager getWardrobeManager() { return wardrobeManager; }
    public WaypointManager getWaypointManager() { return waypointManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public TeleportationManager getTeleportationManager() { return teleportationManager; }
    public RngManager getRngManager() { return rngManager; }
    public PityManager getPityManager() { return pityManager; }
    public SyncStageManager getSyncStageManager() { return syncStageManager; }
    public SkinProcessor getSkinProcessor() { return skinProcessor; }
    public NpcRegistry getNpcRegistry() { return npcRegistry; }
    public NpcZoneManager getNpcZoneManager() { return npcZoneManager; }
    public dc.dcessentials.modules.npc.NpcCanonManager getNpcCanonManager() { return npcCanonManager; }
    public NpcDialogueEngine getNpcDialogueEngine() { return npcDialogueEngine; }
    public NpcSetupWand getNpcSetupWand() { return npcSetupWand; }
    public NpcTrackerGUI getNpcTrackerGUI() { return npcTrackerGUI; }
    public dc.dcessentials.modules.npc.scene.SceneManager getSceneManager() { return sceneManager; }
    public dc.dcessentials.modules.npc.gui.NpcEditorGUI getNpcEditorGUI() { return npcEditorGUI; }
    public dc.dcessentials.modules.npc.gui.SceneEditorGUI getSceneEditorGUI() { return sceneEditorGUI; }
    public dc.dcessentials.modules.npc.gui.NpcStageEditorGUI getNpcStageEditorGUI() { return npcStageEditorGUI; }
    public dc.dcessentials.modules.npc.gui.WarpMenuGUI getWarpMenuGUI() { return warpMenuGUI; }
    public dc.dcessentials.modules.npc.gui.MissionRewardGUI getMissionRewardGUI() { return missionRewardGUI; }
    public dc.dcessentials.modules.npc.scene.SceneCatalog getSceneCatalog() { return sceneCatalog; }
    public dc.dcessentials.modules.npc.skin.BbmodelSkinBaker getBbmodelSkinBaker() { return bbmodelSkinBaker; }
    public dc.dcessentials.modules.npc.behavior.NpcBehaviorController getNpcBehaviorController() { return npcBehaviorController; }
    public dc.dcessentials.modules.npc.scene.DecorationManager getDecorationManager() { return decorationManager; }
    public dc.dcessentials.modules.npc.gui.SkinBakeGUI getSkinBakeGUI() { return skinBakeGUI; }
    public dc.dcessentials.modules.npc.gui.DecorationListGUI getDecorationListGUI() { return decorationListGUI; }
    public dc.dcessentials.modules.npc.gui.DecorationEditorGUI getDecorationEditorGUI() { return decorationEditorGUI; }

    public void registerDynamicCommand(String name, org.bukkit.command.CommandExecutor executor, org.bukkit.command.TabCompleter completer, java.util.List<String> aliases) {
        try {
            java.lang.reflect.Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            org.bukkit.command.CommandMap commandMap = (org.bukkit.command.CommandMap) commandMapField.get(Bukkit.getServer());

            java.lang.reflect.Constructor<org.bukkit.command.PluginCommand> constructor = org.bukkit.command.PluginCommand.class.getDeclaredConstructor(String.class, org.bukkit.plugin.Plugin.class);
            constructor.setAccessible(true);
            org.bukkit.command.PluginCommand dynamicCmd = constructor.newInstance(name, this);
            dynamicCmd.setExecutor(executor);
            if (completer != null) dynamicCmd.setTabCompleter(completer);
            if (aliases != null && !aliases.isEmpty()) dynamicCmd.setAliases(aliases);

            commandMap.register(this.getDescription().getName(), dynamicCmd);
            getLogger().info("[DCEssentials] Dynamically registered command /" + name);
        } catch (Throwable t) {
            getLogger().warning("[DCEssentials] Could not dynamically register command /" + name + ": " + t.getMessage());
        }
    }

    public dc.dcessentials.modules.glow.GlowManager getGlowManager() { return glowManager; }
    public dc.dcessentials.modules.event.EventManager getEventManager() { return eventManager; }
    public dc.dcessentials.modules.items.ItemModule getItemModule() { return itemModule; }
    public dc.dcessentials.modules.pets.PetManager getPetManager() { return petManager; }
    public dc.dcessentials.modules.boss.EmperorBossManager getEmperorBossManager() { return emperorBossManager; }
    public dc.dcessentials.modules.boss.EmperorTabHook getEmperorTabHook() { return emperorTabHook; }
    public dc.dcessentials.modules.boss.EmperorCommand getEmperorCommand() { return emperorCommand; }
    public dc.dccosmetics.tpeffects.TpEffectManager getTpEffectManager() { return tpEffectManager; }
    public dc.dcessentials.modules.curator.CuratorManager getCuratorManager() { return curatorManager; }

    public void registerPlaceholderExpansions() {
        var papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) return;
        try {
            dc.dcessentials.core.placeholders.DCEssentialsExpansion mainExp = new dc.dcessentials.core.placeholders.DCEssentialsExpansion();
            boolean r1 = mainExp.register();
            boolean r2 = new dc.dcessentials.core.placeholders.DceExpansion(mainExp).register();
            boolean r3 = new dc.dcessentials.core.placeholders.TvaExpansion().register();
            if (r1 || r2 || r3) {
                getLogger().info("[DCEssentials] Hooked into PlaceholderAPI (dragoncote, dce, tva) successfully.");
            }
        } catch (Throwable t) {
            getLogger().warning("[DCEssentials] PlaceholderAPI hook warning: " + t.getMessage());
        }
    }

    public boolean isDcNpcDebugEnabled() {
        java.util.List<String> list = getConfig().getStringList("debug-mode");
        if (list != null) {
            for (String mode : list) {
                if ("dcnpc".equalsIgnoreCase(mode) || "all".equalsIgnoreCase(mode)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void debugNpc(String message) {
        if (instance != null && instance.isDcNpcDebugEnabled()) {
            instance.getLogger().info("[DCNPC-DEBUG] " + message);
        }
    }
}