package dc.dccosmetics.command;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.CosmeticTemplate;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CosmeticsTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("help");
            completions.add("sound");
            if (sender.hasPermission("dccosmetics.admin")) {
                completions.add("reload");
                completions.add("sculpt");
                completions.add("import");
                completions.add("debugnode");
                completions.add("debugbb");
                completions.add("scroll");
                completions.add("editcmd");
            }
        } else if (args.length == 2 && sender.hasPermission("dccosmetics.admin")) {
            if (args[0].equalsIgnoreCase("sculpt")) {
                completions.add("clear");
                completions.add("editor");
                completions.add("toggle");
                for (CosmeticTemplate template : DCCosmetics.getInstance().getTemplateRegistry().getAllTemplates()) {
                    completions.add(template.getId());
                }
            } else if (args[0].equalsIgnoreCase("import")) {
                File folder = new File(DCCosmetics.getInstance().getDataFolder(), "imports");
                if (folder.exists() && folder.listFiles() != null) {
                    for (File f : folder.listFiles()) {
                        if (f.getName().endsWith(".bbmodel") || f.getName().endsWith(".json")) {
                            completions.add(f.getName());
                        }
                    }
                }
            } else if (args[0].equalsIgnoreCase("debugnode")) {
                completions.add("clear");
            } else if (args[0].equalsIgnoreCase("debugbb")) {
                completions.add("ratio");
                completions.add("corners");
            }
            else if (args[0].equalsIgnoreCase("scroll")) {
                for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            } else if (args[0].equalsIgnoreCase("editcmd")) {
                for (CosmeticTemplate template : DCCosmetics.getInstance().getTemplateRegistry().getAllTemplates()) {
                    completions.add(template.getId());
                }
            }
        } else if (args.length == 3 && sender.hasPermission("dccosmetics.admin")) {
            if (args[0].equalsIgnoreCase("scroll")) {
                for (CosmeticTemplate template : DCCosmetics.getInstance().getTemplateRegistry().getAllTemplates()) {
                    completions.add(template.getId());
                }
            } else if (args[0].equalsIgnoreCase("import")) {
                completions.add("head");
                completions.add("chest");
                completions.add("waist");
                completions.add("boots");
                completions.add("sword");
            } else if (args[0].equalsIgnoreCase("editcmd")) {
                completions.add("main");
                completions.add("add");
                completions.add("delete");
                completions.add("comp");
                completions.add("shift");
            } else if (args[0].equalsIgnoreCase("debugbb") && args[1].equalsIgnoreCase("ratio")) {
                completions.add("0.92"); // Suggest default X
            }
        } else if (args.length == 4 && sender.hasPermission("dccosmetics.admin")) {
            if (args[0].equalsIgnoreCase("debugbb") && args[1].equalsIgnoreCase("ratio")) {
                completions.add("3.5"); // Suggest default Y
            }
        } else if (args.length == 5 && sender.hasPermission("dccosmetics.admin")) {
            if (args[0].equalsIgnoreCase("debugbb") && args[1].equalsIgnoreCase("ratio")) {
                completions.add("1.0"); // Suggest default Z
            }
        } else if (args.length == 6 && sender.hasPermission("dccosmetics.admin")) {
            if (args[0].equalsIgnoreCase("debugbb") && args[1].equalsIgnoreCase("ratio")) {
                completions.add("0.0"); // Suggest default Gap
            }
        } else if (args.length == 7 && sender.hasPermission("dccosmetics.admin")) {
            if (args[0].equalsIgnoreCase("debugbb") && args[1].equalsIgnoreCase("ratio")) {
                completions.add("0.0"); // Suggest default Slide
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