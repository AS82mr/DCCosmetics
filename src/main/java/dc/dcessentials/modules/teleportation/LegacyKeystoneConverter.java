package dc.dcessentials.modules.teleportation;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.items.hook.PlayerVaultsHook;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.tooltip.TooltipRenderer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.UUID;

/**
 * Converts legacy ExecutableItems keystones/cubes and legacy astral_tesseract
 * into first-class native DCE items (Task P1-04).
 */
public class LegacyKeystoneConverter {

    private static final String[] WHITELIST_STRING_KEYS = {
            "socket_keystone",
            "socket_keystone_slots",
            "socket_combat",
            "socket_mastery",
            "cosmetic_id",
            "cosmetic_color"
    };

    public static Optional<ItemStack> convert(ItemStack item) {
        if (!KeystoneDetector.isLegacy(item)) {
            return Optional.empty();
        }

        String targetId = null;
        if (KeystoneDetector.isEnergyCube(item)) {
            targetId = "tesseract_cube";
        } else {
            KeystoneTier tier = KeystoneDetector.tierOf(item);
            if (tier != KeystoneTier.NONE) {
                targetId = tier.getItemId();
            }
        }
        if (targetId == null) {
            return Optional.empty();
        }

        var itemModule = DCCosmetics.getInstance().getItemModule();
        if (itemModule == null || itemModule.getItemRegistry() == null) {
            return Optional.empty();
        }

        ItemRegistry registry = itemModule.getItemRegistry();
        int amount = Math.max(1, item.getAmount());
        ItemStack converted = registry.createItemStack(targetId, amount);
        if (converted == null) {
            return Optional.empty();
        }

        ItemMeta oldMeta = item.getItemMeta();
        ItemMeta newMeta = converted.getItemMeta();
        boolean copiedSocketsOrCosmetics = false;

        if (oldMeta != null && newMeta != null) {
            PersistentDataContainer oldPdc = oldMeta.getPersistentDataContainer();
            PersistentDataContainer newPdc = newMeta.getPersistentDataContainer();

            // Preserve item_uuid if present for tracker continuity
            if (oldPdc.has(ItemRegistry.UUID_KEY, PersistentDataType.STRING)) {
                String oldUuid = oldPdc.get(ItemRegistry.UUID_KEY, PersistentDataType.STRING);
                if (oldUuid != null && !oldUuid.isEmpty()) {
                    newPdc.set(ItemRegistry.UUID_KEY, PersistentDataType.STRING, oldUuid);
                }
            }

            // Copy whitelisted socket & cosmetic keys
            for (String keyName : WHITELIST_STRING_KEYS) {
                NamespacedKey key = new NamespacedKey(DCCosmetics.getInstance(), keyName);
                if (oldPdc.has(key, PersistentDataType.STRING)) {
                    String val = oldPdc.get(key, PersistentDataType.STRING);
                    if (val != null && !val.isEmpty()) {
                        newPdc.set(key, PersistentDataType.STRING, val);
                        copiedSocketsOrCosmetics = true;
                    }
                }
            }

            // Migrate single socket_keystone to socket_keystone_slots if needed
            if (newPdc.has(KeystoneDetector.SOCKET_KEYSTONE_KEY, PersistentDataType.STRING)
                    && !newPdc.has(KeystoneDetector.SOCKET_KEYSTONE_SLOTS_KEY, PersistentDataType.STRING)) {
                String sId = newPdc.get(KeystoneDetector.SOCKET_KEYSTONE_KEY, PersistentDataType.STRING);
                if (sId != null && !sId.isEmpty()) {
                    newPdc.set(KeystoneDetector.SOCKET_KEYSTONE_SLOTS_KEY, PersistentDataType.STRING, sId);
                }
            }

            newPdc.set(KeystoneDetector.KS_CONVERTED_KEY, PersistentDataType.BYTE, (byte) 1);
            converted.setItemMeta(newMeta);
        }

        if (copiedSocketsOrCosmetics) {
            DCEItem dceItem = registry.getItem(targetId);
            if (dceItem != null) {
                TooltipRenderer.renderItem(dceItem, converted, itemModule.getScrollRegistry());
            }
        }

        return Optional.of(converted);
    }

    public static boolean convertInventory(Inventory inv) {
        if (inv == null) return false;
        boolean changed = false;
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            Optional<ItemStack> converted = convert(item);
            if (converted.isPresent()) {
                inv.setItem(i, converted.get());
                changed = true;
            }
        }
        return changed;
    }

    public static void convertPlayerInventories(Player player) {
        if (player == null) return;
        convertInventory(player.getInventory());
        convertInventory(player.getEnderChest());
    }

    public static void convertPlayerVaults(Player player) {
        if (player == null || !PlayerVaultsHook.isAvailable()) return;
        UUID uuid = player.getUniqueId();
        for (int v = 1; v <= 10; v++) {
            try {
                Inventory vault = PlayerVaultsHook.getVault(uuid, v);
                if (vault != null && convertInventory(vault)) {
                    PlayerVaultsHook.saveVault(uuid, v, vault);
                }
            } catch (Throwable ignored) {}
        }
    }
}
