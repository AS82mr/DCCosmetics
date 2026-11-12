package dc.dcessentials.modules.boss;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.modules.event.EventManager;
import dc.dcessentials.modules.event.model.ServerEvent;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.event.plugin.TabLoadEvent;
import me.neznamy.tab.api.placeholder.PlaceholderManager;
import me.neznamy.tab.api.placeholder.PlayerPlaceholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Direct TAB v6.x integration for Active Events scoreboard lines and Emperor boss combat metrics.
 * Shares placeholder formatting with PlaceholderAPI (`DCEssentialsExpansion`) as a single source of truth.
 */
public class EmperorTabHook {

    private final DCCosmetics plugin;
    private final EmperorBossManager emperorManager;
    private final EventManager eventManager;

    private final List<PlayerPlaceholder> registeredPlaceholders = new ArrayList<>();
    private boolean hooked = false;
    private boolean reloadListenerRegistered = false;

    public EmperorTabHook(DCCosmetics plugin, EmperorBossManager emperorManager, EventManager eventManager) {
        this.plugin = plugin;
        this.emperorManager = emperorManager;
        this.eventManager = eventManager;
    }

    public void init() {
        if (hooked) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("TAB")) {
            plugin.getLogger().info("[EmperorTabHook] TAB plugin not detected; skipping direct TAB placeholder registration.");
            return;
        }

        try {
            registerPlaceholders();

            TabAPI api = TabAPI.getInstance();
            if (!reloadListenerRegistered && api.getEventBus() != null) {
                api.getEventBus().register(TabLoadEvent.class, event ->
                        Bukkit.getScheduler().runTask(plugin, this::registerPlaceholders));
                reloadListenerRegistered = true;
            }
            hooked = true;
            plugin.getLogger().info("[EmperorTabHook] Hooked into TAB API for %dragoncote_active_events_*% and %dragoncote_emperor_*%.");
        } catch (Throwable t) {
            plugin.getLogger().warning("[EmperorTabHook] Failed to hook into TAB API: " + t.getMessage());
        }
    }

    public void registerPlaceholders() {
        try {
            TabAPI api = TabAPI.getInstance();
            if (api == null) return;
            PlaceholderManager pm = api.getPlaceholderManager();
            if (pm == null) return;

            registeredPlaceholders.clear();
            registeredPlaceholders.add(pm.registerPlayerPlaceholder("%dragoncote_active_events_has%", 500, tp -> resolveTab(tp, "active_events_has")));
            registeredPlaceholders.add(pm.registerPlayerPlaceholder("%dragoncote_active_events_header%", 500, tp -> resolveTab(tp, "active_events_header")));
            registeredPlaceholders.add(pm.registerPlayerPlaceholder("%dragoncote_active_events_line%", 500, tp -> resolveTab(tp, "active_events_line")));
            registeredPlaceholders.add(pm.registerPlayerPlaceholder("%dragoncote_emperor_fighting%", 500, tp -> resolveTab(tp, "emperor_fighting")));
            registeredPlaceholders.add(pm.registerPlayerPlaceholder("%dragoncote_emperor_scale%", 500, tp -> resolveTab(tp, "emperor_scale")));
            registeredPlaceholders.add(pm.registerPlayerPlaceholder("%dragoncote_emperor_hp%", 500, tp -> resolveTab(tp, "emperor_hp")));
            registeredPlaceholders.add(pm.registerPlayerPlaceholder("%dragoncote_emperor_players%", 500, tp -> resolveTab(tp, "emperor_players")));

            pushRefreshAll();
        } catch (Throwable t) {
            plugin.getLogger().warning("[EmperorTabHook] Failed registering TAB placeholders: " + t.getMessage());
        }
    }

    private String resolveTab(TabPlayer tp, String key) {
        if (tp == null) return "";
        Object raw = tp.getPlayer();
        Player p = (raw instanceof Player bp) ? bp : Bukkit.getPlayer(tp.getUniqueId());
        if (p == null) return "";
        String val = resolvePlaceholder(emperorManager, eventManager, p, key);
        return val != null ? val : "";
    }

    public void pushRefreshAll() {
        if (!hooked || registeredPlaceholders.isEmpty()) return;
        try {
            TabAPI api = TabAPI.getInstance();
            if (api == null) return;
            for (TabPlayer tp : api.getOnlinePlayers()) {
                for (PlayerPlaceholder ph : registeredPlaceholders) {
                    ph.update(tp);
                }
            }
        } catch (Throwable ignored) {}
    }

    public void pushRefresh(Player player) {
        if (!hooked || player == null || registeredPlaceholders.isEmpty()) return;
        try {
            TabAPI api = TabAPI.getInstance();
            if (api == null) return;
            TabPlayer tp = api.getPlayer(player.getUniqueId());
            if (tp == null) return;
            for (PlayerPlaceholder ph : registeredPlaceholders) {
                ph.update(tp);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Unified placeholder resolver used by both TAB native placeholders and PlaceholderAPI (`dragoncote` / `dce`).
     * Returns null only if `key` does not match an active-event or emperor placeholder.
     */
    public static String resolvePlaceholder(EmperorBossManager emperor, EventManager events, Player player, String key) {
        if (player == null || key == null) return null;

        boolean fightingEmperor = (emperor != null && emperor.isFighting(player));
        EventManager.PersonalEvent personal = (events != null) ? events.getPersonalEvent(player) : null;
        ServerEvent activeMini = (events != null) ? events.getActiveMiniEvent() : null;
        boolean hasAny = fightingEmperor || personal != null || activeMini != null;

        return switch (key) {
            case "active_events_has" -> hasAny ? "true" : "false";
            case "active_events_header" -> hasAny ? ColorUtil.colorize("&eᴀᴄᴛɪᴠᴇ ᴇᴠᴇɴᴛꜱ:") : "";
            case "active_events_line" -> {
                if (fightingEmperor) {
                    double scale = emperor.getAngerScale();
                    int hp = emperor.getHpPercent();
                    String hpCol = (hp <= 25) ? "&c" : (hp <= 50) ? "&e" : "&a";
                    if (scale > 1.0) {
                        String scaleStr = String.format(Locale.US, "%.1f", scale);
                        yield ColorUtil.colorize("&f┃ &e⚔ &eEmperor: &6" + scaleStr + "x &8| " + hpCol + hp + "% hp");
                    }
                    yield ColorUtil.colorize("&f┃ &e⚔ &eEmperor: " + hpCol + hp + "% hp");
                }
                if (personal != null) {
                    yield ColorUtil.colorize("&f┃ &d★ &f" + personal.label());
                }
                if (activeMini != null) {
                    yield ColorUtil.colorize("&f┃ &e⚔ &f" + activeMini.getTitle() + " &aActive");
                }
                yield "";
            }
            case "emperor_fighting" -> fightingEmperor ? "true" : "false";
            case "emperor_scale" -> {
                double scale = (emperor != null) ? emperor.getAngerScale() : 1.0;
                yield String.format(Locale.US, "%.1f", scale) + "x";
            }
            case "emperor_hp" -> ((emperor != null) ? emperor.getHpPercent() : 100) + "%";
            case "emperor_players" -> String.valueOf((emperor != null) ? emperor.getPlayerCount() : 1);
            default -> null;
        };
    }

    public void shutdown() {
        hooked = false;
        registeredPlaceholders.clear();
    }
}
