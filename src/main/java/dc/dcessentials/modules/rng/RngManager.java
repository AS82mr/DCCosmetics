package dc.dcessentials.modules.rng;

import dc.dccosmetics.DCCosmetics;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class RngManager {

    private final RngSecurity security = new RngSecurity();
    private final Map<String, RngModel.Category> categories = new LinkedHashMap<>();
    private final Map<String, List<RngModel.Drop>> dropTables = new HashMap<>();

    // Persistent player state:
    // UUID -> (Category -> Selected Drop ID)
    private final Map<UUID, Map<String, String>> playerSelectedDrops = new ConcurrentHashMap<>();
    // UUID -> (Category -> Accumulated Stored RNG XP Pool)
    private final Map<UUID, Map<String, Integer>> playerCategoryXp = new ConcurrentHashMap<>();

    public void registerDefaults() {
        categories.clear();
        dropTables.clear();

        // 1. Categories
        registerCategory("general", "Global Drop Table", Material.NETHER_STAR, "#38BDF8", "Drops obtainable from ANY mob or activity server-wide.");
        registerCategory("overworld", "Overworld Standard", Material.MOSSY_COBBLESTONE, "#4ADE80", "Standard surface & cavern monsters.");
        registerCategory("nether", "Nether Fortress & Wastes", Material.MAGMA_CREAM, "#F97316", "Hellish entities from the Nether dimension.");
        registerCategory("end", "The End & Void", Material.ENDER_EYE, "#A855F7", "Void-touched creatures of The End.");
        registerCategory("bosses", "Apex Vanilla Bosses", Material.DRAGON_HEAD, "#EF4444", "Warden, Wither, Ender Dragon & Elder Guardian.");
        registerCategory("lowercity", "LowerCity Syndicate", Material.CRYING_OBSIDIAN, "#EC4899", "Emperor Boss, Imperial Sentinels & LowerCity Minibosses.");
        registerCategory("survival_custom", "Wilderness Custom Mobs", Material.ECHO_SHARD, "#14B8A6", "Custom MythicMobs roaming the Survival world.");
        registerCategory("casino", "Vegas & High-Roller Games", Material.GOLD_BLOCK, "#FACC15", "Vegas Jackpots, Roulette Spins & Claw Machine pulls.");

        // 2. Drops
        registerDrop("general", "gen_chat_crimson", "&#FF1744&l[Chat Color] Imperial Crimson", Material.RED_DYE, 0.02, 15000, "lp user %player% permission set chatcolor.crimson true");
        registerDrop("general", "gen_cosmetic_aura", "&#00E5FF&l[Cosmetic] Spark Trail", Material.AMETHYST_SHARD, 0.01, 25000, "give %player% amethyst_shard 1");
        registerDrop("general", "gen_mystery_relic", "&#A855F7&l[Relic] Dormant Core", Material.ECHO_SHARD, 0.05, 8000, "give %player% echo_shard 1");

        registerDrop("overworld", "ow_undead_crest", "&#4ADE80&l[Drop] Undead Crest", Material.ROTTEN_FLESH, 0.005, 12000, "give %player% emerald 16");
        registerDrop("overworld", "ow_arachnid_silk", "&#4ADE80&l[Drop] Crystalline Silk", Material.STRING, 0.008, 9000, "give %player% diamond 2");

        registerDrop("nether", "neth_infernal_ember", "&#F97316&l[Drop] Infernal Ember", Material.BLAZE_POWDER, 0.02, 10000, "give %player% blaze_rod 4");
        registerDrop("nether", "neth_withered_skull", "&#F97316&l[Cosmetic] Soulflame Crown", Material.WITHER_SKELETON_SKULL, 0.008, 20000, "give %player% wither_skeleton_skull 1");

        registerDrop("end", "end_void_singularity", "&#A855F7&l[Drop] Void Singularity", Material.ENDER_PEARL, 0.015, 14000, "give %player% ender_pearl 16");

        registerDrop("bosses", "boss_sculk_heart", "&#06B6D4&l[Drop] Warden's Resonant Heart", Material.SCULK_CATALYST, 0.25, 5000, "give %player% nether_star 1");
        registerDrop("bosses", "boss_dragon_scale", "&#EF4444&l[Drop] Primordial Scale", Material.DRAGON_BREATH, 0.50, 4000, "give %player% dragon_breath 4");

        registerDrop("lowercity", "lc_emperor_blade", "&#EC4899&l[Mythic] Emperor's Decree", Material.NETHERITE_SWORD, 0.05, 30000, "give %player% netherite_sword 1");
        registerDrop("lowercity", "lc_cyber_visor", "&#EC4899&l[Cosmetic] LowerCity Cyber Visor", Material.CARVED_PUMPKIN, 0.10, 15000, "give %player% carved_pumpkin 1");

        registerDrop("survival_custom", "surv_ancient_rune", "&#14B8A6&l[Drop] Wilderness Rune", Material.EMERALD, 0.03, 12000, "give %player% emerald_block 4");

        registerDrop("casino", "cas_highroller_tag", "&#FACC15&l[Title] High-Roller Jackpot Tag", Material.NAME_TAG, 0.02, 20000, "lp user %player% permission set title.highroller true");
        registerDrop("casino", "cas_golden_chip", "&#FACC15&l[Artifact] Platinum Casino Chip", Material.SUNFLOWER, 0.05, 10000, "give %player% gold_block 8");
    }

    public void registerCategory(String id, String name, Material icon, String hexColor, String desc) {
        categories.put(id.toLowerCase(), new RngModel.Category(id.toLowerCase(), name, icon, hexColor, desc));
    }

    public void registerDrop(String cat, String id, String name, Material icon, double chance, int goal, String cmd) {
        dropTables.computeIfAbsent(cat.toLowerCase(), k -> new ArrayList<>())
                .add(new RngModel.Drop(cat.toLowerCase(), id.toLowerCase(), name, icon, chance, goal, cmd));
    }

    public void setInitialState(UUID uuid, String category, String selectedDrop, int xp) {
        String cat = category.toLowerCase();
        if (selectedDrop != null && !selectedDrop.equalsIgnoreCase("none")) {
            playerSelectedDrops.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(cat, selectedDrop.toLowerCase());
        }
        playerCategoryXp.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(cat, Math.max(0, xp));
    }

    public String getSelectedDrop(UUID uuid, String category) {
        Map<String, String> map = playerSelectedDrops.get(uuid);
        if (map != null && map.containsKey(category.toLowerCase())) {
            return map.get(category.toLowerCase());
        }
        return null;
    }

    public void setSelectedDrop(UUID uuid, String category, String dropId) {
        String cat = category.toLowerCase();
        if (dropId == null || dropId.equalsIgnoreCase("none")) {
            Map<String, String> map = playerSelectedDrops.get(uuid);
            if (map != null) map.remove(cat);
        } else {
            playerSelectedDrops.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                    .put(cat, dropId.toLowerCase());
        }
        int curXp = getCategoryXp(uuid, cat);
        DCCosmetics.getInstance().getDatabaseManager().saveRngPlayer(uuid, cat, dropId, curXp);
    }

    public int getCategoryXp(UUID uuid, String category) {
        return playerCategoryXp.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .getOrDefault(category.toLowerCase(), 0);
    }

    public void setCategoryXp(UUID uuid, String category, int xp) {
        String cat = category.toLowerCase();
        int safeXp = Math.max(0, xp);
        playerCategoryXp.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(cat, safeXp);
        String selDrop = getSelectedDrop(uuid, cat);
        DCCosmetics.getInstance().getDatabaseManager().saveRngPlayer(uuid, cat, selDrop, safeXp);
    }

    public void addCategoryXp(UUID uuid, String category, int delta) {
        setCategoryXp(uuid, category, getCategoryXp(uuid, category) + delta);
    }

    public int getRngXp(UUID uuid, String dropOrCategory) {
        String key = dropOrCategory.toLowerCase();
        if (categories.containsKey(key)) {
            return getCategoryXp(uuid, key);
        }
        // Find which category this drop belongs to
        for (Map.Entry<String, List<RngModel.Drop>> entry : dropTables.entrySet()) {
            for (RngModel.Drop d : entry.getValue()) {
                if (d.getDropId().equalsIgnoreCase(key)) {
                    return getCategoryXp(uuid, entry.getKey());
                }
            }
        }
        return getCategoryXp(uuid, key);
    }

    public void setRngXp(UUID uuid, String dropOrCategory, int xp) {
        String key = dropOrCategory.toLowerCase();
        if (categories.containsKey(key)) {
            setCategoryXp(uuid, key, xp);
            return;
        }
        for (Map.Entry<String, List<RngModel.Drop>> entry : dropTables.entrySet()) {
            for (RngModel.Drop d : entry.getValue()) {
                if (d.getDropId().equalsIgnoreCase(key)) {
                    setCategoryXp(uuid, entry.getKey(), xp);
                    return;
                }
            }
        }
        setCategoryXp(uuid, key, xp);
    }

    public RngModel.Drop getDrop(String category, String dropId) {
        List<RngModel.Drop> list = dropTables.get(category.toLowerCase());
        if (list == null) return null;
        for (RngModel.Drop d : list) {
            if (d.getDropId().equalsIgnoreCase(dropId)) return d;
        }
        return null;
    }

    public Map<String, RngModel.Category> getCategories() { return categories; }
    public Map<String, List<RngModel.Drop>> getDropTables() { return dropTables; }
    public RngSecurity getSecurity() { return security; }

    /**
     * Central Dual-Roll Execution Entrypoint.
     * Evaluates security, rolls both Global and Category tracks, advances Pity Core.
     */
    public void processAction(Player player, String categoryId, double generalBoostPct, int xpAmount, String sourceType, Entity victim) {
        if (player == null || !player.isOnline()) return;

        // Security check
        RngSecurity.SecurityResult sec = security.validateAction(player, victim, sourceType);
        if (!sec.allowed) {
            return;
        }

        int finalXp = (int) Math.max(1, Math.round(xpAmount * sec.xpMultiplier));
        double finalBoost = sec.forceZeroBoost ? 0.0 : generalBoostPct;

        // -------------------------------------------------------------
        // DUAL ROLL 1: GLOBAL GENERAL ROLL
        // -------------------------------------------------------------
        rollCategoryDrop(player, "general", finalBoost, finalXp);

        // -------------------------------------------------------------
        // DUAL ROLL 2: TARGET CATEGORY ROLL
        // -------------------------------------------------------------
        if (categoryId != null && !categoryId.equalsIgnoreCase("general")) {
            rollCategoryDrop(player, categoryId, 0.0, finalXp);
        }
    }

    /**
     * Hypixel-Style RNG Category Meter:
     * 1. Category XP accumulates unconditionally into the player's category pool.
     * 2. If a drop is selected, its meter multiplier scales up (to 3.0x).
     * 3. When category XP reaches the goal of the SELECTED drop, it is guaranteed on the next action.
     * 4. When the guaranteed drop hits, it consumes ONLY that item's goal XP; excess XP stays in the pool!
     */
    private void rollCategoryDrop(Player player, String category, double sourceBoostPct, int xpEarned) {
        UUID uuid = player.getUniqueId();
        List<RngModel.Drop> drops = dropTables.get(category.toLowerCase());
        if (drops == null || drops.isEmpty()) return;

        // 1. Accumulate XP into category pool unconditionally
        addCategoryXp(uuid, category, xpEarned);
        int totalCategoryXp = getCategoryXp(uuid, category);

        String selectedId = getSelectedDrop(uuid, category);
        RngModel.Drop selectedDrop = (selectedId != null && !selectedId.equalsIgnoreCase("none")) ? getDrop(category, selectedId) : null;

        // 2. Guaranteed Pull Trigger (If user selected this item and has met or exceeded goal)
        if (selectedDrop != null && totalCategoryXp >= selectedDrop.getGoalXp()) {
            grantDrop(player, selectedDrop, true);
            // Consume ONLY the goal amount of XP, preserving excess XP in the category pool
            int remainingXp = totalCategoryXp - selectedDrop.getGoalXp();
            setCategoryXp(uuid, category, Math.max(0, remainingXp));
            return;
        }

        // 3. Natural RNG Rolls (Jackpots)
        double sourceMult = 1.0 + (sourceBoostPct / 100.0);

        for (RngModel.Drop drop : drops) {
            boolean isSelected = selectedDrop != null && selectedDrop.getDropId().equalsIgnoreCase(drop.getDropId());
            int goalXp = drop.getGoalXp();

            // Meter scaling multiplier (1.0x to 3.0x based on progress towards this drop)
            double meterMult = 1.0;
            if (isSelected) {
                meterMult = 1.0 + (2.0 * Math.min(1.0, (double) totalCategoryXp / goalXp));
            }

            // Final probability: P_final = P_base * M_source * M_meter
            double finalChance = drop.getBaseChancePct() * sourceMult * meterMult;

            // Roll resolution: R in [0.00001, 100.0]
            double roll = ThreadLocalRandom.current().nextDouble(0.00001, 100.00001);
            if (roll <= finalChance) {
                grantDrop(player, drop, false);
                if (isSelected) {
                    int remainingXp = getCategoryXp(uuid, category) - goalXp;
                    setCategoryXp(uuid, category, Math.max(0, remainingXp));
                }
                return;
            }
        }
    }

    private void grantDrop(Player player, RngModel.Drop drop, boolean guaranteed) {
        // Execute commands
        String cmd = drop.getCommand().replace("%player%", player.getName());
        for (String sub : cmd.split(";")) {
            if (!sub.trim().isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), sub.trim());
            }
        }

        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.4f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        String tag = guaranteed ? "&7[&#D946EF&lRNG Guarantee&7]" : "&7[&#38BDF8&lRNG Jackpot&7]";
        String broadcast = dc.dcessentials.core.util.ColorUtil.colorize(
                tag + " &f" + player.getName() + " &7hit the legendary drop: " + drop.getDisplayName() + "&7!");
        Bukkit.broadcastMessage(broadcast);
    }

    public String getPlaceholder(Player player, String category) {
        UUID u = player.getUniqueId();
        int catXp = getCategoryXp(u, category);
        String sel = getSelectedDrop(u, category);
        if (sel == null || sel.equalsIgnoreCase("none")) {
            return String.format(Locale.US, "%,d XP (No Target)", catXp);
        }
        RngModel.Drop d = getDrop(category, sel);
        if (d == null) return String.format(Locale.US, "%,d XP", catXp);

        int goal = d.getGoalXp();
        int pct = Math.min(100, (int) Math.round(((double) catXp / goal) * 100));

        return String.format(Locale.US, "%,d/%,d (%d%%)", catXp, goal, pct);
    }
}
