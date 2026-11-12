package dc.dcessentials.core.util;

import org.bukkit.command.CommandSender;

/**
 * Universal Dragoncote Chat System standardizer.
 * Enforces strict brand guidelines:
 * - Prefixes follow &7&l<...&7&l> format with letter-by-letter hex gradients.
 * - Zero square brackets in chat text.
 * - Zero unnecessary bolding (&l / §l) in body text.
 * - Matching category body colors.
 */
public class DCMessage {

    // [1] GENERAL SERVER MESSAGE
    public static final String PREFIX_GENERAL = "&7&l<&#3C7873D&#2E6667r&#21535Ba&#13414Fg&#115D61o&#107A73n&#0E9685c&#098078o&#05696Ct&#00535Fe&7&l> ";
    public static final String BODY_GENERAL = "&#0CBF93";

    // [2] WARNING MESSAGE
    public static final String PREFIX_WARNING = "&7&l<&#FBBF24W&#F5B31Fa&#F0A71Ar&#EA9B15n&#E48F10i&#DF830Bn&#D97706g&7&l> ";
    public static final String BODY_WARNING = "&#FDE047";

    // [3] ERROR MESSAGE
    public static final String PREFIX_ERROR = "&7&l<&#EF4444E&#DA3A3Ar&#C43030r&#AF2525o&#991B1Br&7&l> ";
    public static final String BODY_ERROR = "&#FCA5A5";

    // [4] INFO MESSAGE
    public static final String PREFIX_INFO = "&7&l<&#38BDF8I&#26A1DBn&#1585BEf&#0369A1o&7&l> ";
    public static final String BODY_INFO = "&#BAE6FD";

    // [5] BROADCAST MESSAGE
    public static final String PREFIX_BROADCAST = "&7&l<&#C084FCB&#B878F6r&#B06CF1o&#A75FEBa&#9F53E5d&#9747DFc&#8F3BDAa&#862ED4s&#7E22CEt&7&l> ";
    public static final String BODY_BROADCAST = "&#F3E8FF";

    // [6] COMMAND HELP MESSAGE
    public static final String PREFIX_HELP = "&7&l<&#94A3B8H&#7A899Ee&#616F83l&#475569p&7&l> ";
    public static final String BODY_HELP = "&#CBD5E1";

    // [7] EVENTS SYSTEM MESSAGE
    public static final String PREFIX_EVENTS = "&7&l<&#FBBF24E&#F5B31Fv&#F0A71Ae&#EA9B15n&#E48F10t&#DF830Bs&7&l> ";
    public static final String BODY_EVENTS = "&#FDE047";

    // [8] FAST TRAVEL / WARP MESSAGE
    public static final String PREFIX_WARP = "&7&l<&#38BDF8W&#26A1DBa&#1585BEr&#0369A1p&7&l> ";
    public static final String BODY_WARP = "&#BAE6FD";

    // [9] STORYLINE / QUEST MESSAGE
    public static final String PREFIX_QUEST = "&7&l<&#C084FCQ&#B878F6u&#B06CF1e&#A75FEBs&#9F53E5t&7&l> ";
    public static final String BODY_QUEST = "&#F3E8FF";

    // [10] WARDROBE / COSMETICS MESSAGE
    public static final String PREFIX_WARDROBE = "&7&l<&#EC4899W&#DB2777a&#BE185Dr&#9D174Ed&#831844r&#BE185Do&#DB2777b&#EC4899e&7&l> ";
    public static final String BODY_WARDROBE = "&#FBCFE8";

    // [11] MULTI-WORLD GIT SYNC MESSAGE
    public static final String PREFIX_SYNC = "&7&l<&#38BDF8S&#26A1DBy&#1585BEn&#0369A1c&7&l> ";
    public static final String BODY_SYNC = "&#BAE6FD";

    public static String general(String message) {
        return ColorUtil.colorize(PREFIX_GENERAL + BODY_GENERAL + message);
    }

    public static String success(String message) {
        return general(message);
    }

    public static String warning(String message) {
        return ColorUtil.colorize(PREFIX_WARNING + BODY_WARNING + message);
    }

    public static String error(String message) {
        return ColorUtil.colorize(PREFIX_ERROR + BODY_ERROR + message);
    }

    public static String info(String message) {
        return ColorUtil.colorize(PREFIX_INFO + BODY_INFO + message);
    }

    public static String broadcast(String message) {
        return ColorUtil.colorize(PREFIX_BROADCAST + BODY_BROADCAST + message);
    }

    public static String help(String message) {
        return ColorUtil.colorize(PREFIX_HELP + BODY_HELP + message);
    }

    public static String event(String message) {
        return ColorUtil.colorize(PREFIX_EVENTS + BODY_EVENTS + message);
    }

    public static String warp(String message) {
        return ColorUtil.colorize(PREFIX_WARP + BODY_WARP + message);
    }

    public static String quest(String message) {
        return ColorUtil.colorize(PREFIX_QUEST + BODY_QUEST + message);
    }

    public static String wardrobe(String message) {
        return ColorUtil.colorize(PREFIX_WARDROBE + BODY_WARDROBE + message);
    }

    public static String sync(String message) {
        return ColorUtil.colorize(PREFIX_SYNC + BODY_SYNC + message);
    }

    public static void sendGeneral(CommandSender sender, String message) {
        if (sender != null) sender.sendMessage(ColorUtil.toComponent(general(message)));
    }

    public static void sendWarning(CommandSender sender, String message) {
        if (sender != null) sender.sendMessage(ColorUtil.toComponent(warning(message)));
    }

    public static void sendError(CommandSender sender, String message) {
        if (sender != null) sender.sendMessage(ColorUtil.toComponent(error(message)));
    }

    public static void sendInfo(CommandSender sender, String message) {
        if (sender != null) sender.sendMessage(ColorUtil.toComponent(info(message)));
    }

    public static void sendBroadcast(CommandSender sender, String message) {
        if (sender != null) sender.sendMessage(ColorUtil.toComponent(broadcast(message)));
    }

    public static void sendHelp(CommandSender sender, String message) {
        if (sender != null) sender.sendMessage(ColorUtil.toComponent(help(message)));
    }

    public static void sendEvent(CommandSender sender, String message) {
        if (sender != null) sender.sendMessage(ColorUtil.toComponent(event(message)));
    }

    public static void sendWarp(CommandSender sender, String message) {
        if (sender != null) sender.sendMessage(ColorUtil.toComponent(warp(message)));
    }

    public static void sendQuest(CommandSender sender, String message) {
        if (sender != null) sender.sendMessage(ColorUtil.toComponent(quest(message)));
    }

    public static void sendWardrobe(CommandSender sender, String message) {
        if (sender != null) sender.sendMessage(ColorUtil.toComponent(wardrobe(message)));
    }

    public static void sendSync(CommandSender sender, String message) {
        if (sender != null) sender.sendMessage(ColorUtil.toComponent(sync(message)));
    }
}
