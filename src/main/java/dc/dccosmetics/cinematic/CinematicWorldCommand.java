package dc.dccosmetics.cinematic;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import dc.dccosmetics.DCCosmetics;

import java.io.File;
import java.io.FileWriter;
import java.util.Random;

public class CinematicWorldCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!sender.hasPermission("dccosmetics.admin")) return true;

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /setupcinematicworld <datapack | create> [worldname]");
            return true;
        }

        if (args[0].equalsIgnoreCase("datapack")) {
            sender.sendMessage(ChatColor.YELLOW + "Reading cinematic_biomes.yml and generating Datapack...");
            generateDatapack(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            String worldName = args.length > 1 ? args[1] : "CinematicSpawn";
            sender.sendMessage(ChatColor.AQUA + "Generating Cinematic Void World: " + worldName + "...");
            
            WorldCreator wc = new WorldCreator(worldName);
            wc.generator(new ChunkGenerator() {
                @Override
                public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
                    // Void chunk generator
                }
            });
            Bukkit.createWorld(wc);
            sender.sendMessage(ChatColor.GREEN + "World '" + worldName + "' created!");
            sender.sendMessage(ChatColor.GRAY + "Teleport using: /mv tp " + worldName + " (If using Multiverse)");
            return true;
        }
        
        return true;
    }

    private void generateDatapack(CommandSender sender) {
        try {
            File configFile = new File(DCCosmetics.getInstance().getDataFolder(), "cinematic_biomes.yml");
            if (!configFile.exists()) {
                createDefaultConfig(configFile);
            }
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

            File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
            File packDir = new File(worldFolder, "datapacks/CinematicPack/data/cinematic/worldgen/biome/");
            if (!packDir.exists()) packDir.mkdirs();

            File metaFile = new File(worldFolder, "datapacks/CinematicPack/pack.mcmeta");
            try (FileWriter w = new FileWriter(metaFile)) {
                w.write("{\n  \"pack\": {\n    \"pack_format\": 48,\n    \"description\": \"Cinematic Desolation Environment\"\n  }\n}");
            }

            int count = 0;
            for (String biomeName : config.getKeys(false)) {
                File biomeFile = new File(packDir, biomeName + ".json");
                
                // Convert beautiful Hex codes into the ugly decimal format Minecraft requires!
                int sky = parseHex(config.getString(biomeName + ".sky_color", "#151515"));
                int fog = parseHex(config.getString(biomeName + ".fog_color", "#1A2525"));
                int water = parseHex(config.getString(biomeName + ".water_color", "#0B1516"));
                int waterFog = parseHex(config.getString(biomeName + ".water_fog_color", "#051010"));
                int grass = parseHex(config.getString(biomeName + ".grass_color", "#353838"));
                int foliage = parseHex(config.getString(biomeName + ".foliage_color", "#183636"));
                
                String biomeJson = "{\n" +
                        "  \"temperature\": 0.5,\n" +
                        "  \"downfall\": 0.5,\n" +
                        "  \"has_precipitation\": false,\n" +
                        "  \"temperature_modifier\": \"none\",\n" +
                        "  \"creature_spawn_probability\": 0.0,\n" +
                        "  \"effects\": {\n" +
                        "    \"sky_color\": " + sky + ",\n" +
                        "    \"fog_color\": " + fog + ",\n" +
                        "    \"water_color\": " + water + ",\n" +
                        "    \"water_fog_color\": " + waterFog + ",\n" +
                        "    \"grass_color\": " + grass + ",\n" +
                        "    \"foliage_color\": " + foliage + ",\n" +
                        "    \"ambient_sound\": \"minecraft:ambient.basalt_deltas.loop\",\n" +
                        "    \"mood_sound\": {\n" +
                        "      \"sound\": \"minecraft:ambient.basalt_deltas.mood\",\n" +
                        "      \"tick_delay\": 6000,\n" +
                        "      \"block_search_extent\": 8,\n" +
                        "      \"offset\": 2.0\n" +
                        "    }\n" +
                        "  }\n" +
                        "}";
                
                try (FileWriter w = new FileWriter(biomeFile)) { w.write(biomeJson); }
                count++;
            }
            
            sender.sendMessage(ChatColor.GREEN + "Generated Datapack with " + count + " biomes!");
            sender.sendMessage(ChatColor.YELLOW + "Please run /minecraft:reload to load the datapack into the registry!");
        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(ChatColor.RED + "Failed to generate Datapack!");
        }
    }

    private int parseHex(String hex) {
        if (hex == null || hex.isEmpty()) return 0;
        if (hex.startsWith("#")) hex = hex.substring(1);
        try { return Integer.parseInt(hex, 16); } catch (Exception e) { return 0; }
    }

    private void createDefaultConfig(File file) {
        YamlConfiguration config = new YamlConfiguration();
        
        // The exact vibe you requested: Gray grass, but Ocean Cyan tint injected into the leaves and fog!
        config.set("desolation.sky_color", "#101214");
        config.set("desolation.fog_color", "#1A2626");         // Deep cyan-tinted gray fog
        config.set("desolation.water_color", "#0A1718");
        config.set("desolation.water_fog_color", "#051010");
        config.set("desolation.grass_color", "#353A3A");       // Ashy dead gray
        config.set("desolation.foliage_color", "#1B4D4D");     // Distinctive Ocean Cyan hue for leaves!
        
        // An example of a future second biome!
        config.set("crimson_rot.sky_color", "#1A0505");
        config.set("crimson_rot.fog_color", "#2B0A0A");
        config.set("crimson_rot.water_color", "#250000");
        config.set("crimson_rot.water_fog_color", "#150000");
        config.set("crimson_rot.grass_color", "#2A2020");
        config.set("crimson_rot.foliage_color", "#551111");
        
        try { config.save(file); } catch (Exception ignored) {}
    }
}