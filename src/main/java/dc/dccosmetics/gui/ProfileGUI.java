package dc.dccosmetics.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.CosmeticTemplate;
import dc.dccosmetics.model.PlayerProfile;
import dc.dccosmetics.util.HeadUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileGUI implements InventoryHolder {
    private final Player viewer;
    private final OfflinePlayer target;
    private final PlayerProfile targetProfile;
    private final boolean isSelf;
    private final Inventory inventory;

    // Maps Inventory Slots -> Internal Cosmetic Slot Names ("head", "chest", etc.)
    private final Map<Integer, String> cosmeticSlotMap = new HashMap<>();
    
    // Maps Inventory Slots -> List of Commands to execute
    private final Map<Integer, List<String>> leftClickActions = new HashMap<>();
    private final Map<Integer, List<String>> rightClickActions = new HashMap<>();

    public ProfileGUI(Player viewer, OfflinePlayer target) {
        this.viewer = viewer;
        this.target = target;
        this.isSelf = viewer.getUniqueId().equals(target.getUniqueId());
        this.targetProfile = DCCosmetics.getInstance().getProfileManager().getOfflineProfile(target);

        YamlConfiguration config = DCCosmetics.getInstance().getProfileConfig();
        String rawTitle = config.getString("title", "&8{target}'s Profile");
        String title = formatText(rawTitle);
        int size = config.getInt("size", 54);

        this.inventory = Bukkit.createInventory(this, size, title);
        build(config);
    }

    private void build(YamlConfiguration config) {
        // 1. Fill Background
        if (config.getBoolean("fillItems.enabled", true)) {
            Material fillMat = Material.valueOf(config.getString("fillItems.material", "BLACK_STAINED_GLASS_PANE"));
            ItemStack filler = createItem(fillMat, formatText(config.getString("fillItems.name", " ")), new ArrayList<>());
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, filler);
            }
        }

        // 2. Parse Items
        ConfigurationSection itemsSec = config.getConfigurationSection("items");
        if (itemsSec == null) return;

        for (String key : itemsSec.getKeys(false)) {
            ConfigurationSection itemConfig = itemsSec.getConfigurationSection(key);
            if (itemConfig == null) continue;

            // Check visibility permissions
            boolean onlyOwner = itemConfig.getBoolean("onlyOwner", false);
            boolean onlyVisitor = itemConfig.getBoolean("onlyVisitor", false);
            if (onlyOwner && !isSelf) continue;
            if (onlyVisitor && isSelf) continue;

            List<Integer> slots = itemConfig.getIntegerList("slots");
            
            // Is this a cosmetic display slot?
            String mappedSlotType = null;
            String playerEquipType = null;
            if (itemConfig.contains("type")) {
                String rawType = itemConfig.getString("type").toUpperCase();
                switch (rawType) {
                    case "HELMET": mappedSlotType = "head"; break;
                    case "CHESTPLATE": mappedSlotType = "chest"; break;
                    case "LEGGINGS": mappedSlotType = "waist"; break;
                    case "BOOTS": mappedSlotType = "boots"; break;
                    case "PLAYER_HELMET": playerEquipType = "HELMET"; break;
                    case "PLAYER_CHESTPLATE": playerEquipType = "CHESTPLATE"; break;
                    case "PLAYER_LEGGINGS": playerEquipType = "LEGGINGS"; break;
                    case "PLAYER_BOOTS": playerEquipType = "BOOTS"; break;
                    case "PLAYER_MAIN_HAND": playerEquipType = "MAIN_HAND"; break;
                    case "PLAYER_OFF_HAND": playerEquipType = "OFF_HAND"; break;
                }
            }

            for (int slot : slots) {
                if (slot >= inventory.getSize()) continue;

                // Register Cosmetic Slot mapping
                if (mappedSlotType != null) {
                    cosmeticSlotMap.put(slot, mappedSlotType);
                    renderCosmeticSlot(slot, mappedSlotType, itemConfig);
                } else if (playerEquipType != null) {
                    renderPlayerEquipmentSlot(slot, playerEquipType, itemConfig);
                } else {
                    // Standard Item Rendering
                    renderStandardItem(slot, itemConfig);
                }

                // Register Actions
                if (itemConfig.contains("leftClickCommands")) {
                    leftClickActions.put(slot, itemConfig.getStringList("leftClickCommands"));
                }
                if (itemConfig.contains("rightClickCommands")) {
                    rightClickActions.put(slot, itemConfig.getStringList("rightClickCommands"));
                }
            }
        }
    }

    private void renderCosmeticSlot(int slot, String internalSlot, ConfigurationSection fallbackConfig) {
        String equippedId = targetProfile.getEquippedCosmetic(internalSlot);
        
        if (equippedId != null) {
            CosmeticTemplate template = DCCosmetics.getInstance().getTemplateRegistry().getTemplate(equippedId);
            if (template != null) {
                String hexColor = targetProfile.getEquippedColor(internalSlot);
                // Convert hex to ChatColor for the display name
                String colorPrefix = net.md_5.bungee.api.ChatColor.of(hexColor).toString();
                
                ItemStack item = HeadUtil.getCustomHead(template.getGuiIconBase64());
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(colorPrefix + ChatColor.BOLD + template.getId());
                
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Rarity: " + ChatColor.WHITE + template.getRarity());
                if (isSelf) {
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "Click to edit your " + internalSlot + "!");
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
                
                inventory.setItem(slot, item);
                return;
            }
        }

        // Fallback to YAML default if nothing is equipped
        renderStandardItem(slot, fallbackConfig);
    }

    private void renderPlayerEquipmentSlot(int slot, String equipType, ConfigurationSection fallbackConfig) {
        ItemStack equip = null;
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null && p.getInventory() != null) {
                switch (equipType) {
                    case "HELMET": equip = p.getInventory().getHelmet(); break;
                    case "CHESTPLATE": equip = p.getInventory().getChestplate(); break;
                    case "LEGGINGS": equip = p.getInventory().getLeggings(); break;
                    case "BOOTS": equip = p.getInventory().getBoots(); break;
                    case "MAIN_HAND": equip = p.getInventory().getItemInMainHand(); break;
                    case "OFF_HAND": equip = p.getInventory().getItemInOffHand(); break;
                }
            }
        }
        
        if (equip != null && equip.getType() != Material.AIR) {
            inventory.setItem(slot, equip.clone());
        } else {
            renderStandardItem(slot, fallbackConfig);
        }
    }

    private void renderStandardItem(int slot, ConfigurationSection config) {
        String matString = config.getString("material", "STONE");
        String name = formatText(config.getString("name", "&f"));
        List<String> lore = new ArrayList<>();
        for (String l : config.getStringList("lore")) lore.add(formatText(l));

        ItemStack item;
        if (matString.startsWith("head;")) {
            item = HeadUtil.getCustomHead(matString);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        } else {
            item = createItem(Material.valueOf(matString), name, lore);
        }
        inventory.setItem(slot, item);
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatText(String text) {
        if (text == null) return "";
        text = text.replace("{target}", target.getName() != null ? target.getName() : "Unknown");
        text = text.replace("%player_name%", viewer.getName());
        
        // 1. Soft-depend PlaceholderAPI parsing FIRST (so returned values are correctly colored!)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                java.lang.reflect.Method setPlaceholders = papiClass.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
                text = (String) setPlaceholders.invoke(null, target, text);
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }
        
        // 2. Parse HEX colors (&#RRGGBB)
        java.util.regex.Pattern hexPattern = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})");
        java.util.regex.Matcher matcher = hexPattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + matcher.group(1)).toString());
        }
        matcher.appendTail(buffer);
        text = buffer.toString();
        
        // 3. Parse standard colors
        text = ChatColor.translateAlternateColorCodes('&', text);
        
        // 4. Handle pure "&f", "&7 ", or empty clearing
        if (ChatColor.stripColor(text).trim().isEmpty()) {
            text = " ";
        }

        return text;
    }

    public void handleClick(Player clicker, int slot, ClickType clickType) {
        // 1. Check if they clicked a Cosmetic Equipment Slot
        if (cosmeticSlotMap.containsKey(slot)) {
            if (isSelf) {
                clicker.closeInventory();
                GuiState state = DCCosmetics.getInstance().getGuiManager().getState(clicker);
                state.setSelectedType(cosmeticSlotMap.get(slot)); // Auto-filter to the clicked slot type!
                state.setViewedCosmeticId(null);
                DCCosmetics.getInstance().getGuiManager().openCosmeticsMenu(clicker);
            }
            return; // If not self, do nothing (Read Only)
        }

        // 2. Check Action Commands
        List<String> commands = (clickType.isLeftClick()) ? leftClickActions.get(slot) : rightClickActions.get(slot);
        if (commands != null) {
            for (String cmd : commands) {
                String formattedCmd = formatText(cmd);
                if (formattedCmd.startsWith("[CLOSE]")) { clicker.closeInventory(); }
                else if (formattedCmd.startsWith("[PLAYER] ")) { clicker.performCommand(formattedCmd.substring(9)); }
                else if (formattedCmd.startsWith("[CONSOLE] ")) { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCmd.substring(10)); }
            }
        }
    }

    public void open() { viewer.openInventory(inventory); }
    @Override public Inventory getInventory() { return inventory; }
}