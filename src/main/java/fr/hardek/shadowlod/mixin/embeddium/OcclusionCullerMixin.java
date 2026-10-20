package fr.hardek.shadowlod.mixin.embeddium;

import fr.hardek.shadowlod.compat.EmbeddiumCompat;
import fr.hardek.shadowlod.compat.ShadowCulling;
import fr.hardek.shadowlod.core.RenderManager;
import fr.hardek.shadowlod.core.Settings;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkUpdateType;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OcclusionCuller.class, remap = false)
public abstract class OcclusionCullerMixin {
    @Unique
    private static boolean shadowlod$lastOccluded;

    @Shadow
    private static boolean isSectionVisible(RenderSection section, Viewport viewport, float maxDistance) {
        throw new AssertionError();
    }

    @Inject(method = "findVisible", at = @At("HEAD"), require = 0)
    private void shadowlod$begin(OcclusionCuller.Visitor visitor, Viewport viewport, float searchDistance,
                               boolean useOcclusionCulling, int frame, CallbackInfo ci) {
        RenderManager.beginTerrainPass();
    }

    @Redirect(method = "processQueue", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lme/jellysquid/mods/sodium/client/render/chunk/occlusion/OcclusionCuller;isSectionVisible(Lme/jellysquid/mods/sodium/client/render/chunk/RenderSection;Lme/jellysquid/mods/sodium/client/render/viewport/Viewport;F)Z"))
    private static boolean shadowlod$isVisible(RenderSection section, Viewport viewport, float maxDistance) {
        boolean visible = isSectionVisible(section, viewport, maxDistance);
        if (!visible) {
            shadowlod$lastOccluded = false;
            return false;
        }
        int r = RenderManager.classifySection(section.getChunkX(), section.getChunkY(), section.getChunkZ(),
                EmbeddiumCompat.isSortable(section));
        shadowlod$lastOccluded = r != ShadowCulling.KEEP;
        if (r == ShadowCulling.HIDE_PRUNE) return false;
        return true;
    }

    @Redirect(method = "processQueue", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lme/jellysquid/mods/sodium/client/render/chunk/occlusion/OcclusionCuller$Visitor;visit(Lme/jellysquid/mods/sodium/client/render/chunk/RenderSection;Z)V"))
    private static void shadowlod$visit(OcclusionCuller.Visitor visitor, RenderSection section, boolean visible) {
        if (!shadowlod$lastOccluded) {
            visitor.visit(section, visible);
            return;
        }
        shadowlod$lastOccluded = false;
        if (Settings.deferRebuilds && visitor instanceof VisibleChunkCollector) {
            ChunkUpdateType t = section.getPendingUpdate();
            boolean important = t == ChunkUpdateType.IMPORTANT_REBUILD || t == ChunkUpdateType.IMPORTANT_SORT;
            boolean initial = t == ChunkUpdateType.INITIAL_BUILD;
            if (!important && (!initial || Settings.deferInitialBuilds)) {
                return;
            }
        }
        visitor.visit(section, false);
    }
}
