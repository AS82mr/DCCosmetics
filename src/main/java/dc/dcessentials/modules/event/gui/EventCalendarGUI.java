package dc.dcessentials.modules.event.gui;

import dc.dcessentials.modules.event.EventManager;
import dc.dcessentials.modules.event.model.ServerEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
 * 54-Slot Native Calendar GUI for Dragoncote Server Events.
 * Page 1: Active Realm (World Cycle) & Running Events.
 * Page 2: Upcoming Schedule & Dimensional Timeline.
 */
public class EventCalendarGUI implements Listener {

    private final EventManager eventManager;
    private final Map<UUID, Integer> playerPages = new HashMap<>();

    public EventCalendarGUI(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    public void open(Player player, int page) {
        playerPages.put(player.getUniqueId(), page);
        String title = (page == 1) ? "§8Server Events: §6Active Realm" : "§8Server Events: §bUpcoming Schedule";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        fillBorders(inv, page);

        if (page == 1) {
            renderActivePage(inv, player);
        } else {
            renderSchedulePage(inv, player);
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
    }

    private void fillBorders(Inventory inv, int page) {
        ItemStack blackGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        ItemStack cyanGlass = createItem(Material.CYAN_STAINED_GLASS_PANE, " ", null);
        ItemStack goldGlass = createItem(Material.ORANGE_STAINED_GLASS_PANE, " ", null);

        // Top & bottom rows
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, blackGlass);
            inv.setItem(i + 45, blackGlass);
        }
        // Column borders
        for (int i = 0; i < 6; i++) {
            inv.setItem(i * 9, (i % 2 == 0) ? cyanGlass : blackGlass);
            inv.setItem(i * 9 + 8, (i % 2 == 0) ? cyanGlass : blackGlass);
        }

        // Bottom Controls
        inv.setItem(49, createItem(Material.BARRIER, "§c§lClose Calendar", Collections.singletonList("§7Click to exit menu")));

        if (page == 1) {
            inv.setItem(50, createItem(Material.ARROW, "§a§lUpcoming Schedule ➔",
                    Arrays.asList("§7View the upcoming dimensional shifts", "§7and scheduled mini-events.", "", "§e▶ Click to view Page 2")));
            inv.setItem(40, createItem(Material.BEACON, "§e§lWarp to Active Realm §f(/spawn)",
                    Arrays.asList("§7Instant teleport to the currently active", "§7world cycle dimension: §6" + eventManager.getActiveSpawnWorld(), "", "§e▶ Click to fast-travel now")));
        } else {
            inv.setItem(48, createItem(Material.ARROW, "§a§l⬅ Active Events & Realm",
                    Arrays.asList("§7Return to active realm status", "§7and running mini-events.", "", "§e▶ Click to view Page 1")));
            inv.setItem(40, createItem(Material.COMPASS, "§e§lCurrent Realm: §6" + eventManager.getActiveSpawnWorld(),
                    Arrays.asList("§7Time Remaining: §e" + eventManager.getFormattedCycleTimeRemaining(), "", "§e▶ Click to warp to realm")));
        }
    }

    private void renderActivePage(Inventory inv, Player player) {
        ServerEvent cycle = eventManager.getCurrentCycleEvent();

        // Slot 4: Active World Cycle Crown Banner
        if (cycle != null) {
            List<String> cycleLore = new ArrayList<>();
            cycleLore.add("§8Dimension: §f" + cycle.getWorldName());
            cycleLore.add("§8Status: " + (eventManager.isEvacuating() ? "§c§lEVACUATING (§e" + eventManager.getCycleSecondsRemaining() + "s§c)" : "§a§lACTIVE REALM"));
            cycleLore.add("§8Time Remaining: §e" + eventManager.getFormattedCycleTimeRemaining());
            cycleLore.add(" ");
            cycleLore.add("§6§lDimensional Perks:");
            for (String perk : cycle.getPerks()) {
                cycleLore.add(ChatColor.translateAlternateColorCodes('&', perk));
            }
            cycleLore.add(" ");
            for (String l : cycle.getLore()) {
                cycleLore.add(ChatColor.translateAlternateColorCodes('&', l));
            }
            cycleLore.add(" ");
            cycleLore.add("§e▶ Click to warp directly to this realm!");

            inv.setItem(4, createItem(cycle.getIcon(), ChatColor.translateAlternateColorCodes('&', cycle.getTitle()), cycleLore, cycle.getCustomModelData()));
        }

        // Slots 19, 21, 23, 25: Running / Scheduled Events
        int[] eventSlots = new int[]{20, 22, 24};
        List<ServerEvent> minis = new ArrayList<>(eventManager.getMiniEvents().values());

        for (int i = 0; i < eventSlots.length && i < minis.size(); i++) {
            ServerEvent me = minis.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("§8Category: §fMini-Event");
            lore.add("§8Status: " + (me.isRunning() ? "§a§lACTIVE NOW" : "§7Waiting"));
            lore.add(me.isRunning() ? "§8Time Left: §e" + me.getFormattedRemainingTime() : "§8Starts in: §e" + me.getFormattedUntilStartTime());
            lore.add("§8Duration: §f" + me.getDurationMinutes() + " mins");
            lore.add("§8Rewards: §6" + me.getRewardSummary());
            lore.add(" ");
            for (String perk : me.getPerks()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', perk));
            }
            lore.add(" ");
            if (me.isRunning() && !me.getWarpTarget().isEmpty()) {
                lore.add("§b▶ Click to fast-travel to /warp " + me.getWarpTarget());
            } else if (!me.isRunning()) {
                lore.add("§7This event is scheduled to start automatically.");
            }

            Material mat = me.getIcon() != null ? me.getIcon() : Material.GOLDEN_APPLE;
            inv.setItem(eventSlots[i], createItem(mat, ChatColor.translateAlternateColorCodes('&', me.getTitle()), lore, me.getCustomModelData()));
        }

        // Slot 31: Next Upcoming World Cycle info card
        ServerEvent nextCycle = eventManager.getNextWorldCycleEvent();
        if (nextCycle != null) {
            List<String> nextLore = new ArrayList<>();
            nextLore.add("§8Next Dimension: §f" + nextCycle.getWorldName());
            nextLore.add("§8Arrives in: §e" + eventManager.getFormattedCycleTimeRemaining());
            nextLore.add("§8Planned Duration: §f" + nextCycle.getDurationMinutes() + " mins");
            nextLore.add(" ");
            nextLore.add("§7When the timer reaches §c00:00§7, players in the");
            nextLore.add("§7current hub are safely evacuated to LowerCity.");
            nextLore.add(" ");
            nextLore.add("§7Upcoming Perks:");
            for (String perk : nextCycle.getPerks()) {
                nextLore.add(ChatColor.translateAlternateColorCodes('&', perk));
            }
            inv.setItem(31, createItem(Material.CLOCK, "§6§lNext Dimensional Shift: " + ChatColor.translateAlternateColorCodes('&', nextCycle.getTitle()), nextLore));
        }
    }

    private void renderSchedulePage(Inventory inv, Player player) {
        // Slot 4: Schedule Header
        inv.setItem(4, createItem(Material.BOOK, "§b§lDimensional Timeline & Schedule", Arrays.asList(
                "§7Dragoncote operates on a continuous multiverse cycle.",
                "§7Spawns rotate every 1-3 hours with unique ambiance,",
                "§7quests, physics, and exclusive merchant wares."
        )));

        // World Cycle Timeline (Slots 20, 22, 24)
        List<String> order = eventManager.getCycleOrder();
        int[] cycleSlots = new int[]{20, 22, 24};
        for (int i = 0; i < cycleSlots.length && i < order.size(); i++) {
            String key = order.get(i);
            ServerEvent se = eventManager.getWorldCycleEvents().get(key);
            if (se == null) continue;

            boolean isCurrent = se.getWorldName().equalsIgnoreCase(eventManager.getActiveSpawnWorld());

            List<String> lore = new ArrayList<>();
            lore.add("§8Cycle Slot: §f#" + (i + 1));
            lore.add("§8World: §f" + se.getWorldName());
            lore.add("§8Standard Duration: §f" + se.getDurationMinutes() + " mins");
            lore.add("§8Status: " + (isCurrent ? "§a§lCURRENTLY ACTIVE" : "§7Queued"));
            if (isCurrent) {
                lore.add("§8Time Remaining: §e" + eventManager.getFormattedCycleTimeRemaining());
            }
            lore.add(" ");
            for (String p : se.getPerks()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', p));
            }

            Material mat = se.getIcon() != null ? se.getIcon() : Material.ENDER_PEARL;
            inv.setItem(cycleSlots[i], createItem(mat, (isCurrent ? "§a✦ " : "§7") + ChatColor.translateAlternateColorCodes('&', se.getTitle()), lore, se.getCustomModelData()));
        }

        // Mini Events Timeline (Slots 29, 31, 33)
        int[] miniSlots = new int[]{29, 31, 33};
        List<ServerEvent> minis = new ArrayList<>(eventManager.getMiniEvents().values());
        for (int i = 0; i < miniSlots.length && i < minis.size(); i++) {
            ServerEvent me = minis.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("§8Type: §eScheduled Event");
            lore.add("§8Status: " + (me.isRunning() ? "§a§lACTIVE NOW" : "§7Queued"));
            lore.add(me.isRunning() ? "§8Time Left: §e" + me.getFormattedRemainingTime() : "§8Starts in: §e" + me.getFormattedUntilStartTime());
            lore.add("§8Duration: §f" + me.getDurationMinutes() + " mins");
            lore.add("§8Interval: §fEvery " + (me.getIntervalMinutes() / 60) + " hours");
            lore.add(" ");
            lore.add("§6Reward: §e" + me.getRewardSummary());

            Material mat = me.getIcon() != null ? me.getIcon() : Material.GOLD_INGOT;
            inv.setItem(miniSlots[i], createItem(mat, ChatColor.translateAlternateColorCodes('&', me.getTitle()), lore, me.getCustomModelData()));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String viewTitle = event.getView().getTitle();
        if (!viewTitle.startsWith("§8Server Events:")) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getRawSlot();
        int page = playerPages.getOrDefault(player.getUniqueId(), 1);

        if (slot == 49) { // Close
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            return;
        }

        if (slot == 50 && page == 1) { // Next page
            open(player, 2);
            return;
        }

        if (slot == 48 && page == 2) { // Prev page
            open(player, 1);
            return;
        }

        if (slot == 40) { // Warp to active realm
            player.closeInventory();
            eventManager.teleportToActiveSpawn(player);
            return;
        }

        if (slot == 4 && page == 1) { // Click crown banner
            player.closeInventory();
            eventManager.teleportToActiveSpawn(player);
            return;
        }

        // Mini-Event warp clicks on Page 1 (slots 20, 22, 24)
        if (page == 1) {
            int[] eventSlots = new int[]{20, 22, 24};
            List<ServerEvent> minis = new ArrayList<>(eventManager.getMiniEvents().values());
            for (int i = 0; i < eventSlots.length && i < minis.size(); i++) {
                if (slot == eventSlots[i]) {
                    ServerEvent me = minis.get(i);
                    if (me.isRunning() && !me.getWarpTarget().isEmpty()) {
                        player.closeInventory();
                        player.performCommand("warp " + me.getWarpTarget());
                    } else {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
                    }
                    return;
                }
            }
        }
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        return createItem(mat, name, lore, 0);
    }

    private ItemStack createItem(Material mat, String name, List<String> lore, int customModelData) {
        ItemStack item = new ItemStack(mat != null ? mat : Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            if (customModelData > 0) meta.setCustomModelData(customModelData);
            item.setItemMeta(meta);
        }
        return item;
    }
}
