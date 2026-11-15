package dc.dcessentials.modules.curator;

import dc.dcessentials.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TEMPORARY MODEL CURATOR GUI.
 * 54-slot paginated browser with 3 view filters (ALL, FAVORITES, CLEANUP)
 * + source filter (ALL / MEG / IA Furniture / IA Items) and direct toggle controls.
 */
public final class CuratorGUI implements Listener {

    private static final int PAGE_SIZE = 45;

    private final CuratorManager manager;

    public CuratorGUI(CuratorManager manager) {
        this.manager = manager;
    }

    public static final class CuratorHolder implements InventoryHolder {
        private final CuratorEntry.FilterMode filterMode;
        private final CuratorEntry.SourceFilter sourceFilter;
        private final int page;
        private final Map<Integer, CuratorEntry> slotMap = new HashMap<>();
        private Inventory inventory;

        CuratorHolder(CuratorEntry.FilterMode filterMode, CuratorEntry.SourceFilter sourceFilter, int page) {
            this.filterMode = filterMode;
            this.sourceFilter = sourceFilter;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void setInventory(Inventory inv) {
            this.inventory = inv;
        }
    }

    public void open(Player player, CuratorEntry.FilterMode filterMode, CuratorEntry.SourceFilter sourceFilter, int requestedPage) {
        List<CuratorEntry> filtered = manager.getFilteredEntries(filterMode, sourceFilter);
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        CuratorHolder holder = new CuratorHolder(filterMode, sourceFilter, page);
        String title = "&#0F172A&lCurator &8[" + filterMode.getLabel() + " &7(" + filtered.size() + ")&8] &7P" + (page + 1) + "/" + totalPages;
        Inventory inv = Bukkit.createInventory(holder, 54, ColorUtil.toComponent(title));
        holder.setInventory(inv);

        CuratorEntry currentSelected = manager.getSelectedEntry(player);
        int start = page * PAGE_SIZE;
        int end = Math.min(filtered.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            CuratorEntry entry = filtered.get(i);
            int slot = i - start;
            boolean isFav = manager.isFavorite(entry);
            boolean isClean = manager.isCleanup(entry);
            boolean isCurrent = currentSelected != null && currentSelected.getId().equalsIgnoreCase(entry.getId());
            inv.setItem(slot, buildEntryIcon(entry, isFav, isClean, isCurrent));
            holder.slotMap.put(slot, entry);
        }

        // Control bar (slots 45-53)
        inv.setItem(45, navButton(Material.ARROW, page > 0 ? "&e◀ Previous Page" : "&8Previous Page",
                List.of("&7Page " + (page + 1) + " of " + totalPages)));

        inv.setItem(46, filterTab(Material.COMPASS, CuratorEntry.FilterMode.ALL, filterMode,
                manager.countEntries(CuratorEntry.FilterMode.ALL, sourceFilter)));
        inv.setItem(47, filterTab(Material.NETHER_STAR, CuratorEntry.FilterMode.FAVORITES, filterMode,
                manager.countEntries(CuratorEntry.FilterMode.FAVORITES, sourceFilter)));
        inv.setItem(48, filterTab(Material.BARRIER, CuratorEntry.FilterMode.CLEANUP, filterMode,
                manager.countEntries(CuratorEntry.FilterMode.CLEANUP, sourceFilter)));

        inv.setItem(49, button(Material.HOPPER, "&#38BDF8Source Filter: &f" + sourceFilter.getLabel(), List.of(
                "&7Currently showing: &e" + sourceFilter.getLabel(),
                "&7Click to cycle:",
                "&8• &fAll Sources",
                "&8• &fMEG Blueprints Only",
                "&8• &fIA Furniture Only",
                "&8• &fIA Items Only"
        )));

        inv.setItem(50, button(Material.TNT_MINECART, "&#F87171Clear All Spawned Previews", List.of(
                "&7Active tracked spawns: &f" + manager.getSpawnedCount(player),
                "&eClick to despawn all active previews."
        )));

        inv.setItem(51, button(Material.WRITABLE_BOOK, "&#34D399Rescan Disk & Export Reports", List.of(
                "&7Rescans ModelEngine blueprints &",
                "&7ItemsAdder configs, then writes:",
                "&8• &fplugins/DCEssentials/curator/cleanup_report.md",
                "&8• &fplugins/DCEssentials/curator/favorites_report.md"
        )));

        String selLabel = currentSelected != null ? currentSelected.getId() : "None";
        boolean autoAnim = manager.isAutoAnimate(player);
        inv.setItem(52, button(Material.SPYGLASS, "&eAnimation & Selection Control", List.of(
                "&7Selected: &f" + selLabel,
                "&7Auto-Animate on Spawn: " + (autoAnim ? "&aON &8(loops default anim)" : "&cOFF &8(static pose)"),
                "&aLeft-Click &8» &7Cycle Animation on Latest Spawn",
                "&bRight-Click &8» &7Toggle Auto-Animate ON/OFF",
                "&dWand In-Hand &8» &eShift+Q &7to cycle animations!"
        )));

        inv.setItem(53, navButton(Material.ARROW, page < totalPages - 1 ? "&eNext Page ▶" : "&8Next Page",
                List.of("&7Page " + (page + 1) + " of " + totalPages)));

        player.openInventory(inv);
    }

    private ItemStack buildEntryIcon(CuratorEntry entry, boolean isFav, boolean isClean, boolean isCurrent) {
        ItemStack icon;
        if (entry.getSourceType() == CuratorEntry.SourceType.MEG) {
            Material mat = isClean ? Material.RED_CONCRETE : (isFav ? Material.LIME_CONCRETE : Material.ARMOR_STAND);
            icon = new ItemStack(mat);
        } else {
            icon = CuratorBridge.resolveItemsAdderItemStack(entry);
        }

        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            String prefix = (isCurrent ? "&#FBBF24▶ " : "")
                    + (isFav ? "&#34D399★ " : "")
                    + (isClean ? "&#F87171✖ " : "");
            String color = isClean ? "&#FCA5A5" : (isFav ? "&#86EFAC" : "&f");
            int gIdx = manager.getGlobalIndex(entry);
            String idxTag = gIdx > 0 ? "&8#" + gIdx + " " : "";
            meta.displayName(ColorUtil.toItemComponent(prefix + idxTag + color + entry.getId()));

            List<String> lore = new ArrayList<>();
            lore.add("&8&m────────────────────────");
            lore.add("&7Source: &b" + entry.getSourceType().getLabel() + " &8(" + entry.getPackName() + ")");
            lore.add("&7Status: " + (isFav ? "&a★ FAVORITE " : "") + (isClean ? "&c✖ MARKED FOR CLEANUP" : (!isFav ? "&7Unmarked" : "")));
            if (entry.getSourceType() == CuratorEntry.SourceType.MEG) {
                List<String> anims = entry.getAnimations();
                if (anims.isEmpty()) {
                    lore.add("&7Animations: &8None");
                } else {
                    String joined = String.join(", ", anims.subList(0, Math.min(4, anims.size())));
                    lore.add("&7Animations (&a" + anims.size() + "&7): &f" + joined + (anims.size() > 4 ? " &8+" + (anims.size() - 4) + " more" : ""));
                }
            }
            lore.add("&7Config: &f" + entry.getConfigPath());
            if (!entry.getAssetPaths().isEmpty()) {
                lore.add("&7Assets:");
                int shown = 0;
                for (String asset : entry.getAssetPaths()) {
                    if (shown >= 3) {
                        lore.add("  &8... +" + (entry.getAssetPaths().size() - 3) + " more");
                        break;
                    }
                    lore.add("  &8• &7" + asset);
                    shown++;
                }
            }
            lore.add("&8&m────────────────────────");
            lore.add("&eLeft-Click &8» &7Select on Wand");
            lore.add("&aShift+Left-Click &8» &7Select & Spawn Now");
            lore.add("&bRight-Click &8» &7Toggle &a★ Favorite");
            lore.add("&cShift+Right-Click &8» &7Toggle &c✖ Cleanup");

            meta.lore(ColorUtil.toItemLore(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            if (isCurrent) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            }
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack filterTab(Material mat, CuratorEntry.FilterMode mode, CuratorEntry.FilterMode active, int count) {
        boolean isActive = mode == active;
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String title = (isActive ? "&a&l[ACTIVE] " : "&7") + mode.getLabel() + " &8(&f" + count + "&8)";
            meta.displayName(ColorUtil.toItemComponent(title));
            meta.lore(ColorUtil.toItemLore(List.of(isActive ? "&aCurrently viewing this list." : "&eClick to switch filter.")));
            if (isActive) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack navButton(Material mat, String name, List<String> lore) {
        return button(mat, name, lore);
    }

    private ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.toItemComponent(name));
            meta.lore(ColorUtil.toItemLore(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!(e.getInventory().getHolder() instanceof CuratorHolder holder)) return;

        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        int slot = e.getSlot();
        if (slot < PAGE_SIZE) {
            CuratorEntry entry = holder.slotMap.get(slot);
            if (entry == null) return;

            if (e.isShiftClick() && e.isRightClick()) {
                manager.toggleCleanup(player, entry);
                open(player, holder.filterMode, holder.sourceFilter, holder.page);
            } else if (e.isRightClick()) {
                manager.toggleFavorite(player, entry);
                open(player, holder.filterMode, holder.sourceFilter, holder.page);
            } else if (e.isShiftClick() && e.isLeftClick()) {
                manager.setPlayerSourceFilter(player, holder.sourceFilter);
                manager.selectEntry(player, entry);
                player.closeInventory();
                manager.spawnSelected(player);
            } else if (e.isLeftClick()) {
                manager.setPlayerSourceFilter(player, holder.sourceFilter);
                manager.selectEntry(player, entry);
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.3f);
            }
            return;
        }

        switch (slot) {
            case 45 -> {
                if (holder.page > 0) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
                    open(player, holder.filterMode, holder.sourceFilter, holder.page - 1);
                }
            }
            case 46 -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                open(player, CuratorEntry.FilterMode.ALL, holder.sourceFilter, 0);
            }
            case 47 -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                open(player, CuratorEntry.FilterMode.FAVORITES, holder.sourceFilter, 0);
            }
            case 48 -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                open(player, CuratorEntry.FilterMode.CLEANUP, holder.sourceFilter, 0);
            }
            case 49 -> {
                CuratorEntry.SourceFilter nextSource = holder.sourceFilter.next();
                manager.setPlayerSourceFilter(player, nextSource);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.4f);
                open(player, holder.filterMode, nextSource, 0);
            }
            case 50 -> {
                manager.clearPlayerPreviews(player, true);
                open(player, holder.filterMode, holder.sourceFilter, holder.page);
            }
            case 51 -> {
                manager.rescanAll();
                manager.writeReports();
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
                player.sendMessage(ColorUtil.colorize("&a[Curator] Rescanned disk & exported reports to &fplugins/DCEssentials/curator/&a."));
                open(player, holder.filterMode, holder.sourceFilter, holder.page);
            }
            case 52 -> {
                if (e.isRightClick()) {
                    manager.toggleAutoAnimate(player);
                    open(player, holder.filterMode, holder.sourceFilter, holder.page);
                } else {
                    manager.cycleAnimationOnLatest(player);
                }
            }
            case 53 -> {
                int total = manager.countEntries(holder.filterMode, holder.sourceFilter);
                int totalPages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
                if (holder.page < totalPages - 1) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
                    open(player, holder.filterMode, holder.sourceFilter, holder.page + 1);
                }
            }
            default -> {}
        }
    }
}
