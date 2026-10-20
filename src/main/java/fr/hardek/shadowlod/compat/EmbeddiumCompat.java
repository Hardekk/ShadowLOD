package fr.hardek.shadowlod.compat;

import net.minecraftforge.fml.loading.LoadingModList;

public final class EmbeddiumCompat {
    private static Boolean loaded;

    public static boolean isLoaded() {
        if (loaded == null) {
            LoadingModList list = LoadingModList.get();
            loaded = list != null && list.getModFileById("embeddium") != null;
        }
        return loaded;
    }

    public static void scheduleTerrainUpdate() {
        if (isLoaded()) Impl.schedule();
    }

    private static java.lang.invoke.MethodHandle sortable;
    private static boolean sortableInit;

    private static void initSortable() {
        sortableInit = true;
        try {
            Class<?> c = Class.forName("me.jellysquid.mods.sodium.client.render.chunk.RenderSection");
            for (String name : new String[]{"containsSortableGeometry", "isNeedsDynamicTranslucencySorting"}) {
                try {
                    sortable = java.lang.invoke.MethodHandles.publicLookup().findVirtual(c, name,
                            java.lang.invoke.MethodType.methodType(boolean.class));
                    return;
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        sortable = null;
    }

    public static boolean isSortable(Object section) {
        if (!sortableInit) initSortable();
        if (sortable == null) return true;
        try {
            return (boolean) sortable.invoke(section);
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean canSplitShadowRebuild() {
        if (!isLoaded()) return false;
        if (!sortableInit) initSortable();
        return sortable != null && Impl.sorting();
    }

    private static final class Impl {
        static boolean sorting() {
            try {
                Class<?> c = Class.forName("me.jellysquid.mods.sodium.client.SodiumClientMod");
                return (boolean) c.getMethod("canApplyTranslucencySorting").invoke(null);
            } catch (Throwable t) {
                return false;
            }
        }

        static void schedule() {
            var r = me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer.instanceNullable();
            if (r != null) r.scheduleTerrainUpdate();
        }
    }

    private EmbeddiumCompat() {}
}
