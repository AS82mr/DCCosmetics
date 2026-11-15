package dc.dcessentials.modules.npc.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dc.dccosmetics.DCCosmetics;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Dynamically indexes all ModelEngine Scenes blueprints on disk (excluding pigeon and crow)
 * along with every animation and replaceable-skin texture slot parsed directly from .bbmodel JSON.
 */
public class SceneCatalog {

    public static class SceneModelInfo {
        public final String modelId;
        public final String displayName;
        public final String category; // BLACKSMITHS, MARKET, TOWNSFOLKS, GUARDS_BATTLE, WORKERS, BAKED_CUSTOM
        public final Material icon;
        public final File bbmodelFile;
        public final List<String> allAnimations;
        public final List<String> idleAnimations;
        public final List<String> socialAnimations;
        public final List<String> walkAnimations;
        public final boolean hasReplaceableSkin;

        public SceneModelInfo(String modelId, String displayName, String category, Material icon,
                              File bbmodelFile, List<String> allAnimations, boolean hasReplaceableSkin) {
            this.modelId = modelId;
            this.displayName = displayName;
            this.category = category;
            this.icon = icon;
            this.bbmodelFile = bbmodelFile;
            this.allAnimations = Collections.unmodifiableList(allAnimations);
            this.hasReplaceableSkin = hasReplaceableSkin;

            List<String> idles = new ArrayList<>();
            List<String> socials = new ArrayList<>();
            List<String> walks = new ArrayList<>();

            for (String anim : allAnimations) {
                String a = anim.toLowerCase();
                if (a.contains("walk") || a.contains("march") || a.contains("run") || a.contains("stroll")) {
                    walks.add(anim);
                } else if (a.contains("wave") || a.contains("salute") || a.contains("interact")
                        || a.contains("wake") || a.contains("see") || a.contains("play")
                        || a.contains("medic") || a.contains("boxing")) {
                    socials.add(anim);
                } else {
                    idles.add(anim);
                }
            }
            // Fallback: if no dedicated idle animations matched, use all non-walk animations
            if (idles.isEmpty() && !allAnimations.isEmpty()) {
                for (String anim : allAnimations) {
                    if (!walks.contains(anim)) idles.add(anim);
                }
            }
            this.idleAnimations = Collections.unmodifiableList(idles);
            this.socialAnimations = Collections.unmodifiableList(socials);
            this.walkAnimations = Collections.unmodifiableList(walks);
        }

        public String getDefaultIdleAnimation() {
            if (allAnimations.contains("idle")) return "idle";
            if (!idleAnimations.isEmpty()) return idleAnimations.get(0);
            return allAnimations.isEmpty() ? null : allAnimations.get(0);
        }

        public String getWalkAnimation() {
            if (allAnimations.contains("walk")) return "walk";
            if (!walkAnimations.isEmpty()) return walkAnimations.get(0);
            return null;
        }
    }

    public static final List<String> CATEGORY_ORDER = Arrays.asList(
            "BLACKSMITHS", "MARKET", "TOWNSFOLKS", "GUARDS_BATTLE", "WORKERS", "BAKED_CUSTOM"
    );

    private final Map<String, SceneModelInfo> modelsById = new LinkedHashMap<>();
    private final Map<String, List<SceneModelInfo>> byCategory = new LinkedHashMap<>();

    public void reload() {
        modelsById.clear();
        byCategory.clear();
        for (String cat : CATEGORY_ORDER) {
            byCategory.put(cat, new ArrayList<>());
        }

        File megPluginDir = new File(DCCosmetics.getInstance().getDataFolder().getParentFile(), "ModelEngine");
        File blueprintsDir = new File(megPluginDir, "blueprints");

        File scenesNpcsDir = new File(blueprintsDir, "Scenes" + File.separator + "NPCs");
        File scenesRootDir = new File(blueprintsDir, "Scenes");
        File bakedDir = new File(blueprintsDir, "dcnpc_baked");

        Set<File> seen = new LinkedHashSet<>();
        collectBbmodels(scenesNpcsDir, seen);
        collectBbmodels(scenesRootDir, seen);
        collectBbmodels(bakedDir, seen);

        for (File file : seen) {
            String rawName = file.getName().substring(0, file.getName().length() - ".bbmodel".length());
            String modelId = rawName.toLowerCase(Locale.ROOT);

            // Strictly exclude pigeon and crow blueprints
            if (isExcludedModel(modelId, file)) {
                continue;
            }

            ParsedBbmodel parsed = parseBbmodel(file);
            // Skip pure static template / empty cosmetic wearables outside NPCs unless inside baked or NPCs
            String normPath = file.getAbsolutePath().replace('\\', '/').toLowerCase(Locale.ROOT);
            boolean inNpcsOrBaked = normPath.contains("/scenes/npcs/") || normPath.contains("/dcnpc_baked/");
            if (!inNpcsOrBaked && parsed.animations.isEmpty()) {
                continue;
            }
            if (modelId.equals("skin") || modelId.equals("skinundead") || modelId.equals("empty")) {
                continue;
            }

            String category = resolveCategory(modelId, normPath);
            String title = prettifyTitle(modelId);
            Material icon = resolveIcon(modelId, category);

            SceneModelInfo info = new SceneModelInfo(modelId, title, category, icon, file, parsed.animations, parsed.hasReplaceableSkin);
            if (!modelsById.containsKey(modelId)) {
                modelsById.put(modelId, info);
                byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(info);
            }
        }

        // Also enrich animations from ModelEngine's loaded registry if already running
        enrichFromModelEngineRegistry();

        for (List<SceneModelInfo> list : byCategory.values()) {
            list.sort(Comparator.comparing(m -> m.modelId));
        }

        DCCosmetics.getInstance().getLogger().info("[SceneCatalog] Indexed " + modelsById.size() + " active Scene NPC blueprints across " + byCategory.size() + " categories.");
    }

    private void collectBbmodels(File dir, Set<File> out) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) {
            if (f.isDirectory()) {
                String nameLower = f.getName().toLowerCase(Locale.ROOT);
                if (nameLower.contains("pigeon") || nameLower.contains("crow")) continue;
                collectBbmodels(f, out);
            } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".bbmodel")) {
                out.add(f);
            }
        }
    }

    private boolean isExcludedModel(String modelId, File file) {
        String lower = (modelId + " " + file.getAbsolutePath()).toLowerCase(Locale.ROOT);
        return lower.contains("pigeon") || lower.contains("crow");
    }

    private static class ParsedBbmodel {
        final List<String> animations = new ArrayList<>();
        boolean hasReplaceableSkin = false;
    }

    private ParsedBbmodel parseBbmodel(File file) {
        ParsedBbmodel result = new ParsedBbmodel();
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonElement rootEl = JsonParser.parseReader(reader);
            if (!rootEl.isJsonObject()) return result;
            JsonObject root = rootEl.getAsJsonObject();

            if (root.has("animations") && root.get("animations").isJsonArray()) {
                JsonArray anims = root.getAsJsonArray("animations");
                for (JsonElement el : anims) {
                    if (el.isJsonObject()) {
                        JsonObject obj = el.getAsJsonObject();
                        if (obj.has("name")) {
                            String aName = obj.get("name").getAsString().trim();
                            if (!aName.isEmpty() && !result.animations.contains(aName)) {
                                result.animations.add(aName);
                            }
                        }
                    }
                }
            }

            if (root.has("textures") && root.get("textures").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("textures")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject tex = el.getAsJsonObject();
                    String tName = tex.has("name") ? tex.get("name").getAsString().toLowerCase(Locale.ROOT) : "";
                    String tPath = tex.has("path") ? tex.get("path").getAsString().toLowerCase(Locale.ROOT) : "";
                    if (tName.contains("npc_skin") || tName.contains("replaceable") || tPath.contains("npc_skin") || tPath.contains("replaceable")) {
                        result.hasReplaceableSkin = true;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }

    @SuppressWarnings("unchecked")
    private void enrichFromModelEngineRegistry() {
        if (Bukkit.getPluginManager().getPlugin("ModelEngine") == null) return;
        try {
            Class<?> apiCls = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Object reg = apiCls.getMethod("getModelRegistry").invoke(null);
            if (reg == null) return;
            for (SceneModelInfo info : new ArrayList<>(modelsById.values())) {
                Object bp = reg.getClass().getMethod("get", String.class).invoke(reg, info.modelId);
                if (bp == null) continue;
                Object animMap = bp.getClass().getMethod("getAnimations").invoke(bp);
                if (animMap instanceof Map<?, ?> map && !map.isEmpty()) {
                    List<String> merged = new ArrayList<>(info.allAnimations);
                    for (Object k : map.keySet()) {
                        String key = String.valueOf(k);
                        if (!merged.contains(key)) merged.add(key);
                    }
                    if (merged.size() != info.allAnimations.size()) {
                        SceneModelInfo updated = new SceneModelInfo(info.modelId, info.displayName, info.category,
                                info.icon, info.bbmodelFile, merged, info.hasReplaceableSkin);
                        modelsById.put(info.modelId, updated);
                        List<SceneModelInfo> catList = byCategory.get(info.category);
                        if (catList != null) {
                            catList.remove(info);
                            catList.add(updated);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private String resolveCategory(String modelId, String normPath) {
        if (normPath.contains("/dcnpc_baked/") || modelId.startsWith("dcnpc_")) return "BAKED_CUSTOM";
        if (normPath.contains("/blacksmiths/") || modelId.contains("blacksmith")) return "BLACKSMITHS";
        if (normPath.contains("/market/") || modelId.contains("market")) return "MARKET";
        if (normPath.contains("/townsfolks/") || modelId.contains("townsfolk")) return "TOWNSFOLKS";
        if (normPath.contains("/guards/") || normPath.contains("/battle/") || modelId.contains("guard") || modelId.contains("combat")) return "GUARDS_BATTLE";
        if (normPath.contains("/farmers/") || normPath.contains("/fishermen/") || normPath.contains("/lumberjacks/")
                || modelId.contains("farmer") || modelId.contains("fisher") || modelId.contains("lumberjack")) {
            return "WORKERS";
        }
        return "BAKED_CUSTOM";
    }

    private Material resolveIcon(String modelId, String category) {
        if (modelId.contains("blacksmith_1")) return Material.ANVIL;
        if (modelId.contains("blacksmith_2")) return Material.FIRE_CHARGE;
        if (modelId.contains("blacksmith_3")) return Material.GRINDSTONE;
        if (modelId.contains("blacksmith_4")) return Material.WATER_BUCKET;
        if (modelId.contains("blacksmith")) return Material.IRON_CHESTPLATE;
        if (modelId.contains("market_10")) return Material.CHEST;
        if (modelId.contains("market_11")) return Material.APPLE;
        if (modelId.contains("market_12")) return Material.MINECART;
        if (modelId.contains("market_2")) return Material.OAK_STAIRS;
        if (modelId.contains("market_3")) return Material.COD;
        if (modelId.contains("market_4")) return Material.BOOKSHELF;
        if (modelId.contains("market_5")) return Material.IRON_SWORD;
        if (modelId.contains("market_6")) return Material.APPLE;
        if (modelId.contains("market_7")) return Material.COOKIE;
        if (modelId.contains("market_8")) return Material.BUNDLE;
        if (modelId.contains("market")) return Material.EMERALD;
        if (modelId.contains("beggar")) return Material.BOWL;
        if (modelId.contains("mug")) return Material.POTION;
        if (modelId.contains("reader")) return Material.BOOK;
        if (modelId.contains("swing")) return Material.LEAD;
        if (modelId.contains("writer")) return Material.FEATHER;
        if (modelId.contains("combat")) return Material.NETHERITE_SWORD;
        if (modelId.contains("guard")) return Material.SHIELD;
        if (modelId.contains("farmer")) return Material.GOLDEN_HOE;
        if (modelId.contains("fisher")) return Material.FISHING_ROD;
        if (modelId.contains("lumberjack_saw")) return Material.STONECUTTER;
        if (modelId.contains("lumberjack")) return Material.IRON_AXE;
        if ("BAKED_CUSTOM".equals(category)) return Material.PLAYER_HEAD;
        return Material.ARMOR_STAND;
    }

    private String prettifyTitle(String modelId) {
        String s = modelId.replace('_', ' ').trim();
        String[] parts = s.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    public SceneModelInfo getModel(String modelId) {
        if (modelId == null) return null;
        return modelsById.get(modelId.toLowerCase(Locale.ROOT));
    }

    public List<SceneModelInfo> getModelsInCategory(String category) {
        return byCategory.getOrDefault(category, Collections.emptyList());
    }

    public Collection<SceneModelInfo> getAllModels() {
        return Collections.unmodifiableCollection(modelsById.values());
    }
}
