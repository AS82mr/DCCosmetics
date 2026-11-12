package dc.dcessentials.commands;

import dc.dccosmetics.DCCosmetics;
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

public class RngHookTabCompleter implements TabCompleter {

    private static final List<String> SOURCES = Arrays.asList("combat", "casino");
    private static final List<String> COMMON_BOOSTS = Arrays.asList("0", "10", "25", "50", "100");
    private static final List<String> COMMON_XP = Arrays.asList("1", "5", "10", "25", "50", "100");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("dcessentials.admin")) return Collections.emptyList();

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> playerNames = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                playerNames.add(p.getName());
            }
            StringUtil.copyPartialMatches(args[0], playerNames, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 2) {
            List<String> cats = new ArrayList<>(DCCosmetics.getInstance().getRngManager().getCategories().keySet());
            StringUtil.copyPartialMatches(args[1], cats, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 3) {
            StringUtil.copyPartialMatches(args[2], COMMON_BOOSTS, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 4) {
            StringUtil.copyPartialMatches(args[3], COMMON_XP, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 5) {
            StringUtil.copyPartialMatches(args[4], SOURCES, completions);
            Collections.sort(completions);
            return completions;
        }

        return Collections.emptyList();
    }
}
