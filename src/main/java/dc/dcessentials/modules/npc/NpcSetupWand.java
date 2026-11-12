package dc.dcessentials.modules.npc;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.npc.gui.NpcEditorGUI;
import dc.dcessentials.modules.npc.gui.NpcTrackerGUI;
import dc.dcessentials.modules.npc.gui.SceneEditorGUI;
import dc.dcessentials.modules.npc.model.NpcCharacter;
import dc.dcessentials.modules.npc.model.NpcZone;
import dc.dcessentials.modules.npc.scene.SceneManager;
import dc.dcessentials.modules.npc.util.DragoncoteColor;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

/**
 * Sequential Administrator Setup Wand & In-World Visual Editor Wand.
 * Handles 43-character canon deployment with interactive Scene Workstation sub-placement,
 * multi-world 1:1 replication across Sanctuary cycles, debounced undo, and direct GUI editing.
 */
public class NpcSetupWand implements Listener {

    private final NpcZoneManager zoneManager;
    private final NpcRegistry registry;
    private final NpcTrackerGUI trackerGUI;
    private final NpcCanonManager canonManager;
    private final SceneManager sceneManager;
    private final NpcEditorGUI npcEditorGUI;
    private final SceneEditorGUI sceneEditorGUI;

    private final NamespacedKey wandKey;
    private final NamespacedKey editorWandKey;

    private final File progressFile;
    private int defaultStep = 1;

    // Sequential step tracking per admin (1 to 43)
    private final Map<UUID, Integer> wandStep = new HashMap<>();
    // Companion scene workstation sub-placement: 1..N
    private final Map<UUID, Integer> companionSubStep = new HashMap<>();
    private final Map<UUID, String> companionParentChar = new HashMap<>();
    private final Map<UUID, List<UUID>> placedCompanionEntities = new HashMap<>();
    // Click debounce map to prevent double-hand firing
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private final Map<UUID, Long> lastEntityInteractTime = new HashMap<>();

    public void recordEntityInteract(UUID uuid) {
        lastEntityInteractTime.put(uuid, System.currentTimeMillis());
    }

    public NpcSetupWand(NpcZoneManager zoneManager, NpcRegistry registry, NpcTrackerGUI trackerGUI,
                        NpcCanonManager canonManager, SceneManager sceneManager,
                        NpcEditorGUI npcEditorGUI, SceneEditorGUI sceneEditorGUI) {
        this.zoneManager = zoneManager;
        this.registry = registry;
        this.trackerGUI = trackerGUI;
        this.canonManager = canonManager;
        this.sceneManager = sceneManager;
        this.npcEditorGUI = npcEditorGUI;
        this.sceneEditorGUI = sceneEditorGUI;
        this.wandKey = new NamespacedKey(DCCosmetics.getInstance(), "npc_setup_wand");
        this.editorWandKey = new NamespacedKey(DCCosmetics.getInstance(), "npc_editor_wand");

        File npcDir = new File(DCCosmetics.getInstance().getDataFolder(), "npc");
        if (!npcDir.exists()) npcDir.mkdirs();
        File newProg = new File(npcDir, "wand_progress.yml");
        File oldProg = new File(DCCosmetics.getInstance().getDataFolder(), "wand_progress.yml");
        if (!newProg.exists() && oldProg.exists()) oldProg.renameTo(newProg);
        this.progressFile = newProg.exists() ? newProg : (oldProg.exists() ? oldProg : newProg);

        loadProgress();
    }

    public int getNextUnplacedStep() {
        return getNextUnplacedStep(null);
    }

    public int getNextUnplacedStep(Player player) {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null || !CitizensAPI.hasImplementation()) {
            return 1;
        }
        List<String> order = registry.getWandOrder();
        String currentWorld = player != null ? player.getWorld().getName() : null;

        for (int i = 0; i < order.size(); i++) {
            String charId = order.get(i);
            NPC npc = null;
            if (currentWorld != null) {
                npc = zoneManager.findCitizensNpc(charId, currentWorld);
            }
            if (npc == null) {
                List<NPC> all = zoneManager.findAllCitizensNpc(charId);
                for (NPC cand : all) {
                    if (cand.isSpawned() && cand.getEntity() != null) {
                        npc = cand;
                        break;
                    }
                }
            }
            if (npc == null || !npc.isSpawned() || npc.getEntity() == null) {
                return i + 1;
            }
        }
        return order.size();
    }

    private void loadProgress() {
        if (!progressFile.exists()) {
            defaultStep = 1;
            return;
        }
        try {
            org.bukkit.configuration.file.YamlConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(progressFile);
            defaultStep = cfg.getInt("default_step", 1);
            if (cfg.contains("players")) {
                org.bukkit.configuration.ConfigurationSection sec = cfg.getConfigurationSection("players");
                if (sec != null) {
                    for (String key : sec.getKeys(false)) {
                        try {
                            UUID u = UUID.fromString(key);
                            wandStep.put(u, sec.getInt(key, defaultStep));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public void saveProgress() {
        try {
            org.bukkit.configuration.file.YamlConfiguration cfg = new org.bukkit.configuration.file.YamlConfiguration();
            cfg.set("default_step", defaultStep);
            for (Map.Entry<UUID, Integer> entry : wandStep.entrySet()) {
                cfg.set("players." + entry.getKey().toString(), entry.getValue());
            }
            cfg.save(progressFile);
        } catch (Exception ignored) {}
    }

    public void giveWand(Player player) {
        int nextUnplaced = getNextUnplacedStep(player);
        int currentStored = wandStep.getOrDefault(player.getUniqueId(), -1);
        int step = (currentStored > 0 && currentStored <= registry.getWandOrder().size()) ? currentStored : nextUnplaced;
        if (step >= registry.getWandOrder().size() && nextUnplaced < registry.getWandOrder().size()) {
            step = nextUnplaced;
        }
        giveSetupWand(player, step);
    }

    public void giveSetupWand(Player player, int step) {
        List<String> order = registry.getWandOrder();
        int total = order.size();
        step = Math.max(1, Math.min(step, total));
        wandStep.put(player.getUniqueId(), step);
        defaultStep = Math.max(defaultStep, step);
        saveProgress();

        String charId = order.get(step - 1);
        NpcCharacter c = registry.getCharacter(charId);

        ItemStack wand = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l[★] Sanctuary Setup Wand");
            List<String> lore = new ArrayList<>();
            lore.add("§7Administrative tool for sequential NPC & Scene deployment.");
            lore.add("§f");
            lore.add("§e▶ Right-Click Block: §fDeploy Active NPC / Workstation & Advance");
            lore.add("§e▶ Left-Click Block: §fSet District Warp Anchor");
            lore.add("§e▶ Shift + Left-Click: §fUndo Placement & Step Backward");
            lore.add("§e▶ Right-Click Air: §fView Step Guidance & Position Tips");
            lore.add("§e▶ Shift + Right-Click: §fOpen District Tracker / Skip Workstations");
            lore.add("§f");
            lore.add("§8Dragoncote Multiverse Management Suite");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            wand.setItemMeta(meta);
        }

        player.getInventory().addItem(wand);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GOLD, 1.0f, 1.2f);
        player.sendMessage(DragoncoteColor.PREFIX_INFO + "Sanctuary Setup Wand equipped! Sequential Setup Mode active.");
        if (c != null) {
            showStepNotification(player, step, c);
        }
    }

    public void giveEditorWand(Player player) {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d§l[✦] NPC & Scene Editor Wand");
            List<String> lore = new ArrayList<>();
            lore.add("§7Administrative tool for inspecting & tweaking NPCs and Scenes.");
            lore.add("§f");
            lore.add("§d▶ Right-Click Questline NPC: §fOpen Stage & Dossier GUI Editor");
            lore.add("§6▶ Right-Click Scene Mob: §fOpen Scene Workstation Tweaker GUI");
            lore.add("§e▶ Right-Click Air: §fOpen District & NPC Tracker");
            lore.add("§f");
            lore.add("§8Dragoncote Multiverse Management Suite");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(editorWandKey, PersistentDataType.BYTE, (byte) 1);
            wand.setItemMeta(meta);
        }

        player.getInventory().addItem(wand);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1.0f, 1.4f);
        player.sendMessage(DragoncoteColor.PREFIX_INFO + "Editor Wand equipped! Right-click any NPC or Scene mob in-world to configure.");
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public boolean isEditorWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(editorWandKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean isSetup = isWand(held);
        boolean isEditor = isEditorWand(held);
        if (!isSetup && !isEditor) return;
        if (!player.hasPermission("dccosmetics.admin")) return;

        event.setCancelled(true);

        // 400ms debounce guard per admin
        long now = System.currentTimeMillis();
        long last = lastClickTime.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 400L) return;
        lastClickTime.put(player.getUniqueId(), now);

        // If player recently clicked an entity within 600ms, ignore air/block interact to prevent overriding GUI
        long lastEntity = lastEntityInteractTime.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastEntity < 600L) return;

        // Handler for Editor Wand clicks
        if (isEditor) {
            if (player.isSneaking() && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
                trackerGUI.open(player);
            } else if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                DCMessage.sendInfo(player, "Right-click a Questline NPC to open its Dossier & Stages Editor, or a Scene mob to open the Tweaker GUI! (Shift+Right-Click for District Tracker)");
            }
            return;
        }

        // Handler for Setup Wand clicks
        List<String> order = registry.getWandOrder();
        int total = order.size();
        int step = wandStep.getOrDefault(player.getUniqueId(), 1);
        if (step < 1) step = 1;
        if (step > total) step = total;

        String charId = order.get(step - 1);
        NpcCharacter c = registry.getCharacter(charId);

        // 1. Shift + Right-Click: Skip companion mode OR open tracker
        if (player.isSneaking() && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            if (companionSubStep.containsKey(player.getUniqueId())) {
                skipCompanion(player);
            } else {
                trackerGUI.open(player);
            }
            return;
        }

        // 2. Shift + Left-Click (air or block): UNDO
        if (player.isSneaking() && (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK)) {
            undo(player);
            return;
        }

        // 3. Right-Click Air: Audit & Guidance
        if (!player.isSneaking() && event.getAction() == Action.RIGHT_CLICK_AIR) {
            if (companionSubStep.containsKey(player.getUniqueId())) {
                showCompanionGuidance(player);
            } else {
                printSector1Audit(player);
                if (c != null) {
                    showStepGuidance(player, step, c);
                }
            }
            return;
        }

        // 4. Left-Click Block (not sneaking): Set District Warp Anchor
        if (!player.isSneaking() && event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            String zoneId = (c != null && c.getZoneId() != null) ? c.getZoneId() : "forge";
            NpcZone zone = zoneManager.getZone(zoneId);

            Location warpLoc = event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
            warpLoc.setYaw(player.getLocation().getYaw());
            warpLoc.setPitch(0.0f);

            zoneManager.setZoneWarpLocation(zoneId, warpLoc);
            player.playSound(warpLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.6f);
            player.sendMessage(DragoncoteColor.PREFIX_GENERAL + "District Warp Anchor for §b" +
                    (zone != null ? zone.getDisplayName() : zoneId) + " §7set to: §f" +
                    warpLoc.getWorld().getName() + " (" + warpLoc.getBlockX() + ", " + warpLoc.getBlockY() + ", " + warpLoc.getBlockZ() + ")");
            return;
        }

        // 5. Right-Click Block (not sneaking): Deploy Active Step NPC or Companion Workstation
        if (!player.isSneaking() && event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Location spawnLoc = event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
            spawnLoc.setYaw(player.getLocation().getYaw());
            spawnLoc.setPitch(0.0f);

            // Sub-Routine: Companion Workstation Placement
            if (companionSubStep.containsKey(player.getUniqueId())) {
                int subStep = companionSubStep.get(player.getUniqueId());
                String parentId = companionParentChar.get(player.getUniqueId());
                NpcCharacter parent = registry.getCharacter(parentId);
                List<String> companions = parent != null ? parent.getCompanionSceneMobIds() : Collections.emptyList();

                if (subStep <= companions.size()) {
                    String mobId = companions.get(subStep - 1);
                    Entity ent = sceneManager.spawnSceneMob(mobId, spawnLoc);
                    if (ent != null) {
                        placedCompanionEntities.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(ent.getUniqueId());
                    }

                    player.playSound(spawnLoc, Sound.BLOCK_ANVIL_USE, 1.0f, 1.4f);
                    player.sendMessage(DragoncoteColor.PREFIX_GENERAL + "§a✔ Placed Scene Workstation §f[" + subStep + "/" + companions.size() + "] §e" + mobId + "§7!");

                    if (subStep < companions.size()) {
                        int nextSub = subStep + 1;
                        companionSubStep.put(player.getUniqueId(), nextSub);
                        String nextMob = companions.get(nextSub - 1);
                        DCMessage.sendInfo(player, "Next Workstation (" + nextSub + "/" + companions.size() + "): Click block to place " + nextMob);
                        player.sendMessage(ColorUtil.colorize("  &7(Run /dcnpc wand skip or Shift+Right-Click to finish remaining workstations)"));
                        return;
                    } else {
                        // All companion workstations placed!
                        companionSubStep.remove(player.getUniqueId());
                        companionParentChar.remove(player.getUniqueId());
                        placedCompanionEntities.remove(player.getUniqueId());
                        DCMessage.sendGeneral(player, "All " + companions.size() + " companion workstations for " + parent.getShortName() + " deployed!");
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

                        // Advance Main Step
                        advanceMainStep(player, step, total, order);
                        return;
                    }
                }
            }

            // Normal Step: Deploy Main Questline NPC
            if (c == null) {
                DCMessage.sendError(player, "Character at step #" + step + " (" + charId + ") not found in registry.");
                return;
            }

            if (CitizensAPI.getPlugin() == null) {
                DCMessage.sendError(player, "Citizens plugin is not active on this server.");
                return;
            }

            deployStepNpc(player, step, c, spawnLoc);

            // Check if character has companion workstations
            if (!c.getCompanionSceneMobIds().isEmpty()) {
                companionSubStep.put(player.getUniqueId(), 1);
                companionParentChar.put(player.getUniqueId(), c.getId());
                placedCompanionEntities.put(player.getUniqueId(), new ArrayList<>());

                List<String> companions = c.getCompanionSceneMobIds();
                player.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
                DCMessage.sendInfo(player, "Companion Scenes: " + c.getShortName() + " placed!");
                player.sendMessage(ColorUtil.colorize("  &7Now click locations to place " + c.getShortName() + "'s " + companions.size() + " supporting workstations:"));
                player.sendMessage(ColorUtil.colorize("  &8• &e[1/" + companions.size() + "] &fClick block to place: &a" + companions.get(0)));
                player.sendMessage(ColorUtil.colorize("  &7(Run /dcnpc wand skip or Shift+Right-Click to skip workstations)"));
                player.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
                return;
            }

            // Advance Main Step immediately if no companions
            advanceMainStep(player, step, total, order);
        }
    }

    private void advanceMainStep(Player player, int currentStep, int total, List<String> order) {
        int nextStep = currentStep + 1;
        wandStep.put(player.getUniqueId(), nextStep);

        if (nextStep > total) {
            DCMessage.sendGeneral(player, "All " + total + " canonical NPCs deployed successfully!");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        } else {
            String nextCharId = order.get(nextStep - 1);
            NpcCharacter nextChar = registry.getCharacter(nextCharId);
            if (nextChar != null) {
                showStepNotification(player, nextStep, nextChar);
            }
        }
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isEditorWand(held)) return;
        if (!player.hasPermission("dccosmetics.admin")) return;

        event.setCancelled(true);
        recordEntityInteract(player.getUniqueId());
        Entity clicked = event.getRightClicked();

        // 1. Check if Citizens NPC
        if (CitizensAPI.getPlugin() != null && CitizensAPI.getNPCRegistry().isNPC(clicked)) {
            NPC npc = CitizensAPI.getNPCRegistry().getNPC(clicked);
            String charId = matchCharId(npc);
            if (charId != null) {
                npcEditorGUI.open(player, charId);
                return;
            }
        }

        // 2. Scene / ModelEngine / MythicMob entity
        sceneEditorGUI.open(player, clicked);
    }

    private String matchCharId(NPC npc) {
        if (npc == null) return null;
        if (npc.data().has("dcnpc_id")) {
            String raw = npc.data().get("dcnpc_id").toString().toLowerCase();
            return raw.contains(":") ? raw.split(":")[0] : raw;
        }
        String npcName = org.bukkit.ChatColor.stripColor(npc.getName());
        for (NpcCharacter c : registry.getAllCharacters()) {
            if (c.getShortName().equalsIgnoreCase(npcName) ||
                c.getId().equalsIgnoreCase(npcName) ||
                npcName.toLowerCase().contains(c.getShortName().toLowerCase()) ||
                npcName.toLowerCase().contains(c.getId().toLowerCase())) {
                return c.getId();
            }
        }
        return null;
    }

    public void skipCompanion(Player player) {
        if (!companionSubStep.containsKey(player.getUniqueId())) {
            player.sendMessage(DragoncoteColor.PREFIX_WARN + "You are not currently placing companion workstations.");
            return;
        }

        String parentId = companionParentChar.remove(player.getUniqueId());
        companionSubStep.remove(player.getUniqueId());
        placedCompanionEntities.remove(player.getUniqueId());

        List<String> order = registry.getWandOrder();
        int total = order.size();
        int step = wandStep.getOrDefault(player.getUniqueId(), 1);
        player.sendMessage(DragoncoteColor.PREFIX_INFO + "Skipped companion workstations for " + parentId + ".");
        advanceMainStep(player, step, total, order);
    }

    public void undo(Player player) {
        // 1. Undo companion sub-step if active
        if (companionSubStep.containsKey(player.getUniqueId())) {
            int subStep = companionSubStep.get(player.getUniqueId());
            String parentId = companionParentChar.get(player.getUniqueId());
            NpcCharacter parent = registry.getCharacter(parentId);
            List<String> companions = parent != null ? parent.getCompanionSceneMobIds() : Collections.emptyList();

            if (subStep > 1) {
                List<UUID> list = placedCompanionEntities.getOrDefault(player.getUniqueId(), new ArrayList<>());
                if (!list.isEmpty()) {
                    UUID lastEnt = list.remove(list.size() - 1);
                    Entity ent = Bukkit.getEntity(lastEnt);
                    if (ent != null) ent.remove();
                }
                int newSub = subStep - 1;
                companionSubStep.put(player.getUniqueId(), newSub);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.2f);
                DCMessage.sendGeneral(player, "Undid companion workstation #" + subStep + ".");
                DCMessage.sendInfo(player, "Click block to re-place: " + companions.get(newSub - 1));
                return;
            } else {
                companionSubStep.remove(player.getUniqueId());
                companionParentChar.remove(player.getUniqueId());
                placedCompanionEntities.remove(player.getUniqueId());
            }
        }

        // 2. Undo main step
        int currentStep = wandStep.getOrDefault(player.getUniqueId(), 1);
        int targetStep = currentStep - 1;
        if (targetStep < 1) {
            DCMessage.sendWarning(player, "Nothing to undo! Already at Step #1 (Corvus).");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
            return;
        }

        List<String> order = registry.getWandOrder();
        String charId = order.get(targetStep - 1);
        NpcCharacter c = registry.getCharacter(charId);

        int removedCount = 0;
        if (CitizensAPI.getPlugin() != null) {
            List<NPC> toDestroy = new ArrayList<>();
            for (NPC npc : CitizensAPI.getNPCRegistry()) {
                if (npc.data().has("dcnpc_id") && npc.data().get("dcnpc_id").toString().equalsIgnoreCase(charId)) {
                    toDestroy.add(npc);
                }
            }
            for (NPC npc : toDestroy) {
                try {
                    npc.destroy();
                    removedCount++;
                } catch (Throwable ignored) {}
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens save");
        }

        wandStep.put(player.getUniqueId(), targetStep);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.2f);
        DCMessage.sendGeneral(player, "Undid placement of #" + targetStep + " " +
                (c != null ? c.getShortName() : charId) + ". Removed " + removedCount + " NPC entity instance(s).");
        DCMessage.sendInfo(player, "Current Wand Step is now back at: #" + targetStep + "/" + order.size());

        if (c != null) {
            showStepNotification(player, targetStep, c);
        }
    }

    private void deployStepNpc(Player player, int step, NpcCharacter c, Location loc) {
        if (c.getSector() == 1) {
            List<String> spawnWorlds = canonManager.getActiveSpawnWorlds();
            for (String wName : spawnWorlds) {
                World w = Bukkit.getWorld(wName);
                if (w == null) continue;

                Location multiLoc = new Location(w, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), 0.0f);
                NPC existing = zoneManager.findCitizensNpc(c.getId(), wName);
                if (existing != null && existing.isSpawned()) {
                    existing.teleport(multiLoc, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                    DCCosmetics.getInstance().getSkinProcessor().applyWorldSkin(existing, wName, c.getId());
                    canonManager.configureNpcOverhead(existing, c, wName);
                    canonManager.applyEngineMode(existing, c);
                } else {
                    NPC spawned = canonManager.spawnCitizensNpc(c, multiLoc, "live");
                    canonManager.applyEngineMode(spawned, c);
                }
            }

            canonManager.setLiveLocation(c.getId(), loc);
            dc.dcessentials.modules.npc.model.NpcStage s1 = c.getStage(1);
            if (s1 != null) {
                s1.setStageLocation(loc);
                registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens save");
            player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
            player.sendMessage(DragoncoteColor.PREFIX_GENERAL + "Deployed §f#" + step + " " + c.getShortName() + " §7(Replicated across §b" + spawnWorlds.size() + " Spawn Dimensions§7)!");
        } else {
            String worldName = loc.getWorld().getName();
            NPC existing = zoneManager.findCitizensNpc(c.getId(), worldName);
            if (existing != null && existing.isSpawned()) {
                existing.teleport(loc, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                DCCosmetics.getInstance().getSkinProcessor().applyWorldSkin(existing, worldName, c.getId());
                canonManager.configureNpcOverhead(existing, c, worldName);
                canonManager.applyEngineMode(existing, c);
            } else {
                NPC spawned = canonManager.spawnCitizensNpc(c, loc, "live");
                canonManager.applyEngineMode(spawned, c);
            }

            canonManager.setLiveLocation(c.getId(), loc);
            dc.dcessentials.modules.npc.model.NpcStage s1 = c.getStage(1);
            if (s1 != null) {
                s1.setStageLocation(loc);
                registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens save");
            player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
            player.sendMessage(DragoncoteColor.PREFIX_GENERAL + "Deployed §f#" + step + " " + c.getShortName() + " §7in §b" + worldName + " §7(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")!");
        }
    }

    public void showCompanionGuidance(Player player) {
        int subStep = companionSubStep.getOrDefault(player.getUniqueId(), 1);
        String parentId = companionParentChar.get(player.getUniqueId());
        NpcCharacter parent = registry.getCharacter(parentId);
        List<String> companions = parent != null ? parent.getCompanionSceneMobIds() : Collections.emptyList();

        player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("&8&m----------------------------------------"));
        DCMessage.sendGeneral(player, "Companion Scene Placement: Workstation #" + subStep + " of " + companions.size());
        player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &8• &7Associated NPC: &f" + (parent != null ? parent.getFullName() : parentId)));
        if (subStep <= companions.size()) {
            player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &8• &7Target Workstation: &a" + companions.get(subStep - 1)));
        }
        player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &8• &7Action: &eRight-Click Block &7to place workstation facing your yaw."));
        player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &8• &7Skip: &eShift+Right-Click &7or &e/dcnpc wand skip &7to skip."));
        player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &8• &7Undo: &eShift+Left-Click &7to step backward."));
        player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("&8&m----------------------------------------"));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.2f);
    }

    public void showStepNotification(Player player, int step, NpcCharacter c) {
        int total = registry.getWandOrder().size();
        DCMessage.sendInfo(player, "Next Objective: #" + step + "/" + total + " " + c.getShortName() + " (" + c.getRole() + ")");
        player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &8• &7Faction: " + registry.getFactionHex(c.getFaction()) + c.getFaction().toUpperCase() +
                " &7| Sector: &e" + c.getSector() + " &7| District: &b" + (c.getZoneId() != null ? c.getZoneId().toUpperCase() : "GENERAL")));
        player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &8• &7Placement Guide: &f" + c.getDeployAdvice()));

        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(dc.dcessentials.core.util.ColorUtil.colorize("&6Setup Wand #" + step + "/" + total + " &8• &f" + c.getShortName() + " &7(" + c.getDeployAdvice() + ")")));
        } catch (Throwable ignored) {}
    }

    public void showStepGuidance(Player player, int step, NpcCharacter c) {
        int total = registry.getWandOrder().size();
        player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("&8&m----------------------------------------"));
        DCMessage.sendGeneral(player, "Wand Setup Status: Step #" + step + " of " + total);
        player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &8• &7Target Personnel: &f" + c.getFullName() + " &7(" + c.getRole() + ")"));
        player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &8• &7Faction: " + registry.getFactionHex(c.getFaction()) + c.getFaction().toUpperCase() +
                " &7| Sector: &e" + c.getSector() + " &7| District: &b" + (c.getZoneId() != null ? c.getZoneId().toUpperCase() : "GENERAL")));
        player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &8• &7Placement Guide: &a" + c.getDeployAdvice()));
        if (c.getSector() == 1) {
            player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &8• &7Multi-World Policy: &bReplicates 1:1 across spawn_main, spawn_bw, and spawn_snow!"));
        } else {
            player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &8• &7Multi-World Policy: &eSingle deployment in LowerCity (" + (c.getZoneId() != null ? c.getZoneId() : "lowercity") + ")!"));
        }
        if (!c.getCompanionSceneMobIds().isEmpty()) {
            player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &8• &7Companion Scenes: &e" + c.getCompanionSceneMobIds().size() + " &7workstations to place after deployment!"));
        }
        player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &8• &7Actions: &eRight-Click Block &7(Deploy) | &eLeft-Click &7(Set Warp)"));
        player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &8• &7Navigation: &eShift+Left-Click &7(Undo) | &eShift+Right-Click &7(Tracker)"));
        player.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("&8&m----------------------------------------"));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.2f);
    }

    public void setStep(Player player, int step) {
        List<String> order = registry.getWandOrder();
        int total = order.size();
        step = Math.max(1, Math.min(step, total));
        wandStep.put(player.getUniqueId(), step);
        String charId = order.get(step - 1);
        NpcCharacter c = registry.getCharacter(charId);
        player.sendMessage(DragoncoteColor.PREFIX_INFO + "Wand step set to §e#" + step + "/" + total + " §f" + (c != null ? c.getShortName() : charId));
        if (c != null) showStepNotification(player, step, c);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    public void setStepByCharacter(Player player, String charId) {
        List<String> order = registry.getWandOrder();
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).equalsIgnoreCase(charId)) {
                setStep(player, i + 1);
                return;
            }
        }
        player.sendMessage(DragoncoteColor.PREFIX_ERROR + "Character '" + charId + "' not found in sequential wand order.");
    }

    public int getStep(Player player) {
        return wandStep.getOrDefault(player.getUniqueId(), 1);
    }

    public void printSector1Audit(org.bukkit.command.CommandSender sender) {
        sender.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("&8&m----------------------------------------"));
        DCMessage.sendGeneral(sender, "Sector 1 Audit: Sanctuary Rebellion (1–20)");
        sender.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("  &7Auditing live spawned NPCs, calibrated anchors, and district warps:"));
        sender.sendMessage(dc.dcessentials.core.util.ColorUtil.colorize("&8&m----------------------------------------"));

        List<String> order = registry.getWandOrder();
        int totalSector1 = Math.min(20, order.size());
        int liveSpawnedCount = 0;
        int nextUnplacedIndex = -1;

        for (int i = 0; i < totalSector1; i++) {
            String charId = order.get(i);
            NpcCharacter c = registry.getCharacter(charId);
            if (c == null) continue;

            // Check if Citizens NPC is spawned in sender's world, or anywhere across multiverse
            NPC liveNpc = null;
            String senderWorld = (sender instanceof Player) ? ((Player) sender).getWorld().getName() : null;
            if (senderWorld != null) {
                liveNpc = zoneManager.findCitizensNpc(charId, senderWorld);
            }
            if (liveNpc == null || !liveNpc.isSpawned() || liveNpc.getEntity() == null) {
                for (NPC cand : zoneManager.findAllCitizensNpc(charId)) {
                    if (cand.isSpawned() && cand.getEntity() != null) {
                        liveNpc = cand;
                        break;
                    } else if (liveNpc == null) {
                        liveNpc = cand;
                    }
                }
            }

            boolean isLive = liveNpc != null && liveNpc.isSpawned() && liveNpc.getEntity() != null;
            boolean isStored = liveNpc != null && (liveNpc.isSpawned() || liveNpc.getStoredLocation() != null);

            if (isLive || isStored) {
                liveSpawnedCount++;
            } else if (nextUnplacedIndex == -1) {
                nextUnplacedIndex = i + 1;
            }

            // Stage 1 Location
            Location liveLoc = canonManager.getLiveLocation(charId);
            if (isLive) {
                liveLoc = liveNpc.getEntity().getLocation();
            } else if (isStored && liveNpc.getStoredLocation() != null) {
                liveLoc = liveNpc.getStoredLocation();
            }
            boolean hasLoc = (liveLoc != null && liveLoc.getWorld() != null);

            // Bound Zone & Warp
            String zoneId = c.getZoneId();
            boolean hasZone = (zoneId != null && !zoneId.isEmpty() && !zoneId.equalsIgnoreCase("none"));
            boolean hasWarp = false;
            if (hasZone) {
                dc.dcessentials.modules.npc.model.NpcZone z = zoneManager.getZone(zoneId);
                hasWarp = (z != null && z.getWarpLocation() != null && z.getWarpLocation().getWorld() != null);
            }

            String liveStr;
            if (isLive) {
                liveStr = "&a[✔ LIVE @" + liveNpc.getEntity().getWorld().getName() + "]";
            } else if (isStored) {
                String w = (liveNpc.getStoredLocation() != null && liveNpc.getStoredLocation().getWorld() != null) ?
                        liveNpc.getStoredLocation().getWorld().getName() : "idle";
                liveStr = "&e[⧗ IDLE @" + w + "]";
            } else {
                liveStr = "&c[✘ NOT SPAWNED]";
            }

            String locStr = hasLoc ?
                    "&7(" + liveLoc.getBlockX() + ", " + liveLoc.getBlockY() + ", " + liveLoc.getBlockZ() + ")" :
                    "&8(Uncalibrated)";

            String zoneStr = hasZone ? ("&b[" + zoneId + "]") : "&c[Unbound]";
            String warpStr = hasWarp ? "&a✔ Warp" : "&8No Warp";

            sender.sendMessage(DragoncoteColor.colorize(
                    "&e#" + (i + 1) + " &f" + c.getShortName() + " &7| " + liveStr + " " + locStr + " &7| " + zoneStr + " &7| " + warpStr
            ));
        }

        sender.sendMessage(DragoncoteColor.colorize("<#38BDF8>--------------------------------------------------"));
        String scoreColor = liveSpawnedCount == totalSector1 ? "&a" : (liveSpawnedCount > 10 ? "&e" : "&c");
        sender.sendMessage(DragoncoteColor.colorize("&7Sector 1 Live Spawned: " + scoreColor + liveSpawnedCount + "/" + totalSector1 + " Active"));
        if (nextUnplacedIndex != -1) {
            String nextChar = order.get(nextUnplacedIndex - 1);
            sender.sendMessage(DragoncoteColor.colorize("&6▶ Next Unplaced NPC: &e#" + nextUnplacedIndex + " &f" + nextChar.toUpperCase() + " &7(Equip wand to deploy)"));
        } else {
            sender.sendMessage(DragoncoteColor.colorize("&a✔ All Sector 1 NPCs are fully placed and active in-world!"));
        }
        sender.sendMessage(DragoncoteColor.colorize("<#38BDF8>=================================================="));
    }
}
