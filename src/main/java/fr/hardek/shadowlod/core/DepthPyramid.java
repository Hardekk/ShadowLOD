package fr.hardek.shadowlod.core;

import fr.hardek.shadowlod.ShadowLOD;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_PACK_BUFFER;
import static org.lwjgl.opengl.GL21C.GL_PIXEL_PACK_BUFFER_BINDING;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL32C.*;

public final class DepthPyramid {
    private static final int RING = 3;
    private static final int MAX_READBACK_WIDTH = 320;

    private static final String VS = """
            #version 330 core
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static final String FS = """
            #version 330 core
            uniform sampler2D uSrc;
            uniform ivec2 uSrcSize;
            uniform int uFirst;
            layout(location = 0) out vec2 oDepth;
            void main() {
                ivec2 base = ivec2(gl_FragCoord.xy) * 4;
                ivec2 lim = uSrcSize - 1;
                float mx = 0.0;
                float mn = 1.0;
                for (int y = 0; y < 4; y++) {
                    for (int x = 0; x < 4; x++) {
                        vec4 t = texelFetch(uSrc, min(base + ivec2(x, y), lim), 0);
                        mx = max(mx, t.r);
                        mn = min(mn, uFirst == 1 ? t.r : t.g);
                    }
                }
                oDepth = vec2(mx, mn);
            }
            """;

    private boolean failed;
    private int program, vao, uSrc, uSrcSize, uFirst;

    private int srcW = -1, srcH = -1;
    private int passes;
    private int[] tex = new int[0], fbo = new int[0], lw = new int[0], lh = new int[0];
    private int texelPx0;

    private final int[] pbo = new int[RING];
    private final long[] fence = new long[RING];
    private final DepthData[] pending = new DepthData[RING];
    private final long[] pendingSerial = new long[RING];
    private int writeSlot;
    private long serial;
    private float[] scratch = new float[0];

    private DepthData published;
    private DepthData spare;

    public boolean isFailed() { return failed; }
    public DepthData latest() { return published; }

    private void initGl() {
        try {
            int vs = compile(GL_VERTEX_SHADER, VS);
            int fs = compile(GL_FRAGMENT_SHADER, FS);
            program = glCreateProgram();
            glAttachShader(program, vs);
            glAttachShader(program, fs);
            glLinkProgram(program);
            glDeleteShader(vs);
            glDeleteShader(fs);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                throw new IllegalStateException("link: " + glGetProgramInfoLog(program));
            }
            uSrc = glGetUniformLocation(program, "uSrc");
            uSrcSize = glGetUniformLocation(program, "uSrcSize");
            uFirst = glGetUniformLocation(program, "uFirst");
            vao = glGenVertexArrays();
            for (int i = 0; i < RING; i++) pbo[i] = glGenBuffers();
            ShadowLOD.LOG.info("[ShadowLOD] depth pyramid ready");
        } catch (Throwable t) {
            failed = true;
            ShadowLOD.LOG.error("[ShadowLOD] shader setup failed, culling disabled", t);
        }
    }

    private static int compile(int type, String src) {
        int s = glCreateShader(type);
        glShaderSource(s, src);
        glCompileShader(s);
        if (glGetShaderi(s, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(s);
            glDeleteShader(s);
            throw new IllegalStateException("shader: " + log);
        }
        return s;
    }

    private void resize(int w, int h) {
        for (int t : tex) glDeleteTextures(t);
        for (int f : fbo) glDeleteFramebuffers(f);
        for (int i = 0; i < RING; i++) dropSlot(i);

        int n = 0, cw = w;
        do { cw = (cw + 3) / 4; n++; } while (cw > MAX_READBACK_WIDTH);
        passes = n;
        tex = new int[n]; fbo = new int[n]; lw = new int[n]; lh = new int[n];
        int pw = w, ph = h;
        int px = 1;
        for (int i = 0; i < n; i++) {
            pw = (pw + 3) / 4; ph = (ph + 3) / 4; px *= 4;
            lw[i] = pw; lh[i] = ph;
            tex[i] = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, tex[i]);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RG32F, pw, ph, 0, GL_RG, GL_FLOAT, (ByteBuffer) null);
            fbo[i] = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, fbo[i]);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex[i], 0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("incomplete depth framebuffer");
            }
        }
        texelPx0 = px;
        int bytes = lw[n - 1] * lh[n - 1] * 8;
        for (int i = 0; i < RING; i++) {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo[i]);
            glBufferData(GL_PIXEL_PACK_BUFFER, bytes, GL_STREAM_READ);
        }
        srcW = w; srcH = h;
        published = null;
        spare = null;
    }

    private void dropSlot(int i) {
        if (fence[i] != 0L) { glDeleteSync(fence[i]); fence[i] = 0L; }
        pending[i] = null;
    }

    public void poll() {
        if (failed || program == 0) return;
        DepthData newest = null;
        long newestSerial = -1;
        int prevPack = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        try {
            for (int i = 0; i < RING; i++) {
                if (fence[i] == 0L) continue;
                int r = glClientWaitSync(fence[i], 0, 0L);
                if (r != GL_ALREADY_SIGNALED && r != GL_CONDITION_SATISFIED) continue;
                glDeleteSync(fence[i]);
                fence[i] = 0L;
                DepthData d = pending[i];
                pending[i] = null;
                if (d == null || pendingSerial[i] < newestSerial) continue;

                int w = lw[passes - 1], h = lh[passes - 1];
                int floats = w * h * 2;
                glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo[i]);
                ByteBuffer mapped = glMapBufferRange(GL_PIXEL_PACK_BUFFER, 0, (long) floats * 4, GL_MAP_READ_BIT);
                if (mapped == null) continue;
                if (scratch.length < floats) scratch = new float[floats];
                FloatBuffer fb = mapped.asFloatBuffer();
                fb.get(scratch, 0, floats);
                glUnmapBuffer(GL_PIXEL_PACK_BUFFER);

                DepthData target = (spare != null && spare != published) ? spare : new DepthData();
                copyCamera(d, target);
                target.buildFromReadback(scratch, w, h);
                newest = target;
                newestSerial = pendingSerial[i];
            }
        } finally {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, prevPack);
        }
        if (newest != null) {
            spare = published;
            published = newest;
        }
    }

    private static void copyCamera(DepthData s, DepthData d) {
        d.camX = s.camX; d.camY = s.camY; d.camZ = s.camZ;
        d.m00 = s.m00; d.m01 = s.m01; d.m02 = s.m02; d.m03 = s.m03;
        d.m10 = s.m10; d.m11 = s.m11; d.m12 = s.m12; d.m13 = s.m13;
        d.m20 = s.m20; d.m21 = s.m21; d.m22 = s.m22; d.m23 = s.m23;
        d.m30 = s.m30; d.m31 = s.m31; d.m32 = s.m32; d.m33 = s.m33;
        d.linA = s.linA; d.linB = s.linB; d.scaleX = s.scaleX; d.scaleY = s.scaleY;
        d.screenW = s.screenW; d.screenH = s.screenH; d.texelPx0 = s.texelPx0;
        d.frameIndex = s.frameIndex;
    }

    public void build(int depthTex, int w, int h, double camX, double camY, double camZ,
                      Matrix4f proj, Matrix4f view, long frameIndex) {
        if (failed) return;
        if (program == 0) {
            initGl();
            if (failed) return;
        }
        if (depthTex <= 0 || w <= 0 || h <= 0) return;

        int slot = writeSlot;
        if (fence[slot] != 0L) return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            int prevDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            int prevRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            IntBuffer vp = stack.mallocInt(4);
            glGetIntegerv(GL_VIEWPORT, vp);
            int prevProgram = glGetInteger(GL_CURRENT_PROGRAM);
            int prevVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
            int prevActive = glGetInteger(GL_ACTIVE_TEXTURE);
            glActiveTexture(GL_TEXTURE0);
            int prevTex = glGetInteger(GL_TEXTURE_BINDING_2D);
            int prevPack = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
            boolean depthTest = glIsEnabled(GL_DEPTH_TEST);
            boolean blend = glIsEnabled(GL_BLEND);
            boolean cull = glIsEnabled(GL_CULL_FACE);
            boolean scissor = glIsEnabled(GL_SCISSOR_TEST);
            boolean stencil = glIsEnabled(GL_STENCIL_TEST);
            ByteBuffer cmask = stack.malloc(4);
            glGetBooleanv(GL_COLOR_WRITEMASK, cmask);
            boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);

            try {
                if (w != srcW || h != srcH) resize(w, h);

                glDisable(GL_DEPTH_TEST);
                glDisable(GL_BLEND);
                glDisable(GL_CULL_FACE);
                glDisable(GL_SCISSOR_TEST);
                glDisable(GL_STENCIL_TEST);
                glColorMask(true, true, true, true);
                glDepthMask(false);

                glUseProgram(program);
                glUniform1i(uSrc, 0);
                glBindVertexArray(vao);

                glBindTexture(GL_TEXTURE_2D, depthTex);
                int prevCompare = glGetTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL14C.GL_TEXTURE_COMPARE_MODE);
                if (prevCompare != GL_NONE) glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL14C.GL_TEXTURE_COMPARE_MODE, GL_NONE);
                int curSrc = depthTex, cw = w, ch = h;
                for (int i = 0; i < passes; i++) {
                    glBindFramebuffer(GL_FRAMEBUFFER, fbo[i]);
                    glViewport(0, 0, lw[i], lh[i]);
                    glBindTexture(GL_TEXTURE_2D, curSrc);
                    glUniform2i(uSrcSize, cw, ch);
                    glUniform1i(uFirst, i == 0 ? 1 : 0);
                    glDrawArrays(GL_TRIANGLES, 0, 3);
                    curSrc = tex[i]; cw = lw[i]; ch = lh[i];
                }
                if (prevCompare != GL_NONE) {
                    glBindTexture(GL_TEXTURE_2D, depthTex);
                    glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL14C.GL_TEXTURE_COMPARE_MODE, prevCompare);
                }

                int last = passes - 1;
                glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo[last]);
                glReadBuffer(GL_COLOR_ATTACHMENT0);
                glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo[slot]);
                glReadPixels(0, 0, lw[last], lh[last], GL_RG, GL_FLOAT, 0L);
                fence[slot] = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);

                DepthData meta = pending[slot] != null ? pending[slot] : new DepthData();
                meta.setCamera(camX, camY, camZ, proj, view);
                meta.screenW = w; meta.screenH = h; meta.texelPx0 = texelPx0;
                meta.frameIndex = frameIndex;
                pending[slot] = meta;
                pendingSerial[slot] = ++serial;
                writeSlot = (slot + 1) % RING;
            } catch (Throwable t) {
                failed = true;
                ShadowLOD.LOG.error("[ShadowLOD] depth pyramid failed, culling disabled", t);
            } finally {
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, prevDraw);
                glBindFramebuffer(GL_READ_FRAMEBUFFER, prevRead);
                glViewport(vp.get(0), vp.get(1), vp.get(2), vp.get(3));
                glUseProgram(prevProgram);
                glBindVertexArray(prevVao);
                glBindTexture(GL_TEXTURE_2D, prevTex);
                glActiveTexture(prevActive);
                glBindBuffer(GL_PIXEL_PACK_BUFFER, prevPack);
                setCap(GL_DEPTH_TEST, depthTest);
                setCap(GL_BLEND, blend);
                setCap(GL_CULL_FACE, cull);
                setCap(GL_SCISSOR_TEST, scissor);
                setCap(GL_STENCIL_TEST, stencil);
                glColorMask(cmask.get(0) != 0, cmask.get(1) != 0, cmask.get(2) != 0, cmask.get(3) != 0);
                glDepthMask(depthMask);
            }
        }
    }

    private static void setCap(int cap, boolean on) {
        if (on) glEnable(cap); else glDisable(cap);
    }

    public void invalidate() {
        published = null;
        spare = null;
        if (program == 0 || failed) return;
        for (int i = 0; i < RING; i++) dropSlot(i);
    }
}
