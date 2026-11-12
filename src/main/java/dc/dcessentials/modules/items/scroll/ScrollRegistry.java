package dc.dcessentials.modules.items.scroll;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class ScrollRegistry {

    public static final NamespacedKey SCROLL_ID_KEY = new NamespacedKey(DCCosmetics.getInstance(), "scroll_id");
    public static final NamespacedKey SCROLL_TYPE_KEY = new NamespacedKey(DCCosmetics.getInstance(), "scroll_type");

    private volatile Map<String, ScrollModel> scrolls = Collections.emptyMap();
    private final File configFile;

    public ScrollRegistry() {
        this.configFile = new File(DCCosmetics.getInstance().getDataFolder(), "scrolls.yml");
    }

    public void loadScrolls() {
        dc.dcessentials.modules.items.util.FontImageUtil.clearCache();
        if (!configFile.exists()) {
            createDefaultConfig();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection root = config.getConfigurationSection("scrolls");
        if (root == null) {
            this.scrolls = Collections.emptyMap();
            DCCosmetics.getInstance().getLogger().warning("[DCEssentials] No 'scrolls' section found in scrolls.yml");
            return;
        }

        Map<String, ScrollModel> staged = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;

            String lowerId = id.toLowerCase(Locale.ROOT);
            if (staged.containsKey(lowerId)) {
                DCCosmetics.getInstance().getLogger().severe("[DCEssentials] Duplicate scroll ID '" + lowerId + "' in scrolls.yml; keeping first, skipping duplicate.");
                continue;
            }

            String name = sec.getString("name", id);
            String rarity = sec.getString("rarity", "COMMON");
            String typeStr = sec.getString("type", "COMBAT");
            ScrollType type;
            try {
                type = ScrollType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                type = ScrollType.COMBAT;
            }

            String targetCategory = sec.getString("target_category", "WEAPON");
            String matStr = sec.getString("icon", "PAPER");
            Material icon = Material.matchMaterial(matStr);
            if (icon == null) icon = Material.PAPER;

            Integer cmd = sec.contains("model_data") ? sec.getInt("model_data") : null;
            List<String> lore = sec.getStringList("lore");
            String socketDisplay = sec.getString("socket_display", name);
            String brief = sec.getString("brief", null);

            Map<String, Object> mechanics = new HashMap<>();
            ConfigurationSection mechSec = root.getConfigurationSection(id + ".mechanics");
            if (mechSec != null) {
                for (String key : mechSec.getKeys(true)) {
                    mechanics.put(key, mechSec.get(key));
                }
            }

            ScrollModel model = new ScrollModel(id, name, rarity, type, targetCategory, icon, cmd, lore, socketDisplay, brief, mechanics);
            staged.put(lowerId, model);
        }

        this.scrolls = Collections.unmodifiableMap(staged);
        DCCosmetics.getInstance().getLogger().info("[DCEssentials] Loaded " + this.scrolls.size() + " universal scrolls from scrolls.yml");
    }

    private void createDefaultConfig() {
        try {
            configFile.getParentFile().mkdirs();
            YamlConfiguration config = new YamlConfiguration();
            
            // 1. Chrono-Slip (Mastery technique for Mace)
            config.set("scrolls.chrono_slip.name", "<#FF5555>&lCHRONO-SLIP SCROLL &8(&c&lMACE&8)");
            config.set("scrolls.chrono_slip.rarity", "MASTERY");
            config.set("scrolls.chrono_slip.type", "MASTERY");
            config.set("scrolls.chrono_slip.target_category", "MACE");
            config.set("scrolls.chrono_slip.icon", "PAPER");
            config.set("scrolls.chrono_slip.model_data", 1001);
            config.set("scrolls.chrono_slip.lore", List.of(
                    "&7RARITY: <#FF5555>&lMASTERY",
                    "&7TYPE: &fWEAPON AUGMENT",
                    "&f",
                    "<#FF5555>&lCHRONO-DISRUPTION MATRIX",
                    "&7Infuses a Mace with phase-slip resonance.",
                    "&7Builds kinetic charge with each attempt.",
                    "&7On the <#10B981>&l5th Strike&7, completely bypasses",
                    "&7The Emperor's Telekinetic Barrier!",
                    "&7Unleashes unmitigated smash devastation!",
                    "&f",
                    "<#FCD34D>&lDrag & drop onto any Mace to infuse!"
            ));
            config.set("scrolls.chrono_slip.socket_display", "<#FF5555>&l◆ <#F87171>Chrono-Slip: <#FF5555>&l0/5 <#64748B>[□□□□□]");
            config.set("scrolls.chrono_slip.mechanics.type", "MACE_BREACH");
            config.set("scrolls.chrono_slip.mechanics.max_charges", 5);
            config.set("scrolls.chrono_slip.mechanics.breach_window_hits", 2);
            config.set("scrolls.chrono_slip.mechanics.target_boss_tags", List.of("emperor_boss", "Temporal Sovereign"));

            // 2. Slayer Rare (+15%)
            config.set("scrolls.slayer_rare.name", "<#0098FF>&lEMPEROR SLAYER SCROLL &8(&b&l+15%&8)");
            config.set("scrolls.slayer_rare.rarity", "RARE");
            config.set("scrolls.slayer_rare.type", "COMBAT");
            config.set("scrolls.slayer_rare.target_category", "WEAPON");
            config.set("scrolls.slayer_rare.icon", "PAPER");
            config.set("scrolls.slayer_rare.model_data", 1002);
            config.set("scrolls.slayer_rare.lore", List.of(
                    "&7RARITY: <#0098FF>&lRARE",
                    "&7TYPE: &fWEAPON AUGMENT",
                    "&f",
                    "<#0098FF>&lSOVEREIGN BANE I",
                    "&7Inscribes temporal fracture runes.",
                    "&7Deals <#0098FF>+15% Damage &7exclusively",
                    "&7against &4&lThe Emperor&7.",
                    "&f",
                    "&8Compatible: Swords, Axes, Maces, Spears,",
                    "&8Bows, Crossbows, and Tridents.",
                    "&f",
                    "<#FCD34D>&lDrag & drop onto weapon to infuse!"
            ));
            config.set("scrolls.slayer_rare.socket_display", "<#0098FF>&l◆ <#7DD3FC>Emperor Slayer: <#0098FF>&l+15% &7Damage");
            config.set("scrolls.slayer_rare.mechanics.type", "BOSS_SLAYER");
            config.set("scrolls.slayer_rare.mechanics.multiplier", 1.15);
            config.set("scrolls.slayer_rare.mechanics.target_boss_tags", List.of("emperor_boss", "Temporal Sovereign"));

            // 3. Slayer Epic (+20%)
            config.set("scrolls.slayer_epic.name", "<#AA00AA>&lEMPEROR SLAYER SCROLL &8(&d&l+20%&8)");
            config.set("scrolls.slayer_epic.rarity", "EPIC");
            config.set("scrolls.slayer_epic.type", "COMBAT");
            config.set("scrolls.slayer_epic.target_category", "WEAPON");
            config.set("scrolls.slayer_epic.icon", "PAPER");
            config.set("scrolls.slayer_epic.model_data", 1003);
            config.set("scrolls.slayer_epic.lore", List.of(
                    "&7RARITY: <#AA00AA>&lEPIC",
                    "&7TYPE: &fWEAPON AUGMENT",
                    "&f",
                    "<#AA00AA>&lSOVEREIGN BANE II",
                    "&7Inscribes concentrated rift ciphers.",
                    "&7Deals <#AA00AA>+20% Damage &7exclusively",
                    "&7against &4&lThe Emperor&7.",
                    "&f",
                    "&8Compatible: Swords, Axes, Maces, Spears,",
                    "&8Bows, Crossbows, and Tridents.",
                    "&f",
                    "<#FCD34D>&lDrag & drop onto weapon to infuse!"
            ));
            config.set("scrolls.slayer_epic.socket_display", "<#AA00AA>&l◆ <#C084FC>Emperor Slayer: <#AA00AA>&l+20% &7Damage");
            config.set("scrolls.slayer_epic.mechanics.type", "BOSS_SLAYER");
            config.set("scrolls.slayer_epic.mechanics.multiplier", 1.20);
            config.set("scrolls.slayer_epic.mechanics.target_boss_tags", List.of("emperor_boss", "Temporal Sovereign"));

            // 4. Slayer Mastery (+35%)
            config.set("scrolls.slayer_mythic.name", "<#FF5555>&lEMPEROR SLAYER SCROLL &8(&c&l+35%&8)");
            config.set("scrolls.slayer_mythic.rarity", "MASTERY");
            config.set("scrolls.slayer_mythic.type", "COMBAT");
            config.set("scrolls.slayer_mythic.target_category", "WEAPON");
            config.set("scrolls.slayer_mythic.icon", "PAPER");
            config.set("scrolls.slayer_mythic.model_data", 1004);
            config.set("scrolls.slayer_mythic.lore", List.of(
                    "&7RARITY: <#FF5555>&lMASTERY",
                    "&7TYPE: &fWEAPON AUGMENT",
                    "&f",
                    "<#FF5555>&lSOVEREIGN BANE III",
                    "&7Inscribes ancient god-killer axioms.",
                    "&7Deals <#FF5555>+35% Damage &7exclusively",
                    "&7against &4&lThe Emperor&7.",
                    "&f",
                    "&8Compatible: Swords, Axes, Maces, Spears,",
                    "&8Bows, Crossbows, and Tridents.",
                    "&f",
                    "<#FCD34D>&lDrag & drop onto weapon to infuse!"
            ));
            config.set("scrolls.slayer_mythic.socket_display", "<#FF5555>&l◆ <#FCD34D>Emperor Slayer: <#FF5555>&l+35% &7Damage");
            config.set("scrolls.slayer_mythic.mechanics.type", "BOSS_SLAYER");
            config.set("scrolls.slayer_mythic.mechanics.multiplier", 1.35);
            config.set("scrolls.slayer_mythic.mechanics.target_boss_tags", List.of("emperor_boss", "Temporal Sovereign"));

            // 5. Keystone Heatsink
            config.set("scrolls.keystone_heatsink.name", "<#06B6D4>&lHEATSINK DAMPENER SCROLL");
            config.set("scrolls.keystone_heatsink.rarity", "EPIC");
            config.set("scrolls.keystone_heatsink.type", "KEYSTONE");
            config.set("scrolls.keystone_heatsink.target_category", "KEYSTONE");
            config.set("scrolls.keystone_heatsink.icon", "PRISMARINE_SHARD");
            config.set("scrolls.keystone_heatsink.model_data", 1010);
            config.set("scrolls.keystone_heatsink.lore", List.of(
                    "&7RARITY: <#06B6D4>&lEPIC",
                    "&7TYPE: &fKEYSTONE AUGMENT",
                    "&f",
                    "<#06B6D4>&lTHERMAL DISSIPATION",
                    "&7Inscribes cryogenic heat-sinks onto Keystone.",
                    "&7Reduces warp heat accumulation by <#06B6D4>-35%&7.",
                    "&7Increases heat cooldown dissipation by <#10B981>+50%&7.",
                    "&f",
                    "<#FCD34D>&lDrag & drop onto Keystone to infuse!"
            ));
            config.set("scrolls.keystone_heatsink.socket_display", "<#06B6D4>&l◆ <#7DD3FC>Cryo-Heatsink: <#06B6D4>&l-35% Heat");
            config.set("scrolls.keystone_heatsink.mechanics.type", "KEYSTONE_HEAT");
            config.set("scrolls.keystone_heatsink.mechanics.heat_reduction_pct", 35);
            config.set("scrolls.keystone_heatsink.mechanics.cooldown_rate_pct", 50);

            // 6. Keystone Tesseract Flux
            config.set("scrolls.keystone_tesseract_flux.name", "<#A855F7>&lTESSERACT RESONATOR SCROLL");
            config.set("scrolls.keystone_tesseract_flux.rarity", "LEGENDARY");
            config.set("scrolls.keystone_tesseract_flux.type", "KEYSTONE");
            config.set("scrolls.keystone_tesseract_flux.target_category", "KEYSTONE");
            config.set("scrolls.keystone_tesseract_flux.icon", "ECHO_SHARD");
            config.set("scrolls.keystone_tesseract_flux.model_data", 1011);
            config.set("scrolls.keystone_tesseract_flux.lore", List.of(
                    "&7RARITY: <#A855F7>&lLEGENDARY",
                    "&7TYPE: &fKEYSTONE AUGMENT",
                    "&f",
                    "<#A855F7>&lSPATIAL COMPRESSION",
                    "&7Harmonizes Keystone with local Tesseract nodes.",
                    "&7Warp and ability Microcharge cost reduced by <#A855F7>-25%&7.",
                    "&f",
                    "<#FCD34D>&lDrag & drop onto Keystone to infuse!"
            ));
            config.set("scrolls.keystone_tesseract_flux.socket_display", "<#A855F7>&l◆ <#C084FC>Tesseract Flux: <#A855F7>&l-25% Cost");
            config.set("scrolls.keystone_tesseract_flux.mechanics.type", "KEYSTONE_TESSERACT");
            config.set("scrolls.keystone_tesseract_flux.mechanics.cost_reduction_pct", 25);

            // 7. Comprehensive Skill Mastery XP Scrolls (Swords, Axes, Mining, Farming, Excavation, Archery, Defense)
            String[][] skills = {
                    {"swords", "Blade Mastery", "SWORD", "Blade Discipline"},
                    {"axes", "Axe Mastery", "WEAPON", "Berserker Cleave"},
                    {"pickaxe", "Mining Mastery", "TOOL", "Geological Attunement"},
                    {"farming", "Farming Mastery", "TOOL", "Bountiful Harvest"},
                    {"excavation", "Excavation Mastery", "TOOL", "Tectonic Resonance"},
                    {"archery", "Archery Mastery", "WEAPON", "Sharpshooter Focus"},
                    {"defense", "Defense Mastery", "ARMOR", "Bastion Attunement"}
            };

            int baseModel = 2000;
            for (String[] sk : skills) {
                String sId = sk[0];
                String sName = sk[1];
                String targetCat = sk[2];
                String discipline = sk[3];

                int[] pcts = {5, 10, 20, 30};
                String[] rarities = {"COMMON", "UNCOMMON", "RARE", "EPIC"};
                String[] colors = {"&7", "<#55FF55>", "<#0098FF>", "<#A855F7>"};

                for (int t = 0; t < 4; t++) {
                    int pct = pcts[t];
                    String r = rarities[t];
                    String col = colors[t];
                    String scrollKey = "mastery_" + sId + "_" + (t + 1);

                    config.set("scrolls." + scrollKey + ".name", col + "&l" + sName.toUpperCase() + " SCROLL &8(" + col + "+" + pct + "%&8)");
                    config.set("scrolls." + scrollKey + ".rarity", r);
                    config.set("scrolls." + scrollKey + ".type", "MASTERY");
                    config.set("scrolls." + scrollKey + ".target_category", targetCat);
                    config.set("scrolls." + scrollKey + ".icon", "PAPER");
                    config.set("scrolls." + scrollKey + ".model_data", baseModel++);
                    config.set("scrolls." + scrollKey + ".lore", List.of(
                            "&7RARITY: " + col + "&l" + r,
                            "&7TYPE: &fSKILL AUGMENT",
                            "&f",
                            col + "&l" + discipline.toUpperCase(),
                            "&7Inscribes ancient techniques into the item.",
                            "&7Grants " + col + "+" + pct + "% Mastery XP &7for &e" + sName + "&7.",
                            "&f",
                            "<#FCD34D>&lDrag & drop onto " + targetCat.toLowerCase() + " to infuse!"
                    ));
                    config.set("scrolls." + scrollKey + ".socket_display", col + "&l◆ &f" + sName + ": " + col + "&l+" + pct + "% &7XP");
                    config.set("scrolls." + scrollKey + ".mechanics.type", "MASTERY_XP_BOOST");
                    config.set("scrolls." + scrollKey + ".mechanics.skill_id", sId);
                    config.set("scrolls." + scrollKey + ".mechanics.xp_multiplier", 1.0 + (pct / 100.0));
                }
            }

            // 9. Temporal Stasis Core Artifact
            config.set("scrolls.stasis_core.name", "<#06B6D4>&lTEMPORAL STASIS CORE");
            config.set("scrolls.stasis_core.rarity", "TRANSCENDENT");
            config.set("scrolls.stasis_core.type", "UTILITY");
            config.set("scrolls.stasis_core.target_category", "NONE");
            config.set("scrolls.stasis_core.icon", "HEART_OF_THE_SEA");
            config.set("scrolls.stasis_core.model_data", 1030);
            config.set("scrolls.stasis_core.lore", List.of(
                    "&7RARITY: <#06B6D4>&lTRANSCENDENT",
                    "&7TYPE: &fCOMBAT ARTIFACT",
                    "&f",
                    "<#06B6D4>&lCHRONO-CRYO CAGE",
                    "&7Condenses absolute zero chronons into",
                    "&7an omnidirectional temporal fracture.",
                    "&f",
                    "<#06B6D4>• &fFreezes &4&lThe Emperor &ffor &b15 Seconds&f.",
                    "<#06B6D4>• &fDisables his AI, mobility, and attacks",
                    "&f  during the stasis window.",
                    "<#06B6D4>• &fCreates a pulsing cryogenic aura.",
                    "&f",
                    "<#FCD34D>&lRight-Click near Emperor to detonate!",
                    "&8Cooldown: 60 seconds"
            ));
            config.set("scrolls.stasis_core.mechanics.type", "STASIS_CORE");
            config.set("scrolls.stasis_core.mechanics.duration_seconds", 15);
            config.set("scrolls.stasis_core.mechanics.cooldown_seconds", 60);

            config.save(configFile);
        } catch (IOException e) {
            DCCosmetics.getInstance().getLogger().log(Level.SEVERE, "[DCEssentials] Failed to create scrolls.yml", e);
        }
    }

    public ScrollModel getScroll(String id) {
        if (id == null) return null;
        return scrolls.get(id.toLowerCase());
    }

    public Collection<ScrollModel> getScrolls() {
        return Collections.unmodifiableCollection(scrolls.values());
    }

    public ItemStack createScrollItem(String id, int amount) {
        ScrollModel model = getScroll(id);
        if (model == null) return null;

        ItemStack item = new ItemStack(model.getIcon(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (model.getCustomModelData() != null) {
                meta.setCustomModelData(model.getCustomModelData());
            }

            // Native 1.21 glint
            meta.setEnchantmentGlintOverride(true);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(SCROLL_ID_KEY, PersistentDataType.STRING, model.getId());
            pdc.set(SCROLL_TYPE_KEY, PersistentDataType.STRING, model.getType().name());

            item.setItemMeta(meta);
        }

        dc.dcessentials.modules.items.model.ItemRarity rarity = dc.dcessentials.modules.items.model.ItemRarity.fromString(model.getRarity());
        dc.dcessentials.modules.items.tooltip.TooltipRenderer.renderScroll(item, model.getName(), rarity, model.getTargetCategory(), model.getLore());

        return item;
    }

    public boolean isScrollItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(SCROLL_ID_KEY, PersistentDataType.STRING);
    }

    public String getScrollId(ItemStack item) {
        if (!isScrollItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(SCROLL_ID_KEY, PersistentDataType.STRING);
    }
}
