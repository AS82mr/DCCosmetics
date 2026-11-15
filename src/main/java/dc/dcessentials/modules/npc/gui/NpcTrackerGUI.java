package dc.dcessentials.modules.npc.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.npc.NpcCanonManager;
import dc.dcessentials.modules.npc.NpcRegistry;
import dc.dcessentials.modules.npc.NpcZoneManager;
import dc.dcessentials.modules.npc.model.NpcCharacter;
import dc.dcessentials.modules.npc.model.NpcZone;
import net.citizensnpcs.api.CitizensAPI;
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
 * Two-Level Sector & District Zone Editor GUI.
 *
 * Level 1: Sector / District Directory
 * - Left-Click Zone: Teleport directly to zone warp anchor.
 * - Right-Click Zone: Calibrate zone warp anchor to player's current location.
 * - Shift-Click / Middle-Click: Open District Personnel Sub-Menu.
 *
 * Level 2: District Sub-Menu (e.g. "Market District" or "The Tech Forge")
 * - Lists all NPCs assigned to this zone.
 * - Left-Click NPC: Teleport directly to that NPC in-world.
 * - Right-Click NPC: Open that character's 54-slot visual Dossier & Stage Editor.
 * - Shift-Click NPC: Recalibrate NPC live anchor to player's current position.
 */
public class NpcTrackerGUI implements Listener {

    private final NpcZoneManager zoneManager;
    private final NpcRegistry registry;
    private NpcCanonManager canonManager;
    private NpcEditorGUI npcEditorGUI;
    private DecorationListGUI decorationListGUI;

    private final Map<UUID, String> viewingDistrict = new HashMap<>();
    private final Map<UUID, Inventory> activeDistrictInventories = new HashMap<>();

    public NpcTrackerGUI(NpcZoneManager zoneManager, NpcRegistry registry) {
        this.zoneManager = zoneManager;
        this.registry = registry;
    }

    public void setManagers(NpcCanonManager canonManager, NpcEditorGUI npcEditorGUI) {
        this.canonManager = canonManager;
        this.npcEditorGUI = npcEditorGUI;
    }

    public void setDecorationListGUI(DecorationListGUI decorationListGUI) {
        this.decorationListGUI = decorationListGUI;
    }

    // ==============================================================
    // LEVEL 1: SECTOR & DISTRICT DIRECTORY
    // ==============================================================
    public void open(Player player) {
        viewingDistrict.remove(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, "§8[Sector Editor] §bDistricts & Zones");

        int slot = 0;
        int mappedCount = 0;
        int totalMissingNpcs = 0;

        for (NpcZone zone : zoneManager.getAllZones()) {
            if (slot >= 45) break;

            ItemStack item = new ItemStack(zone.getIcon() != null ? zone.getIcon() : Material.COMPASS);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                boolean isMapped = zone.isMapped();
                if (isMapped) mappedCount++;

                meta.setDisplayName((isMapped ? "§a§l✔ " : "§c§l✖ ") + "§b§l" + zone.getDisplayName());

                List<String> lore = new ArrayList<>();
                lore.add("§8District ID: " + zone.getId());
                lore.add("§7Warp Anchor: " + (zone.getWarpLocation() != null ?
                        "§f" + zone.getWarpLocation().getWorld().getName() + " (" +
                                zone.getWarpLocation().getBlockX() + ", " +
                                zone.getWarpLocation().getBlockY() + ", " +
                                zone.getWarpLocation().getBlockZ() + ")" :
                        (zone.getWarpCommand() != null ? "§e/" + zone.getWarpCommand() : "§cNot Configured")));
                lore.add("§f");
                lore.add("§6§lBound Personnel (" + zone.getBoundNpcIds().size() + "):");

                for (String charId : zone.getBoundNpcIds()) {
                    NpcCharacter c = registry.getCharacter(charId);
                    String charName = c != null ? c.getShortName() : charId;

                    NPC citizensNpc = zoneManager.findCitizensNpc(charId, null);
                    boolean isAnchored = citizensNpc != null && citizensNpc.isSpawned() && citizensNpc.getEntity() != null;

                    if (isAnchored) {
                        String world = citizensNpc.getEntity().getWorld().getName();
                        lore.add("  §a✔ §f" + charName + " §8[@ " + world + "]");
                    } else {
                        totalMissingNpcs++;
                        lore.add("  §c✖ §f" + charName + " §7(Not Live / Missing)");
                    }
                }

                lore.add("§f");
                lore.add("§a▶ Left-Click: §fTeleport to District Warp");
                lore.add("§6▶ Right-Click: §fSet District Warp to Current Location");
                lore.add("§e▶ Shift-Click: §fOpen District Sub-Menu & Edit NPCs");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            inv.setItem(slot++, item);
        }

        // Bottom Bar
        ItemStack wandItem = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta wandMeta = wandItem.getItemMeta();
        if (wandMeta != null) {
            wandMeta.setDisplayName("§6§l[★] Get Setup Wand");
            List<String> wLore = new ArrayList<>();
            wLore.add("§7Click to receive the Administrator Setup Wand.");
            wandMeta.setLore(wLore);
            wandItem.setItemMeta(wandMeta);
        }
        inv.setItem(45, wandItem);

        ItemStack summary = new ItemStack(Material.NETHER_STAR);
        ItemMeta sumMeta = summary.getItemMeta();
        if (sumMeta != null) {
            sumMeta.setDisplayName("§b§lSanctuary Network Status");
            List<String> sLore = new ArrayList<>();
            sLore.add("§7Total Districts: §f" + zoneManager.getAllZones().size());
            sLore.add("§7Mapped Warp Anchors: §a" + mappedCount);
            sLore.add("§7Unanchored Personnel: " + (totalMissingNpcs > 0 ? "§c" + totalMissingNpcs : "§a0 (Fully Synchronized)"));
            sumMeta.setLore(sLore);
            summary.setItemMeta(sumMeta);
        }
        inv.setItem(49, summary);

        ItemStack decoBtn = new ItemStack(Material.FLOWER_POT);
        ItemMeta dMeta = decoBtn.getItemMeta();
        if (dMeta != null) {
            dMeta.setDisplayName("§a§lDecoration NPCs (Out-of-Storyline)");
            List<String> dLore = new ArrayList<>();
            dLore.add("§7Browse, spawn, animate, and customize");
            dLore.add("§7ambient non-storyline DCNPCs.");
            dLore.add("§f");
            dLore.add("§e▶ Click to open Decoration Catalog");
            dMeta.setLore(dLore);
            decoBtn.setItemMeta(dMeta);
        }
        inv.setItem(51, decoBtn);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = close.getItemMeta();
        if (cMeta != null) {
            cMeta.setDisplayName("§c§lClose Menu");
            close.setItemMeta(cMeta);
        }
        inv.setItem(53, close);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    // ==============================================================
    // LEVEL 2: DISTRICT SUB-MENU (NPC PERSONNEL EDITOR)
    // ==============================================================
    public void openDistrictSubMenu(Player player, String zoneId) {
        NpcZone zone = zoneManager.getZone(zoneId);
        if (zone == null) return;

        Inventory inv = Bukkit.createInventory(null, 54, "§8[District] §b" + zone.getDisplayName());

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

        // Slot 4: District Overview Header
        ItemStack head = new ItemStack(zone.getIcon() != null ? zone.getIcon() : Material.COMPASS);
        ItemMeta hMeta = head.getItemMeta();
        if (hMeta != null) {
            hMeta.setDisplayName("§b§l" + zone.getDisplayName() + " §7(District Editor)");
            List<String> lore = new ArrayList<>();
            lore.add("§8District ID: " + zone.getId());
            lore.add("§7Shortcut: §e/warp " + zone.getId());
            lore.add("§7Warp Anchor: " + (zone.getWarpLocation() != null ?
                    "§f" + zone.getWarpLocation().getWorld().getName() + " (" +
                            zone.getWarpLocation().getBlockX() + ", " +
                            zone.getWarpLocation().getBlockY() + ", " +
                            zone.getWarpLocation().getBlockZ() + ")" : "§cNot Configured"));
            lore.add("§7Assigned Characters: §e" + zone.getBoundNpcIds().size());
            hMeta.setLore(lore);
            head.setItemMeta(hMeta);
        }
        inv.setItem(4, head);

        // Map Bound NPCs to slots 10..16, 19..25, 28..34
        int[] slots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };

        int idx = 0;
        for (String charId : zone.getBoundNpcIds()) {
            if (idx >= slots.length) break;

            NpcCharacter c = registry.getCharacter(charId);
            ItemStack npcItem = new ItemStack(c != null && c.getIcon() != null ? c.getIcon() : Material.PLAYER_HEAD);
            ItemMeta nMeta = npcItem.getItemMeta();
            if (nMeta != null) {
                String shortName = c != null ? c.getShortName() : charId;
                String role = c != null ? c.getRole() : "Resident";
                nMeta.setDisplayName("§e§l" + shortName + " §7[" + role + "]");

                List<String> lore = new ArrayList<>();
                lore.add("§8Unique ID: " + charId);
                lore.add("§7Faction: " + (c != null ? registry.getFactionHex(c.getFaction()) + c.getFaction() : "Neutral"));

                NPC citizensNpc = zoneManager.findCitizensNpc(charId, null);
                boolean isAnchored = (citizensNpc != null && (citizensNpc.isSpawned() || citizensNpc.getStoredLocation() != null))
                        || (canonManager != null && canonManager.getLiveLocation(charId) != null);

                if (isAnchored) {
                    lore.add("§7Status: §a✔ Anchored in World");
                } else {
                    lore.add("§7Status: §c✖ Missing / Unplaced");
                }

                lore.add("§f");
                lore.add("§a▶ Left-Click: §fTeleport to NPC in-world");
                lore.add("§d▶ Right-Click: §fOpen Full Visual Editor");
                lore.add("§e▶ Shift-Click: §fRecalibrate Anchor to your position");
                nMeta.setLore(lore);
                npcItem.setItemMeta(nMeta);
            }
            inv.setItem(slots[idx++], npcItem);
        }

        // Bottom Controls
        // Slot 45: Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bMeta = back.getItemMeta();
        if (bMeta != null) {
            bMeta.setDisplayName("§c§l◀ Back to All Districts");
            back.setItemMeta(bMeta);
        }
        inv.setItem(45, back);

        // Slot 49: Set District Warp
        ItemStack setWarp = new ItemStack(Material.ENDER_EYE);
        ItemMeta swMeta = setWarp.getItemMeta();
        if (swMeta != null) {
            swMeta.setDisplayName("§6§lSet District Warp Anchor");
            List<String> swLore = new ArrayList<>();
            swLore.add("§7Click to set this district's warp coordinates");
            swLore.add("§7to your current in-game location & facing angle.");
            swMeta.setLore(swLore);
            setWarp.setItemMeta(swMeta);
        }
        inv.setItem(49, setWarp);

        // Slot 53: Warp to District
        ItemStack tpDistrict = new ItemStack(Material.COMPASS);
        ItemMeta tpMeta = tpDistrict.getItemMeta();
        if (tpMeta != null) {
            tpMeta.setDisplayName("§a§lTeleport to District Warp");
            tpDistrict.setItemMeta(tpMeta);
        }
        inv.setItem(53, tpDistrict);

        activeDistrictInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
        viewingDistrict.put(player.getUniqueId(), zone.getId());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);
    }

    private boolean hasAdminPerm(Player player) {
        return player.hasPermission("dccosmetics.admin") ||
               player.hasPermission("dragoncote.admin") ||
               player.hasPermission("dcessentials.admin") ||
               player.isOp();
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (event.getInventory().equals(activeDistrictInventories.get(uuid))) {
            viewingDistrict.remove(uuid);
            activeDistrictInventories.remove(uuid);
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        viewingDistrict.remove(uuid);
        activeDistrictInventories.remove(uuid);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        if (!title.startsWith("§8[Sector Editor]") && !title.startsWith("§8[District]")) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        // ==============================================================
        // LEVEL 1 CLICKS
        // ==============================================================
        if (title.startsWith("§8[Sector Editor]")) {
            if (slot == 51) {
                if (!hasAdminPerm(player)) {
                    DCMessage.sendError(player, "You do not have permission to manage Decoration NPCs.");
                    return;
                }
                if (decorationListGUI != null) {
                    decorationListGUI.open(player);
                }
                return;
            }

            if (slot == 53) {
                player.closeInventory();
                return;
            }

            if (slot == 45) {
                if (!hasAdminPerm(player)) {
                    DCMessage.sendError(player, "You do not have permission to equip the Setup Wand.");
                    return;
                }
                player.closeInventory();
                player.performCommand("dcnpc wand");
                return;
            }

            if (slot < 45) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || !clicked.hasItemMeta()) return;

                List<String> lore = clicked.getItemMeta().getLore();
                if (lore == null || lore.isEmpty()) return;

                String districtId = null;
                for (String line : lore) {
                    if (line.startsWith("§8District ID: ")) {
                        districtId = line.substring("§8District ID: ".length()).trim();
                        break;
                    }
                }

                if (districtId == null) return;
                NpcZone zone = zoneManager.getZone(districtId);
                if (zone == null) return;

                // Shift-Click: Open District Sub-Menu (Admin Only)
                if (event.isShiftClick()) {
                    if (!hasAdminPerm(player)) {
                        DCMessage.sendError(player, "You do not have permission to edit districts.");
                        return;
                    }
                    openDistrictSubMenu(player, districtId);
                    return;
                }

                // Right-Click: Set District Warp to player's current location (Admin Only)
                if (event.isRightClick()) {
                    if (!hasAdminPerm(player)) {
                        DCMessage.sendError(player, "You do not have permission to recalibrate warp anchors.");
                        return;
                    }
                    zoneManager.setZoneWarpLocation(districtId, player.getLocation());
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    DCMessage.sendWarp(player, "Set warp anchor for " + zone.getDisplayName() + " to your coordinates!");
                    open(player);
                    return;
                }

                // Left-Click: Teleport to District Warp
                player.closeInventory();
                zoneManager.warpPlayer(player, districtId);
            }
            return;
        }

        // ==============================================================
        // LEVEL 2 CLICKS
        // ==============================================================
        if (title.startsWith("§8[District]")) {
            if (!hasAdminPerm(player)) {
                player.closeInventory();
                return;
            }
            String zoneId = viewingDistrict.get(player.getUniqueId());
            if (zoneId == null) return;
            NpcZone zone = zoneManager.getZone(zoneId);

            // Slot 45: Back button
            if (slot == 45) {
                open(player);
                return;
            }

            // Slot 49: Set District Warp
            if (slot == 49 && zone != null) {
                zoneManager.setZoneWarpLocation(zoneId, player.getLocation());
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                DCMessage.sendWarp(player, "Set warp anchor for " + zone.getDisplayName() + " to your coordinates!");
                openDistrictSubMenu(player, zoneId);
                return;
            }

            // Slot 53: Warp to District
            if (slot == 53) {
                player.closeInventory();
                zoneManager.warpPlayer(player, zoneId);
                return;
            }

            // NPC items in center slots
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta() || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

            List<String> lore = clicked.getItemMeta().getLore();
            if (lore == null || lore.isEmpty()) return;

            String charId = null;
            for (String line : lore) {
                if (line.startsWith("§8Unique ID: ")) {
                    charId = line.substring("§8Unique ID: ".length()).trim();
                    break;
                }
            }

            if (charId == null) return;

            // Shift-Click: Recalibrate NPC anchor to player's location
            if (event.isShiftClick()) {
                if (canonManager != null) {
                    canonManager.setLiveLocation(charId, player.getLocation());
                    NPC npc = zoneManager.findCitizensNpc(charId, player.getWorld().getName());
                    if (npc != null && npc.isSpawned()) {
                        npc.teleport(player.getLocation(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                    }
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens save");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    DCMessage.sendGeneral(player, "Recalibrated live anchor for " + charId + " to your coordinates!");
                    openDistrictSubMenu(player, zoneId);
                }
                return;
            }

            // Right-Click: Open NPC Visual Editor
            if (event.isRightClick()) {
                if (npcEditorGUI != null) {
                    npcEditorGUI.open(player, charId);
                }
                return;
            }

            // Left-Click: Teleport to NPC in-world
            if (canonManager != null) {
                player.closeInventory();
                canonManager.teleportToNpc(player, charId);
            }
        }
    }
}
