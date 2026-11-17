package dc.dcessentials.modules.teleportation.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.PlayerProfile;
import dc.dcessentials.core.gui.DceGui;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.scroll.ScrollModel;
import dc.dcessentials.modules.items.scroll.ScrollRegistry;
import dc.dcessentials.modules.items.scroll.ScrollType;
import dc.dcessentials.modules.teleportation.KeystoneDetector;
import dc.dcessentials.modules.teleportation.KeystoneEnergyService;
import dc.dcessentials.modules.teleportation.KeystoneLoreTokens;
import dc.dcessentials.modules.teleportation.KeystoneProfile;
import dc.dcessentials.modules.teleportation.KeystoneTier;
import dc.dcessentials.modules.teleportation.TeleportationManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native 54-slot Keystone Matrix GUI (Tasks P2-02, P2-05).
 * Replaces DeluxeMenus kris_rare / kris_epic / kris_legendary.
 */
public class KeystoneMenu implements DceGui {

    public static final String GUI_TITLE = ColorUtil.colorize("&#0098FF&lTemporal Keystone Matrix");

    private static final Set<UUID> OP_IN_FLIGHT = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<UUID, Integer> LAST_OP_TICK = new ConcurrentHashMap<>();

    private final Player player;
    private final Inventory inventory;

    public KeystoneMenu(Player player) {
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 54, GUI_TITLE);
        refresh();
    }

    public static void open(Player player) {
        if (player == null || !player.isOnline()) return;
        KeystoneMenu menu = new KeystoneMenu(player);
        player.openInventory(menu.getInventory());
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    public void refresh() {
        TeleportationManager teleMgr = DCCosmetics.getInstance().getTeleportationManager();
        KeystoneProfile profile = teleMgr.getProfile(player);
        KeystoneEnergyService energyService = teleMgr.getEnergyService();

        KeystoneTier tier = profile.getTier();
        int energy = profile.usableEnergy();
        int parked = profile.parkedEnergy();
        int effectiveMax = energyService.getEffectiveMaxEnergy(player, profile);
        int micro = profile.getMicro();
        int heat = profile.getHeat();
        boolean overloaded = profile.isOverloaded();

        ItemStack darkGlass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        ItemStack grayGlass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        ItemStack cyanGlass = createItem(Material.CYAN_STAINED_GLASS_PANE, " ", null);

        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, grayGlass);
        }
        // Row 0 header
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, darkGlass);
        }
        inventory.setItem(0, cyanGlass);
        inventory.setItem(8, cyanGlass);
        inventory.setItem(4, createItem(
                Material.BEACON,
                "&#0098FF&lTemporal Keystone Matrix",
                Arrays.asList(
                        "&8Sanctuary Spatial Core",
                        "&f",
                        "&7Manage your attuned Keystone, deposit or",
                        "&7withdraw Tesseract Cubes, and infuse",
                        "&7Keystone Scrolls into your matrix sockets."
                )
        ));

        // Slot 11: Attunement / Tier + Sockets Summary
        ItemStack equippedKeystone = getEquippedKeystone(player);
        List<ScrollModel> socketedModels = equippedKeystone != null
                ? KeystoneDetector.resolveSocketedScrollModels(equippedKeystone)
                : Collections.emptyList();

        List<String> slot11Lore = new ArrayList<>();
        slot11Lore.add("&8Temporal Matrix Attunement");
        slot11Lore.add("&f");
        slot11Lore.add("&7Attuned Keystone: " + (tier != KeystoneTier.NONE ? tier.getDisplayName() + " &8(" + tier.getKeystoneName() + "&8)" : "&cNone"));
        slot11Lore.add("&7Energy Capacity: &f" + effectiveMax + " &#38BDF8🗲");
        slot11Lore.add("&7Cost Efficiency: " + tier.getEfficiencyText());
        slot11Lore.add("&7Passive Cooling: &b-" + tier.getPassiveDecay() + " 🔥 &7/ 30s");
        slot11Lore.add("&7Unlocked Sockets: &f" + socketedModels.size() + " &7/ &f" + tier.getSockets());
        if (!socketedModels.isEmpty()) {
            slot11Lore.add("&f");
            slot11Lore.add("&#38BDF8&lActive Socket Infusions:");
            for (ScrollModel sm : socketedModels) {
                slot11Lore.add("  &8• " + sm.getSocketDisplay());
            }
        }
        slot11Lore.add("&f");
        slot11Lore.add("&8Equip or swap Keystones in /profile (Slot 11).");

        Material tierMat = switch (tier) {
            case LEGENDARY -> Material.NETHER_STAR;
            case EPIC -> Material.AMETHYST_SHARD;
            case RARE -> Material.ECHO_SHARD;
            default -> Material.IRON_INGOT;
        };
        ItemStack attuneIcon;
        if (equippedKeystone != null && !equippedKeystone.getType().isAir()) {
            attuneIcon = equippedKeystone.clone();
            attuneIcon.setAmount(1);
            ItemMeta meta = attuneIcon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtil.colorize("&#0098FF&lKeystone Attunement: " + tier.getDisplayName()));
                List<String> colored = new ArrayList<>(slot11Lore.size());
                for (String l : slot11Lore) colored.add(ColorUtil.colorize(l));
                meta.setLore(colored);
                attuneIcon.setItemMeta(meta);
            }
        } else {
            attuneIcon = createItem(tierMat, "&#0098FF&lKeystone Attunement: " + tier.getDisplayName(), slot11Lore);
        }
        inventory.setItem(10, attuneIcon);

        // Slots 12, 13, 14: 3-Digit Player Head Energy Reserve [Hundreds] [Tens] [Ones]
        int energyPct = effectiveMax > 0 ? (int) Math.min(100, Math.round((energy * 100.0) / effectiveMax)) : 0;
        String energyBar = KeystoneLoreTokens.formatProgressBar(energy, effectiveMax, "&a");
        List<String> energyLore = new ArrayList<>();
        energyLore.add("&8Tesseract Charge Reserve");
        energyLore.add("&f");
        energyLore.add("&7Usable Energy: &f" + energy + " &7/ &f" + effectiveMax + " &#38BDF8🗲");
        energyLore.add("&7Reserve Bar: [" + energyBar + "&7] &e(" + energyPct + "%)");
        if (parked > 0) {
            energyLore.add("&7Parked Energy: &#FBBF24" + parked + " 🗲 &8(Attune higher tier to unlock)");
        }
        energyLore.add("&f");
        energyLore.add("&7Full teleports (&f/home&7, &f/tpa&7) draw &b1–3 🗲");
        energyLore.add("&7or crack charges into your Microcharge buffer.");

        int clampedDisplayEnergy = Math.max(0, Math.min(999, energy));
        int hundredsDigit = (clampedDisplayEnergy / 100) % 10;
        int tensDigit = (clampedDisplayEnergy / 10) % 10;
        int onesDigit = clampedDisplayEnergy % 10;
        String energyTitle = "&#38BDF8&lTesseract Energy: &f" + energy + " &7/ &f" + effectiveMax + " &#38BDF8🗲";
        inventory.setItem(12, createDigitHead(hundredsDigit, energyTitle + " &8[Hundreds]", energyLore));
        inventory.setItem(13, createDigitHead(tensDigit, energyTitle + " &8[Tens]", energyLore));
        inventory.setItem(14, createDigitHead(onesDigit, energyTitle + " &8[Ones]", energyLore));

        // Slot 15: Microcharge Bar [■■■■■■■■■□] 925/1000 mC
        String microBar = KeystoneLoreTokens.formatMicroBar(micro);
        String exactStr = String.format(Locale.US, "%d.%03d", energy, micro);
        inventory.setItem(15, createItem(
                Material.PRISMARINE_CRYSTALS,
                "&#06B6D4&lMicrocharge Buffer",
                Arrays.asList(
                        "&8Sub-Tesseract Ability Capacitor",
                        "&f",
                        "&7Buffer: &8[" + microBar + "&8] &#38BDF8" + micro + "&7/&f1000 " + KeystoneLoreTokens.MICRO_ICON,
                        "&7Exact Reserve: &f" + exactStr + " &#38BDF8🗲",
                        "&f",
                        "&71 Tesseract Charge = &#38BDF81,000 Microcharges (" + KeystoneLoreTokens.MICRO_ICON + ")&7.",
                        "&7Active item abilities (&dVoid Bounce&7, &bEtherwarp&7, &bSonic Boom&7)",
                        "&7drain from this buffer before cracking a new charge."
                )
        ));

        // Slot 16: Heat / Stability (+Overload Banner & Phase-Vent status)
        int effClearBelow = teleMgr.getEffectiveOverloadClearBelow(player);
        String heatBar = KeystoneLoreTokens.formatProgressBar(heat, 100, heat >= 80 ? "&c" : (heat >= 50 ? "&6" : "&e"));
        List<String> heatLore = new ArrayList<>();
        heatLore.add("&8Spatial Thermodynamics");
        heatLore.add("&f");
        heatLore.add("&7Timeline Heat: &c" + heat + " &7/ &c100 🔥");
        heatLore.add("&7Thermal Load: &8[" + heatBar + "&8]");
        heatLore.add("&7Stability: " + profile.getStability());
        if (KeystoneDetector.hasPhaseVentAnchor(equippedKeystone)) {
            long cdMs = Math.max(10L, KeystoneDetector.getPhaseVentCooldownSeconds(equippedKeystone)) * 1000L;
            long remMs = cdMs - (System.currentTimeMillis() - profile.getLastPhaseVent());
            String status = remMs <= 0L
                    ? "&aReady (-" + KeystoneDetector.getPhaseVentHeatReduction(equippedKeystone) + " Heat)"
                    : "&eCooldown: " + ((remMs + 999L) / 1000L) + "s";
            heatLore.add("&7Phase-Vent Anchor: " + status);
        }
        if (overloaded) {
            heatLore.add("&f");
            heatLore.add("&#EF4444&l⚠ MATRIX OVERLOADED ⚠");
            heatLore.add("&cCharged warps and abilities are locked!");
            heatLore.add("&7Cool down to &f≤ " + effClearBelow + " 🔥 &7(water, powder snow,");
            heatLore.add("&7or passive dissipation) to clear Overload.");
        } else if (heat >= 80) {
            heatLore.add("&f");
            heatLore.add("&#F59E0B&l⚠ UNSTABLE TIMELINE (≥80 🔥)");
            heatLore.add("&7Warps suffer spatial arrival jitter and you");
            heatLore.add("&7take &c+15% &7damage from Boss/Void/Sonic sources.");
        }
        heatLore.add("&f");
        heatLore.add(profile.hasNewbieShield()
                ? "&a✦ Temporal Shield: &fActive (0 Heat jumps)"
                : "&7Immersion in water or powder snow cools heat rapidly.");
        inventory.setItem(16, createItem(
                overloaded ? Material.REDSTONE_BLOCK : (heat >= 80 ? Material.MAGMA_BLOCK : Material.CAMPFIRE),
                overloaded ? "&#EF4444&l⚠ TIMELINE OVERLOAD ⚠" : "&#FBBF24&lTimeline Heat & Stability",
                heatLore
        ));

        // Slots 20, 21, 22: Keystone Socket Slots 1–3
        List<String> socketIds = equippedKeystone != null
                ? KeystoneDetector.socketedScrolls(equippedKeystone)
                : Collections.emptyList();
        var itemMod = DCCosmetics.getInstance().getItemModule();
        ScrollRegistry scrollReg = itemMod != null ? itemMod.getScrollRegistry() : null;
        int maxSockets = tier.getSockets();

        int[] socketGuiSlots = {20, 21, 22};
        String[] reqTierNames = {"&9Rare (Tesseract)", "&5Epic (Chronolith)", "&6Legendary (Singularity)"};
        for (int idx = 0; idx < 3; idx++) {
            int guiSlot = socketGuiSlots[idx];
            if (idx >= maxSockets) {
                inventory.setItem(guiSlot, createItem(
                        Material.IRON_BARS,
                        "&c&lLocked Keystone Socket #" + (idx + 1),
                        Arrays.asList(
                                "&8Keystone Infusion Slot",
                                "&f",
                                "&7Requires: " + reqTierNames[idx],
                                "&7Upgrade your Keystone tier to unlock this socket."
                        )
                ));
            } else if (idx < socketIds.size() && !socketIds.get(idx).isBlank()) {
                String sId = socketIds.get(idx);
                ScrollModel sm = scrollReg != null ? scrollReg.getScroll(sId) : null;
                Material mat = sm != null ? sm.getIcon() : Material.PAPER;
                String title = sm != null ? sm.getName() : ("&#38BDF8&lSocket #" + (idx + 1) + ": &f" + sId);
                List<String> sLore = new ArrayList<>();
                sLore.add("&8Keystone Infusion Slot #" + (idx + 1));
                sLore.add("&f");
                if (sm != null) {
                    sLore.add("&7Effect: " + sm.getSocketDisplay());
                    sLore.add("&7Mechanic: &f" + sm.getMechanic("type", "KEYSTONE"));
                } else {
                    sLore.add("&7Scroll ID: &f" + sId);
                }
                sLore.add("&f");
                sLore.add("&#EF4444▶ &eShift-Click: &cDetach & Return Scroll");
                sLore.add("&#38BDF8▶ &7Click with another Keystone Scroll on cursor to swap");
                ItemStack socketIcon = createItem(mat, title, sLore);
                if (sm != null && sm.getCustomModelData() != null) {
                    ItemMeta smMeta = socketIcon.getItemMeta();
                    if (smMeta != null) {
                        smMeta.setCustomModelData(sm.getCustomModelData());
                        smMeta.setEnchantmentGlintOverride(true);
                        socketIcon.setItemMeta(smMeta);
                    }
                }
                inventory.setItem(guiSlot, socketIcon);
            } else {
                inventory.setItem(guiSlot, createItem(
                        Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                        "&#38BDF8&lEmpty Keystone Socket #" + (idx + 1),
                        Arrays.asList(
                                "&8Keystone Infusion Slot",
                                "&f",
                                "&7Status: &aUnlocked & Empty",
                                "&f",
                                "&#FCD34D▶ &ePick up a Keystone Scroll from your inventory",
                                "&#FCD34D  &eand click this slot to infuse it!"
                        )
                ));
            }
        }

        // Slots 28, 29, 30, 31: Deposit Controls
        int carriedCubes = energyService.countEnergyCubesInInventory(player);
        int space = Math.max(0, effectiveMax - energy);

        inventory.setItem(28, createItem(
                Material.EMERALD,
                "&#10B981&lDeposit +1 Tesseract Cube",
                Arrays.asList(
                        "&8Energy Injection",
                        "&f",
                        "&7Carried Cubes: &f" + carriedCubes,
                        "&7Available Capacity: &b" + space + " 🗲",
                        "&f",
                        "&#10B981▶ &eClick to deposit 1x Tesseract Cube (+1 🗲)"
                )
        ));

        inventory.setItem(29, createItem(
                Material.EMERALD_BLOCK,
                "&#10B981&lDeposit +10 Tesseract Cubes",
                Arrays.asList(
                        "&8Batch Energy Injection",
                        "&f",
                        "&7Carried Cubes: &f" + carriedCubes,
                        "&7Available Capacity: &b" + space + " 🗲",
                        "&f",
                        "&#10B981▶ &eClick to deposit up to 10x Cubes (+10 🗲)"
                )
        ));

        inventory.setItem(30, createItem(
                Material.NETHER_STAR,
                "&#38BDF8&lDeposit ALL Tesseract Cubes",
                Arrays.asList(
                        "&8Maximum Matrix Charge",
                        "&f",
                        "&7Carried Cubes: &f" + carriedCubes,
                        "&7Will Deposit: &b" + Math.min(carriedCubes, space) + "x Cubes",
                        "&8(Surplus cubes are never consumed)",
                        "&f",
                        "&#38BDF8▶ &eClick to fill Keystone up to " + effectiveMax + " 🗲"
                )
        ));

        inventory.setItem(31, createItem(
                Material.HOPPER,
                "&#FBBF24&lCursor Cube Injector",
                Arrays.asList(
                        "&8Direct Stack Deposit",
                        "&f",
                        "&7Pick up a stack of &bTesseract Cubes",
                        "&7from your inventory and click them here",
                        "&7to inject up to &f" + space + " 🗲&7."
                )
        ));

        // Slots 33, 34: Withdraw Controls
        inventory.setItem(33, createItem(
                Material.AMETHYST_SHARD,
                "&#F59E0B&lWithdraw 1x Tesseract Cube",
                Arrays.asList(
                        "&8Energy Extraction",
                        "&f",
                        "&7Extractable Whole Charges: &f" + energy + " 🗲",
                        "&f",
                        "&#F59E0B▶ &eClick to withdraw 1 🗲 as a Cube item"
                )
        ));

        inventory.setItem(34, createItem(
                Material.AMETHYST_CLUSTER,
                "&#F59E0B&lWithdraw 10x Tesseract Cubes",
                Arrays.asList(
                        "&8Batch Energy Extraction",
                        "&f",
                        "&7Extractable Whole Charges: &f" + energy + " 🗲",
                        "&f",
                        "&#F59E0B▶ &eClick to withdraw up to 10 🗲 as Cube items"
                )
        ));

        // Slot 38: Quest Tracker
        String nextNpc = getNextQuestContact(player);
        boolean isFinished = nextNpc == null || nextNpc.isEmpty() || nextNpc.equalsIgnoreCase("finished")
                || nextNpc.toLowerCase(Locale.ROOT).contains("completed");
        if (isFinished) {
            inventory.setItem(38, createItem(
                    Material.EMERALD,
                    "&#10B981&lOperation Log: Storyline Complete",
                    Arrays.asList(
                            "&8Dragoncote Lore & Personnel",
                            "&f",
                            "&aYou have resolved the current Storyline operations!",
                            "&7Stand by for future chapters and imperial expansion.",
                            "&f",
                            "&#38BDF8▶ &eClick to inspect your Personnel Dossier (/lore)"
                    )
            ));
        } else {
            inventory.setItem(38, createItem(
                    Material.COMPASS,
                    "&#38BDF8&lActive Mission Target: &#FBBF24" + nextNpc,
                    Arrays.asList(
                            "&8Dragoncote Lore & Personnel",
                            "&f",
                            "&7Next Contact: &f" + nextNpc,
                            "&7Status: &eAwaiting in-person arrival",
                            "&f",
                            "&#10B981▶ &eLeft-Click: Attune 3D HUD Waypoint to " + nextNpc,
                            "&#38BDF8▶ &eRight-Click: Open Quest Dossier (/lore)"
                    )
            ));
        }

        // Slot 40: TP Effects (/dm open tpcolor)
        inventory.setItem(40, createItem(
                Material.ENDER_EYE,
                "&#A855F7&lTeleportation Visual Effects",
                Arrays.asList(
                        "&8Spatial Arrival & Departure Cosmetics",
                        "&f",
                        "&7Customize your teleportation colors, particles,",
                        "&7and 3D voxel arrival animations.",
                        "&7Preview effects live inside &d/wardrobe&7!",
                        "&f",
                        "&#A855F7▶ &eClick to open Teleportation Colors (/dm open tpcolor)"
                )
        ));

        // Slot 49: Unified Back to Profile
        inventory.setItem(49, dc.dccosmetics.util.HeadUtil.createBackButton(
                "&#38BDF8&lBack to Profile",
                Collections.singletonList("&7Click to return to your Player Profile (/profile).")
        ));
    }

    private static final String[] DIGIT_TEXTURES = new String[]{
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2ExMzI5NTZiY2JmOTUyMTNhNTIxYWE5MzQ1NmQ0YTE1Yzc5Y2YxNzM2ZWNiYWJmZTgxZTVmNWU0NCJ9fX0=",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzM0N2I3YTVhNmJiNjQxOWQzM2ViNDYxMDI2NTBjYzkxYmZkYzE1OWQ4ZmYxZjU1OTk2ZDRmMGFkNWJjNzU4In19fQ==",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjBiYTQxMmFhNzZhOGUxMTlhZTNlY2EwNzE0MjhmM2Q5ZTIwNDJiNDk1MmJkODMzNThkNjI5NmU4MTJiZTgifX19",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzZkYmZkZmFjMTVlMjg0YzJlYzAyZTM4OTExYjRlYzVkODE1YmZiMWU1ZWVlNTIyZDU1N2FmMzVkODQwZGMifX19",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTdhYzUzMjMzYWU4NDJhZWJiMzhjMTNiZWNjZTdjOTFmOGQ1ZjY3OTc2MzMyY2UxYmE4OGRjMGU2ODJmOGQ0In19fQ==",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmE2NzE0NGIzMjNhZDRkY2ViZmQxYzZhNjMwNzQzYzcwMmM0ZGE0NTUyMjg5YTkyNDIxZWFmYzRjMmQ0YjQzIn19fQ==",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDEyYWFiNDAxZDY5YTVmMGM1Y2FkY2IzYzFkMTM3YjEwNzk0YzQzYjA1OGY5MzYzMGI4MTQ1YjgzNDk3YTQwIn19fQ==",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDRlMjgyNGVlZDI1M2FjOTMyY2E4MWRmN2VmYzUxMDZiZDQ2NWMzNDlhMThiZGU2OTk2MzhjMWYxNDZmNTQifX19",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjQ1ODhkOGVmZDk5NTExYjhkMmNhMzk1NjQ4ZDMyMmZhOGNkYzk4ZDQyYmQ3OWUzYzBkNjEwM2M5NDhlNjgifX19",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2MyYTUxYzRkZjc4MjliMDViYWM5YjM4YTk5YzcyNzNiMWEzYjFkNDZiZmIyYTljNjVlNjE3NDBiZjYyNDRhYyJ9fX0="
    };

    private static ItemStack createDigitHead(int digit, String name, List<String> lore) {
        int idx = Math.max(0, Math.min(9, digit));
        ItemStack head = dc.dccosmetics.util.HeadUtil.getCustomHead(DIGIT_TEXTURES[idx]);
        if (head == null) {
            return createItem(Material.SEA_LANTERN, name, lore);
        }
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            if (lore != null) {
                List<String> colored = new ArrayList<>(lore.size());
                for (String l : lore) {
                    colored.add(ColorUtil.colorize(l));
                }
                meta.setLore(colored);
            }
            head.setItemMeta(meta);
        }
        return head;
    }

    private static ItemStack getEquippedKeystone(Player player) {
        var profileMgr = DCCosmetics.getInstance().getProfileManager();
        if (profileMgr == null) return null;
        PlayerProfile pp = profileMgr.getProfile(player);
        if (pp == null) return null;
        ItemStack eq = pp.getEquipment("keystone");
        if (eq == null || eq.getType().isAir()) return null;
        return eq;
    }

    private static String getNextQuestContact(Player player) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                String target = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%dragoncote_next_contact%");
                if (target != null && !target.isEmpty() && !target.startsWith("%")) {
                    return target;
                }
            }
        } catch (Exception ignored) {}
        return "Corvus";
    }

    private static ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            if (lore != null) {
                List<String> colored = new ArrayList<>(lore.size());
                for (String l : lore) {
                    colored.add(ColorUtil.colorize(l));
                }
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Checks and acquires the per-player per-tick operation lock.
     */
    public static boolean tryAcquireOpLock(Player player) {
        UUID uuid = player.getUniqueId();
        int tick = player.getTicksLived();
        Integer lastTick = LAST_OP_TICK.get(uuid);
        if (lastTick != null && lastTick == tick) {
            return false;
        }
        if (!OP_IN_FLIGHT.add(uuid)) {
            return false;
        }
        LAST_OP_TICK.put(uuid, tick);
        return true;
    }

    public static void releaseOpLock(Player player) {
        OP_IN_FLIGHT.remove(player.getUniqueId());
    }

    /**
     * Removes up to {@code maxToRemove} energy cubes from the player's main storage contents first,
     * returning the exact count removed (Task P2-02 rule: remove items first -> verify count -> add energy).
     */
    public static int removeCubesFromInventoryFirst(Player player, int maxToRemove) {
        if (maxToRemove <= 0) return 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        int remaining = maxToRemove;
        int removed = 0;

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) continue;
            if (!KeystoneDetector.isEnergyCube(stack)) continue;

            int take = Math.min(remaining, stack.getAmount());
            if (take <= 0) continue;

            int newAmt = stack.getAmount() - take;
            if (newAmt <= 0) {
                contents[i] = null;
            } else {
                stack.setAmount(newAmt);
            }
            removed += take;
            remaining -= take;
        }

        if (removed > 0) {
            player.getInventory().setStorageContents(contents);
        }
        return removed;
    }

    public static class MenuListener implements Listener {

        @EventHandler(priority = EventPriority.NORMAL)
        public void onInventoryClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (!(event.getView().getTopInventory().getHolder() instanceof KeystoneMenu menu)) return;

            int rawSlot = event.getRawSlot();
            int topSize = event.getView().getTopInventory().getSize();

            // Clicks in the player's bottom inventory are allowed for normal left/right cursor pickup
            // (GuiGuardListener already blocked SHIFT_*, COLLECT_TO_CURSOR, NUMBER_KEY, SWAP_OFFHAND)
            if (rawSlot >= topSize || rawSlot < 0) {
                return;
            }

            event.setCancelled(true);

            if (!tryAcquireOpLock(player)) {
                return;
            }
            try {
                handleTopSlotClick(player, menu, rawSlot, event);
            } finally {
                releaseOpLock(player);
            }
        }

        private void handleTopSlotClick(Player player, KeystoneMenu menu, int slot, InventoryClickEvent event) {
            TeleportationManager teleMgr = DCCosmetics.getInstance().getTeleportationManager();
            KeystoneProfile profile = teleMgr.getProfile(player);
            KeystoneEnergyService energyService = teleMgr.getEnergyService();

            if (slot == 49) {
                player.closeInventory();
                player.performCommand("profile");
                return;
            }

            if (slot == 38) {
                String nextNpc = getNextQuestContact(player);
                boolean isFinished = nextNpc == null || nextNpc.isEmpty() || nextNpc.equalsIgnoreCase("finished")
                        || nextNpc.toLowerCase(Locale.ROOT).contains("completed");
                player.closeInventory();
                if (isFinished || event.isRightClick()) {
                    player.performCommand("lore");
                } else {
                    player.performCommand("dcnpc waypoint");
                }
                return;
            }

            if (slot == 40) {
                player.closeInventory();
                if (Bukkit.getPluginManager().isPluginEnabled("DeluxeMenus")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dm open tpcolor " + player.getName());
                } else {
                    player.performCommand("tpeffect");
                }
                return;
            }

            // Sockets: slots 20, 21, 22
            if (slot == 20 || slot == 21 || slot == 22) {
                handleSocketSlotClick(player, menu, slot - 20, event);
                return;
            }

            // Deposit +1 (28), +10 (29), All (30)
            if (slot == 28 || slot == 29 || slot == 30) {
                if (profile.getTier() == KeystoneTier.NONE) {
                    DCMessage.sendError(player, "Equip a Keystone in /profile first to charge your matrix!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    return;
                }
                int effectiveMax = energyService.getEffectiveMaxEnergy(player, profile);
                int space = Math.max(0, effectiveMax - profile.getEnergy());
                if (space <= 0) {
                    DCMessage.sendWarning(player, "Your Keystone is already at maximum capacity (&#FFFFFF" + effectiveMax + " 🗲&#FDE047)!");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
                    return;
                }

                int requested = (slot == 28) ? 1 : (slot == 29 ? 10 : space);
                int maxToTake = Math.min(requested, space);

                // Remove items first -> verify count -> then add energy -> mark dirty
                int removed = removeCubesFromInventoryFirst(player, maxToTake);
                if (removed <= 0) {
                    DCMessage.sendError(player, "You do not have any Tesseract Energy Cubes in your inventory!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    return;
                }

                int accepted = profile.addCharges(removed, effectiveMax);
                if (accepted < removed) {
                    // Safety net: return any unaccepted cubes
                    energyService.giveEnergyCubes(player, removed - accepted);
                }
                teleMgr.getPersistence().saveNow(profile);
                KeystoneLoreTokens.scheduleInventoryRefresh(player);

                player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.3f);
                DCMessage.sendGeneral(player, "Injected &#FFFFFF+" + accepted + " 🗲 &#0CBF93into your Keystone (&#FFFFFF" + profile.getEnergy() + "/" + effectiveMax + " 🗲&#0CBF93).");
                menu.refresh();
                return;
            }

            // Deposit from Cursor (Slot 31)
            if (slot == 31) {
                ItemStack cursor = event.getCursor();
                if (cursor == null || cursor.getType().isAir() || !KeystoneDetector.isEnergyCube(cursor)) {
                    DCMessage.sendWarning(player, "Pick up Tesseract Energy Cubes on your cursor and click here to inject them!");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
                    return;
                }
                if (profile.getTier() == KeystoneTier.NONE) {
                    DCMessage.sendError(player, "Equip a Keystone in /profile first to charge your matrix!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    return;
                }
                int effectiveMax = energyService.getEffectiveMaxEnergy(player, profile);
                int space = Math.max(0, effectiveMax - profile.getEnergy());
                if (space <= 0) {
                    DCMessage.sendWarning(player, "Your Keystone is already at maximum capacity (&#FFFFFF" + effectiveMax + " 🗲&#FDE047)!");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
                    return;
                }

                int take = Math.min(cursor.getAmount(), space);
                if (take <= 0) return;

                // Remove from cursor first -> verify -> add energy -> save
                int leftoverAmt = cursor.getAmount() - take;
                if (leftoverAmt <= 0) {
                    event.getView().setCursor(null);
                } else {
                    cursor.setAmount(leftoverAmt);
                    event.getView().setCursor(cursor);
                }

                int accepted = profile.addCharges(take, effectiveMax);
                if (accepted < take) {
                    energyService.giveEnergyCubes(player, take - accepted);
                }
                teleMgr.getPersistence().saveNow(profile);
                KeystoneLoreTokens.scheduleInventoryRefresh(player);

                player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.4f);
                DCMessage.sendGeneral(player, "Injected &#FFFFFF+" + accepted + " 🗲 &#0CBF93from cursor (&#FFFFFF" + profile.getEnergy() + "/" + effectiveMax + " 🗲&#0CBF93).");
                menu.refresh();
                return;
            }

            // Withdraw 1 (33) or 10 (34)
            if (slot == 33 || slot == 34) {
                int available = profile.usableEnergy();
                if (available <= 0) {
                    DCMessage.sendError(player, "Your Keystone has no whole Tesseract charges available to withdraw!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    return;
                }
                int requested = (slot == 33) ? 1 : 10;
                int withdrawn = energyService.withdrawCharges(player, requested);
                if (withdrawn <= 0) return;

                // Give items after energy is deducted (leftovers dropped naturally)
                energyService.giveEnergyCubes(player, withdrawn);
                KeystoneLoreTokens.scheduleInventoryRefresh(player);

                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9f, 1.2f);
                DCMessage.sendGeneral(player, "Withdrew &#FFFFFF" + withdrawn + "x Tesseract Energy Cube &#0CBF93from your Keystone.");
                menu.refresh();
            }
        }

        private void handleSocketSlotClick(Player player, KeystoneMenu menu, int socketIdx, InventoryClickEvent event) {
            TeleportationManager teleMgr = DCCosmetics.getInstance().getTeleportationManager();
            KeystoneProfile kProfile = teleMgr.getProfile(player);
            KeystoneTier tier = kProfile.getTier();
            int maxSockets = tier.getSockets();

            if (socketIdx < 0 || socketIdx >= maxSockets) {
                DCMessage.sendError(player, "This Keystone socket requires a higher tier Keystone!");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.8f, 0.8f);
                return;
            }

            var profileMgr = DCCosmetics.getInstance().getProfileManager();
            PlayerProfile pp = profileMgr != null ? profileMgr.getProfile(player) : null;
            ItemStack equipped = pp != null ? pp.getEquipment("keystone") : null;
            if (equipped == null || equipped.getType().isAir() || KeystoneDetector.tierOf(equipped) == KeystoneTier.NONE) {
                DCMessage.sendError(player, "You do not have a valid Keystone equipped in /profile!");
                return;
            }

            var itemMod = DCCosmetics.getInstance().getItemModule();
            ScrollRegistry scrollReg = itemMod != null ? itemMod.getScrollRegistry() : null;
            if (scrollReg == null) return;

            List<String> current = new ArrayList<>(KeystoneDetector.socketedScrolls(equipped));
            ItemStack cursor = event.getCursor();
            boolean hasCursorItem = cursor != null && !cursor.getType().isAir();

            // 1. Shift-Click (with empty cursor) -> Detach & Refund scroll at socketIdx
            if (!hasCursorItem && event.isShiftClick()) {
                if (socketIdx >= current.size()) {
                    return;
                }
                String removedId = current.remove(socketIdx);
                KeystoneDetector.writeSocketedScrolls(equipped, current);
                pp.setEquipment("keystone", equipped);
                profileMgr.saveProfile(player);

                ItemStack refundScroll = scrollReg.createScrollItem(removedId, 1);
                if (refundScroll != null) {
                    player.getInventory().addItem(refundScroll).values().forEach(drop ->
                            player.getWorld().dropItemNaturally(player.getLocation(), drop));
                }

                // If removing a Capacitor scroll lowered effectiveMax below current energy, return overflow as cubes
                int newEffMax = teleMgr.getEnergyService().getEffectiveMaxEnergy(player, kProfile);
                int overflow = kProfile.trimToTier(newEffMax);
                if (overflow > 0) {
                    teleMgr.getEnergyService().giveEnergyCubes(player, overflow);
                    DCMessage.sendInfo(player, "Returned &#FFFFFF" + overflow + "x Tesseract Energy Cube &#BAE6FDabove your updated capacity.");
                }
                teleMgr.getPersistence().saveNow(kProfile);
                if (itemMod.getStatAggregator() != null) {
                    itemMod.getStatAggregator().invalidate(player);
                }
                KeystoneLoreTokens.scheduleInventoryRefresh(player);

                player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.9f, 1.3f);
                DCMessage.sendGeneral(player, "Detached &#FCD34D" + removedId + " &#0CBF93from your Keystone and returned it to your inventory.");
                menu.refresh();
                return;
            }

            // 2. Click with a Keystone Scroll on cursor -> Attach / Swap into socket
            if (hasCursorItem) {
                String scrollId = scrollReg.getScrollId(cursor);
                ScrollModel newScroll = scrollId != null ? scrollReg.getScroll(scrollId) : null;
                if (newScroll == null || newScroll.getType() != ScrollType.KEYSTONE) {
                    DCMessage.sendError(player, "Only Keystone Scrolls can be infused into Keystone sockets!");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.8f, 0.8f);
                    return;
                }

                String newMechType = newScroll.getMechanic("type", "");
                int targetIndex = socketIdx < current.size() ? socketIdx : current.size();

                // Check duplicate scroll ID or duplicate mechanic type across all OTHER slots
                for (int i = 0; i < current.size(); i++) {
                    String existingId = current.get(i);
                    if (existingId.equalsIgnoreCase(newScroll.getId())) {
                        DCMessage.sendWarning(player, "Your Keystone already has " + newScroll.getName() + " &#FDE047infused!");
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.8f, 0.8f);
                        return;
                    }
                    if (i != targetIndex) {
                        ScrollModel existingModel = scrollReg.getScroll(existingId);
                        String oldMechType = existingModel != null ? existingModel.getMechanic("type", "") : "";
                        if (!newMechType.isEmpty() && newMechType.equalsIgnoreCase(oldMechType)) {
                            DCMessage.sendError(player, "Your Keystone already has a &e" + newMechType.replace("KEYSTONE_", "") + " &caugment in another socket!");
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.8f, 0.8f);
                            return;
                        }
                    }
                }

                // Consume 1 scroll from cursor first
                if (cursor.getAmount() > 1) {
                    cursor.setAmount(cursor.getAmount() - 1);
                    event.getView().setCursor(cursor);
                } else {
                    event.getView().setCursor(null);
                }

                // If replacing an existing slot, refund the old scroll
                String replacedId = null;
                if (targetIndex < current.size()) {
                    replacedId = current.set(targetIndex, newScroll.getId());
                } else {
                    current.add(newScroll.getId());
                }

                KeystoneDetector.writeSocketedScrolls(equipped, current);
                pp.setEquipment("keystone", equipped);
                profileMgr.saveProfile(player);

                if (replacedId != null) {
                    ItemStack oldScrollItem = scrollReg.createScrollItem(replacedId, 1);
                    if (oldScrollItem != null) {
                        player.getInventory().addItem(oldScrollItem).values().forEach(drop ->
                                player.getWorld().dropItemNaturally(player.getLocation(), drop));
                        DCMessage.sendInfo(player, "Previous infusion &#FCD34D" + replacedId + " &#BAE6FDwas returned to your inventory.");
                    }
                }

                int newEffMax = teleMgr.getEnergyService().getEffectiveMaxEnergy(player, kProfile);
                int overflow = kProfile.trimToTier(newEffMax);
                if (overflow > 0) {
                    teleMgr.getEnergyService().giveEnergyCubes(player, overflow);
                }
                teleMgr.getPersistence().saveNow(kProfile);
                if (itemMod.getStatAggregator() != null) {
                    itemMod.getStatAggregator().invalidate(player);
                }
                KeystoneLoreTokens.scheduleInventoryRefresh(player);

                player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9f, 1.4f);
                DCMessage.sendGeneral(player, "Infused " + newScroll.getName() + " &#0CBF93into Keystone Socket #" + (targetIndex + 1) + "!");
                menu.refresh();
            }
        }
    }
}
