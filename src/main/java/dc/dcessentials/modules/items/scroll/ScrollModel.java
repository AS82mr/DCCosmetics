package dc.dcessentials.modules.items.scroll;

import org.bukkit.Material;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScrollModel {
    private final String id;
    private final String name;
    private final String rarity;
    private final ScrollType type;
    private final String targetCategory;
    private final Material icon;
    private final Integer customModelData;
    private final List<String> lore;
    private final String socketDisplay;
    private final String brief;
    private final Map<String, Object> mechanics;

    public ScrollModel(String id, String name, String rarity, ScrollType type, String targetCategory,
                       Material icon, Integer customModelData, List<String> lore,
                       String socketDisplay, Map<String, Object> mechanics) {
        this(id, name, rarity, type, targetCategory, icon, customModelData, lore, socketDisplay, null, mechanics);
    }

    public ScrollModel(String id, String name, String rarity, ScrollType type, String targetCategory,
                       Material icon, Integer customModelData, List<String> lore,
                       String socketDisplay, String brief, Map<String, Object> mechanics) {
        this.id = id;
        this.name = name;
        this.rarity = rarity;
        this.type = type;
        this.targetCategory = targetCategory;
        this.icon = icon != null ? icon : Material.PAPER;
        this.customModelData = customModelData;
        this.lore = lore != null ? lore : new ArrayList<>();
        this.socketDisplay = socketDisplay;
        this.brief = brief;
        this.mechanics = mechanics != null ? mechanics : new HashMap<>();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getRarity() { return rarity; }
    public ScrollType getType() { return type; }
    public String getTargetCategory() { return targetCategory; }
    public Material getIcon() { return icon; }
    public Integer getCustomModelData() { return customModelData; }
    public List<String> getLore() { return lore; }
    public String getSocketDisplay() { return socketDisplay; }
    public String getBrief() { return brief; }
    public Map<String, Object> getMechanics() { return mechanics; }

    public String getMechanicType() {
        return getMechanic("type", "");
    }

    @SuppressWarnings("unchecked")
    public <T> T getMechanic(String key, T defaultValue) {
        Object val = mechanics.get(key);
        if (val == null) return defaultValue;
        try {
            return (T) val;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
}
