package dc.dccosmetics.tpeffects;

import dc.dccosmetics.tpeffects.model.TpEffectEntry;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * In-memory registry of loaded teleportation effects, indexed by lowercase ID and rarity.
 */
public final class TpEffectRegistry {

    private final Map<String, TpEffectEntry> byId = new LinkedHashMap<>();
    private final Map<String, List<TpEffectEntry>> byRarity = new LinkedHashMap<>();
    private final List<String> orderedRarities = new ArrayList<>();

    public synchronized void reload(List<TpEffectEntry> entries, List<String> configuredRarityOrder) {
        byId.clear();
        byRarity.clear();
        orderedRarities.clear();

        if (configuredRarityOrder != null) {
            for (String r : configuredRarityOrder) {
                if (r != null && !r.isBlank()) {
                    String norm = r.trim().toLowerCase(Locale.ROOT);
                    if (!orderedRarities.contains(norm)) {
                        orderedRarities.add(norm);
                    }
                }
            }
        }

        if (entries != null) {
            for (TpEffectEntry entry : entries) {
                byId.put(entry.getId(), entry);
                byRarity.computeIfAbsent(entry.getRarity(), k -> new ArrayList<>()).add(entry);
                if (!orderedRarities.contains(entry.getRarity())) {
                    orderedRarities.add(entry.getRarity());
                }
            }
        }
    }

    public synchronized TpEffectEntry getById(String id) {
        if (id == null || id.isBlank()) return null;
        return byId.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public synchronized List<TpEffectEntry> getAll() {
        return new ArrayList<>(byId.values());
    }

    public synchronized List<TpEffectEntry> getVisible() {
        List<TpEffectEntry> list = new ArrayList<>();
        for (TpEffectEntry entry : byId.values()) {
            if (entry.isVisibleInMenu()) {
                list.add(entry);
            }
        }
        return list;
    }

    public synchronized List<TpEffectEntry> getByRarity(String rarity, boolean visibleOnly) {
        if (rarity == null || rarity.isBlank()) return Collections.emptyList();
        List<TpEffectEntry> raw = byRarity.get(rarity.trim().toLowerCase(Locale.ROOT));
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        if (!visibleOnly) return new ArrayList<>(raw);

        List<TpEffectEntry> filtered = new ArrayList<>(raw.size());
        for (TpEffectEntry entry : raw) {
            if (entry.isVisibleInMenu()) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    public synchronized List<String> getOrderedRarities(boolean includeEmpty) {
        if (includeEmpty) {
            return new ArrayList<>(orderedRarities);
        }
        List<String> nonEmpty = new ArrayList<>();
        for (String r : orderedRarities) {
            List<TpEffectEntry> list = byRarity.get(r);
            if (list != null && list.stream().anyMatch(TpEffectEntry::isVisibleInMenu)) {
                nonEmpty.add(r);
            }
        }
        return nonEmpty;
    }

    public synchronized List<String> getAllIds() {
        return new ArrayList<>(byId.keySet());
    }

    /**
     * Checks whether the player owns/is permitted to use a given teleport effect.
     */
    public static boolean hasPermission(Player player, TpEffectEntry entry) {
        if (player == null || entry == null) return false;
        return player.hasPermission(entry.getPermission()) || player.hasPermission("dc.tpeffect.*");
    }
}
