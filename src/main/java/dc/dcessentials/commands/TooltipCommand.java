package dc.dcessentials.commands;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.ItemModule;
import dc.dcessentials.modules.items.tooltip.TooltipMode;
import dc.dcessentials.modules.items.tooltip.TooltipModeService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TooltipCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("compact", "expanded", "toggle");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            DCMessage.sendError(sender, "This command can only be executed by players.");
            return true;
        }

        ItemModule mod = DCCosmetics.getInstance().getItemModule();
        TooltipModeService service = mod != null ? mod.getTooltipModeService() : null;
        if (service == null) {
            DCMessage.sendError(player, "Tooltip service is not initialized.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) {
            service.toggleMode(player, true);
            return true;
        }

        String arg = args[0].toLowerCase(Locale.ROOT);
        if (arg.equals("compact") || arg.equals("short") || arg.equals("off")) {
            service.setMode(player, TooltipMode.COMPACT, true);
            return true;
        }
        if (arg.equals("expanded") || arg.equals("detailed") || arg.equals("long") || arg.equals("on")) {
            service.setMode(player, TooltipMode.EXPANDED, true);
            return true;
        }

        DCMessage.sendHelp(player, "Usage: &#FFFFFF/tooltip [compact|expanded|toggle]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : SUBS) {
                if (s.startsWith(prefix)) out.add(s);
            }
            return out;
        }
        return List.of();
    }
}
