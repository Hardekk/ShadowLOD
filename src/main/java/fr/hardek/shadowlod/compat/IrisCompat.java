package fr.hardek.shadowlod.compat;

import fr.hardek.shadowlod.ShadowLOD;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class IrisCompat {
    private static MethodHandle shadowPass;
    private static MethodHandle packInUse;
    private static boolean initialized;

    private static void init() {
        initialized = true;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            shadowPass = l.findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class)).bindTo(instance);
            packInUse = l.findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(instance);
            ShadowLOD.LOG.info("[ShadowLOD] Oculus API found");
        } catch (Throwable t) {
            shadowPass = null;
            packInUse = null;
        }
    }

    public static boolean isShadowPass() {
        if (!initialized) init();
        if (shadowPass == null) return false;
        try {
            return (boolean) shadowPass.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isShaderPackInUse() {
        if (!initialized) init();
        if (packInUse == null) return false;
        try {
            return (boolean) packInUse.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    public static int lastPipelineDepth = -1;

    public static int pipelineDepthTexture() {
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Object manager = iris.getMethod("getPipelineManager").invoke(null);
            Object pipeline = manager.getClass().getMethod("getPipelineNullable").invoke(manager);
            if (pipeline == null) return lastPipelineDepth = -1;
            Object targets = ShaderProfile.read(pipeline, "renderTargets");
            if (targets == null) return lastPipelineDepth = -1;
            Object d = targets.getClass().getMethod("getDepthTexture").invoke(targets);
            if (d instanceof Integer i) return lastPipelineDepth = i;
            if (d != null) return lastPipelineDepth = (int) d.getClass().getMethod("getTextureId").invoke(d);
        } catch (Throwable ignored) {
        }
        return lastPipelineDepth = -1;
    }

    private IrisCompat() {}
}
