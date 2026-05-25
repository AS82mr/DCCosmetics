package dc.dccosmetics.wardrobe.command;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.wardrobe.WardrobeManager;
import dc.dccosmetics.wardrobe.WardrobeRegionSelector;
import dc.dccosmetics.wardrobe.WardrobeRoom;
import org.bukkit.ChatColor;
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
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
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
                case "admin":
                    adminGUI.open(player);
                    break;
                case "studio":
                    return studioCommand.handle(player, args);
                default:
                    player.sendMessage(ChatColor.RED + "Usage: /wardrobe [wand|setup|setspawn|setnpc|setexit|setsaferoom|admin|studio]");
                    break;
            }
        }

        return true;
    }
}
