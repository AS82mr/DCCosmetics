package dc.dcessentials.modules.pets.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.util.HeadUtil;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.model.ItemRarity;
import dc.dcessentials.modules.items.model.ItemStat;
import dc.dcessentials.modules.items.tooltip.TooltipRenderer;
import dc.dcessentials.modules.pets.PetManager;
import dc.dcessentials.modules.pets.model.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Clean Hypixel-style Companions GUI showing ONLY unlocked pets to regular players
 * (plus an admin-only Full Catalog toggle), category filter, Auto-Cast toggle,
 * Target Circle toggle, Visibility filter, and full progressive Lv 1->100 breakdowns.
 */
public class PetMenuGUI implements InventoryHolder {

    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final NamespacedKey GUI_ACTION_KEY = new NamespacedKey(DCCosmetics.getInstance(), "dce_pet_gui_action");
    private static final NamespacedKey GUI_PET_KEY = new NamespacedKey(DCCosmetics.getInstance(), "dce_pet_gui_id");

    private final Player viewer;
    private final PetManager petManager;
    private final int page;
    private final PetCategory filterCategory; // null = ALL
    private final boolean adminShowLocked;
    private final Inventory inventory;

    public PetMenuGUI(Player viewer, PetManager petManager, int page) {
        this(viewer, petManager, page, null, false);
    }

    public PetMenuGUI(Player viewer, PetManager petManager, int page, PetCategory filterCategory, boolean adminShowLocked) {
        this.viewer = viewer;
        this.petManager = petManager;
        this.page = Math.max(0, page);
        this.filterCategory = filterCategory;
        this.adminShowLocked = adminShowLocked && viewer != null && viewer.hasPermission("dce.pet.admin");
        this.inventory = Bukkit.createInventory(this, 54, ColorUtil.toItemComponent("&8Companions Wardrobe"));
        populate();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    private void populate() {
        ItemStack glass = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack accentGlass = createPane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, glass);
        }
        for (int row = 1; row <= 4; row++) {
            inventory.setItem(row * 9, accentGlass);
            inventory.setItem(row * 9 + 8, accentGlass);
        }

        PlayerPetData data = petManager.getData(viewer.getUniqueId());
        String activeId = data.getActivePetId();

        List<PetSpec> visibleSpecs = new ArrayList<>();
        for (PetSpec spec : petManager.getRegistry().getAllPets()) {
            boolean owned = data.hasPet(spec.getId());
            if (!owned && !adminShowLocked) continue;
            if (filterCategory != null && spec.getCategory() != filterCategory) continue;
            visibleSpecs.add(spec);
        }

        visibleSpecs.sort((a, b) -> {
            boolean actA = a.getId().equalsIgnoreCase(activeId);
            boolean actB = b.getId().equalsIgnoreCase(activeId);
            if (actA != actB) return actA ? -1 : 1;
            boolean ownA = data.hasPet(a.getId());
            boolean ownB = data.hasPet(b.getId());
            if (ownA != ownB) return ownA ? -1 : 1;
            return Integer.compare(b.getRarity().ordinal(), a.getRarity().ordinal());
        });

        int perPage = CONTENT_SLOTS.length;
        int totalPages = Math.max(1, (int) Math.ceil((double) visibleSpecs.size() / perPage));
        int clampedPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = clampedPage * perPage;

        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int slot = CONTENT_SLOTS[i];
            int idx = start + i;
            if (idx < visibleSpecs.size()) {
                PetSpec spec = visibleSpecs.get(idx);
                inventory.setItem(slot, buildPetIcon(spec, data));
            } else {
                inventory.setItem(slot, null);
            }
        }

        // Bottom Controls Bar (45..53)
        if (clampedPage > 0) {
            inventory.setItem(45, menuBtn(Material.ARROW, "&ePrevious Page", "prev_page",
                    List.of("&7View page " + clampedPage + " of " + totalPages)));
        }

        String filterLabel = filterCategory != null ? filterCategory.getFormattedBadge() : "&fAll Categories";
        inventory.setItem(46, menuBtn(Material.HOPPER, "&bFilter: " + filterLabel, "cycle_filter",
                List.of("&7Click to filter companions by skill category.",
                        "&8Active Filter: " + filterLabel)));

        PetVisibilityMode vis = data.getVisibilityMode();
        inventory.setItem(47, menuBtn(Material.ENDER_EYE, "&bVisibility: " + vis.getBadge(), "cycle_visibility",
                List.of("&7Choose which companions render visually:",
                        vis == PetVisibilityMode.ALL ? "&a▶ All Players' Companions" : "&8  All Players' Companions",
                        vis == PetVisibilityMode.OWN_ONLY ? "&a▶ Your Own Companion Only" : "&8  Your Own Companion Only",
                        vis == PetVisibilityMode.HIDDEN ? "&a▶ Hidden (Stats Still Active)" : "&8  Hidden (Stats Still Active)")));

        if (activeId != null && petManager.getRegistry().getPet(activeId) != null) {
            inventory.setItem(48, menuBtn(Material.BARRIER, "&cDismiss Active Companion", "dismiss_pet",
                    List.of("&7Return your active companion back into your wardrobe.")));
        }

        inventory.setItem(49, buildOverviewItem(data, visibleSpecs.size()));

        boolean autoCast = data.isAutoCastSkills();
        inventory.setItem(50, menuBtn(autoCast ? Material.BLAZE_POWDER : Material.GUNPOWDER,
                "&eAuto-Cast Ability: " + (autoCast ? "&aEnabled" : "&cDisabled"), "toggle_autocast",
                List.of("&7Automatically triggers active abilities",
                        "&7when combat trigger conditions are met.",
                        autoCast ? "&eClick to pause auto-casting." : "&aClick to enable auto-casting.")));

        boolean tracker = data.isTrackerEnabled();
        inventory.setItem(51, menuBtn(tracker ? Material.TARGET : Material.COAL,
                "&cTarget Circle: " + (tracker ? "&aEnabled" : "&cDisabled"), "toggle_tracker",
                List.of("&7Shows a glowing ring under the mob",
                        "&7you are currently looking at in combat.",
                        tracker ? "&eClick to hide target ring." : "&aClick to show target ring.")));

        if (viewer.hasPermission("dce.pet.admin")) {
            inventory.setItem(52, menuBtn(adminShowLocked ? Material.NETHER_STAR : Material.COMPASS,
                    "&6Catalog View: " + (adminShowLocked ? "&eAll Blueprints" : "&aOwned Only"),
                    "toggle_admin_catalog",
                    List.of("&7Admin Toggle: Show unowned companions in this menu.",
                            "&8Shift-Left-Click locked entries to unlock them.")));
        }

        if (clampedPage < totalPages - 1) {
            inventory.setItem(53, menuBtn(Material.ARROW, "&eNext Page", "next_page",
                    List.of("&7View page " + (clampedPage + 2) + " of " + totalPages)));
        }
    }

    private ItemStack buildOverviewItem(PlayerPetData data, int shownCount) {
        String activeId = data.getActivePetId();
        PetSpec activeSpec = activeId != null ? petManager.getRegistry().getPet(activeId) : null;
        int unlockedCount = data.getOwnedPets().size();
        int totalCount = petManager.getRegistry().getAllPets().size();

        List<String> lore = new ArrayList<>();
        lore.add("&7Unlocked Companions: &b" + unlockedCount + "&7/" + totalCount);
        lore.add("&7Showing: &f" + shownCount + (filterCategory != null ? " (" + filterCategory.getDisplayName() + ")" : ""));
        lore.add("");
        if (activeSpec != null) {
            PlayerPetData.OwnedPetEntry entry = data.getPetEntry(activeSpec.getId());
            int lvl = entry != null ? entry.getLevel() : 1;
            double xp = entry != null ? entry.getXp() : 0.0;
            ItemRarity rar = entry != null && entry.getRarityOverride() != null ? entry.getRarityOverride() : activeSpec.getRarity();

            lore.add("&7Active: " + rar.getColorHex() + activeSpec.getDisplayName() + " &8· &7Lv " + lvl + "/" + activeSpec.getMaxLevel());
            lore.add("&7Category: " + activeSpec.getCategory().getFormattedBadge());
            if (lvl < activeSpec.getMaxLevel()) {
                double req = activeSpec.getXpRequiredForNextLevel(lvl, petManager.getConfig());
                int pct = req > 0 ? (int) Math.min(100, Math.round((xp / req) * 100.0)) : 0;
                lore.add("&7XP: &e" + pct + "% &8(&f" + Math.round(xp) + "&7/&f" + Math.round(req) + "&8)");
            } else {
                lore.add("<#10B981>Max Level Reached!");
            }
            if (activeSpec.getActiveSkill() != null) {
                PetSkillSpec sk = activeSpec.getActiveSkill();
                double cdMult = petManager.getEffectiveSkillCooldownMultiplier(viewer);
                double pwrMult = petManager.getEffectiveSkillPowerMultiplier(viewer);
                double effCd = Math.round((sk.getEffectiveCooldownMs(lvl, activeSpec.getMaxLevel()) * cdMult) / 100.0) / 10.0;
                double effPwr = Math.round(sk.getEffectivePower(lvl, activeSpec.getMaxLevel()) * pwrMult * 10.0) / 10.0;
                lore.add("");
                lore.add("<#38BDF8>⚡ Ability: &f" + sk.getDisplayName());
                lore.add("&7Trigger: <#FDE047>" + sk.getTriggerConditionText());
                lore.add("&7Power: <#67E8F9>" + effPwr + " &8| &7Cooldown: <#67E8F9>" + effCd + "s &8| &7Range: <#67E8F9>" + (int) Math.round(sk.getRange()) + "m");
            }
        } else {
            lore.add("&7Active: &8None");
            lore.add("&8Click any unlocked companion above to summon it.");
        }
        lore.add("");
        lore.add("&8Allies Immune to AoE: &e" + data.getAllies().size());
        lore.add("&8Tip: Shift + Right-Click a player to toggle Ally");

        return menuBtn(Material.BOOK, "<#0CBF93>Companion Summary", "info", lore);
    }

    private ItemStack buildPetIcon(PetSpec spec, PlayerPetData data) {
        PlayerPetData.OwnedPetEntry owned = data.getPetEntry(spec.getId());
        boolean isOwned = owned != null;
        boolean isActive = spec.getId().equalsIgnoreCase(data.getActivePetId());
        int level = isOwned ? owned.getLevel() : 1;
        double xp = isOwned ? owned.getXp() : 0.0;
        ItemRarity rarity = isOwned && owned.getRarityOverride() != null ? owned.getRarityOverride() : spec.getRarity();

        ItemStack stack = isOwned ? HeadUtil.getCustomHead(spec.getHeadBase64()) : new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(GUI_PET_KEY, PersistentDataType.STRING, spec.getId());

            String title = rarity.getColorHex() + spec.getDisplayName() + " &8· &7Lv " + level + "/" + spec.getMaxLevel()
                    + (isActive ? " <#10B981>✦ Active" : (isOwned ? "" : " &cLocked"));
            meta.displayName(ColorUtil.toItemComponent(title));

            if (isActive) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            List<String> lore = new ArrayList<>();
            lore.add(rarity.getColorHex() + rarity.getDisplayName() + " " + spec.getCategory().getFormattedBadge()
                    + " &8· &7" + spec.getFamily());
            if (spec.getCategory() == PetCategory.OMNI) {
                lore.add("&8Progression: Gains 100% XP from all Skills");
            } else {
                lore.add("&8Progression: 100% " + spec.getCategory().getDisplayName() + " Skills XP &7(25% other)");
            }
            lore.add("");

            // Lv 1 -> 100 stats
            Map<ItemStat, Double> stats = spec.getStatsAtLevel(level);
            if (!stats.isEmpty()) {
                lore.add("&7Companion Stats (Lv " + level + "):");
                for (Map.Entry<ItemStat, Double> st : stats.entrySet()) {
                    ItemStat stat = st.getKey();
                    lore.add("  " + stat.getColor() + stat.getIcon() + " " + stat.getDisplayName()
                            + ": &f" + TooltipRenderer.formatStatValue(stat, st.getValue()));
                }
                lore.add("");
            }

            // Tiered Passive Perks (1/2/3/4/5)
            int unlockedCount = 0;
            for (PetPerk perk : spec.getPerks()) {
                if (perk.isUnlockedAt(level)) unlockedCount++;
            }
            lore.add("&7Perks &8(" + unlockedCount + "/" + spec.getPerks().size() + " Unlocked):");
            for (PetPerk perk : spec.getPerks()) {
                boolean unlocked = isOwned && perk.isUnlockedAt(level);
                lore.add(unlocked
                        ? "<#FCD34D>  ✦ " + perk.getName()
                        : "&8  ✖ " + perk.getName() + " &7(Lv " + perk.getUnlockLevel() + ")");
                for (String pLine : perk.formatDescriptionLines(level, spec.getMaxLevel())) {
                    lore.add((unlocked ? "&7    " : "&8    ") + pLine);
                }
            }
            lore.add("");

            // Active Skill with explicit Trigger / Cooldown / Range / Power
            if (spec.getActiveSkill() != null) {
                PetSkillSpec sk = spec.getActiveSkill();
                double cdMult = petManager.getEffectiveSkillCooldownMultiplier(viewer);
                double pwrMult = petManager.getEffectiveSkillPowerMultiplier(viewer);
                double cdSec = Math.round((sk.getEffectiveCooldownMs(level, spec.getMaxLevel()) * cdMult) / 100.0) / 10.0;
                lore.add("<#38BDF8>⚡ Auto Ability: &f" + sk.getDisplayName());
                lore.add("&7  Trigger: <#FDE047>" + sk.getTriggerConditionText());
                lore.add("&7  Cooldown: <#67E8F9>" + cdSec + "s &8| &7Range: <#67E8F9>" + (int) Math.round(sk.getRange()) + "m");
                for (String sLine : sk.formatDescriptionLines(level, spec.getMaxLevel(), cdMult, pwrMult)) {
                    lore.add("&7  " + sLine);
                }
                lore.add("");
            }

            if (isOwned) {
                if (level >= spec.getMaxLevel()) {
                    lore.add("<#10B981>MAX LEVEL (" + spec.getMaxLevel() + "/" + spec.getMaxLevel() + ")");
                } else {
                    double req = spec.getXpRequiredForNextLevel(level, petManager.getConfig());
                    int pct = req > 0 ? (int) Math.min(100, Math.round((xp / req) * 100.0)) : 0;
                    lore.add("&7Progress to Lv " + (level + 1) + ": &e" + pct + "% &8(&f" + Math.round(xp) + "&7/&f" + Math.round(req) + " XP&8)");
                }
                lore.add("");
                lore.add(isActive ? "&cLeft-Click to Dismiss" : "&aLeft-Click to Summon");
                lore.add("&eRight-Click to convert to Item Token");
            } else {
                lore.add("&cNot Yet Unlocked");
                lore.add("&6Admin Shift-Left-Click to unlock & summon");
            }

            meta.lore(ColorUtil.toItemLore(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack menuBtn(Material mat, String name, String action, List<String> loreLines) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.toItemComponent(name));
            meta.lore(ColorUtil.toItemLore(loreLines));
            meta.getPersistentDataContainer().set(GUI_ACTION_KEY, PersistentDataType.STRING, action);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            is.setItemMeta(meta);
        }
        return is;
    }

    private ItemStack createPane(Material mat, String name) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.toItemComponent(name));
            is.setItemMeta(meta);
        }
        return is;
    }

    private PetCategory nextCategory(PetCategory current) {
        PetCategory[] vals = PetCategory.values();
        if (current == null) return vals[0];
        int nextIdx = current.ordinal() + 1;
        return nextIdx >= vals.length ? null : vals[nextIdx];
    }

    public static class PetMenuListener implements Listener {
        private final PetManager petManager;

        public PetMenuListener(PetManager petManager) {
            this.petManager = petManager;
        }

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof PetMenuGUI gui)) return;
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player clicker)) return;

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
            ItemMeta meta = clicked.getItemMeta();
            if (meta == null) return;

            PlayerPetData data = petManager.getData(clicker.getUniqueId());
            String action = meta.getPersistentDataContainer().get(GUI_ACTION_KEY, PersistentDataType.STRING);

            if (action != null) {
                switch (action) {
                    case "prev_page" -> new PetMenuGUI(clicker, petManager, gui.page - 1, gui.filterCategory, gui.adminShowLocked).open();
                    case "next_page" -> new PetMenuGUI(clicker, petManager, gui.page + 1, gui.filterCategory, gui.adminShowLocked).open();
                    case "cycle_filter" -> {
                        clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.3f);
                        new PetMenuGUI(clicker, petManager, 0, gui.nextCategory(gui.filterCategory), gui.adminShowLocked).open();
                    }
                    case "cycle_visibility" -> {
                        PetVisibilityMode next = data.getVisibilityMode().next();
                        data.setVisibilityMode(next);
                        petManager.savePlayerAsync(data);
                        clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                        DCMessage.sendGeneral(clicker, "Companion visibility set to &f" + next.getDescription() + "&#0CBF93.");
                        new PetMenuGUI(clicker, petManager, gui.page, gui.filterCategory, gui.adminShowLocked).open();
                    }
                    case "dismiss_pet" -> {
                        petManager.dismissPet(clicker, false);
                        new PetMenuGUI(clicker, petManager, gui.page, gui.filterCategory, gui.adminShowLocked).open();
                    }
                    case "toggle_autocast" -> {
                        data.setAutoCastSkills(!data.isAutoCastSkills());
                        petManager.savePlayerAsync(data);
                        clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, data.isAutoCastSkills() ? 1.5f : 0.8f);
                        DCMessage.sendGeneral(clicker, "Auto-Cast abilities "
                                + (data.isAutoCastSkills() ? "&aenabled" : "&cdisabled") + "&#0CBF93.");
                        new PetMenuGUI(clicker, petManager, gui.page, gui.filterCategory, gui.adminShowLocked).open();
                    }
                    case "toggle_tracker" -> {
                        data.setTrackerEnabled(!data.isTrackerEnabled());
                        clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, data.isTrackerEnabled() ? 1.5f : 0.8f);
                        DCMessage.sendGeneral(clicker, "Target tracker circle "
                                + (data.isTrackerEnabled() ? "&aenabled" : "&cdisabled") + "&#0CBF93.");
                        new PetMenuGUI(clicker, petManager, gui.page, gui.filterCategory, gui.adminShowLocked).open();
                    }
                    case "toggle_admin_catalog" -> {
                        if (clicker.hasPermission("dce.pet.admin")) {
                            clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                            new PetMenuGUI(clicker, petManager, 0, gui.filterCategory, !gui.adminShowLocked).open();
                        }
                    }
                    default -> {}
                }
                return;
            }

            String petId = meta.getPersistentDataContainer().get(GUI_PET_KEY, PersistentDataType.STRING);
            if (petId == null) return;
            PetSpec spec = petManager.getRegistry().getPet(petId);
            if (spec == null) return;

            boolean owned = data.hasPet(spec.getId());
            if (!owned) {
                if (clicker.hasPermission("dce.pet.admin") && event.getClick() == ClickType.SHIFT_LEFT) {
                    data.unlockOrUpgradePet(spec.getId(), 1, 0.0, spec.getRarity());
                    petManager.summonPet(clicker, spec.getId(), false);
                    new PetMenuGUI(clicker, petManager, gui.page, gui.filterCategory, gui.adminShowLocked).open();
                } else {
                    clicker.playSound(clicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                    DCMessage.sendWarning(clicker, "That companion is currently locked.");
                }
                return;
            }

            if (event.getClick().isRightClick()) {
                if (clicker.getInventory().firstEmpty() == -1) {
                    DCMessage.sendError(clicker, "Your inventory is full! Clear a slot before withdrawing a companion.");
                    return;
                }
                ItemStack token = petManager.withdrawPetToToken(clicker, spec.getId());
                if (token != null) {
                    clicker.getInventory().addItem(token);
                    clicker.playSound(clicker.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                    DCMessage.sendGeneral(clicker, "Converted &f" + spec.getDisplayName() + " &#0CBF93into a claimable item.");
                    new PetMenuGUI(clicker, petManager, gui.page, gui.filterCategory, gui.adminShowLocked).open();
                }
            } else {
                if (spec.getId().equalsIgnoreCase(data.getActivePetId())) {
                    petManager.dismissPet(clicker, false);
                } else {
                    petManager.summonPet(clicker, spec.getId(), false);
                }
                new PetMenuGUI(clicker, petManager, gui.page, gui.filterCategory, gui.adminShowLocked).open();
            }
        }
    }
}
