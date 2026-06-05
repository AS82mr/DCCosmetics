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
import dc.dccosmetics.nms.ProtocolLibAdapter;
import dc.dccosmetics.wardrobe.WardrobeManager;
import dc.dccosmetics.wardrobe.WardrobeRoom;
import dc.dccosmetics.wardrobe.WardrobeRegionSelector;
import dc.dccosmetics.wardrobe.command.WardrobeCommand;
import dc.dccosmetics.wardrobe.command.WardrobeTabCompleter;
import dc.dccosmetics.wardrobe.gui.WardrobeAdminGUI;
import dc.dccosmetics.wardrobe.listener.WardrobeListener;
import dc.dccosmetics.wardrobe.listener.WardrobePacketListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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
    private YamlConfiguration profileConfig;
    private YamlConfiguration customiseConfig;
    private SculptManager sculptManager;
    private DialogEditorManager dialogEditorManager;
    private BlockbenchImporter blockbenchImporter;
    private CosmeticWatcher watcher;
    private LanguageManager languageManager;
    private WardrobeManager wardrobeManager;

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
        this.languageManager = new LanguageManager();
        this.languageManager.load();
        
        WardrobeRoom wardrobeRoom = new WardrobeRoom();
        wardrobeRoom.load();
        this.wardrobeManager = new WardrobeManager(wardrobeRoom);
        WardrobeRegionSelector regionSelector = new WardrobeRegionSelector(wardrobeRoom);
        WardrobeAdminGUI adminGUI = new WardrobeAdminGUI(wardrobeRoom);

        saveDefaultConfig(); // Automatically saves config.yml from resources!

        // 3. Load Data (Reads your cosmetics folder)
        File profileFile = new File(getDataFolder(), "profile.yml");
        if (!profileFile.exists()) {
            try {
                saveResource("profile.yml", false);
            } catch (IllegalArgumentException e) {
                getLogger().warning("[WARNING] Default profile.yml not found in the compiled jar! Creating a blank one...");
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
                getLogger().warning("[WARNING] Default customise.yml not found! Creating a blank one...");
                try {
                    customiseFile.getParentFile().mkdirs();
                    customiseFile.createNewFile();
                } catch (java.io.IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        
        File cameraTemplate = new File(getDataFolder(), "cosmetics" + File.separator + "camera" + File.separator + "default_camera.yml");
        if (!cameraTemplate.exists()) {
            try {
                saveResource("cosmetics/camera/default_camera.yml", false);
            } catch (IllegalArgumentException e) {
                getLogger().warning("[WARNING] Default camera template not found in jar!");
            }
        }
        
        if (getCommand("customise") != null) {
            getCommand("customise").setExecutor(new CustomiseCommand(this.guiManager));
        } else {
            getLogger().severe("[ERROR] Command 'customise' not found in plugin.yml!");
        }
        this.profileConfig = YamlConfiguration.loadConfiguration(profileFile);
        this.customiseConfig = YamlConfiguration.loadConfiguration(customiseFile);
        this.templateRegistry.loadAll();

        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        getServer().getPluginManager().registerEvents(new EnchantmentDragListener(), this);
        getServer().getPluginManager().registerEvents(new FootstepListener(), this);
        getServer().getPluginManager().registerEvents(new WardrobeListener(this.wardrobeManager), this);
        new WardrobePacketListener(this.wardrobeManager).register();

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

        // 5.5 Register Cinematic Commands
        if (getCommand("setupcinematicworld") != null) {
            getCommand("setupcinematicworld").setExecutor(new dc.dccosmetics.cinematic.CinematicWorldCommand());
        }
        if (getCommand("draincolors") != null) {
            getCommand("draincolors").setExecutor(new dc.dccosmetics.cinematic.DrainColorsCommand());
        }
        if (getCommand("wardrobe") != null) {
            getCommand("wardrobe").setExecutor(new WardrobeCommand(this.wardrobeManager, wardrobeRoom, regionSelector, adminGUI));
            getCommand("wardrobe").setTabCompleter(new WardrobeTabCompleter());
        }

        // 6. Handle Reloads (If the plugin is reloaded while players are already online)
        for (Player player : Bukkit.getOnlinePlayers()) {
            profileManager.loadProfile(player);
            packetAdapter.injectPlayer(player); // Preps ProtocolLib memory
        }
        
        // 7. Start the Skript-Style Auto-Reloader!
        this.watcher = new CosmeticWatcher(this);
        this.watcher.runTaskTimerAsynchronously(this, 60L, 40L); // Starts after 3s, checks every 2s

        // 8. Start the Dynamic Item Bound Equipment Scanner
        this.profileManager.startEquipmentScanner();

        getLogger().info("DCcosmetics successfully enabled!");
        getLogger().info("----------------------------------------");
    }

    public void reloadConfigs() {
        reloadConfig(); // Reloads config.yml
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

    // =========================================
    // GLOBALLY ACCESSIBLE UTILITIES
    // =========================================

    public String parseColors(String text) {
        if (text == null) return "";
        java.util.regex.Pattern hexPattern = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})");
        java.util.regex.Matcher matcher = hexPattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + matcher.group(1)).toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
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
        // If no allowed-colors are defined, accept any valid hex
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
        String rarity = temp.getRarity().toLowerCase();
        String nameFormat = getConfig().getString("scrolls." + rarity + ".name", "&fCosmetic Scroll: {cosmetic}");
        meta.setDisplayName(parseColors(nameFormat.replace("{cosmetic}", temp.getItemName())));
        
        java.util.List<String> lore = new java.util.ArrayList<>();
        for (String line : getConfig().getStringList("scrolls." + rarity + ".lore")) {
            if (line.contains("{itemlore}")) {
                for (String l : temp.getLore()) {
                    lore.add(parseColors(l));
                }
            } else {
                lore.add(parseColors(line.replace("{cosmetic}", temp.getItemName()).replace("{slot}", temp.getEquipmentSlot().toUpperCase())));
            }
        }
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(new NamespacedKey(this, "cosmetic_scroll_id"), PersistentDataType.STRING, id);
        meta.getPersistentDataContainer().set(new NamespacedKey(this, "cosmetic_scroll_color"), PersistentDataType.STRING, color);
        scroll.setItemMeta(meta);
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
                color = getSafeColor(temp, color); // Force sanitize the color to fix any existing corrupted items!
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
            // Auto-update unapplied scrolls if the config changes!
            String id = pdc.get(scrollKey, PersistentDataType.STRING);
            CosmeticTemplate temp = getTemplateRegistry().getTemplate(id);
            String color = pdc.has(new NamespacedKey(this, "cosmetic_scroll_color"), PersistentDataType.STRING) ? pdc.get(new NamespacedKey(this, "cosmetic_scroll_color"), PersistentDataType.STRING) : getSafeColor(temp, null);
            ItemStack freshScroll = createScroll(id, color);
            if (!meta.getDisplayName().equals(freshScroll.getItemMeta().getDisplayName()) || !meta.getLore().equals(freshScroll.getItemMeta().getLore())) {
                item.setItemMeta(freshScroll.getItemMeta());
            }
        }
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
    public YamlConfiguration getProfileConfig() { return profileConfig; }
    public YamlConfiguration getCustomiseConfig() { return customiseConfig; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public WardrobeManager getWardrobeManager() { return wardrobeManager; }
}