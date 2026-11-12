package dc.dcessentials.commands;

import dc.dccosmetics.DCCosmetics;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PityTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> categories = new ArrayList<>(DCCosmetics.getInstance().getRngManager().getCategories().keySet());
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], categories, completions);
            Collections.sort(completions);
            return completions;
        }
        return Collections.emptyList();
    }
}
