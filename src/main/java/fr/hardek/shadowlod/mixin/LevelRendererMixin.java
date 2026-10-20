package fr.hardek.shadowlod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.hardek.shadowlod.core.RenderManager;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void shadowlod$begin(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline,
                               Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projection,
                               CallbackInfo ci) {
        RenderManager.beginFrame(poseStack.last().pose(), poseStack.last().normal(), partialTick, projection, camera);
    }

    @Inject(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V",
                    ordinal = 2, shift = At.Shift.AFTER),
            require = 0)
    private void shadowlod$afterOpaque(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline,
                                     Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projection,
                                     CallbackInfo ci) {
        RenderManager.afterOpaqueTerrain();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void shadowlod$end(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline,
                             Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projection,
                             CallbackInfo ci) {
        RenderManager.endFrame();
    }
}
