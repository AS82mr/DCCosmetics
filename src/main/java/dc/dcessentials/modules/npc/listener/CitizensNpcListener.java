package dc.dcessentials.modules.npc.listener;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.modules.npc.NpcDialogueEngine;
import dc.dcessentials.modules.npc.NpcRegistry;
import dc.dcessentials.modules.npc.NpcSetupWand;
import dc.dcessentials.modules.npc.gui.NpcEditorGUI;
import dc.dcessentials.modules.npc.model.NpcCharacter;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Listens for Citizens interaction events and routes them into NpcDialogueEngine or NpcEditorGUI:
 * Right-Click with Editor Wand: opens full 54-slot visual NPC Dossier Editor GUI.
 * Right-Click normally: quest dialogue and interactive service options.
 * Left-Click: full character lore dialogue.
 */
public class CitizensNpcListener implements Listener {

    private final NpcRegistry registry;
    private final NpcDialogueEngine dialogueEngine;
    private final NpcSetupWand setupWand;
    private final NpcEditorGUI npcEditorGUI;
    private final java.util.Map<Integer, Long> lastSpawnProcessed = new java.util.concurrent.ConcurrentHashMap<>();

    public CitizensNpcListener(NpcRegistry registry, NpcDialogueEngine dialogueEngine,
                               NpcSetupWand setupWand, NpcEditorGUI npcEditorGUI) {
        this.registry = registry;
        this.dialogueEngine = dialogueEngine;
        this.setupWand = setupWand;
        this.npcEditorGUI = npcEditorGUI;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onNPCRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        NPC npc = event.getNPC();

        // Check Out-of-Storyline Decoration NPC first
        if (npc.data().has("dcnpc_deco_id")) {
            String decoId = npc.data().get("dcnpc_deco_id").toString();
            if (setupWand.isEditorWand(player.getInventory().getItemInMainHand())
                    && player.hasPermission("dccosmetics.admin")
                    && DCCosmetics.getInstance().getDecorationEditorGUI() != null) {
                setupWand.recordEntityInteract(player.getUniqueId());
                DCCosmetics.getInstance().getDecorationEditorGUI().open(player, decoId);
                return;
            }
            if (DCCosmetics.getInstance().getDecorationManager() != null) {
                DCCosmetics.getInstance().getDecorationManager().triggerAmbientDialogue(player, decoId);
            }
            return;
        }

        String charId = matchCharId(npc);
        if (charId == null) return;

        if (setupWand != null && setupWand.isEditorWand(player.getInventory().getItemInMainHand())) {
            if (player.hasPermission("dccosmetics.admin")) {
                event.setCancelled(true);
                setupWand.recordEntityInteract(player.getUniqueId());
                if (npcEditorGUI != null) {
                    npcEditorGUI.open(player, charId);
                }
                return;
            }
        }

        dialogueEngine.interact(player, charId);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onNPCLeftClick(NPCLeftClickEvent event) {
        NPC npc = event.getNPC();
        if (npc.data().has("dcnpc_deco_id")) {
            if (DCCosmetics.getInstance().getDecorationManager() != null) {
                DCCosmetics.getInstance().getDecorationManager().triggerAmbientDialogue(
                        event.getClicker(), npc.data().get("dcnpc_deco_id").toString());
            }
            return;
        }
        String charId = matchCharId(npc);
        if (charId != null) {
            dialogueEngine.interact(event.getClicker(), charId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
            if (event.getPlayer().isOnline()) {
                dialogueEngine.updatePlayerNpcVisibility(event.getPlayer());
            }
        }, 15L);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() != null && to != null && to.getWorld() != null && !from.getWorld().equals(to.getWorld())) {
            purgeClientEntities(event.getPlayer(), from.getWorld(), from);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNPCSpawn(net.citizensnpcs.api.event.NPCSpawnEvent event) {
        NPC npc = event.getNPC();
        if (npc.data().has("temp_walker")) return;
        if (event.getLocation() == null || event.getLocation().getWorld() == null) return;
        String worldName = event.getLocation().getWorld().getName();

        long now = System.currentTimeMillis();
        Long last = lastSpawnProcessed.get(npc.getId());
        if (last != null && (now - last) < 500L) {
            return;
        }
        lastSpawnProcessed.put(npc.getId(), now);

        // Decoration NPC spawn refresh
        if (npc.data().has("dcnpc_deco_id") && DCCosmetics.getInstance().getDecorationManager() != null) {
            String decoId = npc.data().get("dcnpc_deco_id").toString();
            dc.dcessentials.modules.npc.model.DecorationNpc deco = DCCosmetics.getInstance().getDecorationManager().getDecoration(decoId);
            if (deco != null) {
                Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
                    if (npc.isSpawned() && npc.getEntity() != null) {
                        DCCosmetics.getInstance().getDecorationManager().applyDecorationVisuals(npc, deco, worldName);
                    }
                }, 1L);
            }
            return;
        }

        String charId = matchCharId(npc);
        if (charId == null) return;

        NpcCharacter c = registry.getCharacter(charId);
        if (c == null) return;

        DCCosmetics.debugNpc("onNPCSpawn triggered: NPC #" + npc.getId() + " (" + npc.getName() + ") charId=" + charId + " world=" + worldName);
        Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
            if (!npc.isSpawned() || npc.getEntity() == null) return;
            DCCosmetics.getInstance().getSkinProcessor().applyProfileSkin(
                    npc, worldName, c.getId(), c.getSkin(), c.getBehaviorProfile().getSkinRevision());
            DCCosmetics.getInstance().getNpcCanonManager().configureNpcOverhead(npc, c, worldName);
            DCCosmetics.getInstance().getNpcCanonManager().applyEngineMode(npc, c);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World fromWorld = event.getFrom();
        World toWorld = player.getWorld();

        DCCosmetics.debugNpc("onPlayerChangedWorld: " + player.getName() + " " + fromWorld.getName() + " -> " + toWorld.getName());

        // 1. Send transient client ENTITY_DESTROY packet for origin world NPCs only
        purgeClientEntities(player, fromWorld, null);

        // 2. Refresh quest visibility, holograms, skins, and 3D rigs in destination world
        Runnable syncTask = () -> {
            if (!player.isOnline() || !player.getWorld().equals(toWorld)) return;

            dialogueEngine.updatePlayerNpcVisibility(player);

            if (CitizensAPI.getPlugin() != null && CitizensAPI.hasImplementation() && CitizensAPI.getNPCRegistry() != null) {
                for (NPC npc : CitizensAPI.getNPCRegistry()) {
                    if (npc.isSpawned() && npc.getEntity() != null && npc.getEntity().getWorld().equals(toWorld)) {
                        if (npc.data().has("dcnpc_deco_id") && DCCosmetics.getInstance().getDecorationManager() != null) {
                            dc.dcessentials.modules.npc.model.DecorationNpc deco =
                                    DCCosmetics.getInstance().getDecorationManager().getDecoration(npc.data().get("dcnpc_deco_id").toString());
                            if (deco != null) {
                                DCCosmetics.getInstance().getDecorationManager().applyDecorationVisuals(npc, deco, toWorld.getName());
                            }
                        } else {
                            String charId = matchCharId(npc);
                            NpcCharacter c = (charId != null) ? registry.getCharacter(charId) : null;
                            if (c != null) {
                                DCCosmetics.getInstance().getNpcCanonManager().configureNpcOverhead(npc, c, toWorld.getName());
                                DCCosmetics.getInstance().getSkinProcessor().applyProfileSkin(
                                        npc, toWorld.getName(), c.getId(), c.getSkin(), c.getBehaviorProfile().getSkinRevision());
                                if (c.getEngineType().equalsIgnoreCase("MODEL_ENGINE")) {
                                    DCCosmetics.getInstance().getNpcCanonManager().applyEngineMode(npc, c);
                                }
                            }
                        }

                        boolean hidden = dialogueEngine.shouldNpcEntityBeHiddenForPlayer(player, npc);
                        if (!hidden) {
                            DCCosmetics.getInstance().getNpcCanonManager().showNpcToPlayer(player, npc);
                        } else {
                            DCCosmetics.getInstance().getNpcCanonManager().hideNpcFromPlayer(player, npc);
                        }
                    }
                }
            }
        };

        Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), syncTask, 2L);
        Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), syncTask, 12L);
    }

    private void purgeClientEntities(Player player, World fromWorld, Location fromLoc) {
        if (player == null || fromWorld == null || !player.isOnline()) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) return;

        List<Integer> entityIds = new ArrayList<>();

        if (CitizensAPI.getPlugin() != null && CitizensAPI.hasImplementation() && CitizensAPI.getNPCRegistry() != null) {
            for (NPC npc : CitizensAPI.getNPCRegistry()) {
                if (npc.isSpawned() && npc.getEntity() != null && fromWorld.equals(npc.getEntity().getWorld())) {
                    entityIds.add(npc.getEntity().getEntityId());
                    for (org.bukkit.entity.Entity passenger : npc.getEntity().getPassengers()) {
                        entityIds.add(passenger.getEntityId());
                    }
                }
            }
        }

        if (entityIds.isEmpty()) return;

        DCCosmetics.debugNpc("purgeClientEntities sending ENTITY_DESTROY to " + player.getName() + " for " + entityIds.size() + " entities from " + fromWorld.getName());

        try {
            PacketContainer destroyPacket = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            try {
                it.unimi.dsi.fastutil.ints.IntList intList = new it.unimi.dsi.fastutil.ints.IntArrayList(entityIds.size());
                for (int id : entityIds) intList.add(id);
                destroyPacket.getModifier().write(0, intList);
            } catch (Throwable t1) {
                try {
                    destroyPacket.getIntLists().write(0, entityIds);
                } catch (Throwable t2) {
                    try {
                        destroyPacket.getIntegerArrays().write(0, entityIds.stream().mapToInt(i -> i).toArray());
                    } catch (Throwable ignored) {}
                }
            }
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, destroyPacket);
        } catch (Throwable ignored) {}
    }

    private String matchCharId(NPC npc) {
        if (npc == null) return null;

        if (npc.data().has("dcnpc_id")) {
            String raw = npc.data().get("dcnpc_id").toString().toLowerCase();
            return raw.contains(":") ? raw.split(":")[0] : raw;
        }

        String npcName = org.bukkit.ChatColor.stripColor(npc.getName());
        for (NpcCharacter c : registry.getAllCharacters()) {
            if (c.getShortName().equalsIgnoreCase(npcName) ||
                c.getId().equalsIgnoreCase(npcName) ||
                npcName.toLowerCase().contains(c.getShortName().toLowerCase()) ||
                npcName.toLowerCase().contains(c.getId().toLowerCase())) {
                return c.getId();
            }
        }
        return null;
    }
}
