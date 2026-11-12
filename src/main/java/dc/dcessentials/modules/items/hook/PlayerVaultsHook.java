package dc.dcessentials.modules.items.hook;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.tracker.ItemLocationRecord;
import dc.dcessentials.modules.items.tracker.ItemTrackerManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerVaultsHook {

    private static Plugin pvPlugin = null;
    private static Object vaultManagerInstance = null;
    private static Method getVaultMethod = null;
    private static Method saveVaultMethod = null;
    private static boolean methodTakesString = false;
    private static boolean saveTakesInvFirst = true;
    private static boolean initialized = false;

    public static boolean isAvailable() {
        if (pvPlugin != null && pvPlugin.isEnabled()) return true;
        pvPlugin = Bukkit.getPluginManager().getPlugin("PlayerVaults");
        if (pvPlugin == null) {
            pvPlugin = Bukkit.getPluginManager().getPlugin("PlayerVaultsX");
        }
        return pvPlugin != null && pvPlugin.isEnabled();
    }

    private static synchronized void initReflection() {
        if (initialized && vaultManagerInstance != null) return;
        initialized = true;

        String[] classCandidates = {
                "com.drtshock.playervaults.vaultmanagement.UUIDVaultManager",
                "com.drtshock.playervaults.vaultmanagement.VaultManager",
                "com.drtshock.playervaults.PlayerVaults"
        };

        Class<?> mgrClass = null;
        for (String cName : classCandidates) {
            try {
                mgrClass = Class.forName(cName);
                break;
            } catch (ClassNotFoundException ignored) {}
        }

        if (mgrClass == null) {
            DCCosmetics.getInstance().getLogger().warning("[DCEssentials] Could not resolve PlayerVaults VaultManager class!");
            return;
        }

        try {
            // Find getInstance or static field
            for (Method m : mgrClass.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0 && m.getReturnType().isAssignableFrom(mgrClass)) {
                    vaultManagerInstance = m.invoke(null);
                    break;
                }
            }
        } catch (Throwable t) {
            DCCosmetics.getInstance().getLogger().log(Level.WARNING, "[DCEssentials] Error getting VaultManager instance: " + t.getMessage());
        }

        if (vaultManagerInstance == null) {
            vaultManagerInstance = pvPlugin;
        }

        // Resolve get/load vault method
        String[] getNames = {"loadOtherVault", "loadVault", "getVault", "getInventory", "loadOwnVault"};
        for (Method m : mgrClass.getMethods()) {
            for (String gName : getNames) {
                if (m.getName().equalsIgnoreCase(gName)) {
                    Class<?>[] pTypes = m.getParameterTypes();
                    if (pTypes.length == 2 && int.class.isAssignableFrom(pTypes[1])) {
                        if (UUID.class.isAssignableFrom(pTypes[0])) {
                            getVaultMethod = m;
                            methodTakesString = false;
                            break;
                        } else if (String.class.isAssignableFrom(pTypes[0])) {
                            getVaultMethod = m;
                            methodTakesString = true;
                            break;
                        }
                    }
                }
            }
            if (getVaultMethod != null) break;
        }

        // Resolve save vault method
        for (Method m : mgrClass.getMethods()) {
            if (m.getName().toLowerCase().contains("save")) {
                Class<?>[] pTypes = m.getParameterTypes();
                if (pTypes.length == 3) {
                    if (Inventory.class.isAssignableFrom(pTypes[0])) {
                        saveVaultMethod = m;
                        saveTakesInvFirst = true;
                        break;
                    } else if (Inventory.class.isAssignableFrom(pTypes[2])) {
                        saveVaultMethod = m;
                        saveTakesInvFirst = false;
                        break;
                    }
                }
            }
        }

        DCCosmetics.getInstance().getLogger().info("[DCEssentials] PlayerVaults hook initialized: mgr=" +
                (vaultManagerInstance != null ? vaultManagerInstance.getClass().getSimpleName() : "null") +
                ", getVault=" + (getVaultMethod != null ? getVaultMethod.getName() : "null") +
                ", saveVault=" + (saveVaultMethod != null ? saveVaultMethod.getName() : "null"));
    }

    public static Inventory getVault(UUID playerUuid, int vaultNumber) {
        if (!isAvailable() || playerUuid == null) return null;
        initReflection();

        if (getVaultMethod != null && vaultManagerInstance != null) {
            try {
                Object arg0 = methodTakesString ? playerUuid.toString() : playerUuid;
                Object res = getVaultMethod.invoke(vaultManagerInstance, arg0, vaultNumber);
                if (res instanceof Inventory inv) return inv;
            } catch (Throwable t) {
                DCCosmetics.getInstance().getLogger().warning("[DCEssentials] Error invoking getVault: " + t.getMessage());
            }
        }

        // Flatfile fallback if reflection failed
        return getVaultFromFlatfile(playerUuid, vaultNumber);
    }

    public static boolean saveVault(UUID playerUuid, int vaultNumber, Inventory inv) {
        if (!isAvailable() || playerUuid == null || inv == null) return false;
        initReflection();

        if (saveVaultMethod != null && vaultManagerInstance != null) {
            try {
                Object uArg = methodTakesString ? playerUuid.toString() : playerUuid;
                if (saveTakesInvFirst) {
                    saveVaultMethod.invoke(vaultManagerInstance, inv, uArg, vaultNumber);
                } else {
                    saveVaultMethod.invoke(vaultManagerInstance, uArg, vaultNumber, inv);
                }
                return true;
            } catch (Throwable t) {
                DCCosmetics.getInstance().getLogger().warning("[DCEssentials] Error invoking saveVault: " + t.getMessage());
            }
        }

        // Flatfile save fallback
        return saveVaultToFlatfile(playerUuid, vaultNumber, inv);
    }

    private static File getFlatfile(UUID playerUuid) {
        File pvDir = new File(Bukkit.getWorldContainer(), "plugins/PlayerVaults");
        if (!pvDir.exists()) pvDir = new File("plugins/PlayerVaults");
        File vaultsDir = new File(pvDir, "vaults");
        if (vaultsDir.exists()) {
            File f = new File(vaultsDir, playerUuid.toString() + ".yml");
            if (f.exists()) return f;
        }
        File direct = new File(pvDir, playerUuid.toString() + ".yml");
        if (direct.exists()) return direct;
        return new File(vaultsDir, playerUuid.toString() + ".yml");
    }

    private static Inventory getVaultFromFlatfile(UUID playerUuid, int vaultNumber) {
        try {
            File file = getFlatfile(playerUuid);
            if (!file.exists()) return null;
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            String secKey = "vault" + vaultNumber;
            if (!cfg.contains(secKey)) return null;

            int size = cfg.getInt(secKey + ".size", 54);
            Inventory inv = Bukkit.createInventory(null, size, "Vault #" + vaultNumber);
            for (int i = 0; i < size; i++) {
                if (cfg.contains(secKey + "." + i)) {
                    ItemStack st = cfg.getItemStack(secKey + "." + i);
                    if (st != null) inv.setItem(i, st);
                }
            }
            return inv;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean saveVaultToFlatfile(UUID playerUuid, int vaultNumber, Inventory inv) {
        try {
            File file = getFlatfile(playerUuid);
            file.getParentFile().mkdirs();
            YamlConfiguration cfg = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
            String secKey = "vault" + vaultNumber;
            cfg.set(secKey + ".size", inv.getSize());
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack st = inv.getItem(i);
                cfg.set(secKey + "." + i, st);
            }
            cfg.save(file);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static int removeSingleItemFromVault(UUID playerUuid, int vaultNumber, String itemId, String instanceUuid, ItemRegistry registry) {
        Inventory inv = getVault(playerUuid, vaultNumber);
        if (inv == null) return 0;

        int removed = 0;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && registry.isDCEItem(stack) && itemId.equalsIgnoreCase(registry.getItemId(stack))) {
                if (instanceUuid == null || instanceUuid.isEmpty() ||
                        instanceUuid.equals(stack.getItemMeta().getPersistentDataContainer().get(ItemTrackerManager.INSTANCE_UUID_KEY, org.bukkit.persistence.PersistentDataType.STRING))) {
                    removed = stack.getAmount();
                    inv.setItem(i, null);
                    break; // Remove only this single stack!
                }
            }
        }

        if (removed > 0) {
            saveVault(playerUuid, vaultNumber, inv);
        }
        return removed;
    }

    public static int removeAllItemFromVault(UUID playerUuid, int vaultNumber, String itemId, ItemRegistry registry) {
        Inventory inv = getVault(playerUuid, vaultNumber);
        if (inv == null) return 0;

        int removed = 0;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && registry.isDCEItem(stack) && itemId.equalsIgnoreCase(registry.getItemId(stack))) {
                removed += stack.getAmount();
                inv.setItem(i, null);
            }
        }

        if (removed > 0) {
            saveVault(playerUuid, vaultNumber, inv);
        }
        return removed;
    }

    public static void scanPlayerVaults(Player player, String itemId, ItemRegistry registry, ItemTrackerManager trackerManager) {
        if (!isAvailable() || player == null || trackerManager == null) return;
        UUID uuid = player.getUniqueId();

        // Scan vaults 1 through 10
        for (int v = 1; v <= 10; v++) {
            try {
                Inventory inv = getVault(uuid, v);
                if (inv == null) continue;
                for (ItemStack st : inv.getContents()) {
                    if (st != null && registry.isDCEItem(st) && (itemId == null || itemId.equalsIgnoreCase(registry.getItemId(st)))) {
                        trackerManager.recordItem(st, player, ItemLocationRecord.LocationType.PLAYER_VAULT, null, v);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }
}
