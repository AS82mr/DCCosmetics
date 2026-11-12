package dc.dcessentials.modules.pets.vfx;

import dc.dccosmetics.DCCosmetics;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Reflection-based bridge to ModelEngine 4 for spawning temporary VFX-only
 * blueprint animations (e.g. dce_vfx_zephyr, drone_shieldvfx) at world locations
 * without compile-time dependency on proprietary ModelEngine jars.
 */
public final class ModelEngineVfxBridge {

    private static volatile boolean initialized = false;
    private static volatile boolean available = false;

    private static Method getBlueprintMethod;
    private static Method createActiveModelMethod;
    private static Method createModeledEntityMethod;

    private static Constructor<?> dummyCtor;
    private static Method dummySetLocation;
    private static Method dummySetYHeadRot;
    private static Method dummySetYBodyRot;
    private static Method dummySetDetectingPlayers;
    private static Method dummySetRemoved;

    private static Method modeledAddModel;
    private static Method modeledDestroy;
    private static Method activeSetScale;
    private static Method activeGetAnimHandler;
    private static Method animPlay;

    private static final Set<VfxHandle> ACTIVE_HANDLES = Collections.synchronizedSet(new HashSet<>());

    private ModelEngineVfxBridge() {}

    public static boolean isAvailable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) return false;
        if (initialized) return available;
        initReflection();
        return available;
    }

    private static synchronized void initReflection() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> apiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Class<?> blueprintClass = Class.forName("com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint");
            Class<?> baseEntityClass = Class.forName("com.ticxo.modelengine.api.entity.BaseEntity");
            Class<?> dummyClass = Class.forName("com.ticxo.modelengine.api.entity.Dummy");
            Class<?> modeledClass = Class.forName("com.ticxo.modelengine.api.model.ModeledEntity");
            Class<?> activeModelClass = Class.forName("com.ticxo.modelengine.api.model.ActiveModel");
            Class<?> animHandlerClass = Class.forName("com.ticxo.modelengine.api.animation.handler.AnimationHandler");

            getBlueprintMethod = apiClass.getMethod("getBlueprint", String.class);
            createActiveModelMethod = apiClass.getMethod("createActiveModel", blueprintClass);
            createModeledEntityMethod = apiClass.getMethod("createModeledEntity", baseEntityClass);

            dummyCtor = dummyClass.getConstructor();
            dummySetLocation = dummyClass.getMethod("setLocation", Location.class);
            dummySetYHeadRot = dummyClass.getMethod("setYHeadRot", float.class);
            dummySetYBodyRot = dummyClass.getMethod("setYBodyRot", float.class);
            dummySetDetectingPlayers = dummyClass.getMethod("setDetectingPlayers", boolean.class);
            dummySetRemoved = dummyClass.getMethod("setRemoved", boolean.class);

            modeledAddModel = modeledClass.getMethod("addModel", activeModelClass, boolean.class);
            modeledDestroy = modeledClass.getMethod("destroy");
            activeSetScale = activeModelClass.getMethod("setScale", double.class);
            activeGetAnimHandler = activeModelClass.getMethod("getAnimationHandler");
            animPlay = animHandlerClass.getMethod("playAnimation", String.class, double.class, double.class, double.class, boolean.class);

            available = true;
        } catch (Throwable t) {
            available = false;
            DCCosmetics.getInstance().getLogger().warning("[DCEPets] ModelEngine VFX bridge unavailable: " + t.getMessage());
        }
    }

    /**
     * Spawns a temporary ModelEngine VFX blueprint, plays its animation, and automatically cleans it up.
     *
     * @param modelId ModelEngine blueprint id (e.g. "dce_vfx_pyre")
     * @param loc Spawn location
     * @param animation Animation state name (e.g. "execute", "shield", "idle")
     * @param scale Visual scale factor
     * @param durationTicks Ticks before automatic destruction
     * @return VfxHandle if spawned, or null if blueprint isn't loaded / ModelEngine absent
     */
    public static VfxHandle spawnEffect(String modelId, Location loc, String animation, double scale, int durationTicks) {
        if (modelId == null || modelId.isBlank() || loc == null || loc.getWorld() == null) return null;
        if (!isAvailable()) return null;

        try {
            Object blueprint = getBlueprintMethod.invoke(null, modelId.trim());
            if (blueprint == null) {
                return null;
            }

            Object dummy = dummyCtor.newInstance();
            dummySetLocation.invoke(dummy, loc);
            dummySetYBodyRot.invoke(dummy, loc.getYaw());
            dummySetYHeadRot.invoke(dummy, loc.getYaw());
            dummySetDetectingPlayers.invoke(dummy, true);

            Object modeled = createModeledEntityMethod.invoke(null, dummy);
            if (modeled == null) {
                dummySetRemoved.invoke(dummy, true);
                return null;
            }

            Object activeModel = createActiveModelMethod.invoke(null, blueprint);
            if (activeModel == null) {
                modeledDestroy.invoke(modeled);
                dummySetRemoved.invoke(dummy, true);
                return null;
            }

            if (scale > 0.05 && Math.abs(scale - 1.0) > 1e-3) {
                activeSetScale.invoke(activeModel, scale);
            }
            modeledAddModel.invoke(modeled, activeModel, true);

            Object animHandler = activeGetAnimHandler.invoke(activeModel);
            if (animHandler != null && animation != null && !animation.isBlank()) {
                animPlay.invoke(animHandler, animation, 0.0, 0.15, 1.0, false);
            }

            VfxHandle handle = new VfxHandle(dummy, modeled);
            ACTIVE_HANDLES.add(handle);

            Bukkit.getScheduler().runTaskLater(DCCosmetics.getInstance(), handle::destroy, Math.max(10L, durationTicks));
            return handle;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void cleanupAll() {
        Set<VfxHandle> copy;
        synchronized (ACTIVE_HANDLES) {
            copy = new HashSet<>(ACTIVE_HANDLES);
            ACTIVE_HANDLES.clear();
        }
        for (VfxHandle handle : copy) {
            handle.destroy();
        }
    }

    public static final class VfxHandle {
        private final Object dummy;
        private final Object modeledEntity;
        private volatile boolean destroyed = false;

        private VfxHandle(Object dummy, Object modeledEntity) {
            this.dummy = dummy;
            this.modeledEntity = modeledEntity;
        }

        public void teleport(Location loc) {
            if (destroyed || loc == null || loc.getWorld() == null) return;
            try {
                dummySetLocation.invoke(dummy, loc);
            } catch (Throwable ignored) {}
        }

        public void destroy() {
            if (destroyed) return;
            destroyed = true;
            ACTIVE_HANDLES.remove(this);
            try {
                modeledDestroy.invoke(modeledEntity);
            } catch (Throwable ignored) {}
            try {
                dummySetRemoved.invoke(dummy, true);
            } catch (Throwable ignored) {}
        }
    }
}
