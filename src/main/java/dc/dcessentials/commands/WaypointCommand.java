package dc.dcessentials.commands;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WaypointCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Player self-toggle or self-clear
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("toggle"))) {
            if (!(sender instanceof Player player)) {
                DCMessage.sendHelp(sender, "Usage: &#FFFFFF/waypoint <player> <world> <x> <y> <z> | clear");
                return true;
            }
            boolean visible = DCCosmetics.getInstance().getWaypointManager().toggleVisibility(player);
            if (visible) {
                DCMessage.sendGeneral(player, "3D Waypoint Arrow enabled.");
            } else {
                DCMessage.sendWarning(player, "3D Waypoint Arrow hidden. Use &#FFFFFF/waypoint toggle &#FDE047to enable it again.");
            }
            return true;
        }

        if (args.length == 1 && (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("stop"))) {
            if (!(sender instanceof Player player)) {
                DCMessage.sendHelp(sender, "Usage: &#FFFFFF/waypoint <player> clear");
                return true;
            }
            DCCosmetics.getInstance().getWaypointManager().clearWaypoint(player, true);
            DCMessage.sendInfo(player, "Active HUD Waypoint cleared.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("next")) {
            if (sender instanceof Player player) {
                player.performCommand("dcnpc waypoint next");
            }
            return true;
        }

        // Target-based operations (Console or Admin or Skript bridge)
        // Syntax: /waypoint <player> clear
        // Syntax: /waypoint <player> <world> <x> <y> <z>
        Player target = Bukkit.getPlayer(args[0]);
        if (target != null) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
                DCCosmetics.getInstance().getWaypointManager().clearWaypoint(target, true);
                return true;
            } else if (args.length >= 5) {
                try {
                    World w = Bukkit.getWorld(args[1]);
                    if (w != null) {
                        double x = Double.parseDouble(args[2]);
                        double y = Double.parseDouble(args[3]);
                        double z = Double.parseDouble(args[4]);
                        Location loc = new Location(w, x, y, z);
                        DCCosmetics.getInstance().getWaypointManager().setWaypoint(target, loc);
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        }

        // Fallback for players passing character id: /waypoint <id> -> /dcnpc waypoint <id>
        if (sender instanceof Player player) {
            player.performCommand("dcnpc waypoint " + args[0]);
            return true;
        }

        DCMessage.sendHelp(sender, "Usage: &#FFFFFF/waypoint [toggle|clear|next|<character>]");
        return true;
    }
}
