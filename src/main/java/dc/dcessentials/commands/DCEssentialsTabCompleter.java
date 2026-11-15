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

public class DCEssentialsTabCompleter implements TabCompleter {

    private static final List<String> ROOT_SUBS = Arrays.asList("status", "gui", "reload", "items", "editor", "import", "tester", "curator", "give", "update", "unbind", "givexp", "selectdrop", "event", "events", "calendar", "emperor");
    private static final List<String> XP_AMOUNTS = Arrays.asList("10", "25", "50", "100", "250", "500", "1000");
    private static final List<String> CATEGORIES = Arrays.asList(
            "general", "overworld", "nether", "end", "bosses", "lowercity", "survival_custom", "casino"
    );
    private static final List<String> ADAPT_SKILLS = Arrays.asList(
            "all", "agility", "architect", "axes", "blocking", "brewing",
            "chronos", "crafting", "discovery", "enchanting", "excavation",
            "herbalism", "hunter", "kinetics", "nether", "pickaxe",
            "ranged", "rift", "seaborne", "stealth", "swords", "taming", "tragoul", "unarmed"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], ROOT_SUBS, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args[0].equalsIgnoreCase("event") || args[0].equalsIgnoreCase("events") || args[0].equalsIgnoreCase("calendar")) {
            var em = dc.dccosmetics.DCCosmetics.getInstance().getEventManager();
            if (em != null) {
                return new EventTabCompleter(em).onTabComplete(sender, command, alias, java.util.Arrays.copyOfRange(args, 1, args.length));
            }
        }

        if (args[0].equalsIgnoreCase("emperor") || args[0].equalsIgnoreCase("emp") || args[0].equalsIgnoreCase("boss")) {
            var ec = dc.dccosmetics.DCCosmetics.getInstance().getEmperorCommand();
            if (ec != null) {
                return ec.onTabComplete(sender, command, alias, java.util.Arrays.copyOfRange(args, 1, args.length));
            }
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("givexp") || args[0].equalsIgnoreCase("addxp") || args[0].equalsIgnoreCase("xp") || args[0].equalsIgnoreCase("selectdrop"))) {
            List<String> playerNames = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                playerNames.add(p.getName());
            }
            StringUtil.copyPartialMatches(args[1], playerNames, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            var mod = dc.dccosmetics.DCCosmetics.getInstance().getItemModule();
            if (mod != null) {
                List<String> ids = new ArrayList<>();
                for (var it : mod.getItemRegistry().getItems()) ids.add(it.getId());
                for (var sc : mod.getScrollRegistry().getScrolls()) ids.add(sc.getId());
                StringUtil.copyPartialMatches(args[2], ids, completions);
                Collections.sort(completions);
                return completions;
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            StringUtil.copyPartialMatches(args[3], Arrays.asList("1", "2", "4", "8", "16", "32", "64"), completions);
            return completions;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("tester") || args[0].equalsIgnoreCase("dummy"))) {
            StringUtil.copyPartialMatches(args[1], Arrays.asList("spawn", "clear", "remove", "chat", "on", "off", "toggle", "stats", "webhook"), completions);
            Collections.sort(completions);
            return completions;
        }

        if (args[0].equalsIgnoreCase("curator") || args[0].equalsIgnoreCase("curate") || args[0].equalsIgnoreCase("models")) {
            if (args.length == 2) {
                StringUtil.copyPartialMatches(args[1], Arrays.asList("wand", "scenewand", "gui", "anim", "clear", "rescan", "export", "filter", "jump"), completions);
                Collections.sort(completions);
                return completions;
            }
            if (args.length == 3 && (args[1].equalsIgnoreCase("anim") || args[1].equalsIgnoreCase("animation") || args[1].equalsIgnoreCase("animate"))) {
                StringUtil.copyPartialMatches(args[2], Arrays.asList("next", "list", "auto", "stop", "play"), completions);
                Collections.sort(completions);
                return completions;
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("gui")) {
                StringUtil.copyPartialMatches(args[2], Arrays.asList("all", "favorites", "cleanup"), completions);
                Collections.sort(completions);
                return completions;
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("filter")) {
                StringUtil.copyPartialMatches(args[2], Arrays.asList("all", "meg", "furniture", "item"), completions);
                Collections.sort(completions);
                return completions;
            }
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("tester") || args[0].equalsIgnoreCase("dummy"))
                && (args[1].equalsIgnoreCase("webhook") || args[1].equalsIgnoreCase("discord"))) {
            StringUtil.copyPartialMatches(args[2], Arrays.asList("test", "status", "off", "clear"), completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("editor") || args[0].equalsIgnoreCase("update"))) {
            var mod = dc.dccosmetics.DCCosmetics.getInstance().getItemModule();
            if (mod != null) {
                List<String> ids = new ArrayList<>();
                for (var it : mod.getItemRegistry().getItems()) ids.add(it.getId());
                StringUtil.copyPartialMatches(args[1], ids, completions);
                Collections.sort(completions);
                return completions;
            }
        }

        if (args[0].equalsIgnoreCase("items") || args[0].equalsIgnoreCase("item") || args[0].equalsIgnoreCase("itemstat")) {
            if (args.length == 2) {
                StringUtil.copyPartialMatches(args[1], Collections.singletonList("scroll"), completions);
                return completions;
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("scroll")) {
                StringUtil.copyPartialMatches(args[2], Collections.singletonList("give"), completions);
                return completions;
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("scroll") && args[2].equalsIgnoreCase("give")) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
                StringUtil.copyPartialMatches(args[3], players, completions);
                Collections.sort(completions);
                return completions;
            }
            if (args.length == 5 && args[1].equalsIgnoreCase("scroll") && args[2].equalsIgnoreCase("give")) {
                var mod = dc.dccosmetics.DCCosmetics.getInstance().getItemModule();
                if (mod != null) {
                    List<String> ids = new ArrayList<>();
                    for (var sc : mod.getScrollRegistry().getScrolls()) ids.add(sc.getId());
                    StringUtil.copyPartialMatches(args[4], ids, completions);
                    Collections.sort(completions);
                    return completions;
                }
            }
            if (args.length == 6 && args[1].equalsIgnoreCase("scroll") && args[2].equalsIgnoreCase("give")) {
                return Arrays.asList("1", "2", "4", "8", "16", "32", "64");
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("selectdrop")) {
            StringUtil.copyPartialMatches(args[2], CATEGORIES, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("selectdrop")) {
            String cat = args[2].toLowerCase();
            var drops = dc.dccosmetics.DCCosmetics.getInstance().getRngManager().getDropTables().get(cat);
            if (drops != null) {
                List<String> dropIds = new ArrayList<>();
                for (var d : drops) dropIds.add(d.getDropId());
                StringUtil.copyPartialMatches(args[3], dropIds, completions);
                Collections.sort(completions);
                return completions;
            }
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("givexp") || args[0].equalsIgnoreCase("addxp") || args[0].equalsIgnoreCase("xp"))) {
            StringUtil.copyPartialMatches(args[2], XP_AMOUNTS, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 4 && (args[0].equalsIgnoreCase("givexp") || args[0].equalsIgnoreCase("addxp") || args[0].equalsIgnoreCase("xp"))) {
            StringUtil.copyPartialMatches(args[3], ADAPT_SKILLS, completions);
            Collections.sort(completions);
            return completions;
        }

        return Collections.emptyList();
    }
}
