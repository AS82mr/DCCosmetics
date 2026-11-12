package dc.dcessentials.modules.npc;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.npc.model.NpcCharacter;
import dc.dcessentials.modules.npc.model.NpcStage;
import dc.dcessentials.modules.npc.model.NpcZone;
import dc.dcessentials.modules.npc.util.DragoncoteColor;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles character interactions, dialogue rendering, discovery dossiers,
 * track/claim buttons, and questline progression matching the Dragoncote aesthetic.
 */
public class NpcDialogueEngine {

    private final NpcRegistry registry;
    private final NpcZoneManager zoneManager;

    // Interaction cooldown debounce to prevent double execution (ms)
    private final Map<UUID, Long> talkCooldown = new ConcurrentHashMap<>();

    // Player quest timeline indices: player UUID -> current timeline index (1 to N)
    private final Map<UUID, Integer> playerTimeline = new ConcurrentHashMap<>();
    // Player completed stages: "uuid:charId:stage"
    private final Set<String> completedStages = ConcurrentHashMap.newKeySet();
    // Claimed cash / items: "uuid:charId:stage"
    private final Set<String> claimedCash = ConcurrentHashMap.newKeySet();
    private final Set<String> claimedItems = ConcurrentHashMap.newKeySet();
    // Met NPCs for discovery dossier bonus: "uuid:charId"
    private final Set<String> metNpcs = ConcurrentHashMap.newKeySet();

    private final java.io.File questDataFile;
    private final org.bukkit.configuration.file.YamlConfiguration questDataCfg;
    private dc.dcessentials.modules.npc.gui.MissionRewardGUI missionRewardGUI;

    public void setMissionRewardGUI(dc.dcessentials.modules.npc.gui.MissionRewardGUI missionRewardGUI) {
        this.missionRewardGUI = missionRewardGUI;
    }

    public void markCashClaimed(Player player, String charId, int stageNum) {
        claimedCash.add(player.getUniqueId().toString() + ":" + charId.toLowerCase() + ":" + stageNum);
    }

    public void markItemClaimed(Player player, String charId, int stageNum) {
        claimedItems.add(player.getUniqueId().toString() + ":" + charId.toLowerCase() + ":" + stageNum);
    }

    public NpcDialogueEngine(NpcRegistry registry, NpcZoneManager zoneManager) {
        this.registry = registry;
        this.zoneManager = zoneManager;

        java.io.File dataDir = new java.io.File(DCCosmetics.getInstance().getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        java.io.File newQuest = new java.io.File(dataDir, "player_quests.yml");
        java.io.File oldQuest = new java.io.File(DCCosmetics.getInstance().getDataFolder(), "player_quest_data.yml");
        if (!newQuest.exists() && oldQuest.exists()) oldQuest.renameTo(newQuest);
        this.questDataFile = newQuest.exists() ? newQuest : (oldQuest.exists() ? oldQuest : newQuest);

        if (!questDataFile.exists()) {
            try { questDataFile.createNewFile(); } catch (Exception ignored) {}
        }
        this.questDataCfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(questDataFile);
        this.claimedCash.addAll(questDataCfg.getStringList("claimed_cash"));
        this.claimedItems.addAll(questDataCfg.getStringList("claimed_items"));
        this.completedStages.addAll(questDataCfg.getStringList("completed_stages"));
        this.metNpcs.addAll(questDataCfg.getStringList("met_npcs"));
        if (questDataCfg.contains("timelines")) {
            org.bukkit.configuration.ConfigurationSection sec = questDataCfg.getConfigurationSection("timelines");
            if (sec != null) {
                for (String k : sec.getKeys(false)) {
                    try { playerTimeline.put(UUID.fromString(k), sec.getInt(k)); } catch (Exception ignored) {}
                }
            }
        }
    }

    public synchronized void saveQuestData() {
        final List<String> cashSnap = new ArrayList<>(claimedCash);
        final List<String> itemsSnap = new ArrayList<>(claimedItems);
        final List<String> stagesSnap = new ArrayList<>(completedStages);
        final List<String> metSnap = new ArrayList<>(metNpcs);
        final Map<UUID, Integer> timelineSnap = new HashMap<>(playerTimeline);

        Bukkit.getScheduler().runTaskAsynchronously(DCCosmetics.getInstance(), () -> {
            synchronized (questDataCfg) {
                questDataCfg.set("claimed_cash", cashSnap);
                questDataCfg.set("claimed_items", itemsSnap);
                questDataCfg.set("completed_stages", stagesSnap);
                questDataCfg.set("met_npcs", metSnap);
                for (Map.Entry<UUID, Integer> entry : timelineSnap.entrySet()) {
                    questDataCfg.set("timelines." + entry.getKey().toString(), entry.getValue());
                }
                try { questDataCfg.save(questDataFile); } catch (Exception ignored) {}
            }
        });
    }

    public int getPlayerTimelineIndex(Player player) {
        return playerTimeline.getOrDefault(player.getUniqueId(), 1);
    }

    public void setPlayerTimelineIndex(Player player, int index) {
        playerTimeline.put(player.getUniqueId(), Math.max(1, index));
        saveQuestData();
    }

    public synchronized void resetPlayerQuestProgress(Player player) {
        if (player == null) return;
        UUID uid = player.getUniqueId();
        String prefix = uid.toString() + ":";
        playerTimeline.put(uid, 1);
        completedStages.removeIf(s -> s.startsWith(prefix));
        claimedCash.removeIf(s -> s.startsWith(prefix));
        claimedItems.removeIf(s -> s.startsWith(prefix));
        metNpcs.removeIf(s -> s.startsWith(prefix));
        saveQuestData();

        // Restore and unhide all quest NPCs across all dimensions
        DCCosmetics.getInstance().getNpcCanonManager().resetQuestlineNpcsForPlayer(player);
    }

    public boolean shouldNpcEntityBeHiddenForPlayer(Player player, NPC npc) {
        if (player == null || npc == null) return false;
        if (!npc.data().has("dcnpc_id")) return false;

        String rawId = npc.data().get("dcnpc_id").toString().toLowerCase();
        if (rawId.contains(":")) {
            // Stage-specific instance (e.g. "corvus:2")
            String[] parts = rawId.split(":");
            String charId = parts[0];
            int stageNum = 1;
            try { stageNum = Integer.parseInt(parts[1]); } catch (Exception ignored) {}

            // Must have completed previous stage to see this stage instance
            if (stageNum > 1 && !isStageCompleted(player, charId, stageNum - 1)) {
                return true; // Hide: player has not reached this stage yet
            }

            // If player already completed this stage AND this stage has POOF, hide it
            NpcCharacter c = registry.getCharacter(charId);
            if (c != null) {
                NpcStage s = c.getStage(stageNum);
                if (s != null && "POOF".equalsIgnoreCase(s.getCompletionBehavior())) {
                    if (isStageCompleted(player, charId, stageNum)) {
                        return true;
                    }
                }
            }
            return false;
        }

        // Base / Stage 1 instance (e.g. "corvus")
        String charId = rawId;
        NpcCharacter c = registry.getCharacter(charId);
        if (c == null) return false;

        // If player has completed stage 1:
        if (isStageCompleted(player, charId, 1)) {
            NpcStage s1 = c.getStage(1);
            if (s1 != null && "POOF".equalsIgnoreCase(s1.getCompletionBehavior())) {
                return true; // Stage 1 is done and explicitly poofed for this player
            }

            // If a subsequent stage exists with a distinct stage location or separate spawned NPC, hide base instance
            NpcStage s2 = c.getStage(2);
            if (s2 != null && (s2.getStageLocation() != null || zoneManager.findCitizensNpc(charId + ":2", null) != null)) {
                return true; // Character has relocated to Stage 2 coordinates
            }

            if ("POOF".equalsIgnoreCase(c.getCompletionBehavior())) {
                boolean allDone = true;
                for (int st : c.getStages().keySet()) {
                    if (!isStageCompleted(player, charId, st)) {
                        allDone = false;
                        break;
                    }
                }
                if (allDone) return true;
            }
        }

        return false;
    }

    public boolean shouldNpcBeHiddenForPlayer(Player player, String charId) {
        if (player == null || charId == null) return false;
        NpcCharacter c = registry.getCharacter(charId);
        if (c == null) return false;

        if (isStageCompleted(player, charId, 1)) {
            NpcStage s1 = c.getStage(1);
            if (s1 != null && "POOF".equalsIgnoreCase(s1.getCompletionBehavior())) {
                return true;
            }
            NpcStage s2 = c.getStage(2);
            if (s2 != null && s2.getStageLocation() != null) {
                return true;
            }
        }
        return false;
    }

    public void updatePlayerNpcVisibility(Player player) {
        if (player == null || !player.isOnline() || CitizensAPI.getPlugin() == null) return;
        DCCosmetics.debugNpc("updatePlayerNpcVisibility evaluating for " + player.getName());

        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (!npc.data().has("dcnpc_id")) continue;
            boolean hide = shouldNpcEntityBeHiddenForPlayer(player, npc);
            if (hide) {
                DCCosmetics.getInstance().getNpcCanonManager().hideNpcFromPlayer(player, npc);
            } else {
                DCCosmetics.getInstance().getNpcCanonManager().showNpcToPlayer(player, npc);
            }
        }
    }

    public boolean isStageCompleted(Player player, String charId, int stage) {
        return completedStages.contains(player.getUniqueId().toString() + ":" + charId.toLowerCase() + ":" + stage);
    }

    public void markStageCompleted(Player player, String charId, int stage) {
        completedStages.add(player.getUniqueId().toString() + ":" + charId.toLowerCase() + ":" + stage);
        saveQuestData();
    }

    public boolean isRewardClaimed(Player player, String charId, int stageNum) {
        String claimKey = player.getUniqueId().toString() + ":" + charId.toLowerCase() + ":" + stageNum;
        return claimedCash.contains(claimKey) && claimedItems.contains(claimKey);
    }

    public int getMetCount(Player player) {
        int count = 0;
        String prefix = player.getUniqueId().toString() + ":";
        for (String entry : metNpcs) {
            if (entry.startsWith(prefix)) count++;
        }
        return count;
    }

    /**
     * Smart Right-Click handler: opens service menu directly if completed/past quest,
     * otherwise triggers dialogue interaction.
     */
    public void handleRightClick(Player player, String charId) {
        NpcCharacter c = registry.getCharacter(charId);
        if (c == null) return;

        int currentIdx = getPlayerTimelineIndex(player);
        List<String> timeline = registry.getTimelineOrder();
        String activeNode = (currentIdx <= timeline.size()) ? timeline.get(currentIdx - 1) : null;

        boolean isCurrentQuestTarget = false;
        if (activeNode != null) {
            String[] parts = activeNode.split(":");
            if (parts[0].equalsIgnoreCase(charId)) {
                isCurrentQuestTarget = true;
            }
        }

        // If player is on their active quest chapter with this NPC, show dialogue
        if (isCurrentQuestTarget) {
            interact(player, charId);
            return;
        }

        // If this character has a registered service command and player isn't actively doing a quest with them
        if (c.getServiceCommand() != null && !c.getServiceCommand().isEmpty()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            player.sendMessage(DragoncoteColor.PREFIX_INFO + "Opening " + c.getServiceTitle() + "...");
            player.performCommand(c.getServiceCommand());
            return;
        }

        // Default fallback: regular interaction
        interact(player, charId);
    }

    /**
     * Central dialogue interaction entry point.
     */
    public void interact(Player player, String charId) {
        // Debounce: prevent duplicate executions within 500ms
        long now = System.currentTimeMillis();
        long last = talkCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 500L) {
            return;
        }
        talkCooldown.put(player.getUniqueId(), now);

        String baseId = (charId != null && charId.contains(":")) ? charId.split(":")[0] : charId;
        int clickedStage = 1;
        boolean hasExplicitStage = false;
        if (charId != null && charId.contains(":")) {
            try {
                clickedStage = Integer.parseInt(charId.split(":")[1]);
                hasExplicitStage = true;
            } catch (NumberFormatException ignored) {}
        }

        NpcCharacter c = registry.getCharacter(baseId);
        if (c == null) {
            player.sendMessage(DragoncoteColor.PREFIX_ERROR + "Unknown character ID: §f" + charId);
            return;
        }

        String worldName = player.getWorld().getName();
        boolean isBw = worldName.toLowerCase().contains("bw") || worldName.toLowerCase().contains("drain") || worldName.toLowerCase().contains("void");

        // 1. First Discovery Dossier ($500 + 15 Mastery XP)
        String metKey = player.getUniqueId().toString() + ":" + c.getId();
        if (metNpcs.add(metKey)) {
            // Give Vault economy bonus ($500)
            giveMoney(player, 500);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dce givexp " + player.getName() + " 15");
            int metCount = getMetCount(player);
            player.sendMessage(DragoncoteColor.PREFIX_INFO + "Personnel Dossier Unlocked: §f" + c.getShortName() +
                    " §7(§a+$500 §7| §e+15 Mastery XP §7| §f" + metCount + "/" + registry.getAllCharacters().size() + " Met§7)");
            player.playSound(player.getLocation(), Sound.BLOCK_VAULT_INSERT_ITEM, 0.9f, 1.6f);
        }

        // 2. Play speech typewriter audio
        playSpeechAudio(player);

        // 4. Determine Global Storyline Progression State
        int currentIdx = getPlayerTimelineIndex(player);
        List<String> timeline = registry.getTimelineOrder();
        String activeNode = (currentIdx <= timeline.size()) ? timeline.get(currentIdx - 1) : null;

        String targetCharId = "corvus";
        int targetStageNum = 1;
        if (activeNode != null) {
            String[] parts = activeNode.split(":");
            targetCharId = parts[0];
            try {
                targetStageNum = Integer.parseInt(parts[1]);
            } catch (Exception ignored) {}
        }

        // 3. Only clear waypoint if player arrived at their active quest target
        if (baseId.equalsIgnoreCase(targetCharId)) {
            DCCosmetics.getInstance().getWaypointManager().clearWaypoint(player);
        }

        boolean isCurrentTarget = baseId.equalsIgnoreCase(targetCharId) ||
                (hasExplicitStage && clickedStage > 1 && isStageCompleted(player, baseId, clickedStage - 1));

        int stageToClaim;
        if (hasExplicitStage) {
            stageToClaim = clickedStage;
        } else if (baseId.equalsIgnoreCase(targetCharId)) {
            stageToClaim = targetStageNum;
        } else {
            // Find the lowest unclaimed stage for this character instead of hardcoding stage 2
            stageToClaim = 1;
            List<Integer> stgKeys = new ArrayList<>(c.getStages().keySet());
            Collections.sort(stgKeys);
            for (int stgKey : stgKeys) {
                if (!isRewardClaimed(player, baseId, stgKey)) {
                    stageToClaim = stgKey;
                    break;
                }
            }
        }

        String dialogueLine;
        String nextContactId = "corvus";
        boolean canClaim = false;

        if (isCurrentTarget) {
            NpcStage stage = c.getStage(stageToClaim);
            if (stage == null && stageToClaim > 1) {
                stage = c.getStage(1);
                stageToClaim = 1;
            }

            if (stage != null) {
                nextContactId = (stage.getNextCharacterId() != null && !stage.getNextCharacterId().isEmpty()) ?
                        stage.getNextCharacterId() : "corvus";

                String req = stage.getRequires();
                boolean requiresStage = (req != null && !req.isEmpty() && !req.equalsIgnoreCase("none") && req.contains(":"));
                boolean requiresItem = (req != null && !req.isEmpty() && !req.equalsIgnoreCase("none") && !req.contains(":"));

                // 1. Check prerequisite stage completion (e.g. "corvus:1")
                if (requiresStage) {
                    String[] parts = req.split(":");
                    String reqChar = parts[0];
                    int reqStageNum = 1;
                    try { reqStageNum = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}

                    if (!isStageCompleted(player, reqChar, reqStageNum)) {
                        NpcCharacter prereqChar = registry.getCharacter(reqChar);
                        String prereqName = (prereqChar != null) ? prereqChar.getShortName() : reqChar;
                        dialogueLine = "You must first conclude your mission with " + prereqName + " (Stage #" + reqStageNum + ") before I can assist you.";
                        canClaim = false;
                        nextContactId = reqChar;
                        player.sendMessage(DragoncoteColor.PREFIX_WARN + "Prerequisite Incomplete: §7Must complete §f" + prereqName + " §7(Stage #" + reqStageNum + ").");
                        sendFormattedDialogue(player, c, dialogueLine, nextContactId, canClaim, stageToClaim, isBw);
                        return;
                    }
                }

                // 2. Check item delivery requirement
                boolean hasItem = false;
                ItemStack foundItem = null;
                if (requiresItem) {
                    for (ItemStack is : player.getInventory().getContents()) {
                        if (is != null && !is.getType().isAir()) {
                            String name = (is.hasItemMeta() && is.getItemMeta().hasDisplayName()) ? is.getItemMeta().getDisplayName() : is.getType().name();
                            if (name.toLowerCase().contains(req.toLowerCase()) || is.getType().name().equalsIgnoreCase(req)) {
                                hasItem = true;
                                foundItem = is;
                                break;
                            }
                        }
                    }

                    if (!hasItem) {
                        dialogueLine = stage.getDialogueLine();
                        canClaim = false;
                        nextContactId = c.getId();
                        player.sendMessage(DragoncoteColor.PREFIX_WARN + "Objective Required: §7Bring §f" + req + " §7to complete this chapter.");
                        sendFormattedDialogue(player, c, dialogueLine, nextContactId, canClaim, stageToClaim, isBw);
                        return;
                    }

                    // Player delivered required item
                    if (foundItem.getAmount() > 1) {
                        foundItem.setAmount(foundItem.getAmount() - 1);
                    } else {
                        player.getInventory().remove(foundItem);
                    }
                    player.sendMessage(DragoncoteColor.PREFIX_SUCCESS + "Delivered §f" + req + " §7to " + c.getShortName() + "!");
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                }

                // 3. Objective satisfied (or no requirement)
                if (isRewardClaimed(player, c.getId(), stage.getStageNumber())) {
                    dialogueLine = (stage.getCompletionLine() != null && !stage.getCompletionLine().isEmpty()) ?
                            stage.getCompletionLine() : c.getQuote();
                    canClaim = false;
                } else {
                    markStageCompleted(player, c.getId(), stage.getStageNumber());
                    dialogueLine = (stage.getCompletionLine() != null && !stage.getCompletionLine().isEmpty()) ?
                            stage.getCompletionLine() : stage.getDialogueLine();
                    canClaim = true;
                }
            } else {
                dialogueLine = c.getQuote();
            }
        } else {
            // Player is speaking with someone else in the world
            NpcCharacter activeTargetChar = registry.getCharacter(targetCharId);
            String targetName = (activeTargetChar != null) ? activeTargetChar.getShortName() : "Corvus";
            nextContactId = targetCharId;
            canClaim = false;

            List<String> ambients = c.getAmbientLines();
            String ambientQuote = (!ambients.isEmpty()) ?
                    ambients.get(ThreadLocalRandom.current().nextInt(ambients.size())) : c.getQuote();

            dialogueLine = ambientQuote + " Focus on your mission: report to " + targetName + ".";

            // Cross-world alert if target is elsewhere
            checkCrossWorldNavigation(player, targetCharId);
        }

        // Send styled dialogue matching dcnpc.sk exactly
        sendFormattedDialogue(player, c, dialogueLine, nextContactId, canClaim, stageToClaim, isBw);
    }

    /**
     * Sends the exact 3-line dialogue structure from dcnpc.sk with interactive buttons.
     */
    private void sendFormattedDialogue(Player player, NpcCharacter c, String line, String nextContactId,
                                       boolean canClaim, int stageNum, boolean isBw) {
        String worldName = player.getWorld().getName();

        // Line 1: Header component with faction badge & click-to-open-dossier
        TextComponent header = registry.getChatHeader(c.getId(), worldName);
        player.spigot().sendMessage(header);

        // Line 2: Dialogue text in #E2F6A8
        String bodyText = isBw ? DragoncoteColor.stripToGrayscale(line) : DragoncoteColor.colorize("<#E2F6A8>\"" + line + "\"");
        player.sendMessage(bodyText);

        // Distance-based smart navigation chat mechanism
        NpcCharacter nextChar = registry.getCharacter(nextContactId);
        String nextName = (nextChar != null) ? nextChar.getShortName() : "Corvus";
        Location targetLoc = null;
        NPC targetNpc = zoneManager.findCitizensNpc(nextContactId, player.getWorld().getName());
        if (targetNpc != null) {
            targetLoc = (targetNpc.isSpawned() && targetNpc.getEntity() != null) ?
                    targetNpc.getEntity().getLocation() : targetNpc.getStoredLocation();
        }

        double dist = -1;
        if (targetLoc != null && targetLoc.getWorld() != null && targetLoc.getWorld().equals(player.getWorld())) {
            dist = player.getLocation().distance(targetLoc);
        }

        if (dist >= 0 && dist <= 50.0) {
            player.sendMessage(DragoncoteColor.colorize("<#E2F6A8>[" + c.getShortName() + "] &7Just walk, don't be lazy! It's right around &a" + (int)dist + " blocks &7away! &f➜"));
        } else if (dist > 50.0) {
            player.sendMessage(DragoncoteColor.colorize("<#E2F6A8>[" + c.getShortName() + "] &7The next rendezvous is far across the district (&a" + (int)dist + " blocks&7). Take the transit network:"));
        }

        // Line 3: Interactive Action Buttons Row
        TextComponent buttonsRow = new TextComponent("");

        // If stage is ready to claim, display ONLY the Claim button!
        if (canClaim) {
            TextComponent btnClaim = new TextComponent(DragoncoteColor.colorize("<#38BDF8>「 <#FCD34D>✦ <#FEF08A>&lClaim Rewards <#38BDF8>」"));
            btnClaim.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dcnpc claim " + c.getId() + " " + stageNum));
            btnClaim.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§aClick to open the Mission Rewards vault!").create()));
            buttonsRow.addExtra(btnClaim);
            player.spigot().sendMessage(buttonsRow);
            return;
        }

        // Button 1: [ Track Objective ]
        TextComponent btnTrack = new TextComponent(DragoncoteColor.colorize("<#38BDF8>「 <#60A5FA>◆ <#BAE6FD>&lTrack Objective <#38BDF8>」  "));
        btnTrack.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dcnpc waypoint " + nextContactId));
        btnTrack.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§bTrack waypoint to " + nextName).create()));
        buttonsRow.addExtra(btnTrack);

        // Fast Travel Warp Button (if distant)
        String warpAlias = (nextChar != null && nextChar.getBoundWarpAlias() != null) ? nextChar.getBoundWarpAlias() :
                (nextChar != null && nextChar.getZoneId() != null ? nextChar.getZoneId() : null);
        if (dist > 50.0 && warpAlias != null) {
            TextComponent btnWarp = new TextComponent(DragoncoteColor.colorize("<#38BDF8>「 <#F59E0B>⚡ <#FDE68A>&lFast Travel <#38BDF8>」  "));
            btnWarp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dcnpc warp " + warpAlias));
            btnWarp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§eFast travel to " + nextName).create()));
            buttonsRow.addExtra(btnWarp);
        }

        // Button 2: [ Quest Log ]
        TextComponent btnLog = new TextComponent(DragoncoteColor.colorize("<#38BDF8>「 <#FCD34D>✦ <#FEF08A>&lQuest Log <#38BDF8>」"));
        btnLog.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lore"));
        btnLog.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§eOpen Quest Log").create()));
        buttonsRow.addExtra(btnLog);

        // Button 3: Service Button (if present)
        if (c.getServiceCommand() != null && !c.getServiceCommand().isEmpty()) {
            TextComponent btnSrv = new TextComponent(DragoncoteColor.colorize("  <#38BDF8>「 <#D946EF>★ <#F0ABFC>&l" + c.getServiceTitle() + " <#38BDF8>」"));
            btnSrv.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + c.getServiceCommand()));
            btnSrv.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§dOpen " + c.getServiceTitle()).create()));
            buttonsRow.addExtra(btnSrv);
        }

        player.spigot().sendMessage(buttonsRow);
    }

    /**
     * Executes reward claiming for a stage.
     */
    public void claimRewards(Player player, String charId, int stageNum) {
        if (missionRewardGUI != null) {
            missionRewardGUI.open(player, charId, stageNum);
            return;
        }

        NpcCharacter c = registry.getCharacter(charId);
        if (c == null) return;

        NpcStage stage = c.getStage(stageNum);
        if (stage == null) {
            player.sendMessage(DragoncoteColor.PREFIX_ERROR + "No quest stage #" + stageNum + " found for " + c.getShortName());
            return;
        }

        String claimKey = player.getUniqueId().toString() + ":" + c.getId() + ":" + stageNum;
        if (claimedCash.contains(claimKey) && claimedItems.contains(claimKey)) {
            player.sendMessage(DragoncoteColor.PREFIX_INFO + "You have already claimed the rewards for this chapter!");
            return;
        }

        // Deposit Cash
        if (!claimedCash.contains(claimKey)) {
            claimedCash.add(claimKey);
            if (stage.getCashReward() > 0) {
                giveMoney(player, (long) stage.getCashReward());
            }
        }

        // Give Item / Execute Command
        if (!claimedItems.contains(claimKey)) {
            claimedItems.add(claimKey);
            if (stage.getCustomRewardItem() != null && !stage.getCustomRewardItem().getType().isAir()) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stage.getCustomRewardItem().clone());
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
            if (stage.getExecutionCommand() != null && !stage.getExecutionCommand().equalsIgnoreCase("none") && !stage.getExecutionCommand().isEmpty()) {
                String cmd = stage.getExecutionCommand().replace("%%player%%", player.getName()).replace("%player%", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        }

        // Give XP
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dce givexp " + player.getName() + " 50");
        markStageCompleted(player, c.getId(), stageNum);
        int currentTimelineIdx = getPlayerTimelineIndex(player);
        setPlayerTimelineIndex(player, currentTimelineIdx + 1);
        saveQuestData();

        // Safe celebration particles
        Location pLoc = player.getLocation();
        player.spawnParticle(org.bukkit.Particle.FIREWORK, pLoc.clone().add(0, 1.0, 0), 20, 0.4, 0.6, 0.4, 0.05);
        player.playSound(pLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        player.sendMessage(DragoncoteColor.PREFIX_GENERAL + "Claimed Mission Rewards for §f" + c.getShortName() + " §a(+$" + (long) stage.getCashReward() + " | +50 XP | " + stage.getRewardItemName() + ")!");
    }

    public void dispatchNextMissionDirective(Player player, NpcCharacter c, NpcStage stage) {
        String worldName = player.getWorld().getName();
        String nextCharId = stage.getNextCharacterId();
        if (nextCharId == null || nextCharId.equalsIgnoreCase("none")) {
            nextCharId = registry.getNextTimelineCharacterId(c.getId());
        }
        NpcCharacter nextChar = registry.getCharacter(nextCharId);
        String nextName = (nextChar != null) ? nextChar.getShortName() : "the Spire";
        String nextZone = (nextChar != null && nextChar.getZoneId() != null) ?
                zoneManager.getZoneDisplayName(nextChar.getZoneId()) : "Sanctuary";

        // Header
        player.spigot().sendMessage(registry.getChatHeader(c.getId(), worldName));

        // Dialogue line
        player.sendMessage(DragoncoteColor.colorize("<#E2F6A8>\"Mission complete, Agent. Report to " + nextName + " at " + nextZone + " immediately.\""));

        // Buttons row
        TextComponent buttonsRow = new TextComponent("");

        // [ Track Objective ]
        TextComponent btnTrack = new TextComponent(DragoncoteColor.colorize("<#38BDF8>「 <#60A5FA>◆ <#BAE6FD>&lTrack Objective <#38BDF8>」  "));
        btnTrack.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dcnpc waypoint " + nextCharId));
        btnTrack.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§bTrack waypoint to " + nextName).create()));
        buttonsRow.addExtra(btnTrack);

        // [ Fast Travel ]
        String warpAlias = (nextChar != null && nextChar.getBoundWarpAlias() != null) ? nextChar.getBoundWarpAlias() :
                (nextChar != null && nextChar.getZoneId() != null ? nextChar.getZoneId() : null);
        if (warpAlias != null) {
            TextComponent btnWarp = new TextComponent(DragoncoteColor.colorize("<#38BDF8>「 <#F59E0B>⚡ <#FDE68A>&lFast Travel <#38BDF8>」  "));
            btnWarp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dcessentials:warp " + warpAlias));
            btnWarp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§eFast travel to " + nextZone).create()));
            buttonsRow.addExtra(btnWarp);
        }

        // [ Quest Log ]
        TextComponent btnLog = new TextComponent(DragoncoteColor.colorize("<#38BDF8>「 <#FCD34D>✦ <#FEF08A>&lQuest Log <#38BDF8>」"));
        btnLog.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lore"));
        btnLog.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§eOpen Quest Log").create()));
        buttonsRow.addExtra(btnLog);

        player.spigot().sendMessage(buttonsRow);
    }

    private void giveMoney(Player player, long amount) {
        // Vault / CMI Console fallback
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cmi money give " + player.getName() + " " + amount);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eco give " + player.getName() + " " + amount);
    }

    private boolean isSanctuarySpawnWorld(String worldName) {
        if (worldName == null) return false;
        String lower = worldName.toLowerCase();
        return lower.equals("main") || lower.equals("snow") || lower.equals("bw") ||
               lower.contains("spawn_main") || lower.contains("spawn_snow") || lower.contains("spawn_bw");
    }

    public void checkCrossWorldNavigation(Player player, String targetCharId) {
        NpcCharacter targetChar = registry.getCharacter(targetCharId);
        if (targetChar == null) return;

        String currentWorld = player.getWorld().getName();
        if (targetChar.getSector() == 1 && isSanctuarySpawnWorld(currentWorld)) {
            // Sanctuary Hub objective inside an active spawn cycle: do not send cross-dimension!
            return;
        }

        NPC citizensNpc = zoneManager.findCitizensNpc(targetCharId, null);
        Location targetLoc = null;
        if (citizensNpc != null) {
            targetLoc = (citizensNpc.isSpawned() && citizensNpc.getEntity() != null) ?
                    citizensNpc.getEntity().getLocation() : citizensNpc.getStoredLocation();
        }

        if (targetLoc == null || targetLoc.getWorld() == null) return;

        String targetWorld = targetLoc.getWorld().getName();
        if (currentWorld.equalsIgnoreCase(targetWorld)) return;

        String targetDisplayName = targetWorld.toLowerCase().contains("lower") ? "LowerCity" : "Sanctuary Hub";
        String tpCmd = targetWorld.toLowerCase().contains("lower") ? "warp slums" : "warp spawn";

        player.sendActionBar("§c§lNavigation: " + targetChar.getShortName() + " is in " + targetDisplayName + "!");

        TextComponent msg = new TextComponent(DragoncoteColor.PREFIX_INFO + "Objective §f" + targetChar.getShortName() + " §7is across dimensions in §c" + targetDisplayName + "§7. ");
        TextComponent warpButton = new TextComponent(DragoncoteColor.colorize("<#38BDF8>&l[⚡ Click to Warp]"));
        warpButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + tpCmd));
        warpButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§aClick to warp across dimensions to §f" + targetDisplayName).create()));

        msg.addExtra(warpButton);
        player.spigot().sendMessage(msg);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.4f);
    }

    public void trackNpcWaypoint(Player player, String rawCharId) {
        String baseId = (rawCharId != null && rawCharId.contains(":")) ? rawCharId.split(":")[0] : rawCharId;
        int targetStage = 1;
        if (rawCharId != null && rawCharId.contains(":")) {
            try {
                targetStage = Integer.parseInt(rawCharId.split(":")[1]);
            } catch (NumberFormatException ignored) {}
        } else {
            // Check player's progress on this character to resolve correct stage waypoint
            int s = 1;
            while (isStageCompleted(player, baseId, s)) {
                s++;
            }
            targetStage = s;
        }

        NpcCharacter targetChar = registry.getCharacter(baseId);
        String currentWorld = player.getWorld().getName();
        Location targetLoc = null;

        // 1. If targeting stage > 1, first look for stage-specific NPC: baseId + ":" + targetStage
        if (targetStage > 1) {
            NPC stageNpc = zoneManager.findCitizensNpc(baseId + ":" + targetStage, currentWorld);
            if (stageNpc == null) {
                stageNpc = zoneManager.findCitizensNpc(baseId + ":" + targetStage, null);
                if (stageNpc != null) {
                    Location sLoc = (stageNpc.isSpawned() && stageNpc.getEntity() != null) ?
                            stageNpc.getEntity().getLocation() : stageNpc.getStoredLocation();
                    if (sLoc != null) {
                        targetLoc = sLoc.clone();
                        targetLoc.setWorld(player.getWorld());
                    }
                }
            } else {
                targetLoc = (stageNpc.isSpawned() && stageNpc.getEntity() != null) ?
                        stageNpc.getEntity().getLocation() : stageNpc.getStoredLocation();
            }
            // Fallback: check if the stage object has an explicit stageLocation configured
            if (targetLoc == null && targetChar != null) {
                NpcStage st = targetChar.getStage(targetStage);
                if (st != null && st.getStageLocation() != null) {
                    targetLoc = st.getStageLocation().clone();
                    targetLoc.setWorld(player.getWorld());
                }
            }
        }

        // 2. Base NPC lookup in current world
        if (targetLoc == null) {
            NPC citizensNpc = zoneManager.findCitizensNpc(baseId, currentWorld);
            if (citizensNpc != null) {
                targetLoc = (citizensNpc.isSpawned() && citizensNpc.getEntity() != null) ?
                        citizensNpc.getEntity().getLocation() : citizensNpc.getStoredLocation();
            }
        }

        // 3. If target is in Sanctuary Hub and player is in a spawn cycle, fallback to cross-world anchor
        if (targetLoc == null || targetLoc.getWorld() == null) {
            if (targetChar != null && targetChar.getSector() == 1 && isSanctuarySpawnWorld(currentWorld)) {
                NPC fallback = zoneManager.findCitizensNpc(baseId, null);
                if (fallback != null) {
                    Location fLoc = (fallback.isSpawned() && fallback.getEntity() != null) ?
                            fallback.getEntity().getLocation() : fallback.getStoredLocation();
                    if (fLoc != null) {
                        targetLoc = fLoc.clone();
                        targetLoc.setWorld(player.getWorld());
                    }
                }
                if (targetLoc == null) {
                    Location live = DCCosmetics.getInstance().getNpcCanonManager().getLiveLocation(baseId);
                    if (live != null) {
                        targetLoc = live.clone();
                        targetLoc.setWorld(player.getWorld());
                    }
                }
            }
        }

        if (targetLoc != null && targetLoc.getWorld() != null && targetLoc.getWorld().getName().equalsIgnoreCase(currentWorld)) {
            DCCosmetics.getInstance().getWaypointManager().setWaypoint(player, targetLoc);
            String displayName = (targetChar != null ? targetChar.getShortName() : baseId) + (targetStage > 1 ? " (Stage #" + targetStage + ")" : "");
            player.sendMessage(DragoncoteColor.PREFIX_INFO + "HUD Waypoint locked onto §f" + displayName + "§7.");
            player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 1.0f, 1.5f);
            return;
        }

        checkCrossWorldNavigation(player, baseId);
    }

    public void startEscort(String charId, Player player) {
        NPC npc = zoneManager.findCitizensNpc(charId, player.getWorld().getName());
        if (npc == null || !npc.isSpawned() || npc.getEntity() == null) return;

        Location targetDest = player.getLocation().add(player.getLocation().getDirection().multiply(15));
        targetDest.setY(player.getWorld().getHighestBlockYAt(targetDest) + 1);

        npc.getNavigator().setTarget(targetDest);
        player.sendMessage(DragoncoteColor.PREFIX_INFO + "§f" + npc.getName() + " §7is moving forward to the objective.");
    }

    private void playSpeechAudio(Player player) {
        float pitch = 1.4f + ThreadLocalRandom.current().nextFloat() * 0.4f;
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, pitch);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.8f);
    }
}
