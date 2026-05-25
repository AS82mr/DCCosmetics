package dc.dccosmetics.wardrobe.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.ActiveCosmetic;
import dc.dccosmetics.model.CosmeticTemplate;
import dc.dccosmetics.wardrobe.WardrobeManager;
import dc.dccosmetics.wardrobe.WardrobeSession;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Packet-level input handling for the Wardrobe UI.
 *
 * LEFT-CLICK  → ARM_ANIMATION → confirm / select
 * RIGHT-CLICK → USE_ITEM (air right-click) is intercepted here for reliable back-nav.
 *               USE_ENTITY is also cancelled here to prevent the Citizens NPC kick.
 *               Both call manager.handleBackNavigation(player).
 */
public class WardrobePacketListener {

    private final WardrobeManager manager;

    public WardrobePacketListener(WardrobeManager manager) {
        this.manager = manager;
    }

    public void register() {
        // ── USE_ENTITY ────────────────────────────────────────────────────────
        // Must be cancelled at packet level to prevent "Cannot interact with self!"
        // (Citizens PLAYER-type NPC triggers the NMS entity == player check).
        //
        // action = 0 (INTERACT) or 2 (INTERACT_AT) → right-click on entity → back nav
        // action = 1 (ATTACK) → left-click on entity → ARM_ANIMATION handles it
        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(DCCosmetics.getInstance(), PacketType.Play.Client.USE_ENTITY) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    Player player = event.getPlayer();
                    if (!manager.isInWardrobe(player)) return;

                    event.setCancelled(true); // always — prevents kick

                    int action = event.getPacket().getIntegers().readSafely(1);
                    if (action == 0 || action == 2) { // right-click on entity
                        WardrobeSession session = manager.getSession(player);
                        if (session != null && !session.isInCinematic() && session.getHud() != null) {
                            manager.handleBackNavigation(player);
                        }
                    }
                }
            }
        );

        // ── USE_ITEM: right-click air with empty hand → back navigation ───────
        // When camera is locked to Marker and player right-clicks open air with nothing
        // in their hand, USE_ENTITY does NOT fire (no entity was targeted). We intercept
        // USE_ITEM here instead. We do NOT cancel it generically — only when the player
        // is in the wardrobe and has nothing in the relevant hand, so normal interactions
        // outside the wardrobe are completely untouched.
        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(DCCosmetics.getInstance(), PacketType.Play.Client.USE_ITEM) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    Player player = event.getPlayer();
                    if (!manager.isInWardrobe(player)) return;

                    WardrobeSession session = manager.getSession(player);
                    if (session == null || session.isInCinematic() || session.getHud() == null) return;

                    event.setCancelled(true);
                    manager.handleBackNavigation(player);
                }
            }
        );

        // ── ARM_ANIMATION: left-click → confirm / select ─────────────────────
        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(DCCosmetics.getInstance(), PacketType.Play.Client.ARM_ANIMATION) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    Player player = event.getPlayer();
                    if (!manager.isInWardrobe(player)) return;

                    WardrobeSession session = manager.getSession(player);
                    if (session == null || session.getHud() == null) return;
                    if (session.isInCinematic()) return;

                    event.setCancelled(true);

                    DCCosmetics.getInstance().getServer().getScheduler().runTask(
                        DCCosmetics.getInstance(),
                        () -> handleConfirm(player, session)
                    );
                }
            }
        );
    }

    // ─────────────────────────────────────────────────────────────
    //  CONFIRM (LEFT CLICK)
    // ─────────────────────────────────────────────────────────────

    private void handleConfirm(Player player, WardrobeSession session) {
        switch (session.getCurrentLevel()) {

            // ── 1. Slot selected → go to RARITIES ────────────────
            case CATEGORIES: {
                if (session.getCategoriesList().isEmpty()) return;
                String selectedCategory = session.getCategoriesList().get(session.getSelectedIndex());
                session.setSelectedCategory(selectedCategory);

                // Collect rarities for this category (in priority order)
                List<CosmeticTemplate> allForCategory = new ArrayList<>();
                for (CosmeticTemplate t : DCCosmetics.getInstance().getTemplateRegistry().getAllTemplates()) {
                    if (t.getEquipmentSlot().equalsIgnoreCase(selectedCategory)) {
                        allForCategory.add(t);
                    }
                }

                LinkedHashSet<String> rarityOrder = new LinkedHashSet<>();
                for (String r : new String[]{"uncommon", "rare", "epic", "legendary"}) {
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

                // Zoom camera to the selected slot and reposition HUD
                zoomCamera(player, session, selectedCategory);

                // Cyberpunk: sharp electronic slot-select click
                player.playSound(player, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, 2.0f);
                break;
            }

            // ── 2. Rarity selected → go to COSMETICS ─────────────
            case RARITIES: {
                if (session.getCurrentRaritiesList().isEmpty()) return;
                String selectedRarity = session.getCurrentRaritiesList().get(session.getSelectedIndex());
                session.setSelectedRarity(selectedRarity);

                List<CosmeticTemplate> filtered = new ArrayList<>();
                for (CosmeticTemplate t : DCCosmetics.getInstance().getTemplateRegistry().getAllTemplates()) {
                    if (t.getEquipmentSlot().equalsIgnoreCase(session.getSelectedCategory())
                            && t.getRarity() != null && t.getRarity().equalsIgnoreCase(selectedRarity)) {
                        filtered.add(t);
                    }
                }
                session.setCurrentCosmeticsList(filtered);
                session.setCurrentLevel(WardrobeSession.MenuLevel.COSMETICS);
                session.setSelectedIndex(0);
                session.setScrollWindowOffset(0);
                session.getHud().renderAll();

                // Cyberpunk: beacon power-up tone at high pitch
                player.playSound(player, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f);
                break;
            }

            // ── 3. Cosmetic selected → preview on mannequin ───────
            case COSMETICS: {
                if (session.getCurrentCosmeticsList().isEmpty()) return;
                CosmeticTemplate selectedCosmetic = session.getCurrentCosmeticsList().get(session.getSelectedIndex());

                // Toggle preview: clicking the already-previewing item deselects it
                if (selectedCosmetic.getId().equals(session.getPreviewingCosmeticId())) {
                    session.setPreviewingCosmeticId(null);
                    if (session.getMannequin() != null) {
                        session.getMannequin().clearPreview();
                    }
                    // Cyberpunk: deselect / power-down tone
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
                    // Cyberpunk: high-frequency item lock-on sound
                    player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 1.0f, 2.0f);
                }

                session.getHud().renderAll();
                break;
            }

            case ACTION: {
                break;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  CAMERA ZOOM
    // ─────────────────────────────────────────────────────────────

    private void zoomCamera(Player player, WardrobeSession session, String category) {
        try {
            org.bukkit.Location root = manager.getRoom().getSpawn();
            org.bukkit.Location npc  = manager.getRoom().getNpcStand();
            if (root == null || npc == null) return;

            org.bukkit.util.Vector dir = npc.toVector().subtract(root.toVector()).normalize();
            org.bukkit.Location zoomLoc = npc.clone().subtract(dir.clone().multiply(2.0));
            zoomLoc.setDirection(dir);

            switch (category.toLowerCase()) {
                case "head":  zoomLoc.add(0,  0.8, 0); break;
                case "chest": zoomLoc.add(0,  0.2, 0); break;
                case "waist": zoomLoc.subtract(0, 0.5, 0); break;
                case "boots": zoomLoc.subtract(0, 1.2, 0); break;
                case "sword": zoomLoc.add(0, 0.2, 0).add(dir.clone().crossProduct(new org.bukkit.util.Vector(0, 1, 0)).multiply(0.5)); break;
            }

            session.getCameraController().moveCamera(player, zoomLoc, session, npc);
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[Wardrobe] Camera zoom failed: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  UTILITIES
    // ─────────────────────────────────────────────────────────────

    private String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
