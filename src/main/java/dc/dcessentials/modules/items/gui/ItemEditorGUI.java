package dc.dcessentials.modules.items.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.gui.DceGui;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemCategory;
import dc.dcessentials.modules.items.model.ItemRarity;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.model.ItemStat;
import dc.dcessentials.modules.items.scroll.ScrollRegistry;
import dc.dcessentials.modules.items.scroll.ScrollType;
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

public class ItemEditorGUI implements Listener {

    private static final String TITLE_PREFIX = ColorUtil.colorize("<#1E293B>&lDCE Editor | &r");
    private final ItemRegistry itemRegistry;
    private final ScrollRegistry scrollRegistry;
    private final ItemStatEditorMenu statEditorMenu;

    // Open sessions: Player UUID -> DCEItem being edited
    private final Map<UUID, DCEItem> openSessions = new HashMap<>();

    public static final class Holder implements DceGui {
        private final DCEItem item;
        private Inventory inventory;

        public Holder(DCEItem item) {
            this.item = item;
        }

        public DCEItem getItem() {
            return item;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public ItemEditorGUI(ItemRegistry itemRegistry, ScrollRegistry scrollRegistry) {
        this.itemRegistry = itemRegistry;
        this.scrollRegistry = scrollRegistry;
        this.statEditorMenu = new ItemStatEditorMenu(itemRegistry, this);
    }

    public ItemStatEditorMenu getStatEditorMenu() {
        return statEditorMenu;
    }

    public void open(Player player, DCEItem item) {
        openSessions.put(player.getUniqueId(), item);
        Holder holder = new Holder(item);
        Inventory gui = Bukkit.createInventory(holder, 54, TITLE_PREFIX + ColorUtil.colorize(item.getRarity().getColor() + item.getId()));
        holder.setInventory(gui);
        refreshGUI(gui, item);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
    }

    public void refreshGUI(Inventory gui, DCEItem item) {
        // Border
        ItemStack border = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "&7", null);
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, border);
        }

        boolean isCosmetic = item.getCategory() == ItemCategory.COSMETIC_SCROLL || item.getCategory() == ItemCategory.COSMETIC;

        // Live Render Preview (Slot 13)
        ItemStack preview = itemRegistry.createItemStack(item.getId(), 1);
        gui.setItem(13, preview);

        // Rarity Picker (Slot 11)
        gui.setItem(11, createGuiItem(Material.NETHER_STAR, "<#FFAA00>&lRarity: " + item.getRarity().getColor() + item.getRarity().getDisplayName(), List.of(
                "&7Current: " + item.getRarity().getColor() + item.getRarity().name(),
                "&f",
                "<#FCD34D>&l▶ Left-Click to Cycle Next",
                "<#38BDF8>&l▶ Right-Click to Cycle Previous"
        )));

        if (!isCosmetic) {
            // Category Picker (Slot 15)
            gui.setItem(15, createGuiItem(Material.COMPASS, "<#38BDF8>&lCategory: &f" + item.getCategory().getDisplayName(), List.of(
                    "&7Current: &f" + item.getCategory().name(),
                    "&f",
                    "<#FCD34D>&l▶ Left-Click to Cycle Next"
            )));

            // Star Tier Rating (Slot 4)
            gui.setItem(4, createGuiItem(Material.GOLD_NUGGET, "<#FBBF24>&lStars: &e" + (item.getStars() > 0 ? "★".repeat(item.getStars()) : "&7None"), List.of(
                    "&7Current Stars: &e" + item.getStars() + " / 5",
                    "&f",
                    "<#FCD34D>&l▶ Left-Click to Add Star",
                    "<#38BDF8>&l▶ Right-Click to Reset"
            )));

            // Manage Stats Button (Slot 31 — opens 16-stat tabbed ItemStatEditorMenu)
            List<String> statLore = new ArrayList<>();
            int activeStatCount = 0;
            for (ItemStat st : ItemStat.values()) {
                double val = item.getStat(st);
                if (val > 0.0) {
                    activeStatCount++;
                    statLore.add("  " + st.getColor() + st.getIcon() + " " + st.getDisplayName() + "&8: "
                            + st.getColor() + dc.dcessentials.modules.items.tooltip.TooltipRenderer.formatStatValue(st, val));
                }
            }
            if (activeStatCount == 0) {
                statLore.add("&7No active stats configured.");
            }
            statLore.add("&f");
            statLore.add("&7Configure all &f16 Dragoncote Stats &7across");
            statLore.add("&cOffense&7, &9Defense&7, and &bUtility &7tabs.");
            statLore.add("&f");
            statLore.add("<#FCD34D>&l▶ Click to Open Stat Matrix");
            gui.setItem(31, createGuiItem(Material.ENCHANTING_TABLE, "<#38BDF8>&l⚔ Manage Stats &8(&f" + activeStatCount + " Active&8)", statLore));

            // Sockets Row (Slots 38, 39, 40, 41)
            renderSocketToggle(gui, 38, ScrollType.COSMETIC, "<#B996E2>&lCosmetic Socket", Material.AMETHYST_SHARD, item);
            renderSocketToggle(gui, 39, ScrollType.COMBAT, "<#EF4444>&lCombat Socket", Material.BLAZE_POWDER, item);
            renderSocketToggle(gui, 40, ScrollType.KEYSTONE, "<#06B6D4>&lKeystone Socket", Material.PRISMARINE_SHARD, item);
            renderSocketToggle(gui, 41, ScrollType.MASTERY, "<#10B981>&lMastery Socket", Material.EXPERIENCE_BOTTLE, item);

            // Drop Template Updater (Slot 46)
            gui.setItem(46, createGuiItem(Material.ANVIL, "<#38BDF8>&lDROP TEMPLATE UPDATE", List.of(
                    "&7Drag & drop an item here on your cursor",
                    "&7to overwrite master template, NBT & model.",
                    "&f",
                    "&8Bumps version and live-syncs server-wide!"
            )));

            // Delete Button (Slot 48)
            gui.setItem(48, createGuiItem(Material.TNT, "<#EF4444>&lDELETE ITEM", List.of(
                    "&7Permanently deletes this item from",
                    "&7the registry and disk file.",
                    "&f",
                    "<#EF4444>&l▶ Click to Open Confirmation Menu"
            )));

            // Copy / Duplicate Button (Slot 50)
            gui.setItem(50, createGuiItem(Material.WRITABLE_BOOK, "<#A855F7>&lCOPY / DUPLICATE ITEM", List.of(
                    "&7Creates an exact cloned copy of this item.",
                    "&f",
                    "<#FCD34D>&l▶ Click to Duplicate Item"
            )));
        }

        // Give Button (Slot 47)
        gui.setItem(47, createGuiItem(Material.CHEST, "<#38BDF8>&lGIVE TO INVENTORY", List.of(
                "&7Receive 1x copy of this item directly.",
                "&f",
                "<#FCD34D>&l▶ Click to Give 1x"
        )));

        // Save & Push Global Sync (Slot 49)
        gui.setItem(49, createGuiItem(Material.EMERALD_BLOCK, "<#10B981>&lSAVE & PUSH GLOBAL SYNC", List.of(
                "&7Saves changes to disk.",
                "&7Bumps item version to &b" + (item.getVersion() + 1) + "&7.",
                "&7Automatically updates every matching item in",
                "&7player inventories across the server!",
                "&f",
                "<#FCD34D>&l▶ Click to Save & Broadcast Sync"
        )));

        // Global Tracker & Dupe Inspection (Slot 51)
        gui.setItem(51, createGuiItem(Material.SPYGLASS, "<#06B6D4>&lTRACK ITEM LOCATIONS & DUPES", List.of(
                "&7Inspect everywhere this item exists:",
                "&7• Online/Offline Player Inventories",
                "&7• World Storage Chests & Shulkers",
                "&7• PlayerVaults",
                "&f",
                "<#06B6D4>&l▶ Click to Open Global Tracker"
        )));

        // Back button (Slot 45)
        gui.setItem(45, createGuiItem(Material.ARROW, "<#E2E8F0>&lBack to " + item.getCategory().getDisplayName(), List.of("&7Return to " + item.getCategory().getDisplayName() + " list")));
    }

    private void renderSocketToggle(Inventory gui, int slot, ScrollType type, String title, Material mat, DCEItem item) {
        boolean active = item.allowsSocket(type);
        gui.setItem(slot, createGuiItem(mat, title, List.of(
                "&7Status: " + (active ? "&a&lENABLED" : "&c&lDISABLED"),
                "&f",
                "<#FCD34D>&l▶ Click to Toggle"
        )));
    }

    private Material getStatIcon(ItemStat stat) {
        switch (stat) {
            case RESONANCE: return Material.BELL;
            case SURGE: return Material.FEATHER;
            case STASIS: return Material.CLOCK;
            case CATALYST: return Material.FERMENTED_SPIDER_EYE;
            case EXECUTE: return Material.WITHER_SKELETON_SKULL;
            case CLEAVE: return Material.IRON_SWORD;
            case SIPHON: return Material.REDSTONE;
            case WARD: return Material.SHIELD;
            default: return Material.BOOK;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;

        int slot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (slot >= topSize || slot < 0) {
            // Allow normal pickup in bottom inventory for template dropping (GuiGuardListener blocks shift/collect)
            return;
        }

        event.setCancelled(true);
        DCEItem item = holder.getItem();
        boolean isCosmetic = item.getCategory() == ItemCategory.COSMETIC_SCROLL || item.getCategory() == ItemCategory.COSMETIC;

        if (slot == 11) { // Rarity
            ItemRarity[] rarities = ItemRarity.values();
            int curIdx = item.getRarity().ordinal();
            if (event.isRightClick()) {
                curIdx = (curIdx - 1 + rarities.length) % rarities.length;
            } else {
                curIdx = (curIdx + 1) % rarities.length;
            }
            item.setRarity(rarities[curIdx]);

            if (isCosmetic) {
                var tm = DCCosmetics.getInstance().getTemplateRegistry();
                var temp = tm != null ? tm.getTemplate(item.getId()) : null;
                if (temp != null) {
                    temp.setRarity(rarities[curIdx].name().toLowerCase());
                    java.io.File file = tm.getTemplateFile(item.getId());
                    if (file != null && file.exists()) {
                        try {
                            org.bukkit.configuration.file.YamlConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
                            cfg.set("rarity", rarities[curIdx].name().toLowerCase());
                            cfg.save(file);
                        } catch (Exception ex) {
                            DCCosmetics.getInstance().getLogger().warning("Failed to save cosmetic rarity: " + ex.getMessage());
                        }
                    }
                }
                ItemStack newScroll = DCCosmetics.getInstance().createScroll(item.getId(), null);
                item.setTemplate(newScroll);
            }

            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            refreshGUI(event.getInventory(), item);
        } else if (isCosmetic && (slot == 4 || slot == 15 || (slot >= 28 && slot <= 35) || (slot >= 38 && slot <= 41) || slot == 46 || slot == 48 || slot == 50)) {
            // Cosmetics ignore stars, category, stats, sockets, template drop, delete, and copy
            return;
        } else if (slot == 15) { // Category
            ItemCategory[] cats = ItemCategory.values();
            int curIdx = (item.getCategory().ordinal() + 1) % cats.length;
            item.setCategory(cats[curIdx]);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            refreshGUI(event.getInventory(), item);
        } else if (slot == 4) { // Stars
            if (event.isRightClick()) {
                item.setStars(0);
            } else {
                item.setStars((item.getStars() + 1) % 6);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.4f);
            refreshGUI(event.getInventory(), item);
        } else if (slot == 31) { // Open ItemStatEditorMenu
            statEditorMenu.open(player, item, dc.dcessentials.modules.items.model.StatCategory.OFFENSE);
        } else if (slot == 38) { // Cosmetic
            toggleSocket(item, ScrollType.COSMETIC);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            refreshGUI(event.getInventory(), item);
        } else if (slot == 39) { // Combat
            toggleSocket(item, ScrollType.COMBAT);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            refreshGUI(event.getInventory(), item);
        } else if (slot == 40) { // Keystone
            toggleSocket(item, ScrollType.KEYSTONE);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            refreshGUI(event.getInventory(), item);
        } else if (slot == 41) { // Mastery
            toggleSocket(item, ScrollType.MASTERY);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            refreshGUI(event.getInventory(), item);
        } else if (slot == 46) { // Drop Template Updater
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                item.setTemplate(cursor.clone());
                item.setMaterial(cursor.getType());
                if (cursor.hasItemMeta()) {
                    ItemMeta cm = cursor.getItemMeta();
                    if (cm.hasCustomModelData()) {
                        item.setCustomModelData(cm.getCustomModelData());
                    }
                    if (cm.hasLore() && cm.getLore() != null) {
                        List<String> rawLore = cm.getLore();
                        List<String> preserved = new ArrayList<>();
                        for (String l : rawLore) {
                            String stripped = org.bukkit.ChatColor.stripColor(l).trim();
                            if (stripped.startsWith(":") && stripped.endsWith(":")) continue;
                            if (stripped.contains("component(s)")) continue;
                            if (stripped.startsWith("minecraft:")) continue;
                            preserved.add(l);
                        }
                        if (!preserved.isEmpty()) {
                            item.setCustomLore(preserved);
                        }
                    }
                }
                item.bumpVersion();
                itemRegistry.registerItem(item);

                // Immediate live-sync
                for (Player online : Bukkit.getOnlinePlayers()) {
                    for (ItemStack invItem : online.getInventory().getContents()) {
                        if (invItem != null && itemRegistry.isDCEItem(invItem) &&
                                itemRegistry.getItemId(invItem).equalsIgnoreCase(item.getId())) {
                            itemRegistry.updateItemToLatest(invItem);
                        }
                    }
                }

                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.4f);
                DCMessage.sendGeneral(player, "Updated master template for &#FCD34D" + item.getId() + " &8(v" + item.getVersion() + ") &aand live-synced!");
                refreshGUI(event.getInventory(), item);
                return;
            } else {
                DCMessage.sendWarning(player, "Please hold an item on your cursor and click this slot to overwrite template!");
            }
        } else if (slot == 47) { // Give
            ItemStack toGive = itemRegistry.createItemStack(item.getId(), 1);
            player.getInventory().addItem(toGive).values().forEach(drop ->
                    player.getWorld().dropItemNaturally(player.getLocation(), drop));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.4f);
            DCMessage.sendGeneral(player, "Delivered &#FFFFFF1x " + item.getDisplayName() + "&#0CBF93!");
        } else if (slot == 48) { // Delete -> Open confirmation GUI
            openSessions.remove(player.getUniqueId());
            new ItemDeleteConfirmGUI(item, itemRegistry).open(player);
        } else if (slot == 50) { // Copy / Duplicate
            DCEItem copied = itemRegistry.copyItem(item, null);
            openSessions.put(player.getUniqueId(), copied);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.4f);
            DCMessage.sendGeneral(player, "Duplicated item into &#FCD34D" + copied.getId() + "&#0CBF93!");
            open(player, copied);
        } else if (slot == 51) { // Global Tracker
            openSessions.remove(player.getUniqueId());
            var mod = DCCosmetics.getInstance().getItemModule();
            if (mod != null && mod.getItemTrackerManager() != null) {
                new dc.dcessentials.modules.items.tracker.ItemTrackerGUI(mod.getItemTrackerManager(), itemRegistry).open(player, item.getId(), 0, dc.dcessentials.modules.items.tracker.ItemTrackerManager.SortMode.MOST_AMOUNT);
            }
        } else if (slot == 49) { // SAVE & PUSH GLOBAL SYNC
            item.bumpVersion();
            itemRegistry.registerItem(item);

            // Immediate live-sync broadcast across all online players
            int updatedTotal = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                for (ItemStack invItem : online.getInventory().getContents()) {
                    if (invItem != null && itemRegistry.isDCEItem(invItem) &&
                            itemRegistry.getItemId(invItem).equalsIgnoreCase(item.getId())) {
                        if (itemRegistry.updateItemToLatest(invItem)) {
                            updatedTotal++;
                        }
                    }
                }
            }

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            DCMessage.sendGeneral(player, "Saved & pushed global update for &#FCD34D" + item.getId() + " &8(v" + item.getVersion() + ")!");
            if (updatedTotal > 0) {
                DCMessage.sendInfo(player, "Updated &#38BDF8" + updatedTotal + " &#BAE6FDactive items held by players.");
            }
            refreshGUI(event.getInventory(), item);
        } else if (slot == 45) { // Back to Category's items view
            openSessions.remove(player.getUniqueId());
            var mod = DCCosmetics.getInstance().getItemModule();
            if (mod != null && mod.getItemBrowserGUI() != null) {
                mod.getItemBrowserGUI().openItems(player, item.getCategory(), item.getRarity(), 0);
            } else {
                new ItemBrowserGUI(itemRegistry, this).open(player);
            }
        }
    }

    private void toggleSocket(DCEItem item, ScrollType type) {
        if (item.allowsSocket(type)) {
            item.removeAllowedSocket(type);
        } else {
            item.addAllowedSocket(type);
        }
    }

    private ItemStack createGuiItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            if (lore != null) {
                List<String> colored = new ArrayList<>();
                for (String l : lore) colored.add(ColorUtil.colorize(l));
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
