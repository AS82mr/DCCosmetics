package dc.dcessentials.modules.items.model;

import dc.dcessentials.modules.pets.model.PetPerkEffectType;

/**
 * Passive or triggered perk attached to an Accessory, Armor piece, or Full-Set Bonus.
 */
public record ItemPerk(
        String name,
        PetPerkEffectType effectType,
        double value,
        String description
) {}
