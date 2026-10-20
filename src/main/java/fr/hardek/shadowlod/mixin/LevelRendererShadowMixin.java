package fr.hardek.shadowlod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.hardek.shadowlod.compat.ShadowCulling;
import fr.hardek.shadowlod.compat.ShadowCache;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 1500)
public abstract class LevelRendererShadowMixin {
    @Inject(method = "setupRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void shadowlod$skipShadowSetup(Camera camera, Frustum frustum, boolean hasCapturedFrustum, boolean spectator, CallbackInfo ci) {
        if (ShadowCulling.isShadowPass() && ShadowCache.mode() == ShadowCache.Mode.USE) {
            ci.cancel();
        }
    }

    @Inject(method = "renderChunkLayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void shadowlod$shadowLayerHead(RenderType type, PoseStack poseStack, double x, double y, double z, Matrix4f projection, CallbackInfo ci) {
        if (!ShadowCulling.isShadowPass()) return;
        if (type == RenderType.solid()) {
            if (ShadowCache.beforeOpaque()) {
                ShadowCulling.cacheRestoredThisFrame = true;
                ci.cancel();
            }
        } else if ((type == RenderType.cutout() || type == RenderType.cutoutMipped()) && ShadowCulling.cacheRestoredThisFrame) {
            ci.cancel();
        }
    }

    @Inject(method = "renderChunkLayer", at = @At("RETURN"), require = 0)
    private void shadowlod$shadowLayerTail(RenderType type, PoseStack poseStack, double x, double y, double z, Matrix4f projection, CallbackInfo ci) {
        if (ShadowCulling.isShadowPass() && type == RenderType.cutoutMipped()) {
            ShadowCache.afterOpaque();
        }
    }
}
