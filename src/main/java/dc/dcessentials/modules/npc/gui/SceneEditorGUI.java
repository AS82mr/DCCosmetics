package dc.dcessentials.modules.npc.gui;

import dc.dcessentials.modules.npc.scene.SceneManager;
import dc.dcessentials.modules.npc.util.DragoncoteColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 27-Slot In-World Scene Entity & Workstation Tweaker GUI.
 * Fine-tune position (±0.1 blocks), rotation, facing, and removal of ModelEngine/MythicMobs scene entities.
 */
public class SceneEditorGUI implements Listener {

    private final SceneManager sceneManager;
    private final Map<UUID, UUID> editingEntity = new HashMap<>();

    public SceneEditorGUI(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    public void open(Player player, Entity entity) {
        if (entity == null || !entity.isValid()) {
            player.sendMessage(DragoncoteColor.PREFIX_ERROR + "Target entity is no longer valid.");
            return;
        }

        editingEntity.put(player.getUniqueId(), entity.getUniqueId());
        String name = entity.getCustomName() != null ? entity.getCustomName() : entity.getType().name();
        Inventory inv = Bukkit.createInventory(null, 27, "§8[Scene Tweaker] §6" + (name.length() > 16 ? name.substring(0, 16) : name));

        // Filler
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pMeta = pane.getItemMeta();
        if (pMeta != null) {
            pMeta.setDisplayName("§7");
            pane.setItemMeta(pMeta);
        }
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        Location loc = entity.getLocation();

        // Slot 4: Info
        ItemStack info = new ItemStack(Material.ARMOR_STAND);
        ItemMeta iMeta = info.getItemMeta();
        if (iMeta != null) {
            iMeta.setDisplayName("§6§lScene Entity Inspector");
            List<String> lore = new ArrayList<>();
            lore.add("§7Type / Name: §f" + name);
            lore.add("§7UUID: §8" + entity.getUniqueId());
            lore.add("§7Coordinates: §f" + loc.getWorld().getName() + " (" +
                    String.format("%.2f, %.2f, %.2f", loc.getX(), loc.getY(), loc.getZ()) + ")");
            lore.add("§7Facing Yaw: §e" + String.format("%.1f°", loc.getYaw()));
            iMeta.setLore(lore);
            info.setItemMeta(iMeta);
        }
        inv.setItem(4, info);

        // Slot 10: Nudge X
        ItemStack nudgeX = new ItemStack(Material.REDSTONE);
        ItemMeta xMeta = nudgeX.getItemMeta();
        if (xMeta != null) {
            xMeta.setDisplayName("§c§lNudge X-Axis");
            List<String> lore = new ArrayList<>();
            lore.add("§7Current X: §f" + String.format("%.2f", loc.getX()));
            lore.add("§f");
            lore.add("§e▶ Left-Click: §f+0.1 blocks");
            lore.add("§e▶ Right-Click: §f-0.1 blocks");
            lore.add("§e▶ Shift + Click: §f±0.5 blocks");
            xMeta.setLore(lore);
            nudgeX.setItemMeta(xMeta);
        }
        inv.setItem(10, nudgeX);

        // Slot 11: Nudge Y
        ItemStack nudgeY = new ItemStack(Material.EMERALD);
        ItemMeta yMeta = nudgeY.getItemMeta();
        if (yMeta != null) {
            yMeta.setDisplayName("§a§lNudge Y-Axis");
            List<String> lore = new ArrayList<>();
            lore.add("§7Current Y: §f" + String.format("%.2f", loc.getY()));
            lore.add("§f");
            lore.add("§e▶ Left-Click: §f+0.1 blocks");
            lore.add("§e▶ Right-Click: §f-0.1 blocks");
            lore.add("§e▶ Shift + Click: §f±0.5 blocks");
            yMeta.setLore(lore);
            nudgeY.setItemMeta(yMeta);
        }
        inv.setItem(11, nudgeY);

        // Slot 12: Nudge Z
        ItemStack nudgeZ = new ItemStack(Material.LAPIS_LAZULI);
        ItemMeta zMeta = nudgeZ.getItemMeta();
        if (zMeta != null) {
            zMeta.setDisplayName("§9§lNudge Z-Axis");
            List<String> lore = new ArrayList<>();
            lore.add("§7Current Z: §f" + String.format("%.2f", loc.getZ()));
            lore.add("§f");
            lore.add("§e▶ Left-Click: §f+0.1 blocks");
            lore.add("§e▶ Right-Click: §f-0.1 blocks");
            lore.add("§e▶ Shift + Click: §f±0.5 blocks");
            zMeta.setLore(lore);
            nudgeZ.setItemMeta(zMeta);
        }
        inv.setItem(12, nudgeZ);

        // Slot 14: Rotation Control
        ItemStack rot = new ItemStack(Material.COMPASS);
        ItemMeta rMeta = rot.getItemMeta();
        if (rMeta != null) {
            rMeta.setDisplayName("§e§lRotate Yaw (Orientation)");
            List<String> lore = new ArrayList<>();
            lore.add("§7Current Yaw: §f" + String.format("%.1f°", loc.getYaw()));
            lore.add("§f");
            lore.add("§e▶ Left-Click: §fRotate +15°");
            lore.add("§e▶ Right-Click: §fRotate -15°");
            lore.add("§e▶ Shift + Click: §fSnap to face you directly");
            rMeta.setLore(lore);
            rot.setItemMeta(rMeta);
        }
        inv.setItem(14, rot);

        // Slot 16: Despawn / Remove
        ItemStack del = new ItemStack(Material.LAVA_BUCKET);
        ItemMeta dMeta = del.getItemMeta();
        if (dMeta != null) {
            dMeta.setDisplayName("§4§lDespawn Scene Entity");
            List<String> lore = new ArrayList<>();
            lore.add("§cClick to permanently remove this scene entity from the world.");
            dMeta.setLore(lore);
            del.setItemMeta(dMeta);
        }
        inv.setItem(16, del);

        // Slot 22: Close
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = close.getItemMeta();
        if (cMeta != null) {
            cMeta.setDisplayName("§c§lClose Tweaker");
            close.setItemMeta(cMeta);
        }
        inv.setItem(22, close);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        if (!title.startsWith("§8[Scene Tweaker]")) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) return;

        UUID entId = editingEntity.get(player.getUniqueId());
        if (entId == null) return;

        Entity ent = Bukkit.getEntity(entId);
        if (ent == null || !ent.isValid()) {
            player.closeInventory();
            player.sendMessage(DragoncoteColor.PREFIX_ERROR + "Target scene entity is no longer active.");
            return;
        }

        Location loc = ent.getLocation().clone();
        double step = event.isShiftClick() ? 0.5 : 0.1;

        // Slot 10: X
        if (slot == 10) {
            double delta = event.isRightClick() ? -step : step;
            loc.add(delta, 0, 0);
            ent.teleport(loc);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
            open(player, ent);
            return;
        }

        // Slot 11: Y
        if (slot == 11) {
            double delta = event.isRightClick() ? -step : step;
            loc.add(0, delta, 0);
            ent.teleport(loc);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
            open(player, ent);
            return;
        }

        // Slot 12: Z
        if (slot == 12) {
            double delta = event.isRightClick() ? -step : step;
            loc.add(0, 0, delta);
            ent.teleport(loc);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
            open(player, ent);
            return;
        }

        // Slot 14: Rotation
        if (slot == 14) {
            float newYaw;
            if (event.isShiftClick()) {
                Location pLoc = player.getLocation();
                double dx = pLoc.getX() - loc.getX();
                double dz = pLoc.getZ() - loc.getZ();
                newYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            } else if (event.isRightClick()) {
                newYaw = loc.getYaw() - 15.0f;
            } else {
                newYaw = loc.getYaw() + 15.0f;
            }
            loc.setYaw(newYaw);
            ent.teleport(loc);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
            open(player, ent);
            return;
        }

        // Slot 16: Despawn
        if (slot == 16) {
            ent.remove();
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.2f);
            player.sendMessage(DragoncoteColor.PREFIX_INFO + "Despawned scene entity.");
            return;
        }

        // Slot 22: Close
        if (slot == 22) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.0f);
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        editingEntity.remove(event.getPlayer().getUniqueId());
    }
}
