package dc.dcessentials.modules.pity;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.teleportation.KeystoneProfile;
import dc.dcessentials.modules.teleportation.KeystoneTier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hypixel-Style Pity System (100% Decoupled from RNG Meter).
 *
 * Distinct Droptable: Essential gameplay items (EMP Laser Deactivator, Tesseract Energy,
 * Stabilization Coils, Catalyst Cores) instead of vanity cosmetics.
 *
 * Dynamic Odds Formula:
 * Odds = 1 / max(1, N - k)
 * Where N is the category goal threshold, and k is the player's accumulated counter.
 * Example (N = 100):
 *   k = 0  -> 1/100 (1%)
 *   k = 50 -> 1/50  (2%)
 *   k = 99 -> 1/1   (100% Guaranteed Drop!)
 *
 * On drop, k is immediately reset to 0.
 */
public class PityManager {

    private final Map<String, PityCategory> categories = new LinkedHashMap<>();
    // UUID -> (Category -> Counter k)
    private final Map<UUID, Map<String, Integer>> playerCounters = new ConcurrentHashMap<>();

    public void registerDefaults() {
        categories.clear();

        // 1. Combat Mobs: EMP Laser Deactivator & Tesseract Energy
        registerCategory(new PityCategory(
                "combat_mobs",
                "Hostile Mob Combat",
                Material.IRON_SWORD,
                100,
                "&#38BDF8&l[Utility] EMP Laser Deactivator & Tesseract Energy (+25)",
                "give %player% iron_ingot 1",
                25
        ));

        // 2. Fishing: Marine Stabilization Coil
        registerCategory(new PityCategory(
                "fishing",
                "Aquatic Fishing & Salvage",
                Material.FISHING_ROD,
                80,
                "&#38BDF8&l[Utility] Marine Stabilization Coil (+15 Energy)",
                "give %player% nautilus_shell 1",
                15
        ));

        // 3. Farming: Botanical Catalyst Core
        registerCategory(new PityCategory(
                "farming",
                "Botanical Cultivation & Harvest",
                Material.GOLDEN_HOE,
                150,
                "&#38BDF8&l[Utility] Botanical Catalyst Core (+10 Energy)",
                "give %player% glow_berries 4",
                10
        ));

        // 4. Mining: Keystone Matrix Crystal
        registerCategory(new PityCategory(
                "mining",
                "Subterranean Mining & Extraction",
                Material.DIAMOND_PICKAXE,
                200,
                "&#38BDF8&l[Utility] Keystone Matrix Crystal (+20 Energy)",
                "give %player% amethyst_cluster 2",
                20
        ));
    }

    public void registerCategory(PityCategory category) {
        categories.put(category.getId().toLowerCase(), category);
    }

    public PityCategory getCategory(String id) {
        if (id == null) return null;
        String key = id.toLowerCase();
        if (categories.containsKey(key)) return categories.get(key);
        // Aliases
        if (key.equals("mobs") || key.equals("combat") || key.equals("general") || key.equals("overworld")) {
            return categories.get("combat_mobs");
        }
        return null;
    }

    public Map<String, PityCategory> getCategories() {
        return categories;
    }

    public int getPlayerCounter(UUID uuid, String category) {
        PityCategory cat = getCategory(category);
        String catId = cat != null ? cat.getId() : category.toLowerCase();
        return playerCounters.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .getOrDefault(catId, 0);
    }

    public void setPlayerCounter(UUID uuid, String category, int counter) {
        PityCategory cat = getCategory(category);
        String catId = cat != null ? cat.getId() : category.toLowerCase();
        int safeCounter = Math.max(0, counter);
        playerCounters.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(catId, safeCounter);
        DCCosmetics.getInstance().getDatabaseManager().savePityPlayer(uuid, catId, safeCounter);
    }

    public void setInitialCounter(UUID uuid, String category, int counter) {
        PityCategory cat = getCategory(category);
        String catId = cat != null ? cat.getId() : category.toLowerCase();
        playerCounters.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(catId, Math.max(0, counter));
    }

    /**
     * Executes a pity roll for an action in the specified category.
     * Dynamic odds: 1 / (N - k)
     */
    public void processAction(Player player, String categoryId) {
        if (player == null || !player.isOnline()) return;

        PityCategory cat = getCategory(categoryId);
        if (cat == null) return;

        UUID uuid = player.getUniqueId();
        int currentK = getPlayerCounter(uuid, cat.getId());
        int maxN = cat.getMaxThreshold();

        // Dynamic denominator: D = max(1, N - k)
        int denominator = Math.max(1, maxN - currentK);

        // Roll: 1 in denominator
        int roll = ThreadLocalRandom.current().nextInt(denominator);
        if (roll == 0) {
            // PITY DROP HIT!
            setPlayerCounter(uuid, cat.getId(), 0);

            // Execute utility reward command(s)
            String cmd = cat.getCommand().replace("%player%", player.getName());
            for (String subCmd : cmd.split(";")) {
                if (!subCmd.trim().isEmpty()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), subCmd.trim());
                }
            }

            // Keystone / Tesseract energy bonus
            if (cat.getEnergyBonus() > 0) {
                KeystoneProfile kp = DCCosmetics.getInstance().getTeleportationManager().getProfile(player);
                if (kp != null && kp.getTier() != KeystoneTier.NONE) {
                    kp.addEnergy(cat.getEnergyBonus());
                }
            }

            // Audio-visual feedback
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.5f);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.9f);

            // Standard Dragoncote Server Chat System Notification
            DCMessage.sendGeneral(player, "Pity Drop Triggered: " + cat.getDropName() + " &#0CBF93(&b1/" + denominator + " &7odds&#0CBF93)!");
        } else {
            // Miss: advance counter towards threshold
            int nextK = Math.min(maxN - 1, currentK + 1);
            setPlayerCounter(uuid, cat.getId(), nextK);
        }
    }

    public String getPlaceholder(Player player, String params) {
        PityCategory cat = getCategory(params);
        if (cat == null) return "N/A";

        UUID u = player.getUniqueId();
        int k = getPlayerCounter(u, cat.getId());
        int n = cat.getMaxThreshold();
        int d = Math.max(1, n - k);

        return k + "/" + n + " (1/" + d + ")";
    }

    public int getProgress(Player player, String category) {
        if (player == null) return 0;
        return getPlayerCounter(player.getUniqueId(), category);
    }

    public int getMax(String category) {
        PityCategory cat = getCategory(category);
        return cat != null ? cat.getMaxThreshold() : 100;
    }

    public String getOdds(Player player, String category) {
        if (player == null) return "1/100";
        PityCategory cat = getCategory(category);
        int n = cat != null ? cat.getMaxThreshold() : 100;
        int k = getPlayerCounter(player.getUniqueId(), category);
        int d = Math.max(1, n - k);
        return "1/" + d;
    }

    public int getPercent(Player player, String category) {
        if (player == null) return 0;
        PityCategory cat = getCategory(category);
        int n = cat != null ? cat.getMaxThreshold() : 100;
        int k = getPlayerCounter(player.getUniqueId(), category);
        return Math.min(100, Math.max(0, (int) Math.round(((double) k / n) * 100)));
    }

    public String getProgressBar(Player player, String category) {
        int pct = getPercent(player, category);
        int filled = Math.min(10, Math.max(0, pct / 10));
        StringBuilder sb = new StringBuilder("&a");
        for (int i = 0; i < filled; i++) sb.append("■");
        sb.append("&7");
        for (int i = filled; i < 10; i++) sb.append("□");
        return sb.toString();
    }
}
