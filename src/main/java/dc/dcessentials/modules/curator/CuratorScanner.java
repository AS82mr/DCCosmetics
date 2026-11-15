package dc.dcessentials.modules.curator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * TEMPORARY MODEL CURATOR SCANNER.
 * Scans live disk folders under plugins/ModelEngine/blueprints and plugins/ItemsAdder/contents
 * so every single model, furniture, and custom item on the server is indexed with its exact paths.
 */
public final class CuratorScanner {

    private CuratorScanner() {}

    public static List<CuratorEntry> scanAll(Plugin plugin) {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        Map<String, CuratorEntry> discovered = new LinkedHashMap<>();

        scanModelEngineBlueprints(pluginsDir, discovered);
        scanModelEngineRuntimeRegistry(discovered);
        scanItemsAdderContents(pluginsDir, discovered);

        List<CuratorEntry> result = new ArrayList<>(discovered.values());
        result.sort(Comparator
                .comparing((CuratorEntry e) -> e.getSourceType().ordinal())
                .thenComparing(CuratorEntry::getPackName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(CuratorEntry::getId, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static void scanModelEngineBlueprints(File pluginsDir, Map<String, CuratorEntry> out) {
        File bpRoot = new File(pluginsDir, "ModelEngine/blueprints");
        if (!bpRoot.exists() || !bpRoot.isDirectory()) return;

        File rpRoot = new File(pluginsDir, "ModelEngine/resource pack/assets/modelengine");
        Path pluginsPath = pluginsDir.toPath();

        try (Stream<Path> walk = Files.walk(bpRoot.toPath())) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bbmodel"))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        String id = fileName.substring(0, fileName.length() - ".bbmodel".length());
                        String key = "meg:" + id.toLowerCase(Locale.ROOT);

                        String relFromPlugins = normalize(pluginsPath.relativize(p).toString());
                        String relFromBp = normalize(bpRoot.toPath().relativize(p).toString());
                        String packName = relFromBp.contains("/")
                                ? relFromBp.substring(0, relFromBp.indexOf('/'))
                                : "root";

                        List<String> assets = new ArrayList<>();
                        assets.add("plugins/" + relFromPlugins);

                        if (rpRoot.exists()) {
                            File generatedModelDir = new File(rpRoot, "models/" + id);
                            if (generatedModelDir.exists()) {
                                assets.add("plugins/" + normalize(pluginsPath.relativize(generatedModelDir.toPath()).toString()) + "/");
                            }
                            File generatedItemDir = new File(rpRoot, "items/" + id);
                            if (generatedItemDir.exists()) {
                                assets.add("plugins/" + normalize(pluginsPath.relativize(generatedItemDir.toPath()).toString()) + "/");
                            }
                            File texDir = new File(rpRoot, "textures/entity/models");
                            if (texDir.exists()) {
                                File png = new File(texDir, id + ".png");
                                if (png.exists()) {
                                    assets.add("plugins/" + normalize(pluginsPath.relativize(png.toPath()).toString()));
                                }
                            }
                        }

                        List<String> animations = parseBbmodelAnimations(p);
                        out.put(key, new CuratorEntry(
                                id,
                                id,
                                packName,
                                CuratorEntry.SourceType.MEG,
                                "plugins/" + relFromPlugins,
                                "ARMOR_STAND",
                                0,
                                assets,
                                animations
                        ));
                    });
        } catch (Throwable ignored) {}
    }

    private static List<String> parseBbmodelAnimations(Path bbmodelFile) {
        List<String> names = new ArrayList<>();
        try {
            String raw = Files.readString(bbmodelFile, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            if (root.has("animations") && root.get("animations").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("animations");
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject animObj = el.getAsJsonObject();
                    if (animObj.has("name")) {
                        String n = animObj.get("name").getAsString();
                        if (n != null && !n.isBlank() && !names.contains(n)) {
                            names.add(n);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return names;
    }

    /**
     * Supplements disk scan with any blueprint currently loaded in ModelEngine's runtime registry
     * (in case a blueprint came from a jar or nested path).
     */
    @SuppressWarnings("unchecked")
    private static void scanModelEngineRuntimeRegistry(Map<String, CuratorEntry> out) {
        if (!Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) return;
        try {
            Class<?> apiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Object api = apiClass.getMethod("getAPI").invoke(null);
            Object registry = apiClass.getMethod("getModelRegistry").invoke(api);
            Object keysObj = registry.getClass().getMethod("getKeys").invoke(registry);
            if (keysObj instanceof Collection<?> keys) {
                for (Object k : keys) {
                    if (k == null) continue;
                    String id = k.toString();
                    String key = "meg:" + id.toLowerCase(Locale.ROOT);
                    out.putIfAbsent(key, new CuratorEntry(
                            id,
                            id,
                            "runtime",
                            CuratorEntry.SourceType.MEG,
                            "ModelEngine Runtime Registry",
                            "ARMOR_STAND",
                            0,
                            Collections.emptyList(),
                            Collections.emptyList()
                    ));
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void scanItemsAdderContents(File pluginsDir, Map<String, CuratorEntry> out) {
        File iaRoot = new File(pluginsDir, "ItemsAdder/contents");
        if (!iaRoot.exists() || !iaRoot.isDirectory()) return;

        Path pluginsPath = pluginsDir.toPath();
        Path iaPath = iaRoot.toPath();

        try (Stream<Path> walk = Files.walk(iaPath)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".yml") || n.endsWith(".yaml");
                    })
                    .forEach(p -> parseItemsAdderYaml(p, pluginsPath, iaPath, iaRoot, out));
        } catch (Throwable ignored) {}
    }

    private static void parseItemsAdderYaml(
            Path file,
            Path pluginsPath,
            Path iaPath,
            File iaRoot,
            Map<String, CuratorEntry> out
    ) {
        YamlConfiguration yml = new YamlConfiguration();
        try {
            yml.load(file.toFile());
        } catch (Throwable ignored) {
            return;
        }

        ConfigurationSection itemsSec = yml.getConfigurationSection("items");
        if (itemsSec == null) return;

        String relFromIa = normalize(iaPath.relativize(file).toString());
        String packFolder = relFromIa.contains("/") ? relFromIa.substring(0, relFromIa.indexOf('/')) : "root";
        String namespace = yml.getString("info.namespace", packFolder);
        String configRelPath = "plugins/" + normalize(pluginsPath.relativize(file).toString());

        for (String itemKey : itemsSec.getKeys(false)) {
            ConfigurationSection itemSec = itemsSec.getConfigurationSection(itemKey);
            if (itemSec == null) continue;

            String fullId = namespace + ":" + itemKey;
            ConfigurationSection behaviours = itemSec.getConfigurationSection("behaviours");
            boolean isFurniture = behaviours != null && behaviours.contains("furniture");

            ConfigurationSection resource = itemSec.getConfigurationSection("resource");
            String material = resource != null ? resource.getString("material", "PAPER") : "PAPER";
            int modelId = resource != null ? resource.getInt("model_id", 0) : 0;
            String modelPath = resource != null ? resource.getString("model_path") : null;
            List<String> textures = resource != null ? resource.getStringList("textures") : Collections.emptyList();

            List<String> assets = resolveIaAssetPaths(iaRoot, packFolder, namespace, modelPath, textures);
            CuratorEntry.SourceType type = isFurniture
                    ? CuratorEntry.SourceType.IA_FURNITURE
                    : CuratorEntry.SourceType.IA_ITEM;

            out.put("ia:" + fullId.toLowerCase(Locale.ROOT), new CuratorEntry(
                    fullId,
                    itemKey,
                    packFolder,
                    type,
                    configRelPath,
                    material,
                    modelId,
                    assets,
                    Collections.emptyList()
            ));
        }
    }

    private static List<String> resolveIaAssetPaths(
            File iaRoot,
            String packFolder,
            String namespace,
            String modelPath,
            List<String> textures
    ) {
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        File packDir = new File(iaRoot, packFolder);

        if (modelPath != null && !modelPath.isBlank()) {
            String clean = modelPath.contains(":") ? modelPath.substring(modelPath.indexOf(':') + 1) : modelPath;
            String ns = modelPath.contains(":") ? modelPath.substring(0, modelPath.indexOf(':')) : namespace;
            String[] candidates = {
                    "plugins/ItemsAdder/contents/" + packFolder + "/resourcepack/assets/" + ns + "/models/" + clean + ".json",
                    "plugins/ItemsAdder/contents/" + packFolder + "/resourcepack/models/" + clean + ".json",
                    "plugins/ItemsAdder/contents/" + packFolder + "/models/" + clean + ".json"
            };
            boolean addedExisting = false;
            for (String cand : candidates) {
                File f = new File(iaRoot.getParentFile().getParentFile(), cand.substring("plugins/".length()));
                if (f.exists()) {
                    resolved.add(cand);
                    addedExisting = true;
                }
            }
            if (!addedExisting) {
                resolved.add("plugins/ItemsAdder/contents/" + packFolder + " (model_path=" + modelPath + ")");
            }
        }

        if (textures != null) {
            for (String tex : textures) {
                if (tex == null || tex.isBlank()) continue;
                String clean = tex.endsWith(".png") ? tex : tex + ".png";
                String[] candidates = {
                        "plugins/ItemsAdder/contents/" + packFolder + "/textures/" + clean,
                        "plugins/ItemsAdder/contents/" + packFolder + "/resourcepack/assets/" + namespace + "/textures/" + clean,
                        "plugins/ItemsAdder/contents/" + packFolder + "/resourcepack/textures/" + clean
                };
                boolean added = false;
                for (String cand : candidates) {
                    File f = new File(iaRoot.getParentFile().getParentFile(), cand.substring("plugins/".length()));
                    if (f.exists()) {
                        resolved.add(cand);
                        added = true;
                    }
                }
                if (!added && !clean.isEmpty()) {
                    resolved.add("plugins/ItemsAdder/contents/" + packFolder + "/textures/" + clean);
                }
            }
        }

        if (resolved.isEmpty() && packDir.exists()) {
            resolved.add("plugins/ItemsAdder/contents/" + packFolder + "/");
        }
        return new ArrayList<>(resolved);
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }
}
