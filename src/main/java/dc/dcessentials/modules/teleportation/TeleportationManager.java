package dc.dcessentials.modules.teleportation;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class TeleportationManager {
    private final Map<UUID, KeystoneProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, TeleportContext> contexts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingTpaSynergy = new ConcurrentHashMap<>();

    private final KeystonePersistence persistence = new KeystonePersistence(this);
    private final TeleportGate gate = new TeleportGate(this);
    private final KeystoneEnergyService energyService = new KeystoneEnergyService(this);

    private BukkitTask thermodynamicsTask;
    private BukkitTask passiveDecayTask;

    private int newbieShieldDays = 3;
    private double netherHeatMultiplier = 2.0;
    private int nexusIncursionCooldownSeconds = 300;
    private int cryoPurgeAmount = 20;
    private int cryoCooldownSeconds = 5;

    private int microPerCharge = 1000;
    private int warpCostChargesMin = 1;
    private int warpCostChargesMax = 3;
    private int depletedHeatMin = 6;
    private int depletedHeatMax = 10;
    private int unattunedHeatMin = 10;
    private int unattunedHeatMax = 15;
    private int costReductionCapPct = 70;
    private int overloadClearBelow = 50;

    private int instabilityThreshold = 80;
    private double instabilityIncomingDamagePct = 15.0;
    private int instabilityJitterMin = 2;
    private int instabilityJitterMax = 4;

    private int tpaSynergyHeatMin = 10;
    private int tpaSynergyHeatMax = 15;
    private int flushIntervalTicks = 200;

    private int voidBounceCostMicro = 75;
    private int voidBounceOverdrawHeat = 6;
    private int sonicBoomCostMicro = 250;
    private int sonicBoomOverdrawHeat = 15;

    private int conduitRefundChanceCapPct = 25;
    private int conduitRefundMicroMin = 25;
    private int conduitRefundMicroMax = 100;
    private int conduitMaxRefundPerMinute = 400;

    private Set<String> freeCommands = Set.of("back", "spawn", "warp", "pwarp", "pw");
    private Set<String> chargedCommands = Set.of("home", "homes", "tpa", "tpahere", "tpaccept", "tpyes", "tp", "teleport", "rtp", "wild");
    private volatile boolean debugTeleportCauses = false;
    private final Map<UUID, Long> recentFreeArrivalGrace = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastFreeMsgIndex = new ConcurrentHashMap<>();
    private static final String[] REBELLION_FREE_MESSAGES = {
            "Safe Rebellion transit relay complete — 0 ⚡ charged.",
            "Travelled via a Rebellion slipstream corridor — no Tesseract spent.",
            "Encrypted Rebellion waypoint route active — travel is free.",
            "Secured Rebellion transfer complete — passage cost waived.",
            "Quiet jump through Rebellion channels — your Keystone rests at 0 ⚡."
    };

    public void loadConfig() {
        var mainCfg = DCCosmetics.getInstance().getConfig();
        List<String> debugModes = mainCfg.getStringList("debug-mode");
        this.debugTeleportCauses = debugModes.contains("teleport") || debugModes.contains("keystone")
                || mainCfg.getBoolean("teleportation.keystone.debug-causes", false);

        if (mainCfg.isConfigurationSection("teleportation") || mainCfg.isConfigurationSection("keystone")) {
            String prefix = mainCfg.isConfigurationSection("teleportation.keystone") ? "teleportation.keystone." :
                    (mainCfg.isConfigurationSection("keystone") ? "keystone." : "teleportation.");
            readKeystoneSection(mainCfg, prefix);
            return;
        }

        java.io.File f = new java.io.File(DCCosmetics.getInstance().getDataFolder(), "teleportation.yml");
        if (f.exists()) {
            var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
            readKeystoneSection(cfg, "keystone.");
        }
    }

    private void readKeystoneSection(org.bukkit.configuration.ConfigurationSection cfg, String prefix) {
        this.newbieShieldDays = cfg.getInt(prefix + "newbie-shield-days", 3);
        this.netherHeatMultiplier = cfg.getDouble(prefix + "nether-heat-multiplier", 2.0);
        this.nexusIncursionCooldownSeconds = cfg.getInt(prefix + "nexus-incursion-cooldown-seconds", 300);
        this.cryoPurgeAmount = cfg.getInt(prefix + "cryo-purge-amount", 20);
        this.cryoCooldownSeconds = cfg.getInt(prefix + "cryo-cooldown-seconds", 5);

        this.microPerCharge = cfg.getInt(prefix + "micro-per-charge", 1000);
        this.warpCostChargesMin = cfg.getInt(prefix + "warp-cost-charges.min", 1);
        this.warpCostChargesMax = cfg.getInt(prefix + "warp-cost-charges.max", 5);
        this.depletedHeatMin = cfg.getInt(prefix + "depleted-heat.min", 6);
        this.depletedHeatMax = cfg.getInt(prefix + "depleted-heat.max", 10);
        this.unattunedHeatMin = cfg.getInt(prefix + "unattuned-heat.min", 10);
        this.unattunedHeatMax = cfg.getInt(prefix + "unattuned-heat.max", 15);
        this.costReductionCapPct = cfg.getInt(prefix + "cost-reduction-cap-pct", 70);
        this.overloadClearBelow = cfg.getInt(prefix + "overload-clear-below", 50);

        this.instabilityThreshold = cfg.getInt(prefix + "instability.threshold", 80);
        this.instabilityIncomingDamagePct = cfg.getDouble(prefix + "instability.incoming-damage-pct", 15.0);
        this.instabilityJitterMin = cfg.getInt(prefix + "instability.jitter-blocks.min", 2);
        this.instabilityJitterMax = cfg.getInt(prefix + "instability.jitter-blocks.max", 4);

        if (cfg.isConfigurationSection(prefix + "tpa-synergy-heat")) {
            this.tpaSynergyHeatMin = cfg.getInt(prefix + "tpa-synergy-heat.min", 10);
            this.tpaSynergyHeatMax = cfg.getInt(prefix + "tpa-synergy-heat.max", 15);
        } else {
            int legacySynergy = cfg.getInt(prefix + "tpa-synergy-cooldown-heat", 15);
            this.tpaSynergyHeatMin = Math.max(1, legacySynergy - 5);
            this.tpaSynergyHeatMax = Math.max(this.tpaSynergyHeatMin, legacySynergy);
        }

        this.flushIntervalTicks = cfg.getInt(prefix + "flush-interval-ticks", 200);

        this.voidBounceCostMicro = cfg.getInt(prefix + "abilities.void_bounce.cost-micro", 75);
        this.voidBounceOverdrawHeat = cfg.getInt(prefix + "abilities.void_bounce.overdraw-heat", 6);
        this.sonicBoomCostMicro = cfg.getInt(prefix + "abilities.sonic_boom.cost-micro", 250);
        this.sonicBoomOverdrawHeat = cfg.getInt(prefix + "abilities.sonic_boom.overdraw-heat", 15);

        this.conduitRefundChanceCapPct = cfg.getInt(prefix + "conduit.refund-chance-cap-pct", 25);
        this.conduitRefundMicroMin = cfg.getInt(prefix + "conduit.refund-micro.min", 25);
        this.conduitRefundMicroMax = cfg.getInt(prefix + "conduit.refund-micro.max", 100);
        this.conduitMaxRefundPerMinute = cfg.getInt(prefix + "conduit.max-refund-micro-per-minute", 400);

        List<String> freeList = cfg.getStringList(prefix + "teleportation-free");
        if (freeList.isEmpty()) freeList = cfg.getStringList("teleportation-free");
        if (freeList.isEmpty()) freeList = cfg.getStringList(prefix + "free-commands");
        if (!freeList.isEmpty()) {
            Set<String> set = new HashSet<>();
            for (String s : freeList) {
                if (s != null && !s.isBlank()) {
                    String normalized = s.trim().toLowerCase(Locale.ROOT).replace("?", "");
                    if (normalized.startsWith("/")) normalized = normalized.substring(1).trim();
                    if (!normalized.isEmpty()) set.add(normalized);
                }
            }
            this.freeCommands = Collections.unmodifiableSet(set);
        }

        List<String> chargedList = cfg.getStringList(prefix + "teleportation-cost");
        if (chargedList.isEmpty()) chargedList = cfg.getStringList("teleportation-cost");
        if (chargedList.isEmpty()) chargedList = cfg.getStringList(prefix + "charged-commands");
        if (!chargedList.isEmpty()) {
            Set<String> set = new HashSet<>();
            for (String s : chargedList) {
                if (s != null && !s.isBlank()) {
                    String normalized = s.trim().toLowerCase(Locale.ROOT);
                    if (normalized.startsWith("/")) normalized = normalized.substring(1).trim();
                    if (!normalized.isEmpty()) set.add(normalized);
                }
            }
            this.chargedCommands = Collections.unmodifiableSet(set);
        }
    }

    public void start() {
        loadConfig();
        persistence.start(flushIntervalTicks);

        // Thermodynamics loop: Immersion Cryo-cooling & Spatial Anomaly pulses
        thermodynamicsTask = Bukkit.getScheduler().runTaskTimer(DCCosmetics.getInstance(), () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                KeystoneProfile profile = getProfile(player);
                if (profile.getHeat() <= 0) continue;

                ItemStack equippedKeystone = getEquippedKeystoneItem(player);
                int effClearBelow = KeystoneDetector.getOverloadClearBelow(equippedKeystone, overloadClearBelow);

                // Check immersion in Water or Powder Snow
                var block = player.getLocation().getBlock();
                var type = block.getType().name();
                boolean isCryo = type.contains("WATER") || type.contains("POWDER_SNOW");

                if (isCryo) {
                    long now = System.currentTimeMillis();
                    if (now - profile.getLastCryoTime() >= cryoCooldownSeconds * 1000L) {
                        profile.setLastCryoTime(now);
                        profile.reduceHeat(cryoPurgeAmount, effClearBelow);
                        player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.8f, 1.2f);
                        player.spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
                        DCMessage.sendInfo(player, "Cryo-immersion purged &#FFFFFF-" + cryoPurgeAmount + " Heat&#BAE6FD! Current Heat: &#FFFFFF" + profile.getHeat() + "/100&#BAE6FD.");
                    }
                }

                // High Heat Visual & Physical Instability Warnings (>= instabilityThreshold)
                if (profile.getHeat() >= instabilityThreshold) {
                    int heat = profile.getHeat();
                    player.spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0, 0.5, 0), 16, 0.4, 0.5, 0.4, 0.03);
                    player.spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1.0, 0), 20, 0.5, 0.6, 0.5, 0.06);
                    player.spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1.0, 0), 15, 0.45, 0.5, 0.45, 0.15);

                    if (!KeystoneDetector.hasInstabilitySuppression(equippedKeystone)) {
                        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 1.0f);
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.45f, 0.65f);
                        player.sendActionBar(net.kyori.adventure.text.Component.text(
                                "§c⚡ Temporal Instability (§e" + heat + "/100 🔥§c) — Spatial recoil, destabilized stats & +15% Boss/Void damage! ⚡"
                        ));
                        if (!player.hasMetadata("in_wardrobe")) {
                            long graceUntil = recentFreeArrivalGrace.getOrDefault(player.getUniqueId(), 0L);
                            if (System.currentTimeMillis() < graceUntil) {
                                continue;
                            }
                            boolean critical = heat >= 90;
                            double impulseScale = critical ? 0.95 : 0.65;
                            double jx = (ThreadLocalRandom.current().nextDouble() - 0.5) * impulseScale;
                            double jz = (ThreadLocalRandom.current().nextDouble() - 0.5) * impulseScale;
                            double jy = player.isFlying() ? 0.0 : (critical ? 0.28 : 0.18);
                            player.setVelocity(player.getVelocity().add(new org.bukkit.util.Vector(jx, jy, jz)));

                            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                    org.bukkit.potion.PotionEffectType.NAUSEA, 80, 0, true, false, true));
                            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                    org.bukkit.potion.PotionEffectType.SLOWNESS, 50, critical ? 1 : 0, true, false, true));
                            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                    org.bukkit.potion.PotionEffectType.MINING_FATIGUE, 50, 0, true, false, true));
                            if (critical) {
                                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                        org.bukkit.potion.PotionEffectType.WEAKNESS, 50, 0, true, false, true));
                                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                        org.bukkit.potion.PotionEffectType.DARKNESS, 45, 0, true, false, true));
                            }

                            org.bukkit.GameMode gm = player.getGameMode();
                            if (gm == org.bukkit.GameMode.SURVIVAL || gm == org.bukkit.GameMode.ADVENTURE) {
                                double zapDmg = critical ? 2.0 : 1.0;
                                if (player.getHealth() > zapDmg + 1.5) {
                                    player.damage(zapDmg);
                                }
                            }
                        }
                    }

                    if (heat >= 85) {
                        for (Player nearby : player.getWorld().getPlayers()) {
                            if (nearby.equals(player)) continue;
                            if (nearby.getLocation().distanceSquared(player.getLocation()) <= 100) {
                                nearby.sendActionBar(net.kyori.adventure.text.Component.text("§c⚡ Spatial Anomaly: §e" + player.getName() + " §cis emitting intense Temporal Heat! ⚡"));
                            }
                        }
                    }
                }
            }
        }, 60L, 60L); // Every 3 seconds

        // Passive Natural Cooling Loop: Every 30 seconds (Flux passive energy regen removed per X5)
        passiveDecayTask = Bukkit.getScheduler().runTaskTimer(DCCosmetics.getInstance(), () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                KeystoneProfile profile = getProfile(player);
                if (profile.getHeat() <= 0) continue;

                ItemStack equippedKeystone = getEquippedKeystoneItem(player);
                int effClearBelow = KeystoneDetector.getOverloadClearBelow(equippedKeystone, overloadClearBelow);

                int baseDecay = profile.getTier().getPassiveDecay();
                int scrollCoolPct = KeystoneDetector.getScrollCooldownRatePct(equippedKeystone);
                int thermalBonus = (int) Math.floor(energyService.getThermalStat(player) / 20.0);

                int totalDecay = Math.max(1, (int) Math.round(baseDecay * (1.0 + (scrollCoolPct / 100.0))) + thermalBonus);
                profile.reduceHeat(totalDecay, effClearBelow);
            }
        }, 600L, 600L); // Every 30 seconds
    }

    public ItemStack getEquippedKeystoneItem(Player player) {
        if (player == null) return null;
        var pm = DCCosmetics.getInstance().getProfileManager();
        if (pm == null) return null;
        var prof = pm.getProfile(player);
        return prof != null ? prof.getEquipment("keystone") : null;
    }

    public void stop() {
        if (thermodynamicsTask != null) thermodynamicsTask.cancel();
        if (passiveDecayTask != null) passiveDecayTask.cancel();
        persistence.flushAll(true);
        profiles.clear();
        contexts.clear();
        pendingTpaSynergy.clear();
    }

    public KeystonePersistence getPersistence() { return persistence; }
    public TeleportGate getGate() { return gate; }
    public KeystoneEnergyService getEnergyService() { return energyService; }

    public KeystoneProfile getProfile(Player player) {
        return profiles.computeIfAbsent(player.getUniqueId(), KeystoneProfile::new);
    }

    public KeystoneProfile getProfile(UUID uuid) {
        return profiles.computeIfAbsent(uuid, KeystoneProfile::new);
    }

    public Collection<KeystoneProfile> getAllProfiles() {
        return profiles.values();
    }

    public KeystoneProfile evictProfile(UUID uuid) {
        contexts.remove(uuid);
        pendingTpaSynergy.remove(uuid);
        return profiles.remove(uuid);
    }

    // =========================================================================
    // TELEPORT CONTEXT TOKENS (Task P1-06)
    // =========================================================================

    public void armContext(Player player, TeleportContext.Type type, String label) {
        if (player == null) return;
        long tick = Bukkit.getCurrentTick();
        contexts.put(player.getUniqueId(), new TeleportContext(
                type,
                label != null ? label : "",
                tick,
                TeleportContext.DEFAULT_TTL_TICKS,
                System.currentTimeMillis()
        ));
    }

    public TeleportContext peekContext(Player player) {
        if (player == null) return null;
        TeleportContext ctx = contexts.get(player.getUniqueId());
        if (ctx == null) return null;
        if (ctx.isExpired(Bukkit.getCurrentTick())) {
            contexts.remove(player.getUniqueId(), ctx);
            return null;
        }
        return ctx;
    }

    public TeleportContext consumeContext(Player player) {
        if (player == null) return null;
        TeleportContext ctx = contexts.remove(player.getUniqueId());
        if (ctx != null && ctx.isExpired(Bukkit.getCurrentTick())) {
            return null;
        }
        return ctx;
    }

    public void armPendingTpaAccept(Player player) {
        if (player == null) return;
        pendingTpaSynergy.put(player.getUniqueId(), System.currentTimeMillis() + 10_000L);
    }

    private void checkAndResolvePendingTpaSynergy(Player teleportingPlayer, Location to) {
        long now = System.currentTimeMillis();
        // 1. Did the teleporting player themselves run /tpaccept (e.g. /tpahere)?
        Long selfExpiry = pendingTpaSynergy.remove(teleportingPlayer.getUniqueId());
        if (selfExpiry != null && now <= selfExpiry) {
            onConsensualTpaSuccess(teleportingPlayer);
            return;
        }
        // 2. Did the teleporting player land near a player who ran /tpaccept within 10s (standard /tpa)?
        if (to != null && to.getWorld() != null) {
            for (Player cand : to.getWorld().getPlayers()) {
                if (cand.equals(teleportingPlayer)) continue;
                Long exp = pendingTpaSynergy.get(cand.getUniqueId());
                if (exp != null) {
                    if (now > exp) {
                        pendingTpaSynergy.remove(cand.getUniqueId(), exp);
                    } else if (cand.getLocation().distanceSquared(to) <= 36.0) {
                        pendingTpaSynergy.remove(cand.getUniqueId(), exp);
                        onConsensualTpaSuccess(cand);
                        break;
                    }
                }
            }
        }
    }

    @Deprecated
    public void addBypass(Player player) {
        armContext(player, TeleportContext.Type.FREE_COMMAND, "bypass");
    }

    @Deprecated
    public boolean consumeBypass(Player player) {
        TeleportContext ctx = peekContext(player);
        if (ctx != null && ctx.type() == TeleportContext.Type.FREE_COMMAND) {
            consumeContext(player);
            return true;
        }
        return false;
    }

    /**
     * Primary cause-filtered teleport handler (runs at MONITOR after TeleportGate.preCheck).
     */
    public void handleTeleport(Player player, Location from, Location to, TeleportCause cause) {
        if (player == null || !player.isOnline()) return;

        KeystoneProfile profile = getProfile(player);
        profile.setBackLocation(from);

        // Resolve any pending /tpaccept social synergy if an actual teleport occurred
        checkAndResolvePendingTpaSynergy(player, to);

        // 1. Check & consume active TeleportContext token
        TeleportContext ctx = consumeContext(player);
        if (ctx != null && ctx.type() == TeleportContext.Type.FREE_COMMAND) {
            if (!gate.isExemptOrSuppressedTransit(player, cause, from, to)) {
                recentFreeArrivalGrace.put(player.getUniqueId(), System.currentTimeMillis() + 3500L);
                sendVariedRebellionFreeMessage(player);
            }
            return;
        }

        // 2. Environmental, wardrobe, cinematic camera, short-hop, and ability teleports are exempt
        if (gate.isExemptOrSuppressedTransit(player, cause, from, to)) {
            return;
        }

        // 3. Unloaded profile safety (already logged in TeleportGate)
        if (!profile.isLoaded()) {
            return;
        }

        // 5. Compute warp cost in Microcharges (1-3 charges = 1000-3000 mC)
        int minC = Math.max(1, warpCostChargesMin);
        int maxC = Math.max(minC, warpCostChargesMax);
        int baseCharges = ThreadLocalRandom.current().nextInt(minC, maxC + 1);
        int baseCostMicro = baseCharges * microPerCharge;

        SpendResult res = energyService.spend(player, SpendKind.WARP, baseCostMicro, to);

        // 6. KEYSTONE_RECLAIM scroll refund on /tpa warps
        if (ctx != null && res.microPaid() > 0) {
            String cmd = ctx.commandLabel();
            if (cmd.startsWith("tpa") || cmd.equals("tpyes")) {
                int refundPct = KeystoneDetector.getTpaRefundPct(getEquippedKeystoneItem(player));
                if (refundPct > 0) {
                    int refundMicro = (int) Math.round(res.microPaid() * (refundPct / 100.0));
                    energyService.refundMicro(player, refundMicro);
                }
            }
        }

        // 7. Player feedback
        if (profile.hasNewbieShield()) {
            DCMessage.sendInfo(player, "Teleport complete: &#FFFFFF-" + formatMicroAsCharges(res.microPaid())
                    + " 🗲 &#BAE6FD(&#0CBF93Newbie Shield: 0 Heat&#BAE6FD).");
            return;
        }

        if (!res.overdraw()) {
            DCMessage.sendInfo(player, "Transit complete: &#FFFFFF-" + formatMicroAsCharges(res.microPaid())
                    + " 🗲 &#BAE6FD(&#FFFFFF" + res.remainingEnergy() + " 🗲 &#7DD3FC| &#FFFFFF" + res.remainingMicro()
                    + KeystoneLoreTokens.MICRO_ICON + "&#BAE6FD) | Matrix absorbed friction (&#10B9810 Heat&#BAE6FD).");
            return;
        }

        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.8f);
        if (profile.getTier() == KeystoneTier.NONE) {
            DCMessage.sendWarning(player, "You teleported without an attuned Keystone! Raw friction: &#EF4444+"
                    + res.heatAdded() + " Heat 🔥 &#FDE047(&#FFFFFF" + res.resultingHeat() + "/100&#FDE047).");
            DCMessage.sendInfo(player, "Use &#FFFFFF/profile &#BAE6FDto equip a Keystone and insulate your jumps.");
        } else {
            DCMessage.sendWarning(player, "Keystone energy depleted! Emergency Overdraw: &#EF4444+"
                    + res.heatAdded() + " Heat 🔥 &#FDE047(&#FFFFFF" + res.resultingHeat() + "/100&#FDE047).");
        }
    }

    private void sendVariedRebellionFreeMessage(Player player) {
        int count = REBELLION_FREE_MESSAGES.length;
        int prev = lastFreeMsgIndex.getOrDefault(player.getUniqueId(), -1);
        int idx = ThreadLocalRandom.current().nextInt(count);
        if (count > 1 && idx == prev) {
            idx = (idx + 1 + ThreadLocalRandom.current().nextInt(count - 1)) % count;
        }
        lastFreeMsgIndex.put(player.getUniqueId(), idx);
        DCMessage.sendInfo(player, REBELLION_FREE_MESSAGES[idx]);
    }

    private String formatMicroAsCharges(int micro) {
        if (micro % microPerCharge == 0) {
            return String.valueOf(micro / microPerCharge);
        }
        return String.format(Locale.US, "%.2f", (double) micro / microPerCharge);
    }

    /**
     * Spawns imperial sentinels upon reaching critical variance limit (100 heat).
     */
    public void checkNexusIncursion(Player player, KeystoneProfile profile) {
        if (profile.getHeat() < 100) return;

        long now = System.currentTimeMillis();
        if (now - profile.getLastNexusIncursion() < nexusIncursionCooldownSeconds * 1000L) {
            DCMessage.sendWarning(player, "Timeline Variance Critical! Imperial Purge units are tracking your coordinates.");
            return;
        }

        profile.setLastNexusIncursion(now);
        Location loc = player.getLocation();

        player.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.6f);
        player.playSound(loc, Sound.BLOCK_PORTAL_TRIGGER, 1.2f, 0.5f);
        player.spawnParticle(Particle.SCULK_CHARGE, loc, 50, 1.0, 1.0, 1.0, 0.1, 0.0f);

        DCMessage.sendError(player, "Timeline Variance Limit (100/100) Exceeded! The Emperor's Sentinels have breached your coordinates!");

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mm mobs spawn imperial_sentinel 1 " +
                loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
    }

    /**
     * Social TPA synergy reward cooling.
     */
    public void onConsensualTpaSuccess(Player player) {
        KeystoneProfile profile = getProfile(player);
        profile.incrementTpaCount();

        if (profile.getConsensualTpaCount() >= 3) {
            profile.resetTpaCount();
            if (profile.getHeat() <= 0) return;

            int min = Math.max(1, tpaSynergyHeatMin);
            int max = Math.max(min, tpaSynergyHeatMax);
            int reduction = Math.min(profile.getHeat(), ThreadLocalRandom.current().nextInt(min, max + 1));
            profile.reduceHeat(reduction, getEffectiveOverloadClearBelow(player));

            player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.6f, 1.0f);
            DCMessage.sendGeneral(player, "Consensual TPA synergy! Timeline Heat purged by &#FFFFFF-" + reduction + " &#0CBF93(Stability: " + profile.getStability() + "&#0CBF93).");
        }
    }

    // =========================================================================
    // CONFIG GETTERS
    // =========================================================================

    public int getNewbieShieldDays() { return newbieShieldDays; }
    public double getNetherHeatMultiplier() { return netherHeatMultiplier; }
    public int getMicroPerCharge() { return microPerCharge; }
    public int getDepletedHeatMin() { return depletedHeatMin; }
    public int getDepletedHeatMax() { return depletedHeatMax; }
    public int getUnattunedHeatMin() { return unattunedHeatMin; }
    public int getUnattunedHeatMax() { return unattunedHeatMax; }
    public int getCostReductionCapPct() { return costReductionCapPct; }
    public int getOverloadClearBelow() { return overloadClearBelow; }
    public int getEffectiveOverloadClearBelow(Player player) {
        return KeystoneDetector.getOverloadClearBelow(getEquippedKeystoneItem(player), overloadClearBelow);
    }
    public int getInstabilityThreshold() { return instabilityThreshold; }
    public double getInstabilityIncomingDamagePct() { return instabilityIncomingDamagePct; }
    public int getInstabilityJitterMin() { return instabilityJitterMin; }
    public int getInstabilityJitterMax() { return instabilityJitterMax; }
    public int getVoidBounceCostMicro() { return voidBounceCostMicro; }
    public int getVoidBounceOverdrawHeat() { return voidBounceOverdrawHeat; }
    public int getSonicBoomCostMicro() { return sonicBoomCostMicro; }
    public int getSonicBoomOverdrawHeat() { return sonicBoomOverdrawHeat; }
    public int getAbilityOverdrawHeat(int baseCostMicro) {
        return baseCostMicro >= sonicBoomCostMicro ? sonicBoomOverdrawHeat : voidBounceOverdrawHeat;
    }
    public int getConduitRefundChanceCapPct() { return conduitRefundChanceCapPct; }
    public int getConduitRefundMicroMin() { return conduitRefundMicroMin; }
    public int getConduitRefundMicroMax() { return conduitRefundMicroMax; }
    public int getConduitMaxRefundPerMinute() { return conduitMaxRefundPerMinute; }
    public Set<String> getFreeCommands() { return freeCommands; }
    public Set<String> getChargedCommands() { return chargedCommands; }
    public boolean isDebugTeleportCauses() { return debugTeleportCauses; }
    public void setDebugTeleportCauses(boolean debugTeleportCauses) { this.debugTeleportCauses = debugTeleportCauses; }
}
