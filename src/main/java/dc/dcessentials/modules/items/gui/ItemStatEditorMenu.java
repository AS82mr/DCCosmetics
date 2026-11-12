package dc.dcessentials.modules.items.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.gui.DceGui;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.model.ItemStat;
import dc.dcessentials.modules.items.model.StatCategory;
import dc.dcessentials.modules.items.tooltip.TooltipRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ItemStatEditorMenu implements Listener {

    private static final int[] GRID_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final ItemRegistry itemRegistry;
    private final ItemEditorGUI parentEditor;

    public static final class Holder implements DceGui {
        private final DCEItem item;
        private StatCategory category;
        private Inventory inventory;
        private final Map<Integer, ItemStat> slotToStat = new HashMap<>();

        public Holder(DCEItem item, StatCategory category) {
            this.item = item;
            this.category = category;
        }

        public DCEItem getItem() { return item; }
        public StatCategory getCategory() { return category; }
        public void setCategory(StatCategory category) { this.category = category; }
        public Map<Integer, ItemStat> getSlotToStat() { return slotToStat; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    public ItemStatEditorMenu(ItemRegistry itemRegistry, ItemEditorGUI parentEditor) {
        this.itemRegistry = itemRegistry;
        this.parentEditor = parentEditor;
    }

    public void open(Player player, DCEItem item, StatCategory category) {
        if (category == null) category = StatCategory.OFFENSE;
        Holder holder = new Holder(item, category);
        Inventory gui = Bukkit.createInventory(holder, 54,
                ColorUtil.colorize("<#1E293B>&lStat Matrix | &r" + item.getRarity().getColor() + item.getId()));
        holder.setInventory(gui);
        refresh(holder);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.3f);
    }

    public void refresh(Holder holder) {
        Inventory gui = holder.getInventory();
        DCEItem item = holder.getItem();
        StatCategory activeCat = holder.getCategory();
        holder.getSlotToStat().clear();

        ItemStack border = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "&7", null);
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, border);
        }
        ItemStack innerBg = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, "&7", null);
        for (int slot : GRID_SLOTS) {
            gui.setItem(slot, innerBg);
        }

        // Slot 0: Back to ItemEditorGUI
        gui.setItem(0, createGuiItem(Material.ARROW, "<#E2E8F0>&l◀ Back to Item Editor",
                List.of("&7Return to " + item.getDisplayName() + " editor")));

        // Category Tabs at Slots 2, 4, 6
        gui.setItem(2, createTabItem(StatCategory.OFFENSE, activeCat, Material.NETHERITE_SWORD, "<#EF4444>&l⚔ Offense Stats", item));
        gui.setItem(4, createTabItem(StatCategory.DEFENSE, activeCat, Material.SHIELD, "<#3B82F6>&l🛡 Defense Stats", item));
        gui.setItem(6, createTabItem(StatCategory.UTILITY, activeCat, Material.BEACON, "<#22D3EE>&l❖ Utility & Keystone Stats", item));

        // Slot 8: Reset All Stats
        gui.setItem(8, createGuiItem(Material.BARRIER, "<#EF4444>&lReset All Stats", List.of(
                "&7Clears all 16 stats on this item to &c0&7.",
                "&f",
                "<#EF4444>&l▶ Click to Reset All"
        )));

        // Live Item Preview at Slot 49
        gui.setItem(49, itemRegistry.createItemStack(item.getId(), 1));

        // Populate category stats in GRID_SLOTS
        int idx = 0;
        for (ItemStat stat : ItemStat.values()) {
            if (stat.getCategory() != activeCat) continue;
            if (idx >= GRID_SLOTS.length) break;
            int slot = GRID_SLOTS[idx++];
            holder.getSlotToStat().put(slot, stat);
            gui.setItem(slot, createStatButton(stat, item.getStat(stat)));
        }
    }

    private ItemStack createTabItem(StatCategory tabCat, StatCategory activeCat, Material mat, String title, DCEItem item) {
        boolean selected = tabCat == activeCat;
        int activeCount = 0;
        for (ItemStat st : ItemStat.values()) {
            if (st.getCategory() == tabCat && item.getStat(st) > 0.0) {
                activeCount++;
            }
        }
        List<String> lore = new ArrayList<>();
        lore.add("&7Active in category: &f" + activeCount);
        lore.add("&f");
        lore.add(selected ? "<#10B981>&l● SELECTED TAB" : "<#FCD34D>&l▶ Click to View");
        ItemStack stack = createGuiItem(mat, title, lore);
        if (selected && stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack createStatButton(ItemStat stat, double val) {
        Material mat = getStatIcon(stat);
        String valDisplay = val > 0.0 ? stat.getColor() + TooltipRenderer.formatStatValue(stat, val) : "&80 (Inactive)";
        String capDisplay = stat == ItemStat.RESONANCE
                ? "2-Hit .. 8-Hit Cadence"
                : TooltipRenderer.formatStatValue(stat, stat.getHardCap()) + " Cap";

        List<String> lore = new ArrayList<>();
        lore.add("&7Current: " + valDisplay);
        lore.add("&7Stat Cap: &f" + capDisplay);
        lore.add("&8" + stat.getBrief());
        lore.add("&f");
        for (String line : stat.getDescription().split("\n")) {
            lore.add("&7" + line);
        }
        lore.add("&f");
        if (stat == ItemStat.RESONANCE) {
            lore.add("<#10B981>&l▶ Left / Right: &a+1 &7/ &c-1 &7Hit (2..8)");
            lore.add("<#38BDF8>&l▶ Shift-Left / Right: &b+5 &7/ &6-5");
        } else {
            lore.add("<#10B981>&l▶ Left / Right: &a+0.5% &7/ &c-0.5%");
            lore.add("<#38BDF8>&l▶ Shift-Left / Right: &b+2.5% &7/ &6-2.5%");
        }
        lore.add("<#94A3B8>&l▶ Drop (Q) / Middle-Click: &7Reset to 0");

        ItemStack stack = createGuiItem(mat, stat.getColor() + "&l" + stat.getIcon() + " " + stat.getDisplayName(), lore);
        if (val > 0.0 && stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static Material getStatIcon(ItemStat stat) {
        return switch (stat) {
            case RESONANCE -> Material.BELL;
            case SURGE -> Material.FEATHER;
            case STASIS -> Material.CLOCK;
            case CATALYST -> Material.FERMENTED_SPIDER_EYE;
            case EXECUTE -> Material.WITHER_SKELETON_SKULL;
            case CLEAVE -> Material.IRON_SWORD;
            case SIPHON -> Material.REDSTONE;
            case WARD -> Material.SHIELD;
            case BASTION -> Material.NETHERITE_CHESTPLATE;
            case DEFLECT -> Material.SPECTRAL_ARROW;
            case AEGIS -> Material.MAGMA_CREAM;
            case PROSPERITY -> Material.EMERALD;
            case TEMPO -> Material.GOLDEN_PICKAXE;
            case REFINEMENT -> Material.ENCHANTED_BOOK;
            case CONDUIT -> Material.ECHO_SHARD;
            case THERMAL -> Material.BLUE_ICE;
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        event.setCancelled(true);

        DCEItem item = holder.getItem();
        if (slot == 0) {
            parentEditor.open(player, item);
            return;
        }
        if (slot == 2) {
            holder.setCategory(StatCategory.OFFENSE);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.3f);
            refresh(holder);
            return;
        }
        if (slot == 4) {
            holder.setCategory(StatCategory.DEFENSE);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.3f);
            refresh(holder);
            return;
        }
        if (slot == 6) {
            holder.setCategory(StatCategory.UTILITY);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.3f);
            refresh(holder);
            return;
        }
        if (slot == 8) {
            for (ItemStat st : ItemStat.values()) {
                item.setStat(st, 0.0);
            }
            persistAndSync(item);
            player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 1.2f);
            DCMessage.sendGeneral(player, "Reset all stats on &#FCD34D" + item.getId() + "&#0CBF93.");
            refresh(holder);
            return;
        }

        ItemStat stat = holder.getSlotToStat().get(slot);
        if (stat == null) return;

        ClickType click = event.getClick();
        double cur = item.getStat(stat);
        double next = cur;

        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP || click == ClickType.MIDDLE) {
            next = 0.0;
        } else if (stat == ItemStat.RESONANCE) {
            double step = event.isShiftClick() ? 5.0 : 1.0;
            if (event.isRightClick()) {
                next = cur <= 2.0 ? 0.0 : Math.max(2.0, cur - step);
            } else {
                next = cur <= 0.0 ? 2.0 : Math.min(8.0, cur + step);
            }
        } else {
            double step = event.isShiftClick() ? 2.5 : 0.5;
            if (event.isRightClick()) {
                next = Math.max(0.0, Math.round((cur - step) * 10.0) / 10.0);
            } else {
                next = Math.min(stat.getHardCap(), Math.round((cur + step) * 10.0) / 10.0);
            }
        }

        item.setStat(stat, next);
        persistAndSync(item);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
        refresh(holder);
    }

    private void persistAndSync(DCEItem item) {
        item.bumpVersion();
        itemRegistry.registerItem(item);
        for (Player online : Bukkit.getOnlinePlayers()) {
            boolean changed = false;
            for (ItemStack invItem : online.getInventory().getContents()) {
                if (invItem != null && itemRegistry.isDCEItem(invItem)
                        && item.getId().equalsIgnoreCase(itemRegistry.getItemId(invItem))) {
                    if (itemRegistry.updateItemToLatest(invItem)) {
                        changed = true;
                    }
                }
            }
            if (changed && DCCosmetics.getInstance().getItemModule() != null
                    && DCCosmetics.getInstance().getItemModule().getStatAggregator() != null) {
                DCCosmetics.getInstance().getItemModule().getStatAggregator().invalidate(online);
            }
        }
    }

    private ItemStack createGuiItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            if (lore != null) {
                List<String> colored = new ArrayList<>(lore.size());
                for (String l : lore) colored.add(ColorUtil.colorize(l));
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
