package dc.dcessentials.commands;

import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.glow.GlowManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class GlowCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dm open glow " + player.getName());
            } else {
                DCMessage.sendHelp(sender, "Usage: /glow <color|off> [player]");
            }
            return true;
        }

        boolean isAdmin = !(sender instanceof Player) || sender.isOp() || sender.hasPermission("dragoncote.admin");

        String sub = args[0].toLowerCase();
        if (sub.equals("off") || sub.equals("clear") || sub.equals("remove") || sub.equals("unset")) {
            Player target = (args.length > 1 && isAdmin)
                    ? Bukkit.getPlayer(args[1]) : (sender instanceof Player p ? p : null);
            if (target == null) {
                DCMessage.sendError(sender, "Player not found.");
                return true;
            }
            GlowManager.getInstance().removeGlow(target);
            DCMessage.sendWarning(target, "Your glow has been disabled.");
            if (sender != target) {
                DCMessage.sendGeneral(sender, "Disabled glow for " + target.getName() + ".");
            }
            return true;
        }

        GlowManager.GlowColor gc = GlowManager.GlowColor.fromId(sub);
        if (gc == null) {
            DCMessage.sendError(sender, "Unknown glow color: " + sub);
            return true;
        }

        Player target = (args.length > 1 && isAdmin)
                ? Bukkit.getPlayer(args[1]) : (sender instanceof Player p ? p : null);
        if (target == null) {
            DCMessage.sendError(sender, "Player not found.");
            return true;
        }

        if (sender == target && !sender.hasPermission("glow." + gc.getId()) && !sender.hasPermission("glow.all")) {
            DCMessage.sendError(sender, "You do not have permission to use the " + gc.getDisplayName() + " glow!");
            return true;
        }

        GlowManager.getInstance().setGlow(target, gc.getId());
        DCMessage.sendGeneral(target, "Equipped glow: " + gc.getDisplayName() + "!");
        if (sender != target) {
            DCMessage.sendGeneral(sender, "Applied " + gc.getDisplayName() + " glow to " + target.getName() + ".");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> candidates = new ArrayList<>();
            candidates.add("off");
            for (GlowManager.GlowColor gc : GlowManager.GlowColor.values()) {
                if (sender.hasPermission("glow." + gc.getId()) || sender.hasPermission("glow.all") || sender.hasPermission("dragoncote.admin")) {
                    candidates.add(gc.getId());
                }
            }
            org.bukkit.util.StringUtil.copyPartialMatches(args[0], candidates, completions);
            java.util.Collections.sort(completions);
            return completions;
        }
        if (args.length == 2 && (sender.hasPermission("dragoncote.admin") || sender.hasPermission("dcessentials.admin") || sender.isOp())) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            org.bukkit.util.StringUtil.copyPartialMatches(args[1], names, completions);
            java.util.Collections.sort(completions);
            return completions;
        }
        return java.util.Collections.emptyList();
    }
}
