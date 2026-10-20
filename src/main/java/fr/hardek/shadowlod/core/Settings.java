package fr.hardek.shadowlod.core;

import fr.hardek.shadowlod.LodConfig;

public final class Settings {
    public static boolean enabled = true;
    public static boolean runtimeToggle = true;
    public static boolean shadowRuntimeToggle = true;
    public static boolean impostors = true;
    public static double impostorDistanceSq = 32 * 32, impostorRefreshSeconds = 1.0;
    public static int impostorResolution = 128, impostorCapturesPerFrame = 6;
    public static float impostorMaxAngle = 20f, impostorBrightness = 1.0f;
    public static boolean occlusion = false;
    public static boolean cullTerrain = true;
    public static boolean cullEntities = true;
    public static boolean cullPlayers = false;
    public static boolean cullBlockEntities = true;
    public static boolean deferRebuilds = true;
    public static boolean disableWithShaders = false;
    public static double safeDistanceSq = 16.0;
    public static int safeSectionRadius = 1;
    public static double maxCameraShift = 1.0;
    public static float depthBias = 0.02f;
    public static boolean debugInfo = true;
    public static boolean pruneTraversal = false;
    public static boolean deferInitialBuilds = false;
    public static boolean parallax = true;
    public static boolean cullParticles = true;
    public static boolean ignoreNoCulling = true;
    public static boolean shadowOptimizations = true;
    public static double shadowTerrainDistance = 0, shadowEntityDistance = 24, shadowBlockEntityDistance = 16, shadowCastLength = 48;
    public static float shadowViewMargin = 0.1f;
    public static int shadowTerrainPercent = 100;
    public static double maxEntityDistanceSq = 128 * 128, maxBlockEntityDistanceSq = 32 * 32, maxParticleDistanceSq = 40 * 40;
    public static int shadowUpdateInterval = 1;
    public static double shadowSkipMaxMove = 0.5;
    public static boolean spikeGovernor = true;
    public static int maxUploadsPerFrame = 24, spikeUploadsPerFrame = 4, spikeSchedulingBudget = 1, spikeCooldownFrames = 20;
    public static double spikeFactor = 1.5, spikeMinMs = 12;
    public static volatile boolean threadGovernor = true;
    public static volatile String[] lowPriorityThreads = {"chunk render task executor", "dh-", "distant", "lod", "worker-main", "c2me", "worldgen", "noisium", "chunky"};
    private static long refreshCount;
    public static boolean autoShaderProfile = true, shadowCache = true, emfVillagerLod = true, emfTuneConfig = true;
    public static String[] shaderProfileOverrides = new String[0];
    public static int shadowCacheMaxAge = 600, emfLodDistance = 12;
    public static float sunStepDegrees = 0.25f;
    public static int shadowGridSize = 16, shadowRebuildFrames = 4, targetFps = 60;
    public static boolean shadowCacheViewCulling = true, autoTune = true;
    public static float shadowCacheMaxRotation = 10f, shadowCacheViewMargin = 0.6f;
    public static double shadowCacheMaxMove = 2.0, autoTuneMinScale = 0.5, translucentSortDistance = 48.0;

    public static void refresh() {
        try {
            enabled = LodConfig.ENABLED.get();
            occlusion = LodConfig.OCCLUSION.get();
            cullTerrain = LodConfig.CULL_TERRAIN.get();
            cullEntities = LodConfig.CULL_ENTITIES.get();
            cullPlayers = LodConfig.CULL_PLAYERS.get();
            cullBlockEntities = LodConfig.CULL_BLOCK_ENTITIES.get();
            deferRebuilds = LodConfig.DEFER_OCCLUDED_REBUILDS.get();
            disableWithShaders = LodConfig.DISABLE_WITH_SHADERS.get();
            double s = LodConfig.SAFE_DISTANCE.get();
            safeDistanceSq = s * s;
            safeSectionRadius = LodConfig.SAFE_SECTION_RADIUS.get();
            maxCameraShift = LodConfig.MAX_CAMERA_SHIFT.get();
            depthBias = LodConfig.DEPTH_BIAS.get().floatValue();
            debugInfo = LodConfig.DEBUG_INFO.get();
            pruneTraversal = LodConfig.PRUNE_TRAVERSAL.get();
            deferInitialBuilds = LodConfig.DEFER_INITIAL_BUILDS.get();
            parallax = LodConfig.PARALLAX_COMPENSATION.get();
            cullParticles = LodConfig.CULL_PARTICLES.get();
            ignoreNoCulling = LodConfig.IGNORE_NO_CULLING.get();
            shadowOptimizations = LodConfig.SHADOW_OPTIMIZATIONS.get() && shadowRuntimeToggle;
            shadowTerrainDistance = LodConfig.SHADOW_TERRAIN_DISTANCE.get();
            shadowTerrainPercent = LodConfig.SHADOW_TERRAIN_PERCENT.get();
            shadowEntityDistance = LodConfig.SHADOW_ENTITY_DISTANCE.get();
            shadowBlockEntityDistance = LodConfig.SHADOW_BLOCK_ENTITY_DISTANCE.get();
            shadowCastLength = LodConfig.SHADOW_CAST_LENGTH.get();
            shadowViewMargin = LodConfig.SHADOW_VIEW_MARGIN.get().floatValue();
            double tune = AutoTune.scale;
            shadowEntityDistance *= tune;
            shadowBlockEntityDistance *= tune;
            double me = LodConfig.MAX_ENTITY_DISTANCE.get(), mb = LodConfig.MAX_BLOCK_ENTITY_DISTANCE.get() * tune;
            maxEntityDistanceSq = me * me;
            maxBlockEntityDistanceSq = mb * mb;
            double mp = LodConfig.MAX_PARTICLE_DISTANCE.get() * tune;
            maxParticleDistanceSq = mp * mp;
            shadowUpdateInterval = LodConfig.SHADOW_UPDATE_INTERVAL.get();
            shadowSkipMaxMove = LodConfig.SHADOW_SKIP_MAX_MOVE.get();
            spikeGovernor = LodConfig.SPIKE_GOVERNOR.get();
            maxUploadsPerFrame = LodConfig.MAX_UPLOADS_PER_FRAME.get();
            spikeUploadsPerFrame = LodConfig.SPIKE_UPLOADS_PER_FRAME.get();
            spikeSchedulingBudget = LodConfig.SPIKE_SCHEDULING_BUDGET.get();
            spikeFactor = LodConfig.SPIKE_FACTOR.get();
            spikeMinMs = LodConfig.SPIKE_MIN_MS.get();
            spikeCooldownFrames = LodConfig.SPIKE_COOLDOWN_FRAMES.get();
            threadGovernor = LodConfig.THREAD_GOVERNOR.get();
            autoShaderProfile = LodConfig.AUTO_SHADER_PROFILE.get();
            shadowCache = LodConfig.SHADOW_CACHE.get();
            shadowCacheMaxAge = LodConfig.SHADOW_CACHE_MAX_AGE.get();
            sunStepDegrees = LodConfig.SUN_STEP_DEGREES.get().floatValue();
            emfVillagerLod = LodConfig.EMF_VILLAGER_LOD.get();
            emfLodDistance = LodConfig.EMF_LOD_DISTANCE.get();
            emfTuneConfig = LodConfig.EMF_TUNE_CONFIG.get();
            shadowGridSize = LodConfig.SHADOW_GRID_SIZE.get();
            shadowRebuildFrames = LodConfig.SHADOW_REBUILD_FRAMES.get();
            shadowCacheViewCulling = LodConfig.SHADOW_CACHE_VIEW_CULLING.get();
            shadowCacheMaxRotation = LodConfig.SHADOW_CACHE_MAX_ROTATION.get().floatValue();
            shadowCacheMaxMove = LodConfig.SHADOW_CACHE_MAX_MOVE.get();
            shadowCacheViewMargin = LodConfig.SHADOW_CACHE_VIEW_MARGIN.get().floatValue();
            autoTune = LodConfig.AUTO_TUNE.get();
            targetFps = LodConfig.TARGET_FPS.get();
            autoTuneMinScale = LodConfig.AUTO_TUNE_MIN_SCALE.get();
            translucentSortDistance = LodConfig.TRANSLUCENT_SORT_DISTANCE.get();
            impostors = LodConfig.IMPOSTORS.get();
            double id = LodConfig.IMPOSTOR_DISTANCE.get();
            impostorDistanceSq = id * id;
            int res = Integer.highestOneBit(LodConfig.IMPOSTOR_RESOLUTION.get());
            impostorResolution = Math.max(32, Math.min(512, res));
            impostorRefreshSeconds = LodConfig.IMPOSTOR_REFRESH_SECONDS.get();
            impostorMaxAngle = LodConfig.IMPOSTOR_MAX_ANGLE.get().floatValue();
            impostorCapturesPerFrame = LodConfig.IMPOSTOR_CAPTURES_PER_FRAME.get();
            impostorBrightness = LodConfig.IMPOSTOR_BRIGHTNESS.get().floatValue();
            if ((refreshCount++ % 300) == 0) {
                shaderProfileOverrides = LodConfig.SHADER_PROFILE_OVERRIDES.get().toArray(String[]::new);
                lowPriorityThreads = LodConfig.LOW_PRIORITY_THREADS.get().stream()
                        .map(x -> x.toLowerCase(java.util.Locale.ROOT)).toArray(String[]::new);
            }
        } catch (IllegalStateException ignored) {
        }
    }

    private Settings() {}
}
