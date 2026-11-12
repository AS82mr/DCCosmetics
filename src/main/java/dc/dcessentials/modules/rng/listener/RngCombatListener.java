package dc.dcessentials.modules.rng.listener;

import dc.dccosmetics.DCCosmetics;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class RngCombatListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            DCCosmetics.getInstance().getRngManager().getSecurity().tagDamage(event.getEntity(), player);
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player player) {
            DCCosmetics.getInstance().getRngManager().getSecurity().tagDamage(event.getEntity(), player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        DCCosmetics.getInstance().getRngManager().getSecurity().tagSpawn(event.getEntity(), event.getSpawnReason());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || victim instanceof Player) {
            DCCosmetics.getInstance().getRngManager().getSecurity().cleanupEntity(victim);
            return;
        }

        // Classify mob into category, general boost, and XP amount
        MobReward reward = classifyMob(victim);
        if (reward != null) {
            DCCosmetics.getInstance().getRngManager().processAction(
                    killer, reward.category, reward.generalBoostPct, reward.xp, "combat", victim
            );
        }

        // Independent Pity Action (Combat Mobs utility drops)
        DCCosmetics.getInstance().getPityManager().processAction(killer, "combat_mobs");

        DCCosmetics.getInstance().getRngManager().getSecurity().cleanupEntity(victim);
    }

    private static class MobReward {
        final String category;
        final double generalBoostPct;
        final int xp;

        MobReward(String category, double generalBoostPct, int xp) {
            this.category = category;
            this.generalBoostPct = generalBoostPct;
            this.xp = xp;
        }
    }

    private MobReward classifyMob(LivingEntity e) {
        String world = e.getWorld().getName().toLowerCase();
        String name = e.getCustomName() != null ? e.getCustomName() : "";

        // LowerCity Syndicate
        if (world.contains("lowercity")) {
            if (name.contains("Emperor")) {
                return new MobReward("lowercity", 800, 250);
            }
            return new MobReward("lowercity", 150, 15);
        }

        // Custom Survival / MythicMobs
        if (e.getScoreboardTags().contains("custom_survival_mob") || !name.isEmpty()) {
            return new MobReward("survival_custom", 100, 12);
        }

        // Apex Vanilla Bosses
        if (e instanceof Warden) return new MobReward("bosses", 500, 150);
        if (e instanceof Wither) return new MobReward("bosses", 450, 120);
        if (e instanceof EnderDragon) return new MobReward("bosses", 600, 200);
        if (e instanceof ElderGuardian) return new MobReward("bosses", 250, 60);

        // The End Mobs
        if (world.contains("end")) {
            return new MobReward("end", 15, 2);
        }

        // Nether Mobs
        if (world.contains("nether")) {
            if (e instanceof WitherSkeleton) return new MobReward("nether", 50, 5);
            if (e instanceof Blaze) return new MobReward("nether", 30, 4);
            if (e instanceof Ghast) return new MobReward("nether", 40, 5);
            if (e instanceof PiglinBrute) return new MobReward("nether", 45, 6);
            return new MobReward("nether", 15, 2);
        }

        // Overworld Standard
        if (e instanceof Monster) {
            if (e instanceof Creeper || e instanceof Skeleton || e instanceof Zombie) {
                return new MobReward("overworld", 0, 1);
            }
            return new MobReward("overworld", 10, 2);
        }

        return null;
    }
}
