package dc.dcessentials.commands;

import dc.dccosmetics.DCCosmetics;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RngHookCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Usage: /rnghook <player> <category_id> <general_boost_pct> <xp_amount> <combat|casino>
        if (args.length < 4) {
            dc.dcessentials.core.util.DCMessage.sendHelp(sender, "Usage: &#FFFFFF/rnghook <player> <category_id> <boost_pct> <xp_amount> [combat|casino]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            return true;
        }

        String category = args[1].toLowerCase();
        double boostPct = 0;
        int xpAmount = 1;
        String sourceType = args.length >= 5 ? args[4].toLowerCase() : "combat";

        try {
            boostPct = Double.parseDouble(args[2]);
            xpAmount = Integer.parseInt(args[3]);
        } catch (NumberFormatException ignored) {}

        DCCosmetics.getInstance().getRngManager().processAction(
                target, category, boostPct, xpAmount, sourceType, null
        );
        return true;
    }
}
