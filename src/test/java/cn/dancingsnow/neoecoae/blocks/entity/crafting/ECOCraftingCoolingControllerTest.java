package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ECOCraftingCoolingControllerTest {
    private ECOCraftingSystemBlockEntity host;
    private ECOCraftingCoolingController cooling;
    private FluidTank input;
    private FluidTank output;
    private CoolingRecipe recipe;
    private int amount;
    private int tier;
    private FluidStack fluid;

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @BeforeEach
    void setup() {
        amount = 0;
        tier = -1;
        fluid = FluidStack.EMPTY;
        host = mock(ECOCraftingSystemBlockEntity.class);
        var cluster = mock(NECraftingCluster.class);
        var inputHatch = mock(ECOFluidInputHatchBlockEntity.class);
        var outputHatch = mock(ECOFluidOutputHatchBlockEntity.class);
        input = inputHatch.tank = new FluidTank(16000);
        output = outputHatch.tank = new FluidTank(16000);
        input.setFluid(new FluidStack(Fluids.WATER, 16000));
        when(host.getCluster()).thenReturn(cluster);
        when(cluster.getInputHatch()).thenReturn(inputHatch);
        when(cluster.getOutputHatch()).thenReturn(outputHatch);
        when(host.getCoolant()).thenAnswer(call -> amount);
        when(host.getCoolantMaxOverclock()).thenAnswer(call -> tier);
        when(host.getCurrentCoolantFluid()).thenAnswer(call -> fluid);
        doAnswer(call -> {
            amount = call.getArgument(0);
            tier = call.getArgument(1);
            fluid = call.getArgument(2);
            return null;
        }).when(host).updateLocalCoolantState(anyInt(), anyInt(), any());
        recipe = mock(CoolingRecipe.class);
        when(recipe.inputAmount()).thenReturn(100);
        when(recipe.coolant()).thenReturn(12000);
        when(recipe.maxOverclock()).thenReturn(9);
        when(recipe.output()).thenReturn(FluidStack.EMPTY);
        cooling = spy(new ECOCraftingCoolingController(host));
        doReturn(recipe).when(cooling).getCoolingRecipe();
    }

    @Test
    void refillsWhileIdleWithoutCoresAndWithCoolingDisabled() {
        cooling.tick();
        assertEquals(ECOCraftingSystemBlockEntity.MAX_COOLANT, amount);
        assertEquals(Fluids.WATER, fluid.getFluid());
        assertTrue(input.getFluidAmount() < 16000);
        verify(host, never()).getCapabilitySnapshot();
        int remaining = input.getFluidAmount();
        cooling.tick();
        assertEquals(remaining, input.getFluidAmount());
    }

    @Test
    void refillsAgainAfterInternalCoolantIsConsumed() {
        cooling.tick();
        amount -= 12000;
        int remaining = input.getFluidAmount();
        cooling.tick();
        assertEquals(ECOCraftingSystemBlockEntity.MAX_COOLANT, amount);
        assertEquals(remaining - 100, input.getFluidAmount());
    }

    @Test
    void differentFluidAtSameTierCannotMixUntilInternalBufferIsEmpty() {
        amount = 12000;
        tier = 9;
        fluid = new FluidStack(Fluids.LAVA, 1);
        cooling.tick();
        assertEquals(16000, input.getFluidAmount());
        assertEquals(12000, amount);
        assertEquals(Fluids.LAVA, fluid.getFluid());
        amount = 0;
        cooling.tick();
        assertEquals(ECOCraftingSystemBlockEntity.MAX_COOLANT, amount);
        assertEquals(Fluids.WATER, fluid.getFluid());
    }

    @Test
    void blockedByproductDoesNotDrainInputAndRefillsWhenSpaceReturns() {
        when(recipe.output()).thenReturn(new FluidStack(Fluids.LAVA, 100));
        when(recipe.outputAmount()).thenReturn(100);
        output.setFluid(new FluidStack(Fluids.LAVA, 16000));
        cooling.tick();
        assertEquals(0, amount);
        assertEquals(16000, input.getFluidAmount());
        output.setFluid(FluidStack.EMPTY);
        cooling.tick();
        assertEquals(ECOCraftingSystemBlockEntity.MAX_COOLANT, amount);
        assertEquals(16000 - input.getFluidAmount(), output.getFluidAmount());
    }

    @Test
    void missingCoolingRecipeLeavesInputUntouched() {
        doReturn(null).when(cooling).getCoolingRecipe();
        cooling.tick();
        assertEquals(0, amount);
        assertEquals(16000, input.getFluidAmount());
    }
}
