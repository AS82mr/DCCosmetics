package dc.dccosmetics.wardrobe.command;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.tpeffects.TpEffectManager;
import dc.dccosmetics.tpeffects.model.TpEffectEntry;
import dc.dccosmetics.wardrobe.camera.CinematicTeleportController;
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
 * Legacy command bridge (/cinetp) routing to the upgraded TpEffects 3D Voxel Camera Engine.
 * Usage:
 *   /cinetp test <obsidian_dissolution|chrono_singularity> [player]
 *   /cinetp start <player> <effectId> <fromWorld,x,y,z,yaw,pitch> <toWorld,x,y,z,yaw,pitch>
 */
public class CinematicTpCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList("test", "start");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dccosmetics.admin") && !sender.hasPermission("dcessentials.admin")) {
            DCMessage.sendError(sender, "You do not have permission to execute cinematic teleport commands.");
            return true;
        }

        if (args.length == 0) {
            DCMessage.sendHelp(sender, "Usage: /cinetp test <obsidian_dissolution|chrono_singularity> [player] (or use /tpeffect test)");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("test")) {
            String effectId = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "obsidian_dissolution";
            Player target = (args.length >= 3) ? Bukkit.getPlayerExact(args[2]) : (sender instanceof Player p ? p : null);
            if (target == null) {
                DCMessage.sendError(sender, "Specify an online player to test the cinematic teleport.");
                return true;
            }

            Location from = target.getLocation().clone();
            Location to = target.getLocation().clone().add(target.getLocation().getDirection().setY(0).normalize().multiply(16.0));
            to.setYaw(from.getYaw());
            to.setPitch(from.getPitch());

            CinematicTeleportController.getInstance().startTeleportSequence(target, from, to, effectId);
            return true;
        }

        if (sub.equals("start")) {
            if (args.length < 5) {
                DCMessage.sendHelp(sender, "Usage: /cinetp start <player> <effect> <fromLoc> <toLoc>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) return true;

            String effectId = args[2].toLowerCase(Locale.ROOT);
            Location from = parseLoc(args[3], target.getLocation());
            Location to = parseLoc(args[4], target.getLocation());

            CinematicTeleportController.getInstance().startTeleportSequence(target, from, to, effectId);
            return true;
        }

        return true;
    }

    private Location parseLoc(String raw, Location fallback) {
        try {
            String[] parts = raw.split(",");
            World w = Bukkit.getWorld(parts[0]);
            if (w == null) w = fallback.getWorld();
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length >= 5 ? Float.parseFloat(parts[4]) : fallback.getYaw();
            float pitch = parts.length >= 6 ? Float.parseFloat(parts[5]) : fallback.getPitch();
            return new Location(w, x, y, z, yaw, pitch);
        } catch (Exception e) {
            return fallback.clone();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("dccosmetics.admin") && !sender.hasPermission("dcessentials.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> out = StringUtil.copyPartialMatches(args[0], SUBS, new ArrayList<>());
            Collections.sort(out);
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> cineIds = getCinematicIds();
        if (args.length == 2) {
            if (sub.equals("test")) {
                List<String> out = StringUtil.copyPartialMatches(args[1], cineIds, new ArrayList<>());
                Collections.sort(out);
                return out;
            }
            if (sub.equals("start")) {
                return partialPlayerNames(args[1]);
            }
        }
        if (args.length == 3) {
            if (sub.equals("test")) {
                return partialPlayerNames(args[2]);
            }
            if (sub.equals("start")) {
                List<String> out = StringUtil.copyPartialMatches(args[2], cineIds, new ArrayList<>());
                Collections.sort(out);
                return out;
            }
        }
        if ((args.length == 4 || args.length == 5) && sub.equals("start") && sender instanceof Player p) {
            Location l = p.getLocation();
            String coordHint = String.format(Locale.US, "%s,%d,%d,%d,%.0f,%.0f",
                    l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), l.getYaw(), l.getPitch());
            return StringUtil.copyPartialMatches(args[args.length - 1], Collections.singletonList(coordHint), new ArrayList<>());
        }
        return Collections.emptyList();
    }

    private List<String> getCinematicIds() {
        DCCosmetics plugin = DCCosmetics.getInstance();
        TpEffectManager mgr = plugin != null ? plugin.getTpEffectManager() : null;
        if (mgr == null) return List.of("obsidian_dissolution", "chrono_singularity");
        List<String> ids = new ArrayList<>();
        for (TpEffectEntry entry : mgr.getRegistry().getAll()) {
            if (entry.isCinematic()) ids.add(entry.getId());
        }
        return ids;
    }

    private List<String> partialPlayerNames(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
        List<String> out = StringUtil.copyPartialMatches(prefix, names, new ArrayList<>());
        Collections.sort(out);
        return out;
    }
}
