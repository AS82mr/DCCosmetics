package dc.dcessentials.modules.rng;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RngSecurity {

    // Combat Attribution: Entity UUID -> Attacker UUID
    private final Map<UUID, UUID> combatAttribution = new ConcurrentHashMap<>();
    private final Set<UUID> nerfedSpawns = ConcurrentHashMap.newKeySet();

    // Passive background heuristics (non-disruptive, runs silently over long durations)
    private final Map<UUID, PassiveHeuristicData> playerHeuristics = new ConcurrentHashMap<>();

    public static class SecurityResult {
        public final boolean allowed;
        public final double xpMultiplier;
        public final boolean forceZeroBoost;
        public final String reason;

        public SecurityResult(boolean allowed, double xpMultiplier, boolean forceZeroBoost, String reason) {
            this.allowed = allowed;
            this.xpMultiplier = xpMultiplier;
            this.forceZeroBoost = forceZeroBoost;
            this.reason = reason;
        }

        public static SecurityResult pass() {
            return new SecurityResult(true, 1.0, false, null);
        }

        public static SecurityResult reject(String reason) {
            return new SecurityResult(false, 0.0, true, reason);
        }
    }

    public static class PassiveHeuristicData {
        public long lastActionTime = 0;
        public List<Long> actionIntervals = new ArrayList<>();
        public Location lastLoc = null;
        public float lastYaw = 0;
        public float lastPitch = 0;
        public int suspiciousIdenticalIntervals = 0;
    }

    public void tagDamage(Entity victim, Player attacker) {
        if (victim == null || attacker == null) return;
        combatAttribution.put(victim.getUniqueId(), attacker.getUniqueId());
    }

    public void tagSpawn(Entity entity, SpawnReason reason) {
        if (entity == null) return;
        if (reason == SpawnReason.SPAWNER ||
            reason == SpawnReason.RAID ||
            reason == SpawnReason.BREEDING ||
            reason == SpawnReason.SLIME_SPLIT) {
            nerfedSpawns.add(entity.getUniqueId());
        }
    }

    public void cleanupEntity(Entity entity) {
        if (entity == null) return;
        combatAttribution.remove(entity.getUniqueId());
        nerfedSpawns.remove(entity.getUniqueId());
    }

    /**
     * Non-disruptive validation. Normal grinder killing chambers and mob farms work 100% freely.
     * No AFK title lockouts, no 2.5m walk requirement.
     */
    public SecurityResult validateAction(Player player, Entity victim, String sourceType) {
        boolean isCasino = "casino".equalsIgnoreCase(sourceType);
        if (isCasino) {
            return SecurityResult.pass();
        }

        UUID playerUuid = player.getUniqueId();

        // 1. Combat Attribution Check (Prevents KS/looting unattacked mobs)
        if (victim != null) {
            UUID taggedAttacker = combatAttribution.get(victim.getUniqueId());
            if (taggedAttacker != null && !taggedAttacker.equals(playerUuid)) {
                return SecurityResult.reject("Attribution mismatch (environmental/stolen kill).");
            }
        }

        // 2. Passive Heuristic Sampling (Silent background evaluation, no disruptive screen locks)
        long now = System.currentTimeMillis();
        PassiveHeuristicData data = playerHeuristics.computeIfAbsent(playerUuid, k -> new PassiveHeuristicData());
        if (data.lastActionTime > 0) {
            long delta = now - data.lastActionTime;
            if (data.actionIntervals.size() > 50) {
                data.actionIntervals.remove(0);
            }
            data.actionIntervals.add(delta);
        }
        data.lastActionTime = now;
        data.lastLoc = player.getLocation();
        data.lastYaw = player.getLocation().getYaw();
        data.lastPitch = player.getLocation().getPitch();

        // 3. Spawner / Raid spawn yield adjustment (Standard balanced rate: 50% XP for spawners, full for wild)
        if (victim != null && nerfedSpawns.contains(victim.getUniqueId())) {
            return new SecurityResult(true, 0.50, false, "Spawner spawn: 50% yield.");
        }

        return SecurityResult.pass();
    }
}
