package dc.dcessentials.modules.teleportation;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Persistence coordinator for KeystoneProfile (Task P1-02).
 * Ensures main-thread snapshotting, periodic dirty flushes, FIFO quit-before-join ordering,
 * and inline shutdown flush before DatabaseManager.close().
 */
public class KeystonePersistence {

    private final TeleportationManager manager;
    private BukkitTask flushTask;

    public KeystonePersistence(TeleportationManager manager) {
        this.manager = manager;
    }

    public void start(int flushIntervalTicks) {
        stopTask();
        int ticks = Math.max(20, flushIntervalTicks);
        this.flushTask = Bukkit.getScheduler().runTaskTimer(
                DCCosmetics.getInstance(),
                () -> flushDirty(false),
                ticks,
                ticks
        );
    }

    public void stopTask() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
    }

    public KeystoneSnapshot snapshot(KeystoneProfile profile) {
        return profile != null ? profile.toSnapshot() : null;
    }

    public void loadAsync(UUID uuid, Consumer<KeystoneProfile> afterApplyMainThread) {
        KeystoneProfile profile = manager.getProfile(uuid);
        DatabaseManager db = DCCosmetics.getInstance().getDatabaseManager();
        if (db == null) {
            profile.setLoaded(true);
            if (afterApplyMainThread != null) afterApplyMainThread.accept(profile);
            return;
        }
        db.loadKeystoneSnapshot(uuid, opt -> {
            KeystoneProfile current = manager.getProfile(uuid);
            if (!(current.isLoaded() && current.isDirty())) {
                if (opt.isPresent()) {
                    current.applySnapshot(opt.get());
                } else {
                    current.setLoaded(true);
                }
            }
            if (afterApplyMainThread != null) {
                afterApplyMainThread.accept(current);
            }
        });
    }

    public void saveNow(KeystoneProfile profile) {
        if (profile == null) return;
        KeystoneSnapshot snap = profile.toSnapshot();
        profile.setDirty(false);
        DatabaseManager db = DCCosmetics.getInstance().getDatabaseManager();
        if (db != null) {
            db.saveKeystoneSnapshot(snap);
        }
    }

    public void onPlayerQuit(UUID uuid) {
        if (uuid == null) return;
        KeystoneProfile removed = manager.evictProfile(uuid);
        if (removed != null && removed.isLoaded()) {
            KeystoneSnapshot snap = removed.toSnapshot();
            removed.setDirty(false);
            DatabaseManager db = DCCosmetics.getInstance().getDatabaseManager();
            if (db != null) {
                db.saveKeystoneSnapshot(snap);
            }
        }
    }

    public void flushDirty(boolean sync) {
        DatabaseManager db = DCCosmetics.getInstance().getDatabaseManager();
        if (db == null) return;

        List<KeystoneSnapshot> batch = new ArrayList<>();
        for (KeystoneProfile profile : manager.getAllProfiles()) {
            if (profile.isLoaded() && profile.isDirty()) {
                synchronized (profile) {
                    if (profile.isDirty()) {
                        batch.add(profile.toSnapshot());
                        profile.setDirty(false);
                    }
                }
            }
        }
        if (!batch.isEmpty()) {
            db.saveKeystoneSnapshotsBatch(batch, sync);
        }
    }

    public void flushAll(boolean sync) {
        stopTask();
        DatabaseManager db = DCCosmetics.getInstance().getDatabaseManager();
        if (db == null) return;

        List<KeystoneSnapshot> batch = new ArrayList<>();
        for (KeystoneProfile profile : manager.getAllProfiles()) {
            if (profile.isLoaded()) {
                synchronized (profile) {
                    batch.add(profile.toSnapshot());
                    profile.setDirty(false);
                }
            }
        }
        if (!batch.isEmpty()) {
            db.saveKeystoneSnapshotsBatch(batch, sync);
        }
    }
}
