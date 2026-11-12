package dc.dccosmetics.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.ActiveCosmetic;
import dc.dccosmetics.model.CosmeticTemplate;
import dc.dccosmetics.model.PlayerProfile;
import dc.dccosmetics.util.HeadUtil;
import dc.dcessentials.core.gui.DceGui;
import dc.dcessentials.modules.items.scroll.ScrollModel;
import dc.dcessentials.modules.teleportation.KeystoneDetector;
import dc.dcessentials.modules.teleportation.KeystoneLoreTokens;
import dc.dcessentials.modules.teleportation.KeystoneProfile;
import dc.dcessentials.modules.teleportation.KeystoneTier;
import dc.dcessentials.modules.teleportation.LegacyKeystoneConverter;
import dc.dcessentials.modules.teleportation.gui.KeystoneMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class ProfileGUI implements DceGui {
    private final Player viewer;
    private final OfflinePlayer target;
    private final PlayerProfile targetProfile;
    private final boolean isSelf;
    private final Inventory inventory;

    // Open sliding drawers: "head", "chest", "waist", "boots"
    private final Set<String> openDrawers = new HashSet<>();

    // Column 1 Armor Slots -> Internal Slot Names (10 -> head, 19 -> chest, 28 -> waist, 37 -> boots)
    private final Map<Integer, String> armorDrawerMap = new HashMap<>();

    // Internal Slot Names -> Column 0 Drawer Slots (head -> 9, chest -> 18, waist -> 27, boots -> 36)
    private final Map<String, Integer> drawerSlotMap = new HashMap<>();
    private final Map<Integer, String> drawerReverseMap = new HashMap<>();

    // Column 2 Equipment Storage Slots (11 -> keystone, 20 -> amulet, 29 -> cloak, 38 -> gloves)
    private final Map<Integer, String> equipmentSlotMap = new HashMap<>();

    // Maps Inventory Slots -> Internal Cosmetic Slot Names (legacy/direct fallback)
    private final Map<Integer, String> cosmeticSlotMap = new HashMap<>();

    // Maps Inventory Slots -> List of Commands to execute
    private final Map<Integer, List<String>> leftClickActions = new HashMap<>();
    private final Map<Integer, List<String>> rightClickActions = new HashMap<>();

    public ProfileGUI(Player viewer, OfflinePlayer target) {
        this.viewer = viewer;
        this.target = target;
        this.isSelf = viewer.getUniqueId().equals(target.getUniqueId());
        this.targetProfile = DCCosmetics.getInstance().getProfileManager().getOfflineProfile(target);

        // Setup default column slot associations
        initSlotMappings();

        YamlConfiguration config = DCCosmetics.getInstance().getProfileConfig();
        String rawTitle = config.getString("title", "&8{target}'s Profile");
        String title = formatText(rawTitle);
        int size = config.getInt("size", 54);

        this.inventory = Bukkit.createInventory(this, size, title);
        build(config);
    }

    private void initSlotMappings() {
        // Armor pieces in Column 1
        armorDrawerMap.put(10, "head");
        armorDrawerMap.put(19, "chest");
        armorDrawerMap.put(28, "waist");
        armorDrawerMap.put(37, "boots");

        // Drawer sliding slots in Column 0
        drawerSlotMap.put("head", 9);
        drawerSlotMap.put("chest", 18);
        drawerSlotMap.put("waist", 27);
        drawerSlotMap.put("boots", 36);

        drawerReverseMap.put(9, "head");
        drawerReverseMap.put(18, "chest");
        drawerReverseMap.put(27, "waist");
        drawerReverseMap.put(36, "boots");

        // Equipment storage slots in Column 2
        equipmentSlotMap.put(11, "keystone");
        equipmentSlotMap.put(20, "amulet");
        equipmentSlotMap.put(29, "cloak");
        equipmentSlotMap.put(38, "gloves");
    }

    private void build(YamlConfiguration config) {
        // 1. Fill Background
        if (config.getBoolean("fillItems.enabled", true)) {
            Material fillMat = Material.valueOf(config.getString("fillItems.material", "BLACK_STAINED_GLASS_PANE"));
            ItemStack filler = createItem(fillMat, formatText(config.getString("fillItems.name", " ")), new ArrayList<>());
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, filler);
            }
        }

        // 2. Load custom equipment slot overrides from config if present
        ConfigurationSection eqSec = config.getConfigurationSection("equipment");
        if (eqSec != null) {
            for (String key : eqSec.getKeys(false)) {
                int slot = eqSec.getInt(key + ".slot", -1);
                if (slot >= 0) {
                    equipmentSlotMap.put(slot, key.toLowerCase());
                }
            }
        }

        // 3. Render Equipment Slots (Column 2)
        for (Map.Entry<Integer, String> entry : equipmentSlotMap.entrySet()) {
            renderEquipmentSlot(entry.getKey(), entry.getValue(), config);
        }

        // 4. Render Closed Drawer Slots (Column 0)
        for (String slotType : drawerSlotMap.keySet()) {
            renderDrawerSlot(slotType, config);
        }

        // 5. Parse General Items (including armor slots in Column 1)
        ConfigurationSection itemsSec = config.getConfigurationSection("items");
        if (itemsSec == null) return;

        for (String key : itemsSec.getKeys(false)) {
            ConfigurationSection itemConfig = itemsSec.getConfigurationSection(key);
            if (itemConfig == null) continue;

            // Check visibility permissions
            boolean onlyOwner = itemConfig.getBoolean("onlyOwner", false);
            boolean onlyVisitor = itemConfig.getBoolean("onlyVisitor", false);
            if (onlyOwner && !isSelf) continue;
            if (onlyVisitor && isSelf) continue;

            List<Integer> slots = itemConfig.getIntegerList("slots");

            // Is this an armor or hand equipment display slot?
            String playerEquipType = null;
            String mappedSlotType = null;
            if (itemConfig.contains("type")) {
                String rawType = itemConfig.getString("type").toUpperCase();
                switch (rawType) {
                    case "PLAYER_HELMET": playerEquipType = "HELMET"; break;
                    case "PLAYER_CHESTPLATE": playerEquipType = "CHESTPLATE"; break;
                    case "PLAYER_LEGGINGS": playerEquipType = "LEGGINGS"; break;
                    case "PLAYER_BOOTS": playerEquipType = "BOOTS"; break;
                    case "PLAYER_MAIN_HAND": playerEquipType = "MAIN_HAND"; break;
                    case "PLAYER_OFF_HAND": playerEquipType = "OFF_HAND"; break;
                    case "HELMET": mappedSlotType = "head"; break;
                    case "CHESTPLATE": mappedSlotType = "chest"; break;
                    case "LEGGINGS": mappedSlotType = "waist"; break;
                    case "BOOTS": mappedSlotType = "boots"; break;
                }
            }

            for (int slot : slots) {
                if (slot >= inventory.getSize()) continue;

                // Don't overwrite Column 2 equipment slots with generic items
                if (equipmentSlotMap.containsKey(slot)) continue;

                if (playerEquipType != null) {
                    renderPlayerEquipmentSlot(slot, playerEquipType, itemConfig);
                } else if (mappedSlotType != null) {
                    cosmeticSlotMap.put(slot, mappedSlotType);
                    renderCosmeticSlot(slot, mappedSlotType, itemConfig);
                } else {
                    renderStandardItem(slot, itemConfig);
                }

                // Register Actions
                if (itemConfig.contains("leftClickCommands")) {
                    leftClickActions.put(slot, itemConfig.getStringList("leftClickCommands"));
                }
                if (itemConfig.contains("rightClickCommands")) {
                    rightClickActions.put(slot, itemConfig.getStringList("rightClickCommands"));
                }
            }
        }
    }

    public void renderDrawerSlot(String slotType, YamlConfiguration config) {
        Integer drawerSlot = drawerSlotMap.get(slotType);
        if (drawerSlot == null || drawerSlot >= inventory.getSize()) return;

        if (!openDrawers.contains(slotType)) {
            // Closed: restore background filler item
            if (config.getBoolean("fillItems.enabled", true)) {
                Material fillMat = Material.valueOf(config.getString("fillItems.material", "BLACK_STAINED_GLASS_PANE"));
                inventory.setItem(drawerSlot, createItem(fillMat, formatText(config.getString("fillItems.name", " ")), new ArrayList<>()));
            } else {
                inventory.setItem(drawerSlot, new ItemStack(Material.AIR));
            }
            return;
        }

        // Open: check if player's armor has a cosmetic scroll applied to it!
        ItemStack armor = getTargetArmorItem(slotType);
        String cosmeticId = null;
        String cosmeticColor = null;

        if (armor != null && armor.hasItemMeta()) {
            PersistentDataContainer pdc = armor.getItemMeta().getPersistentDataContainer();
            NamespacedKey idKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_id");
            NamespacedKey colKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_color");
            if (pdc.has(idKey, PersistentDataType.STRING)) {
                cosmeticId = pdc.get(idKey, PersistentDataType.STRING);
                cosmeticColor = pdc.has(colKey, PersistentDataType.STRING) ? pdc.get(colKey, PersistentDataType.STRING) : "#FFFFFF";
            }
        }

        // Fallback to active holographic cosmetic on target
        if (cosmeticId == null) {
            ActiveCosmetic active = targetProfile.getActiveCosmetic(slotType);
            if (active != null) {
                cosmeticId = active.getTemplate().getId();
                cosmeticColor = active.getColorHex();
            }
        }

        if (cosmeticId != null) {
            CosmeticTemplate template = DCCosmetics.getInstance().getTemplateRegistry().getTemplate(cosmeticId);
            if (template != null) {
                String hexColor = cosmeticColor != null ? cosmeticColor : "#FFFFFF";
                String colorPrefix = "";
                try {
                    colorPrefix = net.md_5.bungee.api.ChatColor.of(hexColor).toString();
                } catch (Exception ignored) {}

                ItemStack item = HeadUtil.getCustomHead(template.getGuiIconBase64());
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(colorPrefix + ChatColor.BOLD + template.getItemName());

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Rarity: " + ChatColor.WHITE + (template.getRarity() != null ? template.getRarity() : "Common"));
                lore.add(ChatColor.GRAY + "Color: " + colorPrefix + hexColor);
                lore.add("");
                lore.add(ChatColor.YELLOW + "Left-Click to customize or detach!");
                lore.add(ChatColor.DARK_GRAY + "Click armor piece again to close drawer.");
                meta.setLore(lore);
                item.setItemMeta(meta);

                inventory.setItem(drawerSlot, item);
                return;
            }
        }

        // No cosmetic attached: render empty drawer item
        ConfigurationSection emptyDrawerSec = config.getConfigurationSection("drawer.emptyItem");
        if (emptyDrawerSec != null) {
            renderStandardItem(drawerSlot, emptyDrawerSec);
        } else {
            ItemStack emptyItem = createItem(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "No Cosmetic Attached",
                    List.of(ChatColor.GRAY + "No cosmetic scroll is attached to this armor piece.", "", ChatColor.DARK_GRAY + "Apply a cosmetic scroll in your inventory to see it here!"));
            inventory.setItem(drawerSlot, emptyItem);
        }
    }

    public void renderEquipmentSlot(int slot, String equipKey, YamlConfiguration config) {
        ItemStack equipped = targetProfile.getEquipment(equipKey);
        if (equipped != null && equipped.getType() != Material.AIR) {
            ItemStack display = equipped.clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                if ("keystone".equalsIgnoreCase(equipKey)) {
                    var teleMgr = DCCosmetics.getInstance().getTeleportationManager();
                    Player onlineTarget = target.isOnline() ? target.getPlayer() : null;
                    KeystoneProfile kProfile = onlineTarget != null ? teleMgr.getProfile(onlineTarget) : null;
                    KeystoneTier itemTier = KeystoneDetector.tierOf(display);
                    int effMax = KeystoneLoreTokens.computeDisplayMax(display, onlineTarget, kProfile, itemTier);

                    // Resolve static {ks:*} tokens in the item's lore first (Task P2-08)
                    lore = KeystoneLoreTokens.resolve(lore, kProfile, effMax);

                    // Append live matrix summary lines (Task P2-07)
                    int energy = kProfile != null ? kProfile.usableEnergy() : 0;
                    int parked = kProfile != null ? kProfile.parkedEnergy() : 0;
                    int micro = kProfile != null ? kProfile.getMicro() : 0;
                    int heat = kProfile != null ? kProfile.getHeat() : 0;
                    String stability = kProfile != null ? kProfile.getStability() : "&a100% Stable";
                    String microBar = KeystoneLoreTokens.formatMicroBar(micro);
                    List<ScrollModel> sockets = KeystoneDetector.resolveSocketedScrollModels(display);

                    lore.add(formatText("&8────────────────────────"));
                    lore.add(formatText("&7Attuned Tier: " + itemTier.getDisplayName() + " &8(" + itemTier.getKeystoneName() + "&8)"));
                    lore.add(formatText("&7Energy: &f" + energy + "&7/&f" + effMax + " &#38BDF8🗲"
                            + (parked > 0 ? " &8(&e+" + parked + " Parked&8)" : "")));
                    lore.add(formatText("&7Buffer: &8[" + microBar + "&8] &#38BDF8" + micro + "&7/1000 mC"));
                    lore.add(formatText("&7Heat: &#EF4444" + heat + "&7/100 🔥 &7(" + stability + "&7)"));
                    lore.add(formatText("&7Sockets: &f" + sockets.size() + "&7/&f" + itemTier.getSockets()));
                    for (ScrollModel sm : sockets) {
                        lore.add(formatText("  &8• " + sm.getSocketDisplay()));
                    }
                    lore.add(formatText("&8────────────────────────"));
                    lore.add(formatText("&eLeft-Click: Open Keystone Matrix"));
                } else {
                    lore.add(formatText("&8────────────────────────"));
                }
                if (isSelf) {
                    lore.add(formatText("&cRight-Click: Unequip to inventory"));
                }
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inventory.setItem(slot, display);
            return;
        }

        // Empty slot placeholder from config
        ConfigurationSection eqItemSec = config.getConfigurationSection("equipment." + equipKey + ".emptyItem");
        if (eqItemSec != null) {
            renderStandardItem(slot, eqItemSec);
        } else {
            Material mat = Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            String name = formatText("&7Empty " + Character.toUpperCase(equipKey.charAt(0)) + equipKey.substring(1) + " Slot");
            inventory.setItem(slot, createItem(mat, name, List.of(formatText("&7Click with an item to equip!"))));
        }
    }

    private ItemStack getTargetArmorItem(String slotType) {
        if (!target.isOnline() || target.getPlayer() == null) return null;
        Player p = target.getPlayer();
        switch (slotType.toLowerCase()) {
            case "head": return p.getInventory().getHelmet();
            case "chest": return p.getInventory().getChestplate();
            case "waist": return p.getInventory().getLeggings();
            case "boots": return p.getInventory().getBoots();
            case "sword": return p.getInventory().getItemInMainHand();
            default: return null;
        }
    }

    private void renderPlayerEquipmentSlot(int slot, String equipType, ConfigurationSection fallbackConfig) {
        ItemStack equip = null;
        if (target.isOnline()) {
            Player p = target.getPlayer();
            if (p != null && p.getInventory() != null) {
                switch (equipType) {
                    case "HELMET": equip = p.getInventory().getHelmet(); break;
                    case "CHESTPLATE": equip = p.getInventory().getChestplate(); break;
                    case "LEGGINGS": equip = p.getInventory().getLeggings(); break;
                    case "BOOTS": equip = p.getInventory().getBoots(); break;
                    case "MAIN_HAND": equip = p.getInventory().getItemInMainHand(); break;
                    case "OFF_HAND": equip = p.getInventory().getItemInOffHand(); break;
                }
            }
        }

        if (equip != null && equip.getType() != Material.AIR) {
            ItemStack display = equip.clone();
            // Add helpful discovery lore for armor pieces
            if (armorDrawerMap.containsKey(slot)) {
                ItemMeta meta = display.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add(formatText("&f"));
                    lore.add(formatText("&eClick to slide out cosmetic drawer ◀"));
                    meta.setLore(lore);
                    display.setItemMeta(meta);
                }
            }
            inventory.setItem(slot, display);
        } else {
            renderStandardItem(slot, fallbackConfig);
        }
    }

    private void renderCosmeticSlot(int slot, String internalSlot, ConfigurationSection fallbackConfig) {
        ActiveCosmetic active = targetProfile.getActiveCosmetic(internalSlot);
        if (active != null) {
            CosmeticTemplate template = active.getTemplate();
            if (template != null) {
                String hexColor = active.getColorHex();
                String colorPrefix = "";
                try {
                    colorPrefix = net.md_5.bungee.api.ChatColor.of(hexColor).toString();
                } catch (Exception ignored) {}

                ItemStack item = HeadUtil.getCustomHead(template.getGuiIconBase64());
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(colorPrefix + ChatColor.BOLD + template.getId());

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Rarity: " + ChatColor.WHITE + template.getRarity());
                if (isSelf) {
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "Click to edit your " + internalSlot + "!");
                }
                meta.setLore(lore);
                item.setItemMeta(meta);

                inventory.setItem(slot, item);
                return;
            }
        }
        renderStandardItem(slot, fallbackConfig);
    }

    private void renderStandardItem(int slot, ConfigurationSection config) {
        String matString = config.getString("material", "STONE");
        String name = formatText(config.getString("name", "&f"));
        List<String> lore = new ArrayList<>();
        for (String l : config.getStringList("lore")) lore.add(formatText(l));

        ItemStack item;
        if (matString.startsWith("head;")) {
            item = HeadUtil.getCustomHead(matString);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        } else {
            Material mat;
            try {
                mat = Material.valueOf(matString.toUpperCase());
            } catch (Exception e) {
                mat = Material.STONE;
            }
            item = createItem(mat, name, lore);
        }
        inventory.setItem(slot, item);
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public String formatText(String text) {
        if (text == null) return "";
        text = text.replace("{target}", target.getName() != null ? target.getName() : "Unknown");
        text = text.replace("%player_name%", viewer.getName());

        // Dragoncote lore branding prefixes
        text = text.replace("{@prefix_corvus}", "§7§l<§#38BDF8C§#2EA7DFo§#2390E0r§#197AE1v§#0E63E2u§#006FE9s§7§l>§r §#BAE6FD");
        text = text.replace("{@prefix_corvus_warn}", "§7§l<§#FBBF24C§#F5B31Fa§#F0A71Ar§#EA9B15v§#E48F10u§#DF830Bs§7§l>§r §#FDE047");
        text = text.replace("{@prefix_corvus_danger}", "§7§l<§#EF4444C§#DA3A3Ao§#C43030r§#AF2525v§#991B1Bu§#831212s§7§l>§r §#FCA5A5");

        // 1. Soft-depend PlaceholderAPI parsing FIRST
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                java.lang.reflect.Method setPlaceholders = papiClass.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
                text = (String) setPlaceholders.invoke(null, target, text);
            } catch (Exception ignored) {}
        }

        // 2. Parse all hex and standard colors using universal ColorUtil
        text = dc.dcessentials.core.util.ColorUtil.colorize(text);

        // 3. Handle pure empty clearing
        if (ChatColor.stripColor(text).trim().isEmpty()) {
            text = " ";
        }

        return text;
    }

    public void handleClick(Player clicker, int slot, ClickType clickType) {
        YamlConfiguration config = DCCosmetics.getInstance().getProfileConfig();

        // 1. Clicked an Armor Piece in Column 1 (10, 19, 28, 37) -> Toggle Drawer!
        if (armorDrawerMap.containsKey(slot)) {
            String slotType = armorDrawerMap.get(slot);
            if (openDrawers.contains(slotType)) {
                openDrawers.remove(slotType);
                clicker.playSound(clicker.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.8f, 1.4f);
            } else {
                openDrawers.add(slotType);
                clicker.playSound(clicker.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.8f, 1.6f);
            }
            renderDrawerSlot(slotType, config);
            return;
        }

        // 2. Clicked a Drawer Slot in Column 0 (9, 18, 27, 36) -> Customise Attached Cosmetic!
        if (drawerReverseMap.containsKey(slot)) {
            String slotType = drawerReverseMap.get(slot);
            if (openDrawers.contains(slotType)) {
                if (isSelf) {
                    ItemStack armor = getTargetArmorItem(slotType);
                    if (armor != null && armor.hasItemMeta()) {
                        NamespacedKey idKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_id");
                        if (armor.getItemMeta().getPersistentDataContainer().has(idKey, PersistentDataType.STRING)) {
                            String cosmeticId = armor.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
                            CosmeticTemplate template = DCCosmetics.getInstance().getTemplateRegistry().getTemplate(cosmeticId);
                            if (template != null) {
                                clicker.closeInventory();
                                DCCosmetics.getInstance().getGuiManager().openCustomiseMenu(clicker, template, armor);
                                return;
                            }
                        }
                    }
                    clicker.sendMessage(formatText("{@prefix_corvus}&cNo cosmetic scroll is attached to this armor piece."));
                    clicker.playSound(clicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
            return;
        }

        // 3. Clicked an Equipment Storage Slot in Column 2 (11, 20, 29, 38)
        if (equipmentSlotMap.containsKey(slot)) {
            if (!isSelf) return; // Read-only for visitors

            String equipKey = equipmentSlotMap.get(slot);
            ItemStack equipped = targetProfile.getEquipment(equipKey);

            if (equipped != null && equipped.getType() != Material.AIR) {
                ItemStack cursor = clicker.getItemOnCursor();
                // Support direct Keystone swap when clicking an occupied Slot 11 with another Keystone on cursor
                if ("keystone".equalsIgnoreCase(equipKey) && cursor != null && !cursor.getType().isAir()) {
                    String newTier = detectKeystoneTier(cursor);
                    if (newTier != null) {
                        ItemStack toEquip = cursor.clone();
                        toEquip.setAmount(1);
                        toEquip = LegacyKeystoneConverter.convert(toEquip).orElse(toEquip);
                        if (cursor.getAmount() > 1) {
                            cursor.setAmount(cursor.getAmount() - 1);
                            clicker.setItemOnCursor(cursor);
                        } else {
                            clicker.setItemOnCursor(new ItemStack(Material.AIR));
                        }

                        targetProfile.setEquipment("keystone", toEquip);
                        DCCosmetics.getInstance().getProfileManager().saveProfile(clicker);

                        Map<Integer, ItemStack> leftover = clicker.getInventory().addItem(equipped);
                        for (ItemStack drop : leftover.values()) {
                            clicker.getWorld().dropItemNaturally(clicker.getLocation(), drop);
                        }

                        applyKeystoneStats(clicker, newTier);
                        renderEquipmentSlot(slot, "keystone", config);
                        return;
                    }
                }

                if (clickType.isRightClick()) {
                    // UNEQUIP ACTION (P1-03 / X1: never wipes energy; energy stays parked)
                    targetProfile.setEquipment(equipKey, null);
                    DCCosmetics.getInstance().getProfileManager().saveProfile(clicker);

                    if ("keystone".equalsIgnoreCase(equipKey)) {
                        var teleMgr = DCCosmetics.getInstance().getTeleportationManager();
                        var kProfile = teleMgr.getProfile(clicker);
                        kProfile.setTier(KeystoneTier.NONE);
                        teleMgr.getPersistence().saveNow(kProfile);
                        KeystoneLoreTokens.scheduleInventoryRefresh(clicker);
                        clicker.sendMessage(dc.dcessentials.core.util.DCMessage.warning("Keystone unequipped and returned to your inventory (stored energy remains safely parked)."));
                        clicker.sendMessage(dc.dcessentials.core.util.DCMessage.error("Warning: Your Keystone matrix is now unattuned! Teleporting will run in Overdraw (Heat)."));
                        clicker.playSound(clicker.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1.0f);
                    } else {
                        clicker.playSound(clicker.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 1.0f);
                    }
                    invalidatePlayerStats(clicker);

                    // Return item to player inventory or drop
                    Map<Integer, ItemStack> leftover = clicker.getInventory().addItem(equipped);
                    for (ItemStack drop : leftover.values()) {
                        clicker.getWorld().dropItemNaturally(clicker.getLocation(), drop);
                    }
                    renderEquipmentSlot(slot, equipKey, config);
                    return;
                } else if (clickType.isLeftClick()) {
                    if ("keystone".equalsIgnoreCase(equipKey)) {
                        String tier = detectKeystoneTier(equipped);
                        if (tier == null) {
                            // Invalid item was previously stuck in keystone slot: unequip it back to player
                            targetProfile.setEquipment("keystone", null);
                            DCCosmetics.getInstance().getProfileManager().saveProfile(clicker);
                            Map<Integer, ItemStack> leftover = clicker.getInventory().addItem(equipped);
                            for (ItemStack drop : leftover.values()) {
                                clicker.getWorld().dropItemNaturally(clicker.getLocation(), drop);
                            }
                            renderEquipmentSlot(slot, "keystone", config);
                            return;
                        }
                        clicker.closeInventory();
                        KeystoneMenu.open(clicker);
                        return;
                    }
                }
            } else {
                // EMPTY SLOT: Check cursor for equipping, or auto-equip
                ItemStack cursor = clicker.getItemOnCursor();
                if (cursor != null && cursor.getType() != Material.AIR) {
                    if ("keystone".equalsIgnoreCase(equipKey)) {
                        String tier = detectKeystoneTier(cursor);
                        if (tier == null) {
                            clicker.sendMessage(dc.dcessentials.core.util.DCMessage.error("Only Keystones can be equipped in this slot!"));
                            clicker.playSound(clicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            return;
                        }

                        ItemStack toEquip = cursor.clone();
                        toEquip.setAmount(1);
                        toEquip = dc.dcessentials.modules.teleportation.LegacyKeystoneConverter.convert(toEquip).orElse(toEquip);
                        if (cursor.getAmount() > 1) {
                            cursor.setAmount(cursor.getAmount() - 1);
                            clicker.setItemOnCursor(cursor);
                        } else {
                            clicker.setItemOnCursor(new ItemStack(Material.AIR));
                        }

                        targetProfile.setEquipment("keystone", toEquip);
                        DCCosmetics.getInstance().getProfileManager().saveProfile(clicker);
                        applyKeystoneStats(clicker, tier);
                        renderEquipmentSlot(slot, "keystone", config);
                        return;
                    } else {
                        // Validate accessory type (amulet, cloak, gloves)
                        if (!isValidAccessory(cursor, equipKey)) {
                            String prettySlot = Character.toUpperCase(equipKey.charAt(0)) + equipKey.substring(1).toLowerCase();
                            clicker.sendMessage(dc.dcessentials.core.util.DCMessage.error("Only a valid " + prettySlot + " can be equipped in this slot!"));
                            clicker.playSound(clicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            return;
                        }

                        ItemStack toEquip = cursor.clone();
                        toEquip.setAmount(1);
                        if (cursor.getAmount() > 1) {
                            cursor.setAmount(cursor.getAmount() - 1);
                            clicker.setItemOnCursor(cursor);
                        } else {
                            clicker.setItemOnCursor(new ItemStack(Material.AIR));
                        }

                        targetProfile.setEquipment(equipKey, toEquip);
                        DCCosmetics.getInstance().getProfileManager().saveProfile(clicker);
                        invalidatePlayerStats(clicker);
                        clicker.playSound(clicker.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 1.0f);
                        renderEquipmentSlot(slot, equipKey, config);
                        return;
                    }
                } else {
                    // Empty cursor: Auto-equip Keystone from inventory
                    if ("keystone".equalsIgnoreCase(equipKey)) {
                        ItemStack found = findHighestKeystoneInInventory(clicker);
                        if (found != null) {
                            String tier = detectKeystoneTier(found);
                            ItemStack toEquip = found.clone();
                            toEquip.setAmount(1);
                            toEquip = dc.dcessentials.modules.teleportation.LegacyKeystoneConverter.convert(toEquip).orElse(toEquip);

                            if (found.getAmount() > 1) {
                                found.setAmount(found.getAmount() - 1);
                            } else {
                                clicker.getInventory().removeItem(found);
                            }

                            targetProfile.setEquipment("keystone", toEquip);
                            DCCosmetics.getInstance().getProfileManager().saveProfile(clicker);
                            applyKeystoneStats(clicker, tier);
                            renderEquipmentSlot(slot, "keystone", config);
                        } else {
                            clicker.sendMessage(dc.dcessentials.core.util.DCMessage.error("No Keystone found in your inventory! Obtain one from Kristoph at Spawn."));
                            clicker.playSound(clicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        }
                        return;
                    }
                }
            }
            return;
        }

        // 4. Legacy/Direct Cosmetic Slot Click
        if (cosmeticSlotMap.containsKey(slot)) {
            if (isSelf) {
                clicker.closeInventory();
                GuiState state = DCCosmetics.getInstance().getGuiManager().getState(clicker);
                state.setSelectedType(cosmeticSlotMap.get(slot));
                state.setViewedCosmeticId(null);
                DCCosmetics.getInstance().getGuiManager().openCosmeticsMenu(clicker);
            }
            return;
        }

        // 5. Configured Action Commands (Teleport, Trade, Auctions, Back, etc.)
        List<String> commands = (clickType.isLeftClick()) ? leftClickActions.get(slot) : rightClickActions.get(slot);
        if (commands != null) {
            for (String cmd : commands) {
                String formattedCmd = formatText(cmd);
                if (formattedCmd.startsWith("[CLOSE]")) { clicker.closeInventory(); }
                else if (formattedCmd.startsWith("[PLAYER] ")) { clicker.performCommand(formattedCmd.substring(9)); }
                else if (formattedCmd.startsWith("[CONSOLE] ")) { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCmd.substring(10)); }
            }
        }
    }

    private boolean isValidAccessory(ItemStack item, String equipKey) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        String keyLower = equipKey.toLowerCase();

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (NamespacedKey k : pdc.getKeys()) {
            String val = pdc.has(k, PersistentDataType.STRING) ? pdc.get(k, PersistentDataType.STRING) : null;
            if (val != null && val.toLowerCase().contains(keyLower)) {
                return true;
            }
        }

        if (meta.hasDisplayName()) {
            String stripped = ChatColor.stripColor(meta.getDisplayName()).toLowerCase();
            if (stripped.contains(keyLower)) return true;
        }
        return false;
    }

    public String detectKeystoneTier(ItemStack item) {
        var tier = KeystoneDetector.tierOf(item);
        return tier == KeystoneTier.NONE ? null : tier.name();
    }

    private void applyKeystoneStats(Player player, String tier) {
        int max = 200;
        String tierName = "&#0098FF&lTesseract Keystone";
        if ("LEGENDARY".equalsIgnoreCase(tier)) {
            max = 600;
            tierName = "&#FFD700&lLegendary Keystone";
        } else if ("EPIC".equalsIgnoreCase(tier)) {
            max = 400;
            tierName = "&#8A00FF&lEpic Keystone";
        }

        String finalTier = tier != null ? tier.toUpperCase() : "RARE";
        var kTier = dc.dcessentials.modules.teleportation.KeystoneTier.fromString(finalTier);
        var teleMgr = DCCosmetics.getInstance().getTeleportationManager();
        var kProfile = teleMgr.getProfile(player);
        kProfile.setTier(kTier);
        int effMax = teleMgr.getEnergyService().getEffectiveMaxEnergy(player, kProfile);
        int overflow = kProfile.trimToTier(effMax);
        if (overflow > 0) {
            teleMgr.getEnergyService().giveEnergyCubes(player, overflow);
            player.sendMessage(dc.dcessentials.core.util.DCMessage.info("Returned &#FFFFFF" + overflow + "x Tesseract Energy Cube &#BAE6FDabove your new Keystone's capacity."));
        }
        teleMgr.getPersistence().saveNow(kProfile);
        invalidatePlayerStats(player);

        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 0.8f, 1.0f);
        player.sendMessage(dc.dcessentials.core.util.DCMessage.general("Equipped " + tierName + " &#0CBF93. Max Capacity: &#38BDF8" + effMax + " 🗲&#0CBF93."));
    }

    private void invalidatePlayerStats(Player player) {
        var itemMod = DCCosmetics.getInstance().getItemModule();
        if (itemMod != null && itemMod.getStatAggregator() != null) {
            itemMod.getStatAggregator().invalidate(player);
        }
    }

    private ItemStack findHighestKeystoneInInventory(Player player) {
        ItemStack candidate = null;
        int highestPriority = 0; // 3 = Legendary, 2 = Epic, 1 = Rare

        for (ItemStack it : player.getInventory().getContents()) {
            if (it == null || it.getType().isAir()) continue;
            String tier = detectKeystoneTier(it);
            if (tier != null) {
                int prio = "LEGENDARY".equalsIgnoreCase(tier) ? 3 : ("EPIC".equalsIgnoreCase(tier) ? 2 : 1);
                if (prio > highestPriority) {
                    highestPriority = prio;
                    candidate = it;
                    if (prio == 3) break; // Maximum tier already found
                }
            }
        }
        return candidate;
    }

    public void open() { viewer.openInventory(inventory); }
    @Override public Inventory getInventory() { return inventory; }
}