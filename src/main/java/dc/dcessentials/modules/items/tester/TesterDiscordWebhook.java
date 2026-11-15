package dc.dcessentials.modules.items.tester;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.model.ItemStat;
import dc.dcessentials.modules.items.stats.PlayerStatSnapshot;
import dc.dcessentials.modules.items.stats.StatAggregator;
import dc.dcessentials.modules.items.stats.StatCombatEngine;
import dc.dcessentials.modules.pets.model.PetPerkEffectType;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Asynchronous Discord Webhook client for /dce tester telemetry.
 * Formats Combat, Defense, Gathering, and 16-Stat Diagnostic reports into clean Discord Embeds
 * alongside in-game chat telemetry without blocking the main server thread.
 */
public final class TesterDiscordWebhook {

    private static final Pattern HEX_TAG_PATTERN = Pattern.compile("<#[0-9a-fA-F]{6}>");
    private static final Pattern AMP_HEX_PATTERN = Pattern.compile("&#[0-9a-fA-F]{6}");
    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("[&§][0-9a-fA-Fk-oK-OrR]");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private TesterDiscordWebhook() {}

    private record EmbedField(String name, String value, boolean inline) {}

    public static boolean isEnabled() {
        DCCosmetics plugin = DCCosmetics.getInstance();
        if (plugin == null) return false;
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("tester.discord-webhook-enabled", true)) return false;
        String url = cfg.getString("tester.discord-webhook-url", "");
        return isValidWebhookUrl(url);
    }

    public static String getConfiguredUrl() {
        DCCosmetics plugin = DCCosmetics.getInstance();
        if (plugin == null) return "";
        String url = plugin.getConfig().getString("tester.discord-webhook-url", "");
        return url == null ? "" : url.trim();
    }

    public static boolean isValidWebhookUrl(String url) {
        if (url == null) return false;
        String t = url.trim();
        return t.startsWith("https://discord.com/api/webhooks/")
                || t.startsWith("https://discordapp.com/api/webhooks/")
                || t.startsWith("https://ptb.discord.com/api/webhooks/")
                || t.startsWith("https://canary.discord.com/api/webhooks/");
    }

    public static void sendCombatEmbed(Player attacker,
                                       StatTesterManager.CombatTelemetryReport r,
                                       StatAggregator.DetailedBreakdown bd,
                                       StatTesterManager tester) {
        if (!isEnabled() || attacker == null || r == null) return;

        List<EmbedField> fields = new ArrayList<>();

        if (r.resonanceCadence() > 0) {
            int targetHits = Math.max(2, (int) Math.round(r.resonanceCadence()));
            int step = r.resonanceStep() <= 0 ? 1 : ((r.resonanceStep() % targetHits == 0) ? targetHits : (r.resonanceStep() % targetHits));
            String proc = r.resonanceProc() ? " -> SHATTER +25% DMG" : "";
            fields.add(new EmbedField(
                    "Resonance (" + targetHits + "-Hit [" + step + "/" + targetHits + "])",
                    stripMcColors(tester.formatSourceBreakdown(ItemStat.RESONANCE, bd, r.resonanceCadence(), true)) + proc,
                    true));
        }

        if (r.surgeCap() > 0) {
            fields.add(new EmbedField(
                    "Surge (+" + fmt(r.surgeActivePct()) + "% [" + r.surgeStacks() + "x], Cap " + fmt(r.surgeCap()) + "%)",
                    stripMcColors(tester.formatSourceBreakdown(ItemStat.SURGE, bd, r.surgeCap(), false)),
                    true));
        }

        if (r.catalystPct() > 0) {
            String detail = (r.catalystBonusPct() > 0 || r.catalystFlatOffset() > 0)
                    ? "\n-> +" + fmt(r.catalystBonusPct()) + "% DMG (" + String.join(", ", r.catalystDebuffs()) + ")"
                    : "\n-> No active debuffs on attacker (0%)";
            fields.add(new EmbedField(
                    "Catalyst (" + fmt(r.catalystPct()) + "%)",
                    stripMcColors(tester.formatSourceBreakdown(ItemStat.CATALYST, bd, r.catalystPct(), false)) + detail,
                    true));
        }

        if (r.executeStatPct() > 0) {
            List<String> exec = new ArrayList<>();
            if (r.executeWoundedActive()) {
                exec.add("+" + fmt(r.executeStatPct()) + "% Wounded (Enemy " + fmt(r.victimHpPctBeforeHit()) + "% <= 35%)");
            } else {
                exec.add("Enemy at " + fmt(r.victimHpPctBeforeHit()) + "% > 35% (Wounded inactive)");
            }
            if (r.executeDesperationActive()) exec.add("+35% Desperation (Self <= 30%)");
            if (r.executeCullTriggered()) exec.add("CULL KILLED TARGET!");
            fields.add(new EmbedField(
                    "Execute (" + fmt(r.executeStatPct()) + "%)",
                    stripMcColors(tester.formatSourceBreakdown(ItemStat.EXECUTE, bd, r.executeStatPct(), false))
                            + "\n-> " + String.join(" | ", exec),
                    true));
        }

        if (r.stasisPct() > 0) {
            fields.add(new EmbedField(
                    "Stasis (" + fmt(r.stasisPct()) + "%)",
                    stripMcColors(tester.formatSourceBreakdown(ItemStat.STASIS, bd, r.stasisPct(), false))
                            + "\n-> Debt Held: " + fmt(r.stasisHeldDebt()) + " DMG (2.5s)",
                    true));
        }

        if (r.cleavePct() > 0) {
            fields.add(new EmbedField(
                    "Cleave (" + fmt(r.cleavePct()) + "%)",
                    stripMcColors(tester.formatSourceBreakdown(ItemStat.CLEAVE, bd, r.cleavePct(), false))
                            + "\n-> Arc: " + fmt(r.cleaveArcDamage()) + " DMG (" + r.cleaveTargetsHit() + " nearby)",
                    true));
        }

        if (r.siphonPct() > 0) {
            String absText = r.siphonAbsorbAdded() > 0 ? " (+" + fmt(r.siphonAbsorbAdded()) + " Absorb)" : "";
            fields.add(new EmbedField(
                    "Siphon (" + fmt(r.siphonPct()) + "%)",
                    stripMcColors(tester.formatSourceBreakdown(ItemStat.SIPHON, bd, r.siphonPct(), false))
                            + "\n-> +" + fmt(r.siphonHpHealed()) + " HP (+" + fmt(r.siphonHpHealed() / 2.0) + " Hearts)" + absText,
                    true));
        }

        if (bd != null && bd.petInfo() != null) {
            List<String> perkParts = r.petPerkNotes() != null && !r.petPerkNotes().isEmpty()
                    ? new ArrayList<>(r.petPerkNotes())
                    : new ArrayList<>();
            if (perkParts.isEmpty()) {
                for (var perk : bd.petInfo().unlockedPerks()) {
                    if (perk.effectType() == PetPerkEffectType.BONUS_MOB_DAMAGE && r.petMobDamageBonusPct() > 0) {
                        perkParts.add(perk.perkName() + " (+" + fmt(perk.value()) + "% Mob DMG)");
                    } else if (perk.effectType() == PetPerkEffectType.BOSS_SLAYER_DAMAGE && r.petBossSlayerBonusPct() > 0) {
                        perkParts.add(perk.perkName() + " (+" + fmt(perk.value()) + "% Boss DMG)");
                    } else if (perk.effectType() == PetPerkEffectType.VAMPIRIC_HEAL && r.petVampiricHeal() > 0) {
                        perkParts.add(perk.perkName() + " (+" + fmt(r.petVampiricHeal()) + " HP)");
                    }
                }
            }
            if (!perkParts.isEmpty()) {
                fields.add(new EmbedField(
                        "Pet Perks (" + bd.petInfo().petName() + " Lv." + bd.petInfo().level() + ")",
                        stripMcColors(String.join(" + ", perkParts)),
                        false));
            }
        }

        if ((r.emperorScrollNote() != null && !r.emperorScrollNote().isBlank()) || r.emperorStasisSmash()) {
            List<String> bossNotes = new ArrayList<>();
            if (r.emperorScrollNote() != null && !r.emperorScrollNote().isBlank()) {
                bossNotes.add(stripMcColors(r.emperorScrollNote()));
            }
            if (r.emperorStasisSmash()) {
                bossNotes.add("1.5x Temporal Shatter");
            }
            fields.add(new EmbedField("Boss Modifiers", String.join(" | ", bossNotes), false));
        }

        if (fields.isEmpty()) {
            fields.add(new EmbedField("Active Combat Stats", "None (0 offensive stats or pet perks triggered)", false));
        }

        if (r.mathSteps() != null && !r.mathSteps().isEmpty()) {
            fields.add(new EmbedField("Step-by-Step Calculation", stripMcColors(String.join(" -> ", r.mathSteps())), false));
        }

        double totalHealed = r.siphonHpHealed() + r.petVampiricHeal();
        StringBuilder result = new StringBuilder();
        result.append("**Damage (No Stats):** ").append(fmt(r.baseDamageNoStats()))
                .append("  ->  **Damage w/ Stats:** **").append(fmt(r.finalDamage())).append("**")
                .append(" *(Pre-Armor: ").append(fmt(r.preMitigationWithPerksAndScrolls())).append(")*");
        if (totalHealed > 0) {
            result.append("\n**Restored:** +").append(fmt(totalHealed / 2.0)).append(" Hearts (")
                    .append(fmt(totalHealed)).append(" HP)");
        } else if (r.siphonAbsorbAdded() > 0) {
            result.append("\n**Absorb Added:** +").append(fmt(r.siphonAbsorbAdded() / 2.0)).append(" Hearts");
        }
        fields.add(new EmbedField("Final Hit Result", result.toString(), false));

        int color = r.executeCullTriggered() ? 0xEF4444 : 0xF59E0B;
        String desc = "**Attacker:** " + attacker.getName()
                + "\n**Target:** " + stripMcColors(r.targetLabel());
        postEmbed("⚡ DCE Combat Telemetry", desc, color, fields);
    }

    public static void sendDefenseEmbed(Player victim,
                                        String srcName,
                                        EntityDamageEvent.DamageCause cause,
                                        double rawDamage,
                                        StatCombatEngine.IncomingDamageResult defRes,
                                        double petDmgReductionPct,
                                        double finalHealthLost,
                                        StatAggregator.DetailedBreakdown bd,
                                        StatAggregator statAggregator,
                                        StatTesterManager tester) {
        if (!isEnabled() || victim == null || defRes == null) return;

        List<EmbedField> fields = new ArrayList<>();
        if (defRes.wardOrAegisReductionPct() > 0) {
            ItemStat usedStat = (cause != null && (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                    || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                    || cause == EntityDamageEvent.DamageCause.PROJECTILE))
                    ? ItemStat.WARD : ItemStat.AEGIS;
            fields.add(new EmbedField(
                    usedStat.getDisplayName() + " (-" + fmt(defRes.wardOrAegisReductionPct()) + "%)",
                    stripMcColors(tester.formatSourceBreakdown(usedStat, bd, defRes.wardOrAegisReductionPct(), false)),
                    true));
        }
        if (defRes.deflected()) {
            double defPct = statAggregator != null ? statAggregator.getTotalStat(victim, ItemStat.DEFLECT) : 0.0;
            fields.add(new EmbedField(
                    "Deflect (PARRIED -50% DMG)",
                    stripMcColors(tester.formatSourceBreakdown(ItemStat.DEFLECT, bd, Math.min(35.0, defPct), false)),
                    true));
        }
        if (defRes.bastionCapped()) {
            double basPct = statAggregator != null ? statAggregator.getTotalStat(victim, ItemStat.BASTION) : 0.0;
            fields.add(new EmbedField(
                    "Bastion (SINGLE-HIT CEILING)",
                    stripMcColors(tester.formatSourceBreakdown(ItemStat.BASTION, bd, Math.min(60.0, basPct), false)),
                    true));
        }
        if (petDmgReductionPct > 0 && bd != null && bd.petInfo() != null) {
            fields.add(new EmbedField(
                    "Pet Defense (-" + fmt(petDmgReductionPct) + "%)",
                    bd.petInfo().petName() + " [Lv." + bd.petInfo().level() + "]",
                    true));
        }
        if (defRes.unstableVulnerabilityApplied()) {
            fields.add(new EmbedField(
                    "Keystone Instability",
                    "+15% Incoming Vulnerability (Heat >= 80)",
                    true));
        }

        List<String> defMath = new ArrayList<>();
        double rVal = rawDamage;
        defMath.add(fmt(rVal) + " (Raw)");
        if (defRes.unstableVulnerabilityApplied()) {
            double next = rVal * 1.15;
            defMath.add("+15% Instability (+" + fmt(next - rVal) + " = " + fmt(next) + ")");
            rVal = next;
        }
        if (defRes.deflected()) {
            double next = rVal * 0.50;
            defMath.add("-50% Deflect (-" + fmt(rVal - next) + " = " + fmt(next) + ")");
            rVal = next;
        }
        if (defRes.wardOrAegisReductionPct() > 0) {
            double next = rVal * (1.0 - (defRes.wardOrAegisReductionPct() / 100.0));
            defMath.add("-" + fmt(defRes.wardOrAegisReductionPct()) + "% Ward/Aegis (-" + fmt(rVal - next) + " = " + fmt(next) + ")");
            rVal = next;
        }
        if (Math.abs(rVal - defRes.damage()) > 0.05) {
            defMath.add("Bastion/Cap = " + fmt(defRes.damage()));
        }
        if (defRes.damage() - finalHealthLost > 0.05) {
            defMath.add("Armor (-" + fmt(defRes.damage() - finalHealthLost) + " = " + fmt(finalHealthLost) + ")");
        }
        fields.add(new EmbedField("Step-by-Step Defense Math", String.join(" -> ", defMath), false));

        fields.add(new EmbedField(
                "Final Defense Result",
                "**Raw Incoming:** " + fmt(rawDamage)
                        + "  ->  **After Stats:** " + fmt(defRes.damage())
                        + "  ->  **Final Lost:** " + fmt(finalHealthLost) + " HP (" + fmt(finalHealthLost / 2.0) + " Hearts)",
                false));

        int color = defRes.unstableVulnerabilityApplied() ? 0xEF4444 : 0x38BDF8;
        String desc = "**Victim:** " + victim.getName()
                + "\n**Source:** " + stripMcColors(srcName) + " (" + (cause != null ? cause.name() : "CUSTOM") + ")";
        postEmbed("🛡 DCE Defense Telemetry", desc, color, fields);
    }

    public static void sendGatheringEmbed(Player player, String activity, String detail) {
        if (!isEnabled() || player == null) return;
        String desc = "**Player:** " + player.getName()
                + "\n**Activity:** " + stripMcColors(activity)
                + "\n**Detail:** " + stripMcColors(detail);
        postEmbed("⛏ DCE Gather Telemetry", desc, 0x34D399, List.of());
    }

    public static void sendStatSheetEmbed(Player player,
                                          PlayerStatSnapshot snap,
                                          StatAggregator.DetailedBreakdown bd,
                                          StatTesterManager tester) {
        if (!isEnabled() || player == null || snap == null || bd == null) return;

        List<EmbedField> fields = new ArrayList<>();
        for (ItemStat stat : ItemStat.values()) {
            double total = snap.get(stat);
            List<StatAggregator.StatSourceContribution> sources = bd.getSources(stat);
            if (total <= 0.0 && sources.isEmpty()) continue;
            boolean isRes = stat == ItemStat.RESONANCE;
            String valStr = isRes ? ((int) total + "-Hit") : ("+" + fmt(total) + "%");
            fields.add(new EmbedField(
                    stat.getDisplayName() + " [" + valStr + "]",
                    stripMcColors(tester.formatSourceBreakdown(stat, bd, total, isRes)),
                    true));
            if (fields.size() >= 23) break;
        }

        if (fields.isEmpty()) {
            fields.add(new EmbedField("Active Stats", "All 16 DCE stats are currently at 0.", false));
        }

        if (bd.petInfo() != null) {
            List<String> extras = new ArrayList<>();
            for (var perk : bd.petInfo().unlockedPerks()) {
                if (perk.effectType().getMappedStat() == null) {
                    extras.add(perk.perkName() + " [" + perk.effectType().name() + ": " + fmt(perk.value()) + "%]");
                }
            }
            if (!extras.isEmpty()) {
                fields.add(new EmbedField(
                        "Extra Pet Perks (" + bd.petInfo().petName() + " Lv." + bd.petInfo().level() + ")",
                        stripMcColors(String.join(" | ", extras)),
                        false));
            }
        }

        postEmbed("📊 Full 16-Stat Breakdown", "**Player:** " + player.getName(), 0x10B981, fields);
    }

    public static void sendTestPing(CommandSender caller) {
        String url = getConfiguredUrl();
        if (!isValidWebhookUrl(url)) {
            DCMessage.sendError(caller, "No valid Discord webhook URL configured. Use /dce tester webhook <url>.");
            return;
        }
        String payload = buildPayloadJson(
                "Dragoncote Stat Tester",
                "🧪 Discord Telemetry Webhook Connected",
                "**Triggered by:** " + caller.getName() + "\nAll `/dce tester` combat, defense, gathering, and `/dce tester stats` sheets will mirror here.",
                0x8B5CF6,
                List.of(new EmbedField("Status", "HTTP POST Pipeline Active", true))
        );

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .whenComplete((resp, err) -> {
                    DCCosmetics plugin = DCCosmetics.getInstance();
                    if (plugin == null) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (err != null) {
                            DCMessage.sendError(caller, "Discord Webhook test failed: " + err.getMessage());
                        } else if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                            DCMessage.sendGeneral(caller, "Discord Webhook test embed delivered! &8(HTTP " + resp.statusCode() + ")");
                        } else {
                            DCMessage.sendError(caller, "Discord Webhook returned HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 140));
                        }
                    });
                });
    }

    private static void postEmbed(String title, String description, int color, List<EmbedField> fields) {
        DCCosmetics plugin = DCCosmetics.getInstance();
        if (plugin == null) return;
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("tester.discord-webhook-enabled", true)) return;
        String url = cfg.getString("tester.discord-webhook-url", "");
        if (!isValidWebhookUrl(url)) return;
        String username = cfg.getString("tester.discord-username", "Dragoncote Stat Tester");
        if (username == null || username.isBlank()) username = "Dragoncote Stat Tester";

        String payload = buildPayloadJson(username.trim(), title, description, color, fields);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url.trim()))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                .thenAccept(resp -> {
                    if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                        plugin.getLogger().warning("[StatTester] Discord webhook returned HTTP " + resp.statusCode());
                    }
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("[StatTester] Discord webhook POST failed: " + ex.getMessage());
                    return null;
                });
    }

    private static String buildPayloadJson(String username,
                                           String title,
                                           String description,
                                           int color,
                                           List<EmbedField> fields) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"username\":\"").append(escapeJson(truncate(username, 80))).append("\",");
        sb.append("\"embeds\":[{");
        sb.append("\"title\":\"").append(escapeJson(truncate(title, 256))).append("\",");
        if (description != null && !description.isBlank()) {
            sb.append("\"description\":\"").append(escapeJson(truncate(description, 4096))).append("\",");
        }
        sb.append("\"color\":").append(color).append(",");
        sb.append("\"timestamp\":\"").append(Instant.now().toString()).append("\",");
        sb.append("\"footer\":{\"text\":\"DCEssentials Stat Telemetry\"}");

        if (fields != null && !fields.isEmpty()) {
            sb.append(",\"fields\":[");
            int limit = Math.min(25, fields.size());
            for (int i = 0; i < limit; i++) {
                EmbedField f = fields.get(i);
                if (i > 0) sb.append(',');
                sb.append("{\"name\":\"").append(escapeJson(truncate(f.name(), 256))).append("\",")
                        .append("\"value\":\"").append(escapeJson(truncate(f.value(), 1024))).append("\",")
                        .append("\"inline\":").append(f.inline()).append('}');
            }
            sb.append(']');
        }
        sb.append("}]}");
        return sb.toString();
    }

    public static String stripMcColors(String input) {
        if (input == null || input.isEmpty()) return "";
        String s = HEX_TAG_PATTERN.matcher(input).replaceAll("");
        s = AMP_HEX_PATTERN.matcher(s).replaceAll("");
        s = LEGACY_CODE_PATTERN.matcher(s).replaceAll("");
        return s.trim();
    }

    private static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.05) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(Locale.US, "%.1f", v);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return max <= 3 ? s.substring(0, max) : s.substring(0, max - 3) + "...";
    }

    private static String escapeJson(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
