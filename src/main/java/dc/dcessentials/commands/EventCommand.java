package dc.dcessentials.commands;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.event.EventManager;
import dc.dcessentials.modules.event.model.ServerEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Command handler for /events, /calendar, and admin /event operations.
 */
public class EventCommand implements CommandExecutor {

    private final EventManager eventManager;

    public EventCommand(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                eventManager.openCalendarGUI((Player) sender, 1);
            } else {
                DCMessage.sendEvent(sender, "Dragoncote Server Events System (v2.0)");
                DCMessage.sendInfo(sender, "Active Realm: &#FFFFFF" + eventManager.getActiveSpawnWorld() + " &#BAE6FD(Time remaining: &#FFFFFF" + eventManager.getFormattedCycleTimeRemaining() + "&#BAE6FD)");
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("page") || sub.equals("2") || sub.equals("schedule")) {
            if (sender instanceof Player) {
                eventManager.openCalendarGUI((Player) sender, 2);
            }
            return true;
        }

        if (sub.equals("spawn") || sub.equals("hub")) {
            if (sender instanceof Player) {
                eventManager.teleportToActiveSpawn((Player) sender);
            } else {
                DCMessage.sendError(sender, "Only players can teleport to active spawn.");
            }
            return true;
        }

        // Admin subcommands require permission
        if (!sender.hasPermission("dcessentials.admin")) {
            if (sender instanceof Player) {
                eventManager.openCalendarGUI((Player) sender, 1);
            } else {
                DCMessage.sendError(sender, "You do not have permission to execute this command.");
            }
            return true;
        }

        switch (sub) {
            case "list":
                sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
                DCMessage.sendEvent(sender, "Dragoncote Events Monitor");
                DCMessage.sendInfo(sender, "Active World Cycle: &#FFFFFF" + eventManager.getActiveSpawnWorld()
                        + " &8| &#BAE6FDTime Left: &#FFFFFF" + eventManager.getFormattedCycleTimeRemaining()
                        + (eventManager.isEvacuating() ? " &#EF4444(Evacuating)" : " &#0CBF93(Stable)"));
                sender.sendMessage(" ");
                sender.sendMessage(ColorUtil.colorize("  &#FBBF24World Cycles:"));
                for (Map.Entry<String, ServerEvent> entry : eventManager.getWorldCycleEvents().entrySet()) {
                    ServerEvent se = entry.getValue();
                    boolean cur = se.getWorldName().equalsIgnoreCase(eventManager.getActiveSpawnWorld());
                    sender.sendMessage(ColorUtil.colorize("  &8• " + (cur ? "&#0CBF93▶ " : "&7  ") + "&#FFFFFF" + entry.getKey() + " &8(World: &#FDE047" + se.getWorldName()
                            + "&8) - " + se.getDurationMinutes() + " mins"));
                }
                sender.sendMessage(" ");
                sender.sendMessage(ColorUtil.colorize("  &#FBBF24Mini-Events:"));
                for (Map.Entry<String, ServerEvent> entry : eventManager.getMiniEvents().entrySet()) {
                    ServerEvent me = entry.getValue();
                    String status = me.isRunning() ? "&#0CBF93Running (" + me.getFormattedRemainingTime() + " left)" : "&7Scheduled in " + me.getFormattedUntilStartTime();
                    sender.sendMessage(ColorUtil.colorize("  &8• &#FFFFFF" + entry.getKey() + " &8- " + status));
                }
                sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
                return true;

            case "swap":
                if (args.length < 2) {
                    DCMessage.sendHelp(sender, "Usage: &#FFFFFF/" + label + " swap <world_or_event_id>");
                    return true;
                }
                String target = args[1].toLowerCase();
                boolean swapped = eventManager.forceSwap(target);
                if (swapped) {
                    DCMessage.sendGeneral(sender, "Initiated dimensional shift to: &#FFFFFF" + target);
                } else {
                    DCMessage.sendError(sender, "Could not find world cycle event matching: &#FFFFFF" + target);
                }
                return true;

            case "time":
                if (args.length < 2) {
                    DCMessage.sendHelp(sender, "Usage: &#FFFFFF/" + label + " time <seconds_remaining>");
                    return true;
                }
                try {
                    int sec = Integer.parseInt(args[1]);
                    eventManager.setCycleSecondsRemaining(sec);
                    DCMessage.sendGeneral(sender, "Set active world cycle countdown to: &#FFFFFF" + sec + " &#0CBF93seconds.");
                } catch (NumberFormatException e) {
                    DCMessage.sendError(sender, "Invalid number: &#FFFFFF" + args[1]);
                }
                return true;

            case "start":
                if (args.length < 2) {
                    DCMessage.sendHelp(sender, "Usage: &#FFFFFF/" + label + " start <mini_event_id>");
                    return true;
                }
                String startId = args[1].toLowerCase();
                ServerEvent toStart = eventManager.getMiniEvents().get(startId);
                if (toStart != null) {
                    eventManager.startMiniEvent(toStart);
                    DCMessage.sendGeneral(sender, "Started event: &#FFFFFF" + toStart.getTitle());
                } else {
                    DCMessage.sendError(sender, "Event not found: &#FFFFFF" + startId);
                }
                return true;

            case "stop":
                if (args.length < 2) {
                    DCMessage.sendHelp(sender, "Usage: &#FFFFFF/" + label + " stop <mini_event_id|all>");
                    return true;
                }
                String stopId = args[1].toLowerCase();
                if (stopId.equals("all")) {
                    for (ServerEvent se : eventManager.getMiniEvents().values()) {
                        if (se.isRunning()) eventManager.endMiniEvent(se);
                    }
                    DCMessage.sendGeneral(sender, "Stopped all running mini-events.");
                } else {
                    ServerEvent toStop = eventManager.getMiniEvents().get(stopId);
                    if (toStop != null) {
                        eventManager.endMiniEvent(toStop);
                        DCMessage.sendGeneral(sender, "Stopped event: &#FFFFFF" + toStop.getTitle());
                    } else {
                        DCMessage.sendError(sender, "Event not found: &#FFFFFF" + stopId);
                    }
                }
                return true;

            case "reload":
                DCCosmetics.getInstance().reloadConfigs();
                DCMessage.sendGeneral(sender, "Reloaded events and system configurations.");
                return true;

            default:
                DCMessage.sendHelp(sender, "Subcommands: &#FFFFFFlist, swap, time, start, stop, reload, page, spawn");
                return true;
        }
    }
}
