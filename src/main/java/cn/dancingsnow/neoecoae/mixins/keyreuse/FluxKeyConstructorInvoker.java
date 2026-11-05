package cn.dancingsnow.neoecoae.mixins.keyreuse;

import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "com.glodblock.github.appflux.common.me.key.FluxKey", remap = false)
public interface FluxKeyConstructorInvoker {
    @Invoker("<init>")
    static FluxKey neoecoae$construct(EnergyType type) {
        throw new AssertionError("Mixin constructor invoker was not applied");
    }
}
