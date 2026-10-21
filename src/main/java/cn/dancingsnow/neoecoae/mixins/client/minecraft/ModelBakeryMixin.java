package cn.dancingsnow.neoecoae.mixins.client.minecraft;

import cn.dancingsnow.neoecoae.client.all.NEBuiltinModels;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBakery.class)
public class ModelBakeryMixin {
    @Shadow
    private void cacheAndQueueDependencies(ResourceLocation id, UnbakedModel model) {
        throw new AssertionError("Mixin shadow");
    }

    @Inject(at = @At("HEAD"), method = "loadModel", cancellable = true)
    private void loadModelHook(ResourceLocation id, CallbackInfo ci) {
        var model = NEBuiltinModels.getUnbakedModel(id);

        if (model != null) {
            cacheAndQueueDependencies(id, model);
            ci.cancel();
        }
    }
}
