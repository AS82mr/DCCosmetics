package dc.dcessentials.modules.teleportation.listener;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.PlayerProfile;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.teleportation.KeystoneDetector;
import dc.dcessentials.modules.teleportation.KeystoneEnergyService;
import dc.dcessentials.modules.teleportation.KeystoneLoreTokens;
import dc.dcessentials.modules.teleportation.KeystoneProfile;
import dc.dcessentials.modules.teleportation.KeystoneTier;
import dc.dcessentials.modules.teleportation.LegacyKeystoneConverter;
import dc.dcessentials.modules.teleportation.TeleportationManager;
import dc.dcessentials.modules.teleportation.gui.KeystoneMenu;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Handles right-clicking Tesseract Energy Cubes in hand (deposit 1 / sneak: deposit all up to capacity)
 * and right-clicking Keystones in hand (auto-equip if slot 11 empty, else open KeystoneMenu) (Task P2-02).
 */
public class KeystoneItemListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        if (hand == null) return;

        ItemStack item = (hand == EquipmentSlot.OFF_HAND)
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();

        if (item == null || item.getType().isAir()) return;

        // 1. Right-Click Tesseract Energy Cube -> Deposit 1 (Sneak: Deposit All up to capacity)
        if (KeystoneDetector.isEnergyCube(item)) {
            event.setCancelled(true);
            if (!KeystoneMenu.tryAcquireOpLock(player)) return;
            try {
                handleCubeRightClick(player, hand, item);
            } finally {
                KeystoneMenu.releaseOpLock(player);
            }
            return;
        }

        // 2. Right-Click Keystone -> Equip if slot 11 empty, else open KeystoneMenu
        KeystoneTier itemTier = KeystoneDetector.tierOf(item);
        if (itemTier != KeystoneTier.NONE) {
            event.setCancelled(true);
            if (!KeystoneMenu.tryAcquireOpLock(player)) return;
            try {
                handleKeystoneRightClick(player, hand, item, itemTier);
            } finally {
                KeystoneMenu.releaseOpLock(player);
            }
        }
    }

    private void handleCubeRightClick(Player player, EquipmentSlot hand, ItemStack handItem) {
        TeleportationManager teleMgr = DCCosmetics.getInstance().getTeleportationManager();
        KeystoneProfile profile = teleMgr.getProfile(player);
        KeystoneEnergyService energyService = teleMgr.getEnergyService();

        // Reconcile tier from /profile slot 11 if profile is currently NONE
        if (profile.getTier() == KeystoneTier.NONE) {
            var profileMgr = DCCosmetics.getInstance().getProfileManager();
            PlayerProfile pp = profileMgr != null ? profileMgr.getProfile(player) : null;
            if (pp != null) {
                ItemStack equipped = pp.getEquipment("keystone");
                if (equipped != null && !equipped.getType().isAir()) {
                    KeystoneTier detected = KeystoneDetector.tierOf(equipped);
                    if (detected != KeystoneTier.NONE) {
                        profile.setTier(detected);
                        teleMgr.getPersistence().saveNow(profile);
                    }
                }
            }
        }

        if (profile.getTier() == KeystoneTier.NONE) {
            DCMessage.sendWarning(player, "Equip a Keystone in your &#38BDF8/profile &#FDE047first to charge your matrix!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            return;
        }

        int effectiveMax = energyService.getEffectiveMaxEnergy(player, profile);
        int space = Math.max(0, effectiveMax - profile.getEnergy());
        if (space <= 0) {
            DCMessage.sendWarning(player, "Your Keystone is already at maximum capacity (&#FFFFFF" + effectiveMax + " 🗲&#FDE047)!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            return;
        }

        int accepted;
        if (player.isSneaking()) {
            // Deposit all cubes across storage + offhand up to capacity
            int removed = KeystoneMenu.removeCubesFromInventoryFirst(player, space);
            if (removed < space) {
                ItemStack off = player.getInventory().getItemInOffHand();
                if (off != null && !off.getType().isAir() && KeystoneDetector.isEnergyCube(off)) {
                    int takeOff = Math.min(space - removed, off.getAmount());
                    if (takeOff > 0) {
                        if (off.getAmount() - takeOff <= 0) {
                            player.getInventory().setItemInOffHand(null);
                        } else {
                            off.setAmount(off.getAmount() - takeOff);
                        }
                        removed += takeOff;
                    }
                }
            }
            if (removed <= 0) return;
            accepted = profile.addCharges(removed, effectiveMax);
            if (accepted < removed) {
                energyService.giveEnergyCubes(player, removed - accepted);
            }
        } else {
            // Deposit 1 cube from the active hand first
            int curAmt = handItem.getAmount();
            if (curAmt <= 0) return;
            if (curAmt == 1) {
                if (hand == EquipmentSlot.OFF_HAND) {
                    player.getInventory().setItemInOffHand(null);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
            } else {
                handItem.setAmount(curAmt - 1);
            }
            accepted = profile.addCharges(1, effectiveMax);
            if (accepted < 1) {
                energyService.giveEnergyCubes(player, 1);
            }
        }

        teleMgr.getPersistence().saveNow(profile);
        KeystoneLoreTokens.scheduleInventoryRefresh(player);

        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.4f);
        player.sendActionBar(ColorUtil.colorize("&#38BDF8&l🗲 KEYSTONE CHARGED &8[&a+" + accepted + " 🗲 &8| &f" + profile.getEnergy() + "/" + effectiveMax + " 🗲&8]"));
    }

    private void handleKeystoneRightClick(Player player, EquipmentSlot hand, ItemStack handItem, KeystoneTier itemTier) {
        if (KeystoneDetector.isLegacy(handItem)) {
            handItem = LegacyKeystoneConverter.convert(handItem);
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(handItem);
            } else {
                player.getInventory().setItemInMainHand(handItem);
            }
            itemTier = KeystoneDetector.tierOf(handItem);
        }

        TeleportationManager teleMgr = DCCosmetics.getInstance().getTeleportationManager();
        KeystoneProfile kProfile = teleMgr.getProfile(player);
        var profileMgr = DCCosmetics.getInstance().getProfileManager();
        PlayerProfile pp = profileMgr != null ? profileMgr.getProfile(player) : null;

        if (pp != null) {
            ItemStack equipped = pp.getEquipment("keystone");
            if (equipped == null || equipped.getType().isAir() || KeystoneDetector.tierOf(equipped) == KeystoneTier.NONE) {
                // Slot 11 is empty: auto-equip 1 from hand
                ItemStack toEquip = handItem.clone();
                toEquip.setAmount(1);
                if (handItem.getAmount() > 1) {
                    handItem.setAmount(handItem.getAmount() - 1);
                } else {
                    if (hand == EquipmentSlot.OFF_HAND) {
                        player.getInventory().setItemInOffHand(null);
                    } else {
                        player.getInventory().setItemInMainHand(null);
                    }
                }

                pp.setEquipment("keystone", toEquip);
                profileMgr.saveProfile(player);

                kProfile.setTier(itemTier);
                int effMax = teleMgr.getEnergyService().getEffectiveMaxEnergy(player, kProfile);
                int overflow = kProfile.trimToTier(effMax);
                if (overflow > 0) {
                    teleMgr.getEnergyService().giveEnergyCubes(player, overflow);
                    DCMessage.sendInfo(player, "Returned &#FFFFFF" + overflow + "x Tesseract Energy Cube &#BAE6FDabove your Keystone's capacity.");
                }
                teleMgr.getPersistence().saveNow(kProfile);

                var itemMod = DCCosmetics.getInstance().getItemModule();
                if (itemMod != null && itemMod.getStatAggregator() != null) {
                    itemMod.getStatAggregator().invalidate(player);
                }

                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 0.9f, 1.1f);
                DCMessage.sendGeneral(player, "Equipped " + itemTier.getDisplayName() + " &#0CBF93into your &#38BDF8/profile &#0CBF93(Capacity: &#FFFFFF" + effMax + " 🗲&#0CBF93)!");
            }
        }

        KeystoneMenu.open(player);
    }
}
