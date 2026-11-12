package dc.dcessentials.modules.npc.util;

import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import net.md_5.bungee.api.ChatColor;

/**
 * High-fidelity hex and legacy gradient color parser matching Dragoncote aesthetic.
 */
public class DragoncoteColor {

    public static final String PREFIX_GENERAL   = colorize(DCMessage.PREFIX_GENERAL + DCMessage.BODY_GENERAL);
    public static final String PREFIX_SUCCESS   = colorize(DCMessage.PREFIX_GENERAL + DCMessage.BODY_GENERAL);
    public static final String PREFIX_INFO      = colorize(DCMessage.PREFIX_INFO + DCMessage.BODY_INFO);
    public static final String PREFIX_WARN      = colorize(DCMessage.PREFIX_WARNING + DCMessage.BODY_WARNING);
    public static final String PREFIX_ERROR     = colorize(DCMessage.PREFIX_ERROR + DCMessage.BODY_ERROR);
    public static final String PREFIX_BROADCAST = colorize(DCMessage.PREFIX_BROADCAST + DCMessage.BODY_BROADCAST);
    public static final String PREFIX_HELP      = colorize(DCMessage.PREFIX_HELP + DCMessage.BODY_HELP);

    public static String colorize(String text) {
        return ColorUtil.colorize(text);
    }

    public static String stripToGrayscale(String text) {
        if (text == null) return "";
        String stripped = ChatColor.stripColor(colorize(text));
        return "§7" + stripped;
    }
}
