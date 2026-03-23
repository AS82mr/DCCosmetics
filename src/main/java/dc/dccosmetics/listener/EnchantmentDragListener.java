package dc.dccosmetics.listener;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import dc.dccosmetics.DCCosmetics;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class EnchantmentDragListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();

        // Ensure cursor is an Enchanted Book and target is a physical item
        if (cursor == null || cursor.getType() != Material.ENCHANTED_BOOK) return;
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.ENCHANTED_BOOK) return;
        if (clicked.getAmount() != 1) return; // Don't allow enchanting stacks of items

        EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) cursor.getItemMeta();
        if (bookMeta == null || !bookMeta.hasStoredEnchants()) return;

        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta == null) return;

        boolean appliedAny = false;

        for (Map.Entry<Enchantment, Integer> entry : bookMeta.getStoredEnchants().entrySet()) {
            Enchantment enchant = entry.getKey();
            int level = entry.getValue();

            // 1. Target Validation: Is this item allowed to receive this enchant natively?
            if (!enchant.canEnchantItem(clicked)) continue;

            // 2. Conflict Validation: Does this enchant conflict with an existing one? (e.g., Sharpness vs Smite)
            boolean conflict = false;
            for (Enchantment existing : clickedMeta.getEnchants().keySet()) {
                if (existing.equals(enchant)) continue; // Handled in level logic below
                if (enchant.conflictsWith(existing)) {
                    conflict = true;
                    break;
                }
            }
            if (conflict) continue;

            // 3. Level Logic: Combine or overwrite levels just like a real anvil
            int existingLevel = clickedMeta.getEnchantLevel(enchant);
            int finalLevel = level;
            
            if (existingLevel == level && level < enchant.getMaxLevel()) {
                finalLevel = level + 1; // Combine identical levels to upgrade
            } else if (existingLevel > level) {
                continue; // The weapon's current enchant is already superior
            }

            clickedMeta.addEnchant(enchant, finalLevel, true);
            appliedAny = true;
        }

        if (appliedAny) {
            event.setCancelled(true);
            
            clicked.setItemMeta(clickedMeta);
            
            cursor.setAmount(cursor.getAmount() - 1);
            player.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);
            
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            DCCosmetics.getInstance().getLanguageManager().sendMessage(player, "enchant_forged");
        }
    }
}