package dc.dcessentials.modules.curator;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * TEMPORARY MODEL & TEXTUREPACK CURATOR MODULE.
 * Replaces vfx_preview_wand.sk with live MEG + ItemsAdder scanning,
 * multi-spawn tracking (never loses previously spawned models on Shift+Left-Click),
 * Q/F marking for Cleanup & Favorites, and live Markdown path reports.
 *
 * Designed to be easy to remove once asset cleanup is complete.
 */
public final class CuratorManager implements Listener {

    public static final NamespacedKey WAND_KEY = new NamespacedKey("dcessentials", "curator_wand");
    public static final NamespacedKey SCENE_WAND_KEY = new NamespacedKey("dcessentials", "curator_scene_wand");

    private final DCCosmetics plugin;
    private final CuratorGUI gui;

    private final File curatorDir;
    private final File stateFile;
    private final File cleanupReportFile;
    private final File favoritesReportFile;

    private List<CuratorEntry> allEntries = new ArrayList<>();
    private final Map<String, CuratorEntry> entryByKey = new LinkedHashMap<>();
    private final Map<String, Integer> globalIndexByKey = new HashMap<>();
    private final Set<String> favoriteIds = new LinkedHashSet<>();
    private final Set<String> cleanupIds = new LinkedHashSet<>();

    private final Map<UUID, Integer> playerIndices = new HashMap<>();
    private final Map<UUID, Integer> playerSceneIndices = new HashMap<>();
    private final Map<UUID, CuratorEntry.SourceFilter> playerSourceFilters = new HashMap<>();
    private final Map<UUID, Boolean> playerAutoAnimate = new HashMap<>();
    private final Map<UUID, List<CuratorBridge.SpawnedPreview>> spawnedByPlayer = new HashMap<>();
    private final Map<UUID, Long> clickDebounce = new HashMap<>();

    public CuratorManager(DCCosmetics plugin) {
        this.plugin = plugin;
        this.gui = new CuratorGUI(this);
        this.curatorDir = new File(plugin.getDataFolder(), "curator");
        this.stateFile = new File(curatorDir, "curator_state.yml");
        this.cleanupReportFile = new File(curatorDir, "cleanup_report.md");
        this.favoritesReportFile = new File(curatorDir, "favorites_report.md");
    }

    public void start() {
        curatorDir.mkdirs();
        loadState();
        rescanAll();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(gui, plugin);
        plugin.getLogger().info("[Curator] Indexed " + allEntries.size() + " models/items (MEG + ItemsAdder). "
                + "Favorites=" + favoriteIds.size() + ", Cleanup=" + cleanupIds.size());
    }

    public void shutdown() {
        for (UUID uid : new ArrayList<>(spawnedByPlayer.keySet())) {
            List<CuratorBridge.SpawnedPreview> list = spawnedByPlayer.remove(uid);
            if (list != null) {
                for (CuratorBridge.SpawnedPreview sp : list) {
                    sp.cleanup();
                }
            }
        }
        saveState();
        writeReports();
    }

    public synchronized void rescanAll() {
        List<CuratorEntry> scanned = CuratorScanner.scanAll(plugin);
        this.allEntries = scanned;
        this.entryByKey.clear();
        this.globalIndexByKey.clear();
        for (int i = 0; i < scanned.size(); i++) {
            CuratorEntry e = scanned.get(i);
            String key = e.getId().toLowerCase(Locale.ROOT);
            this.entryByKey.put(key, e);
            this.globalIndexByKey.put(key, i + 1);
        }
        writeReports();
    }

    public int getGlobalIndex(CuratorEntry entry) {
        if (entry == null) return -1;
        return globalIndexByKey.getOrDefault(entry.getId().toLowerCase(Locale.ROOT), -1);
    }

    // ──────────────────────────────────────────────────────────────
    // Persistence & Live Agent-Readable Export Reports
    // ──────────────────────────────────────────────────────────────

    private void loadState() {
        favoriteIds.clear();
        cleanupIds.clear();
        if (!stateFile.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(stateFile);
        for (String s : yml.getStringList("favorites")) {
            if (s != null && !s.isBlank()) favoriteIds.add(s.toLowerCase(Locale.ROOT));
        }
        for (String s : yml.getStringList("cleanup")) {
            if (s != null && !s.isBlank()) cleanupIds.add(s.toLowerCase(Locale.ROOT));
        }
    }

    public void saveState() {
        curatorDir.mkdirs();
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("favorites", toCanonicalList(favoriteIds));
        yml.set("cleanup", toCanonicalList(cleanupIds));
        try {
            yml.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[Curator] Failed saving curator_state.yml: " + e.getMessage());
        }
    }

    private List<String> toCanonicalList(Set<String> keys) {
        List<String> out = new ArrayList<>();
        for (String k : keys) {
            CuratorEntry entry = entryByKey.get(k);
            out.add(entry != null ? entry.getId() : k);
        }
        return out;
    }

    public void writeReports() {
        curatorDir.mkdirs();
        writeMarkdownReport(cleanupReportFile, "Cleanup / Removal Candidates",
                "Models and ItemsAdder entries marked via `Q` or Curator GUI for removal from Survival / Texturepack.",
                cleanupIds);
        writeMarkdownReport(favoritesReportFile, "Favorite Models & Furniture",
                "Models and ItemsAdder entries bookmarked via `F` or Curator GUI for reuse.",
                favoriteIds);
    }

    private void writeMarkdownReport(File target, String title, String subtitle, Set<String> keys) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n");
        sb.append("> ").append(subtitle).append("\n");
        sb.append("> Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append("> Total entries: ").append(keys.size()).append("\n\n");

        if (keys.isEmpty()) {
            sb.append("_No entries marked yet._\n");
        } else {
            int idx = 1;
            for (String k : keys) {
                CuratorEntry e = entryByKey.get(k);
                if (e == null) {
                    sb.append(idx++).append(". `").append(k).append("` *(no longer found on disk)*\n");
                    continue;
                }
                int gIdx = getGlobalIndex(e);
                sb.append("### ").append(idx++).append(". `").append(e.getId()).append("`");
                if (gIdx > 0) sb.append(" (Global #").append(gIdx).append(")");
                sb.append("\n");
                sb.append("- **Source:** ").append(e.getSourceType().getLabel()).append("\n");
                sb.append("- **Pack:** `").append(e.getPackName()).append("`\n");
                sb.append("- **Config / Blueprint:** `").append(e.getConfigPath()).append("`\n");
                if (!e.getAssetPaths().isEmpty()) {
                    sb.append("- **Associated Assets:**\n");
                    for (String asset : e.getAssetPaths()) {
                        sb.append("  - `").append(asset).append("`\n");
                    }
                }
                sb.append("\n");
            }
        }

        try {
            Files.writeString(target.toPath(), sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("[Curator] Failed writing report " + target.getName() + ": " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Filtering & Selection
    // ──────────────────────────────────────────────────────────────

    public List<CuratorEntry> getFilteredEntries(CuratorEntry.FilterMode filterMode, CuratorEntry.SourceFilter sourceFilter) {
        List<CuratorEntry> out = new ArrayList<>();
        for (CuratorEntry e : allEntries) {
            if (!sourceFilter.matches(e.getSourceType())) continue;
            if (filterMode == CuratorEntry.FilterMode.FAVORITES && !isFavorite(e)) continue;
            if (filterMode == CuratorEntry.FilterMode.CLEANUP && !isCleanup(e)) continue;
            out.add(e);
        }
        return out;
    }

    public int countEntries(CuratorEntry.FilterMode filterMode, CuratorEntry.SourceFilter sourceFilter) {
        return getFilteredEntries(filterMode, sourceFilter).size();
    }

    public CuratorEntry.SourceFilter getPlayerSourceFilter(Player p) {
        return playerSourceFilters.getOrDefault(p.getUniqueId(), CuratorEntry.SourceFilter.ALL);
    }

    public void setPlayerSourceFilter(Player p, CuratorEntry.SourceFilter filter) {
        playerSourceFilters.put(p.getUniqueId(), filter);
        playerIndices.put(p.getUniqueId(), 0);
        showCurrentActionBar(p);
    }

    public CuratorEntry getSelectedEntry(Player p) {
        List<CuratorEntry> list = getFilteredEntries(CuratorEntry.FilterMode.ALL, getPlayerSourceFilter(p));
        if (list.isEmpty()) return null;
        int idx = playerIndices.getOrDefault(p.getUniqueId(), 0);
        if (idx < 0 || idx >= list.size()) {
            idx = Math.floorMod(idx, list.size());
            playerIndices.put(p.getUniqueId(), idx);
        }
        return list.get(idx);
    }

    public void selectEntry(Player p, CuratorEntry target) {
        List<CuratorEntry> list = getFilteredEntries(CuratorEntry.FilterMode.ALL, getPlayerSourceFilter(p));
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equalsIgnoreCase(target.getId())) {
                playerIndices.put(p.getUniqueId(), i);
                showCurrentActionBar(p);
                return;
            }
        }
        // Fallback: switch player source filter to ALL so jump always works
        playerSourceFilters.put(p.getUniqueId(), CuratorEntry.SourceFilter.ALL);
        for (int i = 0; i < allEntries.size(); i++) {
            if (allEntries.get(i).getId().equalsIgnoreCase(target.getId())) {
                playerIndices.put(p.getUniqueId(), i);
                showCurrentActionBar(p);
                return;
            }
        }
    }

    public boolean jumpByQuery(Player p, String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        for (CuratorEntry e : allEntries) {
            if (e.getId().toLowerCase(Locale.ROOT).contains(lower)) {
                selectEntry(p, e);
                DCMessage.sendGeneral(p, "Jumped curator cursor to &#FFFFFF" + e.getId() + " &7[" + e.getSourceType().getLabel() + "]");
                return true;
            }
        }
        return false;
    }

    private void cycle(Player p, int delta) {
        List<CuratorEntry> list = getFilteredEntries(CuratorEntry.FilterMode.ALL, getPlayerSourceFilter(p));
        if (list.isEmpty()) {
            DCMessage.sendError(p, "No models found in current filter (" + getPlayerSourceFilter(p).getLabel() + ").");
            return;
        }
        int cur = playerIndices.getOrDefault(p.getUniqueId(), 0);
        int next = Math.floorMod(cur + delta, list.size());
        playerIndices.put(p.getUniqueId(), next);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, delta > 0 ? 1.35f : 0.95f);
        showCurrentActionBar(p);
    }

    public boolean isFavorite(CuratorEntry e) {
        return favoriteIds.contains(e.getId().toLowerCase(Locale.ROOT));
    }

    public boolean isCleanup(CuratorEntry e) {
        return cleanupIds.contains(e.getId().toLowerCase(Locale.ROOT));
    }

    public void toggleFavorite(Player p, CuratorEntry e) {
        if (e == null) return;
        String key = e.getId().toLowerCase(Locale.ROOT);
        if (favoriteIds.remove(key)) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            p.sendMessage(ColorUtil.colorize("&7[Curator] Removed &f" + e.getId() + " &7from &a★ Favorites&7."));
        } else {
            favoriteIds.add(key);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
            p.sendMessage(ColorUtil.colorize("&a[Curator] ★ Added &f" + e.getId() + " &ato Favorites! &8(" + e.getConfigPath() + ")"));
        }
        saveState();
        writeReports();
        showCurrentActionBar(p);
    }

    public void toggleCleanup(Player p, CuratorEntry e) {
        if (e == null) return;
        String key = e.getId().toLowerCase(Locale.ROOT);
        if (cleanupIds.remove(key)) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 1.1f);
            p.sendMessage(ColorUtil.colorize("&7[Curator] Unmarked &f" + e.getId() + " &7from &c✖ Cleanup&7."));
        } else {
            cleanupIds.add(key);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.7f, 1.0f);
            p.sendMessage(ColorUtil.colorize("&c[Curator] ✖ Marked &f" + e.getId() + " &cfor Cleanup! &8(" + e.getConfigPath() + ")"));
        }
        saveState();
        writeReports();
        showCurrentActionBar(p);
    }

    // ──────────────────────────────────────────────────────────────
    // Spawning & Full Multi-Spawn Cleanup
    // ──────────────────────────────────────────────────────────────

    public void spawnSelected(Player p) {
        List<CuratorEntry> list = getFilteredEntries(CuratorEntry.FilterMode.ALL, getPlayerSourceFilter(p));
        CuratorEntry entry = getSelectedEntry(p);
        if (entry == null) {
            DCMessage.sendError(p, "No model selected in current filter.");
            return;
        }
        int idx = playerIndices.getOrDefault(p.getUniqueId(), 0) + 1;

        Location loc = p.getLocation().clone();
        org.bukkit.util.Vector dir = loc.getDirection().setY(0).normalize().multiply(3.5);
        loc.add(dir);
        loc.setYaw((p.getLocation().getYaw() + 180.0f) % 360.0f);
        loc.setPitch(0.0f);

        CuratorBridge.SpawnedPreview spawned = CuratorBridge.spawnPreview(entry, loc, idx, list.size(), isAutoAnimate(p));
        if (spawned == null) {
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.2f);
            DCMessage.sendError(p, "Could not spawn &#FFFFFF" + entry.getId() + " &#EF4444(" + entry.getSourceType().getLabel() + "). Check if loaded in runtime.");
            return;
        }

        spawnedByPlayer.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(spawned);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.4f);
        if (spawned.getSourceType() == CuratorEntry.SourceType.MEG && !spawned.getAnimations().isEmpty()) {
            sendAnimationStatusChat(p, spawned);
        }
        showCurrentActionBar(p);
    }

    public boolean isAutoAnimate(Player p) {
        return playerAutoAnimate.getOrDefault(p.getUniqueId(), Boolean.TRUE);
    }

    public void toggleAutoAnimate(Player p) {
        boolean next = !isAutoAnimate(p);
        playerAutoAnimate.put(p.getUniqueId(), next);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, next ? 1.4f : 0.9f);
        DCMessage.sendGeneral(p, "Curator Auto-Animate on Spawn: " + (next ? "&aON &7(loops default animation)" : "&cOFF &7(static pose)"));
    }

    private CuratorBridge.SpawnedPreview getLatestMegPreview(Player p) {
        List<CuratorBridge.SpawnedPreview> list = spawnedByPlayer.get(p.getUniqueId());
        if (list == null || list.isEmpty()) return null;
        for (int i = list.size() - 1; i >= 0; i--) {
            CuratorBridge.SpawnedPreview sp = list.get(i);
            if (sp.getSourceType() == CuratorEntry.SourceType.MEG && sp.getActiveModel() != null) {
                return sp;
            }
        }
        return null;
    }

    public void cycleAnimationOnLatest(Player p) {
        CuratorBridge.SpawnedPreview latest = getLatestMegPreview(p);
        if (latest == null) {
            CuratorEntry selected = getSelectedEntry(p);
            if (selected != null && selected.getSourceType() == CuratorEntry.SourceType.MEG) {
                spawnSelected(p);
                latest = getLatestMegPreview(p);
            }
            if (latest == null) {
                DCMessage.sendError(p, "Spawn a ModelEngine blueprint first (Right-Click with wand) to play its animations.");
                return;
            }
        }

        List<String> anims = latest.getAnimations();
        if (anims.isEmpty()) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            DCMessage.sendWarning(p, "Model &#FFFFFF" + latest.getEntryId() + " &#FDE047has no animations defined.");
            return;
        }

        int cur = latest.getCurrentAnimIndex();
        if (anims.size() == 1) {
            if (cur == 0) {
                // Single-animation VFX: replay from start cleanly on press
                CuratorBridge.playAnimationAtIndex(latest, 0, true);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.5f);
                p.sendMessage(ColorUtil.colorize("&#34D399[Curator] ▶ Replaying &f" + latest.getEntryId() + " &7→ &#FBBF24" + anims.get(0)));
            } else {
                CuratorBridge.playAnimationAtIndex(latest, 0, true);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.4f);
                p.sendMessage(ColorUtil.colorize("&#34D399[Curator] ▶ Playing &f" + latest.getEntryId() + " &7→ &#FBBF24" + anims.get(0)));
            }
            showCurrentActionBar(p);
            return;
        }

        // Cycle 0 .. N-1 -> Static (-1) -> 0
        int next = cur + 1;
        if (next >= anims.size()) {
            CuratorBridge.stopAnimations(latest);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 1.0f);
            p.sendMessage(ColorUtil.colorize("&7[Curator] ⏹ Static Pose on &f" + latest.getEntryId() + " &8(Shift+Q for next cycle)"));
        } else {
            CuratorBridge.playAnimationAtIndex(latest, next, true);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.15f + (0.04f * Math.min(next, 12)));
            p.sendMessage(ColorUtil.colorize("&#34D399[Curator] ▶ &f" + latest.getEntryId() + " &7Anim &#FBBF24" + (next + 1) + "/" + anims.size() + ": &f" + anims.get(next)));
        }
        showCurrentActionBar(p);
    }

    public boolean playNamedAnimationOnLatest(Player p, String animName) {
        CuratorBridge.SpawnedPreview latest = getLatestMegPreview(p);
        if (latest == null) {
            DCMessage.sendError(p, "No active spawned ModelEngine preview found.");
            return false;
        }
        boolean ok = CuratorBridge.playNamedAnimation(latest, animName, true);
        if (ok) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.4f);
            DCMessage.sendGeneral(p, "Playing &#FFFFFF" + animName + " &#0CBF93on &#FFFFFF" + latest.getEntryId() + "&#0CBF93.");
            showCurrentActionBar(p);
        } else {
            DCMessage.sendError(p, "Animation '" + animName + "' not found on " + latest.getEntryId() + ".");
        }
        return ok;
    }

    public void stopAnimationOnLatest(Player p) {
        CuratorBridge.SpawnedPreview latest = getLatestMegPreview(p);
        if (latest == null) {
            DCMessage.sendError(p, "No active spawned ModelEngine preview found.");
            return;
        }
        CuratorBridge.stopAnimations(latest);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.9f);
        DCMessage.sendGeneral(p, "Stopped animations (Static Pose) on &#FFFFFF" + latest.getEntryId() + "&#0CBF93.");
        showCurrentActionBar(p);
    }

    public void sendClickableAnimationList(Player p) {
        CuratorBridge.SpawnedPreview latest = getLatestMegPreview(p);
        CuratorEntry selected = getSelectedEntry(p);
        String targetId;
        List<String> anims;
        if (latest != null) {
            targetId = latest.getEntryId();
            anims = latest.getAnimations();
        } else if (selected != null) {
            targetId = selected.getId();
            anims = selected.getAnimations();
        } else {
            DCMessage.sendError(p, "Select or spawn a ModelEngine blueprint first.");
            return;
        }

        if (anims.isEmpty()) {
            DCMessage.sendWarning(p, "No animations listed for &#FFFFFF" + targetId + "&#FDE047.");
            return;
        }

        p.sendMessage(ColorUtil.colorize("&8&m────────────────────────────────────────"));
        p.sendMessage(ColorUtil.colorize("&#38BDF8&lAnimations for &f" + targetId + " &8(" + anims.size() + ") &7— click any to play:"));
        Component line = Component.empty();
        for (int i = 0; i < anims.size(); i++) {
            String anim = anims.get(i);
            boolean active = latest != null && anim.equalsIgnoreCase(latest.getCurrentAnimName());
            String label = (active ? "&#34D399[▶ " + anim + "]" : "&7[" + anim + "]") + " ";
            Component btn = ColorUtil.toComponent(label)
                    .clickEvent(ClickEvent.runCommand("/dce curator anim play " + anim))
                    .hoverEvent(HoverEvent.showText(ColorUtil.toComponent("&aClick to play &f" + anim)));
            line = line.append(btn);
            if ((i + 1) % 4 == 0 && i + 1 < anims.size()) {
                p.sendMessage(line);
                line = Component.empty();
            }
        }
        if (!line.equals(Component.empty())) {
            p.sendMessage(line);
        }
        p.sendMessage(ColorUtil.colorize("&8&m────────────────────────────────────────"));
    }

    private void sendAnimationStatusChat(Player p, CuratorBridge.SpawnedPreview sp) {
        List<String> anims = sp.getAnimations();
        if (anims.isEmpty()) return;
        String current = sp.getCurrentAnimName();
        String curLabel = current != null ? "&#34D399▶ " + current : "&7Static Pose";
        Component msg = ColorUtil.toComponent("&7[Curator] &f" + sp.getEntryId()
                        + " &8(" + anims.size() + " anims: " + curLabel + "&8) ")
                .append(ColorUtil.toComponent("&#FBBF24[Shift+Q: Cycle]")
                        .clickEvent(ClickEvent.runCommand("/dce curator anim next"))
                        .hoverEvent(HoverEvent.showText(ColorUtil.toComponent("&eClick or press &fShift+Q &eto cycle animations"))))
                .append(ColorUtil.toComponent(" &#38BDF8[All Anims]")
                        .clickEvent(ClickEvent.runCommand("/dce curator anim list"))
                        .hoverEvent(HoverEvent.showText(ColorUtil.toComponent("&bClick to list all " + anims.size() + " animations"))));
        p.sendMessage(msg);
    }

    public void clearPlayerPreviews(Player p, boolean notify) {
        List<CuratorBridge.SpawnedPreview> list = spawnedByPlayer.remove(p.getUniqueId());
        int tracked = 0;
        if (list != null) {
            tracked = list.size();
            for (CuratorBridge.SpawnedPreview sp : list) {
                sp.cleanup();
            }
        }
        int nearby = CuratorBridge.purgeNearbyPreviewEntities(p.getLocation(), 48.0);
        if (notify) {
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 0.6f, 1.2f);
            DCMessage.sendGeneral(p, "Cleared &#FFFFFF" + tracked + " &#0CBF93tracked preview(s)" + (nearby > 0 ? " &7(+ " + nearby + " stray entities)" : "") + "&#0CBF93.");
        }
    }

    public int getSpawnedCount(Player p) {
        List<CuratorBridge.SpawnedPreview> list = spawnedByPlayer.get(p.getUniqueId());
        return list == null ? 0 : list.size();
    }

    public void openGui(Player p, CuratorEntry.FilterMode mode) {
        gui.open(p, mode, getPlayerSourceFilter(p), 0);
    }

    private void showCurrentActionBar(Player p) {
        List<CuratorEntry> list = getFilteredEntries(CuratorEntry.FilterMode.ALL, getPlayerSourceFilter(p));
        CuratorEntry e = getSelectedEntry(p);
        if (e == null) {
            p.sendActionBar(ColorUtil.toComponent("&cNo models in filter " + getPlayerSourceFilter(p).getLabel()));
            return;
        }
        int idx = playerIndices.getOrDefault(p.getUniqueId(), 0) + 1;
        String badges = (isFavorite(e) ? " &a★FAV" : "") + (isCleanup(e) ? " &c✖DEL" : "");
        CuratorBridge.SpawnedPreview latest = getLatestMegPreview(p);
        String animTag = "";
        if (latest != null && latest.getCurrentAnimName() != null) {
            animTag = " &8| &#34D399▶ " + latest.getCurrentAnimName()
                    + " &7(" + (latest.getCurrentAnimIndex() + 1) + "/" + Math.max(1, latest.getAnimations().size()) + ")";
        } else if (e.getSourceType() == CuratorEntry.SourceType.MEG && !e.getAnimations().isEmpty()) {
            animTag = " &8| &7" + e.getAnimations().size() + " anims";
        }
        p.sendActionBar(ColorUtil.toComponent(
                "&e[" + idx + "/" + list.size() + "] &f" + e.getId()
                        + " &8(" + e.getSourceType().getLabel() + ")"
                        + badges
                        + animTag
                        + " &7| Active: &b" + getSpawnedCount(p)
        ));
    }

    // ──────────────────────────────────────────────────────────────
    // Wand Item & Input Listeners
    // ──────────────────────────────────────────────────────────────

    public ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.toItemComponent("&#FBBF24&lModel & Texture Curator Wand"));
            meta.lore(ColorUtil.toItemLore(List.of(
                    "&7Live MEG + ItemsAdder Model Browser",
                    "&8&m─────────────────────────────",
                    "&eRight-Click &8» &fSpawn Preview (auto-animates & stacks)",
                    "&eLeft-Click &8» &fNext Model (+1)",
                    "&eShift + Right-Click &8» &fPrevious Model (-1)",
                    "&cShift + Left-Click &8» &fClear ALL Previews",
                    "&a[Shift + Q] &8» &e▶ Cycle / Replay Animation",
                    "&b[F] Swap Key &8» &a★ Toggle Favorite",
                    "&4[Q] Drop Key &8» &c✖ Toggle Cleanup / Remove",
                    "&d[Shift + F] &8» &fOpen Curator GUI & Filters",
                    "&8&m─────────────────────────────",
                    "&7Indexed: &f" + allEntries.size() + " entries &7(/dce curator)"
            )));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(WAND_KEY, PersistentDataType.BYTE, (byte) 1);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    public List<CuratorEntry> getSceneEntries() {
        List<CuratorEntry> out = new ArrayList<>();
        for (CuratorEntry e : allEntries) {
            if (e.getSourceType() != CuratorEntry.SourceType.MEG) continue;
            String packLower = e.getPackName().toLowerCase(Locale.ROOT);
            String idLower = e.getId().toLowerCase(Locale.ROOT);
            if (packLower.startsWith("scene") || idLower.startsWith("scene")) {
                out.add(e);
            }
        }
        return out;
    }

    public ItemStack createSceneWand() {
        List<CuratorEntry> scenes = getSceneEntries();
        ItemStack wand = new ItemStack(Material.END_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.toItemComponent("&#38BDF8&lScenes Subgroup Lineup Wand"));
            meta.lore(ColorUtil.toItemLore(List.of(
                    "&7Spawns EVERY pose/animation of a Scene side-by-side!",
                    "&8&m─────────────────────────────",
                    "&eRight-Click &8» &fSpawn ALL Subgroup Animations in a Row",
                    "&eLeft-Click &8» &fNext Scene Blueprint (+1)",
                    "&eShift + Right-Click &8» &fPrevious Scene Blueprint (-1)",
                    "&cShift + Left-Click &8» &fClear ALL Spawned Lineups",
                    "&b[F] Swap Key &8» &a★ Toggle Favorite",
                    "&4[Q] Drop Key &8» &c✖ Toggle Cleanup / Remove",
                    "&d[Shift + F] &8» &fOpen Curator GUI",
                    "&8&m─────────────────────────────",
                    "&7Scenes Indexed: &f" + scenes.size() + " blueprints &7(/dce curator scenewand)"
            )));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(SCENE_WAND_KEY, PersistentDataType.BYTE, (byte) 1);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    public boolean isCuratorWand(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(WAND_KEY, PersistentDataType.BYTE);
    }

    public boolean isSceneWand(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(SCENE_WAND_KEY, PersistentDataType.BYTE);
    }

    private boolean isAnyWand(ItemStack item) {
        return isCuratorWand(item) || isSceneWand(item);
    }

    public CuratorEntry getSelectedSceneEntry(Player p) {
        List<CuratorEntry> scenes = getSceneEntries();
        if (scenes.isEmpty()) return null;
        int idx = playerSceneIndices.getOrDefault(p.getUniqueId(), 0);
        if (idx < 0 || idx >= scenes.size()) {
            idx = Math.floorMod(idx, scenes.size());
            playerSceneIndices.put(p.getUniqueId(), idx);
        }
        return scenes.get(idx);
    }

    private void showSceneActionBar(Player p) {
        List<CuratorEntry> scenes = getSceneEntries();
        CuratorEntry e = getSelectedSceneEntry(p);
        if (e == null) {
            p.sendActionBar(ColorUtil.toComponent("&cNo Scenes blueprints indexed."));
            return;
        }
        int idx = playerSceneIndices.getOrDefault(p.getUniqueId(), 0) + 1;
        String badges = (isFavorite(e) ? " &a★FAV" : "") + (isCleanup(e) ? " &c✖DEL" : "");
        p.sendActionBar(ColorUtil.toComponent(
                "&#38BDF8[Scene " + idx + "/" + scenes.size() + "] &f" + e.getId()
                        + " &8(#" + getGlobalIndex(e) + " • " + Math.max(1, e.getAnimations().size()) + " poses)"
                        + badges
                        + " &7| Active: &b" + getSpawnedCount(p)
        ));
    }

    private void cycleScene(Player p, int delta) {
        List<CuratorEntry> scenes = getSceneEntries();
        if (scenes.isEmpty()) {
            DCMessage.sendError(p, "No Scenes blueprints found.");
            return;
        }
        int cur = playerSceneIndices.getOrDefault(p.getUniqueId(), 0);
        int next = Math.floorMod(cur + delta, scenes.size());
        playerSceneIndices.put(p.getUniqueId(), next);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, delta > 0 ? 1.35f : 0.95f);
        showSceneActionBar(p);
    }

    public void spawnSceneLineup(Player p) {
        List<CuratorEntry> scenes = getSceneEntries();
        CuratorEntry entry = getSelectedSceneEntry(p);
        if (entry == null) {
            DCMessage.sendError(p, "No Scenes blueprint selected.");
            return;
        }

        List<String> anims = entry.getAnimations();
        int count = Math.max(1, anims.size());
        int sceneIdx = playerSceneIndices.getOrDefault(p.getUniqueId(), 0) + 1;
        int gIdx = Math.max(1, getGlobalIndex(entry));

        Location origin = p.getLocation().clone();
        org.bukkit.util.Vector forward = origin.getDirection().setY(0).normalize();
        if (forward.lengthSquared() < 1e-4) forward = new org.bukkit.util.Vector(0, 0, 1);
        org.bukkit.util.Vector right = new org.bukkit.util.Vector(-forward.getZ(), 0, forward.getX()).normalize();
        float facePlayerYaw = (p.getLocation().getYaw() + 180.0f) % 360.0f;

        int perRow = Math.min(count, 8);
        double spacing = 2.25;
        int spawnedCount = 0;

        for (int i = 0; i < count; i++) {
            int row = i / perRow;
            int col = i % perRow;
            int colsInThisRow = (row == (count - 1) / perRow) ? (count - row * perRow) : perRow;
            double lateralOffset = (col - (colsInThisRow - 1) / 2.0) * spacing;
            double forwardOffset = 3.8 + (row * 2.8);

            Location spot = origin.clone()
                    .add(forward.clone().multiply(forwardOffset))
                    .add(right.clone().multiply(lateralOffset));
            spot.setYaw(facePlayerYaw);
            spot.setPitch(0.0f);

            CuratorBridge.SpawnedPreview sp = CuratorBridge.spawnPreview(entry, spot, gIdx, allEntries.size(), false);
            if (sp == null) continue;

            if (!anims.isEmpty()) {
                CuratorBridge.playAnimationAtIndex(sp, i, true);
            }
            spawnedByPlayer.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(sp);
            spawnedCount++;
        }

        if (spawnedCount == 0) {
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.2f);
            DCMessage.sendError(p, "Could not spawn Scene lineup for &#FFFFFF" + entry.getId() + "&#EF4444.");
            return;
        }

        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.3f);
        DCMessage.sendGeneral(p, "Spawned &#FFFFFF" + spawnedCount + " &#0CBF93subgroup pose(s) for Scene &#FFFFFF" + entry.getId()
                + " &7[" + sceneIdx + "/" + scenes.size() + " | Global #" + gIdx + "]");
        showSceneActionBar(p);
    }

    private boolean isDebounced(Player p) {
        long now = System.currentTimeMillis();
        Long last = clickDebounce.get(p.getUniqueId());
        if (last != null && (now - last) < 120L) return true;
        clickDebounce.put(p.getUniqueId(), now);
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isAnyWand(hand)) return;

        e.setCancelled(true);
        if (isDebounced(p)) return;

        Action action = e.getAction();
        boolean left = (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);
        boolean right = (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK);

        handleWandClick(p, isSceneWand(hand), left, right);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractAtEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isAnyWand(hand)) return;

        e.setCancelled(true);
        if (isDebounced(p)) return;
        handleWandClick(p, isSceneWand(hand), false, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageEntity(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isAnyWand(hand)) return;

        e.setCancelled(true);
        if (isDebounced(p)) return;
        handleWandClick(p, isSceneWand(hand), true, false);
    }

    private void handleWandClick(Player p, boolean sceneMode, boolean left, boolean right) {
        boolean sneak = p.isSneaking();
        if (right && !sneak) {
            if (sceneMode) spawnSceneLineup(p);
            else spawnSelected(p);
        } else if (left && !sneak) {
            if (sceneMode) cycleScene(p, 1);
            else cycle(p, 1);
        } else if (right && sneak) {
            if (sceneMode) cycleScene(p, -1);
            else cycle(p, -1);
        } else if (left && sneak) {
            clearPlayerPreviews(p, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDropKey(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        ItemStack dropped = e.getItemDrop().getItemStack();
        if (!isAnyWand(dropped)) return;
        e.setCancelled(true);

        boolean sceneMode = isSceneWand(dropped);
        if (p.isSneaking()) {
            if (sceneMode) {
                spawnSceneLineup(p);
            } else {
                cycleAnimationOnLatest(p);
            }
        } else {
            CuratorEntry selected = sceneMode ? getSelectedSceneEntry(p) : getSelectedEntry(p);
            if (selected != null) {
                toggleCleanup(p, selected);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapKey(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        ItemStack main = p.getInventory().getItemInMainHand();
        boolean anyWand = isAnyWand(e.getOffHandItem()) || isAnyWand(e.getMainHandItem()) || isAnyWand(main);
        if (!anyWand) return;

        boolean sceneMode = isSceneWand(e.getOffHandItem()) || isSceneWand(main);
        e.setCancelled(true);
        if (p.isSneaking()) {
            if (sceneMode) {
                setPlayerSourceFilter(p, CuratorEntry.SourceFilter.MEG);
            }
            openGui(p, CuratorEntry.FilterMode.ALL);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.3f);
        } else {
            CuratorEntry selected = sceneMode ? getSelectedSceneEntry(p) : getSelectedEntry(p);
            if (selected != null) {
                toggleFavorite(p, selected);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        clearPlayerPreviews(e.getPlayer(), false);
        playerIndices.remove(e.getPlayer().getUniqueId());
        playerSceneIndices.remove(e.getPlayer().getUniqueId());
        playerSourceFilters.remove(e.getPlayer().getUniqueId());
        playerAutoAnimate.remove(e.getPlayer().getUniqueId());
        clickDebounce.remove(e.getPlayer().getUniqueId());
    }
}
