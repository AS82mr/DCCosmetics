package dc.dccosmetics.tpeffects;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.tpeffects.cinematic.CinematicTeleportEngine;
import dc.dccosmetics.tpeffects.config.TpEffectConfig;
import dc.dccosmetics.tpeffects.model.TpEffectEntry;
import dc.dccosmetics.wardrobe.WardrobeSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central coordinator for DCEssentials Teleportation Effects:
 * - Loads per-rarity YML configs and maintains the live TpEffectRegistry
 * - Persists selected & muted states in player PDC (with optional LuckPerms dc.seltpeffect.<id> sync)
 * - Dispatches MythicMobs effects and 3D Voxel Camera cinematics cleanly
 */
public final class TpEffectManager {

    private final DCCosmetics plugin;
    private final TpEffectConfig config;
    private final TpEffectRegistry registry;
    private final CinematicTeleportEngine cinematicEngine;

    private final NamespacedKey selectedKey;
    private final NamespacedKey mutedKey;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> suppressedUntilMs = new ConcurrentHashMap<>();

    private volatile boolean runtimeDisabled = false;

    public TpEffectManager(DCCosmetics plugin) {
        this.plugin = plugin;
        this.config = new TpEffectConfig(plugin);
        this.registry = new TpEffectRegistry();
        this.cinematicEngine = new CinematicTeleportEngine(plugin, config);
        this.selectedKey = new NamespacedKey(plugin, "selected_tpeffect");
        this.mutedKey = new NamespacedKey(plugin, "muted_tpeffect");
        reload();
    }

    public void reload() {
        List<TpEffectEntry> loaded = config.loadAll();
        registry.reload(loaded, config.getRarityOrder());
        plugin.getLogger().info("[TpEffects] Loaded " + loaded.size() + " teleportation effects across "
                + registry.getOrderedRarities(false).size() + " rarities.");
    }

    public void shutdown() {
        cinematicEngine.cleanupAll();
        cooldowns.clear();
        suppressedUntilMs.clear();
    }

    public TpEffectConfig getConfig() {
        return config;
    }

    public TpEffectRegistry getRegistry() {
        return registry;
    }

    public CinematicTeleportEngine getCinematicEngine() {
        return cinematicEngine;
    }

    public boolean isGloballyEnabled() {
        return config.isEnabled() && !runtimeDisabled;
    }

    public boolean isRuntimeDisabled() {
        return runtimeDisabled;
    }

    public void setRuntimeDisabled(boolean runtimeDisabled) {
        this.runtimeDisabled = runtimeDisabled;
    }

    // =========================================================================
    // PLAYER SELECTION, MUTE & PERMISSION SYNC
    // =========================================================================

    public String getSelectedEffectId(Player player) {
        if (player == null) return null;
        String stored = player.getPersistentDataContainer().get(selectedKey, PersistentDataType.STRING);
        return (stored != null && !stored.isBlank()) ? stored.toLowerCase(Locale.ROOT) : null;
    }

    public TpEffectEntry getSelectedEffect(Player player) {
        String id = getSelectedEffectId(player);
        if (id == null) return null;
        TpEffectEntry entry = registry.getById(id);
        if (entry == null || !entry.isEnabled()) return null;
        return entry;
    }

    public boolean setSelectedEffect(Player player, TpEffectEntry entry) {
        if (player == null || entry == null || !entry.isEnabled()) return false;
        String previousId = getSelectedEffectId(player);
        player.getPersistentDataContainer().set(selectedKey, PersistentDataType.STRING, entry.getId());
        syncLuckPermsSelection(player, previousId, entry.getId());
        return true;
    }

    public boolean clearSelectedEffect(Player player) {
        if (player == null) return false;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String previousId = pdc.get(selectedKey, PersistentDataType.STRING);
        boolean hadAny = previousId != null;
        pdc.remove(selectedKey);
        syncLuckPermsSelection(player, previousId, null);
        return hadAny;
    }

    public boolean isMuted(Player player) {
        if (player == null) return false;
        Byte val = player.getPersistentDataContainer().get(mutedKey, PersistentDataType.BYTE);
        return val != null && val == (byte) 1;
    }

    public void setMuted(Player player, boolean muted) {
        if (player == null) return;
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (muted) {
            pdc.set(mutedKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            pdc.remove(mutedKey);
        }
    }

    /**
     * On player join:
     * 1. Pre-warm async 64x64 skin voxel cache
     * 2. Migrate any legacy Skript dc.seltpeffect.<id> LP perm into PDC if PDC has no entry yet
     * 3. Ensure player still holds permission for their currently equipped effect
     */
    public void handlePlayerJoin(Player player) {
        if (player == null || !player.isOnline()) return;
        cinematicEngine.warmSkinCache(player);

        String pdcId = getSelectedEffectId(player);
        if (pdcId == null && config.isMigrateLegacyLuckPermsOnJoin()) {
            for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
                if (!info.getValue()) continue;
                String perm = info.getPermission().toLowerCase(Locale.ROOT);
                if (perm.startsWith("dc.seltpeffect.")) {
                    String candidateId = perm.substring("dc.seltpeffect.".length());
                    TpEffectEntry entry = registry.getById(candidateId);
                    if (entry != null && entry.isEnabled() && TpEffectRegistry.hasPermission(player, entry)) {
                        player.getPersistentDataContainer().set(selectedKey, PersistentDataType.STRING, entry.getId());
                        pdcId = entry.getId();
                        break;
                    }
                }
            }
        }

        if (pdcId != null) {
            TpEffectEntry entry = registry.getById(pdcId);
            if (entry != null && !TpEffectRegistry.hasPermission(player, entry)) {
                clearSelectedEffect(player);
            }
        }
    }

    private void syncLuckPermsSelection(Player player, String prevId, String newId) {
        if (!config.isSyncLuckPermsSelectedPermission()) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) return;

        Set<String> toUnset = new LinkedHashSet<>();
        if (prevId != null && !prevId.isBlank() && !prevId.equalsIgnoreCase(newId)) {
            toUnset.add("dc.seltpeffect." + prevId.toLowerCase(Locale.ROOT));
        }
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String perm = info.getPermission().toLowerCase(Locale.ROOT);
            if (perm.startsWith("dc.seltpeffect.")) {
                String idPart = perm.substring("dc.seltpeffect.".length());
                if (newId == null || !idPart.equalsIgnoreCase(newId)) {
                    toUnset.add(perm);
                }
            }
        }

        for (String perm : toUnset) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + player.getName() + " permission unset " + perm);
        }
        if (newId != null && !newId.isBlank()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + player.getName() + " permission set dc.seltpeffect." + newId.toLowerCase(Locale.ROOT) + " true");
        }
    }

    public boolean grantEffectPermission(Player target, TpEffectEntry entry) {
        if (target == null || entry == null) return false;
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + target.getName() + " permission set " + entry.getPermission() + " true");
            return true;
        }
        return false;
    }

    // =========================================================================
    // COOLDOWN & RUNTIME EXECUTION
    // =========================================================================

    public boolean isOnCooldown(Player player) {
        if (player == null) return false;
        double cdSec = config.getCooldownSeconds();
        if (cdSec <= 0.0) return false;
        Long last = cooldowns.get(player.getUniqueId());
        if (last == null) return false;
        long elapsedMs = System.currentTimeMillis() - last;
        return elapsedMs < (long) (cdSec * 1000.0);
    }

    public void markCooldown(Player player) {
        if (player != null) {
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    public void clearCooldown(UUID uuid) {
        if (uuid != null) {
            cooldowns.remove(uuid);
        }
    }

    /**
     * Suppresses automatic teleportation effects for a player for {@code durationMs} milliseconds.
     * Useful for multi-tick dash/charge abilities or async boss skill sequences.
     */
    public void suppressTemporarily(Player player, long durationMs) {
        if (player == null || durationMs <= 0L) return;
        long target = System.currentTimeMillis() + durationMs;
        suppressedUntilMs.merge(player.getUniqueId(), target, Math::max);
    }

    public boolean isTemporarilySuppressed(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        Long until = suppressedUntilMs.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            suppressedUntilMs.remove(uuid, until);
            return false;
        }
        return true;
    }

    public void clearTemporarySuppression(UUID uuid) {
        if (uuid != null) {
            suppressedUntilMs.remove(uuid);
        }
    }

    /**
     * Teleports a player without triggering any cosmetic teleportation effect or consuming TP effect cooldown.
     * Safe for item dash/warp abilities and boss skill movement mechanics.
     */
    public static boolean teleportWithoutEffect(Player player, Location dest) {
        if (player == null || dest == null || dest.getWorld() == null) return false;
        DCCosmetics plugin = DCCosmetics.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            return player.teleport(dest);
        }

        boolean hadMarker = player.hasMetadata("no_tpeffect");
        if (!hadMarker) {
            player.setMetadata("no_tpeffect", new FixedMetadataValue(plugin, true));
        }
        try {
            return player.teleport(dest);
        } finally {
            if (!hadMarker) {
                player.removeMetadata("no_tpeffect", plugin);
            }
        }
    }

    /**
     * Triggers a MythicMobs pair (departure immediately, arrival after arrivalDelayTicks) OR
     * launches the 3D Voxel Camera Cinematic sequence.
     */
    public void playStandardMythicEffect(Location from, Location to, TpEffectEntry entry) {
        if (entry == null || entry.isCinematic()) return;
        String mob = entry.getMythicMob();
        if (mob == null || mob.isBlank()) return;

        if (from != null && from.getWorld() != null) {
            spawnMythicMob(mob, from);
        }

        if (to != null && to.getWorld() != null) {
            int delay = config.getArrivalDelayTicks();
            Location arrLoc = to.clone();
            if (delay <= 0) {
                spawnMythicMob(mob, arrLoc);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> spawnMythicMob(mob, arrLoc), delay);
            }
        }
    }

    public void previewInWardrobe(WardrobeSession session, TpEffectEntry entry) {
        if (session == null || entry == null) return;
        cinematicEngine.stopPreview(session.getPlayer());

        if (entry.isCinematic()) {
            cinematicEngine.playWardrobePreview(session, entry);
        } else if (session.getMannequin() != null && session.getMannequin().getEntity() != null) {
            Location loc = session.getMannequin().getEntity().getLocation();
            spawnMythicMob(entry.getMythicMob(), loc);
        }
    }

    public void previewStationary(Player viewer, Location targetLoc, TpEffectEntry entry) {
        if (viewer == null || targetLoc == null || entry == null) return;
        if (entry.isCinematic()) {
            cinematicEngine.playStationaryPreview(viewer, targetLoc, entry);
        } else {
            spawnMythicMob(entry.getMythicMob(), targetLoc);
        }
    }

    public boolean toggleEnabled(TpEffectEntry entry, boolean newState) {
        if (entry == null) return false;
        entry.setEnabled(newState);
        return config.saveEffectBoolean(entry, "enabled", newState);
    }

    public boolean toggleHidden(TpEffectEntry entry, boolean newHidden) {
        if (entry == null) return false;
        entry.setHidden(newHidden);
        return config.saveEffectBoolean(entry, "hidden", newHidden);
    }

    public void spawnMythicMob(String mobId, Location loc) {
        if (mobId == null || mobId.isBlank() || loc == null || loc.getWorld() == null) return;

        // Clean API spawn first (matches SceneManager reflection pattern cleanly for soft-depend safety)
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            try {
                Class<?> mythicBukkitCls = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                Object inst = mythicBukkitCls.getMethod("inst").invoke(null);
                if (inst != null) {
                    Object mobManager = inst.getClass().getMethod("getMobManager").invoke(inst);
                    if (mobManager != null) {
                        Object spawned = mobManager.getClass().getMethod("spawnMob", String.class, Location.class)
                                .invoke(mobManager, mobId, loc);
                        if (spawned != null) {
                            return;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Console command fallback if API returned null or threw
        World world = loc.getWorld();
        Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                String.format(Locale.US, "mm mobs spawn %s 1 %s,%.2f,%.2f,%.2f",
                        mobId, world.getName(), loc.getX(), loc.getY(), loc.getZ())
        );
    }
}
