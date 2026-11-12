package dc.dcessentials.modules.items.hook;

import com.ssomar.score.api.executableitems.ExecutableItemsAPI;
import com.ssomar.score.api.executableitems.config.ExecutableItemInterface;
import com.ssomar.score.api.executableitems.config.ExecutableItemObjectInterface;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

public class ExecutableItemsHook {

    private static final NamespacedKey EI_ID_KEY = new NamespacedKey("executableitems", "ei-id");

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("ExecutableItems");
    }

    /**
     * Inspects an item stack to detect if it is backed by ExecutableItems.
     * Checks both the SCore ExecutableItemsAPI and direct PDC tags.
     */
    public static String getExecutableItemId(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;

        // 1. Check API
        if (isAvailable()) {
            try {
                ExecutableItemObjectInterface obj = ExecutableItemsAPI.getExecutableItemObject(item);
                if (obj != null && obj.isValid() && obj.getConfig() != null) {
                    return obj.getConfig().getId();
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. Fallback to direct PDC inspection
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                if (pdc.has(EI_ID_KEY, PersistentDataType.STRING)) {
                    return pdc.get(EI_ID_KEY, PersistentDataType.STRING);
                }
            }
        }

        return null;
    }

    /**
     * Builds a fresh instance of the ExecutableItem with all triggers, commands, cooldowns,
     * and NBT intact.
     */
    public static ItemStack buildExecutableItem(String eiId, int amount) {
        if (!isAvailable() || eiId == null) return null;

        try {
            Optional<ExecutableItemInterface> opt = ExecutableItemsAPI.getExecutableItemsManager().getExecutableItem(eiId);
            if (opt.isPresent()) {
                ItemStack built = opt.get().buildItem(Math.max(1, amount), Optional.empty());
                if (built != null) {
                    return built;
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }
}
