package dc.dcessentials.modules.npc.gui;

import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.npc.behavior.NpcBehaviorController;
import dc.dcessentials.modules.npc.model.DecorationNpc;
import dc.dcessentials.modules.npc.model.NpcBehaviorProfile;
import dc.dcessentials.modules.npc.scene.DecorationManager;
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
 * 54-Slot Editor GUI for Out-of-Storyline Decoration DCNPCs.
 */
public class DecorationEditorGUI implements Listener {

    private static final String TITLE_PREFIX = "§8[Deco Editor] §a";

    private final DecorationManager decorationManager;
    private DecorationListGUI decorationListGUI;
    private NpcEngineSelectorGUI engineSelectorGUI;
    private SkinBakeGUI skinBakeGUI;
    private NpcBehaviorController behaviorController;

    private final Map<UUID, String> viewing = new HashMap<>();
    private final Map<UUID, Inventory> activeInventories = new HashMap<>();

    public DecorationEditorGUI(DecorationManager decorationManager) {
        this.decorationManager = decorationManager;
    }

    public void setDecorationListGUI(DecorationListGUI decorationListGUI) { this.decorationListGUI = decorationListGUI; }
    public void setEngineSelectorGUI(NpcEngineSelectorGUI engineSelectorGUI) { this.engineSelectorGUI = engineSelectorGUI; }
    public void setSkinBakeGUI(SkinBakeGUI skinBakeGUI) { this.skinBakeGUI = skinBakeGUI; }
    public void setBehaviorController(NpcBehaviorController behaviorController) { this.behaviorController = behaviorController; }

    public void open(Player player, String decoId) {
        DecorationNpc deco = decorationManager.getDecoration(decoId);
        if (deco == null) {
            DCMessage.sendError(player, "Decoration NPC '" + decoId + "' not found.");
            return;
        }
        NpcBehaviorProfile p = deco.getProfile();

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_PREFIX + deco.getId());
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, "§7");
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        Location loc = deco.getAnchorLocation();
        String locText = (loc != null && loc.getWorld() != null)
                ? (loc.getWorld().getName() + " (" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")")
                : "§cUnplaced";

        inv.setItem(4, makeItem(Material.FLOWER_POT,
                "§a§lDecoration NPC: §f" + deco.getId(),
                "§7Display Name: §f" + deco.getDisplayName(),
                "§7Type: §bOut-of-Storyline Decorative",
                "§7Anchor: §f" + locText,
                "§7Ambient Quotes: §f" + deco.getAmbientLines().size()
        ));

        inv.setItem(11, makeItem(
                p.isModelEngine() ? Material.ARMOR_STAND : Material.PLAYER_HEAD,
                "§5§lEngine, 3D Model & Smart Animations",
                "§7Engine: §d" + p.getEngineType(),
                "§7Model: §f" + p.getModelId(),
                "§7Baked Skin Model: §b" + (p.getBakedModelId() != null ? p.getBakedModelId() : "§8None"),
                "§7Anim Mode: §e" + p.getAnimationMode(),
                "§7Patrol Waypoints: §a" + p.getPatrolWaypoints().size(),
                "§f",
                "§e▶ Click to open 3D Scene & Animation Selector"
        ));

        inv.setItem(13, makeItem(
                Material.WRITABLE_BOOK,
                "§6§lAmbient Quotes (" + deco.getAmbientLines().size() + ")",
                "§7Left-Click: Test ambient quote on yourself",
                "§7Right-Click: Print quote commands in chat"
        ));

        inv.setItem(15, makeItem(
                deco.isShowNameplate() ? Material.NAME_TAG : Material.GRAY_DYE,
                "§b§lOverhead Nameplate: " + (deco.isShowNameplate() ? "§aVISIBLE" : "§7HIDDEN"),
                "§7Toggle whether this decoration NPC displays",
                "§7a hologram nameplate above its head.",
                "§f",
                "§e▶ Click to Toggle"
        ));

        inv.setItem(19, makeItem(
                Material.BLAZE_ROD,
                "§e§lRecord Custom Patrol Waypoints",
                "§7Recorded Waypoints: §f" + p.getPatrolWaypoints().size(),
                "§7Right-Click blocks safely along a route",
                "§7so the NPC never walks into walls.",
                "§f",
                "§a▶ Click to Start Recorder"
        ));

        inv.setItem(21, makeItem(
                Material.COMPASS,
                "§b§lMove Anchor to Your Position",
                "§7Relocates this decoration NPC (and any synced",
                "§7dimension copies) to your exact location & yaw.",
                "§f",
                "§e▶ Click to Move Anchor Here"
        ));

        inv.setItem(23, makeItem(
                Material.PLAYER_HEAD,
                "§d§lSkins & .bbmodel Baker",
                "§7Skin ID: §f" + deco.getSkinId(),
                "§7Revision: §b#r" + p.getSkinRevision(),
                "§f",
                "§e▶ Click to open Skin & .bbmodel Baker"
        ));

        inv.setItem(25, makeItem(
                Material.CLOCK,
                "§a§lFacing Rotation",
                "§7Left-Click: §f+15°",
                "§7Right-Click: §f-15°",
                "§7Shift-Click: §fFace You"
        ));

        inv.setItem(29, makeItem(
                deco.isSyncSpawnWorlds() ? Material.ENDER_EYE : Material.ENDER_PEARL,
                "§3§lSanctuary Multi-World Sync: " + (deco.isSyncSpawnWorlds() ? "§aENABLED" : "§7SINGLE WORLD"),
                "§7When enabled in a Spawn world, mirrors across",
                "§7spawn_main, spawn_snow, and spawn_bw automatically.",
                "§f",
                "§e▶ Click to Toggle"
        ));

        inv.setItem(31, makeItem(Material.SPECTRAL_ARROW,
                "§a§lTeleport to Decoration NPC",
                "§7Location: §f" + locText));

        inv.setItem(33, makeItem(Material.ANVIL,
                "§e§lRename Display Tag",
                "§7Current: §f" + deco.getDisplayName(),
                "§7Command: §f/dcnpc deco rename " + deco.getId() + " <newName>",
                "§e▶ Click to print rename command"));

        inv.setItem(40, makeItem(Material.TNT,
                "§c§lDelete Decoration NPC",
                "§7Permanently deletes Citizens entities & config.",
                "§f",
                "§c§l▶ Shift + Left-Click to Confirm Delete"));

        inv.setItem(45, makeItem(Material.ARROW, "§c§l◀ Back to Decoration List"));
        inv.setItem(53, makeItem(Material.BARRIER, "§c§lClose"));

        activeInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
        viewing.put(player.getUniqueId(), deco.getId());
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().startsWith(TITLE_PREFIX)) return;

        event.setCancelled(true);
        String decoId = viewing.get(player.getUniqueId());
        if (decoId == null) return;
        DecorationNpc deco = decorationManager.getDecoration(decoId);
        if (deco == null) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 53) {
            player.closeInventory();
            return;
        }
        if (slot == 45) {
            if (decorationListGUI != null) decorationListGUI.open(player);
            else player.closeInventory();
            return;
        }
        if (slot == 11) {
            if (engineSelectorGUI != null) engineSelectorGUI.openDeco(player, decoId);
            return;
        }
        if (slot == 13) {
            if (event.isRightClick()) {
                player.closeInventory();
                DCMessage.sendInfo(player, "Ambient Quote Commands:");
                player.sendMessage("§e• /dcnpc deco quote " + decoId + " <text>");
                player.sendMessage("§e• /dcnpc deco clearquotes " + decoId);
            } else {
                decorationManager.triggerAmbientDialogue(player, decoId);
            }
            return;
        }
        if (slot == 15) {
            deco.setShowNameplate(!deco.isShowNameplate());
            decorationManager.save();
            decorationManager.syncDecorationInstances(deco);
            open(player, decoId);
            return;
        }
        if (slot == 19) {
            if (behaviorController != null) {
                behaviorController.startRecording(player, "DECO", decoId, deco.getAnchorLocation());
            }
            return;
        }
        if (slot == 21) {
            deco.setAnchorLocation(player.getLocation());
            decorationManager.save();
            decorationManager.syncDecorationInstances(deco);
            DCMessage.sendGeneral(player, "Moved Decoration NPC §f" + decoId + " §7to your location.");
            open(player, decoId);
            return;
        }
        if (slot == 23) {
            if (skinBakeGUI != null) skinBakeGUI.openDeco(player, decoId);
            return;
        }
        if (slot == 25) {
            Location anchor = deco.getAnchorLocation();
            NPC live = decorationManager.findInstanceInWorld(decoId, player.getWorld().getName());
            Location base = (live != null && live.isSpawned() && live.getEntity() != null) ? live.getEntity().getLocation() : anchor;
            if (base != null) {
                float yaw;
                if (event.isShiftClick()) {
                    double dx = player.getLocation().getX() - base.getX();
                    double dz = player.getLocation().getZ() - base.getZ();
                    yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                } else {
                    yaw = base.getYaw() + (event.isRightClick() ? -15.0f : 15.0f);
                }
                if (anchor != null) {
                    anchor.setYaw(yaw);
                    deco.setAnchorLocation(anchor);
                }
                decorationManager.save();
                decorationManager.syncDecorationInstances(deco);
                open(player, decoId);
            }
            return;
        }
        if (slot == 29) {
            deco.setSyncSpawnWorlds(!deco.isSyncSpawnWorlds());
            decorationManager.save();
            decorationManager.syncDecorationInstances(deco);
            open(player, decoId);
            return;
        }
        if (slot == 31) {
            if (deco.getAnchorLocation() != null) {
                player.closeInventory();
                player.teleport(deco.getAnchorLocation());
            }
            return;
        }
        if (slot == 33) {
            player.closeInventory();
            DCMessage.sendInfo(player, "Run: §f/dcnpc deco rename " + decoId + " <displayName>");
            return;
        }
        if (slot == 40 && event.isShiftClick()) {
            decorationManager.deleteDecoration(decoId);
            if (behaviorController != null) behaviorController.resetStatesForTag(decoId, true);
            DCMessage.sendWarning(player, "Deleted Decoration NPC §f" + decoId);
            if (decorationListGUI != null) decorationListGUI.open(player);
            else player.closeInventory();
        }
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
            viewing.remove(uuid);
            activeInventories.remove(uuid);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        viewing.remove(uuid);
        activeInventories.remove(uuid);
    }
}
