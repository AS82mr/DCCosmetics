package dc.dcessentials.modules.npc.gui;

import dc.dcessentials.modules.npc.NpcDialogueEngine;
import dc.dcessentials.modules.npc.NpcRegistry;
import dc.dcessentials.modules.npc.model.NpcCharacter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.command.TabCompleter;
import java.util.ArrayList;
import java.util.List;

/**
 * Visual personnel dossiers and storyline timeline GUI.
 */
public class DossierGUI implements CommandExecutor, TabCompleter, Listener {

    private final NpcRegistry registry;
    private final NpcDialogueEngine dialogueEngine;

    public DossierGUI(NpcRegistry registry, NpcDialogueEngine dialogueEngine) {
        this.registry = registry;
        this.dialogueEngine = dialogueEngine;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can open lore dossiers.");
            return true;
        }

        Player player = (Player) sender;
        int sector = 1;
        if (args.length > 0) {
            try {
                sector = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        if (label.equalsIgnoreCase("timeline")) {
            openTimeline(player);
        } else {
            openDossier(player, sector);
        }
        return true;
    }

    public void openDossier(Player player, int sector) {
        String title = "§8Personnel Dossier | Sector " + sector;
        Inventory inv = Bukkit.createInventory(null, 54, title);

        int slot = 0;
        for (NpcCharacter c : registry.getAllCharacters()) {
            if (c.getSector() != sector) continue;
            if (slot >= 45) break;

            ItemStack item = new ItemStack(c.getIcon() != null ? c.getIcon() : Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§b§l" + c.getShortName() + " §7- §f" + c.getRole());
                List<String> lore = new ArrayList<>();
                lore.add("§8ID: " + c.getId());
                lore.add("§7Faction: §f" + c.getFaction());
                lore.add("§7District: §e" + c.getZoneId());
                lore.add("§f");
                lore.add("§7\"§o" + c.getQuote() + "§7\"");
                lore.add("§f");
                lore.add("§e▶ Click to lock Navigational Waypoint");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        // Navigation row
        for (int i = 1; i <= 4; i++) {
            ItemStack tab = new ItemStack(i == sector ? Material.LIME_DYE : Material.GRAY_DYE);
            ItemMeta tMeta = tab.getItemMeta();
            if (tMeta != null) {
                tMeta.setDisplayName((i == sector ? "§a§lSector " : "§7Sector ") + i);
                tab.setItemMeta(tMeta);
            }
            inv.setItem(45 + (i - 1), tab);
        }

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = close.getItemMeta();
        if (cMeta != null) {
            cMeta.setDisplayName("§c§lClose");
            close.setItemMeta(cMeta);
        }
        inv.setItem(53, close);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
    }

    public void openTimeline(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8Syndicate Quest Timeline");
        int currentIdx = dialogueEngine.getPlayerTimelineIndex(player);
        List<String> timeline = registry.getTimelineOrder();

        for (int i = 0; i < Math.min(timeline.size(), 45); i++) {
            String node = timeline.get(i);
            String[] parts = node.split(":");
            String charId = parts[0];
            int stageNum = Integer.parseInt(parts[1]);

            NpcCharacter c = registry.getCharacter(charId);
            String charName = c != null ? c.getShortName() : charId;

            ItemStack item;
            int nodeNum = i + 1;
            if (nodeNum < currentIdx) {
                item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            } else if (nodeNum == currentIdx) {
                item = new ItemStack(Material.BEACON);
            } else {
                item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (nodeNum < currentIdx) {
                    meta.setDisplayName("§a§l✔ Chapter " + nodeNum + ": " + charName);
                    List<String> lore = new ArrayList<>();
                    lore.add("§7Status: §aCompleted");
                    meta.setLore(lore);
                } else if (nodeNum == currentIdx) {
                    meta.setDisplayName("§e§l▶ Active Chapter " + nodeNum + ": " + charName);
                    List<String> lore = new ArrayList<>();
                    lore.add("§7Status: §eIn Progress");
                    lore.add("§7Objective: Speak with §f" + charName + " §7in district §b" + (c != null ? c.getZoneId() : "Sanctuary"));
                    lore.add("§eClick to lock waypoint!");
                    meta.setLore(lore);
                } else {
                    meta.setDisplayName("§7Chapter " + nodeNum + ": Locked");
                }
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.startsWith("§8Personnel Dossier") && !title.equals("§8Syndicate Quest Timeline")) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 53) {
            player.closeInventory();
            return;
        }

        if (title.startsWith("§8Personnel Dossier")) {
            if (slot >= 45 && slot <= 48) {
                int sector = slot - 45 + 1;
                openDossier(player, sector);
                return;
            }

            ItemStack item = event.getCurrentItem();
            if (item != null && item.hasItemMeta() && item.getItemMeta().getLore() != null) {
                for (String line : item.getItemMeta().getLore()) {
                    if (line.startsWith("§8ID: ")) {
                        String id = line.substring("§8ID: ".length()).trim();
                        player.closeInventory();
                        dialogueEngine.trackNpcWaypoint(player, id);
                        break;
                    }
                }
            }
        } else if (title.equals("§8Syndicate Quest Timeline")) {
            int currentIdx = dialogueEngine.getPlayerTimelineIndex(player);
            List<String> timeline = registry.getTimelineOrder();
            if (currentIdx <= timeline.size()) {
                String node = timeline.get(currentIdx - 1);
                String charId = node.split(":")[0];
                player.closeInventory();
                dialogueEngine.trackNpcWaypoint(player, charId);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (command.getName().equalsIgnoreCase("timeline") || alias.equalsIgnoreCase("timeline")) {
                return java.util.Arrays.asList("view", "reset").stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase()))
                        .collect(java.util.stream.Collectors.toList());
            } else {
                return java.util.Arrays.asList("1", "2", "3", "4").stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase()))
                        .collect(java.util.stream.Collectors.toList());
            }
        }
        return java.util.Collections.emptyList();
    }
}
