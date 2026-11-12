package dc.dccosmetics.wardrobe.camera;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.tpeffects.TpEffectManager;
import dc.dccosmetics.tpeffects.model.TpEffectEntry;
import dc.dccosmetics.wardrobe.WardrobeSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Legacy compatibility facade delegating cinematic teleport sequences and Wardrobe previews
 * to the upgraded dc.dccosmetics.tpeffects.cinematic.CinematicTeleportEngine.
 */
public class CinematicTeleportController {

    private static CinematicTeleportController instance;

    private CinematicTeleportController() {}

    public static CinematicTeleportController getInstance() {
        if (instance == null) {
            instance = new CinematicTeleportController();
        }
        return instance;
    }

    public boolean isInSequence(Player player) {
        TpEffectManager mgr = getManager();
        return mgr != null && mgr.getCinematicEngine().isInSequence(player);
    }

    public static boolean isCinematicEffect(String effectId) {
        TpEffectManager mgr = getManager();
        if (mgr == null || effectId == null) return false;
        TpEffectEntry entry = mgr.getRegistry().getById(effectId);
        return entry != null && entry.isCinematic();
    }

    public void warmSkinCache(Player player) {
        TpEffectManager mgr = getManager();
        if (mgr != null) {
            mgr.getCinematicEngine().warmSkinCache(player);
        }
    }

    public void startTeleportSequence(Player player, Location fromLoc, Location toLoc, String effectId) {
        TpEffectManager mgr = getManager();
        if (mgr == null) return;
        TpEffectEntry entry = resolveCinematicEntry(mgr, effectId);
        if (entry != null) {
            mgr.getCinematicEngine().playTeleportSequence(player, fromLoc, toLoc, entry);
        }
    }

    public void playWardrobePreview(WardrobeSession session, String effectId) {
        TpEffectManager mgr = getManager();
        if (mgr == null || session == null) return;
        TpEffectEntry entry = resolveCinematicEntry(mgr, effectId);
        if (entry != null) {
            mgr.getCinematicEngine().playWardrobePreview(session, entry);
        }
    }

    public void cancelForPlayer(Player player) {
        if (player == null) return;
        TpEffectManager mgr = getManager();
        if (mgr != null) {
            mgr.getCinematicEngine().cancelSequence(player.getUniqueId(), false);
            mgr.getCinematicEngine().stopPreview(player);
        }
    }

    public void cancelSequence(java.util.UUID uuid, boolean teleportToFinal) {
        TpEffectManager mgr = getManager();
        if (mgr != null) {
            mgr.getCinematicEngine().cancelSequence(uuid, teleportToFinal);
        }
    }

    public void cleanupAll() {
        TpEffectManager mgr = getManager();
        if (mgr != null) {
            mgr.getCinematicEngine().cleanupAll();
        }
    }

    private static TpEffectManager getManager() {
        DCCosmetics plugin = DCCosmetics.getInstance();
        return plugin != null ? plugin.getTpEffectManager() : null;
    }

    private static TpEffectEntry resolveCinematicEntry(TpEffectManager mgr, String effectId) {
        if (effectId != null) {
            TpEffectEntry entry = mgr.getRegistry().getById(effectId);
            if (entry != null && entry.isCinematic()) {
                return entry;
            }
        }
        TpEffectEntry fallback = mgr.getRegistry().getById("obsidian_dissolution");
        if (fallback != null && fallback.isCinematic()) return fallback;
        for (TpEffectEntry candidate : mgr.getRegistry().getAll()) {
            if (candidate.isCinematic()) return candidate;
        }
        return null;
    }
}
