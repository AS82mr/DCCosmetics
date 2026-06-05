package dc.dccosmetics.wardrobe.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WardrobeTabCompleter implements TabCompleter {

    private final List<String> adminCommands = Arrays.asList("wand", "setup", "setspawn", "setnpc", "setexit", "admin", "studio");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1 && sender.hasPermission("dccosmetics.admin")) {
            String partial = args[0].toLowerCase();
            for (String cmd : adminCommands) {
                if (cmd.startsWith(partial)) {
                    completions.add(cmd);
                }
            }
        }

        return completions;
    }
}
