package dc.dccosmetics.nms;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.api.DisplayWrapper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class ProtocolLibAdapter implements dc.dccosmetics.api.PacketAdapter {

    private final DCCosmetics plugin = DCCosmetics.getInstance();
    private final Logger logger = plugin.getLogger();
    private final ProtocolManager protocolManager;

    // Generate safe fake Entity IDs (Starting very high to avoid real entity conflicts)
    public static final AtomicInteger ENTITY_ID_COUNTER = new AtomicInteger(100_000_000);

    // Keep track of which fake entities belong to which player so we can mount them
    public static final Map<Integer, List<Integer>> playerCosmeticPassengers = new ConcurrentHashMap<>();

    // Suppresses vanilla particles for custom combat hits!
    public static final java.util.List<SuppressedLoc> suppressedParticleLocs = new java.util.concurrent.CopyOnWriteArrayList<>();
    public static class SuppressedLoc {
        public final Location loc;
        public final long expireTime;
        public SuppressedLoc(Location loc, long expireTime) {
            this.loc = loc;
            this.expireTime = expireTime;
        }
    }
    public static void suppressParticles(Location loc, long durationMs) {
        suppressedParticleLocs.add(new SuppressedLoc(loc, System.currentTimeMillis() + durationMs));
    }

    public ProtocolLibAdapter() {
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        registerMountListener();
        registerParticleListener();
        logger.info("[DEBUG] ProtocolLib Adapter initialized successfully.");
    }

    private void registerMountListener() {
        // Intercept the SetPassengers packet (formerly Mount)
        protocolManager.addPacketListener(new PacketAdapter(
                plugin, ListenerPriority.NORMAL, PacketType.Play.Server.MOUNT) {

            @Override
            public void onPacketSending(PacketEvent event) {
                int vehicleId = event.getPacket().getIntegers().read(0);

                // If the vehicle isn't a player that has cosmetics, ignore
                if (!playerCosmeticPassengers.containsKey(vehicleId)) return;

                List<Integer> cosmeticIds = playerCosmeticPassengers.get(vehicleId);
                if (cosmeticIds.isEmpty()) return;

                // Get the original passengers the server was trying to send
                int[] originalPassengers = event.getPacket().getIntegerArrays().read(0);

                // Merge original passengers with our fake cosmetic IDs
                int[] newPassengers = new int[originalPassengers.length + cosmeticIds.size()];
                System.arraycopy(originalPassengers, 0, newPassengers, 0, originalPassengers.length);

                for (int i = 0; i < cosmeticIds.size(); i++) {
                    newPassengers[originalPassengers.length + i] = cosmeticIds.get(i);
                }

                // Write the merged list back to the packet
                event.getPacket().getIntegerArrays().write(0, newPassengers);

                // Debug log (can be commented out for production to avoid console spam)
                // logger.info("[DEBUG] Intercepted Mount packet for Entity " + vehicleId + ". Added " + cosmeticIds.size() + " cosmetic passengers.");
            }
        });
    }

    private void registerParticleListener() {
        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.WORLD_PARTICLES) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (suppressedParticleLocs.isEmpty()) return;
                
                double x = event.getPacket().getDoubles().read(0);
                double y = event.getPacket().getDoubles().read(1);
                double z = event.getPacket().getDoubles().read(2);
                
                long now = System.currentTimeMillis();
                suppressedParticleLocs.removeIf(s -> now > s.expireTime);
                
                for (SuppressedLoc s : suppressedParticleLocs) {
                    if (s.loc.getWorld().equals(event.getPlayer().getWorld())) {
                        double distSq = Math.pow(s.loc.getX() - x, 2) + Math.pow(s.loc.getY() - y, 2) + Math.pow(s.loc.getZ() - z, 2);
                        if (distSq < 9.0) { // Cancel ALL combat particles within 3 blocks of the hit!
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        });

        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.ANIMATION) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (suppressedParticleLocs.isEmpty()) return;
                
                int animationId = event.getPacket().getIntegers().read(1);
                if (animationId == 4 || animationId == 5) { // 4 = Crit, 5 = Magic Crit
                    event.setCancelled(true); // Eradicate crits globally for everyone else!
                }
            }
        });
    }

    @Override
    public void injectPlayer(Player player) {
        // With ProtocolLib, we don't need to manually inject into the Netty channel!
        // ProtocolManager handles listening globally. We just prep their ID map.
        playerCosmeticPassengers.putIfAbsent(player.getEntityId(), new ArrayList<>());
    }

    @Override
    public void removePlayer(Player player) {
        playerCosmeticPassengers.remove(player.getEntityId());
    }

    @Override
    public DisplayWrapper createBlockDisplay(List<Player> viewers, Location location) {
        return new ProtocolDisplayWrapper(viewers, location, protocolManager);
    }
}