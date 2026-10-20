package fr.hardek.shadowlod.mixin.embeddium;

import fr.hardek.shadowlod.core.FrameGovernor;
import fr.hardek.shadowlod.core.RenderManager;
import fr.hardek.shadowlod.core.Settings;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkJobResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedDeque;

@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class RenderSectionManagerMixin {
    @Shadow @Final private ConcurrentLinkedDeque<ChunkJobResult<ChunkBuildOutput>> buildResults;

    @Redirect(method = "uploadChunks", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lme/jellysquid/mods/sodium/client/render/chunk/RenderSectionManager;collectChunkBuildResults()Ljava/util/ArrayList;"))
    private ArrayList<ChunkBuildOutput> shadowlod$limitedCollect(RenderSectionManager self) {
        int budget = FrameGovernor.uploadBudget();
        ArrayList<ChunkBuildOutput> results = new ArrayList<>();
        ChunkJobResult<ChunkBuildOutput> result;
        while (results.size() < budget && (result = this.buildResults.poll()) != null) {
            ChunkBuildOutput out = result.unwrap();
            if (out.info != null) fr.hardek.shadowlod.compat.ShadowCache.markDirty();
            results.add(out);
        }
        return results;
    }

    @Inject(method = "onSectionRemoved", at = @At("HEAD"), require = 0)
    private void shadowlod$sectionRemoved(int x, int y, int z, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        fr.hardek.shadowlod.compat.ShadowCache.markDirty();
    }

    @Inject(method = "createSortTask", at = @At("HEAD"), cancellable = true, require = 0)
    private void shadowlod$sortDistance(RenderSection section, int frame, CallbackInfoReturnable<?> cir) {
        if (!Settings.enabled || !Settings.runtimeToggle || Settings.translucentSortDistance <= 0) return;
        double dx = (section.getChunkX() << 4) + 8 - RenderManager.camX();
        double dy = (section.getChunkY() << 4) + 8 - RenderManager.camY();
        double dz = (section.getChunkZ() << 4) + 8 - RenderManager.camZ();
        double d = Settings.translucentSortDistance;
        if (dx * dx + dy * dy + dz * dz > d * d) cir.setReturnValue(null);
    }

    @Redirect(method = "updateChunks", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lme/jellysquid/mods/sodium/client/render/chunk/compile/executor/ChunkBuilder;getSchedulingBudget()I"))
    private int shadowlod$schedulingBudget(ChunkBuilder builder) {
        return FrameGovernor.schedulingBudget(builder.getSchedulingBudget());
    }
}
