package dc.dcessentials.modules.npc.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dc.dccosmetics.DCCosmetics;
import org.bukkit.Bukkit;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Locale;

/**
 * Bakes any 64x64 Minecraft skin PNG directly into a copy of a Scenes .bbmodel blueprint
 * (plus an ITU-R BT.601 grayscale _bw variant for spawn_bw), writes both to
 * plugins/ModelEngine/blueprints/dcnpc_baked/, and imports them into ModelEngine.
 */
public class BbmodelSkinBaker {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static class BakeResult {
        public final boolean success;
        public final String coloredModelId;
        public final String bwModelId;
        public final String error;

        public BakeResult(boolean success, String coloredModelId, String bwModelId, String error) {
            this.success = success;
            this.coloredModelId = coloredModelId;
            this.bwModelId = bwModelId;
            this.error = error;
        }
    }

    public BakeResult bakeSkinIntoBbmodel(File sourceBbmodel, String targetId, File skinPng, File bwSkinPng, String baseModelId) {
        if (sourceBbmodel == null || !sourceBbmodel.exists()) {
            return new BakeResult(false, null, null, "Source .bbmodel not found");
        }
        if (skinPng == null || !skinPng.exists()) {
            return new BakeResult(false, null, null, "Skin PNG not found: " + (skinPng != null ? skinPng.getName() : "null"));
        }

        try {
            byte[] skinBytes = Files.readAllBytes(skinPng.toPath());
            byte[] bwBytes = (bwSkinPng != null && bwSkinPng.exists())
                    ? Files.readAllBytes(bwSkinPng.toPath())
                    : toGrayscalePng(skinBytes);

            String cleanTarget = targetId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
            String cleanBase = baseModelId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
            // Strip prior dcnpc_ prefix if re-baking from an already baked model
            if (cleanBase.startsWith("dcnpc_")) {
                cleanBase = "custom";
            }

            String coloredId = "dcnpc_" + cleanTarget + "_" + cleanBase;
            String bwId = coloredId + "_bw";

            File megDir = new File(DCCosmetics.getInstance().getDataFolder().getParentFile(), "ModelEngine");
            File outDir = new File(megDir, "blueprints" + File.separator + "dcnpc_baked");
            if (!outDir.exists()) outDir.mkdirs();

            String originalJson;
            try (FileReader reader = new FileReader(sourceBbmodel, StandardCharsets.UTF_8)) {
                originalJson = JsonParser.parseReader(reader).toString();
            }

            JsonObject coloredObj = JsonParser.parseString(originalJson).getAsJsonObject();
            applySkinToJson(coloredObj, coloredId, skinBytes, false);

            JsonObject bwObj = JsonParser.parseString(originalJson).getAsJsonObject();
            applySkinToJson(bwObj, bwId, bwBytes, true);

            File coloredFile = new File(outDir, coloredId + ".bbmodel");
            File bwFile = new File(outDir, bwId + ".bbmodel");
            try (FileWriter fw = new FileWriter(coloredFile, StandardCharsets.UTF_8)) {
                GSON.toJson(coloredObj, fw);
            }
            try (FileWriter fw = new FileWriter(bwFile, StandardCharsets.UTF_8)) {
                GSON.toJson(bwObj, fw);
            }

            return new BakeResult(true, coloredId, bwId, null);
        } catch (Throwable t) {
            return new BakeResult(false, null, null, t.getMessage());
        }
    }

    private void applySkinToJson(JsonObject root, String newModelName, byte[] replacementSkinBytes, boolean grayscaleOtherTextures) throws Exception {
        root.addProperty("name", newModelName);
        root.addProperty("model_identifier", newModelName);

        String skinB64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(replacementSkinBytes);
        boolean replacedAny = false;

        if (root.has("textures") && root.get("textures").isJsonArray()) {
            JsonArray textures = root.getAsJsonArray("textures");
            for (JsonElement el : textures) {
                if (!el.isJsonObject()) continue;
                JsonObject tex = el.getAsJsonObject();
                String tName = tex.has("name") ? tex.get("name").getAsString().toLowerCase(Locale.ROOT) : "";
                String tPath = tex.has("path") ? tex.get("path").getAsString().toLowerCase(Locale.ROOT) : "";
                boolean isSkinSlot = tName.contains("npc_skin") || tName.contains("replaceable")
                        || tPath.contains("npc_skin") || tPath.contains("replaceable");

                if (isSkinSlot) {
                    tex.addProperty("source", skinB64);
                    tex.addProperty("width", 64);
                    tex.addProperty("height", 64);
                    tex.addProperty("uv_width", 64);
                    tex.addProperty("uv_height", 64);
                    replacedAny = true;
                } else if (grayscaleOtherTextures && tex.has("source")) {
                    String src = tex.get("source").getAsString();
                    int comma = src.indexOf(',');
                    if (comma > 0) {
                        byte[] raw = Base64.getDecoder().decode(src.substring(comma + 1));
                        byte[] gray = toGrayscalePng(raw);
                        tex.addProperty("source", "data:image/png;base64," + Base64.getEncoder().encodeToString(gray));
                    }
                }
            }

            // If no explicit replaceable slot existed, grayscale everything in BW variant or replace first 64x64 if single texture
            if (!replacedAny && textures.size() > 0 && !grayscaleOtherTextures) {
                for (JsonElement el : textures) {
                    if (!el.isJsonObject()) continue;
                    JsonObject tex = el.getAsJsonObject();
                    int w = tex.has("width") ? tex.get("width").getAsInt() : 0;
                    int h = tex.has("height") ? tex.get("height").getAsInt() : 0;
                    if (w == 64 && h == 64) {
                        tex.addProperty("source", skinB64);
                        break;
                    }
                }
            }
        }
    }

    public static byte[] toGrayscalePng(byte[] pngBytes) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(pngBytes));
        if (img == null) return pngBytes;

        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage bw = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a == 0) {
                    bw.setRGB(x, y, 0);
                    continue;
                }
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int lum = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
                lum = Math.max(0, Math.min(255, lum));
                bw.setRGB(x, y, (a << 24) | (lum << 16) | (lum << 8) | lum);
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bw, "PNG", baos);
        return baos.toByteArray();
    }

    /**
     * Asks ModelEngine to re-import blueprints on disk so newly baked models become usable in-game.
     */
    public void reloadModelEngineAsync() {
        Bukkit.getScheduler().runTask(DCCosmetics.getInstance(), () -> {
            try {
                if (Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "meg reload");
                }
            } catch (Throwable ignored) {}
        });
    }
}
