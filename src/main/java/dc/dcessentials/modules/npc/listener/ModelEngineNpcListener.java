package dc.dcessentials.modules.npc.listener;

import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import dc.dcessentials.modules.npc.NpcDialogueEngine;
import dc.dcessentials.modules.npc.NpcRegistry;
import dc.dcessentials.modules.npc.NpcSetupWand;
import dc.dcessentials.modules.npc.gui.NpcEditorGUI;
import dc.dcessentials.modules.npc.model.NpcCharacter;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Listens for ModelEngine 4 interactive rig click events.
 * Relays interactions directly into Citizens dialogue and the visual Editor Wand.
 */
public class ModelEngineNpcListener implements Listener {

    private final NpcRegistry registry;
    private final NpcDialogueEngine dialogueEngine;
    private final NpcSetupWand setupWand;
    private final NpcEditorGUI npcEditorGUI;

    public ModelEngineNpcListener(NpcRegistry registry, NpcDialogueEngine dialogueEngine,
                                 NpcSetupWand setupWand, NpcEditorGUI npcEditorGUI) {
        this.registry = registry;
        this.dialogueEngine = dialogueEngine;
        this.setupWand = setupWand;
        this.npcEditorGUI = npcEditorGUI;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onModelInteract(BaseEntityInteractEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        // ModelEngine fires for both hands; process only main hand clicks
        if (event.getSlot() != null && event.getSlot() != EquipmentSlot.HAND) {
            return;
        }

        Object original = event.getBaseEntity().getOriginal();
        if (!(original instanceof Entity)) return;
        Entity entity = (Entity) original;

        if (CitizensAPI.getPlugin() == null) return;
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);
        if (npc == null) return;

        String charId = matchCharId(npc);
        if (charId == null) return;

        BaseEntityInteractEvent.Action action = event.getAction();

        // Right-Click: INTERACT or INTERACT_ON
        if (action == BaseEntityInteractEvent.Action.INTERACT ||
            action == BaseEntityInteractEvent.Action.INTERACT_ON) {

            if (setupWand != null && setupWand.isEditorWand(player.getInventory().getItemInMainHand())) {
                if (player.hasPermission("dccosmetics.admin")) {
                    setupWand.recordEntityInteract(player.getUniqueId());
                    if (npcEditorGUI != null) {
                        npcEditorGUI.open(player, charId);
                    }
                    return;
                }
            }

            dialogueEngine.interact(player, charId);
        } else if (action == BaseEntityInteractEvent.Action.ATTACK) {
            // Left-Click: attack/interact
            dialogueEngine.interact(player, charId);
        }
    }

    private String matchCharId(NPC npc) {
        if (npc == null) return null;

        if (npc.data().has("dcnpc_id")) {
            String raw = npc.data().get("dcnpc_id").toString().toLowerCase();
            if (raw.contains(":")) {
                return raw.split(":")[0];
            }
            return raw;
        }

        String npcName = ChatColor.stripColor(npc.getName());
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
