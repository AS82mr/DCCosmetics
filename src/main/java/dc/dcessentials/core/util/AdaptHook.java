package dc.dcessentials.core.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdaptHook {

    private static volatile boolean reflectionInitAttempted = false;
    private static volatile boolean reflectionAvailable = false;
    private static Field adaptInstanceField;
    private static Method getAdaptServerMethod;
    private static Method getOnlineAdaptPlayerMethod;
    private static Method getDataMethod;
    private static Method getSkillLinesMethod;
    private static Method getXpMethod;
    private static Method getPooledXpMethod;

    // Tracks recent XP granted by DCEssentials itself (e.g. Mastery scrolls / Refinement) per player -> skill -> amount
    private static final Map<UUID, Map<String, Double>> recentOwnGrants = new ConcurrentHashMap<>();

    private AdaptHook() {}

    public static boolean isAvailable() {
        Plugin adaptPlugin = Bukkit.getPluginManager().getPlugin("Adapt");
        return adaptPlugin != null && adaptPlugin.isEnabled();
    }

    private static boolean ensureReflection() {
        if (!isAvailable()) return false;
        if (reflectionInitAttempted) return reflectionAvailable;
        reflectionInitAttempted = true;
        try {
            Class<?> adaptClass = Class.forName("art.arcane.adapt.Adapt");
            adaptInstanceField = adaptClass.getField("instance");
            getAdaptServerMethod = adaptClass.getMethod("getAdaptServer");
            Object adapt = adaptInstanceField.get(null);
            if (adapt == null) {
                reflectionInitAttempted = false;
                return false;
            }
            Object server = getAdaptServerMethod.invoke(adapt);
            if (server == null) {
                reflectionInitAttempted = false;
                return false;
            }
            getOnlineAdaptPlayerMethod = server.getClass().getMethod("getOnlineAdaptPlayer", UUID.class);
            Class<?> adaptPlayerClass = Class.forName("art.arcane.adapt.api.world.AdaptPlayer");
            getDataMethod = adaptPlayerClass.getMethod("getData");
            Class<?> playerDataClass = Class.forName("art.arcane.adapt.api.world.PlayerData");
            getSkillLinesMethod = playerDataClass.getMethod("getSkillLines");
            Class<?> skillLineClass = Class.forName("art.arcane.adapt.api.world.PlayerSkillLine");
            getXpMethod = skillLineClass.getMethod("getXp");
            getPooledXpMethod = skillLineClass.getMethod("getPooledXp");
            reflectionAvailable = true;
        } catch (Throwable ignored) {
            reflectionAvailable = false;
        }
        return reflectionAvailable;
    }

    /**
     * Reads current cumulative Skill XP (active + pooled) per skill key for an online player.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Double> readSkillXpTotals(Player player) {
        if (player == null || !player.isOnline() || !ensureReflection()) {
            return Collections.emptyMap();
        }
        try {
            Object adapt = adaptInstanceField.get(null);
            Object server = adapt != null ? getAdaptServerMethod.invoke(adapt) : null;
            Object adaptPlayer = server != null ? getOnlineAdaptPlayerMethod.invoke(server, player.getUniqueId()) : null;
            if (adaptPlayer == null) return Collections.emptyMap();
            Object data = getDataMethod.invoke(adaptPlayer);
            if (data == null) return Collections.emptyMap();
            Object rawMap = getSkillLinesMethod.invoke(data);
            if (!(rawMap instanceof Map<?, ?> map)) return Collections.emptyMap();

            Map<String, Double> totals = new HashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                String key = entry.getKey().toString().toLowerCase(Locale.ROOT);
                double xp = ((Number) getXpMethod.invoke(entry.getValue())).doubleValue();
                double pooled = ((Number) getPooledXpMethod.invoke(entry.getValue())).doubleValue();
                totals.put(key, xp + pooled);
            }
            return totals;
        } catch (Throwable ignored) {
            return Collections.emptyMap();
        }
    }

    /**
     * Compares live Skill XP totals against a stored per-player baseline map,
     * updates the baseline map in-place, and returns any positive XP gained per skill.
     */
    public static Map<String, Double> pollSkillXpDeltas(Player player, Map<String, Double> baseline) {
        return pollSkillXpDeltas(player, baseline, false);
    }

    public static Map<String, Double> pollSkillXpDeltas(Player player, Map<String, Double> baseline, boolean excludeOwnGrants) {
        if (baseline == null || player == null) return Collections.emptyMap();
        Map<String, Double> current = readSkillXpTotals(player);
        if (current.isEmpty()) return Collections.emptyMap();

        Map<String, Double> ownGrants = recentOwnGrants.remove(player.getUniqueId());
        if (baseline.isEmpty()) {
            baseline.putAll(current);
            return Collections.emptyMap();
        }

        Map<String, Double> deltas = new HashMap<>();
        for (Map.Entry<String, Double> entry : current.entrySet()) {
            String skill = entry.getKey();
            double now = entry.getValue();
            Double prev = baseline.put(skill, now);
            if (prev != null) {
                double diff = now - prev;
                if (excludeOwnGrants && ownGrants != null) {
                    double own = ownGrants.getOrDefault(skill, 0.0);
                    diff = Math.max(0.0, diff - own);
                }
                // Only award sensible positive deltas (ignore negative resets or prestige/command anomalies)
                if (diff > 0.05) {
                    deltas.put(skill, diff);
                }
            }
        }
        return deltas;
    }

    private static void recordOwnGrant(UUID uuid, String skillKey, double amount) {
        if (uuid == null || skillKey == null || amount <= 0.0) return;
        Map<String, Double> map = recentOwnGrants.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        map.merge(skillKey.toLowerCase(Locale.ROOT), amount, Double::sum);
    }

    /**
     * Directly grants Mastery/Skill XP to a player without creating or giving an ExperienceOrb item.
     *
     * @param player online target player
     * @param skillTarget "all" or specific skill name (e.g. "swords", "agility")
     * @param amount amount of XP to grant
     * @return true if successfully applied
     */
    public static boolean giveXp(Player player, String skillTarget, double amount) {
        if (player == null || !player.isOnline()) return false;
        Plugin adaptPlugin = Bukkit.getPluginManager().getPlugin("Adapt");
        if (adaptPlugin == null || !adaptPlugin.isEnabled()) {
            Bukkit.getLogger().warning("[DCEssentials] Adapt plugin is not enabled; cannot grant skill XP.");
            return false;
        }

        try {
            Class<?> adaptClass = Class.forName("art.arcane.adapt.Adapt");
            Object adapt = adaptClass.getField("instance").get(null);
            if (adapt == null) {
                Bukkit.getLogger().warning("[DCEssentials] Adapt instance is null.");
                return false;
            }

            Object server = adaptClass.getMethod("getAdaptServer").invoke(adapt);
            if (server == null) {
                Bukkit.getLogger().warning("[DCEssentials] AdaptServer is null.");
                return false;
            }

            Method getPlayerMethod = server.getClass().getMethod("getOnlineAdaptPlayer", UUID.class);
            Object adaptPlayer = getPlayerMethod.invoke(server, player.getUniqueId());
            if (adaptPlayer == null) {
                Bukkit.getLogger().warning("[DCEssentials] AdaptPlayer not ready for " + player.getName());
                return false;
            }

            Object notifier = null;
            Class<?> notifierClass = null;
            try {
                notifierClass = Class.forName("art.arcane.adapt.api.notification.Notifier");
                notifier = adaptPlayer.getClass().getMethod("getActionBarNotifier").invoke(adaptPlayer);
            } catch (Throwable ignored) {}

            Method getSkillLineMethod = adaptPlayer.getClass().getMethod("getSkillLine", String.class);
            Method requestSaveMethod = adaptPlayer.getClass().getMethod("requestSave");

            boolean isAll = skillTarget == null || skillTarget.isEmpty() || skillTarget.equalsIgnoreCase("all");

            if (isAll) {
                Object registry = server.getClass().getMethod("getSkillRegistry").invoke(server);
                List<?> skills = (List<?>) registry.getClass().getMethod("getSkills").invoke(registry);
                if (skills != null) {
                    for (Object skill : skills) {
                        try {
                            String skillName = (String) skill.getClass().getMethod("getName").invoke(skill);
                            Object line = getSkillLineMethod.invoke(adaptPlayer, skillName);
                            if (line != null) {
                                Method giveMethod = line.getClass().getMethod("giveXP", notifierClass, double.class);
                                giveMethod.invoke(line, notifier, amount);
                                recordOwnGrant(player.getUniqueId(), skillName, amount);
                            }
                        } catch (Exception ignored) {}
                    }
                    requestSaveMethod.invoke(adaptPlayer);
                    return true;
                }
            } else {
                String cleanTarget = skillTarget.toLowerCase(Locale.ROOT);
                Object line = getSkillLineMethod.invoke(adaptPlayer, cleanTarget);
                if (line != null) {
                    Method giveMethod = line.getClass().getMethod("giveXP", notifierClass, double.class);
                    giveMethod.invoke(line, notifier, amount);
                    recordOwnGrant(player.getUniqueId(), cleanTarget, amount);
                    requestSaveMethod.invoke(adaptPlayer);
                    return true;
                } else {
                    Bukkit.getLogger().warning("[DCEssentials] Skill '" + skillTarget + "' not found for " + player.getName());
                    return false;
                }
            }
        } catch (Throwable t) {
            Bukkit.getLogger().severe("[DCEssentials] Error invoking Adapt XP API: " + t.getMessage());
            t.printStackTrace();
            return false;
        }

        return false;
    }
}
