package dc.dcessentials.commands;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.ItemModule;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EmperorItemsCommand implements CommandExecutor, TabCompleter {

    private final ItemModule itemModule;

    public EmperorItemsCommand(ItemModule itemModule) {
        this.itemModule = itemModule;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dragoncote.admin") && !sender.hasPermission("dcessentials.admin")) {
            DCMessage.sendError(sender, "You do not have permission to access Emperor Artifacts.");
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                DCMessage.sendError(sender, "Player '" + args[1] + "' is not online.");
                return true;
            }

            String id = args[2].toLowerCase();
            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException ignored) {}
            }

            String scrollId = resolveScrollAlias(id);
            ItemStack item = itemModule.getScrollRegistry().createScrollItem(scrollId, amount);

            if (item == null) {
                // Try from item registry
                item = itemModule.getItemRegistry().createItemStack(id, amount);
            }

            if (item == null) {
                DCMessage.sendError(sender, "Unknown item or scroll ID! Valid IDs: mace_slip, slayer_15, slayer_20, slayer_35, stasis_core");
                return true;
            }

            target.getInventory().addItem(item).values().forEach(drop ->
                    target.getWorld().dropItemNaturally(target.getLocation(), drop));

            String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : id;
            DCMessage.sendGeneral(sender, "Delivered &#FFFFFF" + amount + "x " + itemName + " &#0CBF93to &#FFFFFF" + target.getName() + "&#0CBF93!");
            if (sender instanceof Player p) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.2f);
            }
            return true;
        }

        if (sender instanceof Player player) {
            itemModule.getItemBrowserGUI().open(player);
        } else {
            DCMessage.sendInfo(sender, "Usage: /emperoritems give <player> <item_id> [amount]");
        }
        return true;
    }

    private String resolveScrollAlias(String input) {
        switch (input.toLowerCase()) {
            case "mace_slip":
            case "mace":
                return "chrono_slip";
            case "slayer_15":
            case "slayer_rare":
                return "slayer_rare";
            case "slayer_20":
            case "slayer_epic":
                return "slayer_epic";
            case "slayer_35":
            case "slayer_mythic":
                return "slayer_mythic";
            case "stasis_core":
            case "stasis":
            case "freeze":
                return "stasis_core";
            default:
                return input;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("dcessentials.admin") && !sender.hasPermission("dragoncote.admin")
                && !sender.hasPermission("dccosmetics.admin") && !sender.isOp()) {
            return Collections.emptyList();
        }
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            org.bukkit.util.StringUtil.copyPartialMatches(args[0], Collections.singletonList("give"), completions);
            Collections.sort(completions);
            return completions;
        }
        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    players.add(p.getName());
                }
                org.bukkit.util.StringUtil.copyPartialMatches(args[1], players, completions);
                Collections.sort(completions);
                return completions;
            }
            if (args.length == 3) {
                java.util.Set<String> ids = new java.util.LinkedHashSet<>(Arrays.asList(
                        "mace_slip", "slayer_15", "slayer_20", "slayer_35", "stasis_core"
                ));
                dc.dccosmetics.DCCosmetics plugin = dc.dccosmetics.DCCosmetics.getInstance();
                if (plugin != null && plugin.getItemModule() != null) {
                    var itemReg = plugin.getItemModule().getItemRegistry();
                    if (itemReg != null) {
                        for (dc.dcessentials.modules.items.model.DCEItem it : itemReg.getItems()) {
                            ids.add(it.getId());
                        }
                    }
                    var scrollReg = plugin.getItemModule().getScrollRegistry();
                    if (scrollReg != null) {
                        for (dc.dcessentials.modules.items.scroll.ScrollModel sc : scrollReg.getScrolls()) {
                            ids.add(sc.getId());
                        }
                    }
                }
                org.bukkit.util.StringUtil.copyPartialMatches(args[2], ids, completions);
                Collections.sort(completions);
                return completions;
            }
            if (args.length == 4) {
                org.bukkit.util.StringUtil.copyPartialMatches(args[3], Arrays.asList("1", "4", "16", "64"), completions);
                return completions;
            }
        }
        return Collections.emptyList();
    }
}
