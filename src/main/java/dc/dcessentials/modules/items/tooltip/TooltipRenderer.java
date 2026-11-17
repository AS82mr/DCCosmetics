package dc.dcessentials.modules.items.tooltip;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.modules.items.enchant.EnchantConfigManager;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemCategory;
import dc.dcessentials.modules.items.model.ItemRarity;
import dc.dcessentials.modules.items.model.ItemStat;
import dc.dcessentials.modules.items.rarity.RarityConfigManager;
import dc.dcessentials.modules.items.scroll.ScrollRegistry;
import dc.dcessentials.modules.items.scroll.ScrollType;
import dc.dcessentials.modules.items.util.FontImageUtil;
import dc.dcessentials.modules.teleportation.KeystoneLoreTokens;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.*;

public class TooltipRenderer {

    private static final int TARGET_LINE_LENGTH = 36;
    public static final NamespacedKey ENCHANT_SIG_KEY = DCCosmetics.getInstance() != null
            ? new NamespacedKey(DCCosmetics.getInstance(), "dce_ench_sig")
            : new NamespacedKey("dcessentials", "dce_ench_sig");

    private static String stripAllFormatting(String str) {
        if (str == null) return "";
        String s = str.replaceAll("(?i)§x(§[0-9a-fA-F]){6}", "");
        s = s.replaceAll("(?i)<##?[0-9a-fA-F]{6}>", "");
        s = s.replaceAll("(?i)&#[0-9a-fA-F]{6}", "");
        s = s.replaceAll("(?i)#[0-9a-fA-F]{6}", "");
        s = s.replaceAll("(?i)[§&][0-9a-fk-orx]", "");
        return s.trim();
    }

    public record SocketedEntry(String display, String brief) {}

    public record TooltipModel(
            DCEItem dceItem,
            ItemRarity rarity,
            RarityConfigManager.RarityFormat rarityFormat,
            Component displayName,
            List<String> headerLines,
            String skinLine,
            Map<ItemStat, Double> stats,
            boolean hasStats,
            List<String> socketBadges,
            List<SocketedEntry> socketedEntries,
            List<String> enchantTags,
            List<String> customLoreLines
    ) {}

    public static void renderItem(DCEItem dceItem, ItemStack itemStack, ScrollRegistry scrollRegistry) {
        renderItem(dceItem, itemStack, scrollRegistry, TooltipMode.COMPACT, null);
    }

    public static void renderItem(DCEItem dceItem, ItemStack itemStack, ScrollRegistry scrollRegistry, TooltipMode mode) {
        renderItem(dceItem, itemStack, scrollRegistry, mode, null);
    }

    public static void renderItem(DCEItem dceItem, ItemStack itemStack, ScrollRegistry scrollRegistry, TooltipMode mode, Player viewer) {
        if (dceItem == null || itemStack == null) return;
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return;

        // Scroll / Cosmetic Scroll Enchantment Safety
        if (dceItem.getCategory() == ItemCategory.SCROLL ||
                dceItem.getCategory() == ItemCategory.COSMETIC_SCROLL) {
            for (Enchantment e : new ArrayList<>(meta.getEnchants().keySet())) {
                meta.removeEnchant(e);
            }
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            meta.setEnchantmentGlintOverride(true);
            itemStack.setItemMeta(meta);
        }

        TooltipModel model = build(dceItem, itemStack, scrollRegistry, viewer);
        if (model == null) return;

        meta = itemStack.getItemMeta();
        if (meta == null) return;
        meta.displayName(model.displayName());
        meta.lore(render(model, mode != null ? mode : TooltipMode.COMPACT));
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(ENCHANT_SIG_KEY, PersistentDataType.STRING, computeEnchantSignature(meta));
        applyTooltipStyle(meta, model.rarityFormat().getTooltipStyle());
        itemStack.setItemMeta(meta);
    }

    public static TooltipModel build(DCEItem dceItem, ItemStack itemStack, ScrollRegistry scrollRegistry) {
        return build(dceItem, itemStack, scrollRegistry, null);
    }

    public static TooltipModel build(DCEItem dceItem, ItemStack itemStack, ScrollRegistry scrollRegistry, Player viewer) {
        if (dceItem == null || itemStack == null) return null;
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return null;

        ItemRarity rarity = dceItem.getRarity();
        RarityConfigManager.RarityFormat rarityFormat = RarityConfigManager.getFormat(rarity);

        // 1. Display Name (stars render strictly on the top header badge line; native DCE items match rarity color)
        ItemCategory category = dceItem.getCategory();
        boolean preserveCustomColors = (category == ItemCategory.VOUCHER
                || category == ItemCategory.COSMETIC
                || category == ItemCategory.PET);
        String rawTitle = dceItem.getDisplayName() != null ? dceItem.getDisplayName() : "";
        String noTrailingStars = rawTitle.replaceAll("\\s*★+\\s*$", "").trim();
        String styledName;
        if (preserveCustomColors && !stripAllFormatting(noTrailingStars).equals(noTrailingStars)) {
            styledName = ColorUtil.colorize(noTrailingStars);
        } else {
            String cleanName = stripAllFormatting(noTrailingStars).replaceAll("\\s*★+\\s*$", "").trim();
            styledName = ColorUtil.colorize(rarityFormat.getNameColor() + "&l" + cleanName);
        }
        Component nameComp = LegacyComponentSerializer.legacySection().deserialize(styledName)
                .decoration(TextDecoration.ITALIC, false);

        // 2. Header Lines
        List<String> headerLines = new ArrayList<>();
        String rarityTag = FontImageUtil.getGlyph(rarityFormat.getTag());
        if (rarityTag.isEmpty() || rarityTag.startsWith(":")) {
            rarityTag = FontImageUtil.getGlyph(rarity.name());
        }
        String catTag = (dceItem.getCategory() != null && dceItem.getCategory().isVisibleTag())
                ? FontImageUtil.getGlyph(dceItem.getCategory().name())
                : "";

        StringBuilder header = new StringBuilder("&f");
        header.append(rarityTag.isEmpty() ? "&8[" + rarity.getDisplayName() + "&8]" : rarityTag);
        if (!catTag.isEmpty()) {
            header.append(" ").append(catTag);
        }
        if (dceItem.getStars() > 0) {
            header.append(" &6").append("★".repeat(dceItem.getStars()));
        }
        headerLines.add(ColorUtil.colorize(header.toString()));

        if (!rarityFormat.getHeader().isEmpty()) {
            for (String hLine : rarityFormat.getHeader()) {
                headerLines.add(ColorUtil.colorize(hLine));
            }
        }

        // 3. Cosmetic skin descriptor (if applied)
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey cosmeticIdKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_id");
        String skinLine = null;
        if (pdc.has(cosmeticIdKey, PersistentDataType.STRING)) {
            String cosmeticId = pdc.get(cosmeticIdKey, PersistentDataType.STRING);
            var template = DCCosmetics.getInstance().getTemplateRegistry() != null
                    ? DCCosmetics.getInstance().getTemplateRegistry().getTemplate(cosmeticId)
                    : null;
            String rawName = (template != null && template.getItemName() != null && !template.getItemName().equals(template.getId()))
                    ? template.getItemName()
                    : cosmeticId;
            String skinName = rawName != null && rawName.contains("_") ? formatSkinName(rawName) : rawName;
            skinLine = ColorUtil.colorize("<#D946EF>✦ " + skinName + " Skin");
        }

        // 4. Stats Block (Combines template stats + per-instance PDC stat bonuses)
        Map<ItemStat, Double> stats = new java.util.EnumMap<>(ItemStat.class);
        stats.putAll(dceItem.getStats());
        NamespacedKey instanceStatsKey = new NamespacedKey(DCCosmetics.getInstance(), "instance_stats");
        if (pdc.has(instanceStatsKey, PersistentDataType.STRING)) {
            String rawInstanceStats = pdc.get(instanceStatsKey, PersistentDataType.STRING);
            if (rawInstanceStats != null && !rawInstanceStats.isEmpty()) {
                for (String token : rawInstanceStats.split(",")) {
                    String[] kv = token.split(":");
                    if (kv.length == 2) {
                        ItemStat st = ItemStat.fromString(kv[0].trim());
                        if (st != null) {
                            try {
                                double bonus = Double.parseDouble(kv[1].trim());
                                if (st == ItemStat.RESONANCE) {
                                    double cur = stats.getOrDefault(st, 0.0);
                                    stats.put(st, cur <= 0 ? bonus : Math.min(cur, bonus));
                                } else {
                                    stats.put(st, stats.getOrDefault(st, 0.0) + bonus);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        }
        boolean hasStats = false;
        for (double v : stats.values()) {
            if (v > 0) {
                hasStats = true;
                break;
            }
        }

        // 5. Vanilla Enchants (strictly vanilla-only; level >= cap from enchants.yml renders &c, else &9)
        Map<Enchantment, Integer> enchants = meta.getEnchants();
        List<String> enchantTags = new ArrayList<>();
        if (!enchants.isEmpty() && category != ItemCategory.SCROLL && category != ItemCategory.COSMETIC_SCROLL) {
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                Enchantment ench = entry.getKey();
                if (!EnchantConfigManager.isVanilla(ench)) continue;
                int lvl = entry.getValue();
                int configuredCap = EnchantConfigManager.getMaxLevel(ench);
                int vanillaMax = ench.getMaxLevel();
                String color = lvl >= configuredCap ? "&c" : "&9";
                String name = formatEnchantmentName(ench);
                String suffix = (lvl == 1 && vanillaMax == 1 && configuredCap == 1) ? "" : (" " + toRoman(lvl));
                enchantTags.add(color + name + suffix);
            }
        }

        // 6. Custom Lore (preserves internal blank spacer lines, strips legacy framing/prose, resolves {mc:...})
        boolean isScrollCat = (category == ItemCategory.SCROLL || category == ItemCategory.COSMETIC_SCROLL);
        List<String> validCustomLore = sanitizeCustomLore(dceItem.getCustomLore(), isScrollCat, viewer);

        // 7. Sockets & Socketed Scroll Entries
        Set<ScrollType> sockets = dceItem.getAllowedSockets();
        List<String> socketBadges = new ArrayList<>();
        List<SocketedEntry> socketedEntries = new ArrayList<>();

        if (!sockets.isEmpty()) {
            // A. Combat Socket
            if (sockets.contains(ScrollType.COMBAT)) {
                NamespacedKey combatKey = new NamespacedKey(DCCosmetics.getInstance(), "socket_combat");
                if (pdc.has(combatKey, PersistentDataType.STRING)) {
                    String scrollId = pdc.get(combatKey, PersistentDataType.STRING);
                    var scroll = scrollRegistry != null ? scrollRegistry.getScroll(scrollId) : null;
                    String tier = scroll != null && scroll.getRarity() != null ? normalizeRarity(scroll.getRarity()) : "common";
                    socketBadges.add(formatFilledSocketBadge(FontImageUtil.getGlyph("scroll_combat_" + tier), tier, rarityFormat));
                    if (scroll != null) {
                        socketedEntries.add(new SocketedEntry(scroll.getSocketDisplay(), scroll.getBrief()));
                    }
                } else {
                    socketBadges.add(formatEmptySocketBadge(FontImageUtil.getGlyph("scroll_combat_empty"), rarityFormat));
                }
            }

            // B. Cosmetic Socket
            if (sockets.contains(ScrollType.COSMETIC)) {
                if (pdc.has(cosmeticIdKey, PersistentDataType.STRING)) {
                    String cosmeticId = pdc.get(cosmeticIdKey, PersistentDataType.STRING);
                    var template = DCCosmetics.getInstance().getTemplateRegistry() != null
                            ? DCCosmetics.getInstance().getTemplateRegistry().getTemplate(cosmeticId)
                            : null;
                    String tier = template != null && template.getRarity() != null ? normalizeRarity(template.getRarity()) : "common";
                    socketBadges.add(formatFilledSocketBadge(FontImageUtil.getGlyph("scroll_cosmetic_" + tier), tier, rarityFormat));
                    if (template != null) {
                        socketedEntries.add(new SocketedEntry("<#D946EF>✦ " + template.getItemName(), "Cosmetic Skin Infusion"));
                    }
                } else {
                    socketBadges.add(formatEmptySocketBadge(FontImageUtil.getGlyph("scroll_cosmetic_empty"), rarityFormat));
                }
            }

            // C. Keystone Socket(s) (1..N badges per socket_slots capacity)
            if (sockets.contains(ScrollType.KEYSTONE)) {
                int ksCap = Math.max(1, dceItem.getSocketCapacity(ScrollType.KEYSTONE));
                List<String> ksScrollIds = dc.dcessentials.modules.teleportation.KeystoneDetector.socketedScrolls(itemStack);
                for (int i = 0; i < ksCap; i++) {
                    if (i < ksScrollIds.size() && !ksScrollIds.get(i).isBlank()) {
                        String scrollId = ksScrollIds.get(i);
                        var scroll = scrollRegistry != null ? scrollRegistry.getScroll(scrollId) : null;
                        String tier = scroll != null && scroll.getRarity() != null ? normalizeRarity(scroll.getRarity()) : "common";
                        socketBadges.add(formatFilledSocketBadge(FontImageUtil.getGlyph("scroll_keystone_" + tier), tier, rarityFormat));
                        if (scroll != null) {
                            socketedEntries.add(new SocketedEntry(scroll.getSocketDisplay(), scroll.getBrief()));
                        }
                    } else {
                        socketBadges.add(formatEmptySocketBadge(FontImageUtil.getGlyph("scroll_keystone_empty"), rarityFormat));
                    }
                }
            }

            // D. Mastery Socket
            if (sockets.contains(ScrollType.MASTERY)) {
                NamespacedKey masteryKey = new NamespacedKey(DCCosmetics.getInstance(), "socket_mastery");
                if (pdc.has(masteryKey, PersistentDataType.STRING)) {
                    String scrollId = pdc.get(masteryKey, PersistentDataType.STRING);
                    var scroll = scrollRegistry != null ? scrollRegistry.getScroll(scrollId) : null;
                    String tier = scroll != null && scroll.getRarity() != null ? normalizeRarity(scroll.getRarity()) : "common";
                    socketBadges.add(formatFilledSocketBadge(FontImageUtil.getGlyph("scroll_mastery_" + tier), tier, rarityFormat));
                    if (scroll != null) {
                        socketedEntries.add(new SocketedEntry(scroll.getSocketDisplay(), scroll.getBrief()));
                    }
                } else {
                    socketBadges.add(formatEmptySocketBadge(FontImageUtil.getGlyph("scroll_mastery_empty"), rarityFormat));
                }
            }
        }

        return new TooltipModel(
                dceItem,
                rarity,
                rarityFormat,
                nameComp,
                headerLines,
                skinLine,
                stats,
                hasStats,
                socketBadges,
                socketedEntries,
                enchantTags,
                validCustomLore
        );
    }

    public static List<Component> render(TooltipModel model, TooltipMode mode) {
        if (model == null) return Collections.emptyList();
        boolean expanded = mode == TooltipMode.EXPANDED;
        List<String> lore = new ArrayList<>(model.headerLines());

        if (model.skinLine() != null) {
            lore.add("");
            lore.add(model.skinLine());
        }

        if (model.hasStats()) {
            lore.add("");
            for (ItemStat stat : ItemStat.values()) {
                double val = model.stats().getOrDefault(stat, 0.0);
                if (val <= 0.0) continue;

                String statLabel = stat.getColor() + stat.getIcon() + " " + stat.getDisplayName();
                String valStr = formatStatValue(stat, val);
                String dotRow = createDotLeader(statLabel, valStr, stat.getColor());
                lore.add(ColorUtil.colorize(dotRow));
                if (expanded && stat.getBrief() != null && !stat.getBrief().isEmpty()) {
                    lore.add(ColorUtil.colorize("  &8└ &7" + stat.getBrief()));
                }
            }
        }

        // Item Passive Perks & 4/4 Full Set Bonus
        var itemPerks = model.dceItem().getPerks();
        var setBonus = model.dceItem().getSetBonus();
        if ((itemPerks != null && !itemPerks.isEmpty()) || setBonus != null) {
            lore.add("");
            if (itemPerks != null) {
                for (var perk : itemPerks) {
                    lore.add(ColorUtil.colorize("&#10B981✦ &l" + perk.name()));
                    if (perk.description() != null && !perk.description().isEmpty()) {
                        lore.add(ColorUtil.colorize("  &8└ &7" + perk.description()));
                    }
                }
            }
            if (setBonus != null && setBonus.perks() != null && !setBonus.perks().isEmpty()) {
                int req = setBonus.requiredPieces();
                lore.add(ColorUtil.colorize("&#F59E0B✦ Full Set Bonus (" + req + "/" + req + "): &f" + setBonus.setName()));
                for (var perk : setBonus.perks()) {
                    if (perk.description() != null && !perk.description().isEmpty()) {
                        lore.add(ColorUtil.colorize("  &8└ &7" + perk.description()));
                    } else {
                        lore.add(ColorUtil.colorize("  &8└ &7" + perk.name()));
                    }
                }
            }
        }

        List<String> socketBadges = model.socketBadges();
        List<String> enchantTags = model.enchantTags();
        List<String> validCustomLore = model.customLoreLines();

        if (!socketBadges.isEmpty()) {
            addSpacerIfNeeded(lore);

            StringBuilder row = new StringBuilder("§8§m=§r ");
            for (int i = 0; i < socketBadges.size(); i++) {
                if (i > 0) row.append(" ");
                row.append(socketBadges.get(i));
            }
            lore.add(ColorUtil.colorize(row.toString()));

            if (expanded && !model.socketedEntries().isEmpty()) {
                for (SocketedEntry entry : model.socketedEntries()) {
                    String disp = entry.display() != null ? entry.display() : "";
                    String brief = entry.brief();
                    if (brief != null && !brief.isBlank()
                            && !stripAllFormatting(disp).equalsIgnoreCase(stripAllFormatting(brief))) {
                        lore.add(ColorUtil.colorize("  &8• " + disp + " &8| &7" + brief));
                    } else {
                        lore.add(ColorUtil.colorize("  &8• " + disp));
                    }
                }
            }
        }

        if (!enchantTags.isEmpty()) {
            addSpacerIfNeeded(lore);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < enchantTags.size(); i++) {
                if (sb.length() > 0) sb.append("&7, ");
                sb.append(enchantTags.get(i));
                boolean endOfRow = (i + 1) % 3 == 0;
                boolean lastOverall = (i == enchantTags.size() - 1);
                if (endOfRow || lastOverall) {
                    if (endOfRow && !lastOverall) {
                        sb.append("&7,");
                    }
                    lore.add(ColorUtil.colorize(sb.toString()));
                    sb.setLength(0);
                }
            }
        }

        if (!validCustomLore.isEmpty()) {
            addSpacerIfNeeded(lore);
            lore.addAll(validCustomLore);
        }

        if (!model.rarityFormat().getFooter().isEmpty()) {
            addSpacerIfNeeded(lore);
            for (String fLine : model.rarityFormat().getFooter()) {
                lore.add(ColorUtil.colorize(fLine));
            }
        }

        return toComponentLore(lore);
    }

    private static void addSpacerIfNeeded(List<String> lore) {
        if (lore != null && !lore.isEmpty() && !lore.get(lore.size() - 1).trim().isEmpty()) {
            lore.add("");
        }
    }

    private static List<Component> toComponentLore(List<String> lore) {
        List<Component> componentLore = new ArrayList<>(lore.size());
        for (String line : lore) {
            if (line == null || line.trim().isEmpty()) {
                componentLore.add(Component.text(" "));
                continue;
            }
            String resolved = FontImageUtil.replaceFontImages(line);
            Component comp = LegacyComponentSerializer.legacySection().deserialize(resolved)
                    .decoration(TextDecoration.ITALIC, false);
            componentLore.add(comp);
        }
        return componentLore;
    }

    private static List<String> sanitizeCustomLore(List<String> rawLines, boolean isScroll, Player viewer) {
        List<String> out = new ArrayList<>();
        if (rawLines == null || rawLines.isEmpty()) return out;

        for (String raw : rawLines) {
            if (raw == null) continue;
            String resolvedMc = KeystoneLoreTokens.resolveMcTokens(raw, viewer);
            String plain = stripAllFormatting(resolvedMc).toLowerCase(Locale.ROOT);

            if (plain.isEmpty()) {
                if (!out.isEmpty() && !out.get(out.size() - 1).isEmpty()) {
                    out.add("");
                }
                continue;
            }

            if (plain.contains("infusion sockets")
                    || plain.startsWith("✦ cosmetic:") || plain.startsWith("\u2726 cosmetic:")
                    || (plain.endsWith(" skin") && (plain.startsWith("✦ ") || plain.startsWith("\u2726 ")))
                    || plain.contains("component(s)") || plain.startsWith("minecraft:")
                    || (plain.startsWith(":") && plain.endsWith(":"))
                    || plain.startsWith("rarity:") || plain.startsWith("ʀᴀʀɪᴛʏ:")
                    || plain.startsWith("type:") || plain.startsWith("ᴛʏᴘᴇ:")) {
                continue;
            }
            if (isScroll && (plain.equals("unbreaking i") || plain.equals("unbreaking 1") || plain.startsWith("unbreaking"))) {
                continue;
            }
            String lettersOnly = plain.replaceAll("[^a-z]", "");
            if (lettersOnly.equals("common") || lettersOnly.equals("uncommon") || lettersOnly.equals("rare")
                    || lettersOnly.equals("epic") || lettersOnly.equals("legendary") || lettersOnly.equals("mastery")
                    || lettersOnly.equals("artifact") || lettersOnly.equals("mythic")) {
                continue;
            }
            String nonSym = plain.replaceAll("[\\-_=~*·•—–―\u2500-\u257F\u00A0 ]", "").trim();
            if (nonSym.isEmpty() && plain.length() >= 3) {
                continue;
            }
            out.add(ColorUtil.colorize(resolvedMc));
        }

        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    private static String formatFilledSocketBadge(String icon, String tier, RarityConfigManager.RarityFormat itemFormat) {
        ItemRarity scrollRarity = ItemRarity.fromString(normalizeRarity(tier));
        String color = RarityConfigManager.getFormat(scrollRarity).getBracketColor();
        return color + itemFormat.getBracketLeft() + "&f" + icon + "&r" + color + itemFormat.getBracketRight();
    }

    private static String formatEmptySocketBadge(String icon, RarityConfigManager.RarityFormat itemFormat) {
        String color = itemFormat.getEmptyBracketColor();
        return color + itemFormat.getBracketLeft() + "&f" + icon + "&r" + color + itemFormat.getBracketRight();
    }

    private static String createDotLeader(String left, String right, String valColor) {
        int leftLen = ChatColor.stripColor(ColorUtil.colorize(left)).length();
        int rightLen = ChatColor.stripColor(ColorUtil.colorize(right)).length();
        int dotCount = Math.max(3, TARGET_LINE_LENGTH - leftLen - rightLen);
        String dots = "·".repeat(dotCount);
        return left + " &8" + dots + " " + valColor + right;
    }

    private static String normalizeRarity(String rarityStr) {
        if (rarityStr == null) return "common";
        String r = rarityStr.toLowerCase().trim();
        if (r.equals("artifact") || r.equals("mythic") || r.equals("transcendent")) return "mastery";
        if (r.equals("uncommon") || r.equals("rare") || r.equals("epic") || r.equals("legendary") || r.equals("mastery") || r.equals("common")) {
            return r;
        }
        return "common";
    }

    public static String formatStatValue(ItemStat stat, double val) {
        if (stat == ItemStat.RESONANCE) {
            return ((int) Math.round(val)) + "-Hit Cadence";
        }
        String numStr = (Math.abs(val - Math.rint(val)) < 1e-4)
                ? String.valueOf((long) Math.rint(val))
                : String.format(java.util.Locale.US, "%.1f", val);
        return (val >= 0 ? "+" : "") + numStr + "%";
    }

    public static String formatSkinName(String id) {
        if (id == null) return "";
        String[] parts = id.replace("_", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }

    private static String formatEnchantmentName(Enchantment ench) {
        String key = ench.getKey().getKey().replace("_", " ");
        String[] parts = key.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }

    private static String toRoman(int number) {
        if (number <= 0) return "";
        int[] values = {10, 9, 5, 4, 1};
        String[] numerals = {"X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (number >= values[i]) {
                result.append(numerals[i]);
                number -= values[i];
            }
        }
        return result.toString();
    }

    public static String computeEnchantSignature(ItemMeta meta) {
        if (meta == null || !meta.hasEnchants()) return "";
        Map<Enchantment, Integer> enchants = meta.getEnchants();
        if (enchants.isEmpty()) return "";
        List<String> parts = new ArrayList<>(enchants.size());
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            if (!EnchantConfigManager.isVanilla(e.getKey())) continue;
            parts.add(e.getKey().getKey().getKey() + ":" + e.getValue());
        }
        if (parts.isEmpty()) return "";
        Collections.sort(parts);
        return String.join(",", parts);
    }

    public static boolean hasMatchingEnchantSignature(ItemMeta meta) {
        if (meta == null) return true;
        String actual = computeEnchantSignature(meta);
        String stored = meta.getPersistentDataContainer().get(ENCHANT_SIG_KEY, PersistentDataType.STRING);
        if (stored == null) {
            return false;
        }
        return actual.equals(stored);
    }

    public static boolean hasMatchingTooltipStyle(ItemMeta meta, String expectedStyleKey) {
        if (meta == null || expectedStyleKey == null || expectedStyleKey.isEmpty()) return true;
        try {
            NamespacedKey expected = NamespacedKey.fromString(expectedStyleKey);
            if (expected == null) return true;
            Method m = meta.getClass().getMethod("getTooltipStyle");
            Object current = m.invoke(meta);
            return expected.equals(current);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void applyTooltipStyle(ItemMeta meta, String styleKey) {
        if (meta == null || styleKey == null) return;
        try {
            NamespacedKey key = NamespacedKey.fromString(styleKey);
            if (key != null) {
                Method m = meta.getClass().getMethod("setTooltipStyle", NamespacedKey.class);
                m.invoke(meta, key);
            }
        } catch (Throwable ignored) {
            // Silently fallback if on Bukkit builds without tooltip_style method
        }
    }

    public static void renderScroll(ItemStack itemStack, String displayName, ItemRarity rarity, String targetCategory, List<String> customLore) {
        if (itemStack == null) return;
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return;

        if (rarity == null) rarity = ItemRarity.COMMON;
        RarityConfigManager.RarityFormat format = RarityConfigManager.getFormat(rarity);

        // 1. Display Name (scroll names always match their rarity color, no duplicate stars)
        String cleanName = stripAllFormatting(displayName).replaceAll("\\s*★+\\s*$", "").trim();
        String styledName = ColorUtil.colorize(format.getNameColor() + "&l" + cleanName);
        Component nameComp = LegacyComponentSerializer.legacySection().deserialize(styledName)
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(nameComp);

        // 2. Build Unified Option A Scroll Frame (top glyph + 1 gradient header, sanitized body, 1 gradient footer)
        List<String> lore = new ArrayList<>();
        String rarityTag = FontImageUtil.getGlyph(format.getTag());
        if (rarityTag.isEmpty() || rarityTag.startsWith(":")) {
            rarityTag = FontImageUtil.getGlyph(rarity.name());
        }
        String header = "&f" + (rarityTag.isEmpty() ? "&8[" + rarity.getDisplayName() + "&8]" : rarityTag);
        lore.add(ColorUtil.colorize(header));
        if (!format.getHeader().isEmpty()) {
            for (String hLine : format.getHeader()) {
                lore.add(ColorUtil.colorize(hLine));
            }
        }

        List<String> validLore = sanitizeCustomLore(customLore, true, null);
        if (!validLore.isEmpty()) {
            addSpacerIfNeeded(lore);
            lore.addAll(validLore);
        }

        if (!format.getFooter().isEmpty()) {
            addSpacerIfNeeded(lore);
            for (String fLine : format.getFooter()) {
                lore.add(ColorUtil.colorize(fLine));
            }
        }

        meta.lore(toComponentLore(lore));

        // Strip any legacy enchantments from scroll to prevent 'Unbreaking I' text
        for (Enchantment e : new ArrayList<>(meta.getEnchants().keySet())) {
            meta.removeEnchant(e);
        }
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.setEnchantmentGlintOverride(true);

        // Modern 1.21 Tooltip Style Component
        applyTooltipStyle(meta, format.getTooltipStyle());

        itemStack.setItemMeta(meta);
    }
}
