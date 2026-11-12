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
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.RecipeManager;
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
    private int previousRefillThreshold;
    private ECOLargeIntegratedWorkingStationInputHatchBlockEntity hatch;
    private org.mockito.MockedStatic<appeng.crafting.RecipeAccess> recipeAccess;
    private MEStorage network;
    private IManagedGridNode smartNode;
    private ECOLargeIntegratedWorkingStationBlockEntity controller;

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        var registry = (MappedRegistry<RecipeType<?>>) BuiltInRegistries.RECIPE_TYPE;
        if (registry.containsKey(NERecipeTypes.COOLING.getId())) return;
        registry.unfreeze(false);
        try {
            Registry.register(registry, NERecipeTypes.COOLING.getId(), new RecipeType<CoolingRecipe>() {});
        } finally {
            registry.freeze();
        }
    }

    @BeforeEach
    void setup() {
        previousRefillThreshold = cn.dancingsnow.neoecoae.config.NEConfig.smartCoolingRefillThresholdPercent;
        setupFixture();
    }

    private void setupFixture() {
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
        recipeAccess = mockStatic(appeng.crafting.RecipeAccess.class);
        hatch.setLevel(level);
        recipeAccess.when(() -> appeng.crafting.RecipeAccess.byType(any(), eq(NERecipeTypes.COOLING.get()))).thenReturn(List.of(cooling(Fluids.WATER)));
    }

    @org.junit.jupiter.api.AfterEach
    void closeRecipeAccess() {
        if (recipeAccess != null) recipeAccess.close();
    }

    @org.junit.jupiter.api.AfterEach void restoreRefillThreshold() {
        cn.dancingsnow.neoecoae.config.NEConfig.smartCoolingRefillThresholdPercent = previousRefillThreshold;
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
        recipeAccess.when(() -> appeng.crafting.RecipeAccess.byType(any(), eq(NERecipeTypes.COOLING.get()))).thenReturn(List.of(cooling(Fluids.LAVA)));
        assertEquals(0, hatch.tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE));
        assertEquals(1000, hatch.tank.drain(1000, FluidAction.EXECUTE).getAmount());
        assertEquals(1000, hatch.tank.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE));
    }

    @Test
    void emptyRecipeListAndDetachedHatchRejectInput() {
        recipeAccess.when(() -> appeng.crafting.RecipeAccess.byType(any(), eq(NERecipeTypes.COOLING.get()))).thenReturn(List.of());
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
        cn.dancingsnow.neoecoae.config.NEConfig.smartCoolingRefillThresholdPercent = 100;
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

    @Test void thresholdSkipsSatisfiedStockAndRefillsOnlyTheActualDeficit() {
        cn.dancingsnow.neoecoae.config.NEConfig.smartCoolingRefillThresholdPercent = 75;
        var smart = smartHatch();
        smart.setMarkedCoolant(new FluidStack(Fluids.WATER, 1));
        int threshold = smart.tank.getCapacity() * 3 / 4;
        smart.tank.fill(new FluidStack(Fluids.WATER, threshold), FluidAction.EXECUTE);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        verifyNoInteractions(network);
        smart.tank.drain(1, FluidAction.EXECUTE);
        when(smart.getLevel().getGameTime()).thenReturn(200L);
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        assertEquals(smart.tank.getCapacity(), smart.tank.getFluidAmount());
        verify(network).extract(any(), eq((long) smart.tank.getCapacity() - threshold + 1), eq(Actionable.MODULATE), any());
    }

    @Test
    void changingTheMarkReturnsOldFluidBeforePullingAndPreservesRejectedReturns() {
        var smart = smartHatch();
        recipeAccess.when(() -> appeng.crafting.RecipeAccess.byType(any(), eq(NERecipeTypes.COOLING.get()))).thenReturn(List.of(cooling(Fluids.WATER), cooling(Fluids.LAVA)));
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
            saved.merge(cn.dancingsnow.neoecoae.util.LegacyNbt.output(registries, smart::saveAdditional));
            smart.loadTag(cn.dancingsnow.neoecoae.util.LegacyNbt.input(registries, saved));
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
        recipeAccess.when(() -> appeng.crafting.RecipeAccess.byType(any(), eq(NERecipeTypes.COOLING.get()))).thenReturn(List.of());
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
            saved.merge(cn.dancingsnow.neoecoae.util.LegacyNbt.output(registries, smart::saveAdditional));
            var restored = smartHatch();
            restored.loadTag(cn.dancingsnow.neoecoae.util.LegacyNbt.input(registries, saved));
            assertEquals(80, restored.getTargetTemperature());
            assertEquals(1, restored.getMarkedCoolant().getAmount());
            assertEquals(Fluids.WATER, restored.getMarkedCoolant().getFluid());
            assertEquals(16_000_000, restored.tank.getFluidAmount());
            saved.remove("markedCoolant");
            saved.remove("returnUnmarkedCoolant");
            restored.loadTag(cn.dancingsnow.neoecoae.util.LegacyNbt.input(registries, saved));
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
        var recipe = new CoolingRecipe(SizedFluidIngredient.of(fluid, 100), null, 1500, 2);
        return new RecipeHolder<>(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, Identifier.parse("test:coolant")), recipe);
    }

    /** Offline request-count measurement, reusing the integration fixture and actual hatch/tank methods. */
    public static void main(String[] args) throws Exception {
        bootstrap();
        cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse.configure(true);
        java.lang.reflect.Field setting = null;
        try { setting = cn.dancingsnow.neoecoae.config.NEConfig.class.getField("smartCoolingRefillThresholdPercent"); }
        catch (NoSuchFieldException baseline) { /* The baseline always replenishes below 100%. */ }
        var rows = new com.google.gson.JsonArray();
        coolantTrajectory(setting, 100, 2000, 1024, false); // Shared warmup outside the reported trajectories.
        for (int rate : new int[]{1024, 8192}) {
            for (boolean outage : new boolean[]{false, true}) {
                for (int threshold : setting == null ? new int[]{100} : new int[]{100, 75})
                    rows.add(coolantTrajectory(setting, threshold, 40_000, rate, outage));
            }
        }
        var report = new com.google.gson.JsonObject();
        report.addProperty("kind", "Offline real cooling hatch/tank and saved-data engine; fixture supplies clock, recipes and node");
        report.addProperty("pollIntervalTicks", 200);
        report.add("results", rows);
        java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]),
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static com.google.gson.JsonObject coolantTrajectory(java.lang.reflect.Field setting, int threshold,
            int ticks, int drainPerTick, boolean outage) throws Exception {
        if (setting != null) setting.setInt(null, threshold);
        var fixture = new LargeWorkstationCoolantInputTest();
        fixture.setupFixture();
        var smart = fixture.smartHatch();
        var engine = new cn.dancingsnow.neoecoae.storage.infinite.SavedDataInfiniteStorageEngine(
            cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageData.createNew());
        var key = AEFluidKey.of(Fluids.WATER);
        engine.insert(key, 1_000_000_000L, Actionable.MODULATE);
        long[] now = {0}, requests = {0, 0};
        when(smart.getLevel().getGameTime()).thenAnswer(call -> now[0]);
        when(fixture.network.extract(any(), anyLong(), any(), any())).thenAnswer(call -> {
            Actionable mode = call.getArgument(2);
            requests[mode == Actionable.SIMULATE ? 0 : 1]++;
            if (outage && now[0] >= 10_000 && now[0] < 12_000) return 0L;
            return engine.extract(call.getArgument(0), call.getArgument(1), mode);
        });
        smart.setMarkedCoolant(new FluidStack(Fluids.WATER, 1));
        smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
        int initial = smart.tank.getFluidAmount();
        requests[0] = requests[1] = 0;
        long consumed = 0, allocations = 0, cpu = 0;
        int lowest = initial;
        long[] durations = new long[ticks];
        var meter = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        meter.setThreadAllocatedMemoryEnabled(true);
        var grid = fixture.smartNode.getGrid();
        Object[] recordedMocks = {fixture.network, fixture.controller, fixture.smartNode,
            smart.getLevel(), smart.cluster, grid, grid.getStorageService(), grid.getEnergyService()};
        for (int tick = 1; tick <= ticks; tick++) {
            now[0] = tick;
            consumed += smart.tank.drain(drainPerTick, FluidAction.EXECUTE).getAmount();
            lowest = Math.min(lowest, smart.tank.getFluidAmount());
            long beforeBytes = meter.getThreadAllocatedBytes(Thread.currentThread().threadId());
            long beforeCpu = meter.getCurrentThreadCpuTime(), start = System.nanoTime();
            smart.tick(smart.getLevel(), BlockPos.ZERO, smart.getBlockState());
            durations[tick - 1] = System.nanoTime() - start;
            cpu += meter.getCurrentThreadCpuTime() - beforeCpu;
            allocations += meter.getThreadAllocatedBytes(Thread.currentThread().threadId()) - beforeBytes;
            // Invocation history is not device state. Bound fixture-only retention outside the measured call.
            if (tick % 200 == 0) clearInvocations(recordedMocks);
        }
        long pulled = 1_000_000_000L - engine.extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
        if (pulled != consumed + smart.tank.getFluidAmount()) throw new AssertionError("Coolant was lost or duplicated");
        java.util.Arrays.sort(durations);
        var row = new com.google.gson.JsonObject();
        row.addProperty("thresholdPercent", threshold); row.addProperty("ticks", ticks);
        row.addProperty("drainPerTick", drainPerTick); row.addProperty("outage", outage);
        row.addProperty("simulateCalls", requests[0]); row.addProperty("modulateCalls", requests[1]);
        row.addProperty("initialTank", initial); row.addProperty("finalTank", smart.tank.getFluidAmount());
        row.addProperty("minimumTank", lowest); row.addProperty("consumed", consumed);
        row.addProperty("totalPulled", pulled); row.addProperty("conservation", true);
        row.addProperty("p50OfflineTickNs", durations[ticks / 2]);
        row.addProperty("p95OfflineTickNs", durations[(int) (ticks * .95)]);
        row.addProperty("p99OfflineTickNs", durations[(int) (ticks * .99)]);
        row.addProperty("totalThreadCpuNs", cpu); row.addProperty("allocatedBytesPerTick", allocations / (double) ticks);
        return row;
    }
}
