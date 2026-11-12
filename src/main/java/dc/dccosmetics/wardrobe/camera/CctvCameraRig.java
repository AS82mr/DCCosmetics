package dc.dccosmetics.wardrobe.camera;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.ActiveCosmetic;
import dc.dccosmetics.model.CosmeticTemplate;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Collections;

public class CctvCameraRig {

    private final Player viewer;
    private BukkitTask laserTask = null;
    
    private ArmorStand anchor;
    private ActiveCosmetic activeCosmetic;

    public CctvCameraRig(Player viewer) {
        this.viewer = viewer;
    }

    /**
     * Spawns the camera rig at the given location, targeting the mannequin.
     */
    public void spawnAt(Location loc, Location targetLoc) {
        destroyAll();

        Location eyeLoc = loc.clone().add(0, 1.62, 0);

        // Load camera model from config
        String modelId = null;
        try {
            File file = dc.dccosmetics.wardrobe.WardrobeManager.getWardrobeConfigFile();
            if (file.exists()) {
                org.bukkit.configuration.file.YamlConfiguration cfg = 
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
                modelId = cfg.getString("camera.model", null);
            }
        } catch (Exception ignored) {}

        if (modelId != null && !modelId.isEmpty() && !modelId.equalsIgnoreCase("none")) {
            CosmeticTemplate template = DCCosmetics.getInstance().getTemplateRegistry().getTemplate(modelId);
            if (template != null) {
                anchor = (ArmorStand) loc.getWorld().spawnEntity(eyeLoc, EntityType.ARMOR_STAND);
                anchor.setVisible(false);
                anchor.setGravity(false);
                anchor.setMarker(true);
                anchor.setInvulnerable(true);

                activeCosmetic = new ActiveCosmetic(anchor, template, null, Collections.singletonList(viewer));
                activeCosmetic.spawn();
            }
        }

        startLaser(eyeLoc, targetLoc);
    }

    public void moveTo(Location loc, Location targetLoc) {
        spawnAt(loc, targetLoc);
    }

    public void destroy() {
        destroyAll();
    }

    private void destroyAll() {
        stopLaser();
        if (activeCosmetic != null) {
            activeCosmetic.despawn();
            activeCosmetic = null;
        }
        if (anchor != null) {
            ArmorStand toRemove = anchor;
            anchor = null;
            DCCosmetics.getInstance().getServer().getScheduler().runTask(
                    DCCosmetics.getInstance(), toRemove::remove);
        }
    }

    private void startLaser(Location source, Location target) {
        stopLaser();
        
        boolean laserEnabled = false;
        try {
            File file = dc.dccosmetics.wardrobe.WardrobeManager.getWardrobeConfigFile();
            if (file.exists()) {
                org.bukkit.configuration.file.YamlConfiguration cfg = 
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
                laserEnabled = cfg.getBoolean("camera.laser-enabled", false);
            }
        } catch (Exception ignored) {}
        
        if (!laserEnabled || target == null || source == null) return;

        org.bukkit.util.Vector dir = target.toVector().subtract(source.toVector());
        double distance = dir.length();
        if (distance <= 0.5) return;
        dir.normalize();

        laserTask = DCCosmetics.getInstance().getServer().getScheduler().runTaskTimer(
                DCCosmetics.getInstance(),
                () -> {
                    if (!viewer.isOnline()) {
                        stopLaser();
                        return;
                    }
                    org.bukkit.Particle.DustOptions redDust = new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 0.6f);
                    for (double d = 0.5; d < distance; d += 0.5) {
                        Location pLoc = source.clone().add(dir.clone().multiply(d));
                        viewer.spawnParticle(org.bukkit.Particle.DUST, pLoc, 1, 0, 0, 0, 0, redDust);
                    }
                },
                0L, 5L
        );
    }

    private void stopLaser() {
        if (laserTask != null) {
            laserTask.cancel();
            laserTask = null;
        }
    }
}
