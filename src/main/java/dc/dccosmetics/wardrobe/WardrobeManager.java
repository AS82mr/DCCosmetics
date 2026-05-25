package dc.dccosmetics.wardrobe;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.wardrobe.camera.CameraController;
import dc.dccosmetics.wardrobe.camera.WardrobeCinematic;
import dc.dccosmetics.wardrobe.npc.WardrobeMannequin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WardrobeManager {

    private final WardrobeRoom room;
    private final CameraController cameraController;
    private final Map<UUID, WardrobeSession> activeSessions = new HashMap<>();

    public WardrobeManager(WardrobeRoom room) {
        this.room = room;
        this.cameraController = new CameraController();
    }

    public WardrobeRoom getRoom() {
        return room;
    }

    public CameraController getCameraController() {
        return cameraController;
    }

    public WardrobeSession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    public java.util.Collection<WardrobeSession> getActiveSessions() {
        return activeSessions.values();
    }

    public boolean isInWardrobe(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public void enterWardrobe(Player player) {
        if (isInWardrobe(player)) {
            player.sendMessage(ChatColor.RED + "You are already in the wardrobe!");
            return;
        }

        if (room.getSpawn() == null || room.getNpcStand() == null) {
            player.sendMessage(ChatColor.RED + "The wardrobe room has not been properly configured yet.");
            return;
        }

        WardrobeSession session = new WardrobeSession(player, player.getLocation());
        activeSessions.put(player.getUniqueId(), session);

        // Hide TAB scoreboard
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab scoreboard off " + player.getName());

        // Start Cinematic (Which will then spawn HUD and Mannequin at the end)
        WardrobeCinematic cinematic = new WardrobeCinematic(this, session);
        cinematic.playIntro();
    }

    public void exitWardrobe(Player player) {
        WardrobeSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            session.cleanup();
            cameraController.unlockCamera(player);

            // Show TAB scoreboard
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab scoreboard on " + player.getName());

            Location exitLoc = room.isReturnToPrevious() ? session.getPreviousLocation() : room.getFallbackExit();
            if (exitLoc != null) {
                player.teleport(exitLoc);
            }
            player.setGameMode(session.getPreviousGameMode());
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
            player.sendMessage(ChatColor.GREEN + "Exited the wardrobe.");
        }
    }
    
    public void cleanupAll() {
        for (WardrobeSession session : activeSessions.values()) {
            session.cleanup();
            cameraController.unlockCamera(session.getPlayer());
        }
        activeSessions.clear();
    }

    // ─────────────────────────────────────────────────────────────
    //  BACK NAVIGATION (called by both listener and packet listener)
    // ─────────────────────────────────────────────────────────────

    public void handleBackNavigation(Player player) {
        WardrobeSession session = getSession(player);
        if (session == null || session.isInCinematic() || session.getHud() == null) return;

        // Cyberpunk: digital step-back / power-off tone
        player.playSound(player, org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.5f);

        switch (session.getCurrentLevel()) {
            case COSMETICS:
                // If a preview is active, deselect it first; otherwise go back to RARITIES
                if (session.getPreviewingCosmeticId() != null) {
                    session.setPreviewingCosmeticId(null);
                    if (session.getMannequin() != null) session.getMannequin().clearPreview();
                    session.getHud().renderAll();
                } else {
                    session.setCurrentLevel(WardrobeSession.MenuLevel.RARITIES);
                    session.setSelectedIndex(0);
                    session.setScrollWindowOffset(0);
                    session.getHud().renderAll();
                }
                break;

            case RARITIES:
                session.setCurrentLevel(WardrobeSession.MenuLevel.CATEGORIES);
                session.setSelectedIndex(0);
                session.setScrollWindowOffset(0);
                zoomToSpawn(player, session);
                break;

            case CATEGORIES:
                // Already at root — do nothing (shift-sneak to exit)
                break;

            default:
                break;
        }
    }

    private void zoomToSpawn(Player player, WardrobeSession session) {
        try {
            Location spawn    = room.getSpawn();
            Location mannequin = room.getNpcStand();
            if (spawn == null || mannequin == null) return;
            session.getCameraController().moveCamera(player, spawn, session, mannequin);
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[Wardrobe] Camera back failed: " + e.getMessage());
        }
    }
}

