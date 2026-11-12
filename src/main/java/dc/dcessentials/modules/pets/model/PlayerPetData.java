package dc.dcessentials.modules.pets.model;

import dc.dcessentials.modules.items.model.ItemRarity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerPetData {

    public static class OwnedPetEntry {
        private final String petId;
        private int level;
        private double xp;
        private ItemRarity rarityOverride;

        public OwnedPetEntry(String petId, int level, double xp, ItemRarity rarityOverride) {
            this.petId = petId.toLowerCase();
            this.level = Math.max(1, level);
            this.xp = Math.max(0.0, xp);
            this.rarityOverride = rarityOverride;
        }

        public String getPetId() { return petId; }
        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = Math.max(1, level); }
        public double getXp() { return xp; }
        public void setXp(double xp) { this.xp = Math.max(0.0, xp); }
        public ItemRarity getRarityOverride() { return rarityOverride; }
        public void setRarityOverride(ItemRarity rarityOverride) { this.rarityOverride = rarityOverride; }
    }

    private final UUID playerUuid;
    private String activePetId;
    private PetVisibilityMode visibilityMode = PetVisibilityMode.ALL;
    private boolean autoCastSkills = true;
    private boolean trackerEnabled = true;
    private final Map<String, OwnedPetEntry> ownedPets = new ConcurrentHashMap<>();
    private final Set<UUID> allies = ConcurrentHashMap.newKeySet();

    private static final Map<String, String> LEGACY_ID_ALIASES = Map.ofEntries(
            Map.entry("cubee_air_dragon", "zephyr"),
            Map.entry("cubee_darkness_dragon", "umbra"),
            Map.entry("cubee_earth_dragon", "terra"),
            Map.entry("cubee_fire_dragon", "pyre"),
            Map.entry("cubee_galaxy_dragon", "astral"),
            Map.entry("cubee_ice_dragon", "glacies"),
            Map.entry("cubee_light_dragon", "solis"),
            Map.entry("cubee_nature_dragon", "verdant"),
            Map.entry("drone_combat", "striker"),
            Map.entry("drone_flame", "ember"),
            Map.entry("drone_repair", "mend"),
            Map.entry("drone_rocket", "salvo"),
            Map.entry("drone_shield", "bastion"),
            Map.entry("drako_dragon", "crimson")
    );

    public static String canonicalizePetId(String rawId) {
        if (rawId == null) return null;
        String clean = rawId.trim().toLowerCase(Locale.ROOT);
        if (clean.isEmpty() || "none".equals(clean)) return null;
        return LEGACY_ID_ALIASES.getOrDefault(clean, clean);
    }

    public PlayerPetData(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getActivePetId() { return activePetId; }
    public void setActivePetId(String activePetId) {
        this.activePetId = canonicalizePetId(activePetId);
    }

    public PetVisibilityMode getVisibilityMode() { return visibilityMode; }
    public void setVisibilityMode(PetVisibilityMode visibilityMode) {
        this.visibilityMode = visibilityMode != null ? visibilityMode : PetVisibilityMode.ALL;
    }

    public boolean isAutoCastSkills() { return autoCastSkills; }
    public void setAutoCastSkills(boolean autoCastSkills) { this.autoCastSkills = autoCastSkills; }

    public boolean isTrackerEnabled() { return trackerEnabled; }
    public void setTrackerEnabled(boolean trackerEnabled) { this.trackerEnabled = trackerEnabled; }

    public Map<String, OwnedPetEntry> getOwnedPets() { return ownedPets; }
    public boolean hasPet(String petId) {
        String key = canonicalizePetId(petId);
        return key != null && ownedPets.containsKey(key);
    }

    public OwnedPetEntry getPetEntry(String petId) {
        String key = canonicalizePetId(petId);
        return key != null ? ownedPets.get(key) : null;
    }

    public OwnedPetEntry unlockOrUpgradePet(String petId, int level, double xp, ItemRarity rarity) {
        String key = canonicalizePetId(petId);
        if (key == null) return null;
        OwnedPetEntry existing = ownedPets.get(key);
        if (existing == null) {
            OwnedPetEntry created = new OwnedPetEntry(key, level, xp, rarity);
            ownedPets.put(key, created);
            return created;
        } else {
            if (level > existing.getLevel()) {
                existing.setLevel(level);
                existing.setXp(xp);
            } else if (level == existing.getLevel() && xp > existing.getXp()) {
                existing.setXp(xp);
            }
            if (rarity != null && (existing.getRarityOverride() == null || rarity.ordinal() > existing.getRarityOverride().ordinal())) {
                existing.setRarityOverride(rarity);
            }
            return existing;
        }
    }

    public Set<UUID> getAllies() { return allies; }
    public boolean isAlly(UUID uuid) { return uuid != null && (uuid.equals(playerUuid) || allies.contains(uuid)); }
    public boolean toggleAlly(UUID uuid) {
        if (uuid == null || uuid.equals(playerUuid)) return false;
        if (allies.contains(uuid)) {
            allies.remove(uuid);
            return false;
        } else {
            allies.add(uuid);
            return true;
        }
    }

    public String serializeOwnedPets() {
        StringBuilder sb = new StringBuilder();
        for (OwnedPetEntry e : ownedPets.values()) {
            if (sb.length() > 0) sb.append(";");
            sb.append(e.getPetId()).append(":")
              .append(e.getLevel()).append(":")
              .append(Math.round(e.getXp() * 10.0) / 10.0).append(":")
              .append(e.getRarityOverride() != null ? e.getRarityOverride().name() : "DEFAULT");
        }
        return sb.toString();
    }

    public void deserializeOwnedPets(String raw) {
        ownedPets.clear();
        if (raw == null || raw.trim().isEmpty()) return;
        for (String token : raw.split(";")) {
            String[] parts = token.split(":");
            if (parts.length >= 3) {
                String id = canonicalizePetId(parts[0]);
                if (id == null) continue;
                try {
                    int lvl = Integer.parseInt(parts[1].trim());
                    double xp = Double.parseDouble(parts[2].trim());
                    ItemRarity rar = (parts.length >= 4 && !"DEFAULT".equalsIgnoreCase(parts[3].trim()))
                            ? ItemRarity.fromString(parts[3].trim())
                            : null;
                    unlockOrUpgradePet(id, lvl, xp, rar);
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    public String serializeAllies() {
        StringBuilder sb = new StringBuilder();
        for (UUID u : allies) {
            if (sb.length() > 0) sb.append(",");
            sb.append(u.toString());
        }
        return sb.toString();
    }

    public void deserializeAllies(String raw) {
        allies.clear();
        if (raw == null || raw.trim().isEmpty()) return;
        for (String s : raw.split(",")) {
            try {
                allies.add(UUID.fromString(s.trim()));
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
