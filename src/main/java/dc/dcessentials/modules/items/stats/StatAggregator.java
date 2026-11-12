package dc.dcessentials.modules.items.stats;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.PlayerProfile;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemCategory;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.model.ItemStat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified, stackable stat aggregation engine for DCEItems, Accessories, Keystones, Scrolls, and DCEPets.
 * For example: 25% Surge on a Sword + 25% Surge on an Active Pet = 50% Total Surge.
 */
public class StatAggregator implements Listener {

    private static final long CACHE_TTL_MS = 250L;
    private static final NamespacedKey BASTION_KB_KEY = new NamespacedKey(DCCosmetics.getInstance(), "dce_bastion_kb");
    private static final NamespacedKey TEMPO_BREAK_KEY = new NamespacedKey(DCCosmetics.getInstance(), "dce_tempo_break");
    public static final Map<ItemStat, Double> STAT_CAPS;

    static {
        EnumMap<ItemStat, Double> caps = new EnumMap<>(ItemStat.class);
        for (ItemStat stat : ItemStat.values()) {
            caps.put(stat, stat.getHardCap());
        }
        STAT_CAPS = java.util.Collections.unmodifiableMap(caps);
    }

    private final ItemRegistry itemRegistry;
    private final Map<UUID, PlayerStatSnapshot> cache = new ConcurrentHashMap<>();

    public StatAggregator(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
    }

    public void invalidate(UUID uuid) {
        if (uuid != null) {
            cache.remove(uuid);
        }
    }

    public void invalidate(Player player) {
        if (player != null) {
            cache.remove(player.getUniqueId());
            if (Bukkit.isPrimaryThread() && player.isOnline()) {
                getSnapshot(player);
            }
        }
    }

    public void clearAll() {
        cache.clear();
    }

    public PlayerStatSnapshot getSnapshot(Player player) {
        if (player == null) {
            return new PlayerStatSnapshot(null, null);
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        PlayerStatSnapshot cached = cache.get(uuid);
        if (cached != null && (now - cached.getComputedAtMs()) <= CACHE_TTL_MS) {
            return cached;
        }

        PlayerStatSnapshot computed = computeSnapshot(player);
        cache.put(uuid, computed);
        return computed;
    }

    public double getTotalStat(Player player, ItemStat stat) {
        return getSnapshot(player).get(stat);
    }

    private PlayerStatSnapshot computeSnapshot(Player player) {
        EnumMap<StatSource, EnumMap<ItemStat, Double>> bySource = new EnumMap<>(StatSource.class);
        for (StatSource src : StatSource.values()) {
            bySource.put(src, new EnumMap<>(ItemStat.class));
        }

        int resonanceSources = 0;
        double lowestResonanceCadence = 0.0;

        // 1. MAIN_HAND (Weapons, Tools, Accessories held in main hand; exclude unequipped scrolls/pet eggs/keystones)
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isValidMainHandStatItem(mainHand)) {
            EnumMap<ItemStat, Double> itemStats = extractItemStats(mainHand);
            resonanceSources += mergeIntoSource(bySource.get(StatSource.MAIN_HAND), itemStats);
            lowestResonanceCadence = updateLowestResonance(lowestResonanceCadence, itemStats.getOrDefault(ItemStat.RESONANCE, 0.0));
        }

        // 2. OFF_HAND (Only Shields, Accessories, Tomes, or explicit offhand items — NOT mainhand weapons/tools)
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isValidOffHandStatItem(offHand)) {
            EnumMap<ItemStat, Double> itemStats = extractItemStats(offHand);
            resonanceSources += mergeIntoSource(bySource.get(StatSource.OFF_HAND), itemStats);
            lowestResonanceCadence = updateLowestResonance(lowestResonanceCadence, itemStats.getOrDefault(ItemStat.RESONANCE, 0.0));
        }

        // 3. ARMOR (Helmet, Chestplate, Leggings, Boots)
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        if (armorContents != null) {
            for (ItemStack armor : armorContents) {
                if (armor == null || armor.getType() == Material.AIR) continue;
                EnumMap<ItemStat, Double> itemStats = extractItemStats(armor);
                resonanceSources += mergeIntoSource(bySource.get(StatSource.ARMOR), itemStats);
                lowestResonanceCadence = updateLowestResonance(lowestResonanceCadence, itemStats.getOrDefault(ItemStat.RESONANCE, 0.0));
            }
        }

        // 4. PROFILE EQUIPMENT: ACCESSORIES (amulet, cloak, gloves) & KEYSTONE (keystone)
        var profileMgr = DCCosmetics.getInstance().getProfileManager();
        if (profileMgr != null) {
            PlayerProfile profile = profileMgr.getProfile(player);
            if (profile != null) {
                String[] accessorySlots = new String[]{"amulet", "cloak", "gloves"};
                for (String slot : accessorySlots) {
                    ItemStack acc = profile.getEquipment(slot);
                    if (acc != null && acc.getType() != Material.AIR) {
                        EnumMap<ItemStat, Double> itemStats = extractItemStats(acc);
                        resonanceSources += mergeIntoSource(bySource.get(StatSource.ACCESSORIES), itemStats);
                        lowestResonanceCadence = updateLowestResonance(lowestResonanceCadence, itemStats.getOrDefault(ItemStat.RESONANCE, 0.0));
                    }
                }

                ItemStack keystone = profile.getEquipment("keystone");
                if (keystone != null && keystone.getType() != Material.AIR) {
                    EnumMap<ItemStat, Double> itemStats = extractItemStats(keystone);
                    resonanceSources += mergeIntoSource(bySource.get(StatSource.KEYSTONE), itemStats);
                    lowestResonanceCadence = updateLowestResonance(lowestResonanceCadence, itemStats.getOrDefault(ItemStat.RESONANCE, 0.0));
                }
            }
        }

        // 5. ACTIVE PET (Level-scaled stats from DCEPet module)
        var petMgr = DCCosmetics.getInstance().getPetManager();
        if (petMgr != null) {
            Map<ItemStat, Double> petStats = petMgr.getActivePetStats(player);
            if (petStats != null && !petStats.isEmpty()) {
                resonanceSources += mergeIntoSource(bySource.get(StatSource.ACTIVE_PET), petStats);
                lowestResonanceCadence = updateLowestResonance(lowestResonanceCadence, petStats.getOrDefault(ItemStat.RESONANCE, 0.0));
            }
        }

        // 6. Compute stacked totals across all sources (enforcing STAT_CAPS)
        EnumMap<ItemStat, Double> totals = new EnumMap<>(ItemStat.class);
        for (ItemStat stat : ItemStat.values()) {
            if (stat == ItemStat.RESONANCE) continue;
            double sum = 0.0;
            for (StatSource src : StatSource.values()) {
                sum += bySource.get(src).getOrDefault(stat, 0.0);
            }
            if (sum > 0.0) {
                double cap = STAT_CAPS.getOrDefault(stat, stat.getHardCap());
                if (cap > 0.0) {
                    sum = Math.min(sum, cap);
                }
                totals.put(stat, sum);
            }
        }

        // Resonance stacking: lowest cadence wins, and each additional Resonance source reduces cadence by 1 (min 2-Hit, max 8-Hit)
        if (lowestResonanceCadence > 0.0) {
            double effectiveCadence = Math.max(2.0, Math.min(8.0, lowestResonanceCadence - Math.max(0, resonanceSources - 1)));
            totals.put(ItemStat.RESONANCE, effectiveCadence);
        }

        PlayerStatSnapshot snap = new PlayerStatSnapshot(totals, bySource);
        if (Bukkit.isPrimaryThread()) {
            applyPassiveAttributes(player, snap);
        } else if (DCCosmetics.getInstance().isEnabled()) {
            Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
                if (player.isOnline()) {
                    applyPassiveAttributes(player, snap);
                }
            });
        }
        return snap;
    }

    public void applyPassiveAttributes(Player player, PlayerStatSnapshot snap) {
        if (player == null || snap == null) return;
        try {
            AttributeInstance kbAttr = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (kbAttr != null) {
                kbAttr.removeModifier(BASTION_KB_KEY);
                double bastion = snap.get(ItemStat.BASTION);
                if (bastion > 0.0) {
                    double kbBonus = Math.min(0.30, bastion / 200.0);
                    if (kbBonus > 0.0) {
                        kbAttr.addTransientModifier(new AttributeModifier(
                                BASTION_KB_KEY, kbBonus, AttributeModifier.Operation.ADD_NUMBER));
                    }
                }
            }
        } catch (Throwable ignored) {}

        try {
            AttributeInstance breakAttr = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
            if (breakAttr != null) {
                breakAttr.removeModifier(TEMPO_BREAK_KEY);
                double tempo = snap.get(ItemStat.TEMPO);
                if (tempo > 0.0) {
                    double breakBonus = Math.min(0.50, tempo / 100.0);
                    if (breakBonus > 0.0) {
                        breakAttr.addTransientModifier(new AttributeModifier(
                                TEMPO_BREAK_KEY, breakBonus, AttributeModifier.Operation.ADD_SCALAR));
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private boolean isValidMainHandStatItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        DCEItem dce = getDCEItem(item);
        if (dce == null) return hasInstanceStats(item);
        ItemCategory cat = dce.getCategory();
        // Raw scrolls, vouchers, keystones, and pet tokens in hand do not grant passive combat stats unless equipped in their proper slot
        return cat != ItemCategory.SCROLL
                && cat != ItemCategory.COSMETIC_SCROLL
                && cat != ItemCategory.VOUCHER
                && cat != ItemCategory.KEYSTONE
                && cat != ItemCategory.PET
                && cat != ItemCategory.ARMOR;
    }

    private boolean isValidOffHandStatItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        if (item.getType() == Material.SHIELD || item.getType() == Material.TOTEM_OF_UNDYING) {
            return true;
        }
        DCEItem dce = getDCEItem(item);
        if (dce != null && dce.getCategory() == ItemCategory.ACCESSORY) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            String slot = pdc.get(ItemRegistry.EQUIPMENT_SLOT_KEY, PersistentDataType.STRING);
            return slot != null && (slot.equalsIgnoreCase("OFF_HAND") || slot.equalsIgnoreCase("OFFHAND") || slot.equalsIgnoreCase("ACCESSORY"));
        }
        return false;
    }

    public EnumMap<ItemStat, Double> extractItemStats(ItemStack item) {
        EnumMap<ItemStat, Double> result = new EnumMap<>(ItemStat.class);
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return result;
        }

        DCEItem dce = getDCEItem(item);
        if (dce != null) {
            for (Map.Entry<ItemStat, Double> entry : dce.getStats().entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0.0) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }

        // Merge per-instance PDC stat bonuses (e.g. "SURGE:10.0,WARD:5.0")
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (pdc.has(ItemRegistry.INSTANCE_STATS_KEY, PersistentDataType.STRING)) {
                String raw = pdc.get(ItemRegistry.INSTANCE_STATS_KEY, PersistentDataType.STRING);
                if (raw != null && !raw.isEmpty()) {
                    for (String token : raw.split(",")) {
                        String[] kv = token.split(":");
                        if (kv.length == 2) {
                            ItemStat st = ItemStat.fromString(kv[0].trim());
                            if (st != null) {
                                try {
                                    double val = Double.parseDouble(kv[1].trim());
                                    if (val <= 0.0) continue;
                                    if (st == ItemStat.RESONANCE) {
                                        double cur = result.getOrDefault(st, 0.0);
                                        result.put(st, cur <= 0.0 ? val : Math.min(cur, val));
                                    } else {
                                        result.put(st, result.getOrDefault(st, 0.0) + val);
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    private DCEItem getDCEItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta() || itemRegistry == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(ItemRegistry.ITEM_ID_KEY, PersistentDataType.STRING)) return null;
        String id = pdc.get(ItemRegistry.ITEM_ID_KEY, PersistentDataType.STRING);
        return itemRegistry.getItem(id);
    }

    private boolean hasInstanceStats(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(ItemRegistry.INSTANCE_STATS_KEY, PersistentDataType.STRING);
    }

    private int mergeIntoSource(EnumMap<ItemStat, Double> target, Map<ItemStat, Double> addition) {
        int resAdded = 0;
        for (Map.Entry<ItemStat, Double> entry : addition.entrySet()) {
            ItemStat stat = entry.getKey();
            double val = entry.getValue() != null ? entry.getValue() : 0.0;
            if (val <= 0.0) continue;

            if (stat == ItemStat.RESONANCE) {
                resAdded++;
                double existing = target.getOrDefault(stat, 0.0);
                target.put(stat, existing <= 0.0 ? val : Math.min(existing, val));
            } else {
                target.put(stat, target.getOrDefault(stat, 0.0) + val);
            }
        }
        return resAdded;
    }

    private double updateLowestResonance(double currentLowest, double candidate) {
        if (candidate <= 0.0) return currentLowest;
        if (currentLowest <= 0.0) return candidate;
        return Math.min(currentLowest, candidate);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        cache.remove(p.getUniqueId());
        if (DCCosmetics.getInstance().isEnabled()) {
            Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
                if (p.isOnline()) invalidate(p);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        cache.remove(p.getUniqueId());
        if (DCCosmetics.getInstance().isEnabled()) {
            Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
                if (p.isOnline()) invalidate(p);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            cache.remove(player.getUniqueId());
            if (DCCosmetics.getInstance().isEnabled()) {
                Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
                    if (player.isOnline()) invalidate(player);
                });
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        invalidate(event.getPlayer().getUniqueId());
    }
}
