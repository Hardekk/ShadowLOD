package fr.hardek.shadowlod.mixin.oculus;

import fr.hardek.shadowlod.compat.ShaderProfile;
import fr.hardek.shadowlod.compat.ShadowCache;
import fr.hardek.shadowlod.core.Settings;
import fr.hardek.shadowlod.core.ShadowThrottle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.shadows.ShadowRenderer", remap = false)
public abstract class ShadowRendererMixin {
    @Inject(method = "renderShadows", at = @At("HEAD"), cancellable = true, require = 0)
    private void shadowlod$begin(CallbackInfo ci) {
        ShaderProfile.onShadowRenderer(this);
        if (Settings.shadowOptimizations && ShaderProfile.fullShadowOpts() && ShadowThrottle.shouldSkip()) {
            ci.cancel();
            return;
        }
        ShadowCache.decide(this);
    }

    @ModifyVariable(method = "createShadowModelView", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private static float shadowlod$grid(float interval) {
        if (!Settings.enabled || !Settings.runtimeToggle || !Settings.shadowOptimizations
                || !Settings.shadowCache || !ShaderProfile.fullShadowOpts() || Settings.shadowGridSize <= 0) return interval;
        return Math.max(interval, Settings.shadowGridSize);
    }

    @Inject(method = "getShadowAngle", at = @At("RETURN"), cancellable = true, require = 0)
    private static void shadowlod$quantizeSun(CallbackInfoReturnable<Float> cir) {
        if (!Settings.enabled || !Settings.runtimeToggle || !Settings.shadowOptimizations
                || !Settings.shadowCache || !ShaderProfile.fullShadowOpts()) return;
        float step = Settings.sunStepDegrees / 360f;
        if (step <= 0f) return;
        float a = cir.getReturnValueF();
        cir.setReturnValue((float) (Math.floor(a / step + 0.5) * step));
    }
}
