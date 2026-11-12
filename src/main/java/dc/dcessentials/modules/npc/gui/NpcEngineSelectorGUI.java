package dc.dcessentials.modules.npc.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.npc.NpcRegistry;
import dc.dcessentials.modules.npc.model.NpcCharacter;
import dc.dcessentials.modules.npc.util.DragoncoteColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 54-Slot NPC Engine & Categorized 3D ModelEngine Rig Selector GUI.
 * Allows switching any Questline NPC between:
 * 1. Citizens Engine (Standard humanoid player model with per-world skinning)
 * 2. ModelEngine 3D Rigs categorized into:
 *    - Blacksmiths & Workstations
 *    - Market Vendors & Bazaar Stalls
 *    - Townsfolks & Village Life (scenes_townsfolks_vol3)
 *    - Farmers & Agronomy (Scenes Farmers)
 *    - Fishermen & Coastal Docks
 *    - HoverRides & Cyber Mounts (EC_HoverRides)
 *    - Custom Canon 3D Lore Rigs
 */
public class NpcEngineSelectorGUI implements Listener {

    private final NpcRegistry registry;
    private NpcEditorGUI npcEditorGUI;

    private final Map<UUID, String> viewingChar = new HashMap<>();
    private final Map<UUID, String> playerCategory = new HashMap<>();

    public static class ModelEntry {
        public final String modelId;
        public final String title;
        public final Material icon;
        public final String desc;

        public ModelEntry(String modelId, String title, Material icon, String desc) {
            this.modelId = modelId;
            this.title = title;
            this.icon = icon;
            this.desc = desc;
        }
    }

    private final Map<String, List<ModelEntry>> categories = new LinkedHashMap<>();

    public NpcEngineSelectorGUI(NpcRegistry registry) {
        this.registry = registry;
        registerCategories();
    }

    public void setNpcEditorGUI(NpcEditorGUI npcEditorGUI) {
        this.npcEditorGUI = npcEditorGUI;
    }

    private void registerCategories() {
        // 1. Blacksmiths & Workstations
        List<ModelEntry> blacksmiths = new ArrayList<>();
        blacksmiths.add(new ModelEntry("scene_blacksmith_1", "Blacksmith Anvil Forge", Material.ANVIL, "Heavy hammer strike workstation animation"));
        blacksmiths.add(new ModelEntry("scene_blacksmith_2", "Blacksmith Bellows", Material.FIRE_CHARGE, "Furnace bellows pumping & flame sparks"));
        blacksmiths.add(new ModelEntry("scene_blacksmith_3", "Blacksmith Grindstone", Material.GRINDSTONE, "Blade sharpening & friction sparks"));
        blacksmiths.add(new ModelEntry("scene_blacksmith_4", "Blacksmith Quench Basin", Material.WATER_BUCKET, "Water steam quench workstation"));
        blacksmiths.add(new ModelEntry("scene_blacksmith_5_interactive", "Interactive Tech Forge", Material.IRON_CHESTPLATE, "Multi-part animated workstation rig"));
        categories.put("BLACKSMITHS", blacksmiths);

        // 2. Market Vendors & Stalls
        List<ModelEntry> market = new ArrayList<>();
        market.add(new ModelEntry("scene_market_1", "Market Baker / Pastry", Material.BREAD, "Counter stand with baked goods display"));
        market.add(new ModelEntry("scene_market_2", "Produce & Fruit Vendor", Material.CARROT, "Fresh fruit crate display stand"));
        market.add(new ModelEntry("scene_market_3", "Textile & Fabric Stall", Material.WHITE_WOOL, "Draped cloth rolls & tailoring counter"));
        market.add(new ModelEntry("scene_market_4", "Apothecary & Brewer", Material.BREWING_STAND, "Bubbling potion bottles display stand"));
        market.add(new ModelEntry("scene_market_5", "Jewelry & Gem Merchant", Material.EMERALD, "Velvet tray gemstone showcase"));
        market.add(new ModelEntry("scene_market_6", "Book Binder & Scribe", Material.BOOKSHELF, "Scroll rack & manuscript desk stand"));
        market.add(new ModelEntry("scene_market_7", "Spice & Herb Merchant", Material.SWEET_BERRIES, "Aromatics & spice sacks display stand"));
        market.add(new ModelEntry("scene_market_8", "Seafood / Catch Stand", Material.COD, "Iced fish tray & hanging net rack"));
        market.add(new ModelEntry("scene_market_9", "Armorer Weapons Stand", Material.SHIELD, "Crossed swords & armor rack scene"));
        market.add(new ModelEntry("scene_market_10", "Harvest Produce Stall", Material.PUMPKIN, "Bountiful harvest crate display"));
        market.add(new ModelEntry("scene_market_11", "General Bazaar Goods", Material.CHEST, "Curio boxes & dry goods stand"));
        market.add(new ModelEntry("scene_market_12", "Mystic Relic Vendor", Material.ENDER_EYE, "Enchanted curios & rift artifacts"));
        categories.put("MARKET", market);

        // 3. Townsfolks & Village Life (scenes_townsfolks_vol3)
        List<ModelEntry> townsfolk = new ArrayList<>();
        townsfolk.add(new ModelEntry("scene_townsfolk_vol3", "Townsfolk Citizen", Material.LEATHER_CHESTPLATE, "Animated strolling / idle citizen"));
        townsfolk.add(new ModelEntry("scene_townsfolk_vol3_beggar", "Street Beggar", Material.BOWL, "Alms collection plate animation"));
        townsfolk.add(new ModelEntry("scene_townsfolk_vol3_mug", "Tavern Patron", Material.POTION, "Toasting tavern mug & drink animation"));
        townsfolk.add(new ModelEntry("scene_townsfolk_vol3_readers", "Book Readers", Material.BOOK, "Seated reading bench animation"));
        townsfolk.add(new ModelEntry("scene_townsfolk_vol3_swing", "Playground Swing", Material.LEAD, "Full dynamic swing pendulum animation"));
        townsfolk.add(new ModelEntry("scene_townsfolk_vol3_swing_mount", "Swing Mount", Material.OAK_FENCE, "Atmospheric swing mounting structure"));
        townsfolk.add(new ModelEntry("scene_townsfolk_vol3_writer", "Town Scribe / Writer", Material.FEATHER, "Quill parchment writing desk animation"));
        categories.put("TOWNSFOLKS", townsfolk);

        // 4. Farmers & Agronomy (Scenes Farmers)
        List<ModelEntry> farmers = new ArrayList<>();
        farmers.add(new ModelEntry("scene_farmer", "Sanctuary Agronomist", Material.GOLDEN_HOE, "Hoe tilling & vegetable crate harvest scene"));
        categories.put("FARMERS", farmers);

        // 5. Coastal Fishermen
        List<ModelEntry> fishermen = new ArrayList<>();
        fishermen.add(new ModelEntry("scene_fishermen", "Pier Fisherman Scene", Material.FISHING_ROD, "Seated pier fishing rod animation with line effects"));
        categories.put("FISHERMEN", fishermen);

        // 6. Canon 3D Character Rigs (Verified ModelEngine Blueprints)
        List<ModelEntry> canon = new ArrayList<>();
        canon.add(new ModelEntry("dcnpc_corvus", "Corvus Grand Marshal 3D", Material.NETHER_STAR, "Authentic character geometry with baked textures"));
        canon.add(new ModelEntry("dcnpc_kristoph", "Kristoph Matrix Engineer 3D", Material.ANVIL, "Forgemaster apron & phase wrench"));
        canon.add(new ModelEntry("dcnpc_lyra", "Lyra Spacetime Archivist 3D", Material.CLOCK, "Celestial robes & floating astrolabe"));
        canon.add(new ModelEntry("dcnpc_vivienne", "Vivienne High Stylist 3D", Material.DIAMOND, "Haute-couture salon shears & silken mantle"));
        canon.add(new ModelEntry("dcnpc_silas", "Silas Rift Cartographer 3D", Material.RECOVERY_COMPASS, "Rift sextant & planar map scroll"));
        canon.add(new ModelEntry("dcnpc_rowan", "Rowan Timbermaster 3D", Material.OAK_LOG, "Forester mantle & felling axe"));
        canon.add(new ModelEntry("dcnpc_maeve", "Maeve Shadow Archer 3D", Material.BOW, "Ranger leather & composite recurve"));
        canon.add(new ModelEntry("dcnpc_evelyn", "Evelyn High Alchemist 3D", Material.POTION, "Alchemical vestments & vial catalyst"));
        categories.put("CANON_3D", canon);
    }

    public void open(Player player, String charId) {
        NpcCharacter c = registry.getCharacter(charId);
        if (c == null) return;

        viewingChar.put(player.getUniqueId(), charId.toLowerCase());
        String currentCat = playerCategory.getOrDefault(player.getUniqueId(), "BLACKSMITHS");

        Inventory inv = Bukkit.createInventory(null, 54, "§8[Engine Selector] §d" + c.getShortName());

        // Fill background
        ItemStack pane = createItem(Material.GRAY_STAINED_GLASS_PANE, "§7");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, pane);
        }

        // --- ROW 1: System Modes & Header ---
        boolean isCitizens = c.getEngineType().equalsIgnoreCase("CITIZENS");
        ItemStack citBtn = createItem(isCitizens ? Material.EMERALD_BLOCK : Material.PLAYER_HEAD,
                isCitizens ? "§a§l✔ Citizens Engine (Active)" : "§7Switch to Citizens Engine",
                "§7Standard Minecraft player humanoid model.",
                "  §7• Automatic per-dimension skins (Prime, Snow, BW).",
                "  §7• Native pathfinding and despawn animations.",
                "  §7• Required for stages with WALK or POOF behaviors.",
                "§f",
                isCitizens ? "§aCurrently active engine." : "§e▶ Click to activate Citizens Engine");
        inv.setItem(0, citBtn);

        boolean isMeg = c.getEngineType().equalsIgnoreCase("MODEL_ENGINE");
        ItemStack megBtn = createItem(isMeg ? Material.GOLD_BLOCK : Material.ARMOR_STAND,
                isMeg ? "§6§l✔ ModelEngine 3D Rig (Active)" : "§7Switch to ModelEngine 3D",
                "§7Fluid 3D Blockbench animated rig with custom geometry.",
                "  §7• Model ID: §e" + c.getModelId(),
                "  §7• Click any model tab below to bind a model.",
                "§f",
                isMeg ? "§6Currently active engine." : "§e▶ Click to switch to ModelEngine 3D");
        inv.setItem(2, megBtn);

        ItemStack head = createItem(c.getIcon() != null ? c.getIcon() : Material.PLAYER_HEAD,
                "§e§l" + c.getShortName() + " §7- Rendering System",
                "§8Internal ID: " + c.getId(),
                "§7Active Engine: " + (isMeg ? "§6§lModelEngine 3D" : "§a§lCitizens Humanoid"),
                "§7Active Model: §f" + c.getModelId(),
                "§f",
                "§7Browse categories below to preview and bind animated 3D rigs.");
        inv.setItem(4, head);

        ItemStack closeBtn = createItem(Material.BARRIER, "§c§lClose Menu");
        inv.setItem(8, closeBtn);

        // --- ROW 2: CATEGORY TABS (Slots 11 to 16) ---
        inv.setItem(9, createItem(Material.ORANGE_STAINED_GLASS_PANE, "§6§lScenes ➜"));
        inv.setItem(10, createItem(Material.GRAY_STAINED_GLASS_PANE, "§7"));
        inv.setItem(11, createCategoryTab("BLACKSMITHS", "🔨 Blacksmiths", Material.ANVIL, currentCat));
        inv.setItem(12, createCategoryTab("MARKET", "📦 Market Stalls", Material.CHEST, currentCat));
        inv.setItem(13, createCategoryTab("TOWNSFOLKS", "🏡 Townsfolks", Material.LEATHER_CHESTPLATE, currentCat));
        inv.setItem(14, createCategoryTab("FARMERS", "🌾 Farmers", Material.GOLDEN_HOE, currentCat));
        inv.setItem(15, createCategoryTab("FISHERMEN", "🎣 Fishermen", Material.FISHING_ROD, currentCat));
        inv.setItem(16, createCategoryTab("CANON_3D", "⭐ Canon 3D Rigs", Material.NETHER_STAR, currentCat));
        inv.setItem(17, createItem(Material.ORANGE_STAINED_GLASS_PANE, "§6§lScenes ⬅"));

        // --- ROWS 3, 4, 5: MODEL ENTRIES (Slots 18 to 44) ---
        List<ModelEntry> entries = categories.getOrDefault(currentCat, Collections.emptyList());
        int slot = 18;
        for (ModelEntry entry : entries) {
            if (slot > 44) break;
            boolean isSelected = entry.modelId.equalsIgnoreCase(c.getModelId()) && isMeg;
            ItemStack modelItem = createItem(entry.icon,
                    (isSelected ? "§a§l✔ " : "§f") + entry.title,
                    "§8Model ID: " + entry.modelId,
                    "§7" + entry.desc,
                    "§f",
                    isSelected ? "§a✔ Currently bound & rendering on this NPC" : "§e▶ Click to bind & apply immediately");
            inv.setItem(slot++, modelItem);
        }

        // --- ROW 6: Navigation & Utilities ---
        ItemStack backBtn = createItem(Material.ARROW, "§c§l◀ Back to Character Editor");
        inv.setItem(45, backBtn);

        ItemStack status = createItem(Material.NAME_TAG, "§e§lBound Model ID: §f" + c.getModelId(),
                "§7Engine: " + c.getEngineType(),
                "§7Changes take effect instantly on all live instances.");
        inv.setItem(49, status);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
    }

    private ItemStack createCategoryTab(String catKey, String title, Material icon, String activeCat) {
        boolean isActive = catKey.equalsIgnoreCase(activeCat);
        return createItem(icon,
                (isActive ? "§a§l▶ " : "§7") + title + (isActive ? " §a(Viewing)" : ""),
                "§7Category: §f" + catKey,
                "§f",
                isActive ? "§aCurrently browsing this category." : "§e▶ Click to view 3D rigs in this pack.");
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(DragoncoteColor.colorize(name));
            if (lore.length > 0) {
                List<String> list = new ArrayList<>();
                for (String l : lore) {
                    list.add(DragoncoteColor.colorize(l));
                }
                meta.setLore(list);
            }
            is.setItemMeta(meta);
        }
        return is;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String charId = viewingChar.get(player.getUniqueId());
        if (charId == null) return;

        if (!event.getView().getTitle().startsWith("§8[Engine Selector]")) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        NpcCharacter c = registry.getCharacter(charId);
        if (c == null) return;

        // Slot 0: Switch to Citizens
        if (slot == 0) {
            c.setEngineType("CITIZENS");
            DCMessage.sendGeneral(player, "Set NPC Engine for " + c.getShortName() + " to Citizens.");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
            registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
            applyLiveEngine(c);
            open(player, charId);
            return;
        }

        // Slot 2: Switch to ModelEngine
        if (slot == 2) {
            c.setEngineType("MODEL_ENGINE");
            DCMessage.sendGeneral(player, "Set NPC Engine for " + c.getShortName() + " to ModelEngine (Model: " + c.getModelId() + ").");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
            registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
            applyLiveEngine(c);
            open(player, charId);
            return;
        }

        // Slot 8: Close
        if (slot == 8) {
            player.closeInventory();
            return;
        }

        // Category Tabs: Slots 11-16
        if (slot >= 11 && slot <= 16) {
            String[] catKeys = {"BLACKSMITHS", "MARKET", "TOWNSFOLKS", "FARMERS", "FISHERMEN", "CANON_3D"};
            int catIdx = slot - 11;
            if (catIdx >= 0 && catIdx < catKeys.length) {
                playerCategory.put(player.getUniqueId(), catKeys[catIdx]);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
                open(player, charId);
                return;
            }
        }

        // Slot 45: Back
        if (slot == 45) {
            if (npcEditorGUI != null) {
                npcEditorGUI.open(player, charId);
            }
            return;
        }

        // Model Selection from content grid (slots 18-44)
        if (slot >= 18 && slot <= 44) {
            String currentCat = playerCategory.getOrDefault(player.getUniqueId(), "BLACKSMITHS");
            List<ModelEntry> entries = categories.getOrDefault(currentCat, Collections.emptyList());
            int modelIdx = slot - 18;
            if (modelIdx >= 0 && modelIdx < entries.size()) {
                ModelEntry chosen = entries.get(modelIdx);
                c.setModelId(chosen.modelId);
                c.setEngineType("MODEL_ENGINE");
                player.sendMessage(DragoncoteColor.PREFIX_SUCCESS + "Bound 3D ModelEngine rig §b" + chosen.title + " §7(§f" + chosen.modelId + "§7) §ato " + c.getShortName() + "!");
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
                registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
                applyLiveEngine(c);
                open(player, charId);
            }
        }
    }

    private void applyLiveEngine(NpcCharacter c) {
        if (net.citizensnpcs.api.CitizensAPI.getPlugin() == null) return;
        if (c.getSector() == 1) {
            DCCosmetics.getInstance().getNpcCanonManager().syncSector1Instances(c);
        }
        for (net.citizensnpcs.api.npc.NPC npc : net.citizensnpcs.api.CitizensAPI.getNPCRegistry()) {
            String canonId = DCCosmetics.getInstance().getNpcCanonManager().resolveCanonId(npc);
            if (canonId != null && (canonId.equalsIgnoreCase(c.getId()) || canonId.toLowerCase().startsWith(c.getId().toLowerCase() + ":"))) {
                DCCosmetics.getInstance().getNpcCanonManager().applyEngineMode(npc, c);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        viewingChar.remove(event.getPlayer().getUniqueId());
        playerCategory.remove(event.getPlayer().getUniqueId());
    }
}
