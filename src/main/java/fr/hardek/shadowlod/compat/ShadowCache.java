package fr.hardek.shadowlod.compat;

import fr.hardek.shadowlod.ShadowLOD;
import fr.hardek.shadowlod.core.RenderManager;
import fr.hardek.shadowlod.core.Settings;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL30C;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL43C.glCopyImageSubData;

public final class ShadowCache {
    public enum Mode { NONE, RENDER, USE, BUILD }

    private static boolean failed;
    public static String failReason = "";
    public static String status = "not evaluated yet";

    private static long decidedFrame = -1;
    private static Mode mode = Mode.NONE;

    private static boolean displayValid;
    private static final Matrix4f displayWorld = new Matrix4f();
    private static double displayX, displayY, displayZ;
    private static float displayYaw, displayPitch;
    private static long displayAt;
    private static boolean dirty;

    private static boolean building;
    private static int buildStep, buildSteps;
    private static final Matrix4f buildWorld = new Matrix4f();
    private static double buildX, buildY, buildZ;
    private static float buildYaw, buildPitch;
    private static boolean dirtyDuringBuild;

    private static final Matrix4f current = new Matrix4f();

    private static int[] srcIds = new int[0];
    private static int[] displayIds = new int[0];
    private static int[] buildIds = new int[0];
    private static int resolution;

    private static Method createModelView;
    private static float sunPathRotation, intervalSize;
    private static Object paramsFrom;

    public static int hits, misses, builds, lastHits, lastMisses, lastBuilds;
    private static long statFrame;

    public static void invalidate() {
        displayValid = false;
        building = false;
    }

    public static void markDirty() {
        dirty = true;
        if (building) dirtyDuringBuild = true;
    }

    public static boolean enabled() {
        return Settings.shadowCache && !failed && ShaderProfile.fullShadowOpts() && Settings.shadowOptimizations
                && RenderManager.isRunning();
    }

    public static boolean viewCulling() {
        return enabled() && Settings.shadowCacheViewCulling;
    }

    public static Mode mode() {
        return decidedFrame == RenderManager.frameIndex() ? mode : Mode.NONE;
    }

    public static int buildStep() {
        return buildStep;
    }

    public static int buildSteps() {
        return buildSteps;
    }

    private static Matrix4f computeShadowModelView(Object renderer) {
        try {
            if (paramsFrom != renderer) {
                paramsFrom = renderer;
                Object spr = ShaderProfile.read(renderer, "sunPathRotation");
                Object itv = ShaderProfile.read(renderer, "intervalSize");
                sunPathRotation = spr instanceof Float x ? x : 0f;
                intervalSize = itv instanceof Float x ? x : 2f;
                createModelView = renderer.getClass().getMethod("createShadowModelView", float.class, float.class);
            }
            Object pose = createModelView.invoke(null, sunPathRotation, intervalSize);
            return ((com.mojang.blaze3d.vertex.PoseStack) pose).last().pose();
        } catch (Throwable t) {
            fail("shadow matrix", t);
            return null;
        }
    }

    public static Mode decide(Object renderer) {
        long f = RenderManager.frameIndex();
        if (f == decidedFrame) return mode;
        decidedFrame = f;
        if (f - statFrame >= 60) {
            lastHits = hits; lastMisses = misses; lastBuilds = builds;
            hits = misses = builds = 0;
            statFrame = f;
        }
        if (!enabled()) {
            status = failed ? "ERROR [" + failReason + "]"
                    : !Settings.shadowCache ? "disabled (config)"
                    : !ShaderProfile.fullShadowOpts() ? "disabled (profile " + ShaderProfile.get() + ")"
                    : "inactive";
            displayValid = false;
            building = false;
            mode = Mode.NONE;
            return mode;
        }
        Matrix4f mv = computeShadowModelView(renderer);
        if (mv == null) {
            mode = Mode.NONE;
            return mode;
        }
        status = "active";
        current.set(mv).translate((float) -RenderManager.camX(), (float) -RenderManager.camY(), (float) -RenderManager.camZ());

        boolean sameMatrix = displayValid && close(current, displayWorld);
        if (!sameMatrix) {
            building = false;
            mode = Mode.RENDER;
            misses++;
            return mode;
        }

        float rot = Settings.shadowCacheViewCulling ? angleTo(displayYaw, displayPitch) : 0f;
        double move = Settings.shadowCacheViewCulling ? distanceTo(displayX, displayY, displayZ) : 0;
        boolean hardView = Settings.shadowCacheViewCulling
                && (rot > Settings.shadowCacheMaxRotation * 2f || move > Settings.shadowCacheMaxMove * 2);
        if (hardView) {
            building = false;
            mode = Mode.RENDER;
            misses++;
            return mode;
        }

        boolean softView = Settings.shadowCacheViewCulling
                && (rot > Settings.shadowCacheMaxRotation || move > Settings.shadowCacheMaxMove);
        boolean stale = dirty || softView || f - displayAt >= Settings.shadowCacheMaxAge;

        if (building) {
            mode = Mode.BUILD;
            builds++;
            return mode;
        }
        if (stale) {
            if (Settings.shadowRebuildFrames <= 1 || !EmbeddiumCompat.canSplitShadowRebuild()) {
                mode = Mode.RENDER;
                misses++;
                return mode;
            }
            building = true;
            buildStep = 0;
            buildSteps = Settings.shadowRebuildFrames;
            buildWorld.set(current);
            buildX = RenderManager.camX(); buildY = RenderManager.camY(); buildZ = RenderManager.camZ();
            buildYaw = RenderManager.yaw(); buildPitch = RenderManager.pitch();
            dirty = false;
            dirtyDuringBuild = false;
            mode = Mode.BUILD;
            builds++;
            return mode;
        }
        mode = Mode.USE;
        hits++;
        return mode;
    }

    private static float angleTo(float yaw, float pitch) {
        double y1 = Math.toRadians(yaw), p1 = Math.toRadians(pitch);
        double y2 = Math.toRadians(RenderManager.yaw()), p2 = Math.toRadians(RenderManager.pitch());
        double ax = -Math.sin(y1) * Math.cos(p1), ay = -Math.sin(p1), az = Math.cos(y1) * Math.cos(p1);
        double bx = -Math.sin(y2) * Math.cos(p2), by = -Math.sin(p2), bz = Math.cos(y2) * Math.cos(p2);
        double d = Math.max(-1, Math.min(1, ax * bx + ay * by + az * bz));
        return (float) Math.toDegrees(Math.acos(d));
    }

    private static double distanceTo(double x, double y, double z) {
        double dx = RenderManager.camX() - x, dy = RenderManager.camY() - y, dz = RenderManager.camZ() - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean close(Matrix4f a, Matrix4f b) {
        if (Math.abs(a.m00() - b.m00()) > 1e-5f || Math.abs(a.m01() - b.m01()) > 1e-5f || Math.abs(a.m02() - b.m02()) > 1e-5f) return false;
        if (Math.abs(a.m10() - b.m10()) > 1e-5f || Math.abs(a.m11() - b.m11()) > 1e-5f || Math.abs(a.m12() - b.m12()) > 1e-5f) return false;
        if (Math.abs(a.m20() - b.m20()) > 1e-5f || Math.abs(a.m21() - b.m21()) > 1e-5f || Math.abs(a.m22() - b.m22()) > 1e-5f) return false;
        return Math.abs(a.m30() - b.m30()) < 1e-3f && Math.abs(a.m31() - b.m31()) < 1e-3f && Math.abs(a.m32() - b.m32()) < 1e-3f;
    }

    public static boolean inBuildSubset(int sx, int sy, int sz) {
        int h = (sx * 73856093) ^ (sy * 19349663) ^ (sz * 83492791);
        return Math.floorMod(h, buildSteps) == buildStep;
    }

    public static boolean beforeOpaque() {
        Mode m = mode();
        if (m == Mode.USE) {
            return restoreDisplay();
        }
        if (m == Mode.BUILD && buildStep > 0) {
            int[] src = gatherSources();
            if (src == null || !Arrays.equals(src, srcIds)) {
                building = false;
                return false;
            }
            copy(buildIds, src);
        }
        return false;
    }

    public static void afterOpaque() {
        Mode m = mode();
        if (m == Mode.RENDER) {
            int[] src = gatherSources();
            if (src == null || !ensureCache(src)) return;
            copy(src, displayIds);
            displayValid = true;
            displayWorld.set(current);
            displayX = RenderManager.camX(); displayY = RenderManager.camY(); displayZ = RenderManager.camZ();
            displayYaw = RenderManager.yaw(); displayPitch = RenderManager.pitch();
            displayAt = RenderManager.frameIndex();
            dirty = false;
            return;
        }
        if (m != Mode.BUILD) return;
        int[] src = gatherSources();
        if (src == null || !ensureCache(src)) {
            building = false;
            return;
        }
        copy(src, buildIds);
        buildStep++;
        if (buildStep >= buildSteps) {
            int[] t = displayIds;
            displayIds = buildIds;
            buildIds = t;
            displayWorld.set(buildWorld);
            displayX = buildX; displayY = buildY; displayZ = buildZ;
            displayYaw = buildYaw; displayPitch = buildPitch;
            displayAt = RenderManager.frameIndex();
            displayValid = true;
            building = false;
            dirty = dirtyDuringBuild;
        }
        copy(displayIds, src);
    }

    private static boolean restoreDisplay() {
        int[] src = gatherSources();
        if (src == null || !displayValid || !Arrays.equals(src, srcIds)) {
            displayValid = false;
            return false;
        }
        copy(displayIds, src);
        return true;
    }

    private static int[] gatherSources() {
        Object renderer = ShaderProfile.renderer();
        if (renderer == null) return null;
        Object targets = ShaderProfile.read(renderer, "targets");
        if (targets == null) return null;
        try {
            Class<?> tc = targets.getClass();
            Object depth = tc.getMethod("getDepthTexture").invoke(targets);
            int depthId = (int) depth.getClass().getMethod("getTextureId").invoke(depth);
            int n = (int) tc.getMethod("getNumColorTextures").invoke(targets);
            Method get = tc.getMethod("get", int.class);
            int[] ids = new int[n + 1];
            int k = 0;
            ids[k++] = depthId;
            for (int i = 0; i < n; i++) {
                Object rt = get.invoke(targets, i);
                if (rt == null) continue;
                ids[k++] = (int) rt.getClass().getMethod("getMainTexture").invoke(rt);
            }
            resolution = (int) tc.getMethod("getResolution").invoke(targets);
            return Arrays.copyOf(ids, k);
        } catch (Throwable t) {
            fail("shadow targets", t);
            return null;
        }
    }

    private static boolean ensureCache(int[] src) {
        if (Arrays.equals(src, srcIds) && displayIds.length == src.length && buildIds.length == src.length) return true;
        for (int id : displayIds) if (id != 0) glDeleteTextures(id);
        for (int id : buildIds) if (id != 0) glDeleteTextures(id);
        displayIds = new int[src.length];
        buildIds = new int[src.length];
        displayValid = false;
        building = false;
        int prev = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            for (int i = 0; i < src.length; i++) {
                displayIds[i] = createLike(src[i], i);
                buildIds[i] = createLike(src[i], i);
            }
        } catch (Throwable t) {
            fail("cache creation", t);
            return false;
        } finally {
            glBindTexture(GL_TEXTURE_2D, prev);
        }
        srcIds = src;
        return true;
    }

    private static int createLike(int source, int index) {
        glBindTexture(GL_TEXTURE_2D, source);
        int fmt = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_INTERNAL_FORMAT);
        int w = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
        int h = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
        int depthBits = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL14C.GL_TEXTURE_DEPTH_SIZE);
        int stencilBits = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL30C.GL_TEXTURE_STENCIL_SIZE);
        int redType = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL30C.GL_TEXTURE_RED_TYPE);
        int pixFormat, pixType;
        if (depthBits > 0 && stencilBits > 0) {
            pixFormat = GL30C.GL_DEPTH_STENCIL; pixType = GL30C.GL_UNSIGNED_INT_24_8;
        } else if (depthBits > 0) {
            pixFormat = GL_DEPTH_COMPONENT; pixType = GL_FLOAT;
        } else if (redType == GL_INT || redType == GL_UNSIGNED_INT) {
            pixFormat = GL30C.GL_RGBA_INTEGER; pixType = redType;
        } else {
            pixFormat = GL_RGBA; pixType = GL_FLOAT;
        }
        for (int i = 0; i < 16 && glGetError() != GL_NO_ERROR; i++) { }
        int t = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, t);
        glTexImage2D(GL_TEXTURE_2D, 0, fmt, w, h, 0, pixFormat, pixType, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL12C.GL_TEXTURE_MAX_LEVEL, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        int err = glGetError();
        if (err != GL_NO_ERROR) {
            throw new IllegalStateException("texture " + index + " format 0x" + Integer.toHexString(fmt)
                    + " (" + w + "x" + h + ") GL error 0x" + Integer.toHexString(err));
        }
        return t;
    }

    private static void copy(int[] from, int[] to) {
        for (int i = 0; i < 16 && glGetError() != GL_NO_ERROR; i++) { }
        for (int i = 0; i < from.length; i++) {
            glCopyImageSubData(from[i], GL_TEXTURE_2D, 0, 0, 0, 0, to[i], GL_TEXTURE_2D, 0, 0, 0, 0, resolution, resolution, 1);
        }
        int err = glGetError();
        if (err != GL_NO_ERROR) fail("GPU copy (GL error 0x" + Integer.toHexString(err) + ")", null);
    }

    private static void fail(String what, Throwable t) {
        failed = true;
        displayValid = false;
        building = false;
        Throwable root = t;
        while (root != null && root.getCause() != null) root = root.getCause();
        failReason = what + (root != null ? " : " + root : "");
        ShadowLOD.LOG.error("[ShadowLOD] shadow cache disabled ({})", failReason, t);
    }

    private ShadowCache() {}
}
