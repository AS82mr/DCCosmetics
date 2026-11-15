package dc.dcessentials.modules.items.tester;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.modules.items.listener.StatCombatListener;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.model.ItemStat;
import dc.dcessentials.modules.items.stats.PlayerStatSnapshot;
import dc.dcessentials.modules.items.stats.StatAggregator;
import dc.dcessentials.modules.items.stats.StatCombatEngine;
import dc.dcessentials.modules.pets.PetManager;
import dc.dcessentials.modules.pets.model.PetPerkEffectType;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StatTesterManager implements Listener {

    public static final NamespacedKey DUMMY_KEY = new NamespacedKey(DCCosmetics.getInstance(), "dce_stat_dummy");
    public static final NamespacedKey DISPLAY_KEY = new NamespacedKey(DCCosmetics.getInstance(), "dce_dummy_display_uuid");

    private static StatTesterManager activeInstance;

    private final ItemRegistry itemRegistry;
    private final StatAggregator statAggregator;
    private final Map<UUID, Integer> cadenceMap = new HashMap<>();
    private final Map<UUID, long[]> surgeMap = new HashMap<>(); // Attacker UUID -> [LastHitTime, Stacks]
    private final Map<UUID, Long> playerDebounceMap = new HashMap<>(); // Attacker UUID -> LastHitTimestamp
    private final Map<UUID, BukkitTask> resetTaskMap = new HashMap<>(); // Dummy UUID -> ResetTask
    private final Map<UUID, Double> dummyHpMap = new HashMap<>(); // Dummy UUID -> Virtual HP (0 - 100.0)
    private final Set<UUID> chatDebugPlayers = ConcurrentHashMap.newKeySet();

    public StatTesterManager(ItemRegistry itemRegistry, StatAggregator statAggregator) {
        this.itemRegistry = itemRegistry;
        this.statAggregator = statAggregator;
        activeInstance = this;
    }

    public static StatTesterManager getActiveInstance() {
        return activeInstance;
    }

    public boolean isChatDebugEnabled(Player player) {
        return player != null && chatDebugPlayers.contains(player.getUniqueId());
    }

    public void setChatDebugEnabled(Player player, boolean enabled) {
        if (player == null) return;
        if (enabled) {
            chatDebugPlayers.add(player.getUniqueId());
        } else {
            chatDebugPlayers.remove(player.getUniqueId());
        }
    }

    public boolean toggleChatDebug(Player player) {
        if (player == null) return false;
        if (chatDebugPlayers.contains(player.getUniqueId())) {
            chatDebugPlayers.remove(player.getUniqueId());
            return false;
        }
        chatDebugPlayers.add(player.getUniqueId());
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        chatDebugPlayers.remove(uuid);
        cadenceMap.remove(uuid);
        surgeMap.remove(uuid);
        playerDebounceMap.remove(uuid);
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

        List<String> mathSteps = new ArrayList<>();
        mathSteps.add(fmt(baseDmg) + " (Raw)");
        double totalDmg = baseDmg;

        // 2. Surge Calculations (Combo Momentum — unified +4% per stack up to stacked surgeCap via StatCombatEngine)
        double surgeCap = snap != null ? snap.get(ItemStat.SURGE) : 0.0;
        double activeSurge = StatCombatEngine.computeSurgeBonus(surgeMap, pUuid, surgeCap, now);
        long[] sArr = surgeMap.get(pUuid);
        int surgeStacks = sArr != null ? (int) sArr[1] : 0;
        if (activeSurge > 0.0) {
            double next = totalDmg * (1.0 + (activeSurge / 100.0));
            mathSteps.add("+" + fmt(activeSurge) + "% Surge (+" + fmt(next - totalDmg) + " = " + fmt(next) + ")");
            totalDmg = next;
        }

        // 3. Resonance Calculations (Acoustic Cadence Counter)
        double resonanceCadence = snap != null ? snap.get(ItemStat.RESONANCE) : 0.0;
        boolean cadenceProc = false;
        int hitCount = 0;
        if (resonanceCadence > 0) {
            hitCount = cadenceMap.getOrDefault(pUuid, 0) + 1;
            cadenceMap.put(pUuid, hitCount);
            if (hitCount % Math.max(2, (int) Math.round(resonanceCadence)) == 0) {
                cadenceProc = true;
                double next = totalDmg * 1.25; // +25% harmonic shatter burst (matches live combat)
                mathSteps.add("+25% Resonance (+" + fmt(next - totalDmg) + " = " + fmt(next) + ")");
                totalDmg = next;
            }
        }

        // 4. Catalyst Inversion (Debuffs Inverted into Combat Damage)
        double catalystPct = snap != null ? snap.get(ItemStat.CATALYST) : 0.0;
        StatCombatListener.CatalystData catData = StatCombatListener.calculateCatalyst(attacker, catalystPct);
        double catBonusPct = 0.0;
        if (catData.multiplier > 1.0 || catData.flatWeaknessOffset > 0) {
            double next = (totalDmg + catData.flatWeaknessOffset) * catData.multiplier;
            catBonusPct = (catData.multiplier - 1.0) * 100.0;
            mathSteps.add("+" + fmt(catBonusPct) + "% Catalyst (+" + fmt(next - totalDmg) + " = " + fmt(next) + ")");
            totalDmg = next;
            if (catData.shouldHeal) {
                attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + 1.5));
            }
            dummy.getWorld().spawnParticle(Particle.WITCH, dummy.getLocation().add(0, 1.2, 0), 10, 0.3, 0.3, 0.3, 0.05);
            attacker.playSound(dummy.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 0.7f, 1.8f);
        }

        // 5. Execute Calculations (Wounded <= 35%, Desperation <= 30% attacker HP, & Cull)
        double executeStatPct = snap != null ? Math.min(50.0, snap.get(ItemStat.EXECUTE)) : 0.0;
        double dummyHp = dummyHpMap.getOrDefault(dummy.getUniqueId(), 100.0);
        boolean woundedExecuteProc = false;
        boolean executeCullProc = false;
        boolean desperationProc = false;

        if (executeStatPct > 0.0 && dummyHp <= 35.0) {
            woundedExecuteProc = true;
            double next = totalDmg * (1.0 + (executeStatPct / 100.0));
            mathSteps.add("+" + fmt(executeStatPct) + "% Execute (+" + fmt(next - totalDmg) + " = " + fmt(next) + ")");
            totalDmg = next;
        }

        double maxAttackerHp = attacker.getMaxHealth();
        double attackerHpRatio = maxAttackerHp > 0 ? attacker.getHealth() / maxAttackerHp : 1.0;
        if (executeStatPct > 0.0 && attackerHpRatio <= 0.30) {
            desperationProc = true;
            double next = totalDmg * 1.35;
            mathSteps.add("+35% Desperation (+" + fmt(next - totalDmg) + " = " + fmt(next) + ")");
            totalDmg = next;
        }

        if (executeStatPct > 0.0 && dummyHp <= Math.min(35.0, executeStatPct)) {
            executeCullProc = true;
            dummy.getWorld().spawnParticle(Particle.LAVA, dummy.getLocation().add(0, 1.2, 0), 10, 0.3, 0.3, 0.3, 0.0);
            attacker.playSound(dummy.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.6f);
        }

        // 5b. Active Pet non-stat combat multipliers (Mob DMG, Boss Slayer, Bloodlust, Permafrost, Debuff Alchemist, Keystone Harmony)
        double petMobBonus = 0.0;
        List<String> petPerkNotes = new ArrayList<>();
        PetManager petMgr = DCCosmetics.getInstance().getPetManager();
        if (petMgr != null) {
            List<PetManager.PerkStepBonus> perkSteps = petMgr.evaluateOffensivePerkBonuses(attacker, dummy);
            double baseBeforePets = totalDmg;
            for (PetManager.PerkStepBonus ps : perkSteps) {
                if (ps.perkName().equals("Mob DMG")) petMobBonus = ps.pct();
                double delta = baseBeforePets * (ps.pct() / 100.0);
                totalDmg += delta;
                mathSteps.add("+" + fmt(ps.pct()) + "% " + ps.perkName() + " (+" + fmt(delta) + " = " + fmt(totalDmg) + ")");
                petPerkNotes.add(ps.perkName() + " (+" + fmt(ps.pct()) + "%)");
            }
        }

        // Apply damage to dummy virtual HP
        double postArmorDummyDmg = totalDmg;
        double stasisPct = snap != null ? Math.min(75.0, snap.get(ItemStat.STASIS)) : 0.0;
        double stasisDebt = stasisPct > 0.0 ? postArmorDummyDmg * (stasisPct / 100.0) : 0.0;

        double newDummyHp = dummyHp - totalDmg;
        boolean executedDeath = false;
        if (newDummyHp <= 0) {
            executedDeath = true;
            newDummyHp = 100.0; // Reset
            dummy.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, dummy.getLocation().add(0, 1.2, 0), 20, 0.4, 0.5, 0.4, 0.1);
            attacker.playSound(dummy.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.5f);
        }
        dummyHpMap.put(dummy.getUniqueId(), newDummyHp);

        // 7. Cleave simulation
        double cleavePct = snap != null ? Math.min(100.0, snap.get(ItemStat.CLEAVE)) : 0.0;
        double cleaveDmg = cleavePct > 0.0 ? postArmorDummyDmg * (cleavePct / 100.0) : 0.0;
        if (cleavePct > 0) {
            dummy.getWorld().spawnParticle(Particle.SWEEP_ATTACK, dummy.getLocation().add(0, 1.0, 0), 2, 0.5, 0.1, 0.5, 0.0);
        }

        // 8. Siphon Calculations (Life Leech & Absorption Overflow)
        double siphonPct = snap != null ? Math.min(50.0, snap.get(ItemStat.SIPHON)) : 0.0;
        double healed = 0.0;
        double absorbAdded = 0.0;
        if (siphonPct > 0) {
            double rawLeech = postArmorDummyDmg * (siphonPct / 100.0);
            double curHp = attacker.getHealth();
            if (curHp < maxAttackerHp) {
                double actualHeal = Math.min(maxAttackerHp - curHp, rawLeech);
                attacker.setHealth(curHp + actualHeal);
                healed = actualHeal;
            } else {
                float curAbs = (float) attacker.getAbsorptionAmount();
                if (curAbs < 12.0f) {
                    float add = Math.min(12.0f - curAbs, (float) (rawLeech * 0.5));
                    attacker.setAbsorptionAmount(curAbs + add);
                    absorbAdded = add;
                }
            }
        }
        double petVampHeal = 0.0;
        if (petMgr != null) {
            double vampPct = petMgr.getActivePerkValue(attacker, PetPerkEffectType.VAMPIRIC_HEAL);
            if (vampPct > 0.0 && postArmorDummyDmg > 0.0 && attacker.getHealth() < maxAttackerHp) {
                double vh = Math.min(maxAttackerHp - attacker.getHealth(), postArmorDummyDmg * (vampPct / 100.0));
                attacker.setHealth(attacker.getHealth() + vh);
                petVampHeal = vh;
            }
        }

        // 9. Update Dedicated Hologram Display
        updateDummyHologram(dummy, totalDmg, activeSurge, cadenceProc, (int) resonanceCadence, hitCount,
                healed + petVampHeal, stasisPct, cleavePct, catData, catBonusPct, woundedExecuteProc || executeCullProc, desperationProc, newDummyHp, executedDeath);

        // 9b. Verbose Chat Telemetry when enabled
        if (isChatDebugEnabled(attacker)) {
            if (petVampHeal > 0.0) {
                petPerkNotes.add("Vampiric Heal (+" + fmt(petVampHeal) + " HP)");
            }
            if (stasisDebt > 0.05) {
                mathSteps.add("-" + fmt(stasisPct) + "% Stasis Held (-" + fmt(stasisDebt) + " = " + fmt(postArmorDummyDmg - stasisDebt) + " + " + fmt(stasisDebt) + " Delayed)");
            }
            CombatTelemetryReport report = new CombatTelemetryReport(
                    "Stat Dummy [" + (int) dummyHp + "/100 HP]",
                    baseDmg,
                    postArmorDummyDmg,
                    postArmorDummyDmg,
                    surgeCap, activeSurge, surgeStacks,
                    resonanceCadence, hitCount, cadenceProc,
                    catalystPct, catBonusPct, catData.flatWeaknessOffset, catData.debuffNames,
                    executeStatPct, woundedExecuteProc, dummyHp, desperationProc, executeCullProc,
                    stasisPct, stasisDebt,
                    cleavePct, cleaveDmg, 0,
                    siphonPct, healed, absorbAdded, petVampHeal,
                    petMobBonus, 0.0, petPerkNotes,
                    null, 1.0, false,
                    mathSteps
            );
            sendCombatBreakdown(attacker, report);
        }

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
        if (woundedExecuteProc || executeCullProc) {
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

    public record CombatTelemetryReport(
            String targetLabel,
            double baseDamageNoStats,
            double preMitigationWithPerksAndScrolls,
            double finalDamage,
            double surgeCap,
            double surgeActivePct,
            int surgeStacks,
            double resonanceCadence,
            int resonanceStep,
            boolean resonanceProc,
            double catalystPct,
            double catalystBonusPct,
            double catalystFlatOffset,
            List<String> catalystDebuffs,
            double executeStatPct,
            boolean executeWoundedActive,
            double victimHpPctBeforeHit,
            boolean executeDesperationActive,
            boolean executeCullTriggered,
            double stasisPct,
            double stasisHeldDebt,
            double cleavePct,
            double cleaveArcDamage,
            int cleaveTargetsHit,
            double siphonPct,
            double siphonHpHealed,
            double siphonAbsorbAdded,
            double petVampiricHeal,
            double petMobDamageBonusPct,
            double petBossSlayerBonusPct,
            List<String> petPerkNotes,
            String emperorScrollNote,
            double emperorScrollMult,
            boolean emperorStasisSmash,
            List<String> mathSteps
    ) {}

    public void sendCombatBreakdown(Player attacker, CombatTelemetryReport r) {
        if (attacker == null || r == null) return;
        StatAggregator.DetailedBreakdown bd = statAggregator != null
                ? statAggregator.getDetailedBreakdown(attacker)
                : null;

        attacker.sendMessage(ColorUtil.colorize("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        attacker.sendMessage(ColorUtil.colorize("<#F59E0B>&l⚡ DCE COMBAT TELEMETRY &8» &fTarget: &c" + r.targetLabel()));

        boolean anyStatShown = false;

        if (r.resonanceCadence() > 0) {
            anyStatShown = true;
            int targetHits = Math.max(2, (int) Math.round(r.resonanceCadence()));
            int step = r.resonanceStep() <= 0 ? 1 : ((r.resonanceStep() % targetHits == 0) ? targetHits : (r.resonanceStep() % targetHits));
            String procTag = r.resonanceProc() ? " &8➜ &d&l💥 SHATTER +25% DMG" : "";
            attacker.sendMessage(ColorUtil.colorize("  &7» <#84E2CD>Resonance &8(" + targetHits + "-Hit &7[" + step + "/" + targetHits + "]&8): &f"
                    + formatSourceBreakdown(ItemStat.RESONANCE, bd, r.resonanceCadence(), true) + procTag));
        }

        if (r.surgeCap() > 0) {
            anyStatShown = true;
            attacker.sendMessage(ColorUtil.colorize("  &7» <#FCD34D>Surge &8(&e+" + fmt(r.surgeActivePct()) + "% &7active &8[" + r.surgeStacks()
                    + "x]&7, Cap &f" + fmt(r.surgeCap()) + "%&8): &f"
                    + formatSourceBreakdown(ItemStat.SURGE, bd, r.surgeCap(), false)));
        }

        if (r.catalystPct() > 0) {
            anyStatShown = true;
            String detail = (r.catalystBonusPct() > 0 || r.catalystFlatOffset() > 0)
                    ? " &8➜ &d+" + fmt(r.catalystBonusPct()) + "% DMG &7(" + String.join(", ", r.catalystDebuffs()) + ")"
                    : " &8➜ &7No active debuffs on attacker (0% applied)";
            attacker.sendMessage(ColorUtil.colorize("  &7» <#A855F7>Catalyst &8(&d" + fmt(r.catalystPct()) + "%&8): &f"
                    + formatSourceBreakdown(ItemStat.CATALYST, bd, r.catalystPct(), false) + detail));
        }

        if (r.executeStatPct() > 0) {
            anyStatShown = true;
            List<String> execOutcome = new ArrayList<>();
            if (r.executeWoundedActive()) {
                execOutcome.add("&c+" + fmt(r.executeStatPct()) + "% Wounded Bonus &7(Enemy " + fmt(r.victimHpPctBeforeHit()) + "% ≤ 35%)");
            } else {
                execOutcome.add("&7Enemy at " + fmt(r.victimHpPctBeforeHit()) + "% > 35% (Wounded inactive)");
            }
            if (r.executeDesperationActive()) execOutcome.add("&4+35% Desperation (Self ≤ 30%)");
            if (r.executeCullTriggered()) execOutcome.add("&4☠ CULL KILLED TARGET!");
            attacker.sendMessage(ColorUtil.colorize("  &7» <#EF4444>Execute &8(&c" + fmt(r.executeStatPct()) + "%&8): &f"
                    + formatSourceBreakdown(ItemStat.EXECUTE, bd, r.executeStatPct(), false)
                    + " &8➜ " + String.join(" &8| ", execOutcome)));
        }

        if (r.stasisPct() > 0) {
            anyStatShown = true;
            attacker.sendMessage(ColorUtil.colorize("  &7» <#06B6D4>Stasis &8(&b" + fmt(r.stasisPct()) + "%&8): &f"
                    + formatSourceBreakdown(ItemStat.STASIS, bd, r.stasisPct(), false)
                    + " &8➜ &bDebt Held: " + fmt(r.stasisHeldDebt()) + " DMG (2.5s)"));
        }

        if (r.cleavePct() > 0) {
            anyStatShown = true;
            attacker.sendMessage(ColorUtil.colorize("  &7» <#F97316>Cleave &8(&6" + fmt(r.cleavePct()) + "%&8): &f"
                    + formatSourceBreakdown(ItemStat.CLEAVE, bd, r.cleavePct(), false)
                    + " &8➜ &6Arc: " + fmt(r.cleaveArcDamage()) + " DMG (" + r.cleaveTargetsHit() + " nearby hit)"));
        }

        if (r.siphonPct() > 0) {
            anyStatShown = true;
            String absText = r.siphonAbsorbAdded() > 0 ? " &e(+" + fmt(r.siphonAbsorbAdded()) + " Absorb)" : "";
            attacker.sendMessage(ColorUtil.colorize("  &7» <#E11D48>Siphon &8(&c" + fmt(r.siphonPct()) + "%&8): &f"
                    + formatSourceBreakdown(ItemStat.SIPHON, bd, r.siphonPct(), false)
                    + " &8➜ &a+" + fmt(r.siphonHpHealed()) + " HP &8(&c+" + fmt(r.siphonHpHealed() / 2.0) + " ❤&8)" + absText));
        }

        // Pet Non-Stat Combat Perks & Boss/Emperor Scrolls
        if (bd != null && bd.petInfo() != null) {
            List<String> perkParts = r.petPerkNotes() != null && !r.petPerkNotes().isEmpty()
                    ? new ArrayList<>(r.petPerkNotes())
                    : new ArrayList<>();
            if (perkParts.isEmpty()) {
                for (var perk : bd.petInfo().unlockedPerks()) {
                    if (perk.effectType() == PetPerkEffectType.BONUS_MOB_DAMAGE && r.petMobDamageBonusPct() > 0) {
                        perkParts.add(perk.perkName() + " (+" + fmt(perk.value()) + "% Mob DMG)");
                    } else if (perk.effectType() == PetPerkEffectType.BOSS_SLAYER_DAMAGE && r.petBossSlayerBonusPct() > 0) {
                        perkParts.add(perk.perkName() + " (+" + fmt(perk.value()) + "% Boss DMG)");
                    } else if (perk.effectType() == PetPerkEffectType.VAMPIRIC_HEAL && r.petVampiricHeal() > 0) {
                        perkParts.add(perk.perkName() + " (+" + fmt(r.petVampiricHeal()) + " HP)");
                    }
                }
            }
            if (!perkParts.isEmpty()) {
                anyStatShown = true;
                attacker.sendMessage(ColorUtil.colorize("  &7» <#C084FC>Pet Perks &8(" + bd.petInfo().petName()
                        + " Lv." + bd.petInfo().level() + "): &f" + String.join(" &8+ &f", perkParts)));
            }
        }

        if ((r.emperorScrollNote() != null && !r.emperorScrollNote().isBlank()) || r.emperorStasisSmash()) {
            anyStatShown = true;
            List<String> bossNotes = new ArrayList<>();
            if (r.emperorScrollNote() != null && !r.emperorScrollNote().isBlank()) {
                bossNotes.add(r.emperorScrollNote());
            }
            if (r.emperorStasisSmash()) {
                bossNotes.add("&b1.5x Temporal Shatter");
            }
            attacker.sendMessage(ColorUtil.colorize("  &7» <#F59E0B>Boss Modifiers: &f" + String.join(" &8| ", bossNotes)));
        }

        if (!anyStatShown) {
            attacker.sendMessage(ColorUtil.colorize("  &7» &8No offensive DCE stats or pet combat perks active on this hit."));
        }

        if (r.mathSteps() != null && !r.mathSteps().isEmpty()) {
            attacker.sendMessage(ColorUtil.colorize("  &eMath: &f" + String.join(" &8➜ &f", r.mathSteps())));
        }

        double totalHealed = r.siphonHpHealed() + r.petVampiricHeal();
        String healSummary = totalHealed > 0
                ? " &8| &aRestored: &c+" + fmt(totalHealed / 2.0) + " ❤ &7(" + fmt(totalHealed) + " HP)"
                : (r.siphonAbsorbAdded() > 0 ? " &8| &e+" + fmt(r.siphonAbsorbAdded() / 2.0) + " ❤ Absorb" : "");

        attacker.sendMessage(ColorUtil.colorize("  &fResult: &7Damage (No Stats) = &f" + fmt(r.baseDamageNoStats())
                + " &8➜ &aDamage w/ Stats = &c&l" + fmt(r.finalDamage())
                + " &8(&7Pre-Armor: &f" + fmt(r.preMitigationWithPerksAndScrolls()) + "&8)" + healSummary));
        attacker.sendMessage(ColorUtil.colorize("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));

        TesterDiscordWebhook.sendCombatEmbed(attacker, r, bd, this);
    }

    public void logIncomingDefenseBreakdown(Player victim,
                                            Entity damager,
                                            EntityDamageEvent.DamageCause cause,
                                            double rawDamage,
                                            StatCombatEngine.IncomingDamageResult defRes,
                                            double petDmgReductionPct,
                                            double finalHealthLost) {
        if (!isChatDebugEnabled(victim) || defRes == null) return;
        StatAggregator.DetailedBreakdown bd = statAggregator != null ? statAggregator.getDetailedBreakdown(victim) : null;
        String srcName = damager != null ? damager.getName() : (cause != null ? cause.name() : "HAZARD");

        victim.sendMessage(ColorUtil.colorize("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        victim.sendMessage(ColorUtil.colorize("<#38BDF8>&l🛡 DCE DEFENSE TELEMETRY &8» &fFrom: &e" + srcName + " &8(" + (cause != null ? cause.name() : "CUSTOM") + ")"));

        if (defRes.wardOrAegisReductionPct() > 0) {
            ItemStat usedStat = (cause != null && (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                    || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                    || cause == EntityDamageEvent.DamageCause.PROJECTILE))
                    ? ItemStat.WARD : ItemStat.AEGIS;
            victim.sendMessage(ColorUtil.colorize("  &7» <#3B82F6>" + usedStat.getDisplayName() + " &8(-" + fmt(defRes.wardOrAegisReductionPct()) + "%): &f"
                    + formatSourceBreakdown(usedStat, bd, defRes.wardOrAegisReductionPct(), false)));
        }
        if (defRes.deflected()) {
            double defPct = statAggregator != null ? statAggregator.getTotalStat(victim, ItemStat.DEFLECT) : 0.0;
            victim.sendMessage(ColorUtil.colorize("  &7» <#38BDF8>Deflect &8(&aPARRIED -50% DMG!&8): &f"
                    + formatSourceBreakdown(ItemStat.DEFLECT, bd, Math.min(35.0, defPct), false)));
        }
        if (defRes.bastionCapped()) {
            double basPct = statAggregator != null ? statAggregator.getTotalStat(victim, ItemStat.BASTION) : 0.0;
            victim.sendMessage(ColorUtil.colorize("  &7» <#60A5FA>Bastion &8(&aSINGLE-HIT CEILING!&8): &f"
                    + formatSourceBreakdown(ItemStat.BASTION, bd, Math.min(60.0, basPct), false)));
        }
        if (petDmgReductionPct > 0 && bd != null && bd.petInfo() != null) {
            victim.sendMessage(ColorUtil.colorize("  &7» <#C084FC>Pet Defense &8(-" + fmt(petDmgReductionPct) + "%): &f"
                    + bd.petInfo().petName() + " [Lv." + bd.petInfo().level() + "]"));
        }
        if (defRes.unstableVulnerabilityApplied()) {
            victim.sendMessage(ColorUtil.colorize("  &7» <#EF4444>Keystone Instability &8(&c+15% Vulnerability — Heat ≥ 80!&8)"));
        }
        List<String> defMath = new ArrayList<>();
        double rVal = rawDamage;
        defMath.add(fmt(rVal) + " (Raw)");
        if (defRes.unstableVulnerabilityApplied()) {
            double next = rVal * 1.15;
            defMath.add("+15% Instability (+" + fmt(next - rVal) + " = " + fmt(next) + ")");
            rVal = next;
        }
        if (defRes.deflected()) {
            double next = rVal * 0.50;
            defMath.add("-50% Deflect (-" + fmt(rVal - next) + " = " + fmt(next) + ")");
            rVal = next;
        }
        if (defRes.wardOrAegisReductionPct() > 0) {
            double next = rVal * (1.0 - (defRes.wardOrAegisReductionPct() / 100.0));
            defMath.add("-" + fmt(defRes.wardOrAegisReductionPct()) + "% Ward/Aegis (-" + fmt(rVal - next) + " = " + fmt(next) + ")");
            rVal = next;
        }
        if (Math.abs(rVal - defRes.damage()) > 0.05) {
            defMath.add("Bastion/Cap = " + fmt(defRes.damage()));
        }
        if (defRes.damage() - finalHealthLost > 0.05) {
            defMath.add("Armor (-" + fmt(defRes.damage() - finalHealthLost) + " = " + fmt(finalHealthLost) + ")");
        }
        victim.sendMessage(ColorUtil.colorize("  &eMath: &f" + String.join(" &8➜ &f", defMath)));

        victim.sendMessage(ColorUtil.colorize("  &fResult: &7Raw Incoming = &f" + fmt(rawDamage)
                + " &8➜ &bAfter Stats = &f" + fmt(defRes.damage())
                + " &8➜ &cFinal Lost = &f" + fmt(finalHealthLost) + " HP &8(" + fmt(finalHealthLost / 2.0) + " ❤)"));
        victim.sendMessage(ColorUtil.colorize("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));

        TesterDiscordWebhook.sendDefenseEmbed(victim, srcName, cause, rawDamage, defRes, petDmgReductionPct, finalHealthLost, bd, statAggregator, this);
    }

    public void logGatheringEvent(Player player, String activity, String detail) {
        if (!isChatDebugEnabled(player)) return;
        player.sendMessage(ColorUtil.colorize("<#34D399>&l⛏ DCE GATHER TELEMETRY &8[&f" + activity + "&8] &7» &f" + detail));
        TesterDiscordWebhook.sendGatheringEmbed(player, activity, detail);
    }

    public void printFullStatDiagnosticSheet(Player player) {
        if (player == null || statAggregator == null) return;
        PlayerStatSnapshot snap = statAggregator.getSnapshot(player);
        StatAggregator.DetailedBreakdown bd = statAggregator.getDetailedBreakdown(player);

        player.sendMessage(ColorUtil.colorize("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        player.sendMessage(ColorUtil.colorize("<#10B981>&l📊 FULL 16-STAT BREAKDOWN &8(&f" + player.getName() + "&8)"));
        int activeCount = 0;
        for (ItemStat stat : ItemStat.values()) {
            double total = snap.get(stat);
            List<StatAggregator.StatSourceContribution> sources = bd.getSources(stat);
            if (total <= 0.0 && sources.isEmpty()) continue;
            activeCount++;
            boolean isRes = stat == ItemStat.RESONANCE;
            String valStr = isRes ? ((int) total + "-Hit") : ("+" + fmt(total) + "%");
            player.sendMessage(ColorUtil.colorize("  " + stat.getColor() + stat.getIcon() + " " + stat.getDisplayName()
                    + " &8[&f" + valStr + "&8]: &7" + formatSourceBreakdown(stat, bd, total, isRes)));
        }
        if (activeCount == 0) {
            player.sendMessage(ColorUtil.colorize("  &7All 16 DCE stats are currently at &f0&7. Equip DCE armor/weapons/accessories/pets to inspect."));
        }
        if (bd.petInfo() != null) {
            List<String> extras = new ArrayList<>();
            for (var perk : bd.petInfo().unlockedPerks()) {
                if (perk.effectType().getMappedStat() == null) {
                    extras.add(perk.perkName() + " [" + perk.effectType().name() + ": " + fmt(perk.value()) + "%]");
                }
            }
            if (!extras.isEmpty()) {
                player.sendMessage(ColorUtil.colorize("  <#C084FC>🐾 Extra Pet Perks &8(" + bd.petInfo().petName() + " Lv." + bd.petInfo().level() + "): &f"
                        + String.join(" &8| &f", extras)));
            }
        }
        player.sendMessage(ColorUtil.colorize("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        TesterDiscordWebhook.sendStatSheetEmbed(player, snap, bd, this);
    }

    public String formatSourceBreakdown(ItemStat stat, StatAggregator.DetailedBreakdown bd, double cappedTotal, boolean isResonance) {
        if (bd == null) return fmt(cappedTotal) + (isResonance ? "-Hit" : "%");
        List<StatAggregator.StatSourceContribution> list = bd.getSources(stat);
        if (list.isEmpty()) return "0";
        if (isResonance) {
            List<String> rParts = new ArrayList<>(list.size());
            double lowest = Double.MAX_VALUE;
            for (var entry : list) {
                if (entry.value() > 0 && entry.value() < lowest) lowest = entry.value();
                rParts.add((int) Math.round(entry.value()) + "-Hit &7(" + entry.label() + ")&f");
            }
            if (list.size() == 1) {
                return rParts.get(0);
            }
            int extra = list.size() - 1;
            int best = (int) Math.round(lowest == Double.MAX_VALUE ? cappedTotal : lowest);
            int eff = (int) Math.round(cappedTotal);
            return "Best of [" + String.join(", ", rParts) + "] = " + best + "-Hit &8- " + extra + " Extra Source"
                    + (extra > 1 ? "s" : "") + " ➜ &b" + eff + "-Hit";
        }
        List<String> parts = new ArrayList<>(list.size());
        double rawSum = 0.0;
        for (var entry : list) {
            rawSum += entry.value();
            parts.add(fmt(entry.value()) + "% &7(" + entry.label() + ")&f");
        }
        String joined = String.join(" &8+ &f", parts);
        if (rawSum > cappedTotal + 0.05) {
            joined += " &8[&eCap: " + fmt(cappedTotal) + "%&8]";
        }
        return joined;
    }

    public static String fmtNum(double v) {
        return fmt(v);
    }

    private static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.05) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(Locale.US, "%.1f", v);
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
