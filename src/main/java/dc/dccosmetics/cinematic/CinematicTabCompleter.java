package dc.dccosmetics.cinematic;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CinematicTabCompleter implements TabCompleter {

    private static final List<String> SUBS = Arrays.asList("datapack", "create", "fix", "setbiome");
    private static final List<String> APPLY_DP_ARGS = Arrays.asList("apply_dp=true", "apply_dp=false");
    private static final List<String> BIOMES = Arrays.asList("desolation", "crimson_rot");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("dccosmetics.admin")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], SUBS, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("datapack")) {
            List<String> worldNames = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) {
                worldNames.add(w.getName());
            }
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], worldNames, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("create")) {
                List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[2], APPLY_DP_ARGS, completions);
                return completions;
            }
            if (args[0].equalsIgnoreCase("fix")) {
                List<String> completions = new ArrayList<>();
                StringUtil.copyPartialMatches(args[2], BIOMES, completions);
                return completions;
            }
            if (args[0].equalsIgnoreCase("setbiome")) {
                return Arrays.asList("8", "12", "16");
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("setbiome")) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[3], BIOMES, completions);
            return completions;
        }

        return Collections.emptyList();
    }
}