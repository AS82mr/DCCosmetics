package dc.dcessentials.modules.npc.scene;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.npc.NpcCanonManager;
import dc.dcessentials.modules.npc.behavior.NpcBehaviorController;
import dc.dcessentials.modules.npc.model.DecorationNpc;
import dc.dcessentials.modules.npc.model.NpcBehaviorProfile;
import dc.dcessentials.modules.npc.skin.SkinProcessor;
import dc.dcessentials.modules.npc.util.DragoncoteColor;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.LookClose;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages out-of-storyline Decoration / Ambient DCNPCs.
 * Persisted in plugins/DCEssentials/npc/decorations.yml and backed by Citizens NPCs
 * with full Skin, ModelEngine, Smart Animation, Patrol Path, and Multi-World parity
 * without any storyline stages, quests, prerequisites, or rewards.
 */
public class DecorationManager {

    private final File file;
    private final Map<String, DecorationNpc> decorations = new LinkedHashMap<>();
    private final Map<UUID, Long> talkCooldown = new ConcurrentHashMap<>();

    private NpcCanonManager canonManager;
    private SkinProcessor skinProcessor;
    private NpcBehaviorController behaviorController;

    public DecorationManager() {
        File npcDir = new File(DCCosmetics.getInstance().getDataFolder(), "npc");
        if (!npcDir.exists()) npcDir.mkdirs();
        this.file = new File(npcDir, "decorations.yml");
    }

    public void setManagers(NpcCanonManager canonManager, SkinProcessor skinProcessor, NpcBehaviorController behaviorController) {
        this.canonManager = canonManager;
        this.skinProcessor = skinProcessor;
        this.behaviorController = behaviorController;
    }

    public void load() {
        decorations.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("decorations");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            DecorationNpc deco = DecorationNpc.loadFrom(id, sec.getConfigurationSection(id));
            if (deco != null) {
                decorations.put(deco.getId(), deco);
                if (skinProcessor != null) {
                    skinProcessor.ensureDirectory(deco.getSkinId());
                }
            }
        }
        DCCosmetics.getInstance().getLogger().info("[DecorationManager] Loaded " + decorations.size() + " out-of-storyline Decoration NPCs.");
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (DecorationNpc deco : decorations.values()) {
            ConfigurationSection sec = cfg.createSection("decorations." + deco.getId());
            deco.saveTo(sec);
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            DCCosmetics.getInstance().getLogger().warning("[DecorationManager] Failed saving decorations.yml: " + e.getMessage());
        }
    }

    public DecorationNpc getDecoration(String id) {
        if (id == null) return null;
        return decorations.get(id.toLowerCase());
    }

    public Collection<DecorationNpc> getAllDecorations() {
        return Collections.unmodifiableCollection(decorations.values());
    }

    public DecorationNpc createDecoration(String rawId, String displayName, Location loc) {
        String cleanId = rawId.toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
        if (cleanId.isEmpty()) cleanId = "deco_" + (decorations.size() + 1);
        if (decorations.containsKey(cleanId)) {
            int n = 2;
            while (decorations.containsKey(cleanId + "_" + n)) n++;
            cleanId = cleanId + "_" + n;
        }
        DecorationNpc deco = new DecorationNpc(cleanId, displayName, loc);
        decorations.put(cleanId, deco);
        if (skinProcessor != null) skinProcessor.ensureDirectory(deco.getSkinId());
        save();
        syncDecorationInstances(deco);
        return deco;
    }

    public boolean deleteDecoration(String id) {
        DecorationNpc deco = decorations.remove(id.toLowerCase());
        if (deco == null) return false;
        destroyCitizensInstances(deco.getId());
        save();
        return true;
    }

    public void destroyCitizensInstances(String decoId) {
        if (CitizensAPI.getPlugin() == null || !CitizensAPI.hasImplementation()) return;
        List<NPC> toRemove = new ArrayList<>();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.data().has("dcnpc_deco_id")
                    && npc.data().get("dcnpc_deco_id").toString().equalsIgnoreCase(decoId)) {
                toRemove.add(npc);
            }
        }
        for (NPC npc : toRemove) {
            try { npc.destroy(); } catch (Throwable ignored) {}
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens save");
    }

    public List<NPC> findInstances(String decoId) {
        List<NPC> list = new ArrayList<>();
        if (CitizensAPI.getPlugin() == null || !CitizensAPI.hasImplementation()) return list;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.data().has("dcnpc_deco_id")
                    && npc.data().get("dcnpc_deco_id").toString().equalsIgnoreCase(decoId)) {
                list.add(npc);
            }
        }
        return list;
    }

    public NPC findInstanceInWorld(String decoId, String worldName) {
        for (NPC npc : findInstances(decoId)) {
            Location loc = (npc.isSpawned() && npc.getEntity() != null) ? npc.getEntity().getLocation() : npc.getStoredLocation();
            if (loc != null && loc.getWorld() != null && loc.getWorld().getName().equalsIgnoreCase(worldName)) {
                return npc;
            }
        }
        return null;
    }

    public void syncAllDecorations() {
        for (DecorationNpc deco : decorations.values()) {
            syncDecorationInstances(deco);
        }
    }

    public void syncDecorationInstances(DecorationNpc deco) {
        if (deco == null || CitizensAPI.getPlugin() == null || !CitizensAPI.hasImplementation()) return;
        Location anchor = deco.getAnchorLocation();
        if (anchor == null || anchor.getWorld() == null) return;

        List<String> targetWorlds = new ArrayList<>();
        String anchorWorld = anchor.getWorld().getName();
        if (deco.isSyncSpawnWorlds() && isSpawnDimension(anchorWorld) && canonManager != null) {
            targetWorlds.addAll(canonManager.getActiveSpawnWorlds());
        } else {
            targetWorlds.add(anchorWorld);
        }

        // Remove instances from worlds no longer targeted
        for (NPC existing : findInstances(deco.getId())) {
            Location loc = (existing.isSpawned() && existing.getEntity() != null) ? existing.getEntity().getLocation() : existing.getStoredLocation();
            String wName = (loc != null && loc.getWorld() != null) ? loc.getWorld().getName() : "";
            boolean keep = targetWorlds.stream().anyMatch(tw -> tw.equalsIgnoreCase(wName));
            if (!keep) {
                try { existing.destroy(); } catch (Throwable ignored) {}
            }
        }

        // Spawn or update each world's instance
        for (String wName : targetWorlds) {
            World w = canonManager != null ? canonManager.getOrLoadWorld(wName) : Bukkit.getWorld(wName);
            if (w == null) continue;
            Location targetLoc = new Location(w, anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getYaw(), anchor.getPitch());

            NPC npc = findInstanceInWorld(deco.getId(), wName);
            if (npc == null) {
                npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, org.bukkit.ChatColor.stripColor(DragoncoteColor.colorize(deco.getDisplayName())));
                npc.data().setPersistent("dcnpc_deco_id", deco.getId());
                npc.data().setPersistent("dcnpc_mode", "deco");
                npc.spawn(targetLoc);
            } else if (!npc.isSpawned()) {
                npc.spawn(targetLoc);
            } else {
                npc.getNavigator().cancelNavigation();
                npc.teleport(targetLoc, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
            }

            applyDecorationVisuals(npc, deco, wName);
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens save");
    }

    public void applyDecorationVisuals(NPC npc, DecorationNpc deco, String worldName) {
        if (npc == null || deco == null || !npc.isSpawned() || npc.getEntity() == null) return;
        npc.data().setPersistent("dcnpc_deco_id", deco.getId());
        npc.getOrAddTrait(LookClose.class).lookClose(true);

        if (skinProcessor != null) {
            skinProcessor.applyProfileSkin(npc, worldName, deco.getSkinId(), deco.getFallbackSkinName(), deco.getProfile().getSkinRevision());
        }
        if (canonManager != null) {
            canonManager.configureHologramTag(npc, deco.isShowNameplate() ? deco.getDisplayName() : null, worldName);
            canonManager.applyProfileEngine(npc, deco.getProfile(), deco.getId(), deco.getSkinId(), deco.getFallbackSkinName());
        }
        if (behaviorController != null && deco.getProfile().isModelEngine()) {
            behaviorController.applyInitialModelAnimation(npc, deco.getProfile());
        }
    }

    public void triggerAmbientDialogue(Player player, String decoId) {
        DecorationNpc deco = getDecoration(decoId);
        if (deco == null) return;
        long now = System.currentTimeMillis();
        if (now - talkCooldown.getOrDefault(player.getUniqueId(), 0L) < 400L) return;
        talkCooldown.put(player.getUniqueId(), now);

        List<String> lines = deco.getAmbientLines();
        if (lines.isEmpty()) {
            // Fall back to gentle non-intrusive reaction if no lines set
            player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().clone().add(0, 1.5, 0), 4, 0.25, 0.25, 0.25, 0);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.5f);
            return;
        }
        String quote = lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
        String wName = player.getWorld().getName().toLowerCase();
        boolean isBw = wName.contains("bw") || wName.contains("void") || wName.contains("drain");
        String prefix = deco.getDisplayName();
        String msg = isBw
                ? DragoncoteColor.stripToGrayscale(prefix + " &7\" " + quote + "\"")
                : DragoncoteColor.colorize(prefix + " &f➜ <#E2F6A8>\"" + quote + "\"");
        player.sendMessage(msg);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.6f, 1.5f);
    }

    private boolean isSpawnDimension(String worldName) {
        if (worldName == null) return false;
        String l = worldName.toLowerCase();
        return l.equals("main") || l.equals("snow") || l.equals("bw")
                || l.contains("spawn_main") || l.contains("spawn_snow") || l.contains("spawn_bw");
    }
}
