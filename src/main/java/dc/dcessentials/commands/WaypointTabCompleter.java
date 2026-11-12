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

public class WaypointTabCompleter implements TabCompleter {

    private static final List<String> ROOT_SUBS = Arrays.asList("toggle", "clear", "next");
    private static final List<String> NPC_IDS = Arrays.asList(
            "corvus", "kristoph", "lyra", "echo", "vane", "jax", "sylas", "ronald",
            "hesper", "balthazar", "emperor", "prisoner404", "nadia", "malakor", "kael",
            "thorne", "vael", "ignis", "theta", "gideon", "valerio", "shade", "sorsha",
            "garrick", "twitch", "brutus", "finch", "rats", "roxy", "slick", "vector", "nyx", "krux"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> candidates = new ArrayList<>(ROOT_SUBS);
            candidates.addAll(NPC_IDS);
            if (sender.hasPermission("dragoncote.admin")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    candidates.add(p.getName());
                }
            }
            StringUtil.copyPartialMatches(args[0], candidates, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 2 && sender.hasPermission("dragoncote.admin")) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                completions.add("clear");
                return completions;
            }
        }

        return Collections.emptyList();
    }
}
