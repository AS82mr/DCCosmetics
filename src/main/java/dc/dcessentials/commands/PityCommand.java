package dc.dcessentials.commands;

import dc.dcessentials.modules.pity.gui.PityGUI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PityCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            dc.dcessentials.core.util.DCMessage.sendError(sender, "This command can only be executed by players.");
            return true;
        }

        if (Bukkit.getPluginManager().getPlugin("DeluxeMenus") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dm open pity_hub " + player.getName());
            return true;
        }

        // Fallback to internal GUI
        String cat = args.length >= 1 ? args[0] : "general";
        PityGUI.open(player, cat);
        return true;
    }
}
