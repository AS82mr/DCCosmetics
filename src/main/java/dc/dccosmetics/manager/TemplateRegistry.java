package dc.dccosmetics.manager;

import dc.dccosmetics.DCCosmetics;
import dc.dccosmetics.model.CosmeticNode;
import dc.dccosmetics.model.CosmeticTemplate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.joml.Vector3f;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class TemplateRegistry {
    private final DCCosmetics plugin = DCCosmetics.getInstance();
    private final Logger logger = plugin.getLogger();

    private final Map<String, CosmeticTemplate> templates = new HashMap<>();
    private final Map<String, File> templateFiles = new HashMap<>();
    private final Map<String, Boolean> headTrackingMap = new HashMap<>();

    public void loadAll() {
        templates.clear();
        File cosmeticsFolder = new File(plugin.getDataFolder(), "cosmetics");
        if (!cosmeticsFolder.exists()) cosmeticsFolder.mkdirs();

        // ALWAYS ensure subfolders exist, even if the parent existed!
        new File(cosmeticsFolder, "head").mkdirs();
        new File(cosmeticsFolder, "boots").mkdirs();
        new File(cosmeticsFolder, "chest").mkdirs();
        new File(cosmeticsFolder, "waist").mkdirs();
        new File(cosmeticsFolder, "sword").mkdirs();

        int count = loadFolder(cosmeticsFolder);
        logger.info("[DEBUG] Successfully loaded " + count + " cosmetic templates.");
    }

    private int loadFolder(File folder) {
        int count = 0;
        File[] files = folder.listFiles();
        if (files == null) return count;

        for (File file : files) {
            if (file.isDirectory()) {
                count += loadFolder(file);
            } else if (file.getName().endsWith(".yml")) {
                if (loadTemplate(file) == null) count++;
            }
        }
        return count;
    }

    public String loadTemplate(File file) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            // Extract the exact line number of the syntax error!
            return e.getMessage().split("\n")[0];
        } catch (Exception e) {
            return "File read error: " + e.getMessage();
        }

        String id = config.getString("id");
        if (id == null) return "Missing 'id' in YAML.";

        String itemName = config.getString("item-name", id); // Fallback to ID if not set!
        String slot = config.getString("type", "head");
        String rarity = config.getString("rarity", "uncommon");
        String permission = config.getString("permission", "dccosmetics." + slot + "." + id);
        String iconBase64 = config.getString("display-item", "");

        headTrackingMap.put(id, config.getBoolean("head-tracking", false));
        boolean blockbench = config.getBoolean("blockbench", false);
        boolean animated = config.getBoolean("animated", false);
        String animationType = config.getString("animation-type", "spin");
        float animationSpeed = (float) config.getDouble("animation-speed", 4.0);

        Vector3f globalOffset = getVector(config, "global-offset", new Vector3f(0, 0, 0));
        Vector3f globalScale = getVector(config, "global-scale", new Vector3f(1.0f, 1.0f, 1.0f));
        Vector3f globalRotation = getVector(config, "global-rotation", new Vector3f(0, 0, 0));
        java.util.List<String> allowedColors = config.getStringList("allowed-colors");
        java.util.List<String> lore = config.getStringList("lore");
        
        String footstepParticle = config.getString("footstep.particle", null);
        String footstepColor = parseColor(config.getString("footstep.color"));
        String footstepSound = config.getString("footstep.sound", null);
        float soundVol = (float) config.getDouble("footstep.volume", 0.5);
        float soundPitch = (float) config.getDouble("footstep.pitch", 1.0);
        Map<String, CosmeticNode> nodes = new HashMap<>();

        ConfigurationSection componentsSec = config.getConfigurationSection("components");
        if (componentsSec != null) {
            for (String compKey : componentsSec.getKeys(false)) {
                ConfigurationSection comp = componentsSec.getConfigurationSection(compKey);
                String type = comp.getString("type", "solid");
                int sides = comp.getInt("sides", 6);
                double pitch = comp.getDouble("pitch", 90.0);
                Vector3f localOffset = getVector(comp, "local-offset", new Vector3f(0, 0, 0));
                
                boolean compAnim = comp.getBoolean("animated", false);
                String compAnimType = comp.getString("animation-type", "spin");
                float compAnimSpeed = (float) comp.getDouble("animation-speed", 4.0);
                double compOpacity = comp.getDouble("opacity", 1.0);

                // THE FIX: Safely parse the YAML color!
                String compColor = parseColor(comp.getString("color"));

                if (type.equals("solid")) {
                    float length = (float) comp.getDouble("length", 1.0);
                    float width = (float) comp.getDouble("width", 1.0);
                    for (int i = 0; i < sides; i++) {
                        float angleDeg = i * (360.0f / sides);
                        Vector3f scale = new Vector3f(width, length, 0.1f);
                        Vector3f rot = new Vector3f((float)pitch, angleDeg, 0);
                        nodes.put(compKey + "_" + i, new CosmeticNode(compKey + "_" + i, scale, localOffset, rot, compColor, compOpacity, compAnim, compAnimType, compAnimSpeed));
                    }
                }
                else if (type.equals("star")) {
                    float longLength = (float) comp.getDouble("length", 1.0);
                    float shortLength = (float) comp.getDouble("short-length", 0.5);
                    float width = (float) comp.getDouble("width", 0.2);
                    for (int i = 0; i < sides; i++) {
                        float angleDeg = i * (360.0f / sides);
                        float currentLength = (i % 2 == 0) ? longLength : shortLength;
                        Vector3f scale = new Vector3f(width, currentLength, 0.1f);
                        Vector3f rot = new Vector3f((float)pitch, angleDeg, 0);
                        nodes.put(compKey + "_" + i, new CosmeticNode(compKey + "_" + i, scale, localOffset, rot, compColor, compOpacity, compAnim, compAnimType, compAnimSpeed));
                    }
                }
                else if (type.equals("flat_ring")) {
                    double radius = comp.getDouble("radius", 0.5);
                    float width = (float) comp.getDouble("width", 0.1);
                    float segLength = (float) (2 * radius * Math.tan(Math.PI / sides)) * 1.05f;
                    for (int i = 0; i < sides; i++) {
                        float angleDeg = i * (360.0f / sides);
                        float angleRad = (float) Math.toRadians(angleDeg);
                        float transX = (float) (-Math.sin(angleRad) * radius) + localOffset.x;
                        float transZ = (float) (Math.cos(angleRad) * radius) + localOffset.z;
                        Vector3f scale = new Vector3f(segLength, width, 0.1f);
                        Vector3f trans = new Vector3f(transX, localOffset.y, transZ);
                        Vector3f rot = new Vector3f(90.0f, angleDeg, 0);
                        nodes.put(compKey + "_" + i, new CosmeticNode(compKey + "_" + i, scale, trans, rot, compColor, compOpacity, compAnim, compAnimType, compAnimSpeed));
                    }
                }
                else if (type.equals("cylinder")) {
                    double radius = comp.getDouble("radius", 0.5);
                    float height = (float) comp.getDouble("height", 0.4);
                    float segLength = (float) (2 * radius * Math.tan(Math.PI / sides)) * 1.05f;
                    for (int i = 0; i < sides; i++) {
                        float angleDeg = i * (360.0f / sides);
                        float angleRad = (float) Math.toRadians(angleDeg);
                        float transX = (float) (-Math.sin(angleRad) * radius) + localOffset.x;
                        float transZ = (float) (Math.cos(angleRad) * radius) + localOffset.z;
                        Vector3f scale = new Vector3f(segLength, height, 0.1f);
                        Vector3f trans = new Vector3f(transX, localOffset.y, transZ);
                        Vector3f rot = new Vector3f(0.0f, angleDeg, 0);
                        nodes.put(compKey + "_" + i, new CosmeticNode(compKey + "_" + i, scale, trans, rot, compColor, compOpacity, compAnim, compAnimType, compAnimSpeed));
                    }
                }
                else if (type.equals("cone")) {
                    double radius = comp.getDouble("radius", 0.5);
                    float height = (float) comp.getDouble("height", 0.6);
                    float width = (float) comp.getDouble("width", 0.1);
                    float inwardPitch = (float) comp.getDouble("inward-pitch", 35.0);
                    for (int i = 0; i < sides; i++) {
                        float angleDeg = i * (360.0f / sides);
                        float angleRad = (float) Math.toRadians(angleDeg);
                        float transX = (float) (-Math.sin(angleRad) * radius) + localOffset.x;
                        float transZ = (float) (Math.cos(angleRad) * radius) + localOffset.z;
                        Vector3f scale = new Vector3f(width, height, 0.1f);
                        Vector3f trans = new Vector3f(transX, localOffset.y, transZ);
                        Vector3f rot = new Vector3f(inwardPitch, angleDeg, 0);
                        nodes.put(compKey + "_" + i, new CosmeticNode(compKey + "_" + i, scale, trans, rot, compColor, compOpacity, compAnim, compAnimType, compAnimSpeed));
                    }
                }
                else if (type.equals("hourglass")) {
                    double radius = comp.getDouble("radius", 0.5);
                    float height = (float) comp.getDouble("height", 0.6);
                    float width = (float) comp.getDouble("width", 0.1);
                    float inwardPitch = (float) comp.getDouble("inward-pitch", 35.0);
                    for (int pass = 0; pass < 2; pass++) {
                        float passPitch = (pass == 0) ? inwardPitch : -inwardPitch;
                        float passY = (pass == 0) ? localOffset.y : localOffset.y + (height * 0.7f);
                        for (int i = 0; i < sides; i++) {
                            float angleDeg = i * (360.0f / sides);
                            float angleRad = (float) Math.toRadians(angleDeg);
                            float transX = (float) (-Math.sin(angleRad) * radius) + localOffset.x;
                            float transZ = (float) (Math.cos(angleRad) * radius) + localOffset.z;
                            Vector3f scale = new Vector3f(width, height, 0.1f);
                            Vector3f trans = new Vector3f(transX, passY, transZ);
                            Vector3f rot = new Vector3f(passPitch, angleDeg, 0);
                            nodes.put(compKey + "_" + pass + "_" + i, new CosmeticNode(compKey + "_" + pass + "_" + i, scale, trans, rot, compColor, compOpacity, compAnim, compAnimType, compAnimSpeed));
                        }
                    }
                }
                else if (type.equals("burst")) {
                    float length = (float) comp.getDouble("length", 0.6);
                    float width = (float) comp.getDouble("width", 0.1);
                    for (int i = 0; i < sides; i++) {
                        float phi = (float) Math.acos(1 - 2 * (i + 0.5f) / sides);
                        float theta = (float) (Math.PI * (1 + Math.sqrt(5)) * i);
                        float pitchDeg = (float) Math.toDegrees(phi - Math.PI / 2);
                        float yawDeg = (float) Math.toDegrees(theta);
                        Vector3f scale = new Vector3f(width, length, 0.1f);
                        Vector3f rot = new Vector3f(pitchDeg, yawDeg, 0);
                        nodes.put(compKey + "_" + i, new CosmeticNode(compKey + "_" + i, scale, localOffset, rot, compColor, compOpacity, compAnim, compAnimType, compAnimSpeed));
                    }
                }
                else if (type.equals("cube")) {
                    float width = (float) comp.getDouble("width", 1.0);
                    float height = (float) comp.getDouble("height", 1.0);
                    float depth = (float) comp.getDouble("depth", 1.0);
                    Vector3f rot = getVector(comp, "rotation", new Vector3f(0, 0, 0));
                    Vector3f pivotOffset = getVector(comp, "pivot-offset", new Vector3f(0, 0, 0));
                    java.util.List<String> hidden = comp.getStringList("hidden-faces");

                    if (width > 0 && height > 0) {
                        if (!hidden.contains("north")) addCubeFace(nodes, compKey + "_north", width, height, new Vector3f(0, 0, -depth/2), new Vector3f(0, 180, 0), rot, localOffset, pivotOffset, compColor, compOpacity, compAnim, compAnimType, compAnimSpeed);
                        if (!hidden.contains("south")) addCubeFace(nodes, compKey + "_south", width, height, new Vector3f(0, 0, depth/2), new Vector3f(0, 0, 0), rot, localOffset, pivotOffset, compColor, compOpacity, compAnim, compAnimType, compAnimSpeed);
                    }
                    if (depth > 0 && height > 0) {
                        if (!hidden.contains("west")) addCubeFace(nodes, compKey + "_west", depth, height, new Vector3f(-width/2, 0, 0), new Vector3f(0, -90, 0), rot, localOffset, pivotOffset, compColor, compOpacity, compAnim, compAnimType, compAnimSpeed);
                        if (!hidden.contains("east")) addCubeFace(nodes, compKey + "_east", depth, height, new Vector3f(width/2, 0, 0), new Vector3f(0, 90, 0), rot, localOffset, pivotOffset, compColor, compOpacity, compAnim, compAnimType, compAnimSpeed);
                    }
                    if (width > 0 && depth > 0) {
                        if (!hidden.contains("up")) addCubeFace(nodes, compKey + "_up", width, depth, new Vector3f(0, height/2, 0), new Vector3f(-90, 0, 0), rot, localOffset, pivotOffset, compColor, compOpacity, compAnim, compAnimType, compAnimSpeed);
                        if (!hidden.contains("down")) addCubeFace(nodes, compKey + "_down", width, depth, new Vector3f(0, -height/2, 0), new Vector3f(90, 0, 0), rot, localOffset, pivotOffset, compColor, compOpacity, compAnim, compAnimType, compAnimSpeed);
                    }
                }
                else if (type.equals("raw_node")) {
                    Vector3f scale = getVector(comp, "scale", new Vector3f(1, 1, 1));
                    Vector3f trans = getVector(comp, "translation", new Vector3f(0, 0, 0));
                    Vector3f rot = getVector(comp, "rotation", new Vector3f(0, 0, 0));
                    nodes.put(compKey, new CosmeticNode(compKey, scale, trans, rot, compColor, compOpacity, compAnim, compAnimType, compAnimSpeed));
                }
            }
        }

        ConfigurationSection nodesSection = config.getConfigurationSection("nodes");
        if (nodesSection != null) {
            for (String nodeKey : nodesSection.getKeys(false)) {
                try {
                    ConfigurationSection n = nodesSection.getConfigurationSection(nodeKey);
                    Vector3f scale = getVector(n, "scale", new Vector3f(1, 1, 1));
                    Vector3f trans = getVector(n, "translation", new Vector3f(0, 0, 0));
                    Vector3f rot = getVector(n, "rotation", new Vector3f(0, 0, 0));
                    String nodeColor = parseColor(n.getString("color")); // THE FIX
                    double nodeOpacity = n.getDouble("opacity", 1.0);
                    boolean nAnim = n.getBoolean("animated", false);
                    String nAnimType = n.getString("animation-type", "spin");
                    float nAnimSpeed = (float) n.getDouble("animation-speed", 4.0);
                    nodes.put(nodeKey, new CosmeticNode(nodeKey, scale, trans, rot, nodeColor, nodeOpacity, nAnim, nAnimType, nAnimSpeed));
                } catch (Exception e) {
                    logger.warning("[ERROR] Failed to parse node: " + nodeKey);
                }
            }
        }

        CosmeticTemplate template = new CosmeticTemplate(id, itemName, slot, rarity, permission, iconBase64, globalOffset, globalScale, globalRotation, allowedColors, lore, blockbench, animated, animationType, animationSpeed, footstepParticle, footstepColor, footstepSound, soundVol, soundPitch, nodes);
        templates.put(id, template);
        templateFiles.put(id, file);
        return null; // Return null if success
    }

    private void addCubeFace(Map<String, CosmeticNode> nodes, String id, float w, float h, Vector3f faceOffsetFromCenter, Vector3f faceRotEulers, Vector3f cubeRotEulers, Vector3f localOffset, Vector3f pivotOffset, String color, double opacity, boolean anim, String animType, float animSpeed) {
        org.joml.Quaternionf qRot = new org.joml.Quaternionf().rotationXYZ(
            (float) Math.toRadians(cubeRotEulers.x()),
            (float) Math.toRadians(cubeRotEulers.y()),
            (float) Math.toRadians(cubeRotEulers.z())
        );
        
        Vector3f totalOffset = new Vector3f(pivotOffset).add(faceOffsetFromCenter);
        totalOffset.rotate(qRot);
        
        org.joml.Quaternionf faceRot = new org.joml.Quaternionf().rotationXYZ(
            (float) Math.toRadians(faceRotEulers.x()),
            (float) Math.toRadians(faceRotEulers.y()),
            (float) Math.toRadians(faceRotEulers.z())
        );
        
        org.joml.Quaternionf finalRot = new org.joml.Quaternionf(qRot).mul(faceRot);
        
        Vector3f finalTrans = new Vector3f(localOffset).add(totalOffset);
        
        // Passing TRUE so faces remain perfectly double sided!
        nodes.put(id, new CosmeticNode(id, new Vector3f(w, h, 0.1f), finalTrans, new Vector3f(0,0,0), finalRot, color, opacity, anim, animType, animSpeed, true));
    }

    private Vector3f getVector(ConfigurationSection section, String key, Vector3f def) {
        if (!section.contains(key)) return def;
        java.util.List<Double> list = section.getDoubleList(key);
        if (list.size() >= 3) { return new Vector3f(list.get(0).floatValue(), list.get(1).floatValue(), list.get(2).floatValue()); }
        return def;
    }

    // THE ULTIMATE COLOR SANITIZER
    private String parseColor(String raw) {
        if (raw == null) return null;
        String clean = raw.trim().replace("\"", "").replace("'", "");
        if (clean.isEmpty() || clean.equals("#")) return null; // Kill broken colors instantly
        if (!clean.startsWith("#")) clean = "#" + clean;
        return clean;
    }

    public CosmeticTemplate getTemplate(String id) { return templates.get(id); }
    public File getTemplateFile(String id) { return templateFiles.get(id); }
    public boolean isCustomHeadTracking(String id) { return headTrackingMap.getOrDefault(id, false); }
    public java.util.Collection<CosmeticTemplate> getAllTemplates() { return templates.values(); }
}