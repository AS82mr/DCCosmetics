package dc.dcessentials.modules.npc.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.npc.NpcZoneManager;
import dc.dcessentials.modules.npc.model.NpcZone;
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
 * Fast Travel Navigation GUI for Sanctuary & LowerCity Multiverse Warps.
 * Supports /warp, /warps, interactive clicking, setting warps for admins,
 * and seamless district jumping.
 */
public class WarpMenuGUI implements Listener {

    private final NpcZoneManager zoneManager;
    private NpcTrackerGUI trackerGUI;
    private NpcEditorGUI npcEditorGUI;
    private NpcSingleStageGUI singleStageGUI;

    public static class BindTarget {
        public final String charId;
        public final Integer stageNum;
        public BindTarget(String charId, Integer stageNum) {
            this.charId = charId;
            this.stageNum = stageNum;
        }
    }

    private final Map<UUID, BindTarget> bindTargets = new HashMap<>();

    public WarpMenuGUI(NpcZoneManager zoneManager) {
        this.zoneManager = zoneManager;
    }

    public void setTrackerGUI(NpcTrackerGUI trackerGUI) {
        this.trackerGUI = trackerGUI;
    }

    public void setNpcEditorGUI(NpcEditorGUI npcEditorGUI) {
        this.npcEditorGUI = npcEditorGUI;
    }

    public void setSingleStageGUI(NpcSingleStageGUI singleStageGUI) {
        this.singleStageGUI = singleStageGUI;
    }

    public void openForBinding(Player player, String charId, Integer stageNum) {
        bindTargets.put(player.getUniqueId(), new BindTarget(charId.toLowerCase(), stageNum));
        openInternal(player, "§8Bind Warp: §f" + charId.toUpperCase());
    }

    public void open(Player player) {
        bindTargets.remove(player.getUniqueId());
        openInternal(player, "§8Fast Travel");
    }

    private void openInternal(Player player, String title) {
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Top border
        ItemStack cyanPane = createPane(Material.CYAN_STAINED_GLASS_PANE, "§7");
        for (int i : new int[]{0, 1, 2, 3, 5, 6, 7, 8}) {
            inv.setItem(i, cyanPane);
        }

        // Bottom border
        ItemStack bluePane = createPane(Material.BLUE_STAINED_GLASS_PANE, "§7");
        for (int i : new int[]{46, 47, 48, 50, 51, 52}) {
            inv.setItem(i, bluePane);
        }

        // Slot 4: Header
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta sMeta = star.getItemMeta();
        if (sMeta != null) {
            sMeta.setDisplayName("§b§lSanctuary Multiverse Navigation Network");
            List<String> lore = new ArrayList<>();
            lore.add("§7Instant quantum routing across all registered districts.");
            lore.add("§7Click any destination below to fast travel immediately.");
            lore.add("§f");
            lore.add("§7Total Districts: §f" + zoneManager.getAllZones().size());
            sMeta.setLore(lore);
            star.setItemMeta(sMeta);
        }
        inv.setItem(4, star);

        // Predefined layout map for beautiful categorisation
        Map<Integer, String> layout = new LinkedHashMap<>();
        // Sector 1: Sanctuary Hub
        layout.put(10, "command");
        layout.put(11, "forge");
        layout.put(12, "market");
        layout.put(13, "auction");
        layout.put(14, "bank");
        layout.put(15, "bakery");
        layout.put(16, "archives");
        layout.put(19, "north_gate");
        layout.put(20, "observatory");
        layout.put(21, "colosseum");
        layout.put(22, "fisherman");
        layout.put(23, "stylist");
        layout.put(24, "mines");
        layout.put(25, "farms");
        layout.put(28, "ruins");
        // Sector 2: LowerCity
        layout.put(30, "slums");
        layout.put(31, "casino");
        layout.put(32, "blackmarket");
        layout.put(33, "citadel");
        layout.put(34, "spire");

        boolean isAdmin = player.hasPermission("dccosmetics.admin") || player.isOp();

        for (Map.Entry<Integer, String> entry : layout.entrySet()) {
            int slot = entry.getKey();
            String zoneId = entry.getValue();
            NpcZone zone = zoneManager.getZone(zoneId);
            if (zone == null) continue;

            ItemStack item = new ItemStack(zone.getIcon() != null ? zone.getIcon() : Material.COMPASS);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§b§l" + zone.getDisplayName());
                List<String> lore = new ArrayList<>();
                lore.add("§8District ID: " + zone.getId());
                lore.add("§7Shortcut: §e/warp " + zone.getId() + (zone.getAliases().isEmpty() ? "" : " §8(or /warp " + zone.getAliases().get(0) + ")"));

                if (zone.getWarpLocation() != null) {
                    lore.add("§7Status: §a✔ Calibrated & Online");
                    lore.add("§8Coordinates: " + zone.getWarpLocation().getWorld().getName() + " (" +
                            zone.getWarpLocation().getBlockX() + ", " +
                            zone.getWarpLocation().getBlockY() + ", " +
                            zone.getWarpLocation().getBlockZ() + ")");
                } else {
                    lore.add("§7Status: §e⚡ Fallback Route (/" + zone.getWarpCommand() + ")");
                }

                lore.add("§7Bound NPCs: §f" + zone.getBoundNpcIds().size() + " characters");
                lore.add("§f");
                lore.add("§a▶ Left-Click to Warp!");
                if (isAdmin) {
                    lore.add("§6▶ Right-Click to Set Warp to Your Location");
                    lore.add("§e▶ Shift-Click to Open District NPC Manager");
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
        }

        // Slot 45: Setup Wand
        if (isAdmin) {
            ItemStack wandItem = new ItemStack(Material.GOLDEN_HOE);
            ItemMeta wandMeta = wandItem.getItemMeta();
            if (wandMeta != null) {
                wandMeta.setDisplayName("§6§l[★] Get Setup Wand");
                wandItem.setItemMeta(wandMeta);
            }
            inv.setItem(45, wandItem);
        }

        // Slot 49: Close
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = close.getItemMeta();
        if (cMeta != null) {
            cMeta.setDisplayName("§c§lClose Navigation");
            close.setItemMeta(cMeta);
        }
        inv.setItem(49, close);

        // Slot 53: District Tracker
        if (isAdmin) {
            ItemStack tracker = new ItemStack(Material.COMPASS);
            ItemMeta tMeta = tracker.getItemMeta();
            if (tMeta != null) {
                tMeta.setDisplayName("§e§lDistrict & Sector Editor");
                List<String> tLore = new ArrayList<>();
                tLore.add("§7Click to open full administrative District Manager.");
                tMeta.setLore(tLore);
                tracker.setItemMeta(tMeta);
            }
            inv.setItem(53, tracker);
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.8f, 1.4f);
    }

    private ItemStack createPane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        boolean isBinding = title.startsWith("§8Bind Warp");
        if (!title.equals("§8Fast Travel") && !isBinding) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 49) {
            player.closeInventory();
            return;
        }

        if (slot == 45) {
            player.closeInventory();
            player.performCommand("dcnpc wand");
            return;
        }

        if (slot == 53) {
            if (trackerGUI != null) {
                trackerGUI.open(player);
            } else {
                player.closeInventory();
            }
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        List<String> lore = clicked.getItemMeta().getLore();
        if (lore == null || lore.isEmpty()) return;

        String zoneId = null;
        for (String line : lore) {
            if (line.startsWith("§8District ID: ")) {
                zoneId = line.substring("§8District ID: ".length()).trim();
                break;
            }
        }

        if (zoneId == null) return;
        NpcZone zone = zoneManager.getZone(zoneId);
        if (zone == null) return;

        if (isBinding) {
            BindTarget bt = bindTargets.remove(player.getUniqueId());
            if (bt != null) {
                dc.dcessentials.modules.npc.model.NpcCharacter c = DCCosmetics.getInstance().getNpcRegistry().getCharacter(bt.charId);
                if (c != null) {
                    if (bt.stageNum != null) {
                        dc.dcessentials.modules.npc.model.NpcStage stg = c.getStage(bt.stageNum);
                        if (stg != null) stg.setBoundWarpAlias(zone.getId());
                    } else {
                        c.setBoundWarpAlias(zone.getId());
                    }
                    DCCosmetics.getInstance().getNpcRegistry().saveCustomStages(DCCosmetics.getInstance().getDataFolder());
                    player.sendMessage(dc.dcessentials.modules.npc.util.DragoncoteColor.PREFIX_SUCCESS + "Bound " + c.getShortName() + " to warp zone §b" + zone.getDisplayName() + " §7(/warp " + zone.getId() + ")!");
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
                    if (bt.stageNum != null && singleStageGUI != null) {
                        singleStageGUI.open(player, bt.charId, bt.stageNum);
                    } else if (npcEditorGUI != null) {
                        npcEditorGUI.open(player, bt.charId);
                    }
                    return;
                }
            }
        }

        boolean isAdmin = player.hasPermission("dccosmetics.admin") || player.isOp();

        // Shift-Click: Open District Sub-Menu in Tracker
        if (isAdmin && event.isShiftClick()) {
            if (trackerGUI != null) {
                trackerGUI.openDistrictSubMenu(player, zoneId);
            }
            return;
        }

        // Right-Click (Admin): Set Warp to Player Location
        if (isAdmin && event.isRightClick()) {
            zoneManager.setZoneWarpLocation(zoneId, player.getLocation());
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            DCMessage.sendWarp(player, "Set warp anchor for " + zone.getDisplayName() + " to your coordinates!");
            open(player);
            return;
        }

        // Left-Click: Warp
        player.closeInventory();
        zoneManager.warpPlayer(player, zoneId);
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        bindTargets.remove(event.getPlayer().getUniqueId());
    }
}
