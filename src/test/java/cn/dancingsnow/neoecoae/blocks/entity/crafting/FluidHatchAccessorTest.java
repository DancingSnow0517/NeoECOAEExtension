package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FluidHatchAccessorTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void inputHatchAccessorsExposeTheFieldBuffer() {
        var hatch = mock(ECOFluidInputHatchBlockEntity.class, CALLS_REAL_METHODS);
        hatch.tank = new FluidTank(16000);
        hatch.tank.setFluid(new FluidStack(Fluids.WATER, 12345));
        assertSame(hatch.tank, hatch.getFluidTankHandler());
        assertEquals(12345L, hatch.getStoredFluidAmount());
    }

    @Test
    void outputHatchAccessorExposesTheFieldBuffer() {
        var hatch = mock(ECOFluidOutputHatchBlockEntity.class, CALLS_REAL_METHODS);
        hatch.tank = new FluidTank(16000);
        assertSame(hatch.tank, hatch.getFluidTankHandler());
    }
}
