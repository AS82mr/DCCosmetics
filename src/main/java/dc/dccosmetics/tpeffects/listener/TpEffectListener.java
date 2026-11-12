package dc.dccosmetics.tpeffects.listener;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.tpeffects.TpEffectManager;
import dc.dccosmetics.tpeffects.config.TpEffectConfig;
import dc.dccosmetics.tpeffects.model.TpEffectEntry;
import dc.dcessentials.core.util.DCMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Handles automatic playback of equipped teleportation effects on PlayerTeleportEvent,
 * multi-layer fallback suppression for combat/item abilities & boss skills,
 * skin pre-fetching & permission verification on join, and fail-safe sequence cleanup.
 */
public final class TpEffectListener implements Listener {

    private static final StackWalker STACK_WALKER = StackWalker.getInstance();

    private final DCCosmetics plugin;
    private final TpEffectManager manager;
    private final Map<UUID, Long> lastCombatActivityMs = new ConcurrentHashMap<>();

    public TpEffectListener(DCCosmetics plugin, TpEffectManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!manager.isGloballyEnabled()) return;

        Player player = event.getPlayer();
        if (player.hasMetadata("in_wardrobe") || player.hasMetadata("no_tpeffect")) return;
        if (manager.getCinematicEngine().isBusy(player)) return;
        if (manager.isMuted(player)) return;
        if (manager.isTemporarilySuppressed(player)) return;

        TpEffectConfig cfg = manager.getConfig();
        TeleportCause cause = event.getCause();
        if (cfg.isCauseIgnored(cause)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() == null || to.getWorld() == null) return;

        if (cfg.isWorldDisabled(from.getWorld().getName()) || cfg.isWorldDisabled(to.getWorld().getName())) {
            return;
        }

        TpEffectEntry entry = manager.getSelectedEffect(player);
        if (entry == null) return;

        boolean sameWorld = from.getWorld().equals(to.getWorld());
        double distSq = sameWorld ? from.distanceSquared(to) : -1.0;
        if (sameWorld) {
            double minDist = (cause == TeleportCause.COMMAND)
                    ? cfg.getMinDistanceCommand()
                    : cfg.getMinDistanceNonCommand();
            if (distSq < (minDist * minDist)) {
                return;
            }
        }

        String suppressionReason = detectAbilitySuppressionReason(player, cause, sameWorld, distSq, cfg);
        if (suppressionReason != null) {
            if (cfg.isAbilityFallbackDebug()) {
                plugin.getLogger().info("[TpEffects][AbilityFallback] Suppressed teleport for " + player.getName()
                        + " (cause=" + cause + ", dist=" + formatDistance(sameWorld, distSq)
                        + ", reason=" + suppressionReason + ")");
            }
            return;
        }

        if (cfg.isAbilityFallbackDebug() && (cause == TeleportCause.PLUGIN || cause == TeleportCause.UNKNOWN)) {
            plugin.getLogger().info("[TpEffects][AbilityFallback] Allowing teleport for " + player.getName()
                    + " (cause=" + cause + ", dist=" + formatDistance(sameWorld, distSq)
                    + ", callers=" + summarizeCallers() + ")");
        }

        if (manager.isOnCooldown(player)) return;
        manager.markCooldown(player);

        if (entry.isCinematic()) {
            // Portal blocks re-trigger every tick if departure stays in frame, so run pure arrival plunge after transit
            boolean isPortalCause = cause == TeleportCause.NETHER_PORTAL
                    || cause == TeleportCause.END_PORTAL
                    || cause == TeleportCause.END_GATEWAY;

            if (isPortalCause) {
                final Location arrCopy = to.clone();
                manager.getCinematicEngine().markPending(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        manager.getCinematicEngine().playArrivalSequence(player, arrCopy, entry);
                    } else {
                        manager.getCinematicEngine().clearPending(player.getUniqueId());
                    }
                });
            } else {
                event.setCancelled(true);
                final Location fromCopy = from.clone();
                final Location toCopy = to.clone();
                manager.getCinematicEngine().markPending(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        manager.getCinematicEngine().playTeleportSequence(player, fromCopy, toCopy, entry);
                    } else {
                        manager.getCinematicEngine().clearPending(player.getUniqueId());
                    }
                });
            }
        } else {
            manager.playStandardMythicEffect(from, to, entry);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        manager.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        manager.getCinematicEngine().cancelSequence(uuid, false);
        manager.getCinematicEngine().stopPreview(player);
        manager.clearCooldown(uuid);
        manager.clearTemporarySuppression(uuid);
        lastCombatActivityMs.remove(uuid);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        manager.getCinematicEngine().cancelSequence(player.getUniqueId(), false);
        manager.getCinematicEngine().stopPreview(player);
        lastCombatActivityMs.remove(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        long now = System.currentTimeMillis();
        if (event.getEntity() instanceof Player player) {
            if (manager.getCinematicEngine().isInSequence(player)) {
                event.setCancelled(true);
                return;
            }
            if (isCombatOrAbilityDamage(event)) {
                lastCombatActivityMs.put(player.getUniqueId(), now);
            }
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = resolvePlayerAttacker(byEntity);
            if (attacker != null) {
                lastCombatActivityMs.put(attacker.getUniqueId(), now);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!manager.getCinematicEngine().isInSequence(player)) return;
        String lower = event.getMessage().toLowerCase();
        if (!lower.startsWith("/tpeffect") && !lower.startsWith("/cinetp")) {
            event.setCancelled(true);
            DCMessage.sendWarning(player, "Please wait for your teleportation cinematic to finish.");
        }
    }

    private String detectAbilitySuppressionReason(
            Player player,
            TeleportCause cause,
            boolean sameWorld,
            double distSq,
            TpEffectConfig cfg
    ) {
        if (!cfg.isAbilityFallbackEnabled()) return null;

        for (String key : cfg.getIgnoredMetadataKeys()) {
            if (player.hasMetadata(key)) {
                return "metadata:" + key;
            }
        }

        Set<String> ignoredTags = cfg.getIgnoredScoreboardTags();
        if (!ignoredTags.isEmpty()) {
            for (String tag : player.getScoreboardTags()) {
                if (ignoredTags.contains(tag.toLowerCase(Locale.ROOT))) {
                    return "tag:" + tag;
                }
            }
        }

        if (cause != TeleportCause.COMMAND && !cfg.getIgnoredCallerPatterns().isEmpty()) {
            String matchedCaller = findIgnoredCallerFrame(cfg.getIgnoredCallerPatterns());
            if (matchedCaller != null) {
                return "caller:" + matchedCaller;
            }
        }

        if (cfg.isAbilityFallbackSuppressCombat()
                && sameWorld
                && (cause == TeleportCause.PLUGIN || cause == TeleportCause.UNKNOWN)) {
            double maxDist = cfg.getAbilityFallbackCombatMaxDistance();
            if (maxDist <= 0.0 || distSq <= (maxDist * maxDist)) {
                Long lastCombat = lastCombatActivityMs.get(player.getUniqueId());
                if (lastCombat != null) {
                    long windowMs = (long) (cfg.getAbilityFallbackCombatWindowSeconds() * 1000.0);
                    if ((System.currentTimeMillis() - lastCombat) <= windowMs) {
                        return "combat-window";
                    }
                }
            }
        }

        return null;
    }

    private String findIgnoredCallerFrame(List<String> patterns) {
        return STACK_WALKER.walk(stream -> stream
                .limit(48)
                .map(f -> (f.getClassName() + "." + f.getMethodName()).toLowerCase(Locale.ROOT))
                .filter(sig -> !sig.startsWith("dc.dccosmetics.tpeffects.listener.tpeffectlistener."))
                .filter(sig -> matchesAnyPattern(sig, patterns))
                .findFirst()
                .orElse(null));
    }

    private static boolean matchesAnyPattern(String signature, List<String> patterns) {
        for (String pat : patterns) {
            if (signature.contains(pat)) return true;
        }
        return false;
    }

    private static String summarizeCallers() {
        return STACK_WALKER.walk(stream -> stream
                .limit(20)
                .map(f -> f.getClassName() + "#" + f.getMethodName())
                .filter(s -> !s.startsWith("dc.dccosmetics.tpeffects.listener.TpEffectListener#"))
                .filter(s -> !s.startsWith("java.") && !s.startsWith("jdk.") && !s.startsWith("org.bukkit.plugin."))
                .limit(6)
                .collect(Collectors.joining(" <- ")));
    }

    private static String formatDistance(boolean sameWorld, double distSq) {
        return sameWorld ? String.format(Locale.ROOT, "%.1f", Math.sqrt(Math.max(0.0, distSq))) : "cross-world";
    }

    private static boolean isCombatOrAbilityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return true;
        DamageCause c = event.getCause();
        return c == DamageCause.CUSTOM
                || c == DamageCause.MAGIC
                || c == DamageCause.SONIC_BOOM
                || c == DamageCause.DRAGON_BREATH
                || c == DamageCause.ENTITY_EXPLOSION
                || c == DamageCause.BLOCK_EXPLOSION
                || c == DamageCause.WITHER;
    }

    private static Player resolvePlayerAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player direct) return direct;
        if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
