package dc.dcessentials.modules.npc.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.npc.NpcRegistry;
import dc.dcessentials.modules.npc.model.NpcCharacter;
import dc.dcessentials.modules.npc.model.NpcStage;
import dc.dcessentials.modules.npc.util.DragoncoteColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 54-Slot Deep Editor for an individual Storyline Chapter.
 * Features drag-and-drop custom item rewards (preserving full NBT, CMD, and lore),
 * stage relocation to player coordinates, completion behavior toggles (STAY, POOF, WALK),
 * and district warp binding.
 */
public class NpcSingleStageGUI implements Listener {

    private final NpcRegistry registry;
    private NpcStageEditorGUI stageListGUI;
    private WarpMenuGUI warpMenuGUI;

    private final Map<UUID, StageEditSession> activeSessions = new HashMap<>();

    public static class StageEditSession {
        public final String charId;
        public final int stageNum;

        public StageEditSession(String charId, int stageNum) {
            this.charId = charId;
            this.stageNum = stageNum;
        }
    }

    public NpcSingleStageGUI(NpcRegistry registry) {
        this.registry = registry;
    }

    public void setStageListGUI(NpcStageEditorGUI stageListGUI) {
        this.stageListGUI = stageListGUI;
    }

    public void setWarpMenuGUI(WarpMenuGUI warpMenuGUI) {
        this.warpMenuGUI = warpMenuGUI;
    }

    public void open(Player player, String charId, int stageNum) {
        NpcCharacter c = registry.getCharacter(charId);
        if (c == null) return;

        NpcStage stage = c.getStage(stageNum);
        if (stage == null) {
            player.sendMessage(DragoncoteColor.PREFIX_ERROR + "Stage #" + stageNum + " does not exist for " + c.getShortName());
            return;
        }

        activeSessions.put(player.getUniqueId(), new StageEditSession(charId.toLowerCase(), stageNum));
        Inventory inv = Bukkit.createInventory(null, 54, "§8[Edit Ch." + stageNum + "] §d" + c.getShortName());

        // Background filler
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pMeta = pane.getItemMeta();
        if (pMeta != null) {
            pMeta.setDisplayName("§7");
            pane.setItemMeta(pMeta);
        }
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, pane);
        }

        // Slot 4: Stage Overview Header
        ItemStack head = new ItemStack(c.getIcon() != null ? c.getIcon() : Material.PLAYER_HEAD);
        ItemMeta hMeta = head.getItemMeta();
        if (hMeta != null) {
            hMeta.setDisplayName("§e§l" + c.getShortName() + " §7- Chapter " + stage.getStageNumber() + ": §f" + stage.getTitle());
            List<String> lore = new ArrayList<>();
            lore.add("§8Internal ID: " + c.getId() + ":" + stage.getStageNumber());
            lore.add("§7Prerequisite: §e" + stage.getRequires());
            lore.add("§7Objective: §f" + stage.getObjective());
            lore.add("§7Next Contact: §b" + (stage.getNextCharacterId() != null ? stage.getNextCharacterId() : "Arc Finale"));
            lore.add("§f");
            lore.add("§d§lDialogue Line:");
            lore.add("  §7\"§o" + stage.getDialogueLine() + "§7\"");
            lore.add("§a§lCompletion Line:");
            lore.add("  §7\"§o" + stage.getCompletionLine() + "§7\"");
            hMeta.setLore(lore);
            head.setItemMeta(hMeta);
        }
        inv.setItem(4, head);

        // Slot 11: DRAG & DROP Custom Reward Item Slot
        ItemStack rewardItem = stage.getCustomRewardItem();
        if (rewardItem != null && !rewardItem.getType().isAir()) {
            ItemStack displayReward = rewardItem.clone();
            ItemMeta rMeta = displayReward.getItemMeta();
            if (rMeta != null) {
                List<String> rLore = rMeta.hasLore() ? new ArrayList<>(rMeta.getLore()) : new ArrayList<>();
                rLore.add("§f");
                rLore.add("§a✔ Active Custom Item Reward (Preserves Full NBT & CMD)");
                rLore.add("§e▶ Click with new item to replace");
                rLore.add("§c▶ Right-Click with empty cursor to clear");
                rMeta.setLore(rLore);
                displayReward.setItemMeta(rMeta);
            }
            inv.setItem(11, displayReward);
        } else {
            ItemStack emptyItem = new ItemStack(Material.ITEM_FRAME);
            ItemMeta eMeta = emptyItem.getItemMeta();
            if (eMeta != null) {
                eMeta.setDisplayName("§e§lDrag & Drop Custom Item Reward");
                List<String> eLore = new ArrayList<>();
                eLore.add("§7Currently: §cNo custom ItemStack assigned");
                eLore.add("§7(Using fallback item name: §f" + (stage.getRewardItemName() != null ? stage.getRewardItemName() : "None") + "§7)");
                eLore.add("§f");
                eLore.add("§a▶ Drop or click ANY custom item from your");
                eLore.add("§a  inventory here to assign it as stage reward!");
                eMeta.setLore(eLore);
                emptyItem.setItemMeta(eMeta);
            }
            inv.setItem(11, emptyItem);
        }

        // Slot 13: Cash Reward Editor
        ItemStack cashItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta cMeta = cashItem.getItemMeta();
        if (cMeta != null) {
            cMeta.setDisplayName("§6§lCash Allocation: §a$" + String.format("%.2f", stage.getCashReward()));
            List<String> cLore = new ArrayList<>();
            cLore.add("§7Claimed automatically by players on chapter completion.");
            cLore.add("§f");
            cLore.add("§e▶ Left-Click: §a+$500");
            cLore.add("§e▶ Right-Click: §c-$500");
            cLore.add("§e▶ Shift + Left-Click: §a+$2,500");
            cLore.add("§e▶ Shift + Right-Click: §c-$2,500");
            cMeta.setLore(cLore);
            cashItem.setItemMeta(cMeta);
        }
        inv.setItem(13, cashItem);

        // Slot 15: Execution Command
        ItemStack cmdItem = new ItemStack(Material.COMMAND_BLOCK);
        ItemMeta cmMeta = cmdItem.getItemMeta();
        if (cmMeta != null) {
            cmMeta.setDisplayName("§d§lExecution Command");
            List<String> cmLore = new ArrayList<>();
            cmLore.add("§7Command executed by Console on stage completion:");
            cmLore.add("§e/" + (stage.getExecutionCommand() != null ? stage.getExecutionCommand() : "none"));
            cmLore.add("§f");
            cmLore.add("§7Use placeholder: §f%player%");
            cmLore.add("§a▶ Set via: §e/dcnpc setcmd " + c.getId() + " " + stageNum + " <cmd>");
            cmMeta.setLore(cmLore);
            cmdItem.setItemMeta(cmMeta);
        }
        inv.setItem(15, cmdItem);

        // Slot 20: Completion Behavior (STAY, POOF, WALK)
        ItemStack behItem = new ItemStack(Material.ENDER_PEARL);
        ItemMeta bMeta = behItem.getItemMeta();
        if (bMeta != null) {
            String behavior = stage.getCompletionBehavior();
            bMeta.setDisplayName("§5§lStage Completion Behavior: §e" + behavior);
            List<String> bLore = new ArrayList<>();
            bLore.add("§7What happens to the NPC once dialogue finishes:");
            bLore.add("  §7• §fSTAY: §7NPC remains stationary at location.");
            bLore.add("  §7• §fPOOF: §7Cinematic smoke & dragon breath burst + whoosh sound; despawns until next cycle.");
            bLore.add("  §7• §fWALK: §7Paths to the next designated stage coordinates.");
            bLore.add("§f");
            bLore.add("§e▶ Click to cycle behavior [STAY ➜ POOF ➜ WALK]");
            bMeta.setLore(bLore);
            behItem.setItemMeta(bMeta);
        }
        inv.setItem(20, behItem);

        // Slot 22: Stage Coordinates & Relocation
        ItemStack locItem = new ItemStack(Material.COMPASS);
        ItemMeta lMeta = locItem.getItemMeta();
        if (lMeta != null) {
            Location loc = stage.getStageLocation();
            lMeta.setDisplayName("§b§lStage Coordinates & Position");
            List<String> lLore = new ArrayList<>();
            if (loc != null && loc.getWorld() != null) {
                lLore.add("§7Location: §f" + loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
                lLore.add("§7Facing: §e" + String.format("%.1f°", loc.getYaw()));
            } else {
                lLore.add("§7Location: §cInherits NPC default base anchor");
            }
            lLore.add("§f");
            lLore.add("§e▶ Click to set stage coordinates to YOUR current position!");
            lMeta.setLore(lLore);
            locItem.setItemMeta(lMeta);
        }
        inv.setItem(22, locItem);

        // Slot 24: Bound Fast Travel Warp Zone
        ItemStack warpItem = new ItemStack(Material.ENDER_EYE);
        ItemMeta wMeta = warpItem.getItemMeta();
        if (wMeta != null) {
            String boundWarp = stage.getBoundWarpAlias() != null ? stage.getBoundWarpAlias() : c.getBoundWarpAlias();
            warpItem.setItemMeta(wMeta);
            wMeta.setDisplayName("§3§lBound District Warp: §b" + (boundWarp != null ? boundWarp.toUpperCase() : "GENERAL / NONE"));
            List<String> wLore = new ArrayList<>();
            wLore.add("§7When this chapter is far away (>50 blocks), the navigation");
            wLore.add("§7system offers a one-click fast travel warp to this district.");
            wLore.add("§f");
            wLore.add("§e▶ Click to open Warp Selector Menu and bind a district!");
            wMeta.setLore(wLore);
            warpItem.setItemMeta(wMeta);
        }
        inv.setItem(24, warpItem);

        // Slot 40: Save to Disk
        ItemStack saveBtn = new ItemStack(Material.NETHER_STAR);
        ItemMeta sMeta = saveBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.setDisplayName("§6§lSave Changes to Disk");
            List<String> sLore = new ArrayList<>();
            sLore.add("§7Permanently writes all stage settings, custom items,");
            sLore.add("§7and coordinates to §fcustom_npc_stages.yml§7.");
            sMeta.setLore(sLore);
            saveBtn.setItemMeta(sMeta);
        }
        inv.setItem(40, saveBtn);

        // Slot 45: Back Button
        ItemStack backBtn = new ItemStack(Material.ARROW);
        ItemMeta bkMeta = backBtn.getItemMeta();
        if (bkMeta != null) {
            bkMeta.setDisplayName("§c§l◀ Back to Chapter List");
            backBtn.setItemMeta(bkMeta);
        }
        inv.setItem(45, backBtn);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.2f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        StageEditSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        if (!event.getView().getTitle().startsWith("§8[Edit Ch.")) return;

        int rawSlot = event.getRawSlot();

        NpcCharacter c = registry.getCharacter(session.charId);
        if (c == null) return;
        NpcStage stage = c.getStage(session.stageNum);
        if (stage == null) return;

        // Slot 11: Drag & Drop Custom Item Slot
        if (rawSlot == 11) {
            event.setCancelled(true);
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                // Drop / Place item as custom reward
                ItemStack toSave = cursor.clone();
                stage.setCustomRewardItem(toSave);
                stage.setRewardItemName(toSave.hasItemMeta() && toSave.getItemMeta().hasDisplayName() ?
                        toSave.getItemMeta().getDisplayName() : toSave.getType().name());
                player.sendMessage(DragoncoteColor.PREFIX_SUCCESS + "Assigned custom reward item: §f" + stage.getRewardItemName() + " §7(Full NBT preserved)!");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
                open(player, session.charId, session.stageNum);
                return;
            } else if (event.getClick() == ClickType.RIGHT) {
                // Clear custom reward
                stage.setCustomRewardItem(null);
                player.sendMessage(DragoncoteColor.PREFIX_INFO + "Cleared custom reward ItemStack for Chapter " + session.stageNum);
                player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.9f, 1.0f);
                registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
                open(player, session.charId, session.stageNum);
                return;
            }
            return;
        }

        // Cancel all clicks in upper inventory
        if (rawSlot < 54) {
            event.setCancelled(true);
        } else {
            // Player inventory click: allow them to take items onto cursor to drop in slot 11
            return;
        }

        switch (rawSlot) {
            case 13: // Cash reward
                double current = stage.getCashReward();
                double delta = (event.getClick() == ClickType.SHIFT_LEFT) ? 2500.0 :
                        (event.getClick() == ClickType.SHIFT_RIGHT) ? -2500.0 :
                                (event.getClick() == ClickType.LEFT) ? 500.0 : -500.0;
                stage.setCashReward(Math.max(0.0, current + delta));
                registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 1.2f);
                open(player, session.charId, session.stageNum);
                break;

            case 20: // Behavior
                String curBeh = stage.getCompletionBehavior();
                String nextBeh = curBeh.equals("STAY") ? "POOF" : curBeh.equals("POOF") ? "WALK" : "STAY";
                stage.setCompletionBehavior(nextBeh);
                registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
                DCMessage.sendGeneral(player, "Completion behavior set to: " + nextBeh);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                open(player, session.charId, session.stageNum);
                break;

            case 22: // Set Location to Player Location
                Location pLoc = player.getLocation();
                stage.setStageLocation(pLoc.clone());
                DCMessage.sendGeneral(player, "Stage coordinates set to: " +
                        pLoc.getWorld().getName() + " (" + pLoc.getBlockX() + ", " + pLoc.getBlockY() + ", " + pLoc.getBlockZ() + " yaw: " + String.format("%.1f", pLoc.getYaw()) + "°)");
                player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0f, 1.5f);
                registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
                open(player, session.charId, session.stageNum);
                break;

            case 24: // Bind Warp Zone
                if (warpMenuGUI != null) {
                    warpMenuGUI.openForBinding(player, session.charId, session.stageNum);
                } else {
                    player.sendMessage(DragoncoteColor.PREFIX_ERROR + "Warp selector is not initialized.");
                }
                break;

            case 40: // Save to Disk
                registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
                player.sendMessage(DragoncoteColor.PREFIX_SUCCESS + "Saved Chapter " + session.stageNum + " settings to §fcustom_npc_stages.yml§a!");
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
                break;

            case 45: // Back to Chapters List
                if (stageListGUI != null) {
                    stageListGUI.open(player, session.charId);
                }
                break;
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        activeSessions.remove(event.getPlayer().getUniqueId());
    }
}
