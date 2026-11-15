package dc.dccosmetics.tpeffects.command;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.tpeffects.TpEffectManager;
import dc.dccosmetics.tpeffects.TpEffectRegistry;
import dc.dccosmetics.tpeffects.model.TpEffectEntry;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Full-featured /tpeffect command + TabCompleter handling player equip/clear/mute/list/preview
 * and admin test/give/enable/disable/hide/show/reload controls.
 */
public final class TpEffectCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBCOMMANDS = List.of(
            "set", "clear", "list", "info", "preview", "mute", "unmute", "help"
    );
    private static final List<String> ADMIN_SUBCOMMANDS = List.of(
            "set", "clear", "list", "info", "preview", "mute", "unmute",
            "test", "give", "enable", "disable", "hide", "show", "reload", "help"
    );

    private final DCCosmetics plugin;
    private final TpEffectManager manager;

    public TpEffectCommand(DCCosmetics plugin, TpEffectManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set" -> handleSet(sender, args);
            case "clear", "remove", "off", "unequip" -> handleClear(sender, args);
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "preview" -> handlePreview(sender, args);
            case "mute" -> handleMute(sender, true);
            case "unmute" -> handleMute(sender, false);
            case "test" -> handleTest(sender, args);
            case "give" -> handleGive(sender, args);
            case "enable" -> handleToggleEnable(sender, args, true);
            case "disable" -> handleToggleEnable(sender, args, false);
            case "hide" -> handleToggleHide(sender, args, true);
            case "show" -> handleToggleHide(sender, args, false);
            case "reload" -> handleReload(sender);
            default -> {
                DCMessage.sendError(sender, "Unknown subcommand '" + args[0] + "'. Use /tpeffect help.");
            }
        }
        return true;
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 2) {
            DCMessage.sendHelp(sender, "Usage: /tpeffect set <id>" + (isAdmin(sender) ? " [player]" : ""));
            return;
        }

        TpEffectEntry entry = manager.getRegistry().getById(args[1]);
        String targetPlayerName = args.length >= 3 ? args[2] : null;
        if (entry == null && args.length >= 3) {
            TpEffectEntry swapped = manager.getRegistry().getById(args[2]);
            if (swapped != null) {
                entry = swapped;
                targetPlayerName = args[1];
            }
        }
        if (entry == null) {
            DCMessage.sendError(sender, "Unknown teleport effect '" + args[1] + "'.");
            return;
        }
        if (!entry.isEnabled()) {
            DCMessage.sendError(sender, "That teleport effect is currently disabled.");
            return;
        }

        if (targetPlayerName != null) {
            if (!requireAdmin(sender)) return;
            Player target = Bukkit.getPlayerExact(targetPlayerName);
            if (target == null) {
                DCMessage.sendError(sender, "Player '" + targetPlayerName + "' is not online.");
                return;
            }
            manager.setSelectedEffect(target, entry);
            DCMessage.sendGeneral(sender, "Equipped teleport effect " + entry.getRawDisplayName() + " &ffor &b" + target.getName() + "&f.");
            DCMessage.sendGeneral(target, "Equipped teleport effect: " + entry.getRawDisplayName());
            return;
        }

        if (!(sender instanceof Player player)) {
            DCMessage.sendError(sender, "Console must specify a target player: /tpeffect set <id> <player>");
            return;
        }
        if (!TpEffectRegistry.hasPermission(player, entry) && !isAdmin(player)) {
            DCMessage.sendError(player, "You do not own the " + entry.getRawDisplayName() + " &cteleport effect.");
            return;
        }

        manager.setSelectedEffect(player, entry);
        DCMessage.sendGeneral(player, "Equipped teleport effect: " + entry.getRawDisplayName());
    }

    private void handleClear(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (!requireAdmin(sender)) return;
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                DCMessage.sendError(sender, "Player '" + args[1] + "' is not online.");
                return;
            }
            boolean cleared = manager.clearSelectedEffect(target);
            if (cleared) {
                DCMessage.sendGeneral(sender, "Cleared teleport effect for &b" + target.getName() + "&f.");
                DCMessage.sendInfo(target, "Your teleport effect has been cleared.");
            } else {
                DCMessage.sendInfo(sender, "&b" + target.getName() + " &fdid not have a teleport effect equipped.");
            }
            return;
        }

        if (!(sender instanceof Player player)) {
            DCMessage.sendError(sender, "Console usage: /tpeffect clear <player>");
            return;
        }
        if (manager.clearSelectedEffect(player)) {
            DCMessage.sendGeneral(player, "Cleared your active teleport effect.");
        } else {
            DCMessage.sendInfo(player, "You don't have a teleport effect equipped.");
        }
    }

    private void handleMute(CommandSender sender, boolean mute) {
        if (!(sender instanceof Player player)) {
            DCMessage.sendError(sender, "Only players can toggle personal teleport effect muting.");
            return;
        }
        manager.setMuted(player, mute);
        if (mute) {
            DCMessage.sendWarning(player, "Muted your own teleportation effects.");
        } else {
            DCMessage.sendGeneral(player, "Unmuted your teleportation effects.");
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        boolean admin = isAdmin(sender);
        String filterRarity = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : null;
        String equippedId = (sender instanceof Player p) ? manager.getSelectedEffectId(p) : null;

        List<String> rarities = filterRarity != null
                ? List.of(filterRarity)
                : manager.getRegistry().getOrderedRarities(admin);

        sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
        sender.sendMessage(ColorUtil.colorize("&#38BDF8&lDragoncote Teleport Effects"));

        int shownCount = 0;
        for (String rarity : rarities) {
            List<TpEffectEntry> entries = manager.getRegistry().getByRarity(rarity, !admin);
            if (entries.isEmpty()) continue;

            sender.sendMessage(ColorUtil.colorize(" " + formatRarityHeader(rarity) + " &7(" + entries.size() + ")"));
            for (TpEffectEntry entry : entries) {
                shownCount++;
                StringBuilder status = new StringBuilder();
                if (entry.getId().equalsIgnoreCase(equippedId)) {
                    status.append(" &a✔ EQUIPPED");
                } else if (sender instanceof Player p && TpEffectRegistry.hasPermission(p, entry)) {
                    status.append(" &2Unlocked");
                } else {
                    status.append(" &8Locked");
                }
                if (admin) {
                    if (!entry.isEnabled()) status.append(" &c[DISABLED]");
                    if (entry.isHidden()) status.append(" &e[HIDDEN]");
                }
                sender.sendMessage(ColorUtil.colorize("  &8• &f" + entry.getId() + " &8→ " + entry.getRawDisplayName() + status));
            }
        }

        if (shownCount == 0) {
            DCMessage.sendInfo(sender, filterRarity != null
                    ? "No teleport effects found for rarity '" + filterRarity + "'."
                    : "No teleport effects loaded.");
        }
        sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            DCMessage.sendHelp(sender, "Usage: /tpeffect info <id>");
            return;
        }
        TpEffectEntry entry = manager.getRegistry().getById(args[1]);
        if (entry == null) {
            DCMessage.sendError(sender, "Unknown teleport effect '" + args[1] + "'.");
            return;
        }

        sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
        sender.sendMessage(ColorUtil.colorize("&#38BDF8&lEffect Info: " + entry.getRawDisplayName() + " &7(" + entry.getId() + ")"));
        sender.sendMessage(ColorUtil.colorize(" &7Rarity: " + formatRarityHeader(entry.getRarity()) + " &8(" + entry.getSourceFile() + ")"));
        sender.sendMessage(ColorUtil.colorize(" &7Type: &b" + entry.getType().name()
                + (entry.isCinematic() ? " &8[" + entry.getCinematicStyle() + "]" : " &8[" + entry.getMythicMob() + "]")));
        sender.sendMessage(ColorUtil.colorize(" &7Permission: &e" + entry.getPermission()));
        sender.sendMessage(ColorUtil.colorize(" &7State: " + (entry.isEnabled() ? "&aEnabled" : "&cDisabled")
                + (entry.isHidden() ? " &e(Hidden)" : " &7(Visible)")));
        if (!entry.getRawLore().isEmpty()) {
            sender.sendMessage(ColorUtil.colorize(" &7Voucher Lore Preview:"));
            for (String line : entry.getColoredLore()) {
                sender.sendMessage("  " + line);
            }
        }
        sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
    }

    private void handlePreview(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            DCMessage.sendError(sender, "Only players can preview teleport effects in-world.");
            return;
        }
        if (args.length < 2) {
            DCMessage.sendHelp(sender, "Usage: /tpeffect preview <id>");
            return;
        }
        TpEffectEntry entry = manager.getRegistry().getById(args[1]);
        if (entry == null) {
            DCMessage.sendError(player, "Unknown teleport effect '" + args[1] + "'.");
            return;
        }
        if (!entry.isEnabled() && !isAdmin(player)) {
            DCMessage.sendError(player, "That teleport effect is currently disabled.");
            return;
        }

        Location frontLoc = findSafeForwardLocation(player.getLocation(), 3.2, false);
        frontLoc.setYaw(Location.normalizeYaw(player.getLocation().getYaw() + 180f));
        manager.previewStationary(player, frontLoc, entry);
        DCMessage.sendGeneral(player, "Previewing " + entry.getRawDisplayName() + " &fin front of you.");
    }

    private void handleTest(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (!(sender instanceof Player player)) {
            DCMessage.sendError(sender, "Only players can run /tpeffect test.");
            return;
        }
        if (args.length < 2) {
            DCMessage.sendHelp(sender, "Usage: /tpeffect test <id> [here|forward|<x> <y> <z>]");
            return;
        }

        TpEffectEntry entry = manager.getRegistry().getById(args[1]);
        if (entry == null) {
            DCMessage.sendError(player, "Unknown teleport effect '" + args[1] + "'.");
            return;
        }

        if (args.length >= 3 && args[2].equalsIgnoreCase("here")) {
            Location frontLoc = findSafeForwardLocation(player.getLocation(), 3.2, false);
            frontLoc.setYaw(Location.normalizeYaw(player.getLocation().getYaw() + 180f));
            manager.previewStationary(player, frontLoc, entry);
            DCMessage.sendGeneral(player, "Running stationary test for " + entry.getRawDisplayName() + "&f.");
            return;
        }

        Location from = player.getLocation().clone();
        Location to;
        if (args.length >= 5) {
            try {
                double x = Double.parseDouble(args[2]);
                double y = Double.parseDouble(args[3]);
                double z = Double.parseDouble(args[4]);
                to = new Location(from.getWorld(), x, y, z, from.getYaw(), from.getPitch());
            } catch (NumberFormatException ex) {
                DCMessage.sendError(player, "Invalid coordinates. Usage: /tpeffect test <id> <x> <y> <z>");
                return;
            }
        } else {
            to = findSafeForwardLocation(from, 18.0, true);
        }

        DCMessage.sendGeneral(player, "Launching full test for " + entry.getRawDisplayName() + "&f!");
        if (entry.isCinematic()) {
            manager.getCinematicEngine().playTeleportSequence(player, from, to, entry);
        } else {
            manager.playStandardMythicEffect(from, to, entry);
            if (!to.equals(from)) {
                player.setMetadata("no_tpeffect", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                player.teleport(to);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) player.removeMetadata("no_tpeffect", plugin);
                }, 4L);
            }
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 3) {
            DCMessage.sendHelp(sender, "Usage: /tpeffect give <player> <id> [voucher]");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            DCMessage.sendError(sender, "Player '" + args[1] + "' is not online.");
            return;
        }

        TpEffectEntry entry = manager.getRegistry().getById(args[2]);
        if (entry == null) {
            DCMessage.sendError(sender, "Unknown teleport effect '" + args[2] + "'.");
            return;
        }

        if (args.length >= 4 && args[3].equalsIgnoreCase("voucher")) {
            ItemStack voucher = entry.buildVoucherItem(plugin);
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(voucher);
            if (!overflow.isEmpty()) {
                overflow.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
            }
            DCMessage.sendGeneral(sender, "Gave voucher for " + entry.getRawDisplayName() + " &fto &b" + target.getName() + "&f.");
            DCMessage.sendGeneral(target, "You received a voucher for " + entry.getRawDisplayName() + "&f!");
            return;
        }

        if (manager.grantEffectPermission(target, entry)) {
            DCMessage.sendGeneral(sender, "Granted &b" + target.getName() + " &funlock permission for " + entry.getRawDisplayName() + "&f.");
            DCMessage.sendGeneral(target, "You unlocked the " + entry.getRawDisplayName() + " &fteleport effect!");
        } else {
            DCMessage.sendWarning(sender, "LuckPerms not detected — permission node &e" + entry.getPermission() + " &fmust be granted manually.");
        }
    }

    private void handleToggleEnable(CommandSender sender, String[] args, boolean enable) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            manager.setRuntimeDisabled(!enable);
            if (enable) {
                DCMessage.sendGeneral(sender, "Globally &aenabled &fteleportation effects.");
            } else {
                DCMessage.sendWarning(sender, "Globally &cdisabled &fteleportation effects.");
            }
            return;
        }

        TpEffectEntry entry = manager.getRegistry().getById(args[1]);
        if (entry == null) {
            DCMessage.sendError(sender, "Unknown teleport effect '" + args[1] + "'.");
            return;
        }
        boolean saved = manager.toggleEnabled(entry, enable);
        DCMessage.sendGeneral(sender, (enable ? "&aEnabled" : "&cDisabled") + " &feffect " + entry.getRawDisplayName()
                + (saved ? " &7(saved to " + entry.getSourceFile() + ")" : " &e(runtime only)"));
    }

    private void handleToggleHide(CommandSender sender, String[] args, boolean hide) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            DCMessage.sendHelp(sender, "Usage: /tpeffect " + (hide ? "hide" : "show") + " <id>");
            return;
        }
        TpEffectEntry entry = manager.getRegistry().getById(args[1]);
        if (entry == null) {
            DCMessage.sendError(sender, "Unknown teleport effect '" + args[1] + "'.");
            return;
        }
        boolean saved = manager.toggleHidden(entry, hide);
        DCMessage.sendGeneral(sender, (hide ? "&eHid" : "&aUnhid") + " &feffect " + entry.getRawDisplayName() + " &fin Wardrobe/listings"
                + (saved ? " &7(saved to " + entry.getSourceFile() + ")" : ""));
    }

    private void handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) return;
        manager.reload();
        int total = manager.getRegistry().getAll().size();
        int visible = manager.getRegistry().getVisible().size();
        DCMessage.sendGeneral(sender, "Reloaded &e" + total + " &fteleportation effects (&a" + visible + " &factive/visible) from &fDCEssentials/tpeffects/&f.");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
        sender.sendMessage(ColorUtil.colorize("&#38BDF8&lDragoncote Teleport Effects"));
        sender.sendMessage(ColorUtil.colorize(" &b/tpeffect list [rarity] &8- &7Browse teleportation effects"));
        sender.sendMessage(ColorUtil.colorize(" &b/tpeffect set <id> &8- &7Equip an unlocked effect"));
        sender.sendMessage(ColorUtil.colorize(" &b/tpeffect clear &8- &7Unequip your active effect"));
        sender.sendMessage(ColorUtil.colorize(" &b/tpeffect preview <id> &8- &7Preview an effect in front of you"));
        sender.sendMessage(ColorUtil.colorize(" &b/tpeffect info <id> &8- &7Inspect effect stats & voucher lore"));
        sender.sendMessage(ColorUtil.colorize(" &b/tpeffect mute | unmute &8- &7Silence your own teleport effects"));
        if (isAdmin(sender)) {
            sender.sendMessage(ColorUtil.colorize(" &6/tpeffect test <id> [here|forward|x y z] &8- &7Test full warp/cinematic"));
            sender.sendMessage(ColorUtil.colorize(" &6/tpeffect give <player> <id> [voucher] &8- &7Grant perm or voucher"));
            sender.sendMessage(ColorUtil.colorize(" &6/tpeffect enable|disable [id] &8- &7Toggle module or specific effect"));
            sender.sendMessage(ColorUtil.colorize(" &6/tpeffect hide|show <id> &8- &7Toggle Wardrobe visibility"));
            sender.sendMessage(ColorUtil.colorize(" &6/tpeffect reload &8- &7Reload all tpeffects/*.yml configs"));
        }
        sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
    }

    // =========================================================================
    // TAB COMPLETION
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = isAdmin(sender);
        if (args.length == 1) {
            return filterPrefix(admin ? ADMIN_SUBCOMMANDS : PLAYER_SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "set", "preview" -> {
                    List<String> ids = new ArrayList<>();
                    for (TpEffectEntry entry : manager.getRegistry().getAll()) {
                        if (!entry.isEnabled() && !admin) continue;
                        if (entry.isHidden() && !admin) continue;
                        if (sub.equals("set") && !admin && sender instanceof Player p
                                && !TpEffectRegistry.hasPermission(p, entry)) {
                            continue;
                        }
                        ids.add(entry.getId());
                    }
                    yield filterPrefix(ids, args[1]);
                }
                case "info" -> filterPrefix(visibleOrAdminIds(admin), args[1]);
                case "list" -> filterPrefix(manager.getRegistry().getOrderedRarities(admin), args[1]);
                case "clear", "give" -> admin ? filterPrefix(onlinePlayerNames(), args[1]) : Collections.emptyList();
                case "test", "enable", "disable", "hide", "show" ->
                        admin ? filterPrefix(manager.getRegistry().getAllIds(), args[1]) : Collections.emptyList();
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3) {
            return switch (sub) {
                case "give" -> admin ? filterPrefix(manager.getRegistry().getAllIds(), args[2]) : Collections.emptyList();
                case "set" -> admin ? filterPrefix(onlinePlayerNames(), args[2]) : Collections.emptyList();
                case "test" -> admin ? filterPrefix(List.of("forward", "here"), args[2]) : Collections.emptyList();
                default -> Collections.emptyList();
            };
        }

        if (args.length == 4 && sub.equals("give") && admin) {
            return filterPrefix(List.of("voucher"), args[3]);
        }

        return Collections.emptyList();
    }

    private List<String> visibleOrAdminIds(boolean admin) {
        List<String> ids = new ArrayList<>();
        for (TpEffectEntry e : admin ? manager.getRegistry().getAll() : manager.getRegistry().getVisible()) {
            ids.add(e.getId());
        }
        return ids;
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private List<String> filterPrefix(Collection<String> options, String prefix) {
        String low = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase(Locale.ROOT).startsWith(low)) {
                result.add(opt);
            }
        }
        return result;
    }

    private boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("dccosmetics.admin") || sender.hasPermission("dcessentials.admin");
    }

    private boolean requireAdmin(CommandSender sender) {
        if (isAdmin(sender)) return true;
        DCMessage.sendError(sender, "You do not have permission to execute that command.");
        return false;
    }

    private String formatRarityHeader(String rarity) {
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "uncommon" -> "&a&lUNCOMMON";
            case "rare" -> "&9&lRARE";
            case "epic" -> "&5&lEPIC";
            case "legendary" -> "&6&lLEGENDARY";
            case "mastery" -> "&#A855F7&lMASTERY";
            default -> "&7&l" + rarity.toUpperCase(Locale.ROOT);
        };
    }

    private Location findSafeForwardLocation(Location origin, double dist, boolean snapToSurface) {
        World world = origin.getWorld();
        if (world == null) return origin.clone();

        Vector horizontal = origin.getDirection().setY(0);
        if (horizontal.lengthSquared() < 1e-4) {
            horizontal = new Vector(0, 0, 1);
        } else {
            horizontal.normalize();
        }

        Location candidate = origin.clone().add(horizontal.multiply(dist));
        if (!snapToSurface) {
            return candidate;
        }

        // Gently search a few blocks up/down so test destinations never spawn inside solid terrain
        int baseX = candidate.getBlockX();
        int baseY = candidate.getBlockY();
        int baseZ = candidate.getBlockZ();
        for (int dy = 0; dy <= 10; dy++) {
            for (int sign : (dy == 0 ? new int[]{0} : new int[]{1, -1})) {
                int y = baseY + (dy * sign);
                if (y <= world.getMinHeight() + 1 || y >= world.getMaxHeight() - 2) continue;
                Block ground = world.getBlockAt(baseX, y - 1, baseZ);
                Block feet = world.getBlockAt(baseX, y, baseZ);
                Block head = world.getBlockAt(baseX, y + 1, baseZ);
                if (ground.getType().isSolid() && !feet.getType().isSolid() && !head.getType().isSolid()) {
                    Location safe = new Location(world, baseX + 0.5, y, baseZ + 0.5, origin.getYaw(), origin.getPitch());
                    return safe;
                }
            }
        }
        return candidate;
    }
}
