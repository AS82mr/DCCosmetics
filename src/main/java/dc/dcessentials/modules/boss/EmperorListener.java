package dc.dcessentials.modules.boss;

import dc.dccosmetics.DCCosmetics;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicHealMechanicEvent;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicMobDespawnEvent;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import io.lumine.mythic.bukkit.events.MythicProjectileHitEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Event Listener for Emperor Boss Combat, Dynamic Scaling, Live Heal Displays & MythicMobs Lifecycle.
 */
public class EmperorListener implements Listener {

    private final DCCosmetics plugin;
    private final EmperorBossManager manager;
    private final EmperorTabHook tabHook;

    private volatile long lastMythicHealEventMillis = 0L;

    public EmperorListener(DCCosmetics plugin, EmperorBossManager manager, EmperorTabHook tabHook) {
        this.plugin = plugin;
        this.manager = manager;
        this.tabHook = tabHook;
    }

    // --- Dynamic Damage Scaling (Effective HP & Attack Multipliers) ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!manager.isActive() && !manager.isManualOverride()) return;

        double scale = manager.getAngerScale();
        if (scale <= 1.0) return;

        Entity victim = event.getEntity();
        Entity attacker = resolveAttacker(event.getDamager());

        // 1. Player (or player projectile) attacks Emperor -> Mitigate incoming damage by Anger Scale
        if (manager.isEmperorEntity(victim)) {
            event.setDamage(event.getDamage() / scale);
            return;
        }

        // 2. Emperor attacks Player -> Boost outgoing damage based on Anger Scale
        if (manager.isEmperorEntity(attacker) && victim instanceof Player) {
            double boost = 1.0 + ((scale - 1.0) * 0.35);
            event.setDamage(event.getDamage() * boost);
        }
    }

    private Entity resolveAttacker(Entity damager) {
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    // --- Native Live Healing Popups (+X ❤) ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicHeal(MythicHealMechanicEvent event) {
        Entity target = event.getTarget();
        if (!manager.isEmperorEntity(target)) return;

        lastMythicHealEventMillis = System.currentTimeMillis();
        int healAmount = (int) Math.max(1, Math.round(event.getFinalAmount()));
        manager.spawnHealText(target, target.getLocation(), healAmount);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicProjectileHit(MythicProjectileHitEvent event) {
        if (event.getEntity() == null) return;
        Entity hitEntity = event.getEntity().getBukkitEntity();
        if (!manager.isEmperorEntity(hitEntity)) return;

        if (!isVoidHolderCaster(event)) return;

        // If Emperor was at max HP (999) or capped, MythicHealMechanicEvent won't fire; still render the heal popup cleanly after 1 tick.
        long hitTime = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (lastMythicHealEventMillis >= hitTime) return;
            if (hitEntity.isValid() && !hitEntity.isDead()) {
                manager.spawnHealText(hitEntity, hitEntity.getLocation(), 12);
            }
        }, 2L);
    }

    private boolean isVoidHolderCaster(MythicProjectileHitEvent event) {
        try {
            if (event.getProjectile() == null || event.getProjectile().getData() == null) return false;
            var caster = event.getProjectile().getData().getCaster();
            if (caster == null || caster.getEntity() == null) return false;

            Entity bukkitCaster = caster.getEntity().getBukkitEntity();
            if (bukkitCaster == null) return false;

            ActiveMob am = MythicBukkit.inst().getMobManager().getMythicMobInstance(bukkitCaster);
            if (am != null && am.getType() != null && "CRIMSON_MINION".equalsIgnoreCase(am.getType().getInternalName())) {
                return true;
            }
            if (bukkitCaster.customName() != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(bukkitCaster.customName());
                return plain.contains("Void Holder");
            }
        } catch (Throwable ignored) {}
        return false;
    }

    // --- MythicMobs Boss Lifecycle Hooks ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicMobSpawn(MythicMobSpawnEvent event) {
        if (event.getMobType() != null && "helly".equalsIgnoreCase(event.getMobType().getInternalName())) {
            plugin.getLogger().info("[EmperorBoss] Detected The Emperor (helly) spawn in " + event.getLocation().getWorld().getName());
            manager.onEmperorSpawn(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (event.getMobType() != null && "helly".equalsIgnoreCase(event.getMobType().getInternalName())) {
            plugin.getLogger().info("[EmperorBoss] The Emperor has fallen!");
            manager.onEmperorDeath(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicMobDespawn(MythicMobDespawnEvent event) {
        if (event.getMobType() != null && "helly".equalsIgnoreCase(event.getMobType().getInternalName())) {
            plugin.getLogger().info("[EmperorBoss] The Emperor despawned.");
            manager.onEmperorDeath(event.getEntity());
        }
    }

    // --- Player World & Connection State ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        manager.updateBossBarViewers();
        if (tabHook != null) {
            tabHook.pushRefresh(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.getEventManager() != null) {
            plugin.getEventManager().clearPersonalEvent(event.getPlayer().getUniqueId(), null);
        }
        manager.updateBossBarViewers();
    }
}
