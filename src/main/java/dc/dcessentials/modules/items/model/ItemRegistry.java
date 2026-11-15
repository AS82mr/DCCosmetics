package dc.dcessentials.modules.items.model;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.items.hook.ExecutableItemsHook;
import dc.dcessentials.modules.items.scroll.ScrollRegistry;
import dc.dcessentials.modules.items.scroll.ScrollType;
import dc.dcessentials.modules.items.tooltip.TooltipRenderer;
import dc.dcessentials.modules.items.tracker.ItemTrackerManager;
import dc.dcessentials.modules.pets.model.PetPerkEffectType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class ItemRegistry {

    public static final NamespacedKey ITEM_ID_KEY = new NamespacedKey(DCCosmetics.getInstance(), "item_id");
    public static final NamespacedKey VERSION_KEY = new NamespacedKey(DCCosmetics.getInstance(), "item_version");
    public static final NamespacedKey UUID_KEY = new NamespacedKey(DCCosmetics.getInstance(), "item_uuid");
    public static final NamespacedKey CATEGORY_KEY = new NamespacedKey(DCCosmetics.getInstance(), "item_category");
    public static final NamespacedKey RARITY_KEY = new NamespacedKey(DCCosmetics.getInstance(), "item_rarity");
    public static final NamespacedKey EQUIPMENT_SLOT_KEY = new NamespacedKey(DCCosmetics.getInstance(), "equipment_slot");
    public static final NamespacedKey INSTANCE_STATS_KEY = new NamespacedKey(DCCosmetics.getInstance(), "instance_stats");
    public static final NamespacedKey PET_ID_KEY = new NamespacedKey(DCCosmetics.getInstance(), "dce_pet_id");
    public static final NamespacedKey PET_LEVEL_KEY = new NamespacedKey(DCCosmetics.getInstance(), "dce_pet_level");
    public static final NamespacedKey PET_XP_KEY = new NamespacedKey(DCCosmetics.getInstance(), "dce_pet_xp");
    public static final NamespacedKey PET_RARITY_KEY = new NamespacedKey(DCCosmetics.getInstance(), "dce_pet_rarity");

    private volatile Map<String, DCEItem> items = Collections.emptyMap();
    private final Map<String, String> itemSources = new HashMap<>();
    private final Object registryLock = new Object();
    private final File itemsDir;
    private final File legacyConfigFile;
    private final ScrollRegistry scrollRegistry;

    public ItemRegistry(ScrollRegistry scrollRegistry) {
        this.scrollRegistry = scrollRegistry;
        this.itemsDir = new File(DCCosmetics.getInstance().getDataFolder(), "items");
        this.legacyConfigFile = new File(DCCosmetics.getInstance().getDataFolder(), "items.yml");
    }

    private void putItemSync(String id, DCEItem item, String source, boolean allowOverwrite) {
        if (id == null || item == null) return;
        String lower = id.toLowerCase(Locale.ROOT);
        synchronized (registryLock) {
            if (!allowOverwrite && items.containsKey(lower)) {
                String existingSource = itemSources.getOrDefault(lower, "unknown");
                if (!existingSource.equals(source)) {
                    DCCosmetics.getInstance().getLogger().severe(
                            "[DCEssentials] Duplicate DCEItem ID '" + lower + "' from " + source
                                    + " collides with existing source " + existingSource + "; keeping first, skipping duplicate.");
                    return;
                }
            }
            Map<String, DCEItem> copy = new LinkedHashMap<>(items);
            copy.put(lower, item);
            items = Collections.unmodifiableMap(copy);
            itemSources.put(lower, source);
        }
    }

    private DCEItem removeItemSync(String id) {
        if (id == null) return null;
        String lower = id.toLowerCase(Locale.ROOT);
        synchronized (registryLock) {
            if (!items.containsKey(lower)) return null;
            Map<String, DCEItem> copy = new LinkedHashMap<>(items);
            DCEItem removed = copy.remove(lower);
            items = Collections.unmodifiableMap(copy);
            itemSources.remove(lower);
            return removed;
        }
    }

    public void loadItems() {
        synchronized (registryLock) {
            items = Collections.emptyMap();
            itemSources.clear();
        }

        // 1. Migrate legacy items.yml if present
        if (legacyConfigFile.exists()) {
            migrateLegacyConfig();
        }

        // 2. Ensure items directory exists or populate defaults
        if (!itemsDir.exists() || itemsDir.listFiles() == null || itemsDir.listFiles().length == 0) {
            createDefaultItems();
        }

        // 3. Recursively load all YAML files in items directory
        loadDirectory(itemsDir);

        // 4. Auto-register Universal Scrolls from ScrollRegistry into /dce items GUI
        if (scrollRegistry != null) {
            for (dc.dcessentials.modules.items.scroll.ScrollModel sm : scrollRegistry.getScrolls()) {
                ItemRarity r = ItemRarity.fromString(sm.getRarity());
                DCEItem dceScroll = new DCEItem(
                        sm.getId(),
                        1,
                        sm.getIcon(),
                        sm.getName(),
                        r,
                        ItemCategory.SCROLL
                );
                dceScroll.setEquipmentSlot(sm.getTargetCategory());
                dceScroll.setCustomLore(sm.getLore());
                ItemStack scrollStack = scrollRegistry.createScrollItem(sm.getId(), 1);
                dceScroll.setTemplate(scrollStack);
                putItemSync(sm.getId(), dceScroll, "scrolls.yml:" + sm.getId(), false);
            }
        }

        // 5. Auto-register Cosmetic Scrolls from TemplateRegistry into /dce items GUI
        syncAllCosmetics();

        // 6. Pre-populate all standard vanilla base equipment templates
        ensureDefaultVanillaItemsRegistered();

        // 7. Ensure Void Warper Wand template is registered and persisted
        ensureVoidWarperRegistered();

        // Clean up any stale legacy cosmetic files in items/
        File cosDir = new File(itemsDir, "cosmetic_scroll");
        if (cosDir.exists()) purgeDirectoryRecursive(cosDir, "", null);
        File cosDir2 = new File(itemsDir, "cosmetic");
        if (cosDir2.exists()) purgeDirectoryRecursive(cosDir2, "", null);

        DCCosmetics.getInstance().getLogger().info("[DCEssentials] Loaded " + items.size() + " master items (including synced scrolls) into registry.");
    }

    public void syncCosmeticTemplate(dc.dccosmetics.model.CosmeticTemplate temp) {
        if (temp == null) return;
        String scrollId = temp.getId();
        ItemRarity r = ItemRarity.fromString(temp.getRarity());
        String baseName = (temp.getItemName() != null && !temp.getItemName().trim().isEmpty() && !temp.getItemName().equalsIgnoreCase(temp.getId()))
                ? temp.getItemName().trim()
                : TooltipRenderer.formatSkinName(temp.getId());
        String dispName = baseName.toLowerCase().endsWith("scroll") ? baseName : baseName + " Scroll";

        ItemStack cosScroll = DCCosmetics.getInstance().createScroll(temp.getId(), null);
        DCEItem dceScroll = items.get(scrollId.toLowerCase());
        if (dceScroll == null) {
            dceScroll = new DCEItem(
                    scrollId,
                    1,
                    cosScroll != null ? cosScroll.getType() : Material.PLAYER_HEAD,
                    dispName,
                    r,
                    ItemCategory.COSMETIC_SCROLL
            );
            putItemSync(scrollId, dceScroll, "cosmetic:" + scrollId, false);
        } else {
            dceScroll.setDisplayName(dispName);
            dceScroll.setRarity(r);
            if (cosScroll != null) dceScroll.setMaterial(cosScroll.getType());
        }
        dceScroll.setEquipmentSlot(temp.getEquipmentSlot());
        dceScroll.setTemplate(cosScroll);

        List<String> loreLines = new ArrayList<>();
        String slotName = temp.getEquipmentSlot() != null ? temp.getEquipmentSlot().toUpperCase() : "EQUIPMENT";
        loreLines.add("&7Type: <#B996E2>Cosmetic Infusion");
        loreLines.add("&7Target Slot: &f" + slotName);
        loreLines.add("&f");
        if (temp.getLore() != null && !temp.getLore().isEmpty()) {
            for (String l : temp.getLore()) {
                loreLines.add(l);
            }
            loreLines.add("&f");
        }
        loreLines.add("<#FCD34D>&l✦  Drag & drop onto " + slotName.toLowerCase() + " to infuse!");
        dceScroll.setCustomLore(loreLines);
    }

    public void syncAllCosmetics() {
        if (DCCosmetics.getInstance().getTemplateRegistry() != null) {
            for (var temp : DCCosmetics.getInstance().getTemplateRegistry().getAllTemplates()) {
                syncCosmeticTemplate(temp);
            }
        }
    }

    private void loadDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        if (dir.getName().equalsIgnoreCase("cosmetic_scroll") || dir.getName().equalsIgnoreCase("cosmetic")) {
            return; // Skip and do not load cosmetics from items/
        }
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                loadDirectory(file);
            } else if (file.getName().endsWith(".yml") || file.getName().endsWith(".yaml")) {
                loadItemFromFile(file);
            }
        }
    }

    private void loadItemFromFile(File file) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String id = config.getString("id", file.getName().replace(".yml", "").replace(".yaml", ""));
            int version = config.getInt("version", 1);
            String matStr = config.getString("material", "NETHERITE_SWORD");
            Material mat = Material.matchMaterial(matStr);
            if (mat == null) mat = Material.NETHERITE_SWORD;

            String name = config.getString("name", id);
            ItemRarity rarity = ItemRarity.fromString(config.getString("rarity", "COMMON"));
            ItemCategory category = ItemCategory.fromString(config.getString("category", "ITEM"));

            DCEItem item = new DCEItem(id, version, mat, name, rarity, category);
            item.setStars(config.getInt("stars", 0));
            if (config.contains("model_data")) item.setCustomModelData(config.getInt("model_data"));
            if (config.contains("equipment_slot")) item.setEquipmentSlot(config.getString("equipment_slot"));
            if (config.contains("lore")) item.setCustomLore(config.getStringList("lore"));
            if (config.contains("executable_item_id")) item.setExecutableItemId(config.getString("executable_item_id"));
            if (config.contains("texture")) {
                String tex = config.getString("texture");
                if (tex != null && !tex.isEmpty()) {
                    item.setHeadTexture(tex);
                    if (mat == Material.PLAYER_HEAD) {
                        item.setTemplate(dc.dccosmetics.util.HeadUtil.getCustomHead(tex));
                    }
                }
            }
            if (config.contains("template")) {
                ItemStack tpl = config.getItemStack("template");
                if (tpl != null) {
                    item.setTemplate(tpl);
                    if (tpl.hasItemMeta()) {
                        var pdc = tpl.getItemMeta().getPersistentDataContainer();
                        if (pdc.has(new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_scroll_id"), PersistentDataType.STRING)
                                || pdc.has(new NamespacedKey("dcessentials", "cosmetic_scroll_id"), PersistentDataType.STRING)) {
                            file.delete();
                            return;
                        }
                    }
                }
            }

            // Stats
            ConfigurationSection statsSec = config.getConfigurationSection("stats");
            if (statsSec != null && !id.startsWith("default_")) {
                for (String statKey : statsSec.getKeys(false)) {
                    ItemStat stat = ItemStat.fromString(statKey);
                    if (stat != null) {
                        item.setStat(stat, statsSec.getDouble(statKey));
                    }
                }
            }

            // Sockets
            List<String> sockets = config.getStringList("sockets");
            for (String s : sockets) {
                try {
                    item.addAllowedSocket(ScrollType.valueOf(s.toUpperCase()));
                } catch (IllegalArgumentException ignored) {}
            }
            ConfigurationSection socketSlotsSec = config.getConfigurationSection("socket_slots");
            if (socketSlotsSec != null) {
                for (String sKey : socketSlotsSec.getKeys(false)) {
                    try {
                        ScrollType st = ScrollType.valueOf(sKey.toUpperCase());
                        item.setSocketCapacity(st, socketSlotsSec.getInt(sKey, 1));
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            if (id.startsWith("default_")) {
                item.getStats().clear(); // Guarantee default vanilla baseline has 0 stats
            }

            // Individual Perks
            List<ItemPerk> loadedPerks = parsePerkList(config.getMapList("perks"));
            if (!loadedPerks.isEmpty()) {
                item.setPerks(loadedPerks);
            }

            // Armor Set ID & Set Bonus
            if (config.contains("set_id")) {
                item.setSetId(config.getString("set_id"));
            }
            ConfigurationSection setBonusSec = config.getConfigurationSection("set_bonus");
            if (setBonusSec != null) {
                String effSetId = setBonusSec.getString("set_id", item.getSetId());
                if (effSetId == null || effSetId.isBlank()) effSetId = id;
                if (item.getSetId() == null) item.setSetId(effSetId);
                String setName = setBonusSec.getString("name", effSetId);
                int reqPieces = Math.max(2, setBonusSec.getInt("required", 4));
                List<ItemPerk> setPerks = parsePerkList(setBonusSec.getMapList("perks"));
                if (!setPerks.isEmpty()) {
                    item.setSetBonus(new ItemSetBonus(effSetId, setName, reqPieces, setPerks));
                }
            }

            putItemSync(id, item, "file:" + file.getPath(), false);
        } catch (Throwable t) {
            DCCosmetics.getInstance().getLogger().log(Level.WARNING, "[DCEssentials] Error loading item from file: " + file.getName(), t);
        }
    }

    private void migrateLegacyConfig() {
        try {
            DCCosmetics.getInstance().getLogger().info("[DCEssentials] Migrating legacy items.yml to hierarchical folder structure...");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(legacyConfigFile);
            ConfigurationSection root = config.getConfigurationSection("items");
            if (root != null) {
                for (String id : root.getKeys(false)) {
                    ConfigurationSection sec = root.getConfigurationSection(id);
                    if (sec == null) continue;

                    int version = sec.getInt("version", 1);
                    String matStr = sec.getString("material", "NETHERITE_SWORD");
                    Material mat = Material.matchMaterial(matStr);
                    if (mat == null) mat = Material.NETHERITE_SWORD;

                    String name = sec.getString("name", id);
                    ItemRarity rarity = ItemRarity.fromString(sec.getString("rarity", "COMMON"));
                    ItemCategory category = ItemCategory.fromString(sec.getString("category", "ITEM"));

                    DCEItem item = new DCEItem(id, version, mat, name, rarity, category);
                    item.setStars(sec.getInt("stars", 0));
                    if (sec.contains("model_data")) item.setCustomModelData(sec.getInt("model_data"));
                    if (sec.contains("equipment_slot")) item.setEquipmentSlot(sec.getString("equipment_slot"));
                    if (sec.contains("lore")) item.setCustomLore(sec.getStringList("lore"));
                    if (sec.contains("executable_item_id")) item.setExecutableItemId(sec.getString("executable_item_id"));
                    if (sec.contains("template")) {
                        ItemStack tpl = sec.getItemStack("template");
                        if (tpl != null) item.setTemplate(tpl);
                    }

                    ConfigurationSection statsSec = sec.getConfigurationSection("stats");
                    if (statsSec != null) {
                        for (String statKey : statsSec.getKeys(false)) {
                            ItemStat stat = ItemStat.fromString(statKey);
                            if (stat != null) item.setStat(stat, statsSec.getDouble(statKey));
                        }
                    }

                    List<String> sockets = sec.getStringList("sockets");
                    for (String s : sockets) {
                        try {
                            item.addAllowedSocket(ScrollType.valueOf(s.toUpperCase()));
                        } catch (IllegalArgumentException ignored) {}
                    }

                    saveItem(item);
                }
            }
            File backup = new File(legacyConfigFile.getParentFile(), "items.yml.migrated");
            if (backup.exists()) backup.delete();
            legacyConfigFile.renameTo(backup);
            DCCosmetics.getInstance().getLogger().info("[DCEssentials] Legacy items.yml successfully migrated and renamed to items.yml.migrated");
        } catch (Throwable t) {
            DCCosmetics.getInstance().getLogger().log(Level.SEVERE, "[DCEssentials] Failed during legacy items.yml migration", t);
        }
    }

    private void createDefaultItems() {
        itemsDir.mkdirs();

        // 1. Superior Dragon Helmet
        DCEItem helm = new DCEItem("superior_dragon_helmet", 2, Material.NETHERITE_HELMET, "Renowned Superior Dragon Helmet", ItemRarity.MASTERY, ItemCategory.ARMOR);
        helm.setStars(5);
        helm.setEquipmentSlot("HEAD");
        helm.setStat(ItemStat.WARD, 25.0);
        helm.setStat(ItemStat.SURGE, 15.0);
        helm.addAllowedSocket(ScrollType.COSMETIC);
        helm.addAllowedSocket(ScrollType.MASTERY);
        helm.setCustomLore(List.of(
                "&6Full Set Bonus: Superior Blood (4/4)",
                "&7Increases all Combat stats by &c5%&7 and expands",
                "&7Resonance blast radiuses by &a+2 blocks&7."
        ));
        saveItem(helm);

        // 2. Void Sovereign Blade
        DCEItem blade = new DCEItem("void_sovereign_blade", 2, Material.NETHERITE_SWORD, "Void Sovereign Greatsword", ItemRarity.LEGENDARY, ItemCategory.WEAPON);
        blade.setStars(4);
        blade.setEquipmentSlot("MAINHAND");
        blade.setStat(ItemStat.RESONANCE, 4.0);
        blade.setStat(ItemStat.CLEAVE, 65.0);
        blade.setStat(ItemStat.SIPHON, 15.0);
        blade.addAllowedSocket(ScrollType.COMBAT);
        blade.addAllowedSocket(ScrollType.MASTERY);
        blade.setCustomLore(List.of(
                "<#84E2CD>&lAbility: Rhythmic Void Rift",
                "&7Landing consecutive cadence strikes opens a spatial",
                "&7collapse dealing unmitigated void damage."
        ));
        saveItem(blade);

        // 3. Chrono-Forged Mace
        DCEItem mace = new DCEItem("chrono_forged_mace", 2, Material.MACE, "Chrono-Forged Temporal Mace", ItemRarity.MASTERY, ItemCategory.WEAPON);
        mace.setStars(5);
        mace.setEquipmentSlot("MAINHAND");
        mace.setStat(ItemStat.STASIS, 35.0);
        mace.setStat(ItemStat.EXECUTE, 20.0);
        mace.addAllowedSocket(ScrollType.COMBAT);
        mace.addAllowedSocket(ScrollType.MASTERY);
        saveItem(mace);

        // 4. Astral Tesseract Keystone
        DCEItem keystone = new DCEItem("astral_tesseract", 2, Material.ECHO_SHARD, "Astral Tesseract Matrix", ItemRarity.MASTERY, ItemCategory.KEYSTONE);
        keystone.setStars(5);
        keystone.addAllowedSocket(ScrollType.KEYSTONE);
        keystone.setCustomLore(List.of(
                "<#06B6D4>&lSpatial Resonance",
                "&7Harmonizes player inventory with all established",
                "&7waypoint coordinates across realms."
        ));
        saveItem(keystone);

        // 5. Void Warper Wand
        ensureVoidWarperRegistered();
    }

    public void ensureVoidWarperRegistered() {
        if (items.containsKey("void_warper")) return;

        DCEItem wand = new DCEItem("void_warper", 1, Material.AMETHYST_SHARD, "<#A855F7>&l✦ ᴠᴏɪᴅ ᴡᴀʀᴘᴇʀ ✦", ItemRarity.MASTERY, ItemCategory.WEAPON);
        wand.setEquipmentSlot("MAINHAND");
        wand.setCustomLore(List.of(
                "&7ʀᴀʀɪᴛʏ: <#A855F7>&lᴍʏᴛʜɪᴄ",
                "&7ᴛʏᴘᴇ: &fᴅɪᴍᴇɴꜱɪᴏɴᴀʟ ᴡᴀɴᴅ",
                "&f",
                "<#A855F7>&lʀɪɢʜᴛ-ᴄʟɪᴄᴋ: &fᴠᴏɪᴅ ʙᴏᴜɴᴄᴇ",
                "&7Instantly teleports &d8 blocks &7in your exact line of sight,",
                "&7preserving directional momentum with &aZero Cooldown&7!",
                "&7Chain-bounce effortlessly through the air.",
                "&f",
                "<#06B6D4>&lꜱʜɪꜰᴛ + ʀɪɢʜᴛ-ᴄʟɪᴄᴋ: &fꜱᴏɴɪᴄ ʙᴏᴏᴍ ᴛᴜʀʙᴏ",
                "&7Supercharges for 1.2s, then propels you &b38+ Blocks",
                "&7forward in a devastating supersonic shockwave.",
                "&f",
                "&8Cooldown: 3s"
        ));
        wand.addAllowedSocket(ScrollType.MASTERY);
        saveItem(wand);
        putItemSync("void_warper", wand, "builtin:void_warper", false);
    }

    public DCEItem getItem(String id) {
        if (id == null) return null;
        return items.get(id.toLowerCase());
    }

    public Collection<DCEItem> getItems() {
        return Collections.unmodifiableCollection(items.values());
    }

    public void registerItem(DCEItem item) {
        if (item == null) return;
        putItemSync(item.getId(), item, "runtime:" + item.getId(), true);
        saveItem(item);
    }

    public boolean deleteItem(String id) {
        if (id == null) return false;
        DCEItem item = removeItemSync(id);

        // Recursively clean all occurrences of id.yml across all directories
        purgeItemFilesOnDisk(id);
        return item != null;
    }

    public int wipeItemGlobally(String itemId, String compensationCommand) {
        if (itemId == null) return 0;
        int totalWiped = 0;

        // 1. Unregister and delete all files on disk
        deleteItem(itemId);

        // 2. Wipe from all online players
        for (org.bukkit.entity.Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            boolean hadItem = false;
            for (int i = 0; i < online.getInventory().getSize(); i++) {
                ItemStack stack = online.getInventory().getItem(i);
                if (stack != null && isDCEItem(stack) && itemId.equalsIgnoreCase(getItemId(stack))) {
                    totalWiped += stack.getAmount();
                    online.getInventory().setItem(i, null);
                    hadItem = true;
                }
            }
            for (int i = 0; i < online.getEnderChest().getSize(); i++) {
                ItemStack stack = online.getEnderChest().getItem(i);
                if (stack != null && isDCEItem(stack) && itemId.equalsIgnoreCase(getItemId(stack))) {
                    totalWiped += stack.getAmount();
                    online.getEnderChest().setItem(i, null);
                    hadItem = true;
                }
            }
            if (hadItem) {
                online.updateInventory();
                if (compensationCommand != null && !compensationCommand.trim().isEmpty()) {
                    String cmd = compensationCommand.replace("%player%", online.getName());
                    org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd);
                }
            }
        }

        // 3. Wipe from tracker ledger
        var itemModule = dc.dccosmetics.DCCosmetics.getInstance().getItemModule();
        if (itemModule != null && itemModule.getItemTrackerManager() != null) {
            totalWiped += itemModule.getItemTrackerManager().deleteItemGlobally(itemId);
        }

        return totalWiped;
    }

    private void purgeItemFilesOnDisk(String id) {
        if (id == null || itemsDir == null || !itemsDir.exists()) return;
        purgeDirectoryRecursive(itemsDir, id.toLowerCase(), null);
    }

    private void purgeStaleFiles(String id, File currentDestination) {
        if (id == null || itemsDir == null || !itemsDir.exists()) return;
        String curPath = currentDestination != null ? currentDestination.getAbsolutePath() : null;
        purgeDirectoryRecursive(itemsDir, id.toLowerCase(), curPath);
    }

    private void purgeDirectoryRecursive(File dir, String targetId, String excludeAbsolutePath) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                purgeDirectoryRecursive(f, targetId, excludeAbsolutePath);
                File[] remaining = f.listFiles();
                if (remaining != null && remaining.length == 0) {
                    f.delete();
                }
            } else if (f.isFile() && f.getName().equalsIgnoreCase(targetId + ".yml")) {
                if (excludeAbsolutePath == null || !f.getAbsolutePath().equalsIgnoreCase(excludeAbsolutePath)) {
                    f.delete();
                }
            }
        }
    }

    public DCEItem copyItem(DCEItem original, String newId) {
        if (original == null) return null;
        if (newId == null || newId.trim().isEmpty()) {
            newId = original.getId() + "_copy";
            int suffix = 1;
            while (items.containsKey(newId.toLowerCase())) {
                newId = original.getId() + "_copy_" + suffix++;
            }
        }
        DCEItem copy = new DCEItem(
                newId,
                1,
                original.getMaterial(),
                original.getDisplayName() + " (Copy)",
                original.getRarity(),
                original.getCategory()
        );
        copy.setStars(original.getStars());
        copy.setCustomModelData(original.getCustomModelData());
        copy.setEquipmentSlot(original.getEquipmentSlot());
        copy.setCustomLore(original.getCustomLore());
        copy.setExecutableItemId(original.getExecutableItemId());
        if (original.getTemplate() != null) {
            copy.setTemplate(original.getTemplate().clone());
        }
        for (Map.Entry<ItemStat, Double> entry : original.getStats().entrySet()) {
            copy.setStat(entry.getKey(), entry.getValue());
        }
        for (ScrollType st : original.getAllowedSockets()) {
            copy.addAllowedSocket(st);
        }
        registerItem(copy);
        return copy;
    }

    public boolean initializeDefaultEquipment(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (isDCEItem(item)) return false;

        Material mat = item.getType();
        if (!isEligibleEquipment(mat)) return false;

        // Safety: Strictly NEVER touch custom items from Skript, ExecutableItems, Denizen, etc.
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                // Any item with a custom display name is a plugin/script custom item
                if (meta.hasDisplayName()) return false;
                // Any item with custom lore is a plugin/script custom item
                if (meta.hasLore()) return false;
                // Any item with custom model data belongs to a custom resource pack mechanic
                if (meta.hasCustomModelData()) return false;

                // Any item with foreign PDC keys must never be converted
                var pdc = meta.getPersistentDataContainer();
                for (NamespacedKey k : pdc.getKeys()) {
                    String ns = k.getNamespace().toLowerCase();
                    if (!ns.equals("minecraft") && !ns.equals("dccosmetics") && !ns.equals("dcessentials")) {
                        return false;
                    }
                }
            }
        }

        String defaultId = "default_" + mat.name().toLowerCase();
        DCEItem dceItem = getItem(defaultId);
        if (dceItem == null) {
            ItemCategory category = ItemCategory.detectFromMaterial(mat);
            String name = formatMaterialName(mat);
            dceItem = new DCEItem(defaultId, 1, mat, name, ItemRarity.COMMON, category);

            // Default Sockets:
            // Mace: Mastery & Combat
            // Sword / Weapon: Mastery & Combat
            // Armor: Cosmetic
            // Tool: Mastery
            if (mat == Material.MACE) {
                dceItem.addAllowedSocket(ScrollType.COMBAT);
                dceItem.addAllowedSocket(ScrollType.MASTERY);
            } else if (category == ItemCategory.WEAPON) {
                dceItem.addAllowedSocket(ScrollType.COMBAT);
                dceItem.addAllowedSocket(ScrollType.MASTERY);
            } else if (category == ItemCategory.ARMOR) {
                dceItem.addAllowedSocket(ScrollType.COSMETIC);
            } else if (category == ItemCategory.TOOL) {
                dceItem.addAllowedSocket(ScrollType.MASTERY);
            }

            putItemSync(defaultId, dceItem, "vanilla:" + defaultId, false);
        }

        // CRITICAL FIX: Ensure default equipment initialized from vanilla drops has ZERO stats
        dceItem.getStats().clear();

        // Apply DCE PDC Tags
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ITEM_ID_KEY, PersistentDataType.STRING, dceItem.getId());
            pdc.set(VERSION_KEY, PersistentDataType.INTEGER, dceItem.getVersion());
            pdc.set(UUID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
            pdc.set(CATEGORY_KEY, PersistentDataType.STRING, dceItem.getCategory().name());
            pdc.set(RARITY_KEY, PersistentDataType.STRING, dceItem.getRarity().name());

            item.setItemMeta(meta);
        }

        TooltipRenderer.renderItem(dceItem, item, scrollRegistry);
        return true;
    }

    public static boolean isEligibleEquipment(Material mat) {
        if (mat == null) return false;
        String name = mat.name();
        return mat == Material.MACE
                || name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")
                || mat == Material.ELYTRA
                || name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")
                || mat == Material.BOW
                || mat == Material.CROSSBOW
                || mat == Material.TRIDENT;
    }

    private static String formatMaterialName(Material mat) {
        String[] parts = mat.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }

    public void saveItem(DCEItem item) {
        if (item == null) return;
        if (item.getCategory() == ItemCategory.COSMETIC_SCROLL || item.getCategory() == ItemCategory.COSMETIC) {
            return; // Cosmetics are configured and persisted strictly in cosmetics/ folder
        }

        File categoryDir;
        if (item.getId().startsWith("default_")) {
            categoryDir = new File(itemsDir, "vanilla");
        } else {
            categoryDir = new File(itemsDir, item.getCategory().name().toLowerCase() + "/" + item.getRarity().name().toLowerCase());
        }
        categoryDir.mkdirs();
        File itemFile = new File(categoryDir, item.getId() + ".yml");

        // Purge any stale YAML files for this ID in other categories or rarities
        purgeStaleFiles(item.getId(), itemFile);

        YamlConfiguration config = new YamlConfiguration();
        config.set("id", item.getId());
        config.set("version", item.getVersion());
        config.set("material", item.getMaterial().name());
        config.set("name", item.getDisplayName());
        config.set("rarity", item.getRarity().name());
        config.set("category", item.getCategory().name());
        config.set("stars", item.getStars());

        if (item.getCustomModelData() != null) config.set("model_data", item.getCustomModelData());
        if (item.getEquipmentSlot() != null) config.set("equipment_slot", item.getEquipmentSlot());
        if (!item.getCustomLore().isEmpty()) config.set("lore", item.getCustomLore());
        if (item.getExecutableItemId() != null) config.set("executable_item_id", item.getExecutableItemId());
        if (item.getHeadTexture() != null && !item.getHeadTexture().isEmpty()) {
            config.set("texture", item.getHeadTexture());
        } else if (item.getTemplate() != null) {
            config.set("template", item.getTemplate());
        }

        // Stats
        for (Map.Entry<ItemStat, Double> entry : item.getStats().entrySet()) {
            config.set("stats." + entry.getKey().name(), entry.getValue());
        }

        // Sockets
        List<String> sockets = new ArrayList<>();
        for (ScrollType st : item.getAllowedSockets()) {
            sockets.add(st.name());
            int cap = item.getSocketCapacity(st);
            if (cap > 1) {
                config.set("socket_slots." + st.name(), cap);
            }
        }
        config.set("sockets", sockets);

        if (!item.getPerks().isEmpty()) {
            config.set("perks", serializePerkList(item.getPerks()));
        }
        if (item.getSetId() != null && !item.getSetId().isBlank()) {
            config.set("set_id", item.getSetId());
        }
        if (item.getSetBonus() != null && !item.getSetBonus().perks().isEmpty()) {
            config.set("set_bonus.name", item.getSetBonus().setName());
            config.set("set_bonus.required", item.getSetBonus().requiredPieces());
            config.set("set_bonus.perks", serializePerkList(item.getSetBonus().perks()));
        }

        try {
            config.save(itemFile);
        } catch (IOException e) {
            DCCosmetics.getInstance().getLogger().log(Level.SEVERE, "[DCEssentials] Failed to save item to: " + itemFile.getPath(), e);
        }
    }

    public void ensureDefaultVanillaItemsRegistered() {
        // Weapons (Combat + Mastery sockets)
        registerVanillaTemplate(Material.NETHERITE_SWORD, ItemCategory.WEAPON, ScrollType.COMBAT, ScrollType.MASTERY);
        registerVanillaTemplate(Material.DIAMOND_SWORD, ItemCategory.WEAPON, ScrollType.COMBAT, ScrollType.MASTERY);
        registerVanillaTemplate(Material.IRON_SWORD, ItemCategory.WEAPON, ScrollType.COMBAT, ScrollType.MASTERY);
        registerVanillaTemplate(Material.GOLDEN_SWORD, ItemCategory.WEAPON, ScrollType.COMBAT, ScrollType.MASTERY);
        registerVanillaTemplate(Material.STONE_SWORD, ItemCategory.WEAPON, ScrollType.COMBAT, ScrollType.MASTERY);
        registerVanillaTemplate(Material.WOODEN_SWORD, ItemCategory.WEAPON, ScrollType.COMBAT, ScrollType.MASTERY);

        registerVanillaTemplate(Material.NETHERITE_AXE, ItemCategory.WEAPON, ScrollType.COMBAT, ScrollType.MASTERY);
        registerVanillaTemplate(Material.DIAMOND_AXE, ItemCategory.WEAPON, ScrollType.COMBAT, ScrollType.MASTERY);
        registerVanillaTemplate(Material.IRON_AXE, ItemCategory.WEAPON, ScrollType.COMBAT, ScrollType.MASTERY);
        registerVanillaTemplate(Material.GOLDEN_AXE, ItemCategory.WEAPON, ScrollType.COMBAT, ScrollType.MASTERY);
        registerVanillaTemplate(Material.STONE_AXE, ItemCategory.WEAPON, ScrollType.COMBAT, ScrollType.MASTERY);
        registerVanillaTemplate(Material.WOODEN_AXE, ItemCategory.WEAPON, ScrollType.COMBAT, ScrollType.MASTERY);

        registerVanillaTemplate(Material.MACE, ItemCategory.WEAPON, ScrollType.COMBAT, ScrollType.MASTERY);
        registerVanillaTemplate(Material.TRIDENT, ItemCategory.WEAPON, ScrollType.COMBAT, ScrollType.MASTERY);
        registerVanillaTemplate(Material.BOW, ItemCategory.WEAPON, ScrollType.COMBAT, ScrollType.MASTERY);
        registerVanillaTemplate(Material.CROSSBOW, ItemCategory.WEAPON, ScrollType.COMBAT, ScrollType.MASTERY);

        // Armor (Cosmetic socket)
        Material[] armors = {
                Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
                Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
                Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
                Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
                Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                Material.TURTLE_HELMET, Material.ELYTRA
        };
        for (Material m : armors) {
            registerVanillaTemplate(m, ItemCategory.ARMOR, ScrollType.COSMETIC);
        }

        // Tools (Mastery socket)
        Material[] tools = {
                Material.NETHERITE_PICKAXE, Material.DIAMOND_PICKAXE, Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE, Material.STONE_PICKAXE, Material.WOODEN_PICKAXE,
                Material.NETHERITE_SHOVEL, Material.DIAMOND_SHOVEL, Material.IRON_SHOVEL, Material.GOLDEN_SHOVEL, Material.STONE_SHOVEL, Material.WOODEN_SHOVEL,
                Material.NETHERITE_HOE, Material.DIAMOND_HOE, Material.IRON_HOE, Material.GOLDEN_HOE, Material.STONE_HOE, Material.WOODEN_HOE,
                Material.FISHING_ROD, Material.SHEARS
        };
        for (Material m : tools) {
            registerVanillaTemplate(m, ItemCategory.TOOL, ScrollType.MASTERY);
        }
    }

    private void registerVanillaTemplate(Material mat, ItemCategory category, ScrollType... sockets) {
        String id = "default_" + mat.name().toLowerCase();
        if (!items.containsKey(id)) {
            String name = formatMaterialName(mat);
            DCEItem dce = new DCEItem(id, 1, mat, name, ItemRarity.COMMON, category);
            for (ScrollType st : sockets) {
                dce.addAllowedSocket(st);
            }
            putItemSync(id, dce, "vanilla:" + id, false);
        }
    }

    public List<DCEItem> getItemsByCategory(ItemCategory category) {
        List<DCEItem> list = new ArrayList<>();
        for (DCEItem it : items.values()) {
            boolean isVanilla = it.getId().startsWith("default_");
            if (category == ItemCategory.VANILLA) {
                if (isVanilla) list.add(it);
            } else {
                if (isVanilla) continue;
                if (it.getCategory() == category) list.add(it);
            }
        }
        return list;
    }

    public List<DCEItem> getItemsByCategoryAndRarity(ItemCategory category, ItemRarity rarity, String filterMode) {
        List<DCEItem> list = new ArrayList<>();
        for (DCEItem it : items.values()) {
            boolean isVanilla = it.getId().startsWith("default_");
            if (category == ItemCategory.VANILLA) {
                if (isVanilla && it.getRarity() == rarity) list.add(it);
                continue;
            }
            if (it.getCategory() != category || it.getRarity() != rarity) continue;

            if ("CUSTOM_ONLY".equalsIgnoreCase(filterMode) && isVanilla) continue;
            if ("VANILLA_ONLY".equalsIgnoreCase(filterMode) && !isVanilla) continue;
            list.add(it);
        }
        return list;
    }

    public List<DCEItem> getItemsByCategoryAndRarity(ItemCategory category, ItemRarity rarity) {
        return getItemsByCategoryAndRarity(category, rarity, "ALL");
    }

    public int getItemCount(ItemCategory category) {
        int count = 0;
        for (DCEItem it : items.values()) {
            boolean isVanilla = it.getId().startsWith("default_");
            if (category == ItemCategory.VANILLA) {
                if (isVanilla) count++;
            } else {
                if (isVanilla) continue;
                if (it.getCategory() == category) count++;
            }
        }
        return count;
    }

    public int getItemCount(ItemCategory category, ItemRarity rarity) {
        int count = 0;
        for (DCEItem it : items.values()) {
            boolean isVanilla = it.getId().startsWith("default_");
            if (category == ItemCategory.VANILLA) {
                if (isVanilla && it.getRarity() == rarity) count++;
            } else {
                if (isVanilla) continue;
                if (it.getCategory() == category && it.getRarity() == rarity) count++;
            }
        }
        return count;
    }

    public ItemStack createItemStack(String id, int amount) {
        DCEItem dceItem = getItem(id);
        if (dceItem == null) return null;

        // If this is a cosmetic scroll, build directly via DCCosmetics
        if (dceItem.getCategory() == ItemCategory.COSMETIC_SCROLL) {
            ItemStack cos = DCCosmetics.getInstance().createScroll(dceItem.getId(), null);
            if (cos != null) {
                cos.setAmount(Math.max(1, amount));
                return cos;
            }
        }

        // If this is a scroll with a pre-rendered template, return a clone directly
        if (dceItem.getCategory() == ItemCategory.SCROLL && dceItem.getTemplate() != null) {
            ItemStack scrollStack = dceItem.getTemplate().clone();
            scrollStack.setAmount(Math.max(1, amount));
            return scrollStack;
        }

        ItemStack stack = null;
        if (dceItem.getExecutableItemId() != null && ExecutableItemsHook.isAvailable()) {
            stack = ExecutableItemsHook.buildExecutableItem(dceItem.getExecutableItemId(), amount);
        }
        if (stack == null && dceItem.getTemplate() != null) {
            stack = dceItem.getTemplate().clone();
            stack.setAmount(Math.max(1, amount));
        }
        if (stack == null && dceItem.getMaterial() == Material.PLAYER_HEAD && dceItem.getHeadTexture() != null && !dceItem.getHeadTexture().isEmpty()) {
            stack = dc.dccosmetics.util.HeadUtil.getCustomHead(dceItem.getHeadTexture());
            stack.setAmount(Math.max(1, amount));
        }
        if (stack == null) {
            stack = new ItemStack(dceItem.getMaterial(), Math.max(1, amount));
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ITEM_ID_KEY, PersistentDataType.STRING, dceItem.getId());
            pdc.set(VERSION_KEY, PersistentDataType.INTEGER, dceItem.getVersion());
            if (!isStackableCategory(dceItem.getCategory())) {
                pdc.set(UUID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
            }
            pdc.set(CATEGORY_KEY, PersistentDataType.STRING, dceItem.getCategory().name());
            pdc.set(RARITY_KEY, PersistentDataType.STRING, dceItem.getRarity().name());

            if (dceItem.getEquipmentSlot() != null) {
                pdc.set(EQUIPMENT_SLOT_KEY, PersistentDataType.STRING, dceItem.getEquipmentSlot());
            }

            if (dceItem.getHeadTexture() != null && !dceItem.getHeadTexture().isEmpty()) {
                pdc.set(new NamespacedKey(DCCosmetics.getInstance(), "dce_head_tex"),
                        PersistentDataType.STRING,
                        Integer.toHexString(dceItem.getHeadTexture().hashCode()));
            }

            stack.setItemMeta(meta);
        }

        TooltipRenderer.renderItem(dceItem, stack, scrollRegistry);
        return stack;
    }

    public boolean isDCEItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(ITEM_ID_KEY, PersistentDataType.STRING)
                || pdc.has(dc.dcessentials.modules.items.scroll.ScrollRegistry.SCROLL_ID_KEY, PersistentDataType.STRING)
                || pdc.has(new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_scroll_id"), PersistentDataType.STRING);
    }

    public String getItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(ITEM_ID_KEY, PersistentDataType.STRING)) {
            return pdc.get(ITEM_ID_KEY, PersistentDataType.STRING);
        }
        if (pdc.has(dc.dcessentials.modules.items.scroll.ScrollRegistry.SCROLL_ID_KEY, PersistentDataType.STRING)) {
            return pdc.get(dc.dcessentials.modules.items.scroll.ScrollRegistry.SCROLL_ID_KEY, PersistentDataType.STRING);
        }
        NamespacedKey cosKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_scroll_id");
        if (pdc.has(cosKey, PersistentDataType.STRING)) {
            return pdc.get(cosKey, PersistentDataType.STRING);
        }
        return null;
    }

    public int getItemVersion(ItemStack item) {
        if (!isDCEItem(item)) return 0;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(VERSION_KEY, PersistentDataType.INTEGER);
        return v != null ? v : 0;
    }

    public static boolean isStackableCategory(ItemCategory cat) {
        return cat == ItemCategory.ITEM
                || cat == ItemCategory.INGREDIENT
                || cat == ItemCategory.VOUCHER
                || cat == ItemCategory.SCROLL
                || cat == ItemCategory.COSMETIC_SCROLL;
    }

    public boolean isStackableItem(ItemStack item) {
        String id = getItemId(item);
        DCEItem dce = id != null ? getItem(id) : null;
        if (dce != null) {
            return isStackableCategory(dce.getCategory());
        }
        if (item != null && item.hasItemMeta()) {
            String catName = item.getItemMeta().getPersistentDataContainer().get(CATEGORY_KEY, PersistentDataType.STRING);
            if (catName != null) {
                return isStackableCategory(ItemCategory.fromString(catName));
            }
        }
        return false;
    }

    private static List<ItemPerk> parsePerkList(List<Map<?, ?>> rawList) {
        List<ItemPerk> list = new ArrayList<>();
        if (rawList == null) return list;
        for (Map<?, ?> map : rawList) {
            if (map == null) continue;
            Object effObj = map.get("effect");
            if (effObj == null) effObj = map.get("type");
            PetPerkEffectType effect = PetPerkEffectType.fromString(effObj != null ? String.valueOf(effObj) : null);
            if (effect == PetPerkEffectType.NONE) continue;
            String name = map.get("name") != null ? String.valueOf(map.get("name")) : effect.name();
            double value = 0.0;
            Object valObj = map.get("value");
            if (valObj instanceof Number n) {
                value = n.doubleValue();
            } else if (valObj != null) {
                try { value = Double.parseDouble(String.valueOf(valObj)); } catch (NumberFormatException ignored) {}
            }
            String desc = map.get("description") != null ? String.valueOf(map.get("description")) : "";
            list.add(new ItemPerk(name, effect, value, desc));
        }
        return list;
    }

    private static List<Map<String, Object>> serializePerkList(List<ItemPerk> perks) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ItemPerk p : perks) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.name());
            m.put("effect", p.effectType().name());
            m.put("value", p.value());
            if (p.description() != null && !p.description().isEmpty()) {
                m.put("description", p.description());
            }
            out.add(m);
        }
        return out;
    }

    /**
     * Zero-Reset Live-Sync Engine:
     * Seamlessly upgrades player item to latest definition while 100% preserving:
     * - Applied cosmetic skins and colors
     * - Applied combat augments (Emperor Slayer, Chrono-Slip)
     * - Applied keystone and mastery scrolls
     * - Custom enchantments
     */
    public boolean updateItemToLatest(ItemStack item) {
        if (!isDCEItem(item)) return false;
        String id = getItemId(item);
        DCEItem master = getItem(id);
        if (master == null) return false;

        int currentVer = getItemVersion(item);
        NamespacedKey headTexKey = new NamespacedKey(DCCosmetics.getInstance(), "dce_head_tex");
        String expectedTexHash = (master.getMaterial() == Material.PLAYER_HEAD
                && master.getHeadTexture() != null
                && !master.getHeadTexture().isEmpty())
                ? Integer.toHexString(master.getHeadTexture().hashCode())
                : null;
        String currentTexHash = item.hasItemMeta()
                ? item.getItemMeta().getPersistentDataContainer().get(headTexKey, PersistentDataType.STRING)
                : null;
        boolean headNeedsUpdate = expectedTexHash != null && !expectedTexHash.equals(currentTexHash);

        boolean stackable = isStackableCategory(master.getCategory());
        boolean hasStaleUniqueKeys = false;
        if (stackable && item.hasItemMeta()) {
            PersistentDataContainer chkPdc = item.getItemMeta().getPersistentDataContainer();
            hasStaleUniqueKeys = chkPdc.has(UUID_KEY, PersistentDataType.STRING)
                    || chkPdc.has(ItemTrackerManager.INSTANCE_UUID_KEY, PersistentDataType.STRING)
                    || chkPdc.has(ItemTrackerManager.LAST_USER_UUID_KEY, PersistentDataType.STRING)
                    || chkPdc.has(ItemTrackerManager.LAST_USER_NAME_KEY, PersistentDataType.STRING);
        }

        if (currentVer >= master.getVersion() && !headNeedsUpdate && !hasStaleUniqueKeys) return false; // Already up to date

        // 1. Material Hot-Swap
        if (item.getType() != master.getMaterial()) {
            item.setType(master.getMaterial());
        }

        // Apply deterministic custom skull texture if master is PLAYER_HEAD with texture
        if (expectedTexHash != null) {
            ItemStack freshHead = dc.dccosmetics.util.HeadUtil.getCustomHead(master.getHeadTexture());
            if (freshHead != null && freshHead.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta freshSkull
                    && item.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta targetSkull) {
                targetSkull.setOwnerProfile(freshSkull.getOwnerProfile());
                item.setItemMeta(targetSkull);
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(VERSION_KEY, PersistentDataType.INTEGER, master.getVersion());
            pdc.set(CATEGORY_KEY, PersistentDataType.STRING, master.getCategory().name());
            pdc.set(RARITY_KEY, PersistentDataType.STRING, master.getRarity().name());
            if (stackable) {
                pdc.remove(UUID_KEY);
                pdc.remove(ItemTrackerManager.INSTANCE_UUID_KEY);
                pdc.remove(ItemTrackerManager.LAST_USER_UUID_KEY);
                pdc.remove(ItemTrackerManager.LAST_USER_NAME_KEY);
            }
            if (expectedTexHash != null) {
                pdc.set(headTexKey, PersistentDataType.STRING, expectedTexHash);
            }

            if (master.getCustomModelData() != null) {
                meta.setCustomModelData(master.getCustomModelData());
            }

            item.setItemMeta(meta);
        }

        // 2. Re-render Tooltip
        TooltipRenderer.renderItem(master, item, scrollRegistry);
        return true;
    }

    public void registerRuntimeItem(DCEItem dceItem) {
        if (dceItem != null && dceItem.getId() != null) {
            putItemSync(dceItem.getId(), dceItem, "runtime:" + dceItem.getId(), true);
        }
    }
}
