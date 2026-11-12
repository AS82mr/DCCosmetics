package dc.dccosmetics.cinematic;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.DCMessage;
import dc.dccosmetics.worldgen.CinematicBiomeProvider;
import dc.dccosmetics.worldgen.VoidGenerator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileWriter;

public class CinematicWorldCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("dccosmetics.admin")) {
            DCMessage.sendError(sender, "You do not have permission to execute this command.");
            return true;
        }

        if (args.length == 0) {
            DCMessage.sendHelp(sender, "Usage: /setupcinematicworld <datapack|create|fix|setbiome> [worldname] [apply_dp|biome]");
            return true;
        }

        String sub = args[0].toLowerCase();

        // 1. DATAPACK GENERATION
        if (sub.equals("datapack")) {
            DCMessage.sendInfo(sender, "Generating cinematic datapack in primary level folder...");
            generateDatapackInPrimaryWorld(sender, true);
            return true;
        }

        // 2. CREATE VOID WORLD
        if (sub.equals("create")) {
            String worldName = args.length > 1 ? args[1] : "CinematicSpawn";
            
            // Only apply datapack if explicitly requested
            boolean applyDp = false;
            String biomeToApply = "the_void";
            
            if (args.length > 2) {
                String flag = args[2].toLowerCase();
                if (flag.equals("apply_dp") || flag.equals("apply_dp=true") || flag.equals("true") || flag.equals("yes")) {
                    applyDp = true;
                    biomeToApply = "desolation";
                } else if (!flag.equals("false") && !flag.equals("apply_dp=false")) {
                    biomeToApply = flag;
                }
            }

            if (applyDp) {
                sender.sendMessage(ChatColor.YELLOW + "Ensuring cinematic datapack is installed in primary world...");
                generateDatapackInPrimaryWorld(sender, false);
            }

            sender.sendMessage(ChatColor.AQUA + "Generating Void World: " + worldName + " (Biome: " + biomeToApply + ")...");

            WorldCreator wc = new WorldCreator(worldName);
            wc.generator(new VoidGenerator(biomeToApply));
            wc.biomeProvider(new CinematicBiomeProvider(biomeToApply));
            wc.generateStructures(false);

            World createdWorld = Bukkit.createWorld(wc);
            if (createdWorld != null) {
                Location spawn = new Location(createdWorld, 0, 63, 0);
                if (spawn.getBlock().getType() == Material.AIR) {
                    spawn.getBlock().setType(Material.BEDROCK, false);
                }
                createdWorld.setSpawnLocation(0, 64, 0);

                applyGameRules(createdWorld);
                applyWorldGuardSettings(createdWorld, sender);
                configureMyWorldsIfPresent(worldName);

                if (applyDp) {
                    final String b = biomeToApply;
                    Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
                        applyBiomeToWorldChunks(createdWorld, b, 8, sender);
                    }, 20L);
                }

                sender.sendMessage(ChatColor.GREEN + "Void World '" + worldName + "' created and locked down successfully!");
            } else {
                sender.sendMessage(ChatColor.RED + "Failed to create world '" + worldName + "'.");
            }
            return true;
        }

        // 3. FIX EXISTING WORLD
        if (sub.equals("fix")) {
            if (args.length < 2) {
                DCMessage.sendHelp(sender, "Usage: /setupcinematicworld fix <worldname> [biome_name]");
                return true;
            }
            String targetName = args[1];
            String biomeName = args.length > 2 ? args[2] : "desolation";

            World targetWorld = Bukkit.getWorld(targetName);
            if (targetWorld == null) {
                DCMessage.sendError(sender, "World '" + targetName + "' not found or not loaded.");
                return true;
            }

            DCMessage.sendInfo(sender, "Applying settings and flags to '" + targetName + "'...");
            applyGameRules(targetWorld);
            applyWorldGuardSettings(targetWorld, sender);
            configureMyWorldsIfPresent(targetName);

            if (biomeName.equalsIgnoreCase("desolation") || biomeName.equalsIgnoreCase("crimson_rot")) {
                generateDatapackInPrimaryWorld(sender, false);
            }

            Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), () -> {
                applyBiomeToWorldChunks(targetWorld, biomeName, 12, sender);
                DCMessage.sendGeneral(sender, "Finished fixing world '" + targetName + "'!");
            }, 20L);
            return true;
        }

        // 4. APPLY OR RESET BIOME ACROSS WORLD
        if (sub.equals("setbiome")) {
            if (args.length < 2) {
                DCMessage.sendHelp(sender, "Usage: /setupcinematicworld setbiome <worldname> [chunk_radius] [biome_name]");
                return true;
            }
            World targetWorld = Bukkit.getWorld(args[1]);
            if (targetWorld == null) {
                DCMessage.sendError(sender, "World '" + args[1] + "' not found.");
                return true;
            }
            int radius = 10;
            if (args.length > 2) {
                try {
                    radius = Integer.parseInt(args[2]);
                } catch (NumberFormatException ignored) {
                }
            }
            String biomeName = args.length > 3 ? args[3] : "desolation";
            applyBiomeToWorldChunks(targetWorld, biomeName, radius, sender);
            return true;
        }

        DCMessage.sendHelp(sender, "Usage: /setupcinematicworld <datapack|create|fix|setbiome> [worldname]");
        return true;
    }

    @SuppressWarnings({"removal", "deprecation"})
    private void applyGameRules(World world) {
        try {
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
            world.setGameRule(GameRule.DO_TILE_DROPS, false);
            world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
            world.setGameRule(GameRule.MOB_GRIEFING, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            world.setStorm(false);
            world.setThundering(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyWorldGuardSettings(World world, CommandSender sender) {
        try {
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
            RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
            if (regions == null) {
                sender.sendMessage(ChatColor.RED + "WorldGuard RegionManager is not available for " + world.getName());
                return;
            }

            if (regions.hasRegion("cinematic_global")) {
                regions.removeRegion("cinematic_global");
            }

            ProtectedRegion global = regions.getRegion(ProtectedRegion.GLOBAL_REGION);
            if (global == null) {
                global = new GlobalProtectedRegion(ProtectedRegion.GLOBAL_REGION);
                regions.addRegion(global);
            }

            global.setFlag(Flags.PASSTHROUGH, StateFlag.State.DENY);
            global.setFlag(Flags.BUILD, StateFlag.State.DENY);
            global.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
            global.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY);
            global.setFlag(Flags.USE, StateFlag.State.DENY);
            global.setFlag(Flags.INTERACT, StateFlag.State.DENY);
            global.setFlag(Flags.PVP, StateFlag.State.DENY);
            global.setFlag(Flags.MOB_SPAWNING, StateFlag.State.DENY);
            global.setFlag(Flags.LEAF_DECAY, StateFlag.State.DENY);
            global.setFlag(Flags.ICE_MELT, StateFlag.State.DENY);
            global.setFlag(Flags.SNOW_MELT, StateFlag.State.DENY);
            global.setFlag(Flags.SNOW_FALL, StateFlag.State.DENY);
            global.setFlag(Flags.VINE_GROWTH, StateFlag.State.DENY);
            global.setFlag(Flags.CROP_GROWTH, StateFlag.State.DENY);
            global.setFlag(Flags.MYCELIUM_SPREAD, StateFlag.State.DENY);
            global.setFlag(Flags.SOIL_DRY, StateFlag.State.DENY);
            global.setFlag(Flags.CORAL_FADE, StateFlag.State.DENY);
            global.setFlag(Flags.FIRE_SPREAD, StateFlag.State.DENY);
            global.setFlag(Flags.OTHER_EXPLOSION, StateFlag.State.DENY);
            global.setFlag(Flags.CREEPER_EXPLOSION, StateFlag.State.DENY);
            global.setFlag(Flags.TNT, StateFlag.State.DENY);

            setDynamicFlag(global, "grass-growth", StateFlag.State.DENY);
            setDynamicFlag(global, "mushroom-growth", StateFlag.State.DENY);
            setDynamicFlag(global, "enderman-grief", StateFlag.State.DENY);
            setDynamicFlag(global, "ender-build", StateFlag.State.DENY);

            regions.save();
            sender.sendMessage(ChatColor.GREEN + "Applied WorldGuard '__global__' protection flags to " + world.getName() + ".");
        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(ChatColor.RED + "Failed to apply WorldGuard flags: " + e.getMessage());
        }
    }

    private void setDynamicFlag(ProtectedRegion region, String flagName, StateFlag.State state) {
        try {
            Flag<?> flag = WorldGuard.getInstance().getFlagRegistry().get(flagName);
            if (flag instanceof StateFlag stateFlag) {
                region.setFlag(stateFlag, state);
            }
        } catch (Exception ignored) {
        }
    }

    private void configureMyWorldsIfPresent(String worldName) {
        try {
            Plugin myWorlds = Bukkit.getPluginManager().getPlugin("My_Worlds");
            if (myWorlds != null && myWorlds.isEnabled()) {
                String pluginName = DCCosmetics.getInstance().getDescription().getName();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mw setgenerator " + worldName + " " + pluginName);
            }
        } catch (Exception ignored) {
        }
    }

    private void generateDatapackInPrimaryWorld(CommandSender sender, boolean forceReloadNow) {
        try {
            org.bukkit.configuration.ConfigurationSection config = DCCosmetics.getInstance().getConfig().getConfigurationSection("cinematic_biomes");
            if (config == null) {
                File configFile = new File(DCCosmetics.getInstance().getDataFolder(), "cinematic_biomes.yml");
                if (!configFile.exists()) {
                    createDefaultConfig(configFile);
                }
                config = YamlConfiguration.loadConfiguration(configFile);
            }

            World primaryWorld = Bukkit.getWorlds().get(0);
            File worldFolder = primaryWorld.getWorldFolder();
            File packDir = new File(worldFolder, "datapacks/CinematicPack/data/cinematic/worldgen/biome/");
            if (!packDir.exists()) {
                packDir.mkdirs();
            }

            File metaFile = new File(worldFolder, "datapacks/CinematicPack/pack.mcmeta");
            try (FileWriter w = new FileWriter(metaFile)) {
                w.write("{\n" +
                        "  \"pack\": {\n" +
                        "    \"pack_format\": 48,\n" +
                        "    \"supported_formats\": [15, 99],\n" +
                        "    \"description\": \"Cinematic Desolation Environment\"\n" +
                        "  }\n" +
                        "}");
            }

            int count = 0;
            for (String biomeName : config.getKeys(false)) {
                File biomeFile = new File(packDir, biomeName + ".json");
                int sky = parseHex(config.getString(biomeName + ".sky_color", "#101214"));
                int fog = parseHex(config.getString(biomeName + ".fog_color", "#1A2626"));
                int water = parseHex(config.getString(biomeName + ".water_color", "#0A1718"));
                int waterFog = parseHex(config.getString(biomeName + ".water_fog_color", "#051010"));
                int grass = parseHex(config.getString(biomeName + ".grass_color", "#353A3A"));
                int foliage = parseHex(config.getString(biomeName + ".foliage_color", "#1B4D4D"));

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
                        "  },\n" +
                        "  \"spawners\": {},\n" +
                        "  \"spawn_costs\": {},\n" +
                        "  \"carvers\": [],\n" +
                        "  \"features\": []\n" +
                        "}";
                try (FileWriter w = new FileWriter(biomeFile)) {
                    w.write(biomeJson);
                }
                count++;
            }

            sender.sendMessage(ChatColor.GREEN + "Installed CinematicPack (" + count + " biomes) into primary level '" + primaryWorld.getName() + "'!");
            if (forceReloadNow || Registry.BIOME.get(new NamespacedKey("cinematic", "desolation")) == null) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:datapack enable \"file/CinematicPack\"");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:reload");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(ChatColor.RED + "Failed to generate datapack: " + e.getMessage());
        }
    }

    private void applyBiomeToWorldChunks(World world, String biomeInput, int chunkRadius, CommandSender sender) {
        try {
            Biome targetBiome = null;
            String lower = biomeInput.toLowerCase();

            // Check vanilla namespace
            if (lower.startsWith("minecraft:")) {
                targetBiome = Registry.BIOME.get(NamespacedKey.minecraft(lower.replace("minecraft:", "")));
            } else if (lower.equals("plains") || lower.equals("snowy_plains") || lower.equals("the_void") ||
                       lower.equals("desert") || lower.equals("forest") || lower.equals("ocean")) {
                targetBiome = Registry.BIOME.get(NamespacedKey.minecraft(lower));
            } else {
                // Custom cinematic biome
                String cleanKey = lower.contains(":") ? lower.split(":")[1] : lower;
                targetBiome = Registry.BIOME.get(new NamespacedKey("cinematic", cleanKey));
                if (targetBiome == null) {
                    targetBiome = Registry.BIOME.get(NamespacedKey.minecraft(cleanKey));
                }
            }

            if (targetBiome == null) {
                sender.sendMessage(ChatColor.RED + "Biome '" + biomeInput + "' could not be found in the registry.");
                return;
            }

            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();
            Location spawn = world.getSpawnLocation();
            int centerChunkX = spawn.getBlockX() >> 4;
            int centerChunkZ = spawn.getBlockZ() >> 4;

            int updatedChunks = 0;

            for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
                for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                    if (!world.isChunkGenerated(cx, cz)) continue;
                    paintChunkBiome(world, cx, cz, minY, maxY, targetBiome);
                    updatedChunks++;
                }
            }

            for (Chunk chunk : world.getLoadedChunks()) {
                int cx = chunk.getX();
                int cz = chunk.getZ();
                if (Math.abs(cx - centerChunkX) > chunkRadius || Math.abs(cz - centerChunkZ) > chunkRadius) {
                    paintChunkBiome(world, cx, cz, minY, maxY, targetBiome);
                    updatedChunks++;
                }
            }

            sender.sendMessage(ChatColor.GREEN + "Applied biome '" + targetBiome.getKey() + "' across " + updatedChunks + " chunks in " + world.getName() + "!");
        } catch (Exception e) {
            e.printStackTrace();
            sender.sendMessage(ChatColor.RED + "Error applying biome to chunks: " + e.getMessage());
        }
    }

    private void paintChunkBiome(World world, int chunkX, int chunkZ, int minY, int maxY, Biome biome) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                for (int y = minY; y < maxY; y += 4) {
                    world.setBiome(baseX + x, y, baseZ + z, biome);
                }
            }
        }
        world.refreshChunk(chunkX, chunkZ);
    }

    private int parseHex(String hex) {
        if (hex == null || hex.isEmpty()) return 0;
        if (hex.startsWith("#")) hex = hex.substring(1);
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void createDefaultConfig(File file) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("desolation.sky_color", "#101214");
        config.set("desolation.fog_color", "#1A2626");
        config.set("desolation.water_color", "#0A1718");
        config.set("desolation.water_fog_color", "#051010");
        config.set("desolation.grass_color", "#353A3A");
        config.set("desolation.foliage_color", "#1B4D4D");
        config.set("crimson_rot.sky_color", "#1A0505");
        config.set("crimson_rot.fog_color", "#2B0A0A");
        config.set("crimson_rot.water_color", "#250000");
        config.set("crimson_rot.water_fog_color", "#150000");
        config.set("crimson_rot.grass_color", "#2A2020");
        config.set("crimson_rot.foliage_color", "#551111");
        try {
            config.save(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}