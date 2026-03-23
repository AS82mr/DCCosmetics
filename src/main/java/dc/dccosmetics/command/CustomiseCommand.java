package dc.dccosmetics.command;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.gui.GuiManager;
import dc.dccosmetics.model.CosmeticTemplate;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class CustomiseCommand implements CommandExecutor {
    private final GuiManager guiManager;

    public CustomiseCommand(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) {
            player.sendMessage(ChatColor.RED + "You must hold an enchanted cosmetic item to customise it!");
            return true;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        NamespacedKey idKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_id");
        if (!pdc.has(idKey, PersistentDataType.STRING)) {
            player.sendMessage(ChatColor.RED + "The item in your hand does not have a cosmetic applied to it!");
            return true;
        }

        String id = pdc.get(idKey, PersistentDataType.STRING);
        CosmeticTemplate temp = DCCosmetics.getInstance().getTemplateRegistry().getTemplate(id);
        if (temp == null) {
            player.sendMessage(ChatColor.RED + "The cosmetic on this item is corrupt or no longer exists.");
            return true;
        }

        guiManager.openCustomiseMenu(player, temp, item);
        return true;
    }
}