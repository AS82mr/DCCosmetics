package dc.dcessentials.modules.items.listener;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.scroll.ScrollRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EmperorCombatListener implements Listener {

    private final ScrollRegistry scrollRegistry;

    // Breach state tracking: UUID -> remaining breach shots (2 or 1)
    private final Map<UUID, Integer> breachShots = new HashMap<>();
    // Mace charge tracking: UUID -> current charge (0 to 5)
    private final Map<UUID, Integer> maceCharges = new HashMap<>();
    // Stasis Core cooldown: UUID -> expiration timestamp
    private final Map<UUID, Long> stasisCooldowns = new HashMap<>();
    // Throttled dialogue: UUID -> timestamp
    private final Map<UUID, Long> lastDialogue = new HashMap<>();

    public EmperorCombatListener(ScrollRegistry scrollRegistry) {
        this.scrollRegistry = scrollRegistry;
    }

    public boolean isEmperor(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        if (entity.getScoreboardTags().contains("emperor_boss") || entity.getScoreboardTags().contains("frozen_stasis")) {
            return true;
        }

        String customName = entity.getCustomName();
        if (customName != null) {
            String lower = ChatColor.stripColor(customName).toLowerCase();
            if (lower.contains("emperor") || lower.contains("temporal sovereign")) {
                return true;
            }
        }

        if (entity instanceof Husk) {
            String world = entity.getWorld().getName().toLowerCase();
            if (world.contains("lowercity") || world.contains("world")) {
                return true;
            }
        }

        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!isEmperor(victim)) return;

        LivingEntity boss = (LivingEntity) victim;
        boolean inStasis = boss.getScoreboardTags().contains("frozen_stasis");

        // 1. Thrown Spear/Trident Intercept
        if (event.getDamager() instanceof Trident trident) {
            if (trident.getShooter() instanceof Player shooter) {
                if (!inStasis && !shooter.getScoreboardTags().contains("mace_bypassed")) {
                    event.setCancelled(true);
                    trident.remove();
                    shooter.sendActionBar(ColorUtil.colorize("<#EF4444>&l⛔ THROWN SPEAR DEFLECTED &8| &cTemporal Shield!"));
                    shooter.playSound(shooter.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.8f, 0.4f);
                    shooter.playSound(shooter.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.5f, 0.5f);
                    castMythicMobSkill(boss, "EmperorSpearGrip");
                    return;
                }
            }
        }

        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.getType().isAir()) return;

        ItemMeta meta = weapon.hasItemMeta() ? weapon.getItemMeta() : null;
        String weaponName = (meta != null && meta.hasDisplayName()) ? ChatColor.stripColor(meta.getDisplayName()).toLowerCase() : "";
        String matName = weapon.getType().name().toLowerCase();

        // 2. Spear Melee Intercept
        boolean isSpear = matName.contains("trident") || weaponName.contains("spear") || weaponName.contains("trident");
        if (isSpear && !inStasis && !attacker.getScoreboardTags().contains("mace_bypassed")) {
            event.setCancelled(true);
            attacker.sendActionBar(ColorUtil.colorize("<#EF4444>&l⛔ SPEAR BLOCKED &8| &cTelekinetic Barrier!"));
            attacker.playSound(attacker.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.5f, 0.5f);
            attacker.playSound(attacker.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.2f, 0.6f);
            if (boss instanceof org.bukkit.entity.Mob mob) {
                mob.setTarget(attacker);
            }
            castMythicMobSkill(boss, "EmperorSpearGrip", attacker);
            return;
        }

        // 3. Helmet Damage Flicker
        if (!boss.getScoreboardTags().contains("mask_broken")) {
            castMythicMobSkill(boss, "hellydamaged");
        }

        // 4. Emperor Slayer Damage Multiplier
        String socketCombat = getCombatSocket(meta);
        double slayerMultiplier = 1.0;
        String slayerTag = "";
        String slayerColor = "";

        if (socketCombat != null) {
            if (socketCombat.equalsIgnoreCase("slayer_mythic") || socketCombat.contains("35")) {
                slayerMultiplier = 1.35;
                slayerTag = "+35% Sovereign Bane";
                slayerColor = "<#F59E0B>";
            } else if (socketCombat.equalsIgnoreCase("slayer_epic") || socketCombat.contains("20")) {
                slayerMultiplier = 1.20;
                slayerTag = "+20% Sovereign Bane";
                slayerColor = "<#A855F7>";
            } else if (socketCombat.equalsIgnoreCase("slayer_rare") || socketCombat.contains("15")) {
                slayerMultiplier = 1.15;
                slayerTag = "+15% Sovereign Bane";
                slayerColor = "<#38BDF8>";
            }
        }

        if (slayerMultiplier > 1.0) {
            event.setDamage(event.getDamage() * slayerMultiplier);
            double disp = Math.round(event.getDamage() * 10.0) / 10.0;
            attacker.sendActionBar(ColorUtil.colorize("<#EF4444>⚔ &f" + disp + " &8(" + slayerColor + slayerTag + "&8)"));
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.4f);
            boss.getWorld().spawnParticle(Particle.CRIT, boss.getLocation().add(0, 1.5, 0), 8, 0.3, 0.3, 0.3, 0.1);
        }

        // 5. Mace Chrono-Slip 1-in-5 Breach Engine & Anti-Mace Telekinetic Intercept
        if (weapon.getType() == Material.MACE) {
            UUID pUuid = attacker.getUniqueId();

            if (inStasis) {
                event.setDamage(event.getDamage() * 1.5);
                attacker.sendActionBar(ColorUtil.colorize("<#06B6D4>&l❄ STASIS HIT ❄ &fUnmitigated Smash!"));
                attacker.playSound(attacker.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
                return;
            }

            boolean hasChronoSlip = socketCombat != null && socketCombat.equalsIgnoreCase("chrono_slip");
            if (!hasChronoSlip && meta != null && meta.hasLore()) {
                for (String l : meta.getLore()) {
                    if (l.contains("ᴄʜʀᴏɴᴏ-ꜱʟɪᴘ") || l.contains("Chrono-Slip")) {
                        hasChronoSlip = true;
                        break;
                    }
                }
            }

            if (hasChronoSlip) {
                int activeBreach = breachShots.getOrDefault(pUuid, 0);

                if (activeBreach == 2) {
                    // Shot 1 of 2: First breach smash landed!
                    breachShots.put(pUuid, 1);
                    event.setDamage(event.getDamage() * 1.4);
                    attacker.sendActionBar(ColorUtil.colorize("<#10B981>&l⚡ BREACH &8[<#38BDF8>1/2&8] &eJump for Max Height!"));
                    attacker.playSound(attacker.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.8f, 1.0f);
                    attacker.playSound(attacker.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.8f);

                    ensureWindBurst(attacker, weapon);

                    long now = System.currentTimeMillis();
                    if (now - lastDialogue.getOrDefault(pUuid, 0L) > 30000L) {
                        lastDialogue.put(pUuid, now);
                        attacker.sendMessage(ColorUtil.colorize("&4&lThe Emperor &8• &r&c\"My spatial anchor... broke?!\""));
                    }
                    return;
                } else if (activeBreach == 1) {
                    // Shot 2 of 2: Final breach smash!
                    breachShots.remove(pUuid);
                    maceCharges.put(pUuid, 0);
                    attacker.removeScoreboardTag("mace_bypassed");
                    event.setDamage(event.getDamage() * 1.4);
                    attacker.sendActionBar(ColorUtil.colorize("<#10B981>&l⚡ BREACH COMPLETED &8| &7Resetting charge..."));
                    attacker.playSound(attacker.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 1.2f);

                    ensureWindBurst(attacker, weapon);
                    return;
                }

                // Normal strike: accumulate charge (Damage cancelled while building charge!)
                event.setCancelled(true);
                int currentCharge = maceCharges.getOrDefault(pUuid, 0) + 1;
                if (currentCharge >= 5) {
                    // BREACH ACTIVATED
                    maceCharges.put(pUuid, 0);
                    breachShots.put(pUuid, 2);
                    attacker.addScoreboardTag("mace_bypassed");
                    attacker.sendActionBar(ColorUtil.colorize("<#10B981>&l⚡ BREACH READY (2/2 SHOTS) &8| &fVader Shield Breached!"));
                    attacker.playSound(attacker.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.2f);
                    attacker.playSound(attacker.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.5f);
                } else {
                    maceCharges.put(pUuid, currentCharge);
                    String bar = buildChargeBar(currentCharge, 5);
                    attacker.sendActionBar(ColorUtil.colorize("<#EF4444>Chrono-Charge: &f" + currentCharge + "/5 " + bar));
                    attacker.playSound(attacker.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.0f + (currentCharge * 0.2f));
                }
                return;
            }

            // NO Chrono-Slip Scroll: 100% Intercepted by Anti-Mace Telekinetic Force!
            event.setCancelled(true);
            attacker.sendActionBar(ColorUtil.colorize("<#EF4444>&l⛔ MACE REJECTED &8| &cTelekinetic Force Intercept!"));
            attacker.playSound(attacker.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.8f, 0.4f);
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.5f);
            if (boss instanceof org.bukkit.entity.Mob mob) {
                mob.setTarget(attacker);
            }

            // Hard physical lock directly in Java: slowness 255 + jump boost 128 (no WASD, no jumping)
            attacker.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 45, 255, false, false, false));
            attacker.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST, 45, 128, false, false, false));
            attacker.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

            boolean isFatality = attacker.getScoreboardTags().contains("mace_attempt_3");
            String skillToCast = isFatality ? "EmperorMaceFatality" : "EmperorVaderGrip";
            castMythicMobSkill(boss, skillToCast, attacker);
            return;
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.HEART_OF_THE_SEA || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        String name = meta.hasDisplayName() ? ChatColor.stripColor(meta.getDisplayName()).toLowerCase() : "";
        if (!name.contains("temporal stasis core")) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // Check Cooldown
        long now = System.currentTimeMillis();
        long expire = stasisCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < expire) {
            long remaining = (expire - now) / 1000L;
            DCMessage.sendWarning(player, "Temporal Stasis Core is on cooldown for &#FDE047" + remaining + "s!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 0.8f);
            return;
        }

        // Search for nearby Emperor within 14 blocks
        LivingEntity targetEmperor = null;
        for (Entity e : player.getNearbyEntities(14, 14, 14)) {
            if (isEmperor(e)) {
                targetEmperor = (LivingEntity) e;
                break;
            }
        }

        if (targetEmperor == null) {
            DCMessage.sendError(player, "No Temporal Sovereign or Emperor detected nearby within 14 blocks!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 0.8f);
            return;
        }

        // Consume 1 core
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        stasisCooldowns.put(player.getUniqueId(), now + 60000L);

        // Apply 15s Cryo Stasis
        final LivingEntity boss = targetEmperor;
        boss.addScoreboardTag("frozen_stasis");
        boss.setAI(false);

        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.5f, 0.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.5f);
        DCMessage.sendGeneral(player, "Cryo Cage detonated! The Emperor is frozen for &#38BDF815 seconds&#0CBF93!");

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead() || ticks >= 300) { // 300 ticks = 15 seconds
                    boss.removeScoreboardTag("frozen_stasis");
                    boss.setAI(true);
                    boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 1.2f);
                    this.cancel();
                    return;
                }

                Location loc = boss.getLocation().add(0, 1.0, 0);
                boss.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 12, 1.2, 1.5, 1.2, 0.05);
                boss.getWorld().spawnParticle(Particle.ENCHANT, loc, 6, 0.8, 1.0, 0.8, 0.1);

                ticks += 10;
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 0L, 10L);
    }

    private String getCombatSocket(ItemMeta meta) {
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(ScrollDragListener.SOCKET_COMBAT_KEY, PersistentDataType.STRING)) {
            return pdc.get(ScrollDragListener.SOCKET_COMBAT_KEY, PersistentDataType.STRING);
        }

        // Legacy Skript lore check
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                if (line.contains("ᴇᴍᴘᴇʀᴏʀ ꜱʟᴀʏᴇʀ: +35%") || line.contains("+35% Damage")) return "slayer_mythic";
                if (line.contains("ᴇᴍᴘᴇʀᴏʀ ꜱʟᴀʏᴇʀ: +20%") || line.contains("+20% Damage")) return "slayer_epic";
                if (line.contains("ᴇᴍᴘᴇʀᴏʀ ꜱʟᴀʏᴇʀ: +15%") || line.contains("+15% Damage")) return "slayer_rare";
                if (line.contains("ᴄʜʀᴏɴᴏ-ꜱʟɪᴘ") || line.contains("Chrono-Slip")) return "chrono_slip";
            }
        }
        return null;
    }

    private String buildChargeBar(int current, int max) {
        StringBuilder sb = new StringBuilder("&8[");
        for (int i = 0; i < max; i++) {
            if (i < current) {
                sb.append("<#10B981>■");
            } else {
                sb.append("<#64748B>□");
            }
        }
        sb.append("&8]");
        return sb.toString();
    }

    private void ensureWindBurst(Player attacker, ItemStack weapon) {
        boolean hasWindBurst = weapon.getEnchantments().keySet().stream()
                .anyMatch(e -> e.getKey().getKey().toLowerCase().contains("wind_burst"));
        if (!hasWindBurst && weapon.hasItemMeta() && weapon.getItemMeta().hasLore()) {
            for (String line : weapon.getItemMeta().getLore()) {
                if (line.toLowerCase().contains("wind burst") || line.toLowerCase().contains("wind-burst")) {
                    hasWindBurst = true;
                    break;
                }
            }
        }

        if (hasWindBurst) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (attacker.isOnline() && attacker.getVelocity().getY() <= 0.1) {
                        attacker.setVelocity(new org.bukkit.util.Vector(attacker.getVelocity().getX(), 0.85, attacker.getVelocity().getZ()));
                        attacker.getWorld().spawnParticle(Particle.CLOUD, attacker.getLocation(), 15, 0.5, 0.2, 0.5, 0.1);
                        attacker.playSound(attacker.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.2f, 1.2f);
                    }
                }
            }.runTaskLater(DCCosmetics.getInstance(), 1L);
        }
    }

    private void castMythicMobSkill(LivingEntity caster, String skillName) {
        castMythicMobSkill(caster, skillName, null);
    }

    private void castMythicMobSkill(LivingEntity caster, String skillName, Entity target) {
        if (target instanceof Player p) {
            Location bLoc = caster.getLocation();
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format(
                java.util.Locale.US,
                "ex run emperor_vader_force_pull def:%s|%.2f|%.2f|%.2f|%.2f",
                p.getName(), bLoc.getX(), bLoc.getY(), bLoc.getZ(), bLoc.getYaw()
            ));
        }

        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object mythicBukkit = mythicBukkitClass.getMethod("get").invoke(null);
            Object apiHelper = mythicBukkitClass.getMethod("getAPIHelper").invoke(mythicBukkit);

            if (target != null) {
                try {
                    // MythicMobs 5.x 7-argument signature: castSkill(Entity, String, Entity, Location, Collection, Collection, float)
                    java.lang.reflect.Method castMethod = apiHelper.getClass().getMethod(
                        "castSkill",
                        Entity.class,
                        String.class,
                        Entity.class,
                        Location.class,
                        java.util.Collection.class,
                        java.util.Collection.class,
                        float.class
                    );
                    castMethod.invoke(apiHelper, caster, skillName, target, caster.getLocation(), java.util.Collections.singletonList(target), null, 1.0f);
                    return;
                } catch (NoSuchMethodException ignored) {
                }
            }

            try {
                java.lang.reflect.Method castMethod = apiHelper.getClass().getMethod("castSkill", Entity.class, String.class);
                castMethod.invoke(apiHelper, caster, skillName);
                return;
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Throwable ignored) {
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:execute as " + caster.getUniqueId() + " run mm test cast -s " + skillName);
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        breachShots.remove(uuid);
        maceCharges.remove(uuid);
        stasisCooldowns.remove(uuid);
        lastDialogue.remove(uuid);
    }
}
