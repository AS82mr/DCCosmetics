package dc.dcessentials.commands;

import dc.dcessentials.modules.event.EventManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EventTabCompleter implements TabCompleter {

    private final EventManager eventManager;

    public EventTabCompleter(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>(Arrays.asList("page", "schedule", "spawn"));
            if (sender.hasPermission("dcessentials.admin")) {
                subcommands.addAll(Arrays.asList("list", "swap", "time", "start", "stop", "reload"));
            }
            StringUtil.copyPartialMatches(args[0], subcommands, completions);
        } else if (args.length == 2 && sender.hasPermission("dcessentials.admin")) {
            String sub = args[0].toLowerCase();
            if (sub.equals("swap")) {
                List<String> options = new ArrayList<>();
                options.addAll(eventManager.getWorldCycleEvents().keySet());
                options.addAll(Arrays.asList("spawn_main", "spawn_bw", "spawn_snow"));
                StringUtil.copyPartialMatches(args[1], options, completions);
            } else if (sub.equals("start") || sub.equals("stop")) {
                List<String> options = new ArrayList<>(eventManager.getMiniEvents().keySet());
                if (sub.equals("stop")) options.add("all");
                StringUtil.copyPartialMatches(args[1], options, completions);
            } else if (sub.equals("time")) {
                StringUtil.copyPartialMatches(args[1], Arrays.asList("10", "30", "60", "300", "1800", "3600"), completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}
