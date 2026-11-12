package dc.dcessentials.modules.items.listener;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.AdaptHook;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.modules.items.scroll.ScrollModel;
import dc.dcessentials.modules.items.scroll.ScrollRegistry;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class MasteryBoosterListener implements Listener {

    private final ScrollRegistry scrollRegistry;

    public MasteryBoosterListener(ScrollRegistry scrollRegistry) {
        this.scrollRegistry = scrollRegistry;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon.getType().isAir() || !weapon.hasItemMeta()) return;

        ItemMeta meta = weapon.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(ScrollDragListener.SOCKET_MASTERY_KEY, PersistentDataType.STRING)) return;

        String scrollId = pdc.get(ScrollDragListener.SOCKET_MASTERY_KEY, PersistentDataType.STRING);
        if (scrollId == null) return;

        ScrollModel scroll = scrollRegistry.getScroll(scrollId);
        if (scroll == null) return;

        String skillId = scroll.getMechanic("skill_id", "swords");
        double multiplier = scroll.getMechanic("xp_multiplier", 1.20);
        double bonusBase = event.getEntity() instanceof Monster ? 15.0 : 5.0;
        double bonusXp = bonusBase * (multiplier - 1.0);

        if (bonusXp > 0) {
            AdaptHook.giveXp(killer, skillId, bonusXp);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType().isAir() || !tool.hasItemMeta()) return;

        ItemMeta meta = tool.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(ScrollDragListener.SOCKET_MASTERY_KEY, PersistentDataType.STRING)) return;

        String scrollId = pdc.get(ScrollDragListener.SOCKET_MASTERY_KEY, PersistentDataType.STRING);
        if (scrollId == null) return;

        ScrollModel scroll = scrollRegistry.getScroll(scrollId);
        if (scroll == null) return;

        String skillId = scroll.getMechanic("skill_id", "pickaxe");
        double multiplier = scroll.getMechanic("xp_multiplier", 1.20);

        String matName = event.getBlock().getType().name();
        if (matName.contains("ORE") || matName.contains("DEBRIS") || matName.contains("AMETHYST")) {
            double bonusXp = 10.0 * (multiplier - 1.0);
            AdaptHook.giveXp(player, skillId, bonusXp);
        }
    }
}
