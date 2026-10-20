package fr.hardek.shadowlod.compat;

import fr.hardek.shadowlod.ShadowLOD;
import fr.hardek.shadowlod.core.RenderManager;
import fr.hardek.shadowlod.core.Settings;
import org.joml.Matrix4f;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class ShadowCulling {
    public static final int KEEP = 0, HIDE = 1, HIDE_PRUNE = 2;

    private static VarHandle activeHandle;
    private static VarHandle modelViewHandle;
    private static boolean initialized;

    public static boolean cacheRestoredThisFrame;
    private static VarHandle renderDistanceHandle;

    public static double packShadowDistance() {
        if (!initialized) init();
        if (renderDistanceHandle == null) return -1;
        try {
            int chunks = (int) renderDistanceHandle.get();
            return chunks > 0 ? chunks * 16.0 : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    public static double effectiveTerrainDistance() {
        double manual = Settings.shadowTerrainDistance;
        if (manual > 0) return manual;
        double pack = packShadowDistance();
        if (pack <= 0) return 0;
        return pack * Settings.shadowTerrainPercent / 100.0 + 16.0;
    }

    public static Matrix4f shadowModelView() {
        if (!initialized) init();
        if (modelViewHandle == null) return null;
        try {
            return (Matrix4f) modelViewHandle.get();
        } catch (Throwable t) {
            return null;
        }
    }

    private static float ldx = 0f, ldy = -1f, ldz = 0f;
    private static long dirFrame = -1;

    public static int terrainTested, terrainCulled, entitiesTested, entitiesCulled, beTested, beCulled;
    public static int lastTerrainTested, lastTerrainCulled, lastEntitiesTested, lastEntitiesCulled, lastBeTested, lastBeCulled;

    private static void init() {
        initialized = true;
        try {
            Class<?> c = Class.forName("net.irisshaders.iris.shadows.ShadowRenderer");
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            activeHandle = l.findStaticVarHandle(c, "ACTIVE", boolean.class);
            modelViewHandle = l.findStaticVarHandle(c, "MODELVIEW", Matrix4f.class);
            try {
                renderDistanceHandle = l.findStaticVarHandle(c, "renderDistance", int.class);
            } catch (Throwable ignored) {
                renderDistanceHandle = null;
            }
            ShadowLOD.LOG.info("[ShadowLOD] Oculus shadow renderer found");
        } catch (Throwable t) {
            activeHandle = null;
            modelViewHandle = null;
        }
    }

    public static boolean isShadowPassAvailable() {
        if (!initialized) init();
        return activeHandle != null;
    }

    public static boolean isShadowPass() {
        if (!initialized) init();
        if (activeHandle != null) {
            try {
                return (boolean) activeHandle.get();
            } catch (Throwable ignored) {
            }
        }
        return IrisCompat.isShadowPass();
    }

    private static void updateLightDir() {
        long f = RenderManager.frameIndex();
        if (f == dirFrame) return;
        dirFrame = f;
        Matrix4f mv = null;
        if (modelViewHandle != null) {
            try {
                mv = (Matrix4f) modelViewHandle.get();
            } catch (Throwable ignored) {
            }
        }
        if (mv == null) {
            ldx = 0f; ldy = -1f; ldz = 0f;
            return;
        }

        float x = -mv.m02(), y = -mv.m12(), z = -mv.m22();
        if (y > 0f) { x = -x; y = -y; z = -z; }
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len < 1e-5f) { ldx = 0f; ldy = -1f; ldz = 0f; return; }
        ldx = x / len; ldy = y / len; ldz = z / len;
    }

    public static int classify(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double maxDistance) {
        return classify(minX, minY, minZ, maxX, maxY, maxZ, maxDistance, Settings.shadowViewMargin);
    }

    public static int classify(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double maxDistance, float margin) {
        if (!Settings.shadowOptimizations || !RenderManager.isRunning() || !ShaderProfile.anyShadowOpts()) return KEEP;
        updateLightDir();

        double cx = RenderManager.camX(), cz = RenderManager.camZ();
        if (maxDistance > 0) {
            double dx = Math.max(Math.max(minX - cx, cx - maxX), 0);
            double dz = Math.max(Math.max(minZ - cz, cz - maxZ), 0);
            if (dx * dx + dz * dz > maxDistance * maxDistance) return HIDE_PRUNE;
        }

        double L = Settings.shadowCastLength;
        double ex = ldx * L, ey = ldy * L, ez = ldz * L;
        double eMinX = Math.min(minX, minX + ex), eMaxX = Math.max(maxX, maxX + ex);
        double eMinY = Math.min(minY, minY + ey), eMaxY = Math.max(maxY, maxY + ey);
        double eMinZ = Math.min(minZ, minZ + ez), eMaxZ = Math.max(maxZ, maxZ + ez);

        if (RenderManager.isOutsideCurrentView(eMinX, eMinY, eMinZ, eMaxX, eMaxY, eMaxZ, margin)) return HIDE;
        if (RenderManager.isBoxOccluded(eMinX, eMinY, eMinZ, eMaxX, eMaxY, eMaxZ)) return HIDE;
        return KEEP;
    }

    public static int classifyTerrain(int sx, int sy, int sz, boolean sortable) {
        double minX = sx << 4, minY = sy << 4, minZ = sz << 4;
        double maxX = minX + 16, maxY = minY + 16, maxZ = minZ + 16;
        if (!ShaderProfile.fullShadowOpts()) return KEEP;
        if (ShadowCache.enabled()) {
            if (!Settings.shadowOptimizations || !RenderManager.isRunning()) return KEEP;
            double d = effectiveTerrainDistance();
            if (d > 0) {
                double cx = RenderManager.camX(), cz = RenderManager.camZ();
                double dx = Math.max(Math.max(minX - cx, cx - maxX), 0);
                double dz = Math.max(Math.max(minZ - cz, cz - maxZ), 0);
                if (dx * dx + dz * dz > d * d) return HIDE_PRUNE;
            }
            if (ShadowCache.mode() == ShadowCache.Mode.BUILD && !sortable && !ShadowCache.inBuildSubset(sx, sy, sz)) return HIDE;
            if (ShadowCache.viewCulling()) {
                return classify(minX, minY, minZ, maxX, maxY, maxZ, 0, Settings.shadowCacheViewMargin) == KEEP ? KEEP : HIDE;
            }
            return KEEP;
        }
        return classify(minX, minY, minZ, maxX, maxY, maxZ, effectiveTerrainDistance());
    }

    public static void endFrame() {
        cacheRestoredThisFrame = false;
        lastEntitiesTested = entitiesTested; lastEntitiesCulled = entitiesCulled;
        lastBeTested = beTested; lastBeCulled = beCulled;
        lastTerrainTested = terrainTested; lastTerrainCulled = terrainCulled;
        entitiesTested = entitiesCulled = beTested = beCulled = 0;
    }

    private ShadowCulling() {}
}
