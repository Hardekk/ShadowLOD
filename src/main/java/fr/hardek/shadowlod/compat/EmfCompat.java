package fr.hardek.shadowlod.compat;

import fr.hardek.shadowlod.ShadowLOD;
import fr.hardek.shadowlod.core.RenderManager;
import fr.hardek.shadowlod.core.Settings;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.AbstractVillager;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

public final class EmfCompat {
    private static MethodHandle emfEntity;
    private static boolean init, tuned;

    private static final Object2LongOpenHashMap<String> state = new Object2LongOpenHashMap<>();

    private static void init() {
        init = true;
        try {
            Class<?> c = Class.forName("traben.entity_model_features.models.animation.state.EMFState");
            emfEntity = MethodHandles.publicLookup().unreflect(c.getMethod("emfEntity"));
        } catch (Throwable t) {
            emfEntity = null;
        }
    }

    public static int skipFrame(String modelId) {
        if (!Settings.enabled || !Settings.emfVillagerLod) return -1;
        if (!init) init();
        if (emfEntity == null) return -1;
        Object o;
        try {
            o = emfEntity.invoke();
        } catch (Throwable t) {
            return -1;
        }
        if (!(o instanceof Entity e)) return -1;
        if (!(e instanceof AbstractVillager) && !(e instanceof AbstractHorse)) return -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e.isVehicle() && e.hasPassenger(mc.player)) return 0;

        long frame = RenderManager.frameIndex();
        String key = e.getId() + ":" + modelId;
        long s = state.getOrDefault(key, -1L);
        if (s != -1L && (s >>> 8) == frame) return (int) (s & 0xFF);

        double dist = Math.sqrt(e.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()));
        int factor = (int) (dist / Math.max(1, Settings.emfLodDistance));
        if (factor > 4) factor = 4;
        int result = factor <= 0 ? 0 : ((frame + (e.getId() & 3)) % (factor + 1) == 0 ? 0 : 1);
        state.put(key, (frame << 8) | result);
        if (state.size() > 4096) state.clear();
        return result;
    }

    public static void tuneOnce() {
        if (tuned || !Settings.emfTuneConfig) return;
        tuned = true;
        try {
            Class<?> emf = Class.forName("traben.entity_model_features.EMF");
            Object handler = emf.getMethod("config").invoke(null);
            Object cfg = handler.getClass().getMethod("getConfig").invoke(handler);
            set(cfg, "retainDetailOnLowFps", false);
            set(cfg, "retainDetailOnLargerMobs", false);
            set(cfg, "animationFrameSkipDuringIrisShadowPass", true);
            Field lod = cfg.getClass().getField("animationLODDistance");
            int cur = lod.getInt(cfg);
            if (cur == 0 || cur > Settings.emfLodDistance) lod.setInt(cfg, Settings.emfLodDistance);
            ShadowLOD.LOG.info("[ShadowLOD] EMF options adjusted (LOD distance {})", Settings.emfLodDistance);
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            ShadowLOD.LOG.warn("[ShadowLOD] could not adjust EMF: {}", t.toString());
        }
    }

    private static void set(Object o, String name, boolean v) {
        try {
            o.getClass().getField(name).setBoolean(o, v);
        } catch (Throwable ignored) {
        }
    }

    private EmfCompat() {}
}
