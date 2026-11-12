package dc.dcessentials.modules.boss;

import dc.dccosmetics.DCCosmetics;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dragoncote Emperor Boss Combat & Event Engine.
 *
 * Handles:
 * 1. Live Emperor mob tracking (MythicMobs `helly` & Bukkit) in `lowercity`.
 * 2. Dynamic player scaling (Anger scale & damage modulation).
 * 3. Paper Adventure BossBar (auto-visible in `lowercity`, clean scale display only when > 1x).
 * 4. Native Paper TextDisplay floating emerald heal popups (+X ❤) with scatter & upward float interpolation.
 * 5. Direct TAB scoreboard refresh pushes via EmperorTabHook.
 */
public class EmperorBossManager {

    private final DCCosmetics plugin;
    private final String arenaWorldName = "lowercity";
    private final double arenaRadius = 80.0;

    private boolean active = false;
    private LivingEntity emperorEntity = null;
    private Location emperorLocation = null;
    private double currentHp = 999.0;
    private double maxHp = 999.0;
    private int hpPercent = 100;
    private int playerCount = 1;
    private double angerScale = 1.0;

    private boolean manualOverride = false;
    private long lastHealPopupTime = 0L;
    private long lastHeartbeatSoundTime = 0L;
    private long lastSeenMillis = 0L;

    private final Set<UUID> fightingPlayers = ConcurrentHashMap.newKeySet();
    private BossBar adventureBossBar = null;
    private BukkitTask tickTask = null;
    private EmperorTabHook tabHook = null;

    public EmperorBossManager(DCCosmetics plugin) {
        this.plugin = plugin;
    }

    public void setTabHook(EmperorTabHook tabHook) {
        this.tabHook = tabHook;
    }

    public void init() {
        this.adventureBossBar = BossBar.bossBar(
                Component.text("The Emperor - Temporal Sovereign", NamedTextColor.RED, TextDecoration.BOLD),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.NOTCHED_20
        );

        startTickLoop();
        plugin.getLogger().info("[EmperorBossManager] Initialized Emperor Boss Engine & Adventure BossBar.");
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        if (adventureBossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(adventureBossBar);
            }
        }

        fightingPlayers.clear();
        cleanStrayDisplays(Bukkit.getWorld(arenaWorldName));
    }

    private void startTickLoop() {
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    private void tick() {
        if (manualOverride) {
            updateBossBarState();
            updateBossBarViewers();
            return;
        }

        World world = Bukkit.getWorld(arenaWorldName);
        if (world == null) {
            if (active) deactivate();
            return;
        }

        // 1. Locate living Emperor entity if cached reference is dead or missing
        if (emperorEntity == null || !emperorEntity.isValid() || emperorEntity.isDead()) {
            emperorEntity = findEmperorEntity(world);
        }

        if (emperorEntity != null && emperorEntity.isValid() && !emperorEntity.isDead()) {
            boolean wasActive = this.active;
            int prevHpPct = this.hpPercent;
            double prevScale = this.angerScale;

            this.active = true;
            this.lastSeenMillis = System.currentTimeMillis();
            this.emperorLocation = emperorEntity.getLocation();
            stripMythicBossBar(emperorEntity);
            updateEntityHealth(emperorEntity);
            updateArenaScaling(world);

            updateBossBarState();
            updateBossBarViewers();

            if (tabHook != null && (!wasActive || prevHpPct != this.hpPercent || Double.compare(prevScale, this.angerScale) != 0)) {
                tabHook.pushRefreshAll();
            }
        } else {
            // Grace window in case ModelEngine/chunk briefly flickers
            if (active && (System.currentTimeMillis() - lastSeenMillis > 3500L)) {
                deactivate();
            }
        }
    }

    private void updateEntityHealth(LivingEntity entity) {
        this.currentHp = entity.getHealth();
        AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
        double resolvedMax = (attr != null) ? attr.getValue() : 999.0;
        if (resolvedMax <= 0) resolvedMax = 999.0;
        this.maxHp = resolvedMax;
        this.hpPercent = (int) Math.max(0, Math.min(100, Math.round((currentHp / maxHp) * 100.0)));
    }

    private void updateArenaScaling(World world) {
        if (emperorLocation == null || world == null) return;

        double rSq = arenaRadius * arenaRadius;
        int nearbyCount = 0;
        Set<UUID> prevFighters = new HashSet<>(fightingPlayers);
        fightingPlayers.clear();

        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(emperorLocation) <= rSq) {
                fightingPlayers.add(player.getUniqueId());
                nearbyCount++;
            }
        }

        int count = Math.max(1, nearbyCount);
        this.playerCount = count;
        if (count == 1) {
            this.angerScale = 1.0;
        } else {
            double raw = 1.0 + ((count - 1) * 0.33);
            this.angerScale = Math.round(raw * 10.0) / 10.0;
        }

        if (tabHook != null && !prevFighters.equals(fightingPlayers)) {
            tabHook.pushRefreshAll();
        }
    }

    private LivingEntity findEmperorEntity(World world) {
        LivingEntity fallback = null;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof LivingEntity living && !(living instanceof ArmorStand) && !living.isDead()) {
                try {
                    if (MythicBukkit.inst() != null && MythicBukkit.inst().getMobManager() != null) {
                        ActiveMob am = MythicBukkit.inst().getMobManager().getMythicMobInstance(living);
                        if (am != null && am.getType() != null && "helly".equalsIgnoreCase(am.getType().getInternalName())) {
                            return living;
                        }
                    }
                } catch (Throwable ignored) {}
                if (fallback == null && isEmperorEntity(living)) {
                    fallback = living;
                }
            }
        }
        return fallback;
    }

    /**
     * Checks whether the given entity is The Emperor (`helly`) via identity, MythicMobs internal ID, tag, or name.
     */
    public boolean isEmperorEntity(Entity entity) {
        if (entity == null || entity instanceof ArmorStand) return false;
        if (emperorEntity != null && emperorEntity.getUniqueId().equals(entity.getUniqueId())) return true;
        if (entity.getScoreboardTags().contains("emperor_boss")) return true;

        try {
            if (MythicBukkit.inst() != null && MythicBukkit.inst().getMobManager() != null) {
                ActiveMob am = MythicBukkit.inst().getMobManager().getMythicMobInstance(entity);
                if (am != null && am.getType() != null && "helly".equalsIgnoreCase(am.getType().getInternalName())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        Component custom = entity.customName();
        if (custom != null) {
            String plain = PlainTextComponentSerializer.plainText().serialize(custom);
            if (plain.contains("The Emperor")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Terminates MythicMobs's built-in BossBar on the Emperor so only DCEssentials's BossBar renders.
     */
    private void stripMythicBossBar(LivingEntity entity) {
        if (entity == null) return;
        try {
            if (MythicBukkit.inst() != null && MythicBukkit.inst().getMobManager() != null) {
                ActiveMob am = MythicBukkit.inst().getMobManager().getMythicMobInstance(entity);
                if (am != null) {
                    am.unloadBossBars();
                }
            }
        } catch (Throwable ignored) {}
    }

    public void onEmperorSpawn(Entity spawned) {
        if (!(spawned instanceof LivingEntity living) || living instanceof ArmorStand) return;
        this.manualOverride = false;
        this.emperorEntity = living;
        this.active = true;
        this.lastSeenMillis = System.currentTimeMillis();
        this.emperorLocation = living.getLocation();
        stripMythicBossBar(living);

        updateEntityHealth(living);
        updateArenaScaling(living.getWorld());
        updateBossBarState();
        updateBossBarViewers();
        if (tabHook != null) tabHook.pushRefreshAll();
    }

    public void onEmperorDeath(Entity dead) {
        if (manualOverride) return;
        if (dead == null || isEmperorEntity(dead)) {
            deactivate();
        }
    }

    private void updateBossBarState() {
        if (adventureBossBar == null) return;

        float progress = (float) Math.max(0.0, Math.min(1.0, maxHp > 0 ? currentHp / maxHp : 1.0));
        adventureBossBar.progress(progress);

        Component title;
        if (angerScale <= 1.0) {
            title = Component.text("The Emperor - Temporal Sovereign", NamedTextColor.RED, TextDecoration.BOLD);
        } else {
            String scaleStr = String.format(Locale.US, "%.1f", angerScale);
            title = Component.text("The Emperor ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text("[" + scaleStr + "x] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("- Temporal Sovereign", NamedTextColor.RED, TextDecoration.BOLD));
        }
        adventureBossBar.name(title);
    }

    public void updateBossBarViewers() {
        if (adventureBossBar == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean show = active && (manualOverride || isFighting(player));
            if (show) {
                player.showBossBar(adventureBossBar);
            } else {
                player.hideBossBar(adventureBossBar);
            }
        }
    }

    public void deactivate() {
        boolean wasActive = this.active;
        this.active = false;
        this.emperorEntity = null;
        this.emperorLocation = null;
        this.angerScale = 1.0;
        this.hpPercent = 100;
        this.playerCount = 1;
        this.fightingPlayers.clear();

        if (adventureBossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(adventureBossBar);
            }
        }
        if (wasActive && tabHook != null) {
            tabHook.pushRefreshAll();
        }
    }

    /**
     * Natively spawns a low-key small green "+X ❤" TextDisplay anchored above the target.
     * Tracks the Emperor's live position tick-by-tick so popups stay with him during dashes.
     */
    public void spawnHealText(Location loc, int baseAmount) {
        spawnHealText(null, loc, baseAmount);
    }

    /**
     * Spawns a small low-key green "+X ❤" TextDisplay that tracks `anchor` (falling back to cached Emperor or static location).
     */
    public void spawnHealText(Entity anchor, Location fallbackLoc, int baseAmount) {
        Entity trackTarget = (anchor != null && anchor.isValid() && !anchor.isDead())
                ? anchor
                : ((emperorEntity != null && emperorEntity.isValid() && !emperorEntity.isDead()) ? emperorEntity : null);

        Location baseLoc = (trackTarget != null) ? trackTarget.getLocation() : fallbackLoc;
        if (baseLoc == null || baseLoc.getWorld() == null) {
            if (emperorLocation != null && emperorLocation.getWorld() != null) baseLoc = emperorLocation;
            else return;
        }

        long now = System.currentTimeMillis();
        if (now - lastHealPopupTime < 90L) {
            return;
        }
        lastHealPopupTime = now;

        int finalAmount = (int) Math.max(1, Math.round(baseAmount * (angerScale > 0 ? angerScale : 1.0)));

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double ox = (rng.nextDouble() - 0.5) * 1.1;
        double oz = (rng.nextDouble() - 0.5) * 1.1;
        double baseYOffset = 1.9 + rng.nextDouble() * 0.55;
        Location staticAnchor = baseLoc.clone();
        Location spawnLoc = staticAnchor.clone().add(ox, baseYOffset, oz);

        try {
            TextDisplay display = spawnLoc.getWorld().spawn(spawnLoc, TextDisplay.class, d -> {
                d.text(Component.text("+" + finalAmount + " ❤", NamedTextColor.GREEN));
                d.setBillboard(Display.Billboard.CENTER);
                d.setSeeThrough(true);
                d.setShadowed(true);
                d.setViewRange(48.0f);
                d.setTeleportDuration(2);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.addScoreboardTag("emp_heal_txt");

                Transformation t = d.getTransformation();
                t.getScale().set(0.65f, 0.65f, 0.65f);
                t.getTranslation().set(0.0f, 0.0f, 0.0f);
                d.setTransformation(t);
            });

            if (now - lastHeartbeatSoundTime >= 250L) {
                lastHeartbeatSoundTime = now;
                spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 1.2f);
            }

            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    ticks++;
                    if (!display.isValid() || ticks >= 12) {
                        if (display.isValid()) {
                            display.remove();
                        }
                        cancel();
                        return;
                    }
                    Location origin = staticAnchor;
                    if (trackTarget != null && trackTarget.isValid() && !trackTarget.isDead()
                            && trackTarget.getWorld().equals(display.getWorld())) {
                        origin = trackTarget.getLocation();
                    }
                    display.teleport(origin.clone().add(ox, baseYOffset + (ticks * 0.04), oz));
                }
            }.runTaskTimer(plugin, 1L, 1L);

        } catch (Throwable t) {
            plugin.getLogger().warning("[EmperorBossManager] Failed to spawn TextDisplay: " + t.getMessage());
        }
    }

    public int cleanStrayDisplays(World world) {
        if (world == null) world = Bukkit.getWorld(arenaWorldName);
        if (world == null) return 0;

        int cleaned = 0;
        for (Entity e : world.getEntities()) {
            if (e instanceof TextDisplay td && td.getScoreboardTags().contains("emp_heal_txt")) {
                td.remove();
                cleaned++;
            }
        }
        if (cleaned > 0) {
            plugin.getLogger().info("[EmperorBossManager] Cleaned up " + cleaned + " stray TextDisplays.");
        }
        return cleaned;
    }

    // --- Admin Simulation Suite ---

    public void setManualOverride(int count, int hp) {
        this.manualOverride = true;
        this.active = true;
        this.playerCount = Math.max(1, count);
        if (this.playerCount == 1) {
            this.angerScale = 1.0;
        } else {
            double raw = 1.0 + ((this.playerCount - 1) * 0.33);
            this.angerScale = Math.round(raw * 10.0) / 10.0;
        }
        this.hpPercent = Math.max(1, Math.min(100, hp));
        this.maxHp = 999.0;
        this.currentHp = (this.hpPercent / 100.0) * this.maxHp;

        fightingPlayers.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            fightingPlayers.add(p.getUniqueId());
        }

        updateBossBarState();
        updateBossBarViewers();
        if (tabHook != null) tabHook.pushRefreshAll();
    }

    public void resetManualOverride() {
        this.manualOverride = false;
        deactivate();
        if (tabHook != null) tabHook.pushRefreshAll();
    }

    public void testHeal(Player player) {
        Entity anchor = (emperorEntity != null && emperorEntity.isValid() && !emperorEntity.isDead())
                ? emperorEntity
                : player;
        Location target = (anchor != null)
                ? anchor.getLocation()
                : ((emperorLocation != null && emperorLocation.getWorld() != null) ? emperorLocation : null);
        spawnHealText(anchor, target, 12);
    }

    // --- Getters & Queries ---

    public boolean isActive() { return active; }

    public boolean isFighting(Player player) {
        if (player == null) return false;
        if (manualOverride) return true;
        if (!active || emperorLocation == null || emperorLocation.getWorld() == null) return false;
        if (!player.getWorld().equals(emperorLocation.getWorld())) return false;
        return player.getLocation().distanceSquared(emperorLocation) <= (arenaRadius * arenaRadius);
    }

    public LivingEntity getEmperorEntity() { return emperorEntity; }
    public double getAngerScale() { return angerScale; }
    public int getHpPercent() { return hpPercent; }
    public int getPlayerCount() { return playerCount; }
    public Location getEmperorLocation() { return emperorLocation; }
    public boolean isManualOverride() { return manualOverride; }
}
