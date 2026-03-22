package dc.dccosmetics.listener;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.api.DisplayWrapper;
import dc.dccosmetics.model.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.joml.Vector3f;

import java.util.List;

public class PlayerListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Load their saved cosmetics from the files!
        // (This includes our 2-second crash-loop protection delay)
        DCCosmetics.getInstance().getProfileManager().loadProfile(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Save their equipped cosmetics and despawn the holograms
        DCCosmetics.getInstance().getProfileManager().unloadProfile(event.getPlayer());

        // Cleanup the tracking map to prevent memory leaks
        dc.dccosmetics.nms.ProtocolLibAdapter.playerCosmeticPassengers.remove(event.getPlayer().getEntityId());
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        DCCosmetics.getInstance().getProfileManager().unloadProfile(player);
        DCCosmetics.getInstance().getProfileManager().loadProfile(player);
    }

    private final DCCosmetics plugin = DCCosmetics.getInstance();

    public PlayerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}