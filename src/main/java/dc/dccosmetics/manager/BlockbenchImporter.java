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

                // Blockbench base rotation quaternion
                Quaternionf qRot = new Quaternionf().rotationYXZ(
                        (float) Math.toRadians(rotY),
                        (float) Math.toRadians(rotX),
                        (float) Math.toRadians(rotZ)
                );

                java.util.List<FaceDef> faces = new java.util.ArrayList<>();
                
                // 3. True 3D Cube Generation: Generate 6 TextDisplay planes for every cube to give it actual depth!
                if (sx > 0 && sy > 0) {
                    // North face: Normal = -Z -> Yaw = 180
                    faces.add(new FaceDef("north", sx, sy, new Vector3f(0, 0, -sz/2.0f), new Vector3f(0, 180, 0)));
                    // South face: Normal = +Z -> Yaw = 0
                    faces.add(new FaceDef("south", sx, sy, new Vector3f(0, 0, sz/2.0f), new Vector3f(0, 0, 0)));
                }
                if (sz > 0 && sy > 0) {
                    // West face: Normal = -X -> Yaw = -90
                    faces.add(new FaceDef("west", sz, sy, new Vector3f(-sx/2.0f, 0, 0), new Vector3f(0, -90, 0)));
                    // East face: Normal = +X -> Yaw = 90
                    faces.add(new FaceDef("east", sz, sy, new Vector3f(sx/2.0f, 0, 0), new Vector3f(0, 90, 0)));
                }
                if (sx > 0 && sz > 0) {
                    // Up face: Normal = +Y -> Pitch = -90
                    faces.add(new FaceDef("up", sx, sz, new Vector3f(0, sy/2.0f, 0), new Vector3f(-90, 0, 0)));
                    // Down face: Normal = -Y -> Pitch = 90
                    faces.add(new FaceDef("down", sx, sz, new Vector3f(0, -sy/2.0f, 0), new Vector3f(90, 0, 0)));
                }

                // If it's literally a 2D plane in Blockbench, spawn 1 face. The engine will auto-duplicate it!
                if (faces.isEmpty()) {
                    faces.add(new FaceDef("flat", sx > 0 ? sx : 1, sy > 0 ? sy : 1, new Vector3f(0,0,0), new Vector3f(0,0,0)));
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

                for (FaceDef face : faces) {
                    // Orbit the face center around the BB origin
                    Vector3f faceCenter = new Vector3f(cx + face.offset.x, cy + face.offset.y, cz + face.offset.z);
                    Vector3f faceOffsetFromOrigin = new Vector3f(faceCenter).sub(origin);
                    faceOffsetFromOrigin.rotate(qRot);
                    Vector3f finalCenter = new Vector3f(origin).add(faceOffsetFromOrigin);

                    // Center the 16x16 BB grid around [0,0,0] so it attaches cleanly to the player's mount point!
                    float transX = ((finalCenter.x - 8.0f) / 16.0f) * globalScale;
                    float transY = ((finalCenter.y - 8.0f) / 16.0f) * globalScale;
                    float transZ = ((finalCenter.z - 8.0f) / 16.0f) * globalScale;

                    // Combine rotations
                    Quaternionf baseFaceRot = new Quaternionf().rotationYXZ(
                        (float) Math.toRadians(face.rot.y),
                        (float) Math.toRadians(face.rot.x),
                        (float) Math.toRadians(face.rot.z)
                    );
                    Quaternionf finalQuat = new Quaternionf(qRot).mul(baseFaceRot);
                    Vector3f euler = new Vector3f();
                    finalQuat.getEulerAnglesYXZ(euler);

                    // JOML getEulerAnglesYXZ maps: x=Pitch, y=Yaw, z=Roll
                    float pitch = (float) Math.toDegrees(euler.x);
                    float yaw = (float) Math.toDegrees(euler.y);
                    float roll = (float) Math.toDegrees(euler.z);

                    float scaleX = (face.w / 16.0f) * globalScale;
                    float scaleY = (face.h / 16.0f) * globalScale;

                    if (scaleX == 0) scaleX = 0.01f;
                    if (scaleY == 0) scaleY = 0.01f;

                    String nodeName = name + "_" + face.suffix;
                    String path = "nodes." + nodeName;
                    
                    config.set(path + ".scale", Arrays.asList(scaleX, scaleY, 0.01f));
                    config.set(path + ".translation", Arrays.asList(-transX, transY, transZ)); // Invert X for Minecraft!
                    config.set(path + ".rotation", Arrays.asList(pitch, -yaw, -roll)); // Invert Yaw and Roll for mirroring
                    config.set(path + ".color", hexColor);
                }

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

    private static class FaceDef {
        String suffix; float w, h; Vector3f offset, rot;
        FaceDef(String suffix, float w, float h, Vector3f offset, Vector3f rot) {
            this.suffix = suffix;
            this.w = w;
            this.h = h;
            this.offset = offset;
            this.rot = rot;
        }
    }
}