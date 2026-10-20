package fr.hardek.shadowlod.core;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import fr.hardek.shadowlod.ShadowLOD;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.*;

public final class Impostors {
    public static final ResourceLocation ATLAS = new ResourceLocation("shadowlod", "impostor_atlas");
    private static final int ATLAS_SIZE = 2048;

    private static boolean failed;
    private static int fbo, colorTex, depthRb;
    private static int tileSize, tilesPerRow, tileCount;

    private static final Int2IntOpenHashMap tileOf = new Int2IntOpenHashMap();
    private static int[] tileOwner = new int[0];
    private static long[] tileUsed = new long[0];
    private static long[] tileCaptured = new long[0];
    private static float[] tileYaw = new float[0], tilePitch = new float[0], tileSize2 = new float[0];
    private static boolean[] tileReady = new boolean[0];
    private static long[] tileDrawn = new long[0];

    private static final List<Entity> pending = new ArrayList<>();
    private static MultiBufferSource.BufferSource captureBuffers;

    public static int drawn, captured, lastDrawn, lastCaptured;

    static {
        tileOf.defaultReturnValue(-1);
    }

    private static final class AtlasTexture extends AbstractTexture {
        AtlasTexture(int glId) {
            this.id = glId;
        }

        @Override
        public void load(ResourceManager manager) {
        }

        @Override
        public void releaseId() {
        }
    }

    private static boolean ensure() {
        if (failed) return false;
        int wanted = Settings.impostorResolution;
        if (fbo != 0 && tileSize == wanted) return true;
        try {
            if (fbo != 0) {
                glDeleteFramebuffers(fbo);
                glDeleteTextures(colorTex);
                glDeleteRenderbuffers(depthRb);
            }
            tileSize = wanted;
            tilesPerRow = ATLAS_SIZE / tileSize;
            tileCount = tilesPerRow * tilesPerRow;
            tileOwner = new int[tileCount];
            tileUsed = new long[tileCount];
            tileCaptured = new long[tileCount];
            tileYaw = new float[tileCount];
            tilePitch = new float[tileCount];
            tileSize2 = new float[tileCount];
            tileReady = new boolean[tileCount];
            tileDrawn = new long[tileCount];
            java.util.Arrays.fill(tileOwner, Integer.MIN_VALUE);
            tileOf.clear();

            int prevTex = glGetInteger(GL_TEXTURE_BINDING_2D);
            colorTex = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, colorTex);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, ATLAS_SIZE, ATLAS_SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glBindTexture(GL_TEXTURE_2D, prevTex);

            int prevRb = glGetInteger(GL_RENDERBUFFER_BINDING);
            depthRb = glGenRenderbuffers();
            glBindRenderbuffer(GL_RENDERBUFFER, depthRb);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, ATLAS_SIZE, ATLAS_SIZE);
            glBindRenderbuffer(GL_RENDERBUFFER, prevRb);

            int prevFb = glGetInteger(GL_FRAMEBUFFER_BINDING);
            fbo = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTex, 0);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthRb);
            int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
            glBindFramebuffer(GL_FRAMEBUFFER, prevFb);
            if (status != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("impostor framebuffer incomplete");

            Minecraft.getInstance().getTextureManager().register(ATLAS, new AtlasTexture(colorTex));
            captureBuffers = MultiBufferSource.immediate(new BufferBuilder(4096));
            return true;
        } catch (Throwable t) {
            failed = true;
            ShadowLOD.LOG.error("[ShadowLOD] impostors disabled", t);
            return false;
        }
    }

    public static boolean active() {
        return Settings.impostors && !failed;
    }

    public static boolean tryDraw(Entity e, double camX, double camY, double camZ) {
        if (!Settings.impostors || failed) return false;
        try {
            return draw(e, camX, camY, camZ);
        } catch (Throwable t) {
            failed = true;
            pending.clear();
            ShadowLOD.LOG.error("[ShadowLOD] impostors disabled", t);
            return false;
        }
    }

    private static boolean draw(Entity e, double camX, double camY, double camZ) {
        if (e.isVehicle() || e.isPassenger()) return false;
        if (!ensure()) return false;
        float pt = RenderManager.partialTick();
        Vec3 pos = e.getPosition(pt);
        double cx = pos.x, cy = pos.y + e.getBbHeight() * 0.5, cz = pos.z;
        float yaw = RenderManager.yaw(), pitch = RenderManager.pitch();
        long frame = RenderManager.frameIndex();

        int tile = tileOf.get(e.getId());
        boolean needCapture;
        if (tile < 0) {
            tile = allocate(e.getId(), frame);
            if (tile < 0) return false;
            needCapture = true;
        } else {
            float dy = Math.abs(wrap(yaw - tileYaw[tile]));
            float dp = Math.abs(pitch - tilePitch[tile]);
            double age = (frame - tileCaptured[tile]) / Math.max(1.0, 1000.0 / Math.max(1.0, FrameGovernor.averageMs()));
            needCapture = dy > Settings.impostorMaxAngle || dp > Settings.impostorMaxAngle || age > Settings.impostorRefreshSeconds;
        }
        tileUsed[tile] = frame;
        if (needCapture && !pending.contains(e)) pending.add(e);
        if (!tileReady[tile]) return false;
        if (tileDrawn[tile] == frame) return true;
        tileDrawn[tile] = frame;

        float size = tileSize2[tile];
        emitQuad(tile, cx - camX, cy - camY, cz - camZ, size, Minecraft.getInstance().getEntityRenderDispatcher().getPackedLightCoords(e, pt));
        drawn++;
        return true;
    }

    private static float wrap(float a) {
        a %= 360f;
        if (a > 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }

    private static int allocate(int id, long frame) {
        int best = -1;
        long oldest = Long.MAX_VALUE;
        for (int i = 0; i < tileCount; i++) {
            if (tileOwner[i] == Integer.MIN_VALUE) {
                best = i;
                break;
            }
            if (tileUsed[i] < oldest && frame - tileUsed[i] > 2) {
                oldest = tileUsed[i];
                best = i;
            }
        }
        if (best < 0) return -1;
        if (tileOwner[best] != Integer.MIN_VALUE) tileOf.remove(tileOwner[best]);
        tileOwner[best] = id;
        tileOf.put(id, best);
        tileReady[best] = false;
        tileUsed[best] = frame;
        return best;
    }

    private static void emitQuad(int tile, double x, double y, double z, float size, int light) {
        Matrix4f view = RenderManager.viewMatrix();
        Matrix3f normal = RenderManager.normalMatrix();
        float rx = view.m00(), ry = view.m10(), rz = view.m20();
        float ux = view.m01(), uy = view.m11(), uz = view.m21();
        float fx = view.m02(), fy = view.m12() + 0.6f, fz = view.m22();
        float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        float h = size * 0.5f;

        float inset = 1.0f / ATLAS_SIZE;
        float u0 = (tile % tilesPerRow) * (float) tileSize / ATLAS_SIZE + inset;
        float v0 = (tile / tilesPerRow) * (float) tileSize / ATLAS_SIZE + inset;
        float u1 = u0 + (float) tileSize / ATLAS_SIZE - 2 * inset;
        float v1 = v0 + (float) tileSize / ATLAS_SIZE - 2 * inset;

        VertexConsumer vc = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.entityCutoutNoCull(ATLAS));
        float px = (float) x, py = (float) y, pz = (float) z;
        vertex(vc, view, normal, px - rx * h - ux * h, py - ry * h - uy * h, pz - rz * h - uz * h, u0, v0, light, fx, fy, fz);
        vertex(vc, view, normal, px + rx * h - ux * h, py + ry * h - uy * h, pz + rz * h - uz * h, u1, v0, light, fx, fy, fz);
        vertex(vc, view, normal, px + rx * h + ux * h, py + ry * h + uy * h, pz + rz * h + uz * h, u1, v1, light, fx, fy, fz);
        vertex(vc, view, normal, px - rx * h + ux * h, py - ry * h + uy * h, pz - rz * h + uz * h, u0, v1, light, fx, fy, fz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f view, Matrix3f normal, float x, float y, float z, float u, float v,
                               int light, float nx, float ny, float nz) {
        vc.vertex(view, x, y, z).color(1f, 1f, 1f, 1f).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normal, nx, ny, nz).endVertex();
    }

    public static void processCaptures() {
        if (pending.isEmpty() || failed || fbo == 0) {
            pending.clear();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            pending.clear();
            return;
        }
        int budget = Settings.impostorCapturesPerFrame;
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        RenderSystem.backupProjectionMatrix();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        int prevFb = glGetInteger(GL_FRAMEBUFFER_BINDING);
        try {
            dispatcher.setRenderShadow(false);
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            float pt = RenderManager.partialTick();
            for (int i = 0; i < pending.size() && budget > 0; i++) {
                Entity e = pending.get(i);
                int tile = tileOf.get(e.getId());
                if (tile < 0 || e.isRemoved() || e.level() != mc.level) continue;
                capture(e, tile, pt, dispatcher);
                budget--;
                captured++;
            }
        } catch (Throwable t) {
            failed = true;
            ShadowLOD.LOG.error("[ShadowLOD] impostors disabled", t);
        } finally {
            pending.clear();
            dispatcher.setRenderShadow(true);
            glDisable(GL_SCISSOR_TEST);
            glBindFramebuffer(GL_FRAMEBUFFER, prevFb);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    private static void capture(Entity e, int tile, float pt, EntityRenderDispatcher dispatcher) {
        float size = Math.max(e.getBbWidth() * 1.8f, e.getBbHeight() * 1.25f) + 0.5f;
        int tx = (tile % tilesPerRow) * tileSize, ty = (tile / tilesPerRow) * tileSize;
        glViewport(tx, ty, tileSize, tileSize);
        glEnable(GL_SCISSOR_TEST);
        glScissor(tx, ty, tileSize, tileSize);
        RenderSystem.clearColor(0f, 0f, 0f, 0f);
        RenderSystem.clear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        float h = size * 0.5f;
        Matrix4f proj = new Matrix4f().setOrtho(-h, h, -h, h, -64f, 64f);
        RenderSystem.setProjectionMatrix(proj, VertexSorting.ORTHOGRAPHIC_Z);

        float yaw = RenderManager.yaw(), pitch = RenderManager.pitch();
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw + 180f));
        Vec3 p = e.getPosition(pt);
        double cy = e.getBbHeight() * 0.5;
        RenderSystem.setShaderLights(new Vector3f(0.3f, 0.5f, 1f).normalize(), new Vector3f(-0.3f, 0.5f, 1f).normalize());
        Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
        float k = Settings.impostorBrightness;
        RenderSystem.setShaderColor(k, k, k, 1f);
        try {
            dispatcher.render(e, 0, -cy, 0, e.getViewYRot(pt), pt, pose, captureBuffers, LightTexture.FULL_BRIGHT);
            captureBuffers.endBatch();
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        com.mojang.blaze3d.platform.Lighting.setupLevel(new Matrix4f());

        tileCaptured[tile] = RenderManager.frameIndex();
        tileYaw[tile] = yaw;
        tilePitch[tile] = pitch;
        tileSize2[tile] = size;
        tileReady[tile] = true;
    }

    public static void endFrame() {
        lastDrawn = drawn;
        lastCaptured = captured;
        drawn = 0;
        captured = 0;
    }

    public static void reset() {
        tileOf.clear();
        java.util.Arrays.fill(tileOwner, Integer.MIN_VALUE);
        java.util.Arrays.fill(tileReady, false);
        pending.clear();
    }

    private Impostors() {}
}
