package dc.dcessentials.modules.event;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.event.gui.EventCalendarGUI;
import dc.dcessentials.modules.event.model.ServerEvent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core Event Engine: Manages World Cycle (Sanctuary Dawn -> Void Convergence -> Glacial Grand Prix),
 * 60-second evacuation alerts, auto-evacuation to Mainland (lowercity), /spawn dynamic redirection,
 * ambient crow/particle effects, and scheduled mini-events (Colosseum KotH, Adapt EXP Boost).
 */
public class EventManager implements Listener {

    private final DCCosmetics plugin;
    private final File configFile;
    private FileConfiguration config;

    private String activeSpawnWorld = "spawn_main";
    private String evacuationWorld = "lowercity";
    private String evacuationCommand = "iris teleport player=%player% world=minecraft:lowercity";
    private String evacuationFallbackCommand = "mw tp lowercity %player%";
    private int evacuationWarningSeconds = 60;
    private boolean redirectSpawnCommand = true;
    private boolean enableAmbientEffects = true;

    private final List<String> cycleOrder = new ArrayList<>();
    private final Map<String, ServerEvent> worldCycleEvents = new LinkedHashMap<>();
    private final Map<String, ServerEvent> miniEvents = new LinkedHashMap<>();
    private final Map<UUID, PersonalEvent> personalEvents = new ConcurrentHashMap<>();

    private ServerEvent currentCycleEvent = null;
    private int cycleIndex = 0;
    private long cycleEndTimeMillis = 0;
    private boolean isEvacuating = false;

    private BukkitTask tickTask;
    private int ambientCounter = 0;
    private EventCalendarGUI calendarGUI;

    public EventManager(DCCosmetics plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "events.yml");
    }

    public void init() {
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.calendarGUI = new EventCalendarGUI(this);
        Bukkit.getPluginManager().registerEvents(this.calendarGUI, plugin);
        startScheduleLoop();
        plugin.getLogger().info("[EventManager] Loaded " + worldCycleEvents.size() + " World Cycle events and "
                + miniEvents.size() + " mini-events. Active realm: " + activeSpawnWorld);
    }

    public void loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        ConfigurationSection settings = config.getConfigurationSection("settings");
        if (settings != null) {
            this.evacuationWorld = settings.getString("evacuation_world", "lowercity");
            this.evacuationCommand = settings.getString("evacuation_command", "iris teleport player=%player% world=minecraft:lowercity");
            this.evacuationFallbackCommand = settings.getString("evacuation_fallback_command", "mw tp lowercity %player%");
            this.evacuationWarningSeconds = settings.getInt("evacuation_warning_seconds", 60);
            this.redirectSpawnCommand = settings.getBoolean("redirect_spawn_command", true);
            this.enableAmbientEffects = settings.getBoolean("enable_ambient_effects", true);
        }

        cycleOrder.clear();
        worldCycleEvents.clear();
        miniEvents.clear();

        ConfigurationSection cyclesSec = config.getConfigurationSection("world_cycles");
        if (cyclesSec != null) {
            List<String> order = cyclesSec.getStringList("cycle_order");
            if (order.isEmpty()) {
                order = Arrays.asList("sanctuary_dawn", "void_convergence", "glacial_grand_prix");
            }
            cycleOrder.addAll(order);

            ConfigurationSection eventsSec = cyclesSec.getConfigurationSection("events");
            if (eventsSec != null) {
                for (String key : eventsSec.getKeys(false)) {
                    ConfigurationSection cs = eventsSec.getConfigurationSection(key);
                    if (cs == null) continue;

                    String title = cs.getString("title", key);
                    String subtitle = cs.getString("subtitle", "");
                    String world = cs.getString("world", "spawn_main");
                    Material mat = Material.matchMaterial(cs.getString("icon", "NETHER_STAR"));
                    int cmd = cs.getInt("custom_model_data", 0);
                    int dur = cs.getInt("duration_minutes", 60);
                    int interval = cs.getInt("interval_minutes", 180);

                    ServerEvent se = new ServerEvent(key, title, subtitle, world, mat, dur, interval, true);
                    se.setCustomModelData(cmd);
                    se.setEvacuationWorld(cs.getString("evacuation_world", evacuationWorld));
                    se.setStartCommand(cs.getString("start_command", "none"));
                    se.setEndCommand(cs.getString("end_command", "none"));
                    se.setSoundEffect(cs.getString("sound", "UI_TOAST_CHALLENGE_COMPLETE"));
                    se.setRewardSummary(cs.getString("reward_summary", "Seasonal Tokens & Global XP"));
                    se.setLore(cs.getStringList("lore"));
                    se.setPerks(cs.getStringList("perks"));

                    worldCycleEvents.put(key.toLowerCase(), se);
                }
            }
        }

        ConfigurationSection miniSec = config.getConfigurationSection("mini_events");
        if (miniSec != null) {
            long now = System.currentTimeMillis();
            for (String key : miniSec.getKeys(false)) {
                ConfigurationSection ms = miniSec.getConfigurationSection(key);
                if (ms == null) continue;

                String title = ms.getString("title", key);
                String subtitle = ms.getString("subtitle", "");
                Material mat = Material.matchMaterial(ms.getString("icon", "DIAMOND_SWORD"));
                int cmd = ms.getInt("custom_model_data", 0);
                int dur = ms.getInt("duration_minutes", 20);
                int interval = ms.getInt("interval_minutes", 120);

                ServerEvent se = new ServerEvent(key, title, subtitle, "", mat, dur, interval, false);
                se.setCustomModelData(cmd);
                se.setWarpTarget(ms.getString("warp", ""));
                se.setStartCommand(ms.getString("start_command", "none"));
                se.setEndCommand(ms.getString("end_command", "none"));
                se.setSoundEffect(ms.getString("sound", "ENTITY_PLAYER_LEVELUP"));
                se.setRewardSummary(ms.getString("reward_summary", "Tokens & Honors"));
                se.setLore(ms.getStringList("lore"));
                se.setPerks(ms.getStringList("perks"));

                // Stagger start times so they don't all trigger simultaneously
                long offset = (long) (Math.random() * 15 * 60 * 1000L) + (5 * 60 * 1000L);
                se.setNextRunTimeMillis(now + offset);
                miniEvents.put(key.toLowerCase(), se);
            }
        }

        // Initialize active world cycle
        if (!cycleOrder.isEmpty()) {
            String initialKey = cycleOrder.get(0);
            currentCycleEvent = worldCycleEvents.get(initialKey);
            if (currentCycleEvent != null) {
                activeSpawnWorld = currentCycleEvent.getWorldName();
                cycleEndTimeMillis = System.currentTimeMillis() + (currentCycleEvent.getDurationMinutes() * 60L * 1000L);
                currentCycleEvent.setRunning(true);
                currentCycleEvent.setStartTimeMillis(System.currentTimeMillis());
                currentCycleEvent.setEndTimeMillis(cycleEndTimeMillis);
            }
        }
    }

    private void createDefaultConfig() {
        configFile.getParentFile().mkdirs();
        FileConfiguration def = new YamlConfiguration();

        def.set("settings.evacuation_world", "lowercity");
        def.set("settings.evacuation_command", "iris teleport player=%player% world=minecraft:lowercity");
        def.set("settings.evacuation_fallback_command", "mw tp lowercity %player%");
        def.set("settings.evacuation_warning_seconds", 60);
        def.set("settings.redirect_spawn_command", true);
        def.set("settings.enable_ambient_effects", true);

        def.set("world_cycles.cycle_order", Arrays.asList("sanctuary_dawn", "void_convergence", "glacial_grand_prix"));

        // 1. Sanctuary Dawn (spawn_main)
        def.set("world_cycles.events.sanctuary_dawn.title", "&6&lSanctuary Dawn");
        def.set("world_cycles.events.sanctuary_dawn.subtitle", "&eThe Prime Timeline");
        def.set("world_cycles.events.sanctuary_dawn.world", "spawn_main");
        def.set("world_cycles.events.sanctuary_dawn.icon", "SUNFLOWER");
        def.set("world_cycles.events.sanctuary_dawn.custom_model_data", 0);
        def.set("world_cycles.events.sanctuary_dawn.duration_minutes", 180);
        def.set("world_cycles.events.sanctuary_dawn.sound", "UI_TOAST_CHALLENGE_COMPLETE");
        def.set("world_cycles.events.sanctuary_dawn.perks", Arrays.asList(
                "&7• Full access to Citadel Plaza, Market & Academy",
                "&7• Normal timeline quest progression with Corvus",
                "&7• Standard merchant vendor trading & bank"
        ));
        def.set("world_cycles.events.sanctuary_dawn.lore", Arrays.asList(
                "&7The radiant prime timeline of Dragoncote.",
                "&7Bathed in warm sunlight, all city operations",
                "&7are operating at peak efficiency."
        ));

        // 2. Void Convergence (spawn_bw)
        def.set("world_cycles.events.void_convergence.title", "&8&lThe Void Convergence");
        def.set("world_cycles.events.void_convergence.subtitle", "&5Eclipse of Shadows");
        def.set("world_cycles.events.void_convergence.world", "spawn_bw");
        def.set("world_cycles.events.void_convergence.icon", "ENDER_EYE");
        def.set("world_cycles.events.void_convergence.custom_model_data", 0);
        def.set("world_cycles.events.void_convergence.duration_minutes", 60);
        def.set("world_cycles.events.void_convergence.sound", "ENTITY_WITHER_SPAWN");
        def.set("world_cycles.events.void_convergence.perks", Arrays.asList(
                "&5• Monochrome Shadow World & Raven Swarms",
                "&5• Spooky Dimensional Rifts active across the plaza",
                "&5• Resistance operatives whisper encrypted secrets",
                "&5• +25% Shadow drop rates across all regions"
        ));
        def.set("world_cycles.events.void_convergence.lore", Arrays.asList(
                "&7The dimensional tether fractures, plunging",
                "&7the citadel into an eerie monochrome void.",
                "&7Crows circle overhead as dark secrets emerge."
        ));

        // 3. Glacial Grand Prix (spawn_snow)
        def.set("world_cycles.events.glacial_grand_prix.title", "&b&lGlacial Frost Grand Prix");
        def.set("world_cycles.events.glacial_grand_prix.subtitle", "&fWinter Solstice & IceBoat Trials");
        def.set("world_cycles.events.glacial_grand_prix.world", "spawn_snow");
        def.set("world_cycles.events.glacial_grand_prix.icon", "PACKED_ICE");
        def.set("world_cycles.events.glacial_grand_prix.custom_model_data", 0);
        def.set("world_cycles.events.glacial_grand_prix.duration_minutes", 60);
        def.set("world_cycles.events.glacial_grand_prix.sound", "ENTITY_PLAYER_LEVELUP");
        def.set("world_cycles.events.glacial_grand_prix.perks", Arrays.asList(
                "&b• IceBoat Racing Championship tournament queue open",
                "&b• Snow Colosseum PvP Duel Arenas active",
                "&b• Frozen cosmetic crates & winter souvenirs",
                "&b• 1.5x Speed and Agility in snow districts"
        ));
        def.set("world_cycles.events.glacial_grand_prix.lore", Arrays.asList(
                "&7A sub-zero polar front locks the hub in ice.",
                "&7Pilots gather for high-octane boat races",
                "&7and intense winter Colosseum trial matches."
        ));

        // Mini Events
        // Colosseum KotH
        def.set("mini_events.colosseum_koth.title", "&c&lColosseum King of the Hill");
        def.set("mini_events.colosseum_koth.subtitle", "&4Combat Master Valerius' Arena");
        def.set("mini_events.colosseum_koth.icon", "NETHERITE_SWORD");
        def.set("mini_events.colosseum_koth.custom_model_data", 0);
        def.set("mini_events.colosseum_koth.duration_minutes", 20);
        def.set("mini_events.colosseum_koth.interval_minutes", 120);
        def.set("mini_events.colosseum_koth.warp", "colosseum");
        def.set("mini_events.colosseum_koth.start_command", "none");
        def.set("mini_events.colosseum_koth.end_command", "none");
        def.set("mini_events.colosseum_koth.sound", "ITEM_TRIDENT_THUNDER");
        def.set("mini_events.colosseum_koth.reward_summary", "Colosseum Glory Tokens & Warrior Title");
        def.set("mini_events.colosseum_koth.lore", Arrays.asList(
                "&7Hold the central hill in the Grand Colosseum.",
                "&7Earn exclusive PvP tokens, leaderboard clout,",
                "&7and the blessing of Combat Master Valerius."
        ));
        def.set("mini_events.colosseum_koth.perks", Arrays.asList(
                "&c• Double PvP Honor points inside the Colosseum",
                "&c• Direct warp via &f/warp colosseum"
        ));

        // Skill EXP Surge
        def.set("mini_events.adapt_exp_surge.title", "&a&lSkill EXP Surge");
        def.set("mini_events.adapt_exp_surge.subtitle", "&22x Skill Experience Across the Realm");
        def.set("mini_events.adapt_exp_surge.icon", "EXPERIENCE_BOTTLE");
        def.set("mini_events.adapt_exp_surge.custom_model_data", 0);
        def.set("mini_events.adapt_exp_surge.duration_minutes", 30);
        def.set("mini_events.adapt_exp_surge.interval_minutes", 180);
        def.set("mini_events.adapt_exp_surge.warp", "");
        def.set("mini_events.adapt_exp_surge.start_command", "none");
        def.set("mini_events.adapt_exp_surge.end_command", "none");
        def.set("mini_events.adapt_exp_surge.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        def.set("mini_events.adapt_exp_surge.reward_summary", "Double Experience for all Skills");
        def.set("mini_events.adapt_exp_surge.lore", Arrays.asList(
                "&7An arcane surge energizes the island ley lines.",
                "&7Earn double experience towards all Skills",
                "&7including Mining, Combat, Woodcutting, and Foraging."
        ));
        def.set("mini_events.adapt_exp_surge.perks", Arrays.asList(
                "&a• 2x Multiplier on all Skill gains",
                "&a• Applies across all dimensions automatically"
        ));

        // Supersonic IceBoat Rally
        def.set("mini_events.iceboat_rally.title", "&3&lSupersonic IceBoat Rally");
        def.set("mini_events.iceboat_rally.subtitle", "&bDrift Track Open & Time Trials");
        def.set("mini_events.iceboat_rally.icon", "OAK_BOAT");
        def.set("mini_events.iceboat_rally.custom_model_data", 0);
        def.set("mini_events.iceboat_rally.duration_minutes", 15);
        def.set("mini_events.iceboat_rally.interval_minutes", 90);
        def.set("mini_events.iceboat_rally.warp", "iceboat");
        def.set("mini_events.iceboat_rally.start_command", "none");
        def.set("mini_events.iceboat_rally.end_command", "none");
        def.set("mini_events.iceboat_rally.sound", "ENTITY_BOAT_PADDLE_WATER");
        def.set("mini_events.iceboat_rally.reward_summary", "Speedster Trophy & Drift Particles");
        def.set("mini_events.iceboat_rally.lore", Arrays.asList(
                "&7High-speed ice racing circuit heats are live!",
                "&7Jump into your boat and post the top lap time."
        ));
        def.set("mini_events.iceboat_rally.perks", Arrays.asList(
                "&b• Ice drift physics enabled",
                "&b• Clickable warp available: &f/warp iceboat"
        ));

        try {
            def.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[EventManager] Failed to save default events.yml: " + e.getMessage());
        }
    }

    private void startScheduleLoop() {
        if (tickTask != null) tickTask.cancel();

        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickSecond();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void tickSecond() {
        long now = System.currentTimeMillis();

        // 1. Process World Cycle Countdown & Evacuation
        if (currentCycleEvent != null) {
            long remainingSec = Math.max(0, (cycleEndTimeMillis - now) / 1000L);

            if (remainingSec <= evacuationWarningSeconds && !isEvacuating) {
                isEvacuating = true;
                broadcastEvacuationWarning(remainingSec);
            } else if (isEvacuating) {
                if (remainingSec == 30 || remainingSec == 10 || (remainingSec <= 5 && remainingSec > 0)) {
                    broadcastEvacuationWarning(remainingSec);
                }
            }

            if (remainingSec <= 0) {
                executeEvacuationAndAdvanceCycle();
            }
        }

        // 2. Process Mini-Events
        for (ServerEvent event : miniEvents.values()) {
            if (event.isRunning()) {
                if (now >= event.getEndTimeMillis()) {
                    endMiniEvent(event);
                }
            } else {
                if (now >= event.getNextRunTimeMillis()) {
                    startMiniEvent(event);
                }
            }
        }

        // 3. Process Ambient Effects
        if (enableAmbientEffects) {
            ambientCounter++;
            if (ambientCounter >= 7) {
                ambientCounter = 0;
                processAmbientEffects();
            }
        }
    }

    /**
     * Broadcasts the 60s/30s/10s evacuation alert to players in the collapsing spawn world.
     */
    private void broadcastEvacuationWarning(long remainingSec) {
        World world = Bukkit.getWorld(activeSpawnWorld);
        if (world == null) return;

        List<Player> players = world.getPlayers();
        if (players.isEmpty()) return;

        for (Player p : players) {
            // Visual Title
            String secColor = remainingSec <= 10 ? "§c" : "§e";
            p.sendTitle(ColorUtil.colorize("&cDimensional Rift"), ColorUtil.colorize("&eRealm collapsing in " + secColor + remainingSec + "s&e! Evacuate now!"), 0, 40, 10);

            // Warning Sound
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, remainingSec <= 10 ? 1.5f : 0.8f);

            // Interactive Clickable Evacuate Component
            TextComponent msg = new TextComponent(ColorUtil.colorize("\n&8&m----------------------------------------------------\n"));
            TextComponent alert = new TextComponent(ColorUtil.colorize(DCMessage.PREFIX_WARNING + DCMessage.BODY_WARNING + "Dimensional shift incoming: &#FFFFFF" + activeSpawnWorld + " &#FDE047closes in &#EF4444" + remainingSec + "s&#FDE047!\n"));
            TextComponent prompt = new TextComponent(ColorUtil.colorize("  &7All adventurers must retreat to the mainland LowerCity.\n\n"));

            TextComponent btn = new TextComponent(ColorUtil.colorize("  &7&l<&#F59E0BE&#F97316v&#FB923Ca&#FBBF24c&#FCD34Du&#FDE047a&#F59E0Bt&#F97316e&7&l> &#FEF08AClick here to evacuate to Mainland LowerCity\n"));
            btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lowercity"));
            btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ColorUtil.colorize("&aClick to instantly evacuate to Mainland LowerCity")).create()));

            TextComponent footer = new TextComponent(ColorUtil.colorize("&8&m----------------------------------------------------\n"));

            p.spigot().sendMessage(msg, alert, prompt, btn, footer);
        }
    }

    /**
     * Safely teleports all remaining players out of the expiring world and transitions to the next cycle.
     */
    private void executeEvacuationAndAdvanceCycle() {
        World oldWorld = Bukkit.getWorld(activeSpawnWorld);
        if (oldWorld != null) {
            List<Player> remaining = new ArrayList<>(oldWorld.getPlayers());
            for (Player p : remaining) {
                evacuatePlayer(p);
            }
        }

        // Advance to next world cycle
        if (!cycleOrder.isEmpty()) {
            cycleIndex = (cycleIndex + 1) % cycleOrder.size();
            String nextKey = cycleOrder.get(cycleIndex);
            ServerEvent nextEvent = worldCycleEvents.get(nextKey);
            if (nextEvent != null) {
                if (currentCycleEvent != null) {
                    currentCycleEvent.setRunning(false);
                    if (!"none".equalsIgnoreCase(currentCycleEvent.getEndCommand())) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), currentCycleEvent.getEndCommand());
                    }
                }

                currentCycleEvent = nextEvent;
                activeSpawnWorld = nextEvent.getWorldName();
                cycleEndTimeMillis = System.currentTimeMillis() + (nextEvent.getDurationMinutes() * 60L * 1000L);
                currentCycleEvent.setRunning(true);
                currentCycleEvent.setStartTimeMillis(System.currentTimeMillis());
                currentCycleEvent.setEndTimeMillis(cycleEndTimeMillis);

                if (!"none".equalsIgnoreCase(nextEvent.getStartCommand())) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), nextEvent.getStartCommand());
                }

                isEvacuating = false;
                broadcastWorldCycleShift(nextEvent);
            }
        }
    }

    public void evacuatePlayer(Player p) {
        if (p == null || !p.isOnline()) return;

        // Try primary evacuation command
        String cmd = evacuationCommand.replace("%player%", p.getName());
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        boolean executed = false;
        try {
            executed = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } catch (Throwable ignored) {}

        // Fallback: If still in the old world, directly teleport to lowercity world spawn
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && p.getWorld().getName().equalsIgnoreCase(activeSpawnWorld)) {
                World lc = Bukkit.getWorld(evacuationWorld);
                if (lc != null) {
                    p.teleport(lc.getSpawnLocation());
                } else {
                    String fb = evacuationFallbackCommand.replace("%player%", p.getName());
                    if (fb.startsWith("/")) fb = fb.substring(1);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), fb);
                }
            }
            p.sendTitle(ColorUtil.colorize("&6Dimension Collapsed"), ColorUtil.colorize("&7You were safely evacuated to LowerCity mainland"), 10, 60, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        }, 10L);
    }

    private void broadcastWorldCycleShift(ServerEvent event) {
        String titleColor = ColorUtil.colorize(event.getTitle());
        String subtitleColor = ColorUtil.colorize(event.getSubtitle());

        Sound sound = Sound.UI_TOAST_CHALLENGE_COMPLETE;
        try {
            sound = Sound.valueOf(event.getSoundEffect());
        } catch (Throwable ignored) {}

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(titleColor, subtitleColor, 10, 70, 20);
            p.playSound(p.getLocation(), sound, 1.0f, 1.0f);

            TextComponent bar = new TextComponent(ColorUtil.colorize("\n&8&m----------------------------------------------------\n"
                    + DCMessage.PREFIX_BROADCAST + DCMessage.BODY_BROADCAST + "Dimensional Shift: " + titleColor + "\n"));
            TextComponent sub = new TextComponent(ColorUtil.colorize("  &7" + subtitleColor + " &8(World: &#FFFFFF" + event.getWorldName() + "&8)\n"));

            ComponentBuilder perksBuilder = new ComponentBuilder();
            for (String perk : event.getPerks()) {
                perksBuilder.append(ColorUtil.colorize("  " + perk + "\n"));
            }

            TextComponent btn = new TextComponent(ColorUtil.colorize("  &7&l<&#3C7873E&#2E6667n&#21535Bt&#13414Fe&#115D61r&7&l> &#0CBF93Click here to enter realm (/spawn)\n"));
            btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/spawn"));
            btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ColorUtil.colorize("&aTeleport to " + event.getWorldName())).create()));

            TextComponent footer = new TextComponent(ColorUtil.colorize("&8&m----------------------------------------------------\n"));

            p.spigot().sendMessage(bar, sub, new TextComponent(perksBuilder.create()), btn, footer);
        }
    }

    public void startMiniEvent(ServerEvent event) {
        event.setRunning(true);
        event.setStartTimeMillis(System.currentTimeMillis());
        event.setEndTimeMillis(System.currentTimeMillis() + (event.getDurationMinutes() * 60L * 1000L));

        if (!"none".equalsIgnoreCase(event.getStartCommand())) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), event.getStartCommand());
        }

        String titleColor = ColorUtil.colorize(event.getTitle());
        String subtitleColor = ColorUtil.colorize(event.getSubtitle());

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(titleColor, subtitleColor, 10, 60, 20);
            try {
                p.playSound(p.getLocation(), Sound.valueOf(event.getSoundEffect()), 1.0f, 1.0f);
            } catch (Throwable ignored) {}

            TextComponent header = new TextComponent(ColorUtil.colorize("\n" + DCMessage.PREFIX_BROADCAST + DCMessage.BODY_BROADCAST + "Event Active: " + titleColor + "\n"));
            TextComponent info = new TextComponent(ColorUtil.colorize("  &7Duration: &#FFFFFF" + event.getDurationMinutes() + " mins &8| &7Rewards: &#FDE047" + event.getRewardSummary() + "\n"));

            if (!event.getWarpTarget().isEmpty()) {
                TextComponent warpBtn = new TextComponent(ColorUtil.colorize("  &7&l<&#38BDF8W&#26A1DBa&#1585BEr&#0369A1p&7&l> &#BAE6FDClick here to fast travel: /warp " + event.getWarpTarget() + "\n"));
                warpBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/warp " + event.getWarpTarget()));
                warpBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(ColorUtil.colorize("&bFast travel to " + event.getTitle())).create()));
                p.spigot().sendMessage(header, info, warpBtn);
            } else {
                p.spigot().sendMessage(header, info);
            }
        }
    }

    public void endMiniEvent(ServerEvent event) {
        event.setRunning(false);
        if (!"none".equalsIgnoreCase(event.getEndCommand())) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), event.getEndCommand());
        }
        event.setNextRunTimeMillis(System.currentTimeMillis() + (event.getIntervalMinutes() * 60L * 1000L));

        String titleColor = ColorUtil.colorize(event.getTitle());
        Bukkit.broadcastMessage(ColorUtil.colorize(DCMessage.PREFIX_BROADCAST + DCMessage.BODY_BROADCAST + titleColor + " &#F3E8FFhas concluded. Next session in &#FFFFFF"
                + event.getFormattedUntilStartTime() + "&#F3E8FF."));
    }

    /**
     * Ambient crows, sounds, and shadowy particles in spawn_bw during Void Convergence.
     */
    private void processAmbientEffects() {
        if (currentCycleEvent == null) return;
        String wName = currentCycleEvent.getWorldName();

        World world = Bukkit.getWorld(wName);
        if (world == null || world.getPlayers().isEmpty()) return;

        if ("spawn_bw".equalsIgnoreCase(wName)) {
            for (Player p : world.getPlayers()) {
                Location loc = p.getLocation();
                // Play ambient crow flap / raven screech
                if (Math.random() < 0.40) {
                    float pitch = 0.5f + (float) (Math.random() * 0.4f);
                    p.playSound(loc.clone().add((Math.random() - 0.5) * 16, 5, (Math.random() - 0.5) * 16),
                            Sound.ENTITY_PHANTOM_FLAP, 0.4f, pitch);
                }
                // Subtle void particles
                world.spawnParticle(Particle.SQUID_INK, loc.clone().add(0, 1.5, 0), 3, 0.6, 0.6, 0.6, 0.01);
                world.spawnParticle(Particle.PORTAL, loc.clone().add((Math.random() - 0.5) * 8, 1, (Math.random() - 0.5) * 8), 6, 0.2, 0.5, 0.2, 0.05);
            }
        } else if ("spawn_snow".equalsIgnoreCase(wName)) {
            for (Player p : world.getPlayers()) {
                Location loc = p.getLocation();
                world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 3, 0), 5, 4.0, 1.0, 4.0, 0.02);
            }
        }
    }

    /**
     * Intercepts `/spawn` and `/hub` to dynamically teleport to current active World Cycle realm.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!redirectSpawnCommand) return;

        String msg = event.getMessage().trim().toLowerCase();
        if (msg.equals("/spawn") || msg.startsWith("/spawn ") || msg.equals("/hub") || msg.startsWith("/hub ")) {
            event.setCancelled(true);
            teleportToActiveSpawn(event.getPlayer());
        }
    }

    public void teleportToActiveSpawn(Player player) {
        if (player == null || !player.isOnline()) return;

        World world = Bukkit.getWorld(activeSpawnWorld);
        if (world == null) {
            // Attempt to load through Multiverse or WorldCreator
            world = Bukkit.createWorld(new WorldCreator(activeSpawnWorld));
        }

        if (world != null) {
            Location spawnLoc = world.getSpawnLocation().clone().add(0.5, 0.1, 0.5);
            player.teleport(spawnLoc);
            String title = currentCycleEvent != null ? currentCycleEvent.getTitle() : activeSpawnWorld;
            DCMessage.sendEvent(player, "Warping to active realm: " + ColorUtil.colorize(title) + "...");
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
        } else {
            DCMessage.sendError(player, "Active spawn world '" + activeSpawnWorld + "' could not be reached.");
        }
    }

    /**
     * Force swap to a specific world cycle by ID or World Name.
     */
    public boolean forceSwap(String idOrWorld) {
        String lower = idOrWorld.toLowerCase();
        ServerEvent target = null;

        for (ServerEvent se : worldCycleEvents.values()) {
            if (se.getId().equalsIgnoreCase(lower) || se.getWorldName().equalsIgnoreCase(lower)) {
                target = se;
                break;
            }
        }

        if (target == null) return false;

        World oldWorld = Bukkit.getWorld(activeSpawnWorld);
        if (oldWorld != null) {
            for (Player p : new ArrayList<>(oldWorld.getPlayers())) {
                evacuatePlayer(p);
            }
        }

        if (currentCycleEvent != null) currentCycleEvent.setRunning(false);

        currentCycleEvent = target;
        activeSpawnWorld = target.getWorldName();
        cycleEndTimeMillis = System.currentTimeMillis() + (target.getDurationMinutes() * 60L * 1000L);
        target.setRunning(true);
        target.setStartTimeMillis(System.currentTimeMillis());
        target.setEndTimeMillis(cycleEndTimeMillis);
        isEvacuating = false;

        broadcastWorldCycleShift(target);
        return true;
    }

    public void setCycleSecondsRemaining(int seconds) {
        this.cycleEndTimeMillis = System.currentTimeMillis() + (seconds * 1000L);
        if (currentCycleEvent != null) {
            currentCycleEvent.setEndTimeMillis(this.cycleEndTimeMillis);
        }
    }

    public void openCalendarGUI(Player player, int page) {
        if (calendarGUI != null) {
            calendarGUI.open(player, page);
        }
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
    }

    // Getters
    public DCCosmetics getPlugin() { return plugin; }
    public String getActiveSpawnWorld() { return activeSpawnWorld; }
    public String getEvacuationWorld() { return evacuationWorld; }
    public boolean isEvacuating() { return isEvacuating; }
    public ServerEvent getCurrentCycleEvent() { return currentCycleEvent; }
    public long getCycleEndTimeMillis() { return cycleEndTimeMillis; }
    public Map<String, ServerEvent> getWorldCycleEvents() { return worldCycleEvents; }
    public Map<String, ServerEvent> getMiniEvents() { return miniEvents; }
    public List<String> getCycleOrder() { return cycleOrder; }

    public long getCycleSecondsRemaining() {
        return Math.max(0, (cycleEndTimeMillis - System.currentTimeMillis()) / 1000L);
    }

    public String getFormattedCycleTimeRemaining() {
        long sec = getCycleSecondsRemaining();
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) {
            return String.format("%02dh %02dm %02ds", h, m, s);
        } else {
            return String.format("%02dm %02ds", m, s);
        }
    }

    public ServerEvent getNextWorldCycleEvent() {
        if (cycleOrder.isEmpty()) return null;
        int nextIndex = (cycleIndex + 1) % cycleOrder.size();
        return worldCycleEvents.get(cycleOrder.get(nextIndex));
    }

    /**
     * Returns the first currently running scheduled mini-event, or null if none is active.
     */
    public ServerEvent getActiveMiniEvent() {
        for (ServerEvent se : miniEvents.values()) {
            if (se.isRunning()) {
                return se;
            }
        }
        return null;
    }

    /**
     * Registers or updates a personal/temporary active event for a specific player
     * (e.g. casino high-roller session, duel, trial). Higher priority overrides lower.
     */
    public void setPersonalEvent(UUID playerId, String id, String label, int priority, long durationMillis) {
        if (playerId == null || label == null) return;
        long expiresAt = (durationMillis > 0) ? System.currentTimeMillis() + durationMillis : 0L;
        PersonalEvent incoming = new PersonalEvent(id != null ? id : "custom", label, priority, expiresAt);
        personalEvents.compute(playerId, (k, existing) -> {
            if (existing != null && !existing.isExpired() && existing.priority() > incoming.priority()) {
                return existing;
            }
            return incoming;
        });
    }

    /**
     * Clears a player's personal active event (pass null id to clear unconditionally).
     */
    public void clearPersonalEvent(UUID playerId, String id) {
        if (playerId == null) return;
        if (id == null) {
            personalEvents.remove(playerId);
            return;
        }
        personalEvents.computeIfPresent(playerId, (k, existing) ->
                existing.id().equalsIgnoreCase(id) ? null : existing);
    }

    /**
     * Returns the player's currently active personal event, pruning expired entries automatically.
     */
    public PersonalEvent getPersonalEvent(Player player) {
        if (player == null) return null;
        PersonalEvent current = personalEvents.get(player.getUniqueId());
        if (current != null && current.isExpired()) {
            personalEvents.remove(player.getUniqueId(), current);
            return null;
        }
        return current;
    }

    public record PersonalEvent(String id, String label, int priority, long expiresAtMillis) {
        public boolean isExpired() {
            return expiresAtMillis > 0 && System.currentTimeMillis() > expiresAtMillis;
        }
    }
}
