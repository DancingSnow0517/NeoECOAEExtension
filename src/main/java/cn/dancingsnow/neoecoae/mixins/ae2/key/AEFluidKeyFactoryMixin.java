package cn.dancingsnow.neoecoae.mixins.ae2.key;

import appeng.api.stacks.AEFluidKey;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.FluidKeyComponents;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** AE2 already normalizes fluid amounts to one; only empty component patches are shared. */
@Mixin(value = AEFluidKey.class, remap = false)
public abstract class AEFluidKeyFactoryMixin implements FluidKeyComponents {
    @Shadow @Final private FluidStack stack;

    @Override public boolean neoecoae$hasComponentPatch() { return !stack.isComponentsPatchEmpty(); }

    @WrapMethod(method = "of(Lnet/neoforged/neoforge/fluids/FluidStack;)Lappeng/api/stacks/AEFluidKey;")
    private static AEFluidKey neoecoae$stack(FluidStack stack, Operation<AEFluidKey> original) {
        Object epoch = KeyReuse.epoch();
        if (epoch == null || stack.isEmpty() || !stack.isComponentsPatchEmpty()) return original.call(stack);
        AEFluidKey cached = ECOKeyFactory.cachedFluid(epoch, stack.getFluid());
        return cached != null ? cached : ECOKeyFactory.publishFluid(epoch, original.call(stack));
    }

    @WrapMethod(method = "of(Lnet/minecraft/world/level/material/Fluid;)Lappeng/api/stacks/AEFluidKey;")
    private static AEFluidKey neoecoae$fluid(Fluid fluid, Operation<AEFluidKey> original) {
        AEFluidKey cached = ECOKeyFactory.cachedFluid(KeyReuse.epoch(), fluid);
        return cached != null ? cached : original.call(fluid);
    }
}
