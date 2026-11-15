package dc.dcessentials.modules.npc.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.npc.NpcCanonManager;
import dc.dcessentials.modules.npc.NpcRegistry;
import dc.dcessentials.modules.npc.NpcZoneManager;
import dc.dcessentials.modules.npc.model.DecorationNpc;
import dc.dcessentials.modules.npc.model.NpcBehaviorProfile;
import dc.dcessentials.modules.npc.model.NpcCharacter;
import dc.dcessentials.modules.npc.scene.DecorationManager;
import dc.dcessentials.modules.npc.scene.SceneCatalog;
import dc.dcessentials.modules.npc.skin.BbmodelSkinBaker;
import dc.dcessentials.modules.npc.skin.SkinProcessor;
import dc.dcessentials.modules.npc.util.DragoncoteColor;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

/**
 * Unified Skin Management & .bbmodel Skin Baking GUI for both Storyline & Decoration NPCs.
 */
public class SkinBakeGUI implements Listener {

    private static final String TITLE_PREFIX = "§8[Skin Baker] ";

    private static class Session {
        final String targetType;
        final String targetId;
        Session(String targetType, String targetId) {
            this.targetType = targetType;
            this.targetId = targetId;
        }
    }

    private final NpcRegistry registry;
    private final SkinProcessor skinProcessor;
    private final BbmodelSkinBaker modelBaker;
    private final SceneCatalog sceneCatalog;
    private final NpcZoneManager zoneManager;
    private final NpcCanonManager canonManager;

    private DecorationManager decorationManager;
    private NpcEditorGUI npcEditorGUI;
    private DecorationEditorGUI decorationEditorGUI;

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Inventory> activeInventories = new HashMap<>();

    public SkinBakeGUI(NpcRegistry registry, SkinProcessor skinProcessor, BbmodelSkinBaker modelBaker,
                       SceneCatalog sceneCatalog, NpcZoneManager zoneManager, NpcCanonManager canonManager) {
        this.registry = registry;
        this.skinProcessor = skinProcessor;
        this.modelBaker = modelBaker;
        this.sceneCatalog = sceneCatalog;
        this.zoneManager = zoneManager;
        this.canonManager = canonManager;
    }

    public void setDecorationManager(DecorationManager decorationManager) { this.decorationManager = decorationManager; }
    public void setNpcEditorGUI(NpcEditorGUI npcEditorGUI) { this.npcEditorGUI = npcEditorGUI; }
    public void setDecorationEditorGUI(DecorationEditorGUI decorationEditorGUI) { this.decorationEditorGUI = decorationEditorGUI; }

    public void openChar(Player player, String charId) {
        open(player, "CHAR", charId);
    }

    public void openDeco(Player player, String decoId) {
        open(player, "DECO", decoId);
    }

    private void open(Player player, String targetType, String targetId) {
        String skinId = resolveSkinId(targetType, targetId);
        NpcBehaviorProfile profile = resolveProfile(targetType, targetId);
        if (skinId == null || profile == null) {
            DCMessage.sendError(player, "Cannot open Skin Baker: target '" + targetId + "' not found.");
            return;
        }

        skinProcessor.ensureDirectory(skinId);
        Session session = new Session(targetType, targetId.toLowerCase());

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_PREFIX + "§d" + targetId);
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, "§7");
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        boolean hasPng = skinProcessor.hasLocalPng(skinId);
        boolean hasPrimeCache = skinProcessor.hasSignedCache(skinId, "prime");
        boolean hasBwCache = skinProcessor.hasSignedCache(skinId, "bw");
        boolean hasSnowCache = skinProcessor.hasSignedCache(skinId, "snow");

        inv.setItem(4, makeItem(Material.PLAYER_HEAD,
                "§d§lSkin Dossier: §f" + targetId,
                "§7Skin Folder: §e/npcs/" + skinId + "/",
                "§7Local default.png: " + (hasPng ? "§a✔ Present" : "§c✖ Missing"),
                "§7MineSkin Prime Cache: " + (hasPrimeCache ? "§a✔ Signed" : "§7Not Yet Baked"),
                "§7MineSkin Void BW Cache: " + (hasBwCache ? "§a✔ Signed" : "§7Not Yet Baked"),
                "§7MineSkin Snow Cache: " + (hasSnowCache ? "§a✔ Signed" : "§7Not Yet Baked"),
                "§7Skin Revision: §b#r" + profile.getSkinRevision()
        ));

        inv.setItem(11, makeItem(
                Material.ANVIL,
                "§a§lBake Local PNG -> Citizens Multiverse",
                "§7Reads §f/npcs/" + skinId + "/default.png§7,",
                "§7auto-generates ITU-R BT.601 §8spawn_bw.png§7 + §bspawn_snow.png§7,",
                "§7uploads all 3 to MineSkin, bumps skin revision,",
                "§7and permanently locks Citizens skin textures.",
                "§f",
                hasPng ? "§a▶ Click to Bake & Lock Skin" : "§c✖ Upload default.png or use /dcnpc skin first"
        ));

        inv.setItem(13, makeItem(
                Material.ENDER_EYE,
                "§b§lRefresh & Lock Live NPC Skins",
                "§7Invalidates skin cache and reapplies",
                "§7dimension-locked textures across all spawned instances",
                "§7(prevents Steve / Knight Mojang reverts).",
                "§f",
                "§e▶ Click to Force Refresh All Worlds"
        ));

        SceneCatalog.SceneModelInfo modelInfo = sceneCatalog.getModel(profile.getModelId());
        inv.setItem(15, makeItem(
                Material.ARMOR_STAND,
                "§6§lBake Skin into 3D Scene .bbmodel",
                "§7Current 3D Base Model: §d" + profile.getModelId(),
                "§7Model Replaceable Slot: " + (modelInfo != null && modelInfo.hasReplaceableSkin ? "§a✔ Detected" : "§eGeneric 64x64 fallback"),
                "§7Baked Colored ID: §f" + (profile.getBakedModelId() != null ? profile.getBakedModelId() : "§8None"),
                "§7Baked BW ID: §7" + (profile.getBakedModelBwId() != null ? profile.getBakedModelBwId() : "§8None"),
                "§f",
                hasPng ? "§e▶ Click to Generate Colored + BW .bbmodel & Reload MEG" : "§c✖ Requires default.png first"
        ));

        inv.setItem(29, makeItem(
                Material.PAPER,
                "§e§lCLI Commands (URL / Mojang Player)",
                "§7Set & bake both Citizens + active Scene model:",
                "§f/dcnpc skin " + targetId + " <url|player|file> [model|citizens|all]",
                "§f",
                "§e▶ Click to print copy-ready commands in chat"
        ));

        inv.setItem(33, makeItem(
                Material.MILK_BUCKET,
                "§c§lClear Baked .bbmodel Skin Override",
                "§7Reverts 3D NPC back to the base blueprint's",
                "§7original Scene textures.",
                "§f",
                "§e▶ Click to Clear Baked Override"
        ));

        inv.setItem(45, makeItem(Material.ARROW, "§c§l◀ Back to Editor"));
        inv.setItem(53, makeItem(Material.BARRIER, "§c§lClose"));

        activeInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
        sessions.put(player.getUniqueId(), session);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().startsWith(TITLE_PREFIX)) return;

        event.setCancelled(true);
        Session s = sessions.get(player.getUniqueId());
        if (s == null) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 53) {
            player.closeInventory();
            return;
        }
        if (slot == 45) {
            if ("DECO".equals(s.targetType) && decorationEditorGUI != null) {
                decorationEditorGUI.open(player, s.targetId);
            } else if (npcEditorGUI != null) {
                npcEditorGUI.open(player, s.targetId);
            } else {
                player.closeInventory();
            }
            return;
        }

        String skinId = resolveSkinId(s.targetType, s.targetId);
        NpcBehaviorProfile profile = resolveProfile(s.targetType, s.targetId);
        if (skinId == null || profile == null) return;

        if (slot == 11) {
            if (!skinProcessor.hasLocalPng(skinId)) {
                DCMessage.sendError(player, "Missing /npcs/" + skinId + "/default.png! Run /dcnpc skin " + s.targetId + " <url|player> first.");
                return;
            }
            player.closeInventory();
            DCMessage.sendInfo(player, "Baking local skin + generating BW/Snow variants for " + s.targetId + "...");
            skinProcessor.processSkin(skinId, "file").thenAccept(ok -> Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
                if (ok) {
                    profile.bumpSkinRevision();
                    saveAndRefreshAll(s, profile, skinId);
                    DCMessage.sendGeneral(player, "Successfully baked & locked multiverse skin for §f" + s.targetId + " §7(rev #" + profile.getSkinRevision() + ")!");
                } else {
                    DCMessage.sendError(player, "Skin baking failed for " + s.targetId + ". Check console for MineSkin details.");
                }
            }));
            return;
        }

        if (slot == 13) {
            skinProcessor.invalidateCache(skinId);
            profile.bumpSkinRevision();
            saveAndRefreshAll(s, profile, skinId);
            DCMessage.sendGeneral(player, "Force-refreshed and locked all world skins for §f" + s.targetId + " §7(rev #" + profile.getSkinRevision() + ").");
            open(player, s.targetType, s.targetId);
            return;
        }

        if (slot == 15) {
            if (!skinProcessor.hasLocalPng(skinId)) {
                DCMessage.sendError(player, "Missing /npcs/" + skinId + "/default.png! Download/bake skin first.");
                return;
            }
            SceneCatalog.SceneModelInfo info = sceneCatalog.getModel(profile.getModelId());
            if (info == null || info.bbmodelFile == null) {
                DCMessage.sendError(player, "Base .bbmodel file not found for '" + profile.getModelId() + "'. Select a Scenes model first.");
                return;
            }
            File dir = skinProcessor.getNpcDirectory(skinId);
            File png = new File(dir, "default.png");
            File bwPng = new File(dir, "spawn_bw.png");
            BbmodelSkinBaker.BakeResult res = modelBaker.bakeSkinIntoBbmodel(
                    info.bbmodelFile, s.targetId, png, bwPng.exists() ? bwPng : null, info.modelId);
            if (!res.success) {
                DCMessage.sendError(player, "Failed baking .bbmodel: " + res.error);
                return;
            }
            profile.setEngineType(NpcBehaviorProfile.ENGINE_MODEL_ENGINE);
            profile.setBakedModelId(res.coloredModelId);
            profile.setBakedModelBwId(res.bwModelId);
            saveProfileOnly(s);
            modelBaker.reloadModelEngineAsync();
            DCMessage.sendGeneral(player, "Baked custom 3D skin models §f" + res.coloredModelId + " §7& §7" + res.bwModelId + "§7. Reloading ModelEngine...");
            Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
                sceneCatalog.reload();
                saveAndRefreshAll(s, profile, skinId);
            }, 45L);
            player.closeInventory();
            return;
        }

        if (slot == 29) {
            player.closeInventory();
            player.sendMessage(DragoncoteColor.colorize("&8&m----------------------------------------"));
            DCMessage.sendInfo(player, "Skin Baking Commands for §f" + s.targetId + "§7:");
            player.sendMessage("§e• /dcnpc skin " + s.targetId + " <playerName> §7- Bake Mojang player skin");
            player.sendMessage("§e• /dcnpc skin " + s.targetId + " <https://...> §7- Bake PNG URL");
            player.sendMessage("§e• /dcnpc skin " + s.targetId + " <source> model §7- Also bake into 3D Scene .bbmodel");
            player.sendMessage("§e• /dcnpc skin " + s.targetId + " <source> all §7- Bake BOTH Citizens & 3D .bbmodel");
            player.sendMessage(DragoncoteColor.colorize("&8&m----------------------------------------"));
            return;
        }

        if (slot == 33) {
            profile.setBakedModelId(null);
            profile.setBakedModelBwId(null);
            saveAndRefreshAll(s, profile, skinId);
            DCMessage.sendWarning(player, "Cleared custom .bbmodel baked skin override for " + s.targetId + ".");
            open(player, s.targetType, s.targetId);
        }
    }

    private void saveProfileOnly(Session s) {
        if ("DECO".equals(s.targetType) && decorationManager != null) {
            decorationManager.save();
        } else {
            registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
        }
    }

    private void saveAndRefreshAll(Session s, NpcBehaviorProfile profile, String skinId) {
        saveProfileOnly(s);
        if ("DECO".equals(s.targetType) && decorationManager != null) {
            DecorationNpc deco = decorationManager.getDecoration(s.targetId);
            if (deco != null) {
                for (NPC npc : decorationManager.findInstances(deco.getId())) {
                    if (npc.isSpawned() && npc.getEntity() != null) {
                        decorationManager.applyDecorationVisuals(npc, deco, npc.getEntity().getWorld().getName());
                    }
                }
            }
        } else {
            NpcCharacter c = registry.getCharacter(s.targetId);
            if (c != null) {
                for (NPC npc : zoneManager.findAllCitizensNpc(c.getId())) {
                    if (npc.isSpawned() && npc.getEntity() != null) {
                        skinProcessor.applyProfileSkin(npc, npc.getEntity().getWorld().getName(), skinId, c.getSkin(), profile.getSkinRevision());
                        canonManager.applyProfileEngine(npc, profile, c.getId(), skinId, c.getSkin());
                    }
                }
            }
        }
    }

    private String resolveSkinId(String targetType, String targetId) {
        if ("DECO".equals(targetType) && decorationManager != null) {
            DecorationNpc deco = decorationManager.getDecoration(targetId);
            return deco != null ? deco.getSkinId() : null;
        }
        NpcCharacter c = registry.getCharacter(targetId);
        return c != null ? c.getId() : null;
    }

    private NpcBehaviorProfile resolveProfile(String targetType, String targetId) {
        if ("DECO".equals(targetType) && decorationManager != null) {
            DecorationNpc deco = decorationManager.getDecoration(targetId);
            return deco != null ? deco.getProfile() : null;
        }
        NpcCharacter c = registry.getCharacter(targetId);
        return c != null ? c.getBehaviorProfile() : null;
    }

    private static ItemStack makeItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (event.getInventory().equals(activeInventories.get(uuid))) {
            sessions.remove(uuid);
            activeInventories.remove(uuid);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.remove(uuid);
        activeInventories.remove(uuid);
    }
}
