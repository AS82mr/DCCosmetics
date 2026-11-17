package dc.dccosmetics.listener;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.items.ItemModule;
import dc.dcessentials.modules.items.enchant.EnchantConfigManager;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemCategory;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.tooltip.TooltipMode;
import dc.dcessentials.modules.items.tooltip.TooltipRenderer;
import dc.dcessentials.modules.teleportation.KeystoneLoreTokens;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles vanilla enchantment drag-and-drop onto items, anvil combining up to
 * DCEssentials/enchants.yml caps, and refreshing DCE tooltips after enchanting table / anvil use.
 * Vanilla Enchanting Tables remain strictly vanilla-capped.
 */
public class EnchantmentDragListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();

        if (cursor == null || cursor.getType() != Material.ENCHANTED_BOOK) return;
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.ENCHANTED_BOOK) return;
        if (clicked.getAmount() != 1) return;

        ItemMeta rawBookMeta = cursor.getItemMeta();
        if (!(rawBookMeta instanceof EnchantmentStorageMeta bookMeta) || !bookMeta.hasStoredEnchants()) return;

        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta == null) return;

        boolean appliedAny = false;

        for (Map.Entry<Enchantment, Integer> entry : bookMeta.getStoredEnchants().entrySet()) {
            Enchantment enchant = entry.getKey();
            if (!EnchantConfigManager.isVanilla(enchant)) continue;
            int cap = EnchantConfigManager.getMaxLevel(enchant);
            int level = Math.min(entry.getValue(), cap);

            if (!enchant.canEnchantItem(clicked)) continue;

            boolean conflict = false;
            for (Enchantment existing : clickedMeta.getEnchants().keySet()) {
                if (existing.equals(enchant)) continue;
                if (enchant.conflictsWith(existing)) {
                    conflict = true;
                    break;
                }
            }
            if (conflict) continue;

            int existingLevel = clickedMeta.getEnchantLevel(enchant);
            int finalLevel;

            if (existingLevel == level && level < cap) {
                finalLevel = level + 1;
            } else if (existingLevel >= cap || existingLevel > level || (existingLevel == level && level >= cap)) {
                continue;
            } else {
                finalLevel = level;
            }

            clickedMeta.addEnchant(enchant, finalLevel, true);
            appliedAny = true;
        }

        if (appliedAny) {
            event.setCancelled(true);
            clicked.setItemMeta(clickedMeta);

            reRenderIfDceItem(clicked, player);

            cursor.setAmount(cursor.getAmount() - 1);
            player.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);

            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            DCCosmetics.getInstance().getLanguageManager().sendMessage(player, "enchant_forged");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack first = inv.getFirstItem();
        ItemStack second = inv.getSecondItem();
        if (first == null || first.getType().isAir()) return;

        ItemStack result = event.getResult();
        if (second != null && !second.getType().isAir()) {
            Map<Enchantment, Integer> donorEnchants = extractEnchants(second);
            if (!donorEnchants.isEmpty()) {
                Map<Enchantment, Integer> firstEnchants = extractEnchants(first);
                Map<Enchantment, Integer> boostedEnchants = new HashMap<>();

                for (Map.Entry<Enchantment, Integer> entry : donorEnchants.entrySet()) {
                    Enchantment ench = entry.getKey();
                    if (!EnchantConfigManager.isVanilla(ench)) continue;
                    int cap = EnchantConfigManager.getMaxLevel(ench);
                    int donorLvl = Math.min(entry.getValue(), cap);
                    int curLvl = firstEnchants.getOrDefault(ench, 0);

                    boolean targetOk = (first.getType() == Material.ENCHANTED_BOOK && second.getType() == Material.ENCHANTED_BOOK)
                            || ench.canEnchantItem(first);
                    if (!targetOk) continue;

                    boolean conflicts = false;
                    for (Enchantment existing : firstEnchants.keySet()) {
                        if (!existing.equals(ench) && ench.conflictsWith(existing)) {
                            conflicts = true;
                            break;
                        }
                    }
                    if (conflicts) continue;

                    int targetLvl = (curLvl == donorLvl && donorLvl < cap)
                            ? donorLvl + 1
                            : Math.max(curLvl, donorLvl);
                    if (targetLvl > ench.getMaxLevel() && targetLvl <= cap) {
                        boostedEnchants.put(ench, targetLvl);
                    }
                }

                if (!boostedEnchants.isEmpty()) {
                    if (result == null || result.getType().isAir()) {
                        result = first.clone();
                        if (inv.getRepairCost() <= 0) {
                            inv.setRepairCost(Math.max(5, boostedEnchants.size() * 4));
                        }
                    } else {
                        result = result.clone();
                    }
                    ItemMeta rMeta = result.getItemMeta();
                    if (rMeta instanceof EnchantmentStorageMeta storage) {
                        for (var e : boostedEnchants.entrySet()) {
                            storage.addStoredEnchant(e.getKey(), e.getValue(), true);
                        }
                        result.setItemMeta(storage);
                    } else if (rMeta != null) {
                        for (var e : boostedEnchants.entrySet()) {
                            rMeta.addEnchant(e.getKey(), e.getValue(), true);
                        }
                        result.setItemMeta(rMeta);
                    }
                }
            }
        }

        if (result != null && !result.getType().isAir()) {
            ItemStack updated = result.clone();
            if (reRenderIfDceItem(updated, null)) {
                event.setResult(updated);
            } else if (event.getResult() == null) {
                event.setResult(updated);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) return;

        ItemStack updated = result.clone();
        if (reRenderIfDceItem(updated, null)) {
            event.setResult(updated);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantTable(EnchantItemEvent event) {
        // Never modify vanilla enchanting table levels/caps; only refresh DCE tooltip 1 tick later.
        Inventory inv = event.getInventory();
        Player enchanter = event.getEnchanter();
        Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
            ItemStack item = inv.getItem(0);
            if (item != null && !item.getType().isAir() && reRenderIfDceItem(item, enchanter)) {
                inv.setItem(0, item);
            }
        });
    }

    private Map<Enchantment, Integer> extractEnchants(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Map.of();
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storage) {
            return storage.getStoredEnchants();
        }
        return meta != null ? meta.getEnchants() : Map.of();
    }

    private boolean reRenderIfDceItem(ItemStack item, Player viewer) {
        DCCosmetics plugin = DCCosmetics.getInstance();
        ItemModule itemMod = plugin != null ? plugin.getItemModule() : null;
        ItemRegistry reg = itemMod != null ? itemMod.getItemRegistry() : null;
        if (reg == null) return false;
        if (!reg.isDCEItem(item)) {
            if (ItemRegistry.isEligibleEquipment(item.getType())
                    && item.hasItemMeta()
                    && item.getItemMeta().hasEnchants()) {
                return reg.initializeDefaultEquipment(item);
            }
            return false;
        }
        String id = reg.getItemId(item);
        DCEItem dceItem = id != null ? reg.getItem(id) : null;
        if (dceItem == null
                || dceItem.getCategory() == ItemCategory.SCROLL
                || dceItem.getCategory() == ItemCategory.COSMETIC_SCROLL) {
            return false;
        }
        Player effectiveViewer = (viewer != null && KeystoneLoreTokens.itemUsesMcTokens(dceItem)) ? viewer : null;
        TooltipRenderer.renderItem(dceItem, item, itemMod.getScrollRegistry(), TooltipMode.COMPACT, effectiveViewer);
        return true;
    }
}
