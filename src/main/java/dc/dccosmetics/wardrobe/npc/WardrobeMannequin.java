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
    private volatile boolean rotationPaused = false;

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

        // Ensure the chunk is loaded and pitch is level (looking at horizon, not down at feet)
        startLocation.getChunk().load(true);
        startLocation.setPitch(0.0f);

        Player player = session.getPlayer();

        boolean useCitizens = false;
        try {
            java.io.File file = dc.dccosmetics.wardrobe.WardrobeManager.getWardrobeConfigFile();
            if (file != null && file.exists()) {
                org.bukkit.configuration.file.YamlConfiguration cfg =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
                useCitizens = cfg.getBoolean("mannequin.use-citizens", false);
            }
        } catch (Exception ignored) {}

        boolean citizensOk = false;
        if (useCitizens && org.bukkit.Bukkit.getPluginManager().getPlugin("Citizens") != null) {
            citizensOk = spawnCitizensNpc(player);
        }

        if (!citizensOk) {
            // Stable ArmorStand mannequin: immune to Pl-Hide-Pro and Citizens player entity disconnects
            spawnArmorStand();
        }

        if (entity == null) {
            DCCosmetics.getInstance().getLogger().severe("[WardrobeMannequin] Entity is still null after spawn attempts!");
            return;
        }

        // Apply player's current armor to the mannequin so they see their own gear
        applyPlayerArmor(player);

        // Smooth slow rotation task (2 ticks)
        rotationTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (entity == null || !entity.isValid() || !session.getPlayer().isOnline()) {
                    this.cancel();
                    return;
                }
                if (rotationPaused) {
                    return;
                }
                currentYaw += 1.8f;
                if (currentYaw >= 360f) currentYaw -= 360f;

                Location newLoc = entity.getLocation();
                newLoc.setYaw(currentYaw);
                newLoc.setPitch(0.0f);
                entity.teleport(newLoc);
                if (citizensNpc != null) {
                    entity.setRotation(currentYaw, 0.0f);
                }
            }
        }.runTaskTimer(DCCosmetics.getInstance(), 2L, 2L);
    }

    /**
     * Attempts to spawn a Citizens PLAYER-type NPC.
     * Note: We name the NPC "Wardrobe" instead of player.getName() to prevent Pl-Hide-Pro from
     * detecting a duplicate player name and disconnecting the real player.
     * The player's skin is applied via SkinTrait.
     * @return true if spawn succeeded, false if Citizens failed
     */
    private boolean spawnCitizensNpc(Player player) {
        try {
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry()
                    .createNPC(EntityType.PLAYER, "Wardrobe");

            // Hide the nameplate so it looks like an authentic mannequin
            npc.data().setPersistent(net.citizensnpcs.api.npc.NPC.Metadata.NAMEPLATE_VISIBLE, false);

            // Apply player's skin via Citizens SkinTrait
            try {
                Class<?> skinTraitClass = Class.forName("net.citizensnpcs.trait.SkinTrait");
                @SuppressWarnings("unchecked")
                Object skinTrait = npc.getOrAddTrait((Class) skinTraitClass);
                try {
                    skinTraitClass.getMethod("setSkinPersistent", Player.class).invoke(skinTrait, player);
                } catch (NoSuchMethodException e) {
                    skinTraitClass.getMethod("setSkinName", String.class).invoke(skinTrait, player.getName());
                }
            } catch (Exception e) {
                DCCosmetics.getInstance().getLogger().warning("[WardrobeMannequin] Failed to apply SkinTrait: " + e.getMessage());
            }

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
            this.entity.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
            this.entity.setInvisible(false);
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

            // Give the armor stand the player's skull so it displays their identity
            ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
            org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(session.getPlayer());
                head.setItemMeta(meta);
            }
            stand.getEquipment().setHelmet(head);

            this.entity = stand;
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().severe("[WardrobeMannequin] ArmorStand fallback also failed: " + e.getMessage());
        }
    }

    private void applyPlayerArmor(Player player) {
        if (entity == null) return;
        if (entity.getEquipment() == null) return;

        ItemStack[] armor = session.getSavedArmor();
        if (armor == null && player.getInventory() != null) {
            armor = player.getInventory().getArmorContents();
        }

        ItemStack boots = (armor != null && armor.length > 0 && armor[0] != null) ? armor[0].clone() : null;
        ItemStack leggings = (armor != null && armor.length > 1 && armor[1] != null) ? armor[1].clone() : null;
        ItemStack chestplate = (armor != null && armor.length > 2 && armor[2] != null) ? armor[2].clone() : null;
        ItemStack helmet = (armor != null && armor.length > 3 && armor[3] != null) ? armor[3].clone() : null;

        ItemStack mainHand = null;
        ItemStack[] savedInv = session.getSavedInventory();
        int heldSlot = session.getSavedHeldSlot();
        if (savedInv != null && heldSlot >= 0 && heldSlot < savedInv.length && savedInv[heldSlot] != null) {
            mainHand = savedInv[heldSlot].clone();
        }
        ItemStack offHand = session.getSavedOffHand() != null ? session.getSavedOffHand().clone() : null;

        entity.getEquipment().setBoots(boots);
        entity.getEquipment().setLeggings(leggings);
        entity.getEquipment().setChestplate(chestplate);
        if (helmet != null && helmet.getType() != org.bukkit.Material.AIR) {
            entity.getEquipment().setHelmet(helmet);
        } else if (citizensNpc != null) {
            entity.getEquipment().setHelmet(null);
        }
        entity.getEquipment().setItemInMainHand(mainHand);
        entity.getEquipment().setItemInOffHand(offHand);

        if (citizensNpc instanceof net.citizensnpcs.api.npc.NPC npc) {
            try {
                net.citizensnpcs.api.trait.trait.Equipment eqTrait =
                        npc.getOrAddTrait(net.citizensnpcs.api.trait.trait.Equipment.class);
                eqTrait.set(net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot.BOOTS, boots);
                eqTrait.set(net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot.LEGGINGS, leggings);
                eqTrait.set(net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot.CHESTPLATE, chestplate);
                eqTrait.set(net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot.HELMET,
                        (helmet != null && helmet.getType() != org.bukkit.Material.AIR) ? helmet : null);
                eqTrait.set(net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot.HAND, mainHand);
                eqTrait.set(net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot.OFF_HAND, offHand);
            } catch (Throwable ignored) {}
        }
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

    public void setRotationPaused(boolean paused) {
        this.rotationPaused = paused;
    }

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

        previewCosmetic = new ActiveCosmetic(entity, template, "#FFFFFF", java.util.Collections.singletonList(session.getPlayer()));
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
