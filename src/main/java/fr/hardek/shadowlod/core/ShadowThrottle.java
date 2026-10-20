package fr.hardek.shadowlod.core;

public final class ShadowThrottle {
    private static long lastRenderFrame = -1000;
    private static double lx, ly, lz;
    private static float lastSun = -1f;
    public static int skipped, rendered;
    public static int lastSkipped, lastRendered;
    private static long statFrame;

    public static boolean shouldSkip() {
        long f = RenderManager.frameIndex();
        if (f - statFrame >= 60) {
            lastSkipped = skipped; lastRendered = rendered;
            skipped = rendered = 0;
            statFrame = f;
        }
        if (!Settings.enabled || !Settings.runtimeToggle || Settings.shadowUpdateInterval <= 1) {
            mark(f);
            return false;
        }
        int interval = Settings.shadowUpdateInterval;
        if (FrameGovernor.inSpike()) interval++;
        double dx = RenderManager.camX() - lx, dy = RenderManager.camY() - ly, dz = RenderManager.camZ() - lz;
        double max = Settings.shadowSkipMaxMove;
        float sun = RenderManager.sunAngle();
        boolean moved = dx * dx + dy * dy + dz * dz > max * max || Math.abs(sun - lastSun) > 0.002f;
        if (!moved && f - lastRenderFrame < interval) {
            skipped++;
            return true;
        }
        mark(f);
        return false;
    }

    private static void mark(long f) {
        lastRenderFrame = f;
        lx = RenderManager.camX(); ly = RenderManager.camY(); lz = RenderManager.camZ();
        lastSun = RenderManager.sunAngle();
        rendered++;
    }

    private ShadowThrottle() {}
}
