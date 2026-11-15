package dc.dcessentials.modules.teleportation;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.scroll.ScrollModel;
import dc.dcessentials.modules.items.scroll.ScrollRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class KeystoneDetector {

    public static final NamespacedKey EI_ID_KEY = new NamespacedKey("executableitems", "ei-id");
    public static final NamespacedKey SOCKET_KEYSTONE_KEY = new NamespacedKey(DCCosmetics.getInstance(), "socket_keystone");
    public static final NamespacedKey SOCKET_KEYSTONE_SLOTS_KEY = new NamespacedKey(DCCosmetics.getInstance(), "socket_keystone_slots");
    public static final NamespacedKey KS_CONVERTED_KEY = new NamespacedKey(DCCosmetics.getInstance(), "ks_converted");

    public static List<String> socketedScrolls(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Collections.emptyList();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Collections.emptyList();

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(SOCKET_KEYSTONE_SLOTS_KEY, PersistentDataType.STRING)) {
            String raw = pdc.get(SOCKET_KEYSTONE_SLOTS_KEY, PersistentDataType.STRING);
            if (raw != null && !raw.isBlank()) {
                List<String> out = new ArrayList<>(3);
                for (String part : raw.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        out.add(trimmed);
                    }
                }
                return Collections.unmodifiableList(out);
            }
        }
        if (pdc.has(SOCKET_KEYSTONE_KEY, PersistentDataType.STRING)) {
            String single = pdc.get(SOCKET_KEYSTONE_KEY, PersistentDataType.STRING);
            if (single != null && !single.isBlank()) {
                return List.of(single.trim());
            }
        }
        return Collections.emptyList();
    }

    public static String getSocketedKeystoneScrollId(ItemStack item) {
        List<String> list = socketedScrolls(item);
        return list.isEmpty() ? null : list.get(0);
    }

    public static void writeSocketedScrolls(ItemStack item, List<String> scrollIds) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<String> cleaned = new ArrayList<>();
        if (scrollIds != null) {
            for (String s : scrollIds) {
                if (s != null && !s.isBlank()) {
                    cleaned.add(s.trim());
                }
            }
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (cleaned.isEmpty()) {
            pdc.remove(SOCKET_KEYSTONE_SLOTS_KEY);
            pdc.remove(SOCKET_KEYSTONE_KEY);
        } else {
            pdc.set(SOCKET_KEYSTONE_SLOTS_KEY, PersistentDataType.STRING, String.join(",", cleaned));
            pdc.set(SOCKET_KEYSTONE_KEY, PersistentDataType.STRING, cleaned.get(0));
        }
        item.setItemMeta(meta);

        var itemMod = DCCosmetics.getInstance().getItemModule();
        if (itemMod != null && itemMod.getItemRegistry() != null && itemMod.getItemRegistry().isDCEItem(item)) {
            String dceId = itemMod.getItemRegistry().getItemId(item);
            var dceItem = itemMod.getItemRegistry().getItem(dceId);
            if (dceItem != null) {
                dc.dcessentials.modules.items.tooltip.TooltipRenderer.renderItem(dceItem, item, itemMod.getScrollRegistry());
            }
        }
    }

    public static List<ScrollModel> resolveSocketedScrollModels(ItemStack item) {
        List<String> ids = socketedScrolls(item);
        if (ids.isEmpty()) return Collections.emptyList();
        var itemMod = DCCosmetics.getInstance().getItemModule();
        if (itemMod == null || itemMod.getScrollRegistry() == null) return Collections.emptyList();
        ScrollRegistry reg = itemMod.getScrollRegistry();
        List<ScrollModel> models = new ArrayList<>(ids.size());
        for (String id : ids) {
            ScrollModel sm = reg.getScroll(id);
            if (sm != null) {
                models.add(sm);
            }
        }
        return models;
    }

    public static int getScrollHeatReductionPct(ItemStack item) {
        int total = 0;
        for (ScrollModel sm : resolveSocketedScrollModels(item)) {
            if ("KEYSTONE_HEAT".equalsIgnoreCase(sm.getMechanic("type", ""))) {
                Number n = sm.getMechanic("heat_reduction_pct", 0);
                if (n != null) total += n.intValue();
            }
        }
        return total;
    }

    public static int getScrollCooldownRatePct(ItemStack item) {
        int total = 0;
        for (ScrollModel sm : resolveSocketedScrollModels(item)) {
            if ("KEYSTONE_HEAT".equalsIgnoreCase(sm.getMechanic("type", ""))) {
                Number n = sm.getMechanic("cooldown_rate_pct", 0);
                if (n != null) total += n.intValue();
            }
        }
        return total;
    }

    public static int getScrollCostReductionPct(ItemStack item) {
        int total = 0;
        for (ScrollModel sm : resolveSocketedScrollModels(item)) {
            if ("KEYSTONE_TESSERACT".equalsIgnoreCase(sm.getMechanic("type", ""))) {
                Number n = sm.getMechanic("cost_reduction_pct", 0);
                if (n != null) total += n.intValue();
            }
        }
        return total;
    }

    public static int getScrollMaxEnergyBonus(ItemStack item) {
        int total = 0;
        for (ScrollModel sm : resolveSocketedScrollModels(item)) {
            if ("KEYSTONE_CAPACITOR".equalsIgnoreCase(sm.getMechanic("type", ""))) {
                Number n = sm.getMechanic("max_energy_bonus", 0);
                if (n != null) total += n.intValue();
            }
        }
        return total;
    }

    public static boolean hasDimensionAnchor(ItemStack item) {
        for (ScrollModel sm : resolveSocketedScrollModels(item)) {
            if ("KEYSTONE_ANCHOR".equalsIgnoreCase(sm.getMechanic("type", ""))) {
                Boolean b = sm.getMechanic("negate_dimension_penalty", Boolean.FALSE);
                if (Boolean.TRUE.equals(b)) return true;
            }
        }
        return false;
    }

    public static boolean hasInstabilitySuppression(ItemStack item) {
        for (ScrollModel sm : resolveSocketedScrollModels(item)) {
            if ("KEYSTONE_ANCHOR".equalsIgnoreCase(sm.getMechanic("type", ""))) {
                Boolean b = sm.getMechanic("suppress_instability", Boolean.FALSE);
                if (Boolean.TRUE.equals(b)) return true;
            }
        }
        return false;
    }

    public static boolean hasOverloadStabilizer(ItemStack item) {
        return hasPhaseVentAnchor(item);
    }

    public static boolean hasPhaseVentAnchor(ItemStack item) {
        for (ScrollModel sm : resolveSocketedScrollModels(item)) {
            if ("KEYSTONE_ANCHOR".equalsIgnoreCase(sm.getMechanic("type", ""))) {
                Number vent = sm.getMechanic("phase_vent_heat_reduction", 0);
                Boolean legacy = sm.getMechanic("stabilize_at_overload", Boolean.FALSE);
                if ((vent != null && vent.intValue() > 0) || Boolean.TRUE.equals(legacy)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int getPhaseVentHeatReduction(ItemStack item) {
        int maxVent = 0;
        for (ScrollModel sm : resolveSocketedScrollModels(item)) {
            if ("KEYSTONE_ANCHOR".equalsIgnoreCase(sm.getMechanic("type", ""))) {
                Number vent = sm.getMechanic("phase_vent_heat_reduction", 0);
                if (vent != null && vent.intValue() > maxVent) {
                    maxVent = vent.intValue();
                } else if (Boolean.TRUE.equals(sm.getMechanic("stabilize_at_overload", Boolean.FALSE)) && maxVent < 35) {
                    maxVent = 35;
                }
            }
        }
        return maxVent;
    }

    public static int getPhaseVentCooldownSeconds(ItemStack item) {
        int cd = 180;
        for (ScrollModel sm : resolveSocketedScrollModels(item)) {
            if ("KEYSTONE_ANCHOR".equalsIgnoreCase(sm.getMechanic("type", ""))) {
                Number n = sm.getMechanic("phase_vent_cooldown_seconds", 0);
                if (n != null && n.intValue() > 0) {
                    cd = Math.min(cd, n.intValue());
                }
            }
        }
        return cd;
    }

    public static int getOverloadClearBelow(ItemStack item, int defaultClearBelow) {
        int eff = defaultClearBelow;
        for (ScrollModel sm : resolveSocketedScrollModels(item)) {
            if ("KEYSTONE_ANCHOR".equalsIgnoreCase(sm.getMechanic("type", ""))) {
                Number n = sm.getMechanic("overload_clear_below", 0);
                if (n != null && n.intValue() > eff) {
                    eff = Math.min(95, n.intValue());
                } else if (Boolean.TRUE.equals(sm.getMechanic("stabilize_at_overload", Boolean.FALSE)) && eff < 75) {
                    eff = 75;
                }
            }
        }
        return eff;
    }

    public static int getTpaRefundPct(ItemStack item) {
        int total = 0;
        for (ScrollModel sm : resolveSocketedScrollModels(item)) {
            if ("KEYSTONE_RECLAIM".equalsIgnoreCase(sm.getMechanic("type", ""))) {
                Number n = sm.getMechanic("tpa_refund_pct", 0);
                if (n != null) total += n.intValue();
            }
        }
        return total;
    }

    public static boolean hasHeatsinkScroll(ItemStack item) {
        return getScrollHeatReductionPct(item) > 0 || getScrollCooldownRatePct(item) > 0;
    }

    public static boolean hasTesseractFluxScroll(ItemStack item) {
        return getScrollCostReductionPct(item) > 0;
    }

    /**
     * Strict PDC-only Keystone tier detection (Task P1-04).
     * Never inspects display name, lore, or arbitrary PDC values.
     */
    public static KeystoneTier tierOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return KeystoneTier.NONE;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return KeystoneTier.NONE;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // 1. Native DCEItem Keystone lookup
        if (pdc.has(ItemRegistry.ITEM_ID_KEY, PersistentDataType.STRING)) {
            String dceId = pdc.get(ItemRegistry.ITEM_ID_KEY, PersistentDataType.STRING);
            String cat = pdc.get(ItemRegistry.CATEGORY_KEY, PersistentDataType.STRING);
            if (dceId != null && "KEYSTONE".equalsIgnoreCase(cat)) {
                return switch (dceId.toLowerCase(Locale.ROOT)) {
                    case "tesseract" -> KeystoneTier.RARE;
                    case "chronolith" -> KeystoneTier.EPIC;
                    case "singularity", "astral_tesseract" -> KeystoneTier.LEGENDARY;
                    default -> KeystoneTier.NONE;
                };
            }
            return KeystoneTier.NONE;
        }

        // 2. Exact Legacy ExecutableItems ID lookup
        if (pdc.has(EI_ID_KEY, PersistentDataType.STRING)) {
            String eiId = pdc.get(EI_ID_KEY, PersistentDataType.STRING);
            if (eiId != null) {
                return switch (eiId.toLowerCase(Locale.ROOT)) {
                    case "tesseract" -> KeystoneTier.RARE;
                    case "tesseract_epic" -> KeystoneTier.EPIC;
                    case "tesseract_legendary" -> KeystoneTier.LEGENDARY;
                    default -> KeystoneTier.NONE;
                };
            }
        }

        return KeystoneTier.NONE;
    }

    public static KeystoneTier detectTier(ItemStack item) {
        return tierOf(item);
    }

    public static boolean isKeystone(ItemStack item) {
        return tierOf(item) != KeystoneTier.NONE;
    }

    public static boolean isEnergyCube(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (pdc.has(ItemRegistry.ITEM_ID_KEY, PersistentDataType.STRING)) {
            String dceId = pdc.get(ItemRegistry.ITEM_ID_KEY, PersistentDataType.STRING);
            return "tesseract_cube".equalsIgnoreCase(dceId);
        }
        if (pdc.has(EI_ID_KEY, PersistentDataType.STRING)) {
            String eiId = pdc.get(EI_ID_KEY, PersistentDataType.STRING);
            return "tesseractenergy".equalsIgnoreCase(eiId);
        }
        return false;
    }

    public static boolean isLegacy(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (pdc.has(ItemRegistry.ITEM_ID_KEY, PersistentDataType.STRING)) {
            String dceId = pdc.get(ItemRegistry.ITEM_ID_KEY, PersistentDataType.STRING);
            return "astral_tesseract".equalsIgnoreCase(dceId);
        }
        if (pdc.has(EI_ID_KEY, PersistentDataType.STRING)) {
            String eiId = pdc.get(EI_ID_KEY, PersistentDataType.STRING);
            if (eiId == null) return false;
            return switch (eiId.toLowerCase(Locale.ROOT)) {
                case "tesseract", "tesseract_epic", "tesseract_legendary", "tesseractenergy" -> true;
                default -> false;
            };
        }
        return false;
    }
}
