package dc.dcessentials.modules.items.listener;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.model.ItemStat;
import dc.dcessentials.modules.items.stats.PlayerStatSnapshot;
import dc.dcessentials.modules.items.stats.StatAggregator;
import dc.dcessentials.modules.items.stats.StatCombatEngine;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class StatCombatListener implements Listener {

    private final ItemRegistry itemRegistry;
    private final StatAggregator statAggregator;

    // Reentrancy guard to prevent recursive damage cascades (Cleave, ModelEngine hitboxes, Stasis detonation)
    private static final ThreadLocal<Boolean> IN_INTERNAL_DAMAGE = ThreadLocal.withInitial(() -> false);

    // Resonance tracking: Attacker UUID -> [LastHitTime, Stacks]
    private final Map<UUID, long[]> resonanceMap = new HashMap<>();
    // Surge tracking: Attacker UUID -> [LastHitTime, Stacks]
    private final Map<UUID, long[]> surgeMap = new HashMap<>();

    public StatCombatListener(ItemRegistry itemRegistry, StatAggregator statAggregator) {
        this.itemRegistry = itemRegistry;
        this.statAggregator = statAggregator;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        // 1. Thread-local reentrancy check + metadata check to kill infinite damage loops
        if (IN_INTERNAL_DAMAGE.get() || victim.hasMetadata("dce_internal_damage")) {
            return;
        }

        // 2. Never process combat mechanics on the DCE Stat Tester Dummy
        if (victim instanceof ArmorStand stand &&
                stand.getPersistentDataContainer().has(dc.dcessentials.modules.items.tester.StatTesterManager.DUMMY_KEY, org.bukkit.persistence.PersistentDataType.INTEGER)) {
            return;
        }

        Player attacker = (event.getDamager() instanceof Player p) ? p : null;
        PlayerStatSnapshot atkStats = (attacker != null && statAggregator != null) ? statAggregator.getSnapshot(attacker) : null;
        Player victimPlayer = (victim instanceof Player vp) ? vp : null;
        PlayerStatSnapshot vicStats = (victimPlayer != null && statAggregator != null) ? statAggregator.getSnapshot(victimPlayer) : null;

        // -------------------------------------------------------------
        // STEP 1: OFFENSIVE PRE-MITIGATION MULTIPLIERS
        // (Stacks across Weapon, Offhand, Armor, Accessories, Keystone, Scrolls, and Active Pet)
        // -------------------------------------------------------------
        if (attacker != null && atkStats != null && atkStats.hasAnyStats()) {
            long now = System.currentTimeMillis();

            // 1. RESONANCE (Acoustic Cadence & Shield Shatter)
            double resonanceCadence = atkStats.get(ItemStat.RESONANCE);
            if (resonanceCadence > 0) {
                boolean proc = handleResonance(attacker, victim, resonanceCadence, now);
                if (proc) {
                    event.setDamage(event.getDamage() * 1.25);
                }
            }

            // 2. SURGE (Combo Momentum — Stackable across Weapon + Pet + Accessories)
            double surgeCap = atkStats.get(ItemStat.SURGE);
            if (surgeCap > 0) {
                double bonusPct = handleSurge(attacker, surgeCap, now);
                if (bonusPct > 0) {
                    event.setDamage(event.getDamage() * (1.0 + (bonusPct / 100.0)));
                }
            }

            // 3. CATALYST (Debuff Inversion)
            double catalystPct = atkStats.get(ItemStat.CATALYST);
            if (catalystPct > 0) {
                CatalystData catData = calculateCatalyst(attacker, catalystPct);
                if (catData.multiplier > 1.0 || catData.flatWeaknessOffset > 0) {
                    double newDmg = (event.getDamage() + catData.flatWeaknessOffset) * catData.multiplier;
                    event.setDamage(newDmg);
                    if (catData.shouldHeal) {
                        attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + 1.5));
                    }
                    int bonusPct = (int) Math.round((catData.multiplier - 1.0) * 100.0);
                    String debuffs = String.join(", ", catData.debuffNames);
                    attacker.sendActionBar(ColorUtil.colorize("<#A855F7>&l🔮 CATALYST INVERSION &8| &d+" + bonusPct + "% DMG &7(" + debuffs + ")"));
                    attacker.getWorld().spawnParticle(Particle.WITCH, victim.getLocation().add(0, 1.2, 0), 12, 0.3, 0.3, 0.3, 0.05);
                    attacker.playSound(victim.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 0.7f, 1.8f);
                }
            }

            // 4. EXECUTE (Desperation Pre-Multiplier when Attacker HP <= 30%)
            double executePct = atkStats.get(ItemStat.EXECUTE);
            if (executePct > 0) {
                double attackerHpRatio = attacker.getHealth() / attacker.getMaxHealth();
                if (attackerHpRatio <= 0.30) {
                    event.setDamage(event.getDamage() * 1.35);
                    attacker.sendActionBar(ColorUtil.colorize("<#EF4444>&l💀 DESPERATION SURGE &8| &c+35% Damage!"));
                }
            }
        }

        // -------------------------------------------------------------
        // STEP 2: DEFENSIVE MITIGATION (UnstableVulnerability -> DEFLECT -> WARD/AEGIS -> 80% floor -> BASTION)
        // Applied BEFORE Stasis debt, Execute cull, Cleave, and Siphon!
        // -------------------------------------------------------------
        if (victimPlayer != null) {
            // Reset victim's offensive Surge combo when struck
            surgeMap.remove(victimPlayer.getUniqueId());

            StatCombatEngine.IncomingDamageResult defRes = StatCombatEngine.evaluateIncomingDamage(
                    victimPlayer, vicStats, event.getDamager(), event.getCause(), event.getDamage());
            event.setDamage(defRes.damage());

            if (defRes.deflected()) {
                victimPlayer.getWorld().spawnParticle(Particle.CRIT, victimPlayer.getLocation().add(0, 1.1, 0), 8, 0.25, 0.25, 0.25, 0.08);
                victimPlayer.playSound(victimPlayer.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.7f, 1.5f);
            } else if (defRes.wardOrAegisReductionPct() > 0) {
                victimPlayer.getWorld().spawnParticle(Particle.ENCHANTED_HIT, victimPlayer.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0.05);
            }
            if (defRes.bastionCapped()) {
                victimPlayer.playSound(victimPlayer.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.6f);
            }
        }

        // -------------------------------------------------------------
        // STEP 3: POST-MITIGATION EFFECTS (Stasis, Execute Cull, Cleave, Siphon)
        // -------------------------------------------------------------
        if (attacker != null && atkStats != null && atkStats.hasAnyStats()) {
            // 5. STASIS (Offensive Chronon Debt Buffer — uses post-mitigation debt so armor doesn't mitigate twice)
            double stasisPct = Math.min(75.0, atkStats.get(ItemStat.STASIS));
            if (stasisPct > 0) {
                double ratio = stasisPct / 100.0;
                double postMitigationDebt = Math.max(0.0, event.getFinalDamage() * ratio);
                event.setDamage(event.getDamage() * (1.0 - ratio));
                if (postMitigationDebt > 0.05) {
                    scheduleStasisDetonation(attacker, victim, postMitigationDebt);
                }
            }

            // 6. EXECUTE (Cull Threshold on non-boss mobs using post-mitigation damage)
            double executePct = Math.min(50.0, atkStats.get(ItemStat.EXECUTE));
            if (executePct > 0) {
                handleExecuteCull(attacker, victim, executePct, event);
            }

            double finalDamageDealt = Math.max(0.0, event.getFinalDamage());

            // 7. CLEAVE (Armor-Piercing Arc & Shield Stun)
            double cleavePct = Math.min(100.0, atkStats.get(ItemStat.CLEAVE));
            if (cleavePct > 0 && finalDamageDealt > 0) {
                handleCleave(attacker, victim, finalDamageDealt, cleavePct);
            }

            // 8. SIPHON (Life Leech & Absorption Overflow from actual post-mitigation damage dealt)
            double siphonPct = Math.min(50.0, atkStats.get(ItemStat.SIPHON));
            if (siphonPct > 0 && finalDamageDealt > 0) {
                handleSiphon(attacker, finalDamageDealt, siphonPct);
            }
        }
    }

    private DCEItem getDCEItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        if (!itemRegistry.isDCEItem(item)) return null;
        String id = itemRegistry.getItemId(item);
        return itemRegistry.getItem(id);
    }

    private double getEquippedStatTotal(Player player, ItemStat stat) {
        double total = 0.0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            DCEItem dce = getDCEItem(armor);
            if (dce != null) {
                total += dce.getStat(stat);
            }
        }
        return total;
    }

    private boolean handleResonance(Player attacker, LivingEntity victim, double cadenceTarget, long now) {
        UUID pUuid = attacker.getUniqueId();
        long[] data = resonanceMap.computeIfAbsent(pUuid, k -> new long[]{0, 0});
        long lastHit = data[0];
        long diff = now - lastHit;
        int targetHits = Math.max(2, (int) Math.round(cadenceTarget));

        // Timed cadence window (between 400ms and 1400ms)
        if (diff >= 400 && diff <= 1400) {
            data[1] = Math.min(targetHits, data[1] + 1);
        } else {
            data[1] = 1;
        }
        data[0] = now;

        int stacks = (int) data[1];

        // Rhythmic acoustic pulse
        Location loc = victim.getLocation().add(0, 1, 0);
        attacker.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.0f + (stacks * 0.2f));
        victim.getWorld().spawnParticle(Particle.NOTE, loc, 3, 0.5, 0.5, 0.5, 0.0);

        if (stacks >= targetHits) {
            // HARMONIC SHATTER
            data[1] = 0;
            victim.getWorld().spawnParticle(Particle.SONIC_BOOM, loc, 1, 0, 0, 0, 0);
            attacker.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.5f);
            attacker.sendActionBar(ColorUtil.colorize("<#84E2CD>&l🎶 HARMONIC SHATTER &8| &fShields Disabled!"));

            if (victim instanceof Player vp) {
                vp.setCooldown(Material.SHIELD, 60); // 3-second shield disable
            }
            return true;
        }
        return false;
    }

    private double handleSurge(Player attacker, double cap, long now) {
        UUID pUuid = attacker.getUniqueId();
        long[] data = surgeMap.computeIfAbsent(pUuid, k -> new long[]{0, 0});
        long lastHit = data[0];
        long diff = now - lastHit;
        int maxStacks = Math.max(6, (int) Math.ceil(cap / 4.0));

        if (diff <= 3500) {
            data[1] = Math.min(maxStacks, data[1] + 1);
        } else {
            data[1] = 1;
        }
        data[0] = now;

        int stacks = (int) data[1];
        double bonus = Math.min(cap, stacks * 4.0);
        attacker.playSound(attacker.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.0f + (Math.min(stacks, 8) * 0.12f));
        attacker.sendActionBar(ColorUtil.colorize("<#FCD34D>⚡ Surge: <#FDE047>" + stacks + "x &8(&e+" + (int) Math.round(bonus) + "%&8)"));
        return bonus;
    }

    private void scheduleStasisDetonation(Player attacker, LivingEntity victim, double debt) {
        Location startLoc = victim.getLocation();
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                try {
                    if (!victim.isValid() || victim.isDead()) {
                        this.cancel();
                        return;
                    }

                    if (ticks < 50) { // 2.5 seconds = 50 ticks
                        victim.getWorld().spawnParticle(Particle.ENCHANT, victim.getLocation().add(0, 1.2, 0), 4, 0.3, 0.4, 0.3, 0.05);
                        ticks += 10;
                        return;
                    }

                    // Detonate post-mitigation debt directly so armor does not mitigate it a second time
                    double multiplier = victim.getLocation().distanceSquared(startLoc) > 4.0 ? 1.25 : 1.0;
                    double finalDmg = debt * multiplier;

                    IN_INTERNAL_DAMAGE.set(true);
                    victim.setMetadata("dce_internal_damage", new org.bukkit.metadata.FixedMetadataValue(DCCosmetics.getInstance(), true));
                    try {
                        double newHp = victim.getHealth() - finalDmg;
                        if (newHp <= 0.0) {
                            victim.setHealth(0.01);
                            victim.damage(100.0, attacker);
                        } else {
                            victim.setHealth(newHp);
                            victim.playHurtAnimation(0f);
                        }
                    } finally {
                        victim.removeMetadata("dce_internal_damage", DCCosmetics.getInstance());
                        IN_INTERNAL_DAMAGE.set(false);
                    }

                    victim.getWorld().spawnParticle(Particle.FIREWORK, victim.getLocation().add(0, 1.0, 0), 8, 0.3, 0.3, 0.3, 0.05);
                    victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 1.4f);
                } finally {
                    if (ticks >= 50) {
                        this.cancel();
                    }
                }
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 0L, 10L);
    }

    public static class CatalystData {
        public final double multiplier;
        public final double flatWeaknessOffset;
        public final List<String> debuffNames;
        public final boolean shouldHeal;

        public CatalystData(double multiplier, double flatWeaknessOffset, List<String> debuffNames, boolean shouldHeal) {
            this.multiplier = multiplier;
            this.flatWeaknessOffset = flatWeaknessOffset;
            this.debuffNames = debuffNames;
            this.shouldHeal = shouldHeal;
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHazardDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return;
        if (!(event.getEntity() instanceof Player victimPlayer)) return;
        if (IN_INTERNAL_DAMAGE.get() || victimPlayer.hasMetadata("dce_internal_damage")) return;

        PlayerStatSnapshot vicStats = statAggregator != null ? statAggregator.getSnapshot(victimPlayer) : null;
        StatCombatEngine.IncomingDamageResult defRes = StatCombatEngine.evaluateIncomingDamage(
                victimPlayer, vicStats, null, event.getCause(), event.getDamage());
        event.setDamage(defRes.damage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeathConduit(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof Player || dead instanceof ArmorStand) return;
        Player killer = dead.getKiller();
        if (killer == null) return;
        if (!(dead instanceof Monster) && !StatCombatEngine.isBossOrEmperor(dead)) return;

        PlayerStatSnapshot snap = statAggregator != null ? statAggregator.getSnapshot(killer) : null;
        int granted = StatCombatEngine.tryConduitMobKillRefund(killer, snap);
        if (granted > 0) {
            killer.playSound(killer.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.6f);
            killer.sendActionBar(ColorUtil.colorize("<#22D3EE>⚛ Conduit Siphon: &b+" + granted + " mC"));
        }
    }

    public static CatalystData calculateCatalyst(Player attacker, double statVal) {
        return StatCombatEngine.calculateCatalyst(attacker, statVal);
    }

    public static boolean isBossOrEmperor(Entity entity) {
        return StatCombatEngine.isBossOrEmperor(entity);
    }

    private void handleExecuteCull(Player attacker, LivingEntity victim, double thresholdPct, EntityDamageByEntityEvent event) {
        // Cull threshold on non-boss mobs
        if (!isBossOrEmperor(victim) && !(victim instanceof Player)) {
            double currentHp = victim.getHealth() - event.getFinalDamage();
            double maxHp = victim.getMaxHealth();
            if (currentHp > 0 && (currentHp / maxHp) <= (thresholdPct / 100.0)) {
                event.setDamage(victim.getHealth() + 20.0);
                victim.getWorld().spawnParticle(Particle.LARGE_SMOKE, victim.getLocation().add(0, 1, 0), 12, 0.4, 0.4, 0.4, 0.1);
                attacker.playSound(victim.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, 0.7f, 1.6f);
            }
        }
    }

    private void handleCleave(Player attacker, LivingEntity primaryVictim, double baseDamage, double cleavePct) {
        double cleaveDamage = baseDamage * (cleavePct / 100.0);
        Location center = attacker.getLocation();

        for (Entity nearby : primaryVictim.getNearbyEntities(3.5, 2.0, 3.5)) {
            if (!(nearby instanceof LivingEntity target) || nearby.equals(primaryVictim) || nearby.equals(attacker)) continue;
            if (nearby instanceof ArmorStand) continue;
            if (target.hasMetadata("NPC")) continue;

            // Skip ModelEngine sub-hitboxes to prevent multi-hitbox duplication and recursion
            String targetCls = target.getClass().getName();
            if (targetCls.contains("Hitbox") || targetCls.contains("ModelEngine") || target.hasMetadata("modelengine") || target.hasMetadata("meg_hitbox")) {
                continue;
            }

            IN_INTERNAL_DAMAGE.set(true);
            target.setMetadata("dce_internal_damage", new org.bukkit.metadata.FixedMetadataValue(DCCosmetics.getInstance(), true));
            try {
                target.damage(cleaveDamage, attacker);
            } finally {
                target.removeMetadata("dce_internal_damage", DCCosmetics.getInstance());
                IN_INTERNAL_DAMAGE.set(false);
            }
            target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);

            if (target instanceof Player vp && vp.isBlocking()) {
                vp.setCooldown(Material.SHIELD, 50); // Guard-break stun
                vp.playSound(vp.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.2f, 0.8f);
            }
        }
    }

    private void handleSiphon(Player attacker, double damageDealt, double siphonPct) {
        double healAmount = damageDealt * (siphonPct / 100.0);
        double maxHp = attacker.getMaxHealth();
        double currentHp = attacker.getHealth();

        if (currentHp < maxHp) {
            attacker.setHealth(Math.min(maxHp, currentHp + healAmount));
            attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 2.0, 0), 1, 0.2, 0.2, 0.2, 0.0);
        } else {
            // Surplus becomes Golden Absorption Hearts (up to +6 absorption)
            float currentAbs = (float) attacker.getAbsorptionAmount();
            if (currentAbs < 12.0f) { // 12 HP = 6 absorption hearts
                attacker.setAbsorptionAmount(Math.min(12.0f, currentAbs + (float) (healAmount * 0.5)));
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        resonanceMap.remove(uuid);
        surgeMap.remove(uuid);
        StatCombatEngine.clearPlayer(uuid);
    }
}
