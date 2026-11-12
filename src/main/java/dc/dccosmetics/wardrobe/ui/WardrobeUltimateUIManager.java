package dc.dccosmetics.wardrobe.ui;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.CosmeticTemplate;
import dc.dccosmetics.wardrobe.WardrobeManager;
import dc.dccosmetics.wardrobe.WardrobeSession;
import dc.dccosmetics.wardrobe.teleport.WardrobeTeleportEffect;
import dc.dccosmetics.wardrobe.teleport.WardrobeTeleportRegistry;
import dev.xqedii.ultimateUI.api.UltimateUIAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class WardrobeUltimateUIManager {

    private final WardrobeManager manager;

    public WardrobeUltimateUIManager(WardrobeManager manager) {
        this.manager = manager;
    }

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("UltimateUI") && UltimateUIAPI.isAvailable();
    }

    // =========================================================================
    // 1. OVERVIEW MENU
    // =========================================================================
    public void openOverview(Player player, WardrobeSession session) {
        if (!isAvailable()) {
            player.sendMessage(ChatColor.RED + "UltimateUI is not available on this server!");
            return;
        }

        session.setCurrentLevel(WardrobeSession.MenuLevel.CATEGORIES);
        UltimateUIAPI api = UltimateUIAPI.get();
        try {
            api.closeGui(player);
        } catch (Throwable ignored) {}

        boolean opened = api.openGuiHud(player, "wardrobe_overview");
        if (!opened) {
            DCCosmetics.getInstance().getLogger().warning("[Wardrobe][UltimateUI] Failed to open wardrobe_overview for " + player.getName());
            return;
        }

        renderOverview(player, session);
    }

    public void renderOverview(Player player, WardrobeSession session) {
        if (!isAvailable()) return;
        UltimateUIAPI api = UltimateUIAPI.get();

        int sel = session.getSelectedIndex();
        List<String> cats = session.getCategoriesList();

        for (int i = 0; i < 4; i++) {
            boolean isSel = (i == sel);
            api.setElementColor(player, "btn_ov_cat_" + i, isSel ? "162238" : "0A0E17");
            if (cats != null && i < cats.size()) {
                String catName = cats.get(i).toUpperCase();
                String prefix = isSel ? "&#84E2CD▶ " : "&7";
                api.setElementText(player, "text_ov_cat_" + i, prefix + catName);
            }
        }
    }

    // =========================================================================
    // 2. RARITIES MENU
    // =========================================================================
    public void openRarities(Player player, WardrobeSession session) {
        if (!isAvailable()) return;

        session.setCurrentLevel(WardrobeSession.MenuLevel.RARITIES);
        UltimateUIAPI api = UltimateUIAPI.get();
        try {
            api.closeGui(player);
        } catch (Throwable ignored) {}

        boolean opened = api.openGuiHud(player, "wardrobe_cosmetics");
        if (!opened) {
            DCCosmetics.getInstance().getLogger().warning("[Wardrobe][UltimateUI] Failed to open wardrobe_cosmetics for " + player.getName());
            return;
        }

        String activeCat = session.getSelectedCategory().toLowerCase();
        LinkedHashSet<String> rarities = new LinkedHashSet<>();

        if ("teleport".equalsIgnoreCase(activeCat)) {
            for (String r : WardrobeTeleportRegistry.getOrderedVisibleRarities()) {
                rarities.add(cap(r));
            }
        } else {
            List<CosmeticTemplate> allForCat = new ArrayList<>();
            for (CosmeticTemplate t : DCCosmetics.getInstance().getTemplateRegistry().getAllTemplates()) {
                if (t.getEquipmentSlot().equalsIgnoreCase(activeCat) && manager.isCosmeticVisible(t)) {
                    allForCat.add(t);
                }
            }
            for (String r : new String[]{"uncommon", "rare", "epic", "legendary", "mastery"}) {
                for (CosmeticTemplate t : allForCat) {
                    if (t.getRarity() != null && t.getRarity().equalsIgnoreCase(r)) {
                        rarities.add(cap(r));
                        break;
                    }
                }
            }
            for (CosmeticTemplate t : allForCat) {
                if (t.getRarity() != null) {
                    rarities.add(cap(t.getRarity()));
                }
            }
        }

        List<String> rarityList = new ArrayList<>(rarities);
        session.setCurrentRaritiesList(rarityList);
        session.setSelectedIndex(0);
        session.setScrollWindowOffset(0);

        renderRarities(player, session);
    }

    public void renderRarities(Player player, WardrobeSession session) {
        if (!isAvailable()) return;
        UltimateUIAPI api = UltimateUIAPI.get();

        String activeCat = session.getSelectedCategory().toUpperCase();
        List<String> rarities = session.getCurrentRaritiesList();
        if (rarities == null) rarities = new ArrayList<>();

        int total = rarities.size();
        int sel = session.getSelectedIndex();
        if (sel >= total && total > 0) sel = total - 1;
        if (sel < 0) sel = 0;
        session.setSelectedIndex(sel);

        api.setElementText(player, "text_cos_cat_title", "&#84E2CD" + activeCat + " RARITIES");
        api.setElementText(player, "text_cos_count", "&7" + total + " Collections Available");

        for (int i = 0; i < 6; i++) {
            if (i < total) {
                String r = rarities.get(i);
                boolean isSel = (i == sel);
                int count = countItemsInRarity(session.getSelectedCategory(), r);

                setCardVisibility(api, player, i, true);

                String prefix = isSel ? "&#84E2CD▶ &f" : "&7";
                api.setElementText(player, "card_title_" + i, prefix + r.toUpperCase());
                api.setElementText(player, "card_sub_" + i, rarityBadge(r) + " &7• " + count + " Styles");
                api.setElementText(player, "card_status_" + i, isSel ? "&a[ BROWSE ]" : "&8[ SELECT ]");

                api.setElementColor(player, "card_face_" + i, isSel ? getOreColorHex(r) : "1f2632");
                api.setElementColor(player, "card_border_" + i, isSel ? "62c4ff" : "1e1e1f");
                api.setElementVisible(player, "card_indicator_" + i, isSel);
            } else {
                setCardVisibility(api, player, i, false);
            }
        }

        updateScrollbar(api, player, sel, total);

        if (sel < total && total > 0) {
            String selectedRarity = rarities.get(sel);
            int count = countItemsInRarity(session.getSelectedCategory(), selectedRarity);

            api.setElementText(player, "text_det_header", "&#84E2CD✦ COLLECTION TIER ✦");
            api.setElementText(player, "text_det_title", rarityBadge(selectedRarity) + " &fGRADE");
            api.setElementText(player, "text_det_sub", "&7" + activeCat + " ARCHIVE");
            api.setElementText(player, "text_det_desc", "&7Browse " + selectedRarity + " models.\n&8Precision scaled for high visual quality.");
            api.setElementText(player, "text_det_status", "&e✦ " + count + " Available Models");
            api.setElementText(player, "text_det_action", "&f[ LEFT-CLICK ]");
            api.setElementColor(player, "btn_det_action_face", "2b4c38");
        }

        api.setElementText(player, "cos_hint_text",
            "&e⟨ Scroll Hotbar ⟩ &7Select Tier   &8|   &a⟨ Left-Click ⟩ &7Browse Items   &8|   &b⟨ Right-Click ⟩ &7Back   &8|   &c⟨ Shift ⟩ &7Exit");
    }

    // =========================================================================
    // 3. COSMETICS MENU & SCROLLING VIEWPORT
    // =========================================================================
    public void openCosmetics(Player player, WardrobeSession session) {
        openCosmetics(player, session, false);
    }

    public void openCosmetics(Player player, WardrobeSession session, boolean preserveSelection) {
        if (!isAvailable()) return;

        session.setCurrentLevel(WardrobeSession.MenuLevel.COSMETICS);
        UltimateUIAPI api = UltimateUIAPI.get();
        try {
            api.closeGui(player);
        } catch (Throwable ignored) {}

        boolean opened = api.openGuiHud(player, "wardrobe_cosmetics");
        if (!opened) {
            DCCosmetics.getInstance().getLogger().warning("[Wardrobe][UltimateUI] Failed to open wardrobe_cosmetics for " + player.getName());
            return;
        }

        String activeCat = session.getSelectedCategory().toLowerCase();
        String activeRarity = session.getSelectedRarity();

        if ("teleport".equalsIgnoreCase(activeCat)) {
            session.setCurrentTeleportList(WardrobeTeleportRegistry.getByRarity(activeRarity));
        } else {
            List<CosmeticTemplate> templates = new ArrayList<>();
            for (CosmeticTemplate t : DCCosmetics.getInstance().getTemplateRegistry().getAllTemplates()) {
                if (t.getEquipmentSlot().equalsIgnoreCase(activeCat)
                        && t.getRarity() != null && t.getRarity().equalsIgnoreCase(activeRarity)
                        && manager.isCosmeticVisible(t)) {
                    templates.add(t);
                }
            }
            session.setCurrentCosmeticsList(templates);
        }

        if (!preserveSelection) {
            session.setSelectedIndex(0);
            session.setScrollWindowOffset(0);
        }

        renderCosmetics(player, session);
    }

    public void renderCosmetics(Player player, WardrobeSession session) {
        if (!isAvailable()) return;
        UltimateUIAPI api = UltimateUIAPI.get();

        String activeCat = session.getSelectedCategory().toLowerCase();
        boolean isTeleport = "teleport".equalsIgnoreCase(activeCat);

        if (isTeleport) {
            renderTeleportList(player, session, api);
        } else {
            renderCosmeticsList(player, session, api);
        }
    }

    private void renderCosmeticsList(Player player, WardrobeSession session, UltimateUIAPI api) {
        String activeCat = session.getSelectedCategory().toLowerCase();
        String activeRarity = session.getSelectedRarity();
        List<CosmeticTemplate> list = session.getCurrentCosmeticsList();
        if (list == null) list = new ArrayList<>();

        int total = list.size();
        int sel = session.getSelectedIndex();
        if (sel >= total && total > 0) sel = total - 1;
        if (sel < 0) sel = 0;
        session.setSelectedIndex(sel);

        int offset = session.getScrollWindowOffset();
        if (sel < offset) {
            offset = sel;
        } else if (sel >= offset + 6) {
            offset = sel - 5;
        }
        if (offset < 0) offset = 0;
        session.setScrollWindowOffset(offset);

        api.setElementText(player, "text_cos_cat_title", "&#84E2CD" + activeCat.toUpperCase() + " &8• " + rarityBadge(activeRarity));
        api.setElementText(player, "text_cos_count", "&7" + total + " Available Styles");

        for (int i = 0; i < 6; i++) {
            int itemIndex = offset + i;
            if (itemIndex < total) {
                CosmeticTemplate t = list.get(itemIndex);
                boolean isSel = (itemIndex == sel);
                boolean hasPerm = player.hasPermission(t.getPermission()) || player.hasPermission("dccosmetics.all");

                setCardVisibility(api, player, i, true);

                String titlePrefix = isSel ? "&#84E2CD▶ &f" : "&7";
                api.setElementText(player, "card_title_" + i, titlePrefix + t.getItemName());
                api.setElementText(player, "card_sub_" + i, rarityBadge(t.getRarity()));

                String status = hasPerm ? "&a✔ OWNED" : "&6✦ STORE";
                api.setElementText(player, "card_status_" + i, isSel ? (hasPerm ? "&aEQUIPPED" : "&6PREVIEW") : status);

                api.setElementColor(player, "card_face_" + i, isSel ? "2b3340" : "1f2632");
                api.setElementColor(player, "card_border_" + i, isSel ? "62c4ff" : "1e1e1f");
                api.setElementVisible(player, "card_indicator_" + i, isSel);
            } else {
                setCardVisibility(api, player, i, false);
            }
        }

        updateScrollbar(api, player, sel, total);

        if (sel < total && total > 0) {
            CosmeticTemplate selected = list.get(sel);
            boolean hasPerm = player.hasPermission(selected.getPermission()) || player.hasPermission("dccosmetics.all");

            api.setElementText(player, "text_det_header", "&#84E2CD✦ LIVE PREVIEW ✦");
            api.setElementText(player, "text_det_title", "&b" + selected.getItemName());
            api.setElementText(player, "text_det_sub", rarityBadge(selected.getRarity()) + " &7• " + activeCat.toUpperCase());
            api.setElementText(player, "text_det_desc", "&7Live holographic preview model.\n&8Precision calibrated on the central NPC.");

            if (hasPerm) {
                api.setElementText(player, "text_det_status", "&a✔ UNLOCKED (Owned)");
                api.setElementText(player, "text_det_action", "&f[ EQUIPPED ]");
                api.setElementColor(player, "btn_det_action_face", "3c8527");
            } else {
                api.setElementText(player, "text_det_status", "&6✦ STORE ITEM ✦");
                api.setElementText(player, "text_det_action", "&6[ STORE ITEM ]");
                api.setElementColor(player, "btn_det_action_face", "48494a");
            }
        }

        api.setElementText(player, "cos_hint_text",
            "&e⟨ Scroll Hotbar ⟩ &7Browse Items   &8|   &a⟨ Left-Click ⟩ &7Equip/Preview   &8|   &b⟨ Right-Click ⟩ &7Back   &8|   &c⟨ Shift ⟩ &7Exit");
    }

    private void renderTeleportList(Player player, WardrobeSession session, UltimateUIAPI api) {
        String activeRarity = session.getSelectedRarity();
        List<WardrobeTeleportEffect> list = session.getCurrentTeleportList();
        if (list == null) list = new ArrayList<>();

        int total = list.size();
        int sel = session.getSelectedIndex();
        if (sel >= total && total > 0) sel = total - 1;
        if (sel < 0) sel = 0;
        session.setSelectedIndex(sel);

        int offset = session.getScrollWindowOffset();
        if (sel < offset) {
            offset = sel;
        } else if (sel >= offset + 6) {
            offset = sel - 5;
        }
        if (offset < 0) offset = 0;
        session.setScrollWindowOffset(offset);

        dc.dccosmetics.tpeffects.TpEffectManager tpMgr = DCCosmetics.getInstance().getTpEffectManager();
        String equippedId = tpMgr != null ? tpMgr.getSelectedEffectId(player) : null;
        String previewingId = session.getPreviewingTeleportId();

        api.setElementText(player, "text_cos_cat_title", "&#d97706" + "TELEPORT &8• " + rarityBadge(activeRarity));
        api.setElementText(player, "text_cos_count", "&7" + total + " Effects Available");

        for (int i = 0; i < 6; i++) {
            int itemIndex = offset + i;
            if (itemIndex < total) {
                WardrobeTeleportEffect eff = list.get(itemIndex);
                boolean isSel = (itemIndex == sel);
                boolean hasPerm = eff.getEntry() != null
                        ? dc.dccosmetics.tpeffects.TpEffectRegistry.hasPermission(player, eff.getEntry())
                        : (player.hasPermission(eff.getPermission()) || player.hasPermission("dc.tpeffect.*"));
                boolean isEquipped = eff.getId().equalsIgnoreCase(equippedId);
                boolean isPreviewing = eff.getId().equalsIgnoreCase(previewingId);

                setCardVisibility(api, player, i, true);

                String titlePrefix = isSel ? "&#d97706▶ &f" : "&7";
                api.setElementText(player, "card_title_" + i, titlePrefix + eff.getDisplayName());
                api.setElementText(player, "card_sub_" + i, rarityBadge(eff.getRarity()));

                String status;
                if (isEquipped) {
                    status = "&a✔ EQUIPPED";
                } else if (isPreviewing) {
                    status = hasPerm ? "&e▶ CLICK EQUIP" : "&e▶ PREVIEWING";
                } else {
                    status = hasPerm ? "&2✔ OWNED" : "&6✦ STORE";
                }
                api.setElementText(player, "card_status_" + i, status);

                api.setElementColor(player, "card_face_" + i, isSel ? "2b3340" : "1f2632");
                api.setElementColor(player, "card_border_" + i, isEquipped ? "3c8527" : (isSel ? "62c4ff" : "1e1e1f"));
                api.setElementVisible(player, "card_indicator_" + i, isSel);
            } else {
                setCardVisibility(api, player, i, false);
            }
        }

        updateScrollbar(api, player, sel, total);

        if (sel < total && total > 0) {
            WardrobeTeleportEffect selected = list.get(sel);
            boolean hasPerm = selected.getEntry() != null
                    ? dc.dccosmetics.tpeffects.TpEffectRegistry.hasPermission(player, selected.getEntry())
                    : (player.hasPermission(selected.getPermission()) || player.hasPermission("dc.tpeffect.*"));
            boolean isCine = selected.isCinematic();
            boolean isEquipped = selected.getId().equalsIgnoreCase(equippedId);
            boolean isPreviewing = selected.getId().equalsIgnoreCase(previewingId);

            api.setElementText(player, "text_det_header", isCine ? "&#f59e0b✦ 3D CAMERA CINEMATIC ✦" : "&#d97706✦ PORTAL PREVIEW ✦");
            api.setElementText(player, "text_det_title", "&d" + selected.getDisplayName());
            api.setElementText(player, "text_det_sub", rarityBadge(selected.getRarity()) + (isCine ? " &7• 3D VOXEL CAMERA" : " &7• WARP EFFECT"));
            api.setElementText(player, "text_det_desc", isCine
                    ? ("mastery".equalsIgnoreCase(selected.getRarity())
                    ? "&7Chrono singularity helix & 3D voxel camera.\n&8Implodes skin voxels with shockwave plunge."
                    : "&7Full 3D camera orbit & pixel dissolution.\n&8Dissolves skin into ascending obsidian shards.")
                    : "&7Animated departure & destination warp.\n&8Triggers dynamically when teleporting.");

            if (isEquipped) {
                api.setElementText(player, "text_det_status", "&a✔ CURRENTLY EQUIPPED");
                api.setElementText(player, "text_det_action", isPreviewing ? "&e[ CLICK TO UNEQUIP ]" : "&a[ EQUIPPED • CLICK PREVIEW ]");
                api.setElementColor(player, "btn_det_action_face", isPreviewing ? "b45309" : "3c8527");
            } else if (hasPerm) {
                api.setElementText(player, "text_det_status", "&a✔ UNLOCKED (Owned)");
                api.setElementText(player, "text_det_action", isPreviewing ? "&a[ CLICK AGAIN TO EQUIP ]" : "&b[ CLICK TO PREVIEW ]");
                api.setElementColor(player, "btn_det_action_face", isPreviewing ? "3c8527" : "2563eb");
            } else {
                api.setElementText(player, "text_det_status", "&6✦ STORE ITEM (Locked)");
                api.setElementText(player, "text_det_action", isPreviewing ? "&e[ LOCKED • PREVIEWING ]" : "&6[ CLICK TO PREVIEW ]");
                api.setElementColor(player, "btn_det_action_face", "48494a");
            }
        }

        api.setElementText(player, "cos_hint_text",
            "&e⟨ Scroll ⟩ &7Highlight   &8|   &a⟨ Left-Click ⟩ &7Preview → Equip   &8|   &b⟨ Right-Click ⟩ &7Back   &8|   &c⟨ Shift ⟩ &7Exit");
    }

    public void closeStudio(Player player) {
        if (!isAvailable()) return;
        try {
            UltimateUIAPI.get().closeGui(player);
        } catch (Throwable ignored) {}
    }

    // =========================================================================
    // 4. UTILITIES & SCROLLBAR CALCULATIONS
    // =========================================================================

    private void setCardVisibility(UltimateUIAPI api, Player player, int index, boolean visible) {
        api.setElementVisible(player, "card_border_" + index, visible);
        api.setElementVisible(player, "card_face_" + index, visible);
        api.setElementVisible(player, "card_bevel_top_" + index, visible);
        api.setElementVisible(player, "card_bevel_left_" + index, visible);
        api.setElementVisible(player, "card_band_" + index, visible);
        api.setElementVisible(player, "card_title_" + index, visible);
        api.setElementVisible(player, "card_sub_" + index, visible);
        api.setElementVisible(player, "card_status_" + index, visible);
        if (!visible) {
            api.setElementVisible(player, "card_indicator_" + index, false);
        }
    }

    private void updateScrollbar(UltimateUIAPI api, Player player, int sel, int total) {
        if (total <= 0) {
            api.setElementText(player, "text_cos_scroll_ind", "&8[&b00 &8/ &700&8]");
            api.setElementPosition(player, "scroll_thumb", 430.0, 155.0);
            return;
        }

        String indexStr = String.format("%02d", sel + 1);
        String totalStr = String.format("%02d", total);
        api.setElementText(player, "text_cos_scroll_ind", "&8[&#62c4ff" + indexStr + " &8/ &7" + totalStr + "&8]");

        double trackY = 155.0;
        double trackHeight = 755.0;
        double thumbHeight = Math.max(48.0, (6.0 / (double) Math.max(6, total)) * trackHeight);

        double progress = total > 1 ? (double) sel / (double) (total - 1) : 0.0;
        double thumbY = trackY + (progress * (trackHeight - thumbHeight));

        try {
            api.setElementPosition(player, "scroll_thumb", 430.0, thumbY);
        } catch (Throwable ignored) {}
    }

    private String getOreColorHex(String rarity) {
        if (rarity == null) return "2b3340";
        switch (rarity.toLowerCase()) {
            case "uncommon": return "1e4a2b"; // Emerald Ore
            case "rare":     return "1b3a5c"; // Diamond Ore
            case "epic":     return "3d225a"; // Amethyst Ore
            case "legendary":return "5a3d1b"; // Gold Ore
            case "mastery":  return "5c1922"; // Crimson Mastery Ore
            default:         return "2b3340";
        }
    }

    private int countItemsInRarity(String category, String rarity) {
        if ("teleport".equalsIgnoreCase(category)) {
            return WardrobeTeleportRegistry.getByRarity(rarity).size();
        }
        int c = 0;
        for (CosmeticTemplate t : DCCosmetics.getInstance().getTemplateRegistry().getAllTemplates()) {
            if (t.getEquipmentSlot().equalsIgnoreCase(category)
                    && t.getRarity() != null && t.getRarity().equalsIgnoreCase(rarity)
                    && manager.isCosmeticVisible(t)) {
                c++;
            }
        }
        return c;
    }

    private String rarityBadge(String rarity) {
        if (rarity == null) return "&7COMMON";
        switch (rarity.toLowerCase()) {
            case "uncommon": return "&aUNCOMMON";
            case "rare":     return "&9RARE";
            case "epic":     return "&5EPIC";
            case "legendary":return "&6LEGENDARY";
            case "mastery":  return "&cMASTERY";
            default:         return "&f" + rarity.toUpperCase();
        }
    }

    private String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}