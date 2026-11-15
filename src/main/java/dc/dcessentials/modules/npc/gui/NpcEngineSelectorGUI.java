package dc.dcessentials.modules.npc.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.npc.NpcCanonManager;
import dc.dcessentials.modules.npc.NpcRegistry;
import dc.dcessentials.modules.npc.NpcZoneManager;
import dc.dcessentials.modules.npc.behavior.NpcBehaviorController;
import dc.dcessentials.modules.npc.model.DecorationNpc;
import dc.dcessentials.modules.npc.model.NpcBehaviorProfile;
import dc.dcessentials.modules.npc.model.NpcCharacter;
import dc.dcessentials.modules.npc.scene.DecorationManager;
import dc.dcessentials.modules.npc.scene.SceneCatalog;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 54-Slot Dynamic Engine, Scenes Blueprint, Animation Mode & Custom Patrol Path Selector.
 * Shared across Storyline DCNPCs and Out-of-Storyline Decoration DCNPCs.
 */
public class NpcEngineSelectorGUI implements Listener {

    private static final String TITLE_PREFIX = "§8[Engine] ";

    private static class ViewState {
        final String targetType; // "CHAR" or "DECO"
        final String targetId;
        String viewMode;         // "MODELS" or "ANIMS"
        String category;
        int page;

        ViewState(String targetType, String targetId, String viewMode, String category, int page) {
            this.targetType = targetType;
            this.targetId = targetId;
            this.viewMode = viewMode;
            this.category = category;
            this.page = page;
        }
    }

    private final NpcRegistry registry;
    private NpcEditorGUI npcEditorGUI;
    private SceneCatalog sceneCatalog;
    private NpcBehaviorController behaviorController;
    private DecorationManager decorationManager;
    private DecorationEditorGUI decorationEditorGUI;
    private NpcCanonManager canonManager;
    private NpcZoneManager zoneManager;

    private final Map<UUID, ViewState> sessions = new HashMap<>();

    public NpcEngineSelectorGUI(NpcRegistry registry) {
        this.registry = registry;
    }

    public void setNpcEditorGUI(NpcEditorGUI npcEditorGUI) { this.npcEditorGUI = npcEditorGUI; }
    public void setSceneCatalog(SceneCatalog sceneCatalog) { this.sceneCatalog = sceneCatalog; }
    public void setBehaviorController(NpcBehaviorController behaviorController) { this.behaviorController = behaviorController; }
    public void setDecorationManager(DecorationManager decorationManager) { this.decorationManager = decorationManager; }
    public void setDecorationEditorGUI(DecorationEditorGUI decorationEditorGUI) { this.decorationEditorGUI = decorationEditorGUI; }
    public void setCanonManager(NpcCanonManager canonManager) { this.canonManager = canonManager; }
    public void setZoneManager(NpcZoneManager zoneManager) { this.zoneManager = zoneManager; }

    public void open(Player player, String charId) {
        openTarget(player, "CHAR", charId, "MODELS", null, 0);
    }

    public void openDeco(Player player, String decoId) {
        openTarget(player, "DECO", decoId, "MODELS", null, 0);
    }

    private void openTarget(Player player, String targetType, String targetId, String viewMode, String category, int page) {
        NpcBehaviorProfile profile = resolveProfile(targetType, targetId);
        if (profile == null) {
            DCMessage.sendError(player, "Target NPC not found: " + targetId);
            return;
        }

        if (sceneCatalog != null && sceneCatalog.getAllModels().isEmpty()) {
            sceneCatalog.reload();
        }

        if (category == null) {
            SceneCatalog.SceneModelInfo current = (sceneCatalog != null) ? sceneCatalog.getModel(profile.getModelId()) : null;
            category = (current != null) ? current.category : "BLACKSMITHS";
        }

        ViewState state = new ViewState(targetType, targetId.toLowerCase(), viewMode, category, Math.max(0, page));

        String titleName = resolveDisplayTitle(targetType, targetId);
        Inventory inv = Bukkit.createInventory(null, 54,
                TITLE_PREFIX + ("ANIMS".equals(viewMode) ? "§6Anim/Patrol §7- §d" : "§53D Models §7- §d") + trimTitle(titleName, 18));

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, "§7");
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        if ("ANIMS".equals(viewMode)) {
            renderAnimationView(inv, state, profile);
        } else {
            renderModelsView(inv, state, profile);
        }

        player.openInventory(inv);
        sessions.put(player.getUniqueId(), state);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    private void renderModelsView(Inventory inv, ViewState state, NpcBehaviorProfile profile) {
        // Row 0: Citizens Engine + Dynamic Scene Categories + Animation Mode Shortcut
        boolean isCitizens = !profile.isModelEngine();
        inv.setItem(0, makeItem(
                isCitizens ? Material.PLAYER_HEAD : Material.ARMOR_STAND,
                (isCitizens ? "§a§l✔ " : "§b§l") + "Citizens Humanoid",
                "§7Standard player NPC using",
                "§7/npcs/<id>/ PNG skins (prime/snow/bw).",
                "§f",
                isCitizens ? "§aCurrently Active" : "§e▶ Click to switch to Citizens"
        ));

        setCatTab(inv, 1, Material.ANVIL, "Blacksmiths", "BLACKSMITHS", state.category);
        setCatTab(inv, 2, Material.EMERALD, "Market", "MARKET", state.category);
        setCatTab(inv, 3, Material.BOOK, "Townsfolks", "TOWNSFOLKS", state.category);
        setCatTab(inv, 4, Material.SHIELD, "Guards & Battle", "GUARDS_BATTLE", state.category);
        setCatTab(inv, 5, Material.IRON_AXE, "Workers", "WORKERS", state.category);
        setCatTab(inv, 6, Material.PLAYER_HEAD, "Baked Custom", "BAKED_CUSTOM", state.category);

        inv.setItem(8, makeItem(
                Material.CLOCK,
                "§6§lAnimations & Smart Patrol ▶",
                "§7Active Engine: §f" + profile.getEngineType(),
                "§7Base Model: §d" + profile.getModelId(),
                "§7Anim Mode: §e" + profile.getAnimationMode(),
                "§7Waypoints: §a" + profile.getPatrolWaypoints().size(),
                "§f",
                "§e▶ Click to configure Fixed/Smart/Patrol"
        ));

        List<SceneCatalog.SceneModelInfo> list = (sceneCatalog != null)
                ? sceneCatalog.getModelsInCategory(state.category)
                : Collections.emptyList();

        int perPage = 36;
        int totalPages = Math.max(1, (int) Math.ceil(list.size() / (double) perPage));
        int page = Math.min(state.page, totalPages - 1);
        state.page = page;

        int start = page * perPage;
        int end = Math.min(list.size(), start + perPage);
        int slot = 9;
        for (int i = start; i < end; i++) {
            SceneCatalog.SceneModelInfo info = list.get(i);
            boolean active = profile.isModelEngine() && info.modelId.equalsIgnoreCase(profile.getModelId());
            List<String> lore = new ArrayList<>();
            lore.add("§8Blueprint: " + info.modelId);
            lore.add("§7Animations: §f" + info.allAnimations.size()
                    + (info.walkAnimations.isEmpty() ? " §8(Static/Idle)" : " §a(Walk Supported ✔)"));
            lore.add("§7Custom Skin Bake: " + (info.hasReplaceableSkin ? "§a✔ Skin-Replaceable" : "§7Dedicated Texture"));
            if (!info.allAnimations.isEmpty()) {
                int previewMax = Math.min(4, info.allAnimations.size());
                lore.add("§7Sample Anims: §d" + String.join(", ", info.allAnimations.subList(0, previewMax))
                        + (info.allAnimations.size() > 4 ? " ..." : ""));
            }
            lore.add("§f");
            lore.add(active ? "§a§l✔ CURRENTLY EQUIPPED" : "§e▶ Left-Click: Equip Model");
            lore.add("§6▶ Right-Click: Equip & Pick Animations");
            inv.setItem(slot++, makeItemList(active ? Material.LIME_CONCRETE : info.icon,
                    (active ? "§a§l" : "§f§l") + info.displayName, lore));
        }

        // Bottom Bar
        inv.setItem(45, makeItem(Material.ARROW, "§c§l◀ Back to Editor"));
        if (page > 0) inv.setItem(48, makeItem(Material.SPECTRAL_ARROW, "§e◀ Previous Page (" + page + "/" + totalPages + ")"));
        inv.setItem(49, makeItem(Material.NETHER_STAR,
                "§b§lScenes Catalog Status",
                "§7Loaded Blueprints: §a" + (sceneCatalog != null ? sceneCatalog.getAllModels().size() : 0),
                "§7Category Items: §f" + list.size() + " §7(Page " + (page + 1) + "/" + totalPages + ")",
                "§7Excluded: §8pigeon, crow, unrigged parts",
                "§f",
                "§e▶ Click to rescan blueprints on disk"));
        if (page + 1 < totalPages) inv.setItem(50, makeItem(Material.SPECTRAL_ARROW, "§eNext Page (" + (page + 2) + "/" + totalPages + ") ▶"));
        inv.setItem(53, makeItem(Material.BARRIER, "§c§lClose"));
    }

    private void renderAnimationView(Inventory inv, ViewState state, NpcBehaviorProfile profile) {
        String mode = profile.getAnimationMode();
        SceneCatalog.SceneModelInfo info = (sceneCatalog != null) ? sceneCatalog.getModel(profile.getModelId()) : null;

        // Mode controls row
        boolean isDef = NpcBehaviorProfile.ANIM_DEFAULT.equals(mode);
        boolean isSmart = NpcBehaviorProfile.ANIM_SMART.equals(mode);
        boolean isPatrol = NpcBehaviorProfile.ANIM_SMART_PATROL.equals(mode);

        inv.setItem(0, makeItem(
                isDef ? Material.LIME_DYE : Material.GRAY_DYE,
                (isDef ? "§a§l✔ " : "§f§l") + "Default Mode",
                "§7Plays the model's base default pose.",
                "§e▶ Click to activate Default Mode"
        ));

        inv.setItem(1, makeItem(
                isSmart ? Material.NETHER_STAR : Material.ENDER_EYE,
                (isSmart ? "§a§l✔ " : "§b§l") + "Smart Ambient Mode",
                "§7Automatically cycles seamlessly across",
                "§7idle, sitting, waving & social animations.",
                "§7Cycle Interval: §f" + profile.getSmartCycleSeconds() + "s §8(Right-Click cycles 8/12/20s)",
                "§e▶ Left-Click to activate Smart Mode"
        ));

        inv.setItem(2, makeItem(
                isPatrol ? Material.COMPASS : Material.MAP,
                (isPatrol ? "§a§l✔ " : "§6§l") + "Smart Mode + Custom Patrol Path",
                "§7Walks your custom recorded waypoint loop",
                "§7using the model's walk animation, then",
                "§7rests in Smart Mode before repeating.",
                "§7Recorded Waypoints: §f" + profile.getPatrolWaypoints().size(),
                "§7Rest Between Patrols: §e" + profile.getPatrolRestSeconds() + "s §8(Right-Click: 30/60/180/300s)",
                "§e▶ Left-Click to activate Smart Patrol"
        ));

        inv.setItem(4, makeItem(
                Material.BLAZE_ROD,
                "§e§lRecord Custom Patrol Waypoints",
                "§7Walk & Right-Click blocks along your",
                "§7desired safe path (zero wall collisions!).",
                "§7Current Points: §f" + profile.getPatrolWaypoints().size(),
                "§f",
                "§a▶ Click to start In-World Recorder"
        ));

        inv.setItem(5, makeItem(
                Material.REDSTONE,
                "§c§lClear Patrol Waypoints",
                "§7Resets stored waypoints for this NPC."
        ));

        inv.setItem(8, makeItem(Material.PLAYER_HEAD, "§d§l◀ Back to 3D Models"));

        // Animation list (Slots 9-44)
        List<String> anims = (info != null) ? info.allAnimations : Collections.emptyList();
        if (anims.isEmpty()) {
            inv.setItem(22, makeItem(Material.BARRIER,
                    "§cNo Custom Animations Parsed",
                    "§7Model: §f" + profile.getModelId(),
                    "§7Citizens NPCs & static models still work normally,",
                    "§7or choose a Scenes NPC model first."));
        } else {
            int perPage = 36;
            int totalPages = Math.max(1, (int) Math.ceil(anims.size() / (double) perPage));
            int page = Math.min(state.page, totalPages - 1);
            state.page = page;

            int start = page * perPage;
            int end = Math.min(anims.size(), start + perPage);
            int slot = 9;
            for (int i = start; i < end; i++) {
                String anim = anims.get(i);
                boolean selected = NpcBehaviorProfile.ANIM_FIXED.equals(mode) && anim.equalsIgnoreCase(profile.getFixedAnimation());
                boolean isWalk = info.walkAnimations.contains(anim);
                boolean isSocial = info.socialAnimations.contains(anim);
                Material icon = selected ? Material.LIME_CONCRETE
                        : (isWalk ? Material.LEATHER_BOOTS : (isSocial ? Material.SUNFLOWER : Material.BOOK));
                String tag = isWalk ? "§a[Walk/Move]" : (isSocial ? "§6[Social/Interact]" : "§b[Idle/Pose]");
                inv.setItem(slot++, makeItem(icon,
                        (selected ? "§a§l✔ " : "§f") + anim,
                        "§7Role: " + tag,
                        "§f",
                        selected ? "§aCurrently Looped (Fixed Mode)" : "§e▶ Click to lock Fixed Animation & preview"));
            }

            if (page > 0) inv.setItem(48, makeItem(Material.SPECTRAL_ARROW, "§e◀ Previous Animations"));
            if (page + 1 < totalPages) inv.setItem(50, makeItem(Material.SPECTRAL_ARROW, "§eNext Animations ▶"));
        }

        inv.setItem(45, makeItem(Material.ARROW, "§c§l◀ Back to Editor"));
        inv.setItem(49, makeItem(Material.PAPER,
                "§f§lAnimation Summary",
                "§7Engine: §d" + profile.getEngineType() + " (" + profile.getModelId() + ")",
                "§7Mode: §e" + profile.getAnimationMode(),
                "§7Fixed Anim: §f" + profile.getFixedAnimation()));
        inv.setItem(53, makeItem(Material.BARRIER, "§c§lClose"));
    }

    private void setCatTab(Inventory inv, int slot, Material mat, String name, String catKey, String activeCat) {
        boolean sel = catKey.equals(activeCat);
        int count = (sceneCatalog != null) ? sceneCatalog.getModelsInCategory(catKey).size() : 0;
        inv.setItem(slot, makeItem(
                sel ? Material.NETHER_STAR : mat,
                (sel ? "§a§l[ACTIVE] " : "§e§l") + name,
                "§8CategoryKey:" + catKey,
                "§7Blueprints: §f" + count
        ));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(TITLE_PREFIX)) return;

        event.setCancelled(true);
        ViewState state = sessions.get(player.getUniqueId());
        if (state == null) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        NpcBehaviorProfile profile = resolveProfile(state.targetType, state.targetId);
        if (profile == null) return;

        if (slot == 53) {
            player.closeInventory();
            return;
        }
        if (slot == 45) {
            openParentEditor(player, state);
            return;
        }

        if ("MODELS".equals(state.viewMode)) {
            handleModelsClick(player, state, profile, slot, event.isRightClick());
        } else {
            handleAnimsClick(player, state, profile, slot, event.isRightClick());
        }
    }

    private void handleModelsClick(Player player, ViewState state, NpcBehaviorProfile profile, int slot, boolean rightClick) {
        if (slot == 0) {
            profile.setEngineType(NpcBehaviorProfile.ENGINE_CITIZENS);
            persistAndRefresh(state, profile);
            DCMessage.sendGeneral(player, "Switched NPC engine to Citizens Humanoid.");
            openTarget(player, state.targetType, state.targetId, "MODELS", state.category, state.page);
            return;
        }
        if (slot >= 1 && slot <= 6) {
            String[] cats = {"BLACKSMITHS", "MARKET", "TOWNSFOLKS", "GUARDS_BATTLE", "WORKERS", "BAKED_CUSTOM"};
            openTarget(player, state.targetType, state.targetId, "MODELS", cats[slot - 1], 0);
            return;
        }
        if (slot == 8) {
            openTarget(player, state.targetType, state.targetId, "ANIMS", state.category, 0);
            return;
        }
        if (slot == 48 && state.page > 0) {
            openTarget(player, state.targetType, state.targetId, "MODELS", state.category, state.page - 1);
            return;
        }
        if (slot == 50) {
            openTarget(player, state.targetType, state.targetId, "MODELS", state.category, state.page + 1);
            return;
        }
        if (slot == 49 && sceneCatalog != null) {
            sceneCatalog.reload();
            DCMessage.sendInfo(player, "Rescanned Scenes blueprints: " + sceneCatalog.getAllModels().size() + " loaded.");
            openTarget(player, state.targetType, state.targetId, "MODELS", state.category, 0);
            return;
        }

        if (slot >= 9 && slot <= 44 && sceneCatalog != null) {
            List<SceneCatalog.SceneModelInfo> list = sceneCatalog.getModelsInCategory(state.category);
            int idx = state.page * 36 + (slot - 9);
            if (idx < 0 || idx >= list.size()) return;
            SceneCatalog.SceneModelInfo chosen = list.get(idx);

            profile.setEngineType(NpcBehaviorProfile.ENGINE_MODEL_ENGINE);
            profile.setModelId(chosen.modelId);
            if (!"BAKED_CUSTOM".equals(chosen.category)) {
                profile.setBakedModelId(null);
                profile.setBakedModelBwId(null);
            }
            if (chosen.getDefaultIdleAnimation() != null
                    && !chosen.allAnimations.contains(profile.getFixedAnimation())) {
                profile.setFixedAnimation(chosen.getDefaultIdleAnimation());
            }

            persistAndRefresh(state, profile);
            DCMessage.sendGeneral(player, "Equipped 3D model §f" + chosen.modelId + " §7(anim mode: §e" + profile.getAnimationMode() + "§7).");
            if (rightClick) {
                openTarget(player, state.targetType, state.targetId, "ANIMS", state.category, 0);
            } else {
                openTarget(player, state.targetType, state.targetId, "MODELS", state.category, state.page);
            }
        }
    }

    private void handleAnimsClick(Player player, ViewState state, NpcBehaviorProfile profile, int slot, boolean rightClick) {
        if (slot == 8) {
            openTarget(player, state.targetType, state.targetId, "MODELS", state.category, 0);
            return;
        }
        if (slot == 0) {
            profile.setAnimationMode(NpcBehaviorProfile.ANIM_DEFAULT);
            persistAndRefresh(state, profile);
            DCMessage.sendGeneral(player, "Animation Mode set to DEFAULT.");
            openTarget(player, state.targetType, state.targetId, "ANIMS", state.category, state.page);
            return;
        }
        if (slot == 1) {
            if (rightClick) {
                int cur = profile.getSmartCycleSeconds();
                int next = cur <= 8 ? 12 : (cur <= 12 ? 20 : 8);
                profile.setSmartCycleSeconds(next);
                persistAndRefresh(state, profile);
                DCMessage.sendInfo(player, "Smart Ambient cycle interval set to " + next + "s.");
            } else {
                profile.setAnimationMode(NpcBehaviorProfile.ANIM_SMART);
                persistAndRefresh(state, profile);
                DCMessage.sendGeneral(player, "Enabled Smart Ambient Mode.");
            }
            openTarget(player, state.targetType, state.targetId, "ANIMS", state.category, state.page);
            return;
        }
        if (slot == 2) {
            if (rightClick) {
                int cur = profile.getPatrolRestSeconds();
                int next = cur <= 30 ? 60 : (cur <= 60 ? 180 : (cur <= 180 ? 300 : 30));
                profile.setPatrolRestSeconds(next);
                persistAndRefresh(state, profile);
                DCMessage.sendInfo(player, "Patrol rest cooldown set to " + next + "s.");
                openTarget(player, state.targetType, state.targetId, "ANIMS", state.category, state.page);
            } else if (profile.getPatrolWaypoints().size() < 2) {
                if (behaviorController != null) {
                    behaviorController.startRecording(player, state.targetType, state.targetId, resolveAnchor(state));
                }
            } else {
                profile.setAnimationMode(NpcBehaviorProfile.ANIM_SMART_PATROL);
                persistAndRefresh(state, profile);
                DCMessage.sendGeneral(player, "Enabled Smart Mode + Custom Patrol Path (" + profile.getPatrolWaypoints().size() + " pts).");
                openTarget(player, state.targetType, state.targetId, "ANIMS", state.category, state.page);
            }
            return;
        }
        if (slot == 4) {
            if (behaviorController != null) {
                behaviorController.startRecording(player, state.targetType, state.targetId, resolveAnchor(state));
            }
            return;
        }
        if (slot == 5) {
            profile.setPatrolWaypoints(Collections.emptyList());
            if (NpcBehaviorProfile.ANIM_SMART_PATROL.equals(profile.getAnimationMode())) {
                profile.setAnimationMode(NpcBehaviorProfile.ANIM_SMART);
            }
            persistAndRefresh(state, profile);
            DCMessage.sendWarning(player, "Cleared patrol waypoints.");
            openTarget(player, state.targetType, state.targetId, "ANIMS", state.category, state.page);
            return;
        }
        if (slot == 48 && state.page > 0) {
            openTarget(player, state.targetType, state.targetId, "ANIMS", state.category, state.page - 1);
            return;
        }
        if (slot == 50) {
            openTarget(player, state.targetType, state.targetId, "ANIMS", state.category, state.page + 1);
            return;
        }

        if (slot >= 9 && slot <= 44 && sceneCatalog != null) {
            SceneCatalog.SceneModelInfo info = sceneCatalog.getModel(profile.getModelId());
            if (info == null) return;
            int idx = state.page * 36 + (slot - 9);
            if (idx < 0 || idx >= info.allAnimations.size()) return;
            String anim = info.allAnimations.get(idx);

            profile.setAnimationMode(NpcBehaviorProfile.ANIM_FIXED);
            profile.setFixedAnimation(anim);
            persistAndRefresh(state, profile);
            DCMessage.sendGeneral(player, "Locked fixed animation: §f" + anim);
            openTarget(player, state.targetType, state.targetId, "ANIMS", state.category, state.page);
        }
    }

    private void persistAndRefresh(ViewState state, NpcBehaviorProfile profile) {
        if ("DECO".equals(state.targetType) && decorationManager != null) {
            DecorationNpc deco = decorationManager.getDecoration(state.targetId);
            if (deco != null) {
                decorationManager.save();
                for (NPC npc : decorationManager.findInstances(deco.getId())) {
                    if (npc.isSpawned() && npc.getEntity() != null) {
                        decorationManager.applyDecorationVisuals(npc, deco, npc.getEntity().getWorld().getName());
                    }
                }
            }
            if (behaviorController != null) behaviorController.resetStatesForTag(state.targetId, true);
        } else {
            NpcCharacter c = registry.getCharacter(state.targetId);
            if (c != null) {
                c.setEngineType(profile.getEngineType());
                c.setModelId(profile.getModelId());
                registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
                if (zoneManager != null && canonManager != null) {
                    for (NPC npc : zoneManager.findAllCitizensNpc(c.getId())) {
                        if (npc.isSpawned()) {
                            canonManager.applyEngineMode(npc, c);
                        }
                    }
                }
            }
            if (behaviorController != null) behaviorController.resetStatesForTag(state.targetId, false);
        }
    }

    private NpcBehaviorProfile resolveProfile(String targetType, String targetId) {
        if ("DECO".equals(targetType) && decorationManager != null) {
            DecorationNpc deco = decorationManager.getDecoration(targetId);
            return deco != null ? deco.getProfile() : null;
        }
        NpcCharacter c = registry.getCharacter(targetId);
        return c != null ? c.getBehaviorProfile() : null;
    }

    private String resolveDisplayTitle(String targetType, String targetId) {
        if ("DECO".equals(targetType) && decorationManager != null) {
            DecorationNpc d = decorationManager.getDecoration(targetId);
            return d != null ? d.getId() : targetId;
        }
        NpcCharacter c = registry.getCharacter(targetId);
        return c != null ? c.getShortName() : targetId;
    }

    private Location resolveAnchor(ViewState state) {
        if ("DECO".equals(state.targetType) && decorationManager != null) {
            DecorationNpc d = decorationManager.getDecoration(state.targetId);
            return d != null ? d.getAnchorLocation() : null;
        }
        return canonManager != null ? canonManager.getLiveLocation(state.targetId) : null;
    }

    private void openParentEditor(Player player, ViewState state) {
        if ("DECO".equals(state.targetType) && decorationEditorGUI != null) {
            decorationEditorGUI.open(player, state.targetId);
        } else if (npcEditorGUI != null) {
            npcEditorGUI.open(player, state.targetId);
        } else {
            player.closeInventory();
        }
    }

    private static String trimTitle(String s, int max) {
        String clean = org.bukkit.ChatColor.stripColor(DragoncoteColor.colorize(s));
        return clean.length() > max ? clean.substring(0, max) : clean;
    }

    private static ItemStack makeItem(Material mat, String name, String... lore) {
        return makeItemList(mat, name, lore.length == 0 ? null : Arrays.asList(lore));
    }

    private static ItemStack makeItemList(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) { sessions.remove(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { sessions.remove(event.getPlayer().getUniqueId()); }
}
