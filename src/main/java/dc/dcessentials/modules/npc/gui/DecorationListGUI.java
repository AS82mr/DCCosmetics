package dc.dcessentials.modules.npc.gui;

import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.npc.model.DecorationNpc;
import dc.dcessentials.modules.npc.model.NpcBehaviorProfile;
import dc.dcessentials.modules.npc.scene.DecorationManager;
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
 * Paginated Catalog of Out-of-Storyline Decoration DCNPCs.
 */
public class DecorationListGUI implements Listener {

    private static final String TITLE_PREFIX = "§8[Decoration NPCs] ";

    private final DecorationManager decorationManager;
    private DecorationEditorGUI decorationEditorGUI;
    private NpcTrackerGUI trackerGUI;

    private final Map<UUID, Integer> playerPage = new HashMap<>();
    private final Map<UUID, Inventory> activeInventories = new HashMap<>();

    public DecorationListGUI(DecorationManager decorationManager) {
        this.decorationManager = decorationManager;
    }

    public void setDecorationEditorGUI(DecorationEditorGUI decorationEditorGUI) { this.decorationEditorGUI = decorationEditorGUI; }
    public void setTrackerGUI(NpcTrackerGUI trackerGUI) { this.trackerGUI = trackerGUI; }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        List<DecorationNpc> list = new ArrayList<>(decorationManager.getAllDecorations());
        int perPage = 45;
        int totalPages = Math.max(1, (int) Math.ceil(list.size() / (double) perPage));
        int curPage = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_PREFIX + "§aPage " + (curPage + 1) + "/" + totalPages);
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, "§7");
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        int start = curPage * perPage;
        int end = Math.min(list.size(), start + perPage);
        int slot = 0;
        for (int i = start; i < end; i++) {
            DecorationNpc deco = list.get(i);
            NpcBehaviorProfile p = deco.getProfile();
            Location loc = deco.getAnchorLocation();
            String locStr = (loc != null && loc.getWorld() != null)
                    ? (loc.getWorld().getName() + " (" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")")
                    : "§cUnplaced";
            Material mat = p.isModelEngine() ? Material.ARMOR_STAND : Material.PLAYER_HEAD;
            inv.setItem(slot++, makeItem(mat,
                    "§a§l" + deco.getId(),
                    "§8DecoID:" + deco.getId(),
                    "§7Engine: §d" + p.getEngineType() + (p.isModelEngine() ? " §8(" + p.getModelId() + ")" : ""),
                    "§7Anim Mode: §e" + p.getAnimationMode() + " §8(" + p.getPatrolWaypoints().size() + " wps)",
                    "§7Sync Worlds: " + (deco.isSyncSpawnWorlds() ? "§a✔ Main/Snow/BW" : "§7Single World"),
                    "§7Location: §f" + locStr,
                    "§f",
                    "§e▶ Left-Click: Edit Decoration NPC",
                    "§b▶ Right-Click: Teleport to NPC"
            ));
        }

        if (list.isEmpty()) {
            inv.setItem(22, makeItem(Material.FLOWER_POT,
                    "§e§lNo Decoration NPCs Yet",
                    "§7Decoration NPCs share all DCNPC visuals, skins,",
                    "§7Scene animations & patrols WITHOUT quests or stages.",
                    "§f",
                    "§a▶ Click the Emerald below to spawn your first one!"));
        }

        inv.setItem(45, makeItem(Material.ARROW, "§c§l◀ Back to District Tracker"));
        inv.setItem(47, makeItem(Material.EMERALD,
                "§a§l[+] Create Decoration NPC Here",
                "§7Spawns a new out-of-storyline Decoration NPC",
                "§7at your current position & opens its Editor.",
                "§7Or run: §f/dcnpc deco create <id> [name]"));
        if (curPage > 0) inv.setItem(48, makeItem(Material.SPECTRAL_ARROW, "§e◀ Previous Page"));
        inv.setItem(49, makeItem(Material.NETHER_STAR,
                "§b§lDecoration Pool Summary",
                "§7Total Decorative NPCs: §a" + list.size(),
                "§7Zero storyline quests/stages attached."));
        if (curPage + 1 < totalPages) inv.setItem(50, makeItem(Material.SPECTRAL_ARROW, "§eNext Page ▶"));
        inv.setItem(53, makeItem(Material.BARRIER, "§c§lClose"));

        activeInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
        playerPage.put(player.getUniqueId(), curPage);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().startsWith(TITLE_PREFIX)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        int page = playerPage.getOrDefault(player.getUniqueId(), 0);
        if (slot == 53) {
            player.closeInventory();
            return;
        }
        if (slot == 45) {
            if (trackerGUI != null) trackerGUI.open(player);
            else player.closeInventory();
            return;
        }
        if (slot == 47) {
            String autoId = "deco_" + (decorationManager.getAllDecorations().size() + 1);
            DecorationNpc created = decorationManager.createDecoration(autoId, "<#E2F6A8>" + autoId, player.getLocation());
            DCMessage.sendGeneral(player, "Created Decoration NPC §f" + created.getId() + " §7at your location.");
            if (decorationEditorGUI != null) decorationEditorGUI.open(player, created.getId());
            else open(player, page);
            return;
        }
        if (slot == 48 && page > 0) {
            open(player, page - 1);
            return;
        }
        if (slot == 50) {
            open(player, page + 1);
            return;
        }

        if (slot < 45) {
            ItemStack item = event.getCurrentItem();
            if (item == null || !item.hasItemMeta() || item.getItemMeta().getLore() == null) return;
            String decoId = null;
            for (String line : item.getItemMeta().getLore()) {
                if (line.startsWith("§8DecoID:")) {
                    decoId = line.substring("§8DecoID:".length()).trim();
                    break;
                }
            }
            if (decoId == null) return;
            DecorationNpc deco = decorationManager.getDecoration(decoId);
            if (deco == null) return;

            if (event.isRightClick()) {
                if (deco.getAnchorLocation() != null) {
                    player.closeInventory();
                    player.teleport(deco.getAnchorLocation());
                    DCMessage.sendInfo(player, "Teleported to Decoration NPC §f" + deco.getId());
                } else {
                    DCMessage.sendError(player, "Decoration NPC has no anchor yet.");
                }
            } else if (decorationEditorGUI != null) {
                decorationEditorGUI.open(player, deco.getId());
            }
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
            playerPage.remove(uuid);
            activeInventories.remove(uuid);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerPage.remove(uuid);
        activeInventories.remove(uuid);
    }
}
