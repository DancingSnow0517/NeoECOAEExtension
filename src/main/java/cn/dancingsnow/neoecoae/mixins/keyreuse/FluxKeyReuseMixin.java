package cn.dancingsnow.neoecoae.mixins.keyreuse;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOFluxKeys;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** FE interfaces all use this factory, including their simulated transfers and capacity queries. */
@Pseudo
@Mixin(targets = "com.glodblock.github.appflux.common.me.key.FluxKey", remap = false)
public abstract class FluxKeyReuseMixin {
    @Redirect(method = "of", at = @At(value = "NEW", target =
            "(Lcom/glodblock/github/appflux/common/me/key/type/EnergyType;)Lcom/glodblock/github/appflux/common/me/key/FluxKey;"))
    private static FluxKey neoecoae$reuseEnergyKey(EnergyType type) {
        return ECOFluxKeys.getOrCreate(type, FluxKeyConstructorInvoker::neoecoae$construct);
    }
}
