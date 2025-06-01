package cn.dancingsnow.neoecoae.mixins;

import cn.dancingsnow.neoecoae.client.all.NEBuiltinModels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

@Mixin(ModelBakery.class)
public class ModelBakeryMixin {
    @Inject(at = @At("HEAD"), method = "getModel", cancellable = true)
    private void getModelHook(ResourceLocation id, CallbackInfoReturnable<UnbakedModel> cir) {
        var model = NEBuiltinModels.getUnbakedModel(id);

        if (model != null) {
            cir.setReturnValue(model);
        }
    }
}
