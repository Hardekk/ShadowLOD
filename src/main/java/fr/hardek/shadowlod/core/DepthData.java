package fr.hardek.shadowlod.core;

import org.joml.Matrix4f;

public final class DepthData {
    public double camX, camY, camZ;

    float m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23, m30, m31, m32, m33;

    float linA, linB;

    float scaleX, scaleY;

    public int screenW, screenH;

    public int texelPx0;
    int levels;
    int[] widths, heights;
    float[][] maxD, minD;

    public long frameIndex;

    void setCamera(double x, double y, double z, Matrix4f proj, Matrix4f view) {
        camX = x; camY = y; camZ = z;
        Matrix4f vp = new Matrix4f(proj).mul(view);
        m00 = vp.m00(); m01 = vp.m01(); m02 = vp.m02(); m03 = vp.m03();
        m10 = vp.m10(); m11 = vp.m11(); m12 = vp.m12(); m13 = vp.m13();
        m20 = vp.m20(); m21 = vp.m21(); m22 = vp.m22(); m23 = vp.m23();
        m30 = vp.m30(); m31 = vp.m31(); m32 = vp.m32(); m33 = vp.m33();
        float p23 = proj.m23();
        if (Math.abs(p23) < 1e-6f) p23 = -1f;
        linA = -proj.m22() / p23;
        linB = proj.m32() + linA * proj.m33();
        scaleX = Math.abs(proj.m00());
        scaleY = Math.abs(proj.m11());
    }

    void buildFromReadback(float[] rg, int w0, int h0) {
        int lv = 1;
        int w = w0, h = h0;
        while (w > 1 || h > 1) { w = (w + 1) >> 1; h = (h + 1) >> 1; lv++; }
        if (widths == null || widths.length != lv || widths[0] != w0 || heights[0] != h0) {
            levels = lv;
            widths = new int[lv]; heights = new int[lv];
            maxD = new float[lv][]; minD = new float[lv][];
            w = w0; h = h0;
            for (int i = 0; i < lv; i++) {
                widths[i] = w; heights[i] = h;
                maxD[i] = new float[w * h]; minD[i] = new float[w * h];
                w = (w + 1) >> 1; h = (h + 1) >> 1;
            }
        }
        float[] mx = maxD[0], mn = minD[0];
        for (int i = 0, n = w0 * h0; i < n; i++) {
            mx[i] = rg[i * 2];
            mn[i] = rg[i * 2 + 1];
        }
        for (int l = 1; l < levels; l++) {
            int pw = widths[l - 1], ph = heights[l - 1];
            int cw = widths[l], ch = heights[l];
            float[] pmx = maxD[l - 1], pmn = minD[l - 1];
            float[] cmx = maxD[l], cmn = minD[l];
            for (int y = 0; y < ch; y++) {
                int y0 = y * 2, y1 = Math.min(y0 + 1, ph - 1);
                for (int x = 0; x < cw; x++) {
                    int x0 = x * 2, x1 = Math.min(x0 + 1, pw - 1);
                    int a = y0 * pw + x0, b = y0 * pw + x1, c = y1 * pw + x0, d = y1 * pw + x1;
                    cmx[y * cw + x] = Math.max(Math.max(pmx[a], pmx[b]), Math.max(pmx[c], pmx[d]));
                    cmn[y * cw + x] = Math.min(Math.min(pmn[a], pmn[b]), Math.min(pmn[c], pmn[d]));
                }
            }
        }
    }

    private float linearize(float depth01) {
        if (depth01 >= 0.9999999f) return Float.POSITIVE_INFINITY;
        float ndc = depth01 * 2f - 1f;
        float den = ndc + linA;
        if (Math.abs(den) < 1e-9f) return Float.POSITIVE_INFINITY;
        float w = linB / den;
        return w > 0f ? w : Float.POSITIVE_INFINITY;
    }

    public float centerDistance() {
        if (widths == null) return -1f;
        int w = widths[0], h = heights[0];
        return linearize(maxD[0][(h / 2) * w + w / 2]);
    }

    public float skyFraction() {
        if (widths == null) return -1f;
        float[] mx = maxD[0];
        int n = 0;
        for (float v : mx) if (v >= 0.9999999f) n++;
        return n / (float) mx.length;
    }

    private float qMax, qMin;

    private void regionQuery(float px0, float py0, float px1, float py1) {
        int tx0 = (int) (px0 / texelPx0), ty0 = (int) (py0 / texelPx0);
        int tx1 = (int) (px1 / texelPx0), ty1 = (int) (py1 / texelPx0);
        int w = widths[0], h = heights[0];
        if (tx0 < 0) tx0 = 0; if (ty0 < 0) ty0 = 0;
        if (tx1 > w - 1) tx1 = w - 1; if (ty1 > h - 1) ty1 = h - 1;
        int l = 0;
        while ((tx1 - tx0 > 3 || ty1 - ty0 > 3) && l < levels - 1) {
            tx0 >>= 1; ty0 >>= 1; tx1 >>= 1; ty1 >>= 1; l++;
        }
        int lw = widths[l];
        float[] mx = maxD[l], mn = minD[l];
        float m = 0f, n = 1f;
        for (int y = ty0; y <= ty1; y++) {
            int row = y * lw;
            for (int x = tx0; x <= tx1; x++) {
                float a = mx[row + x];
                if (a > m) m = a;
                float b = mn[row + x];
                if (b < n) n = b;
            }
        }
        qMax = m; qMin = n;
    }

    public boolean isOccluded(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                              double shift, float bias, boolean parallax) {
        float x0 = (float) (minX - camX), y0 = (float) (minY - camY), z0 = (float) (minZ - camZ);
        float x1 = (float) (maxX - camX), y1 = (float) (maxY - camY), z1 = (float) (maxZ - camZ);

        float nx0 = Float.POSITIVE_INFINITY, ny0 = Float.POSITIVE_INFINITY;
        float nx1 = Float.NEGATIVE_INFINITY, ny1 = Float.NEGATIVE_INFINITY;
        float minW = Float.POSITIVE_INFINITY;

        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? x0 : x1;
            float y = (i & 2) == 0 ? y0 : y1;
            float z = (i & 4) == 0 ? z0 : z1;
            float cw = m03 * x + m13 * y + m23 * z + m33;
            if (cw < 0.05f) return false;
            float inv = 1f / cw;
            float cx = (m00 * x + m10 * y + m20 * z + m30) * inv;
            float cy = (m01 * x + m11 * y + m21 * z + m31) * inv;
            if (cx < nx0) nx0 = cx; if (cx > nx1) nx1 = cx;
            if (cy < ny0) ny0 = cy; if (cy > ny1) ny1 = cy;
            if (cw < minW) minW = cw;
        }

        if (nx0 < -1f || ny0 < -1f || nx1 > 1f || ny1 > 1f) return false;

        float hw = screenW * 0.5f, hh = screenH * 0.5f;

        if (parallax && shift > 1e-4) {
            regionQuery((nx0 + 1f) * hw, (ny0 + 1f) * hh, (nx1 + 1f) * hw, (ny1 + 1f) * hh);
            float nearOcc = linearize(qMin);
            if (nearOcc < 0.5f) nearOcc = 0.5f;
            float angle = (float) shift / nearOcc;
            float dx = angle * scaleX * 1.5f, dy = angle * scaleY * 1.5f;
            nx0 -= dx; nx1 += dx; ny0 -= dy; ny1 += dy;
            if (nx0 < -1f || ny0 < -1f || nx1 > 1f || ny1 > 1f) return false;
        }

        regionQuery((nx0 + 1f) * hw, (ny0 + 1f) * hh, (nx1 + 1f) * hw, (ny1 + 1f) * hh);
        float occFar = linearize(qMax);
        if (occFar == Float.POSITIVE_INFINITY) return false;
        float margin = parallax ? 0.25f + (float) shift : 0f;
        return minW > occFar * (1f + bias) + margin;
    }
}
