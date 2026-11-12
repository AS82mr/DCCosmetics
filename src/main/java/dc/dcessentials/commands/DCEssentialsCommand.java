package dc.dcessentials.commands;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.AdaptHook;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.ItemModule;
import dc.dcessentials.modules.items.model.DCEItem;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class DCEssentialsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
            DCMessage.sendGeneral(sender, "Dragoncote Essentials &7(v2.0)");
            sender.sendMessage(ColorUtil.colorize("  &8• &#38BDF8Cosmetics & Hologram Engine &8- &#0CBF93Active"));
            sender.sendMessage(ColorUtil.colorize("  &8• &#38BDF83D Navigational Waypoint &8- &#0CBF93Active"));
            sender.sendMessage(ColorUtil.colorize("  &8• &#38BDF8Temporal Keystone Matrix &8- &#0CBF93Active"));
            sender.sendMessage(ColorUtil.colorize("  &8• &#38BDF8Unified RNG Meter Engine &8- &#0CBF93Active"));
            sender.sendMessage(ColorUtil.colorize("  &8• &#38BDF8Deterministic Pity Core &8- &#0CBF93Active"));
            sender.sendMessage(ColorUtil.colorize("  &8• &#38BDF8SQL Async Database Bridge &8- &#0CBF93Active"));
            sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("reload")) {
            if (!sender.hasPermission("dragoncote.admin") && !sender.hasPermission("dcessentials.admin")) {
                DCMessage.sendError(sender, "You do not have permission to execute this command.");
                return true;
            }
            DCCosmetics.getInstance().reloadConfigs();
            DCMessage.sendGeneral(sender, "Reloaded all configurations, master items (hierarchical), elixirs, world cycles, drop tables, NPC zones, waypoints, and pity milestones.");
            return true;
        }

        if (sub.equals("event") || sub.equals("events") || sub.equals("calendar")) {
            String[] forwardedArgs = (args.length > 1) ? java.util.Arrays.copyOfRange(args, 1, args.length) : new String[0];
            org.bukkit.command.PluginCommand cmd = DCCosmetics.getInstance().getCommand("events");
            if (cmd != null) {
                return cmd.execute(sender, label, forwardedArgs);
            }
            return true;
        }

        if (sub.equals("emperor") || sub.equals("emp") || sub.equals("boss")) {
            String[] forwardedArgs = (args.length > 1) ? java.util.Arrays.copyOfRange(args, 1, args.length) : new String[0];
            var empCmd = DCCosmetics.getInstance().getEmperorCommand();
            if (empCmd != null) {
                return empCmd.onCommand(sender, command, "emperor", forwardedArgs);
            }
            DCMessage.sendError(sender, "EmperorBossManager is not loaded.");
            return true;
        }

        if (sub.equals("givexp") || sub.equals("addxp") || sub.equals("xp")) {
            if (!sender.hasPermission("dragoncote.admin")) {
                DCMessage.sendError(sender, "You do not have permission to execute this command.");
                return true;
            }
            if (args.length < 3) {
                DCMessage.sendHelp(sender, "Usage: &#FFFFFF/dce givexp <player> <amount> [skillName|all]");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                DCMessage.sendError(sender, "Player '" + args[1] + "' not found or offline.");
                return true;
            }

            double amount;
            try {
                amount = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                DCMessage.sendError(sender, "Invalid XP amount: " + args[2]);
                return true;
            }

            String skillTarget = (args.length >= 4) ? args[3] : "all";
            boolean success = AdaptHook.giveXp(target, skillTarget, amount);
            if (success) {
                DCMessage.sendGeneral(sender, "Successfully awarded &#FFFFFF" + (int) amount + " " + (skillTarget.equalsIgnoreCase("all") ? "Mastery" : skillTarget) + " XP &#0CBF93directly to &#FFFFFF" + target.getName() + "&#0CBF93.");
            } else {
                DCMessage.sendError(sender, "Failed to grant XP to " + target.getName() + ". Check console logs.");
            }
            return true;
        }

        if (sub.equals("waypoint") && args.length >= 2) {
            // Forward to cosmetics waypoint command handler
            return DCCosmetics.getInstance().getCommand("cosmetics").execute(sender, label, args);
        }

        if (sub.equals("selectdrop") && args.length >= 4) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null) {
                String cat = args[2].toLowerCase();
                String dropId = args[3].toLowerCase();
                String curSelected = DCCosmetics.getInstance().getRngManager().getSelectedDrop(target.getUniqueId(), cat);

                if (dropId.equalsIgnoreCase(curSelected) || dropId.equalsIgnoreCase("none")) {
                    // DESELECT
                    DCCosmetics.getInstance().getRngManager().setSelectedDrop(target.getUniqueId(), cat, null);
                    target.playSound(target.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 0.8f);
                    DCMessage.sendGeneral(target, "Deselected active target for category &#FFFFFF" + cat + "&#0CBF93. Your accumulated category XP remains safe in your pool!");
                } else {
                    // SELECT
                    DCCosmetics.getInstance().getRngManager().setSelectedDrop(target.getUniqueId(), cat, dropId);
                    target.playSound(target.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
                    DCMessage.sendGeneral(target, "Selected &#FFFFFF" + dropId + " &#0CBF93as active guaranteed RNG goal for category &#FFFFFF" + cat + "&#0CBF93.");
                }
            }
            return true;
        }

        if (sub.equals("items") || sub.equals("item") || sub.equals("itemstat")) {
            ItemModule mod = DCCosmetics.getInstance().getItemModule();
            if (mod == null) {
                DCMessage.sendError(sender, "ItemModule is not loaded.");
                return true;
            }

            // Subcommand: /dce items scroll give <player> <scroll_id> [amount]
            if (args.length >= 2 && args[1].equalsIgnoreCase("scroll")) {
                if (args.length >= 5 && args[2].equalsIgnoreCase("give")) {
                    if (!sender.hasPermission("dragoncote.admin") && !sender.hasPermission("dcessentials.admin")) {
                        DCMessage.sendError(sender, "You do not have permission to execute this command.");
                        return true;
                    }
                    Player target = Bukkit.getPlayer(args[3]);
                    if (target == null) {
                        DCMessage.sendError(sender, "Player '" + args[3] + "' not found or offline.");
                        return true;
                    }
                    String scrollId = args[4];
                    int amount = 1;
                    if (args.length >= 6) {
                        try { amount = Integer.parseInt(args[5]); } catch (NumberFormatException ignored) {}
                    }

                    ItemStack scrollItem = mod.getScrollRegistry().createScrollItem(scrollId, amount);
                    if (scrollItem != null) {
                        target.getInventory().addItem(scrollItem).values().forEach(drop ->
                                target.getWorld().dropItemNaturally(target.getLocation(), drop));
                        DCMessage.sendGeneral(sender, "Delivered &#FFFFFF" + amount + "x " + scrollId + " &#0CBF93to &#FFFFFF" + target.getName() + "&#0CBF93.");
                    } else {
                        DCMessage.sendError(sender, "Scroll/Infusion '" + scrollId + "' not found in scrolls.yml.");
                    }
                    return true;
                } else {
                    DCMessage.sendHelp(sender, "Usage: &#FFFFFF/dce items scroll give <player> <scroll_id> [amount]");
                    return true;
                }
            }

            if (sender instanceof Player p) {
                mod.getItemBrowserGUI().open(p);
            } else {
                DCMessage.sendError(sender, "This command can only be executed by players.");
            }
            return true;
        }

        if (sub.equals("editor")) {
            if (sender instanceof Player p) {
                ItemModule mod = DCCosmetics.getInstance().getItemModule();
                if (mod == null) {
                    DCMessage.sendError(sender, "ItemModule is not loaded.");
                    return true;
                }

                if (args.length >= 2) {
                    DCEItem item = mod.getItemRegistry().getItem(args[1]);
                    if (item != null) {
                        mod.getItemEditorGUI().open(p, item);
                    } else {
                        DCMessage.sendError(sender, "Item '" + args[1] + "' does not exist in the DCE registry.");
                    }
                } else {
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    if (mod.getItemRegistry().isDCEItem(hand)) {
                        String id = mod.getItemRegistry().getItemId(hand);
                        DCEItem item = mod.getItemRegistry().getItem(id);
                        if (item != null) {
                            mod.getItemEditorGUI().open(p, item);
                            return true;
                        }
                    }
                    mod.getItemBrowserGUI().open(p);
                }
            } else {
                DCMessage.sendError(sender, "This command can only be executed by players.");
            }
            return true;
        }

        if (sub.equals("import")) {
            if (sender instanceof Player p) {
                ItemModule mod = DCCosmetics.getInstance().getItemModule();
                if (mod != null) {
                    mod.getMassImportGUI().open(p);
                } else {
                    DCMessage.sendError(sender, "ItemModule is not loaded.");
                }
            } else {
                DCMessage.sendError(sender, "This command can only be executed by players.");
            }
            return true;
        }

        if (sub.equals("give") && args.length >= 3) {
            if (!sender.hasPermission("dragoncote.admin") && !sender.hasPermission("dcessentials.admin")) {
                DCMessage.sendError(sender, "You do not have permission to execute this command.");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                DCMessage.sendError(sender, "Player '" + args[1] + "' not found or offline.");
                return true;
            }
            String itemId = args[2];
            int amount = 1;
            if (args.length >= 4) {
                try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
            }

            ItemModule mod = DCCosmetics.getInstance().getItemModule();
            if (mod == null) {
                DCMessage.sendError(sender, "ItemModule is not loaded.");
                return true;
            }

            ItemStack stack = mod.getItemRegistry().createItemStack(itemId, amount);
            if (stack == null) {
                // Try scroll registry as fallback
                stack = mod.getScrollRegistry().createScrollItem(itemId, amount);
            }

            if (stack != null) {
                target.getInventory().addItem(stack).values().forEach(drop ->
                        target.getWorld().dropItemNaturally(target.getLocation(), drop));
                DCMessage.sendGeneral(sender, "Delivered &#FFFFFF" + amount + "x " + itemId + " &#0CBF93to &#FFFFFF" + target.getName() + "&#0CBF93.");
            } else {
                DCMessage.sendError(sender, "Item or scroll '" + itemId + "' not found.");
            }
            return true;
        }

        if (sub.equals("update") && args.length >= 2) {
            if (!sender.hasPermission("dragoncote.admin") && !sender.hasPermission("dcessentials.admin")) {
                DCMessage.sendError(sender, "You do not have permission to execute this command.");
                return true;
            }
            ItemModule mod = DCCosmetics.getInstance().getItemModule();
            if (mod == null) {
                DCMessage.sendError(sender, "ItemModule is not loaded.");
                return true;
            }

            DCEItem item = mod.getItemRegistry().getItem(args[1]);
            if (item == null) {
                DCMessage.sendError(sender, "Item '" + args[1] + "' does not exist.");
                return true;
            }

            item.bumpVersion();
            mod.getItemRegistry().registerItem(item);

            int updated = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                for (ItemStack invItem : p.getInventory().getContents()) {
                    if (invItem != null && mod.getItemRegistry().isDCEItem(invItem) &&
                            mod.getItemRegistry().getItemId(invItem).equalsIgnoreCase(item.getId())) {
                        if (mod.getItemRegistry().updateItemToLatest(invItem)) {
                            updated++;
                        }
                    }
                }
            }
            DCMessage.sendGeneral(sender, "Bumped &#FFFFFF" + item.getId() + " &#0CBF93to v" + item.getVersion() + " and live-synced &#FFFFFF" + updated + " &#0CBF93items in player inventories.");
            return true;
        }

        if (sub.equals("gui")) {
            if (sender instanceof Player p) {
                p.performCommand("keystone");
            }
            return true;
        }

        if (sub.equals("inspect")) {
            if (sender instanceof Player p) {
                ItemModule mod = DCCosmetics.getInstance().getItemModule();
                if (mod != null) {
                    dc.dcessentials.modules.items.gui.ItemInspectGUI.open(p, mod.getItemRegistry(), mod.getScrollRegistry());
                } else {
                    DCMessage.sendError(p, "ItemModule is not loaded.");
                }
            } else {
                DCMessage.sendError(sender, "This command can only be executed by players.");
            }
            return true;
        }

        if (sub.equals("tester") || sub.equals("dummy")) {
            if (!sender.hasPermission("dragoncote.admin") && !sender.hasPermission("dcessentials.admin")) {
                DCMessage.sendError(sender, "You do not have permission to execute this command.");
                return true;
            }
            if (!(sender instanceof Player p)) {
                DCMessage.sendError(sender, "This command can only be executed by players.");
                return true;
            }
            ItemModule mod = DCCosmetics.getInstance().getItemModule();
            if (mod != null && mod.getStatTesterManager() != null) {
                String action = (args.length >= 2) ? args[1].toLowerCase() : "spawn";
                if (action.equals("clear") || action.equals("remove")) {
                    int removed = mod.getStatTesterManager().clearDummies(p.getWorld());
                    DCMessage.sendGeneral(p, "Removed &#FFFFFF" + removed + " &#0CBF93combat stat testing dummies.");
                } else {
                    mod.getStatTesterManager().spawnDummy(p.getLocation());
                    DCMessage.sendGeneral(p, "Spawned &#FFFFFFCombat Stat Dummy &#0CBF93at your location! Attack it to inspect real-time damage telemetry and stat procs.");
                }
            } else {
                DCMessage.sendError(p, "ItemModule or StatTesterManager is not loaded.");
            }
            return true;
        }

        if (sub.equals("unbind") || sub.equals("strip")) {
            if (sender instanceof Player p) {
                if (!p.hasPermission("dragoncote.admin") && !p.hasPermission("dcessentials.admin")) {
                    DCMessage.sendError(p, "You do not have permission to execute this command.");
                    return true;
                }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir() || !hand.hasItemMeta()) {
                    DCMessage.sendError(p, "You must hold an item in your main hand.");
                    return true;
                }
                ItemMeta meta = hand.getItemMeta();
                var pdc = meta.getPersistentDataContainer();
                pdc.remove(new NamespacedKey(DCCosmetics.getInstance(), "dce_item_id"));
                pdc.remove(new NamespacedKey(DCCosmetics.getInstance(), "dce_item_version"));
                pdc.remove(new NamespacedKey(DCCosmetics.getInstance(), "dce_item_uuid"));
                pdc.remove(new NamespacedKey(DCCosmetics.getInstance(), "dce_category"));
                pdc.remove(new NamespacedKey(DCCosmetics.getInstance(), "dce_rarity"));
                hand.setItemMeta(meta);
                DCMessage.sendGeneral(p, "Successfully un-bound and stripped all DCE tags from held item.");
            } else {
                DCMessage.sendError(sender, "This command can only be executed by players.");
            }
            return true;
        }

        DCMessage.sendHelp(sender, "Usage: &#FFFFFF/dce [items|editor|import|tester|give|update|unbind|emperor|events|status|reload]");
        return true;
    }
}
