package fr.hardek.shadowlod.core;

public final class AutoTune {
    public static float scale = 1f;
    private static int counter;

    public static void update() {
        if (!Settings.autoTune || !Settings.enabled || !Settings.runtimeToggle || Settings.targetFps <= 0) {
            scale = 1f;
            return;
        }
        if (!net.minecraft.client.Minecraft.getInstance().isWindowActive()) {
            counter = 0;
            return;
        }
        if (++counter < 20) return;
        counter = 0;
        double target = 1000.0 / Settings.targetFps;
        double avg = FrameGovernor.averageMs();
        if (avg > target * 1.06) {
            scale = Math.max((float) Settings.autoTuneMinScale, scale - 0.05f);
        } else if (avg < target * 1.02) {
            scale = Math.min(1f, scale + 0.025f);
        }
    }

    private AutoTune() {}
}
