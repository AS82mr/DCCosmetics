package dc.dcessentials.modules.items.tester;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.model.ItemStat;
import dc.dcessentials.modules.items.stats.PlayerStatSnapshot;
import dc.dcessentials.modules.items.stats.StatAggregator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class StatTesterManager implements Listener {

    public static final NamespacedKey DUMMY_KEY = new NamespacedKey(DCCosmetics.getInstance(), "dce_stat_dummy");
    public static final NamespacedKey DISPLAY_KEY = new NamespacedKey(DCCosmetics.getInstance(), "dce_dummy_display_uuid");

    private final ItemRegistry itemRegistry;
    private final StatAggregator statAggregator;
    private final Map<UUID, Integer> cadenceMap = new HashMap<>();
    private final Map<UUID, long[]> surgeMap = new HashMap<>(); // Attacker UUID -> [LastHitTime, Stacks]
    private final Map<UUID, Long> playerDebounceMap = new HashMap<>(); // Attacker UUID -> LastHitTimestamp
    private final Map<UUID, BukkitTask> resetTaskMap = new HashMap<>(); // Dummy UUID -> ResetTask
    private final Map<UUID, Double> dummyHpMap = new HashMap<>(); // Dummy UUID -> Virtual HP (0 - 100.0)

    public StatTesterManager(ItemRegistry itemRegistry, StatAggregator statAggregator) {
        this.itemRegistry = itemRegistry;
        this.statAggregator = statAggregator;
    }

    public ArmorStand spawnDummy(Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        // 1. Spawn ArmorStand Target Dummy
        ArmorStand dummy = world.spawn(location, ArmorStand.class, stand -> {
            stand.setCustomName(null);
            stand.setCustomNameVisible(false);
            stand.setArms(true);
            stand.setBasePlate(true);
            stand.setGravity(false);
            stand.setInvulnerable(false);
            stand.setCanPickupItems(false);

            if (stand.getEquipment() != null) {
                stand.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                stand.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                stand.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                stand.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
                stand.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
                stand.getEquipment().setItemInOffHand(new ItemStack(Material.SHIELD));
            }

            stand.getPersistentDataContainer().set(DUMMY_KEY, PersistentDataType.INTEGER, 1);
        });

        // 2. Spawn a single, dedicated TextDisplay floating above the dummy
        Location holoLoc = location.clone().add(0, 3.0, 0);
        TextDisplay display = world.spawn(holoLoc, TextDisplay.class, td -> {
            td.text(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.colorize("&7[Ready for Hit]")));
            td.setBillboard(Display.Billboard.CENTER);
            td.setViewRange(24.0f);
            td.setShadowed(true);
            td.getPersistentDataContainer().set(DUMMY_KEY, PersistentDataType.INTEGER, 2);
        });

        // Link display UUID to the dummy
        dummy.getPersistentDataContainer().set(DISPLAY_KEY, PersistentDataType.STRING, display.getUniqueId().toString());

        world.playSound(location, Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 1.0f);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, location.clone().add(0, 1.2, 0), 20, 0.5, 0.5, 0.5, 0.1);

        return dummy;
    }

    public int clearDummies(World world) {
        if (world == null) return 0;
        int count = 0;
        for (Entity ent : world.getEntities()) {
            PersistentDataContainer pdc = ent.getPersistentDataContainer();
            if (pdc.has(DUMMY_KEY, PersistentDataType.INTEGER)) {
                ent.remove();
                count++;
            }
        }
        cadenceMap.clear();
        surgeMap.clear();
        playerDebounceMap.clear();
        resetTaskMap.values().forEach(BukkitTask::cancel);
        resetTaskMap.clear();
        return count;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDummyInteract(org.bukkit.event.player.PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand dummy)) return;
        PersistentDataContainer pdc = dummy.getPersistentDataContainer();
        if (!pdc.has(DUMMY_KEY, PersistentDataType.INTEGER)) return;

        event.setCancelled(true);
        dummy.setCustomNameVisible(false);
        Player player = event.getPlayer();

        // Cycle Simulated HP: 100 -> 50 -> 25 (Execute Range) -> 10 (Critical) -> 100
        double curHp = dummyHpMap.getOrDefault(dummy.getUniqueId(), 100.0);
        double newHp;
        if (curHp > 75.0) {
            newHp = 50.0;
        } else if (curHp > 35.0) {
            newHp = 25.0; // In execute range!
        } else if (curHp > 15.0) {
            newHp = 10.0;
        } else {
            newHp = 100.0;
        }
        dummyHpMap.put(dummy.getUniqueId(), newHp);

        String statusTag = (newHp <= 35.0) ? " &c&l[EXECUTE RANGE]" : (newHp <= 50.0 ? " &e[HALF HP]" : " &a[FULL HP]");
        player.sendMessage(ColorUtil.colorize("<#10B981>[DCE] &fDummy Simulated Health set to: " + (newHp <= 35 ? "<#EF4444>" : "<#FCD34D>") + (int) newHp + " / 100 HP (" + (int) newHp + "%)" + statusTag));
        player.playSound(dummy.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);

        TextDisplay display = findOrCreateDisplay(dummy);
        if (display != null) {
            display.text(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.colorize(
                    "&c&lCombat Stat Dummy &7[Level 100]\n" +
                    "&7Simulated HP: &e" + (int) newHp + "/100 HP (" + (int) newHp + "%)" + statusTag + "\n" +
                    "&8[Right-Click to Cycle HP | Hit to Test]"
            )));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDummyDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand dummy)) return;

        PersistentDataContainer pdc = dummy.getPersistentDataContainer();
        if (!pdc.has(DUMMY_KEY, PersistentDataType.INTEGER)) return;

        // Cancel damage and keep dummy intact
        event.setCancelled(true);
        dummy.setCustomName(null);
        dummy.setCustomNameVisible(false);

        if (!(event.getDamager() instanceof Player attacker)) return;

        long now = System.currentTimeMillis();
        UUID pUuid = attacker.getUniqueId();

        // Debounce: minimum 250ms between registered hits on dummy to prevent multi-packet sweeps
        long lastHit = playerDebounceMap.getOrDefault(pUuid, 0L);
        if (now - lastHit < 250) {
            return;
        }
        playerDebounceMap.put(pUuid, now);

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        PlayerStatSnapshot snap = statAggregator != null ? statAggregator.getSnapshot(attacker) : null;

        // 1. Calculate Base Weapon Damage (ArmorStands return 0.0 in Paper, so we resolve true weapon damage)
        double baseDmg = event.getDamage();
        if (baseDmg <= 0.0) {
            baseDmg = getBaseWeaponDamage(weapon);
        }

        // 2. Surge Calculations (Combo Momentum — unified +4% per stack up to stacked surgeCap via StatCombatEngine)
        double surgeCap = snap != null ? snap.get(ItemStat.SURGE) : 0.0;
        double activeSurge = dc.dcessentials.modules.items.stats.StatCombatEngine.computeSurgeBonus(surgeMap, pUuid, surgeCap, now);

        double totalDmg = baseDmg * (1.0 + (activeSurge / 100.0));

        // 3. Resonance Calculations (Acoustic Cadence Counter)
        double resonanceCadence = snap != null ? snap.get(ItemStat.RESONANCE) : 0.0;
        boolean cadenceProc = false;
        int hitCount = 0;
        if (resonanceCadence > 0) {
            hitCount = cadenceMap.getOrDefault(pUuid, 0) + 1;
            cadenceMap.put(pUuid, hitCount);
            if (hitCount % Math.max(2, (int) Math.round(resonanceCadence)) == 0) {
                cadenceProc = true;
                totalDmg *= 1.25; // +25% harmonic shatter burst (matches live combat)
            }
        }

        // 4. Catalyst Inversion (Debuffs Inverted into Combat Damage)
        double catalystPct = snap != null ? snap.get(ItemStat.CATALYST) : 0.0;
        dc.dcessentials.modules.items.listener.StatCombatListener.CatalystData catData =
                dc.dcessentials.modules.items.listener.StatCombatListener.calculateCatalyst(attacker, catalystPct);
        double catBonusPct = 0.0;
        if (catData.multiplier > 1.0 || catData.flatWeaknessOffset > 0) {
            totalDmg = (totalDmg + catData.flatWeaknessOffset) * catData.multiplier;
            catBonusPct = (catData.multiplier - 1.0) * 100.0;
            if (catData.shouldHeal) {
                attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + 1.5));
            }
            dummy.getWorld().spawnParticle(Particle.WITCH, dummy.getLocation().add(0, 1.2, 0), 10, 0.3, 0.3, 0.3, 0.05);
            attacker.playSound(dummy.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 0.7f, 1.8f);
        }

        // 5. Execute Calculations (Cull Threshold & Desperation Surge)
        double executeThreshold = snap != null ? snap.get(ItemStat.EXECUTE) : 0.0;
        boolean executeProc = false;
        boolean desperationProc = false;

        // Desperation surge (Attacker low HP)
        double attackerHpRatio = attacker.getHealth() / attacker.getMaxHealth();
        if (executeThreshold > 0 && attackerHpRatio <= 0.30) {
            desperationProc = true;
            totalDmg *= 1.35;
        }

        // Dummy Virtual Health Check
        double dummyHp = dummyHpMap.getOrDefault(dummy.getUniqueId(), 100.0);
        if (executeThreshold > 0 && dummyHp <= executeThreshold) {
            executeProc = true;
            totalDmg *= 1.50; // +50% true execute cull
            dummy.getWorld().spawnParticle(Particle.LAVA, dummy.getLocation().add(0, 1.2, 0), 10, 0.3, 0.3, 0.3, 0.0);
            attacker.playSound(dummy.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.6f);
        }

        // Apply damage to dummy virtual HP
        dummyHp -= totalDmg;
        boolean executedDeath = false;
        if (dummyHp <= 0) {
            executedDeath = true;
            dummyHp = 100.0; // Reset
            dummy.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, dummy.getLocation().add(0, 1.2, 0), 20, 0.4, 0.5, 0.4, 0.1);
            attacker.playSound(dummy.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.5f);
        }
        dummyHpMap.put(dummy.getUniqueId(), dummyHp);

        // 6. Stasis Buffer
        double stasisPct = snap != null ? snap.get(ItemStat.STASIS) : 0.0;

        // 7. Cleave simulation
        double cleavePct = snap != null ? snap.get(ItemStat.CLEAVE) : 0.0;
        if (cleavePct > 0) {
            dummy.getWorld().spawnParticle(Particle.SWEEP_ATTACK, dummy.getLocation().add(0, 1.0, 0), 2, 0.5, 0.1, 0.5, 0.0);
        }

        // 8. Siphon Calculations (Life Leech)
        double siphonPct = snap != null ? snap.get(ItemStat.SIPHON) : 0.0;
        double healed = 0.0;
        if (siphonPct > 0) {
            healed = totalDmg * (siphonPct / 100.0);
            attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + healed));
        }

        // 9. Update Dedicated Hologram Display
        updateDummyHologram(dummy, totalDmg, activeSurge, cadenceProc, (int) resonanceCadence, hitCount,
                healed, stasisPct, cleavePct, catData, catBonusPct, executeProc, desperationProc, dummyHp, executedDeath);

        // 10. Action Bar Telemetry
        StringBuilder ab = new StringBuilder();
        ab.append("&c🗡 &l").append(String.format("%.1f", totalDmg)).append(" DMG");
        if (activeSurge > 0) {
            ab.append(" &8| &e⚡ Surge: &a+").append((int) activeSurge).append("%");
        }
        if (resonanceCadence > 0) {
            int step = (hitCount % (int) resonanceCadence == 0) ? (int) resonanceCadence : (hitCount % (int) resonanceCadence);
            ab.append(" &8| &d⚡ Cadence: &f[").append(step).append("/").append((int) resonanceCadence).append("]");
            if (cadenceProc) ab.append(" &e💥");
        }
        if (catBonusPct > 0) {
            ab.append(" &8| &5🔮 Cat: &d+").append((int) catBonusPct).append("%");
        }
        if (executeProc) {
            ab.append(" &8| &4☠ EXECUTE!");
        } else if (desperationProc) {
            ab.append(" &8| &c⚔ DESPERATION!");
        }
        if (healed > 0) {
            ab.append(" &8| &a💚 +").append(String.format("%.1f", healed)).append(" HP");
        }
        attacker.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.colorize(ab.toString())));

        // 11. Sound FX & Particles
        if (cadenceProc) {
            attacker.playSound(dummy.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 1.8f);
            attacker.playSound(dummy.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 2.0f);
            dummy.getWorld().spawnParticle(Particle.EXPLOSION, dummy.getLocation().add(0, 1.2, 0), 1, 0, 0, 0, 0);
        } else {
            attacker.playSound(dummy.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
            dummy.getWorld().spawnParticle(Particle.CRIT, dummy.getLocation().add(0, 1.4, 0), 8, 0.2, 0.2, 0.2, 0.05);
        }
    }

    private void updateDummyHologram(ArmorStand dummy, double totalDmg, double activeSurge, boolean cadenceProc,
                                     int cadenceMax, int hitCount, double healed, double stasisPct, double cleavePct,
                                     dc.dcessentials.modules.items.listener.StatCombatListener.CatalystData catData,
                                     double catBonusPct, boolean executeProc, boolean desperationProc,
                                     double dummyHp, boolean executedDeath) {
        World world = dummy.getWorld();
        TextDisplay display = findOrCreateDisplay(dummy);
        if (display == null) return;

        List<String> lines = new ArrayList<>();
        StringBuilder dmgLine = new StringBuilder("&c&l🗡 ").append(String.format("%.1f", totalDmg)).append(" DMG");
        if (activeSurge > 0) {
            dmgLine.append(" &8[&e+").append((int) activeSurge).append("% Surge&8]");
        }
        lines.add(dmgLine.toString());

        if (executeProc) {
            lines.add("&4☠ &lEXECUTE CULL TRIGGERED! &c(+50% True DMG)");
        } else if (desperationProc) {
            lines.add("&c⚔ &lDESPERATION SURGE! &e(+35% Low HP Bonus)");
        }

        if (catBonusPct > 0) {
            lines.add("&5🔮 &lCATALYST INVERSION: &d+" + (int) catBonusPct + "% &7(" + String.join(", ", catData.debuffNames) + ")");
        }

        if (cadenceProc) {
            lines.add("&d💥 &lRESONANCE SHOCKWAVE!");
        } else if (cadenceMax > 0) {
            int step = (hitCount % cadenceMax == 0) ? cadenceMax : (hitCount % cadenceMax);
            lines.add("&d⚡ Resonance: &f[" + step + "/" + cadenceMax + "]");
        }

        if (healed > 0) {
            lines.add("&a💚 +" + String.format("%.1f", healed) + " HP (Siphon)");
        }
        if (stasisPct > 0) {
            lines.add("&b⏱ Stasis Buffer: &f" + (int) stasisPct + "%");
        }
        if (cleavePct > 0) {
            lines.add("&6⚔ Cleave: &f+" + (int) cleavePct + "% Arc");
        }

        // Dummy Virtual HP Status Line
        String hpColor = dummyHp <= 35.0 ? "&c" : (dummyHp <= 50.0 ? "&e" : "&a");
        lines.add("&7Dummy HP: " + hpColor + String.format("%.1f", dummyHp) + "/100 &8(" + (int) dummyHp + "%)" +
                (executedDeath ? " &a[RESET]" : (dummyHp <= 35.0 ? " &c[EXECUTE READY]" : "")));

        String fullText = String.join("\n", lines);
        display.text(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.colorize(fullText)));

        // Cancel previous reset task and schedule a 4.0s idle reset
        BukkitTask prev = resetTaskMap.remove(dummy.getUniqueId());
        if (prev != null) prev.cancel();

        BukkitTask resetTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (display.isValid()) {
                    double hp = dummyHpMap.getOrDefault(dummy.getUniqueId(), 100.0);
                    display.text(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.colorize(
                            "&7[Ready for Hit]\n&8Dummy HP: " + (hp <= 35.0 ? "&c" : "&a") + (int) hp + "/100 HP\n&8[Right-Click to Toggle HP Mode]"
                    )));
                }
                resetTaskMap.remove(dummy.getUniqueId());
            }
        }.runTaskLater(DCCosmetics.getInstance(), 80L); // 4.0 seconds

        resetTaskMap.put(dummy.getUniqueId(), resetTask);
    }

    private TextDisplay findOrCreateDisplay(ArmorStand dummy) {
        // 1. Search nearby entities for any TextDisplay with DUMMY_KEY to reuse or prune duplicates
        TextDisplay found = null;
        List<TextDisplay> duplicates = new ArrayList<>();

        for (Entity ent : dummy.getNearbyEntities(1.5, 3.5, 1.5)) {
            if (ent instanceof TextDisplay td) {
                PersistentDataContainer pdc = td.getPersistentDataContainer();
                if (pdc.has(DUMMY_KEY, PersistentDataType.INTEGER)) {
                    if (found == null && td.isValid()) {
                        found = td;
                    } else {
                        duplicates.add(td);
                    }
                }
            }
        }

        // Clean up any stray/duplicate text displays immediately
        for (TextDisplay dup : duplicates) {
            dup.remove();
        }

        if (found != null) {
            dummy.getPersistentDataContainer().set(DISPLAY_KEY, PersistentDataType.STRING, found.getUniqueId().toString());
            found.teleport(dummy.getLocation().clone().add(0, 3.0, 0));
            return found;
        }

        // 2. Check PDC UUID if nearby search didn't find one
        PersistentDataContainer pdc = dummy.getPersistentDataContainer();
        if (pdc.has(DISPLAY_KEY, PersistentDataType.STRING)) {
            try {
                UUID displayUuid = UUID.fromString(pdc.get(DISPLAY_KEY, PersistentDataType.STRING));
                Entity ent = Bukkit.getEntity(displayUuid);
                if (ent instanceof TextDisplay td && td.isValid()) {
                    td.teleport(dummy.getLocation().clone().add(0, 3.0, 0));
                    return td;
                }
            } catch (Exception ignored) {}
        }

        // 3. Only spawn a fresh display if none exists
        Location holoLoc = dummy.getLocation().clone().add(0, 3.0, 0);
        TextDisplay display = dummy.getWorld().spawn(holoLoc, TextDisplay.class, td -> {
            td.text(LegacyComponentSerializer.legacySection().deserialize(ColorUtil.colorize("&7[Ready for Hit]")));
            td.setBillboard(Display.Billboard.CENTER);
            td.setViewRange(24.0f);
            td.setShadowed(true);
            td.getPersistentDataContainer().set(DUMMY_KEY, PersistentDataType.INTEGER, 2);
        });

        pdc.set(DISPLAY_KEY, PersistentDataType.STRING, display.getUniqueId().toString());
        return display;
    }

    private double getBaseWeaponDamage(ItemStack weapon) {
        if (weapon == null || weapon.getType().isAir()) return 1.0;
        Material mat = weapon.getType();
        double dmg = switch (mat) {
            case NETHERITE_SWORD -> 8.0;
            case DIAMOND_SWORD -> 7.0;
            case IRON_SWORD -> 6.0;
            case STONE_SWORD -> 5.0;
            case WOODEN_SWORD, GOLDEN_SWORD -> 4.0;
            case NETHERITE_AXE -> 10.0;
            case DIAMOND_AXE -> 9.0;
            case IRON_AXE -> 9.0;
            case STONE_AXE -> 9.0;
            case WOODEN_AXE, GOLDEN_AXE -> 7.0;
            case MACE -> 6.0;
            case TRIDENT -> 9.0;
            default -> 1.0;
        };

        if (weapon.hasItemMeta() && weapon.getItemMeta().hasEnchant(Enchantment.SHARPNESS)) {
            int level = weapon.getItemMeta().getEnchantLevel(Enchantment.SHARPNESS);
            dmg += (0.5 * level + 0.5);
        }
        return dmg;
    }
}
