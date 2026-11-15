package dc.dcessentials.modules.curator;

import java.util.Collections;
import java.util.List;

/**
 * TEMPORARY MODEL CURATOR MODULE ENTRY.
 * Represents one ModelEngine blueprint or ItemsAdder furniture/item discovered on disk.
 */
public final class CuratorEntry {

    public enum SourceType {
        MEG("MEG Blueprint"),
        IA_FURNITURE("IA Furniture"),
        IA_ITEM("IA Item");

        private final String label;

        SourceType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum FilterMode {
        ALL("All Models"),
        FAVORITES("Favorites"),
        CLEANUP("Cleanup / Remove");

        private final String label;

        FilterMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum SourceFilter {
        ALL("All Sources"),
        MEG("MEG Only"),
        IA_FURNITURE("IA Furniture"),
        IA_ITEM("IA Items");

        private final String label;

        SourceFilter(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public boolean matches(SourceType type) {
            return switch (this) {
                case ALL -> true;
                case MEG -> type == SourceType.MEG;
                case IA_FURNITURE -> type == SourceType.IA_FURNITURE;
                case IA_ITEM -> type == SourceType.IA_ITEM;
            };
        }

        public SourceFilter next() {
            SourceFilter[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    private final String id;            // Canonical key (e.g. "scene_farmer" or "nightclub_furniture_v2:bar_chair")
    private final String rawName;       // Model/item name
    private final String packName;      // Subfolder / IA namespace or pack name
    private final SourceType sourceType;
    private final String configPath;    // Relative path from plugins/ (e.g. ModelEngine/blueprints/...)
    private final String materialName;  // For IA items
    private final int customModelData;  // For IA items
    private final List<String> assetPaths; // Related .bbmodel / model / texture files
    private final List<String> animations; // Animation names (for MEG blueprints)

    public CuratorEntry(
            String id,
            String rawName,
            String packName,
            SourceType sourceType,
            String configPath,
            String materialName,
            int customModelData,
            List<String> assetPaths,
            List<String> animations
    ) {
        this.id = id;
        this.rawName = rawName;
        this.packName = packName;
        this.sourceType = sourceType;
        this.configPath = configPath;
        this.materialName = materialName;
        this.customModelData = customModelData;
        this.assetPaths = assetPaths == null ? Collections.emptyList() : Collections.unmodifiableList(assetPaths);
        this.animations = animations == null ? Collections.emptyList() : Collections.unmodifiableList(animations);
    }

    public String getId() { return id; }
    public String getRawName() { return rawName; }
    public String getPackName() { return packName; }
    public SourceType getSourceType() { return sourceType; }
    public String getConfigPath() { return configPath; }
    public String getMaterialName() { return materialName; }
    public int getCustomModelData() { return customModelData; }
    public List<String> getAssetPaths() { return assetPaths; }
    public List<String> getAnimations() { return animations; }
}
