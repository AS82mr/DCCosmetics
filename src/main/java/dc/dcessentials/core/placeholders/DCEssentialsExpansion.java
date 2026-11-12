package dc.dcessentials.core.placeholders;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.rng.RngModel;
import dc.dcessentials.modules.teleportation.KeystoneProfile;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;

public class DCEssentialsExpansion extends PlaceholderExpansion {

    @Override
    public @NotNull String getIdentifier() {
        return "dragoncote";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Dragoncote";
    }

    @Override
    public @NotNull String getVersion() {
        return "2.2";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        KeystoneProfile profile = DCCosmetics.getInstance().getTeleportationManager().getProfile(player);
        String pLower = params.toLowerCase();

        // ====================================================================
        // Legacy Dragoncote Placeholders (Ported from Skript dcplaceholders.sk)
        // ====================================================================
        switch (pLower) {
            case "guild":
                String gTier = PlaceholderAPI.setPlaceholders(player, "%guilds_tier%");
                return switch (gTier) {
                    case "1" -> "&7";
                    case "2" -> "&f";
                    case "3" -> "&e";
                    case "4" -> "&6";
                    case "5" -> "&a";
                    case "6" -> "&d";
                    case "7" -> "&2";
                    case "8" -> "&b";
                    case "9" -> "&9";
                    case "10" -> "&3";
                    case "11" -> "&1";
                    case "12" -> "&c";
                    case "13" -> "&4";
                    default -> "&7";
                };

            case "tagspace":
                String tagDesc = PlaceholderAPI.setPlaceholders(player, "%deluxetags_description%");
                return "used".equalsIgnoreCase(tagDesc) ? " " : "";

            case "guildnotag":
                String gForm = PlaceholderAPI.setPlaceholders(player, "%guilds_formatted%");
                return "✖".equals(gForm) ? "✖" : "";

            case "keystone_tier_raw":
                return profile.getTier().name();

            case "keystone_tier":
                return profile.getTier().getDisplayName();

            case "keystone_name":
                return profile.getTier().getKeystoneName();

            case "keystone_max":
                return String.valueOf(DCCosmetics.getInstance().getTeleportationManager().getEnergyService().getEffectiveMaxEnergy(player, profile));

            case "keystone_energy":
            case "tesseract":
                return String.valueOf(profile.getEnergy());

            case "keystone_parked":
                return String.valueOf(profile.parkedEnergy());

            case "microcharges":
            case "keystone_micro":
                return String.valueOf(profile.getMicro());

            case "tesseract_exact":
                return String.format(Locale.US, "%d.%03d", profile.usableEnergy(), profile.getMicro());

            case "tesseract_total_micro":
                return String.valueOf(profile.getTotalMicro());

            case "keystone_heat":
            case "heat":
                return String.valueOf(profile.getHeat());

            case "overloaded":
            case "keystone_overloaded":
                return String.valueOf(profile.isOverloaded());

            case "stability":
                return profile.getStability();

            case "keystone_efficiency":
            case "keystone_insulation":
                return profile.getTier().getEfficiencyText();

            case "shield_active":
                return profile.hasNewbieShield() ? "true" : "false";

            case "shield_status":
                if (profile.hasNewbieShield()) {
                    long remMs = profile.getNewbieShieldUntil() - System.currentTimeMillis();
                    long minutes = (remMs / 1000) / 60;
                    long seconds = (remMs / 1000) % 60;
                    String rem = minutes > 0 ? (minutes + "m " + seconds + "s") : (seconds + "s");
                    return "&aActive (&b" + rem + "&a)";
                } else if (profile.getNewbieShieldUntil() > 0) {
                    return "&cExpired";
                } else {
                    return "&cNone";
                }

            case "waypoint_visible":
                return DCCosmetics.getInstance().getWaypointManager().isVisible(player) ? "true" : "false";

            case "waypoint_name":
                UUID u = player.getUniqueId();
                Object wIdObj = getSkriptVar("dcnpc::waypoint::" + u);
                if (wIdObj instanceof String wId && !wId.isEmpty()) {
                    Object nameObj = getSkriptVar("dcnpc::registry::" + wId + "::name");
                    if (nameObj instanceof String n) return n;
                    return Character.toUpperCase(wId.charAt(0)) + wId.substring(1);
                }
                return "&7None";

            case "waypoint_coords":
                return DCCosmetics.getInstance().getWaypointManager().getTargetCoords(player);

            case "questline_finished":
            case "questline_completed":
                int qIdx = getSkriptQuestIndex(player);
                return qIdx > 25 ? "true" : "false";

            case "questline_index":
            case "timeline_index":
                return String.valueOf(getSkriptQuestIndex(player));

            case "timeline_total":
                Object tot = getSkriptVar("dcnpc::timeline_total");
                return (tot instanceof Number n) ? String.valueOf(n.intValue()) : "25";

            case "timeline_percent":
                int curQ = getSkriptQuestIndex(player);
                Object tTot = getSkriptVar("dcnpc::timeline_total");
                int totVal = (tTot instanceof Number n) ? n.intValue() : 25;
                if (totVal <= 0) totVal = 25;
                int pPct = Math.min(100, Math.max(0, (int) Math.round(((double)(curQ - 1) / totVal) * 100)));
                return pPct + "%";

            case "questline_next_npc":
            case "timeline_target":
            case "next_contact":
                return getNextNpcName(player);

            case "timeline_target_id":
                int idxId = getSkriptQuestIndex(player);
                Object nodeObj = getSkriptVar("dcnpc::timeline_order::" + idxId);
                if (nodeObj instanceof String s) {
                    return s.split(":")[0];
                }
                return "corvus";

            case "timeline_target_faction":
                int idxFac = getSkriptQuestIndex(player);
                Object nodeFac = getSkriptVar("dcnpc::timeline_order::" + idxFac);
                String tId = "corvus";
                if (nodeFac instanceof String s) {
                    tId = s.split(":")[0];
                }
                Object fac = getSkriptVar("dcnpc::registry::" + tId + "::faction");
                if (fac instanceof String f) return f;
                return "Resistance";

            default:
                break;
        }

        // ====================================================================
        // Dynamic Timeline Status & Stage Placeholders
        // ====================================================================
        if (pLower.startsWith("timeline_status_")) {
            String rawIdx = pLower.substring(16);
            try {
                int nodeIdx = Integer.parseInt(rawIdx);
                int curQIdx = getSkriptQuestIndex(player);
                if (nodeIdx < curQIdx) return "COMPLETED";
                if (nodeIdx == curQIdx) return "ACTIVE";
                return "LOCKED";
            } catch (NumberFormatException ignored) {}
            return "LOCKED";
        }

        if (pLower.startsWith("stage_")) {
            String charId = pLower.substring(6);
            UUID u = player.getUniqueId();
            Object stg = getSkriptVar("dcnpc::char_stage::" + u + "::" + charId);
            if (stg instanceof Number n) return String.valueOf(n.intValue());
            return "1";
        }

        // ====================================================================
        // RNG Drop Placeholders: %dragoncote_rng_drop_<field>_<cat>_<dropId>%
        // (Must come BEFORE generic rng_ prefix!)
        // ====================================================================
        if (pLower.startsWith("rng_drop_")) {
            String remainder = pLower.substring(9); // e.g. "xp_survival_custom_surv_ancient_rune", "pct_casino_cas_highroller_tag"
            int firstUnder = remainder.indexOf('_');
            if (firstUnder != -1) {
                String field = remainder.substring(0, firstUnder);
                String rest = remainder.substring(firstUnder + 1);
                var rng = DCCosmetics.getInstance().getRngManager();
                for (String catKey : rng.getCategories().keySet()) {
                    if (rest.startsWith(catKey.toLowerCase() + "_")) {
                        String dropId = rest.substring(catKey.length() + 1);
                        return getDropPlaceholder(player, catKey, dropId, field);
                    }
                }
            }
        }

        // ====================================================================
        // RNG Category Placeholders: %dragoncote_rng_cat_<field>_<cat>%
        // ====================================================================
        if (pLower.startsWith("rng_cat_")) {
            String remainder = pLower.substring(8); // e.g. "progress_survival_custom", "selected_casino"
            int firstUnder = remainder.indexOf('_');
            if (firstUnder != -1) {
                String field = remainder.substring(0, firstUnder);
                String cat = remainder.substring(firstUnder + 1);
                return getCategoryPlaceholder(player, cat, field);
            }
        }

        // Legacy/Direct %dragoncote_rng_<cat>%
        if (pLower.startsWith("rng_")) {
            String cat = pLower.substring(4);
            return DCCosmetics.getInstance().getRngManager().getPlaceholder(player, cat);
        }

        // ====================================================================
        // Glow System Placeholders
        // ====================================================================
        if (pLower.equals("glow") || pLower.equals("glow_formatted")) {
            var gm = DCCosmetics.getInstance().getGlowManager();
            return gm != null ? gm.getActiveGlowFormatted(player) : "&cNone";
        }
        if (pLower.equals("glow_id") || pLower.equals("glow_color")) {
            var gm = DCCosmetics.getInstance().getGlowManager();
            String id = gm != null ? gm.getActiveGlow(player) : null;
            return id != null ? id : "none";
        }
        if (pLower.startsWith("has_glow_")) {
            String color = pLower.substring(9);
            boolean has = player.hasPermission("glow." + color) || player.hasPermission("glow.all");
            return has ? "true" : "false";
        }
        if (pLower.startsWith("is_glow_")) {
            String color = pLower.substring(8);
            var gm = DCCosmetics.getInstance().getGlowManager();
            String id = gm != null ? gm.getActiveGlow(player) : null;
            return color.equalsIgnoreCase(id) ? "true" : "false";
        }

        // ====================================================================
        // Pity Placeholders (Decoupled Hypixel Utility Pity Engine)
        // ====================================================================
        if (pLower.startsWith("pity_")) {
            return getPityPlaceholder(player, pLower.substring(5));
        }

        // ====================================================================
        // Server Events & Dimensional World Cycle Placeholders
        // ====================================================================
        if (pLower.startsWith("active_events_") || pLower.startsWith("emperor_")) {
            String resolved = dc.dcessentials.modules.boss.EmperorTabHook.resolvePlaceholder(
                    DCCosmetics.getInstance().getEmperorBossManager(),
                    DCCosmetics.getInstance().getEventManager(),
                    player,
                    pLower
            );
            if (resolved != null) return resolved;
        }

        if (pLower.startsWith("event_")) {
            return getEventPlaceholder(pLower.substring(6));
        }

        // ====================================================================
        // Stackable DCEStats Placeholders (%dragoncote_stat_*% & %dragoncote_stats_summary%)
        // ====================================================================
        if (pLower.equals("stats_summary") || pLower.equals("stat_summary")) {
            return getStatsSummaryPlaceholder(player);
        }
        if (pLower.startsWith("stat_")) {
            return getStatPlaceholder(player, pLower.substring(5));
        }

        // ====================================================================
        // DCEPet Placeholders (%dragoncote_pet_*%)
        // ====================================================================
        if (pLower.startsWith("pet_")) {
            return getPetPlaceholder(player, pLower.substring(4));
        }

        // ====================================================================
        // Teleportation Effects Placeholders (%dragoncote_tpeffect*%)
        // ====================================================================
        if (pLower.equals("tpeffect") || pLower.equals("tpeffect_display") || pLower.equals("tpeffect_rarity")) {
            var tpMgr = DCCosmetics.getInstance().getTpEffectManager();
            if (tpMgr == null) {
                return pLower.equals("tpeffect_display") ? dc.dcessentials.core.util.ColorUtil.colorize("&7None") : "None";
            }
            var entry = tpMgr.getSelectedEffect(player);
            if (pLower.equals("tpeffect")) {
                return entry != null ? entry.getId() : "None";
            }
            if (pLower.equals("tpeffect_display")) {
                return entry != null ? entry.getColoredDisplayName() : dc.dcessentials.core.util.ColorUtil.colorize("&7None");
            }
            return entry != null ? entry.getRarity() : "none";
        }

        return null;
    }

    private String getStatsSummaryPlaceholder(Player player) {
        var itemMod = DCCosmetics.getInstance().getItemModule();
        if (itemMod == null || itemMod.getStatAggregator() == null) return "&7No Active Stats";
        var snap = itemMod.getStatAggregator().getSnapshot(player);
        if (!snap.hasAnyStats()) return "&7No Active Stats";

        StringBuilder sb = new StringBuilder();
        for (dc.dcessentials.modules.items.model.ItemStat st : dc.dcessentials.modules.items.model.ItemStat.values()) {
            double val = snap.get(st);
            if (val <= 0.0) continue;
            if (sb.length() > 0) sb.append(" &8| ");
            sb.append(st.getColor()).append(st.getIcon()).append(" ")
              .append(dc.dcessentials.modules.items.tooltip.TooltipRenderer.formatStatValue(st, val));
        }
        return sb.toString();
    }

    private String getStatPlaceholder(Player player, String param) {
        var itemMod = DCCosmetics.getInstance().getItemModule();
        if (itemMod == null || itemMod.getStatAggregator() == null) return "0";
        var snap = itemMod.getStatAggregator().getSnapshot(player);

        // %dragoncote_stat_raw_<stat>%
        if (param.startsWith("raw_")) {
            dc.dcessentials.modules.items.model.ItemStat st = dc.dcessentials.modules.items.model.ItemStat.fromString(param.substring(4));
            if (st == null) return "0";
            double val = snap.get(st);
            return (Math.abs(val - Math.rint(val)) < 1e-4)
                    ? String.valueOf((long) Math.rint(val))
                    : String.format(Locale.US, "%.1f", val);
        }

        // %dragoncote_stat_icon_<stat>%
        if (param.startsWith("icon_")) {
            dc.dcessentials.modules.items.model.ItemStat st = dc.dcessentials.modules.items.model.ItemStat.fromString(param.substring(5));
            return st != null ? (st.getColor() + st.getIcon()) : "";
        }

        // %dragoncote_stat_line_<stat>%
        if (param.startsWith("line_")) {
            dc.dcessentials.modules.items.model.ItemStat st = dc.dcessentials.modules.items.model.ItemStat.fromString(param.substring(5));
            if (st == null) return "";
            double val = snap.get(st);
            return st.getColor() + st.getIcon() + " " + st.getDisplayName() + ": &f"
                    + dc.dcessentials.modules.items.tooltip.TooltipRenderer.formatStatValue(st, val);
        }

        // %dragoncote_stat_source_<source>_<stat>%
        if (param.startsWith("source_")) {
            String rest = param.substring(7);
            int lastUnder = rest.lastIndexOf('_');
            if (lastUnder != -1) {
                String srcStr = rest.substring(0, lastUnder);
                String statStr = rest.substring(lastUnder + 1);
                var src = dc.dcessentials.modules.items.stats.StatSource.fromString(srcStr);
                var st = dc.dcessentials.modules.items.model.ItemStat.fromString(statStr);
                if (src != null && st != null) {
                    double val = snap.getFromSource(src, st);
                    return dc.dcessentials.modules.items.tooltip.TooltipRenderer.formatStatValue(st, val);
                }
            }
            return "0%";
        }

        // Default: %dragoncote_stat_<stat>% -> formatted (+50%, 4-Hit Cadence, etc.)
        dc.dcessentials.modules.items.model.ItemStat st = dc.dcessentials.modules.items.model.ItemStat.fromString(param);
        if (st == null) return "0%";
        double val = snap.get(st);
        return dc.dcessentials.modules.items.tooltip.TooltipRenderer.formatStatValue(st, val);
    }

    private String getPetPlaceholder(Player player, String param) {
        var pm = DCCosmetics.getInstance().getPetManager();
        if (pm == null) return "";
        var data = pm.getData(player.getUniqueId());
        String activeId = data.getActivePetId();
        var spec = activeId != null ? pm.getRegistry().getPet(activeId) : null;
        var entry = activeId != null ? data.getPetEntry(activeId) : null;
        int level = entry != null ? entry.getLevel() : 1;
        double xp = entry != null ? entry.getXp() : 0.0;
        var rarity = (entry != null && entry.getRarityOverride() != null)
                ? entry.getRarityOverride()
                : (spec != null ? spec.getRarity() : null);

        switch (param) {
            case "active":
                return spec != null ? "true" : "false";
            case "id":
                return spec != null ? spec.getId() : "none";
            case "name":
                return spec != null ? (rarity.getColorHex() + spec.getDisplayName()) : "&7None";
            case "name_raw":
                return spec != null ? spec.getDisplayName() : "None";
            case "rarity":
                return rarity != null ? (rarity.getColorHex() + rarity.getDisplayName()) : "&7None";
            case "level":
                return spec != null ? String.valueOf(level) : "0";
            case "max_level":
                return spec != null ? String.valueOf(spec.getMaxLevel()) : "0";
            case "xp":
                return String.valueOf((int) xp);
            case "xp_req":
                return spec != null ? String.valueOf((int) spec.getXpRequiredForNextLevel(level, pm.getConfig())) : "0";
            case "xp_pct": {
                if (spec == null) return "0%";
                if (level >= spec.getMaxLevel()) return "100%";
                double req = spec.getXpRequiredForNextLevel(level, pm.getConfig());
                int pct = req > 0 ? (int) Math.min(100, Math.round((xp / req) * 100.0)) : 0;
                return pct + "%";
            }
            case "xp_bar": {
                if (spec == null) return "&8□□□□□□□□□□";
                if (level >= spec.getMaxLevel()) return formatProgressBar(100);
                double req = spec.getXpRequiredForNextLevel(level, pm.getConfig());
                int pct = req > 0 ? (int) Math.min(100, Math.round((xp / req) * 100.0)) : 0;
                return formatProgressBar(pct);
            }
            case "category":
                return spec != null ? spec.getCategory().getDisplayName() : "None";
            case "autocast":
                return data.isAutoCastSkills() ? "ON" : "OFF";
            case "tracker":
                return data.isTrackerEnabled() ? "ON" : "OFF";
            case "skill_name":
                return (spec != null && spec.getActiveSkill() != null) ? spec.getActiveSkill().getDisplayName() : "None";
            case "skill_trigger":
                return (spec != null && spec.getActiveSkill() != null) ? spec.getActiveSkill().getTrigger().getDisplayLabel() : "None";
            case "perks_unlocked": {
                if (spec == null) return "0/0";
                int unlocked = 0;
                for (var p : spec.getPerks()) {
                    if (p.isUnlockedAt(level)) unlocked++;
                }
                return unlocked + "/" + spec.getPerks().size();
            }
            case "visibility":
                return data.getVisibilityMode().getBadge();
            case "skill_cooldown": {
                long remMs = pm.getSkillCooldownRemainingMs(player.getUniqueId());
                if (remMs <= 0L) return "&aReady";
                return String.format(Locale.US, "&e%.1fs", remMs / 1000.0);
            }
            case "unlocked_count":
                return String.valueOf(data.getOwnedPets().size());
            case "total_count":
                return String.valueOf(pm.getRegistry().getAllPets().size());
            default:
                return "";
        }
    }

    private String getEventPlaceholder(String param) {
        var em = DCCosmetics.getInstance().getEventManager();
        if (em == null) return "";

        var curCycle = em.getCurrentCycleEvent();
        var nextCycle = em.getNextWorldCycleEvent();

        switch (param) {
            case "active_name":
            case "worldcycle_name":
                return curCycle != null ? curCycle.getTitle() : "Sanctuary Dawn";
            case "active_world":
            case "worldcycle_world":
                return em.getActiveSpawnWorld();
            case "active_time":
            case "worldcycle_time":
                return em.getFormattedCycleTimeRemaining();
            case "active_time_raw":
            case "worldcycle_time_raw":
                return String.valueOf(em.getCycleSecondsRemaining());
            case "is_evacuating":
                return em.isEvacuating() ? "true" : "false";
            case "status":
            case "worldcycle_status":
                return em.isEvacuating() ? ("&cEvacuating (" + em.getCycleSecondsRemaining() + "s)") : "&aActive Realm";
            case "next_name":
            case "next_worldcycle_name":
                return nextCycle != null ? nextCycle.getTitle() : "The Void Convergence";
            case "next_world":
            case "next_worldcycle_world":
                return nextCycle != null ? nextCycle.getWorldName() : "spawn_bw";
            case "next_time":
                return em.getFormattedCycleTimeRemaining();
            case "koth_status":
                var koth = em.getMiniEvents().get("colosseum_koth");
                if (koth == null) return "&7Inactive";
                return koth.isRunning() ? ("&aActive (" + koth.getFormattedRemainingTime() + ")") : ("&7Starts in " + koth.getFormattedUntilStartTime());
            case "adapt_status":
                var adapt = em.getMiniEvents().get("adapt_exp_surge");
                if (adapt == null) return "&7Inactive";
                return adapt.isRunning() ? ("&aActive (" + adapt.getFormattedRemainingTime() + ")") : ("&7Starts in " + adapt.getFormattedUntilStartTime());
            case "iceboat_status":
                var boat = em.getMiniEvents().get("iceboat_rally");
                if (boat == null) return "&7Inactive";
                return boat.isRunning() ? ("&aActive (" + boat.getFormattedRemainingTime() + ")") : ("&7Starts in " + boat.getFormattedUntilStartTime());
            default:
                if (param.startsWith("mini_")) {
                    String eventId = param.substring(5);
                    var me = em.getMiniEvents().get(eventId);
                    if (me != null) {
                        return me.isRunning() ? ("&aActive (" + me.getFormattedRemainingTime() + ")") : ("&7In " + me.getFormattedUntilStartTime());
                    }
                }
                return "";
        }
    }

    private String getPityPlaceholder(Player player, String param) {
        var pity = DCCosmetics.getInstance().getPityManager();
        if (param.startsWith("progress_")) {
            return String.valueOf(pity.getProgress(player, param.substring(9)));
        }
        if (param.startsWith("max_")) {
            return String.valueOf(pity.getMax(param.substring(4)));
        }
        if (param.startsWith("odds_")) {
            return pity.getOdds(player, param.substring(5));
        }
        if (param.startsWith("pct_") || param.startsWith("cat_pct_")) {
            String cat = param.contains("cat_pct_") ? param.substring(8) : param.substring(4);
            return pity.getPercent(player, cat) + "%";
        }
        if (param.startsWith("drop_")) {
            var cat = pity.getCategory(param.substring(5));
            return cat != null ? cat.getDropName() : "";
        }
        if (param.startsWith("bar_")) {
            return pity.getProgressBar(player, param.substring(4));
        }
        return pity.getPlaceholder(player, param);
    }

    private String getCategoryPlaceholder(Player player, String cat, String field) {
        var rng = DCCosmetics.getInstance().getRngManager();
        RngModel.Category category = rng.getCategories().get(cat.toLowerCase());
        if (category == null) return "";

        UUID u = player.getUniqueId();
        int catXp = rng.getCategoryXp(u, cat);
        String selId = rng.getSelectedDrop(u, cat);
        RngModel.Drop selDrop = (selId != null && !selId.equalsIgnoreCase("none")) ? rng.getDrop(cat, selId) : null;

        int goal = selDrop != null ? selDrop.getGoalXp() : 10000;
        int pct = selDrop != null ? Math.min(100, Math.max(0, (int) Math.round(((double) catXp / goal) * 100))) : 0;

        switch (field) {
            case "name":
                return category.getName();
            case "desc":
                return category.getDescription();
            case "selected":
                return selDrop != null ? selDrop.getDisplayName() : "&7None Selected";
            case "xp":
                return String.format(Locale.US, "%,d", catXp);
            case "xp_raw":
                return String.valueOf(catXp);
            case "goal":
                return String.format(Locale.US, "%,d", goal);
            case "goal_raw":
                return String.valueOf(goal);
            case "pct":
                return pct + "%";
            case "pct_raw":
                return String.valueOf(pct);
            case "progress":
                return formatProgressBar(pct);
            case "mult":
                double mult = selDrop != null ? 1.0 + (2.0 * Math.min(1.0, (double) catXp / goal)) : 1.0;
                return String.format(Locale.US, "%.1fx", mult);
            default:
                return "";
        }
    }

    private String getDropPlaceholder(Player player, String cat, String dropId, String field) {
        var rng = DCCosmetics.getInstance().getRngManager();
        RngModel.Drop drop = rng.getDrop(cat, dropId);
        if (drop == null) return "";

        UUID u = player.getUniqueId();
        int catXp = rng.getCategoryXp(u, cat);
        String selId = rng.getSelectedDrop(u, cat);
        boolean isSelected = dropId.equalsIgnoreCase(selId);

        int goal = drop.getGoalXp();
        int pct = Math.min(100, Math.max(0, (int) Math.round(((double) catXp / goal) * 100)));

        switch (field) {
            case "is_selected":
                return isSelected ? "true" : "false";
            case "selected":
                return isSelected ? "&#10B981&lSELECTED" : "&7Not Selected";
            case "name":
                return drop.getDisplayName();
            case "chance":
                return String.format(Locale.US, "%.2f%%", drop.getBaseChancePct());
            case "xp":
                return String.format(Locale.US, "%,d", catXp);
            case "xp_raw":
                return String.valueOf(catXp);
            case "goal":
                return String.format(Locale.US, "%,d", goal);
            case "goal_raw":
                return String.valueOf(goal);
            case "pct":
                return pct + "%";
            case "pct_raw":
                return String.valueOf(pct);
            case "progress":
                return formatProgressBar(pct);
            case "mult":
                double mult = isSelected ? 1.0 + (2.0 * Math.min(1.0, (double) catXp / goal)) : 1.0;
                return String.format(Locale.US, "%.1fx", mult);
            default:
                return "";
        }
    }

    private String formatProgressBar(int pct) {
        int filled = Math.min(10, Math.max(0, pct / 10));
        StringBuilder sb = new StringBuilder("&a");
        for (int i = 0; i < filled; i++) sb.append("■");
        sb.append("&7");
        for (int i = filled; i < 10; i++) sb.append("□");
        return sb.toString();
    }

    public static Object getSkriptVar(String varName) {
        try {
            Class<?> varClass = Class.forName("ch.njol.skript.variables.Variables");
            java.lang.reflect.Method getVar = varClass.getMethod("getVariable", String.class, Class.forName("ch.njol.skript.lang.TriggerContext"), boolean.class);
            return getVar.invoke(null, varName, null, false);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int getSkriptQuestIndex(Player player) {
        Object val = getSkriptVar("dcnpc::player_quest_index::" + player.getUniqueId());
        if (val instanceof Number n) {
            return n.intValue();
        }
        return 1;
    }

    private String getNextNpcName(Player player) {
        int idx = getSkriptQuestIndex(player);
        if (idx > 25) {
            return "&aQuestline Completed";
        }
        Object node = getSkriptVar("dcnpc::timeline_order::" + idx);
        if (node instanceof String s) {
            String targetId = s.split(":")[0];
            Object nameVal = getSkriptVar("dcnpc::registry::" + targetId + "::name");
            if (nameVal instanceof String n) {
                return n;
            }
            return Character.toUpperCase(targetId.charAt(0)) + targetId.substring(1);
        }
        return "Corvus";
    }
}
