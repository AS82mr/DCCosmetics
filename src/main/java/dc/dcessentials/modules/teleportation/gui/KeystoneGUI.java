package dc.dcessentials.modules.teleportation.gui;

import org.bukkit.entity.Player;

/**
 * Facade delegating to {@link KeystoneMenu} (Task P2-02).
 */
public class KeystoneGUI extends KeystoneMenu.MenuListener {

    public static void open(Player player) {
        KeystoneMenu.open(player);
    }
}
