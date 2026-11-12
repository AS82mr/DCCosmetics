package dc.dcessentials.modules.boss;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Unified command handler for `/dce emperor ...` and legacy compatibility aliases
 * (`/emperortest`, `/emperorscale`, `/emperorheal`, `/emperoractive`, `/emperordeath`).
 */
public class EmperorCommand implements CommandExecutor, TabCompleter {

    private final DCCosmetics plugin;
    private final EmperorBossManager manager;

    private static final List<String> SUBS = Arrays.asList("scale", "reset", "info", "testheal", "clean", "heal");

    public EmperorCommand(DCCosmetics plugin, EmperorBossManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = label.toLowerCase(Locale.ROOT);
        if (cmd.contains(":")) {
            cmd = cmd.substring(cmd.indexOf(':') + 1);
        }

        // Silent MythicMobs lifecycle compatibility commands (in case external skills still dispatch them)
        if (cmd.equals("emperoractive")) {
            manager.updateBossBarViewers();
            return true;
        }
        if (cmd.equals("emperordeath")) {
            manager.onEmperorDeath(manager.getEmperorEntity());
            return true;
        }
        if (cmd.equals("emperorheal")) {
            return handleCoordHeal(sender, args);
        }

        if (!sender.hasPermission("dragoncote.admin") && !sender.hasPermission("dcessentials.admin")) {
            DCMessage.sendError(sender, "You do not have permission to execute this command.");
            return true;
        }

        // Handle `/emperortest [heal|scale <N>|reset|info]`
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("scale")) {
            if (args.length < 2) {
                DCMessage.sendHelp(sender, "Usage: &#FFFFFF/dce emperor scale <players> [hp%]");
                return true;
            }
            int count;
            try {
                count = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                DCMessage.sendError(sender, "Invalid player count: " + args[1]);
                return true;
            }
            int hp = 100;
            if (args.length >= 3) {
                try {
                    hp = Math.max(1, Math.min(100, Integer.parseInt(args[2])));
                } catch (NumberFormatException e) {
                    DCMessage.sendError(sender, "Invalid HP percentage: " + args[2]);
                    return true;
                }
            }
            manager.setManualOverride(count, hp);
            DCMessage.sendGeneral(sender, "Emperor simulation enabled: &#FFFFFF" + manager.getPlayerCount()
                    + " players &#7C8CA1(Scale: &#FCD34D" + String.format(Locale.US, "%.1f", manager.getAngerScale())
                    + "x&#7C8CA1, HP: &#10B981" + manager.getHpPercent() + "%&#7C8CA1). BossBar & TAB Scoreboard refreshed.");
            return true;
        }

        if (sub.equals("reset") || sub.equals("off") || sub.equals("clear")) {
            manager.resetManualOverride();
            DCMessage.sendGeneral(sender, "Cleared manual Emperor override. Returned to live arena tracking.");
            return true;
        }

        if (sub.equals("testheal") || (cmd.startsWith("emperor") && sub.equals("heal") && args.length == 1)) {
            if (!(sender instanceof Player p)) {
                DCMessage.sendError(sender, "Run this in-game to preview the TextDisplay location.");
                return true;
            }
            manager.testHeal(p);
            DCMessage.sendGeneral(sender, "Spawned test floating &#10B981+12 ❤ &#0CBF93TextDisplay & heartbeat.");
            return true;
        }

        if (sub.equals("heal")) {
            return handleCoordHeal(sender, Arrays.copyOfRange(args, 1, args.length));
        }

        if (sub.equals("clean") || sub.equals("cleanup")) {
            int removed = manager.cleanStrayDisplays(Bukkit.getWorld("lowercity"));
            if (sender instanceof Player p && !p.getWorld().getName().equalsIgnoreCase("lowercity")) {
                removed += manager.cleanStrayDisplays(p.getWorld());
            }
            DCMessage.sendGeneral(sender, "Cleaned up &#FFFFFF" + removed + " &#0CBF93stray Emperor heal TextDisplays.");
            return true;
        }

        if (sub.equals("info") || sub.equals("status")) {
            sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
            DCMessage.sendGeneral(sender, "Emperor Boss Engine Telemetry");
            sender.sendMessage(ColorUtil.colorize("  &7Active: " + (manager.isActive() ? "&aYes" : "&cNo")
                    + " &8| &7Manual Override: " + (manager.isManualOverride() ? "&eYes" : "&7No")));
            var entity = manager.getEmperorEntity();
            sender.sendMessage(ColorUtil.colorize("  &7Tracked Entity: "
                    + (entity != null && entity.isValid() && !entity.isDead() ? "&a" + entity.getUniqueId() : "&cNone")));
            Location loc = manager.getEmperorLocation();
            String locStr = (loc != null && loc.getWorld() != null)
                    ? String.format(Locale.US, "%s (%.1f, %.1f, %.1f)", loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ())
                    : "None";
            sender.sendMessage(ColorUtil.colorize("  &7Location: &f" + locStr));
            sender.sendMessage(ColorUtil.colorize("  &7Players: &e" + manager.getPlayerCount()
                    + " &8| &7Anger Scale: &e" + String.format(Locale.US, "%.1fx", manager.getAngerScale())
                    + " &8| &7HP: &a" + manager.getHpPercent() + "%"));
            sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private boolean handleCoordHeal(CommandSender sender, String[] coordArgs) {
        if (coordArgs.length < 3) {
            Location fallback = manager.getEmperorLocation();
            if (fallback == null && sender instanceof Player p) fallback = p.getLocation();
            if (fallback != null) {
                manager.spawnHealText(fallback, 12);
                return true;
            }
            DCMessage.sendHelp(sender, "Usage: &#FFFFFF/dce emperor heal <x> <y> <z> [amount]");
            return true;
        }
        try {
            double x = Double.parseDouble(coordArgs[0]);
            double y = Double.parseDouble(coordArgs[1]);
            double z = Double.parseDouble(coordArgs[2]);
            int amount = (coordArgs.length >= 4) ? Integer.parseInt(coordArgs[3]) : 12;

            World world = Bukkit.getWorld("lowercity");
            if (world == null && sender instanceof Player p) world = p.getWorld();
            if (world == null && manager.getEmperorLocation() != null) world = manager.getEmperorLocation().getWorld();
            if (world == null) return true;

            manager.spawnHealText(new Location(world, x, y, z), amount);
        } catch (NumberFormatException ignored) {
            if (manager.getEmperorLocation() != null) {
                manager.spawnHealText(manager.getEmperorLocation(), 12);
            }
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        DCMessage.sendHelp(sender, "Emperor Admin Commands:");
        sender.sendMessage(ColorUtil.colorize("  &f/dce emperor scale <players> [hp%] &7- Simulate player scaling, BossBar & scoreboard"));
        sender.sendMessage(ColorUtil.colorize("  &f/dce emperor reset &7- Clear manual simulation override"));
        sender.sendMessage(ColorUtil.colorize("  &f/dce emperor info &7- Inspect tracked entity, scale & HP"));
        sender.sendMessage(ColorUtil.colorize("  &f/dce emperor testheal &7- Preview floating +12 ❤ TextDisplay"));
        sender.sendMessage(ColorUtil.colorize("  &f/dce emperor clean &7- Remove any stray emp_heal_txt displays"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("dcessentials.admin") && !sender.hasPermission("dragoncote.admin")
                && !sender.hasPermission("dccosmetics.admin") && !sender.isOp()) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            List<String> subs = new ArrayList<>(SUBS);
            subs.addAll(Arrays.asList("off", "clear", "status"));
            StringUtil.copyPartialMatches(args[0], subs, out);
            Collections.sort(out);
            return out;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("scale")) {
            if (args.length == 2) {
                List<String> out = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], Arrays.asList("1", "2", "3", "5", "8", "10"), out);
                return out;
            }
            if (args.length == 3) {
                List<String> out = new ArrayList<>();
                StringUtil.copyPartialMatches(args[2], Arrays.asList("100", "75", "50", "25"), out);
                return out;
            }
        }
        if (sub.equals("heal") && args.length >= 2 && args.length <= 5) {
            List<String> opts = new ArrayList<>();
            if (args.length <= 4) {
                opts.add("~");
                if (sender instanceof org.bukkit.entity.Player p) {
                    if (args.length == 2) opts.add(String.valueOf(p.getLocation().getBlockX()));
                    else if (args.length == 3) opts.add(String.valueOf(p.getLocation().getBlockY()));
                    else opts.add(String.valueOf(p.getLocation().getBlockZ()));
                }
            } else {
                opts.addAll(Arrays.asList("20", "40", "75", "100"));
            }
            List<String> out = new ArrayList<>();
            StringUtil.copyPartialMatches(args[args.length - 1], opts, out);
            return out;
        }
        return Collections.emptyList();
    }
}
