package dc.dcessentials.commands;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.PlayerProfile;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.teleportation.KeystoneDetector;
import dc.dcessentials.modules.teleportation.KeystoneProfile;
import dc.dcessentials.modules.teleportation.KeystoneTier;
import dc.dcessentials.modules.teleportation.LegacyKeystoneConverter;
import dc.dcessentials.modules.teleportation.SpendKind;
import dc.dcessentials.modules.teleportation.SpendResult;
import dc.dcessentials.modules.teleportation.TeleportationManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public class KeystoneCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        TeleportationManager teleManager = DCCosmetics.getInstance().getTeleportationManager();

        if (args.length == 0 || args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("open") || args[0].equalsIgnoreCase("matrix")) {
            if (sender instanceof Player player) {
                openKeystoneDeluxeMenu(player);
            } else {
                DCMessage.sendHelp(sender, "Usage from console: &#FFFFFF/tesseract <charge|set|take|remove|reset|clearheat|tier|heat|microcharge> ...");
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("sync_tier")) {
            DCMessage.sendError(sender, "Subcommand removed: Keystone tier derives strictly from the equipped Keystone item.");
            return true;
        }

        if (sub.equals("clearheat") || sub.equals("resetheat")) {
            if (!sender.hasPermission("dragoncote.admin")) {
                DCMessage.sendError(sender, "You do not have permission to execute this command.");
                return true;
            }
            Player target = sender instanceof Player p ? p : null;
            if (args.length >= 2) {
                target = Bukkit.getPlayer(args[1]);
            }
            if (target == null) {
                DCMessage.sendError(sender, "Player not found.");
                return true;
            }
            KeystoneProfile profile = teleManager.getProfile(target);
            profile.setHeat(0);
            teleManager.getPersistence().saveNow(profile);
            dc.dcessentials.modules.teleportation.KeystoneLoreTokens.scheduleInventoryRefresh(target);
            DCMessage.sendGeneral(target, "Timeline Heat purged to &#FFFFFF0/100&#0CBF93.");
            if (!sender.equals(target)) {
                DCMessage.sendGeneral(sender, "Purged heat for &#FFFFFF" + target.getName() + "&#0CBF93.");
            }
            return true;
        }

        if (sub.equals("heat")) {
            if (!sender.hasPermission("dragoncote.admin")) {
                DCMessage.sendError(sender, "You do not have permission to execute this command.");
                return true;
            }
            if (args.length < 3) {
                DCMessage.sendHelp(sender, "Usage: &#FFFFFF/keystone heat <player> <delta>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                DCMessage.sendError(sender, "Player not found.");
                return true;
            }
            try {
                int delta = Integer.parseInt(args[2]);
                KeystoneProfile profile = teleManager.getProfile(target);
                if (delta >= 0) {
                    profile.addHeat(delta);
                } else {
                    profile.reduceHeat(-delta);
                }
                teleManager.getPersistence().saveNow(profile);
                dc.dcessentials.modules.teleportation.KeystoneLoreTokens.scheduleInventoryRefresh(target);
                DCMessage.sendGeneral(sender, "Adjusted &#FFFFFF" + target.getName() + "'s &#0CBF93heat by &#FFFFFF" + (delta >= 0 ? "+" + delta : delta) + " &#0CBF93(Now: &#EF4444" + profile.getHeat() + "/100 🔥&#0CBF93).");
            } catch (NumberFormatException e) {
                DCMessage.sendError(sender, "Invalid heat delta.");
            }
            return true;
        }

        if (sub.equals("microcharge") || sub.equals("micro")) {
            if (!sender.hasPermission("dragoncote.admin")) {
                DCMessage.sendError(sender, "You do not have permission to execute this command.");
                return true;
            }
            if (args.length < 3) {
                DCMessage.sendHelp(sender, "Usage: &#FFFFFF/keystone microcharge <player> <delta>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                DCMessage.sendError(sender, "Player not found.");
                return true;
            }
            try {
                int delta = Integer.parseInt(args[2]);
                KeystoneProfile profile = teleManager.getProfile(target);
                int effectiveMax = teleManager.getEnergyService().getEffectiveMaxEnergy(target, profile);
                if (delta >= 0) {
                    profile.refundMicro(delta, effectiveMax);
                } else {
                    profile.spendMicro(-delta, 0, 1.0);
                }
                teleManager.getPersistence().saveNow(profile);
                dc.dcessentials.modules.teleportation.KeystoneLoreTokens.scheduleInventoryRefresh(target);
                DCMessage.sendGeneral(sender, "Adjusted &#FFFFFF" + target.getName() + "'s &#0CBF93microcharges by &#FFFFFF" + (delta >= 0 ? "+" + delta : delta) + " " + dc.dcessentials.modules.teleportation.KeystoneLoreTokens.MICRO_ICON + " &#0CBF93(Now: &#38BDF8" + profile.getMicro() + "/1000 " + dc.dcessentials.modules.teleportation.KeystoneLoreTokens.MICRO_ICON + " &8| &f" + profile.getEnergy() + "/" + effectiveMax + " 🗲&#0CBF93).");
            } catch (NumberFormatException e) {
                DCMessage.sendError(sender, "Invalid microcharge delta.");
            }
            return true;
        }

        if (sub.equals("charge") || sub.equals("addenergy")) {
            if (!sender.hasPermission("dragoncote.admin")) {
                DCMessage.sendError(sender, "You do not have permission to execute this command.");
                return true;
            }
            if (args.length < 3) {
                DCMessage.sendHelp(sender, "Usage: &#FFFFFF/keystone charge <player> <amount>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                DCMessage.sendError(sender, "Player not found.");
                return true;
            }
            try {
                int amt = Integer.parseInt(args[2]);
                KeystoneProfile profile = teleManager.getProfile(target);
                int effectiveMax = teleManager.getEnergyService().getEffectiveMaxEnergy(target, profile);
                if (amt >= 0) {
                    profile.addCharges(amt, effectiveMax);
                    if (amt > 0 && profile.getHeat() > 0 && profile.getTotalMicro() > 0) {
                        profile.setHeat(0, teleManager.getOverloadClearBelow());
                        profile.setOverloaded(false);
                        DCMessage.sendInfo(target, "Active Tesseract infusion cooled Timeline Heat to &#FFFFFF0/100 🔥&#BAE6FD.");
                    }
                    DCMessage.sendGeneral(sender, "Added &#FFFFFF" + amt + " &#0CBF93energy to &#FFFFFF" + target.getName() + " &#0CBF93(Total: &#FFFFFF" + profile.getEnergy() + "/" + effectiveMax + " 🗲&#0CBF93).");
                    DCMessage.sendGeneral(target, "Matrix charged with &#FFFFFF+" + amt + " &#38BDF8Tesseract Energy 🗲&#0CBF93.");
                } else {
                    int removed = profile.removeCharges(-amt);
                    DCMessage.sendGeneral(sender, "Removed &#FFFFFF" + removed + " &#0CBF93energy from &#FFFFFF" + target.getName() + " &#0CBF93(Total: &#FFFFFF" + profile.getEnergy() + "/" + effectiveMax + " 🗲&#0CBF93).");
                    DCMessage.sendGeneral(target, "Matrix drained by &#FFFFFF-" + removed + " &#38BDF8Tesseract Energy 🗲&#0CBF93.");
                }
                teleManager.getPersistence().saveNow(profile);
                dc.dcessentials.modules.teleportation.KeystoneLoreTokens.scheduleInventoryRefresh(target);
            } catch (NumberFormatException e) {
                DCMessage.sendError(sender, "Invalid energy amount.");
            }
            return true;
        }

        if (sub.equals("set") || sub.equals("setenergy")) {
            if (!sender.hasPermission("dragoncote.admin")) {
                DCMessage.sendError(sender, "You do not have permission to execute this command.");
                return true;
            }
            if (args.length < 3) {
                DCMessage.sendHelp(sender, "Usage: &#FFFFFF/" + label + " set <player> <amount>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                DCMessage.sendError(sender, "Player not found.");
                return true;
            }
            try {
                int amt = Math.max(0, Integer.parseInt(args[2]));
                KeystoneProfile profile = teleManager.getProfile(target);
                int effectiveMax = teleManager.getEnergyService().getEffectiveMaxEnergy(target, profile);
                int cap = Math.max(effectiveMax, KeystoneProfile.ABSOLUTE_MAX_ENERGY);
                int clamped = Math.min(amt, cap);
                profile.setEnergy(clamped);
                if (clamped == 0) {
                    profile.setMicro(0);
                } else if (profile.getHeat() > 0 && profile.getTotalMicro() > 0) {
                    profile.setHeat(0, teleManager.getOverloadClearBelow());
                    profile.setOverloaded(false);
                }
                teleManager.getPersistence().saveNow(profile);
                dc.dcessentials.modules.teleportation.KeystoneLoreTokens.scheduleInventoryRefresh(target);
                DCMessage.sendGeneral(sender, "Set &#FFFFFF" + target.getName() + "'s &#0CBF93Tesseract Energy to &#FFFFFF" + profile.getEnergy() + "/" + effectiveMax + " 🗲&#0CBF93.");
                DCMessage.sendGeneral(target, "Your Tesseract Energy was set to &#FFFFFF" + profile.getEnergy() + " &#38BDF8🗲&#0CBF93.");
            } catch (NumberFormatException e) {
                DCMessage.sendError(sender, "Invalid energy amount.");
            }
            return true;
        }

        if (sub.equals("take") || sub.equals("remove") || sub.equals("sub")) {
            if (!sender.hasPermission("dragoncote.admin")) {
                DCMessage.sendError(sender, "You do not have permission to execute this command.");
                return true;
            }
            if (args.length < 3) {
                DCMessage.sendHelp(sender, "Usage: &#FFFFFF/" + label + " take <player> <amount>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                DCMessage.sendError(sender, "Player not found.");
                return true;
            }
            try {
                int amt = Math.max(0, Integer.parseInt(args[2]));
                KeystoneProfile profile = teleManager.getProfile(target);
                int effectiveMax = teleManager.getEnergyService().getEffectiveMaxEnergy(target, profile);
                int removed = profile.removeCharges(amt);
                if (profile.getEnergy() == 0 && amt > removed) {
                    profile.setMicro(0);
                }
                teleManager.getPersistence().saveNow(profile);
                dc.dcessentials.modules.teleportation.KeystoneLoreTokens.scheduleInventoryRefresh(target);
                DCMessage.sendGeneral(sender, "Removed &#FFFFFF" + removed + " &#0CBF93energy from &#FFFFFF" + target.getName() + " &#0CBF93(Total: &#FFFFFF" + profile.getEnergy() + "/" + effectiveMax + " 🗲&#0CBF93).");
                DCMessage.sendGeneral(target, "Matrix drained by &#FFFFFF-" + removed + " &#38BDF8Tesseract Energy 🗲&#0CBF93.");
            } catch (NumberFormatException e) {
                DCMessage.sendError(sender, "Invalid energy amount.");
            }
            return true;
        }

        if (sub.equals("reset")) {
            if (!sender.hasPermission("dragoncote.admin")) {
                DCMessage.sendError(sender, "You do not have permission to execute this command.");
                return true;
            }
            Player target = sender instanceof Player p ? p : null;
            if (args.length >= 2) {
                target = Bukkit.getPlayer(args[1]);
            }
            if (target == null) {
                DCMessage.sendError(sender, "Player not found.");
                return true;
            }
            KeystoneProfile profile = teleManager.getProfile(target);
            profile.setEnergy(0);
            profile.setMicro(0);
            teleManager.getPersistence().saveNow(profile);
            dc.dcessentials.modules.teleportation.KeystoneLoreTokens.scheduleInventoryRefresh(target);
            DCMessage.sendGeneral(sender, "Reset &#FFFFFF" + target.getName() + "'s &#0CBF93Tesseract Energy to &#FFFFFF0 🗲 (0 " + dc.dcessentials.modules.teleportation.KeystoneLoreTokens.MICRO_ICON + ")&#0CBF93.");
            DCMessage.sendGeneral(target, "Your Tesseract Energy was reset to &#FFFFFF0 &#38BDF8🗲&#0CBF93.");
            return true;
        }

        if (sub.equals("tier") || sub.equals("settier")) {
            if (!sender.hasPermission("dragoncote.admin")) {
                DCMessage.sendError(sender, "You do not have permission to execute this command.");
                return true;
            }
            if (args.length < 3) {
                DCMessage.sendHelp(sender, "Usage: &#FFFFFF/keystone tier <player> <NONE|RARE|EPIC|LEGENDARY>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                DCMessage.sendError(sender, "Player not found.");
                return true;
            }
            KeystoneTier tier = KeystoneTier.fromString(args[2]);
            KeystoneProfile profile = teleManager.getProfile(target);
            profile.setTier(tier);
            teleManager.getPersistence().saveNow(profile);
            DCMessage.sendGeneral(sender, "Set &#FFFFFF" + target.getName() + "'s &#0CBF93Keystone tier to &#FFFFFF" + tier.name() + "&#0CBF93.");
            DCMessage.sendGeneral(target, "Your Keystone matrix has been tuned to " + tier.getDisplayName() + "&#0CBF93.");
            return true;
        }

        if (sub.equals("debugspend")) {
            if (!sender.hasPermission("dragoncote.admin")) {
                DCMessage.sendError(sender, "You do not have permission to execute this command.");
                return true;
            }
            if (!(sender instanceof Player player)) {
                DCMessage.sendError(sender, "Players only.");
                return true;
            }
            if (args.length < 3) {
                DCMessage.sendHelp(sender, "Usage: &#FFFFFF/keystone debugspend <WARP|ABILITY> <baseCostMicro>");
                return true;
            }
            SpendKind kind;
            try {
                kind = SpendKind.valueOf(args[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                DCMessage.sendError(sender, "Invalid SpendKind. Use WARP or ABILITY.");
                return true;
            }
            int baseCost;
            try {
                baseCost = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                DCMessage.sendError(sender, "Invalid baseCostMicro.");
                return true;
            }
            SpendResult res = teleManager.getEnergyService().spend(player, kind, baseCost);
            player.sendMessage(ColorUtil.colorize("&8[&bKeystoneDebug&8] &7allowed=&f" + res.authorized()
                    + " &7effCost=&b" + res.effectiveCostMicro() + dc.dcessentials.modules.teleportation.KeystoneLoreTokens.MICRO_ICON
                    + " &7cracked=&e" + res.chargesCracked()
                    + " &7mPaid=&a" + res.microPaid()
                    + " &7shortfall=&c" + res.microShortfall()
                    + " &7overdraw=&6" + res.overdraw()
                    + " &7heatAdd=&c+" + res.heatAdded()
                    + " &7rem=&f" + res.remainingEnergy() + "🗲/" + res.remainingMicro() + dc.dcessentials.modules.teleportation.KeystoneLoreTokens.MICRO_ICON
                    + " &7heat=&c" + res.resultingHeat()
                    + " &7overloaded=&4" + res.overloaded()));
            return true;
        }

        if (sub.equals("debugtp")) {
            if (!sender.hasPermission("dragoncote.admin")) {
                DCMessage.sendError(sender, "You do not have permission to execute this command.");
                return true;
            }
            boolean next = !teleManager.isDebugTeleportCauses();
            teleManager.setDebugTeleportCauses(next);
            DCMessage.sendGeneral(sender, "Teleport cause debug logging: " + (next ? "&#10B981ON" : "&#EF4444OFF"));
            return true;
        }

        if (sub.equals("status") && sender instanceof Player player) {
            KeystoneProfile profile = teleManager.getProfile(player);
            int effectiveMax = teleManager.getEnergyService().getEffectiveMaxEnergy(player, profile);
            player.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
            DCMessage.sendInfo(player, "Keystone Matrix Status");
            player.sendMessage(ColorUtil.colorize("  &8• &7Tier: " + profile.getTier().getDisplayName() + " &8(" + profile.getTier().getKeystoneName() + "&8)"));
            player.sendMessage(ColorUtil.colorize("  &8• &7Energy: &f" + profile.usableEnergy() + " &7/ &f" + effectiveMax + " &#38BDF8🗲"));
            if (profile.parkedEnergy() > 0) {
                player.sendMessage(ColorUtil.colorize("  &8• &7Parked Energy: &#FBBF24" + profile.parkedEnergy() + " 🗲 &8(Exceeds current tier cap)"));
            }
            player.sendMessage(ColorUtil.colorize("  &8• &7Microcharge Buffer: &#38BDF8" + profile.getMicro() + " &7/ &f1,000 " + dc.dcessentials.modules.teleportation.KeystoneLoreTokens.MICRO_ICON));
            player.sendMessage(ColorUtil.colorize("  &8• &7Heat: &#EF4444" + profile.getHeat() + " &7/ &c100 🔥 &7(" + profile.getStability() + "&7)"));
            if (profile.isOverloaded()) {
                player.sendMessage(ColorUtil.colorize("  &8• &7State: &#EF4444&lOVERLOADED &7(Cool to ≤50🔥 to unlock)"));
            }
            player.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
            return true;
        }

        if (sender instanceof Player p) {
            openKeystoneDeluxeMenu(p);
        }
        return true;
    }

    private void openKeystoneDeluxeMenu(Player player) {
        TeleportationManager teleManager = DCCosmetics.getInstance().getTeleportationManager();
        KeystoneProfile profile = teleManager.getProfile(player);
        KeystoneTier tier = profile.getTier();

        // 1. Check profile equipment slot "keystone"
        if (tier == null || tier == KeystoneTier.NONE) {
            PlayerProfile playerProfile = DCCosmetics.getInstance().getProfileManager().getProfile(player);
            if (playerProfile != null) {
                ItemStack equipped = playerProfile.getEquipment("keystone");
                if (equipped != null && !equipped.getType().isAir()) {
                    if (KeystoneDetector.isLegacy(equipped)) {
                        equipped = LegacyKeystoneConverter.convert(equipped).orElse(equipped);
                        playerProfile.setEquipment("keystone", equipped);
                        DCCosmetics.getInstance().getProfileManager().saveProfile(player);
                    }
                    KeystoneTier detected = KeystoneDetector.tierOf(equipped);
                    if (detected != KeystoneTier.NONE) {
                        profile.setTier(detected);
                        tier = detected;
                        teleManager.getPersistence().saveNow(profile);
                    }
                }
            }
        }

        // 2. Check player inventory items (Auto-equip if carried)
        if (tier == null || tier == KeystoneTier.NONE) {
            ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack it = contents[slot];
                if (it == null || it.getType().isAir()) continue;
                if (KeystoneDetector.isLegacy(it)) {
                    it = LegacyKeystoneConverter.convert(it).orElse(it);
                    player.getInventory().setItem(slot, it);
                }
                KeystoneTier detected = KeystoneDetector.tierOf(it);
                if (detected != KeystoneTier.NONE) {
                    PlayerProfile playerProfile = DCCosmetics.getInstance().getProfileManager().getProfile(player);
                    if (playerProfile != null) {
                        ItemStack toEquip = it.clone();
                        toEquip.setAmount(1);
                        if (it.getAmount() > 1) {
                            it.setAmount(it.getAmount() - 1);
                        } else {
                            player.getInventory().setItem(slot, null);
                        }
                        playerProfile.setEquipment("keystone", toEquip);
                        DCCosmetics.getInstance().getProfileManager().saveProfile(player);
                    }
                    profile.setTier(detected);
                    tier = detected;
                    if (profile.getHeat() > 0 && profile.getTotalMicro() > 0) {
                        profile.setHeat(0, teleManager.getOverloadClearBelow());
                        profile.setOverloaded(false);
                        DCMessage.sendInfo(player, "Parked Tesseract matrix reactivated — &#10B981Temporal Heat reset to 0 🔥&#BAE6FD.");
                    }
                    teleManager.getPersistence().saveNow(profile);
                    DCMessage.sendGeneral(player, "Automatically equipped your " + detected.getDisplayName() + " &#0CBF93from inventory into your &#38BDF8/profile&#0CBF93!");
                    break;
                }
            }
        }

        if (tier == null || tier == KeystoneTier.NONE) {
            DCMessage.sendWarning(player, "You do not have a Keystone equipped! Equip a Keystone in your &#38BDF8/profile &#FDE047to access the matrix.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
            return;
        }

        dc.dcessentials.modules.teleportation.gui.KeystoneGUI.open(player);
    }
}
