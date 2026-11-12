package dc.dcessentials.modules.teleportation;

/**
 * Ephemeral command-to-teleport context token (Task P1-06).
 * Armed at PlayerCommandPreprocessEvent MONITOR when not cancelled,
 * consumed by the first matching PlayerTeleportEvent or expired after ttlTicks.
 */
public record TeleportContext(
        Type type,
        String commandLabel,
        long createdTick,
        int ttlTicks,
        long createdMs
) {
    public static final int DEFAULT_TTL_TICKS = 200; // 10 seconds

    public enum Type {
        FREE_COMMAND,
        CHARGED_COMMAND,
        TPA_ACCEPT
    }

    public boolean isExpired(long currentTick) {
        return (currentTick - createdTick) > ttlTicks;
    }
}
