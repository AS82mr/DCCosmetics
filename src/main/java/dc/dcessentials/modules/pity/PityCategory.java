package dc.dcessentials.modules.pity;

import org.bukkit.Material;

public class PityCategory {
    private final String id;
    private final String name;
    private final Material icon;
    private final int maxThreshold;
    private final String dropName;
    private final String command;
    private final int energyBonus;

    public PityCategory(String id, String name, Material icon, int maxThreshold, String dropName, String command, int energyBonus) {
        this.id = id.toLowerCase();
        this.name = name;
        this.icon = icon != null ? icon : Material.NETHER_STAR;
        this.maxThreshold = Math.max(1, maxThreshold);
        this.dropName = dropName;
        this.command = command != null ? command : "";
        this.energyBonus = energyBonus;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Material getIcon() { return icon; }
    public int getMaxThreshold() { return maxThreshold; }
    public String getDropName() { return dropName; }
    public String getCommand() { return command; }
    public int getEnergyBonus() { return energyBonus; }
}
