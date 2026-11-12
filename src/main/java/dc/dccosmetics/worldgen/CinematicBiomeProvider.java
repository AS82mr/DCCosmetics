package dc.dccosmetics.worldgen;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CinematicBiomeProvider extends BiomeProvider {

    private final String targetBiomeKey;

    public CinematicBiomeProvider() {
        this("desolation");
    }

    public CinematicBiomeProvider(String biomeName) {
        if (biomeName == null || biomeName.isEmpty()) {
            this.targetBiomeKey = "desolation";
        } else {
            this.targetBiomeKey = biomeName.toLowerCase();
        }
    }

    private Biome resolveBiome() {
        try {
            // 1. Try vanilla minecraft namespace first if specified or common vanilla biome
            if (targetBiomeKey.startsWith("minecraft:")) {
                String vanillaKey = targetBiomeKey.replace("minecraft:", "");
                Biome b = Registry.BIOME.get(NamespacedKey.minecraft(vanillaKey));
                if (b != null) return b;
            } else if (targetBiomeKey.equals("plains") || targetBiomeKey.equals("snowy_plains") ||
                       targetBiomeKey.equals("the_void") || targetBiomeKey.equals("desert") ||
                       targetBiomeKey.equals("forest") || targetBiomeKey.equals("ocean")) {
                Biome b = Registry.BIOME.get(NamespacedKey.minecraft(targetBiomeKey));
                if (b != null) return b;
            }

            // 2. Try custom cinematic namespace (e.g. cinematic:desolation)
            String cleanCustom = targetBiomeKey.contains(":") ? targetBiomeKey.split(":")[1] : targetBiomeKey;
            NamespacedKey key = new NamespacedKey("cinematic", cleanCustom);
            Biome custom = Registry.BIOME.get(key);
            if (custom != null) {
                return custom;
            }

            // 3. Fallback to direct vanilla lookup
            Biome fallback = Registry.BIOME.get(NamespacedKey.minecraft(cleanCustom));
            if (fallback != null) return fallback;
        } catch (Throwable ignored) {
        }
        return Biome.THE_VOID;
    }

    @Override
    public @NotNull Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
        return resolveBiome();
    }

    @Override
    public @NotNull List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
        List<Biome> biomes = new ArrayList<>(2);
        biomes.add(Biome.THE_VOID);
        Biome resolved = resolveBiome();
        if (resolved != Biome.THE_VOID) {
            biomes.add(resolved);
        }
        return biomes;
    }
}