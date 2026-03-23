package dc.dccosmetics.model;

import org.joml.Vector3f;

public class CosmeticNode {
    private final String id;
    private final Vector3f scale;
    private final Vector3f translation;
    private final Vector3f rotation;
    private final org.joml.Quaternionf orientation;
    private final String color; // NEW: Holds the static color (can be null)
    private final double opacity;
    
    private final boolean animated;
    private final String animationType;
    private final float animationSpeed;
    private final boolean twoSided;

    public CosmeticNode(String id, Vector3f scale, Vector3f translation, Vector3f rotation, org.joml.Quaternionf orientation, String color, double opacity, boolean animated, String animationType, float animationSpeed, boolean twoSided) {
        this.id = id;
        this.scale = scale;
        this.translation = translation;
        this.rotation = rotation;
        this.orientation = orientation;
        this.color = color;
        this.opacity = opacity;
        this.animated = animated;
        this.animationType = animationType;
        this.animationSpeed = animationSpeed;
        this.twoSided = twoSided;
    }

    public CosmeticNode(String id, Vector3f scale, Vector3f translation, Vector3f rotation, String color, double opacity, boolean animated, String animationType, float animationSpeed, boolean twoSided) {
        this(id, scale, translation, rotation, null, color, opacity, animated, animationType, animationSpeed, twoSided);
    }

    public CosmeticNode(String id, Vector3f scale, Vector3f translation, Vector3f rotation, String color, double opacity, boolean animated, String animationType, float animationSpeed) {
        this(id, scale, translation, rotation, null, color, opacity, animated, animationType, animationSpeed, true);
    }

    public CosmeticNode(String id, Vector3f scale, Vector3f translation, Vector3f rotation, String color) {
        this(id, scale, translation, rotation, null, color, 1.0, false, "spin", 4.0f, true);
    }

    public String getId() { return id; }
    public Vector3f getScale() { return scale; }
    public Vector3f getTranslation() { return translation; }
    public Vector3f getRotation() { return rotation; }
    public org.joml.Quaternionf getOrientation() { return orientation; }
    public String getColor() { return color; }
    public boolean isAnimated() { return animated; }
    public String getAnimationType() { return animationType; }
    public float getAnimationSpeed() { return animationSpeed; }
    public boolean isTwoSided() { return twoSided; }
    public double getOpacity() { return opacity; }
}