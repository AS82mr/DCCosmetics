package dc.dcessentials.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class KeystoneTabCompleter implements TabCompleter {

    private static final List<String> USER_SUBS = Arrays.asList("gui", "status");
    private static final List<String> ADMIN_SUBS = Arrays.asList(
            "gui", "status", "charge", "set", "take", "remove", "reset",
            "clearheat", "tier", "heat", "microcharge", "debugspend", "debugtp");
    private static final List<String> TIERS = Arrays.asList("NONE", "RARE", "EPIC", "LEGENDARY");
    private static final List<String> AMOUNTS = Arrays.asList("0", "10", "25", "50", "100", "200");
    private static final List<String> DELTAS = Arrays.asList("-50", "-25", "-10", "10", "25", "50", "250");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        boolean isAdmin = sender.hasPermission("dragoncote.admin") || sender.hasPermission("dcessentials.admin") || sender.isOp();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], isAdmin ? ADMIN_SUBS : USER_SUBS, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 2 && isAdmin) {
            String sub = args[0].toLowerCase();
            if (sub.equals("charge") || sub.equals("addenergy") || sub.equals("set") || sub.equals("setenergy")
                    || sub.equals("take") || sub.equals("remove") || sub.equals("deduct")
                    || sub.equals("reset") || sub.equals("clear") || sub.equals("clearenergy")
                    || sub.equals("clearheat") || sub.equals("resetheat")
                    || sub.equals("tier") || sub.equals("settier") || sub.equals("heat")
                    || sub.equals("microcharge") || sub.equals("micro")) {
                List<String> playerNames = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    playerNames.add(p.getName());
                }
                StringUtil.copyPartialMatches(args[1], playerNames, completions);
                Collections.sort(completions);
                return completions;
            }
            if (sub.equals("debugspend")) {
                StringUtil.copyPartialMatches(args[1], Arrays.asList("WARP", "ABILITY"), completions);
                return completions;
            }
        }

        if (args.length == 3 && isAdmin) {
            String sub = args[0].toLowerCase();
            if (sub.equals("charge") || sub.equals("addenergy") || sub.equals("set") || sub.equals("setenergy")
                    || sub.equals("take") || sub.equals("remove") || sub.equals("deduct")) {
                StringUtil.copyPartialMatches(args[2], AMOUNTS, completions);
                return completions;
            }
            if (sub.equals("heat") || sub.equals("microcharge") || sub.equals("micro")) {
                StringUtil.copyPartialMatches(args[2], DELTAS, completions);
                return completions;
            }
            if (sub.equals("tier") || sub.equals("settier")) {
                StringUtil.copyPartialMatches(args[2], TIERS, completions);
                Collections.sort(completions);
                return completions;
            }
        }

        return Collections.emptyList();
    }
}
