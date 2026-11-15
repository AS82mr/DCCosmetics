package dc.dcessentials.modules.teleportation;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Pre-teleport gate & Unstable safe-jitter resolver (Task P1-05 & P1-06).
 */
public class TeleportGate {

    private final TeleportationManager manager;

    public TeleportGate(TeleportationManager manager) {
        this.manager = manager;
    }

    public static boolean isExemptCause(TeleportCause cause) {
        if (cause == null) return true;
        return switch (cause) {
            case NETHER_PORTAL,
                 END_PORTAL,
                 END_GATEWAY,
                 CONSUMABLE_EFFECT,
                 ENDER_PEARL,
                 DISMOUNT,
                 SPECTATE,
                 EXIT_BED -> true;
            default -> false;
        };
    }

    public boolean isExemptOrSuppressedTransit(Player player, TeleportCause cause, Location from, Location to) {
        if (player == null || !player.isOnline()) return true;
        if (DCCosmetics.getInstance().getWardrobeManager() != null
                && DCCosmetics.getInstance().getWardrobeManager().isInWardrobe(player)) {
            return true;
        }
        if (isExemptCause(cause)) {
            return true;
        }
        return dc.dccosmetics.tpeffects.listener.TpEffectListener.isSuppressedOrShortHop(player, cause, from, to);
    }

    /**
     * Runs at EventPriority.HIGH on PlayerTeleportEvent.
     * Returns false if the teleport must be cancelled (Overload lock on charged warps).
     */
    public boolean preCheck(Player player, TeleportCause cause, Location from, Location to) {
        if (player == null || !player.isOnline()) return true;

        // 1. Peek active command context: Free commands (/spawn, /warp, /pwarp, /back) remain usable even while overloaded
        TeleportContext ctx = manager.peekContext(player);
        if (ctx != null && ctx.type() == TeleportContext.Type.FREE_COMMAND) {
            return true;
        }

        // 2. Environmental, wardrobe, cinematic camera, short-hop, and ability teleports are exempt
        if (isExemptOrSuppressedTransit(player, cause, from, to)) {
            return true;
        }

        KeystoneProfile profile = manager.getProfile(player);

        // 3. Unloaded profile handling: <= 5s after join -> warps allowed free & logged; > 5s -> default-load
        if (!profile.isLoaded()) {
            long ageMs = System.currentTimeMillis() - profile.getCreatedAtMs();
            if (ageMs > 5000L) {
                profile.setLoaded(true);
            } else {
                DCCosmetics.getInstance().getLogger().info("[Keystone] Allowing free warp for " + player.getName() + " while DB profile is loading (" + ageMs + "ms).");
                return true;
            }
        }

        // 4. Overload lock (Heat >= 100 until cooled <= effective overload-clear-below)
        int effClearBelow = manager.getEffectiveOverloadClearBelow(player);
        if (profile.isOverloaded()) {
            manager.consumeContext(player);
            player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.5f);
            DCMessage.sendError(player, "Keystone Matrix OVERLOADED (&#FFFFFF" + profile.getHeat()
                    + "/100 🔥&#EF4444)! Cool your Heat to &#FFFFFF≤" + effClearBelow
                    + " &#EF4444before warping (free warps like &#FFFFFF/spawn &#EF4444& &#FFFFFF/warp &#EF4444still work).");
            return false;
        }

        return true;
    }

    /**
     * Authorizes active item abilities (e.g. Void Warper Bounce / Sonic Boom / Etherwarp).
     * Unloaded <= 5s -> denied; > 5s -> default-loads. Overloaded -> denied until cooled <= clear threshold.
     */
    public boolean authorizeAbility(Player player) {
        if (player == null || !player.isOnline()) return false;
        KeystoneProfile profile = manager.getProfile(player);

        if (!profile.isLoaded()) {
            long ageMs = System.currentTimeMillis() - profile.getCreatedAtMs();
            if (ageMs <= 5000L) {
                DCMessage.sendWarning(player, "Synchronizing Keystone matrix... please wait a moment.");
                return false;
            }
            profile.setLoaded(true);
        }

        int effClearBelow = manager.getEffectiveOverloadClearBelow(player);
        if (profile.isOverloaded()) {
            player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.9f, 0.5f);
            DCMessage.sendError(player, "Keystone Matrix OVERLOADED (&#FFFFFF" + profile.getHeat()
                    + "/100 🔥&#EF4444)! Abilities are locked until Heat cools to &#FFFFFF≤"
                    + effClearBelow + "&#EF4444.");
            return false;
        }
        return true;
    }

    /**
     * Safe-location search for Unstable (heat >= 80) warp jitter.
     * Never places a player inside solid blocks, liquids, or hazards.
     */
    public Location findSafeJitterLocation(Location dest, int minBlocks, int maxBlocks) {
        if (dest == null || dest.getWorld() == null) return dest;
        World world = dest.getWorld();
        int min = Math.max(1, minBlocks);
        int max = Math.max(min, maxBlocks);
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        int baseX = dest.getBlockX();
        int baseY = dest.getBlockY();
        int baseZ = dest.getBlockZ();
        int[] dyOrder = {0, 1, -1, 2, -2};

        for (int attempt = 0; attempt < 16; attempt++) {
            int dist = rng.nextInt(min, max + 1);
            double angle = rng.nextDouble(0, Math.PI * 2.0);
            int dx = (int) Math.round(Math.cos(angle) * dist);
            int dz = (int) Math.round(Math.sin(angle) * dist);
            if (dx == 0 && dz == 0) dx = min;

            int cx = baseX + dx;
            int cz = baseZ + dz;
            if (!world.isChunkLoaded(cx >> 4, cz >> 4)) continue;

            for (int dy : dyOrder) {
                int cy = baseY + dy;
                if (cy <= world.getMinHeight() + 1 || cy >= world.getMaxHeight() - 2) continue;
                if (isSafeStand(world, cx, cy, cz)) {
                    return new Location(world, cx + 0.5, cy, cz + 0.5, dest.getYaw(), dest.getPitch());
                }
            }
        }
        return dest;
    }

    private boolean isSafeStand(World world, int x, int y, int z) {
        Block ground = world.getBlockAt(x, y - 1, z);
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);

        if (!ground.getType().isSolid() || isHazard(ground.getType())) {
            return false;
        }
        if (!feet.isPassable() || feet.isLiquid() || isHazard(feet.getType())) {
            return false;
        }
        return head.isPassable() && !head.isLiquid() && !isHazard(head.getType());
    }

    private boolean isHazard(Material mat) {
        if (mat == null) return true;
        return switch (mat) {
            case LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CACTUS, SWEET_BERRY_BUSH, POWDER_SNOW, WITHER_ROSE, POINTED_DRIPSTONE -> true;
            default -> false;
        };
    }
}
