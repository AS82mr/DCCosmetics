package dc.dcessentials.modules.npc.model;

import org.bukkit.Location;
import org.bukkit.Material;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a geographical zone/district binding a cluster of NPCs and a warp anchor.
 */
public class NpcZone {
    private final String id;
    private String displayName;
    private Material icon;
    private String warpCommand;
    private Location warpLocation;
    private final List<String> boundNpcIds = new ArrayList<>();
    private final List<String> aliases = new ArrayList<>();

    public NpcZone(String id, String displayName, Material icon, String warpCommand) {
        this.id = id.toLowerCase();
        this.displayName = displayName;
        this.icon = icon != null ? icon : Material.COMPASS;
        this.warpCommand = warpCommand;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Material getIcon() { return icon; }
    public void setIcon(Material icon) { this.icon = icon; }
    public String getWarpCommand() { return warpCommand; }
    public void setWarpCommand(String warpCommand) { this.warpCommand = warpCommand; }
    public Location getWarpLocation() { return warpLocation; }
    public void setWarpLocation(Location warpLocation) { this.warpLocation = warpLocation; }

    public List<String> getAliases() { return aliases; }
    public void addAlias(String alias) {
        if (alias != null && !alias.trim().isEmpty() && !aliases.contains(alias.toLowerCase())) {
            aliases.add(alias.toLowerCase());
        }
    }
    public boolean matches(String query) {
        if (query == null) return false;
        String q = query.trim().toLowerCase();
        if (id.equalsIgnoreCase(q)) return true;
        for (String a : aliases) {
            if (a.equalsIgnoreCase(q)) return true;
        }
        return false;
    }

    public List<String> getBoundNpcIds() { return boundNpcIds; }
    public void addBoundNpc(String npcId) {
        if (!boundNpcIds.contains(npcId.toLowerCase())) {
            boundNpcIds.add(npcId.toLowerCase());
        }
    }
    public void removeBoundNpc(String npcId) { boundNpcIds.remove(npcId.toLowerCase()); }

    public boolean isMapped() {
        return warpLocation != null || (warpCommand != null && !warpCommand.isEmpty());
    }
}
