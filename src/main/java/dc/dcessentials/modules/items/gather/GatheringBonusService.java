package dc.dcessentials.modules.items.gather;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.AdaptHook;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.modules.items.model.ItemStat;
import dc.dcessentials.modules.items.stats.PlayerStatSnapshot;
import dc.dcessentials.modules.items.stats.StatAggregator;
import dc.dcessentials.modules.pets.PetManager;
import dc.dcessentials.modules.pets.model.PetPerkEffectType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class GatheringBonusService implements Listener {

    private final StatAggregator statAggregator;
    private final PlacedBlockTracker placedBlockTracker;

    public GatheringBonusService(StatAggregator statAggregator, PlacedBlockTracker placedBlockTracker) {
        this.statAggregator = statAggregator;
        this.placedBlockTracker = placedBlockTracker;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        BlockState state = event.getBlockState();
        boolean wasPlayerPlaced = placedBlockTracker != null && placedBlockTracker.isPlayerPlaced(block);
        if (placedBlockTracker != null) {
            placedBlockTracker.unmark(block);
        }

        if (player == null || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (event.getItems().isEmpty()) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool != null && tool.hasItemMeta() && tool.getItemMeta().hasEnchant(Enchantment.SILK_TOUCH)) {
            return;
        }

        if (!isEligibleHarvestBlock(state, wasPlayerPlaced)) {
            return;
        }

        double totalChance = getEffectiveProsperityChance(player);
        if (totalChance <= 0.0) return;

        if (ThreadLocalRandom.current().nextDouble(100.0) < totalChance) {
            List<ItemStack> toDrop = new ArrayList<>();
            for (Item droppedEntity : event.getItems()) {
                ItemStack stack = droppedEntity.getItemStack();
                if (stack != null && !stack.getType().isAir()) {
                    toDrop.add(stack.clone());
                }
            }
            for (ItemStack extra : toDrop) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), extra);
            }
            if (!toDrop.isEmpty()) {
                block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.5, 0.5), 6, 0.25, 0.25, 0.25, 0.02);
                player.playSound(block.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.7f);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        PlayerStatSnapshot snap = statAggregator != null ? statAggregator.getSnapshot(player) : null;

        if (event.getState() == PlayerFishEvent.State.FISHING) {
            double tempo = snap != null ? Math.min(50.0, snap.get(ItemStat.TEMPO)) : 0.0;
            if (tempo > 0.0) {
                FishHook hook = event.getHook();
                double mult = Math.max(0.50, 1.0 - (tempo / 100.0));
                try {
                    int minWait = Math.max(20, (int) Math.round(hook.getMinWaitTime() * mult));
                    int maxWait = Math.max(minWait + 10, (int) Math.round(hook.getMaxWaitTime() * mult));
                    hook.setMinWaitTime(minWait);
                    hook.setMaxWaitTime(maxWait);
                } catch (Throwable ignored) {}
            }
            return;
        }

        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item caughtItem) {
            double totalChance = getEffectiveProsperityChance(player);
            if (totalChance > 0.0 && ThreadLocalRandom.current().nextDouble(100.0) < totalChance) {
                ItemStack bonus = caughtItem.getItemStack().clone();
                player.getWorld().dropItemNaturally(player.getLocation(), bonus);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.7f);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        PlayerStatSnapshot snap = statAggregator != null ? statAggregator.getSnapshot(player) : null;
        if (snap == null) return;

        double refinement = Math.min(40.0, snap.get(ItemStat.REFINEMENT));
        if (refinement <= 0.0) return;

        int levelCost = Math.max(1, event.whichButton() + 1);
        double bonusXp = (12.0 * levelCost) * (refinement / 100.0);
        if (bonusXp > 0.1) {
            AdaptHook.giveXp(player, "enchanting", bonusXp);
        }

        if (ThreadLocalRandom.current().nextDouble(100.0) < refinement) {
            if (DCCosmetics.getInstance().isEnabled()) {
                Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
                    if (player.isOnline()) {
                        player.setLevel(player.getLevel() + levelCost);
                        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.6f);
                        player.sendActionBar(ColorUtil.colorize("<#C084FC>✧ Refinement Refund: &d+" + levelCost + " Lv"));
                    }
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        PlayerStatSnapshot snap = statAggregator != null ? statAggregator.getSnapshot(player) : null;
        if (snap == null) return;

        double refinement = Math.min(40.0, snap.get(ItemStat.REFINEMENT));
        if (refinement <= 0.0) return;

        double bonusXp = 6.0 * (refinement / 100.0);
        if (bonusXp > 0.1) {
            AdaptHook.giveXp(player, "crafting", bonusXp);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        PlayerStatSnapshot snap = statAggregator != null ? statAggregator.getSnapshot(player) : null;
        if (snap == null) return;

        double refinement = Math.min(40.0, snap.get(ItemStat.REFINEMENT));
        if (refinement <= 0.0) return;

        double bonusXp = Math.min(20.0, event.getItemAmount() * 1.5) * (refinement / 100.0);
        if (bonusXp > 0.1) {
            AdaptHook.giveXp(player, "crafting", bonusXp);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(org.bukkit.event.inventory.BrewEvent event) {
        for (org.bukkit.entity.HumanEntity viewer : event.getContents().getViewers()) {
            if (viewer instanceof Player player) {
                PlayerStatSnapshot snap = statAggregator != null ? statAggregator.getSnapshot(player) : null;
                if (snap == null) continue;
                double refinement = Math.min(40.0, snap.get(ItemStat.REFINEMENT));
                if (refinement > 0.0) {
                    AdaptHook.giveXp(player, "brewing", 10.0 * (refinement / 100.0));
                }
            }
        }
    }

    public double getEffectiveProsperityChance(Player player) {
        if (player == null) return 0.0;
        PlayerStatSnapshot snap = statAggregator != null ? statAggregator.getSnapshot(player) : null;
        double statChance = snap != null ? Math.min(60.0, snap.get(ItemStat.PROSPERITY)) : 0.0;
        PetManager petMgr = DCCosmetics.getInstance().getPetManager();
        double petFortune = petMgr != null ? Math.max(0.0, petMgr.getActivePerkValue(player, PetPerkEffectType.GATHERING_FORTUNE)) : 0.0;
        return Math.min(75.0, statChance + petFortune);
    }

    private boolean isEligibleHarvestBlock(BlockState state, boolean wasPlayerPlaced) {
        if (state == null) return false;
        Material mat = state.getType();
        String name = mat.name();

        // 1. Ores / debris / amethyst (never player-placed)
        if (name.endsWith("_ORE") || mat == Material.ANCIENT_DEBRIS || mat == Material.AMETHYST_CLUSTER || mat == Material.GILDED_BLACKSTONE) {
            return !wasPlayerPlaced;
        }

        // 2. Logs / stems / melons / pumpkins / sugar cane / bamboo / cactus (never player-placed)
        if (name.endsWith("_LOG") || name.endsWith("_STEM")
                || mat == Material.MELON || mat == Material.PUMPKIN
                || mat == Material.SUGAR_CANE || mat == Material.BAMBOO || mat == Material.CACTUS) {
            return !wasPlayerPlaced;
        }

        // 3. Mature Ageable crops (wheat, carrots, potatoes, beetroots, nether wart, cocoa, sweet berries)
        if (state.getBlockData() instanceof Ageable ageable) {
            return !wasPlayerPlaced && ageable.getAge() >= ageable.getMaximumAge();
        }

        return false;
    }
}
