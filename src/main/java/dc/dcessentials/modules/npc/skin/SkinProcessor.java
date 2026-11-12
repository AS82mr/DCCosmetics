package dc.dcessentials.modules.npc.skin;

import dc.dccosmetics.DCCosmetics;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

/**
 * High-performance automated skin downloading and ITU-R BT.601 grayscale luminance filtering engine.
 */
public class SkinProcessor {

    private final java.util.Map<String, org.bukkit.configuration.file.YamlConfiguration> skinCache = new java.util.concurrent.ConcurrentHashMap<>();

    private org.bukkit.configuration.file.YamlConfiguration getCachedSkinConfig(String cleanId) {
        return skinCache.computeIfAbsent(cleanId.toLowerCase(), id -> {
            File skinDataFile = new File(getNpcDirectory(id), "skin_data.yml");
            if (skinDataFile.exists()) {
                return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(skinDataFile);
            }
            return new org.bukkit.configuration.file.YamlConfiguration();
        });
    }

    private final File npcsDir;

    public SkinProcessor() {
        this.npcsDir = new File(DCCosmetics.getInstance().getDataFolder(), "npcs");
        if (!npcsDir.exists()) {
            npcsDir.mkdirs();
        }
    }

    public File getNpcDirectory(String npcId) {
        File dir = new File(npcsDir, npcId.toLowerCase());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Creates dedicated directories for all characters upfront with usage documentation.
     */
    public void ensureAllNpcDirectories(java.util.Collection<dc.dcessentials.modules.npc.model.NpcCharacter> characters) {
        for (dc.dcessentials.modules.npc.model.NpcCharacter c : characters) {
            File dir = getNpcDirectory(c.getId());
            File info = new File(dir, "README.txt");
            if (!info.exists()) {
                try {
                    String content = "=====================================================\n" +
                            "DRAGONCOTE MULTIVERSE NPC TEXTURES: " + c.getShortName().toUpperCase() + " (" + c.getId() + ")\n" +
                            "=====================================================\n\n" +
                            "Place your custom skin files in this directory:\n" +
                            "  1. default.png    - Base skin used in Prime Dimension (spawn_main / lowercity)\n" +
                            "  2. spawn_bw.png   - (Optional) Grayscale ITU-R BT.601 texture for Void Dimension\n" +
                            "  3. spawn_snow.png - (Optional) Cryo Frost texture for Sub-Zero Dimension\n\n" +
                            "In-Game Commands:\n" +
                            "  /dcnpc skin " + c.getId() + " <https://url-to-skin.png> - Download from URL & apply\n" +
                            "  /dcnpc skin " + c.getId() + " <player_name>           - Fetch Mojang skin & apply\n" +
                            "  /dcnpc skin " + c.getId() + " file                   - Process local default.png & apply\n";
                    Files.write(info.toPath(), content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Downloads or copies a base skin into the NPC directory and generates the spawn_bw grayscale variant.
     */
    public CompletableFuture<Boolean> processSkin(String npcId, String sourceUrlOrPlayer) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File charDir = getNpcDirectory(npcId);
                File defaultSkinFile = new File(charDir, "default.png");
                File bwSkinFile = new File(charDir, "spawn_bw.png");
                File snowSkinFile = new File(charDir, "spawn_snow.png");

                // Download from URL or Mojang if provided
                if (sourceUrlOrPlayer != null && !sourceUrlOrPlayer.isEmpty() &&
                        !sourceUrlOrPlayer.equalsIgnoreCase("file") && !sourceUrlOrPlayer.equalsIgnoreCase("local")) {
                    if (sourceUrlOrPlayer.startsWith("http://") || sourceUrlOrPlayer.startsWith("https://")) {
                        downloadFile(sourceUrlOrPlayer, defaultSkinFile);
                    } else {
                        // Attempt Mojang skin download via Minotar/Crafatar
                        String mojangUrl = "https://minotar.net/skin/" + sourceUrlOrPlayer;
                        downloadFile(mojangUrl, defaultSkinFile);
                    }
                }

                if (!defaultSkinFile.exists()) {
                    DCCosmetics.getInstance().getLogger().warning("[SkinProcessor] No default.png found for NPC: " + npcId);
                    return false;
                }

                // Generate grayscale spawn_bw.png
                generateGrayscaleVariant(defaultSkinFile, bwSkinFile);

                // Generate snow/frost variant if not manually provided
                if (!snowSkinFile.exists()) {
                    generateFrostVariant(defaultSkinFile, snowSkinFile);
                }

                // Upload to Mineskin and generate persistent signed skin_data.yml
                uploadAndSaveSignedSkinData(charDir, defaultSkinFile, bwSkinFile);

                DCCosmetics.getInstance().getLogger().info("[SkinProcessor] Successfully generated variants and signed skin for NPC: " + npcId);
                return true;
            } catch (Exception e) {
                DCCosmetics.getInstance().getLogger().severe("[SkinProcessor] Failed processing skin for " + npcId + ": " + e.getMessage());
                return false;
            }
        });
    }

    private void uploadAndSaveSignedSkinData(File charDir, File defaultSkinFile, File bwSkinFile) {
        try {
            File skinDataFile = new File(charDir, "skin_data.yml");
            org.bukkit.configuration.file.YamlConfiguration cfg = skinDataFile.exists() ?
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(skinDataFile) :
                    new org.bukkit.configuration.file.YamlConfiguration();

            String[] primeRes = uploadFileToMineskin(defaultSkinFile);
            if (primeRes != null) {
                cfg.set("prime.value", primeRes[0]);
                cfg.set("prime.signature", primeRes[1]);
            }

            if (bwSkinFile != null && bwSkinFile.exists()) {
                String[] bwRes = uploadFileToMineskin(bwSkinFile);
                if (bwRes != null) {
                    cfg.set("bw.value", bwRes[0]);
                    cfg.set("bw.signature", bwRes[1]);
                }
            }

            cfg.save(skinDataFile);
            skinCache.put(charDir.getName().toLowerCase(), cfg);
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[SkinProcessor] Could not sign skin via Mineskin: " + e.getMessage());
        }
    }

    private String[] uploadFileToMineskin(File file) {
        try {
            String boundary = "===DragoncoteSkinBoundary" + System.currentTimeMillis() + "===";
            URL url = URI.create("https://api.mineskin.org/generate/upload").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("User-Agent", "Dragoncote-SkinEngine/2.0");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(("--" + boundary + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n").getBytes());
                os.write(("Content-Type: image/png\r\n\r\n").getBytes());
                Files.copy(file.toPath(), os);
                os.write(("\r\n--" + boundary + "--\r\n").getBytes());
                os.flush();
            }

            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream();
                     java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    String resp = sb.toString();

                    int vIdx = resp.indexOf("\"value\":");
                    int sIdx = resp.indexOf("\"signature\":");
                    if (vIdx != -1 && sIdx != -1) {
                        String value = extractJsonString(resp, vIdx + 8);
                        String signature = extractJsonString(resp, sIdx + 12);
                        if (value != null && signature != null) {
                            return new String[]{value, signature};
                        }
                    }
                }
            } else {
                DCCosmetics.getInstance().getLogger().warning("[SkinProcessor] Mineskin returned HTTP " + conn.getResponseCode());
            }
        } catch (Exception e) {
            DCCosmetics.getInstance().getLogger().warning("[SkinProcessor] Mineskin upload failed: " + e.getMessage());
        }
        return null;
    }

    private String extractJsonString(String json, int startIdx) {
        int firstQuote = json.indexOf('"', startIdx);
        if (firstQuote == -1) return null;
        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (secondQuote == -1) return null;
        return json.substring(firstQuote + 1, secondQuote);
    }

    private void downloadFile(String urlString, File destination) throws Exception {
        URL url = URI.create(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Dragoncote-SkinEngine/2.0");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);

        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Converts a base skin PNG into a monochromatic grayscale skin using ITU-R BT.601 luminance.
     */
    public void generateGrayscaleVariant(File input, File output) throws Exception {
        BufferedImage img = ImageIO.read(input);
        if (img == null) return;

        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage bwImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a == 0) {
                    bwImg.setRGB(x, y, 0);
                    continue;
                }

                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                // ITU-R BT.601 Luminance Formula
                int gray = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
                gray = Math.max(0, Math.min(255, gray));

                int newArgb = (a << 24) | (gray << 16) | (gray << 8) | gray;
                bwImg.setRGB(x, y, newArgb);
            }
        }

        ImageIO.write(bwImg, "PNG", output);
    }

    /**
     * Generates a sub-zero cryo/frost variant with cyan highlight saturation.
     */
    public void generateFrostVariant(File input, File output) throws Exception {
        BufferedImage img = ImageIO.read(input);
        if (img == null) return;

        int width = img.getWidth();
        int height = img.getHeight();
        BufferedImage frostImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a == 0) {
                    frostImg.setRGB(x, y, 0);
                    continue;
                }

                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                // Blend with icy frost tint (Pale cyan / white)
                int frostR = (int) Math.min(255, r * 0.75 + 40);
                int frostG = (int) Math.min(255, g * 0.90 + 50);
                int frostB = (int) Math.min(255, b * 1.05 + 70);

                int newArgb = (a << 24) | (frostR << 16) | (frostG << 8) | frostB;
                frostImg.setRGB(x, y, newArgb);
            }
        }

        ImageIO.write(frostImg, "PNG", output);
    }

    /**
     * Applies the world-appropriate skin to a Citizens NPC using persistent signed textures.
     */
    public void applyWorldSkin(NPC npc, String worldName, String charId) {
        if (npc == null || charId == null) return;
        String cleanId = charId.contains(":") ? charId.split(":")[0].toLowerCase() : charId.toLowerCase();

        DCCosmetics.debugNpc("applyWorldSkin: NPC #" + npc.getId() + " (" + npc.getName() + ") world=" + worldName + " cleanId=" + cleanId);

        SkinTrait trait = npc.getOrAddTrait(SkinTrait.class);
        trait.setShouldUpdateSkins(true);
        String worldLower = (worldName != null) ? worldName.toLowerCase() : "";
        boolean isBw = worldLower.contains("bw") || worldLower.contains("void") || worldLower.contains("drain");

        // 1. Check in-memory cached skin_data.yml
        org.bukkit.configuration.file.YamlConfiguration cfg = getCachedSkinConfig(cleanId);
        String section = (isBw && cfg.contains("bw.value")) ? "bw" : "prime";
        if (cfg.contains(section + ".value") && cfg.contains(section + ".signature")) {
            String value = cfg.getString(section + ".value");
            String signature = cfg.getString(section + ".signature");
            if (value != null && signature != null && !value.isEmpty() && !signature.isEmpty()) {
                if (value.equals(trait.getTexture()) && signature.equals(trait.getSignature())) {
                    return;
                }
                trait.setSkinPersistent(cleanId, signature, value);
                npc.data().setPersistent("player-skin-textures", value);
                npc.data().setPersistent("player-skin-signature", signature);
                npc.data().setPersistent("cached-skin-uuid-name", cleanId);
                if (npc.isSpawned()) {
                    try { npc.scheduleUpdate(NPC.NPCUpdate.PACKET); } catch (Throwable ignored) {}
                }
                return;
            }
        }

        // 2. Fallback to registered character skin name
        dc.dcessentials.modules.npc.model.NpcCharacter c = DCCosmetics.getInstance().getNpcRegistry().getCharacter(cleanId);
        if (c != null) {
            String skinName = isBw ? c.getSkinBw() : ((worldLower.contains("snow") || worldLower.contains("cryo")) ? c.getSkinSnow() : c.getSkin());
            String target = (skinName != null) ? skinName : c.getSkin();
            if (target != null && !target.equalsIgnoreCase(trait.getSkinName())) {
                trait.setSkinName(target, false);
            }
        } else {
            if (!cleanId.equalsIgnoreCase(trait.getSkinName())) {
                trait.setSkinName(cleanId, false);
            }
        }

        if (npc.isSpawned()) {
            try { npc.scheduleUpdate(NPC.NPCUpdate.PACKET); } catch (Throwable ignored) {}
        }
    }

    public void copySkinToWalker(NPC sourceNpc, NPC walkerNpc, String worldName, String charId) {
        if (walkerNpc == null) return;
        if (sourceNpc != null && sourceNpc.hasTrait(SkinTrait.class)) {
            SkinTrait srcSkin = sourceNpc.getTrait(SkinTrait.class);
            if (srcSkin.getTexture() != null && srcSkin.getSignature() != null) {
                SkinTrait dstSkin = walkerNpc.getOrAddTrait(SkinTrait.class);
                String skinName = srcSkin.getSkinName() != null ? srcSkin.getSkinName() : charId;
                dstSkin.setSkinPersistent(skinName, srcSkin.getSignature(), srcSkin.getTexture());
                walkerNpc.data().setPersistent("player-skin-textures", srcSkin.getTexture());
                walkerNpc.data().setPersistent("player-skin-signature", srcSkin.getSignature());
                walkerNpc.data().setPersistent("cached-skin-uuid-name", skinName);
                return;
            }
        }
        applyWorldSkin(walkerNpc, worldName, charId);
    }
}
