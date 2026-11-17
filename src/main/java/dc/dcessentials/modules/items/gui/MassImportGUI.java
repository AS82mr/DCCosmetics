package dc.dcessentials.modules.items.gui;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.items.hook.ExecutableItemsHook;
import dc.dcessentials.modules.items.model.DCEItem;
import dc.dcessentials.modules.items.model.ItemCategory;
import dc.dcessentials.modules.items.model.ItemRarity;
import dc.dcessentials.modules.items.model.ItemRegistry;
import dc.dcessentials.modules.items.scroll.ScrollType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MassImportGUI implements Listener {

    private static final String TITLE = ColorUtil.colorize("<#1E293B>&lDCE | Mass Item Importer");
    private final ItemRegistry itemRegistry;

    public MassImportGUI(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
    }

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, TITLE);

        // Border glass around the bottom 2 rows
        ItemStack border = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "&7", null);
        for (int i = 27; i < 54; i++) {
            gui.setItem(i, border);
        }

        // Info item
        ItemStack info = createGuiItem(Material.BOOK, "<#38BDF8>&lMass Import Instructions", List.of(
                "&71. Drop up to 27 items into the top grid.",
                "&72. Items can come from &eExecutableItems&7,",
                "&7   &bSkript&7, or &aCreative inventory&7.",
                "&73. The engine automatically detects:",
                "&8   • Category (Weapon, Armor, Tool, etc.)",
                "&8   • Sockets (Combat, Cosmetic, Mastery)",
                "&8   • Rarity & NBT Preservations",
                "&f",
                "<#0CBF93>&lClick CONFIRM to register all at once!"
        ));
        gui.setItem(47, info);

        // Confirm button
        ItemStack confirm = createGuiItem(Material.EMERALD_BLOCK, "<#10B981>&lCONFIRM BATCH IMPORT", List.of(
                "&7Registers all items above into DCEssentials.",
                "&7Stamps initial version &e1&7 and generates",
                "&7full unified tooltips & PDC tags.",
                "&f",
                "<#FCD34D>&l▶ Click to Ingest & Register"
        ));
        gui.setItem(49, confirm);

        // Cancel button
        ItemStack cancel = createGuiItem(Material.BARRIER, "<#EF4444>&lCancel & Exit", List.of("&7Close menu without registering."));
        gui.setItem(51, cancel);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int rawSlot = event.getRawSlot();

        // Allow interacting freely in the drop zone (slots 0 to 26) and player's own inventory (>= 54)
        if (rawSlot >= 0 && rawSlot < 27) return;
        if (rawSlot >= 54) return;

        // Bottom control rows
        event.setCancelled(true);

        if (rawSlot == 49) { // CONFIRM IMPORT
            Inventory inv = event.getInventory();
            int importedCount = 0;

            for (int i = 0; i < 27; i++) {
                ItemStack item = inv.getItem(i);
                if (item == null || item.getType().isAir()) continue;

                // 1. Check ExecutableItems hook
                String eiId = ExecutableItemsHook.getExecutableItemId(item);

                // 2. Determine or generate unique ID
                String id = (eiId != null && !eiId.isEmpty()) ? eiId : generateItemId(item);
                ItemCategory category = ItemCategory.detectFromItem(item);
                ItemRarity rarity = detectRarityFromItem(item);
                String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                        ? item.getItemMeta().getDisplayName()
                        : DCCosmetics.getInstance().formatMaterialName(item.getType());

                DCEItem dceItem = new DCEItem(id, 1, item.getType(), name, rarity, category);

                if (eiId != null) {
                    dceItem.setExecutableItemId(eiId);
                }

                // 3. Store full cloned template (preserves SkullMeta, textures, NBT, commands)
                dceItem.setTemplate(item.clone());

                // 4. Extract and preserve any existing lore lines (abilities, rules, explanations)
                if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
                    List<String> rawLore = item.getItemMeta().getLore();
                    List<String> preserved = new ArrayList<>();
                    for (String line : rawLore) {
                        String stripped = ChatColor.stripColor(line).trim();
                        if (stripped.startsWith(":") && stripped.endsWith(":")) continue;
                        if (stripped.toLowerCase().startsWith("===") && stripped.toLowerCase().endsWith("===")) continue;
                        if (stripped.toLowerCase().matches(".*(:common:|:uncommon:|:rare:|:epic:|:legendary:|:mastery:|:artifact:).*")) continue;
                        if (stripped.contains("component(s)")) continue;
                        if (stripped.startsWith("minecraft:")) continue;
                        if (stripped.toLowerCase().contains("infusion sockets")) continue;
                        if (stripped.toLowerCase().contains("✦ cosmetic:")) continue;
                        if (stripped.startsWith("✦") && stripped.toLowerCase().endsWith("(cosmetic)")) continue;
                        if (stripped.startsWith("✦") && stripped.toLowerCase().endsWith("skin")) continue;
                        if (stripped.toLowerCase().contains("drag & drop")) continue;
                        if (stripped.startsWith("When on ") || stripped.startsWith("When in ")) continue;
                        if (stripped.matches("^[+-]\\d+.*(Armor|Toughness|Knockback|Attack).*")) continue;
                        if (stripped.matches(".*(Surge|Ward|Resonance|Execute|Cleave|Siphon|Stasis|Catalyst).*·+.*")) continue;
                        if (stripped.startsWith("----------------------------------------") || stripped.startsWith("-------[")) continue;
                        if (stripped.contains("[🛡]") || stripped.contains("[🪽]") || stripped.contains("[🧪]") || stripped.contains("[🔮]")) continue;
                        if (stripped.toLowerCase().matches("^(?:rarity|ʀᴀʀɪᴛʏ)\\s*:.*$")) continue;
                        if (stripped.toLowerCase().matches("^(?:type|ᴛʏᴘᴇ)\\s*:.*$")) continue;
                        if (stripped.replaceAll("[\\-_=~*·•—–―\u2500-\u257F\u00A0 ]", "").isEmpty() && stripped.length() >= 3) continue;
                        preserved.add(line);
                    }
                    // Trim leading and trailing empty lines from preserved, keeping internal returns intact
                    while (!preserved.isEmpty() && ChatColor.stripColor(preserved.get(0)).trim().isEmpty()) {
                        preserved.remove(0);
                    }
                    while (!preserved.isEmpty() && ChatColor.stripColor(preserved.get(preserved.size() - 1)).trim().isEmpty()) {
                        preserved.remove(preserved.size() - 1);
                    }
                    if (!preserved.isEmpty()) {
                        dceItem.setCustomLore(preserved);
                    }
                }

                // 5. Auto-assign sockets based on category
                if (category == ItemCategory.WEAPON) {
                    dceItem.addAllowedSocket(ScrollType.COMBAT);
                    dceItem.addAllowedSocket(ScrollType.MASTERY);
                    dceItem.setEquipmentSlot("MAINHAND");
                } else if (category == ItemCategory.ARMOR) {
                    dceItem.addAllowedSocket(ScrollType.COSMETIC);
                    String matName = item.getType().name();
                    if (matName.endsWith("_HELMET") || item.getType() == Material.PLAYER_HEAD) dceItem.setEquipmentSlot("HEAD");
                    else if (matName.endsWith("_CHESTPLATE")) dceItem.setEquipmentSlot("CHEST");
                    else if (matName.endsWith("_LEGGINGS")) dceItem.setEquipmentSlot("LEGS");
                    else if (matName.endsWith("_BOOTS")) dceItem.setEquipmentSlot("FEET");
                } else if (category == ItemCategory.KEYSTONE) {
                    dceItem.addAllowedSocket(ScrollType.KEYSTONE);
                } else if (category == ItemCategory.TOOL) {
                    dceItem.addAllowedSocket(ScrollType.MASTERY);
                }

                if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData()) {
                    dceItem.setCustomModelData(item.getItemMeta().getCustomModelData());
                }

                itemRegistry.registerItem(dceItem);

                // 6. Update item in GUI slot directly preserving template & skull
                ItemStack updated = itemRegistry.createItemStack(id, item.getAmount());
                inv.setItem(i, updated);
                importedCount++;
            }

            if (importedCount > 0) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);
                DCMessage.sendGeneral(player, "Successfully imported &#FFFFFF" + importedCount + " &#0CBF93items into the DCEssentials Registry!");
                DCMessage.sendInfo(player, "Use &#38BDF8/dce editor <item_id> &#BAE6FDto fine-tune stats or sockets.");
            } else {
                DCMessage.sendError(player, "No valid items found in the drop slots (0-26).");
            }
        } else if (rawSlot == 51) { // CANCEL
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        Player player = (Player) event.getPlayer();

        // Return remaining items in slots 0-26 to player
        for (int i = 0; i < 27; i++) {
            ItemStack item = event.getInventory().getItem(i);
            if (item != null && !item.getType().isAir()) {
                player.getInventory().addItem(item).values().forEach(drop ->
                        player.getWorld().dropItemNaturally(player.getLocation(), drop));
            }
        }
    }

    private String generateItemId(ItemStack item) {
        String base = "";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            base = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
            base = base.replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_");
            if (base.startsWith("_")) base = base.substring(1);
            if (base.endsWith("_")) base = base.substring(0, base.length() - 1);
        }
        if (base.isEmpty()) {
            base = item.getType().name().toLowerCase();
        }

        String candidate = base;
        int count = 2;
        while (itemRegistry.getItem(candidate) != null) {
            candidate = base + "_" + count;
            count++;
        }
        return candidate;
    }

    private ItemRarity detectRarityFromItem(ItemStack item) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();
            if (lore != null) {
                // 1. Prefer explicit structured rarity identifier line (e.g. "&8Rarity: Epic")
                for (String rawLine : lore) {
                    String plain = ChatColor.stripColor(rawLine).trim().toLowerCase(Locale.ROOT);
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("^(?:rarity|ʀᴀʀɪᴛʏ)\\s*:\\s*([a-zA-Z]+)\\b")
                            .matcher(plain);
                    if (m.find()) {
                        return ItemRarity.fromString(m.group(1));
                    }
                }
                // 2. Fallback: match highest-tier rarity keyword as whole word (prevents "common" prose overriding)
                String fullLore = ChatColor.stripColor(String.join(" ", lore)).toUpperCase(Locale.ROOT);
                ItemRarity[] values = ItemRarity.values();
                for (int i = values.length - 1; i >= 0; i--) {
                    ItemRarity r = values[i];
                    if (fullLore.matches(".*\\b(" + r.name() + "|" + r.getDisplayName().toUpperCase(Locale.ROOT) + ")\\b.*")) {
                        return r;
                    }
                }
            }
        }
        return ItemRarity.COMMON;
    }

    private ItemStack createGuiItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            if (lore != null) {
                List<String> colored = new ArrayList<>();
                for (String l : lore) colored.add(ColorUtil.colorize(l));
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
