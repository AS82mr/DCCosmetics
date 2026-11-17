package dc.dccosmetics.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class HeadUtil {

    /** Canonical unified Back / Return to Main Menu button texture. */
    public static final String BACK_BUTTON_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ2NmM1YmQwNDlmNzVhYzdhYWJhZTgyY2MzZmNlNDIzMzUzMmFhNTk4NjMyZmEzYmVmYjE1YWM0MGIwMTUyNiJ9fX0=";

    public static ItemStack createBackButton(String displayName, java.util.List<String> lore) {
        ItemStack item = getCustomHead(BACK_BUTTON_TEXTURE);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (displayName != null) {
                meta.setDisplayName(dc.dcessentials.core.util.ColorUtil.colorize(displayName));
            }
            if (lore != null && !lore.isEmpty()) {
                java.util.List<String> colored = new java.util.ArrayList<>(lore.size());
                for (String l : lore) {
                    colored.add(dc.dcessentials.core.util.ColorUtil.colorize(l));
                }
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates a Player Head with a custom Base64 texture.
     * * @param base64 The raw Base64 string or a string prefixed with "head;" or "basehead-"
     * @return An ItemStack of the custom head.
     */
    public static ItemStack getCustomHead(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (base64 == null || base64.isEmpty()) return head;

        // Clean up the string if it contains your config prefixes
        if (base64.startsWith("head;")) {
            base64 = base64.substring(5);
        } else if (base64.startsWith("basehead-")) {
            base64 = base64.substring(9);
        }

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            // Deterministic UUID per texture payload so identical custom heads stack cleanly
            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8)));
            profile.setProperty(new ProfileProperty("textures", base64));
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
        }

        return head;
    }
}