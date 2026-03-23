package dc.dccosmetics.manager;

import dc.dccosmetics.DCCosmetics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CosmeticWatcher extends BukkitRunnable {
    private final Map<String, Long> fileTimestamps = new HashMap<>();
    private final DCCosmetics plugin;

    public CosmeticWatcher(DCCosmetics plugin) {
        this.plugin = plugin;
        scanInitially(new File(plugin.getDataFolder(), "cosmetics"));
    }

    private void scanInitially(File folder) {
        if (!folder.exists()) return;
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) scanInitially(file);
            else if (file.getName().endsWith(".yml")) {
                fileTimestamps.put(file.getAbsolutePath(), file.lastModified());
            }
        }
    }

    @Override
    public void run() {
        checkConfig(new File(plugin.getDataFolder(), "config.yml"));
        checkConfig(new File(plugin.getDataFolder(), "gui.yml"));
        checkFolder(new File(plugin.getDataFolder(), "cosmetics"));
    }

    private void checkConfig(File file) {
        if (!file.exists()) return;
        long lastModified = file.lastModified();
        Long previous = fileTimestamps.get(file.getAbsolutePath());
        if (previous == null || lastModified > previous) {
            fileTimestamps.put(file.getAbsolutePath(), lastModified);
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.reloadConfigs();
                broadcastAdmin("§a[DCCosmetics] Auto-Reloaded System Config: §e" + file.getName());
            });
        }
    }

    private void checkFolder(File folder) {
        if (!folder.exists()) return;
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                checkFolder(file);
            } else if (file.getName().endsWith(".yml")) {
                long lastModified = file.lastModified();
                Long previous = fileTimestamps.get(file.getAbsolutePath());

                // If file is new or was modified recently
                if (previous == null || lastModified > previous) {
                    fileTimestamps.put(file.getAbsolutePath(), lastModified);

                    // Jump back to main thread to safely process entity updates
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String error = plugin.getTemplateRegistry().loadTemplate(file);
                        if (error == null) {
                            broadcastAdmin("§a[DCCosmetics] Auto-Reloaded: §e" + file.getName());
                            plugin.getProfileManager().refreshAllOnlinePlayers();
                            plugin.getSculptManager().refreshAllDummies();
                        } else {
                            broadcastAdmin("§c[DCCosmetics] Auto-Reload Failed: §e" + file.getName());
                            broadcastAdmin("§7" + error);
                        }
                    });
                }
            }
        }
    }

    private void broadcastAdmin(String message) {
        plugin.getLogger().info(ChatColor.stripColor(message));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("dccosmetics.admin")) {
                player.sendMessage(message);
            }
        }
    }
}