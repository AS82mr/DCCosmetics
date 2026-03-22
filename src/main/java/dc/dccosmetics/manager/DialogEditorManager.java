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
        }

        inv.setItem(45, createItem(Material.EMERALD, "§a+ Add Component", "§7Adds a new solid component."));
        inv.setItem(49, createItem(Material.BARRIER, "§cClose Editor"));

        if (!player.getOpenInventory().getTitle().equals("§8Sculpting: " + templateId)) {
            player.openInventory(inv);
        }
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
        String path = "components." + compName;

        if (!config.contains(path)) {
            openMainMenu(player, templateId);
            return;
        }

        String type = config.getString(path + ".type", "solid");
        inv.setItem(4, createItem(Material.NAME_TAG, "§eComponent: " + compName, "§7Type: " + type, "", "§fClick to change type!"));

        int slot = 9;
        inv.setItem(slot++, createPropertyItem("pitch", config.getDouble(path + ".pitch", 90.0)));
        inv.setItem(slot++, createPropertyItem("local-offset.x", getVectorVal(config, path + ".local-offset", 0)));
        inv.setItem(slot++, createPropertyItem("local-offset.y", getVectorVal(config, path + ".local-offset", 1)));
        inv.setItem(slot++, createPropertyItem("local-offset.z", getVectorVal(config, path + ".local-offset", 2)));

        if (type.equals("solid")) {
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

        if (!player.getOpenInventory().getTitle().equals("§8Editing: " + compName)) {
            player.openInventory(inv);
        }
    }

    private double getVectorVal(YamlConfiguration config, String path, int index) {
        List<Double> list = config.getDoubleList(path);
        if (list.size() > index) return list.get(index);
        return 0.0;
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

            try {
                double val = Double.parseDouble(input);
                awaitingChatInput.remove(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    applyShift(templateId, compName, prop, "", val, true);
                    openComponentMenu(player, templateId, compName);
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
            } else if (event.getCurrentItem().getType() == Material.LIME_DYE) {
                String comp = org.bukkit.ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName()).replace("Edit: ", "");
                openComponentMenu(player, templateId, comp);
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
                String[] types = {"solid", "star", "flat_ring", "cylinder", "cone", "hourglass", "burst"};
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
                    applyShift(templateId, compName, prop, "", shift, false);
                    openComponentMenu(player, templateId, compName);
                }
            }
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
    
    public void applyShift(String templateId, String compName, String prop, String extra, double shiftAmt) {
        applyShift(templateId, compName, prop, extra, shiftAmt, false);
    }

    public void applyShift(String templateId, String compName, String prop, String extra, double shiftAmt, boolean isAbsolute) {
        File file = plugin.getTemplateRegistry().getTemplateFile(templateId);
        if (file == null) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        if (prop.startsWith("local-offset.")) {
            int axis = prop.endsWith(".x") ? 0 : (prop.endsWith(".y") ? 1 : 2);
            List<Double> list = config.getDoubleList("components." + compName + ".local-offset");
            while (list.size() < 3) list.add(0.0);
            
            list.set(axis, isAbsolute ? shiftAmt : (list.get(axis) + shiftAmt));
            config.set("components." + compName + ".local-offset", list);
        } else {
            String path = "components." + compName + "." + prop;
            double current = config.getDouble(path, 0.0);
            double newVal = isAbsolute ? shiftAmt : (current + shiftAmt);
            if (!prop.contains("pitch") && newVal <= 0) newVal = 0.01;
            config.set(path, newVal);
        }
        
        saveAndReload(file, config);
    }
}