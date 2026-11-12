package dc.dcessentials.modules.npc.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.npc.NpcCanonManager;
import dc.dcessentials.modules.npc.NpcRegistry;
import dc.dcessentials.modules.npc.NpcZoneManager;
import dc.dcessentials.modules.npc.model.NpcCharacter;
import dc.dcessentials.modules.npc.model.NpcStage;
import dc.dcessentials.modules.npc.model.NpcZone;
import dc.dcessentials.modules.npc.util.DragoncoteColor;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
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
 * 54-Slot Visual Management & Dossier Editor GUI for Citizens Questline NPCs.
 * Opened via Editor Wand (/dcnpc wand edit) or /dcnpc editor.
 */
public class NpcEditorGUI implements Listener {

    private final NpcRegistry registry;
    private final NpcZoneManager zoneManager;
    private final NpcCanonManager canonManager;

    private NpcStageEditorGUI stageEditorGUI;
    private NpcTrackerGUI trackerGUI;
    private NpcEngineSelectorGUI engineSelectorGUI;
    private WarpMenuGUI warpMenuGUI;

    private final Map<UUID, String> viewingCharId = new HashMap<>();

    public NpcEditorGUI(NpcRegistry registry, NpcZoneManager zoneManager, NpcCanonManager canonManager) {
        this.registry = registry;
        this.zoneManager = zoneManager;
        this.canonManager = canonManager;
    }

    public void setStageEditorGUI(NpcStageEditorGUI stageEditorGUI) {
        this.stageEditorGUI = stageEditorGUI;
    }

    public void setTrackerGUI(NpcTrackerGUI trackerGUI) {
        this.trackerGUI = trackerGUI;
    }

    public void setEngineSelectorGUI(NpcEngineSelectorGUI engineSelectorGUI) {
        this.engineSelectorGUI = engineSelectorGUI;
    }

    public void setWarpMenuGUI(WarpMenuGUI warpMenuGUI) {
        this.warpMenuGUI = warpMenuGUI;
    }

    public void open(Player player, String charId) {
        if (charId != null && charId.contains(":")) {
            charId = charId.split(":")[0];
        }
        NpcCharacter c = registry.getCharacter(charId);
        if (c == null) {
            player.sendMessage(DragoncoteColor.PREFIX_ERROR + "Character '" + charId + "' not found in registry.");
            return;
        }

        viewingCharId.put(player.getUniqueId(), c.getId().toLowerCase());
        Inventory inv = Bukkit.createInventory(null, 54, "§8[NPC Editor] §d" + c.getShortName() + " §7(" + c.getRole() + ")");

        // Filler
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pMeta = pane.getItemMeta();
        if (pMeta != null) {
            pMeta.setDisplayName("§7");
            pane.setItemMeta(pMeta);
        }
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, pane);
        }

        // Slot 4: Character Header
        ItemStack head = new ItemStack(c.getIcon() != null ? c.getIcon() : Material.PLAYER_HEAD);
        ItemMeta hMeta = head.getItemMeta();
        if (hMeta != null) {
            hMeta.setDisplayName(DragoncoteColor.colorize(c.getHexColor() + "&l" + c.getShortName() + " &7[" + c.getRole() + "]"));
            List<String> lore = new ArrayList<>();
            lore.add("§8Unique ID: " + c.getId());
            lore.add("§7Full Name: §f" + c.getFullName());
            lore.add("§7Faction: " + registry.getFactionHex(c.getFaction()) + c.getFaction().toUpperCase());
            lore.add("§7Sector: §e" + c.getSector());
            lore.add("§7District Zone: §b" + (c.getZoneId() != null ? c.getZoneId().toUpperCase() : "GENERAL"));
            lore.add("§f");
            lore.add("§7Tag Preview: " + registry.getOverheadTag(c.getId(), player.getWorld().getName()));
            lore.add("§7Signature Quote: §o\"" + c.getQuote() + "\"");
            hMeta.setLore(lore);
            head.setItemMeta(hMeta);
        }
        inv.setItem(4, head);

        // Slot 11: NPC Rendering Engine (Citizens vs ModelEngine)
        boolean isMeg = c.getEngineType().equalsIgnoreCase("MODEL_ENGINE");
        ItemStack engineItem = new ItemStack(isMeg ? Material.ARMOR_STAND : Material.PLAYER_HEAD);
        ItemMeta engMeta = engineItem.getItemMeta();
        if (engMeta != null) {
            engMeta.setDisplayName("§5§lNPC Rendering Engine");
            List<String> lore = new ArrayList<>();
            lore.add("§7Active Engine: " + (isMeg ? "§6§lModelEngine 3D Rig" : "§a§lCitizens Humanoid"));
            lore.add("§7Model ID: §e" + c.getModelId());
            lore.add("§f");
            lore.add("§7• Citizens: Standard player NPC, full skin per world support.");
            lore.add("§7• ModelEngine: 3D animated scene rig (Cookieseller, Blacksmith, etc).");
            lore.add("§f");
            lore.add("§e▶ Click to switch engine or choose animated 3D rig!");
            engMeta.setLore(lore);
            engineItem.setItemMeta(engMeta);
        }
        inv.setItem(11, engineItem);

        // Slot 19: Stages & Chapters Visual Editor
        ItemStack stagesItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta sMeta = stagesItem.getItemMeta();
        if (sMeta != null) {
            sMeta.setDisplayName("§e§lQuest Stages & Chapters Editor");
            List<String> lore = new ArrayList<>();
            lore.add("§7Total Configured Chapters: §e" + c.getStages().size());
            if (c.getStages().isEmpty()) {
                lore.add("  §8(No active stages configured)");
            } else {
                for (Map.Entry<Integer, NpcStage> entry : c.getStages().entrySet()) {
                    NpcStage st = entry.getValue();
                    lore.add("  §eCh." + entry.getKey() + ": §f" + st.getTitle() + " §a($" + String.format("%.0f", st.getCashReward()) + ")");
                }
            }
            lore.add("§f");
            lore.add("§e▶ Left-Click: §fOpen Full Chapter Stages & Rewards Editor");
            lore.add("§6▶ Right-Click: §fPrint Dialogue Transcript to Chat");
            sMeta.setLore(lore);
            stagesItem.setItemMeta(sMeta);
        }
        inv.setItem(19, stagesItem);

        // Slot 21: Anchor Coordinates & Recalibration
        ItemStack anchorItem = new ItemStack(Material.COMPASS);
        ItemMeta aMeta = anchorItem.getItemMeta();
        if (aMeta != null) {
            aMeta.setDisplayName("§b§lDimensional Anchor & Coordinates");
            Location liveLoc = canonManager.getLiveLocation(c.getId());
            List<String> lore = new ArrayList<>();
            if (liveLoc != null && liveLoc.getWorld() != null) {
                lore.add("§7Saved Anchor: §f" + liveLoc.getWorld().getName() +
                        " (" + liveLoc.getBlockX() + ", " + liveLoc.getBlockY() + ", " + liveLoc.getBlockZ() + ")");
                lore.add("§7Facing Yaw: §e" + String.format("%.1f°", liveLoc.getYaw()));
            } else {
                lore.add("§cAnchor not yet calibrated via Setup Wand.");
            }
            lore.add("§f");
            if (c.getSector() == 1) {
                lore.add("§7Replication: §a1:1 Active across all 3 Spawn dimensions!");
            } else {
                lore.add("§7Deployment: §eSingle instance in LowerCity!");
            }
            lore.add("§f");
            lore.add("§e▶ Click to Recalibrate Anchor to your current position!");
            aMeta.setLore(lore);
            anchorItem.setItemMeta(aMeta);
        }
        inv.setItem(21, anchorItem);

        // Slot 23: Dimensional Skins & Texture Directories
        ItemStack skinItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta skMeta = skinItem.getItemMeta();
        if (skMeta != null) {
            skMeta.setDisplayName("§d§lDimensional Skins & Textures");
            List<String> lore = new ArrayList<>();
            lore.add("§7• Prime (spawn_main): §f" + c.getSkin());
            lore.add("§7• Sub-Zero (spawn_snow): §b" + c.getSkinSnow());
            lore.add("§7• Void/Glitch (spawn_bw): §8" + c.getSkinBw() + " (Monochromatic)");

            java.io.File skinDir = DCCosmetics.getInstance().getSkinProcessor().getNpcDirectory(c.getId());
            java.io.File localFile = new java.io.File(skinDir, "default.png");
            if (localFile.exists()) {
                lore.add("§7• Local File: §a✔ default.png present in /npcs/" + c.getId() + "/");
            } else {
                lore.add("§7• Local File: §7✖ No default.png in /npcs/" + c.getId() + "/");
            }

            lore.add("§f");
            lore.add("§a▶ Left-Click: §fRefresh skin for active NPC in this world");
            lore.add("§6▶ Right-Click: §fSet skin from URL, Player, or Local Folder");
            skMeta.setLore(lore);
            skinItem.setItemMeta(skMeta);
        }
        inv.setItem(23, skinItem);

        // Slot 25: Rotation & Facing
        ItemStack rotItem = new ItemStack(Material.CLOCK);
        ItemMeta rMeta = rotItem.getItemMeta();
        if (rMeta != null) {
            rMeta.setDisplayName("§a§lFacing & Rotation Control");
            List<String> lore = new ArrayList<>();
            NPC inWorld = zoneManager.findCitizensNpc(c.getId(), player.getWorld().getName());
            if (inWorld != null && inWorld.isSpawned() && inWorld.getEntity() != null) {
                lore.add("§7Current Yaw: §e" + String.format("%.1f°", inWorld.getEntity().getLocation().getYaw()));
            } else {
                lore.add("§cNPC not currently spawned in this world.");
            }
            lore.add("§f");
            lore.add("§e▶ Left-Click: §fRotate +15°");
            lore.add("§e▶ Right-Click: §fRotate -15°");
            lore.add("§e▶ Shift + Click: §fSnap to face you directly");
            rMeta.setLore(lore);
            rotItem.setItemMeta(rMeta);
        }
        inv.setItem(25, rotItem);

        // Slot 29: Completion Behavior
        ItemStack behaviorItem = new ItemStack(Material.ENDER_PEARL);
        ItemMeta bMeta = behaviorItem.getItemMeta();
        if (bMeta != null) {
            bMeta.setDisplayName("§6§lQuest Completion Behavior");
            List<String> lore = new ArrayList<>();
            lore.add("§7Current Setting: §e§l" + c.getCompletionBehavior());
            lore.add("§f");
            lore.add("§7• §fSTAY_IN_WORLD: §7Remains anchored in world.");
            lore.add("§7• §fWALK_PATROL_PATH: §7Walks navigation route.");
            lore.add("§7• §fPOOF_DESPAWN: §7Fades in portal particles.");
            lore.add("§f");
            lore.add("§e▶ Click to cycle completion behavior");
            bMeta.setLore(lore);
            behaviorItem.setItemMeta(bMeta);
        }
        inv.setItem(29, behaviorItem);

        // Slot 31: Rewards & Economics
        ItemStack rewardsItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta rewMeta = rewardsItem.getItemMeta();
        if (rewMeta != null) {
            rewMeta.setDisplayName("§6§lStage Rewards & Economics");
            List<String> lore = new ArrayList<>();
            NpcStage stage1 = c.getStage(1);
            if (stage1 != null) {
                lore.add("§7Ch.1 Cash Reward: §a$" + String.format("%.2f", stage1.getCashReward()));
                lore.add("§7Ch.1 Mastery XP: §b+15 XP");
                lore.add("§7Ch.1 Reward Item: §f" + stage1.getRewardItemName());
                lore.add("§7Ch.1 Command: §e/" + stage1.getExecutionCommand());
            } else {
                lore.add("§7Standard Baseline: §a$500.00 | +15 Mastery XP");
            }
            lore.add("§f");
            lore.add("§e▶ Left-Click: §fOpen Full Stages & Rewards Editor");
            lore.add("§6▶ Right-Click: §fCommand Help to set custom rewards");
            rewMeta.setLore(lore);
            rewardsItem.setItemMeta(rewMeta);
        }
        inv.setItem(31, rewardsItem);

        // Slot 33: Bound District Warp
        ItemStack warpItem = new ItemStack(Material.ENDER_EYE);
        ItemMeta wMeta = warpItem.getItemMeta();
        if (wMeta != null) {
            String zoneId = c.getZoneId() != null ? c.getZoneId() : "command";
            NpcZone zone = zoneManager.getZone(zoneId);
            wMeta.setDisplayName("§3§lBound District Warp");
            List<String> lore = new ArrayList<>();
            lore.add("§7Assigned District: §b" + (zone != null ? zone.getDisplayName() : zoneId));
            lore.add("§7Warp Shortcut: §e/warp " + (zone != null ? zone.getId() : zoneId));
            if (c.getServiceCommand() != null) {
                lore.add("§7Service Trigger: §f/" + c.getServiceCommand());
            }
            lore.add("§f");
            lore.add("§e▶ Left-Click: §fSelect / Bind Warp in Menu");
            lore.add("§a▶ Right-Click: §fFast Travel / Test Warp to Anchor");
            wMeta.setLore(lore);
            warpItem.setItemMeta(wMeta);
        }
        inv.setItem(33, warpItem);

        // Slot 45: Back to District Sub-Menu
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c§l◀ Back to District Menu");
            back.setItemMeta(backMeta);
        }
        inv.setItem(45, back);

        // Slot 49: Close
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = close.getItemMeta();
        if (cMeta != null) {
            cMeta.setDisplayName("§c§lClose Editor");
            close.setItemMeta(cMeta);
        }
        inv.setItem(49, close);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        if (!title.startsWith("§8[NPC Editor]")) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        String charId = viewingCharId.get(player.getUniqueId());
        if (charId == null) return;
        NpcCharacter c = registry.getCharacter(charId);
        if (c == null) return;

        // Slot 11: Open NPC Engine Selector
        if (slot == 11) {
            if (engineSelectorGUI != null) {
                engineSelectorGUI.open(player, charId);
            } else {
                player.sendMessage(DragoncoteColor.PREFIX_ERROR + "Engine selector is not initialized.");
            }
            return;
        }

        // Slot 19: Open Chapter Stages Editor OR Print Dialogue
        if (slot == 19) {
            if (event.isRightClick()) {
                player.closeInventory();
                player.sendMessage("§8==================================================");
                player.sendMessage(DragoncoteColor.PREFIX_INFO + "DIALOGUE DOSSIER: " + c.getFullName());
                for (Map.Entry<Integer, NpcStage> e : c.getStages().entrySet()) {
                    player.sendMessage("§e[Chapter " + e.getKey() + "] §f" + e.getValue().getTitle());
                    player.sendMessage("§7Dialogue: §f\"" + e.getValue().getDialogueLine() + "\"");
                    player.sendMessage("§7Completion: §a\"" + e.getValue().getCompletionLine() + "\"");
                }
                player.sendMessage("§6Ambient Quotes:");
                for (String amb : c.getAmbientLines()) {
                    player.sendMessage("§7- §o\"" + amb + "\"");
                }
                player.sendMessage("§8==================================================");
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
            } else {
                if (stageEditorGUI != null) {
                    stageEditorGUI.open(player, charId);
                }
            }
            return;
        }

        // Slot 21: Recalibrate Anchor to player's current location
        if (slot == 21) {
            Location newLoc = player.getLocation();
            canonManager.setLiveLocation(c.getId(), newLoc);

            // Re-anchor active NPC instances
            if (c.getSector() == 1) {
                for (String wName : canonManager.getActiveSpawnWorlds()) {
                    NPC npc = zoneManager.findCitizensNpc(c.getId(), wName);
                    if (npc != null && npc.isSpawned()) {
                        Location dest = new Location(Bukkit.getWorld(wName), newLoc.getX(), newLoc.getY(), newLoc.getZ(), newLoc.getYaw(), 0.0f);
                        npc.teleport(dest, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                        canonManager.configureNpcOverhead(npc, c, wName);
                    }
                }
            } else {
                NPC npc = zoneManager.findCitizensNpc(c.getId(), newLoc.getWorld().getName());
                if (npc != null && npc.isSpawned()) {
                    npc.teleport(newLoc, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                    canonManager.configureNpcOverhead(npc, c, newLoc.getWorld().getName());
                }
            }

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens save");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            player.sendMessage(DragoncoteColor.PREFIX_GENERAL + "Recalibrated live anchor for §f" + c.getShortName() + " §7to your coordinates!");
            open(player, charId);
            return;
        }

        // Slot 23: Skin Configuration & Reload
        if (slot == 23) {
            if (event.isRightClick()) {
                player.closeInventory();
                player.sendMessage("§8==================================================");
                player.sendMessage(DragoncoteColor.PREFIX_INFO + "SKIN CONFIGURATION FOR: §f" + c.getShortName());
                player.sendMessage("§e• Set from URL: §f/dcnpc skin " + charId + " <url>");
                player.sendMessage("§e• Set from Player Name: §f/dcnpc skin " + charId + " <player>");
                player.sendMessage("§e• Apply Local File: §f/dcnpc skin " + charId + " file");
                player.sendMessage("§7Folder: §eplugins/DCEssentials/npcs/" + charId + "/default.png");
                player.sendMessage("§8==================================================");
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
            } else {
                NPC npc = zoneManager.findCitizensNpc(c.getId(), player.getWorld().getName());
                if (npc != null && npc.isSpawned()) {
                    DCCosmetics.getInstance().getSkinProcessor().applyWorldSkin(npc, player.getWorld().getName(), c.getId());
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.4f);
                    player.sendMessage(DragoncoteColor.PREFIX_INFO + "Refreshed skin for §f" + c.getShortName() + "§7 in §b" + player.getWorld().getName());
                }
            }
            return;
        }

        // Slot 25: Rotation Control
        if (slot == 25) {
            NPC npc = zoneManager.findCitizensNpc(c.getId(), player.getWorld().getName());
            if (npc != null && npc.isSpawned() && npc.getEntity() != null) {
                Location loc = npc.getEntity().getLocation();
                float newYaw;
                if (event.isShiftClick()) {
                    // Face player
                    Location pLoc = player.getLocation();
                    double dx = pLoc.getX() - loc.getX();
                    double dz = pLoc.getZ() - loc.getZ();
                    newYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                } else if (event.isRightClick()) {
                    newYaw = loc.getYaw() - 15.0f;
                } else {
                    newYaw = loc.getYaw() + 15.0f;
                }
                loc.setYaw(newYaw);
                npc.teleport(loc, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                canonManager.setLiveLocation(c.getId(), loc);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens save");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.4f);
                open(player, charId);
            }
            return;
        }

        // Slot 29: Toggle Completion Behavior
        if (slot == 29) {
            String current = c.getCompletionBehavior();
            String next;
            if ("STAY_IN_WORLD".equalsIgnoreCase(current)) next = "WALK_PATROL_PATH";
            else if ("WALK_PATROL_PATH".equalsIgnoreCase(current)) next = "POOF_DESPAWN";
            else next = "STAY_IN_WORLD";
            c.setCompletionBehavior(next);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
            player.sendMessage(DragoncoteColor.PREFIX_INFO + "Updated completion behavior for §f" + c.getShortName() + " §7to: §e" + next);
            open(player, charId);
            return;
        }

        // Slot 31: Rewards Editor
        if (slot == 31) {
            if (event.isRightClick()) {
                player.closeInventory();
                DCMessage.sendQuest(player, "Set cash reward: /dcnpc setreward " + charId + " 1 <amount>");
                DCMessage.sendQuest(player, "Set command: /dcnpc setcmd " + charId + " 1 <command>");
                DCMessage.sendQuest(player, "Set item name: /dcnpc setitem " + charId + " 1 <item_name>");
            } else {
                if (stageEditorGUI != null) {
                    stageEditorGUI.open(player, charId);
                }
            }
            return;
        }

        // Slot 33: Bind Warp Zone (Left) or Test Warp (Right)
        if (slot == 33) {
            if (event.isRightClick()) {
                player.closeInventory();
                String zoneId = c.getBoundWarpAlias() != null ? c.getBoundWarpAlias() : (c.getZoneId() != null ? c.getZoneId() : "command");
                zoneManager.warpPlayer(player, zoneId);
            } else {
                if (warpMenuGUI != null) {
                    warpMenuGUI.openForBinding(player, charId, null);
                } else {
                    player.sendMessage(DragoncoteColor.PREFIX_ERROR + "Warp menu is not initialized.");
                }
            }
            return;
        }

        // Slot 45: Back to District Sub-Menu
        if (slot == 45) {
            if (trackerGUI != null) {
                trackerGUI.openDistrictSubMenu(player, c.getZoneId() != null ? c.getZoneId() : "command");
            } else {
                player.closeInventory();
            }
            return;
        }

        // Slot 49: Close
        if (slot == 49) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.0f);
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        viewingCharId.remove(event.getPlayer().getUniqueId());
    }
}
