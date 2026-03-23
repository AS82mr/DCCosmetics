package dc.dccosmetics.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.CosmeticTemplate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class CustomiseGUI implements InventoryHolder {
    private final Player player;
    private final CosmeticTemplate template;
    private final ItemStack heldItem;
    private final Inventory inventory;

    public CustomiseGUI(Player player, CosmeticTemplate template, ItemStack heldItem) {
        this.player = player;
        this.template = template;
        this.heldItem = heldItem;
        
        org.bukkit.configuration.file.YamlConfiguration cfg = DCCosmetics.getInstance().getCustomiseConfig();
        String title = cfg != null ? cfg.getString("title", "&5Customise: {cosmetic}") : "&5Customise: {cosmetic}";
        title = ChatColor.translateAlternateColorCodes('&', title.replace("{cosmetic}", template.getItemName()));
        int size = cfg != null ? cfg.getInt("size", 45) : 45;
        
        this.inventory = Bukkit.createInventory(this, size, title);
        build();
    }

    private void build() {
        org.bukkit.configuration.file.YamlConfiguration cfg = DCCosmetics.getInstance().getCustomiseConfig();
        if (cfg == null) return;
        
        if (cfg.getBoolean("fillItems.enabled", true)) {
            Material fillMat = Material.valueOf(cfg.getString("fillItems.material", "BLACK_STAINED_GLASS_PANE"));
            ItemStack filler = new ItemStack(fillMat);
            ItemMeta meta = filler.getItemMeta();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', cfg.getString("fillItems.name", " ")));
            filler.setItemMeta(meta);
            for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
        }

        renderConfigItem(cfg.getConfigurationSection("infoItem"));
        renderConfigItem(cfg.getConfigurationSection("detachItem"));

        List<String> colors = template.getAllowedColors();
        int slot = cfg.getInt("colorsStartSlot", 19);
        int maxSlot = cfg.getInt("colorsMaxSlot", 25);

        if (colors == null || colors.isEmpty()) {
            ConfigurationSection bCfg = cfg.getConfigurationSection("barrierItem");
            if (bCfg != null) {
                ItemStack barrier = new ItemStack(Material.valueOf(bCfg.getString("material", "BARRIER")));
                ItemMeta bMeta = barrier.getItemMeta();
                bMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', bCfg.getString("name", "&cNo Colors")));
                List<String> lore = new ArrayList<>();
                for (String l : bCfg.getStringList("lore")) lore.add(ChatColor.translateAlternateColorCodes('&', l));
                bMeta.setLore(lore);
                barrier.setItemMeta(bMeta);
                inventory.setItem(bCfg.getInt("slot", 22), barrier);
            }
        } else {
            for (String cData : colors) {
                if (slot > maxSlot) break;
                String[] parts = cData.split(":");
                String hex = parts[0];
                String name = parts.length > 1 ? parts[1] : hex;
                
                ItemStack icon = new ItemStack(Material.LEATHER_HORSE_ARMOR);
                LeatherArmorMeta meta = (LeatherArmorMeta) icon.getItemMeta();
                meta.setDisplayName(net.md_5.bungee.api.ChatColor.of(hex) + ChatColor.BOLD.toString() + ChatColor.translateAlternateColorCodes('&', name));
                try {
                    java.awt.Color jColor = java.awt.Color.decode(hex);
                    meta.setColor(Color.fromRGB(jColor.getRed(), jColor.getGreen(), jColor.getBlue()));
                } catch (Exception ignored) {}
                
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Click to dye your cosmetic!");
                lore.add(ChatColor.DARK_GRAY + hex); // Hidden hex code for parser
                meta.setLore(lore);
                icon.setItemMeta(meta);
                
                inventory.setItem(slot++, icon);
            }
        }
    }

    private void renderConfigItem(ConfigurationSection section) {
        if (section == null) return;
        ItemStack item = new ItemStack(Material.valueOf(section.getString("material", "STONE")));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', section.getString("name", "")));
        List<String> lore = new ArrayList<>();
        for (String l : section.getStringList("lore")) lore.add(ChatColor.translateAlternateColorCodes('&', l));
        meta.setLore(lore);
        item.setItemMeta(meta);
        inventory.setItem(section.getInt("slot", 0), item);
    }

    public void handleClick(int slot) {
        ItemStack clicked = inventory.getItem(slot);
        if (clicked == null || !clicked.hasItemMeta()) return;

        org.bukkit.configuration.file.YamlConfiguration cfg = DCCosmetics.getInstance().getCustomiseConfig();
        int detachSlot = cfg != null ? cfg.getInt("detachItem.slot", 40) : 40;

        if (slot == detachSlot) {
            // DETACH LOGIC
            ItemMeta meta = heldItem.getItemMeta();
            NamespacedKey idKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_id");
            NamespacedKey cKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_color");
            NamespacedKey origKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_original_name");

            String cosmeticId = meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
            String color = meta.getPersistentDataContainer().has(cKey, PersistentDataType.STRING) ? meta.getPersistentDataContainer().get(cKey, PersistentDataType.STRING) : DCCosmetics.getInstance().getSafeColor(template, null);

            meta.getPersistentDataContainer().remove(idKey);
            meta.getPersistentDataContainer().remove(cKey);

            String origName = meta.getPersistentDataContainer().has(origKey, PersistentDataType.STRING) ? meta.getPersistentDataContainer().get(origKey, PersistentDataType.STRING) : null;
            if (origName != null) {
                meta.setDisplayName(origName.equals(DCCosmetics.getInstance().formatMaterialName(heldItem.getType())) ? null : origName);
                meta.getPersistentDataContainer().remove(origKey);
            }

            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.removeIf(l -> ChatColor.stripColor(l).contains("✦ Cosmetic:"));
            meta.setLore(lore.isEmpty() ? null : lore);
            heldItem.setItemMeta(meta);

            player.getInventory().addItem(DCCosmetics.getInstance().createScroll(cosmeticId, color));
            player.sendMessage(ChatColor.GREEN + "Detached the cosmetic scroll!");
            player.closeInventory();
            return;
        }

        // Prevent clicking info books, background glass, or anything outside the designated color area!
        int startSlot = cfg != null ? cfg.getInt("colorsStartSlot", 19) : 19;
        int maxSlot = cfg != null ? cfg.getInt("colorsMaxSlot", 25) : 25;
        if (slot < startSlot || slot > maxSlot) return;

        if (clicked.getItemMeta().getLore() == null || clicked.getType() == Material.BARRIER) return;
        
        String hexColor = ChatColor.stripColor(clicked.getItemMeta().getLore().get(1));
        
        ItemMeta meta = heldItem.getItemMeta();
        NamespacedKey cKey = new NamespacedKey(DCCosmetics.getInstance(), "cosmetic_color");
        meta.getPersistentDataContainer().set(cKey, PersistentDataType.STRING, hexColor);

        heldItem.setItemMeta(meta);
        DCCosmetics.getInstance().updateCosmeticItem(heldItem);
        
        player.sendMessage(ChatColor.GREEN + "Dyed cosmetic to " + clicked.getItemMeta().getDisplayName() + ChatColor.GREEN + "!");
        player.closeInventory();
    }

    public void open() { player.openInventory(inventory); }
    @Override public Inventory getInventory() { return inventory; }
}