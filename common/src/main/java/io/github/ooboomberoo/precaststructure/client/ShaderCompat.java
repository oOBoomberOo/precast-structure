package io.github.ooboomberoo.precaststructure.client;

/**
 * Iris / Oculus compatibility helpers.
 *
 * <p>Iris cannot run custom core shaders inside an active shader pack's gbuffer pipeline
 * (meshes using those programs are dropped). When a pack is active we keep holograms visible
 * by drawing with vanilla solid/translucent programs and applying the hologram look on the
 * CPU via {@link HologramStyleVertexConsumer}.
 *
 * <p>Deferred packs also composite over anything drawn during the opaque/entity phase, so
 * overlays must flush after translucent geometry when a pack is active.
 */
public final class ShaderCompat {
    private static final String[] IRIS_API_CLASSES = {
        "net.irisshaders.iris.api.v0.IrisApi",
        "net.coderbot.iris.api.v0.IrisApi"
    };

    private static final String[] IRIS_INTERNAL_CLASSES = {
        "net.irisshaders.iris.Iris",
        "net.coderbot.iris.Iris"
    };

    private static final int UNKNOWN = -1;
    private static final int ABSENT = 0;
    private static final int PRESENT = 1;

    private static int irisApiState = UNKNOWN;
    private static Class<?> irisApiClass;
    private static int irisInternalState = UNKNOWN;
    private static Class<?> irisInternalClass;

    private ShaderCompat() {
    }

    public static boolean isExternalShaderPackActive() {
        Boolean api = invokeIrisApiBoolean("isShaderPackInUse");
        if (api != null) {
            return api;
        }
        return invokeIrisInternalBoolean("isPackInUseQuick");
    }

    public static boolean isRenderingShadowPass() {
        Boolean api = invokeIrisApiBoolean("isRenderingShadowPass");
        return api != null && api;
    }

    /**
     * Deferred shader packs overwrite translucent draws made during the entity phase.
     * Flush holograms after translucent/particles when a pack is active.
     */
    public static boolean shouldUseLateWorldOverlayPass() {
        return isExternalShaderPackActive();
    }

    private static Boolean invokeIrisApiBoolean(String method) {
        Class<?> apiClass = resolveIrisApi();
        if (apiClass == null) {
            return null;
        }
        try {
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object result = apiClass.getMethod(method).invoke(api);
            return result instanceof Boolean bool ? bool : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean invokeIrisInternalBoolean(String method) {
        Class<?> irisClass = resolveIrisInternal();
        if (irisClass == null) {
            return false;
        }
        try {
            Object result = irisClass.getMethod(method).invoke(null);
            return result instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static Class<?> resolveIrisApi() {
        if (irisApiState == ABSENT) {
            return null;
        }
        if (irisApiState == PRESENT) {
            return irisApiClass;
        }
        irisApiClass = findClass(IRIS_API_CLASSES);
        irisApiState = irisApiClass != null ? PRESENT : ABSENT;
        return irisApiClass;
    }

    private static Class<?> resolveIrisInternal() {
        if (irisInternalState == ABSENT) {
            return null;
        }
        if (irisInternalState == PRESENT) {
            return irisInternalClass;
        }
        irisInternalClass = findClass(IRIS_INTERNAL_CLASSES);
        irisInternalState = irisInternalClass != null ? PRESENT : ABSENT;
        return irisInternalClass;
    }

    private static Class<?> findClass(String[] classNames) {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        for (String className : classNames) {
            Class<?> found = tryLoad(className, context);
            if (found != null) {
                return found;
            }
            found = tryLoad(className, ShaderCompat.class.getClassLoader());
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Class<?> tryLoad(String className, ClassLoader loader) {
        try {
            if (loader != null) {
                return Class.forName(className, false, loader);
            }
            return Class.forName(className);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }
}
