package dc.dccosmetics.manager;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;

import java.io.File;

public class LanguageManager {
    private static final String DEFAULT_PREFIX = DCMessage.PREFIX_GENERAL;
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
        String rawPrefix = langConfig.getString("prefix", DEFAULT_PREFIX);
        if (rawPrefix == null || rawPrefix.contains("DCCosmetics")) {
            rawPrefix = DEFAULT_PREFIX;
        }
        prefix = ColorUtil.colorize(rawPrefix);
    }

    public String getMessage(String key) {
        String msg = langConfig.getString("messages." + key);
        if (msg == null || msg.isEmpty()) return "";
        return ColorUtil.colorize(prefix + msg);
    }

    public void sendMessage(CommandSender sender, String key) {
        String msg = getMessage(key);
        if (!msg.isEmpty()) {
            sender.sendMessage(msg);
        }
    }
}