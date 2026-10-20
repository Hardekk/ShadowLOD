package fr.hardek.shadowlod.compat;

import fr.hardek.shadowlod.ShadowLOD;
import fr.hardek.shadowlod.core.Settings;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class ShaderProfile {
    public enum Profile { FULL, SAFE, OFF }

    private static Object lastRenderer;
    private static Profile current = Profile.FULL;
    private static String reason = "no shader pack";
    private static String packName = "";

    public static void onShadowRenderer(Object renderer) {
        if (renderer == lastRenderer) return;
        lastRenderer = renderer;
        ShadowCache.invalidate();
        detect(renderer);
        ShadowLOD.LOG.info("[ShadowLOD] shader pack '{}', profile {} ({})", packName, current, reason);
    }

    private static void detect(Object r) {
        packName = currentPackName();
        String override = findOverride(packName);
        if (override != null) {
            try {
                current = Profile.valueOf(override.toUpperCase(Locale.ROOT));
                reason = "forced by config";
                return;
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (!Settings.autoShaderProfile) {
            current = Profile.FULL;
            reason = "auto detection off";
            return;
        }
        boolean voxel = readBoolean(r, "packHasVoxelization", false);
        String cull = readEnumName(r, "packCullingState");
        if (voxel) {
            current = Profile.SAFE;
            reason = "voxelization or images in the shadow pass";
        } else if ("REVERSED".equals(cull)) {
            current = Profile.SAFE;
            reason = "reversed shadow culling";
        } else {
            current = Profile.FULL;
            reason = "standard pack";
        }
    }

    private static String findOverride(String pack) {
        String p = pack.toLowerCase(Locale.ROOT);
        for (String entry : Settings.shaderProfileOverrides) {
            int eq = entry.lastIndexOf('=');
            if (eq <= 0) continue;
            String key = entry.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty() && p.contains(key)) return entry.substring(eq + 1).trim();
        }
        return null;
    }

    private static String currentPackName() {
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Method m = iris.getMethod("getCurrentPackName");
            Object o = m.invoke(null);
            return o == null ? "" : o.toString();
        } catch (Throwable t) {
            return "?";
        }
    }

    private static Field field(Object o, String name) throws NoSuchFieldException {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    static Object read(Object o, String name) {
        try {
            return field(o, name).get(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean readBoolean(Object o, String name, boolean def) {
        Object v = read(o, name);
        return v instanceof Boolean b ? b : def;
    }

    private static String readEnumName(Object o, String name) {
        Object v = read(o, name);
        return v instanceof Enum<?> e ? e.name() : "";
    }

    public static Profile get() {
        return lastRenderer == null ? Profile.FULL : current;
    }

    public static boolean fullShadowOpts() {
        return get() == Profile.FULL;
    }

    public static boolean anyShadowOpts() {
        return get() != Profile.OFF;
    }

    public static String describe() {
        if (lastRenderer == null) return "no shadow pass";
        return packName + ", " + current + " (" + reason + ")";
    }

    public static Object renderer() {
        return lastRenderer;
    }

    private ShaderProfile() {}
}
