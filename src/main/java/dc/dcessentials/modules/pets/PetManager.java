package dc.dcessentials.modules.pets;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.AdaptHook;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.model.ItemRarity;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.model.ItemStat;
import dc.dcessentials.modules.pets.config.PetsConfig;
import dc.dcessentials.modules.pets.gui.PetMenuGUI;
import dc.dcessentials.modules.pets.model.*;
import dc.dcessentials.modules.pets.render.PacketPetInstance;
import dc.dcessentials.modules.pets.vfx.ModelEngineVfxBridge;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Central coordinator for DCEPets: lifecycle, Lv 1->100 progressive stats & perks,
 * Skills XP delta polling, target tracking, auto-cast triggers, and database sync.
 */
public class PetManager implements Listener {

    private final DCCosmetics plugin;
    private final PetsConfig petsConfig;
    private final PetRegistry petRegistry;
    private final PetCombatDirector combatDirector;
    private final NamespacedKey speedModifierKey;

    private final Map<UUID, PlayerPetData> playerDataMap = new ConcurrentHashMap<>();
    private final Map<UUID, PacketPetInstance> activeInstances = new ConcurrentHashMap<>();
    private final Map<UUID, Long> skillCooldownMap = new ConcurrentHashMap<>();
    private final Map<UUID, LivingEntity> lockedTargets = new ConcurrentHashMap<>();
    private final Map<UUID, LivingEntity> lookTargets = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Double>> skillXpBaselines = new ConcurrentHashMap<>();
    private BukkitTask tickerTask;
    private long tickCounter = 0L;

    public PetManager(DCCosmetics plugin) {
        this.plugin = plugin;
        this.petsConfig = new PetsConfig(plugin);
        this.petRegistry = new PetRegistry(petsConfig);
        this.combatDirector = new PetCombatDirector(petsConfig);
        this.speedModifierKey = new NamespacedKey(plugin, "dce_pet_speed_bonus");
    }

    public void start() {
        petsConfig.load();
        petRegistry.loadPets();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(new PetMenuGUI.PetMenuListener(this), plugin);

        this.tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 2L, 2L);

        for (Player p : Bukkit.getOnlinePlayers()) {
            loadPlayer(p);
        }
    }

    public void reload() {
        petsConfig.load();
        petRegistry.loadPets();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerPetData data = playerDataMap.get(p.getUniqueId());
            if (data != null && data.getActivePetId() != null) {
                summonPet(p, data.getActivePetId(), true);
            }
            applySpeedPerkModifier(p);
            invalidateStats(p);
        }
    }

    public void shutdown() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
        ModelEngineVfxBridge.cleanupAll();
        for (Player p : Bukkit.getOnlinePlayers()) {
            removeSpeedPerkModifier(p);
        }
        for (PacketPetInstance inst : activeInstances.values()) {
            inst.destroyForAll();
        }
        activeInstances.clear();
        for (PlayerPetData data : playerDataMap.values()) {
            savePlayerSync(data);
        }
    }

    private void tickAll() {
        tickCounter += 2L;
        int trackerInterval = Math.max(2, petsConfig.getTrackerIntervalTicks());
        int skillPollInterval = Math.max(2, petsConfig.getSkillsPollIntervalTicks());
        boolean doTracker = (tickCounter % trackerInterval) == 0L;
        boolean doSkillsPoll = petsConfig.isSkillsXpEnabled() && (tickCounter % skillPollInterval) == 0L;
        boolean doAuraTick = (tickCounter % 20L) == 0L;

        for (Map.Entry<UUID, PacketPetInstance> entry : activeInstances.entrySet()) {
            UUID uuid = entry.getKey();
            Player owner = Bukkit.getPlayer(uuid);
            if (owner == null || !owner.isOnline()) {
                entry.getValue().destroyForAll();
                activeInstances.remove(uuid);
                continue;
            }
            entry.getValue().tick(owner, this);

            PlayerPetData data = playerDataMap.get(uuid);
            PetSpec spec = data != null && data.getActivePetId() != null
                    ? petRegistry.getPet(data.getActivePetId())
                    : null;

            if (spec == null) continue;

            if (doTracker) {
                LivingEntity looked = combatDirector.tickTargetTracker(owner, data, spec);
                if (looked != null) {
                    lookTargets.put(uuid, looked);
                    entry.getValue().setLookTarget(looked.getEyeLocation());
                } else {
                    lookTargets.remove(uuid);
                    entry.getValue().setLookTarget(null);
                }
            }

            if (doSkillsPoll) {
                Map<String, Double> baseline = skillXpBaselines.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
                Map<String, Double> deltas = AdaptHook.pollSkillXpDeltas(owner, baseline, petsConfig.isExcludeScrollBonusXp());
                if (!deltas.isEmpty()) {
                    double xpBoostPct = getActivePerkValue(owner, PetPerkEffectType.PET_XP_BOOST);
                    double totalEarned = 0.0;
                    for (Map.Entry<String, Double> gain : deltas.entrySet()) {
                        double catMult = petsConfig.getCategoryXpMultiplier(spec.getCategory(), gain.getKey());
                        totalEarned += gain.getValue() * catMult * (1.0 + (xpBoostPct / 100.0));
                    }
                    if (totalEarned > 0.05) {
                        addPetXp(owner, totalEarned);
                    }
                }
            }

            if (doAuraTick) {
                tickPassivePerks(owner, data);
            }
        }
    }

    private void tickPassivePerks(Player owner, PlayerPetData data) {
        double regenAmount = getActivePerkValue(owner, PetPerkEffectType.ALLY_AURA_REGEN);
        if (regenAmount > 0.0) {
            healIfHurt(owner, regenAmount);
            for (Entity e : owner.getWorld().getNearbyEntities(owner.getLocation(), 7.0, 4.5, 7.0)) {
                if (e instanceof Player p && !p.equals(owner) && data.isAlly(p.getUniqueId())) {
                    healIfHurt(p, regenAmount * 0.85);
                }
            }
        }
    }

    private void healIfHurt(Player p, double amount) {
        var maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
        if (maxAttr == null || p.isDead()) return;
        double maxHp = maxAttr.getValue();
        if (p.getHealth() < maxHp) {
            p.setHealth(Math.min(maxHp, p.getHealth() + amount));
        }
    }

    public PetsConfig getConfig() { return petsConfig; }
    public PetRegistry getRegistry() { return petRegistry; }
    public PetCombatDirector getCombatDirector() { return combatDirector; }

    public PlayerPetData getData(UUID uuid) {
        return playerDataMap.computeIfAbsent(uuid, PlayerPetData::new);
    }

    public PacketPetInstance getActiveInstance(UUID uuid) {
        return activeInstances.get(uuid);
    }

    public LivingEntity getLookTarget(UUID uuid) {
        return lookTargets.get(uuid);
    }

    /**
     * Returns total stat contributions from the player's active pet, combining
     * progressive Lv 1->100 base stats AND unlocked STAT_* passive perks.
     */
    public Map<ItemStat, Double> getActivePetStats(Player player) {
        if (player == null) return Collections.emptyMap();
        PlayerPetData data = playerDataMap.get(player.getUniqueId());
        if (data == null || data.getActivePetId() == null) return Collections.emptyMap();
        PetSpec spec = petRegistry.getPet(data.getActivePetId());
        if (spec == null) return Collections.emptyMap();
        PlayerPetData.OwnedPetEntry entry = data.getPetEntry(spec.getId());
        int level = entry != null ? entry.getLevel() : 1;

        EnumMap<ItemStat, Double> combined = spec.getStatsAtLevel(level);
        for (PetPerk perk : spec.getPerks()) {
            if (!perk.isUnlockedAt(level)) continue;
            ItemStat mapped = perk.getEffectType().getMappedStat();
            if (mapped == null) continue;
            double val = perk.getEffectiveValue(level, spec.getMaxLevel());
            if (val > 0.0) {
                if (mapped == ItemStat.RESONANCE) {
                    double cur = combined.getOrDefault(mapped, val);
                    combined.put(mapped, Math.min(cur, val));
                } else {
                    combined.put(mapped, Math.round((combined.getOrDefault(mapped, 0.0) + val) * 10.0) / 10.0);
                }
            }
        }
        return combined;
    }

    public double getActivePerkValue(Player player, PetPerkEffectType type) {
        if (player == null || type == null) return 0.0;
        PlayerPetData data = playerDataMap.get(player.getUniqueId());
        if (data == null || data.getActivePetId() == null) return 0.0;
        PetSpec spec = petRegistry.getPet(data.getActivePetId());
        if (spec == null) return 0.0;
        PlayerPetData.OwnedPetEntry entry = data.getPetEntry(spec.getId());
        int level = entry != null ? entry.getLevel() : 1;

        double total = 0.0;
        for (PetPerk perk : spec.getPerks()) {
            if (perk.getEffectType() == type && perk.isUnlockedAt(level)) {
                total += perk.getEffectiveValue(level, spec.getMaxLevel());
            }
        }
        return total;
    }

    public void summonPet(Player player, String petId, boolean silent) {
        if (player == null || petId == null) return;
        PetSpec spec = petRegistry.getPet(petId);
        if (spec == null) return;

        PlayerPetData data = getData(player.getUniqueId());
        PlayerPetData.OwnedPetEntry owned = data.getPetEntry(spec.getId());
        if (owned == null) {
            owned = data.unlockOrUpgradePet(spec.getId(), 1, 0.0, spec.getRarity());
        }

        PacketPetInstance old = activeInstances.remove(player.getUniqueId());
        if (old != null) {
            old.destroyForAll();
        }

        data.setActivePetId(spec.getId());
        ItemRarity effRarity = owned.getRarityOverride() != null ? owned.getRarityOverride() : spec.getRarity();
        PacketPetInstance instance = new PacketPetInstance(player, spec, owned.getLevel(), effRarity);
        activeInstances.put(player.getUniqueId(), instance);
        instance.tick(player, this);

        // Refresh Skill baseline cleanly when switching pets
        Map<String, Double> snap = AdaptHook.readSkillXpTotals(player);
        if (!snap.isEmpty()) {
            skillXpBaselines.put(player.getUniqueId(), new ConcurrentHashMap<>(snap));
        }

        applySpeedPerkModifier(player);
        invalidateStats(player);
        savePlayerAsync(data);

        if (!silent) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7f, 1.5f);
            DCMessage.sendGeneral(player, "Summoned Level " + owned.getLevel() + " "
                    + effRarity.getColorHex() + spec.getDisplayName() + "&#0CBF93!");
        }
    }

    public void dismissPet(Player player, boolean silent) {
        if (player == null) return;
        PacketPetInstance old = activeInstances.remove(player.getUniqueId());
        if (old != null) {
            old.destroyForAll();
        }
        PlayerPetData data = getData(player.getUniqueId());
        String prevId = data.getActivePetId();
        data.setActivePetId(null);
        lookTargets.remove(player.getUniqueId());
        lockedTargets.remove(player.getUniqueId());

        removeSpeedPerkModifier(player);
        invalidateStats(player);
        savePlayerAsync(data);

        if (!silent && prevId != null) {
            PetSpec spec = petRegistry.getPet(prevId);
            String name = spec != null ? spec.getDisplayName() : prevId;
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f);
            DCMessage.sendWarning(player, "Dismissed " + name + " back to your Pet Wardrobe.");
        }
    }

    public ItemStack withdrawPetToToken(Player player, String petId) {
        if (player == null || petId == null) return null;
        String canonical = PlayerPetData.canonicalizePetId(petId);
        if (canonical == null) return null;

        PlayerPetData data = getData(player.getUniqueId());
        PlayerPetData.OwnedPetEntry entry = data.getOwnedPets().remove(canonical);
        if (entry == null) return null;

        if (canonical.equalsIgnoreCase(data.getActivePetId())) {
            dismissPet(player, true);
        }

        PetSpec spec = petRegistry.getPet(canonical);
        if (spec == null) return null;

        savePlayerAsync(data);
        invalidateStats(player);
        return petRegistry.createPetTokenItem(spec, entry.getLevel(), entry.getXp(), entry.getRarityOverride());
    }

    public void addPetXp(Player player, double amount) {
        if (player == null || amount <= 0) return;
        PlayerPetData data = getData(player.getUniqueId());
        if (data.getActivePetId() == null) return;
        PetSpec spec = petRegistry.getPet(data.getActivePetId());
        if (spec == null) return;
        PlayerPetData.OwnedPetEntry entry = data.getPetEntry(spec.getId());
        if (entry == null || entry.getLevel() >= spec.getMaxLevel()) return;

        int previousLevel = entry.getLevel();
        double newXp = entry.getXp() + amount;
        boolean leveledUp = false;
        while (entry.getLevel() < spec.getMaxLevel()) {
            double req = spec.getXpRequiredForNextLevel(entry.getLevel(), petsConfig);
            if (newXp >= req && req > 0) {
                newXp -= req;
                entry.setLevel(entry.getLevel() + 1);
                leveledUp = true;
            } else {
                break;
            }
        }
        if (entry.getLevel() >= spec.getMaxLevel()) {
            newXp = 0.0;
        }
        entry.setXp(newXp);

        if (leveledUp) {
            ItemRarity effRarity = entry.getRarityOverride() != null ? entry.getRarityOverride() : spec.getRarity();
            PacketPetInstance inst = activeInstances.get(player.getUniqueId());
            if (inst != null) {
                inst.updateLevelAndRarity(player, entry.getLevel(), effRarity);
            }
            applySpeedPerkModifier(player);
            invalidateStats(player);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.85f, 1.35f);
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.4, 0), 24, 0.5, 0.6, 0.5, 0.1);
            DCMessage.sendGeneral(player, "Your companion " + effRarity.getColorHex() + spec.getDisplayName()
                    + " &#0CBF93reached Level &e" + entry.getLevel() + "&#0CBF93!");

            for (PetPerk perk : spec.getPerks()) {
                if (perk.getUnlockLevel() > previousLevel && perk.getUnlockLevel() <= entry.getLevel()) {
                    DCMessage.sendGeneral(player, "Unlocked companion perk &e" + perk.getName()
                            + " &#0CBF93on " + spec.getDisplayName() + "!");
                }
            }
            savePlayerAsync(data);
        }
    }

    public long getSkillCooldownRemainingMs(UUID playerUuid) {
        Long until = skillCooldownMap.get(playerUuid);
        if (until == null) return 0L;
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public double getEffectiveSkillCooldownMultiplier(Player player) {
        double hastePct = Math.min(55.0, getActivePerkValue(player, PetPerkEffectType.SKILL_COOLDOWN_HASTE));
        return Math.max(0.45, 1.0 - (hastePct / 100.0));
    }

    public double getEffectiveSkillPowerMultiplier(Player player) {
        double powerPct = getActivePerkValue(player, PetPerkEffectType.SKILL_POWER_BOOST);
        return 1.0 + (powerPct / 100.0);
    }

    private void applySpeedPerkModifier(Player player) {
        if (player == null || !player.isOnline()) return;
        AttributeInstance attr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) return;
        removeSpeedPerkModifier(player);
        double pct = getActivePerkValue(player, PetPerkEffectType.MOVEMENT_SPEED);
        if (pct > 0.0) {
            double bonus = Math.min(0.60, pct / 100.0);
            attr.addTransientModifier(new AttributeModifier(
                    speedModifierKey, bonus, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    private void removeSpeedPerkModifier(Player player) {
        if (player == null) return;
        AttributeInstance attr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) return;
        for (AttributeModifier mod : new ArrayList<>(attr.getModifiers())) {
            if (speedModifierKey.equals(mod.getKey())) {
                attr.removeModifier(mod);
            }
        }
    }

    private void invalidateStats(Player player) {
        var itemMod = plugin.getItemModule();
        if (itemMod != null && itemMod.getStatAggregator() != null) {
            itemMod.getStatAggregator().invalidate(player);
        }
    }

    // =========================================================================
    // EVENT LISTENERS (Claiming, Perk Modifiers, Combat Triggers, Gathering, Allies)
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onClaimPetToken(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir() || !hand.hasItemMeta()) return;

        ItemMeta meta = hand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String petId = pdc.get(ItemRegistry.PET_ID_KEY, PersistentDataType.STRING);
        if (petId == null) {
            String itemId = pdc.get(ItemRegistry.ITEM_ID_KEY, PersistentDataType.STRING);
            if (itemId != null && itemId.startsWith("pet_")) {
                petId = itemId.substring(4);
            }
        }
        if (petId == null) return;

        event.setCancelled(true);
        PetSpec spec = petRegistry.getPet(petId);
        if (spec == null) return;

        int level = pdc.getOrDefault(ItemRegistry.PET_LEVEL_KEY, PersistentDataType.INTEGER, 1);
        double xp = pdc.getOrDefault(ItemRegistry.PET_XP_KEY, PersistentDataType.DOUBLE, 0.0);
        String rarStr = pdc.get(ItemRegistry.PET_RARITY_KEY, PersistentDataType.STRING);
        ItemRarity rar = rarStr != null ? ItemRarity.fromString(rarStr) : spec.getRarity();

        PlayerPetData data = getData(player.getUniqueId());
        if (data.hasPet(spec.getId())) {
            PlayerPetData.OwnedPetEntry existing = data.getPetEntry(spec.getId());
            if (existing.getLevel() >= level && (existing.getRarityOverride() == null || existing.getRarityOverride().ordinal() >= rar.ordinal())) {
                DCMessage.sendWarning(player, "You already own " + spec.getDisplayName()
                        + " at Level " + existing.getLevel() + ". Opening your Pet Wardrobe.");
                new PetMenuGUI(player, this, 0).open();
                return;
            }
        }

        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        data.unlockOrUpgradePet(spec.getId(), level, xp, rar);
        summonPet(player, spec.getId(), false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombatPerkScaling(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (victim.hasMetadata("dce_internal_damage")) return;

        if (event.getDamager() instanceof Player attacker) {
            PlayerPetData data = playerDataMap.get(attacker.getUniqueId());
            if (data != null && data.getActivePetId() != null && PetCombatDirector.isValidEnemy(attacker, victim, data)) {
                double bonusPct = getActivePerkValue(attacker, PetPerkEffectType.BONUS_MOB_DAMAGE);
                var maxAttr = victim.getAttribute(Attribute.MAX_HEALTH);
                boolean isBossOrElite = victim instanceof Boss || (maxAttr != null && maxAttr.getValue() >= 100.0);
                if (isBossOrElite) {
                    bonusPct += getActivePerkValue(attacker, PetPerkEffectType.BOSS_SLAYER_DAMAGE);
                }
                if (bonusPct > 0.0) {
                    event.setDamage(event.getDamage() * (1.0 + (bonusPct / 100.0)));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIncomingDamagePerk(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player owner)) return;
        PlayerPetData data = playerDataMap.get(owner.getUniqueId());
        if (data == null || data.getActivePetId() == null) return;

        double redPct = getActivePerkValue(owner, PetPerkEffectType.DAMAGE_REDUCTION);
        if (redPct > 0.0) {
            double clamped = Math.min(50.0, redPct);
            event.setDamage(event.getDamage() * (1.0 - (clamped / 100.0)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatTargetAndCast(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (victim.hasMetadata("dce_internal_damage")) return;

        // 1. Owner deals damage
        if (event.getDamager() instanceof Player owner) {
            PlayerPetData data = playerDataMap.get(owner.getUniqueId());
            if (data == null || data.getActivePetId() == null) return;
            PetSpec spec = petRegistry.getPet(data.getActivePetId());
            if (spec == null) return;

            if (!PetCombatDirector.isValidEnemy(owner, victim, data)) return;

            lockedTargets.put(owner.getUniqueId(), victim);

            double vampPct = getActivePerkValue(owner, PetPerkEffectType.VAMPIRIC_HEAL);
            if (vampPct > 0.0 && event.getFinalDamage() > 0.0) {
                healIfHurt(owner, event.getFinalDamage() * (vampPct / 100.0));
            }

            if (data.isAutoCastSkills() && spec.getActiveSkill() != null) {
                PetSkillTrigger trig = spec.getActiveSkill().getTrigger();
                boolean shouldFire = switch (trig) {
                    case ON_ATTACK, ON_COMBAT_ANY -> true;
                    case ON_TARGET_LOCK -> victim.equals(lookTargets.get(owner.getUniqueId())) || victim.equals(lockedTargets.get(owner.getUniqueId()));
                    case ON_LOW_HP -> isOwnerBelowThreshold(owner, spec.getActiveSkill().getActivationThreshold());
                    case ON_DAMAGED -> false;
                };
                if (shouldFire) {
                    tryTriggerActiveSkill(owner, victim, spec, data);
                }
            }
        }

        // 2. Owner takes damage
        if (victim instanceof Player owner && event.getDamager() instanceof LivingEntity attacker) {
            PlayerPetData data = playerDataMap.get(owner.getUniqueId());
            if (data == null || data.getActivePetId() == null) return;
            PetSpec spec = petRegistry.getPet(data.getActivePetId());
            if (spec == null) return;

            if (PetCombatDirector.isValidEnemy(owner, attacker, data)) {
                lockedTargets.putIfAbsent(owner.getUniqueId(), attacker);
                if (data.isAutoCastSkills() && spec.getActiveSkill() != null) {
                    PetSkillTrigger trig = spec.getActiveSkill().getTrigger();
                    boolean shouldFire = switch (trig) {
                        case ON_DAMAGED, ON_COMBAT_ANY -> true;
                        case ON_LOW_HP -> isOwnerBelowThreshold(owner, spec.getActiveSkill().getActivationThreshold());
                        case ON_ATTACK, ON_TARGET_LOCK -> false;
                    };
                    if (shouldFire) {
                        tryTriggerActiveSkill(owner, attacker, spec, data);
                    }
                }
            }
        }
    }

    private boolean isOwnerBelowThreshold(Player owner, double thresholdRatio) {
        var maxAttr = owner.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = maxAttr != null ? maxAttr.getValue() : 20.0;
        return maxHp > 0 && (owner.getHealth() / maxHp) <= Math.max(0.1, Math.min(0.99, thresholdRatio));
    }

    private void tryTriggerActiveSkill(Player owner, LivingEntity target, PetSpec spec, PlayerPetData data) {
        long now = System.currentTimeMillis();
        long rem = getSkillCooldownRemainingMs(owner.getUniqueId());
        if (rem > 0L) return;

        PetSkillSpec skill = spec.getActiveSkill();
        PlayerPetData.OwnedPetEntry entry = data.getPetEntry(spec.getId());
        int level = entry != null ? entry.getLevel() : 1;

        if (target != null && owner.getWorld().equals(target.getWorld())
                && owner.getLocation().distanceSquared(target.getLocation()) > skill.getRange() * skill.getRange()) {
            return;
        }

        double cdMult = getEffectiveSkillCooldownMultiplier(owner);
        double powerMult = getEffectiveSkillPowerMultiplier(owner);
        long effCd = Math.max(1200L, Math.round(skill.getEffectiveCooldownMs(level, spec.getMaxLevel()) * cdMult));
        skillCooldownMap.put(owner.getUniqueId(), now + effCd);

        PacketPetInstance inst = activeInstances.get(owner.getUniqueId());
        Location petOrigin = inst != null && inst.getCurrentLocation() != null
                ? inst.getCurrentLocation()
                : owner.getEyeLocation();

        combatDirector.executeSkill(owner, data, spec, skill, level, powerMult, petOrigin, target);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobKillFallbackXp(EntityDeathEvent event) {
        if (!petsConfig.isFallbackMobKillXp() || AdaptHook.isAvailable()) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        double amt = petsConfig.getFallbackMobKillAmount();
        if (amt > 0.0) {
            addPetXp(killer, amt);
        }
    }

    // Gathering Fortune (blocks & fishing) is now unified inside GatheringBonusService (Task P3-04 / A-23)

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCrouchClickAlly(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        PlayerPetData data = playerDataMap.get(player.getUniqueId());
        if (data == null || data.getActivePetId() == null) return;
        if (!player.getInventory().getItemInMainHand().getType().isAir()) return;

        boolean added = data.toggleAlly(target.getUniqueId());
        savePlayerAsync(data);
        if (added) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
            DCMessage.sendGeneral(player, "Added &f" + target.getName() + " &#0CBF93to your Pet Ally Whitelist.");
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.9f);
            DCMessage.sendWarning(player, "Removed &f" + target.getName() + " &#FDE047from your Pet Ally Whitelist.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        removeSpeedPerkModifier(event.getPlayer());
        PacketPetInstance inst = activeInstances.remove(uuid);
        if (inst != null) {
            inst.destroyForAll();
        }
        for (PacketPetInstance other : activeInstances.values()) {
            other.sendDestroyPacket(event.getPlayer());
        }
        lockedTargets.remove(uuid);
        lookTargets.remove(uuid);
        skillCooldownMap.remove(uuid);
        skillXpBaselines.remove(uuid);
        PlayerPetData data = playerDataMap.remove(uuid);
        if (data != null) {
            savePlayerAsync(data);
        }
    }

    // =========================================================================
    // DATABASE PERSISTENCE
    // =========================================================================

    public void loadPlayer(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        PlayerPetData data = getData(uuid);
        var db = plugin.getDatabaseManager();
        if (db == null) return;

        db.executeAsync(() -> {
            boolean existed = false;
            synchronized (db) {
                try (PreparedStatement ps = db.getConnection().prepareStatement(
                        "SELECT active_pet_id, visibility_mode, auto_cast, pets_data, allies_data FROM dce_pets_player WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            existed = true;
                            data.setActivePetId(rs.getString("active_pet_id"));
                            data.setVisibilityMode(PetVisibilityMode.fromString(rs.getString("visibility_mode")));
                            data.setAutoCastSkills(rs.getInt("auto_cast") != 0);
                            data.deserializeOwnedPets(rs.getString("pets_data"));
                            data.deserializeAllies(rs.getString("allies_data"));
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().warning("[DCEPets] Error loading pet data for " + player.getName() + ": " + e.getMessage());
                }
            }

            boolean firstJoinNoRecord = !existed;
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;

                    Map<String, Double> snap = AdaptHook.readSkillXpTotals(player);
                    if (!snap.isEmpty()) {
                        skillXpBaselines.put(uuid, new ConcurrentHashMap<>(snap));
                    }

                    if (firstJoinNoRecord && petsConfig.isStarterEnabled()) {
                        String starterId = petsConfig.getStarterPetId();
                        PetSpec starterSpec = petRegistry.getPet(starterId);
                        if (starterSpec != null && !data.hasPet(starterSpec.getId())) {
                            data.unlockOrUpgradePet(starterSpec.getId(), 1, 0.0, starterSpec.getRarity());
                            savePlayerAsync(data);
                            DCMessage.sendGeneral(player, "You unlocked starter companion "
                                    + starterSpec.getRarity().getColorHex() + starterSpec.getDisplayName()
                                    + "&#0CBF93! Open /pet to summon or inspect.");
                            if (petsConfig.isStarterAutoSummon()) {
                                summonPet(player, starterSpec.getId(), true);
                            }
                        }
                    }

                    if (data.getActivePetId() != null) {
                        summonPet(player, data.getActivePetId(), true);
                    }
                }, 12L);
            }
        });
    }

    public void savePlayerAsync(PlayerPetData data) {
        if (data == null) return;
        var db = plugin.getDatabaseManager();
        if (db == null) return;
        db.executeAsync(() -> savePlayerSync(data));
    }

    private void savePlayerSync(PlayerPetData data) {
        var db = plugin.getDatabaseManager();
        if (db == null) return;
        synchronized (db) {
            try (PreparedStatement ps = db.getConnection().prepareStatement(
                    "REPLACE INTO dce_pets_player (uuid, active_pet_id, visibility_mode, auto_cast, pets_data, allies_data) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, data.getPlayerUuid().toString());
                ps.setString(2, data.getActivePetId() != null ? data.getActivePetId() : "");
                ps.setString(3, data.getVisibilityMode().name());
                ps.setInt(4, data.isAutoCastSkills() ? 1 : 0);
                ps.setString(5, data.serializeOwnedPets());
                ps.setString(6, data.serializeAllies());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[DCEPets] Error saving pet data: " + e.getMessage());
            }
        }
    }
}
