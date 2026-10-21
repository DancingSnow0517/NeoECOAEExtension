package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.networking.IManagedGridNode;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LargeWorkstationCoolantInputTest {
    private ECOLargeIntegratedWorkingStationInputHatchBlockEntity hatch;
    private RecipeManager recipes;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        var registry = (MappedRegistry<RecipeType<?>>) BuiltInRegistries.RECIPE_TYPE;
        if (registry.containsKey(NERecipeTypes.COOLING.getId())) return;
        registry.unfreeze();
        try {
            Registry.register(registry, NERecipeTypes.COOLING.getId(), new RecipeType<CoolingRecipe>() {});
        } finally {
            registry.freeze();
        }
    }

    @BeforeEach
    void setup() {
        var type = mock(BlockEntityType.class);
        when(type.isValid(any())).thenReturn(true);
        hatch = new ECOLargeIntegratedWorkingStationInputHatchBlockEntity(
            type, BlockPos.ZERO, Blocks.AIR.defaultBlockState()) {
            @Override
            protected IManagedGridNode createMainNode() {
                return mock(IManagedGridNode.class, RETURNS_SELF);
            }

            @Override
            public void setChanged() {}

            @Override
            public void markForUpdate() {}
        };
        var level = mock(Level.class);
        recipes = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(recipes);
        hatch.setLevel(level);
        when(recipes.getAllRecipesFor(NERecipeTypes.COOLING.get())).thenReturn(List.of(cooling(Fluids.WATER)));
    }

    @Test
    void rejectsNonCoolantDuringSimulationAndExecution() {
        var lava = new FluidStack(Fluids.LAVA, 1000);
        assertFalse(hatch.tank.isFluidValid(0, lava));
        assertEquals(0, hatch.tank.fill(lava, FluidAction.SIMULATE));
        assertEquals(0, hatch.tank.fill(lava, FluidAction.EXECUTE));
        assertTrue(hatch.tank.isEmpty());
    }

    @Test
    void acceptsCoolantBelowTheRecipeBatchAmount() {
        var water = new FluidStack(Fluids.WATER, 1);
        assertTrue(hatch.tank.isFluidValid(0, water));
        assertEquals(1, hatch.tank.fill(water, FluidAction.SIMULATE));
        assertTrue(hatch.tank.isEmpty());
        assertEquals(1, hatch.tank.fill(water, FluidAction.EXECUTE));
        assertEquals(1, hatch.tank.getFluidAmount());
    }

    @Test
    void automaticTransferDoesNotDrainRejectedFluid() {
        var source = new FluidTank(1000);
        source.setFluid(new FluidStack(Fluids.LAVA, 1000));
        assertTrue(FluidUtil.tryFluidTransfer(hatch.tank, source, 1000, true).isEmpty());
        assertEquals(1000, source.getFluidAmount());
        assertTrue(hatch.tank.isEmpty());

        source.setFluid(new FluidStack(Fluids.WATER, 1000));
        assertEquals(1000, FluidUtil.tryFluidTransfer(hatch.tank, source, 1000, true).getAmount());
        assertTrue(source.isEmpty());
        assertEquals(1000, hatch.tank.getFluidAmount());
    }

    @Test
    void recipeReloadChangesAcceptedCoolantsAndStillAllowsDrainingOldContents() {
        hatch.tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        when(recipes.getAllRecipesFor(NERecipeTypes.COOLING.get())).thenReturn(List.of(cooling(Fluids.LAVA)));
        assertEquals(0, hatch.tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE));
        assertEquals(1000, hatch.tank.drain(1000, FluidAction.EXECUTE).getAmount());
        assertEquals(1000, hatch.tank.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE));
    }

    @Test
    void emptyRecipeListAndDetachedHatchRejectInput() {
        when(recipes.getAllRecipesFor(NERecipeTypes.COOLING.get())).thenReturn(List.of());
        assertEquals(0, hatch.tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE));
        hatch.setLevel(null);
        assertFalse(hatch.tank.isFluidValid(0, FluidStack.EMPTY));
        assertEquals(0, hatch.tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE));
    }

    private static CoolingRecipe cooling(net.minecraft.world.level.material.Fluid fluid) {
        var recipe = new CoolingRecipe(SizedFluidIngredient.of(fluid, 100), FluidStack.EMPTY, 1500, 2);
        return cn.dancingsnow.neoecoae.util.RecipeTestSupport.withId(new ResourceLocation("test:coolant"), recipe);
    }
}
