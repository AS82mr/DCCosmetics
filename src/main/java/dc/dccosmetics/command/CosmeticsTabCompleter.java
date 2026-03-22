package dc.dccosmetics.command;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.CosmeticTemplate;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class CosmeticsTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("reload");
            completions.add("sound");
            completions.add("debugboots");
            if (sender.hasPermission("dccosmetics.admin")) {
                completions.add("sculpt");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("sculpt")) {
            completions.add("clear");
            completions.add("editor");
            for (CosmeticTemplate template : DCCosmetics.getInstance().getTemplateRegistry().getAllTemplates()) {
                completions.add(template.getId());
            }
        }

        // Filter results based on what the user is typing
        String current = args[args.length - 1].toLowerCase();
        List<String> filtered = new ArrayList<>();
        for (String c : completions) {
            if (c.toLowerCase().startsWith(current)) filtered.add(c);
        }
        return filtered;
    }
}