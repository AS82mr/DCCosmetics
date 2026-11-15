package dc.dcessentials.modules.teleportation.listener;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.listener.StatCombatListener;
import dc.dcessentials.modules.teleportation.KeystoneDetector;
import dc.dcessentials.modules.teleportation.KeystoneProfile;
import dc.dcessentials.modules.teleportation.LegacyKeystoneConverter;
import dc.dcessentials.modules.teleportation.TeleportContext;
import dc.dcessentials.modules.teleportation.TeleportGate;
import dc.dcessentials.modules.teleportation.TeleportationManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportListener implements Listener {
    private final TeleportationManager manager;
    private final Map<UUID, RecentCommand> lastCommands = new ConcurrentHashMap<>();

    private record RecentCommand(String raw, String resolved, long timestampMs) {}

    public TeleportListener(TeleportationManager manager) {
        this.manager = manager;
    }

    /**
     * P1-01: Temporary debug instrumentation for teleport cause matrix capture.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleportDebug(PlayerTeleportEvent event) {
        if (!manager.isDebugTeleportCauses()) return;
        Player player = event.getPlayer();
        String fromWorld = event.getFrom().getWorld() != null ? event.getFrom().getWorld().getName() : "null";
        String toWorld = (event.getTo() != null && event.getTo().getWorld() != null) ? event.getTo().getWorld().getName() : "null";

        RecentCommand rc = lastCommands.get(player.getUniqueId());
        long ageMs = rc != null ? (System.currentTimeMillis() - rc.timestampMs()) : -1L;
        String cmdInfo = (rc != null && ageMs <= 2000L)
                ? (rc.raw() + " [resolved=" + rc.resolved() + ", " + ageMs + "ms ago]")
                : "none(<=2s)";

        String callerHint = resolveCallerPluginHint();
        DCCosmetics.getInstance().getLogger().info(String.format(
                "[Keystone-Debug] player=%s cause=%s worlds=%s->%s lastCmd=%s caller=%s cancelled=%s",
                player.getName(), event.getCause(), fromWorld, toWorld, cmdInfo, callerHint, event.isCancelled()
        ));
    }

    private String resolveCallerPluginHint() {
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        for (int i = 2; i < trace.length; i++) {
            String cls = trace[i].getClassName();
            if (cls.startsWith("org.bukkit.")
                    || cls.startsWith("io.papermc.")
                    || cls.startsWith("net.minecraft.")
                    || cls.startsWith("com.destroystokyo.")
                    || cls.startsWith("java.")
                    || cls.startsWith("jdk.")
                    || cls.equals(TeleportListener.class.getName())) {
                continue;
            }
            return cls + "#" + trace[i].getMethodName() + ":" + trace[i].getLineNumber();
        }
        return "native/server";
    }

    /**
     * P1-05: Pre-teleport Overload gate + Unstable (heat >= 80) safe arrival jitter.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleportPre(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        TeleportCause cause = event.getCause();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (!manager.getGate().preCheck(player, cause, from, to)) {
            event.setCancelled(true);
            return;
        }

        if (to == null) return;

        // Check if this is a charged warp subject to Unstable jitter
        TeleportContext ctx = manager.peekContext(player);
        if (ctx != null && ctx.type() == TeleportContext.Type.FREE_COMMAND) return;
        if (manager.getGate().isExemptOrSuppressedTransit(player, cause, from, to)) return;

        KeystoneProfile profile = manager.getProfile(player);
        if (profile.isLoaded()
                && profile.getHeat() >= manager.getInstabilityThreshold()
                && !KeystoneDetector.hasInstabilitySuppression(manager.getEquippedKeystoneItem(player))) {
            Location safeJitter = manager.getGate().findSafeJitterLocation(
                    to, manager.getInstabilityJitterMin(), manager.getInstabilityJitterMax());
            if (safeJitter != null && !safeJitter.equals(to)) {
                event.setTo(safeJitter);
                if (safeJitter.getWorld() != null) {
                    safeJitter.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, safeJitter.clone().add(0, 1, 0), 16, 0.4, 0.5, 0.4, 0.05);
                }
                DCMessage.sendWarning(player, "Temporal Instability (&#EF4444" + profile.getHeat()
                        + "/100 🔥&#FDE047) caused spatial drift on arrival!");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        manager.handleTeleport(player, event.getFrom(), event.getTo(), event.getCause());
    }

    /**
     * P1-06: Capture raw command before CMI/Multiverse alias rewrites (LOWEST) and after (MONITOR).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocessEarly(PlayerCommandPreprocessEvent event) {
        captureCommandContext(event.getPlayer(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        captureCommandContext(event.getPlayer(), event.getMessage());
    }

    private void captureCommandContext(Player player, String rawInput) {
        if (player == null || rawInput == null) return;
        String rawMsg = rawInput.trim();
        if (rawMsg.isEmpty() || !rawMsg.startsWith("/")) return;

        String body = rawMsg.substring(1).trim();
        if (body.isEmpty()) return;

        String[] parts = body.split("\\s+");
        String token = stripNamespace(parts[0].toLowerCase(Locale.ROOT));
        String subToken = parts.length > 1 ? stripNamespace(parts[1].toLowerCase(Locale.ROOT)) : "";

        // Unwrap plugin command wrappers (/cmi home, /cmi warp, /mw tp, /mv tp, /essentials warp)
        String effectiveToken = token;
        if ((token.equals("cmi") || token.equals("mw") || token.equals("mv")
                || token.equals("multiverse") || token.equals("essentials") || token.equals("ess"))
                && !subToken.isEmpty()) {
            effectiveToken = subToken;
        }

        String resolved = effectiveToken;
        try {
            Command cmd = Bukkit.getCommandMap().getCommand(effectiveToken);
            if (cmd != null && cmd.getName() != null) {
                String cmdName = stripNamespace(cmd.getName().toLowerCase(Locale.ROOT));
                if (!cmdName.equals("cmi") && !cmdName.equals("mw") && !cmdName.equals("mv")) {
                    resolved = cmdName;
                }
            }
        } catch (Throwable ignored) {}

        lastCommands.put(player.getUniqueId(), new RecentCommand(rawMsg, resolved, System.currentTimeMillis()));

        if (manager.getFreeCommands().contains(effectiveToken)
                || manager.getFreeCommands().contains(resolved)
                || manager.getFreeCommands().contains(token)) {
            manager.armContext(player, TeleportContext.Type.FREE_COMMAND, effectiveToken);
        } else if (manager.getChargedCommands().contains(effectiveToken)
                || manager.getChargedCommands().contains(resolved)
                || manager.getChargedCommands().contains(token)) {
            if (effectiveToken.equals("tpaccept") || effectiveToken.equals("tpyes")
                    || resolved.equals("tpaccept") || resolved.equals("tpyes")) {
                manager.armPendingTpaAccept(player);
            }
            manager.armContext(player, TeleportContext.Type.CHARGED_COMMAND, effectiveToken);
        }
    }

    private static String stripNamespace(String cmd) {
        int colonIdx = cmd.indexOf(':');
        if (colonIdx >= 0 && colonIdx + 1 < cmd.length()) {
            return cmd.substring(colonIdx + 1);
        }
        return cmd;
    }

    /**
     * P1-05: Unstable (heat >= 80) +15% incoming damage from Emperor/boss entities, SONIC_BOOM, and VOID.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUnstableDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        KeystoneProfile profile = manager.getProfile(player);
        if (!profile.isLoaded() || profile.getHeat() < manager.getInstabilityThreshold()) return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean applies = (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.SONIC_BOOM);
        if (!applies && event instanceof EntityDamageByEntityEvent edbe) {
            applies = StatCombatListener.isBossOrEmperor(edbe.getDamager());
        }

        if (applies) {
            double mult = 1.0 + (manager.getInstabilityIncomingDamagePct() / 100.0);
            event.setDamage(event.getDamage() * mult);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // P1-04: Convert legacy EI keystones/cubes in inventory, ender chest, and PlayerVaults
        LegacyKeystoneConverter.convertPlayerInventories(player);
        Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
            if (player.isOnline()) {
                LegacyKeystoneConverter.convertPlayerVaults(player);
            }
        }, 20L);

        // First join: grant 50 Tesseract Energy & Newbie Shield after DB load callback
        if (!player.hasPlayedBefore()) {
            long shieldMs = manager.getNewbieShieldDays() * 24L * 3600L * 1000L;
            KeystoneProfile profile = manager.getProfile(player);
            profile.setEnergy(50);
            profile.setNewbieShieldUntil(System.currentTimeMillis() + shieldMs);
            profile.setLoaded(true);
            manager.getPersistence().saveNow(profile);

            DCMessage.sendGeneral(player, "Welcome to Dragoncote, Operative!");
            DCMessage.sendInfo(player, "Corvus has shielded your timeline for &#FFFFFF" + manager.getNewbieShieldDays()
                    + " Days &#BAE6FDand loaded you with &#FFFFFF50 Tesseract Energy 🗲&#BAE6FD.");
            DCMessage.sendInfo(player, "Speak with &#FFFFFFCorvus &#BAE6FDat Sanctuary Spawn or open &#FFFFFF/lore &#BAE6FDto begin your mission!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        }
    }

    /**
     * P1-02: Snapshot, FIFO async save, and evict on quit.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastCommands.remove(uuid);
        manager.getPersistence().onPlayerQuit(uuid);
    }
}
