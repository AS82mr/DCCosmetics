package dc.dcessentials.modules.items.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemCategory;
import dc.dcessentials.modules.items.model.ItemRarity;
import dc.dcessentials.modules.items.model.ItemRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ItemBrowserGUI implements Listener {

    private final ItemRegistry itemRegistry;
    private final ItemEditorGUI itemEditorGUI;

    public enum MenuLevel {
        CATEGORIES,
        RARITIES,
        ITEMS
    }

    public static class DCEBrowserHolder implements InventoryHolder {
        private final MenuLevel level;
        private final ItemCategory category;
        private final ItemRarity rarity;
        private final int page;

        public DCEBrowserHolder(MenuLevel level, ItemCategory category, ItemRarity rarity, int page) {
            this.level = level;
            this.category = category;
            this.rarity = rarity;
            this.page = page;
        }

        public MenuLevel getLevel() { return level; }
        public ItemCategory getCategory() { return category; }
        public ItemRarity getRarity() { return rarity; }
        public int getPage() { return page; }

        @Override
        public Inventory getInventory() { return null; }
    }

    public static class BrowserSession {
        private final ItemCategory category;
        private final ItemRarity rarity;
        private final int page;

        public BrowserSession(ItemCategory category, ItemRarity rarity, int page) {
            this.category = category;
            this.rarity = rarity;
            this.page = page;
        }

        public ItemCategory getCategory() { return category; }
        public ItemRarity getRarity() { return rarity; }
        public int getPage() { return page; }
    }

    private static final java.util.concurrent.ConcurrentHashMap<UUID, BrowserSession> lastSessionMap = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<UUID, String> filterModeMap = new java.util.concurrent.ConcurrentHashMap<>();

    public ItemBrowserGUI(ItemRegistry itemRegistry, ItemEditorGUI itemEditorGUI) {
        this.itemRegistry = itemRegistry;
        this.itemEditorGUI = itemEditorGUI;
    }

    public void open(Player player) {
        BrowserSession session = lastSessionMap.get(player.getUniqueId());
        if (session != null && session.getCategory() != null && session.getRarity() != null) {
            openItems(player, session.getCategory(), session.getRarity(), session.getPage());
        } else {
            openCategories(player);
        }
    }

    // ==========================================
    // Level 1: Categories View
    // ==========================================
    public void openCategories(Player player) {
        itemRegistry.syncAllCosmetics();
        lastSessionMap.remove(player.getUniqueId());
        String title = ColorUtil.colorize("<#1E293B>&lDCE &8| <#0EA5E9>&lItem Categories");
        DCEBrowserHolder holder = new DCEBrowserHolder(MenuLevel.CATEGORIES, null, null, 0);
        Inventory gui = Bukkit.createInventory(holder, 54, title);

        // Bottom border
        ItemStack border = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "&7", null);
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, border);
        }

        // Category icons
        ItemCategory[] categories = ItemCategory.values();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

        for (int i = 0; i < categories.length && i < slots.length; i++) {
            ItemCategory cat = categories[i];
            int count = itemRegistry.getItemCount(cat);
            Material icon = getCategoryIcon(cat);

            List<String> lore = new ArrayList<>();
            lore.add("&7Registered items: &e" + count);
            lore.add("&f");
            lore.add("<#10B981>&l▶ Click to View Rarities");

            gui.setItem(slots[i], createGuiItem(icon, "<#38BDF8>&l" + cat.getDisplayName(), lore));
        }

        // Global Overview (Slot 49)
        gui.setItem(49, createGuiItem(Material.NETHER_STAR, "<#FFAA00>&lDCE Master Registry", List.of(
                "&7Total items: &e" + itemRegistry.getItems().size(),
                "&7Live-Sync: &aACTIVE",
                "&f",
                "&8Hierarchical storage: items/<cat>/<rarity>/"
        )));

        // Mass Importer (Slot 48)
        gui.setItem(48, createGuiItem(Material.HOPPER, "<#10B981>&lMass Item Importer", List.of(
                "&7Batch-import multiple items at once.",
                "&f",
                "<#FCD34D>&l▶ Click to Open Importer"
        )));

        // Close (Slot 53)
        gui.setItem(53, createGuiItem(Material.BARRIER, "<#EF4444>&lClose & Exit", List.of("&7Close menu.")));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
    }

    // ==========================================
    // Level 2: Rarities View
    // ==========================================
    public void openRarities(Player player, ItemCategory category) {
        if (category == null) {
            openCategories(player);
            return;
        }

        String title = ColorUtil.colorize("<#1E293B>&lDCE &8| &f" + category.getDisplayName() + " &8> <#0EA5E9>&lRarities");
        DCEBrowserHolder holder = new DCEBrowserHolder(MenuLevel.RARITIES, category, null, 0);
        Inventory gui = Bukkit.createInventory(holder, 54, title);

        // Bottom border
        ItemStack border = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "&7", null);
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, border);
        }

        // 6 Standardized Rarities
        ItemRarity[] rarities = ItemRarity.values();
        int[] slots = {20, 21, 22, 23, 24, 25};

        for (int i = 0; i < rarities.length && i < slots.length; i++) {
            ItemRarity r = rarities[i];
            int count = itemRegistry.getItemCount(category, r);
            Material mat = getRarityIcon(r);

            List<String> lore = new ArrayList<>();
            lore.add("&7Category: &f" + category.getDisplayName());
            lore.add("&7Items in tier: &e" + count);
            lore.add("&f");
            lore.add("<#10B981>&l▶ Click to Browse Items");

            gui.setItem(slots[i], createGuiItem(mat, r.getColor() + "&l" + r.getDisplayName(), lore));
        }

        // Path helpers
        gui.setItem(45, createGuiItem(Material.ANVIL, "<#F59E0B>&lRoot Directory", List.of("&7Back to all categories.")));
        gui.setItem(46, createGuiItem(Material.OAK_DOOR, "<#38BDF8>&l⬅ Back to Categories", List.of("&7Return one level up.")));
        gui.setItem(48, createGuiItem(Material.HOPPER, "<#10B981>&lMass Item Importer", List.of("&7Open batch importer.")));
        gui.setItem(53, createGuiItem(Material.BARRIER, "<#EF4444>&lClose & Exit", List.of("&7Close menu.")));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.3f);
    }

    // ==========================================
    // Level 3: Items View (in Category & Rarity)
    // ==========================================
    public void openItems(Player player, ItemCategory category, ItemRarity rarity, int page) {
        if (category == null || rarity == null) {
            openCategories(player);
            return;
        }

        itemRegistry.syncAllCosmetics();
        String filterMode = filterModeMap.getOrDefault(player.getUniqueId(), "ALL");
        List<DCEItem> matching = new ArrayList<>(itemRegistry.getItemsByCategoryAndRarity(category, rarity, filterMode));
        int totalItems = matching.size();
        int maxPages = Math.max(1, (int) Math.ceil((double) totalItems / 45));
        int currentPage = Math.max(0, Math.min(page, maxPages - 1));
        lastSessionMap.put(player.getUniqueId(), new BrowserSession(category, rarity, currentPage));

        String title = ColorUtil.colorize("<#1E293B>&lDCE &8| &f" + category.getDisplayName() + " &8> " + rarity.getColor() + rarity.getDisplayName() + (maxPages > 1 ? " &8(&f" + (currentPage + 1) + "/" + maxPages + "&8)" : ""));
        DCEBrowserHolder holder = new DCEBrowserHolder(MenuLevel.ITEMS, category, rarity, currentPage);
        Inventory gui = Bukkit.createInventory(holder, 54, title);

        // Bottom border
        ItemStack border = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "&7", null);
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, border);
        }

        // Populate items
        int start = currentPage * 45;
        int end = Math.min(start + 45, totalItems);

        for (int i = start; i < end; i++) {
            DCEItem dce = matching.get(i);
            ItemStack stack = itemRegistry.createItemStack(dce.getId(), 1);
            if (stack != null && stack.hasItemMeta()) {
                ItemMeta meta = stack.getItemMeta();
                List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                boolean isVanilla = dce.getId().startsWith("default_");
                boolean isCosmetic = dce.getCategory() == ItemCategory.COSMETIC_SCROLL || dce.getCategory() == ItemCategory.COSMETIC;

                if (isCosmetic || dce.getCategory() == ItemCategory.SCROLL) {
                    for (Enchantment e : new ArrayList<>(meta.getEnchants().keySet())) {
                        meta.removeEnchant(e);
                    }
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                    meta.setEnchantmentGlintOverride(true);
                }

                if (isVanilla) {
                    lore.add(Component.empty());
                    lore.add(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.colorize("<#10B981>&l🛡 [VANILLA BASE TEMPLATE]")).decoration(TextDecoration.ITALIC, false));
                    lore.add(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.colorize("&7Controls baseline &f" + dce.getDisplayName() + " &7across the server.")).decoration(TextDecoration.ITALIC, false));
                }

                lore.add(Component.empty());
                lore.add(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.colorize("<#10B981>&l▶ Left-Click: &fGive 1x")).decoration(TextDecoration.ITALIC, false));
                lore.add(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.colorize("<#38BDF8>&l▶ Shift-Left-Click: &bInspect Tracking & Dupes")).decoration(TextDecoration.ITALIC, false));
                lore.add(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.colorize("<#FCD34D>&l▶ Right-Click: &eOpen Component Editor")).decoration(TextDecoration.ITALIC, false));
                if (!isCosmetic) {
                    lore.add(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.colorize("<#A855F7>&l▶ Shift-Right-Click: &dDuplicate / Copy Item")).decoration(TextDecoration.ITALIC, false));
                    if (!isVanilla) {
                        lore.add(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.colorize("<#EF4444>&l▶ Drop-Key (Q) / Middle-Click: &cDelete Item")).decoration(TextDecoration.ITALIC, false));
                    }
                }
                meta.lore(lore);
                stack.setItemMeta(meta);
            }
            gui.setItem(i - start, stack);
        }

        // Pagination buttons
        if (currentPage > 0) {
            gui.setItem(47, createGuiItem(Material.ARROW, "<#38BDF8>&l◀ Previous Page", List.of("&7Go to page " + currentPage)));
        }
        if (currentPage < maxPages - 1) {
            gui.setItem(51, createGuiItem(Material.ARROW, "<#38BDF8>&lNext Page ▶", List.of("&7Go to page " + (currentPage + 2))));
        }

        // View Filter Toggle Button (Slot 49)
        String modeName = switch (filterMode) {
            case "CUSTOM_ONLY" -> "<#A855F7>&lCustom Gear Only";
            case "VANILLA_ONLY" -> "<#10B981>&lVanilla Base Gear Only";
            default -> "<#38BDF8>&lAll Items (Custom & Base)";
        };
        gui.setItem(49, createGuiItem(Material.HOPPER, "<#FFAA00>&lView Filter: " + modeName, List.of(
                "&7Filter between custom items",
                "&7and server-wide vanilla base templates.",
                "&f",
                "&7Active: " + modeName,
                "&f",
                "<#FCD34D>&l✦ Click to Cycle Filter Mode"
        )));

        // Path helpers
        gui.setItem(45, createGuiItem(Material.ANVIL, "<#F59E0B>&lRoot Directory", List.of("&7Return to categories.")));
        gui.setItem(46, createGuiItem(Material.OAK_DOOR, "<#38BDF8>&l⬅ Back to " + category.getDisplayName(), List.of("&7Back to rarity selector.")));
        gui.setItem(48, createGuiItem(Material.HOPPER, "<#10B981>&lMass Item Importer", List.of("&7Open batch importer.")));
        gui.setItem(53, createGuiItem(Material.BARRIER, "<#EF4444>&lClose & Exit", List.of("&7Close menu.")));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check if the top inventory belongs to our browser
        if (event.getView().getTopInventory() == null || !(event.getView().getTopInventory().getHolder() instanceof DCEBrowserHolder holder)) {
            return;
        }

        // Always lock down all item moves/steals in this GUI
        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= 54) return; // Player inventory click cancelled, ignore logic

        // Universal Path Navigation
        if (rawSlot == 45) { // Root Directory (Categories)
            openCategories(player);
            return;
        } else if (rawSlot == 48) { // Mass Importer
            new MassImportGUI(itemRegistry).open(player);
            return;
        } else if (rawSlot == 53) { // Close
            player.closeInventory();
            return;
        }

        MenuLevel level = holder.getLevel();

        if (level == MenuLevel.CATEGORIES) {
            ItemCategory[] categories = ItemCategory.values();
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
            for (int i = 0; i < categories.length && i < slots.length; i++) {
                if (rawSlot == slots[i]) {
                    openRarities(player, categories[i]);
                    return;
                }
            }
        } else if (level == MenuLevel.RARITIES) {
            if (rawSlot == 46) { // Back to categories
                openCategories(player);
                return;
            }
            ItemRarity[] rarities = ItemRarity.values();
            int[] slots = {20, 21, 22, 23, 24, 25};
            for (int i = 0; i < rarities.length && i < slots.length; i++) {
                if (rawSlot == slots[i]) {
                    openItems(player, holder.getCategory(), rarities[i], 0);
                    return;
                }
            }
        } else if (level == MenuLevel.ITEMS) {
            if (rawSlot == 46) { // Back to rarities
                openRarities(player, holder.getCategory());
                return;
            } else if (rawSlot == 47 && holder.getPage() > 0) { // Prev page
                openItems(player, holder.getCategory(), holder.getRarity(), holder.getPage() - 1);
                return;
            } else if (rawSlot == 49) { // Cycle filter mode
                String cur = filterModeMap.getOrDefault(player.getUniqueId(), "ALL");
                String next = switch (cur) {
                    case "ALL" -> "CUSTOM_ONLY";
                    case "CUSTOM_ONLY" -> "VANILLA_ONLY";
                    default -> "ALL";
                };
                filterModeMap.put(player.getUniqueId(), next);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                openItems(player, holder.getCategory(), holder.getRarity(), 0);
                return;
            } else if (rawSlot == 51) { // Next page
                openItems(player, holder.getCategory(), holder.getRarity(), holder.getPage() + 1);
                return;
            }

            if (rawSlot >= 0 && rawSlot < 45) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || !itemRegistry.isDCEItem(clicked)) return;

                String itemId = itemRegistry.getItemId(clicked);
                DCEItem dce = itemRegistry.getItem(itemId);
                if (dce == null) return;

                boolean isVanilla = dce.getId().startsWith("default_");
                boolean isCosmetic = dce.getCategory() == ItemCategory.COSMETIC_SCROLL || dce.getCategory() == ItemCategory.COSMETIC;

                // Delete item via Drop-key or Middle-click -> Confirmation GUI
                if (event.getClick() == org.bukkit.event.inventory.ClickType.DROP ||
                    event.getClick() == org.bukkit.event.inventory.ClickType.CONTROL_DROP ||
                    event.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE) {
                    if (isCosmetic) {
                        DCMessage.sendWarning(player, "Cosmetics are managed directly by DCCosmetics.");
                        return;
                    }
                    if (isVanilla) {
                        DCMessage.sendWarning(player, "Vanilla base templates cannot be deleted.");
                        return;
                    }
                    new ItemDeleteConfirmGUI(dce, itemRegistry).open(player);
                    return;
                }

                // Copy / Duplicate item via Shift-Right-Click
                if (event.isRightClick() && event.isShiftClick()) {
                    if (isCosmetic) {
                        DCMessage.sendWarning(player, "Cosmetics cannot be duplicated via DCE Items.");
                        return;
                    }
                    DCEItem copied = itemRegistry.copyItem(dce, null);
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.4f);
                    DCMessage.sendGeneral(player, "Duplicated &#FFFFFF" + dce.getId() + " &#0CBF93-> &#FCD34D" + copied.getId() + "&#0CBF93!");
                    openItems(player, holder.getCategory(), holder.getRarity(), holder.getPage());
                    return;
                }

                // Tracking & dupe inspection via Shift-Left-Click
                if (event.isLeftClick() && event.isShiftClick()) {
                    var mod = DCCosmetics.getInstance().getItemModule();
                    if (mod != null && mod.getItemTrackerManager() != null) {
                        new dc.dcessentials.modules.items.tracker.ItemTrackerGUI(mod.getItemTrackerManager(), itemRegistry).open(player, dce.getId(), 0, dc.dcessentials.modules.items.tracker.ItemTrackerManager.SortMode.MOST_AMOUNT);
                    }
                    return;
                }

                if (event.isRightClick()) {
                    itemEditorGUI.open(player, dce);
                } else {
                    int amount = 1;
                    ItemStack toGive = itemRegistry.createItemStack(dce.getId(), amount);
                    player.getInventory().addItem(toGive).values().forEach(drop ->
                            player.getWorld().dropItemNaturally(player.getLocation(), drop));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.4f);
                    DCMessage.sendGeneral(player, "Delivered &#FFFFFF" + amount + "x " + dce.getDisplayName() + "&#0CBF93!");
                }
            }
        }
    }

    private Material getCategoryIcon(ItemCategory cat) {
        return switch (cat) {
            case VANILLA -> Material.ANVIL;
            case WEAPON -> Material.NETHERITE_SWORD;
            case ARMOR -> Material.NETHERITE_CHESTPLATE;
            case TOOL -> Material.NETHERITE_PICKAXE;
            case ACCESSORY -> Material.RECOVERY_COMPASS;
            case KEYSTONE -> Material.ECHO_SHARD;
            case VOUCHER -> Material.PAPER;
            case SCROLL -> Material.ENCHANTED_BOOK;
            case COSMETIC_SCROLL -> Material.NAME_TAG;
            case COSMETIC -> Material.AMETHYST_SHARD;
            default -> Material.CHEST;
        };
    }

    private Material getRarityIcon(ItemRarity r) {
        return switch (r) {
            case COMMON -> Material.GRAY_SHULKER_BOX;
            case UNCOMMON -> Material.LIME_SHULKER_BOX;
            case RARE -> Material.LIGHT_BLUE_SHULKER_BOX;
            case EPIC -> Material.MAGENTA_SHULKER_BOX;
            case LEGENDARY -> Material.ORANGE_SHULKER_BOX;
            case MASTERY -> Material.RED_SHULKER_BOX;
        };
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
