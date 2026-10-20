package fr.hardek.shadowlod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.hardek.shadowlod.core.ObjectCulling;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void shadowlod$occlusion(E blockEntity, float partialTick, PoseStack poseStack,
                                                         MultiBufferSource buffers, CallbackInfo ci) {
        if (ObjectCulling.shouldCullBlockEntity(blockEntity)) {
            ci.cancel();
        }
    }
}
