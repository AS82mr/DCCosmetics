package dc.dccosmetics.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.List;

/**
 * The bridge to our version-specific NMS logic.
 */
public interface PacketAdapter {

    /**
     * Injects the packet interceptor into the player's connection pipeline.
     */
    void injectPlayer(Player player);

    /**
     * Removes the packet interceptor.
     */
    void removePlayer(Player player);

    /**
     * Creates a new BlockDisplay (holographic panel) packet wrapper.
     * @param viewers The players who can see this packet.
     * @param location The initial spawn location.
     * @return The wrapper manipulating the display entity.
     */
    DisplayWrapper createBlockDisplay(List<Player> viewers, Location location);
}