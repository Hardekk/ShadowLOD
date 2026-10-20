package fr.hardek.shadowlod.core;

import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.hardek.shadowlod.compat.EmbeddiumCompat;
import fr.hardek.shadowlod.compat.ShadowCulling;
import fr.hardek.shadowlod.compat.IrisCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class RenderManager {
    public static final DepthPyramid PYRAMID = new DepthPyramid();

    private static int depth;
    private static boolean inMainPass;
    private static boolean activeThisFrame;
    private static long frameIndex;

    private static final Matrix4f proj = new Matrix4f();
    private static final Matrix4f view = new Matrix4f();
    private static double camX, camY, camZ;
    private static float lastYaw, lastPitch;
    private static double lastX, lastY, lastZ;
    private static int framesSinceMove = 1000;
    private static ClientLevel lastLevel;
    private static long lastPublishedFrame = -1;

    private static DepthData frameData;
    private static double frameShift;

    public static int terrainTested, terrainCulled, entitiesTested, entitiesCulled, beTested, beCulled, partTested, partCulled;
    public static int lastTerrainTested, lastTerrainCulled, lastEntitiesTested, lastEntitiesCulled, lastBeTested, lastBeCulled, lastPartTested, lastPartCulled;
    public static long lastDataAge = -1;
    public static int lastMainDepth = -1, lastUsedDepth = -1;

    private static float v00, v01, v03, v10, v11, v13, v20, v21, v23, v30, v31, v33;

    public static long frameIndex() { return frameIndex; }

    public static float yaw() { return lastYaw; }

    public static float pitch() { return lastPitch; }

    private static float sunAngle;
    public static float sunAngle() { return sunAngle; }

    public static boolean isRunning() {
        return activeThisFrame && inMainPass;
    }

    public static boolean isOutsideCurrentView(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float margin) {
        float x0 = (float) (minX - camX), y0 = (float) (minY - camY), z0 = (float) (minZ - camZ);
        float x1 = (float) (maxX - camX), y1 = (float) (maxY - camY), z1 = (float) (maxZ - camZ);
        float k = 1f + margin;
        int all = 0x1F;
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? x0 : x1;
            float y = (i & 2) == 0 ? y0 : y1;
            float z = (i & 4) == 0 ? z0 : z1;
            float cx = v00 * x + v10 * y + v20 * z + v30;
            float cy = v01 * x + v11 * y + v21 * z + v31;
            float cw = v03 * x + v13 * y + v23 * z + v33;
            int code = 0;
            if (cx < -k * cw) code |= 1;
            if (cx > k * cw) code |= 2;
            if (cy < -k * cw) code |= 4;
            if (cy > k * cw) code |= 8;
            if (cw < 0.05f) code |= 16;
            all &= code;
            if (all == 0) return false;
        }
        return all != 0;
    }

    public static boolean isActive() {
        return activeThisFrame && inMainPass && frameData != null;
    }

    private static final org.joml.Matrix3f normal = new org.joml.Matrix3f();
    private static float partialTick;

    public static Matrix4f viewMatrix() { return view; }

    public static org.joml.Matrix3f normalMatrix() { return normal; }

    public static float partialTick() { return partialTick; }

    public static void beginFrame(Matrix4f pose, org.joml.Matrix3f normalPose, float tickDelta, Matrix4f projection, Camera camera) {
        if (++depth != 1) return;
        inMainPass = true;
        frameIndex++;
        Settings.refresh();
        FrameGovernor.onFrame();
        AutoTune.update();
        ThreadGovernor.start();
        fr.hardek.shadowlod.compat.EmfCompat.tuneOnce();

        Minecraft mc = Minecraft.getInstance();
        sunAngle = mc.level != null ? mc.level.getTimeOfDay(1.0f) : 0f;
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            PYRAMID.invalidate();
            Impostors.reset();
        }

        boolean wanted = Settings.enabled && Settings.runtimeToggle && !PYRAMID.isFailed()
                && !(Settings.disableWithShaders && IrisCompat.isShaderPackInUse());

        proj.set(projection);
        view.set(pose);
        normal.set(normalPose);
        partialTick = tickDelta;
        Matrix4f vp = new Matrix4f(projection).mul(pose);
        v00 = vp.m00(); v01 = vp.m01(); v03 = vp.m03();
        v10 = vp.m10(); v11 = vp.m11(); v13 = vp.m13();
        v20 = vp.m20(); v21 = vp.m21(); v23 = vp.m23();
        v30 = vp.m30(); v31 = vp.m31(); v33 = vp.m33();
        Vec3 p = camera.getPosition();
        camX = p.x; camY = p.y; camZ = p.z;

        if (p.x != lastX || p.y != lastY || p.z != lastZ || camera.getYRot() != lastYaw || camera.getXRot() != lastPitch) {
            framesSinceMove = 0;
        } else {
            framesSinceMove++;
        }
        lastX = p.x; lastY = p.y; lastZ = p.z; lastYaw = camera.getYRot(); lastPitch = camera.getXRot();

        if (!wanted) {
            activeThisFrame = false;
            frameData = null;
            return;
        }
        PYRAMID.poll();
        DepthData d = Settings.occlusion ? PYRAMID.latest() : null;
        activeThisFrame = true;
        frameData = d;
        if (d != null) {
            double dx = camX - d.camX, dy = camY - d.camY, dz = camZ - d.camZ;
            frameShift = Math.sqrt(dx * dx + dy * dy + dz * dz);
            lastDataAge = frameIndex - d.frameIndex;
            if (frameShift > Settings.maxCameraShift) frameData = null;
        } else {
            lastDataAge = -1;
        }
    }

    public static void afterOpaqueTerrain() {
        if (depth != 1 || !activeThisFrame) return;
        if (IrisCompat.isShadowPass()) return;
        if (!Settings.occlusion) return;
        RenderTarget rt = Minecraft.getInstance().getMainRenderTarget();
        int tex = rt.getDepthTextureId();
        lastMainDepth = tex;
        if (IrisCompat.isShaderPackInUse()) {
            int iris = IrisCompat.pipelineDepthTexture();
            if (iris > 0) tex = iris;
        }
        lastUsedDepth = tex;
        PYRAMID.build(tex, rt.width, rt.height, camX, camY, camZ, proj, view, frameIndex);
    }

    public static void endFrame() {
        if (depth-- != 1) return;
        inMainPass = false;

        if (activeThisFrame && frameData != null && Settings.cullTerrain && EmbeddiumCompat.isLoaded()) {
            boolean fresh = frameData.frameIndex != lastPublishedFrame;
            lastPublishedFrame = frameData.frameIndex;
            if (fresh && (framesSinceMove <= 2 || (terrainCulled > 0 && (frameIndex & 7) == 0))) {
                EmbeddiumCompat.scheduleTerrainUpdate();
            }
        }

        lastTerrainTested = terrainTested; lastTerrainCulled = terrainCulled;
        lastEntitiesTested = entitiesTested; lastEntitiesCulled = entitiesCulled;
        lastBeTested = beTested; lastBeCulled = beCulled;
        lastPartTested = partTested; lastPartCulled = partCulled;

        entitiesTested = entitiesCulled = beTested = beCulled = partTested = partCulled = 0;
        ShadowCulling.endFrame();
        Impostors.endFrame();
    }

    public static void beginTerrainPass() {
        if (ShadowCulling.isShadowPass()) {
            ShadowCulling.terrainTested = 0;
            ShadowCulling.terrainCulled = 0;
        } else {
            terrainTested = 0;
            terrainCulled = 0;
        }
    }

    public static void onToggled() {
        EmbeddiumCompat.scheduleTerrainUpdate();
    }

    public static boolean isBoxOccluded(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        DepthData d = frameData;
        if (d == null || !Settings.occlusion) return false;
        return d.isOccluded(minX, minY, minZ, maxX, maxY, maxZ, frameShift, Settings.depthBias, Settings.parallax);
    }

    public static int classifySection(int sx, int sy, int sz, boolean sortable) {
        if (!activeThisFrame || !inMainPass) return ShadowCulling.KEEP;
        if (ShadowCulling.isShadowPass()) {
            if (!Settings.shadowOptimizations) return ShadowCulling.KEEP;
            ShadowCulling.terrainTested++;
            int r = ShadowCulling.classifyTerrain(sx, sy, sz, sortable);
            if (r != ShadowCulling.KEEP) ShadowCulling.terrainCulled++;
            return r;
        }
        if (!shouldCullSection(sx, sy, sz)) return ShadowCulling.KEEP;
        return Settings.pruneTraversal ? ShadowCulling.HIDE_PRUNE : ShadowCulling.HIDE;
    }

    private static boolean shouldCullSection(int sx, int sy, int sz) {
        if (frameData == null || !Settings.cullTerrain) return false;
        int r = Settings.safeSectionRadius;
        int cx = ((int) Math.floor(camX)) >> 4, cy = ((int) Math.floor(camY)) >> 4, cz = ((int) Math.floor(camZ)) >> 4;
        if (r > 0 && Math.abs(sx - cx) <= r && Math.abs(sy - cy) <= r && Math.abs(sz - cz) <= r) return false;
        if (sx == cx && sy == cy && sz == cz) return false;
        terrainTested++;
        double ox = sx << 4, oy = sy << 4, oz = sz << 4;

        boolean occ = isBoxOccluded(ox, oy, oz, ox + 16, oy + 16, oz + 16);
        if (occ) terrainCulled++;
        return occ;
    }

    public static double camX() { return camX; }
    public static double camY() { return camY; }
    public static double camZ() { return camZ; }

    private RenderManager() {}
}
