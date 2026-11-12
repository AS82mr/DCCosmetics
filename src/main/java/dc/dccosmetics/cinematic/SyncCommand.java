package dc.dccosmetics.cinematic;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

/**
 * Command Executor for /spawnsync (/sync, /gitsync).
 */
public class SyncCommand implements CommandExecutor {

    private final SyncStageManager stageManager;

    public SyncCommand(SyncStageManager stageManager) {
        this.stageManager = stageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dccosmetics.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("wand") || args[0].equalsIgnoreCase("tools")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players can receive sync tools.");
                return true;
            }
            giveSyncTools(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("push") || sub.equals("commit")) {
            stageManager.commitAndPush(sender);
            return true;
        }

        if (sub.equals("status")) {
            sendStatus(sender, stageManager);
            return true;
        }

        if (sub.equals("discard") || sub.equals("reset") || sub.equals("clear")) {
            int count = stageManager.getStagedBoxCount();
            stageManager.clearStage();
            DCMessage.sendSync(sender, "Discarded " + count + " staged region(s). Stage is now clean.");
            if (sender instanceof Player p) {
                p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.2f);
            }
            return true;
        }

        if (sub.equals("stagewe") || sub.equals("add")) {
            if (!(sender instanceof Player player)) {
                DCMessage.sendError(sender, "Only players can stage a WorldEdit selection.");
                return true;
            }
            stageWorldEditSelection(player, stageManager);
            return true;
        }

        if (sub.equals("snow")) {
            boolean next = !stageManager.isAutoSnowOnPush();
            if (args.length > 1) {
                next = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true");
            }
            stageManager.setAutoSnowOnPush(next);
            DCMessage.sendSync(sender, "Auto-Snow surface dusting on push to " +
                    SyncStageManager.SNOW_WORLD + " is now: " + (next ? "ENABLED" : "DISABLED"));
            return true;
        }

        DCMessage.sendHelp(sender, "Usage: /sync <wand|push|status|discard|stagewe|snow>");
        return true;
    }

    private void giveSyncTools(Player player) {
        NamespacedKey key = new NamespacedKey(DCCosmetics.getInstance(), "sync_tool_type");

        // 1. Git-Sync Controller (Breeze Rod / Nether Star fallback)
        Material controllerMat = Material.matchMaterial("BREEZE_ROD");
        if (controllerMat == null) controllerMat = Material.NETHER_STAR;

        ItemStack syncWand = new ItemStack(controllerMat);
        ItemMeta syncMeta = syncWand.getItemMeta();
        if (syncMeta != null) {
            syncMeta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Git-Sync Controller");
            syncMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Automatically tracks edits in " + ChatColor.WHITE + "spawn_main",
                    ChatColor.GRAY + "and pushes delta changes to " + ChatColor.WHITE + "spawn_bw" + ChatColor.GRAY + " & " + ChatColor.WHITE + "spawn_snow" + ChatColor.GRAY + ".",
                    "",
                    ChatColor.YELLOW + "Right-Click: " + ChatColor.GREEN + "Commit & Push Staged Changes",
                    ChatColor.YELLOW + "Shift + Right-Click: " + ChatColor.AQUA + "View Staged Status",
                    ChatColor.YELLOW + "Shift + Left-Click: " + ChatColor.LIGHT_PURPLE + "Stage Current WE Selection"
            ));
            syncMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, SyncListener.SYNC_WAND_TAG);
            syncWand.setItemMeta(syncMeta);
        }

        // 2. Multi-World Instant Eraser (Blaze Rod)
        ItemStack eraserWand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta eraserMeta = eraserWand.getItemMeta();
        if (eraserMeta != null) {
            eraserMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Multi-World Eraser");
            eraserMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Left-click any block (chests, signs, props)",
                    ChatColor.GRAY + "to instantly delete it across:",
                    ChatColor.DARK_GRAY + " » " + ChatColor.WHITE + "spawn_main",
                    ChatColor.DARK_GRAY + " » " + ChatColor.WHITE + "spawn_bw",
                    ChatColor.DARK_GRAY + " » " + ChatColor.WHITE + "spawn_snow",
                    "",
                    ChatColor.RED + "Safely empties containers without dropping loot."
            ));
            eraserMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, SyncListener.ERASER_WAND_TAG);
            eraserWand.setItemMeta(eraserMeta);
        }

        player.getInventory().addItem(syncWand, eraserWand);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
        DCMessage.sendSync(player, "Received the Git-Sync Controller and Multi-World Eraser!");
    }

    public static void sendStatus(CommandSender sender, SyncStageManager stageManager) {
        List<SyncStageManager.DirtyBox> boxes = stageManager.getStagedBoxesSnapshot();
        sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
        DCMessage.sendSync(sender, "Multiverse Synchronization Status &8(Master: &#FFFFFF" + SyncStageManager.MASTER_WORLD + "&8)");
        sender.sendMessage(ColorUtil.colorize("  &7Auto-Snow on Push: " +
                (stageManager.isAutoSnowOnPush() ? "&#0CBF93ENABLED" : "&#EF4444DISABLED")));

        if (boxes.isEmpty()) {
            DCMessage.sendGeneral(sender, "Working tree clean — no uncommitted regions.");
        } else {
            sender.sendMessage(ColorUtil.colorize("  &#FBBF24Staged Dirty Regions &7(" + boxes.size() +
                    " boxes, " + stageManager.getTotalStagedVolume() + " total blocks):"));
            int idx = 1;
            for (SyncStageManager.DirtyBox box : boxes) {
                if (idx <= 10) {
                    sender.sendMessage(ColorUtil.colorize("    &8• &#FFFFFF#" + idx + ": " + box.toString()));
                }
                idx++;
            }
            if (boxes.size() > 10) {
                sender.sendMessage(ColorUtil.colorize("    &7... and &#FFFFFF" + (boxes.size() - 10) + " &7more region(s)."));
            }
            DCMessage.sendInfo(sender, "Right-click the Git-Sync Controller or run /sync push to commit.");
        }
        sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
    }

    public static void stageWorldEditSelection(Player player, SyncStageManager stageManager) {
        try {
            com.sk89q.worldedit.entity.Player wePlayer = BukkitAdapter.adapt(player);
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);
            Region sel = session.getSelection(wePlayer.getWorld());
            BlockVector3 min = sel.getMinimumPoint();
            BlockVector3 max = sel.getMaximumPoint();

            stageManager.recordBox(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
            DCMessage.sendSync(player, "Staged WorldEdit selection: (" + min.x() + ", " + min.y() + ", " + min.z() + ") -> (" +
                    max.x() + ", " + max.y() + ", " + max.z() + ")");
        } catch (Exception e) {
            DCMessage.sendError(player, "Make a WorldEdit selection first before staging manually!");
        }
    }
}