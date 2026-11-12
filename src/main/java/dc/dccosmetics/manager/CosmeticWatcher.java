package dc.dccosmetics.manager;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
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
        // Pre-populate all timestamps on startup to prevent false reload alerts on boot
        recordInitialFile(new File(plugin.getDataFolder(), "config.yml"));
        recordInitialFile(new File(plugin.getDataFolder(), "events.yml"));
        recordInitialFile(new File(plugin.getDataFolder(), "profile.yml"));
        recordInitialFile(new File(plugin.getDataFolder(), "customise.yml"));
        recordInitialFile(new File(plugin.getDataFolder(), "gui.yml"));
        recordInitialFile(new File(plugin.getDataFolder(), "items.yml"));
        recordInitialFile(new File(plugin.getDataFolder(), "scrolls.yml"));
        recordInitialFile(dc.dccosmetics.wardrobe.WardrobeManager.getWardrobeConfigFile());
        recordInitialFolder(new File(plugin.getDataFolder(), "npc"));
        recordInitialFolder(new File(plugin.getDataFolder(), "npcs"));
        recordInitialFolder(new File(plugin.getDataFolder(), "wardrobe"));
        recordInitialFolder(new File(plugin.getDataFolder(), "cosmetics"));
    }

    private void recordInitialFile(File file) {
        if (file != null && file.exists()) {
            fileTimestamps.put(file.getAbsolutePath(), file.lastModified());
        }
    }

    private void recordInitialFolder(File folder) {
        if (folder == null || !folder.exists()) return;
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                recordInitialFolder(file);
            } else if (file.getName().endsWith(".yml")) {
                fileTimestamps.put(file.getAbsolutePath(), file.lastModified());
            }
        }
    }

    @Override
    public void run() {
        checkConfig(new File(plugin.getDataFolder(), "config.yml"));
        checkConfig(new File(plugin.getDataFolder(), "events.yml"));
        checkConfig(new File(plugin.getDataFolder(), "profile.yml"));
        checkConfig(new File(plugin.getDataFolder(), "customise.yml"));
        checkConfig(new File(plugin.getDataFolder(), "gui.yml"));
        checkConfig(new File(plugin.getDataFolder(), "items.yml"));
        checkConfig(new File(plugin.getDataFolder(), "scrolls.yml"));
        checkWardrobeConfig(dc.dccosmetics.wardrobe.WardrobeManager.getWardrobeConfigFile());
        checkNpcFolder(new File(plugin.getDataFolder(), "npc"));
        checkNpcFolder(new File(plugin.getDataFolder(), "npcs"));
        checkCosmeticsFolder(new File(plugin.getDataFolder(), "cosmetics"));
    }

    private void checkWardrobeConfig(File file) {
        if (!file.exists()) return;
        long lastModified = file.lastModified();
        Long previous = fileTimestamps.get(file.getAbsolutePath());
        if (previous != null && lastModified > previous) {
            fileTimestamps.put(file.getAbsolutePath(), lastModified);
            Bukkit.getScheduler().runTask(plugin, () -> {
                dc.dccosmetics.wardrobe.hud.HudConfig.get().load();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    dc.dccosmetics.wardrobe.WardrobeSession session = plugin.getWardrobeManager().getSession(player);
                    if (session != null && session.getHud() != null) {
                        session.getHud().renderAll();
                    }
                }
                broadcastAdmin(DCMessage.info("Auto-reloaded wardrobe config: &#FFFFFF" + file.getName()));
            });
        }
    }

    private void checkConfig(File file) {
        if (!file.exists()) return;
        long lastModified = file.lastModified();
        Long previous = fileTimestamps.get(file.getAbsolutePath());
        if (previous != null && lastModified > previous) {
            fileTimestamps.put(file.getAbsolutePath(), lastModified);
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.reloadConfigs();
                broadcastAdmin(DCMessage.info("Auto-reloaded configuration: &#FFFFFF" + file.getName()));
            });
        }
    }

    private void checkNpcFolder(File folder) {
        if (folder == null || !folder.exists()) return;
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                checkNpcFolder(file);
            } else if (file.getName().endsWith(".yml")) {
                long lastModified = file.lastModified();
                Long previous = fileTimestamps.get(file.getAbsolutePath());

                if (previous != null && lastModified > previous) {
                    fileTimestamps.put(file.getAbsolutePath(), lastModified);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.reloadConfigs();
                        broadcastAdmin(DCMessage.info("Auto-reloaded NPC config: &#FFFFFF" + file.getName()));
                    });
                }
            }
        }
    }

    private void checkCosmeticsFolder(File folder) {
        if (folder == null || !folder.exists()) return;
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                checkCosmeticsFolder(file);
            } else if (file.getName().endsWith(".yml")) {
                long lastModified = file.lastModified();
                Long previous = fileTimestamps.get(file.getAbsolutePath());

                if (previous != null && lastModified > previous) {
                    fileTimestamps.put(file.getAbsolutePath(), lastModified);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String error = plugin.getTemplateRegistry().loadTemplate(file);
                        if (error == null) {
                            broadcastAdmin(DCMessage.info("Auto-reloaded cosmetic: &#FFFFFF" + file.getName()));
                            plugin.getProfileManager().refreshAllOnlinePlayers();
                            plugin.getSculptManager().refreshAllDummies();
                            if (plugin.getItemModule() != null && plugin.getItemModule().getItemRegistry() != null) {
                                plugin.getItemModule().getItemRegistry().syncAllCosmetics();
                            }
                        } else {
                            broadcastAdmin(DCMessage.error("Auto-reload failed for &#FFFFFF" + file.getName() + "&#FCA5A5: " + error));
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
