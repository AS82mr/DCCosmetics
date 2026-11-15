package dc.dcessentials.modules.items.listener;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.tpeffects.TpEffectManager;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.teleportation.KeystoneEnergyService;
import dc.dcessentials.modules.teleportation.KeystoneLoreTokens;
import dc.dcessentials.modules.teleportation.SpendResult;
import dc.dcessentials.modules.teleportation.TeleportationManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.util.VoxelShape;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VoidWarperListener implements Listener {

    private final ItemRegistry itemRegistry;

    // 50ms (1 tick) Anti-ghost debounce per player
    private final Map<UUID, Long> clickDebounce = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastInteractTick = new ConcurrentHashMap<>();
    // Cooldown map for Sonic Boom (3 seconds)
    private final Map<UUID, Long> sonicCooldowns = new ConcurrentHashMap<>();
    // Currently charging players
    private final Set<UUID> chargingPlayers = Collections.synchronizedSet(new HashSet<>());
    // Active Etherwarp BlockDisplay markers per sneaking player
    private final Map<UUID, org.bukkit.entity.BlockDisplay> etherwarpMarkers = new ConcurrentHashMap<>();
    private static final double ETHERWARP_RANGE = 60.0;

    public VoidWarperListener(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
        startEtherwarpPreviewTask();
    }

    public boolean isVoidWarper(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String itemId = itemRegistry.getItemId(item);
        if (itemId != null && itemId.equalsIgnoreCase("void_warper")) return true;

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                String name = meta.getDisplayName();
                if (name.contains("ᴠᴏɪᴅ ᴡᴀʀᴘᴇʀ") || name.contains("Void Warper") || name.contains("Void Wand")) return true;
            }
            if (meta.hasLore()) {
                for (String line : meta.getLore()) {
                    if (line.contains("ᴠᴏɪᴅ ʙᴏᴜɴᴄᴇ") || line.contains("Void Bounce") || line.contains("ᴅɪᴍᴇɴꜱɪᴏɴᴀʟ ᴡᴀɴᴅ")) return true;
                }
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isVoidWarper(item)) {
            if (isVoidWarper(player.getInventory().getItemInOffHand())) {
                item = player.getInventory().getItemInOffHand();
            } else {
                return;
            }
        }

        if (event.getAction() == Action.PHYSICAL) return;

        Action action = event.getAction();
        boolean isRight = (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK);
        boolean isLeft = (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);

        // Normal Left-Click is allowed through for normal melee/interactions
        if (isLeft && !player.isSneaking()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        int currentTick = player.getTicksLived();
        Integer prevTick = lastInteractTick.put(uuid, currentTick);
        if (prevTick != null && prevTick == currentTick) {
            event.setCancelled(true);
            return;
        }

        long now = System.currentTimeMillis();
        Long lastClick = clickDebounce.get(uuid);
        if (lastClick != null && (now - lastClick) < 80L) {
            event.setCancelled(true);
            return;
        }
        clickDebounce.put(uuid, now);

        event.setCancelled(true);

        TeleportationManager teleMgr = DCCosmetics.getInstance().getTeleportationManager();
        KeystoneEnergyService energyService = teleMgr != null ? teleMgr.getEnergyService() : null;
        if (energyService != null && !energyService.authorizeAbility(player)) {
            return;
        }

        if (isLeft && player.isSneaking()) {
            // Shift + Left-Click: Sonic Boom
            handleSonicBoomCharge(player);
        } else if (isRight && player.isSneaking()) {
            // Shift + Right-Click: Etherwarp
            handleEtherwarp(player, energyService);
        } else if (isRight) {
            // Normal Right-Click: Void Bounce
            handleVoidBounce(player, energyService);
        }
    }

    private Block raycastEtherwarpBlock(Player player) {
        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                eye,
                eye.getDirection().normalize(),
                ETHERWARP_RANGE,
                FluidCollisionMode.NEVER,
                true
        );
        if (hit == null || hit.getHitBlock() == null) {
            return null;
        }
        return hit.getHitBlock();
    }

    private boolean isSafeEtherwarpLanding(Block targetBlock) {
        if (targetBlock == null || targetBlock.getType().isAir() || !targetBlock.getType().isSolid()) {
            return false;
        }
        Block feet = targetBlock.getRelative(0, 1, 0);
        Block head = targetBlock.getRelative(0, 2, 0);
        return isStandableAirOrPassable(feet) && isStandableAirOrPassable(head);
    }

    private boolean isStandableAirOrPassable(Block b) {
        if (b == null) return false;
        Material type = b.getType();
        if (type == Material.LAVA || type == Material.FIRE || type == Material.SOUL_FIRE || type == Material.POWDER_SNOW) {
            return false;
        }
        if (b.isPassable()) {
            return true;
        }
        VoxelShape shape = b.getCollisionShape();
        return shape.getBoundingBoxes().isEmpty();
    }

    private void handleEtherwarp(Player player, KeystoneEnergyService energyService) {
        Block targetBlock = raycastEtherwarpBlock(player);
        if (targetBlock == null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.9f, 0.7f);
            player.sendActionBar(ColorUtil.colorize("&f&l✦ ETHERWARP &8[&cNO BLOCK IN RANGE (60m)&8] &f✦"));
            return;
        }

        if (!isSafeEtherwarpLanding(targetBlock)) {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.45f, 1.8f);
            player.sendActionBar(ColorUtil.colorize("&f&l✦ ETHERWARP &8[&cOBSTRUCTED — NEED 2 BLOCKS ABOVE&8] &f✦"));
            return;
        }

        SpendResult spendRes = energyService != null ? energyService.spendVoidBounce(player) : null;
        if (spendRes != null && !spendRes.authorized()) {
            return;
        }
        if (spendRes != null) {
            KeystoneLoreTokens.scheduleInventoryRefresh(player);
        }

        Location start = player.getLocation();
        World world = player.getWorld();
        Location dest = new Location(
                world,
                targetBlock.getX() + 0.5,
                targetBlock.getY() + 1.0,
                targetBlock.getZ() + 0.5,
                start.getYaw(),
                start.getPitch()
        );

        world.playSound(start, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.45f);
        world.spawnParticle(Particle.END_ROD, start.clone().add(0, 0.9, 0), 18, 0.25, 0.45, 0.25, 0.04);

        suppressAbilityTp(player, 1000L);
        TpEffectManager.teleportWithoutEffect(player, dest);
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));

        world.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.1f, 1.65f);
        world.playSound(dest, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.3f, 1.85f);
        world.spawnParticle(Particle.END_ROD, dest.clone().add(0, 0.5, 0), 22, 0.3, 0.4, 0.3, 0.05);
        world.spawnParticle(Particle.WITCH, dest.clone().add(0, 0.2, 0), 12, 0.25, 0.1, 0.25, 0.02);

        String costSuffix = formatSpendSuffix(spendRes);
        player.sendActionBar(ColorUtil.colorize("&f&l✦ ETHERWARP &7(" + (int) Math.round(dest.distance(start)) + "m) &f✦" + costSuffix));
    }

    private void startEtherwarpPreviewTask() {
        Bukkit.getScheduler().runTaskTimer(DCCosmetics.getInstance(), () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                boolean holding = isVoidWarper(player.getInventory().getItemInMainHand())
                        || isVoidWarper(player.getInventory().getItemInOffHand());
                if (!player.isSneaking() || !holding || player.isDead()) {
                    removeEtherwarpMarker(uuid);
                    continue;
                }

                Block target = raycastEtherwarpBlock(player);
                if (target == null) {
                    removeEtherwarpMarker(uuid);
                    continue;
                }

                boolean safe = isSafeEtherwarpLanding(target);
                updateEtherwarpMarker(player, target, safe);
            }

            // Cleanup any stale markers for offline players
            Iterator<Map.Entry<UUID, org.bukkit.entity.BlockDisplay>> it = etherwarpMarkers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, org.bukkit.entity.BlockDisplay> entry = it.next();
                Player p = Bukkit.getPlayer(entry.getKey());
                org.bukkit.entity.BlockDisplay disp = entry.getValue();
                if (p == null || !p.isOnline() || disp == null || !disp.isValid()) {
                    if (disp != null && disp.isValid()) {
                        disp.remove();
                    }
                    it.remove();
                }
            }
        }, 4L, 2L);
    }

    private void updateEtherwarpMarker(Player player, Block target, boolean safe) {
        UUID uuid = player.getUniqueId();
        Location markerLoc = target.getLocation().add(-0.002, -0.002, -0.002);
        org.bukkit.entity.BlockDisplay display = etherwarpMarkers.get(uuid);

        if (display == null || !display.isValid() || !display.getWorld().equals(target.getWorld())) {
            removeEtherwarpMarker(uuid);
            display = target.getWorld().spawn(markerLoc, org.bukkit.entity.BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.setGlowing(true);
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                d.setTransformation(new org.bukkit.util.Transformation(
                        new org.joml.Vector3f(0f, 0f, 0f),
                        new org.joml.Quaternionf(),
                        new org.joml.Vector3f(1.004f, 1.004f, 1.004f),
                        new org.joml.Quaternionf()
                ));
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.getUniqueId().equals(uuid)) {
                        other.hideEntity(DCCosmetics.getInstance(), d);
                    }
                }
            });
            etherwarpMarkers.put(uuid, display);
        } else {
            display.teleport(markerLoc);
        }

        display.setBlock((safe ? Material.WHITE_STAINED_GLASS : Material.RED_STAINED_GLASS).createBlockData());
        display.setGlowColorOverride(safe ? Color.WHITE : Color.fromRGB(239, 68, 68));
    }

    private void removeEtherwarpMarker(UUID uuid) {
        org.bukkit.entity.BlockDisplay existing = etherwarpMarkers.remove(uuid);
        if (existing != null && existing.isValid()) {
            existing.remove();
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        removeEtherwarpMarker(event.getPlayer().getUniqueId());
    }

    private void handleVoidBounce(Player player, KeystoneEnergyService energyService) {
        Location start = player.getLocation();
        Vector travelDir = player.getEyeLocation().getDirection().normalize();
        World world = player.getWorld();

        // 1. Multi-Ray Trajectory Raycast along travel direction (feet, waist, eyes, head)
        final double MAX_BOUNCE = 8.0;
        double closestHit = sweepBodyRaycast(world, start, travelDir, MAX_BOUNCE);
        boolean hitObstacle = (closestHit < MAX_BOUNCE);

        double safeDist;
        if (!hitObstacle) {
            safeDist = MAX_BOUNCE;
        } else {
            // Keep 0.45m clearance in front of obstacle
            safeDist = Math.max(0.0, closestHit - 0.45);
        }

        // 2. Compute destination FIRST (Task P2-06)
        Location dest;
        if (safeDist <= 0.05) {
            dest = start.clone();
        } else {
            dest = start.clone().add(travelDir.clone().multiply(safeDist));
        }

        // 3. Strict Floor Protection: prevent users from sinking into blocks
        dest = ensureNotSinking(world, dest, start);
        dest.setYaw(player.getLocation().getYaw());
        dest.setPitch(player.getLocation().getPitch());

        double actualDisplacement = dest.distance(start);
        if (safeDist <= 0.05 || actualDisplacement <= 0.05) {
            // Blocked right in front of obstacle: 0 displacement -> NO charge!
            player.setVelocity(new Vector(0, 0, 0));
            world.playSound(start, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1.2f, 0.8f);
            player.sendActionBar(ColorUtil.colorize("&d&l✦ VOID BOUNCE &8[&cOBSTACLE BLOCKED&8] &d✦"));
            return;
        }

        // 4. Spend Microcharge / Overdraw Heat before teleporting
        SpendResult spendRes = energyService != null ? energyService.spendVoidBounce(player) : null;
        if (spendRes != null && !spendRes.authorized()) {
            return;
        }
        if (spendRes != null) {
            KeystoneLoreTokens.scheduleInventoryRefresh(player);
        }

        // 5. Departure sound & particles + Teleport
        world.playSound(start, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.7f);
        world.playSound(start, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.9f, 1.9f);
        world.spawnParticle(Particle.PORTAL, start.clone().add(0, 0.8, 0), 15, 0.3, 0.4, 0.3, 0.1);

        suppressAbilityTp(player, 1000L);
        TpEffectManager.teleportWithoutEffect(player, dest);

        String costSuffix = formatSpendSuffix(spendRes);

        // 6. Momentum & Feedback
        if (hitObstacle) {
            // Partial displacement before hitting obstacle: zero out velocity so player doesn't slide into barrier
            player.setVelocity(new Vector(0, 0, 0));
            world.playSound(dest, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1.2f, 0.8f);
            player.sendActionBar(ColorUtil.colorize("&d&l✦ VOID BOUNCE &8[&cOBSTACLE BLOCKED&8] &d✦" + costSuffix));
        } else {
            // Iconic fluid forward bounce momentum with character!
            Vector bounce = travelDir.clone().multiply(0.85);
            bounce.setY(bounce.getY() * 0.7 + 0.28);
            player.setVelocity(bounce);

            world.playSound(dest, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.8f);
            world.spawnParticle(Particle.WITCH, dest.clone().add(0, 0.8, 0), 8, 0.2, 0.3, 0.2, 0.05);
            player.sendActionBar(ColorUtil.colorize("&d&l✦ VOID BOUNCE &d✦" + costSuffix));
        }
    }

    private String formatSpendSuffix(SpendResult res) {
        if (res == null) return "";
        if (res.overdraw()) {
            return " &8| &c&lOVERDRAW +" + res.heatAdded() + " 🔥";
        }
        return " &8| &#38BDF8-" + res.effectiveCostMicro() + " mC";
    }

    private void handleSonicBoomCharge(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastUse = sonicCooldowns.get(uuid);

        if (lastUse != null && (now - lastUse) < 3000L) {
            double rem = (3000L - (now - lastUse)) / 1000.0;
            player.sendActionBar(ColorUtil.colorize(String.format("&c&l⏳ Sonic Boom Cooldown: &f%.1fs", rem)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            return;
        }

        if (chargingPlayers.contains(uuid)) return;
        chargingPlayers.add(uuid);

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 28, 4, false, false, false));

        World world = player.getWorld();
        Vector dir = player.getEyeLocation().getDirection().normalize();

        // Spawn Star Wars Hyperspace Inhale Vortex (VFX_VoidWarper_Hyperspace) 2m in front of player
        Location vfxLoc = player.getEyeLocation().add(dir.clone().multiply(2.0));
        vfxLoc.setY(player.getLocation().getY());
        spawnPrivateVfx(player, vfxLoc);

        // 1.2s Charge sequence (6 ticks * 4 = 24 ticks)
        final String[] stages = new String[]{
                "<#06B6D4>■&8□□□□□",
                "<#06B6D4>■■&8□□□□",
                "<#38BDF8>■■■&8□□□",
                "<#38BDF8>■■■■&8□□",
                "<#67E8F9>■■■■■&8□",
                "<#A5F3FC>■■■■■■"
        };

        new BukkitRunnable() {
            int step = 0;
            float pitch = 0.9f;

            @Override
            public void run() {
                if (!player.isOnline()
                        || player.isDead()
                        || (!isVoidWarper(player.getInventory().getItemInMainHand())
                        && !isVoidWarper(player.getInventory().getItemInOffHand()))) {
                    chargingPlayers.remove(uuid);
                    if (player.isOnline()) {
                        player.removePotionEffect(PotionEffectType.SLOWNESS);
                    }
                    cancel();
                    return;
                }

                if (step < 6) {
                    player.sendActionBar(ColorUtil.colorize("<#06B6D4>&l⚡ CHARGING SONIC BOOM &8[" + stages[step] + "&8]"));
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1.4f, pitch);
                    player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 0.7f, pitch + 0.2f);

                    Location pLoc = player.getLocation().add(0, 1.2, 0);
                    world.spawnParticle(Particle.SCULK_CHARGE_POP, pLoc, 8, 0.5, 0.5, 0.5, 0.05);
                    world.spawnParticle(Particle.PORTAL, pLoc, 12, 0.4, 0.6, 0.4, 0.1);

                    pitch += 0.22f;
                    step++;
                } else {
                    cancel();
                    chargingPlayers.remove(uuid);
                    player.removePotionEffect(PotionEffectType.SLOWNESS);
                    executeSonicDetonation(player);
                }
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 0L, 4L);
    }

    private void executeSonicDetonation(Player player) {
        TeleportationManager teleMgr = DCCosmetics.getInstance().getTeleportationManager();
        KeystoneEnergyService energyService = teleMgr != null ? teleMgr.getEnergyService() : null;
        if (energyService != null && !energyService.authorizeAbility(player)) {
            return;
        }
        SpendResult spendRes = energyService != null ? energyService.spendSonicBoom(player) : null;
        if (spendRes != null && !spendRes.authorized()) {
            return;
        }
        if (spendRes != null) {
            KeystoneLoreTokens.scheduleInventoryRefresh(player);
        }

        UUID uuid = player.getUniqueId();
        sonicCooldowns.put(uuid, System.currentTimeMillis());
        suppressAbilityTp(player, 1800L);

        World world = player.getWorld();
        Location start = player.getLocation();
        Vector travelDir = player.getEyeLocation().getDirection().normalize();

        // Soundwave departure
        world.playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 1.0f);
        world.playSound(start, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.8f, 0.8f);
        world.playSound(start, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.5f);

        // Multi-Ray Trajectory Raycast along travel direction up to 38.5 blocks
        final double MAX_SONIC = 38.5;
        double closestHit = sweepBodyRaycast(world, start, travelDir, MAX_SONIC);
        boolean hitObstacle = (closestHit < MAX_SONIC);

        double safeDist;
        if (!hitObstacle) {
            safeDist = MAX_SONIC;
        } else {
            safeDist = Math.max(0.0, closestHit - 0.6);
        }

        // Damage and push mobs along trajectory strictly up to safeDist
        double stepSize = 0.5;
        int steps = (int) (safeDist / stepSize);
        Location cur = player.getEyeLocation().clone();

        for (int i = 0; i < steps; i++) {
            cur.add(travelDir.clone().multiply(stepSize));
            if (i % 2 == 0) {
                world.spawnParticle(Particle.SONIC_BOOM, cur, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.SWEEP_ATTACK, cur, 1, 0.2, 0.2, 0.2, 0);
                world.spawnParticle(Particle.SCULK_SOUL, cur, 2, 0.3, 0.3, 0.3, 0.05);
            }

            for (Entity e : world.getNearbyEntities(cur, 2.4, 2.4, 2.4)) {
                if (e instanceof LivingEntity living && !e.equals(player)) {
                    living.damage(14.0, player);
                    living.setVelocity(travelDir.clone().multiply(1.6));
                }
            }
        }

        // Compute destination
        Location dest;
        if (safeDist <= 0.05) {
            dest = start;
        } else {
            dest = start.clone().add(travelDir.clone().multiply(safeDist));
        }

        dest = ensureNotSinking(world, dest, start);
        dest.setYaw(player.getLocation().getYaw());
        dest.setPitch(player.getLocation().getPitch());

        if (dest != start && safeDist > 0.05) {
            TpEffectManager.teleportWithoutEffect(player, dest);
        }

        String costSuffix = formatSpendSuffix(spendRes);

        if (hitObstacle) {
            player.setVelocity(new Vector(0, 0, 0));
            world.spawnParticle(Particle.EXPLOSION, dest.clone().add(0, 1.0, 0), 1, 0.5, 0.5, 0.5, 0);
            spawnSonicFlash(world, dest.clone().add(0, 1.0, 0));
            world.playSound(dest, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.8f);
            world.playSound(dest, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 1.2f);

            // Impact shockwave AOE
            for (Entity e : world.getNearbyEntities(dest, 3.5, 3.5, 3.5)) {
                if (e instanceof LivingEntity living && !e.equals(player)) {
                    living.damage(14.0, player);
                    Vector push = living.getLocation().toVector().subtract(dest.toVector()).normalize().multiply(1.2);
                    push.setY(0.4);
                    living.setVelocity(push);
                }
            }
            player.sendActionBar(ColorUtil.colorize(String.format("<#06B6D4>&l💥 SONIC IMPACT! &cObstacle Blocked at &f%d Blocks!%s", (int) safeDist, costSuffix)));
        } else {
            Vector turbo = travelDir.clone().multiply(2.4);
            turbo.setY(turbo.getY() * 0.8 + 0.35);
            player.setVelocity(turbo);

            spawnSonicFlash(world, dest.clone().add(0, 1.0, 0));
            world.playSound(dest, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 1.8f);
            player.sendActionBar(ColorUtil.colorize(String.format("<#06B6D4>&l💥 SONIC BOOM TURBO DASH! &f%d Blocks Traversed!%s", (int) safeDist, costSuffix)));
        }
    }

    private static void spawnSonicFlash(World world, Location loc) {
        // Paper 1.21.9+ / 26.2 requires org.bukkit.Color data for Particle.FLASH
        world.spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0, Color.fromRGB(103, 232, 249));
    }

    private static void suppressAbilityTp(Player player, long durationMs) {
        DCCosmetics plugin = DCCosmetics.getInstance();
        if (plugin != null && plugin.getTpEffectManager() != null) {
            plugin.getTpEffectManager().suppressTemporarily(player, durationMs);
        }
    }

    /**
     * Sweeps rays along the player's body in the travel direction.
     * Traces along the exact trajectory vector so obstacles to the side or behind never falsely block movement.
     */
    private double sweepBodyRaycast(World world, Location start, Vector dir, double maxDist) {
        double closest = maxDist;

        // When looking nearly straight up (pitch <= -75 deg, dir.getY() > 0.85),
        // trace from eyes/head center so lower body rays don't falsely clip walls in front of the player's feet.
        Location[] origins;
        if (dir.getY() > 0.85) {
            origins = new Location[]{
                    start.clone().add(0, 1.50, 0),
                    start.clone().add(0, 1.75, 0)
            };
        } else {
            origins = new Location[]{
                    start.clone().add(0, 0.25, 0), // Feet
                    start.clone().add(0, 0.95, 0), // Waist
                    start.clone().add(0, 1.62, 0), // Eyes
                    start.clone().add(0, 1.80, 0)  // Head top
            };
        }

        for (Location orig : origins) {
            RayTraceResult res = world.rayTraceBlocks(orig, dir, closest, FluidCollisionMode.NEVER, true);
            if (res != null && res.getHitPosition() != null) {
                double dist = res.getHitPosition().distance(orig.toVector());
                if (dist < closest) {
                    closest = dist;
                }
            }
        }

        return closest;
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        clickDebounce.remove(uuid);
        lastInteractTick.remove(uuid);
        sonicCooldowns.remove(uuid);
        chargingPlayers.remove(uuid);
    }

    /**
     * Prevents users from sinking into any blocks or falling through 1-block thick floors.
     * Snaps destination feet cleanly to the top surface of the floor block.
     */
    private Location ensureNotSinking(World world, Location dest, Location start) {
        double x = dest.getX();
        double y = dest.getY();
        double z = dest.getZ();

        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);

        // 1. Check if the block containing feet is solid/non-passable
        Block feetBlock = world.getBlockAt(bx, by, bz);
        if (!feetBlock.isPassable() && feetBlock.getType().isSolid()) {
            double floorTop = by + 1.0;
            // Check if head space above floorTop is clear
            Block headBlock = world.getBlockAt(bx, (int) Math.floor(floorTop + 1.2), bz);
            if (headBlock.isPassable()) {
                dest.setY(floorTop);
                y = floorTop;
                by = (int) Math.floor(y);
            } else {
                return start;
            }
        }

        // 2. Catch fractional sinking near floor surface (e.g. 63.85 with floor at 64.0)
        Block belowBlock = world.getBlockAt(bx, (int) Math.floor(y - 0.05), bz);
        if (!belowBlock.isPassable() && belowBlock.getType().isSolid()) {
            double floorTop = belowBlock.getY() + 1.0;
            if (y < floorTop) {
                dest.setY(floorTop);
            }
        }

        return dest;
    }

    private void spawnPrivateVfx(Player player, Location loc) {
        try {
            String w = loc.getWorld().getName();
            double x = loc.getX();
            double y = loc.getY();
            double z = loc.getZ();
            float yaw = player.getLocation().getYaw();

            String cmd = String.format(Locale.US, "mm m spawn VFX_VoidWarper_Hyperspace 1 %s,%.2f,%.2f,%.2f,%.1f,0", w, x, y, z, yaw);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

            // Hide from all other players across multiple ticks to catch all child bones/displays
            for (long delay : new long[]{1L, 2L, 4L, 8L}) {
                Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
                    if (!player.isOnline()) return;
                    for (Entity e : loc.getWorld().getNearbyEntities(loc, 4.0, 4.0, 4.0)) {
                        if (!e.equals(player) && (e.getType().name().contains("DISPLAY") || e.getType() == EntityType.ARMOR_STAND || e.getType() == EntityType.INTERACTION)) {
                            for (Player online : Bukkit.getOnlinePlayers()) {
                                if (!online.getUniqueId().equals(player.getUniqueId())) {
                                    online.hideEntity(DCCosmetics.getInstance(), e);
                                }
                            }
                        }
                    }
                }, delay);
            }
        } catch (Throwable t) {
            DCCosmetics.getInstance().getLogger().warning("[DCEssentials] Error spawning private Void Warper VFX: " + t.getMessage());
        }
    }
}
