package dc.dcessentials.modules.teleportation;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.modules.teleportation.gui.KeystoneMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves dynamic Keystone lore tokens ({ks:energy}, {ks:max}, {ks:micro}, {ks:heat})
 * and manages debounced inventory refreshes (Task P2-08).
 */
public final class KeystoneLoreTokens {

    private static final Map<UUID, BukkitTask> PENDING_REFRESH = new ConcurrentHashMap<>();

    private KeystoneLoreTokens() {}

    public static boolean containsTokens(String line) {
        if (line == null || line.isEmpty()) return false;
        return line.contains("{ks:energy}")
                || line.contains("{ks:max}")
                || line.contains("{ks:micro}")
                || line.contains("{ks:heat}");
    }

    public static List<String> resolve(List<String> lines, KeystoneProfile profile) {
        int max = (profile != null && profile.getTier() != KeystoneTier.NONE)
                ? profile.getTier().getMaxEnergy()
                : 200;
        return resolve(lines, profile, max);
    }

    public static List<String> resolve(List<String> lines, KeystoneProfile profile, int effectiveMax) {
        if (lines == null || lines.isEmpty()) return lines;
        int energy = profile != null ? profile.getEnergy() : 0;
        int micro = profile != null ? profile.getMicro() : 0;
        int heat = profile != null ? profile.getHeat() : 0;
        int max = Math.max(0, effectiveMax);

        String energyStr = String.valueOf(energy);
        String maxStr = String.valueOf(max);
        String microStr = String.valueOf(micro);
        String heatStr = String.valueOf(heat);

        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (line == null) {
                out.add(null);
                continue;
            }
            if (containsTokens(line)) {
                String replaced = line
                        .replace("{ks:energy}", energyStr)
                        .replace("{ks:max}", maxStr)
                        .replace("{ks:micro}", microStr)
                        .replace("{ks:heat}", heatStr);
                out.add(ColorUtil.colorize(replaced));
            } else {
                out.add(line);
            }
        }
        return out;
    }

    /**
     * Resolves {ks:*} tokens on an ItemStack in-place (used on display clones in ProfileGUI / KeystoneMenu,
     * or in degraded mode when ProtocolLib packet rewriting is unavailable).
     */
    public static void bakeIntoItem(ItemStack stack, Player viewer, KeystoneProfile profile) {
        applyToStack(stack, viewer, profile, dc.dcessentials.modules.items.tooltip.TooltipMode.COMPACT);
    }

    public static void bakeIntoItem(ItemStack stack, Player viewer, KeystoneProfile profile,
                                    dc.dcessentials.modules.items.tooltip.TooltipMode mode) {
        applyToStack(stack, viewer, profile, mode);
    }

    public static void applyToStack(ItemStack stack, Player viewer, KeystoneProfile profile) {
        applyToStack(stack, viewer, profile, dc.dcessentials.modules.items.tooltip.TooltipMode.COMPACT);
    }

    public static void applyToStack(ItemStack stack, Player viewer, KeystoneProfile profile,
                                    dc.dcessentials.modules.items.tooltip.TooltipMode mode) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return;
        KeystoneTier itemTier = KeystoneDetector.tierOf(stack);
        if (itemTier == KeystoneTier.NONE) return;

        // Re-render base lore from master DCEItem template first so previously baked lore can still update
        var plugin = DCCosmetics.getInstance();
        var itemMod = plugin != null ? plugin.getItemModule() : null;
        if (itemMod != null && itemMod.getItemRegistry() != null) {
            String itemId = itemMod.getItemRegistry().getItemId(stack);
            var master = itemId != null ? itemMod.getItemRegistry().getItem(itemId) : null;
            if (master != null) {
                dc.dcessentials.modules.items.tooltip.TooltipRenderer.renderItem(
                        master, stack, itemMod.getScrollRegistry(),
                        mode != null ? mode : dc.dcessentials.modules.items.tooltip.TooltipMode.COMPACT);
            }
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasLore()) return;
        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) return;

        boolean hasAny = false;
        for (String l : lore) {
            if (containsTokens(l)) {
                hasAny = true;
                break;
            }
        }
        if (!hasAny) return;

        int effectiveMax = computeDisplayMax(stack, viewer, profile, itemTier);
        List<String> resolved = resolve(lore, profile, effectiveMax);

        List<Component> compLore = new ArrayList<>(resolved.size());
        for (String line : resolved) {
            if (line == null || line.trim().isEmpty()) {
                compLore.add(Component.text(" "));
            } else {
                compLore.add(LegacyComponentSerializer.legacySection().deserialize(line)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(compLore);
        stack.setItemMeta(meta);
    }

    public static int computeDisplayMax(ItemStack stack, Player viewer, KeystoneProfile profile, KeystoneTier itemTier) {
        var teleMgr = DCCosmetics.getInstance().getTeleportationManager();
        if (viewer != null && profile != null && profile.getTier() != KeystoneTier.NONE && teleMgr != null && teleMgr.getEnergyService() != null) {
            return teleMgr.getEnergyService().getEffectiveMaxEnergy(viewer, profile);
        }
        int baseMax = itemTier != null && itemTier != KeystoneTier.NONE ? itemTier.getMaxEnergy() : 200;
        int capBonus = KeystoneDetector.getScrollMaxEnergyBonus(stack);
        return Math.min(800, baseMax + capBonus);
    }

    public static String formatProgressBar(int current, int max, String filledColor) {
        int pct = max > 0 ? (int) Math.min(100, Math.max(0, Math.round((current * 100.0) / max))) : 0;
        int filled = Math.min(10, Math.max(0, pct / 10));
        StringBuilder sb = new StringBuilder(filledColor);
        for (int i = 0; i < filled; i++) sb.append("■");
        sb.append("&8");
        for (int i = filled; i < 10; i++) sb.append("□");
        return ColorUtil.colorize(sb.toString());
    }

    public static String formatMicroBar(int micro) {
        return formatProgressBar(micro, KeystoneProfile.MICRO_PER_CHARGE, "&#38BDF8");
    }

    /**
     * Schedules a 10-tick debounced inventory/GUI refresh if the player holds a physical keystone
     * or currently has KeystoneMenu open (Task P2-08).
     */
    public static void scheduleInventoryRefresh(Player player) {
        if (player == null || !player.isOnline()) return;
        DCCosmetics plugin = DCCosmetics.getInstance();
        if (plugin == null || !plugin.isEnabled()) return;

        UUID uuid = player.getUniqueId();
        if (PENDING_REFRESH.containsKey(uuid)) {
            return;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PENDING_REFRESH.remove(uuid);
            if (!player.isOnline()) return;

            if (player.getOpenInventory().getTopInventory().getHolder() instanceof KeystoneMenu menu) {
                menu.refresh();
            }

            // Only bake into player inventory slots when cursor is empty to prevent HashedStack cursor desync
            if (player.getItemOnCursor().getType().isAir() && carriesPhysicalKeystone(player)) {
                var teleMgr = plugin.getTeleportationManager();
                KeystoneProfile kProfile = teleMgr != null ? teleMgr.getProfile(player.getUniqueId()) : null;
                ItemStack[] contents = player.getInventory().getContents();
                boolean modified = false;
                for (int i = 0; i < contents.length; i++) {
                    ItemStack st = contents[i];
                    if (st != null && !st.getType().isAir() && KeystoneDetector.isKeystone(st)) {
                        applyToStack(st, player, kProfile);
                        player.getInventory().setItem(i, st);
                        modified = true;
                    }
                }
                if (modified) {
                    player.updateInventory();
                }
            }
        }, 10L);

        PENDING_REFRESH.put(uuid, task);
    }

    public static boolean carriesPhysicalKeystone(Player player) {
        if (player == null) return false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir() && KeystoneDetector.tierOf(item) != KeystoneTier.NONE) {
                return true;
            }
        }
        return false;
    }
}
