package dc.dcessentials.modules.curator;

import dc.dccosmetics.DCCosmetics;
import dc.dcessentials.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * TEMPORARY MODEL CURATOR BRIDGE.
 * Safe reflection bridge for ModelEngine 4 & ItemsAdder so compilation needs zero proprietary jars
 * and every spawned entity/furniture/hologram + active animation state is tracked cleanly.
 */
public final class CuratorBridge {

    public static final NamespacedKey PREVIEW_ENTITY_KEY = new NamespacedKey("dcessentials", "curator_preview_entity");

    private static volatile boolean megInit = false;
    private static volatile boolean megOk = false;
    private static Method megGetBlueprint;
    private static Method megBlueprintGetAnimations;
    private static Method megCreateActiveModel;
    private static Method megCreateModeledEntityEntity;
    private static Method megModeledAddModel;
    private static Method megModeledDestroy;
    private static Method megActiveGetAnimHandler;
    private static Method megAnimPlay;
    private static Method megAnimForceStopAll;
    private static Method megPropSetForceLoopMode;
    private static Object megLoopModeLoop;

    private static volatile boolean iaInit = false;
    private static volatile boolean iaOk = false;
    private static Method iaCustomStackGetInstance;
    private static Method iaCustomStackGetItemStack;
    private static Method iaFurnitureSpawnPrecise;
    private static Method iaFurnitureSpawnBlock;
    private static Method iaFurnitureGetArmorstand;
    private static Method iaFurnitureRemoveBool;

    private CuratorBridge() {}

    public static final class SpawnedPreview {
        private final String entryId;
        private final CuratorEntry.SourceType sourceType;
        private final List<UUID> entityUuids = new ArrayList<>();
        private final Object modeledEntity;
        private final Object activeModel;
        private final Object customFurniture;
        private final List<String> animations;
        private final String headerLine;
        private final String subHeaderLine;
        private TextDisplay labelDisplay;
        private int currentAnimIndex = -1; // -1 = static/no animation playing
        private String currentAnimName = null;

        public SpawnedPreview(
                String entryId,
                CuratorEntry.SourceType sourceType,
                Object modeledEntity,
                Object activeModel,
                Object customFurniture,
                List<String> animations,
                String headerLine,
                String subHeaderLine
        ) {
            this.entryId = entryId;
            this.sourceType = sourceType;
            this.modeledEntity = modeledEntity;
            this.activeModel = activeModel;
            this.customFurniture = customFurniture;
            this.animations = animations == null ? Collections.emptyList() : Collections.unmodifiableList(animations);
            this.headerLine = headerLine;
            this.subHeaderLine = subHeaderLine;
        }

        public String getEntryId() { return entryId; }
        public CuratorEntry.SourceType getSourceType() { return sourceType; }
        public Object getActiveModel() { return activeModel; }
        public List<String> getAnimations() { return animations; }
        public int getCurrentAnimIndex() { return currentAnimIndex; }
        public String getCurrentAnimName() { return currentAnimName; }

        public void addEntity(Entity e) {
            if (e != null) entityUuids.add(e.getUniqueId());
        }

        public void setLabelDisplay(TextDisplay labelDisplay) {
            this.labelDisplay = labelDisplay;
            refreshLabel();
        }

        public void setCurrentAnimation(int idx, String animName) {
            this.currentAnimIndex = idx;
            this.currentAnimName = animName;
            refreshLabel();
        }

        public void refreshLabel() {
            if (labelDisplay == null || !labelDisplay.isValid()) return;
            StringBuilder text = new StringBuilder();
            text.append(headerLine).append("\n").append(subHeaderLine);
            if (sourceType == CuratorEntry.SourceType.MEG) {
                if (animations.isEmpty()) {
                    text.append("\n&8[No animations]");
                } else if (currentAnimName != null) {
                    text.append("\n&#34D399▶ Anim ").append(currentAnimIndex + 1).append("/").append(animations.size())
                            .append(": &f").append(currentAnimName);
                } else {
                    text.append("\n&7[Static Pose • ").append(animations.size()).append(" anims | Shift+Q]");
                }
            }
            labelDisplay.text(ColorUtil.toComponent(text.toString()));
        }

        public void cleanup() {
            if (modeledEntity != null && megModeledDestroy != null) {
                try {
                    megModeledDestroy.invoke(modeledEntity);
                } catch (Throwable ignored) {}
            }
            if (customFurniture != null && iaFurnitureRemoveBool != null) {
                try {
                    iaFurnitureRemoveBool.invoke(customFurniture, false);
                } catch (Throwable ignored) {}
            }
            for (UUID uid : entityUuids) {
                Entity ent = Bukkit.getEntity(uid);
                if (ent != null && ent.isValid()) {
                    ent.remove();
                }
            }
            entityUuids.clear();
            labelDisplay = null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initMeg() {
        if (megInit) return;
        megInit = true;
        if (!Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) return;
        try {
            Class<?> api = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Class<?> bp = Class.forName("com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint");
            Class<?> modeled = Class.forName("com.ticxo.modelengine.api.model.ModeledEntity");
            Class<?> active = Class.forName("com.ticxo.modelengine.api.model.ActiveModel");
            Class<?> animHandler = Class.forName("com.ticxo.modelengine.api.animation.handler.AnimationHandler");
            Class<?> animProp = Class.forName("com.ticxo.modelengine.api.animation.property.IAnimationProperty");
            Class<?> loopMode = Class.forName("com.ticxo.modelengine.api.animation.BlueprintAnimation$LoopMode");

            megGetBlueprint = api.getMethod("getBlueprint", String.class);
            megBlueprintGetAnimations = bp.getMethod("getAnimations");
            megCreateActiveModel = api.getMethod("createActiveModel", bp);
            megCreateModeledEntityEntity = api.getMethod("createModeledEntity", Entity.class);
            megModeledAddModel = modeled.getMethod("addModel", active, boolean.class);
            megModeledDestroy = modeled.getMethod("destroy");

            megActiveGetAnimHandler = active.getMethod("getAnimationHandler");
            megAnimPlay = animHandler.getMethod("playAnimation", String.class, double.class, double.class, double.class, boolean.class);
            megAnimForceStopAll = animHandler.getMethod("forceStopAllAnimations");
            megPropSetForceLoopMode = animProp.getMethod("setForceLoopMode", loopMode);
            megLoopModeLoop = Enum.valueOf((Class<Enum>) loopMode, "LOOP");

            megOk = true;
        } catch (Throwable t) {
            megOk = false;
            DCCosmetics.getInstance().getLogger().warning("[Curator] ModelEngine reflection init failed: " + t.getMessage());
        }
    }

    private static void initIa() {
        if (iaInit) return;
        iaInit = true;
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return;
        try {
            Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
            iaCustomStackGetInstance = cs.getMethod("getInstance", String.class);
            iaCustomStackGetItemStack = cs.getMethod("getItemStack");

            Class<?> cf = Class.forName("dev.lone.itemsadder.api.CustomFurniture");
            iaFurnitureSpawnPrecise = cf.getMethod("spawnPreciseNonSolid", String.class, Location.class);
            iaFurnitureSpawnBlock = cf.getMethod("spawn", String.class, Block.class);
            iaFurnitureRemoveBool = cf.getMethod("remove", boolean.class);
            try {
                iaFurnitureGetArmorstand = cf.getMethod("getArmorstand");
            } catch (NoSuchMethodException e) {
                try {
                    iaFurnitureGetArmorstand = cf.getMethod("getEntity");
                } catch (NoSuchMethodException ignored) {}
            }
            iaOk = true;
        } catch (Throwable t) {
            iaOk = false;
            DCCosmetics.getInstance().getLogger().warning("[Curator] ItemsAdder reflection init failed: " + t.getMessage());
        }
    }

    public static ItemStack resolveItemsAdderItemStack(CuratorEntry entry) {
        initIa();
        if (iaOk) {
            try {
                Object cs = iaCustomStackGetInstance.invoke(null, entry.getId());
                if (cs != null) {
                    Object stackObj = iaCustomStackGetItemStack.invoke(cs);
                    if (stackObj instanceof ItemStack is && !is.getType().isAir()) {
                        return is.clone();
                    }
                }
            } catch (Throwable ignored) {}
        }
        Material mat = Material.matchMaterial(entry.getMaterialName());
        if (mat == null || mat.isAir()) mat = Material.PAPER;
        ItemStack fallback = new ItemStack(mat);
        if (entry.getCustomModelData() > 0) {
            ItemMeta meta = fallback.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(entry.getCustomModelData());
                fallback.setItemMeta(meta);
            }
        }
        return fallback;
    }

    public static SpawnedPreview spawnPreview(
            CuratorEntry entry,
            Location loc,
            int index1Based,
            int total,
            boolean autoAnimate
    ) {
        if (entry == null || loc == null || loc.getWorld() == null) return null;

        String header = "&e#" + index1Based + "/" + total + " &f" + entry.getId();
        String subHeader = "&7[" + entry.getSourceType().getLabel() + " • " + entry.getPackName() + "]";

        SpawnedPreview preview = switch (entry.getSourceType()) {
            case MEG -> spawnMegPreview(entry, loc, header, subHeader);
            case IA_FURNITURE -> spawnIaFurniturePreview(entry, loc, header, subHeader);
            case IA_ITEM -> spawnIaItemDisplayPreview(entry, loc, header, subHeader);
        };

        if (preview != null) {
            Location labelLoc = loc.clone().add(0, entry.getSourceType() == CuratorEntry.SourceType.IA_ITEM ? 1.85 : 2.65, 0);
            TextDisplay label = loc.getWorld().spawn(labelLoc, TextDisplay.class, td -> {
                td.setBillboard(Display.Billboard.CENTER);
                td.setBackgroundColor(Color.fromARGB(155, 15, 23, 42));
                td.setShadowed(true);
                td.setPersistent(false);
                td.getPersistentDataContainer().set(PREVIEW_ENTITY_KEY, PersistentDataType.BYTE, (byte) 1);
            });
            preview.addEntity(label);
            preview.setLabelDisplay(label);

            if (autoAnimate && preview.getSourceType() == CuratorEntry.SourceType.MEG && !preview.getAnimations().isEmpty()) {
                int defaultIdx = pickDefaultAnimationIndex(preview.getAnimations());
                playAnimationAtIndex(preview, defaultIdx, true);
            }
        }
        return preview;
    }

    private static int pickDefaultAnimationIndex(List<String> animations) {
        for (int i = 0; i < animations.size(); i++) {
            String n = animations.get(i).toLowerCase();
            if (n.equals("idle") || n.equals("stand") || n.equals("default")) {
                return i;
            }
        }
        return 0;
    }

    public static boolean playAnimationAtIndex(SpawnedPreview preview, int animIndex, boolean forceLoop) {
        if (preview == null || preview.getActiveModel() == null || !megOk) return false;
        List<String> anims = preview.getAnimations();
        if (anims.isEmpty()) return false;

        if (animIndex < 0 || animIndex >= anims.size()) {
            stopAnimations(preview);
            return true;
        }
        String animName = anims.get(animIndex);
        boolean played = playAnimationRaw(preview.getActiveModel(), animName, forceLoop);
        if (played) {
            preview.setCurrentAnimation(animIndex, animName);
        }
        return played;
    }

    public static boolean playNamedAnimation(SpawnedPreview preview, String animName, boolean forceLoop) {
        if (preview == null || preview.getActiveModel() == null || animName == null || !megOk) return false;
        int matchedIdx = -1;
        List<String> anims = preview.getAnimations();
        for (int i = 0; i < anims.size(); i++) {
            if (anims.get(i).equalsIgnoreCase(animName)) {
                matchedIdx = i;
                animName = anims.get(i);
                break;
            }
        }
        boolean played = playAnimationRaw(preview.getActiveModel(), animName, forceLoop);
        if (played) {
            preview.setCurrentAnimation(matchedIdx, animName);
        }
        return played;
    }

    public static void stopAnimations(SpawnedPreview preview) {
        if (preview == null) return;
        if (preview.getActiveModel() != null && megOk && megActiveGetAnimHandler != null && megAnimForceStopAll != null) {
            try {
                Object handler = megActiveGetAnimHandler.invoke(preview.getActiveModel());
                if (handler != null) {
                    megAnimForceStopAll.invoke(handler);
                }
            } catch (Throwable ignored) {}
        }
        preview.setCurrentAnimation(-1, null);
    }

    private static boolean playAnimationRaw(Object activeModel, String animName, boolean forceLoop) {
        if (activeModel == null || animName == null || animName.isBlank() || !megOk) return false;
        try {
            Object handler = megActiveGetAnimHandler.invoke(activeModel);
            if (handler == null) return false;
            megAnimForceStopAll.invoke(handler);
            Object prop = megAnimPlay.invoke(handler, animName, 0.05, 0.05, 1.0, true);
            if (prop == null) return false;
            if (forceLoop && megPropSetForceLoopMode != null && megLoopModeLoop != null) {
                megPropSetForceLoopMode.invoke(prop, megLoopModeLoop);
            }
            return true;
        } catch (Throwable t) {
            DCCosmetics.getInstance().getLogger().warning("[Curator] Failed playing animation '" + animName + "': " + t.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> mergeRuntimeAnimations(Object blueprint, List<String> diskAnims) {
        Set<String> merged = new LinkedHashSet<>();
        if (diskAnims != null) merged.addAll(diskAnims);
        if (blueprint != null && megBlueprintGetAnimations != null) {
            try {
                Object mapObj = megBlueprintGetAnimations.invoke(blueprint);
                if (mapObj instanceof Map<?, ?> map) {
                    for (Object key : map.keySet()) {
                        if (key != null && !key.toString().isBlank()) {
                            merged.add(key.toString());
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return new ArrayList<>(merged);
    }

    private static SpawnedPreview spawnMegPreview(CuratorEntry entry, Location loc, String header, String subHeader) {
        initMeg();
        if (!megOk) return null;
        try {
            Object blueprint = megGetBlueprint.invoke(null, entry.getId());
            if (blueprint == null) return null;

            ArmorStand base = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            base.setInvisible(true);
            base.setInvulnerable(true);
            base.setGravity(false);
            base.setSilent(true);
            base.setPersistent(false);
            base.setBasePlate(false);
            base.setCollidable(false);
            base.getPersistentDataContainer().set(PREVIEW_ENTITY_KEY, PersistentDataType.BYTE, (byte) 1);

            Object modeledEntity = megCreateModeledEntityEntity.invoke(null, base);
            Object activeModel = megCreateActiveModel.invoke(null, blueprint);
            megModeledAddModel.invoke(modeledEntity, activeModel, true);

            List<String> animations = mergeRuntimeAnimations(blueprint, entry.getAnimations());
            SpawnedPreview sp = new SpawnedPreview(
                    entry.getId(),
                    CuratorEntry.SourceType.MEG,
                    modeledEntity,
                    activeModel,
                    null,
                    animations,
                    header,
                    subHeader
            );
            sp.addEntity(base);
            return sp;
        } catch (Throwable t) {
            DCCosmetics.getInstance().getLogger().warning("[Curator] Failed spawning MEG model '" + entry.getId() + "': " + t.getMessage());
            return null;
        }
    }

    private static SpawnedPreview spawnIaFurniturePreview(CuratorEntry entry, Location loc, String header, String subHeader) {
        initIa();
        if (iaOk) {
            try {
                Object cf = iaFurnitureSpawnPrecise.invoke(null, entry.getId(), loc);
                if (cf == null) {
                    cf = iaFurnitureSpawnBlock.invoke(null, entry.getId(), loc.getBlock());
                }
                if (cf != null) {
                    SpawnedPreview sp = new SpawnedPreview(
                            entry.getId(),
                            CuratorEntry.SourceType.IA_FURNITURE,
                            null,
                            null,
                            cf,
                            Collections.emptyList(),
                            header,
                            subHeader
                    );
                    if (iaFurnitureGetArmorstand != null) {
                        Object entObj = iaFurnitureGetArmorstand.invoke(cf);
                        if (entObj instanceof Entity ent) {
                            ent.getPersistentDataContainer().set(PREVIEW_ENTITY_KEY, PersistentDataType.BYTE, (byte) 1);
                            sp.addEntity(ent);
                        }
                    }
                    return sp;
                }
            } catch (Throwable ignored) {}
        }
        return spawnIaItemDisplayPreview(entry, loc, header, subHeader);
    }

    private static SpawnedPreview spawnIaItemDisplayPreview(CuratorEntry entry, Location loc, String header, String subHeader) {
        ItemStack stack = resolveItemsAdderItemStack(entry);
        Location displayLoc = loc.clone().add(0, 0.9, 0);
        ItemDisplay display = loc.getWorld().spawn(displayLoc, ItemDisplay.class, id -> {
            id.setItemStack(stack);
            id.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            id.setBillboard(Display.Billboard.FIXED);
            id.setPersistent(false);
            id.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(1.5f, 1.5f, 1.5f),
                    new AxisAngle4f(0, 0, 1, 0)
            ));
            id.getPersistentDataContainer().set(PREVIEW_ENTITY_KEY, PersistentDataType.BYTE, (byte) 1);
        });
        SpawnedPreview sp = new SpawnedPreview(
                entry.getId(),
                entry.getSourceType(),
                null,
                null,
                null,
                Collections.emptyList(),
                header,
                subHeader
        );
        sp.addEntity(display);
        return sp;
    }

    public static int purgeNearbyPreviewEntities(Location center, double radius) {
        if (center == null || center.getWorld() == null) return 0;
        int removed = 0;
        World w = center.getWorld();
        for (Entity e : w.getNearbyEntities(center, radius, radius, radius)) {
            if (e.getPersistentDataContainer().has(PREVIEW_ENTITY_KEY, PersistentDataType.BYTE)) {
                e.remove();
                removed++;
            }
        }
        return removed;
    }
}
