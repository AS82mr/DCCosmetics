package dc.dccosmetics.listener;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.api.DisplayWrapper;
import dc.dccosmetics.model.ActiveCosmetic;
import dc.dccosmetics.model.CosmeticTemplate;
import dc.dccosmetics.model.PlayerProfile;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class FootstepListener implements Listener {

    public static final Set<UUID> MUTED_PLAYERS = new HashSet<>();
    private final java.util.Map<UUID, Location> lastFootprintLoc = new java.util.HashMap<>();
    private final java.util.Map<UUID, Boolean> lastFootIsRight = new java.util.HashMap<>();

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        Player player = event.getPlayer();
        if (!player.isOnGround()) return;

        Location lastLoc = lastFootprintLoc.get(player.getUniqueId());
        if (lastLoc != null && lastLoc.getWorld().equals(to.getWorld()) && lastLoc.distanceSquared(to) < 0.35) {
            return; // Only drop a step every ~0.6 blocks for a perfect walking cycle
        }
        lastFootprintLoc.put(player.getUniqueId(), to);

        PlayerProfile profile = DCCosmetics.getInstance().getProfileManager().getProfile(player);
        if (profile == null) return;

        ActiveCosmetic boots = profile.getActiveCosmetic("boots");
        if (boots == null) return;

        CosmeticTemplate template = boots.getTemplate();

        // 1. VANILLA PARTICLES
        if (template.getFootstepParticle() != null) {
            try {
                Particle p = Particle.valueOf(template.getFootstepParticle().toUpperCase());
                if (p == Particle.DUST && template.getFootstepColor() != null) {
                    java.awt.Color jColor = java.awt.Color.decode(template.getFootstepColor());
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(jColor.getRed(), jColor.getGreen(), jColor.getBlue()), 1.2f);
                    player.getWorld().spawnParticle(p, to.clone().add(0, 0.1, 0), 4, 0.15, 0.0, 0.15, 0, dust);
                } else {
                    player.getWorld().spawnParticle(p, to.clone().add(0, 0.1, 0), 4, 0.15, 0.0, 0.15, 0);
                }
            } catch (Exception e) { /* Ignore */ }
        }

        // 2. PLAY SOUND
        if (template.getFootstepSound() != null && !MUTED_PLAYERS.contains(player.getUniqueId())) {
            player.getWorld().playSound(to, template.getFootstepSound(), template.getSoundVolume(), template.getSoundPitch());
        }

        // 3. TRANSIENT HOLOGRAPHIC FOOTPRINT ENGINE!
        // The ActiveCosmetic object natively stores the fully-resolved exact color!
        String finalColor = boots.getColorHex();
        if (finalColor == null || finalColor.isEmpty()) finalColor = template.getFootstepColor();
        
        if (finalColor != null) {
            spawnHolographicFootprint(player, to.clone(), finalColor, template.getId());
        }
    }

    private void spawnHolographicFootprint(Player player, Location loc, String hexColor, String templateId) {
        boolean right = lastFootIsRight.getOrDefault(player.getUniqueId(), false);
        lastFootIsRight.put(player.getUniqueId(), !right);

        // Local X axis: positive is Left foot, negative is Right foot. Orbit to match yaw!
        float lateralOffset = right ? -0.2f : 0.2f;
        Vector3f offset = new Vector3f(lateralOffset, 0, 0);
        offset.rotateY((float) Math.toRadians(loc.getYaw()));

        // Snap to foot level, apply lateral orbit offset, push up by 0.05 to avoid surface clipping
        Location footLoc = new Location(loc.getWorld(), loc.getX() + offset.x, loc.getY() + 0.02, loc.getZ() + offset.z);
        List<Player> viewers = footLoc.getWorld().getPlayers();

        DisplayWrapper footprint = DCCosmetics.getInstance().getPacketAdapter().createBlockDisplay(viewers, footLoc);

        // DYNAMIC SHAPE ENGINE: Detects the cosmetic type and spawns actual shapes!
        String shapeId = templateId.toLowerCase();
        String symbol = "■"; // Mathematically perfect square character
        if (shapeId.contains("star")) symbol = "★";
        else if (shapeId.contains("heart")) symbol = "❤";
        else if (shapeId.contains("ring") || shapeId.contains("disc")) symbol = "⬤";

        if (footprint instanceof dc.dccosmetics.nms.ProtocolDisplayWrapper pWrapper) {
            pWrapper.setAsTextShape(symbol);
        }

        footprint.setScale(new Vector3f(2.0f, 2.0f, 2.0f)); // Give it a nice, flat uniform scale!
        footprint.setTranslation(new Vector3f(0, 0, 0));

        // THE FIX: If you lay a plane flat on the ground (-90 X), spinning the Y-axis will flip it into the dirt!
        // By spinning the Z-axis (Roll), it rotates like a steering wheel and always stays facing UP!
        footprint.setRotation(new Vector3f(-90.0f, 0.0f, -loc.getYaw()));

        footprint.setColor(hexColor);

        footprint.update();

        // THE CLEANUP CREW: Delete the fake footprint after 30 ticks (1.5 seconds)
        DCCosmetics.getInstance().getServer().getScheduler().runTaskLater(DCCosmetics.getInstance(), footprint::destroy, 30L);
    }
}