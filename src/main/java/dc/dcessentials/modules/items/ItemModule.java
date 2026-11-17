package dc.dcessentials.modules.items;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.items.enchant.EnchantConfigManager;
import dc.dcessentials.modules.items.gather.GatheringBonusService;
import dc.dcessentials.modules.items.gather.PlacedBlockTracker;
import dc.dcessentials.modules.items.gui.ItemBrowserGUI;
import dc.dcessentials.modules.items.gui.ItemDeleteConfirmGUI;
import dc.dcessentials.modules.items.gui.ItemEditorGUI;
import dc.dcessentials.modules.items.gui.MassImportGUI;
import dc.dcessentials.modules.items.listener.EmperorCombatListener;
import dc.dcessentials.modules.items.listener.MasteryBoosterListener;
import dc.dcessentials.modules.items.listener.ScrollDragListener;
import dc.dcessentials.modules.items.listener.StatCombatListener;
import dc.dcessentials.modules.items.listener.VoidWarperListener;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.rarity.RarityConfigManager;
import dc.dcessentials.modules.items.scroll.ScrollRegistry;
import dc.dcessentials.modules.items.stats.StatAggregator;
import dc.dcessentials.modules.items.sync.LiveSyncListener;
import dc.dcessentials.modules.items.tester.StatTesterManager;
import dc.dcessentials.modules.items.tooltip.TooltipModeService;
import dc.dcessentials.modules.items.tooltip.TooltipPacketListener;
import dc.dcessentials.modules.items.tracker.ItemTrackerGUI;
import dc.dcessentials.modules.items.tracker.ItemTrackerManager;
import dc.dcessentials.modules.items.tracker.PlayerLocationsGUI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;

public class ItemModule {

    private final DCCosmetics plugin;
    private final ScrollRegistry scrollRegistry;
    private final ItemRegistry itemRegistry;
    private final StatAggregator statAggregator;
    private final ItemTrackerManager itemTrackerManager;

    private final MassImportGUI massImportGUI;
    private final ItemEditorGUI itemEditorGUI;
    private final ItemBrowserGUI itemBrowserGUI;
    private final ItemTrackerGUI itemTrackerGUI;
    private final PlayerLocationsGUI playerLocationsGUI;
    private final ItemDeleteConfirmGUI itemDeleteConfirmGUI;

    private final ScrollDragListener scrollDragListener;
    private final EmperorCombatListener emperorCombatListener;
    private final StatCombatListener statCombatListener;
    private final MasteryBoosterListener masteryBoosterListener;
    private final LiveSyncListener liveSyncListener;
    private final StatTesterManager statTesterManager;
    private final VoidWarperListener voidWarperListener;
    private final PlacedBlockTracker placedBlockTracker;
    private final GatheringBonusService gatheringBonusService;
    private final TooltipModeService tooltipModeService;
    private final TooltipPacketListener tooltipPacketListener;

    public ItemModule(DCCosmetics plugin) {
        this.plugin = plugin;
        this.scrollRegistry = new ScrollRegistry();
        this.itemRegistry = new ItemRegistry(scrollRegistry);
        this.statAggregator = new StatAggregator(itemRegistry);
        this.itemTrackerManager = new ItemTrackerManager(itemRegistry);

        this.massImportGUI = new MassImportGUI(itemRegistry);
        this.itemEditorGUI = new ItemEditorGUI(itemRegistry, scrollRegistry);
        this.itemBrowserGUI = new ItemBrowserGUI(itemRegistry, itemEditorGUI);
        this.itemTrackerGUI = new ItemTrackerGUI(itemTrackerManager, itemRegistry);
        this.playerLocationsGUI = new PlayerLocationsGUI(itemTrackerManager, itemRegistry, itemTrackerGUI);
        this.itemDeleteConfirmGUI = new ItemDeleteConfirmGUI(null, itemRegistry);

        this.scrollDragListener = new ScrollDragListener(scrollRegistry);
        this.emperorCombatListener = new EmperorCombatListener(scrollRegistry);
        this.statCombatListener = new StatCombatListener(itemRegistry, statAggregator);
        this.masteryBoosterListener = new MasteryBoosterListener(scrollRegistry);
        this.liveSyncListener = new LiveSyncListener(itemRegistry);
        this.statTesterManager = new StatTesterManager(itemRegistry, statAggregator);
        this.voidWarperListener = new VoidWarperListener(itemRegistry);
        this.placedBlockTracker = new PlacedBlockTracker();
        this.gatheringBonusService = new GatheringBonusService(statAggregator, placedBlockTracker);
        this.tooltipModeService = new TooltipModeService(itemRegistry);
        this.tooltipPacketListener = new TooltipPacketListener(plugin, itemRegistry, scrollRegistry, tooltipModeService);
    }

    public void start() {
        // Pre-warm classes into classloader
        try {
            Class.forName("dc.dcessentials.modules.items.tooltip.TooltipRenderer");
            Class.forName("dc.dcessentials.modules.items.model.DCEItem");
            Class.forName("dc.dcessentials.modules.items.model.ItemStat");
            Class.forName("dc.dcessentials.modules.items.model.ItemRarity");
            Class.forName("dc.dcessentials.modules.items.model.ItemCategory");
            Class.forName("dc.dcessentials.modules.items.gui.ItemBrowserGUI");
            Class.forName("dc.dcessentials.modules.items.gui.ItemBrowserGUI$DCEBrowserHolder");
            Class.forName("dc.dcessentials.modules.items.gui.ItemBrowserGUI$MenuLevel");
        } catch (Throwable t) {
            DCCosmetics.getInstance().getLogger().warning("[DCEssentials] Class pre-warm warning: " + t.getMessage());
        }

        // Load configurations
        RarityConfigManager.load();
        EnchantConfigManager.load();
        scrollRegistry.loadScrolls();
        itemRegistry.loadItems();

        // Register event listeners
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(massImportGUI, DCCosmetics.getInstance());
        pm.registerEvents(itemEditorGUI, DCCosmetics.getInstance());
        pm.registerEvents(itemEditorGUI.getStatEditorMenu(), DCCosmetics.getInstance());
        pm.registerEvents(itemBrowserGUI, DCCosmetics.getInstance());
        pm.registerEvents(itemTrackerManager, DCCosmetics.getInstance());
        pm.registerEvents(itemTrackerGUI, DCCosmetics.getInstance());
        pm.registerEvents(playerLocationsGUI, DCCosmetics.getInstance());
        pm.registerEvents(itemDeleteConfirmGUI, DCCosmetics.getInstance());

        pm.registerEvents(scrollDragListener, DCCosmetics.getInstance());
        pm.registerEvents(statAggregator, DCCosmetics.getInstance());
        pm.registerEvents(emperorCombatListener, DCCosmetics.getInstance());
        pm.registerEvents(statCombatListener, DCCosmetics.getInstance());
        pm.registerEvents(masteryBoosterListener, DCCosmetics.getInstance());
        pm.registerEvents(liveSyncListener, DCCosmetics.getInstance());
        pm.registerEvents(statTesterManager, DCCosmetics.getInstance());
        pm.registerEvents(voidWarperListener, DCCosmetics.getInstance());
        pm.registerEvents(placedBlockTracker, DCCosmetics.getInstance());
        pm.registerEvents(gatheringBonusService, DCCosmetics.getInstance());
        pm.registerEvents(tooltipModeService, DCCosmetics.getInstance());

        tooltipModeService.loadOnlinePlayers();
        if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            tooltipPacketListener.register();
        }

        DCCosmetics.getInstance().getLogger().info("[DCEssentials] Item, Stat, and Scroll ecosystem started successfully.");
    }

    public void shutdown() {
        if (tooltipPacketListener != null) {
            tooltipPacketListener.unregister();
        }
    }

    public void reload() {
        RarityConfigManager.load();
        EnchantConfigManager.load();
        scrollRegistry.loadScrolls();
        itemRegistry.loadItems();
        if (statAggregator != null) {
            statAggregator.clearAll();
        }
        if (itemTrackerManager != null) {
            itemTrackerManager.saveLedger();
        }
    }

    public ScrollRegistry getScrollRegistry() { return scrollRegistry; }
    public ItemRegistry getItemRegistry() { return itemRegistry; }
    public StatAggregator getStatAggregator() { return statAggregator; }
    public ItemTrackerManager getItemTrackerManager() { return itemTrackerManager; }
    public MassImportGUI getMassImportGUI() { return massImportGUI; }
    public ItemEditorGUI getItemEditorGUI() { return itemEditorGUI; }
    public ItemBrowserGUI getItemBrowserGUI() { return itemBrowserGUI; }
    public StatTesterManager getStatTesterManager() { return statTesterManager; }
    public TooltipModeService getTooltipModeService() { return tooltipModeService; }
    public GatheringBonusService getGatheringBonusService() { return gatheringBonusService; }
    public PlacedBlockTracker getPlacedBlockTracker() { return placedBlockTracker; }
}
