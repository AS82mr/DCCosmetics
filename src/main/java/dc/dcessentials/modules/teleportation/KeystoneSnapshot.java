package dc.dcessentials.modules.teleportation;

import java.util.UUID;

/**
 * Immutable snapshot passed between the DB worker thread and the Bukkit main thread.
 */
public record KeystoneSnapshot(
        UUID uuid,
        KeystoneTier tier,
        int energy,
        int micro,
        int heat,
        boolean overloaded,
        long shieldUntil,
        long updatedAt
) {}
