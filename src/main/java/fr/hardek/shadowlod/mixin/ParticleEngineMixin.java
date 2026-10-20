package fr.hardek.shadowlod.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.hardek.shadowlod.core.ObjectCulling;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"),
            require = 0)
    private void shadowlod$particle(Particle particle, VertexConsumer consumer, Camera camera, float partialTick) {
        if (ObjectCulling.shouldCullParticle(particle)) return;
        particle.render(consumer, camera, partialTick);
    }
}
