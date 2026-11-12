package dc.dccosmetics.wardrobe.teleport;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.tpeffects.TpEffectManager;
import dc.dccosmetics.tpeffects.model.TpEffectEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wardrobe registry view delegating dynamically to the unified TpEffectRegistry
 * so enable/disable/hide/reload YAML changes reflect immediately in the Wardrobe UI.
 */
public class WardrobeTeleportRegistry {

    public static List<WardrobeTeleportEffect> getAll() {
        TpEffectManager mgr = getTpEffectManager();
        if (mgr == null) return Collections.emptyList();
        return convertList(mgr.getRegistry().getVisible());
    }

    public static WardrobeTeleportEffect getById(String id) {
        TpEffectManager mgr = getTpEffectManager();
        if (mgr == null || id == null) return null;
        return WardrobeTeleportEffect.fromEntry(mgr.getRegistry().getById(id));
    }

    public static List<WardrobeTeleportEffect> getByRarity(String rarity) {
        TpEffectManager mgr = getTpEffectManager();
        if (mgr == null) return Collections.emptyList();
        if (rarity == null || rarity.equalsIgnoreCase("all")) {
            return getAll();
        }
        return convertList(mgr.getRegistry().getByRarity(rarity, true));
    }

    public static List<String> getOrderedVisibleRarities() {
        TpEffectManager mgr = getTpEffectManager();
        if (mgr == null) return Collections.emptyList();
        return mgr.getRegistry().getOrderedRarities(false);
    }

    private static TpEffectManager getTpEffectManager() {
        DCCosmetics plugin = DCCosmetics.getInstance();
        return plugin != null ? plugin.getTpEffectManager() : null;
    }

    private static List<WardrobeTeleportEffect> convertList(List<TpEffectEntry> entries) {
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        List<WardrobeTeleportEffect> out = new ArrayList<>(entries.size());
        for (TpEffectEntry entry : entries) {
            out.add(WardrobeTeleportEffect.fromEntry(entry));
        }
        return out;
    }
}
