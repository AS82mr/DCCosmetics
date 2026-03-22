package dc.dccosmetics.model;

import org.bukkit.OfflinePlayer;
import java.util.HashMap;
import java.util.Map;

public class PlayerProfile {
    private final OfflinePlayer player;
    // Maps equipment slot (e.g., "head") to the Cosmetic ID
    private final Map<String, String> equippedCosmetics = new HashMap<>();
    // Maps equipment slot to the Hex Color
    private final Map<String, String> equippedColors = new HashMap<>();
    // Maps equipment slot to the actual live holographic entity
    private final Map<String, ActiveCosmetic> activeCosmetics = new HashMap<>();

    public PlayerProfile(OfflinePlayer player) {
        this.player = player;
    }

    public OfflinePlayer getPlayer() { return player; }

    public String getEquippedCosmetic(String slot) { return equippedCosmetics.get(slot); }
    public String getEquippedColor(String slot) { return equippedColors.getOrDefault(slot, "#FFFFFF"); }

    public void setEquipped(String slot, String cosmeticId, String hexColor) {
        equippedCosmetics.put(slot, cosmeticId);
        equippedColors.put(slot, hexColor);
    }

    public void removeEquipped(String slot) {
        equippedCosmetics.remove(slot);
        equippedColors.remove(slot);
    }

    public ActiveCosmetic getActiveCosmetic(String slot) { return activeCosmetics.get(slot); }
    public void setActiveCosmetic(String slot, ActiveCosmetic active) { activeCosmetics.put(slot, active); }
    public void removeActiveCosmetic(String slot) { activeCosmetics.remove(slot); }
}