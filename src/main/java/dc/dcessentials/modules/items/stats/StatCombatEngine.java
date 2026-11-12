package dc.dcessentials.modules.items.stats;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.items.listener.StatCombatListener.CatalystData;
import dc.dcessentials.modules.items.model.ItemStat;
import dc.dcessentials.modules.teleportation.KeystoneProfile;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class StatCombatEngine {

    private static final Set<DamageCause> WARD_CAUSES = EnumSet.of(
            DamageCause.ENTITY_ATTACK,
            DamageCause.ENTITY_SWEEP_ATTACK,
            DamageCause.PROJECTILE
    );

    private static final Set<DamageCause> AEGIS_CAUSES = EnumSet.of(
            DamageCause.FIRE,
            DamageCause.FIRE_TICK,
            DamageCause.LAVA,
            DamageCause.HOT_FLOOR,
            DamageCause.MAGIC,
            DamageCause.POISON,
            DamageCause.WITHER,
            DamageCause.DRAGON_BREATH,
            DamageCause.BLOCK_EXPLOSION,
            DamageCause.ENTITY_EXPLOSION,
            DamageCause.LIGHTNING,
            DamageCause.FREEZE,
            DamageCause.SONIC_BOOM
    );

    // Per-player Conduit mob-kill refund rolling minute window: UUID -> [minuteBucket, grantedInMinute]
    private static final Map<UUID, long[]> conduitMinuteWindow = new ConcurrentHashMap<>();

    private StatCombatEngine() {}

    public record IncomingDamageResult(
            double damage,
            boolean unstableVulnerabilityApplied,
            boolean deflected,
            double wardOrAegisReductionPct,
            boolean bastionCapped
    ) {}

    public static IncomingDamageResult evaluateIncomingDamage(
            Player victim,
            PlayerStatSnapshot vicStats,
            Entity damager,
            DamageCause cause,
            double rawDamage
    ) {
        if (victim == null || rawDamage <= 0.0) {
            return new IncomingDamageResult(rawDamage, false, false, 0.0, false);
        }

        double damage = rawDamage;
        boolean unstableApplied = false;
        boolean deflected = false;
        double statReductionPct = 0.0;
        boolean bastionCapped = false;

        Entity rootDamager = resolveRootDamager(damager);

        // 1. UnstableVulnerability (+15% if heat >= 80 && !suppressInstability && source is Boss/Emperor, VOID, or SONIC_BOOM)
        var teleMgr = DCCosmetics.getInstance().getTeleportationManager();
        if (teleMgr != null) {
            KeystoneProfile kProfile = teleMgr.getProfile(victim);
            if (kProfile != null && kProfile.getHeat() >= 80
                    && !teleMgr.getEnergyService().isInstabilitySuppressed(victim)) {
                if (isBossOrEmperor(rootDamager) || cause == DamageCause.VOID || cause == DamageCause.SONIC_BOOM) {
                    damage *= 1.15;
                    unstableApplied = true;
                }
            }
        }

        // 2. DEFLECT (glance: -50% on entity hit, cap 35% chance)
        double multAfterMitigation = 1.0;
        boolean isEntityHit = damager != null || (cause != null && WARD_CAUSES.contains(cause));
        if (isEntityHit && vicStats != null) {
            double deflectChance = Math.min(35.0, vicStats.get(ItemStat.DEFLECT));
            if (deflectChance > 0.0 && ThreadLocalRandom.current().nextDouble(100.0) < deflectChance) {
                deflected = true;
                multAfterMitigation *= 0.50;
            }
        }

        // 3. WARD (physical/projectile) OR AEGIS (hazard/magic/elemental)
        if (vicStats != null && cause != null) {
            if (WARD_CAUSES.contains(cause)) {
                double ward = Math.min(60.0, vicStats.get(ItemStat.WARD));
                if (ward > 0.0) {
                    statReductionPct = ward;
                    multAfterMitigation *= (1.0 - (ward / 100.0));
                }
            } else if (AEGIS_CAUSES.contains(cause)) {
                double aegis = Math.min(60.0, vicStats.get(ItemStat.AEGIS));
                if (aegis > 0.0) {
                    statReductionPct = aegis;
                    multAfterMitigation *= (1.0 - (aegis / 100.0));
                }
            }
        }

        // 4. Combined-reduction floor 80% (minimum multiplier 0.20)
        multAfterMitigation = Math.max(0.20, multAfterMitigation);
        damage *= multAfterMitigation;

        // 5. BASTION single-hit ceiling: maxHP * max(0.40, 1 - v/100) while HP >= 60% of maxHP
        if (vicStats != null) {
            double bastion = Math.min(60.0, vicStats.get(ItemStat.BASTION));
            if (bastion > 0.0) {
                double maxHp = getMaxHealth(victim);
                if (victim.getHealth() >= maxHp * 0.60) {
                    double ceiling = maxHp * Math.max(0.40, 1.0 - (bastion / 100.0));
                    if (damage > ceiling) {
                        damage = ceiling;
                        bastionCapped = true;
                    }
                }
            }
        }

        return new IncomingDamageResult(damage, unstableApplied, deflected, statReductionPct, bastionCapped);
    }

    public static int tryConduitMobKillRefund(Player killer, PlayerStatSnapshot snap) {
        if (killer == null || snap == null) return 0;
        double conduit = snap.get(ItemStat.CONDUIT);
        if (conduit <= 0.0) return 0;

        double chance = Math.min(25.0, conduit);
        if (ThreadLocalRandom.current().nextDouble(100.0) >= chance) {
            return 0;
        }

        long minuteBucket = System.currentTimeMillis() / 60_000L;
        long[] win = conduitMinuteWindow.computeIfAbsent(killer.getUniqueId(), k -> new long[]{minuteBucket, 0L});
        int grant;
        synchronized (win) {
            if (win[0] != minuteBucket) {
                win[0] = minuteBucket;
                win[1] = 0L;
            }
            int remainingInMinute = (int) Math.max(0L, 400L - win[1]);
            if (remainingInMinute <= 0) return 0;
            int roll = ThreadLocalRandom.current().nextInt(25, 101);
            grant = Math.min(roll, remainingInMinute);
            win[1] += grant;
        }

        var teleMgr = DCCosmetics.getInstance().getTeleportationManager();
        if (teleMgr == null || teleMgr.getEnergyService() == null) return 0;
        return teleMgr.getEnergyService().grantMicro(killer, grant);
    }

    public static void clearPlayer(UUID uuid) {
        if (uuid != null) {
            conduitMinuteWindow.remove(uuid);
        }
    }

    public static double computeSurgeBonus(Map<UUID, long[]> surgeMap, UUID attackerId, double cap, long now) {
        if (cap <= 0.0) {
            surgeMap.remove(attackerId);
            return 0.0;
        }
        long[] data = surgeMap.computeIfAbsent(attackerId, k -> new long[]{0L, 0L});
        int maxStacks = Math.max(6, (int) Math.ceil(cap / 4.0));
        if (now - data[0] <= 3500L) {
            data[1] = Math.min(maxStacks, data[1] + 1);
        } else {
            data[1] = 1;
        }
        data[0] = now;
        return Math.min(cap, data[1] * 4.0);
    }

    public static CatalystData calculateCatalyst(Player attacker, double statVal) {
        if (attacker == null || statVal <= 0.0) {
            return new CatalystData(1.0, 0.0, Collections.emptyList(), false);
        }

        double cappedVal = Math.min(60.0, statVal);
        double mult = 1.0;
        double weaknessOffset = 0.0;
        List<String> names = new ArrayList<>();
        boolean heal = false;

        if (attacker.hasPotionEffect(PotionEffectType.WEAKNESS)) {
            var effect = attacker.getPotionEffect(PotionEffectType.WEAKNESS);
            int amp = effect != null ? effect.getAmplifier() : 0;
            weaknessOffset = 4.0 * (amp + 1);
            mult += (cappedVal / 100.0) * 0.50;
            names.add("Weakness");
        }
        if (attacker.getFireTicks() > 0) {
            mult += (cappedVal / 100.0) * 0.50;
            names.add("Fire");
        }
        if (attacker.hasPotionEffect(PotionEffectType.POISON)) {
            mult += (cappedVal / 100.0) * 0.45;
            heal = true;
            names.add("Poison");
        }
        if (attacker.hasPotionEffect(PotionEffectType.WITHER)) {
            mult += (cappedVal / 100.0) * 0.45;
            heal = true;
            names.add("Wither");
        }
        if (attacker.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            mult += (cappedVal / 100.0) * 0.35;
            names.add("Slowness");
        }
        if (attacker.hasPotionEffect(PotionEffectType.BLINDNESS) || attacker.hasPotionEffect(PotionEffectType.DARKNESS)) {
            mult += (cappedVal / 100.0) * 0.50;
            names.add("Darkness");
        }
        if (attacker.hasPotionEffect(PotionEffectType.MINING_FATIGUE)) {
            mult += (cappedVal / 100.0) * 0.30;
            names.add("Fatigue");
        }
        if (attacker.hasPotionEffect(PotionEffectType.HUNGER)) {
            mult += (cappedVal / 100.0) * 0.25;
            names.add("Hunger");
        }
        if (attacker.hasPotionEffect(PotionEffectType.NAUSEA)) {
            mult += (cappedVal / 100.0) * 0.30;
            names.add("Nausea");
        }
        if (attacker.hasPotionEffect(PotionEffectType.LEVITATION)) {
            mult += (cappedVal / 100.0) * 0.40;
            names.add("Levitation");
        }

        return new CatalystData(mult, weaknessOffset, names, heal);
    }

    public static boolean isBossOrEmperor(Entity entity) {
        if (entity == null) return false;
        if (entity.getScoreboardTags().contains("emperor_boss") || entity instanceof Boss) {
            return true;
        }
        String customName = entity.getCustomName();
        return customName != null && customName.contains("Temporal Sovereign");
    }

    private static Entity resolveRootDamager(Entity damager) {
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    private static double getMaxHealth(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : player.getMaxHealth();
    }
}
