package dc.dcessentials.modules.items.util;

import org.bukkit.Bukkit;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FontImageUtil {

    private static final Map<String, String> UNICODE_MAP = new HashMap<>();
    private static final Map<String, String> GLYPH_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean iaReflectionResolved = false;
    private static volatile Constructor<?> iaWrapperCtor = null;
    private static volatile Method iaGetStringMethod = null;
    private static volatile Method iaReplaceMethod = null;

    static {
        // Direct ItemsAdder Unicode glyph mappings from default.json
        UNICODE_MAP.put("item", "\ue001");
        UNICODE_MAP.put("ingredient", "\ue001");
        UNICODE_MAP.put("currency", "\ue002");
        UNICODE_MAP.put("block", "\ue003");
        UNICODE_MAP.put("furniture", "\ue004");
        UNICODE_MAP.put("armor", "\ue005");
        UNICODE_MAP.put("hat", "\ue006");
        UNICODE_MAP.put("cosmetic", "\ue007");
        UNICODE_MAP.put("weapon", "\ue008");
        UNICODE_MAP.put("food", "\ue009");
        UNICODE_MAP.put("tool", "\ue00a");
        UNICODE_MAP.put("other", "\ue00b");
        UNICODE_MAP.put("common", "\ue00c");
        UNICODE_MAP.put("uncommon", "\ue00d");
        UNICODE_MAP.put("rare", "\ue00e");
        UNICODE_MAP.put("epic", "\ue00f");
        UNICODE_MAP.put("legendary", "\ue010");
        UNICODE_MAP.put("mastery", "\ue011");
        UNICODE_MAP.put("artifact", "\ue011");
        UNICODE_MAP.put("mythic", "\ue011");
        UNICODE_MAP.put("transcendent", "\ue011");
        UNICODE_MAP.put("pet", "\ue012");
        UNICODE_MAP.put("accessory", "\ue013");
        UNICODE_MAP.put("keystone", "\ue013");
        UNICODE_MAP.put("decoration", "\ue014");
    }

    public static void clearCache() {
        GLYPH_CACHE.clear();
        iaReflectionResolved = false;
    }

    private static void ensureIaReflection() {
        if (iaReflectionResolved) return;
        try {
            Class<?> clazz = Class.forName("dev.lone.itemsadder.api.FontImages.FontImageWrapper");
            iaWrapperCtor = clazz.getConstructor(String.class);
            iaGetStringMethod = clazz.getMethod("getString");
            iaReplaceMethod = clazz.getMethod("replaceFontImages", String.class);
        } catch (Throwable ignored) {
            iaWrapperCtor = null;
            iaGetStringMethod = null;
            iaReplaceMethod = null;
        }
        iaReflectionResolved = true;
    }

    public static String getGlyph(String name) {
        if (name == null) return "";
        String clean = name.toLowerCase(Locale.ROOT).replace("lztooltips:", "").replace(":", "").trim();
        String cached = GLYPH_CACHE.get(clean);
        if (cached != null) return cached;

        ensureIaReflection();
        // 1. Try ItemsAdder FontImageWrapper API via cached reflection
        if (iaWrapperCtor != null && iaGetStringMethod != null) {
            try {
                Object wrapper = iaWrapperCtor.newInstance("lztooltips:" + clean);
                String result = (String) iaGetStringMethod.invoke(wrapper);
                if (result != null && !result.isEmpty() && !result.startsWith(":")) {
                    GLYPH_CACHE.put(clean, result);
                    return result;
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. Direct Unicode fallback
        String fallback = UNICODE_MAP.getOrDefault(clean, UNICODE_MAP.getOrDefault(name.toLowerCase(Locale.ROOT), ":" + clean + ":"));
        if (!fallback.startsWith(":")) {
            GLYPH_CACHE.put(clean, fallback);
        }
        return fallback;
    }

    public static String replaceFontImages(String text) {
        if (text == null || text.isEmpty()) return text;

        ensureIaReflection();
        // Try ItemsAdder global replacer first if active
        if (iaReplaceMethod != null) {
            try {
                text = (String) iaReplaceMethod.invoke(null, text);
            } catch (Throwable ignored) {
            }
        }

        // Fallback: replace known :lztooltips:xyz: or :xyz: tags with Unicode glyphs
        for (Map.Entry<String, String> entry : UNICODE_MAP.entrySet()) {
            String key = entry.getKey();
            String glyph = entry.getValue();
            text = text.replace(":lztooltips:" + key + ":", glyph);
            text = text.replace(":" + key + ":", glyph);
        }

        return text;
    }
}
