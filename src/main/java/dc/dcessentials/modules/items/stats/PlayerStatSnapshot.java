package dc.dcessentials.modules.items.stats;

import dc.dcessentials.modules.items.model.ItemStat;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class PlayerStatSnapshot {

    private final EnumMap<ItemStat, Double> totals = new EnumMap<>(ItemStat.class);
    private final EnumMap<StatSource, EnumMap<ItemStat, Double>> bySource = new EnumMap<>(StatSource.class);
    private final long computedAtMs;

    public PlayerStatSnapshot(EnumMap<ItemStat, Double> totals,
                              EnumMap<StatSource, EnumMap<ItemStat, Double>> bySource) {
        if (totals != null) {
            this.totals.putAll(totals);
        }
        if (bySource != null) {
            for (Map.Entry<StatSource, EnumMap<ItemStat, Double>> entry : bySource.entrySet()) {
                this.bySource.put(entry.getKey(), new EnumMap<>(entry.getValue()));
            }
        }
        this.computedAtMs = System.currentTimeMillis();
    }

    public double get(ItemStat stat) {
        if (stat == null) return 0.0;
        return totals.getOrDefault(stat, 0.0);
    }

    public double getFromSource(StatSource source, ItemStat stat) {
        if (source == null || stat == null) return 0.0;
        EnumMap<ItemStat, Double> map = bySource.get(source);
        if (map == null) return 0.0;
        return map.getOrDefault(stat, 0.0);
    }

    public Map<ItemStat, Double> getTotals() {
        return Collections.unmodifiableMap(totals);
    }

    public Map<ItemStat, Double> getSourceStats(StatSource source) {
        EnumMap<ItemStat, Double> map = bySource.get(source);
        return map != null ? Collections.unmodifiableMap(map) : Collections.emptyMap();
    }

    public boolean hasAnyStats() {
        for (double val : totals.values()) {
            if (val > 0.0) return true;
        }
        return false;
    }

    public long getComputedAtMs() {
        return computedAtMs;
    }
}
