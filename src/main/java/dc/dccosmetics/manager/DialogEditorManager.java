package dc.dccosmetics.manager;

import dc.dccosmetics.DCCosmetics;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DialogEditorManager implements Listener {
    private final DCCosmetics plugin = DCCosmetics.getInstance();
    private final Map<UUID, String> activeTemplate = new HashMap<>();
    private final Map<UUID, String> activeComponent = new HashMap<>();
    private final Map<UUID, String> awaitingChatInput = new HashMap<>();

    public DialogEditorManager() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void reopenLastMenu(Player player, String templateId) {
        String lastComp = activeComponent.get(player.getUniqueId());
        String lastTemplate = activeTemplate.get(player.getUniqueId());
        if (lastTemplate != null && lastTemplate.equals(templateId) && lastComp != null) {
            if (lastComp.equals("GLOBAL")) openGeneralSettingsMenu(player, templateId);
            else openComponentMenu(player, templateId, lastComp);
        } else {
            openMainMenu(player, templateId);
        }
    }

    public void openMainMenu(Player player, String templateId) {
        activeTemplate.put(player.getUniqueId(), templateId);
        activeComponent.remove(player.getUniqueId());

        Inventory inv;
        if (player.getOpenInventory().getTitle().equals("§8Sculpting: " + templateId)) {
            inv = player.getOpenInventory().getTopInventory();
            inv.clear();
        } else {
            inv = Bukkit.createInventory(null, 54, "§8Sculpting: " + templateId);
        }

        File file = plugin.getTemplateRegistry().getTemplateFile(templateId);
        if (file != null) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            int slot = 0;
            if (config.getConfigurationSection("components") != null) {
                for (String comp : config.getConfigurationSection("components").getKeys(false)) {
                    String type = config.getString("components." + comp + ".type", "solid");
                    inv.setItem(slot++, createItem(Material.LIME_DYE, "§aEdit: " + comp, "§7Type: " + type, "", "§eClick to edit properties!"));
                }
            }
            if (config.getConfigurationSection("nodes") != null) {
                for (String node : config.getConfigurationSection("nodes").getKeys(false)) {
                    if (slot >= 44) break; // Prevent overflow
                    inv.setItem(slot++, createItem(Material.PURPLE_DYE, "§dEdit Node: " + node, "§7Type: Raw Matrix", "", "§eClick to edit matrix properties!"));
                }
            }
        }

        inv.setItem(48, createItem(Material.COMMAND_BLOCK, "§bGeneral Settings", "§7Edit Global Offset, Scale,", "§7and Rotation of the entire model."));
        inv.setItem(45, createItem(Material.EMERALD, "§a+ Add Component", "§7Adds a new solid component."));
        inv.setItem(49, createItem(Material.BARRIER, "§cClose Editor"));

        if (!player.getOpenInventory().getTitle().equals("§8Sculpting: " + templateId)) {
            player.openInventory(inv);
        }
    }

    public void openGeneralSettingsMenu(Player player, String templateId) {
        activeTemplate.put(player.getUniqueId(), templateId);
        activeComponent.put(player.getUniqueId(), "GLOBAL");
        Inventory inv = Bukkit.createInventory(null, 27, "§8General: " + templateId);
        File file = plugin.getTemplateRegistry().getTemplateFile(templateId);
        if (file == null) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        inv.setItem(0, createPropertyItem("global-offset.x", getVectorVal(config, "global-offset", 0)));
        inv.setItem(1, createPropertyItem("global-offset.y", getVectorVal(config, "global-offset", 1)));
        inv.setItem(2, createPropertyItem("global-offset.z", getVectorVal(config, "global-offset", 2)));
        inv.setItem(3, createPropertyItem("global-scale.x", getVectorVal(config, "global-scale", 0, 1.0)));
        inv.setItem(4, createPropertyItem("global-scale.y", getVectorVal(config, "global-scale", 1, 1.0)));
        inv.setItem(5, createPropertyItem("global-scale.z", getVectorVal(config, "global-scale", 2, 1.0)));
        inv.setItem(6, createPropertyItem("global-rotation.x", getVectorVal(config, "global-rotation", 0)));
        inv.setItem(7, createPropertyItem("global-rotation.y", getVectorVal(config, "global-rotation", 1)));
        inv.setItem(8, createPropertyItem("global-rotation.z", getVectorVal(config, "global-rotation", 2)));
        
        inv.setItem(12, createItem(Material.ARMOR_STAND, "§eEquipment Slot", "§7Current: " + config.getString("type", "chest"), "", "§fClick to cycle (head, chest, waist, boots)"));
        inv.setItem(13, createItem(Material.DIAMOND, "§dRarity", "§7Current: " + config.getString("rarity", "epic"), "", "§fClick to cycle"));
        inv.setItem(14, createItem(Material.MINECART, "§aBlockbench Mode", "§7Current: " + config.getBoolean("blockbench", false), "", "§fClick to toggle"));
        
        inv.setItem(22, createItem(Material.ARROW, "§aBack to Main Menu"));
        player.openInventory(inv);
    }

    public void openComponentMenu(Player player, String templateId, String compName) {
        activeTemplate.put(player.getUniqueId(), templateId);
        activeComponent.put(player.getUniqueId(), compName);

        Inventory inv;
        if (player.getOpenInventory().getTitle().equals("§8Editing: " + compName)) {
            inv = player.getOpenInventory().getTopInventory();
            inv.clear();
        } else {
            inv = Bukkit.createInventory(null, 27, "§8Editing: " + compName);
        }

        File file = plugin.getTemplateRegistry().getTemplateFile(templateId);
        if (file == null) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        String path;
        if (config.contains("components." + compName)) { path = "components." + compName; }
        else if (config.contains("nodes." + compName)) { path = "nodes." + compName; }
        else { openMainMenu(player, templateId); return; }

        String type = config.getString(path + ".type", "raw_node");
        inv.setItem(4, createItem(Material.NAME_TAG, "§eEditing: " + compName, "§7Type: " + type, "", "§fClick to change type!"));

        int slot = 9;
        inv.setItem(slot++, createPropertyItem("local-offset.x", getVectorVal(config, path + ".local-offset", 0)));
        inv.setItem(slot++, createPropertyItem("local-offset.y", getVectorVal(config, path + ".local-offset", 1)));
        inv.setItem(slot++, createPropertyItem("local-offset.z", getVectorVal(config, path + ".local-offset", 2)));
        
        inv.setItem(slot++, createPropertyItem("opacity", config.getDouble(path + ".opacity", 1.0)));

        if (type.equals("raw_node") || type.equals("cube")) {
            inv.setItem(slot++, createPropertyItem("rotation.x", getVectorVal(config, path + ".rotation", 0)));
            inv.setItem(slot++, createPropertyItem("rotation.y", getVectorVal(config, path + ".rotation", 1)));
            inv.setItem(slot++, createPropertyItem("rotation.z", getVectorVal(config, path + ".rotation", 2)));
            if (type.equals("raw_node")) {
                inv.setItem(slot++, createPropertyItem("scale.x", getVectorVal(config, path + ".scale", 0, 1.0)));
                inv.setItem(slot++, createPropertyItem("scale.y", getVectorVal(config, path + ".scale", 1, 1.0)));
                inv.setItem(slot++, createPropertyItem("scale.z", getVectorVal(config, path + ".scale", 2, 1.0)));
            }
        } else {
            inv.setItem(slot++, createPropertyItem("pitch", config.getDouble(path + ".pitch", 90.0)));
        }

        if (type.equals("cube")) {
            inv.setItem(slot++, createPropertyItem("pivot-offset.x", getVectorVal(config, path + ".pivot-offset", 0)));
            inv.setItem(slot++, createPropertyItem("pivot-offset.y", getVectorVal(config, path + ".pivot-offset", 1)));
            inv.setItem(slot++, createPropertyItem("pivot-offset.z", getVectorVal(config, path + ".pivot-offset", 2)));
            inv.setItem(slot++, createPropertyItem("width", config.getDouble(path + ".width", 1.0)));
            inv.setItem(slot++, createPropertyItem("height", config.getDouble(path + ".height", 1.0)));
            inv.setItem(slot++, createPropertyItem("depth", config.getDouble(path + ".depth", 1.0)));
            
            List<String> hidden = config.getStringList(path + ".hidden-faces");
            String hiddenStr = hidden.isEmpty() ? "None" : String.join(",", hidden);
            inv.setItem(slot++, createItem(Material.GLASS, "§bhidden-faces", "§7Current: " + hiddenStr, "", "§eDrop (Q) to type hidden faces in chat!", "§8(e.g., north,up)"));
        } else if (type.equals("solid")) {
            inv.setItem(slot++, createPropertyItem("length", config.getDouble(path + ".length", 1.0)));
            inv.setItem(slot++, createPropertyItem("width", config.getDouble(path + ".width", 1.0)));
        } else if (type.equals("star")) {
            inv.setItem(slot++, createPropertyItem("length", config.getDouble(path + ".length", 1.0)));
            inv.setItem(slot++, createPropertyItem("short-length", config.getDouble(path + ".short-length", 0.5)));
            inv.setItem(slot++, createPropertyItem("width", config.getDouble(path + ".width", 0.2)));
        } else if (type.equals("flat_ring") || type.equals("cylinder")) {
            inv.setItem(slot++, createPropertyItem("radius", config.getDouble(path + ".radius", 0.5)));
            if (type.equals("flat_ring")) inv.setItem(slot++, createPropertyItem("width", config.getDouble(path + ".width", 0.1)));
            if (type.equals("cylinder")) inv.setItem(slot++, createPropertyItem("height", config.getDouble(path + ".height", 0.4)));
        } else if (type.equals("cone") || type.equals("hourglass")) {
            inv.setItem(slot++, createPropertyItem("radius", config.getDouble(path + ".radius", 0.5)));
            inv.setItem(slot++, createPropertyItem("height", config.getDouble(path + ".height", 0.6)));
            inv.setItem(slot++, createPropertyItem("width", config.getDouble(path + ".width", 0.1)));
            inv.setItem(slot++, createPropertyItem("inward-pitch", config.getDouble(path + ".inward-pitch", 35.0)));
        } else if (type.equals("burst")) {
            inv.setItem(slot++, createPropertyItem("length", config.getDouble(path + ".length", 0.6)));
            inv.setItem(slot++, createPropertyItem("width", config.getDouble(path + ".width", 0.1)));
        }

        inv.setItem(22, createItem(Material.ARROW, "§aBack to Main Menu"));
        inv.setItem(26, createItem(Material.RED_DYE, "§cDelete Component", "§7Shift-Right-Click to delete."));
        
        inv.setItem(20, createItem(Material.CYAN_DYE, "§bEdit Color", "§7Current: " + config.getString(path + ".color", "#FFFFFF"), "", "§eDrop (Q) to type Hex Color in chat!"));

        if (!player.getOpenInventory().getTitle().equals("§8Editing: " + compName)) {
            player.openInventory(inv);
        }
    }

    private double getVectorVal(YamlConfiguration config, String path, int index) {
        return getVectorVal(config, path, index, 0.0);
    }
    private double getVectorVal(YamlConfiguration config, String path, int index, double def) {
        List<Double> list = config.getDoubleList(path);
        if (list.size() > index) return list.get(index);
        return def;
    }

    private ItemStack createPropertyItem(String prop, double val) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b" + prop);
        List<String> lore = new ArrayList<>();
        lore.add("§7Current: §e" + String.format("%.3f", val));
        lore.add("");
        lore.add("§fLeft-Click: §a+0.2");
        lore.add("§fShift-Left: §a+0.02");
        lore.add("§fRight-Click: §c-0.2");
        lore.add("§fShift-Right: §c-0.02");
        lore.add("§fDrop (Q): §eType exact value");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (loreLines.length > 0) {
            List<String> lore = new ArrayList<>();
            for (String l : loreLines) lore.add(l);
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String context = awaitingChatInput.get(player.getUniqueId());
        if (context != null) {
            event.setCancelled(true);
            String input = event.getMessage().trim();
            
            String[] parts = context.split(":");
            String templateId = parts[0];
            String compName = parts[1];
            String prop = parts[2];

            if (input.equalsIgnoreCase("cancel")) {
                awaitingChatInput.remove(player.getUniqueId());
                player.sendMessage("§cInput cancelled.");
                Bukkit.getScheduler().runTask(plugin, () -> openComponentMenu(player, templateId, compName));
                return;
            }

            if (prop.equals("hidden-faces")) {
                awaitingChatInput.remove(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> applyHiddenFaces(player, templateId, compName, input));
                return;
            }

            // FIX: Bypass the decimal parser if it's a color!
            if (prop.equals("color")) {
                awaitingChatInput.remove(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> applyStringShift(player, templateId, compName, input));
                return;
            }

            try {
                double val = Double.parseDouble(input);
                awaitingChatInput.remove(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    applyShift(templateId, compName, prop, val, true);
                    if (compName.equals("GLOBAL")) openGeneralSettingsMenu(player, templateId);
                    else openComponentMenu(player, templateId, compName);
                });
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid number! Type a valid decimal or 'cancel'.");
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (title.startsWith("§8Sculpting: ")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            String templateId = activeTemplate.get(player.getUniqueId());
            if (templateId == null) return;

            if (event.getSlot() == 49) {
                player.closeInventory();
            } else if (event.getSlot() == 45) {
                File file = plugin.getTemplateRegistry().getTemplateFile(templateId);
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                int count = config.getConfigurationSection("components") != null ? config.getConfigurationSection("components").getKeys(false).size() : 0;
                config.set("components.new_comp_" + count + ".type", "solid");
                saveAndReload(file, config);
                openMainMenu(player, templateId);
            } else if (event.getSlot() == 48) {
                openGeneralSettingsMenu(player, templateId);
            } else if (event.getCurrentItem().getType() == Material.PURPLE_DYE) {
                String comp = org.bukkit.ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName()).replace("Edit Node: ", "");
                openComponentMenu(player, templateId, comp);
            } else if (event.getCurrentItem().getType() == Material.LIME_DYE) {
                String comp = org.bukkit.ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName()).replace("Edit: ", "");
                openComponentMenu(player, templateId, comp);
            }
        }
        // THE FIX: This block was incorrectly checking for "Editing:" instead of "General:"!
        else if (title.startsWith("§8General: ")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            
            String templateId = activeTemplate.get(player.getUniqueId());
            if (event.getSlot() == 22) { openMainMenu(player, templateId); return; }
            
            File file = plugin.getTemplateRegistry().getTemplateFile(templateId);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            if (event.getSlot() == 12) {
                String[] slots = {"head", "chest", "waist", "boots"};
                String current = config.getString("type", "chest");
                String next = "head";
                for (int i = 0; i < slots.length; i++) if (slots[i].equals(current)) next = slots[(i + 1) % slots.length];
                config.set("type", next);
                saveAndReload(file, config);
                openGeneralSettingsMenu(player, templateId);
                return;
            } else if (event.getSlot() == 13) {
                String[] rarities = {"uncommon", "rare", "epic", "legendary"};
                String current = config.getString("rarity", "epic");
                String next = "uncommon";
                for (int i = 0; i < rarities.length; i++) if (rarities[i].equals(current)) next = rarities[(i + 1) % rarities.length];
                config.set("rarity", next);
                saveAndReload(file, config);
                openGeneralSettingsMenu(player, templateId);
                return;
            } else if (event.getSlot() == 14) {
                config.set("blockbench", !config.getBoolean("blockbench", false));
                saveAndReload(file, config);
                openGeneralSettingsMenu(player, templateId);
                return;
            }

            if (event.getCurrentItem().getType() == Material.PAPER) {
                String prop = org.bukkit.ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
                handlePropertyClick(player, event, templateId, "GLOBAL", prop);
            }
        }
        else if (title.startsWith("§8Editing: ")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            String templateId = activeTemplate.get(player.getUniqueId());
            String compName = activeComponent.get(player.getUniqueId());
            if (templateId == null || compName == null) return;

            if (event.getSlot() == 22) {
                openMainMenu(player, templateId);
                return;
            }

            File file = plugin.getTemplateRegistry().getTemplateFile(templateId);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            if (event.getSlot() == 4) {
                String currentType = config.getString("components." + compName + ".type", "solid");
                String[] types = {"solid", "star", "flat_ring", "cylinder", "cone", "hourglass", "burst", "cube", "raw_node"};
                String nextType = "solid";
                for (int i = 0; i < types.length; i++) {
                    if (types[i].equals(currentType)) {
                        nextType = types[(i + 1) % types.length];
                        break;
                    }
                }
                config.set("components." + compName + ".type", nextType);
                config.set("components." + compName + ".local-offset", null); // Reset offsets to prevent visual chaos
                saveAndReload(file, config);
                openComponentMenu(player, templateId, compName);
                return;
            }

            if (event.getSlot() == 26 && event.getClick().isShiftClick() && event.getClick().isRightClick()) {
                config.set("components." + compName, null);
                saveAndReload(file, config);
                openMainMenu(player, templateId);
                return;
            }

            if (event.getCurrentItem().getType() == Material.PAPER) {
                String prop = org.bukkit.ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
                handlePropertyClick(player, event, templateId, compName, prop);
            } else if (event.getCurrentItem().getType() == Material.GLASS && event.getClick() == org.bukkit.event.inventory.ClickType.DROP) {
                player.closeInventory();
                awaitingChatInput.put(player.getUniqueId(), templateId + ":" + compName + ":hidden-faces");
                player.sendMessage("§eType hidden faces separated by commas (e.g. north,up), type 'none' to clear, or 'cancel'.");
            } else if (event.getCurrentItem().getType() == Material.CYAN_DYE && event.getClick() == org.bukkit.event.inventory.ClickType.DROP) {
                player.closeInventory();
                awaitingChatInput.put(player.getUniqueId(), templateId + ":" + compName + ":color");
                player.sendMessage("§ePlease type a Hex Color (e.g. #FF0000) in chat, or type 'cancel'.");
            }
        }
    }

    private void handlePropertyClick(Player player, InventoryClickEvent event, String templateId, String compName, String prop) {
        double shift = 0;
        if (event.getClick().isLeftClick()) shift = event.getClick().isShiftClick() ? 0.02 : 0.2;
        else if (event.getClick().isRightClick()) shift = event.getClick().isShiftClick() ? -0.02 : -0.2;
        else if (event.getClick() == org.bukkit.event.inventory.ClickType.DROP) {
            player.closeInventory();
            awaitingChatInput.put(player.getUniqueId(), templateId + ":" + compName + ":" + prop);
            player.sendMessage("§ePlease type the exact new value for §b" + prop + " §ein chat, or type 'cancel'.");
            return;
        }
        if (shift != 0) {
            applyShift(templateId, compName, prop, shift, false);
            if (compName.equals("GLOBAL")) openGeneralSettingsMenu(player, templateId);
            else openComponentMenu(player, templateId, compName);
        }
    }

    private void saveAndReload(File file, YamlConfiguration config) {
        try {
            config.save(file);
            plugin.getTemplateRegistry().loadTemplate(file);
            plugin.getSculptManager().refreshAllDummies();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void applyStringShift(Player player, String templateId, String compName, String colorStr) {
        File file = plugin.getTemplateRegistry().getTemplateFile(templateId);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = (config.contains("nodes." + compName) ? "nodes." : "components.") + compName + ".color";
        config.set(path, colorStr);
        saveAndReload(file, config);
        openComponentMenu(player, templateId, compName);
    }

    public void applyHiddenFaces(Player player, String templateId, String compName, String input) {
        File file = plugin.getTemplateRegistry().getTemplateFile(templateId);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = "components." + compName + ".hidden-faces";
        if (input.equalsIgnoreCase("none")) {
            config.set(path, null);
        } else {
            List<String> faces = new ArrayList<>();
            for (String f : input.split(",")) faces.add(f.trim().toLowerCase());
            config.set(path, faces);
        }
        saveAndReload(file, config);
        openComponentMenu(player, templateId, compName);
    }

    public void applyShift(String templateId, String compName, String prop, double shiftAmt, boolean isAbsolute) {
        File file = plugin.getTemplateRegistry().getTemplateFile(templateId);
        if (file == null) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        String basePath = "";
        if (!compName.equals("GLOBAL")) {
            basePath = (config.contains("nodes." + compName) ? "nodes." : "components.") + compName + ".";
        }
        
        if (prop.startsWith("local-offset.") || prop.startsWith("pivot-offset.") || prop.startsWith("global-offset.") || prop.startsWith("global-scale.") || prop.startsWith("global-rotation.") || prop.startsWith("scale.") || prop.startsWith("rotation.")) {
            int axis = prop.endsWith(".x") ? 0 : (prop.endsWith(".y") ? 1 : 2);
            
            String listPath = basePath + prop.substring(0, prop.length() - 2);
            List<Double> list = config.getDoubleList(listPath);
            while (list.size() < 3) list.add(0.0);
            
            list.set(axis, isAbsolute ? shiftAmt : (list.get(axis) + shiftAmt));
            config.set(listPath, list);
        } else {
            String path = basePath + prop;
            double current = config.getDouble(path, prop.contains("scale") ? 1.0 : 0.0);
            double newVal = isAbsolute ? shiftAmt : (current + shiftAmt);
            if (!prop.contains("pitch") && !prop.contains("rotation") && !prop.contains("offset") && newVal <= 0) newVal = 0.01;
            config.set(path, newVal);
        }
        
        saveAndReload(file, config);
    }
}