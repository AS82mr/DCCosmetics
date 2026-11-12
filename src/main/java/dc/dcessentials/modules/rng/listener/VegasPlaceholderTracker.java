package dc.dcessentials.modules.rng.listener;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Robust Placeholder-Based Vegas Casino Tracker.
 *
 * Tracks player Vegas stats directly via native PlaceholderAPI placeholders:
 * - %vegas_victories% -> Awards 3x TRIPLE Casino RNG XP on victory
 * - %vegas_defeats%   -> Awards 1x BASE Casino RNG XP on defeat
 * - %vegas_bonuses%   -> Awards 3x TRIPLE Casino RNG XP on bonus
 * - %vegas_draws%     -> Awards 1x BASE Casino RNG XP on draw
 *
 * Fully decoupled from internal Vegas class implementations, making it 100%
 * robust against version discrepancies.
 */
public class VegasPlaceholderTracker implements Listener {

    private static class VegasSnapshot {
        int victories;
        int defeats;
        int bonuses;
        int draws;
        double wonMoney;
        double lostMoney;

        VegasSnapshot(int v, int d, int b, int dr, double won, double lost) {
            this.victories = v;
            this.defeats = d;
            this.bonuses = b;
            this.draws = dr;
            this.wonMoney = won;
            this.lostMoney = lost;
        }
    }

    private static final Map<UUID, VegasSnapshot> snapshots = new ConcurrentHashMap<>();
    private static BukkitTask periodicTask = null;

    public static void start() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            DCCosmetics.getInstance().getLogger().warning("[DCEssentials] PlaceholderAPI not found. Vegas tracker inactive.");
            return;
        }

        Bukkit.getPluginManager().registerEvents(new VegasPlaceholderTracker(), DCCosmetics.getInstance());

        // Initialize baseline snapshots for currently online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            samplePlayer(p, true);
        }

        // Periodic check every 20 ticks (1 second)
        periodicTask = Bukkit.getScheduler().runTaskTimer(
                DCCosmetics.getInstance(),
                () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        samplePlayer(p, false);
                    }
                },
                20L, 20L
        );

        DCCosmetics.getInstance().getLogger().info("[DCEssentials] Vegas Placeholder Tracker initialized (1s poll + on-action checks active).");
    }

    public static void stop() {
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }
        snapshots.clear();
    }

    /**
     * Samples player's Vegas placeholders and awards RNG progress on deltas.
     * @param initOnly if true, records current values without awarding progress (baseline)
     */
    public static void samplePlayer(Player player, boolean initOnly) {
        if (player == null || !player.isOnline()) return;

        UUID uuid = player.getUniqueId();

        try {
            String vStr = PlaceholderAPI.setPlaceholders(player, "%vegas_victories%");
            String dStr = PlaceholderAPI.setPlaceholders(player, "%vegas_defeats%");
            String bStr = PlaceholderAPI.setPlaceholders(player, "%vegas_bonuses%");
            String drStr = PlaceholderAPI.setPlaceholders(player, "%vegas_draws%");
            String wStr = PlaceholderAPI.setPlaceholders(player, "%vegas_won_money%");
            String lStr = PlaceholderAPI.setPlaceholders(player, "%vegas_lost_money%");

            // If PlaceholderAPI doesn't recognize the placeholder, it returns the string unchanged
            if (vStr.contains("%vegas_")) return;

            int curV = parseIntSafe(vStr);
            int curD = parseIntSafe(dStr);
            int curB = parseIntSafe(bStr);
            int curDr = parseIntSafe(drStr);
            double curWon = parseDoubleSafe(wStr);
            double curLost = parseDoubleSafe(lStr);

            VegasSnapshot old = snapshots.get(uuid);

            if (old == null || initOnly) {
                // Initialize baseline
                snapshots.put(uuid, new VegasSnapshot(curV, curD, curB, curDr, curWon, curLost));
                return;
            }

            // ── 1. Victory Check (3x Multiplier) ─────────────────────────
            if (curV > old.victories) {
                int deltaV = curV - old.victories;
                double deltaWon = Math.max(0, curWon - old.wonMoney);
                int baseXp = (int) Math.max(30, Math.min(250, Math.round(30 + deltaWon * 0.15)));
                int finalXp = (baseXp * 3) * deltaV; // 3x Win Bonus

                DCCosmetics.getInstance().getRngManager().processAction(player, "casino", 25.0, finalXp, "casino", null);
                DCMessage.sendGeneral(player, "Casino Victory recorded! Awarded &#FFFFFF+" + finalXp + " Casino RNG XP &#0CBF93(&b3x Win Bonus&#0CBF93)!");
            }

            // ── 2. Defeat Check (1x Base Multiplier) ─────────────────────
            if (curD > old.defeats) {
                int deltaD = curD - old.defeats;
                double deltaLost = Math.max(0, curLost - old.lostMoney);
                int baseXp = (int) Math.max(25, Math.min(200, Math.round(25 + deltaLost * 0.1))) * deltaD;

                DCCosmetics.getInstance().getRngManager().processAction(player, "casino", 0.0, baseXp, "casino", null);
                DCMessage.sendGeneral(player, "Casino Defeat recorded. Awarded &#FFFFFF+" + baseXp + " Casino RNG XP &#0CBF93(Base Progress).");
            }

            // ── 3. Bonus Check (3x Multiplier) ───────────────────────────
            if (curB > old.bonuses) {
                int deltaB = curB - old.bonuses;
                int xp = 100 * deltaB;
                DCCosmetics.getInstance().getRngManager().processAction(player, "casino", 25.0, xp, "casino", null);
                DCMessage.sendGeneral(player, "Casino Bonus recorded! Awarded &#FFFFFF+" + xp + " Casino RNG XP &#0CBF93!");
            }

            // ── 4. Draw Check (1x Multiplier) ────────────────────────────
            if (curDr > old.draws) {
                int deltaDr = curDr - old.draws;
                int xp = 30 * deltaDr;
                DCCosmetics.getInstance().getRngManager().processAction(player, "casino", 0.0, xp, "casino", null);
            }

            // Update snapshot
            old.victories = curV;
            old.defeats = curD;
            old.bonuses = curB;
            old.draws = curDr;
            old.wonMoney = curWon;
            old.lostMoney = curLost;

        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
            samplePlayer(event.getPlayer(), true);
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        snapshots.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            // Trigger quick sampling after player clicks in a casino inventory
            Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
                samplePlayer(player, false);
            }, 3L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        // Trigger quick sampling on table/machine interact
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
            samplePlayer(player, false);
        }, 5L);
    }

    private static int parseIntSafe(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.trim().replace(",", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private static double parseDoubleSafe(String s) {
        if (s == null) return 0.0;
        try {
            return Double.parseDouble(s.trim().replace(",", ""));
        } catch (Exception e) {
            return 0.0;
        }
    }
}
