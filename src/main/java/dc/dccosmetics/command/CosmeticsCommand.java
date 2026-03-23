package dc.dccosmetics.command;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.gui.GuiManager;
import dc.dccosmetics.listener.FootstepListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CosmeticsCommand implements CommandExecutor {

    private final GuiManager guiManager;

    public CosmeticsCommand(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        // =========================================
        // 1. ADMIN COMMAND: /cosmetics reload
        // (Can be run by players OR the console)
        // =========================================
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("dccosmetics.admin")) {
                DCCosmetics.getInstance().reloadConfigs();
                DCCosmetics.getInstance().getLanguageManager().sendMessage(sender, "reloaded");
            } else {
                DCCosmetics.getInstance().getLanguageManager().sendMessage(sender, "no_permission");
            }
            return true;
        }

        // =========================================
        // 4. BLOCKBENCH IMPORTER: /cosmetics import <filename>
        // (Can be run by players OR the console)
        // =========================================
        if (args.length >= 2 && args[0].equalsIgnoreCase("import")) {
            if (!sender.hasPermission("dccosmetics.admin")) {
                DCCosmetics.getInstance().getLanguageManager().sendMessage(sender, "no_permission");
                return true;
            }
            String filename = args[1];
            String type = args.length >= 3 ? args[2] : "chest";
            DCCosmetics.getInstance().getBlockbenchImporter().importModel(filename, type, sender);
            return true;
        }

        // =========================================
        // 5. SCROLL GIVER: /cosmetics scroll <player> <id> [color]
        // =========================================
        if (args.length >= 3 && args[0].equalsIgnoreCase("scroll")) {
            if (!sender.hasPermission("dccosmetics.admin")) return true;
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                DCCosmetics.getInstance().getLanguageManager().sendMessage(sender, "player_not_found");
                return true;
            }
            String id = args[2];
            dc.dccosmetics.model.CosmeticTemplate temp = DCCosmetics.getInstance().getTemplateRegistry().getTemplate(id);
            if (temp == null) {
                DCCosmetics.getInstance().getLanguageManager().sendMessage(sender, "cosmetic_not_found");
                return true;
            }
            
            target.getInventory().addItem(DCCosmetics.getInstance().createScroll(id, args.length >= 4 ? args[3] : "#FFFFFF"));
            String msg = DCCosmetics.getInstance().getLanguageManager().getMessage("scroll_given").replace("{player}", target.getName());
            sender.sendMessage(msg);
            return true;
        }

        // =========================================
        // DEBUG HIT COMMAND
        // =========================================
        if (args.length >= 2 && args[0].equalsIgnoreCase("debughit") && sender instanceof Player p) {
            if (!sender.hasPermission("dccosmetics.admin")) return true;
            dc.dccosmetics.model.CosmeticTemplate temp = DCCosmetics.getInstance().getTemplateRegistry().getTemplate(args[1]);
            if (temp != null) {
                String type = args.length >= 3 ? args[2] : "regular";
                String color = args.length >= 4 ? args[3] : "#FFFFFF";
                dc.dccosmetics.listener.PlayerListener.spawnHitPhysicsEffect(
                    p.getLocation().add(0, 1.5, 0).add(p.getLocation().getDirection().multiply(2)), 
                    temp, color, 
                    type,
                    p.getLocation().getYaw()
                );
            }
            return true;
        }

        // --- ALL COMMANDS BELOW THIS LINE REQUIRE A REAL PLAYER ---
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        // =========================================
        // 2. TOGGLE SOUND COMMAND: /cosmetics sound
        // =========================================
        if (args.length > 0 && args[0].equalsIgnoreCase("sound")) {
            if (FootstepListener.MUTED_PLAYERS.contains(player.getUniqueId())) {
                FootstepListener.MUTED_PLAYERS.remove(player.getUniqueId());
                player.sendMessage(ChatColor.AQUA + "Cosmetic footstep sounds are now " + ChatColor.GREEN + "ON" + ChatColor.AQUA + ".");
            } else {
                FootstepListener.MUTED_PLAYERS.add(player.getUniqueId());
                player.sendMessage(ChatColor.AQUA + "Cosmetic footstep sounds are now " + ChatColor.RED + "OFF" + ChatColor.AQUA + ".");
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("debugboots")) {
            dc.dccosmetics.model.ActiveCosmetic.DEBUG_BOOTS = !dc.dccosmetics.model.ActiveCosmetic.DEBUG_BOOTS;
            player.sendMessage(ChatColor.YELLOW + "Boots Teleport Debug is now: " + dc.dccosmetics.model.ActiveCosmetic.DEBUG_BOOTS);
            return true;
        }

        // =========================================
        // THE NODE ISOLATOR (Fixing Blockbench Errors!)
        // =========================================
        if (args.length >= 2 && args[0].equalsIgnoreCase("debugnode")) {
            String node = args[1];
            if (node.equalsIgnoreCase("clear")) {
                dc.dccosmetics.model.ActiveCosmetic.ISOLATED_NODE = null;
                player.sendMessage(ChatColor.GREEN + "Cleared node isolation! Showing full model.");
            } else {
                dc.dccosmetics.model.ActiveCosmetic.ISOLATED_NODE = node;
                player.sendMessage(ChatColor.YELLOW + "Isolated node: " + ChatColor.AQUA + node);
                player.sendMessage(ChatColor.GRAY + "All other nodes are now invisible.");
            }
            return true;
        }

        // =========================================
        // ADVANCED BLOCKBENCH DEBUGGER
        // =========================================
        if (args.length >= 1 && args[0].equalsIgnoreCase("debugbb")) {
            if (!sender.hasPermission("dccosmetics.admin")) return true;
            
            if (args.length == 2 && args[1].equalsIgnoreCase("corners")) {
                dc.dccosmetics.model.ActiveCosmetic.DEBUG_CORNERS = !dc.dccosmetics.model.ActiveCosmetic.DEBUG_CORNERS;
                player.sendMessage(ChatColor.YELLOW + "Mathematical Corner Particles: " + dc.dccosmetics.model.ActiveCosmetic.DEBUG_CORNERS);
                return true;
            } else if (args.length >= 5 && args[1].equalsIgnoreCase("ratio")) {
                try {
                    dc.dccosmetics.nms.ProtocolDisplayWrapper.BB_MAGIC_X = Float.parseFloat(args[2]);
                    dc.dccosmetics.nms.ProtocolDisplayWrapper.BB_MAGIC_Y = Float.parseFloat(args[3]);
                    dc.dccosmetics.nms.ProtocolDisplayWrapper.BB_MAGIC_Z = Float.parseFloat(args[4]);
                    if (args.length >= 6) {
                        dc.dccosmetics.nms.ProtocolDisplayWrapper.BB_OFFSET_Z = Float.parseFloat(args[5]);
                    }
                    if (args.length >= 7) {
                        dc.dccosmetics.nms.ProtocolDisplayWrapper.BB_BACK_OFFSET_X = Float.parseFloat(args[6]);
                    }
                    player.sendMessage(ChatColor.GREEN + "Set Ratios -> X: " + args[2] + ", Y: " + args[3] + ", Z: " + args[4] + (args.length >= 6 ? ", Gap: " + args[5] : "") + (args.length >= 7 ? ", Slide: " + args[6] : ""));
                    DCCosmetics.getInstance().getSculptManager().refreshAllDummies();
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid numbers. Use formats like: 0.95");
                }
                return true;
            }
            player.sendMessage(ChatColor.RED + "Usage: /cosmetics debugbb <corners | ratio x y z [gap] [slide_x]>");
            return true;
        }

        // =========================================
        // NEW: CHAT-BASED DIALOG EDITOR ROUTING
        // =========================================
        if (args.length >= 3 && args[0].equalsIgnoreCase("editcmd")) {
            if (!sender.hasPermission("dccosmetics.admin")) return true;
            String templateId = args[1];
            String action = args[2];
            dc.dccosmetics.manager.DialogEditorManager editor = DCCosmetics.getInstance().getDialogEditorManager();
            
            if (action.equalsIgnoreCase("main")) {
                editor.openMainMenu(player, templateId);
            } else if (action.equalsIgnoreCase("add")) {
                java.io.File file = DCCosmetics.getInstance().getTemplateRegistry().getTemplateFile(templateId);
                org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
                int count = config.getConfigurationSection("components") != null ? config.getConfigurationSection("components").getKeys(false).size() : 0;
                config.set("components.new_comp_" + count + ".type", "solid");
                try { config.save(file); } catch (Exception ignored) {}
                editor.openMainMenu(player, templateId);
            } else if (action.equalsIgnoreCase("delete")) {
                java.io.File file = DCCosmetics.getInstance().getTemplateRegistry().getTemplateFile(templateId);
                org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
                config.set("components." + args[3], null);
                try { config.save(file); } catch (Exception ignored) {}
                editor.openMainMenu(player, templateId);
            } else if (action.equalsIgnoreCase("comp")) {
                editor.openComponentMenu(player, templateId, args[3]);
            } else if (action.equalsIgnoreCase("shift")) {
                // /cosmetics editcmd <id> shift <comp> <field> <value>
                editor.applyShift(templateId, args[3], args[4], Double.parseDouble(args[args.length - 1]), true);
                if (args[3].equals("GLOBAL")) editor.openGeneralSettingsMenu(player, templateId);
                else editor.openComponentMenu(player, templateId, args[3]);
            }
            return true;
        }

        // =========================================
        // 3. SCULPTOR COMMANDS: /cosmetics sculpt <id|editor|clear|toggle>
        // =========================================
        if (args.length >= 2 && args[0].equalsIgnoreCase("sculpt")) {
            if (!sender.hasPermission("dccosmetics.admin")) {
                sender.sendMessage(ChatColor.RED + "You need admin permissions to sculpt.");
                return true;
            }
            
            if (args[1].equalsIgnoreCase("clear")) {
                DCCosmetics.getInstance().getSculptManager().clearSculpt(player);
                player.sendMessage(ChatColor.GREEN + "Cleared dummy sculptor!");
                return true;
            }
            if (args[1].equalsIgnoreCase("editor")) {
                String activeId = DCCosmetics.getInstance().getSculptManager().getActiveSculptId(player);
                if (activeId != null) DCCosmetics.getInstance().getDialogEditorManager().reopenLastMenu(player, activeId);
                return true;
            }
            if (args[1].equalsIgnoreCase("toggle")) {
                DCCosmetics.getInstance().getSculptManager().toggleDummyVisibility(player);
                return true;
            }
            
            String id = args[1];
            DCCosmetics.getInstance().getSculptManager().startSculpting(player, id);
            player.sendMessage(ChatColor.AQUA + "Spawned Sculpt Dummy for: " + ChatColor.YELLOW + id);
            return true;
        }

        // =========================================
        // 3. BASE COMMAND: /cosmetics (Opens GUI)
        // =========================================

        // Ensure their profile is loaded (Usually done on PlayerJoinEvent, but safe to check here)
        if (DCCosmetics.getInstance().getProfileManager().getProfile(player) == null) {
            DCCosmetics.getInstance().getProfileManager().loadProfile(player);
        }

        guiManager.openCosmeticsMenu(player);
        return true;
    }
}