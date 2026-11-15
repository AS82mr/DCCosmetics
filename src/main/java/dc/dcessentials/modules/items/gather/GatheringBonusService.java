package dc.dcessentials.modules.items.gather;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.AdaptHook;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.modules.items.model.ItemStat;
import dc.dcessentials.modules.items.stats.PlayerStatSnapshot;
import dc.dcessentials.modules.items.stats.StatAggregator;
import dc.dcessentials.modules.items.tester.StatTesterManager;
import dc.dcessentials.modules.pets.PetManager;
import dc.dcessentials.modules.pets.model.PetPerkEffectType;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class GatheringBonusService implements Listener {

    private static final ThreadLocal<Boolean> CHAIN_BREAKING = ThreadLocal.withInitial(() -> false);

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

        PetManager petMgr = DCCosmetics.getInstance().getPetManager();
        StatTesterManager tester = StatTesterManager.getActiveInstance();
        boolean debug = tester != null && tester.isChatDebugEnabled(player);

        // Conditional Pet Gathering Perks (Auto-Smelt, Vein Chain, Replant Bloom, Sunlit Harvest, Timber Feller)
        if (petMgr != null && !CHAIN_BREAKING.get()) {
            applyPetBlockPerks(player, block, state, tool, event.getItems(), petMgr, tester, debug);
        }

        double totalChance = getEffectiveProsperityChance(player);
        if (totalChance <= 0.0 && !debug) return;

        boolean rolled = totalChance > 0.0 && ThreadLocalRandom.current().nextDouble(100.0) < totalChance;
        if (rolled) {
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
        if (debug) {
            logProsperityDetail(tester, player, "HARVEST " + state.getType().name(), totalChance, rolled);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        PlayerStatSnapshot snap = statAggregator != null ? statAggregator.getSnapshot(player) : null;
        StatTesterManager tester = StatTesterManager.getActiveInstance();
        boolean debug = tester != null && tester.isChatDebugEnabled(player);

        if (event.getState() == PlayerFishEvent.State.FISHING) {
            double tempo = snap != null ? Math.min(50.0, snap.get(ItemStat.TEMPO)) : 0.0;
            PetManager petMgr = DCCosmetics.getInstance().getPetManager();
            double rainHaste = 0.0;
            if (petMgr != null && (player.getWorld().hasStorm() || player.isInWater())) {
                rainHaste = petMgr.getActivePerkValue(player, PetPerkEffectType.RAIN_ANGLER_HASTE);
                if (rainHaste > 0.0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 240, 0, true, false));
                }
            }
            double totalHaste = Math.min(70.0, tempo + rainHaste);
            if (totalHaste > 0.0) {
                FishHook hook = event.getHook();
                double mult = Math.max(0.30, 1.0 - (totalHaste / 100.0));
                try {
                    int minWait = Math.max(15, (int) Math.round(hook.getMinWaitTime() * mult));
                    int maxWait = Math.max(minWait + 10, (int) Math.round(hook.getMaxWaitTime() * mult));
                    hook.setMinWaitTime(minWait);
                    hook.setMaxWaitTime(maxWait);
                } catch (Throwable ignored) {}
                if (debug) {
                    var bd = statAggregator.getDetailedBreakdown(player);
                    String rainNote = rainHaste > 0.0 ? " + Rain/Water Stormcaller (-" + String.format(Locale.US, "%.0f", rainHaste) + "%)" : "";
                    tester.logGatheringEvent(player, "FISHING BITE SPEED",
                            "Tempo -" + String.format(Locale.US, "%.1f", tempo) + "%" + rainNote + " wait time ("
                                    + tester.formatSourceBreakdown(ItemStat.TEMPO, bd, tempo, false) + ")");
                }
            }
            return;
        }

        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item caughtItem) {
            double totalChance = getEffectiveProsperityChance(player);
            boolean rolled = totalChance > 0.0 && ThreadLocalRandom.current().nextDouble(100.0) < totalChance;
            if (rolled) {
                ItemStack bonus = caughtItem.getItemStack().clone();
                player.getWorld().dropItemNaturally(player.getLocation(), bonus);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.7f);
            }
            PetManager petMgr = DCCosmetics.getInstance().getPetManager();
            double treasurePct = petMgr != null ? petMgr.getActivePerkValue(player, PetPerkEffectType.TREASURE_FISHER) : 0.0;
            boolean treasureProc = treasurePct > 0.0 && ThreadLocalRandom.current().nextDouble(100.0) < treasurePct;
            if (treasureProc) {
                ItemStack[] pool = {
                        new ItemStack(Material.EXPERIENCE_BOTTLE, 2),
                        new ItemStack(Material.GOLD_NUGGET, 4),
                        new ItemStack(Material.NAUTILUS_SHELL, 1),
                        new ItemStack(Material.GOLDEN_APPLE, 1)
                };
                ItemStack prize = pool[ThreadLocalRandom.current().nextInt(pool.length)];
                player.getWorld().dropItemNaturally(player.getLocation(), prize);
                AdaptHook.giveXp(player, "fishing", 12.0);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.5f);
            }
            if (debug) {
                logProsperityDetail(tester, player, "FISHING CATCH (" + caughtItem.getItemStack().getType().name() + ")", totalChance, rolled);
                if (treasurePct > 0.0) {
                    tester.logGatheringEvent(player, "SUNKEN JACKPOT PERK",
                            (treasureProc ? "&aPROCCED! Dropped bonus treasure & +12 Fishing XP" : "&7Missed (" + String.format(Locale.US, "%.0f", treasurePct) + "% chance)"));
                }
            }
        }
    }

    private void logProsperityDetail(StatTesterManager tester, Player player, String activity, double totalChance, boolean rolled) {
        if (tester == null || statAggregator == null) return;
        PlayerStatSnapshot snap = statAggregator.getSnapshot(player);
        var bd = statAggregator.getDetailedBreakdown(player);
        double statChance = Math.min(60.0, snap.get(ItemStat.PROSPERITY));
        PetManager petMgr = DCCosmetics.getInstance().getPetManager();
        double petFortune = petMgr != null ? Math.max(0.0, petMgr.getActivePerkValue(player, PetPerkEffectType.GATHERING_FORTUNE)) : 0.0;
        double tempo = snap.get(ItemStat.TEMPO);
        String outcome = rolled ? "&aBONUS DROP PROCCED!" : "&7No bonus roll (chance " + String.format(java.util.Locale.US, "%.1f", totalChance) + "%)";
        String perkStr = petFortune > 0 ? " + &d" + String.format(java.util.Locale.US, "%.1f", petFortune) + "% (Pet Fortune Perk)&f" : "";
        String tempoStr = tempo > 0 ? " &8| &eTempo: +" + String.format(java.util.Locale.US, "%.1f", tempo) + "%" : "";
        tester.logGatheringEvent(player, activity,
                "Prosperity: " + tester.formatSourceBreakdown(ItemStat.PROSPERITY, bd, statChance, false)
                        + perkStr + " &8➜ &bTotal " + String.format(java.util.Locale.US, "%.1f", totalChance) + "% &8| " + outcome + tempoStr);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        PlayerStatSnapshot snap = statAggregator != null ? statAggregator.getSnapshot(player) : null;
        double refinement = snap != null ? Math.min(40.0, snap.get(ItemStat.REFINEMENT)) : 0.0;
        PetManager petMgr = DCCosmetics.getInstance().getPetManager();
        double perkRefund = petMgr != null ? petMgr.getActivePerkValue(player, PetPerkEffectType.ENCHANT_REFUND) : 0.0;
        if (refinement <= 0.0 && perkRefund <= 0.0) return;

        int levelCost = Math.max(1, event.whichButton() + 1);
        double bonusXp = (12.0 * levelCost) * (refinement / 100.0);
        if (bonusXp > 0.1) {
            AdaptHook.giveXp(player, "enchanting", bonusXp);
        }

        double totalRefundChance = Math.min(75.0, refinement + perkRefund);
        boolean refunded = totalRefundChance > 0.0 && ThreadLocalRandom.current().nextDouble(100.0) < totalRefundChance;
        boolean lapisRefunded = perkRefund > 0.0 && refunded;
        if (refunded) {
            if (DCCosmetics.getInstance().isEnabled()) {
                Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
                    if (player.isOnline()) {
                        player.setLevel(player.getLevel() + levelCost);
                        if (lapisRefunded) {
                            player.getInventory().addItem(new ItemStack(Material.LAPIS_LAZULI, levelCost));
                        }
                        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.6f);
                        player.sendActionBar(ColorUtil.colorize("<#C084FC>✧ Arcane Refund: &d+" + levelCost + " Lv" + (lapisRefunded ? " &9+" + levelCost + " Lapis" : "")));
                    }
                });
            }
        }
        StatTesterManager tester = StatTesterManager.getActiveInstance();
        if (tester != null && tester.isChatDebugEnabled(player)) {
            var bd = statAggregator.getDetailedBreakdown(player);
            tester.logGatheringEvent(player, "ENCHANTING",
                    "Refinement " + tester.formatSourceBreakdown(ItemStat.REFINEMENT, bd, refinement, false)
                            + (perkRefund > 0 ? " + Rune Preserver " + String.format(Locale.US, "%.0f", perkRefund) + "%" : "")
                            + " &8➜ &b+" + String.format(Locale.US, "%.1f", bonusXp) + " Enchanting XP"
                            + (refunded ? " &a(+" + levelCost + " Lv" + (lapisRefunded ? " & Lapis" : "") + " Refund!)" : ""));
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
        StatTesterManager tester = StatTesterManager.getActiveInstance();
        if (tester != null && tester.isChatDebugEnabled(player)) {
            var bd = statAggregator.getDetailedBreakdown(player);
            tester.logGatheringEvent(player, "SMELTING (" + event.getItemType().name() + ")",
                    "Refinement " + tester.formatSourceBreakdown(ItemStat.REFINEMENT, bd, refinement, false)
                            + " &8➜ &b+" + String.format(java.util.Locale.US, "%.1f", bonusXp) + " Crafting XP");
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

    private void applyPetBlockPerks(Player player,
                                    Block block,
                                    BlockState state,
                                    ItemStack tool,
                                    List<Item> droppedEntities,
                                    PetManager petMgr,
                                    StatTesterManager tester,
                                    boolean debug) {
        Material mat = state.getType();
        String mName = mat.name();
        boolean isOre = mName.endsWith("_ORE") || mat == Material.ANCIENT_DEBRIS || mat == Material.GILDED_BLACKSTONE;

        // 1. AUTO_SMELT_FORTUNE (Mining raw metal ores)
        if (isOre && tool != null && tool.getType().name().endsWith("_PICKAXE")) {
            double smeltPct = petMgr.getActivePerkValue(player, PetPerkEffectType.AUTO_SMELT_FORTUNE);
            if (smeltPct > 0.0) {
                int smeltedCount = 0;
                int bonusIngot = 0;
                for (Item itemEnt : droppedEntities) {
                    ItemStack st = itemEnt.getItemStack();
                    Material smelted = switch (st.getType()) {
                        case RAW_IRON -> Material.IRON_INGOT;
                        case RAW_GOLD -> Material.GOLD_INGOT;
                        case RAW_COPPER -> Material.COPPER_INGOT;
                        case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
                        default -> null;
                    };
                    if (smelted != null) {
                        int extra = ThreadLocalRandom.current().nextDouble(100.0) < smeltPct ? 1 : 0;
                        bonusIngot += extra;
                        itemEnt.setItemStack(new ItemStack(smelted, st.getAmount() + extra));
                        smeltedCount += st.getAmount();
                    }
                }
                if (smeltedCount > 0) {
                    AdaptHook.giveXp(player, "crafting", smeltedCount * 1.5);
                    if (debug) {
                        tester.logGatheringEvent(player, "MAGMA FORGE PERK",
                                "Auto-smelted " + smeltedCount + " raw ore" + (bonusIngot > 0 ? " &a(+" + bonusIngot + " bonus ingot!)" : ""));
                    }
                }
            }
        }

        // 2. VEIN_CHAIN_SURGE (Mining ore vein with 3+ matching adjacent ores)
        if (isOre) {
            double veinPct = petMgr.getActivePerkValue(player, PetPerkEffectType.VEIN_CHAIN_SURGE);
            if (veinPct > 0.0) {
                List<Block> adjacentMatching = new ArrayList<>();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            Block nb = block.getRelative(dx, dy, dz);
                            if (nb.getType() == mat && (placedBlockTracker == null || !placedBlockTracker.isPlayerPlaced(nb))) {
                                adjacentMatching.add(nb);
                            }
                        }
                    }
                }
                if (adjacentMatching.size() >= 2) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 80, 1, true, false));
                    AdaptHook.giveXp(player, "mining", 3.5 * (veinPct / 100.0));
                    boolean chainProc = ThreadLocalRandom.current().nextDouble(100.0) < veinPct;
                    if (chainProc && !adjacentMatching.isEmpty()) {
                        Block victimOre = adjacentMatching.get(0);
                        CHAIN_BREAKING.set(true);
                        try {
                            victimOre.breakNaturally(tool != null ? tool : new ItemStack(Material.DIAMOND_PICKAXE));
                        } finally {
                            CHAIN_BREAKING.set(false);
                        }
                    }
                    if (debug) {
                        tester.logGatheringEvent(player, "MOTHERLODE RUSH PERK",
                                "Vein of " + (adjacentMatching.size() + 1) + " " + mName + " -> Haste II (4s)"
                                        + (chainProc ? " &a+ Fractured Adjacent Ore!" : ""));
                    }
                }
            }
        }

        // 3. TIMBER_FELLER (Foraging logs w/ Axe)
        if (Tag.LOGS.isTagged(mat) && tool != null && tool.getType().name().endsWith("_AXE")) {
            double timberPct = petMgr.getActivePerkValue(player, PetPerkEffectType.TIMBER_FELLER);
            if (timberPct > 0.0 && ThreadLocalRandom.current().nextDouble(100.0) < timberPct) {
                int felled = 0;
                Block cursor = block.getRelative(0, 1, 0);
                CHAIN_BREAKING.set(true);
                try {
                    while (felled < 4 && Tag.LOGS.isTagged(cursor.getType())) {
                        if (placedBlockTracker != null && placedBlockTracker.isPlayerPlaced(cursor)) break;
                        cursor.breakNaturally(tool);
                        felled++;
                        cursor = cursor.getRelative(0, 1, 0);
                    }
                } finally {
                    CHAIN_BREAKING.set(false);
                }
                if (ThreadLocalRandom.current().nextDouble(100.0) < 20.0) {
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.APPLE, 1));
                }
                AdaptHook.giveXp(player, "woodcutting", Math.max(1, felled) * 2.0);
                if (debug && felled > 0) {
                    tester.logGatheringEvent(player, "TIMBER CASCADE PERK",
                            "Toppled &a+" + felled + " &fconnected trunk logs!");
                }
            }
        }

        // 4. AUTO_REPLANT_BLOOM & SUNLIT_HARVEST (Farming)
        boolean isCrop = state.getBlockData() instanceof Ageable;
        if (isCrop) {
            double replantPct = petMgr.getActivePerkValue(player, PetPerkEffectType.AUTO_REPLANT_BLOOM);
            if (replantPct > 0.0) {
                Material seedMat = switch (mat) {
                    case WHEAT -> Material.WHEAT_SEEDS;
                    case CARROTS -> Material.CARROT;
                    case POTATOES -> Material.POTATO;
                    case BEETROOTS -> Material.BEETROOT_SEEDS;
                    case NETHER_WART -> Material.NETHER_WART;
                    default -> null;
                };
                if (seedMat != null) {
                    boolean consumedSeed = false;
                    for (Item itemEnt : droppedEntities) {
                        ItemStack st = itemEnt.getItemStack();
                        if (st.getType() == seedMat && st.getAmount() > 0) {
                            st.setAmount(st.getAmount() - 1);
                            itemEnt.setItemStack(st);
                            consumedSeed = true;
                            break;
                        }
                    }
                    if (consumedSeed && DCCosmetics.getInstance().isEnabled()) {
                        boolean spreadPulse = ThreadLocalRandom.current().nextDouble(100.0) < replantPct;
                        Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
                            if (block.getType().isAir()) {
                                block.setType(mat, false);
                                if (block.getBlockData() instanceof Ageable ag) {
                                    ag.setAge(0);
                                    block.setBlockData(ag, false);
                                }
                            }
                            if (spreadPulse) {
                                for (int dx = -1; dx <= 1; dx++) {
                                    for (int dz = -1; dz <= 1; dz++) {
                                        if (dx == 0 && dz == 0) continue;
                                        Block nb = block.getRelative(dx, 0, dz);
                                        if (nb.getBlockData() instanceof Ageable nbAge && nbAge.getAge() < nbAge.getMaximumAge()) {
                                            nbAge.setAge(nbAge.getAge() + 1);
                                            nb.setBlockData(nbAge, false);
                                        }
                                    }
                                }
                            }
                        });
                        if (debug) {
                            tester.logGatheringEvent(player, "AUTO-REPLANTER PERK",
                                    "Replanted " + mName + (spreadPulse ? " &a+ 3x3 Growth Pulse!" : ""));
                        }
                    }
                }
            }
        }

        if (isCrop || mat == Material.MELON || mat == Material.PUMPKIN || mat == Material.SUGAR_CANE) {
            double sunlitPct = petMgr.getActivePerkValue(player, PetPerkEffectType.SUNLIT_HARVEST);
            if (sunlitPct > 0.0 && block.getLightFromSky() >= 12) {
                player.setFoodLevel(Math.min(20, player.getFoodLevel() + 1));
                player.setSaturation(Math.min(20f, player.getSaturation() + 1.0f));
                double bonusXp = 4.0 * (sunlitPct / 100.0);
                AdaptHook.giveXp(player, "herbalism", bonusXp);
                if (debug) {
                    tester.logGatheringEvent(player, "SOLAR PHOTOSYNTHESIS PERK",
                            "Daylight Harvest: +1 Hunger/Sat & +&a" + String.format(Locale.US, "%.1f", bonusXp) + " Farming XP");
                }
            }
        }
    }
}
