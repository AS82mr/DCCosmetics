package dc.dccosmetics.wardrobe.command;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import dc.dccosmetics.wardrobe.WardrobeManager;
import dc.dccosmetics.wardrobe.WardrobeRegionSelector;
import dc.dccosmetics.wardrobe.WardrobeRoom;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import dc.dccosmetics.wardrobe.gui.WardrobeAdminGUI;

public class WardrobeCommand implements CommandExecutor {

    private final WardrobeManager wardrobeManager;
    private final WardrobeRoom room;
    private final WardrobeRegionSelector regionSelector;
    private final WardrobeAdminGUI adminGUI;
    private final WardrobeStudioCommand studioCommand;

    public WardrobeCommand(WardrobeManager wardrobeManager, WardrobeRoom room, WardrobeRegionSelector regionSelector, WardrobeAdminGUI adminGUI) {
        this.wardrobeManager = wardrobeManager;
        this.room = room;
        this.regionSelector = regionSelector;
        this.adminGUI = adminGUI;
        this.studioCommand = new WardrobeStudioCommand(wardrobeManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && (args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("refresh"))) {
            if (!sender.hasPermission("dccosmetics.admin")) {
                sender.sendMessage(ChatColor.RED + "You lack permission to reload wardrobe configurations.");
                return true;
            }
            room.load();
            dc.dccosmetics.wardrobe.hud.HudConfig.get().load();
            wardrobeManager.loadConfig();
            DCCosmetics.getInstance().getTemplateRegistry().loadAll();
            DCMessage.sendWardrobe(sender, "Wardrobe settings, studio layouts, categories, and cosmetic templates successfully refreshed!");
            return true;
        }

        if (!(sender instanceof Player)) {
            DCMessage.sendError(sender, "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Enter wardrobe
            wardrobeManager.enterWardrobe(player);
            return true;
        }

        if (args.length > 0 && player.hasPermission("dccosmetics.admin")) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "wand":
                    regionSelector.giveWand(player);
                    break;
                case "setup":
                    regionSelector.saveRegion(player);
                    break;
                case "setspawn":
                    room.saveSpawn(player.getLocation());
                    player.sendMessage(ChatColor.GREEN + "Wardrobe spawn location set.");
                    break;
                case "setnpc":
                    room.saveNpcStand(player.getLocation());
                    player.sendMessage(ChatColor.GREEN + "Wardrobe NPC location set.");
                    break;
                case "setexit":
                    room.saveFallbackExit(player.getLocation());
                    player.sendMessage(ChatColor.GREEN + "Wardrobe fallback exit location set.");
                    break;
                case "setsaferoom":
                    room.saveSafeRoom(player.getLocation());
                    player.sendMessage(ChatColor.GREEN + "Safe room location set. Players will be held here during camera mode.");
                    break;
                case "settppreview":
                case "settpview":
                    room.saveTpPreview(player.getLocation());
                    Location tpLoc = player.getLocation();
                    DCMessage.sendWardrobe(player, String.format(
                            "Wardrobe Teleport Preview location saved (%s @ %.1f, %.1f, %.1f).",
                            tpLoc.getWorld() != null ? tpLoc.getWorld().getName() : "?",
                            tpLoc.getX(), tpLoc.getY(), tpLoc.getZ()));
                    break;
                case "reload":
                case "refresh":
                    room.load();
                    dc.dccosmetics.wardrobe.hud.HudConfig.get().load();
                    wardrobeManager.loadConfig();
                    DCCosmetics.getInstance().getTemplateRegistry().loadAll();
                    DCMessage.sendWardrobe(player, "Wardrobe settings, studio layouts, categories, and cosmetic templates successfully refreshed!");
                    break;
                case "admin":
                    adminGUI.open(player);
                    break;
                case "studio":
                    return studioCommand.handle(player, args);
                default:
                    DCMessage.sendHelp(player, "Usage: /wardrobe [wand|setup|setspawn|setnpc|setexit|setsaferoom|settppreview|reload|admin|studio]");
                    break;
            }
        }

        return true;
    }
}
