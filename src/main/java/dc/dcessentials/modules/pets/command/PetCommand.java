package dc.dcessentials.modules.pets.command;

import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.model.ItemRarity;
import dc.dcessentials.modules.pets.PetManager;
import dc.dcessentials.modules.pets.gui.PetMenuGUI;
import dc.dcessentials.modules.pets.model.PetSpec;
import dc.dcessentials.modules.pets.model.PetVisibilityMode;
import dc.dcessentials.modules.pets.model.PlayerPetData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Primary /pet command & tab completer adhering strictly to DCMessage guidelines.
 */
public class PetCommand implements CommandExecutor, TabCompleter {

    private final PetManager petManager;

    public PetCommand(PetManager petManager) {
        this.petManager = petManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                DCMessage.sendError(sender, "Console must use /pet give, unlockall, addxp, or reload.");
                return true;
            }
            new PetMenuGUI(player, petManager, 0).open();
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> {
                sendHelp(sender);
                return true;
            }
            case "menu", "gui", "wardrobe" -> {
                if (!(sender instanceof Player player)) return true;
                new PetMenuGUI(player, petManager, 0).open();
                return true;
            }
            case "summon", "equip" -> {
                if (!(sender instanceof Player player)) return true;
                if (args.length < 2) {
                    DCMessage.sendWarning(player, "Usage: /pet summon <companion>");
                    return true;
                }
                String petId = PlayerPetData.canonicalizePetId(args[1]);
                PlayerPetData data = petManager.getData(player.getUniqueId());
                if (petId == null || (!data.hasPet(petId) && !player.hasPermission("dce.pet.admin"))) {
                    DCMessage.sendError(player, "You have not unlocked that companion yet.");
                    return true;
                }
                petManager.summonPet(player, petId, false);
                return true;
            }
            case "dismiss", "hide" -> {
                if (!(sender instanceof Player player)) return true;
                petManager.dismissPet(player, false);
                return true;
            }
            case "visibility", "vis" -> {
                if (!(sender instanceof Player player)) return true;
                PlayerPetData data = petManager.getData(player.getUniqueId());
                PetVisibilityMode mode = args.length >= 2
                        ? PetVisibilityMode.fromString(args[1])
                        : data.getVisibilityMode().next();
                data.setVisibilityMode(mode);
                petManager.savePlayerAsync(data);
                DCMessage.sendGeneral(player, "Companion visibility set to &f" + mode.getDescription() + "&#0CBF93.");
                return true;
            }
            case "autocast", "skill" -> {
                if (!(sender instanceof Player player)) return true;
                PlayerPetData data = petManager.getData(player.getUniqueId());
                if (args.length >= 2) {
                    data.setAutoCastSkills("on".equalsIgnoreCase(args[1]) || "true".equalsIgnoreCase(args[1]));
                } else {
                    data.setAutoCastSkills(!data.isAutoCastSkills());
                }
                petManager.savePlayerAsync(data);
                DCMessage.sendGeneral(player, "Companion auto-cast ability "
                        + (data.isAutoCastSkills() ? "&aenabled" : "&cdisabled") + "&#0CBF93.");
                return true;
            }
            case "tracker", "ring" -> {
                if (!(sender instanceof Player player)) return true;
                PlayerPetData data = petManager.getData(player.getUniqueId());
                if (args.length >= 2) {
                    data.setTrackerEnabled("on".equalsIgnoreCase(args[1]) || "true".equalsIgnoreCase(args[1]));
                } else {
                    data.setTrackerEnabled(!data.isTrackerEnabled());
                }
                DCMessage.sendGeneral(player, "Mob target tracker ring "
                        + (data.isTrackerEnabled() ? "&aenabled" : "&cdisabled") + "&#0CBF93.");
                return true;
            }
            case "ally" -> {
                if (!(sender instanceof Player player)) return true;
                if (args.length < 2) {
                    DCMessage.sendWarning(player, "Usage: /pet ally <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    DCMessage.sendError(player, "Player not found online.");
                    return true;
                }
                PlayerPetData data = petManager.getData(player.getUniqueId());
                boolean added = data.toggleAlly(target.getUniqueId());
                petManager.savePlayerAsync(data);
                if (added) {
                    DCMessage.sendGeneral(player, "Added &f" + target.getName() + " &#0CBF93to your Companion Ally Whitelist.");
                } else {
                    DCMessage.sendWarning(player, "Removed &f" + target.getName() + " &#FDE047from your Companion Ally Whitelist.");
                }
                return true;
            }
            case "give" -> {
                if (!sender.hasPermission("dce.pet.admin")) {
                    DCMessage.sendError(sender, "You lack permission to use this command.");
                    return true;
                }
                if (args.length < 3) {
                    DCMessage.sendWarning(sender, "Usage: /pet give <player> <companion> <level> <rarity>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    DCMessage.sendError(sender, "Player not found online.");
                    return true;
                }
                PetSpec spec = petManager.getRegistry().getPet(args[2]);
                if (spec == null) {
                    DCMessage.sendError(sender, "Unknown companion ID: " + args[2]);
                    return true;
                }
                int level = args.length >= 4 ? parseIntOrDefault(args[3], 1) : 1;
                level = Math.max(1, Math.min(spec.getMaxLevel(), level));
                ItemRarity rarity = args.length >= 5 ? ItemRarity.fromString(args[4]) : spec.getRarity();

                ItemStack token = petManager.getRegistry().createPetTokenItem(spec, level, 0.0, rarity);
                target.getInventory().addItem(token);
                DCMessage.sendGeneral(sender, "Gave Level " + level + " " + rarity.getColorHex()
                        + spec.getDisplayName() + " &#0CBF93to &f" + target.getName() + "&#0CBF93.");
                if (!sender.equals(target)) {
                    DCMessage.sendGeneral(target, "You received Level " + level + " "
                            + rarity.getColorHex() + spec.getDisplayName() + "&#0CBF93!");
                }
                return true;
            }
            case "unlockall" -> {
                if (!sender.hasPermission("dce.pet.admin")) {
                    DCMessage.sendError(sender, "You lack permission to use this command.");
                    return true;
                }
                if (args.length < 2) {
                    DCMessage.sendWarning(sender, "Usage: /pet unlockall <player> <level>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    DCMessage.sendError(sender, "Player not found online.");
                    return true;
                }
                int lvl = args.length >= 3 ? Math.max(1, parseIntOrDefault(args[2], 1)) : 1;
                PlayerPetData data = petManager.getData(target.getUniqueId());
                for (PetSpec spec : petManager.getRegistry().getAllPets()) {
                    data.unlockOrUpgradePet(spec.getId(), Math.min(spec.getMaxLevel(), lvl), 0.0, spec.getRarity());
                }
                petManager.savePlayerAsync(data);
                DCMessage.sendGeneral(sender, "Unlocked all " + petManager.getRegistry().getAllPets().size()
                        + " companions at Level " + lvl + " for &f" + target.getName() + "&#0CBF93.");
                return true;
            }
            case "addxp" -> {
                if (!sender.hasPermission("dce.pet.admin")) {
                    DCMessage.sendError(sender, "You lack permission to use this command.");
                    return true;
                }
                if (args.length < 3) {
                    DCMessage.sendWarning(sender, "Usage: /pet addxp <player> <amount>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    DCMessage.sendError(sender, "Player not found online.");
                    return true;
                }
                double amt = Math.max(0.0, parseDoubleOrDefault(args[2], 100.0));
                petManager.addPetXp(target, amt);
                DCMessage.sendGeneral(sender, "Added &f" + Math.round(amt) + " &#0CBF93XP to &f"
                        + target.getName() + "&#0CBF93's active companion.");
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("dce.pet.admin")) {
                    DCMessage.sendError(sender, "You lack permission to use this command.");
                    return true;
                }
                petManager.reload();
                DCMessage.sendGeneral(sender, "Reloaded DCEPets config and all companion blueprints.");
                return true;
            }
            default -> {
                if (sender instanceof Player player) {
                    new PetMenuGUI(player, petManager, 0).open();
                } else {
                    sendHelp(sender);
                }
                return true;
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        DCMessage.sendHelp(sender, "Companions Commands");
        DCMessage.sendInfo(sender, "/pet &8- Open your Companions Wardrobe");
        DCMessage.sendInfo(sender, "/pet summon <companion> &8- Summon an unlocked companion");
        DCMessage.sendInfo(sender, "/pet dismiss &8- Dismiss your active companion");
        DCMessage.sendInfo(sender, "/pet visibility <all|own|hidden> &8- Toggle visual companion visibility");
        DCMessage.sendInfo(sender, "/pet autocast <on|off> &8- Toggle automatic combat abilities");
        DCMessage.sendInfo(sender, "/pet tracker <on|off> &8- Toggle mob target tracker circle");
        DCMessage.sendInfo(sender, "/pet ally <player> &8- Toggle ally immunity & heal sharing");
        if (sender.hasPermission("dce.pet.admin")) {
            DCMessage.sendInfo(sender, "/pet give <player> <companion> <lvl> <rarity> &8- Give pet item token");
            DCMessage.sendInfo(sender, "/pet unlockall <player> <lvl> &8- Unlock all companions");
            DCMessage.sendInfo(sender, "/pet addxp <player> <amount> &8- Grant XP to active pet");
            DCMessage.sendInfo(sender, "/pet reload &8- Reload pets_config & companion files");
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("menu", "summon", "dismiss", "visibility", "autocast", "tracker", "ally", "help"));
            if (sender.hasPermission("dce.pet.admin")) {
                subs.addAll(Arrays.asList("give", "unlockall", "addxp", "reload"));
            }
            return prefixMatch(args[0], subs);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "summon", "equip" -> {
                    List<String> ids = new ArrayList<>();
                    if (sender instanceof Player p) {
                        PlayerPetData data = petManager.getData(p.getUniqueId());
                        if (p.hasPermission("dce.pet.admin")) {
                            petManager.getRegistry().getAllPets().forEach(s -> ids.add(s.getId()));
                        } else {
                            ids.addAll(data.getOwnedPets().keySet());
                        }
                    }
                    return prefixMatch(args[1], ids);
                }
                case "visibility", "vis" -> {
                    return prefixMatch(args[1], Arrays.asList("all", "own_only", "hidden"));
                }
                case "autocast", "skill", "tracker", "ring" -> {
                    return prefixMatch(args[1], Arrays.asList("on", "off"));
                }
                case "ally", "give", "unlockall", "addxp" -> {
                    List<String> names = new ArrayList<>();
                    Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                    return prefixMatch(args[1], names);
                }
            }
        }
        if (args.length == 3 && "give".equals(sub) && sender.hasPermission("dce.pet.admin")) {
            List<String> ids = new ArrayList<>();
            petManager.getRegistry().getAllPets().forEach(s -> ids.add(s.getId()));
            return prefixMatch(args[2], ids);
        }
        if (args.length == 4 && "give".equals(sub) && sender.hasPermission("dce.pet.admin")) {
            return prefixMatch(args[3], Arrays.asList("1", "25", "50", "100"));
        }
        if (args.length == 5 && "give".equals(sub) && sender.hasPermission("dce.pet.admin")) {
            List<String> rars = new ArrayList<>();
            for (ItemRarity r : ItemRarity.values()) rars.add(r.name().toLowerCase(Locale.ROOT));
            return prefixMatch(args[4], rars);
        }
        return Collections.emptyList();
    }

    private List<String> prefixMatch(String token, List<String> options) {
        String low = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase(Locale.ROOT).startsWith(low)) out.add(opt);
        }
        return out;
    }

    private static int parseIntOrDefault(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private static double parseDoubleOrDefault(String s, double def) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }
}
