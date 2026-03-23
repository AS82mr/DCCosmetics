package dc.dccosmetics.model;

import org.joml.Vector3f;
import java.util.List;
import java.util.Map;

public class CosmeticTemplate {
    private final String id;
    private final String itemName;
    private final String equipmentSlot;
    private final String rarity;
    private final String permission;
    private final String guiIconBase64;
    private final Vector3f globalOffset;
    private final Vector3f globalScale;
    private final Vector3f globalRotation;
    private final List<String> allowedColors;
    private final List<String> lore;

    private final Map<String, Integer> attackDurations;
    private final Map<String, Integer> attackFades;
    private final boolean blockbench;
    private final boolean animated;
    private final String animationType;
    private final float animationSpeed;
    private final Map<String, CosmeticNode> nodes;

    // NEW: Footstep Engine Variables!
    private final String footstepParticle;
    private final String footstepColor;
    private final String footstepSound;
    private final float soundVolume;
    private final float soundPitch;

    public CosmeticTemplate(String id, String itemName, String equipmentSlot, String rarity, String permission, String guiIconBase64, Vector3f globalOffset, Vector3f globalScale, Vector3f globalRotation, List<String> allowedColors, List<String> lore, Map<String, Integer> attackDurations, Map<String, Integer> attackFades, boolean blockbench, boolean animated, String animationType, float animationSpeed, String footstepParticle, String footstepColor, String footstepSound, float soundVolume, float soundPitch, Map<String, CosmeticNode> nodes) {
        this.id = id;
        this.itemName = itemName;
        this.equipmentSlot = equipmentSlot;
        this.rarity = rarity;
        this.permission = permission;
        this.guiIconBase64 = guiIconBase64;
        this.globalOffset = globalOffset;
        this.globalScale = globalScale;
        this.globalRotation = globalRotation;
        this.allowedColors = allowedColors;
        this.lore = lore;
        this.attackDurations = attackDurations;
        this.attackFades = attackFades;
        this.blockbench = blockbench;
        this.animated = animated;
        this.animationType = animationType;
        this.animationSpeed = animationSpeed;
        this.nodes = nodes;

        this.footstepParticle = footstepParticle;
        this.footstepColor = footstepColor;
        this.footstepSound = footstepSound;
        this.soundVolume = soundVolume;
        this.soundPitch = soundPitch;
    }

    public String getId() { return id; }
    public String getItemName() { return itemName; }
    public String getEquipmentSlot() { return equipmentSlot; }
    public String getRarity() { return rarity; }
    public String getPermission() { return permission; }
    public String getGuiIconBase64() { return guiIconBase64; }
    public Vector3f getGlobalOffset() { return globalOffset; }
    public Vector3f getGlobalScale() { return globalScale; }
    public Vector3f getGlobalRotation() { return globalRotation; }
    public List<String> getAllowedColors() { return allowedColors; }
    public List<String> getLore() { return lore; }
    public Map<String, Integer> getAttackDurations() { return attackDurations; }
    public Map<String, Integer> getAttackFades() { return attackFades; }
    public boolean isBlockbench() { return blockbench; }
    public boolean isAnimated() { return animated; }
    public String getAnimationType() { return animationType; }
    public float getAnimationSpeed() { return animationSpeed; }
    public Map<String, CosmeticNode> getNodes() { return nodes; }

    public String getFootstepParticle() { return footstepParticle; }
    public String getFootstepColor() { return footstepColor; }
    public String getFootstepSound() { return footstepSound; }
    public float getSoundVolume() { return soundVolume; }
    public float getSoundPitch() { return soundPitch; }
}