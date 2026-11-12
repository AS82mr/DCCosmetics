package dc.dcessentials.modules.rng.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.modules.pity.gui.PityGUI;
import dc.dcessentials.modules.rng.RngModel;
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

public class RngGUI implements Listener {

    private static final String TITLE_PREFIX = ColorUtil.colorize("&#D946EF&lRNG Meter &7| ");
    private static final Map<UUID, String> openCategory = new HashMap<>();

    public static void open(Player player, String categoryId) {
        String cat = (categoryId != null && !categoryId.isEmpty()) ? categoryId.toLowerCase() : "general";
        openCategory.put(player.getUniqueId(), cat);

        RngModel.Category category = DCCosmetics.getInstance().getRngManager().getCategories().get(cat);
        String catTitle = category != null ? category.getName() : "Global Drops";

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_PREFIX + catTitle);
        UUID u = player.getUniqueId();

        // 1. Decorative border
        ItemStack grayGlass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 9; i++) inv.setItem(i, grayGlass);
        for (int i = 45; i < 54; i++) inv.setItem(i, grayGlass);

        // 2. Category Selector Buttons in top row (Slots 1 to 7)
        int catSlot = 1;
        for (RngModel.Category c : DCCosmetics.getInstance().getRngManager().getCategories().values()) {
            if (catSlot > 7) break;
            boolean active = c.getId().equalsIgnoreCase(cat);
            List<String> lore = new ArrayList<>();
            lore.add("&8Category Drop Table");
            lore.add("&f");
            lore.add("&7" + c.getDescription());
            lore.add("&f");
            lore.add(active ? "&a▶ Currently Viewing" : "&#38BDF8▶ Click to View Drops");

            inv.setItem(catSlot, createItem(
                    c.getIcon(),
                    c.getHexColor() + "&l" + c.getName(),
                    lore
            ));
            catSlot++;
        }

        // 3. Populate drops for selected category (Slots 19 to 34)
        List<RngModel.Drop> drops = DCCosmetics.getInstance().getRngManager().getDropTables().get(cat);
        String currentSelectedDropId = DCCosmetics.getInstance().getRngManager().getSelectedDrop(u, cat);

        int[] dropSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int dIdx = 0;

        if (drops != null) {
            for (RngModel.Drop drop : drops) {
                if (dIdx >= dropSlots.length) break;

                boolean isSelected = drop.getDropId().equalsIgnoreCase(currentSelectedDropId);
                int currentXp = DCCosmetics.getInstance().getRngManager().getRngXp(u, drop.getDropId());
                int goalXp = drop.getGoalXp();
                int pct = (int) Math.round(((double) currentXp / goalXp) * 100);

                double meterMult = isSelected ? 1.0 + (2.0 * Math.min(1.0, (double) currentXp / goalXp)) : 1.0;
                double effectiveChance = drop.getBaseChancePct() * meterMult;

                // Progress Bar [■■■■■□□□□□]
                int filled = Math.min(10, Math.max(0, pct / 10));
                StringBuilder bar = new StringBuilder("&a");
                for (int i = 0; i < filled; i++) bar.append("■");
                bar.append("&7");
                for (int i = filled; i < 10; i++) bar.append("□");

                List<String> lore = new ArrayList<>();
                lore.add("&8Hypixel-Style Selectable Drop");
                lore.add("&f");
                lore.add("&7Base Probability: &f" + String.format("%.4f", drop.getBaseChancePct()) + "%");
                lore.add("&7Meter Multiplier: &#FBBF24" + String.format("%.2f", meterMult) + "x Odds");
                lore.add("&7Effective Chance: &#10B981" + String.format("%.4f", effectiveChance) + "%");
                lore.add("&f");
                lore.add("&7RNG Meter Progress: &f" + currentXp + " &7/ &f" + goalXp + " XP");
                lore.add(bar + " &e(" + pct + "%)");
                lore.add("&f");

                if (isSelected) {
                    lore.add("&#0CBF93&l✔ ACTIVE SELECTED DROP");
                    lore.add("&7All valid kills advance this meter!");
                } else {
                    lore.add("&#38BDF8▶ Click to Select as Target");
                }

                inv.setItem(dropSlots[dIdx], createItem(
                        drop.getIcon(),
                        drop.getDisplayName(),
                        lore
                ));
                dIdx++;
            }
        }

        // 4. Close button (Slot 49)
        List<String> closeLore = new ArrayList<>();
        closeLore.add("&7Click to close the RNG Meter.");
        inv.setItem(49, createItem(Material.BARRIER, "&c&lClose Menu", closeLore));

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle() == null || !event.getView().getTitle().startsWith(TITLE_PREFIX)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        UUID u = player.getUniqueId();
        String currentCat = openCategory.getOrDefault(u, "general");
        int slot = event.getRawSlot();

        // Close button (Slot 49)
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        // Category Switcher (Slots 1 to 7)
        if (slot >= 1 && slot <= 7) {
            int counter = 1;
            for (RngModel.Category c : DCCosmetics.getInstance().getRngManager().getCategories().values()) {
                if (counter == slot) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                    open(player, c.getId());
                    return;
                }
                counter++;
            }
            return;
        }

        // Click on a drop item to select it
        List<RngModel.Drop> drops = DCCosmetics.getInstance().getRngManager().getDropTables().get(currentCat);
        if (drops == null) return;

        int[] dropSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < dropSlots.length; i++) {
            if (dropSlots[i] == slot && i < drops.size()) {
                RngModel.Drop selected = drops.get(i);
                DCCosmetics.getInstance().getRngManager().setSelectedDrop(u, currentCat, selected.getDropId());

                player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.8f, 1.4f);
                dc.dcessentials.core.util.DCMessage.sendGeneral(player, "Attuned meter target to " + selected.getDisplayName() + "&#0CBF93!");

                // Re-render GUI
                open(player, currentCat);
                return;
            }
        }
    }

    private static ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            if (lore != null) {
                meta.setLore(ColorUtil.colorize(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
