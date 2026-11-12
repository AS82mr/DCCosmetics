package dc.dcessentials.modules.npc.command;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.npc.NpcCanonManager;
import dc.dcessentials.modules.npc.NpcDialogueEngine;
import dc.dcessentials.modules.npc.NpcRegistry;
import dc.dcessentials.modules.npc.NpcSetupWand;
import dc.dcessentials.modules.npc.NpcZoneManager;
import dc.dcessentials.modules.npc.gui.NpcTrackerGUI;
import dc.dcessentials.modules.npc.model.NpcCharacter;
import dc.dcessentials.modules.npc.model.NpcStage;
import dc.dcessentials.modules.npc.model.NpcZone;
import dc.dcessentials.modules.npc.scene.SceneManager;
import dc.dcessentials.modules.npc.util.DragoncoteColor;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Universal command handler for Dragoncote Multiverse NPCs, Canon Auto-Setup,
 * District Warping, Admin Diagnostics, Setup Wand, Scenes, and Lore Progression.
 */
public class DCNpcCommand implements CommandExecutor, TabCompleter {

    private final NpcRegistry registry;
    private final NpcZoneManager zoneManager;
    private final NpcCanonManager canonManager;
    private final NpcDialogueEngine dialogueEngine;
    private final NpcSetupWand wand;
    private final NpcTrackerGUI trackerGUI;
    private final SceneManager sceneManager;
    private final dc.dcessentials.modules.npc.gui.NpcEditorGUI npcEditorGUI;
    private dc.dcessentials.modules.npc.gui.NpcStageEditorGUI stageEditorGUI;
    private dc.dcessentials.modules.npc.gui.WarpMenuGUI warpMenuGUI;

    public DCNpcCommand(NpcRegistry registry, NpcZoneManager zoneManager,
                        NpcCanonManager canonManager, NpcDialogueEngine dialogueEngine,
                        NpcSetupWand wand, NpcTrackerGUI trackerGUI, SceneManager sceneManager,
                        dc.dcessentials.modules.npc.gui.NpcEditorGUI npcEditorGUI) {
        this.registry = registry;
        this.zoneManager = zoneManager;
        this.canonManager = canonManager;
        this.dialogueEngine = dialogueEngine;
        this.wand = wand;
        this.trackerGUI = trackerGUI;
        this.sceneManager = sceneManager;
        this.npcEditorGUI = npcEditorGUI;
    }

    public void setGuis(dc.dcessentials.modules.npc.gui.NpcStageEditorGUI stageEditorGUI,
                        dc.dcessentials.modules.npc.gui.WarpMenuGUI warpMenuGUI) {
        this.stageEditorGUI = stageEditorGUI;
        this.warpMenuGUI = warpMenuGUI;
    }

    private boolean hasAdminPerm(CommandSender sender) {
        return sender.hasPermission("dccosmetics.admin") ||
               sender.hasPermission("dragoncote.admin") ||
               sender.hasPermission("dcessentials.admin") ||
               sender.isOp();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                trackerGUI.open((Player) sender);
            } else {
                DCMessage.sendHelp(sender, "Commands: &#FFFFFFsetup, canon, purge, fixnames, scene, tp, loc, wand, track, warp, spawn, remove, talk, claim, stage, reset, skin, reload");
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help":
                sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
                DCMessage.sendGeneral(sender, "Dragoncote Multiverse NPC System");
                sender.sendMessage(ColorUtil.colorize("  &#FBBF24Player Commands:"));
                sender.sendMessage(ColorUtil.colorize("  &8• &#FFFFFF/dcnpc talk <id> &7- Interact with character dialogue"));
                sender.sendMessage(ColorUtil.colorize("  &8• &#FFFFFF/dcnpc claim <id> [stage] &7- Collect completed mission bounty"));
                sender.sendMessage(ColorUtil.colorize("  &8• &#FFFFFF/dcnpc track [next|<id>] &7- Set HUD waypoint to objective"));
                sender.sendMessage(ColorUtil.colorize("  &8• &#FFFFFF/dcnpc waypoint clear &7- Clear active HUD waypoint"));
                sender.sendMessage(ColorUtil.colorize("  &8• &#FFFFFF/dcnpc warp <district> &7- Fast travel via transit network"));
                sender.sendMessage(ColorUtil.colorize("  &8• &#FFFFFF/dcnpc timeline &7- View storyline quest progression"));
                sender.sendMessage(ColorUtil.colorize("  &8• &#FFFFFF/dcnpc gui &7- Open Sector & District Explorer"));
                if (hasAdminPerm(sender)) {
                    sender.sendMessage(ColorUtil.colorize("  &#FBBF24Admin Commands:"));
                    sender.sendMessage(ColorUtil.colorize("  &8• &#FFFFFF/dcnpc list [world] &7- Show all actual live spawned NPCs"));
                    sender.sendMessage(ColorUtil.colorize("  &8• &#FFFFFF/dcnpc wand [step|undo|skip|audit] &7- Equip sequential setup wand"));
                    sender.sendMessage(ColorUtil.colorize("  &8• &#FFFFFF/dcnpc editor <id> &7- Open full 54-slot visual Dossier Editor"));
                    sender.sendMessage(ColorUtil.colorize("  &8• &#FFFFFF/dcnpc canon [live|test] &7- Deploy entire canonical NPC batch"));
                    sender.sendMessage(ColorUtil.colorize("  &8• &#FFFFFF/dcnpc refresh &7- Refresh world skins, overhead badges, and 3D rigs"));
                    sender.sendMessage(ColorUtil.colorize("  &8• &#FFFFFF/dcnpc skin <id> [local|url] &7- Reload character skin texture"));
                    sender.sendMessage(ColorUtil.colorize("  &8• &#FFFFFF/dcnpc purge [all|live|test|cliff|scenes] &7- Cleanly wipe NPCs"));
                    sender.sendMessage(ColorUtil.colorize("  &8• &#FFFFFF/dcnpc reset <player> &7- Reset player progression to Chapter 1"));
                    sender.sendMessage(ColorUtil.colorize("  &8• &#FFFFFF/dcnpc reload &7- Reload all configs, zones, and stages"));
                }
                sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
                return true;

            case "list":
            case "status":
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                if (CitizensAPI.getPlugin() == null) {
                    DCMessage.sendError(sender, "Citizens plugin is not active.");
                    return true;
                }
                String filterWorld = (args.length > 1) ? args[1].toLowerCase() : null;
                sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
                DCMessage.sendGeneral(sender, "Live Citizens NPC Audit" + (filterWorld != null ? " &8(World: &#FFFFFF" + filterWorld + "&8)" : ""));
                sender.sendMessage(ColorUtil.colorize("  &7Auditing active spawned entities across multiverse dimensions:"));

                Map<String, List<NPC>> byWorld = new LinkedHashMap<>();
                Set<String> liveCharIds = new HashSet<>();
                int liveCount = 0;

                for (NPC npc : CitizensAPI.getNPCRegistry()) {
                    if (!npc.isSpawned() || npc.getEntity() == null) continue;
                    String wName = npc.getEntity().getWorld().getName();
                    if (filterWorld != null && !wName.toLowerCase().contains(filterWorld)) continue;

                    byWorld.computeIfAbsent(wName, k -> new ArrayList<>()).add(npc);
                    String cId = canonManager.resolveCanonId(npc);
                    if (cId != null) {
                        liveCharIds.add(cId.toLowerCase());
                    }
                    liveCount++;
                }

                if (byWorld.isEmpty()) {
                    DCMessage.sendWarning(sender, "No live spawned NPCs found in target world(s).");
                } else {
                    for (Map.Entry<String, List<NPC>> entry : byWorld.entrySet()) {
                        String wName = entry.getKey();
                        List<NPC> list = entry.getValue();
                        sender.sendMessage(ColorUtil.colorize("  &#0CBF93▶ Dimension: &#FFFFFF" + wName + " &7(&a" + list.size() + " Live NPCs&7)"));
                        for (NPC npc : list) {
                            String cId = canonManager.resolveCanonId(npc);
                            NpcCharacter c = (cId != null) ? registry.getCharacter(cId) : null;
                            String charDisplay = (c != null) ? c.getShortName() : npc.getName();
                            String engine = (c != null && c.getEngineType().equalsIgnoreCase("MODEL_ENGINE")) ?
                                    "&#F59E0BMEG (" + c.getModelId() + ")" : "&#0CBF93Citizens";
                            Location loc = npc.getEntity().getLocation();
                            sender.sendMessage(ColorUtil.colorize(
                                    "    &8• &#FFFFFF#" + npc.getId() + " " + charDisplay + " &7ID: &#FFFFFF" + (cId != null ? cId : "custom") +
                                    " &7at &#FFFFFF(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ") &7Engine: " + engine + " &#0CBF93(Live)"
                            ));
                        }
                    }
                }

                // Check for unspawned characters
                List<String> unspawned = new ArrayList<>();
                for (NpcCharacter c : registry.getAllCharacters()) {
                    if (!liveCharIds.contains(c.getId())) {
                        unspawned.add(c.getId());
                    }
                }

                sender.sendMessage(ColorUtil.colorize("  &7Total Live Spawned: &#0CBF93" + liveCount + " NPCs &7| Active Worlds: &#38BDF8" + byWorld.keySet().size()));
                if (!unspawned.isEmpty()) {
                    DCMessage.sendWarning(sender, "Not Spawned In Any World (" + unspawned.size() + "): &#FFFFFF" + String.join(", ", unspawned));
                } else {
                    DCMessage.sendGeneral(sender, "All " + registry.getAllCharacters().size() + " canonical characters have active live instances.");
                }
                sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
                return true;

            // ==============================================================
            // CANON DEPLOYMENT & BATCH MANAGEMENT
            // ==============================================================
            case "setup":
            case "canon":
            case "replace":
            case "respawn":
            case "respawnall":
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                if (args.length > 1 && args[1].equalsIgnoreCase("test")) {
                    canonManager.respawnAllTest(sender);
                } else {
                    canonManager.respawnAllLive(sender);
                }
                return true;

            case "purge":
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                String purgeMode = (args.length > 1) ? args[1].toLowerCase() : "all";
                if (purgeMode.equals("scenes")) {
                    if (sender instanceof Player) {
                        sceneManager.purgeScenes((Player) sender, "all");
                    } else {
                        DCMessage.sendError(sender, "Only players in-world can purge nearby scenes.");
                    }
                } else if (purgeMode.equals("cliff")) {
                    canonManager.purgeCliffAndMisplacedNpcs(sender);
                } else if (purgeMode.equals("replace") || purgeMode.equals("respawn")) {
                    canonManager.respawnAllLive(sender);
                } else {
                    canonManager.purge(sender, purgeMode);
                }
                return true;

            case "fixnames":
            case "fix":
            case "refresh":
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                canonManager.fixNames(sender);
                return true;

            // ==============================================================
            // SCENES & WORKSTATIONS (MODEL ENGINE & MYTHICMOBS)
            // ==============================================================
            case "scene":
            case "scenes":
                if (!(sender instanceof Player)) {
                    DCMessage.sendError(sender, "Only players can manage scenes.");
                    return true;
                }
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                Player pScene = (Player) sender;
                if (args.length < 2) {
                    DCMessage.sendHelp(pScene, "Scene Actions: forge, market, tool, spawn <mobId>, purge [category]");
                    return true;
                }
                String sceneSub = args[1].toLowerCase();
                switch (sceneSub) {
                    case "forge":
                    case "blacksmith":
                        sceneManager.deployForgeScenes(pScene);
                        return true;
                    case "market":
                    case "bazaar":
                        sceneManager.deployMarketScenes(pScene);
                        return true;
                    case "pigeon":
                    case "pigeons":
                        sceneManager.deployPigeons(pScene);
                        return true;
                    case "crow":
                    case "crows":
                        sceneManager.deployCrows(pScene);
                        return true;
                    case "fisherman":
                    case "fishermen":
                    case "docks":
                        sceneManager.deployFishermen(pScene);
                        return true;
                    case "spawner":
                    case "menu":
                        Bukkit.dispatchCommand(pScene, "dm open scenes_spawner " + pScene.getName());
                        return true;
                    case "env":
                    case "environment":
                        Bukkit.dispatchCommand(pScene, "dm open scenes_environment " + pScene.getName());
                        return true;
                    case "auto":
                    case "ambient":
                        String w = pScene.getWorld().getName().toLowerCase();
                        if (w.contains("bw") || w.contains("void") || w.contains("drain")) {
                            sceneManager.deployCrows(pScene);
                        } else {
                            sceneManager.deployPigeons(pScene);
                        }
                        return true;
                    case "tool":
                        sceneManager.giveScenesTool(pScene);
                        return true;
                    case "spawn":
                        if (args.length < 3) {
                            DCMessage.sendHelp(pScene, "Usage: /" + label + " scene spawn <modelId> [customTagName]");
                            return true;
                        }
                        String customTag = null;
                        if (args.length > 3) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 3; i < args.length; i++) {
                                if (sb.length() > 0) sb.append(" ");
                                sb.append(args[i]);
                            }
                            customTag = sb.toString();
                        }
                        sceneManager.spawnSceneModel(args[2].toLowerCase(), pScene.getLocation(), pScene, customTag);
                        return true;
                    case "purge":
                    case "clear":
                        String pCat = (args.length > 2) ? args[2] : "all";
                        sceneManager.purgeScenes(pScene, pCat);
                        return true;
                    default:
                        DCMessage.sendError(pScene, "Unknown scene action: " + sceneSub + ". Available: forge, market, pigeons, crows, fishermen, spawner, env, auto, tool, spawn, purge");
                        return true;
                }

            // ==============================================================
            // LOCATION & TELEPORTATION
            // ==============================================================
            case "tp":
            case "goto":
                if (!(sender instanceof Player)) {
                    DCMessage.sendError(sender, "Only players can teleport to NPCs.");
                    return true;
                }
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                if (args.length < 2) {
                    DCMessage.sendHelp(sender, "Usage: &#FFFFFF/" + label + " tp <characterId>");
                    return true;
                }
                canonManager.teleportToNpc((Player) sender, args[1].toLowerCase());
                return true;

            case "loc":
            case "setloc":
                if (!(sender instanceof Player)) {
                    DCMessage.sendError(sender, "Only players can set NPC locations.");
                    return true;
                }
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                if (args.length < 2) {
                    DCMessage.sendHelp(sender, "Usage: &#FFFFFF/" + label + " loc <characterId>");
                    return true;
                }
                Player pLoc = (Player) sender;
                String locCharId = args[1].toLowerCase();
                NpcCharacter targetChar = registry.getCharacter(locCharId);
                if (targetChar == null) {
                    DCMessage.sendError(sender, "Unknown character ID: &#FFFFFF" + locCharId);
                    return true;
                }
                canonManager.setLiveLocation(locCharId, pLoc.getLocation());
                List<NPC> instances = zoneManager.findAllCitizensNpc(locCharId);
                for (NPC n : instances) {
                    if (n.getNavigator() != null) {
                        n.getNavigator().cancelNavigation();
                    }
                    if (n.isSpawned() && n.getEntity() != null && n.getEntity().getWorld().equals(pLoc.getWorld())) {
                        n.teleport(pLoc.getLocation(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                    }
                }
                if (targetChar.getSector() == 1) {
                    canonManager.syncSector1Instances(targetChar);
                }
                DCMessage.sendGeneral(sender, "Saved canonical coordinates for &#FFFFFF" + targetChar.getShortName() + " &#0CBF93at your current position and synced across dimensions.");
                return true;

            // ==============================================================
            // SETUP WAND & DISTRICT WARPING
            // ==============================================================
            case "editor":
            case "edit":
                if (!(sender instanceof Player)) {
                    DCMessage.sendError(sender, "Only players can open the editor.");
                    return true;
                }
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                Player pEd = (Player) sender;
                if (args.length > 1) {
                    npcEditorGUI.open(pEd, args[1].toLowerCase());
                } else {
                    wand.giveEditorWand(pEd);
                }
                return true;

            case "wand":
                if (!(sender instanceof Player)) {
                    DCMessage.sendError(sender, "Only players can receive the setup wand.");
                    return true;
                }
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                Player pWand = (Player) sender;
                if (args.length > 1) {
                    String wArg = args[1].toLowerCase();
                    if (wArg.equals("editor") || wArg.equals("edit")) {
                        wand.giveEditorWand(pWand);
                        return true;
                    }
                    if (wArg.equals("setup")) {
                        wand.giveSetupWand(pWand, wand.getStep(pWand));
                        return true;
                    }
                    if (wArg.equals("undo")) {
                        wand.undo(pWand);
                        return true;
                    }
                    if (wArg.equals("skip")) {
                        wand.skipCompanion(pWand);
                        return true;
                    }
                    if (wArg.equals("audit")) {
                        wand.printSector1Audit(pWand);
                        return true;
                    }
                    try {
                        int stepNum = Integer.parseInt(args[1]);
                        wand.setStep(pWand, stepNum);
                    } catch (NumberFormatException e) {
                        wand.setStepByCharacter(pWand, args[1]);
                    }
                }
                wand.giveWand(pWand);
                return true;

            case "warp":
                if (!(sender instanceof Player)) {
                    DCMessage.sendError(sender, "Only players can warp.");
                    return true;
                }
                if (args.length < 2) {
                    DCMessage.sendHelp(sender, "Usage: &#FFFFFF/" + label + " warp <zoneId>");
                    return true;
                }
                boolean success = zoneManager.warpPlayer((Player) sender, args[1].toLowerCase());
                if (!success) {
                    DCMessage.sendError(sender, "District &#FFFFFF" + args[1] + " &#FCA5A5has no configured warp anchor.");
                }
                return true;

            // ==============================================================
            // WAYPOINT & NAVIGATION
            // ==============================================================
            case "waypoint":
            case "track":
                if (!(sender instanceof Player)) {
                    DCMessage.sendError(sender, "Only players can track waypoints.");
                    return true;
                }
                Player pTrack = (Player) sender;
                if (args.length < 2 || args[1].equalsIgnoreCase("next")) {
                    int currentIdx = dialogueEngine.getPlayerTimelineIndex(pTrack);
                    List<String> timeline = registry.getTimelineOrder();
                    if (currentIdx > timeline.size()) {
                        DCMessage.sendGeneral(sender, "You have completed all storyline quest chapters.");
                        return true;
                    }
                    String node = timeline.get(currentIdx - 1);
                    String targetId = node.split(":")[0];
                    dialogueEngine.trackNpcWaypoint(pTrack, targetId);
                    return true;
                }
                if (args[1].equalsIgnoreCase("clear") || args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("none")) {
                    DCCosmetics.getInstance().getWaypointManager().clearWaypoint(pTrack);
                    DCMessage.sendInfo(sender, "Cleared active HUD Waypoint.");
                    return true;
                }
                dialogueEngine.trackNpcWaypoint(pTrack, args[1].toLowerCase());
                return true;

            // ==============================================================
            // INTERACTION & PROGRESSION
            // ==============================================================
            case "talk":
                if (!(sender instanceof Player)) {
                    DCMessage.sendError(sender, "Only players can talk to NPCs.");
                    return true;
                }
                if (args.length < 2) {
                    DCMessage.sendHelp(sender, "Usage: &#FFFFFF/" + label + " talk <characterId>");
                    return true;
                }
                dialogueEngine.interact((Player) sender, args[1].toLowerCase());
                return true;

            case "claim":
                if (!(sender instanceof Player)) {
                    DCMessage.sendError(sender, "Only players can claim mission rewards.");
                    return true;
                }
                if (args.length < 2) {
                    DCMessage.sendHelp(sender, "Usage: &#FFFFFF/" + label + " claim <characterId> [stage]");
                    return true;
                }
                Player pClaim = (Player) sender;
                String cId = args[1].toLowerCase();
                NpcCharacter charClaim = registry.getCharacter(cId);
                if (charClaim == null) {
                    DCMessage.sendError(sender, "Unknown character ID: &#FFFFFF" + cId);
                    return true;
                }
                int stg = 1;
                if (args.length > 2) {
                    try { stg = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
                }
                dialogueEngine.claimRewards(pClaim, cId, stg);
                return true;

            case "stage":
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                if (args.length < 2) {
                    DCMessage.sendHelp(sender, "Usage: /" + label + " stage <player> [characterId] [stageNumber]");
                    return true;
                }
                Player targetPlayer = Bukkit.getPlayer(args[1]);
                if (targetPlayer == null) {
                    DCMessage.sendError(sender, "Player not found: " + args[1]);
                    return true;
                }
                if (args.length == 2) {
                    int pIdx = dialogueEngine.getPlayerTimelineIndex(targetPlayer);
                    DCMessage.sendQuest(sender, targetPlayer.getName() + " Timeline Chapter: &#FBBF24#" + pIdx);
                    return true;
                }
                if (args.length >= 4) {
                    String sCharId = args[2].toLowerCase();
                    int sNum = 1;
                    try { sNum = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
                    dialogueEngine.markStageCompleted(targetPlayer, sCharId, sNum);
                    DCMessage.sendQuest(sender, "Marked Stage #" + sNum + " for " + sCharId + " as completed for " + targetPlayer.getName() + ".");
                    return true;
                }
                return true;

            case "audit":
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                wand.printSector1Audit(sender);
                return true;

            case "skin":
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                if (args.length < 2) {
                    DCMessage.sendHelp(sender, "Usage: /" + label + " skin <characterId> [local|url|playerName]");
                    return true;
                }
                String sCharId = args[1].toLowerCase();
                NpcCharacter sChar = registry.getCharacter(sCharId);
                if (sChar == null) {
                    DCMessage.sendError(sender, "Character '" + sCharId + "' not found.");
                    return true;
                }
                String skinSource = (args.length > 2) ? args[2] : "local";
                DCMessage.sendInfo(sender, "Processing skin for " + sChar.getShortName() + " (source: " + skinSource + ")...");
                DCCosmetics.getInstance().getSkinProcessor().processSkin(sCharId, skinSource).thenAccept(res -> {
                    Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
                        if (res) {
                            int updated = 0;
                            if (CitizensAPI.getPlugin() != null) {
                                for (NPC npc : CitizensAPI.getNPCRegistry()) {
                                    String canId = canonManager.resolveCanonId(npc);
                                    if (canId != null && canId.equalsIgnoreCase(sCharId)) {
                                        String wName = (npc.isSpawned() && npc.getEntity() != null) ? npc.getEntity().getWorld().getName() : null;
                                        DCCosmetics.getInstance().getSkinProcessor().applyWorldSkin(npc, wName, sCharId);
                                        updated++;
                                    }
                                }
                            }
                            DCMessage.sendGeneral(sender, "Applied skin to " + updated + " instance(s) of " + sChar.getShortName() + ".");
                        } else {
                            DCMessage.sendError(sender, "Failed to process skin. Ensure default.png exists in /plugins/DCEssentials/npcs/" + sCharId + "/");
                        }
                    });
                });
                return true;

            case "reset":
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                Player pReset = (args.length > 1) ? Bukkit.getPlayer(args[1]) : (sender instanceof Player ? (Player) sender : null);
                if (pReset == null) {
                    DCMessage.sendError(sender, "Player not found.");
                    return true;
                }
                dialogueEngine.resetPlayerQuestProgress(pReset);
                DCMessage.sendQuest(sender, "Completely reset questline progress, stage claims, and timeline for " + pReset.getName() + " back to Chapter 1.");
                return true;

            // ==============================================================
            // SPONTANEOUS SPAWN / DESPAWN
            // ==============================================================
            case "spawn":
                if (!(sender instanceof Player)) {
                    DCMessage.sendError(sender, "Only players can spawn NPCs.");
                    return true;
                }
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                if (args.length < 2) {
                    DCMessage.sendHelp(sender, "Usage: /" + label + " spawn <characterId>");
                    return true;
                }
                Player pSpawn = (Player) sender;
                NpcCharacter cSpawn = registry.getCharacter(args[1].toLowerCase());
                if (cSpawn == null) {
                    DCMessage.sendError(sender, "Unknown character ID: " + args[1]);
                    return true;
                }
                if (CitizensAPI.getPlugin() == null) {
                    DCMessage.sendError(sender, "Citizens plugin is not active.");
                    return true;
                }
                NPC newNpc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, cSpawn.getShortName());
                newNpc.data().setPersistent("dcnpc_id", cSpawn.getId());
                newNpc.spawn(pSpawn.getLocation());
                DCCosmetics.getInstance().getSkinProcessor().applyWorldSkin(newNpc, pSpawn.getWorld().getName(), cSpawn.getId());
                DCCosmetics.getInstance().getNpcCanonManager().configureNpcOverhead(newNpc, cSpawn, pSpawn.getWorld().getName());
                DCCosmetics.getInstance().getNpcCanonManager().applyEngineMode(newNpc, cSpawn);
                DCMessage.sendGeneral(sender, "Spawned NPC " + cSpawn.getShortName() + " (Citizens #" + newNpc.getId() + ") at your location.");
                return true;

            case "remove":
            case "despawn":
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                if (args.length < 2) {
                    DCMessage.sendHelp(sender, "Usage: /" + label + " remove <characterId>");
                    return true;
                }
                String remId = args[1].toLowerCase();
                NPC remNpc = zoneManager.findCitizensNpc(remId, null);
                if (remNpc != null) {
                    remNpc.destroy();
                    DCMessage.sendGeneral(sender, "Despawned NPC " + remId + " (Citizens #" + remNpc.getId() + ").");
                } else {
                    DCMessage.sendWarning(sender, "No active spawned NPC found for ID: " + remId);
                }
                return true;

            // ==============================================================
            // STAGES & CHAPTERS MANAGEMENT
            // ==============================================================
            case "stages":
            case "chapters":
                if (!(sender instanceof Player)) {
                    DCMessage.sendError(sender, "Only players can open the stages GUI.");
                    return true;
                }
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                Player pStg = (Player) sender;
                if (args.length < 2) {
                    DCMessage.sendHelp(sender, "Usage: /" + label + " stages <characterId>");
                    return true;
                }
                if (stageEditorGUI != null) {
                    stageEditorGUI.open(pStg, args[1].toLowerCase());
                }
                return true;

            case "setreward":
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                if (args.length < 4) {
                    DCMessage.sendHelp(sender, "Usage: /" + label + " setreward <characterId> <stageNum> <cashAmount>");
                    return true;
                }
                NpcCharacter cReward = registry.getCharacter(args[1].toLowerCase());
                if (cReward == null) {
                    DCMessage.sendError(sender, "Character not found: " + args[1]);
                    return true;
                }
                try {
                    int sNum = Integer.parseInt(args[2]);
                    double amt = Double.parseDouble(args[3]);
                    NpcStage stgReward = cReward.getStage(sNum);
                    if (stgReward != null) {
                        stgReward.setCashReward(amt);
                        registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
                        DCMessage.sendQuest(sender, "Set cash reward for " + cReward.getShortName() + " Ch." + sNum + " to: $" + amt);
                    } else {
                        DCMessage.sendError(sender, "Stage #" + sNum + " not found on character " + args[1]);
                    }
                } catch (NumberFormatException e) {
                    DCMessage.sendError(sender, "Invalid number format for stage or cash amount.");
                }
                return true;

            case "setcmd":
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                if (args.length < 4) {
                    DCMessage.sendHelp(sender, "Usage: /" + label + " setcmd <characterId> <stageNum> <command>");
                    return true;
                }
                NpcCharacter cCmd = registry.getCharacter(args[1].toLowerCase());
                if (cCmd == null) {
                    DCMessage.sendError(sender, "Character not found: " + args[1]);
                    return true;
                }
                try {
                    int sNum = Integer.parseInt(args[2]);
                    String cmdStr = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                    if (cmdStr.startsWith("/")) cmdStr = cmdStr.substring(1);
                    NpcStage stgCmd = cCmd.getStage(sNum);
                    if (stgCmd != null) {
                        stgCmd.setExecutionCommand(cmdStr);
                        registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
                        DCMessage.sendQuest(sender, "Set execution command for " + cCmd.getShortName() + " Ch." + sNum + " to: /" + cmdStr);
                    } else {
                        DCMessage.sendError(sender, "Stage #" + sNum + " not found on character " + args[1]);
                    }
                } catch (NumberFormatException e) {
                    DCMessage.sendError(sender, "Invalid stage number.");
                }
                return true;

            case "setitem":
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                if (args.length < 4) {
                    DCMessage.sendHelp(sender, "Usage: /" + label + " setitem <characterId> <stageNum> <itemName>");
                    return true;
                }
                NpcCharacter cItem = registry.getCharacter(args[1].toLowerCase());
                if (cItem == null) {
                    DCMessage.sendError(sender, "Character not found: " + args[1]);
                    return true;
                }
                try {
                    int sNum = Integer.parseInt(args[2]);
                    String itemStr = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                    NpcStage stgItem = cItem.getStage(sNum);
                    if (stgItem != null) {
                        stgItem.setRewardItemName(itemStr);
                        registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
                        DCMessage.sendQuest(sender, "Set reward item for " + cItem.getShortName() + " Ch." + sNum + " to: " + itemStr);
                    }
                } catch (NumberFormatException e) {
                    DCMessage.sendError(sender, "Invalid stage number.");
                }
                return true;

            case "settitle":
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                if (args.length < 4) {
                    DCMessage.sendHelp(sender, "Usage: /" + label + " settitle <characterId> <stageNum> <title>");
                    return true;
                }
                NpcCharacter cTit = registry.getCharacter(args[1].toLowerCase());
                if (cTit == null) {
                    DCMessage.sendError(sender, "Character not found: " + args[1]);
                    return true;
                }
                try {
                    int sNum = Integer.parseInt(args[2]);
                    String titStr = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                    NpcStage stgTit = cTit.getStage(sNum);
                    if (stgTit != null) {
                        stgTit.setTitle(titStr);
                        registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
                        DCMessage.sendQuest(sender, "Set chapter title for " + cTit.getShortName() + " Ch." + sNum + " to: " + titStr);
                    }
                } catch (NumberFormatException e) {
                    DCMessage.sendError(sender, "Invalid stage number.");
                }
                return true;

            case "setobj":
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                if (args.length < 4) {
                    DCMessage.sendHelp(sender, "Usage: /" + label + " setobj <characterId> <stageNum> <objective>");
                    return true;
                }
                NpcCharacter cObj = registry.getCharacter(args[1].toLowerCase());
                if (cObj == null) {
                    DCMessage.sendError(sender, "Character not found: " + args[1]);
                    return true;
                }
                try {
                    int sNum = Integer.parseInt(args[2]);
                    String objStr = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                    NpcStage stgObj = cObj.getStage(sNum);
                    if (stgObj != null) {
                        stgObj.setObjective(objStr);
                        registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
                        DCMessage.sendQuest(sender, "Set objective for " + cObj.getShortName() + " Ch." + sNum + " to: " + objStr);
                    }
                } catch (NumberFormatException e) {
                    DCMessage.sendError(sender, "Invalid stage number.");
                }
                return true;

            case "setdialogue":
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                if (args.length < 4) {
                    DCMessage.sendHelp(sender, "Usage: /" + label + " setdialogue <characterId> <stageNum> <dialogueText>");
                    return true;
                }
                NpcCharacter cDia = registry.getCharacter(args[1].toLowerCase());
                if (cDia == null) {
                    DCMessage.sendError(sender, "Character not found: " + args[1]);
                    return true;
                }
                try {
                    int sNum = Integer.parseInt(args[2]);
                    String diaStr = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                    NpcStage stgDia = cDia.getStage(sNum);
                    if (stgDia != null) {
                        stgDia.setDialogueLine(diaStr);
                        registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
                        DCMessage.sendQuest(sender, "Set intro dialogue for " + cDia.getShortName() + " Ch." + sNum + ".");
                    }
                } catch (NumberFormatException e) {
                    DCMessage.sendError(sender, "Invalid stage number.");
                }
                return true;

            case "setcomp":
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                if (args.length < 4) {
                    DCMessage.sendHelp(sender, "Usage: /" + label + " setcomp <characterId> <stageNum> <completionText>");
                    return true;
                }
                NpcCharacter cComp = registry.getCharacter(args[1].toLowerCase());
                if (cComp == null) {
                    DCMessage.sendError(sender, "Character not found: " + args[1]);
                    return true;
                }
                try {
                    int sNum = Integer.parseInt(args[2]);
                    String compStr = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                    NpcStage stgComp = cComp.getStage(sNum);
                    if (stgComp != null) {
                        stgComp.setCompletionLine(compStr);
                        registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
                        DCMessage.sendQuest(sender, "Set completion dialogue for " + cComp.getShortName() + " Ch." + sNum + ".");
                    }
                } catch (NumberFormatException e) {
                    DCMessage.sendError(sender, "Invalid stage number.");
                }
                return true;

            case "warpmenu":
            case "warpgui":
                if (sender instanceof Player && warpMenuGUI != null) {
                    warpMenuGUI.open((Player) sender);
                }
                return true;

            case "reload":
                if (!hasAdminPerm(sender)) {
                    DCMessage.sendError(sender, "You do not have permission to execute this command.");
                    return true;
                }
                zoneManager.loadConfig();
                canonManager.loadLocations();
                registry.initializeDefaults();
                registry.loadCustomStages(DCCosmetics.getInstance().getDataFolder());
                DCMessage.sendGeneral(sender, "Reloaded districts, canonical anchors, custom stages, and character registry successfully.");
                return true;

            case "gui":
            case "admin":
            case "tracker":
                if (sender instanceof Player) {
                    trackerGUI.open((Player) sender);
                } else {
                    List<String> missing = zoneManager.getMissingNpcs(registry);
                    sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
                    DCMessage.sendGeneral(sender, "Dragoncote District & NPC Status");
                    sender.sendMessage(ColorUtil.colorize("  &7Total Districts: &#FFFFFF" + zoneManager.getAllZones().size()));
                    sender.sendMessage(ColorUtil.colorize("  &7Missing NPCs (" + missing.size() + "): &#FCA5A5" + String.join(", ", missing)));
                    sender.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
                }
                return true;

            case "timeline":
                if (args.length > 1 && args[1].equalsIgnoreCase("reset")) {
                    if (!hasAdminPerm(sender)) {
                        DCMessage.sendError(sender, "You do not have permission to execute this command.");
                        return true;
                    }
                    Player pTReset = (args.length > 2) ? Bukkit.getPlayer(args[2]) : (sender instanceof Player ? (Player) sender : null);
                    if (pTReset == null) {
                        DCMessage.sendError(sender, "Player not found.");
                        return true;
                    }
                    dialogueEngine.resetPlayerQuestProgress(pTReset);
                    DCMessage.sendQuest(sender, "Completely reset questline progress for " + pTReset.getName() + " back to Chapter 1.");
                    return true;
                }
                if (sender instanceof Player) {
                    Bukkit.dispatchCommand(sender, "timeline");
                }
                return true;

            default:
                DCMessage.sendError(sender, "Unknown subcommand. Type /" + label + " help to view available commands.");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean isAdmin = hasAdminPerm(sender);

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (isAdmin) {
                subs.addAll(Arrays.asList(
                    "help", "list", "status", "setup", "canon", "purge", "fixnames", "refresh", "scene", "wand", "editor", "edit", "track", "waypoint",
                    "warp", "warpmenu", "tp", "loc", "spawn", "remove", "despawn", "talk", "claim",
                    "stages", "setreward", "setcmd", "setitem", "settitle", "setobj", "setdialogue", "setcomp",
                    "stage", "reset", "timeline", "gui", "admin", "skin", "audit", "reload"
                ));
            } else {
                subs.addAll(Arrays.asList("help", "track", "waypoint", "talk", "claim", "warp", "gui", "timeline"));
            }
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();

            switch (sub) {
                case "canon":
                case "setup":
                    return Arrays.asList("live", "test", "all").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());

                case "purge":
                    return Arrays.asList("all", "test", "live", "cliff", "scenes", "replace").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());

                case "scene":
                case "scenes":
                    return Arrays.asList("forge", "market", "pigeons", "crows", "fishermen", "spawner", "env", "auto", "tool", "spawn", "purge").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());

                case "wand":
                    List<String> wandOpts = new ArrayList<>(Arrays.asList("setup", "editor", "edit", "undo", "skip", "audit"));
                    for (int i = 1; i <= registry.getWandOrder().size(); i++) {
                        wandOpts.add(String.valueOf(i));
                    }
                    wandOpts.addAll(registry.getWandOrder());
                    return wandOpts.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());

                case "editor":
                case "edit":
                case "talk":
                case "spawn":
                case "remove":
                case "despawn":
                case "tp":
                case "loc":
                case "skin":
                case "claim":
                case "stages":
                case "chapters":
                case "setreward":
                case "setcmd":
                case "setitem":
                case "settitle":
                case "setobj":
                case "setdialogue":
                case "setcomp":
                    return registry.getAllCharacters().stream()
                            .map(NpcCharacter::getId)
                            .filter(id -> id.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());

                case "warp":
                    Set<String> warpOpts = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                    for (NpcZone z : zoneManager.getAllZones()) {
                        warpOpts.add(z.getId());
                        warpOpts.addAll(z.getAliases());
                    }
                    return warpOpts.stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());

                case "track":
                case "waypoint":
                    List<String> trackOpts = new ArrayList<>(Arrays.asList("next", "clear"));
                    trackOpts.addAll(registry.getAllCharacters().stream().map(NpcCharacter::getId).collect(Collectors.toList()));
                    return trackOpts.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());

                case "stage":
                case "reset":
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());

                case "audit":
                    return Collections.emptyList();

                case "timeline":
                    return Arrays.asList("view", "reset").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("stage") || sub.equals("stages") || sub.equals("chapters")) {
                return registry.getAllCharacters().stream()
                        .map(NpcCharacter::getId)
                        .filter(id -> id.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (sub.equals("skin")) {
                return Arrays.asList("file", "local", "https://").stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (sub.equals("setreward") || sub.equals("setcmd") || sub.equals("setitem") ||
                sub.equals("settitle") || sub.equals("setobj") || sub.equals("setdialogue") || sub.equals("setcomp")) {
                NpcCharacter targetChar = registry.getCharacter(args[1].toLowerCase());
                if (targetChar != null) {
                    return targetChar.getStages().keySet().stream()
                            .map(String::valueOf)
                            .filter(s -> s.startsWith(args[2]))
                            .collect(Collectors.toList());
                }
                return Arrays.asList("1", "2", "3", "4", "5").stream().filter(s -> s.startsWith(args[2])).collect(Collectors.toList());
            }
            if (sub.equals("claim")) {
                return Arrays.asList("1", "2", "3", "4", "5").stream()
                        .filter(s -> s.startsWith(args[2]))
                        .collect(Collectors.toList());
            }
            if (sub.equals("scene") || sub.equals("scenes")) {
                if (args[1].equalsIgnoreCase("spawn")) {
                    return sceneManager.getAllSceneModelIds().stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (args[1].equalsIgnoreCase("purge") || args[1].equalsIgnoreCase("clear")) {
                    return Arrays.asList("all", "blacksmith", "market", "pigeons", "crows", "fishermen", "townsfolk", "farmers", "hoverrides").stream()
                            .filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                }
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("stage")) {
            return Arrays.asList("1", "2", "3", "4", "5").stream()
                    .filter(s -> s.startsWith(args[3]))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
