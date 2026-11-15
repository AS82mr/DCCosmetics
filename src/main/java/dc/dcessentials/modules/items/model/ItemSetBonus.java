package dc.dcessentials.modules.items.model;

import java.util.List;

/**
 * Set bonus unlocked when equipping at least {@code requiredPieces} pieces sharing {@code setId}.
 */
public record ItemSetBonus(
        String setId,
        String setName,
        int requiredPieces,
        List<ItemPerk> perks
) {}
