package dc.dccosmetics.gui;

import dc.dccosmetics.DCCosmetics;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GuiManager implements Listener {
    private final Map<UUID, GuiState> playerStates = new HashMap<>();

    public GuiManager() {
        DCCosmetics.getInstance().getServer().getPluginManager().registerEvents(this, DCCosmetics.getInstance());
    }

    public GuiState getState(Player player) {
        GuiState state = playerStates.get(player.getUniqueId());
        if (state == null || state.isExpired()) {
            state = new GuiState();
            playerStates.put(player.getUniqueId(), state);
        }
        state.touch();
        return state;
    }

    public void openCosmeticsMenu(Player player) {
        CosmeticsGUI gui = new CosmeticsGUI(player, getState(player));
        gui.open();
    }

    public void openProfileMenu(Player viewer, OfflinePlayer target) {
        ProfileGUI gui = new ProfileGUI(viewer, target);
        gui.open();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof CosmeticsGUI) {
            event.setCancelled(true); // Prevent picking up items
            if (event.getCurrentItem() == null) return;

            CosmeticsGUI gui = (CosmeticsGUI) event.getInventory().getHolder();
            gui.handleClick((Player) event.getWhoClicked(), event.getRawSlot(), event.getClick());
        } else if (event.getInventory().getHolder() instanceof ProfileGUI) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            
            ProfileGUI gui = (ProfileGUI) event.getInventory().getHolder();
            gui.handleClick((Player) event.getWhoClicked(), event.getRawSlot(), event.getClick());
        }
    }
}