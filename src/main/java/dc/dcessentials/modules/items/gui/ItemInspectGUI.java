package dc.dcessentials.modules.items.gui;

import dc.dcessentials.core.gui.DceGui;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.scroll.ScrollRegistry;
import dc.dcessentials.modules.items.tooltip.TooltipMode;
import dc.dcessentials.modules.items.tooltip.TooltipRenderer;
import dc.dcessentials.modules.teleportation.KeystoneDetector;
import dc.dcessentials.modules.teleportation.KeystoneLoreTokens;
import dc.dccosmetics.DCCosmetics;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ItemInspectGUI implements DceGui {

    private final Inventory inventory;

    public ItemInspectGUI(ItemStack preview) {
        this.inventory = Bukkit.createInventory(this, 27, ColorUtil.colorize("<#1E293B>&lDetailed Item Inspector"));
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.setDisplayName(ColorUtil.colorize("&7"));
            filler.setItemMeta(fm);
        }
        for (int i = 0; i < 27; i++) {
            this.inventory.setItem(i, filler);
        }
        this.inventory.setItem(13, preview);
    }

    public static void open(Player player, ItemRegistry itemRegistry, ScrollRegistry scrollRegistry) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir() || !itemRegistry.isDCEItem(hand)) {
            DCMessage.sendError(player, "Hold a Dragoncote item in your main hand to inspect it.");
            return;
        }
        String id = itemRegistry.getItemId(hand);
        DCEItem dceItem = itemRegistry.getItem(id);
        ItemStack clone = hand.clone();
        if (dceItem != null) {
            TooltipRenderer.renderItem(dceItem, clone, scrollRegistry, TooltipMode.EXPANDED);
        }
        if (KeystoneDetector.isKeystone(clone)) {
            var teleMgr = DCCosmetics.getInstance().getTeleportationManager();
            var kProfile = teleMgr != null ? teleMgr.getProfile(player) : null;
            KeystoneLoreTokens.bakeIntoItem(clone, player, kProfile);
        }
        ItemMeta meta = clone.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null) {
                lore.add("");
                lore.add(ColorUtil.colorize("&8[Expanded Inspector View]"));
                meta.setLore(lore);
                clone.setItemMeta(meta);
            }
        }
        player.openInventory(new ItemInspectGUI(clone).getInventory());
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
