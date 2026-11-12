package dc.dcessentials.modules.teleportation;

/**
 * Immutable result of a Keystone microcharge expenditure (Section 5.4).
 */
public record SpendResult(
        boolean authorized,
        int effectiveCostMicro,
        int chargesCracked,
        int microPaid,
        int microShortfall,
        boolean overdraw,
        int heatAdded,
        int remainingEnergy,
        int remainingMicro,
        int resultingHeat,
        boolean overloaded
) {}
