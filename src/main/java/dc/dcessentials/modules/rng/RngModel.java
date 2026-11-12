package dc.dcessentials.modules.rng;

import org.bukkit.Material;

public class RngModel {

    public static class Category {
        private final String id;
        private final String name;
        private final Material icon;
        private final String hexColor;
        private final String description;

        public Category(String id, String name, Material icon, String hexColor, String description) {
            this.id = id;
            this.name = name;
            this.icon = icon != null ? icon : Material.CHEST;
            this.hexColor = hexColor != null ? hexColor : "#38BDF8";
            this.description = description != null ? description : "";
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public Material getIcon() { return icon; }
        public String getHexColor() { return hexColor; }
        public String getDescription() { return description; }
    }

    public static class Drop {
        private final String categoryId;
        private final String dropId;
        private final String displayName;
        private final Material icon;
        private final double baseChancePct;
        private final int goalXp;
        private final String command;

        public Drop(String categoryId, String dropId, String displayName, Material icon, double baseChancePct, int goalXp, String command) {
            this.categoryId = categoryId;
            this.dropId = dropId;
            this.displayName = displayName;
            this.icon = icon != null ? icon : Material.DIAMOND;
            this.baseChancePct = baseChancePct;
            this.goalXp = Math.max(1, goalXp);
            this.command = command != null ? command : "";
        }

        public String getCategoryId() { return categoryId; }
        public String getDropId() { return dropId; }
        public String getDisplayName() { return displayName; }
        public Material getIcon() { return icon; }
        public double getBaseChancePct() { return baseChancePct; }
        public int getGoalXp() { return goalXp; }
        public String getCommand() { return command; }
    }
}
