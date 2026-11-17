package dc.dcessentials.modules.pity.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.modules.pity.PityCategory;
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

public class PityGUI implements Listener {

    private static final String TITLE = ColorUtil.colorize("&#38BDF8&lPity System &7| Game Utilities");

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        UUID u = player.getUniqueId();
        var pity = DCCosmetics.getInstance().getPityManager();

        // 1. Decorative border
        ItemStack grayGlass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 9; i++) inv.setItem(i, grayGlass);
        for (int i = 45; i < 54; i++) inv.setItem(i, grayGlass);
        inv.setItem(9, grayGlass);
        inv.setItem(17, grayGlass);
        inv.setItem(18, grayGlass);
        inv.setItem(26, grayGlass);
        inv.setItem(27, grayGlass);
        inv.setItem(35, grayGlass);
        inv.setItem(36, grayGlass);
        inv.setItem(44, grayGlass);

        // Header (Slot 4)
        List<String> headLore = new ArrayList<>();
        headLore.add("&7Separate from RNG Meter. Awards essential gameplay");
        headLore.add("&7utility items with dynamic odds scaling:");
        headLore.add("&#38BDF8  Odds = 1 / (Goal - Kills)");
        headLore.add("&7Guaranteed drop when counter reaches goal threshold!");
        inv.setItem(4, createItem(Material.NETHER_STAR, "&#38BDF8&lPity Progression Engine", headLore));

        // 2. Display Pity Categories in center slots (Slots 20, 22, 24, 31)
        int[] displaySlots = {20, 22, 24, 31};
        int idx = 0;

        for (PityCategory cat : pity.getCategories().values()) {
            if (idx >= displaySlots.length) break;

            int k = pity.getPlayerCounter(u, cat.getId());
            int n = cat.getMaxThreshold();
            int d = Math.max(1, n - k);
            int pct = pity.getPercent(player, cat.getId());
            String bar = pity.getProgressBar(player, cat.getId());

            List<String> lore = new ArrayList<>();
            lore.add("&8Gameplay Utility Droptable");
            lore.add("&f");
            lore.add("&7Target Item: " + cat.getDropName());
            if (cat.getEnergyBonus() > 0) {
                lore.add("&7Keystone Energy: &#0CBF93+" + cat.getEnergyBonus() + " Energy");
            }
            lore.add("&f");
            lore.add("&7Progress: &f" + k + " &7/ &f" + n);
            lore.add(bar + " &e(" + pct + "%)");
            lore.add("&7Current Odds: &#38BDF81/" + d + " &7per qualifying action");
            lore.add("&f");
            if (k >= n - 1) {
                lore.add("&#10B981&l★ GUARANTEED DROP READY");
                lore.add("&7Next action guarantees the drop!");
            } else {
                lore.add("&#0CBF93▶ IN PROGRESS");
                lore.add("&7Advance by performing this activity.");
            }

            inv.setItem(displaySlots[idx], createItem(
                    cat.getIcon(),
                    "&#38BDF8&l" + cat.getName(),
                    lore
            ));
            idx++;
        }

        // Close button (Slot 49)
        List<String> closeLore = new ArrayList<>();
        closeLore.add("&7Click to close this menu.");
        inv.setItem(49, createItem(Material.BARRIER, "&c&lClose Menu", closeLore));

        player.openInventory(inv);
    }

    public static void open(Player player, String ignored) {
        open(player);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle() == null || !event.getView().getTitle().equals(TITLE)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
        }
    }

    private static ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat != null ? mat : Material.STONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(ColorUtil.colorize(name));
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
