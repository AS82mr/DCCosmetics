package dc.dccosmetics.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.CosmeticTemplate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import dc.dccosmetics.model.PlayerProfile;
import dc.dccosmetics.model.ActiveCosmetic;

import java.util.ArrayList;
import java.util.List;

public class CosmeticsGUI implements InventoryHolder {
    private final Player player;
    private final GuiState state;
    private final Inventory inventory;

    public CosmeticsGUI(Player player, GuiState state) {
        this.player = player;
        this.state = state;
        this.inventory = Bukkit.createInventory(this, 54, ChatColor.DARK_RED + "Cosmetics | " + capitalize(state.getSelectedRarity()));
        build();
    }

    private void build() {
        inventory.clear();

        // 1. Fill Background
        ItemStack filler = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inventory.setItem(i, filler);

        // 2. Left Column Navigation (Using the Base64 strings from your layout)
        inventory.setItem(9, createHeadItem("basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmFlNzRjZDNjMzg1NTFmY2VlMGM0NzU2NmUxNjM3YzI2ODIyNzlmOTAyMjc0MDZhMDZkM2FkZjBkYzBhYzMxMiJ9fX0=", ChatColor.GREEN + "Chat Colors"));
        inventory.setItem(18, createHeadItem("basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjNjMDUwYWVhZWY1MmE0NTFhZGJhZWIxNDI4ZGZmOGZmNjFiMmEyZGU4Y2M1YTQzOTc5Yjk0MTU0NzQ3ZDhjMiJ9fX0=", ChatColor.AQUA + "Player Tags"));
        inventory.setItem(27, createHeadItem("basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzRkYTJhODg0MmY5ZGFjY2M4OTcxN2E0YmMxM2Q2Mjk3NzlkYTNjMWY3YmNkZTZjNGJjNWFlNzhkYTJmMmY1NiJ9fX0=", ChatColor.DARK_PURPLE + "Teleportation Colors"));
        inventory.setItem(36, createHeadItem("basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzIyNzY3MGQxNDg3OTQ5MTUzMDQ4MjdiMGViMDNlZmYyNzNjYTE1M2Y4NzRkYjVlOTA5NGQxY2RiYjYyNThhMiJ9fX0=", ChatColor.DARK_RED + "Cosmetics"));
        inventory.setItem(45, createHeadItem("basehead-eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzc2YTk5ZmY1NzUxY2U5N2Q1ZTFjZGQ1NjEzMThlMWY2ZDQxZjFjZDAyYWRjNjVmNDU2ODRkYTMxNTY0Zjc2MSJ9fX0=", ChatColor.YELLOW + "Glow Colors"));

        // Highlight the current tab
        inventory.setItem(37, createItem(Material.PURPLE_STAINED_GLASS_PANE, ChatColor.YELLOW + "Selected Menu"));

        // 3. Row 0: Rarity Filters
        inventory.setItem(2, createFilterItem(Material.LIME_DYE, Material.LIME_STAINED_GLASS_PANE, "uncommon", ChatColor.GREEN, state.getSelectedRarity()));
        inventory.setItem(3, createFilterItem(Material.LIGHT_BLUE_DYE, Material.LIGHT_BLUE_STAINED_GLASS_PANE, "rare", ChatColor.BLUE, state.getSelectedRarity()));
        inventory.setItem(4, createFilterItem(Material.PURPLE_DYE, Material.PURPLE_STAINED_GLASS_PANE, "epic", ChatColor.DARK_PURPLE, state.getSelectedRarity()));
        inventory.setItem(5, createFilterItem(Material.ORANGE_DYE, Material.ORANGE_STAINED_GLASS_PANE, "legendary", ChatColor.GOLD, state.getSelectedRarity()));

        // 4. Row 1: Type/Slot Filters
        inventory.setItem(12, createFilterItem(Material.LEATHER_HELMET, Material.GOLDEN_HELMET, "head", ChatColor.YELLOW, state.getSelectedType()));
        inventory.setItem(13, createFilterItem(Material.LEATHER_CHESTPLATE, Material.GOLDEN_CHESTPLATE, "chest", ChatColor.YELLOW, state.getSelectedType()));
        inventory.setItem(14, createFilterItem(Material.LEATHER_LEGGINGS, Material.GOLDEN_LEGGINGS, "waist", ChatColor.YELLOW, state.getSelectedType()));
        inventory.setItem(15, createFilterItem(Material.LEATHER_BOOTS, Material.GOLDEN_BOOTS, "boots", ChatColor.YELLOW, state.getSelectedType()));

        // 5. Rows 2 & 3: Render Filtered Cosmetics
        renderCosmetics();

        // 6. Row 4: Render Colors (If a cosmetic is selected)
        renderColors();

        // 7. Back Button
        inventory.setItem(49, createItem(Material.ARROW, ChatColor.GOLD + "Back to Menu"));
    }

    private void renderCosmetics() {
        int[] displaySlots = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
        int slotIndex = 0;

        for (CosmeticTemplate template : DCCosmetics.getInstance().getTemplateRegistry().getAllTemplates()) {
            if (template.getRarity().equalsIgnoreCase(state.getSelectedRarity()) &&
                    template.getEquipmentSlot().equalsIgnoreCase(state.getSelectedType())) {

                if (slotIndex >= displaySlots.length) break;

                // TODO: Replace with Base64 skull parser
                ItemStack item = createHeadItem(template.getGuiIconBase64(), ChatColor.AQUA + template.getId());
                // If it's the currently viewed one, make it glow or add lore
                if (template.getId().equals(state.getViewedCosmeticId())) {
                    ItemMeta meta = item.getItemMeta();
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.YELLOW + "Viewing colors below!");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }

                inventory.setItem(displaySlots[slotIndex], item);
                slotIndex++;
            }
        }
    }

    private void renderColors() {
        if (state.getViewedCosmeticId() == null) return;

        // Slot range for colors on the 5th row
        inventory.setItem(38, createColorItem(Material.RED_DYE, ChatColor.RED + "Red", "#FF5555"));
        inventory.setItem(39, createColorItem(Material.LIME_DYE, ChatColor.GREEN + "Green", "#55FF55"));
        inventory.setItem(40, createColorItem(Material.LIGHT_BLUE_DYE, ChatColor.AQUA + "Aqua", "#55FFFF"));
        inventory.setItem(41, createColorItem(Material.YELLOW_DYE, ChatColor.YELLOW + "Yellow", "#FFFF55"));
        inventory.setItem(42, createColorItem(Material.PURPLE_DYE, ChatColor.LIGHT_PURPLE + "Purple", "#FF55FF"));
        inventory.setItem(43, createColorItem(Material.WHITE_DYE, ChatColor.WHITE + "White", "#FFFFFF"));
    }
    private ItemStack createColorItem(Material mat, String name, String hex) {
        ItemStack item = createItem(mat, name);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + hex); // Hidden hex code for the click handler
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
        }

    public void handleClick(Player clicker, int slot, ClickType clickType) {
        // Handle Rarity Clicks
        if (slot == 2) { state.setSelectedRarity("uncommon"); build(); }
        else if (slot == 3) { state.setSelectedRarity("rare"); build(); }
        else if (slot == 4) { state.setSelectedRarity("epic"); build(); }
        else if (slot == 5) { state.setSelectedRarity("legendary"); build(); }

        // Handle Type Clicks
        else if (slot == 12) { state.setSelectedType("head"); state.setViewedCosmeticId(null); build(); }
        else if (slot == 13) { state.setSelectedType("chest"); state.setViewedCosmeticId(null); build(); }
        else if (slot == 14) { state.setSelectedType("waist"); state.setViewedCosmeticId(null); build(); }
        else if (slot == 15) { state.setSelectedType("boots"); state.setViewedCosmeticId(null); build(); }

        // Handle Back / External Navigation
        else if (slot == 49) {
            clicker.closeInventory();
            clicker.performCommand("mainmenu");
        }
        else if (slot == 9) { clicker.performCommand("chatcolor"); }
        else if (slot == 18) { clicker.performCommand("tagmenu"); }


        // 1. Handle clicking a cosmetic to view its colors
        else if (slot >= 20 && slot <= 33 && clicker.getOpenInventory().getItem(slot) != null) {
            Material clickedType = clicker.getOpenInventory().getItem(slot).getType();
            if (clickedType != Material.BLACK_STAINED_GLASS_PANE && clickedType != Material.AIR) {
                String clickedName = ChatColor.stripColor(clicker.getOpenInventory().getItem(slot).getItemMeta().getDisplayName());
                state.setViewedCosmeticId(clickedName);
                build(); // Refresh the GUI to show the colors on the bottom row
            }
        }

        // 2. Handle clicking a color on the bottom row to EQUIP it
        else if (slot >= 38 && slot <= 43 && state.getViewedCosmeticId() != null) {
            ItemStack clickedItem = clicker.getOpenInventory().getItem(slot);

            // Safety check: Make sure it's not air, not glass, has meta, AND has lore!
            if (clickedItem != null && clickedItem.getType() != Material.AIR && clickedItem.getType() != Material.BLACK_STAINED_GLASS_PANE) {
                ItemMeta meta = clickedItem.getItemMeta();

                if (meta != null && meta.hasLore() && !meta.getLore().isEmpty()) {
                    clicker.closeInventory();

                    // Read the hex code from the lore safely
                    String hexColor = ChatColor.stripColor(meta.getLore().get(0));

                    DCCosmetics.getInstance().getProfileManager().equipCosmetic(clicker, state.getSelectedType(), state.getViewedCosmeticId(), hexColor);
                    clicker.sendMessage(ChatColor.GREEN + "Equipped " + state.getViewedCosmeticId() + "!");
                }
            }
        }

        // 3. Handle Unequipping (Slot 44)
        else if (slot == 44) {
            clicker.closeInventory();
            PlayerProfile profile = DCCosmetics.getInstance().getProfileManager().getProfile(clicker);
            if (profile != null) {
                ActiveCosmetic active = profile.getActiveCosmetic(state.getSelectedType());
                if (active != null) {
                    active.despawn();
                    profile.removeActiveCosmetic(state.getSelectedType());
                    profile.removeEquipped(state.getSelectedType());
                    clicker.sendMessage(ChatColor.RED + "Unequipped cosmetic!");
                }
            }
        }
    }

    private ItemStack createFilterItem(Material defaultMat, Material selectedMat, String name, ChatColor color, String currentState) {
        Material mat = name.equalsIgnoreCase(currentState) ? selectedMat : defaultMat;
        ItemStack item = createItem(mat, color + capitalize(name));
        if (name.equalsIgnoreCase(currentState)) {
            ItemMeta meta = item.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "▶ Selected");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
    private ItemStack createHeadItem(String base64, String name) {
        ItemStack item = dc.dccosmetics.util.HeadUtil.getCustomHead(base64);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    public void open() {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() { return inventory; }
}