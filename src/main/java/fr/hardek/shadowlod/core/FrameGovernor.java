package fr.hardek.shadowlod.core;

public final class FrameGovernor {
    private static long lastNanos;
    private static double emaMs = 16.0;
    private static int cooldown;
    public static double lastFrameMs;
    public static int spikes;

    public static void onFrame() {
        long now = System.nanoTime();
        if (lastNanos != 0L) {
            double ms = (now - lastNanos) / 1_000_000.0;
            lastFrameMs = ms;
            if (ms < 250.0) {
                boolean spike = ms > emaMs * Settings.spikeFactor && ms > Settings.spikeMinMs;
                if (spike) {
                    cooldown = Settings.spikeCooldownFrames;
                    spikes++;
                } else if (cooldown > 0) {
                    cooldown--;
                }
                emaMs += (ms - emaMs) * 0.05;
            }
        }
        lastNanos = now;
    }

    public static boolean inSpike() {
        return cooldown > 0;
    }

    public static double averageMs() {
        return emaMs;
    }

    public static int uploadBudget() {
        if (!Settings.spikeGovernor || !Settings.enabled || !Settings.runtimeToggle) return Integer.MAX_VALUE;
        int b = inSpike() ? Settings.spikeUploadsPerFrame : Settings.maxUploadsPerFrame;
        return b <= 0 ? Integer.MAX_VALUE : b;
    }

    public static int schedulingBudget(int original) {
        if (!Settings.spikeGovernor || !Settings.enabled || !Settings.runtimeToggle || !inSpike()) return original;
        return Math.min(original, Settings.spikeSchedulingBudget);
    }

    private FrameGovernor() {}
}
