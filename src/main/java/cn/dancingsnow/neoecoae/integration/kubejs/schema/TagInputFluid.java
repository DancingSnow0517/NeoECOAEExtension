package cn.dancingsnow.neoecoae.integration.kubejs.schema;

import dev.latvian.mods.kubejs.fluid.FluidLike;
import dev.latvian.mods.kubejs.fluid.FluidStackJS;
import dev.latvian.mods.kubejs.fluid.InputFluid;
import net.minecraft.resources.ResourceLocation;

public record TagInputFluid(ResourceLocation tag, long amount) implements InputFluid {
    @Override
    public long kjs$getAmount() {
        return amount;
    }

    @Override
    public boolean kjs$isEmpty() {
        return amount <= 0L || tag == null;
    }

    @Override
    public FluidLike kjs$copy(long newAmount) {
        return new TagInputFluid(tag, newAmount);
    }

    @Override
    public boolean matches(FluidLike other) {
        return other instanceof FluidStackJS stack && stack.hasTag(tag);
    }
}
