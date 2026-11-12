package dc.dccosmetics.wardrobe.teleport;

import dc.dccosmetics.tpeffects.model.TpEffectEntry;

import java.util.Collections;
import java.util.List;

/**
 * Wardrobe adapter model wrapping a configured TpEffectEntry from plugins/DCEssentials/tpeffects/*.yml.
 */
public class WardrobeTeleportEffect {

    private final String id;
    private final String displayName;
    private final String rawDisplayName;
    private final String rarity;
    private final String mythicMob;
    private final String permission;
    private final boolean cinematic;
    private final List<String> lore;
    private final TpEffectEntry entry;

    public WardrobeTeleportEffect(String id, String displayName, String rarity, String mythicMob) {
        this.id = id;
        this.displayName = displayName;
        this.rawDisplayName = displayName;
        this.rarity = rarity;
        this.mythicMob = mythicMob;
        this.permission = "dc.tpeffect." + id;
        this.cinematic = mythicMob != null && mythicMob.startsWith("CINE:");
        this.lore = Collections.emptyList();
        this.entry = null;
    }

    private WardrobeTeleportEffect(TpEffectEntry entry) {
        this.entry = entry;
        this.id = entry.getId();
        this.displayName = entry.getStrippedDisplayName();
        this.rawDisplayName = entry.getRawDisplayName();
        this.rarity = entry.getRarity();
        this.mythicMob = entry.getLegacyEffectKey();
        this.permission = entry.getPermission();
        this.cinematic = entry.isCinematic();
        this.lore = entry.getRawLore();
    }

    public static WardrobeTeleportEffect fromEntry(TpEffectEntry entry) {
        return entry != null ? new WardrobeTeleportEffect(entry) : null;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getRawDisplayName() { return rawDisplayName; }
    public String getRarity() { return rarity; }
    public String getMythicMob() { return mythicMob; }
    public String getPermission() { return permission; }
    public boolean isCinematic() { return cinematic; }
    public List<String> getLore() { return lore; }
    public TpEffectEntry getEntry() { return entry; }
}
