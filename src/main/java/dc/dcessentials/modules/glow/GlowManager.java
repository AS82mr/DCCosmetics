package dc.dcessentials.modules.glow;

import dc.dccosmetics.DCCosmetics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GlowManager {

    private static GlowManager instance;
    private final DCCosmetics plugin;
    private final NamespacedKey glowKey;
    private final Map<UUID, BukkitTask> animatedGlowTasks = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeGlows = new ConcurrentHashMap<>();
    private final Set<UUID> suppressedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public enum GlowColor {
        AQUA("aqua", "&bAqua", ChatColor.AQUA),
        BLUE("blue", "&9Blue", ChatColor.BLUE),
        DARK_AQUA("dark_aqua", "&3Dark Aqua", ChatColor.DARK_AQUA),
        DARK_BLUE("dark_blue", "&1Dark Blue", ChatColor.DARK_BLUE),
        DARK_GRAY("dark_gray", "&8Dark Gray", ChatColor.DARK_GRAY),
        DARK_GREEN("dark_green", "&2Dark Green", ChatColor.DARK_GREEN),
        DARK_PURPLE("dark_purple", "&5Dark Purple", ChatColor.DARK_PURPLE),
        DARK_RED("dark_red", "&4Dark Red", ChatColor.DARK_RED),
        GOLD("gold", "&6Gold", ChatColor.GOLD),
        GRAY("gray", "&7Gray", ChatColor.GRAY),
        GREEN("green", "&aGreen", ChatColor.GREEN),
        LIGHT_PURPLE("light_purple", "&dLight Purple", ChatColor.LIGHT_PURPLE),
        RED("red", "&cRed", ChatColor.RED),
        WHITE("white", "&fWhite", ChatColor.WHITE),
        YELLOW("yellow", "&eYellow", ChatColor.YELLOW),
        RAINBOW("rainbow", "&cR&6a&ei&an&bb&9o&dw", null),
        CHRISTMAS("christmas", "&cChrist&amas", null),
        HALLOWEEN("halloween", "&6Hallo&eween", null);

        private final String id;
        private final String displayName;
        private final ChatColor chatColor;

        GlowColor(String id, String displayName, ChatColor chatColor) {
            this.id = id;
            this.displayName = displayName;
            this.chatColor = chatColor;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public ChatColor getChatColor() { return chatColor; }

        public static GlowColor fromId(String id) {
            if (id == null) return null;
            for (GlowColor gc : values()) {
                if (gc.id.equalsIgnoreCase(id) || gc.name().equalsIgnoreCase(id)) return gc;
            }
            return null;
        }
    }

    public GlowManager(DCCosmetics plugin) {
        instance = this;
        this.plugin = plugin;
        this.glowKey = new NamespacedKey(plugin, "player_glow_color");
        if (Bukkit.getScoreboardManager() != null) {
            initTeams();
        } else {
            Bukkit.getScheduler().runTask(plugin, this::initTeams);
        }
    }

    public static GlowManager getInstance() {
        return instance;
    }

    private void initTeams() {
        try {
            if (Bukkit.getScoreboardManager() == null) return;
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            for (GlowColor gc : GlowColor.values()) {
                if (gc.chatColor != null) {
                    String teamName = "dc_g_" + gc.id;
                    Team team = board.getTeam(teamName);
                    if (team == null) {
                        team = board.registerNewTeam(teamName);
                    }
                    team.setColor(gc.chatColor);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[GlowManager] Failed to initialize glow scoreboard teams: " + e.getMessage());
        }
    }

    public void setGlow(Player player, String colorId) {
        if (player == null || !player.isOnline()) return;

        if (colorId == null || colorId.equalsIgnoreCase("off") || colorId.equalsIgnoreCase("none")) {
            removeGlow(player);
            return;
        }

        GlowColor gc = GlowColor.fromId(colorId);
        if (gc == null) return;

        stopAnimation(player.getUniqueId());
        activeGlows.put(player.getUniqueId(), gc.getId());

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(glowKey, PersistentDataType.STRING, gc.getId());

        if (suppressedPlayers.contains(player.getUniqueId())) {
            return; // In wardrobe — suppressed until exit
        }

        applyGlowEffect(player, gc);
    }

    private void applyGlowEffect(Player player, GlowColor gc) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();

        if (gc.chatColor != null) {
            removeFromGlowTeams(player);
            String teamName = "dc_g_" + gc.id;
            Team team = board.getTeam(teamName);
            if (team != null) {
                team.addEntry(player.getName());
            }
            updateTabGlow(player, gc.chatColor);
            player.setGlowing(true);
        } else {
            player.setGlowing(true);
            startAnimatedGlow(player, gc);
        }
    }

    private void updateTabGlow(Player player, ChatColor color) {
        if (player == null || Bukkit.getPluginManager().getPlugin("TAB") == null) return;
        try {
            Class<?> tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Object api = tabApiClass.getMethod("getInstance").invoke(null);
            if (api == null) return;
            Object tabPlayer = tabApiClass.getMethod("getPlayer", UUID.class).invoke(api, player.getUniqueId());
            if (tabPlayer == null) return;
            Object nameTagManager = tabApiClass.getMethod("getNameTagManager").invoke(api);
            if (nameTagManager == null) return;

            Class<?> ntmClass = nameTagManager.getClass();
            java.lang.reflect.Method getOrig = null;
            java.lang.reflect.Method setPrefix = null;
            for (java.lang.reflect.Method m : ntmClass.getMethods()) {
                if (m.getName().equals("getOriginalPrefix") && m.getParameterCount() == 1) {
                    getOrig = m;
                } else if (m.getName().equals("setPrefix") && m.getParameterCount() == 2) {
                    setPrefix = m;
                }
            }

            if (getOrig != null && setPrefix != null) {
                String orig = (String) getOrig.invoke(nameTagManager, tabPlayer);
                if (orig == null) orig = "";
                if (color != null) {
                    setPrefix.invoke(nameTagManager, tabPlayer, orig + color.toString());
                } else {
                    setPrefix.invoke(nameTagManager, tabPlayer, orig);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[GlowManager] Failed to update TAB glow prefix: " + t.getMessage());
        }
    }

    private void startAnimatedGlow(Player player, GlowColor gc) {
        stopAnimation(player.getUniqueId());

        List<ChatColor> cycleColors;
        long periodTicks;

        if (gc == GlowColor.RAINBOW) {
            cycleColors = Arrays.asList(
                ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW,
                ChatColor.GREEN, ChatColor.AQUA, ChatColor.BLUE, ChatColor.LIGHT_PURPLE
            );
            periodTicks = 8L;
        } else if (gc == GlowColor.CHRISTMAS) {
            cycleColors = Arrays.asList(ChatColor.RED, ChatColor.DARK_GREEN);
            periodTicks = 15L;
        } else if (gc == GlowColor.HALLOWEEN) {
            cycleColors = Arrays.asList(ChatColor.GOLD, ChatColor.YELLOW);
            periodTicks = 15L;
        } else {
            return;
        }

        BukkitTask task = new BukkitRunnable() {
            private int index = 0;
            @Override
            public void run() {
                if (!player.isOnline() || suppressedPlayers.contains(player.getUniqueId())) {
                    cancel();
                    return;
                }
                ChatColor nextColor = cycleColors.get(index % cycleColors.size());
                index++;

                Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
                removeFromGlowTeams(player);
                for (GlowColor c : GlowColor.values()) {
                    if (c.chatColor == nextColor) {
                        Team t = board.getTeam("dc_g_" + c.id);
                        if (t != null) t.addEntry(player.getName());
                        break;
                    }
                }
                updateTabGlow(player, nextColor);
            }
        }.runTaskTimer(plugin, 0L, periodTicks);

        animatedGlowTasks.put(player.getUniqueId(), task);
    }

    private void stopAnimation(UUID uuid) {
        BukkitTask task = animatedGlowTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void removeGlow(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        stopAnimation(uuid);
        activeGlows.remove(uuid);

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(glowKey);

        updateTabGlow(player, null);
        removeFromGlowTeams(player);
        player.setGlowing(false);
    }

    public void suppressGlow(Player player) {
        if (player == null) return;
        suppressedPlayers.add(player.getUniqueId());
        stopAnimation(player.getUniqueId());
        updateTabGlow(player, null);
        removeFromGlowTeams(player);
        player.setGlowing(false);
    }

    public void restoreGlow(Player player) {
        if (player == null) return;
        suppressedPlayers.remove(player.getUniqueId());
        String current = getActiveGlow(player);
        if (current != null && !current.isEmpty()) {
            GlowColor gc = GlowColor.fromId(current);
            if (gc != null) {
                applyGlowEffect(player, gc);
            }
        }
    }

    public void loadPlayer(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String saved = pdc.get(glowKey, PersistentDataType.STRING);
        if (saved != null && !saved.isEmpty()) {
            activeGlows.put(player.getUniqueId(), saved);
            if (!suppressedPlayers.contains(player.getUniqueId())) {
                GlowColor gc = GlowColor.fromId(saved);
                if (gc != null) applyGlowEffect(player, gc);
            }
        }
    }

    public void unloadPlayer(Player player) {
        stopAnimation(player.getUniqueId());
        activeGlows.remove(player.getUniqueId());
        suppressedPlayers.remove(player.getUniqueId());
        removeFromGlowTeams(player);
    }

    private void removeFromGlowTeams(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (GlowColor gc : GlowColor.values()) {
            if (gc.chatColor != null) {
                Team team = board.getTeam("dc_g_" + gc.id);
                if (team != null && team.hasEntry(player.getName())) {
                    team.removeEntry(player.getName());
                }
            }
        }
    }

    public String getActiveGlow(Player player) {
        if (player == null) return null;
        String inMem = activeGlows.get(player.getUniqueId());
        if (inMem != null) return inMem;
        return player.getPersistentDataContainer().get(glowKey, PersistentDataType.STRING);
    }

    public String getActiveGlowFormatted(Player player) {
        String id = getActiveGlow(player);
        if (id == null) return "&cNone";
        GlowColor gc = GlowColor.fromId(id);
        return gc != null ? gc.getDisplayName() : "&cNone";
    }
}
