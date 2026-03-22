package dc.dccosmetics.manager;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.ActiveCosmetic;
import dc.dccosmetics.model.CosmeticTemplate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SculptManager {
    private final Map<UUID, LivingEntity> activeDummies = new HashMap<>();
    private final Map<UUID, ActiveCosmetic> activeSculpts = new HashMap<>();
    private final Map<UUID, String> activeSculptIds = new HashMap<>();
    private final Map<UUID, Object> activeNPCs = new HashMap<>(); // Holds Citizens NPCs

    public Map<UUID, LivingEntity> getActiveDummies() { return activeDummies; }

    public void startSculpting(Player admin, String templateId) {
        CosmeticTemplate template = DCCosmetics.getInstance().getTemplateRegistry().getTemplate(templateId);
        if (template == null) {
            admin.sendMessage("§cTemplate not found!");
            return;
        }

        clearSculpt(admin);

        // Spawn Mannequin
        Location loc = admin.getLocation();
        loc.setPitch(0f);
        LivingEntity dummyEntity;

        if (Bukkit.getPluginManager().getPlugin("Citizens") != null) {
            dummyEntity = spawnCitizensNPC(admin, templateId, loc);
        } else {
            ArmorStand dummy = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            dummy.setGravity(false);
            dummy.setArms(true);
            dummy.setBasePlate(false);
            dummy.setCustomName("§eSculpting: §b" + templateId);
            dummy.setCustomNameVisible(true);
            dummyEntity = dummy;
        }

        ActiveCosmetic active = new ActiveCosmetic(dummyEntity, template, "#FFFFFF");
        active.spawn();

        activeDummies.put(admin.getUniqueId(), dummyEntity);
        activeSculpts.put(admin.getUniqueId(), active);
        activeSculptIds.put(admin.getUniqueId(), templateId);
    }

    private LivingEntity spawnCitizensNPC(Player admin, String templateId, Location loc) {
        net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "§eSculpting: §b" + templateId);
        npc.spawn(loc);
        activeNPCs.put(admin.getUniqueId(), npc);
        return (LivingEntity) npc.getEntity();
    }

    public void openEditor(Player admin) {
        String currentId = activeSculptIds.get(admin.getUniqueId());
        if (currentId == null) {
            admin.sendMessage("§cYou are not currently sculpting anything! Use /cosmetics sculpt <id> first.");
            return;
        }
        DCCosmetics.getInstance().getDialogEditorManager().openMainMenu(admin, currentId);
    }

    public void toggleDummyVisibility(Player admin) {
        LivingEntity dummy = activeDummies.get(admin.getUniqueId());
        if (dummy != null) {
            boolean newState = !dummy.isInvisible();
            dummy.setInvisible(newState);
            admin.sendMessage("§aDummy visibility: " + (newState ? "§cHidden" : "§eVisible"));
        } else {
            admin.sendMessage("§cYou must spawn a sculpt dummy first!");
        }
    }

    public String getActiveSculptId(Player admin) {
        return activeSculptIds.get(admin.getUniqueId());
    }

    public void clearSculpt(Player admin) {
        if (activeSculpts.containsKey(admin.getUniqueId())) {
            activeSculpts.get(admin.getUniqueId()).despawn();
            activeSculpts.remove(admin.getUniqueId());
        }
        
        if (Bukkit.getPluginManager().getPlugin("Citizens") != null && activeNPCs.containsKey(admin.getUniqueId())) {
            destroyNPC(admin);
        } else if (activeDummies.containsKey(admin.getUniqueId())) {
            activeDummies.get(admin.getUniqueId()).remove(); // Standard ArmorStand cleanup
        }
        activeDummies.remove(admin.getUniqueId());
        activeNPCs.remove(admin.getUniqueId());
        activeSculptIds.remove(admin.getUniqueId());
    }

    private void destroyNPC(Player admin) {
        Object npcObj = activeNPCs.get(admin.getUniqueId());
        if (npcObj instanceof net.citizensnpcs.api.npc.NPC npc) {
            npc.destroy();
        }
    }

    public void refreshAllDummies() {
        // Hot-reload support!
        for (Map.Entry<UUID, String> entry : activeSculptIds.entrySet()) {
            UUID adminId = entry.getKey();
            String templateId = entry.getValue();
            
            CosmeticTemplate updatedTemplate = DCCosmetics.getInstance().getTemplateRegistry().getTemplate(templateId);
            LivingEntity dummy = activeDummies.get(adminId);
            
            if (updatedTemplate != null && dummy != null) {
                if (activeSculpts.containsKey(adminId)) activeSculpts.get(adminId).despawn();
                
                ActiveCosmetic active = new ActiveCosmetic(dummy, updatedTemplate, "#FFFFFF");
                active.spawn();
                activeSculpts.put(adminId, active);
            }
        }
    }
}
