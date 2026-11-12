package dc.dccosmetics.wardrobe.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WardrobeTabCompleter implements TabCompleter {

    private final List<String> adminCommands = Arrays.asList("wand", "setup", "setspawn", "setnpc", "setexit", "setsaferoom", "reload", "refresh", "admin", "studio");
    private final List<String> studioSubs = Arrays.asList("cam", "category", "setcam", "preview", "exitpreview", "set", "save", "reload", "angle", "panel", "apply", "show");
    private final List<String> studioCategories = Arrays.asList("overview", "head", "chest", "waist", "boots", "teleport");
    private final List<String> studioParams = Arrays.asList("x", "y", "z", "yaw", "pitch", "scale", "forward", "right");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("dccosmetics.admin")) {
            return Collections.emptyList();
        }
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], adminCommands, completions);
        } else if (args[0].equalsIgnoreCase("studio")) {
            if (args.length == 2) {
                StringUtil.copyPartialMatches(args[1], studioSubs, completions);
            } else if (args.length == 3) {
                String sub = args[1].toLowerCase();
                if (sub.equals("cam") || sub.equals("category") || sub.equals("setcam") || sub.equals("preview")) {
                    StringUtil.copyPartialMatches(args[2], studioCategories, completions);
                } else if (sub.equals("set")) {
                    StringUtil.copyPartialMatches(args[2], studioParams, completions);
                } else if (sub.equals("angle")) {
                    StringUtil.copyPartialMatches(args[2], Arrays.asList("full-body", "head", "chest", "waist", "boots"), completions);
                } else if (sub.equals("panel")) {
                    StringUtil.copyPartialMatches(args[2], Arrays.asList("left", "modular", "info", "keybind"), completions);
                }
            }
        }

        Collections.sort(completions);
        return completions;
    }
}
