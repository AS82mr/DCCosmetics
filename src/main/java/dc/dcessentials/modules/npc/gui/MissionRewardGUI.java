package dc.dcessentials.modules.npc.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.npc.NpcDialogueEngine;
import dc.dcessentials.modules.npc.NpcRegistry;
import dc.dcessentials.modules.npc.NpcZoneManager;
import dc.dcessentials.modules.npc.model.NpcCharacter;
import dc.dcessentials.modules.npc.model.NpcStage;
import dc.dcessentials.modules.npc.util.DragoncoteColor;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * High-end Interactive Mission Rewards Collection GUI.
 * Shows money, custom item, and XP rewards with visual flair, audio fanfares,
 * and handles stage transition behaviors (POOF despawn, WALK pathfinding).
 */
public class MissionRewardGUI implements Listener {

    private final NpcRegistry registry;
    private final NpcDialogueEngine dialogueEngine;
    private final NpcZoneManager zoneManager;

    private static class PendingClaim {
        final String charId;
        final int stageNum;
        PendingClaim(String charId, int stageNum) {
            this.charId = charId;
            this.stageNum = stageNum;
        }
    }

    private final Map<UUID, PendingClaim> activeClaims = new HashMap<>();

    public MissionRewardGUI(NpcRegistry registry, NpcDialogueEngine dialogueEngine, NpcZoneManager zoneManager) {
        this.registry = registry;
        this.dialogueEngine = dialogueEngine;
        this.zoneManager = zoneManager;
    }

    public void open(Player player, String charId, int stageNum) {
        NpcCharacter c = registry.getCharacter(charId);
        if (c == null) return;
        NpcStage stage = c.getStage(stageNum);
        if (stage == null) return;

        // EXPLOIT FIX: Verify the player actually completed the stage objectives before allowing claim
        if (!dialogueEngine.isStageCompleted(player, c.getId(), stageNum)) {
            DCMessage.sendError(player, "You have not completed Chapter #" + stageNum + " for " + c.getShortName() + " yet.");
            return;
        }

        if (dialogueEngine.isRewardClaimed(player, charId, stageNum)) {
            player.sendMessage(DragoncoteColor.PREFIX_INFO + "You have already collected all rewards for Chapter #" + stageNum + "!");
            return;
        }

        activeClaims.put(player.getUniqueId(), new PendingClaim(charId, stageNum));

        Inventory inv = Bukkit.createInventory(null, 27, "§8✦ Mission Rewards: §f" + c.getShortName());

        // Background filler
        ItemStack darkPane = createItem(Material.GRAY_STAINED_GLASS_PANE, "§7");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, darkPane);
        }

        // Slot 11: Currency / Cash Reward
        ItemStack moneyItem = createItem(Material.GOLD_INGOT, "§6§l✦ Cash Bounty",
                "§7Granted: §a+$" + String.format("%,d", (long) stage.getCashReward()),
                "§7Transferred directly to your balance.",
                "§f",
                "§a✔ Part of the all-in-one reward bundle",
                "§7Click to collect all 3 rewards at once.");
        inv.setItem(11, moneyItem);

        // Slot 13: Custom Item Reward
        ItemStack rewardItem;
        if (stage.getCustomRewardItem() != null) {
            rewardItem = stage.getCustomRewardItem().clone();
        } else {
            Material icon = stage.getIcon() != null ? stage.getIcon() : Material.NETHER_STAR;
            rewardItem = createItem(icon, "§e§l✦ " + stage.getRewardItemName(),
                    "§7" + (stage.getRewardItemDesc() != null ? stage.getRewardItemDesc() : "Mission equipment artifact."),
                    "§f",
                    "§d✦ Storyline Quest Artifact",
                    "§a✔ Part of the all-in-one reward bundle",
                    "§7Click to collect all 3 rewards at once.");
        }
        inv.setItem(13, rewardItem);

        // Slot 15: Progression & XP
        ItemStack xpItem = createItem(Material.EXPERIENCE_BOTTLE, "§b§l✦ Timeline Mastery & XP",
                "§7Granted: §b+50 Timeline XP",
                "§7Advances chapter progression to §fStage " + (stageNum + 1) + "§7.",
                "§f",
                "§b✔ Timeline Shielding Attuned",
                "§a✔ Part of the all-in-one reward bundle",
                "§7Click to collect all 3 rewards at once.");
        inv.setItem(15, xpItem);

        // Slot 22: Big Collect Button
        ItemStack claimBtn = createItem(Material.LIME_CONCRETE, "§a§l✦ COLLECT ALL 3 REWARDS ✦",
                "§7Simultaneously claims your cash bounty,",
                "§7quest artifact, and timeline mastery experience.",
                "§f",
                "§e▶ Click to Claim All & Advance Chapter");
        inv.setItem(22, claimBtn);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.3f);
    }

    private ItemStack createItem(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(DragoncoteColor.colorize(name));
            if (loreLines.length > 0) {
                List<String> list = new ArrayList<>();
                for (String l : loreLines) {
                    list.add(DragoncoteColor.colorize(l));
                }
                meta.setLore(list);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        if (!title.startsWith("§8✦ Mission Rewards:")) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) return;

        // Any click on slot 11, 13, 15, or 22 triggers collection
        if (slot == 11 || slot == 13 || slot == 15 || slot == 22) {
            PendingClaim claim = activeClaims.remove(player.getUniqueId());
            if (claim == null) {
                player.closeInventory();
                return;
            }

            NpcCharacter c = registry.getCharacter(claim.charId);
            if (c == null) {
                player.closeInventory();
                return;
            }

            NpcStage stage = c.getStage(claim.stageNum);
            if (stage == null) {
                player.closeInventory();
                return;
            }

            if (!dialogueEngine.isStageCompleted(player, c.getId(), claim.stageNum)) {
                player.closeInventory();
                DCMessage.sendError(player, "You must complete this chapter before claiming its bounty.");
                return;
            }

            if (dialogueEngine.isRewardClaimed(player, c.getId(), claim.stageNum)) {
                player.closeInventory();
                DCMessage.sendInfo(player, "You have already collected all rewards for Chapter #" + claim.stageNum + ".");
                return;
            }

            player.closeInventory();

            // 1. Give Cash
            if (stage.getCashReward() > 0) {
                long amount = (long) stage.getCashReward();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cmi money give " + player.getName() + " " + amount);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eco give " + player.getName() + " " + amount);
            }

            // 2. Give Item (with full-inventory overflow drop protection) or Execute Command
            if (stage.getCustomRewardItem() != null && !stage.getCustomRewardItem().getType().isAir()) {
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stage.getCustomRewardItem().clone());
                for (ItemStack drop : leftovers.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            } else if (stage.getExecutionCommand() != null && !stage.getExecutionCommand().equalsIgnoreCase("none")) {
                String cmd = stage.getExecutionCommand().replace("%%player%%", player.getName()).replace("%player%", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }

            // 3. Give XP
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dce givexp " + player.getName() + " 50");

            // 4. Record claimed state & advance timeline chapter
            dialogueEngine.markCashClaimed(player, c.getId(), claim.stageNum);
            dialogueEngine.markItemClaimed(player, c.getId(), claim.stageNum);
            dialogueEngine.markStageCompleted(player, c.getId(), claim.stageNum);
            int currentTimelineIdx = dialogueEngine.getPlayerTimelineIndex(player);
            dialogueEngine.setPlayerTimelineIndex(player, currentTimelineIdx + 1);
            dialogueEngine.saveQuestData();

            // 5. Celebration Audio & Safe Particles
            Location pLoc = player.getLocation();
            player.playSound(pLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
            player.playSound(pLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);
            player.spawnParticle(org.bukkit.Particle.FIREWORK, pLoc.clone().add(0, 1.0, 0), 25, 0.5, 0.8, 0.5, 0.05);
            player.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, pLoc.clone().add(0, 1.2, 0), 15, 0.4, 0.5, 0.4, 0.02);

            player.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
            DCMessage.sendQuest(player, "Mission Rewards Claimed &8(" + c.getShortName() + " - Ch." + claim.stageNum + ")");
            player.sendMessage(ColorUtil.colorize("  &#0CBF93✔ &f+$" + String.format("%,d", (long) stage.getCashReward()) + " &7Imperial Bounty (Deposited)"));
            player.sendMessage(ColorUtil.colorize("  &#0CBF93✔ &f+1 " + stage.getRewardItemName() + " &7(Transferred to Inventory)"));
            player.sendMessage(ColorUtil.colorize("  &#0CBF93✔ &f+50 Timeline XP &7(Advanced to Chapter #" + (currentTimelineIdx + 1) + ")"));
            player.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));

            // 6. Handle Completion Behavior (POOF / WALK / STAY)
            handleCompletionBehavior(player, c, stage);

            // 7. Dispatch next objective dialogue & navigation buttons
            dialogueEngine.dispatchNextMissionDirective(player, c, stage);
        }
    }

    private void handleCompletionBehavior(Player player, NpcCharacter c, NpcStage stage) {
        String beh = stage.getCompletionBehavior();
        if (beh == null || beh.isEmpty() || beh.equalsIgnoreCase("STAY")) {
            beh = c.getCompletionBehavior();
        }
        if (beh == null) beh = "STAY";

        NPC npc = zoneManager.findCitizensNpc(c.getId(), player.getWorld().getName());

        if ("POOF".equalsIgnoreCase(beh) || "POOF_DESPAWN".equalsIgnoreCase(beh)) {
            if (npc != null && npc.isSpawned() && npc.getEntity() != null) {
                Location nLoc = npc.getEntity().getLocation();
                nLoc.getWorld().spawnParticle(org.bukkit.Particle.POOF, nLoc.clone().add(0, 1.0, 0), 40, 0.5, 0.8, 0.5, 0.08);
                nLoc.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, nLoc.clone().add(0, 1.0, 0), 50, 0.6, 1.0, 0.6, 0.5);
                nLoc.getWorld().playSound(nLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
                nLoc.getWorld().playSound(nLoc, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.4f);
            }
            dialogueEngine.updatePlayerNpcVisibility(player);
        } else if ("WALK".equalsIgnoreCase(beh) || "WALK_PATROL_PATH".equalsIgnoreCase(beh) || stage.isWalkOnComplete()) {
            NpcStage nextStage = c.getStage(stage.getStageNumber() + 1);
            Location walkDest = null;
            if (nextStage != null && nextStage.getStageLocation() != null) {
                Location sLoc = nextStage.getStageLocation();
                walkDest = new Location(player.getWorld(), sLoc.getX(), sLoc.getY(), sLoc.getZ(), sLoc.getYaw(), sLoc.getPitch());
            }
            if (walkDest == null) {
                walkDest = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(12));
                walkDest.setY(player.getLocation().getY());
            }

            Location startLoc = (npc != null && npc.isSpawned() && npc.getEntity() != null) ?
                    npc.getEntity().getLocation().clone() : player.getLocation().clone();

            // 1. Update visibility across ALL dimensions immediately (hides Stage 1, reveals Stage 2 at destination)
            // Keep Stage 2 hidden briefly in current world until walker finishes walking
            NPC stage2InWorld = zoneManager.findCitizensNpc(c.getId() + ":" + (stage.getStageNumber() + 1), player.getWorld().getName());
            dialogueEngine.updatePlayerNpcVisibility(player);
            if (stage2InWorld != null) {
                DCCosmetics.getInstance().getNpcCanonManager().hideNpcFromPlayer(player, stage2InWorld);
            }

            final Location finalWalkDest = walkDest;
            try {
                NPC walker = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().createNPC(
                        org.bukkit.entity.EntityType.PLAYER, c.getShortName());
                walker.data().setPersistent("temp_walker", true);
                walker.data().setPersistent("saving", false);
                walker.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, false);

                // Copy signed skin textures from base NPC BEFORE spawning so it never blinks as Steve/Alex
                DCCosmetics.getInstance().getSkinProcessor().copySkinToWalker(npc, walker, player.getWorld().getName(), c.getId());

                // Isolate visibility to this player only
                try {
                    net.citizensnpcs.api.trait.trait.PlayerFilter filter =
                            walker.getOrAddTrait(net.citizensnpcs.api.trait.trait.PlayerFilter.class);
                    filter.setAllowlist();
                    filter.addPlayer(player.getUniqueId());
                } catch (Throwable ignored) {}

                walker.spawn(startLoc);
                DCCosmetics.getInstance().getNpcCanonManager().configureNpcOverhead(walker, c, player.getWorld().getName());
                DCCosmetics.getInstance().getNpcCanonManager().applyEngineMode(walker, c);

                if (walker.getEntity() != null) {
                    for (Player other : player.getWorld().getPlayers()) {
                        if (!other.getUniqueId().equals(player.getUniqueId())) {
                            try { other.hideEntity(DCCosmetics.getInstance(), walker.getEntity()); } catch (Throwable ignored) {}
                        }
                    }
                }

                walker.getNavigator().getDefaultParameters()
                        .baseSpeed(1.25f)
                        .range(64.0f)
                        .avoidWater(false)
                        .useNewPathfinder(true);
                walker.getNavigator().setTarget(finalWalkDest);
                DCMessage.sendInfo(player, "&#FFFFFF" + c.getShortName() + " &7is moving forward to the next objective.");

                new org.bukkit.scheduler.BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        ticks += 5;
                        if (!walker.isSpawned() || walker.getEntity() == null) {
                            cleanupWalker();
                            return;
                        }

                        Location cur = walker.getEntity().getLocation();
                        double distSqToDest = cur.distanceSquared(finalWalkDest);
                        double distSqFromStart = cur.distanceSquared(startLoc);

                        // If Citizens pathfinder fails on stairs/slabs during warmup, manually step the entity toward target
                        if (!walker.getNavigator().isNavigating() && distSqToDest > 4.0 && ticks < 120) {
                            org.bukkit.util.Vector dir = finalWalkDest.toVector().subtract(cur.toVector());
                            dir.setY(0);
                            if (dir.lengthSquared() > 0.01) {
                                dir.normalize().multiply(0.35);
                                Location step = cur.clone().add(dir);
                                step.setDirection(dir);
                                walker.teleport(step, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                            }
                        }

                        // Only complete after 30 ticks (1.5s warmup) AND reaching destination, walking 16+ blocks, or 7s timeout
                        boolean finished = ticks >= 30 && (distSqToDest <= 4.0 || distSqFromStart >= 256.0 || ticks >= 140);
                        if (finished) {
                            cleanupWalker();
                        }
                    }

                    private void cleanupWalker() {
                        cancel();
                        try {
                            if (walker.isSpawned() && walker.getEntity() != null) {
                                Location wLoc = walker.getEntity().getLocation();
                                player.spawnParticle(org.bukkit.Particle.PORTAL, wLoc.clone().add(0, 1.0, 0), 40, 0.4, 0.8, 0.4, 0.2);
                                player.playSound(wLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                            }
                            walker.destroy();
                        } catch (Throwable ignored) {}
                        if (player.isOnline()) {
                            dialogueEngine.updatePlayerNpcVisibility(player);
                        }
                    }
                }.runTaskTimer(DCCosmetics.getInstance(), 5L, 5L);
            } catch (Throwable t) {
                if (player.isOnline()) {
                    dialogueEngine.updatePlayerNpcVisibility(player);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        activeClaims.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        activeClaims.remove(event.getPlayer().getUniqueId());
    }
}
