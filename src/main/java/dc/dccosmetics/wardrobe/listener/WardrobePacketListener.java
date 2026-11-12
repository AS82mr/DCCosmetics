package dc.dccosmetics.wardrobe.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.CosmeticTemplate;
import dc.dccosmetics.wardrobe.WardrobeManager;
import dc.dccosmetics.wardrobe.WardrobeSession;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class WardrobePacketListener {

    private final WardrobeManager manager;

    public WardrobePacketListener(WardrobeManager manager) {
        this.manager = manager;
    }

    public void register() {
        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(DCCosmetics.getInstance(), PacketType.Play.Client.USE_ENTITY) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    Player player = event.getPlayer();
                    if (!manager.isInWardrobe(player)) return;

                    event.setCancelled(true);

                    boolean isAttack = false;
                    try {
                        var actions = event.getPacket().getEntityUseActions();
                        if (actions != null && actions.size() > 0) {
                            var act = actions.readSafely(0);
                            if (act == com.comphenix.protocol.wrappers.EnumWrappers.EntityUseAction.ATTACK) {
                                isAttack = true;
                            }
                        }
                    } catch (Throwable ignored) {}

                    if (!isAttack) {
                        Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
                            WardrobeSession session = manager.getSession(player);
                            if (session != null && !session.isInCinematic() && (session.getHud() != null || manager.getWardrobeVersion() == 2)) {
                                manager.handleBackNavigation(player);
                            }
                        });
                    }
                }
            }
        );

        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(DCCosmetics.getInstance(), PacketType.Play.Client.USE_ITEM) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    Player player = event.getPlayer();
                    if (!manager.isInWardrobe(player)) return;

                    event.setCancelled(true);
                    Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
                        WardrobeSession session = manager.getSession(player);
                        if (session != null && !session.isInCinematic() && (session.getHud() != null || manager.getWardrobeVersion() == 2)) {
                            manager.handleBackNavigation(player);
                        }
                    });
                }
            }
        );

        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(DCCosmetics.getInstance(), PacketType.Play.Client.USE_ITEM_ON) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    Player player = event.getPlayer();
                    if (!manager.isInWardrobe(player)) return;

                    event.setCancelled(true);
                    Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
                        WardrobeSession session = manager.getSession(player);
                        if (session != null && !session.isInCinematic() && (session.getHud() != null || manager.getWardrobeVersion() == 2)) {
                            manager.handleBackNavigation(player);
                        }
                    });
                }
            }
        );

        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(DCCosmetics.getInstance(), PacketType.Play.Client.ARM_ANIMATION) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    Player player = event.getPlayer();
                    if (!manager.isInWardrobe(player)) return;

                    WardrobeSession session = manager.getSession(player);
                    if (session == null || (session.getHud() == null && manager.getWardrobeVersion() != 2)) return;
                    if (session.isInCinematic()) return;

                    event.setCancelled(true);

                    DCCosmetics.getInstance().getServer().getScheduler().runTask(
                        DCCosmetics.getInstance(),
                        () -> handleConfirm(player, session)
                    );
                }
            }
        );

        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(
                DCCosmetics.getInstance(),
                PacketType.Play.Server.SPAWN_ENTITY,
                PacketType.Play.Server.ENTITY_METADATA,
                PacketType.Play.Server.ENTITY_EQUIPMENT,
                PacketType.Play.Server.ENTITY_TELEPORT,
                PacketType.Play.Server.REL_ENTITY_MOVE,
                PacketType.Play.Server.REL_ENTITY_MOVE_LOOK,
                PacketType.Play.Server.ENTITY_LOOK,
                PacketType.Play.Server.ENTITY_HEAD_ROTATION,
                PacketType.Play.Server.ENTITY_VELOCITY,
                PacketType.Play.Server.ANIMATION
            ) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    if (event.isPlayerTemporary()) return;
                    Player viewer = event.getPlayer();
                    if (viewer == null) return;
                    int entityId = event.getPacket().getIntegers().readSafely(0);
                    if (manager.isPrivateEntityHiddenFrom(entityId, viewer.getUniqueId())) {
                        event.setCancelled(true);
                    }
                }
            }
        );
    }

    private void handleConfirm(Player player, WardrobeSession session) {
        if (manager.getWardrobeVersion() == 2) {
            handleConfirmV2(player, session);
            return;
        }

        switch (session.getCurrentLevel()) {
            case CATEGORIES: {
                if (session.getCategoriesList().isEmpty()) return;
                String selectedCategory = session.getCategoriesList().get(session.getSelectedIndex());
                session.setSelectedCategory(selectedCategory);

                List<CosmeticTemplate> allForCategory = new ArrayList<>();
                for (CosmeticTemplate t : DCCosmetics.getInstance().getTemplateRegistry().getAllTemplates()) {
                    if (t.getEquipmentSlot().equalsIgnoreCase(selectedCategory) && manager.isCosmeticVisible(t)) {
                        allForCategory.add(t);
                    }
                }

                LinkedHashSet<String> rarityOrder = new LinkedHashSet<>();
                for (String r : new String[]{"uncommon", "rare", "epic", "legendary", "mastery"}) {
                    for (CosmeticTemplate t : allForCategory) {
                        if (t.getRarity() != null && t.getRarity().equalsIgnoreCase(r)) {
                            rarityOrder.add(cap(r));
                            break;
                        }
                    }
                }
                for (CosmeticTemplate t : allForCategory) {
                    if (t.getRarity() != null) rarityOrder.add(cap(t.getRarity()));
                }

                session.setCurrentRaritiesList(new ArrayList<>(rarityOrder));
                session.setCurrentLevel(WardrobeSession.MenuLevel.RARITIES);
                session.setSelectedIndex(0);
                session.setScrollWindowOffset(0);

                zoomCamera(player, session, selectedCategory);
                player.playSound(player, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, 2.0f);
                break;
            }

            case RARITIES: {
                if (session.getCurrentRaritiesList().isEmpty()) return;
                String selectedRarity = session.getCurrentRaritiesList().get(session.getSelectedIndex());
                session.setSelectedRarity(selectedRarity);

                List<CosmeticTemplate> filtered = new ArrayList<>();
                for (CosmeticTemplate t : DCCosmetics.getInstance().getTemplateRegistry().getAllTemplates()) {
                    if (t.getEquipmentSlot().equalsIgnoreCase(session.getSelectedCategory())
                            && t.getRarity() != null && t.getRarity().equalsIgnoreCase(selectedRarity)
                            && manager.isCosmeticVisible(t)) {
                        filtered.add(t);
                    }
                }
                session.setCurrentCosmeticsList(filtered);
                session.setCurrentLevel(WardrobeSession.MenuLevel.COSMETICS);
                session.setSelectedIndex(0);
                session.setScrollWindowOffset(0);
                session.getHud().renderAll();

                player.playSound(player, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f);
                break;
            }

            case COSMETICS: {
                if (session.getCurrentCosmeticsList().isEmpty()) return;
                CosmeticTemplate selectedCosmetic = session.getCurrentCosmeticsList().get(session.getSelectedIndex());

                if (selectedCosmetic.getId().equals(session.getPreviewingCosmeticId())) {
                    session.setPreviewingCosmeticId(null);
                    if (session.getMannequin() != null) {
                        session.getMannequin().clearPreview();
                    }
                    player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.7f);
                } else {
                    session.setPreviewingCosmeticId(selectedCosmetic.getId());
                    if (session.getMannequin() != null) {
                        try {
                            session.getMannequin().previewCosmetic(selectedCosmetic);
                        } catch (Exception e) {
                            DCCosmetics.getInstance().getLogger().warning(
                                "[Wardrobe] Preview failed for " + selectedCosmetic.getId() + ": " + e.getMessage());
                        }
                    }
                    player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 1.0f, 2.0f);
                }

                session.getHud().renderAll();
                break;
            }

            case ACTION:
                break;
        }
    }

    private void handleConfirmV2(Player player, WardrobeSession session) {
        if (session.getCurrentLevel() == WardrobeSession.MenuLevel.CATEGORIES) {
            if (session.getCategoriesList().isEmpty()) return;
            int sel = session.getSelectedIndex();
            if (sel >= session.getCategoriesList().size()) sel = 0;
            String selectedCategory = session.getCategoriesList().get(sel);
            String cat = selectedCategory.toLowerCase();

            session.setSelectedCategory(cat);
            zoomCamera(player, session, cat);

            manager.getUltimateUIManager().openRarities(player, session);
            player.playSound(player, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, 2.0f);

        } else if (session.getCurrentLevel() == WardrobeSession.MenuLevel.RARITIES) {
            if (session.getCurrentRaritiesList() == null || session.getCurrentRaritiesList().isEmpty()) return;
            int sel = session.getSelectedIndex();
            if (sel >= session.getCurrentRaritiesList().size()) sel = 0;
            String selectedRarity = session.getCurrentRaritiesList().get(sel);

            session.setSelectedRarity(selectedRarity);

            String activeCat = session.getSelectedCategory().toLowerCase();
            zoomCamera(player, session, activeCat);

            manager.getUltimateUIManager().openCosmetics(player, session);

            if ("teleport".equalsIgnoreCase(activeCat)) {
                // Only highlight first card cleanly; do not auto-play until the player left-clicks
                session.setPreviewingTeleportId(null);
            } else {
                if (session.getMannequin() != null && session.getCurrentCosmeticsList() != null && !session.getCurrentCosmeticsList().isEmpty()) {
                    session.getMannequin().previewCosmetic(session.getCurrentCosmeticsList().get(0));
                }
            }

            player.playSound(player, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f);

        } else if (session.getCurrentLevel() == WardrobeSession.MenuLevel.COSMETICS) {
            String activeCat = session.getSelectedCategory().toLowerCase();

            if ("teleport".equalsIgnoreCase(activeCat)) {
                handleTeleportClickV2(player, session);
            } else {
                int sel = session.getSelectedIndex();
                if (session.getCurrentCosmeticsList() == null || sel >= session.getCurrentCosmeticsList().size()) return;
                CosmeticTemplate t = session.getCurrentCosmeticsList().get(sel);

                if (session.getMannequin() != null) {
                    session.getMannequin().previewCosmetic(t);
                }
                player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.8f);
            }

            if (!session.isInCinematic() && !session.isInTpPreview()) {
                manager.getUltimateUIManager().renderCosmetics(player, session);
            }
        }
    }

    private void handleTeleportClickV2(Player player, WardrobeSession session) {
        int sel = session.getSelectedIndex();
        if (session.getCurrentTeleportList() == null || sel >= session.getCurrentTeleportList().size()) return;
        dc.dccosmetics.wardrobe.teleport.WardrobeTeleportEffect eff = session.getCurrentTeleportList().get(sel);

        dc.dccosmetics.tpeffects.TpEffectManager tpMgr = DCCosmetics.getInstance().getTpEffectManager();
        if (tpMgr == null || eff == null) return;

        dc.dccosmetics.tpeffects.model.TpEffectEntry entry = eff.getEntry() != null
                ? eff.getEntry()
                : tpMgr.getRegistry().getById(eff.getId());
        if (entry == null) return;

        boolean twoStep = tpMgr.getConfig().isWardrobeClickToPreviewThenEquip();
        boolean isAlreadyPreviewing = eff.getId().equalsIgnoreCase(session.getPreviewingTeleportId());

        if (!twoStep || !isAlreadyPreviewing) {
            // 1st Left-Click: Play preview animation and mark as staged for equip on next click
            session.setPreviewingTeleportId(eff.getId());
            tpMgr.previewInWardrobe(session, entry);
            player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 0.85f, 1.75f);
            return;
        }

        // 2nd Left-Click on already-previewed item: strictly verify ownership before equipping
        boolean owned = dc.dccosmetics.tpeffects.TpEffectRegistry.hasPermission(player, entry);
        if (!owned) {
            player.playSound(player, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.1f);
            dc.dcessentials.core.util.DCMessage.sendError(player,
                    "You do not own " + entry.getRawDisplayName() + " &c(unlock required to equip).");
            return;
        }

        String currentEquipped = tpMgr.getSelectedEffectId(player);
        if (entry.getId().equalsIgnoreCase(currentEquipped)) {
            tpMgr.clearSelectedEffect(player);
            player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.75f);
            dc.dcessentials.core.util.DCMessage.sendWardrobe(player,
                    "Unequipped teleport effect: " + entry.getRawDisplayName());
        } else {
            tpMgr.setSelectedEffect(player, entry);
            player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.8f);
            dc.dcessentials.core.util.DCMessage.sendWardrobe(player,
                    "Equipped teleport effect: " + entry.getRawDisplayName());
        }
        session.setPreviewingTeleportId(null);
    }

    private void zoomCamera(Player player, WardrobeSession session, String category) {
        try {
            org.bukkit.Location camLoc = manager.getRoom().getCamera(category);
            if (camLoc != null) {
                session.getCameraController().moveCamera(player, camLoc, session);
            }
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[Wardrobe] Camera zoom failed: " + e.getMessage());
        }
    }

    private String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}