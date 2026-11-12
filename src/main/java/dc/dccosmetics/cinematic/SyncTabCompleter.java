package dc.dccosmetics.cinematic;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SyncTabCompleter implements TabCompleter {

    private static final List<String> SUBS = Arrays.asList("wand", "push", "status", "discard", "stagewe", "snow");
    private static final List<String> SNOW_OPTS = Arrays.asList("on", "off");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("dccosmetics.admin")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], SUBS, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("snow")) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], SNOW_OPTS, completions);
            return completions;
        }

        return Collections.emptyList();
    }
}