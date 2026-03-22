package dc.dccosmetics.command;

import dc.dccosmetics.gui.GuiManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ProfileCommand implements CommandExecutor {

    private final GuiManager guiManager;

    public ProfileCommand(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can view profiles.");
            return true;
        }

        OfflinePlayer target = player;

        if (args.length > 0) {
            target = Bukkit.getOfflinePlayer(args[0]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                player.sendMessage(ChatColor.RED + "Player '" + args[0] + "' has never played on this server.");
                return true;
            }
        }

        guiManager.openProfileMenu(player, target);
        return true;
    }
}