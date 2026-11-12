package dc.dcessentials.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final Pattern HEX_TAG_PATTERN = Pattern.compile("<##?([A-Fa-f0-9]{6})>");
    private static final Pattern AMP_HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HASH_HEX_PATTERN = Pattern.compile("#([A-Fa-f0-9]{6})");
    private static final Pattern ITALIC_CODE_PATTERN = Pattern.compile("(?i)[&§]o");

    private ColorUtil() {}

    /**
     * Universal colorizer supporting:
     * - <#RRGGBB> and <##RRGGBB> (MiniMessage & Skript style)
     * - &#RRGGBB (Spigot / Bungee style)
     * - Standard Minecraft & codes (&0-&f, &l, &m, &n, &o, &k, &r)
     */
    public static String colorize(String text) {
        if (text == null || text.isEmpty()) return "";

        // 1. Convert <#RRGGBB> and <##RRGGBB>
        Matcher tagMatcher = HEX_TAG_PATTERN.matcher(text);
        StringBuffer sb1 = new StringBuffer();
        while (tagMatcher.find()) {
            String hex = tagMatcher.group(1);
            tagMatcher.appendReplacement(sb1, ChatColor.of("#" + hex).toString());
        }
        tagMatcher.appendTail(sb1);
        text = sb1.toString();

        // 2. Convert &#RRGGBB
        Matcher ampHexMatcher = AMP_HEX_PATTERN.matcher(text);
        StringBuffer sb2 = new StringBuffer();
        while (ampHexMatcher.find()) {
            String hex = ampHexMatcher.group(1);
            ampHexMatcher.appendReplacement(sb2, ChatColor.of("#" + hex).toString());
        }
        ampHexMatcher.appendTail(sb2);
        text = sb2.toString();

        // 3. Convert #RRGGBB
        Matcher hashMatcher = HASH_HEX_PATTERN.matcher(text);
        StringBuffer sb3 = new StringBuffer();
        while (hashMatcher.find()) {
            String hex = hashMatcher.group(1);
            hashMatcher.appendReplacement(sb3, ChatColor.of("#" + hex).toString());
        }
        hashMatcher.appendTail(sb3);
        text = sb3.toString();

        // 4. Fallback standard alternate color codes
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }

    private static final LegacyComponentSerializer SECTION_COMPONENT_SERIALIZER = LegacyComponentSerializer.builder()
            .character('\u00a7')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    /**
     * Colorize an entire list of strings (e.g. item lore).
     */
    public static List<String> colorize(List<String> list) {
        if (list == null) return Collections.emptyList();
        List<String> colored = new ArrayList<>(list.size());
        for (String line : list) {
            colored.add(colorize(line));
        }
        return colored;
    }

    /**
     * Parse Dragoncote color tags and legacy codes directly into a Kyori Adventure Component
     * with full 24-bit RGB hex support.
     */
    public static Component toComponent(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return SECTION_COMPONENT_SERIALIZER.deserialize(colorize(text));
    }

    /**
     * Parses text into an Adventure Component with italic formatting stripped and explicitly
     * disabled so Minecraft item names/lores never inherit default italic styling.
     */
    public static Component toItemComponent(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty().decoration(TextDecoration.ITALIC, false);
        }
        String noItalic = ITALIC_CODE_PATTERN.matcher(text).replaceAll("");
        return SECTION_COMPONENT_SERIALIZER.deserialize(colorize(noItalic))
                .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Converts lore lines into non-italic Adventure Components for ItemMeta.lore(...).
     */
    public static List<Component> toItemLore(List<String> lines) {
        if (lines == null || lines.isEmpty()) return Collections.emptyList();
        List<Component> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                result.add(Component.text(" ").decoration(TextDecoration.ITALIC, false));
            } else {
                result.add(toItemComponent(line));
            }
        }
        return result;
    }
}
