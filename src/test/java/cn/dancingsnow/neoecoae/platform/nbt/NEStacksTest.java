package cn.dancingsnow.neoecoae.platform.nbt;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class NEStacksTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1000})
    void emptyFluidCannotBeResurrectedByChangingItsAmount(int amount) {
        assertSame(FluidStack.EMPTY, NEStacks.withAmount(FluidStack.EMPTY, amount));
        var drained = new FluidStack(Fluids.WATER, 0);
        assertSame(FluidStack.EMPTY, NEStacks.withAmount(drained, amount));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void nonPositiveAmountsReturnEmptyWithoutModifyingTheSource(int amount) {
        var source = new FluidStack(Fluids.WATER, 1000);
        assertSame(FluidStack.EMPTY, NEStacks.withAmount(source, amount));
        assertEquals(1000, source.getAmount());
    }

    @Test
    void positiveAmountCopiesFluidAndTagWithoutAliasingTheSource() {
        var tag = new CompoundTag();
        tag.putString("batch", "original");
        var source = new FluidStack(Fluids.WATER, 1000, tag);
        var copy = NEStacks.withAmount(source, 375);
        assertSame(Fluids.WATER, copy.getFluid());
        assertEquals(375, copy.getAmount());
        assertEquals(tag, copy.getTag());
        copy.getTag().putString("batch", "changed");
        assertEquals("original", source.getTag().getString("batch"));
        assertEquals(1000, source.getAmount());
    }
}
