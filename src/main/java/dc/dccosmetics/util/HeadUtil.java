package dc.dccosmetics.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

public class HeadUtil {

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
            // Create a random UUID for the profile so it doesn't conflict with real players
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", base64));
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
        }

        return head;
    }
}