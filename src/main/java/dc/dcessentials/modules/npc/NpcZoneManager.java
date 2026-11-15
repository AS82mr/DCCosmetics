package dc.dcessentials.modules.npc;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import dc.dcessentials.modules.npc.model.NpcCharacter;
import dc.dcessentials.modules.npc.model.NpcZone;
import dc.dcessentials.modules.npc.util.DragoncoteColor;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

/**
 * Manages district zones, warp point bindings, and tracks NPC placement across dimensions.
 */
public class NpcZoneManager {

    private final Map<String, NpcZone> zones = new LinkedHashMap<>();
    private final File zonesConfigFile;
    private final Set<UUID> warpingPlayers = Collections.synchronizedSet(new HashSet<>());

    public NpcZoneManager() {
        File npcDir = new File(DCCosmetics.getInstance().getDataFolder(), "npc");
        if (!npcDir.exists()) npcDir.mkdirs();
        File newZones = new File(npcDir, "zones.yml");
        File oldZones = new File(DCCosmetics.getInstance().getDataFolder(), "npc_zones.yml");
        if (!newZones.exists() && oldZones.exists()) oldZones.renameTo(newZones);
        this.zonesConfigFile = newZones.exists() ? newZones : (oldZones.exists() ? oldZones : newZones);

        registerDefaultZones();
        loadConfig();
    }

    private void registerDefaultZones() {
        // 1. Central Spawn Plaza
        NpcZone commandZone = new NpcZone("command", "Central Spawn Plaza", Material.BEACON, "warp spawn");
        commandZone.addAlias("spawn");
        commandZone.addAlias("hub");
        commandZone.addAlias("center");
        registerZone(commandZone, "corvus", "echo", "ronald", "vane", "sylas", "balthazar");

        // 2. The Tech Forge
        NpcZone forgeZone = new NpcZone("forge", "The Tech Forge", Material.ANVIL, "warp forge");
        forgeZone.addAlias("blacksmith");
        forgeZone.addAlias("techforge");
        registerZone(forgeZone, "kristoph", "marion");

        // 3. Spacetime Archives & Library
        NpcZone archivesZone = new NpcZone("archives", "Spacetime Archives & Library", Material.BOOKSHELF, "warp library");
        archivesZone.addAlias("library");
        archivesZone.addAlias("books");
        registerZone(archivesZone, "lyra");

        // 4. Sanctuary Markets
        NpcZone marketZone = new NpcZone("market", "Sanctuary Markets", Material.CHEST, "warp market");
        marketZone.addAlias("markets");
        marketZone.addAlias("bazaar");
        registerZone(marketZone, "vivienne", "jax");

        // 5. Grand Auction House
        NpcZone auctionZone = new NpcZone("auction", "The Grand Auction House", Material.GOLD_INGOT, "warp ah");
        auctionZone.addAlias("ah");
        auctionZone.addAlias("auctionhouse");
        registerZone(auctionZone, "valen");

        // 6. Guild Central Bank
        NpcZone bankZone = new NpcZone("bank", "Guild Central Bank", Material.GOLD_BLOCK, "warp bank");
        bankZone.addAlias("guildbank");
        bankZone.addAlias("treasury");
        registerZone(bankZone, "aurelius");

        // 7. Hearthstone Bakery
        NpcZone bakeryZone = new NpcZone("bakery", "Hearthstone Bakery", Material.BREAD, "warp bakery");
        bakeryZone.addAlias("baker");
        registerZone(bakeryZone, "clara");

        // 8. Snow Colosseum
        NpcZone colosseumZone = new NpcZone("colosseum", "Snow Colosseum", Material.DIAMOND_SWORD, "warp colosseum");
        colosseumZone.addAlias("arena");
        colosseumZone.addAlias("gladiator");
        registerZone(colosseumZone, "valerius");

        // 9. Fisherman House & Bayou Docks
        NpcZone fishZone = new NpcZone("fisherman", "Sanctuary Docks & Swamp", Material.FISHING_ROD, "warp fish");
        fishZone.addAlias("fish");
        fishZone.addAlias("docks");
        fishZone.addAlias("swamp");
        registerZone(fishZone, "finn");

        // 10. Haute Couture Salon
        NpcZone stylistZone = new NpcZone("stylist", "Haute Couture Salon", Material.DIAMOND, "warp stylist");
        stylistZone.addAlias("wardrobe");
        stylistZone.addAlias("salon");
        registerZone(stylistZone, "vivienne");

        // 11. Celestial Observatory
        NpcZone obsZone = new NpcZone("observatory", "Celestial Observatory", Material.ENCHANTING_TABLE, "warp observatory");
        obsZone.addAlias("celestial");
        obsZone.addAlias("spire_observatory");
        registerZone(obsZone, "hesper", "orion");

        // 12. Spruce Foothills Mines
        NpcZone minesZone = new NpcZone("mines", "Spruce Foothills Mines", Material.NETHERITE_PICKAXE, "warp mines");
        minesZone.addAlias("quarry");
        minesZone.addAlias("deepmines");
        registerZone(minesZone, "thrum");

        // 13. Terraced Farms
        NpcZone farmsZone = new NpcZone("farms", "Golden Terraced Farms", Material.WHEAT, "warp farm");
        farmsZone.addAlias("farm");
        farmsZone.addAlias("terrace");
        registerZone(farmsZone, "sylas");

        // 14. Ancient Dead Ruins
        NpcZone ruinsZone = new NpcZone("ruins", "Ancient Dead Ruins", Material.SOUL_LANTERN, "warp ruins");
        ruinsZone.addAlias("ancientruins");
        ruinsZone.addAlias("cemetery");
        registerZone(ruinsZone, "elden");

        // 15. North Gate Bastion
        NpcZone northGateZone = new NpcZone("north_gate", "North Gate Bastion", Material.SHIELD, "warp ng");
        northGateZone.addAlias("ng");
        northGateZone.addAlias("northgate");
        northGateZone.addAlias("gate");
        registerZone(northGateZone);

        // LowerCity Districts:
        // 16. Slums & Entry Gate
        NpcZone slumsZone = new NpcZone("slums", "LowerCity Slums", Material.IRON_DOOR, "warp slums");
        slumsZone.addAlias("lowercity_slums");
        slumsZone.addAlias("sewers");
        registerZone(slumsZone, "prisoner404", "nadia", "gideon", "sorsha", "rats", "twitch", "finch");

        // 17. The Black Drift Casino
        NpcZone casinoZone = new NpcZone("casino", "The Black Drift Casino", Material.EMERALD, "warp casino");
        casinoZone.addAlias("drift");
        casinoZone.addAlias("blackdrift");
        casinoZone.addAlias("roulette");
        registerZone(casinoZone, "valerio", "slick", "brutus", "garrick");

        // 18. Black Market & Chop Shop
        NpcZone bmZone = new NpcZone("blackmarket", "Black Market & Chop Shop", Material.REDSTONE_TORCH, "warp blackmarket");
        bmZone.addAlias("bm");
        bmZone.addAlias("chopshop");
        bmZone.addAlias("contraband");
        registerZone(bmZone, "shade", "krux", "nyx", "vector", "roxy");

        // 19. Imperial Citadel
        NpcZone citadelZone = new NpcZone("citadel", "Imperial Citadel", Material.BLAST_FURNACE, "warp citadel");
        citadelZone.addAlias("bastion");
        citadelZone.addAlias("highgate");
        registerZone(citadelZone, "malakor", "kael", "thorne", "vael", "ignis", "theta");

        // 20. Imperial Spire
        NpcZone spireZone = new NpcZone("spire", "Imperial Spire Throne Room", Material.NETHERITE_HELMET, "warp spire");
        spireZone.addAlias("throne");
        spireZone.addAlias("emperor");
        registerZone(spireZone, "emperor");
    }

    private void registerZone(NpcZone zone, String... npcs) {
        for (String npc : npcs) zone.addBoundNpc(npc);
        zones.put(zone.getId(), zone);
    }

    public NpcZone getZone(String id) {
        if (id == null) return null;
        return zones.get(id.toLowerCase());
    }

    public String getZoneDisplayName(String zoneId) {
        if (zoneId == null) return "Sanctuary";
        NpcZone z = getZone(zoneId);
        return (z != null) ? z.getDisplayName() : zoneId;
    }

    public NpcZone getZoneByAlias(String nameOrAlias) {
        if (nameOrAlias == null) return null;
        String q = nameOrAlias.trim().toLowerCase();
        NpcZone direct = zones.get(q);
        if (direct != null) return direct;

        for (NpcZone z : zones.values()) {
            if (z.matches(q)) return z;
        }
        return null;
    }

    public Collection<NpcZone> getAllZones() {
        return zones.values();
    }

    public void setZoneWarpLocation(String zoneIdOrAlias, Location loc) {
        NpcZone zone = getZoneByAlias(zoneIdOrAlias);
        if (zone != null) {
            zone.setWarpLocation(loc);
            saveConfig();
        }
    }

    public boolean warpPlayer(Player player, String zoneIdOrAlias) {
        if (player == null || zoneIdOrAlias == null) return false;
        UUID uid = player.getUniqueId();
        if (warpingPlayers.contains(uid)) {
            return false; // Prevent recursive command loops!
        }

        warpingPlayers.add(uid);
        try {
            NpcZone zone = getZoneByAlias(zoneIdOrAlias);
            if (zone == null) {
                DCMessage.sendError(player, "District '" + zoneIdOrAlias + "' does not exist.");
                return false;
            }

            if (DCCosmetics.getInstance().getTeleportationManager() != null) {
                DCCosmetics.getInstance().getTeleportationManager().armContext(
                        player,
                        dc.dcessentials.modules.teleportation.TeleportContext.Type.FREE_COMMAND,
                        "warp"
                );
            }

            // 1. Direct calibrated warp location
            if (zone.getWarpLocation() != null && zone.getWarpLocation().getWorld() != null) {
                Location dest = zone.getWarpLocation().clone();
                String curWorld = player.getWorld().getName().toLowerCase();
                String destWorld = dest.getWorld().getName().toLowerCase();

                // If player is in a mirrored spawn dimension, keep them in their dimension
                boolean curIsSpawn = curWorld.contains("snow") || curWorld.contains("bw") || curWorld.contains("main");
                boolean destIsSpawn = destWorld.contains("snow") || destWorld.contains("bw") || destWorld.contains("main");
                if (curIsSpawn && destIsSpawn && !curWorld.equals(destWorld)) {
                    dest.setWorld(player.getWorld());
                }

                player.teleport(dest, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                DCMessage.sendWarp(player, "Arrived at " + zone.getDisplayName() + ".");
                return true;
            }

            // 2. Fallback to external non-recursive command (e.g. CMI / Essentials warp)
            String cmd = zone.getWarpCommand();
            if (cmd != null && !cmd.isEmpty()) {
                if (cmd.toLowerCase().startsWith("warp ")) {
                    String warpName = cmd.substring(5).trim();
                    boolean cmiSuccess = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cmi warp " + warpName + " " + player.getName());
                    if (!cmiSuccess) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "essentials:warp " + warpName + " " + player.getName());
                    }
                } else {
                    player.performCommand(cmd);
                }
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                DCMessage.sendWarp(player, "Routing to " + zone.getDisplayName() + "...");
                return true;
            }

            // DO NOT dump onto the NPC entity!
            DCMessage.sendError(player, "Warp anchor for " + zone.getDisplayName() + " has not been calibrated yet! Use /setwarp " + zone.getId() + " or the Setup Wand to set its location.");
            return false;
        } finally {
            warpingPlayers.remove(uid);
        }
    }

    /**
     * Checks Citizens for an NPC associated with character ID in the specified world.
     * Supports unloaded chunks by inspecting getStoredLocation().
     */
    public NPC findCitizensNpc(String charId, String worldName) {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null || !CitizensAPI.hasImplementation() || charId == null) return null;

        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            boolean match = false;
            if (npc.data().has("dcnpc_id")) {
                String rawTag = npc.data().get("dcnpc_id").toString();
                if (charId.contains(":")) {
                    match = rawTag.equalsIgnoreCase(charId);
                } else {
                    match = rawTag.equalsIgnoreCase(charId) || rawTag.equalsIgnoreCase(charId + ":1");
                }
            } else if (!charId.contains(":")) {
                String stripped = org.bukkit.ChatColor.stripColor(npc.getName()).trim();
                match = stripped.equalsIgnoreCase(charId);
            }
            if (match) {
                Location loc = (npc.isSpawned() && npc.getEntity() != null) ? npc.getEntity().getLocation() : npc.getStoredLocation();
                if (loc != null && loc.getWorld() != null) {
                    if (worldName == null || loc.getWorld().getName().equalsIgnoreCase(worldName)) {
                        return npc;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds all active Citizens NPCs belonging to this character across all worlds.
     */
    public List<NPC> findAllCitizensNpc(String charId) {
        List<NPC> list = new ArrayList<>();
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null || !CitizensAPI.hasImplementation() || charId == null) return list;

        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            boolean match = false;
            if (npc.data().has("dcnpc_id")) {
                String rawTag = npc.data().get("dcnpc_id").toString();
                if (charId.contains(":")) {
                    match = rawTag.equalsIgnoreCase(charId);
                } else {
                    match = rawTag.equalsIgnoreCase(charId) || rawTag.equalsIgnoreCase(charId + ":1");
                }
            } else if (!charId.contains(":")) {
                String stripped = org.bukkit.ChatColor.stripColor(npc.getName()).trim();
                match = stripped.equalsIgnoreCase(charId);
            }
            if (match) {
                list.add(npc);
            }
        }
        return list;
    }

    /**
     * Returns true if the NPC is currently spawned in any active world.
     */
    public boolean isNpcSpawnedAnywhere(String charId) {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null || !CitizensAPI.hasImplementation()) return false;

        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            String cId = null;
            if (npc.data().has("dcnpc_id")) {
                cId = npc.data().get("dcnpc_id").toString();
            } else {
                String stripped = org.bukkit.ChatColor.stripColor(npc.getName()).trim();
                if (stripped.equalsIgnoreCase(charId)) {
                    cId = stripped;
                }
            }
            if (cId != null && cId.equalsIgnoreCase(charId)) {
                if (npc.isSpawned()) return true;
            }
        }
        return false;
    }

    /**
     * Returns list of unspawned / missing NPCs across the entire registry.
     */
    public List<String> getMissingNpcs(NpcRegistry registry) {
        List<String> missing = new ArrayList<>();
        for (NpcCharacter c : registry.getAllCharacters()) {
            if (!isNpcSpawnedAnywhere(c.getId())) {
                missing.add(c.getId());
            }
        }
        return missing;
    }

    public void loadConfig() {
        if (!zonesConfigFile.exists()) {
            saveConfig();
            return;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(zonesConfigFile);
        ConfigurationSection sec = cfg.getConfigurationSection("zones");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            NpcZone zone = zones.get(key.toLowerCase());
            if (zone == null) {
                String dName = sec.getString(key + ".display_name", key);
                String cmd = sec.getString(key + ".warp_command", "warp " + key);
                zone = new NpcZone(key, dName, Material.COMPASS, cmd);
                zones.put(key.toLowerCase(), zone);
            }

            if (sec.contains(key + ".warp_location")) {
                try {
                    Location loc = sec.getLocation(key + ".warp_location");
                    if (loc != null) zone.setWarpLocation(loc);
                } catch (Throwable ignored) {}
            }

            if (sec.contains(key + ".bound_npcs")) {
                List<String> npcs = sec.getStringList(key + ".bound_npcs");
                for (String n : npcs) zone.addBoundNpc(n);
            }
        }
    }

    public void saveConfig() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (NpcZone zone : zones.values()) {
            String path = "zones." + zone.getId();
            cfg.set(path + ".display_name", zone.getDisplayName());
            cfg.set(path + ".warp_command", zone.getWarpCommand());
            if (zone.getWarpLocation() != null) {
                cfg.set(path + ".warp_location", zone.getWarpLocation());
            }
            cfg.set(path + ".bound_npcs", zone.getBoundNpcIds());
        }

        try {
            cfg.save(zonesConfigFile);
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().severe("[NpcZoneManager] Failed saving npc_zones.yml: " + e.getMessage());
        }
    }
}
