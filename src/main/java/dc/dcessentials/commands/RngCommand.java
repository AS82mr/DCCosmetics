package dc.dcessentials.commands;

import dc.dcessentials.modules.rng.gui.RngGUI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RngCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            dc.dcessentials.core.util.DCMessage.sendError(sender, "This command can only be executed by players.");
            return true;
        }

        if (Bukkit.getPluginManager().getPlugin("DeluxeMenus") != null) {
            if (args.length >= 1) {
                String cat = args[0].toLowerCase();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dm open rng_" + cat + " " + player.getName());
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dm open rng_hub " + player.getName());
            }
            return true;
        }

        // Fallback to internal GUI
        String cat = args.length >= 1 ? args[0] : "general";
        RngGUI.open(player, cat);
        return true;
    }
}
