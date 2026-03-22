package dc.dccosmetics.model;

import org.joml.Vector3f;

public class CosmeticNode {
    private final String id;
    private final Vector3f scale;
    private final Vector3f translation;
    private final Vector3f rotation;
    private final String color; // NEW: Holds the static color (can be null)
    
    private final boolean animated;
    private final String animationType;
    private final float animationSpeed;

    public CosmeticNode(String id, Vector3f scale, Vector3f translation, Vector3f rotation, String color, boolean animated, String animationType, float animationSpeed) {
        this.id = id;
        this.scale = scale;
        this.translation = translation;
        this.rotation = rotation;
        this.color = color;
        this.animated = animated;
        this.animationType = animationType;
        this.animationSpeed = animationSpeed;
    }

    public CosmeticNode(String id, Vector3f scale, Vector3f translation, Vector3f rotation, String color) {
        this(id, scale, translation, rotation, color, false, "spin", 4.0f);
    }

    public String getId() { return id; }
    public Vector3f getScale() { return scale; }
    public Vector3f getTranslation() { return translation; }
    public Vector3f getRotation() { return rotation; }
    public String getColor() { return color; }
    public boolean isAnimated() { return animated; }
    public String getAnimationType() { return animationType; }
    public float getAnimationSpeed() { return animationSpeed; }
}