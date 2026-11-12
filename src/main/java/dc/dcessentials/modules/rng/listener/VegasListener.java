package dc.dcessentials.modules.rng.listener;

import dc.dccosmetics.DCCosmetics;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

public class VegasListener implements Listener {

    public static void registerIfAvailable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vegas")) {
            return;
        }
        try {
            Class.forName("com.github.Shimado.vegasapi.api.VictoryUtil");
            VegasHookHandler.register();
        } catch (ClassNotFoundException e) {
            DCCosmetics.getInstance().getLogger().info("[DCEssentials] Vegas API classes not found; skipping Vegas hook.");
        } catch (Throwable t) {
            DCCosmetics.getInstance().getLogger().warning("[DCEssentials] Could not hook into Vegas: " + t.getMessage());
        }
    }
}
