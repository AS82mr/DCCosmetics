package dc.dcessentials.modules.items.rarity;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.modules.items.model.ItemRarity;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

public class RarityConfigManager {

    public static class RarityFormat {
        private final String nameColor;
        private final String bracketColor;
        private final String emptyBracketColor;
        private final String bracketLeft;
        private final String bracketRight;
        private final String tag;
        private final String divider;
        private final String tooltipStyle;
        private final List<String> header;
        private final List<String> footer;

        public RarityFormat(String nameColor, String bracketColor, String emptyBracketColor,
                            String bracketLeft, String bracketRight,
                            String tag, String divider, String tooltipStyle,
                            List<String> header, List<String> footer) {
            this.nameColor = nameColor;
            this.bracketColor = bracketColor;
            this.emptyBracketColor = emptyBracketColor != null ? emptyBracketColor : "&8";
            this.bracketLeft = bracketLeft != null ? bracketLeft : "[";
            this.bracketRight = bracketRight != null ? bracketRight : "]";
            this.tag = tag;
            this.divider = divider;
            this.tooltipStyle = tooltipStyle;
            this.header = header != null ? header : Collections.emptyList();
            this.footer = footer != null ? footer : Collections.emptyList();
        }

        public String getNameColor() { return nameColor; }
        public String getBracketColor() { return bracketColor; }
        public String getEmptyBracketColor() { return emptyBracketColor; }
        public String getBracketLeft() { return bracketLeft; }
        public String getBracketRight() { return bracketRight; }
        public String getTag() { return tag; }
        public String getDivider() { return divider; }
        public String getTooltipStyle() { return tooltipStyle; }
        public List<String> getHeader() { return header; }
        public List<String> getFooter() { return footer; }
    }

    private static final Map<ItemRarity, RarityFormat> formats = new EnumMap<>(ItemRarity.class);

    public static void load() {
        File file = new File(DCCosmetics.getInstance().getDataFolder(), "rarities.yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                try (InputStream in = DCCosmetics.getInstance().getResource("rarities.yml")) {
                    if (in != null) {
                        Files.copy(in, file.toPath());
                    }
                }
            } catch (Exception e) {
                DCCosmetics.getInstance().getLogger().warning("[DCEssentials] Could not save default rarities.yml: " + e.getMessage());
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        formats.clear();

        for (ItemRarity rarity : ItemRarity.values()) {
            String sectionKey = rarity.name().toLowerCase();
            String nameColor = config.getString(sectionKey + ".name_color", rarity.getColor());
            String bracketColor = config.getString(sectionKey + ".bracket_color", getDefaultBracketColor(rarity));
            String emptyBracketColor = config.getString(sectionKey + ".empty_bracket_color", "&8");
            String bracketLeft = config.getString(sectionKey + ".bracket_left", "[");
            String bracketRight = config.getString(sectionKey + ".bracket_right", "]");
            String tag = config.getString(sectionKey + ".tag", ":lztooltips:" + sectionKey + ":");
            String divider = config.getString(sectionKey + ".divider", getDefaultDivider(rarity));
            String style = config.getString(sectionKey + ".tooltip_style", rarity.getTooltipStyle());
            List<String> header = config.getStringList(sectionKey + ".header");
            List<String> footer = config.getStringList(sectionKey + ".footer");

            formats.put(rarity, new RarityFormat(nameColor, bracketColor, emptyBracketColor, bracketLeft, bracketRight, tag, divider, style, header, footer));
        }
    }

    public static RarityFormat getFormat(ItemRarity rarity) {
        if (rarity == null) rarity = ItemRarity.COMMON;
        RarityFormat f = formats.get(rarity);
        if (f == null) {
            return new RarityFormat(rarity.getColor(), getDefaultBracketColor(rarity), "&8", "[", "]",
                    ":lztooltips:" + rarity.name().toLowerCase() + ":",
                    getDefaultDivider(rarity), rarity.getTooltipStyle(), Collections.emptyList(), Collections.emptyList());
        }
        return f;
    }

    public static String getDefaultBracketColor(ItemRarity rarity) {
        if (rarity == null) return "&7";
        return switch (rarity) {
            case COMMON -> "&7";
            case UNCOMMON -> "<#55FF55>";
            case RARE -> "<#0098FF>";
            case EPIC -> "<#A855F7>";
            case LEGENDARY -> "<#FFD700>";
            case MASTERY -> "<#FF5555>";
        };
    }

    public static String getDefaultDivider(ItemRarity rarity) {
        if (rarity == null) return "&8&m----------------------------------------";
        return switch (rarity) {
            case RARE -> "&#0098FF&l&n&m_&#098FFF&l&n&m_&#1185FF&l&n&m_&#1A7CFF&l&n&m_&#2372FF&l&n&m_&#2B69FF&l&n&m_&#345FFF&l&n&m_&#3C56FF&l&n&m_&#454CFF&l&n&m_&#4E43FF&l&n&m_&#5639FF&l&n&m_&#5F30FF&l&n&m_&#6826FF&l&n&m_&#701DFF&l&n&m_&#7913FF&l&n&m_&#810AFF&l&n&m_&#8A00FF&l&n&m_&#8A00FF&l&n&m_&#8A00FF&l&n&m_&#8A00FF&l&n&m_&#8A00FF&l&n&m_&#8A00FF&l&n&m_&#8A00FF&l&n&m_&#8A00FF&l&n&m_&#8A00FF&l&n&m_&#8A00FF&l&n&m_";
            case UNCOMMON -> "&#55FF55&l&n&m_&#66FF66&l&n&m_&#77FF77&l&n&m_&#88FF88&l&n&m_&#99FF99&l&n&m_&#AAFFAA&l&n&m_&#BBFFBB&l&n&m_&#CCFFCC&l&n&m_&#DDFFDD&l&n&m_&#EEFFEE&l&n&m_&#FFFFFF&l&n&m_&#EEFFEE&l&n&m_&#DDFFDD&l&n&m_&#CCFFCC&l&n&m_&#BBFFBB&l&n&m_&#AAFFAA&l&n&m_&#99FF99&l&n&m_&#88FF88&l&n&m_&#77FF77&l&n&m_&#66FF66&l&n&m_&#55FF55&l&n&m_";
            case EPIC -> "&#A855F7&l&n&m_&#B15FF8&l&n&m_&#BA68F9&l&n&m_&#C372F9&l&n&m_&#CB7CFA&l&n&m_&#D385FA&l&n&m_&#DC8FFB&l&n&m_&#E399FB&l&n&m_&#EAA2FC&l&n&m_&#F0ACFC&l&n&m_&#F6B5FD&l&n&m_&#FCBFFD&l&n&m_&#F6B5FD&l&n&m_&#F0ACFC&l&n&m_&#EAA2FC&l&n&m_&#E399FB&l&n&m_&#DC8FFB&l&n&m_&#D385FA&l&n&m_&#CB7CFA&l&n&m_&#C372F9&l&n&m_&#BA68F9&l&n&m_&#B15FF8&l&n&m_&#A855F7&l&n&m_";
            case LEGENDARY -> "&#FFD700&l&n&m_&#FFDA1A&l&n&m_&#FFDC33&l&n&m_&#FFDF4D&l&n&m_&#FFE266&l&n&m_&#FFE580&l&n&m_&#FFE899&l&n&m_&#FFEB99&l&n&m_&#FFE899&l&n&m_&#FFE580&l&n&m_&#FFE266&l&n&m_&#FFDF4D&l&n&m_&#FFDC33&l&n&m_&#FFDA1A&l&n&m_&#FFD700&l&n&m_&#FFD700&l&n&m_&#FFDA1A&l&n&m_&#FFDC33&l&n&m_&#FFDF4D&l&n&m_&#FFE266&l&n&m_&#FFE580&l&n&m_&#FFE899&l&n&m_&#FFD700&l&n&m_";
            case MASTERY -> "&#FF5555&l&n&m_&#FF5F5F&l&n&m_&#FF6969&l&n&m_&#FF7373&l&n&m_&#FF7C7C&l&n&m_&#FF8686&l&n&m_&#FF9090&l&n&m_&#FF9A9A&l&n&m_&#FFA4A4&l&n&m_&#FF9A9A&l&n&m_&#FF9090&l&n&m_&#FF8686&l&n&m_&#FF7C7C&l&n&m_&#FF7373&l&n&m_&#FF6969&l&n&m_&#FF5F5F&l&n&m_&#FF5555&l&n&m_&#FF5555&l&n&m_&#FF5F5F&l&n&m_&#FF6969&l&n&m_&#FF7373&l&n&m_&#FF7C7C&l&n&m_&#FF5555&l&n&m_";
            default -> "&8&m----------------------------------------";
        };
    }
}
