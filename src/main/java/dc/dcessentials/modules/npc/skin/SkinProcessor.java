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

                // Always regenerate monochromatic & frost variants from current default.png
                generateGrayscaleVariant(defaultSkinFile, bwSkinFile);
                generateFrostVariant(defaultSkinFile, snowSkinFile);

                // Upload to Mineskin and generate persistent signed skin_data.yml (prime, bw, snow)
                uploadAndSaveSignedSkinData(charDir, defaultSkinFile, bwSkinFile, snowSkinFile);

                DCCosmetics.getInstance().getLogger().info("[SkinProcessor] Successfully generated variants and signed skin for NPC: " + npcId);
                return true;
            } catch (Exception e) {
                DCCosmetics.getInstance().getLogger().severe("[SkinProcessor] Failed processing skin for " + npcId + ": " + e.getMessage());
                return false;
            }
        });
    }

    private void uploadAndSaveSignedSkinData(File charDir, File defaultSkinFile, File bwSkinFile, File snowSkinFile) {
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
                try { Thread.sleep(1200L); } catch (InterruptedException ignored) {}
                String[] bwRes = uploadFileToMineskin(bwSkinFile);
                if (bwRes != null) {
                    cfg.set("bw.value", bwRes[0]);
                    cfg.set("bw.signature", bwRes[1]);
                }
            }

            if (snowSkinFile != null && snowSkinFile.exists()) {
                try { Thread.sleep(1200L); } catch (InterruptedException ignored) {}
                String[] snowRes = uploadFileToMineskin(snowSkinFile);
                if (snowRes != null) {
                    cfg.set("snow.value", snowRes[0]);
                    cfg.set("snow.signature", snowRes[1]);
                }
            }

            cfg.set("revision", cfg.getInt("revision", 0) + 1);
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

    public void ensureDirectory(String skinId) {
        if (skinId == null || skinId.isEmpty()) return;
        File dir = getNpcDirectory(skinId.toLowerCase());
        if (!dir.exists()) dir.mkdirs();
    }

    public void invalidateCache(String skinId) {
        if (skinId != null) {
            skinCache.remove(skinId.toLowerCase());
        }
    }

    public boolean hasLocalPng(String skinId) {
        if (skinId == null) return false;
        File f = new File(getNpcDirectory(skinId.toLowerCase()), "default.png");
        return f.exists();
    }

    public boolean hasSignedCache(String skinId) {
        return hasSignedCache(skinId, "prime");
    }

    public boolean hasSignedCache(String skinId, String variant) {
        if (skinId == null || variant == null) return false;
        File f = new File(getNpcDirectory(skinId.toLowerCase()), "skin_data.yml");
        if (!f.exists()) return false;
        org.bukkit.configuration.file.YamlConfiguration cfg = getFreshSkinConfig(skinId.toLowerCase());
        return cfg.contains(variant + ".value") && cfg.contains(variant + ".signature");
    }

    private org.bukkit.configuration.file.YamlConfiguration getFreshSkinConfig(String cleanId) {
        File dir = getNpcDirectory(cleanId);
        File file = new File(dir, "skin_data.yml");
        if (!file.exists()) return new org.bukkit.configuration.file.YamlConfiguration();
        org.bukkit.configuration.file.YamlConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        skinCache.put(cleanId, cfg);
        return cfg;
    }

    /**
     * Applies the world-appropriate permanently baked skin to a Citizens Storyline NPC.
     */
    public void applyWorldSkin(NPC npc, String worldName, String charId) {
        if (npc == null || charId == null) return;
        String cleanId = charId.contains(":") ? charId.split(":")[0].toLowerCase() : charId.toLowerCase();
        dc.dcessentials.modules.npc.model.NpcCharacter c = DCCosmetics.getInstance().getNpcRegistry().getCharacter(cleanId);
        String worldLower = (worldName != null) ? worldName.toLowerCase() : "";
        boolean isBw = worldLower.contains("bw") || worldLower.contains("void") || worldLower.contains("drain");
        boolean isSnow = worldLower.contains("snow") || worldLower.contains("cryo");
        String fallback = null;
        int revision = 1;
        if (c != null) {
            fallback = isBw ? c.getSkinBw() : (isSnow ? c.getSkinSnow() : c.getSkin());
            if (fallback == null) fallback = c.getSkin();
            revision = c.getBehaviorProfile().getSkinRevision();
        }
        applyProfileSkin(npc, worldName, cleanId, fallback != null ? fallback : cleanId, revision);
    }

    /**
     * Applies the world-appropriate permanently baked skin to ANY Citizens NPC (Storyline or Decoration)
     * using distinct per-dimension & per-revision cache keys and locking setShouldUpdateSkins(false).
     */
    public void applyProfileSkin(NPC npc, String worldName, String skinId, String fallbackName, int externalRevision) {
        if (npc == null || skinId == null) return;
        String cleanId = skinId.toLowerCase();

        DCCosmetics.debugNpc("applyProfileSkin: NPC #" + npc.getId() + " (" + npc.getName() + ") world=" + worldName + " skinId=" + cleanId);

        SkinTrait trait = npc.getOrAddTrait(SkinTrait.class);
        String worldLower = (worldName != null) ? worldName.toLowerCase() : "";
        boolean isBw = worldLower.contains("bw") || worldLower.contains("void") || worldLower.contains("drain");
        boolean isSnow = worldLower.contains("snow") || worldLower.contains("cryo");

        org.bukkit.configuration.file.YamlConfiguration cfg = getFreshSkinConfig(cleanId);
        int fileRev = cfg.getInt("revision", 1);
        int rev = Math.max(fileRev, Math.max(1, externalRevision));

        String section = "prime";
        if (isBw && cfg.contains("bw.value") && cfg.contains("bw.signature")) {
            section = "bw";
        } else if (isSnow && cfg.contains("snow.value") && cfg.contains("snow.signature")) {
            section = "snow";
        }

        if (cfg.contains(section + ".value") && cfg.contains(section + ".signature")) {
            String value = cfg.getString(section + ".value");
            String signature = cfg.getString(section + ".signature");
            if (value != null && signature != null && !value.isEmpty() && !signature.isEmpty()) {
                // Distinct key per dimension & bake revision guarantees prime and bw NEVER collide in Citizens cache
                String scopedKey = cleanId + "__" + section + "__r" + rev;
                trait.setShouldUpdateSkins(false);

                if (value.equals(trait.getTexture())
                        && signature.equals(trait.getSignature())
                        && scopedKey.equalsIgnoreCase(trait.getSkinName())) {
                    return;
                }

                // Purge stale Mojang player lookup metadata so Citizens never reverts to player skin
                npc.data().remove("player-skin-name");
                npc.data().remove("player-skin-uuid");
                npc.data().remove("cached-skin-uuid-name");
                npc.data().remove("cached-skin-uuid");

                trait.setSkinPersistent(scopedKey, signature, value);
                trait.setShouldUpdateSkins(false);
                npc.data().setPersistent("player-skin-textures", value);
                npc.data().setPersistent("player-skin-signature", signature);
                npc.data().setPersistent("cached-skin-uuid-name", scopedKey);

                if (npc.isSpawned()) {
                    try { npc.scheduleUpdate(NPC.NPCUpdate.PACKET); } catch (Throwable ignored) {}
                }
                return;
            }
        }

        // Fallback to Minecraft player skin name ONLY when no local baked signed data exists
        trait.setShouldUpdateSkins(false);
        String target = (fallbackName != null && !fallbackName.isEmpty()) ? fallbackName : cleanId;
        if (!target.equalsIgnoreCase(trait.getSkinName())) {
            trait.setSkinName(target, false);
        }
        if (npc.isSpawned()) {
            try { npc.scheduleUpdate(NPC.NPCUpdate.PACKET); } catch (Throwable ignored) {}
        }
    }

    public void copySkinToWalker(NPC sourceNpc, NPC walkerNpc, String worldName, String charId) {
        if (walkerNpc == null) return;
        SkinTrait dstSkin = walkerNpc.getOrAddTrait(SkinTrait.class);
        dstSkin.setShouldUpdateSkins(false);

        if (sourceNpc != null && sourceNpc.hasTrait(SkinTrait.class)) {
            SkinTrait srcSkin = sourceNpc.getTrait(SkinTrait.class);
            if (srcSkin.getTexture() != null && srcSkin.getSignature() != null
                    && !srcSkin.getTexture().isEmpty() && !srcSkin.getSignature().isEmpty()) {
                String worldLower = (worldName != null) ? worldName.toLowerCase() : "";
                String suffix = worldLower.contains("bw") ? "bw" : (worldLower.contains("snow") ? "snow" : "prime");
                String skinName = srcSkin.getSkinName() != null ? srcSkin.getSkinName() : (charId + "__" + suffix);
                walkerNpc.data().remove("player-skin-name");
                walkerNpc.data().remove("player-skin-uuid");
                dstSkin.setSkinPersistent(skinName, srcSkin.getSignature(), srcSkin.getTexture());
                dstSkin.setShouldUpdateSkins(false);
                walkerNpc.data().setPersistent("player-skin-textures", srcSkin.getTexture());
                walkerNpc.data().setPersistent("player-skin-signature", srcSkin.getSignature());
                walkerNpc.data().setPersistent("cached-skin-uuid-name", skinName);
                return;
            }
        }
        applyWorldSkin(walkerNpc, worldName, charId);
    }
}
