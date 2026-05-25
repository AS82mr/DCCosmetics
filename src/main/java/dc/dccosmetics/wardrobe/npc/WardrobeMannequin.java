package dc.dccosmetics.wardrobe.npc;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.ActiveCosmetic;
import dc.dccosmetics.model.CosmeticTemplate;
import dc.dccosmetics.wardrobe.WardrobeSession;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class WardrobeMannequin {

    private final WardrobeSession session;
    private final Location startLocation;
    private LivingEntity entity;
    private Object citizensNpc;
    private BukkitTask rotationTask;
    private float currentYaw;

    /** The currently-previewed cosmetic (spawned via the real engine) */
    private ActiveCosmetic previewCosmetic;

    public WardrobeMannequin(WardrobeSession session, Location startLocation) {
        this.session = session;
        this.startLocation = startLocation;
        this.currentYaw = startLocation != null ? startLocation.getYaw() : 0;
    }

    public void spawn() {
        if (startLocation == null) {
            DCCosmetics.getInstance().getLogger().warning("[WardrobeMannequin] startLocation is null — skipping spawn.");
            return;
        }

        // Ensure the chunk is loaded before spawning anything
        startLocation.getChunk().load(true);

        Player player = session.getPlayer();

        boolean citizensOk = false;
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("Citizens") != null) {
            citizensOk = spawnCitizensNpc(player);
        }

        if (!citizensOk) {
            // Fall back to armor stand if Citizens failed or isn't present
            spawnArmorStand();
        }

        if (entity == null) {
            DCCosmetics.getInstance().getLogger().severe("[WardrobeMannequin] Entity is still null after spawn attempts!");
            return;
        }

        // Apply player's current armor to the mannequin so they see their own gear
        applyPlayerArmor(player);

        // Slow rotation task
        rotationTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || !entity.isValid()) {
                    this.cancel();
                    return;
                }
                currentYaw += 1.8f;
                if (currentYaw >= 360f) currentYaw -= 360f;

                Location newLoc = entity.getLocation();
                newLoc.setYaw(currentYaw);
                entity.teleport(newLoc);
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 1L, 1L);
    }

    /**
     * Attempts to spawn a Citizens PLAYER-type NPC.
     * @return true if spawn succeeded, false if Citizens failed
     */
    private boolean spawnCitizensNpc(Player player) {
        try {
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry()
                    .createNPC(EntityType.PLAYER, player.getName());
            npc.spawn(startLocation);

            // Validate entity was actually created
            if (npc.getEntity() == null || !npc.getEntity().isValid()) {
                DCCosmetics.getInstance().getLogger().warning(
                    "[WardrobeMannequin] Citizens NPC spawned but entity is invalid — falling back to ArmorStand.");
                npc.destroy();
                return false;
            }

            this.citizensNpc = npc;
            this.entity = (LivingEntity) npc.getEntity();
            return true;
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning(
                "[WardrobeMannequin] Citizens spawn failed: " + e.getMessage() + " — falling back to ArmorStand.");
            return false;
        }
    }

    private void spawnArmorStand() {
        try {
            ArmorStand stand = (ArmorStand) startLocation.getWorld().spawnEntity(startLocation, EntityType.ARMOR_STAND);
            stand.setGravity(false);
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setCustomNameVisible(false);
            this.entity = stand;
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().severe("[WardrobeMannequin] ArmorStand fallback also failed: " + e.getMessage());
        }
    }

    private void applyPlayerArmor(Player player) {
        if (entity == null || player.getInventory() == null) return;
        if (entity.getEquipment() == null) return;

        ItemStack helmet     = player.getInventory().getHelmet();
        ItemStack chestplate = player.getInventory().getChestplate();
        ItemStack leggings   = player.getInventory().getLeggings();
        ItemStack boots      = player.getInventory().getBoots();
        ItemStack mainHand   = player.getInventory().getItemInMainHand();

        entity.getEquipment().setHelmet(helmet);
        entity.getEquipment().setChestplate(chestplate);
        entity.getEquipment().setLeggings(leggings);
        entity.getEquipment().setBoots(boots);
        entity.getEquipment().setItemInMainHand(mainHand);
    }

    public void destroy() {
        if (rotationTask != null) rotationTask.cancel();

        clearPreview();

        if (citizensNpc != null) {
            try {
                ((net.citizensnpcs.api.npc.NPC) citizensNpc).destroy();
            } catch (Exception e) {
                DCCosmetics.getInstance().getLogger().warning("[WardrobeMannequin] Citizens destroy failed: " + e.getMessage());
            }
        } else if (entity != null) {
            entity.remove();
        }
    }

    public LivingEntity getEntity() { return entity; }

    /**
     * Previews a cosmetic on the mannequin using the real holographic engine.
     * Does NOT modify the player's actual armor items.
     */
    public void previewCosmetic(CosmeticTemplate template) {
        if (entity == null || !entity.isValid()) {
            DCCosmetics.getInstance().getLogger().warning("[WardrobeMannequin] Cannot preview — entity is null or invalid.");
            return;
        }

        clearPreview();

        previewCosmetic = new ActiveCosmetic(entity, template, "#FFFFFF");
        previewCosmetic.spawn();

        // Play sound directly to the session player (reliable regardless of camera position)
        session.getPlayer().playSound(session.getPlayer(), org.bukkit.Sound.ITEM_ARMOR_EQUIP_IRON, 1.0f, 1.0f);
    }

    /**
     * Despawns the current preview cosmetic from the mannequin.
     */
    public void clearPreview() {
        if (previewCosmetic != null) {
            previewCosmetic.despawn();
            previewCosmetic = null;
        }
    }
}
