package dc.dcessentials.modules.npc.command;

import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.npc.NpcZoneManager;
import dc.dcessentials.modules.npc.gui.WarpMenuGUI;
import dc.dcessentials.modules.npc.model.NpcZone;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Universal fast travel /warp and /setwarp command handler.
 * Supports /warp (opens sleek GUI), /warp <name|alias>, and /setwarp <name>.
 */
public class WarpCommand implements CommandExecutor, TabCompleter {

    private final NpcZoneManager zoneManager;
    private final WarpMenuGUI warpMenuGUI;

    public WarpCommand(NpcZoneManager zoneManager, WarpMenuGUI warpMenuGUI) {
        this.zoneManager = zoneManager;
        this.warpMenuGUI = warpMenuGUI;
    }

    private boolean hasAdminPerm(CommandSender sender) {
        return sender.hasPermission("dccosmetics.admin") ||
               sender.hasPermission("dragoncote.admin") ||
               sender.hasPermission("dcessentials.admin") ||
               sender.isOp();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            DCMessage.sendError(sender, "Only players in-world can use fast travel warps.");
            return true;
        }

        Player player = (Player) sender;
        String cmdName = command.getName().toLowerCase();

        // Handle /setwarp <name>
        if (cmdName.equals("setwarp") || label.equalsIgnoreCase("setwarp")) {
            if (!hasAdminPerm(player)) {
                DCMessage.sendError(player, "You do not have permission to calibrate warp anchors.");
                return true;
            }
            if (args.length == 0) {
                DCMessage.sendHelp(player, "Usage: &#FFFFFF/setwarp <zone_name|alias>");
                return true;
            }
            String target = args[0].toLowerCase();
            NpcZone zone = zoneManager.getZoneByAlias(target);
            if (zone != null) {
                zoneManager.setZoneWarpLocation(zone.getId(), player.getLocation());
                DCMessage.sendWarp(player, "Calibrated warp anchor for &#FFFFFF" + zone.getDisplayName() + " &#BAE6FD(&#FDE047" + target + "&#BAE6FD) to your coordinates.");
            } else {
                // Create custom zone on the fly if not existing
                zoneManager.setZoneWarpLocation(target, player.getLocation());
                DCMessage.sendWarp(player, "Saved custom warp anchor for &#FFFFFF" + target + " &#BAE6FDto your coordinates.");
            }
            return true;
        }

        // Handle /warp or /warps
        if (args.length == 0) {
            warpMenuGUI.open(player);
            return true;
        }

        String target = args[0].toLowerCase();
        boolean success = zoneManager.warpPlayer(player, target);
        if (!success) {
            DCMessage.sendError(player, "Unknown warp or uncalibrated anchor: &#FFFFFF" + target);
            DCMessage.sendHelp(player, "Type &#FFFFFF/warp &#CBD5E1to open the fast travel menu.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            Set<String> options = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (NpcZone z : zoneManager.getAllZones()) {
                options.add(z.getId());
                options.addAll(z.getAliases());
            }
            return options.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
