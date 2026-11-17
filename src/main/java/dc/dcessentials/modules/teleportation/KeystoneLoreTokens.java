package dc.dcessentials.modules.teleportation;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemCategory;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.tooltip.TooltipMode;
import dc.dcessentials.modules.items.tooltip.TooltipRenderer;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves dynamic Keystone lore tokens ({ks:energy}, {ks:max}, {ks:micro}, {ks:heat})
 * and dynamic Microcharge ability cost tokens ({mc:...}) + debounced inventory refreshes.
 */
public final class KeystoneLoreTokens {

    /** Canonical lightning-style Unicode symbol for Microcharges (never render plain text "mC"). */
    public static final String MICRO_ICON = "\u21AF"; // ↯

    private static final Pattern MC_TOKEN_PATTERN = Pattern.compile("\\{mc:([a-zA-Z0-9_]+)\\}");
    private static final Map<UUID, BukkitTask> PENDING_REFRESH = new ConcurrentHashMap<>();

    private KeystoneLoreTokens() {}

    public static boolean containsTokens(String line) {
        if (line == null || line.isEmpty()) return false;
        return line.contains("{ks:energy}")
                || line.contains("{ks:max}")
                || line.contains("{ks:micro}")
                || line.contains("{ks:heat}");
    }

    public static boolean containsMcTokens(String line) {
        return line != null && !line.isEmpty() && line.contains("{mc:");
    }

    public static boolean itemUsesMcTokens(DCEItem dceItem) {
        if (dceItem == null || dceItem.getCustomLore() == null) return false;
        for (String line : dceItem.getCustomLore()) {
            if (containsMcTokens(line)) return true;
        }
        return false;
    }

    public static int resolveBaseMcCost(String key, TeleportationManager teleMgr) {
        if (key == null || key.isEmpty()) return 0;
        if ("void_bounce".equalsIgnoreCase(key)) {
            return teleMgr != null ? teleMgr.getVoidBounceCostMicro() : 75;
        }
        if ("sonic_boom".equalsIgnoreCase(key)) {
            return teleMgr != null ? teleMgr.getSonicBoomCostMicro() : 250;
        }
        try {
            return Math.max(0, Integer.parseInt(key));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static String resolveMcTokens(String line, Player viewer) {
        if (line == null || line.isEmpty() || !line.contains("{mc:")) return line;
        DCCosmetics plugin = DCCosmetics.getInstance();
        TeleportationManager teleMgr = plugin != null ? plugin.getTeleportationManager() : null;
        KeystoneEnergyService energySvc = teleMgr != null ? teleMgr.getEnergyService() : null;
        KeystoneProfile profile = (viewer != null && teleMgr != null) ? teleMgr.getProfile(viewer.getUniqueId()) : null;

        Matcher m = MC_TOKEN_PATTERN.matcher(line);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            int base = resolveBaseMcCost(key, teleMgr);
            int val = (viewer != null && energySvc != null && base > 0)
                    ? energySvc.effectiveCost(viewer, profile, base)
                    : base;
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(val)));
        }
        m.appendTail(sb);
        return sb.toString();
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
                TooltipRenderer.renderItem(
                        master, stack, itemMod.getScrollRegistry(),
                        mode != null ? mode : TooltipMode.COMPACT,
                        viewer);
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
     * Reverts any dynamic player-discounted {mc:...} lore on an ItemStack back to server base rate
     * (used whenever an item leaves the player's inventory to the world, a container, AH, or trade).
     */
    public static boolean revertToBaseRate(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return false;
        DCCosmetics plugin = DCCosmetics.getInstance();
        var itemMod = plugin != null ? plugin.getItemModule() : null;
        if (itemMod == null || itemMod.getItemRegistry() == null) return false;

        ItemRegistry reg = itemMod.getItemRegistry();
        if (!reg.isDCEItem(stack)) return false;
        String id = reg.getItemId(stack);
        DCEItem master = id != null ? reg.getItem(id) : null;
        if (master == null) return false;

        if (!itemUsesMcTokens(master) && KeystoneDetector.tierOf(stack) == KeystoneTier.NONE) {
            return false;
        }
        if (master.getCategory() == ItemCategory.SCROLL || master.getCategory() == ItemCategory.COSMETIC_SCROLL) {
            return false;
        }

        if (KeystoneDetector.tierOf(stack) != KeystoneTier.NONE) {
            applyToStack(stack, null, null, TooltipMode.COMPACT);
        } else {
            TooltipRenderer.renderItem(master, stack, itemMod.getScrollRegistry(), TooltipMode.COMPACT, null);
        }
        return true;
    }

    public static void revertInventoryDynamicMc(Player player) {
        if (player == null) return;
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack st = contents[i];
            if (st != null && revertToBaseRate(st)) {
                player.getInventory().setItem(i, st);
                changed = true;
            }
        }
        if (changed) {
            player.updateInventory();
        }
    }

    /**
     * Schedules a 10-tick debounced inventory/GUI refresh if the player holds a physical keystone,
     * dynamic-mC DCE item, or currently has KeystoneMenu open (Task P2-08).
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
            if (!player.getItemOnCursor().getType().isAir()) return;

            var teleMgr = plugin.getTeleportationManager();
            var itemMod = plugin.getItemModule();
            ItemRegistry reg = itemMod != null ? itemMod.getItemRegistry() : null;
            var scrollReg = itemMod != null ? itemMod.getScrollRegistry() : null;
            KeystoneProfile kProfile = teleMgr != null ? teleMgr.getProfile(player.getUniqueId()) : null;

            ItemStack[] contents = player.getInventory().getContents();
            boolean modified = false;
            for (int i = 0; i < contents.length; i++) {
                ItemStack st = contents[i];
                if (st == null || st.getType().isAir()) continue;
                if (KeystoneDetector.isKeystone(st)) {
                    if (reg != null) {
                        reg.updateItemToLatest(st);
                    }
                    applyToStack(st, player, kProfile);
                    player.getInventory().setItem(i, st);
                    modified = true;
                } else if (reg != null && reg.isDCEItem(st)) {
                    boolean itemModified = reg.updateItemToLatest(st);
                    String id = reg.getItemId(st);
                    DCEItem master = id != null ? reg.getItem(id) : null;
                    if (master != null && itemUsesMcTokens(master)) {
                        TooltipRenderer.renderItem(master, st, scrollReg, TooltipMode.COMPACT, player);
                        itemModified = true;
                    }
                    if (itemModified) {
                        player.getInventory().setItem(i, st);
                        modified = true;
                    }
                }
            }
            if (modified) {
                player.updateInventory();
            }
        }, 4L);

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
