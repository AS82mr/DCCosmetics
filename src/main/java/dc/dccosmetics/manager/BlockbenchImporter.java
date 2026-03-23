package dc.dccosmetics.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dc.dccosmetics.DCCosmetics;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class BlockbenchImporter {

    private final DCCosmetics plugin = DCCosmetics.getInstance();

    public void importModel(String filename, CommandSender sender) {
        File importsFolder = new File(plugin.getDataFolder(), "imports");
        if (!importsFolder.exists()) importsFolder.mkdirs();

        File file = new File(importsFolder, filename);
        if (!file.exists()) {
            if (!filename.endsWith(".bbmodel")) {
                file = new File(importsFolder, filename + ".bbmodel");
            }
            if (!file.exists()) {
                sender.sendMessage(ChatColor.RED + "Could not find '" + filename + "' in the plugins/DCCosmetics/imports/ folder!");
                sender.sendMessage(ChatColor.GRAY + "Please drop your .bbmodel file there and try again.");
                return;
            }
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject bbmodel = JsonParser.parseReader(reader).getAsJsonObject();

            if (!bbmodel.has("elements")) {
                sender.sendMessage(ChatColor.RED + "This Blockbench model has no elements (cubes)!");
                return;
            }

            JsonArray elements = bbmodel.getAsJsonArray("elements");
            YamlConfiguration config = new YamlConfiguration();
            String id = file.getName().replace(".bbmodel", "").replace(".json", "").toLowerCase();
            
            config.set("id", id);
            config.set("type", "chest");
            config.set("rarity", "epic");
            config.set("blockbench", true);
            config.set("global-offset", Arrays.asList(0.0, 0.0, 0.0));

            int count = 0;
            Map<String, Integer> nameCounter = new HashMap<>();

            for (JsonElement el : elements) {
                JsonObject element = el.getAsJsonObject();
                
                String baseName = element.has("name") ? element.get("name").getAsString().replaceAll("[^a-zA-Z0-9]", "_").toLowerCase() : "node";
                nameCounter.put(baseName, nameCounter.getOrDefault(baseName, 0) + 1);
                String name = baseName + "_" + nameCounter.get(baseName);

                JsonArray from = element.getAsJsonArray("from");
                JsonArray to = element.getAsJsonArray("to");

                float fx = from.get(0).getAsFloat(); float fy = from.get(1).getAsFloat(); float fz = from.get(2).getAsFloat();
                float tx = to.get(0).getAsFloat(); float ty = to.get(1).getAsFloat(); float tz = to.get(2).getAsFloat();

                // 1. Global Scale: Make imported models 2.5x larger so they aren't microscopic!
                float globalScale = 2.5f;

                float cx = (fx + tx) / 2.0f;
                float cy = (fy + ty) / 2.0f;
                float cz = (fz + tz) / 2.0f;

                float sx = Math.abs(tx - fx);
                float sy = Math.abs(ty - fy);
                float sz = Math.abs(tz - fz);

                // Calculate true mathematical center and apply Pivot offsets
                Vector3f origin = new Vector3f(cx, cy, cz);
                if (element.has("origin")) {
                    JsonArray orig = element.getAsJsonArray("origin");
                    origin.set(orig.get(0).getAsFloat(), orig.get(1).getAsFloat(), orig.get(2).getAsFloat());
                }

                float rotX = 0, rotY = 0, rotZ = 0;
                if (element.has("rotation")) {
                    JsonArray rot = element.getAsJsonArray("rotation");
                    rotX = rot.get(0).getAsFloat(); rotY = rot.get(1).getAsFloat(); rotZ = rot.get(2).getAsFloat();
                }

                // Process Colors
                String hexColor = "#FFFFFF";
                if (element.has("color")) {
                    JsonElement colorEl = element.get("color");
                    if (colorEl.isJsonPrimitive() && colorEl.getAsJsonPrimitive().isString()) {
                        hexColor = colorEl.getAsString();
                        if (!hexColor.startsWith("#")) hexColor = "#" + hexColor;
                    } else if (colorEl.isJsonPrimitive() && colorEl.getAsJsonPrimitive().isNumber()) {
                        int c = colorEl.getAsInt();
                        String[] bbColors = {"#5e81ac", "#22c55e", "#ef4444", "#f97316", "#eab308", "#8b5cf6", "#14b8a6", "#64748b"};
                        if (c >= 0 && c < bbColors.length) hexColor = bbColors[c];
                    }
                }

                String compName = name;
                String path = "components." + compName;

                float originX = ((origin.x - 8.0f) / 16.0f) * globalScale;
                float originY = ((origin.y - 8.0f) / 16.0f) * globalScale;
                float originZ = ((origin.z - 8.0f) / 16.0f) * globalScale;

                float pivotOffsetX = ((cx - origin.x) / 16.0f) * globalScale;
                float pivotOffsetY = ((cy - origin.y) / 16.0f) * globalScale;
                float pivotOffsetZ = ((cz - origin.z) / 16.0f) * globalScale;

                float width = (sx / 16.0f) * globalScale;
                float height = (sy / 16.0f) * globalScale;
                float depth = (sz / 16.0f) * globalScale;

                config.set(path + ".type", "cube");
                // THE FIX: Allow exact 0.0 dimensions! This stops the engine from creating microscopic intersecting side-walls!
                config.set(path + ".width", width);
                config.set(path + ".height", height);
                config.set(path + ".depth", depth);

                // Invert X for Minecraft space mirroring
                config.set(path + ".local-offset", Arrays.asList(-originX, originY, originZ));
                config.set(path + ".pivot-offset", Arrays.asList(-pivotOffsetX, pivotOffsetY, pivotOffsetZ));
                config.set(path + ".rotation", Arrays.asList(rotX, -rotY, -rotZ));
                config.set(path + ".color", hexColor);

                count++;
            }

            File outputDir = new File(plugin.getDataFolder(), "cosmetics" + File.separator + "chest");
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                sender.sendMessage(ChatColor.RED + "Failed to create directory: " + outputDir.getAbsolutePath());
                return;
            }
            
            // Aggressively force the directory to be writable by the OS
            outputDir.setWritable(true, false);

            File outputFile = new File(outputDir, id + ".yml");
            if (outputFile.exists()) {
                // If the file already exists, force it to be writable so we can overwrite it
                outputFile.setWritable(true, false);
            }
            
            try {
                config.save(outputFile);
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Permission Denied: Could not save to " + outputFile.getPath() + ". Check your OS folder permissions!");
                e.printStackTrace();
                return;
            }

            sender.sendMessage(ChatColor.GREEN + "Successfully imported " + count + " elements from " + file.getName() + "!");
            sender.sendMessage(ChatColor.AQUA + "Saved to: " + outputFile.getPath());
            sender.sendMessage(ChatColor.YELLOW + "Use /cosmetics sculpt " + id + " to preview it!");
            
            // Auto-reload to immediately load the file!
            plugin.reloadConfigs();

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to parse Blockbench model: " + e.getMessage());
            e.printStackTrace();
        }
    }
}