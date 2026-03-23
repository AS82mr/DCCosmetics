package dc.dccosmetics.manager;

import dc.dccosmetics.DCCosmetics;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

import java.io.File;

public class LanguageManager {
    private final DCCosmetics plugin = DCCosmetics.getInstance();
    private YamlConfiguration langConfig;
    private String prefix;

    public void load() {
        File dir = new File(plugin.getDataFolder(), "lang");
        if (!dir.exists()) dir.mkdirs();
        File langFile = new File(dir, "langENG.yml");
        if (!langFile.exists()) {
            plugin.saveResource("lang/langENG.yml", false);
        }
        langConfig = YamlConfiguration.loadConfiguration(langFile);
        prefix = plugin.parseColors(langConfig.getString("prefix", "&#38bdf8&lDCCosmetics &8»&r "));
    }

    public String getMessage(String key) {
        String msg = langConfig.getString("messages." + key);
        if (msg == null || msg.isEmpty()) return "";
        return plugin.parseColors(prefix + msg);
    }

    public void sendMessage(CommandSender sender, String key) {
        String msg = getMessage(key);
        if (!msg.isEmpty()) {
            sender.sendMessage(msg);
        }
    }
}