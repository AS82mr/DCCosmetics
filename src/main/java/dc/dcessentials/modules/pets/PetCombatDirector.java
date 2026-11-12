package dc.dcessentials.modules.pets;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.pets.config.PetsConfig;
import dc.dcessentials.modules.pets.model.PetSkillSpec;
import dc.dcessentials.modules.pets.model.PetSpec;
import dc.dcessentials.modules.pets.model.PlayerPetData;
import dc.dcessentials.modules.pets.vfx.ModelEngineVfxBridge;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Executes high-impact active companion skills, ModelEngine VFX animations,
 * and the mob-look target tracker ring indicator.
 */
public class PetCombatDirector {

    private final PetsConfig config;

    public PetCombatDirector(PetsConfig config) {
        this.config = config;
    }

    /**
     * Finds the entity the player is directly looking at within tracking distance,
     * renders the rotating particle target circle, and returns the tracked entity (or null).
     */
    public LivingEntity tickTargetTracker(Player owner, PlayerPetData data, PetSpec activeSpec) {
        if (owner == null || !owner.isOnline() || config == null || !config.isTrackerEnabled()) return null;
        if (data == null || !data.isTrackerEnabled() || activeSpec == null) return null;

        double maxLook = config.getTrackerMaxLookDistance();
        double skillRange = activeSpec.getActiveSkill() != null ? activeSpec.getActiveSkill().getRange() : maxLook;
        double searchDist = Math.max(maxLook, skillRange);

        World world = owner.getWorld();
        Location eye = owner.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        RayTraceResult hit = world.rayTrace(
                eye,
                dir,
                searchDist,
                FluidCollisionMode.NEVER,
                true,
                0.95,
                entity -> {
                    if (!(entity instanceof LivingEntity le)) return false;
                    if (le.equals(owner) || le.isDead() || !le.isValid()) return false;
                    if (le instanceof ArmorStand || le.hasMetadata("NPC")) return false;
                    if (le instanceof Player p && !data.isAlly(p.getUniqueId()) && !world.getPVP()) return false;
                    return owner.hasLineOfSight(le);
                }
        );

        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
            return null;
        }

        double dist = owner.getLocation().distance(target.getLocation());
        if (target instanceof Player p && data.isAlly(p.getUniqueId())) {
            drawTrackerRing(owner, target,
                    parseHexColor(config.getTrackerAllyColorFrom(), Color.fromRGB(0x8E, 0xE6, 0xEC)),
                    parseHexColor(config.getTrackerAllyColorTo(), Color.fromRGB(0xEA, 0xFD, 0x47)),
                    false);
            return target;
        }

        if (dist <= skillRange) {
            drawTrackerRing(owner, target,
                    parseHexColor(config.getTrackerEnemyColorFrom(), Color.fromRGB(0xE1, 0x17, 0x17)),
                    parseHexColor(config.getTrackerEnemyColorTo(), Color.fromRGB(0xFC, 0x5A, 0xA4)),
                    true);
        } else if (config.isTrackerOutOfRangeRing()) {
            drawOutOfRangeRing(owner, target);
        }

        return target;
    }

    public void drawTrackerRing(Player viewer, LivingEntity target, Color from, Color to, boolean enemyStyle) {
        if (target == null || target.isDead()) return;
        Location base = target.getLocation().add(0, 0.14, 0);
        World world = base.getWorld();
        if (world == null) return;

        int pts = config != null ? config.getTrackerRingPoints() : 18;
        double pad = config != null ? config.getTrackerRadiusPadding() : 0.35;
        double radius = Math.max(0.65, (target.getWidth() * 0.65) + pad);
        double phase = (System.currentTimeMillis() % 1800L) / 1800.0 * (Math.PI * 2.0);
        Particle.DustTransition transition = new Particle.DustTransition(from, to, enemyStyle ? 0.95f : 0.85f);

        for (int i = 0; i < pts; i++) {
            double angle = phase + (2.0 * Math.PI * i) / pts;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location pLoc = base.clone().add(x, 0.0, z);
            if (viewer != null && viewer.isOnline()) {
                viewer.spawnParticle(Particle.DUST_COLOR_TRANSITION, pLoc, 1, 0, 0, 0, 0, transition);
            } else {
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, pLoc, 1, 0, 0, 0, 0, transition);
            }
        }

        // Subtle cardinal reticle accents for enemy lock visibility
        if (enemyStyle && viewer != null && viewer.isOnline()) {
            for (int c = 0; c < 4; c++) {
                double a = phase + (Math.PI * 0.5 * c);
                Location mark = base.clone().add(Math.cos(a) * (radius + 0.18), 0.12, Math.sin(a) * (radius + 0.18));
                viewer.spawnParticle(Particle.DUST_COLOR_TRANSITION, mark, 1, 0, 0, 0, 0, transition);
            }
        }
    }

    private void drawOutOfRangeRing(Player viewer, LivingEntity target) {
        Location base = target.getLocation().add(0, 0.14, 0);
        double radius = Math.max(0.75, (target.getWidth() * 0.65) + 0.40);
        double phase = (System.currentTimeMillis() % 1200L) / 1200.0 * (Math.PI * 2.0);
        int pts = 10;
        for (int i = 0; i < pts; i++) {
            double angle = phase + (2.0 * Math.PI * i) / pts;
            Location pLoc = base.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            viewer.spawnParticle(Particle.ELECTRIC_SPARK, pLoc, 1, 0, 0, 0, 0);
        }
    }

    public void executeSkill(Player owner,
                             PlayerPetData data,
                             PetSpec spec,
                             PetSkillSpec skill,
                             int level,
                             double powerMultiplier,
                             Location petLocation,
                             LivingEntity target) {
        if (owner == null || !owner.isOnline() || skill == null) return;
        Location castOrigin = petLocation != null ? petLocation.clone() : owner.getEyeLocation();
        double rawPower = skill.getEffectivePower(level, spec.getMaxLevel()) * Math.max(0.5, powerMultiplier);
        double power = Math.round(rawPower * 10.0) / 10.0;
        String id = skill.getSkillId().toLowerCase(Locale.ROOT);

        // Immediately flash a brief lock-on circle on primary target
        if (target != null && !target.isDead()) {
            drawTrackerRing(owner, target,
                    parseHexColor(config.getTrackerEnemyColorFrom(), Color.fromRGB(0xE1, 0x17, 0x17)),
                    parseHexColor(config.getTrackerEnemyColorTo(), Color.fromRGB(0xFC, 0x5A, 0xA4)),
                    true);
        }

        switch (id) {
            case "spark_cinder" -> runSparkCinder(owner, data, castOrigin, target, power);
            case "striker_burst" -> runStrikerBurst(owner, data, castOrigin, target, power);
            case "ember_stream" -> runEmberStream(owner, data, castOrigin, target, power);
            case "mend_pulse" -> runMendPulse(owner, data, power);
            case "salvo_barrage" -> runSalvoBarrage(owner, data, castOrigin, target, power);
            case "bastion_aegis" -> runBastionAegis(owner, data, skill, power);
            case "zephyr_tempest" -> runZephyrTempest(owner, data, skill, castOrigin, target, power);
            case "terra_rupture" -> runTerraRupture(owner, data, skill, castOrigin, target, power);
            case "verdant_bloom" -> runVerdantBloom(owner, data, skill, castOrigin, target, power);
            case "glacies_permafrost" -> runGlaciesPermafrost(owner, data, skill, target, power);
            case "pyre_cataclysm" -> runPyreCataclysm(owner, data, skill, castOrigin, target, power);
            case "umbra_eclipse" -> runUmbraEclipse(owner, data, skill, target, power);
            case "solis_zenith" -> runSolisZenith(owner, data, skill, target, power);
            case "astral_supernova" -> runAstralSupernova(owner, data, skill, castOrigin, target, power);
            case "crimson_dominion" -> runCrimsonDominion(owner, data, skill, castOrigin, target, power);
            default -> runSparkCinder(owner, data, castOrigin, target, power);
        }
    }

    private void spawnModelVfx(PetSkillSpec skill, Location loc, double scale, int durationTicks, String fallbackModel, String fallbackAnim) {
        if (config != null && !config.isModelEngineVfxEnabled()) return;
        int dur = durationTicks > 0 ? durationTicks : (config != null ? config.getDefaultVfxDurationTicks() : 70);
        ModelEngineVfxBridge.VfxHandle handle = null;
        if (skill.getVfxModelId() != null && !skill.getVfxModelId().isBlank()) {
            handle = ModelEngineVfxBridge.spawnEffect(skill.getVfxModelId(), loc, skill.getVfxAnimation(), scale, dur);
        }
        if (handle == null && fallbackModel != null && !fallbackModel.isBlank()) {
            ModelEngineVfxBridge.spawnEffect(fallbackModel, loc, fallbackAnim, scale, dur);
        }
    }

    private void runSparkCinder(Player owner, PlayerPetData data, Location origin, LivingEntity target, double power) {
        if (target == null || target.isDead()) return;
        origin.getWorld().playSound(origin, Sound.ENTITY_BLAZE_SHOOT, 0.7f, 1.45f);
        launchHomingBeam(origin, target, Particle.FLAME, Color.fromRGB(255, 145, 40), () -> {
            if (target.isDead()) return;
            target.setFireTicks(Math.max(target.getFireTicks(), 60));
            applyPetDamage(owner, data, target, power);
            target.getWorld().spawnParticle(Particle.LAVA, target.getLocation().add(0, 0.8, 0), 10, 0.35, 0.35, 0.35, 0.04);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.45f, 1.7f);
        });
    }

    private void runStrikerBurst(Player owner, PlayerPetData data, Location origin, LivingEntity target, double power) {
        if (target == null || target.isDead()) return;
        double perShot = power / 4.0;
        new BukkitRunnable() {
            int shots = 0;
            @Override
            public void run() {
                if (!owner.isOnline() || target.isDead() || !target.isValid() || shots >= 4) {
                    cancel();
                    return;
                }
                Location from = origin.clone().add(0, 0.1, 0);
                Location to = target.getEyeLocation().clone().add(0, -0.35, 0);
                drawParticleLine(from, to, Particle.CRIT, Color.fromRGB(56, 189, 248), 14);
                from.getWorld().playSound(from, Sound.ENTITY_ARROW_SHOOT, 0.75f, 1.55f + (shots * 0.1f));
                target.setNoDamageTicks(0);
                applyPetDamage(owner, data, target, perShot);
                shots++;
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 0L, 3L);
    }

    private void runEmberStream(Player owner, PlayerPetData data, Location origin, LivingEntity target, double power) {
        Location center = target != null && !target.isDead() ? target.getLocation() : origin.clone().add(owner.getLocation().getDirection().multiply(4));
        origin.getWorld().playSound(origin, Sound.ITEM_FIRECHARGE_USE, 0.9f, 0.95f);
        drawParticleLine(origin, center.clone().add(0, 1.0, 0), Particle.FLAME, Color.fromRGB(251, 146, 60), 22);
        spawnParticleRing(center.clone().add(0, 0.3, 0), 3.6, 26, Particle.FLAME);
        spawnParticleRing(center.clone().add(0, 0.6, 0), 2.2, 16, Particle.SWEEP_ATTACK);

        for (LivingEntity victim : getValidAoeTargets(owner, data, center, 4.2, target)) {
            victim.setFireTicks(Math.max(victim.getFireTicks(), 90));
            applyPetDamage(owner, data, victim, power);
        }
    }

    private void runMendPulse(Player owner, PlayerPetData data, double power) {
        Location center = owner.getLocation();
        World world = center.getWorld();
        if (world == null) return;
        double healAmount = Math.max(4.0, power * 0.35);
        double blastPower = Math.max(8.0, power * 0.55);

        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.85f, 1.35f);
        healLiving(owner, healAmount);
        owner.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 1, false, false, true));

        for (Entity e : center.getWorld().getNearbyEntities(center, 6.5, 4.5, 6.5)) {
            if (e instanceof Player p && !p.equals(owner) && data.isAlly(p.getUniqueId())) {
                healLiving(p, healAmount);
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, false, false, true));
            } else if (e instanceof LivingEntity le && isValidAoeCandidate(owner, data, le, null)) {
                Vector push = le.getLocation().toVector().subtract(center.toVector()).setY(0.38).normalize().multiply(0.85);
                le.setVelocity(push);
                applyPetDamage(owner, data, le, blastPower);
            }
        }
        spawnParticleRing(center.clone().add(0, 0.5, 0), 3.0, 28, Particle.HAPPY_VILLAGER);
        spawnParticleRing(center.clone().add(0, 1.1, 0), 4.5, 28, Particle.END_ROD);
    }

    private void runSalvoBarrage(Player owner, PlayerPetData data, Location origin, LivingEntity target, double power) {
        if (target == null || target.isDead()) return;
        double perMissile = power / 3.0;
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (!owner.isOnline() || count >= 3) {
                    cancel();
                    return;
                }
                Location dest = !target.isDead() ? target.getLocation().add(0, 0.8, 0) : owner.getLocation();
                origin.getWorld().playSound(origin, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.25f);
                launchArcingProjectile(origin.clone().add((count - 1) * 0.5, 0.4, 0), dest, () -> {
                    dest.getWorld().spawnParticle(Particle.EXPLOSION, dest, 1);
                    dest.getWorld().playSound(dest, Sound.ENTITY_GENERIC_EXPLODE, 0.65f, 1.3f);
                    for (LivingEntity v : getValidAoeTargets(owner, data, dest, 3.6, target)) {
                        v.setNoDamageTicks(0);
                        applyPetDamage(owner, data, v, perMissile);
                    }
                });
                count++;
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 0L, 4L);
    }

    private void runBastionAegis(Player owner, PlayerPetData data, PetSkillSpec skill, double power) {
        Location center = owner.getLocation();
        World world = center.getWorld();
        if (world == null) return;

        world.playSound(center, Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.8f);
        owner.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 1, false, false, true));
        owner.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1, false, false, true));

        ModelEngineVfxBridge.VfxHandle dome = ModelEngineVfxBridge.spawnEffect("drone_shieldvfx", center, "shield", 1.15, 55);
        if (dome != null) {
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (!owner.isOnline() || ticks >= 50) {
                        dome.destroy();
                        cancel();
                        return;
                    }
                    dome.teleport(owner.getLocation());
                    ticks += 2;
                }
            }.runTaskTimer(DCCosmetics.getInstance(), 2L, 2L);
        }

        spawnParticleRing(center.clone().add(0, 1.0, 0), 3.4, 30, Particle.ELECTRIC_SPARK);
        for (LivingEntity v : getValidAoeTargets(owner, data, center, 5.2, null)) {
            Vector push = v.getLocation().toVector().subtract(center.toVector()).setY(0.45).normalize().multiply(1.15);
            v.setVelocity(push);
            applyPetDamage(owner, data, v, power);
        }
    }

    private void runZephyrTempest(Player owner, PlayerPetData data, PetSkillSpec skill, Location origin, LivingEntity target, double power) {
        Location center = target != null && !target.isDead() ? target.getLocation().clone() : origin.clone().add(owner.getLocation().getDirection().multiply(5));
        owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 1, false, false, true));
        spawnModelVfx(skill, center, 1.15, 115, "nocsy_drakonin_tornado", "idle");

        double perTick = power / 4.0;
        new BukkitRunnable() {
            int step = 0;
            @Override
            public void run() {
                if (!owner.isOnline() || step >= 4) {
                    cancel();
                    return;
                }
                Location eye = center.clone().add(0, 0.25 + (step * 0.45), 0);
                eye.getWorld().playSound(eye, Sound.ENTITY_PHANTOM_FLAP, 0.9f, 0.65f + (step * 0.2f));
                spawnParticleRing(eye, 3.8 - (step * 0.4), 26, Particle.CLOUD);
                for (LivingEntity v : getValidAoeTargets(owner, data, center, 5.5, target)) {
                    v.setNoDamageTicks(0);
                    applyPetDamage(owner, data, v, perTick);
                    if (step < 3) {
                        Vector pull = center.toVector().subtract(v.getLocation().toVector()).multiply(0.32).setY(0.16);
                        v.setVelocity(pull);
                    } else {
                        v.setVelocity(new Vector(0, 0.78, 0));
                    }
                }
                step++;
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 0L, 6L);
    }

    private void runTerraRupture(Player owner, PlayerPetData data, PetSkillSpec skill, Location origin, LivingEntity target, double power) {
        Location impact = target != null && !target.isDead() ? target.getLocation().clone() : origin.clone().add(owner.getLocation().getDirection().multiply(5));
        impact.setYaw(owner.getLocation().getYaw());
        spawnModelVfx(skill, impact, 1.15, 95, "vfx_earthquake_rupture_1", "skill");

        Location start = owner.getLocation().clone().add(0, 0.3, 0);
        Vector dir = impact.toVector().subtract(start.toVector()).setY(0).normalize();
        if (dir.lengthSquared() < 0.01) dir = owner.getLocation().getDirection().setY(0).normalize();
        Vector finalDir = dir;

        new BukkitRunnable() {
            int seg = 0;
            @Override
            public void run() {
                if (!owner.isOnline() || seg >= 5) {
                    cancel();
                    return;
                }
                Location point = impact.clone().add(finalDir.clone().multiply((seg - 2) * 1.4));
                point.getWorld().playSound(point, Sound.BLOCK_BASALT_BREAK, 1.0f, 0.6f);
                point.getWorld().spawnParticle(Particle.BLOCK, point.clone().add(0, 0.6, 0), 22, 0.7, 0.5, 0.7, 0.15, Material.STONE.createBlockData());
                if (seg == 4) {
                    point.getWorld().playSound(impact, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.75f);
                    for (LivingEntity v : getValidAoeTargets(owner, data, impact, 5.0, target)) {
                        v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 90, 3, false, true, true));
                        v.setVelocity(new Vector(0, 0.65, 0));
                        applyPetDamage(owner, data, v, power);
                    }
                }
                seg++;
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 0L, 3L);
    }

    private void runVerdantBloom(Player owner, PlayerPetData data, PetSkillSpec skill, Location origin, LivingEntity target, double power) {
        Location dest = target != null && !target.isDead() ? target.getLocation().clone() : origin.clone().add(owner.getLocation().getDirection().multiply(6));
        dest.setYaw(owner.getLocation().getYaw());
        spawnModelVfx(skill, dest, 1.15, 85, "nocsy_drakonin_roots", "spawn");

        double perPulse = power / 3.0;
        new BukkitRunnable() {
            int pulse = 0;
            @Override
            public void run() {
                if (!owner.isOnline() || pulse >= 3) {
                    cancel();
                    return;
                }
                Location targetLoc = !dest.getChunk().isLoaded() ? origin : (target != null && !target.isDead() ? target.getEyeLocation() : dest.clone().add(0, 1.0, 0));
                drawParticleLine(origin, targetLoc, Particle.HAPPY_VILLAGER, Color.fromRGB(52, 211, 153), 24);
                targetLoc.getWorld().playSound(targetLoc, Sound.BLOCK_AZALEA_LEAVES_BREAK, 0.9f, 1.2f);
                spawnParticleRing(targetLoc.clone().add(0, -0.6, 0), 3.6, 22, Particle.SPORE_BLOSSOM_AIR);

                for (LivingEntity v : getValidAoeTargets(owner, data, targetLoc, 4.4, target)) {
                    v.setNoDamageTicks(0);
                    v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, true, true));
                    applyPetDamage(owner, data, v, perPulse);
                }
                healLiving(owner, Math.max(2.0, perPulse * 0.22));
                pulse++;
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 0L, 6L);
    }

    private void runGlaciesPermafrost(Player owner, PlayerPetData data, PetSkillSpec skill, LivingEntity target, double power) {
        if (target == null || target.isDead()) return;
        Location center = target.getLocation().clone();
        spawnModelVfx(skill, center, 1.15, 80, "", "");
        center.getWorld().playSound(center, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.65f);
        center.getWorld().playSound(center, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 1.1f);
        center.getWorld().spawnParticle(Particle.SNOWFLAKE, center.clone().add(0, 1.0, 0), 40, 1.8, 1.0, 1.8, 0.06);
        center.getWorld().spawnParticle(Particle.BLOCK, center.clone().add(0, 0.8, 0), 28, 1.4, 0.8, 1.4, 0.12, Material.BLUE_ICE.createBlockData());
        spawnParticleRing(center.clone().add(0, 0.4, 0), 4.8, 32, Particle.END_ROD);

        for (LivingEntity v : getValidAoeTargets(owner, data, center, 5.2, target)) {
            v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 90, 4, false, true, true));
            v.setFreezeTicks(Math.max(v.getFreezeTicks(), 160));
            applyPetDamage(owner, data, v, power);
        }
    }

    private void runPyreCataclysm(Player owner, PlayerPetData data, PetSkillSpec skill, Location origin, LivingEntity target, double power) {
        if (target == null || target.isDead()) return;
        Location vfxLoc = target.getLocation().clone();
        spawnModelVfx(skill, vfxLoc, 1.2, 80, "nocsy_drakonin_firespell", "idle");

        double perVolley = power / 3.0;
        new BukkitRunnable() {
            int wave = 0;
            @Override
            public void run() {
                if (!owner.isOnline() || wave >= 3) {
                    cancel();
                    return;
                }
                Location dest = !target.isDead() ? target.getLocation().add(0, 0.9, 0) : vfxLoc.clone().add(0, 0.9, 0);
                origin.getWorld().playSound(origin, Sound.ENTITY_BLAZE_SHOOT, 0.9f, 0.85f + (wave * 0.15f));
                launchHomingBeam(origin, target, Particle.FLAME, Color.fromRGB(249, 115, 22), () -> {
                    dest.getWorld().spawnParticle(Particle.EXPLOSION, dest, 1);
                    dest.getWorld().spawnParticle(Particle.LAVA, dest, 14, 0.9, 0.5, 0.9, 0.05);
                    dest.getWorld().playSound(dest, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.1f);
                    for (LivingEntity v : getValidAoeTargets(owner, data, dest, 4.2, target)) {
                        v.setNoDamageTicks(0);
                        v.setFireTicks(Math.max(v.getFireTicks(), 100));
                        applyPetDamage(owner, data, v, perVolley);
                    }
                });
                wave++;
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 0L, 6L);
    }

    private void runUmbraEclipse(Player owner, PlayerPetData data, PetSkillSpec skill, LivingEntity target, double power) {
        if (target == null || target.isDead()) return;
        Location center = target.getLocation().clone();
        spawnModelVfx(skill, center, 1.2, 80, "nocsy_drakonin_shadowspell", "idle");

        center.getWorld().playSound(center, Sound.ENTITY_WITHER_SHOOT, 0.85f, 0.65f);
        spawnParticleRing(center.clone().add(0, 0.5, 0), 4.6, 32, Particle.WITCH);
        spawnParticleRing(center.clone().add(0, 1.3, 0), 3.0, 22, Particle.DRAGON_BREATH);

        double perTick = power / 2.0;
        new BukkitRunnable() {
            int pulse = 0;
            @Override
            public void run() {
                if (!owner.isOnline() || pulse >= 2) {
                    cancel();
                    return;
                }
                for (LivingEntity v : getValidAoeTargets(owner, data, center, 5.2, target)) {
                    v.setNoDamageTicks(0);
                    v.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1, false, true, true));
                    v.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1, false, true, true));
                    v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2, false, true, true));
                    Vector drag = center.toVector().subtract(v.getLocation().toVector()).multiply(0.35);
                    drag.setY(-0.22);
                    v.setVelocity(drag);
                    applyPetDamage(owner, data, v, perTick);
                }
                pulse++;
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 0L, 12L);
    }

    private void runSolisZenith(Player owner, PlayerPetData data, PetSkillSpec skill, LivingEntity target, double power) {
        Location center = target != null && !target.isDead() ? target.getLocation().clone() : owner.getLocation().clone().add(owner.getLocation().getDirection().multiply(5));
        spawnModelVfx(skill, center, 1.2, 100, "", "");

        double healAmount = Math.max(5.0, power * 0.35);
        healLiving(owner, healAmount);
        for (Entity e : center.getWorld().getNearbyEntities(owner.getLocation(), 7.5, 5.0, 7.5)) {
            if (e instanceof Player p && !p.equals(owner) && data.isAlly(p.getUniqueId())) {
                healLiving(p, healAmount);
            }
        }

        double rainTickDamage = (power * 0.65) / 8.0;
        double finalZenithDamage = power * 0.45;
        new BukkitRunnable() {
            int strike = 0;
            @Override
            public void run() {
                if (!owner.isOnline()) {
                    cancel();
                    return;
                }
                if (strike < 8) {
                    double angle = (Math.PI * 2.0 * strike) / 8.0;
                    Location impact = center.clone().add(Math.cos(angle) * 2.2, 0.2, Math.sin(angle) * 2.2);
                    Location sky = impact.clone().add(0, 6.5, 0);
                    drawParticleLine(sky, impact, Particle.END_ROD, Color.fromRGB(253, 224, 71), 14);
                    impact.getWorld().playSound(impact, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.85f, 1.2f + (strike * 0.06f));
                    for (LivingEntity v : getValidAoeTargets(owner, data, impact, 3.8, target)) {
                        v.setNoDamageTicks(0);
                        applyPetDamage(owner, data, v, rainTickDamage);
                    }
                    strike++;
                } else {
                    center.getWorld().playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.35f);
                    spawnParticleRing(center.clone().add(0, 0.8, 0), 5.2, 34, Particle.END_ROD);
                    for (LivingEntity v : getValidAoeTargets(owner, data, center, 5.5, target)) {
                        v.setNoDamageTicks(0);
                        applyPetDamage(owner, data, v, finalZenithDamage);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 0L, 3L);
    }

    private void runAstralSupernova(Player owner, PlayerPetData data, PetSkillSpec skill, Location origin, LivingEntity target, double power) {
        if (target == null || target.isDead()) return;
        Location anchor = target.getLocation().clone();
        spawnModelVfx(skill, anchor, 1.2, 95, "", "");

        List<LivingEntity> hopTargets = getValidAoeTargets(owner, data, anchor, 7.0, target);
        if (hopTargets.isEmpty()) hopTargets.add(target);

        double hopDmg = (power * 0.55) / 4.0;
        double novaDmg = power * 0.45;
        new BukkitRunnable() {
            int hop = 0;
            Location prev = origin.clone();
            @Override
            public void run() {
                if (!owner.isOnline()) {
                    cancel();
                    return;
                }
                if (hop < 4) {
                    LivingEntity cur = hopTargets.get(hop % hopTargets.size());
                    if (!cur.isDead() && cur.isValid()) {
                        Location dest = cur.getEyeLocation();
                        drawParticleLine(prev, dest, Particle.WITCH, Color.fromRGB(192, 132, 252), 18);
                        dest.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.4f + (hop * 0.1f));
                        cur.setNoDamageTicks(0);
                        applyPetDamage(owner, data, cur, hopDmg);
                        prev = dest;
                    }
                    hop++;
                } else {
                    Location explodeAt = !target.isDead() ? target.getLocation() : anchor;
                    explodeAt.getWorld().playSound(explodeAt, Sound.ENTITY_GENERIC_EXPLODE, 0.95f, 0.7f);
                    spawnParticleRing(explodeAt.clone().add(0, 1.0, 0), 5.6, 36, Particle.WITCH);
                    for (LivingEntity v : getValidAoeTargets(owner, data, explodeAt, 5.8, target)) {
                        v.setNoDamageTicks(0);
                        applyPetDamage(owner, data, v, novaDmg);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 0L, 4L);
    }

    private void runCrimsonDominion(Player owner, PlayerPetData data, PetSkillSpec skill, Location origin, LivingEntity target, double power) {
        if (target == null || target.isDead()) return;
        Location center = target.getLocation().clone();
        spawnModelVfx(skill, center, 1.35, 85, "nocsy_drakonin_firespell", "idle");

        center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.9f, 1.25f);
        center.getWorld().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.65f, 1.4f);
        center.getWorld().strikeLightningEffect(center);

        spawnParticleRing(center.clone().add(0, 0.4, 0), 3.0, 26, Particle.DRAGON_BREATH);
        spawnParticleRing(center.clone().add(0, 1.0, 0), 5.2, 34, Particle.FLAME);

        for (LivingEntity v : getValidAoeTargets(owner, data, center, 6.2, target)) {
            v.setNoDamageTicks(0);
            double execBonus = 0.0;
            var maxAttr = v.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (maxAttr != null && maxAttr.getValue() > 0) {
                double missingRatio = Math.max(0.0, 1.0 - (v.getHealth() / maxAttr.getValue()));
                execBonus = power * 0.30 * missingRatio;
            }
            applyPetDamage(owner, data, v, power + execBonus);
            v.setFireTicks(Math.max(v.getFireTicks(), 100));
        }
    }

    private void launchHomingBeam(Location start, LivingEntity target, Particle particle, Color dustColor, Runnable onArrive) {
        Location dest = target.getLocation().add(0, target.getHeight() * 0.55, 0);
        drawParticleLine(start, dest, particle, dustColor, 20);
        if (onArrive != null) {
            onArrive.run();
        }
    }

    private void launchArcingProjectile(Location start, Location end, Runnable onImpact) {
        new BukkitRunnable() {
            int step = 0;
            final int total = 6;
            @Override
            public void run() {
                if (step >= total) {
                    if (onImpact != null) onImpact.run();
                    cancel();
                    return;
                }
                double t = (double) (step + 1) / total;
                Location p = interpolateLocation(start, end, t);
                p.add(0, Math.sin(t * Math.PI) * 2.2, 0);
                p.getWorld().spawnParticle(Particle.FIREWORK, p, 2, 0.05, 0.05, 0.05, 0.01);
                p.getWorld().spawnParticle(Particle.FLAME, p, 2, 0.04, 0.04, 0.04, 0.01);
                step++;
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 0L, 1L);
    }

    private void drawParticleLine(Location from, Location to, Particle particle, Color dustColor, int steps) {
        World world = from.getWorld();
        if (world == null || to == null || !world.equals(to.getWorld())) return;
        Particle.DustOptions dust = new Particle.DustOptions(dustColor, 1.05f);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / Math.max(1, steps);
            Location point = interpolateLocation(from, to, t);
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
            if (i % 2 == 0) {
                world.spawnParticle(particle, point, 1, 0.04, 0.04, 0.04, 0.01);
            }
        }
    }

    private static Location interpolateLocation(Location from, Location to, double t) {
        double x = from.getX() + (to.getX() - from.getX()) * t;
        double y = from.getY() + (to.getY() - from.getY()) * t;
        double z = from.getZ() + (to.getZ() - from.getZ()) * t;
        return new Location(from.getWorld(), x, y, z);
    }

    private void spawnParticleRing(Location center, double radius, int count, Particle particle) {
        World world = center.getWorld();
        if (world == null) return;
        for (int i = 0; i < count; i++) {
            double angle = (2.0 * Math.PI * i) / count;
            Location point = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            world.spawnParticle(particle, point, 1, 0, 0, 0, 0);
        }
    }

    private List<LivingEntity> getValidAoeTargets(Player owner, PlayerPetData data, Location center, double radius, LivingEntity primary) {
        List<LivingEntity> list = new ArrayList<>();
        if (primary != null && !primary.isDead() && primary.isValid() && isValidAoeCandidate(owner, data, primary, primary)) {
            list.add(primary);
        }
        if (center.getWorld() == null) return list;
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius * 0.85, radius)) {
            if (e instanceof LivingEntity le && !list.contains(le) && isValidAoeCandidate(owner, data, le, primary)) {
                list.add(le);
            }
        }
        return list;
    }

    private boolean isValidAoeCandidate(Player owner, PlayerPetData data, LivingEntity candidate, LivingEntity primary) {
        if (candidate == null || candidate.isDead() || !candidate.isValid()) return false;
        if (candidate.equals(owner) || candidate instanceof ArmorStand || candidate.hasMetadata("NPC")) return false;
        if (data != null && data.isAlly(candidate.getUniqueId())) return false;
        if (candidate instanceof Player p && !candidate.getWorld().getPVP()) return false;
        if (candidate instanceof Tameable tame && tame.isTamed() && !candidate.equals(primary)) return false;
        // Direct primary target is always valid; splash AoE targets monsters/bosses or same entity type
        if (candidate.equals(primary)) return true;
        return candidate instanceof Monster || candidate instanceof Boss || candidate instanceof org.bukkit.entity.Slime
                || candidate instanceof org.bukkit.entity.Phantom || candidate instanceof org.bukkit.entity.Ghast
                || candidate instanceof org.bukkit.entity.Hoglin;
    }

    public static boolean isValidEnemy(Player owner, LivingEntity candidate, PlayerPetData data) {
        if (candidate == null || candidate.isDead() || !candidate.isValid()) return false;
        if (owner != null && candidate.equals(owner)) return false;
        if (candidate instanceof ArmorStand || candidate.hasMetadata("NPC")) return false;
        if (data != null && data.isAlly(candidate.getUniqueId())) return false;
        if (candidate instanceof Player && candidate.getWorld() != null && !candidate.getWorld().getPVP()) return false;
        return true;
    }

    private void applyPetDamage(Player owner, PlayerPetData data, LivingEntity target, double amount) {
        if (target == null || target.isDead() || amount <= 0.0) return;
        if (target instanceof Player p && (!target.getWorld().getPVP() || (data != null && data.isAlly(p.getUniqueId())))) return;
        target.setMetadata("dce_internal_damage", new org.bukkit.metadata.FixedMetadataValue(DCCosmetics.getInstance(), true));
        try {
            target.damage(amount, owner);
        } finally {
            target.removeMetadata("dce_internal_damage", DCCosmetics.getInstance());
        }
    }

    private void healLiving(LivingEntity entity, double amount) {
        if (entity == null || entity.isDead() || amount <= 0.0) return;
        var maxAttr = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double maxHp = maxAttr != null ? maxAttr.getValue() : 20.0;
        entity.setHealth(Math.min(maxHp, entity.getHealth() + amount));
    }

    private static Color parseHexColor(String hex, Color fallback) {
        if (hex == null || hex.isBlank()) return fallback;
        String clean = hex.trim().replace("#", "");
        if (clean.length() != 6) return fallback;
        try {
            return Color.fromRGB(Integer.parseInt(clean, 16));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
