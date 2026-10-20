package fr.hardek.shadowlod.mixin.emf;

import fr.hardek.shadowlod.compat.EmfCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "traben.entity_model_features.utils.EMFLODHandler", remap = false)
public abstract class EmfLodMixin {
    @Inject(method = "isLODSkippingThisFrame", at = @At("HEAD"), cancellable = true, require = 0)
    private static void shadowlod$lod(String modelId, CallbackInfoReturnable<Boolean> cir) {
        int r = EmfCompat.skipFrame(modelId);
        if (r >= 0) cir.setReturnValue(r == 1);
    }
}
