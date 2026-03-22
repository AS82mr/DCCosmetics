package dc.dccosmetics;

import dc.dccosmetics.api.PacketAdapter;
import dc.dccosmetics.command.CosmeticsCommand;
import dc.dccosmetics.command.ProfileCommand;
import dc.dccosmetics.gui.GuiManager;
import dc.dccosmetics.listener.PlayerListener;
import dc.dccosmetics.manager.ProfileManager;
import dc.dccosmetics.manager.TemplateRegistry;
import dc.dccosmetics.manager.SculptManager;
import dc.dccosmetics.manager.DialogEditorManager;
import dc.dccosmetics.manager.BlockbenchImporter;
import dc.dccosmetics.manager.CosmeticWatcher;
import dc.dccosmetics.nms.ProtocolLibAdapter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import dc.dccosmetics.listener.FootstepListener;
import java.io.File;

public final class DCCosmetics extends JavaPlugin {

    private static DCCosmetics instance;

    // Core Managers
    private TemplateRegistry templateRegistry;
    private ProfileManager profileManager;
    private GuiManager guiManager;
    private PacketAdapter packetAdapter;
    private YamlConfiguration guiConfig;
    private SculptManager sculptManager;
    private DialogEditorManager dialogEditorManager;
    private BlockbenchImporter blockbenchImporter;
    private CosmeticWatcher watcher;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("----------------------------------------");
        getLogger().info("DCcosmetics is enabling...");

        // 1. Initialize NMS/Packet Adapter FIRST
        this.packetAdapter = new ProtocolLibAdapter();

        // 2. Initialize Core Managers (MUST BE DONE BEFORE COMMANDS!)
        this.templateRegistry = new TemplateRegistry();
        this.profileManager = new ProfileManager();
        this.guiManager = new GuiManager();
        this.sculptManager = new SculptManager();
        this.dialogEditorManager = new DialogEditorManager();
        this.blockbenchImporter = new BlockbenchImporter();

        // 3. Load Data (Reads your cosmetics folder)
        File guiFile = new File(getDataFolder(), "gui.yml");
        if (!guiFile.exists()) {
            try {
                saveResource("gui.yml", false);
            } catch (IllegalArgumentException e) {
                getLogger().warning("[WARNING] Default gui.yml not found in the compiled jar! Creating a blank one...");
                try {
                    guiFile.getParentFile().mkdirs();
                    guiFile.createNewFile();
                } catch (java.io.IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        this.guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        this.templateRegistry.loadAll();

        // 4. Register Listeners (Cleaned up the duplicates!)
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        getServer().getPluginManager().registerEvents(new FootstepListener(), this);

        // 5. Register Command (Now guiManager is fully loaded!)
        if (getCommand("cosmetics") != null) {
            getCommand("cosmetics").setExecutor(new CosmeticsCommand(this.guiManager));
            getCommand("cosmetics").setTabCompleter(new dc.dccosmetics.command.CosmeticsTabCompleter());
        } else {
            getLogger().severe("[ERROR] Command 'cosmetics' not found in plugin.yml!");
        }
        
        if (getCommand("profile") != null) {
            getCommand("profile").setExecutor(new ProfileCommand(this.guiManager));
        } else {
            getLogger().severe("[ERROR] Command 'profile' not found in plugin.yml!");
        }

        // 6. Handle Reloads (If the plugin is reloaded while players are already online)
        for (Player player : Bukkit.getOnlinePlayers()) {
            profileManager.loadProfile(player);
            packetAdapter.injectPlayer(player); // Preps ProtocolLib memory
        }
        
        // 7. Start the Skript-Style Auto-Reloader!
        this.watcher = new CosmeticWatcher(this);
        this.watcher.runTaskTimerAsynchronously(this, 60L, 40L); // Starts after 3s, checks every 2s

        getLogger().info("DCcosmetics successfully enabled!");
        getLogger().info("----------------------------------------");
    }

    public void reloadConfigs() {
        File guiFile = new File(getDataFolder(), "gui.yml");
        if (guiFile.exists()) {
            this.guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        }
        this.templateRegistry.loadAll();
        this.profileManager.refreshAllOnlinePlayers();
        this.sculptManager.refreshAllDummies();
    }

    @Override
    public void onDisable() {
        getLogger().info("DCcosmetics is disabling...");

        // Clean up holograms for all online players to prevent permanent ghost blocks
        if (profileManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                profileManager.unloadProfile(player);
            }
        }

        getLogger().info("DCcosmetics successfully disabled!");
    }

    // Getters for everywhere else in the plugin
    public static DCCosmetics getInstance() { return instance; }
    public TemplateRegistry getTemplateRegistry() { return templateRegistry; }
    public ProfileManager getProfileManager() { return profileManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public PacketAdapter getPacketAdapter() { return packetAdapter; }
    public SculptManager getSculptManager() { return sculptManager; }
    public DialogEditorManager getDialogEditorManager() { return dialogEditorManager; }
    public BlockbenchImporter getBlockbenchImporter() { return blockbenchImporter; }
    public YamlConfiguration getGuiConfig() { return guiConfig; }
}