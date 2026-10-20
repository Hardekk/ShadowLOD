package fr.hardek.shadowlod.mixin;

import fr.hardek.shadowlod.compat.ShadowCulling;
import fr.hardek.shadowlod.core.Impostors;
import fr.hardek.shadowlod.core.RenderManager;
import fr.hardek.shadowlod.core.Settings;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityDistanceMixin {
    @Inject(method = "shouldRenderAtSqrDistance", at = @At("HEAD"), cancellable = true, require = 0)
    private void shadowlod$impostorRange(double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (!Impostors.active() || Settings.maxEntityDistanceSq <= 0 || !RenderManager.isRunning() || ShadowCulling.isShadowPass()) return;
        Object self = this;
        if (!(self instanceof LivingEntity) || self instanceof Player) return;
        if (!((Entity) self).level().isClientSide) return;
        if (distanceSq <= Settings.maxEntityDistanceSq) cir.setReturnValue(true);
    }
}
