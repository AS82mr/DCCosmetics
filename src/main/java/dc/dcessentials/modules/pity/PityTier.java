package dc.dcessentials.modules.pity;

import org.bukkit.Material;

public class PityTier {
    private final String categoryId;
    private final int tierNumber;
    private final String displayName;
    private final Material icon;
    private final int reqXp;
    private final String command;

    public PityTier(String categoryId, int tierNumber, String displayName, Material icon, int reqXp, String command) {
        this.categoryId = categoryId;
        this.tierNumber = tierNumber;
        this.displayName = displayName;
        this.icon = icon != null ? icon : Material.GOLD_INGOT;
        this.reqXp = Math.max(1, reqXp);
        this.command = command != null ? command : "";
    }

    public String getCategoryId() { return categoryId; }
    public int getTierNumber() { return tierNumber; }
    public String getDisplayName() { return displayName; }
    public Material getIcon() { return icon; }
    public int getReqXp() { return reqXp; }
    public String getCommand() { return command; }
}
