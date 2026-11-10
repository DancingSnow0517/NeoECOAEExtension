package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGrid;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LargeWorkstationCoolantInputTest {
    private ECOLargeIntegratedWorkingStationInputHatchBlockEntity hatch;
    private RecipeManager recipes;
    private MEStorage network;
    private IManagedGridNode smartNode;
    private ECOLargeIntegratedWorkingStationBlockEntity controller;

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

    @Test
    void smartModuleRequiresAnExplicitValidMarkAndNeverScansNetworkStock() {
        var smart = smartHatch();
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        verifyNoInteractions(network);
        smart.setMarkedCoolant(new FluidStack(Fluids.LAVA, 1000));
        assertTrue(smart.getMarkedCoolant().isEmpty());
        var selected = new FluidStack(Fluids.WATER, 1000);
        smart.setMarkedCoolant(selected);
        selected.setAmount(0);
        assertEquals(1, smart.getMarkedCoolant().getAmount());
        smart.getMarkedCoolant().setAmount(0);
        assertFalse(smart.getMarkedCoolant().isEmpty());
        when(smart.getLevel().getGameTime()).thenReturn(200L);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        assertEquals(16_384_000, smart.tank.getFluidAmount());
        assertEquals(1_024_000, hatch.tank.getCapacity());
        verify(network, never()).getAvailableStacks();
        verify(network).extract(eq(AEFluidKey.of(Fluids.WATER)), eq(16_384_000L), eq(Actionable.MODULATE), any());
    }

    @Test
    void smartModuleOnlyRefillsTheDeficitEveryTwoHundredTicks() {
        var smart = smartHatch();
        smart.setMarkedCoolant(new FluidStack(Fluids.WATER, 1));
        when(smart.getLevel().getGameTime()).thenReturn(7L);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        smart.tank.drain(60_000, FluidAction.EXECUTE);
        clearInvocations(network);
        for (long time = 8; time < 207; time++) {
            when(smart.getLevel().getGameTime()).thenReturn(time);
            smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        }
        verifyNoInteractions(network);
        assertEquals(16_324_000, smart.tank.getFluidAmount());
        when(smart.getLevel().getGameTime()).thenReturn(207L);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        assertEquals(16_384_000, smart.tank.getFluidAmount());
        verify(network).extract(eq(AEFluidKey.of(Fluids.WATER)), eq(60_000L), eq(Actionable.MODULATE), any());
        clearInvocations(network);
        when(smart.getLevel().getGameTime()).thenReturn(407L);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        verifyNoInteractions(network);
    }

    @Test
    void changingTheMarkReturnsOldFluidBeforePullingAndPreservesRejectedReturns() {
        var smart = smartHatch();
        when(recipes.getAllRecipesFor(NERecipeTypes.COOLING.get())).thenReturn(List.of(cooling(Fluids.WATER), cooling(Fluids.LAVA)));
        smart.tank.fill(new FluidStack(Fluids.WATER, 90_000), FluidAction.EXECUTE);
        smart.setMarkedCoolant(new FluidStack(Fluids.LAVA, 1));
        when(network.insert(any(), anyLong(), any(), any())).thenReturn(4000L);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        assertEquals(86_000, smart.tank.getFluidAmount());
        assertEquals(Fluids.WATER, smart.tank.getFluid().getFluid());
        verify(network, never()).extract(any(), anyLong(), any(), any());
        when(network.insert(any(), anyLong(), any(), any())).thenReturn(0L);
        when(smart.getLevel().getGameTime()).thenReturn(200L);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        assertEquals(86_000, smart.tank.getFluidAmount());
        when(network.insert(any(), anyLong(), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        clearInvocations(network);
        when(smart.getLevel().getGameTime()).thenReturn(400L);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        var order = inOrder(network);
        order.verify(network).insert(eq(AEFluidKey.of(Fluids.WATER)), eq(86_000L), eq(Actionable.MODULATE), any());
        order.verify(network).extract(eq(AEFluidKey.of(Fluids.LAVA)), eq(16_384_000L), eq(Actionable.SIMULATE), any());
        assertEquals(16_384_000, smart.tank.getFluidAmount());
        assertEquals(Fluids.LAVA, smart.tank.getFluid().getFluid());
    }

    @Test
    void clearingMarkReturnsStockEvenWhenActiveCoolingIsDisabled() {
        var smart = smartHatch();
        smart.tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        smart.setMarkedCoolant(new FluidStack(Fluids.WATER, 1));
        smart.setMarkedCoolant(FluidStack.EMPTY);
        when(controller.isActiveCooling()).thenReturn(false);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        assertTrue(smart.tank.isEmpty());
        verify(network).insert(eq(AEFluidKey.of(Fluids.WATER)), eq(1000L), eq(Actionable.MODULATE), any());
        verify(network, never()).extract(any(), anyLong(), any(), any());
    }

    @Test
    void unmarkedManualCoolantStaysAvailableAndChangingTheMarkCannotReturnFreshInletFluid() throws Exception {
        var smart = smartHatch();
        smart.tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
        assertTrue(smart.acceptsManualCoolant(new FluidStack(Fluids.WATER, 1)));
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        assertEquals(1000, smart.tank.getFluidAmount());
        verifyNoInteractions(network);

        var saved = new CompoundTag();
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        try (var visuals = mockStatic(appeng.hooks.VisualStateSaving.class)) {
            smart.saveAdditional(saved, registries);
            smart.loadTag(saved, registries);
        }
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        assertEquals(1000, smart.tank.getFluidAmount());
        smart.setMarkedCoolant(new FluidStack(Fluids.WATER, 1));
        assertFalse(smart.acceptsManualCoolant(new FluidStack(Fluids.LAVA, 1)));
        smart.setMarkedCoolant(FluidStack.EMPTY);
        assertFalse(smart.acceptsManualCoolant(new FluidStack(Fluids.WATER, 1)));
        when(smart.getLevel().getGameTime()).thenReturn(200L);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        assertTrue(smart.tank.isEmpty());
        assertTrue(smart.acceptsManualCoolant(new FluidStack(Fluids.WATER, 1)));
        verify(network).insert(eq(AEFluidKey.of(Fluids.WATER)), eq(1000L), eq(Actionable.MODULATE), any());
    }

    @Test
    void offlineNetworkAndStoppedCoolingKeepStockWithoutExtracting() {
        var smart = smartHatch();
        smart.tank.fill(new FluidStack(Fluids.WATER, 40_000), FluidAction.EXECUTE);
        smart.setMarkedCoolant(new FluidStack(Fluids.WATER, 1));
        when(smartNode.isActive()).thenReturn(false);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        when(smartNode.isActive()).thenReturn(true);
        when(controller.isActiveCooling()).thenReturn(false);
        when(smart.getLevel().getGameTime()).thenReturn(200L);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        assertEquals(40_000, smart.tank.getFluidAmount());
        verifyNoInteractions(network);
        when(controller.isActiveCooling()).thenReturn(true);
        when(smart.getLevel().getGameTime()).thenReturn(400L);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        assertEquals(16_384_000, smart.tank.getFluidAmount());
    }

    @Test
    void overheatNeverOverridesTheMarkAndRecipeReloadRejectsRemovedCoolants() {
        var smart = smartHatch();
        smart.setMarkedCoolant(new FluidStack(Fluids.WATER, 1));
        when(controller.isOverheated()).thenReturn(true);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        assertEquals(Fluids.WATER, smart.getMarkedCoolant().getFluid());
        assertEquals(Fluids.WATER, smart.tank.getFluid().getFluid());
        smart.tank.drain(1000, FluidAction.EXECUTE);
        when(recipes.getAllRecipesFor(NERecipeTypes.COOLING.get())).thenReturn(List.of());
        clearInvocations(network);
        when(smart.getLevel().getGameTime()).thenReturn(200L);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        verifyNoInteractions(network);
        assertEquals(16_383_000, smart.tank.getFluidAmount());
    }

    @Test
    void partialExtractionAndMissingExtractionPowerNeverCreateFluid() {
        var smart = smartHatch();
        smart.setMarkedCoolant(new FluidStack(Fluids.WATER, 1));
        when(network.extract(any(), anyLong(), any(), any())).thenReturn(12_345L);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        assertEquals(12_345, smart.tank.getFluidAmount());
        var energy = smartNode.getGrid().getEnergyService();
        when(energy.extractAEPower(anyDouble(), any(), any())).thenReturn(0.0);
        clearInvocations(network);
        when(smart.getLevel().getGameTime()).thenReturn(200L);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        assertEquals(12_345, smart.tank.getFluidAmount());
        verify(network, never()).extract(any(), anyLong(), eq(Actionable.MODULATE), any());
    }

    @Test
    void savesMarkAndLargeBufferAndLoadsLegacyStockWithoutInferringAMark() {
        try (var visuals = mockStatic(appeng.hooks.VisualStateSaving.class)) {
            var smart = smartHatch();
            smart.setMarkedCoolant(new FluidStack(Fluids.WATER, 1000));
            smart.setTargetTemperature(80);
            smart.tank.fill(new FluidStack(Fluids.WATER, 16_000_000), FluidAction.EXECUTE);
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            var saved = new CompoundTag();
            smart.saveAdditional(saved, registries);
            var restored = smartHatch();
            restored.loadTag(saved, registries);
            assertEquals(80, restored.getTargetTemperature());
            assertEquals(1, restored.getMarkedCoolant().getAmount());
            assertEquals(Fluids.WATER, restored.getMarkedCoolant().getFluid());
            assertEquals(16_000_000, restored.tank.getFluidAmount());
            saved.remove("markedCoolant");
            saved.remove("returnUnmarkedCoolant");
            restored.loadTag(saved, registries);
            assertTrue(restored.getMarkedCoolant().isEmpty());
            assertEquals(16_000_000, restored.tank.getFluidAmount());
            restored.tick(restored.getLevel(), BlockPos.ZERO, restored.getBlockState());
            assertTrue(restored.tank.isEmpty());
            verify(network, never()).extract(any(), anyLong(), any(), any());
        }
    }

    private ECOLargeIntegratedWorkingStationInputHatchBlockEntity smartHatch() {
        var type = mock(BlockEntityType.class);
        when(type.isValid(any())).thenReturn(true);
        smartNode = mock(IManagedGridNode.class, RETURNS_SELF);
        var smart = new ECOLargeIntegratedWorkingStationInputHatchBlockEntity(type, BlockPos.ZERO, Blocks.AIR.defaultBlockState()) {
            @Override public boolean isSmartCoolingModule() { return true; }
            @Override protected IManagedGridNode createMainNode() { return smartNode; }
            @Override public void setChanged() {}
            @Override public void markForUpdate() {}
        };
        smart.setLevel(hatch.getLevel());
        controller = mock(ECOLargeIntegratedWorkingStationBlockEntity.class);
        when(controller.isActiveCooling()).thenReturn(true);
        smart.cluster = mock(cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster.class);
        when(smart.cluster.getController()).thenReturn(controller);
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        when(smartNode.isActive()).thenReturn(true);
        when(smartNode.getGrid()).thenReturn(grid);
        network = mock(MEStorage.class);
        when(grid.getStorageService().getInventory()).thenReturn(network);
        when(grid.getEnergyService().extractAEPower(anyDouble(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(network.extract(any(), anyLong(), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(network.insert(any(), anyLong(), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        return smart;
    }

    private static RecipeHolder<CoolingRecipe> cooling(net.minecraft.world.level.material.Fluid fluid) {
        var recipe = new CoolingRecipe(SizedFluidIngredient.of(fluid, 100), FluidStack.EMPTY, 1500, 2);
        return new RecipeHolder<>(ResourceLocation.parse("test:coolant"), recipe);
    }
}
