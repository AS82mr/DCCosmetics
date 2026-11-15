package dc.dcessentials.modules.teleportation;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.model.ItemStat;
import dc.dcessentials.modules.items.stats.PlayerStatSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Authoritative Keystone Energy & Microcharge Service (Section 5.4 & Task P1-05).
 */
public class KeystoneEnergyService {

    private final TeleportationManager manager;

    public KeystoneEnergyService(TeleportationManager manager) {
        this.manager = manager;
    }

    public int getEffectiveMaxEnergy(Player player, KeystoneProfile profile) {
        if (profile == null || profile.getTier() == KeystoneTier.NONE) {
            return 0;
        }
        int base = profile.getTier().getMaxEnergy();
        ItemStack equipped = manager.getEquippedKeystoneItem(player);
        int bonus = KeystoneDetector.getScrollMaxEnergyBonus(equipped);
        return Math.min(KeystoneProfile.ABSOLUTE_MAX_ENERGY, base + bonus);
    }

    public double getConduitStat(Player player) {
        return getPlayerStat(player, "CONDUIT");
    }

    public double getThermalStat(Player player) {
        return getPlayerStat(player, "THERMAL");
    }

    private double getPlayerStat(Player player, String statName) {
        if (player == null) return 0.0;
        ItemStat stat = ItemStat.fromString(statName);
        if (stat == null) return 0.0;
        var itemMod = DCCosmetics.getInstance().getItemModule();
        if (itemMod == null || itemMod.getStatAggregator() == null) return 0.0;
        PlayerStatSnapshot snap = itemMod.getStatAggregator().getSnapshot(player);
        return snap != null ? snap.get(stat) : 0.0;
    }

    /**
     * Section 5.4: effectiveCost(base) = max(1, round(base * (1 - min(capPct, tierPct + conduit + scrollPct)/100)))
     */
    public int effectiveCost(Player player, KeystoneProfile profile, int baseCostMicro) {
        if (baseCostMicro <= 0) return 0;
        int tierPct = (profile != null && profile.getTier() != null) ? profile.getTier().getCostReductionPct() : 0;
        double conduit = getConduitStat(player);
        int scrollPct = KeystoneDetector.getScrollCostReductionPct(manager.getEquippedKeystoneItem(player));

        double totalReductionPct = Math.min(manager.getCostReductionCapPct(), tierPct + conduit + scrollPct);
        return Math.max(1, (int) Math.round(baseCostMicro * (1.0 - (totalReductionPct / 100.0))));
    }

    /**
     * Section 5.4: heatMult = dimMult * (1 - min(60, THERMAL + scrollHeatPct)/100)
     */
    public double computeHeatMultiplier(Player player, Location to) {
        ItemStack equipped = manager.getEquippedKeystoneItem(player);
        boolean hasAnchor = KeystoneDetector.hasDimensionAnchor(equipped);

        double dimMult = 1.0;
        if (!hasAnchor && player != null) {
            boolean fromNether = player.getWorld().getName().toLowerCase(Locale.ROOT).contains("nether");
            boolean toNether = to != null && to.getWorld() != null
                    && to.getWorld().getName().toLowerCase(Locale.ROOT).contains("nether");
            if (fromNether || toNether) {
                dimMult = manager.getNetherHeatMultiplier();
            }
        }

        double thermal = getThermalStat(player);
        int scrollHeatPct = KeystoneDetector.getScrollHeatReductionPct(equipped);
        double totalHeatRedPct = Math.min(60.0, thermal + scrollHeatPct);

        return dimMult * (1.0 - (totalHeatRedPct / 100.0));
    }

    public boolean authorizeAbility(Player player) {
        return manager.getGate().authorizeAbility(player);
    }

    public SpendResult spendVoidBounce(Player player) {
        return spend(player, SpendKind.ABILITY, manager.getVoidBounceCostMicro());
    }

    public SpendResult spendSonicBoom(Player player) {
        return spend(player, SpendKind.ABILITY, manager.getSonicBoomCostMicro());
    }

    public SpendResult spend(Player player, SpendKind kind, int baseCostMicro) {
        return spend(player, kind, baseCostMicro, player != null ? player.getLocation() : null);
    }

    public int effectiveWarpCost(Player player, KeystoneProfile profile, int baseCostMicro) {
        if (baseCostMicro <= 0) return 0;
        int microPerCharge = Math.max(1, manager.getMicroPerCharge());
        int baseCharges = Math.max(1, (int) Math.round((double) baseCostMicro / microPerCharge));
        int tierPct = (profile != null && profile.getTier() != null) ? profile.getTier().getCostReductionPct() : 0;
        double conduit = getConduitStat(player);
        int scrollPct = KeystoneDetector.getScrollCostReductionPct(manager.getEquippedKeystoneItem(player));

        double totalReductionPct = Math.min(manager.getCostReductionCapPct(), tierPct + conduit + scrollPct);
        int effCharges = Math.max(1, (int) Math.round(baseCharges * (1.0 - (totalReductionPct / 100.0))));
        return effCharges * microPerCharge;
    }

    public SpendResult spend(Player player, SpendKind kind, int baseCostMicro, Location to) {
        KeystoneProfile profile = manager.getProfile(player);

        // Live reconcile tier from equipped keystone slot if currently NONE
        if (profile.getTier() == KeystoneTier.NONE && player != null) {
            ItemStack keystoneItem = manager.getEquippedKeystoneItem(player);
            if (keystoneItem != null && keystoneItem.getType() != Material.AIR) {
                KeystoneTier detected = KeystoneDetector.tierOf(keystoneItem);
                if (detected != KeystoneTier.NONE) {
                    profile.setTier(detected);
                }
            }
        }

        // Section 5.4: Newbie shield — heat 0 always; energy charged only if a keystone is attuned (keeps starter 50 energy intact when unattuned)
        if (profile.hasNewbieShield() && kind == SpendKind.WARP) {
            if (profile.getTier() == KeystoneTier.NONE) {
                return new SpendResult(
                        true, 0, 0, 0, 0, false, 0,
                        profile.getEnergy(), profile.getMicro(), profile.getHeat(), profile.isOverloaded()
                );
            }
            int effCost = effectiveWarpCost(player, profile, baseCostMicro);
            return profile.spendMicro(effCost, 0, 0.0, manager.getOverloadClearBelow());
        }

        int effCost = kind == SpendKind.WARP
                ? effectiveWarpCost(player, profile, baseCostMicro)
                : effectiveCost(player, profile, baseCostMicro);
        double heatMult = computeHeatMultiplier(player, to);

        int overdrawHeatBase;
        if (profile.getTier() == KeystoneTier.NONE) {
            if (kind == SpendKind.WARP) {
                int min = manager.getUnattunedHeatMin();
                int max = Math.max(min, manager.getUnattunedHeatMax());
                overdrawHeatBase = ThreadLocalRandom.current().nextInt(min, max + 1);
            } else {
                overdrawHeatBase = manager.getAbilityOverdrawHeat(baseCostMicro);
            }
        } else {
            if (kind == SpendKind.WARP) {
                int min = manager.getDepletedHeatMin();
                int max = Math.max(min, manager.getDepletedHeatMax());
                int extra = ThreadLocalRandom.current().nextInt(min, max + 1);
                overdrawHeatBase = profile.getTier().getBaseHeat() + extra;
            } else {
                overdrawHeatBase = manager.getAbilityOverdrawHeat(baseCostMicro);
            }
        }

        int effClearBelow = manager.getEffectiveOverloadClearBelow(player);
        SpendResult res = profile.spendMicro(effCost, overdrawHeatBase, heatMult, effClearBelow);

        // Mastery Anchor scroll: 1 Emergency Phase-Vent per 3m (-35 Heat on hitting 100) + lower Overload clear threshold
        ItemStack equipped = manager.getEquippedKeystoneItem(player);
        if (profile.getHeat() >= 100 && KeystoneDetector.hasPhaseVentAnchor(equipped)) {
            long now = System.currentTimeMillis();
            long cooldownMs = Math.max(10L, KeystoneDetector.getPhaseVentCooldownSeconds(equipped)) * 1000L;
            long lastVent = profile.getLastPhaseVent();
            if (now - lastVent >= cooldownMs) {
                profile.setLastPhaseVent(now);
                int ventAmount = Math.max(10, KeystoneDetector.getPhaseVentHeatReduction(equipped));
                profile.reduceHeat(ventAmount, effClearBelow);
                profile.setOverloaded(false);
                res = new SpendResult(
                        res.authorized(),
                        res.effectiveCostMicro(),
                        res.chargesCracked(),
                        res.microPaid(),
                        res.microShortfall(),
                        res.overdraw(),
                        res.heatAdded(),
                        profile.getEnergy(),
                        profile.getMicro(),
                        profile.getHeat(),
                        profile.isOverloaded()
                );
                if (player != null && player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.4f);
                    player.spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0, 1.0, 0), 28, 0.5, 0.6, 0.5, 0.08);
                    DCMessage.sendInfo(player, "&#EF4444✦ Singularity Phase-Vent triggered! &#BAE6FDPurged &#FFFFFF-"
                            + ventAmount + " Heat &#BAE6FD(Heat: &#FFFFFF" + profile.getHeat() + "/100🔥&#BAE6FD | 3m cooldown).");
                }
            } else if (player != null && player.isOnline()) {
                long remSec = Math.max(1L, (cooldownMs - (now - lastVent) + 999L) / 1000L);
                DCMessage.sendWarning(player, "Singularity Phase-Vent on cooldown (&#FFFFFF" + remSec + "s&#FDE047) — Matrix OVERLOADED!");
            }
        }

        if (profile.getHeat() >= 100 && player != null) {
            manager.checkNexusIncursion(player, profile);
        }

        if (player != null) {
            KeystoneLoreTokens.scheduleInventoryRefresh(player);
        }

        return res;
    }

    public void refundMicro(Player player, int microAmount) {
        if (player == null || microAmount <= 0) return;
        KeystoneProfile profile = manager.getProfile(player);
        profile.refundMicro(microAmount, getEffectiveMaxEnergy(player, profile));
        KeystoneLoreTokens.scheduleInventoryRefresh(player);
    }

    public boolean isInstabilitySuppressed(Player player) {
        if (player == null) return false;
        ItemStack equipped = manager.getEquippedKeystoneItem(player);
        return KeystoneDetector.hasInstabilitySuppression(equipped);
    }

    public int grantMicro(Player player, int microAmount) {
        if (player == null || microAmount <= 0) return 0;
        KeystoneProfile profile = manager.getProfile(player);
        if (profile == null || profile.getTier() == KeystoneTier.NONE) return 0;
        refundMicro(player, microAmount);
        return microAmount;
    }

    public int countEnergyCubesInInventory(Player player) {
        if (player == null) return 0;
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir() && KeystoneDetector.isEnergyCube(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public int addCharges(Player player, int charges) {
        if (player == null || charges <= 0) return 0;
        KeystoneProfile profile = manager.getProfile(player);
        return profile.addCharges(charges, getEffectiveMaxEnergy(player, profile));
    }

    public int withdrawCharges(Player player, int charges) {
        if (player == null || charges <= 0) return 0;
        KeystoneProfile profile = manager.getProfile(player);
        return profile.removeCharges(charges);
    }

    public void giveEnergyCubes(Player player, int totalCubes) {
        if (player == null || totalCubes <= 0) return;
        var itemMod = DCCosmetics.getInstance().getItemModule();
        if (itemMod == null || itemMod.getItemRegistry() == null) return;

        int remaining = totalCubes;
        while (remaining > 0) {
            int batch = Math.min(64, remaining);
            remaining -= batch;
            ItemStack cubeStack = itemMod.getItemRegistry().createItemStack("tesseract_cube", batch);
            if (cubeStack == null) break;
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(cubeStack);
            for (ItemStack drop : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }
}
