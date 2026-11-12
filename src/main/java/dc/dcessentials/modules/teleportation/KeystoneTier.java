package dc.dcessentials.modules.teleportation;

public enum KeystoneTier {
    NONE("None", null, "&7None", "&7Unattuned", 0, 0, 0, 1, 12, "&c0% (Unattuned)"),
    RARE("Rare", "tesseract", "&#0098FF&lRare", "&b&lTesseract", 200, 1, 0, 1, 4, "&aStandard (0% Cost | 1x Cool)"),
    EPIC("Epic", "chronolith", "&#8A00FF&lEpic", "&5&lChronolith", 400, 2, 15, 2, 2, "&d-15% Cost | 2x Cool"),
    LEGENDARY("Legendary", "singularity", "&#FFD700&lLegendary", "&6&lSingularity", 600, 3, 30, 4, 1, "&6-30% Cost | 4x Cool");

    private final String id;
    private final String itemId;
    private final String displayName;
    private final String keystoneName;
    private final int maxEnergy;
    private final int sockets;
    private final int costReductionPct;
    private final int passiveDecay;
    private final int baseHeat;
    private final String efficiencyText;

    KeystoneTier(String id, String itemId, String displayName, String keystoneName,
                 int maxEnergy, int sockets, int costReductionPct, int passiveDecay,
                 int baseHeat, String efficiencyText) {
        this.id = id;
        this.itemId = itemId;
        this.displayName = displayName;
        this.keystoneName = keystoneName;
        this.maxEnergy = maxEnergy;
        this.sockets = sockets;
        this.costReductionPct = costReductionPct;
        this.passiveDecay = passiveDecay;
        this.baseHeat = baseHeat;
        this.efficiencyText = efficiencyText;
    }

    public String getId() { return id; }
    public String getItemId() { return itemId; }
    public String getDisplayName() { return displayName; }
    public String getKeystoneName() { return keystoneName; }
    public int getMaxEnergy() { return maxEnergy; }
    public int getSockets() { return sockets; }
    public int getCostReductionPct() { return costReductionPct; }
    public int getPassiveDecay() { return passiveDecay; }
    public int getBaseHeat() { return baseHeat; }
    public String getEfficiencyText() { return efficiencyText; }

    @Deprecated
    public double getInsulation() { return costReductionPct / 100.0; }

    public static KeystoneTier fromString(String str) {
        if (str == null || str.isEmpty()) return NONE;
        for (KeystoneTier t : values()) {
            if (t.name().equalsIgnoreCase(str)
                    || t.id.equalsIgnoreCase(str)
                    || (t.itemId != null && t.itemId.equalsIgnoreCase(str))) {
                return t;
            }
        }
        return NONE;
    }
}
