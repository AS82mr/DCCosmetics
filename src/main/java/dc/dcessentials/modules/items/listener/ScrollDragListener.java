package dc.dcessentials.modules.items.listener;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.gui.DceGui;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.scroll.ScrollModel;
import dc.dcessentials.modules.items.scroll.ScrollRegistry;
import dc.dcessentials.modules.items.scroll.ScrollType;
import dc.dcessentials.modules.teleportation.KeystoneDetector;
import dc.dcessentials.modules.teleportation.KeystoneTier;
import dc.dcessentials.modules.teleportation.LegacyKeystoneConverter;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ScrollDragListener implements Listener {

    public static final NamespacedKey SOCKET_COMBAT_KEY = new NamespacedKey(DCCosmetics.getInstance(), "socket_combat");
    public static final NamespacedKey SOCKET_KEYSTONE_KEY = new NamespacedKey(DCCosmetics.getInstance(), "socket_keystone");
    public static final NamespacedKey SOCKET_MASTERY_KEY = new NamespacedKey(DCCosmetics.getInstance(), "socket_mastery");

    private final ScrollRegistry scrollRegistry;

    public ScrollDragListener(ScrollRegistry scrollRegistry) {
        this.scrollRegistry = scrollRegistry;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Task P2-05: Never consume or apply scrolls inside a DceGui (e.g. /profile clone or /keystone menu;
        // KeystoneMenu handles its own socket slots 20-22 directly against the real PlayerProfile item)
        if (event.getView().getTopInventory().getHolder() instanceof DceGui
                || (event.getClickedInventory() != null && event.getClickedInventory().getHolder() instanceof DceGui)) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();

        if (cursor == null || cursor.getType().isAir() || !cursor.hasItemMeta()) return;
        if (clicked == null || clicked.getType().isAir()) return;

        // Check if cursor is a registered scroll or legacy Skript Emperor scroll
        String scrollId = resolveScrollId(cursor);
        if (scrollId == null) return;

        ScrollModel scroll = scrollRegistry.getScroll(scrollId);
        if (scroll == null) return;

        // Convert legacy Keystone before applying scroll
        if (KeystoneDetector.isLegacy(clicked)) {
            clicked = LegacyKeystoneConverter.convert(clicked);
            event.setCurrentItem(clicked);
        }

        // Auto-initialize vanilla equipment into DCE item when dragged onto by a scroll
        var mod = DCCosmetics.getInstance().getItemModule();
        if (mod != null && !mod.getItemRegistry().isDCEItem(clicked)) {
            if (dc.dcessentials.modules.items.model.ItemRegistry.isEligibleEquipment(clicked.getType())) {
                mod.getItemRegistry().initializeDefaultEquipment(clicked);
            }
        }

        // Validate Compatibility
        if (!isCompatible(scroll, clicked)) {
            event.setCancelled(true);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 0.8f);
            DCMessage.sendError(player, "This scroll is only compatible with &e" + scroll.getTargetCategory().toLowerCase() + "&c!");
            return;
        }

        dc.dcessentials.modules.items.model.DCEItem dceItem = null;
        if (mod != null && mod.getItemRegistry().isDCEItem(clicked)) {
            String dceId = mod.getItemRegistry().getItemId(clicked);
            dceItem = mod.getItemRegistry().getItem(dceId);
            if (dceItem != null && !dceItem.getAllowedSockets().contains(scroll.getType())) {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 0.8f);
                DCMessage.sendError(player, "This item does not possess an &e" + scroll.getType().name().toLowerCase() + " socket&c!");
                return;
            }
        }

        // Apply Augment
        event.setCancelled(true);

        // Multi-slot Keystone socket handling (Task P2-04)
        if (scroll.getType() == ScrollType.KEYSTONE) {
            int capacity = dceItem != null
                    ? dceItem.getSocketCapacity(ScrollType.KEYSTONE)
                    : Math.max(1, KeystoneDetector.tierOf(clicked).getSockets());

            List<String> current = new ArrayList<>(KeystoneDetector.socketedScrolls(clicked));
            String newMechType = scroll.getMechanic("type", "");

            for (String existingId : current) {
                if (existingId.equalsIgnoreCase(scroll.getId())) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 0.8f);
                    DCMessage.sendWarning(player, "This Keystone already has " + scroll.getName() + " &#FDE047infused!");
                    return;
                }
                ScrollModel existingModel = scrollRegistry.getScroll(existingId);
                String oldMechType = existingModel != null ? existingModel.getMechanic("type", "") : "";
                if (!newMechType.isEmpty() && newMechType.equalsIgnoreCase(oldMechType)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 0.8f);
                    DCMessage.sendError(player, "This Keystone already has a &e" + newMechType.replace("KEYSTONE_", "") + " &caugment infused!");
                    return;
                }
            }

            if (current.size() >= capacity) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 0.8f);
                DCMessage.sendError(player, "All &e" + capacity + " &cKeystone sockets are occupied! Open &b/keystone &cto swap or Shift-Click to detach.");
                return;
            }

            current.add(scroll.getId());
            KeystoneDetector.writeSocketedScrolls(clicked, current);
            event.setCurrentItem(clicked);

            // Consume 1 scroll
            if (cursor.getAmount() > 1) {
                cursor.setAmount(cursor.getAmount() - 1);
                event.getView().setCursor(cursor);
            } else {
                event.getView().setCursor(null);
            }

            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.4f);
            DCMessage.sendGeneral(player, "Successfully infused " + scroll.getName() + " &#0CBF93into Keystone Socket #" + current.size() + "!");
            return;
        }

        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta == null) return;

        PersistentDataContainer pdc = clickedMeta.getPersistentDataContainer();
        NamespacedKey targetSocketKey = getSocketKey(scroll.getType());

        // Check if item already has this socket occupied
        if (targetSocketKey != null && pdc.has(targetSocketKey, PersistentDataType.STRING)) {
            String existing = pdc.get(targetSocketKey, PersistentDataType.STRING);
            if (existing != null) {
                if (existing.equalsIgnoreCase(scroll.getId())) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 0.8f);
                    DCMessage.sendWarning(player, "This item already has " + scroll.getName() + " &#FDE047infused!");
                    return;
                }
                // Detach and refund the previous scroll so it is never permanently lost
                ItemStack oldScroll = scrollRegistry.createScrollItem(existing, 1);
                if (oldScroll != null) {
                    player.getInventory().addItem(oldScroll).values().forEach(drop ->
                            player.getWorld().dropItemNaturally(player.getLocation(), drop));
                    DCMessage.sendInfo(player, "Previous infusion &#FCD34D" + existing + " &#BAE6FDwas detached and returned to your inventory.");
                }
            }
        }

        // Inscribe PDC
        if (targetSocketKey != null) {
            pdc.set(targetSocketKey, PersistentDataType.STRING, scroll.getId());
        }

        // Update Lore
        updateItemLoreWithSocket(clickedMeta, scroll);

        clicked.setItemMeta(clickedMeta);

        if (mod != null && mod.getItemRegistry().isDCEItem(clicked)) {
            String dceId = mod.getItemRegistry().getItemId(clicked);
            var dceItem = mod.getItemRegistry().getItem(dceId);
            if (dceItem != null) {
                dc.dcessentials.modules.items.tooltip.TooltipRenderer.renderItem(dceItem, clicked, scrollRegistry);
            }
        }

        // Consume 1 scroll
        if (cursor.getAmount() > 1) {
            cursor.setAmount(cursor.getAmount() - 1);
            event.getView().setCursor(cursor);
        } else {
            event.getView().setCursor(null);
        }

        // Sound & FX
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.4f);
        DCMessage.sendGeneral(player, "Successfully infused " + scroll.getName() + "&#0CBF93!");
    }

    private String resolveScrollId(ItemStack cursor) {
        // 1. Check Native PDC
        if (scrollRegistry.isScrollItem(cursor)) {
            return scrollRegistry.getScrollId(cursor);
        }

        // 2. Legacy Skript Lore Compatibility Bridge
        ItemMeta meta = cursor.getItemMeta();
        if (meta != null && meta.hasLore()) {
            String loreStr = String.join(" ", meta.getLore());
            if (loreStr.contains("ᴄʜʀᴏɴᴏ-ᴅɪꜱʀᴜᴘᴛɪᴏɴ ᴍᴀᴛʀɪx") || loreStr.contains("Chrono-Slip")) {
                return "chrono_slip";
            }
            if (loreStr.contains("ꜱᴏᴠᴇʀᴇɪɢɴ ʙᴀɴᴇ ɪɪɪ") || loreStr.contains("+35%")) {
                return "slayer_mythic";
            }
            if (loreStr.contains("ꜱᴏᴠᴇʀᴇɪɢɴ ʙᴀɴᴇ ɪɪ") || loreStr.contains("+20%")) {
                return "slayer_epic";
            }
            if (loreStr.contains("ꜱᴏᴠᴇʀᴇɪɢɴ ʙᴀɴᴇ ɪ") || loreStr.contains("+15%")) {
                return "slayer_rare";
            }
        }

        return null;
    }

    private boolean isCompatible(ScrollModel scroll, ItemStack target) {
        String cat = scroll.getTargetCategory().toUpperCase();
        Material mat = target.getType();
        String matName = mat.name();

        switch (cat) {
            case "MACE":
                return mat == Material.MACE;
            case "SWORD":
                return matName.endsWith("_SWORD");
            case "WEAPON":
                return matName.endsWith("_SWORD") || matName.endsWith("_AXE") || mat == Material.MACE ||
                        mat == Material.TRIDENT || mat == Material.BOW || mat == Material.CROSSBOW;
            case "TOOL":
                return matName.endsWith("_PICKAXE") || matName.endsWith("_AXE") ||
                        matName.endsWith("_SHOVEL") || matName.endsWith("_HOE");
            case "KEYSTONE":
                return KeystoneDetector.tierOf(target) != KeystoneTier.NONE;
            case "ALL":
                return true;
            default:
                return true;
        }
    }

    private NamespacedKey getSocketKey(ScrollType type) {
        switch (type) {
            case COMBAT: return SOCKET_COMBAT_KEY;
            case KEYSTONE: return SOCKET_KEYSTONE_KEY;
            case MASTERY: return SOCKET_MASTERY_KEY;
            default: return null;
        }
    }

    private void updateItemLoreWithSocket(ItemMeta meta, ScrollModel scroll) {
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        String displayTag = ColorUtil.colorize(scroll.getSocketDisplay());

        // Check if an existing socket line of this type exists (e.g. Emperor Slayer or Chrono-Slip)
        boolean replaced = false;
        String prefixMatch = getSocketPrefix(scroll.getId());

        if (prefixMatch != null) {
            for (int i = 0; i < lore.size(); i++) {
                String line = ChatColor.stripColor(lore.get(i)).toLowerCase();
                if (line.contains(prefixMatch.toLowerCase())) {
                    lore.set(i, displayTag);
                    replaced = true;
                    break;
                }
            }
        }

        if (!replaced) {
            // Append with clean blank divider
            if (!lore.isEmpty() && !lore.get(lore.size() - 1).isEmpty()) {
                lore.add("");
            }
            lore.add(displayTag);

            // If Chrono-Slip, add mechanic explanation lines matching dcemperor_items.sk
            if (scroll.getId().equalsIgnoreCase("chrono_slip")) {
                lore.add(ColorUtil.colorize("&7Builds kinetic charge with each attempt."));
                lore.add(ColorUtil.colorize("<#FCD34D>&l5th strike breaches Emperor's Vader Grip!"));
            }
        }

        meta.setLore(lore);
    }

    private String getSocketPrefix(String scrollId) {
        if (scrollId.startsWith("slayer_")) return "emperor slayer";
        if (scrollId.equalsIgnoreCase("chrono_slip")) return "chrono-slip";
        if (scrollId.startsWith("keystone_")) return "heatsink";
        if (scrollId.startsWith("mastery_")) return "mastery";
        return null;
    }
}
