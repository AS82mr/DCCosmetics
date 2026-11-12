package dc.dcessentials.modules.npc.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.npc.NpcRegistry;
import dc.dcessentials.modules.npc.model.NpcCharacter;
import dc.dcessentials.modules.npc.model.NpcStage;
import dc.dcessentials.modules.npc.util.DragoncoteColor;
import org.bukkit.Bukkit;
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
 * 54-Slot Visual Quest Stage & Chapter Editor for Dragoncote NPCs.
 * Allows viewing all storyline chapters, configuring cash/XP rewards,
 * setting completion commands, testing dialogues, and adding new stages.
 */
public class NpcStageEditorGUI implements Listener {

    private final NpcRegistry registry;
    private NpcEditorGUI npcEditorGUI;
    private NpcSingleStageGUI singleStageGUI;

    private final Map<UUID, String> viewingChar = new HashMap<>();

    public NpcStageEditorGUI(NpcRegistry registry) {
        this.registry = registry;
    }

    public void setNpcEditorGUI(NpcEditorGUI npcEditorGUI) {
        this.npcEditorGUI = npcEditorGUI;
    }

    public void setSingleStageGUI(NpcSingleStageGUI singleStageGUI) {
        this.singleStageGUI = singleStageGUI;
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

        viewingChar.put(player.getUniqueId(), c.getId().toLowerCase());
        Inventory inv = Bukkit.createInventory(null, 54, "§8[Stages] §d" + c.getShortName() + " §7(Chapters)");

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

        // Slot 4: Character Header
        ItemStack head = new ItemStack(c.getIcon() != null ? c.getIcon() : Material.PLAYER_HEAD);
        ItemMeta hMeta = head.getItemMeta();
        if (hMeta != null) {
            hMeta.setDisplayName(DragoncoteColor.colorize(c.getHexColor() + "&l" + c.getShortName() + " &7- Storyline Stages"));
            List<String> lore = new ArrayList<>();
            lore.add("§8Unique ID: " + c.getId());
            lore.add("§7Total Chapters: §e" + c.getStages().size());
            lore.add("§7Faction: " + registry.getFactionHex(c.getFaction()) + c.getFaction().toUpperCase());
            lore.add("§7District: §b" + (c.getZoneId() != null ? c.getZoneId().toUpperCase() : "GENERAL"));
            lore.add("§f");
            lore.add("§a◀ Click to return to Character Dossier Editor");
            hMeta.setLore(lore);
            head.setItemMeta(hMeta);
        }
        inv.setItem(4, head);

        // Stage Slots mapping (up to 21 visible stages in center rows)
        int[] stageSlots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };

        List<Integer> stageKeys = new ArrayList<>(c.getStages().keySet());
        Collections.sort(stageKeys);

        int slotIdx = 0;
        for (int stageNum : stageKeys) {
            if (slotIdx >= stageSlots.length) break;

            NpcStage stage = c.getStage(stageNum);
            if (stage == null) continue;

            ItemStack book = new ItemStack(stage.getIcon() != null ? stage.getIcon() : Material.WRITABLE_BOOK);
            ItemMeta bMeta = book.getItemMeta();
            if (bMeta != null) {
                bMeta.setDisplayName("§e§lChapter " + stage.getStageNumber() + ": §f" + stage.getTitle());
                List<String> lore = new ArrayList<>();
                lore.add("§8Internal ID: " + c.getId() + ":" + stage.getStageNumber());
                lore.add("§7Prerequisite: §e" + stage.getRequires());
                lore.add("§7Objective: §f" + stage.getObjective());
                lore.add("§7Next Target: §b" + (stage.getNextCharacterId() != null ? stage.getNextCharacterId() : "Arc Finale"));
                lore.add("§f");
                lore.add("§6§lRewards & Economics:");
                lore.add("  §7Cash: §a$" + String.format("%.2f", stage.getCashReward()));
                lore.add("  §7Mastery XP: §b+15 XP");
                lore.add("  §7Reward Item: §f" + (stage.getRewardItemName() != null ? stage.getRewardItemName() : "None"));
                lore.add("  §7Command: §e/" + (stage.getExecutionCommand() != null ? stage.getExecutionCommand() : "none"));
                lore.add("§f");
                lore.add("§d§lDialogue Preview:");
                lore.add("  §7\"§o" + truncate(stage.getDialogueLine(), 48) + "§7\"");
                lore.add("§a§lCompletion Dialogue:");
                lore.add("  §7\"§o" + truncate(stage.getCompletionLine(), 48) + "§7\"");
                lore.add("§f");
                lore.add("§e▶ Left-Click: §fOpen Deep Editor (Item Drop, Pathing, Warp)");
                lore.add("§e▶ Shift + Left-Click: §fTest Play Dialogue in Chat");
                lore.add("§c▶ Shift + Right-Click: §fDelete This Stage");
                bMeta.setLore(lore);
                book.setItemMeta(bMeta);
            }

            inv.setItem(stageSlots[slotIdx++], book);
        }

        // Slot 40: Add Stage Button
        ItemStack addBtn = new ItemStack(Material.EMERALD);
        ItemMeta aMeta = addBtn.getItemMeta();
        if (aMeta != null) {
            aMeta.setDisplayName("§a§l[+] Add New Chapter Stage");
            List<String> aLore = new ArrayList<>();
            aLore.add("§7Click to append the next storyline chapter");
            aLore.add("§7for §f" + c.getShortName() + "§7 with editable defaults.");
            aMeta.setLore(aLore);
            addBtn.setItemMeta(aMeta);
        }
        inv.setItem(40, addBtn);

        // Slot 45: Back Button
        ItemStack backBtn = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backBtn.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c§l◀ Back to Character Editor");
            backBtn.setItemMeta(backMeta);
        }
        inv.setItem(45, backBtn);

        // Slot 49: Command Help
        ItemStack helpBtn = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta hlpMeta = helpBtn.getItemMeta();
        if (hlpMeta != null) {
            hlpMeta.setDisplayName("§e§l[✎] Edit Text & Commands");
            List<String> hLore = new ArrayList<>();
            loreCommandInfo(hLore, c.getId());
            hlpMeta.setLore(hLore);
            helpBtn.setItemMeta(hlpMeta);
        }
        inv.setItem(49, helpBtn);

        // Slot 53: Save Button
        ItemStack saveBtn = new ItemStack(Material.NETHER_STAR);
        ItemMeta sMeta = saveBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.setDisplayName("§6§lSave Stages to Disk");
            List<String> sLore = new ArrayList<>();
            sLore.add("§7Saves all stage configurations and rewards");
            sLore.add("§7permanently to §fcustom_npc_stages.yml§7.");
            sMeta.setLore(sLore);
            saveBtn.setItemMeta(sMeta);
        }
        inv.setItem(53, saveBtn);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.2f);
    }

    private void loreCommandInfo(List<String> lore, String charId) {
        lore.add("§7Commands to modify stage text directly:");
        lore.add("§e/dcnpc setreward " + charId + " <ch> <$>");
        lore.add("§e/dcnpc setcmd " + charId + " <ch> <command>");
        lore.add("§e/dcnpc setitem " + charId + " <ch> <itemName>");
        lore.add("§e/dcnpc settitle " + charId + " <ch> <title>");
        lore.add("§e/dcnpc setobj " + charId + " <ch> <objective>");
        lore.add("§e/dcnpc setdialogue " + charId + " <ch> <line>");
        lore.add("§e/dcnpc setcomp " + charId + " <ch> <line>");
        lore.add("§f");
        lore.add("§a▶ Click to output command templates to chat");
    }

    private String truncate(String text, int max) {
        if (text == null) return "None";
        if (text.length() <= max) return text;
        return text.substring(0, max - 3) + "...";
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        if (!title.startsWith("§8[Stages]")) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        String charId = viewingChar.get(player.getUniqueId());
        if (charId == null) return;
        NpcCharacter c = registry.getCharacter(charId);
        if (c == null) return;

        // Slot 4 or 45: Back to Character Dossier Editor
        if (slot == 4 || slot == 45) {
            if (npcEditorGUI != null) {
                npcEditorGUI.open(player, charId);
            } else {
                player.closeInventory();
            }
            return;
        }

        // Slot 40: Add New Stage
        if (slot == 40) {
            int nextStageNum = c.getStages().keySet().stream().max(Integer::compareTo).orElse(0) + 1;
            String req = (nextStageNum > 1) ? charId + ":" + (nextStageNum - 1) : "none";
            NpcStage newStage = new NpcStage(
                    charId, nextStageNum, "Chapter " + nextStageNum, req,
                    "Complete directive for " + c.getShortName(),
                    "Greetings Agent. We have an operational assignment for you.",
                    "Directive accomplished. Here is your compensation.",
                    "none", 1000.0, "Rebellion Supply Cache",
                    "A standard Syndicate supply cache.", Material.CHEST, "none", false
            );
            c.addStage(newStage);
            registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.6f);
            player.sendMessage(DragoncoteColor.PREFIX_GENERAL + "Created §eChapter #" + nextStageNum + " §7for §f" + c.getShortName() + "§7!");
            open(player, charId);
            return;
        }

        // Slot 49: Output command templates to chat
        if (slot == 49) {
            player.closeInventory();
            player.sendMessage("§8==================================================");
            player.sendMessage(DragoncoteColor.PREFIX_INFO + "STAGE CUSTOMIZATION COMMANDS (" + c.getShortName().toUpperCase() + "):");
            player.sendMessage("§e/dcnpc setreward " + charId + " 1 <amount> §7- Modify cash reward");
            player.sendMessage("§e/dcnpc setcmd " + charId + " 1 <command> §7- Modify reward command");
            player.sendMessage("§e/dcnpc setitem " + charId + " 1 <item_name> §7- Modify reward item title");
            player.sendMessage("§e/dcnpc settitle " + charId + " 1 <title> §7- Modify chapter title");
            player.sendMessage("§e/dcnpc setobj " + charId + " 1 <objective> §7- Modify quest objective");
            player.sendMessage("§e/dcnpc setdialogue " + charId + " 1 <text> §7- Modify intro dialogue");
            player.sendMessage("§e/dcnpc setcomp " + charId + " 1 <text> §7- Modify completion dialogue");
            player.sendMessage("§8==================================================");
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
            return;
        }

        // Slot 53: Explicit Save
        if (slot == 53) {
            registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            player.sendMessage(DragoncoteColor.PREFIX_GENERAL + "Saved all custom stages to §fcustom_npc_stages.yml§7!");
            return;
        }

        // Center Stage Slots Clicked
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        List<String> lore = clicked.getItemMeta().getLore();
        if (lore == null || lore.isEmpty()) return;

        int stageNum = -1;
        for (String line : lore) {
            if (line.startsWith("§8Internal ID: ")) {
                String sub = line.substring("§8Internal ID: ".length()).trim();
                String[] parts = sub.split(":");
                if (parts.length >= 2) {
                    try {
                        stageNum = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {}
                }
                break;
            }
        }

        if (stageNum == -1) return;
        NpcStage stage = c.getStage(stageNum);
        if (stage == null) return;

        // Shift + Right-Click: Delete Stage
        if (event.isShiftClick() && event.isRightClick()) {
            c.removeStage(stageNum);
            registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            player.sendMessage(DragoncoteColor.PREFIX_WARN + "Deleted Chapter #" + stageNum + " for " + c.getShortName() + ".");
            open(player, charId);
            return;
        }

        // Shift + Left-Click: Test Play Dialogue
        if (event.isShiftClick() && event.isLeftClick()) {
            player.closeInventory();
            player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("&8&m----------------------------------------"));
            player.spigot().sendMessage(registry.getChatHeader(charId, player.getWorld().getName()));
            player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &#FFFFFF\"" + stage.getDialogueLine() + "\""));
            player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &#0CBF93Completion: &7\"" + stage.getCompletionLine() + "\""));
            player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("&8&m----------------------------------------"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
            return;
        }

        // Normal Click: Open Deep Single Stage Editor
        if (singleStageGUI != null) {
            singleStageGUI.open(player, charId, stageNum);
        } else {
            player.sendMessage(DragoncoteColor.PREFIX_ERROR + "Single stage editor not initialized.");
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        viewingChar.remove(event.getPlayer().getUniqueId());
    }
}
