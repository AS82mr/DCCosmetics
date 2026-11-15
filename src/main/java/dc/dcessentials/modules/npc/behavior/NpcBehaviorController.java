package dc.dcessentials.modules.npc.behavior;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.BlueprintAnimation;
import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.animation.property.IAnimationProperty;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.npc.NpcRegistry;
import dc.dcessentials.modules.npc.model.DecorationNpc;
import dc.dcessentials.modules.npc.model.NpcBehaviorProfile;
import dc.dcessentials.modules.npc.model.NpcCharacter;
import dc.dcessentials.modules.npc.scene.DecorationManager;
import dc.dcessentials.modules.npc.scene.SceneCatalog;
import dc.dcessentials.modules.npc.util.DragoncoteColor;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.LookClose;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages Smart Mode animation cycling, Custom Walking Patrol Loops (both Citizens & ModelEngine),
 * and the interactive in-world Block-Click Waypoint Recorder for all Storyline & Decoration DCNPCs.
 */
public class NpcBehaviorController {

    private final NpcRegistry registry;
    private final SceneCatalog sceneCatalog;
    private DecorationManager decorationManager;

    private BukkitTask mainTask;
    private BukkitTask recorderParticleTask;

    private static class RuntimeState {
        boolean patrolling = false;
        int waypointIndex = 0;
        long nextPatrolAtMs = 0L;
        long nextSmartAnimAtMs = 0L;
        long lastProgressCheckMs = 0L;
        Location lastCheckedLoc;
        String currentAnim;
        boolean initialPatrolDone = false;
    }

    public static class PathRecordSession {
        public final String targetType; // "CHAR" or "DECO"
        public final String targetId;
        public final Location anchor;
        public final List<Location> points = new ArrayList<>();

        public PathRecordSession(String targetType, String targetId, Location anchor) {
            this.targetType = targetType;
            this.targetId = targetId;
            this.anchor = anchor != null ? anchor.clone() : null;
        }
    }

    private final Map<Integer, RuntimeState> runtimeStates = new ConcurrentHashMap<>();
    private final Map<UUID, PathRecordSession> recordingSessions = new ConcurrentHashMap<>();
    private final boolean modelEnginePresent;

    public NpcBehaviorController(NpcRegistry registry, SceneCatalog sceneCatalog) {
        this.registry = registry;
        this.sceneCatalog = sceneCatalog;
        this.modelEnginePresent = Bukkit.getPluginManager().getPlugin("ModelEngine") != null;
    }

    public void setDecorationManager(DecorationManager decorationManager) {
        this.decorationManager = decorationManager;
    }

    public void start() {
        stop();
        // Tick every 10 ticks (0.5s) for smooth navigation checks and Smart Mode cycling
        mainTask = Bukkit.getScheduler().runTaskTimer(DCCosmetics.getInstance(), this::tickAll, 40L, 10L);
        recorderParticleTask = Bukkit.getScheduler().runTaskTimer(DCCosmetics.getInstance(), this::tickRecorderPreview, 20L, 12L);
    }

    public void stop() {
        if (mainTask != null) { mainTask.cancel(); mainTask = null; }
        if (recorderParticleTask != null) { recorderParticleTask.cancel(); recorderParticleTask = null; }
        runtimeStates.clear();
        recordingSessions.clear();
    }

    // =========================================================================
    // PATH RECORDING SESSION API
    // =========================================================================

    public boolean isRecording(UUID playerId) {
        return recordingSessions.containsKey(playerId);
    }

    public PathRecordSession getRecordingSession(UUID playerId) {
        return recordingSessions.get(playerId);
    }

    public void startRecording(Player player, String targetType, String targetId, Location anchor) {
        PathRecordSession session = new PathRecordSession(targetType, targetId.toLowerCase(), anchor);
        recordingSessions.put(player.getUniqueId(), session);

        player.closeInventory();
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.4f);
        player.sendMessage(DragoncoteColor.colorize("&8&m----------------------------------------"));
        DCMessage.sendGeneral(player, "Patrol Waypoint Recorder Started &8(&f" + targetId + "&8)");
        player.sendMessage(DragoncoteColor.colorize("  &8• &eRight-Click Ground Blocks: &fAdd waypoint (max 20)"));
        player.sendMessage(DragoncoteColor.colorize("  &8• &cLeft-Click Air/Block: &fUndo last waypoint"));
        player.sendMessage(DragoncoteColor.colorize("  &8• &aShift + Right-Click &7or &a/dcnpc path save: &fSave & enable Smart Patrol"));
        player.sendMessage(DragoncoteColor.colorize("  &8• &7Tip: Start & end near the NPC anchor so it never walks into walls!"));
        player.sendMessage(DragoncoteColor.colorize("&8&m----------------------------------------"));
    }

    public void addRecordedWaypoint(Player player, Location blockTopLoc) {
        PathRecordSession session = recordingSessions.get(player.getUniqueId());
        if (session == null) return;

        if (session.points.size() >= 20) {
            DCMessage.sendWarning(player, "Maximum of 20 waypoints reached! Shift+Right-Click or run /dcnpc path save to finish.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
            return;
        }

        Location wp = blockTopLoc.clone();
        wp.setYaw(player.getLocation().getYaw());
        wp.setPitch(0.0f);
        session.points.add(wp);

        player.playSound(wp, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.0f + (session.points.size() * 0.04f));
        DCMessage.sendInfo(player, "Added Waypoint &#FFFFFF#" + session.points.size() + "/20 &7at (" +
                wp.getBlockX() + ", " + wp.getBlockY() + ", " + wp.getBlockZ() + ")");
    }

    public void undoRecordedWaypoint(Player player) {
        PathRecordSession session = recordingSessions.get(player.getUniqueId());
        if (session == null) return;
        if (session.points.isEmpty()) {
            DCMessage.sendWarning(player, "No waypoints recorded yet.");
            return;
        }
        session.points.remove(session.points.size() - 1);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.2f);
        DCMessage.sendInfo(player, "Removed last waypoint. Remaining: &#FFFFFF" + session.points.size());
    }

    public boolean saveRecording(Player player) {
        PathRecordSession session = recordingSessions.remove(player.getUniqueId());
        if (session == null) return false;

        if (session.points.size() < 2) {
            DCMessage.sendError(player, "Patrol path cancelled: please record at least 2 waypoints before saving.");
            return false;
        }

        if ("DECO".equalsIgnoreCase(session.targetType) && decorationManager != null) {
            DecorationNpc deco = decorationManager.getDecoration(session.targetId);
            if (deco != null) {
                deco.getProfile().setPatrolWaypoints(session.points);
                deco.getProfile().setAnimationMode(NpcBehaviorProfile.ANIM_SMART_PATROL);
                decorationManager.save();
                resetStatesForTag(deco.getId(), true);
                DCMessage.sendGeneral(player, "Saved &#FFFFFF" + session.points.size() + " &#0CBF93patrol waypoints for Decoration NPC &#FFFFFF" + deco.getId() + "&#0CBF93!");
                return true;
            }
        } else {
            NpcCharacter c = registry.getCharacter(session.targetId);
            if (c != null) {
                c.getBehaviorProfile().setPatrolWaypoints(session.points);
                c.getBehaviorProfile().setAnimationMode(NpcBehaviorProfile.ANIM_SMART_PATROL);
                registry.saveCustomStages(DCCosmetics.getInstance().getDataFolder());
                resetStatesForTag(c.getId(), false);
                DCMessage.sendGeneral(player, "Saved &#FFFFFF" + session.points.size() + " &#0CBF93patrol waypoints for &#FFFFFF" + c.getShortName() + "&#0CBF93!");
                return true;
            }
        }
        return false;
    }

    public void cancelRecording(Player player) {
        if (recordingSessions.remove(player.getUniqueId()) != null) {
            DCMessage.sendWarning(player, "Cancelled patrol waypoint recording.");
        }
    }

    public void resetStatesForTag(String id, boolean isDeco) {
        if (CitizensAPI.getPlugin() == null || !CitizensAPI.hasImplementation()) return;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (isDeco && npc.data().has("dcnpc_deco_id")
                    && npc.data().get("dcnpc_deco_id").toString().equalsIgnoreCase(id)) {
                runtimeStates.remove(npc.getId());
            } else if (!isDeco && npc.data().has("dcnpc_id")) {
                String raw = npc.data().get("dcnpc_id").toString().toLowerCase();
                if (raw.equals(id.toLowerCase()) || raw.startsWith(id.toLowerCase() + ":")) {
                    runtimeStates.remove(npc.getId());
                }
            }
        }
    }

    private void tickRecorderPreview() {
        for (Map.Entry<UUID, PathRecordSession> entry : recordingSessions.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) continue;
            PathRecordSession session = entry.getValue();
            List<Location> pts = session.points;
            for (int i = 0; i < pts.size(); i++) {
                Location a = pts.get(i);
                if (a.getWorld() == null || !a.getWorld().equals(p.getWorld())) continue;
                p.spawnParticle(Particle.HAPPY_VILLAGER, a.clone().add(0, 0.35, 0), 3, 0.08, 0.08, 0.08, 0);
                if (i + 1 < pts.size()) {
                    spawnLine(p, a.clone().add(0, 0.35, 0), pts.get(i + 1).clone().add(0, 0.35, 0));
                }
            }
            if (pts.size() >= 2 && pts.get(0).getWorld() != null && pts.get(0).getWorld().equals(p.getWorld())) {
                spawnLine(p, pts.get(pts.size() - 1).clone().add(0, 0.35, 0), pts.get(0).clone().add(0, 0.35, 0));
            }
        }
    }

    private void spawnLine(Player p, Location start, Location end) {
        double dist = start.distance(end);
        if (dist < 0.2 || dist > 64.0) return;
        int steps = Math.min(20, Math.max(2, (int) (dist * 2)));
        double dx = (end.getX() - start.getX()) / steps;
        double dy = (end.getY() - start.getY()) / steps;
        double dz = (end.getZ() - start.getZ()) / steps;
        for (int i = 1; i < steps; i++) {
            p.spawnParticle(Particle.END_ROD,
                    start.getX() + dx * i, start.getY() + dy * i, start.getZ() + dz * i,
                    1, 0, 0, 0, 0);
        }
    }

    // =========================================================================
    // SMART MODE & PATROL TICKER
    // =========================================================================

    private void tickAll() {
        if (CitizensAPI.getPlugin() == null || !CitizensAPI.hasImplementation()) return;
        long now = System.currentTimeMillis();

        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (!npc.isSpawned() || npc.getEntity() == null) continue;
            if (npc.data().has("temp_walker")) continue;

            NpcBehaviorProfile profile = resolveProfile(npc);
            if (profile == null) continue;

            String mode = profile.getAnimationMode();
            RuntimeState state = runtimeStates.computeIfAbsent(npc.getId(), k -> {
                RuntimeState rs = new RuntimeState();
                rs.nextPatrolAtMs = now + 8000L; // Start first patrol ~8s after activation
                return rs;
            });

            if (NpcBehaviorProfile.ANIM_FIXED.equals(mode)) {
                if (profile.isModelEngine() && !Objects.equals(state.currentAnim, profile.getFixedAnimation())) {
                    applyModelAnimation(npc, profile, profile.getFixedAnimation(), true);
                    state.currentAnim = profile.getFixedAnimation();
                }
                continue;
            }

            if (NpcBehaviorProfile.ANIM_SMART.equals(mode)) {
                tickSmartIdle(npc, profile, state, now);
                continue;
            }

            if (NpcBehaviorProfile.ANIM_SMART_PATROL.equals(mode)) {
                List<Location> waypoints = profile.getPatrolWaypoints();
                if (waypoints.size() < 2) {
                    tickSmartIdle(npc, profile, state, now);
                    continue;
                }
                tickPatrol(npc, profile, state, waypoints, now);
            }
        }
    }

    private NpcBehaviorProfile resolveProfile(NPC npc) {
        if (npc.data().has("dcnpc_deco_id") && decorationManager != null) {
            DecorationNpc deco = decorationManager.getDecoration(npc.data().get("dcnpc_deco_id").toString());
            return deco != null ? deco.getProfile() : null;
        }
        if (npc.data().has("dcnpc_id")) {
            String raw = npc.data().get("dcnpc_id").toString();
            NpcCharacter c = registry.getCharacter(raw);
            return c != null ? c.getBehaviorProfile() : null;
        }
        return null;
    }

    private void tickSmartIdle(NPC npc, NpcBehaviorProfile profile, RuntimeState state, long now) {
        if (!profile.isModelEngine()) return;
        if (now < state.nextSmartAnimAtMs) return;

        SceneCatalog.SceneModelInfo info = sceneCatalog.getModel(profile.getModelId());
        String chosen = null;
        boolean loop = true;

        if (info != null && !info.allAnimations.isEmpty()) {
            boolean useSocial = !info.socialAnimations.isEmpty() && ThreadLocalRandom.current().nextInt(100) < 25;
            if (useSocial) {
                chosen = info.socialAnimations.get(ThreadLocalRandom.current().nextInt(info.socialAnimations.size()));
                loop = false;
            } else if (!info.idleAnimations.isEmpty()) {
                chosen = info.idleAnimations.get(ThreadLocalRandom.current().nextInt(info.idleAnimations.size()));
                loop = true;
            } else {
                chosen = info.getDefaultIdleAnimation();
            }
        }
        if (chosen != null) {
            applyModelAnimation(npc, profile, chosen, loop);
            state.currentAnim = chosen;
        }
        int cycleSec = Math.max(4, profile.getSmartCycleSeconds());
        state.nextSmartAnimAtMs = now + (cycleSec * 1000L) + ThreadLocalRandom.current().nextInt(4000);
    }

    private void tickPatrol(NPC npc, NpcBehaviorProfile profile, RuntimeState state, List<Location> rawWaypoints, long now) {
        Entity ent = npc.getEntity();
        World npcWorld = ent.getWorld();

        if (!state.patrolling) {
            // Resting at anchor in Smart Mode
            tickSmartIdle(npc, profile, state, now);
            if (now >= state.nextPatrolAtMs) {
                state.patrolling = true;
                state.waypointIndex = 0;
                state.lastProgressCheckMs = now;
                state.lastCheckedLoc = ent.getLocation().clone();
                try {
                    if (npc.hasTrait(LookClose.class)) npc.getTrait(LookClose.class).lookClose(false);
                } catch (Throwable ignored) {}
                navigateToWaypoint(npc, profile, state, toWorldWaypoint(rawWaypoints.get(0), npcWorld));
            }
            return;
        }

        // Actively patrolling
        if (state.waypointIndex >= rawWaypoints.size()) {
            // Finished full loop -> return to anchor & rest
            Location anchor = toWorldWaypoint(rawWaypoints.get(0), npcWorld);
            npc.getNavigator().cancelNavigation();
            npc.teleport(anchor, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
            state.patrolling = false;
            state.initialPatrolDone = true;
            state.nextPatrolAtMs = now + (profile.getPatrolRestSeconds() * 1000L);
            state.nextSmartAnimAtMs = 0L; // Immediately pick idle animation
            try {
                if (npc.hasTrait(LookClose.class)) npc.getTrait(LookClose.class).lookClose(true);
            } catch (Throwable ignored) {}
            if (profile.isModelEngine()) {
                forceStopAnimations(npc);
                tickSmartIdle(npc, profile, state, now);
            }
            return;
        }

        Location target = toWorldWaypoint(rawWaypoints.get(state.waypointIndex), npcWorld);
        double distSq = ent.getLocation().distanceSquared(target);

        if (profile.isModelEngine()) {
            String walkAnim = resolveWalkAnimation(profile);
            if (walkAnim != null && !walkAnim.equals(state.currentAnim)) {
                applyModelAnimation(npc, profile, walkAnim, true);
                state.currentAnim = walkAnim;
            }
        }

        if (distSq <= 1.44) { // Within 1.2 blocks -> advance to next waypoint
            state.waypointIndex++;
            state.lastProgressCheckMs = now;
            state.lastCheckedLoc = ent.getLocation().clone();
            if (state.waypointIndex < rawWaypoints.size()) {
                navigateToWaypoint(npc, profile, state, toWorldWaypoint(rawWaypoints.get(state.waypointIndex), npcWorld));
            }
            return;
        }

        // Keep ModelEngine facing movement vector & check if Citizens Navigator got stuck
        if (!npc.getNavigator().isNavigating()) {
            navigateToWaypoint(npc, profile, state, target);
        }

        if (now - state.lastProgressCheckMs > 4000L) {
            Location cur = ent.getLocation();
            if (state.lastCheckedLoc != null && state.lastCheckedLoc.distanceSquared(cur) < 0.25) {
                // Nudged smoothly onto waypoint so it doesn't stall indefinitely on slabs/stairs
                npc.teleport(target, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                state.waypointIndex++;
            }
            state.lastProgressCheckMs = now;
            state.lastCheckedLoc = cur.clone();
        }
    }

    private void navigateToWaypoint(NPC npc, NpcBehaviorProfile profile, RuntimeState state, Location target) {
        if (npc == null || target == null) return;
        npc.getNavigator().getDefaultParameters()
                .baseSpeed(0.95f)
                .range(64.0f)
                .avoidWater(false)
                .useNewPathfinder(true);
        npc.getNavigator().setTarget(target);
        if (profile.isModelEngine()) {
            String walkAnim = resolveWalkAnimation(profile);
            if (walkAnim != null) {
                applyModelAnimation(npc, profile, walkAnim, true);
                state.currentAnim = walkAnim;
            }
        }
    }

    private Location toWorldWaypoint(Location src, World targetWorld) {
        return new Location(targetWorld, src.getX(), src.getY(), src.getZ(), src.getYaw(), src.getPitch());
    }

    public String resolveWalkAnimation(NpcBehaviorProfile profile) {
        SceneCatalog.SceneModelInfo info = sceneCatalog.getModel(profile.getModelId());
        if (info != null && info.getWalkAnimation() != null) {
            return info.getWalkAnimation();
        }
        return null;
    }

    public void applyInitialModelAnimation(NPC npc, NpcBehaviorProfile profile) {
        if (npc == null || profile == null || !profile.isModelEngine()) return;
        String mode = profile.getAnimationMode();
        if (NpcBehaviorProfile.ANIM_FIXED.equals(mode)) {
            applyModelAnimation(npc, profile, profile.getFixedAnimation(), true);
        } else if (NpcBehaviorProfile.ANIM_SMART.equals(mode) || NpcBehaviorProfile.ANIM_SMART_PATROL.equals(mode)) {
            SceneCatalog.SceneModelInfo info = sceneCatalog.getModel(profile.getModelId());
            if (info != null && info.getDefaultIdleAnimation() != null) {
                applyModelAnimation(npc, profile, info.getDefaultIdleAnimation(), true);
            }
        }
    }

    public void forceStopAnimations(NPC npc) {
        if (!modelEnginePresent || npc == null || !npc.isSpawned() || npc.getEntity() == null) return;
        try {
            ModeledEntity me = ModelEngineAPI.getModeledEntity(npc.getEntity());
            if (me == null) return;
            for (ActiveModel model : me.getModels().values()) {
                AnimationHandler ah = model.getAnimationHandler();
                if (ah != null) ah.forceStopAllAnimations();
            }
        } catch (Throwable ignored) {}
    }

    public void applyModelAnimation(NPC npc, NpcBehaviorProfile profile, String animName, boolean loop) {
        if (!modelEnginePresent || npc == null || !npc.isSpawned() || npc.getEntity() == null || animName == null) return;
        try {
            ModeledEntity me = ModelEngineAPI.getModeledEntity(npc.getEntity());
            if (me == null) return;
            for (ActiveModel model : me.getModels().values()) {
                AnimationHandler ah = model.getAnimationHandler();
                if (ah == null) continue;
                ah.forceStopAllAnimations();
                IAnimationProperty prop = ah.playAnimation(animName, 0.25, 0.25, 1.0, true);
                if (prop != null) {
                    prop.setForceLoopMode(loop ? BlueprintAnimation.LoopMode.LOOP : BlueprintAnimation.LoopMode.ONCE);
                }
            }
        } catch (Throwable ignored) {}
    }
}
