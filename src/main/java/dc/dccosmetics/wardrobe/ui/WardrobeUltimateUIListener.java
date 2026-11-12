package dc.dccosmetics.wardrobe.ui;

import dc.dccosmetics.wardrobe.WardrobeManager;
import dev.xqedii.ultimateUI.api.event.UltimateUIKeyEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class WardrobeUltimateUIListener implements Listener {

    private final WardrobeManager manager;
    private final WardrobeUltimateUIManager uiManager;

    public WardrobeUltimateUIListener(WardrobeManager manager, WardrobeUltimateUIManager uiManager) {
        this.manager = manager;
        this.uiManager = uiManager;
    }

    @EventHandler
    public void onKey(UltimateUIKeyEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInWardrobe(player)) return;

        String key = event.getKey();
        if ("ESCAPE".equalsIgnoreCase(key)) {
            event.setCancelled(true);
            manager.exitWardrobe(player);
        }
    }
}
