package dc.dcessentials.modules.items.model;

import dc.dcessentials.modules.items.scroll.ScrollType;
import org.bukkit.Material;

import java.util.*;

public class DCEItem {
    private final String id;
    private int version;
    private Material material;
    private String displayName;
    private ItemRarity rarity;
    private ItemCategory category;
    private final Map<ItemStat, Double> stats = new EnumMap<>(ItemStat.class);
    private final Set<ScrollType> allowedSockets = new HashSet<>();
    private final Map<ScrollType, Integer> socketCapacity = new EnumMap<>(ScrollType.class);
    private String equipmentSlot;
    private Integer customModelData;
    private final List<String> customLore = new ArrayList<>();
    private int stars;
    private String executableItemId;
    private String headTexture;
    private org.bukkit.inventory.ItemStack template;
    private final List<ItemPerk> perks = new ArrayList<>();
    private String setId;
    private ItemSetBonus setBonus;

    public DCEItem(String id, int version, Material material, String displayName, ItemRarity rarity, ItemCategory category) {
        this.id = id;
        this.version = version;
        this.material = material != null ? material : Material.STONE;
        this.displayName = displayName;
        this.rarity = rarity != null ? rarity : ItemRarity.COMMON;
        this.category = category != null ? category : ItemCategory.INGREDIENT;
    }

    public String getId() { return id; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public void bumpVersion() { this.version++; }

    public Material getMaterial() { return material; }
    public void setMaterial(Material material) { this.material = material; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public ItemRarity getRarity() { return rarity; }
    public void setRarity(ItemRarity rarity) { this.rarity = rarity; }

    public ItemCategory getCategory() { return category; }
    public void setCategory(ItemCategory category) { this.category = category; }

    public Map<ItemStat, Double> getStats() { return stats; }
    public double getStat(ItemStat stat) { return stats.getOrDefault(stat, 0.0); }
    public void setStat(ItemStat stat, double value) {
        if (value <= 0.0) {
            stats.remove(stat);
        } else {
            stats.put(stat, value);
        }
    }

    public Set<ScrollType> getAllowedSockets() { return allowedSockets; }
    public boolean allowsSocket(ScrollType type) { return allowedSockets.contains(type); }
    public void addAllowedSocket(ScrollType type) {
        if (type == null) return;
        allowedSockets.add(type);
        socketCapacity.putIfAbsent(type, 1);
    }
    public void removeAllowedSocket(ScrollType type) {
        if (type == null) return;
        allowedSockets.remove(type);
        socketCapacity.remove(type);
    }

    public Map<ScrollType, Integer> getSocketCapacity() { return socketCapacity; }
    public int getSocketCapacity(ScrollType type) {
        if (type == null || !allowedSockets.contains(type)) return 0;
        return Math.max(1, socketCapacity.getOrDefault(type, 1));
    }
    public void setSocketCapacity(ScrollType type, int capacity) {
        if (type == null) return;
        if (capacity <= 0) {
            removeAllowedSocket(type);
        } else {
            allowedSockets.add(type);
            socketCapacity.put(type, capacity);
        }
    }

    public String getEquipmentSlot() { return equipmentSlot; }
    public void setEquipmentSlot(String equipmentSlot) { this.equipmentSlot = equipmentSlot; }

    public Integer getCustomModelData() { return customModelData; }
    public void setCustomModelData(Integer customModelData) { this.customModelData = customModelData; }

    public List<String> getCustomLore() { return customLore; }
    public void setCustomLore(List<String> lore) {
        this.customLore.clear();
        if (lore != null) this.customLore.addAll(lore);
    }

    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = Math.max(0, Math.min(5, stars)); }

    public String getExecutableItemId() { return executableItemId; }
    public void setExecutableItemId(String executableItemId) { this.executableItemId = executableItemId; }

    public String getHeadTexture() { return headTexture; }
    public void setHeadTexture(String headTexture) { this.headTexture = headTexture; }

    public org.bukkit.inventory.ItemStack getTemplate() { return template; }
    public void setTemplate(org.bukkit.inventory.ItemStack template) {
        this.template = template != null ? template.clone() : null;
    }

    public List<ItemPerk> getPerks() { return perks; }
    public void setPerks(List<ItemPerk> perks) {
        this.perks.clear();
        if (perks != null) this.perks.addAll(perks);
    }
    public void addPerk(ItemPerk perk) {
        if (perk != null) this.perks.add(perk);
    }

    public String getSetId() { return setId; }
    public void setSetId(String setId) { this.setId = setId; }

    public ItemSetBonus getSetBonus() { return setBonus; }
    public void setSetBonus(ItemSetBonus setBonus) { this.setBonus = setBonus; }
}
