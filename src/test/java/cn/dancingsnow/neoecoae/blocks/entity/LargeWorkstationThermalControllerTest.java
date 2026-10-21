package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.all.NERecipeTypes;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.recipe.CoolingRecipe;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayDeque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import cn.dancingsnow.neoecoae.platform.recipe.SizedFluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LargeWorkstationThermalControllerTest {
    private static Fluid cryotheum;
    private ECOLargeIntegratedWorkingStationBlockEntity host;
    private Level level;
    private RecipeManager recipes;
    private FluidTank input;
    private FluidTank output;
    private boolean previousTemperatureEnabled;

    @BeforeAll
    static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName());
        }
        var recipeRegistry = (MappedRegistry<RecipeType<?>>) BuiltInRegistries.RECIPE_TYPE;
        if (!recipeRegistry.containsKey(NERecipeTypes.COOLING.getId())) {
            recipeRegistry.unfreeze();
            Registry.register(recipeRegistry, NERecipeTypes.COOLING.getId(), new RecipeType<CoolingRecipe>() {});
            recipeRegistry.freeze();
        }
        var fluids = (MappedRegistry<Fluid>) BuiltInRegistries.FLUID;
        var id = new ResourceLocation("neoecoae:cryotheum_solution");
        fluids.unfreeze();
        // This registry entry is shared with diagnostics tests that resolve the coolant's display name.
        var cryotheumType = new FluidType(FluidType.Properties.create()
            .descriptionId("fluid.neoecoae.cryotheum_solution")) {};
        cryotheum = Registry.register(fluids, id, new WaterFluid.Source() {
            @Override
            public FluidType getFluidType() { return cryotheumType; }
        });
        fluids.freeze();
    }

    @BeforeEach
    void setup() throws Exception {
        previousTemperatureEnabled = NEConfig.largeWorkstationTemperatureEnabled;
        NEConfig.largeWorkstationTemperatureEnabled = true;
        host = mock(ECOLargeIntegratedWorkingStationBlockEntity.class, CALLS_REAL_METHODS);
        var node = mock(IManagedGridNode.class);
        doReturn(node).when(host).getMainNode();
        when(node.isActive()).thenReturn(true);
        doNothing().when(host).setChanged();
        doNothing().when(host).markForUpdate();
        doNothing().when(host).setWorking(anyBoolean());
        doNothing().when(host).setProcessingTime(anyInt());
        doReturn(Blocks.AIR.defaultBlockState()).when(host).getBlockState();
        doReturn(0.0).when(host).getAECurrentPower();
        doReturn(16_000_000.0).when(host).getAEMaxPower();
        doReturn(0.0).when(host).extractAEPower(anyDouble(), any(), any());
        level = mock(Level.class);
        recipes = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(recipes);
        input = new FluidTank(100_000);
        output = new FluidTank(100_000);
        doReturn(input).when(host).getInputTank();
        doReturn(output).when(host).getOutputTank();
        field(host, "inputTank", input);
        field(host, "outputTank", output);
        field(host, "level", level);
        field(host, "formed", true);
        field(host, "overclocked", true);
        field(host, "pendingBatches", new ArrayDeque<>());
        field(host, "coolingOutput", new LargeWorkstationCoolingOutput());
        field(host, "uiEnergyUsage", new LargeWorkstationEnergyUsage());
        field(host, "moduleInventory", new LargeWorkstationModuleInventory(() -> {}));
    }

    @AfterEach
    void restoreConfig() {
        NEConfig.largeWorkstationTemperatureEnabled = previousTemperatureEnabled;
    }

    @Test
    void registeredRecoveryCoolantHasATranslatableDisplayName() {
        assertEquals(Component.translatable("fluid.neoecoae.cryotheum_solution"),
            new FluidStack(cryotheum, 1).getDisplayName());
    }

    @Test
    void strongCoolantLowersIdleMachineTemperatureAndAdvertisedCapacity() throws Exception {
        field(host, "temperature", 20.0);
        field(host, "activeCooling", true);
        coolant(Fluids.WATER, 9, FluidStack.EMPTY);
        input.setFluid(new FluidStack(Fluids.WATER, 1000));
        assertEquals(16_384, host.getMaxBatchParallelism());
        tick();
        assertEquals(19.965, host.getTemperature(), 1e-10);
        assertEquals(256, host.getMaxBatchParallelism());
        assertEquals(900, input.getFluidAmount());
    }

    @Test
    void coolantOutputBlockagePreventsConsumptionAndCooling() throws Exception {
        field(host, "temperature", 50.0);
        field(host, "activeCooling", true);
        coolant(Fluids.WATER, 6, new FluidStack(Fluids.LAVA, 100));
        input.setFluid(new FluidStack(Fluids.WATER, 1000));
        output.setFluid(new FluidStack(Fluids.WATER, 100_000));
        tick();
        assertEquals(49.995, host.getTemperature(), 1e-10);
        assertEquals(1000, input.getFluidAmount());
    }

    @Test
    void recoveryRequiresRealCryotheumAndThreeHundredActualConsumptionTicks() throws Exception {
        field(host, "temperature", 100.0);
        field(host, "overheated", true);
        field(host, "activeCooling", true);
        coolant(Fluids.WATER, 9, FluidStack.EMPTY);
        input.setFluid(new FluidStack(Fluids.WATER, 50_000));
        tick();
        assertEquals(0, value(host, "recoveryTicks"));
        assertEquals(50_000, input.getFluidAmount(), "A custom tier-9 water recipe cannot bypass cryotheum recovery");
        coolant(cryotheum, 9, FluidStack.EMPTY);
        input.setFluid(new FluidStack(cryotheum, 50_000));
        for (int i = 0; i < 299; i++) tick();
        assertTrue((boolean) value(host, "overheated"));
        assertEquals(0, host.getMaxBatchParallelism());
        tick();
        assertFalse((boolean) value(host, "overheated"));
        assertEquals(0, host.getTemperature());
        assertEquals(20_000, input.getFluidAmount());
        assertEquals(256, host.getMaxBatchParallelism());
    }

    @Test
    void recoveryResetsWhenCoolingStopsOrItsOutputBlocks() throws Exception {
        field(host, "temperature", 100.0);
        field(host, "overheated", true);
        field(host, "activeCooling", true);
        coolant(cryotheum, 9, new FluidStack(Fluids.LAVA, 100));
        input.setFluid(new FluidStack(cryotheum, 50_000));
        for (int i = 0; i < 100; i++) tick();
        assertEquals(100, value(host, "recoveryTicks"));
        output.setFluid(new FluidStack(Fluids.WATER, 100_000));
        tick();
        assertEquals(0, value(host, "recoveryTicks"));
        assertEquals(100, host.getTemperature());
        assertEquals(40_000, input.getFluidAmount());
    }

    @ParameterizedTest
    @CsvSource({"19.95,20", "49.95,50", "79.95,80"})
    void preheatIsPaidSeparatelyStopsAtTheNextThresholdAndNeverRestartsAutomatically(double start, double target) throws Exception {
        field(host, "temperature", start);
        field(host, "activeCooling", true);
        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());
        host.togglePreheating();
        assertFalse(host.isActiveCooling());
        tick();
        assertEquals(target, host.getTemperature(), 1e-10);
        assertFalse((boolean) value(host, "preheating"));
        verify(host).extractAEPower(doubleThat(amount -> Math.abs(amount - 500) < 1e-8), eq(Actionable.MODULATE), any());
        field(host, "temperature", 19.0);
        clearInvocations(host);
        tick();
        assertEquals(18.995, host.getTemperature(), 1e-10);
        verify(host, never()).extractAEPower(anyDouble(), eq(Actionable.MODULATE), any());
    }

    @Test
    void coldAcceptedBatchKeepsItsInputsAndProgressWithoutChargingProcessingEnergy() throws Exception {
        var type = Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName() + "$PendingBatch");
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var owned = new KeyCounter();
        owned.add(AEItemKey.of(Items.IRON_INGOT), 400);
        var produced = new KeyCounter();
        produced.add(AEItemKey.of(Items.DIAMOND), 100);
        Object batch = constructor.newInstance(100L, 1000L, 2, 1, owned, produced,
            null, new ResourceLocation("test:thermal"), null, null, 0L);
        field(batch, "progress", 50);
        var queue = new ArrayDeque<>();
        queue.add(batch);
        field(host, "pendingBatches", queue);
        field(host, "temperature", 19.9);
        host.tickingRequest(mock(IGridNode.class), 20);
        assertSame(batch, cn.dancingsnow.neoecoae.platform.JdkCompat.first(queue));
        assertEquals(50, value(batch, "progress"));
        assertEquals(400, ((KeyCounter) value(batch, "inputTotal")).get(AEItemKey.of(Items.IRON_INGOT)));
        verify(host, never()).extractAEPower(anyDouble(), any(), any());
    }

    @Test
    void highTierProcessingPaysBaseRecipeEnergyAndRetainsCompletedOutputWhenDeliveryIsUnavailable() throws Exception {
        var type = Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName() + "$PendingBatch");
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var owned = new KeyCounter();
        owned.add(AEItemKey.of(Items.IRON_INGOT), 400);
        var produced = new KeyCounter();
        produced.add(AEItemKey.of(Items.DIAMOND), 100);
        Object batch = constructor.newInstance(100L, 1000L, 9, 1, owned, produced,
            null, new ResourceLocation("test:thermal"), null, null, 0L);
        var queue = new ArrayDeque<>();
        queue.add(batch);
        field(host, "pendingBatches", queue);
        field(host, "temperature", 80.0);
        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());
        host.tickingRequest(mock(IGridNode.class), 1);
        verify(host).extractAEPower(eq(100_000.0), eq(Actionable.MODULATE), any());
        assertEquals(200, value(batch, "progress"));
        assertTrue(((KeyCounter) value(batch, "inputTotal")).isEmpty());
        assertEquals(100, ((KeyCounter) value(batch, "pendingOutput")).get(AEItemKey.of(Items.DIAMOND)));
        tick();
        assertTrue(host.getTemperature() > 80);
    }

    @ParameterizedTest
    @CsvSource({"0,256", "2,16384", "6,65536", "9,262144"})
    void disabledTemperatureUsesCoolantTierRegardlessOfHeatOrSelectedMaximum(int tier, int expected) throws Exception {
        NEConfig.largeWorkstationTemperatureEnabled = false;
        field(host, "activeCooling", true);
        if (tier == 0) when(recipes.getAllRecipesFor(NERecipeTypes.COOLING.get())).thenReturn(List.of());
        else {
            coolant(Fluids.WATER, tier, FluidStack.EMPTY);
            input.setFluid(new FluidStack(Fluids.WATER, 1000));
        }
        assertEquals(expected, host.getMaxBatchParallelism(), "Cold machine needs no preheat");
        field(host, "temperature", 100.0);
        field(host, "overheated", true);
        assertEquals(expected, host.getMaxBatchParallelism(), "Saved overheat cannot block coolant mode");
        assertTrue(host.canAcceptPattern());
        field(host, "activeCooling", false);
        assertEquals(256, host.getMaxBatchParallelism());
        field(host, "activeCooling", true);
        field(host, "overclocked", false);
        assertEquals(256, host.getMaxBatchParallelism());
    }

    @Test
    void coolantModeDoesNotConsumeWhileIdleOrRunThermalControls() throws Exception {
        NEConfig.largeWorkstationTemperatureEnabled = false;
        field(host, "temperature", 50.0);
        field(host, "activeCooling", true);
        coolant(cryotheum, 9, FluidStack.EMPTY);
        input.setFluid(new FluidStack(cryotheum, 1000));
        host.togglePreheating();
        for (int i = 0; i < 20; i++) tick();
        assertEquals(50, host.getTemperature());
        assertEquals(1000, input.getFluidAmount());
        assertFalse((boolean) value(host, "preheating"));
        verify(host, never()).extractAEPower(anyDouble(), any(), any());
    }

    @Test
    void coolantModeConsumesOneHundredMbWithByproductAndChargesOnlyBaseRecipeEnergy() throws Exception {
        NEConfig.largeWorkstationTemperatureEnabled = false;
        field(host, "activeCooling", true);
        field(host, "temperature", 100.0);
        field(host, "overheated", true);
        coolant(Fluids.WATER, 6, new FluidStack(Fluids.LAVA, 100));
        input.setFluid(new FluidStack(Fluids.WATER, 1000));
        var batch = queuedBatch(6);
        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());
        host.tickingRequest(mock(IGridNode.class), 1);
        verify(host).extractAEPower(eq(100_000.0), eq(Actionable.MODULATE), any());
        assertEquals(200, value(batch, "progress"));
        assertEquals(900, input.getFluidAmount());
        assertEquals(100, output.getFluidAmount());
        assertEquals(100, host.getTemperature(), "Temperature state is frozen");
        assertTrue((boolean) value(host, "overheated"), "Latch is saved for re-enabling temperature mode");
        assertEquals(100, ((KeyCounter) value(batch, "pendingOutput")).get(AEItemKey.of(Items.DIAMOND)));
    }

    @ParameterizedTest
    @CsvSource({"2,1000,true,false", "9,99,true,false", "9,1000,false,false", "9,1000,true,true"})
    void coolantModePausesForLowTierLowAmountDisabledCoolingOrBlockedOutput(
        int coolantTier, int amount, boolean enabled, boolean blocked
    ) throws Exception {
        NEConfig.largeWorkstationTemperatureEnabled = false;
        field(host, "activeCooling", enabled);
        coolant(Fluids.WATER, coolantTier, new FluidStack(Fluids.LAVA, 100));
        input.setFluid(new FluidStack(Fluids.WATER, amount));
        if (blocked) output.setFluid(new FluidStack(Fluids.WATER, 100_000));
        var batch = queuedBatch(9);
        host.tickingRequest(mock(IGridNode.class), 1);
        assertEquals(0, value(batch, "progress"));
        assertEquals(amount, input.getFluidAmount());
        assertEquals(400, ((KeyCounter) value(batch, "inputTotal")).get(AEItemKey.of(Items.IRON_INGOT)));
        verify(host, never()).extractAEPower(anyDouble(), eq(Actionable.MODULATE), any());
    }

    @Test
    void coolantModeDoesNotConsumeWithoutProcessingPower() throws Exception {
        NEConfig.largeWorkstationTemperatureEnabled = false;
        field(host, "activeCooling", true);
        coolant(Fluids.WATER, 9, FluidStack.EMPTY);
        input.setFluid(new FluidStack(Fluids.WATER, 1000));
        var batch = queuedBatch(9);
        host.tickingRequest(mock(IGridNode.class), 1);
        assertEquals(0, value(batch, "progress"));
        assertEquals(1000, input.getFluidAmount());
    }

    @Test
    void reEnablingTemperatureChecksTheSameBatchWithoutLosingOwnedInputs() throws Exception {
        NEConfig.largeWorkstationTemperatureEnabled = false;
        field(host, "activeCooling", true);
        field(host, "temperature", 10.0);
        coolant(Fluids.WATER, 9, FluidStack.EMPTY);
        input.setFluid(new FluidStack(Fluids.WATER, 1000));
        var batch = queuedBatch(9);
        doAnswer(call -> Math.min((double) call.getArgument(0), 50_000.0))
            .when(host).extractAEPower(anyDouble(), any(), any());
        host.tickingRequest(mock(IGridNode.class), 1);
        assertEquals(100, value(batch, "progress"));
        NEConfig.largeWorkstationTemperatureEnabled = true;
        host.tickingRequest(mock(IGridNode.class), 1);
        assertEquals(100, value(batch, "progress"));
        assertEquals(400, ((KeyCounter) value(batch, "inputTotal")).get(AEItemKey.of(Items.IRON_INGOT)));
        assertEquals(900, input.getFluidAmount());
    }

    @Test
    void disablingTemperatureBypassesButDoesNotEraseSavedProtection() throws Exception {
        field(host, "temperature", 100.0);
        field(host, "overheated", true);
        assertEquals(0, host.getMaxBatchParallelism());
        NEConfig.largeWorkstationTemperatureEnabled = false;
        assertEquals(256, host.getMaxBatchParallelism());
        host.togglePreheating();
        tick();
        assertTrue((boolean) value(host, "overheated"));
        assertEquals(100, host.getTemperature());
        NEConfig.largeWorkstationTemperatureEnabled = true;
        assertEquals(0, host.getMaxBatchParallelism());
    }

    @Test
    void normalCoolantModeBatchNeedsNoLiquidAndDoesNotConsumeAvailableCoolant() throws Exception {
        NEConfig.largeWorkstationTemperatureEnabled = false;
        field(host, "activeCooling", true);
        coolant(Fluids.WATER, 9, FluidStack.EMPTY);
        input.setFluid(new FluidStack(Fluids.WATER, 1000));
        var batch = queuedBatch(0);
        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());
        host.tickingRequest(mock(IGridNode.class), 1);
        assertEquals(200, value(batch, "progress"));
        assertEquals(1000, input.getFluidAmount());
    }

    @Test
    void acceptedCoolantBatchWaitsWhenNoCoolantRecipeIsAvailable() throws Exception {
        NEConfig.largeWorkstationTemperatureEnabled = false;
        field(host, "activeCooling", true);
        when(recipes.getAllRecipesFor(NERecipeTypes.COOLING.get())).thenReturn(List.of());
        var batch = queuedBatch(9);
        host.tickingRequest(mock(IGridNode.class), 1);
        assertEquals(0, value(batch, "progress"));
        assertEquals(400, ((KeyCounter) value(batch, "inputTotal")).get(AEItemKey.of(Items.IRON_INGOT)));
        verify(host, never()).extractAEPower(anyDouble(), eq(Actionable.MODULATE), any());
    }

    @Test
    void menuSnapshotReportsServerSelectedModeAndHidesInactiveThermalReadings() throws Exception {
        NEConfig.largeWorkstationTemperatureEnabled = false;
        field(host, "temperature", 100.0);
        field(host, "overheated", true);
        field(host, "preheating", true);
        var snapshot = ECOLargeIntegratedWorkingStationBlockEntity.class.getDeclaredMethod("getUiSnapshot", int.class);
        snapshot.setAccessible(true);
        var value = (net.minecraft.nbt.CompoundTag) snapshot.invoke(host, 0);
        assertFalse(value.getBoolean("temperatureEnabled"));
        assertFalse(value.getBoolean("preheating"));
        assertEquals(0, value.getDouble("temperature"));
        assertEquals(256, value.getLong("parallelCapacity"));
        assertFalse(value.getString("pause").endsWith("overheated"));
        assertEquals(100, host.getTemperature(), "Only the display is hidden, persisted state remains intact");
    }

    @ParameterizedTest
    @CsvSource({"2,0.1", "6,0.3", "9,0.6"})
    void menuReportsActualCoolingSpeedAndZeroWhenCoolantIsNotConsumed(int tier, double speed) throws Exception {
        field(host, "temperature", 40.0);
        field(host, "activeCooling", true);
        coolant(Fluids.WATER, tier, FluidStack.EMPTY);
        input.setFluid(new FluidStack(Fluids.WATER, 1000));
        tick();
        assertEquals(speed, value(host, "uiCoolingPerSecond"));
        var snapshot = ECOLargeIntegratedWorkingStationBlockEntity.class.getDeclaredMethod("getUiSnapshot", int.class);
        snapshot.setAccessible(true);
        assertEquals(speed, ((net.minecraft.nbt.CompoundTag) snapshot.invoke(host, 0)).getDouble("coolingPerSecond"));
        field(host, "activeCooling", false);
        assertEquals(0, ((net.minecraft.nbt.CompoundTag) snapshot.invoke(host, 0)).getDouble("coolingPerSecond"));
        tick();
        assertEquals(0.0, value(host, "uiCoolingPerSecond"));
        field(host, "activeCooling", true);
        input.setFluid(FluidStack.EMPTY);
        tick();
        assertEquals(0.0, value(host, "uiCoolingPerSecond"));
    }

    @ParameterizedTest
    @ValueSource(longs = {100, 16_384, 262_144, 2_147_483_648L, Long.MAX_VALUE})
    void runningBatchMenuUsesOneItemIconAndPreservesTheExactOutputCount(long amount) throws Exception {
        var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        when(level.registryAccess()).thenReturn(registries);
        Object batch = queuedBatch(0);
        field(batch, "recipeId", null);
        field(batch, "progress", 25);
        var stack = new net.minecraft.world.item.ItemStack(Items.ENDER_PEARL);
        stack.setHoverName(net.minecraft.network.chat.Component.literal("Batch icon"));
        KeyCounter outputs = (KeyCounter) value(batch, "outputTotal");
        outputs.clear();
        outputs.add(AEItemKey.of(stack), amount);
        var snapshot = ECOLargeIntegratedWorkingStationBlockEntity.class.getDeclaredMethod("getUiSnapshot", int.class);
        snapshot.setAccessible(true);
        var tag = assertDoesNotThrow(() -> (net.minecraft.nbt.CompoundTag) snapshot.invoke(host, 0));
        var row = tag.getList("tasks", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(amount, row.getLong("amount"));
        assertEquals(25, row.getInt("progress"));
        assertTrue(row.getBoolean("returnable"));
        var icon = cn.dancingsnow.neoecoae.platform.nbt.NEStacks.read(registries, row.getCompound("item"));
        assertEquals(1, icon.getCount());
        assertEquals(8, icon.getMaxStackSize());
        assertEquals("Batch icon", icon.getHoverName().getString());
        assertEquals(amount, outputs.get(AEItemKey.of(stack)), "Opening the UI must not alter the owned batch output");
    }

    private Object queuedBatch(int tier) throws Exception {
        var type = Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName() + "$PendingBatch");
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var owned = new KeyCounter();
        owned.add(AEItemKey.of(Items.IRON_INGOT), 400);
        var produced = new KeyCounter();
        produced.add(AEItemKey.of(Items.DIAMOND), 100);
        Object batch = constructor.newInstance(100L, 1000L, tier, 1, owned, produced,
            null, new ResourceLocation("test:thermal"), null, null, 0L);
        var queue = new ArrayDeque<>();
        queue.add(batch);
        field(host, "pendingBatches", queue);
        return batch;
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void coldNormalBatchGeneratesHeatOnlyWhileOverclockingAndActuallyAdvancing(boolean enabled) throws Exception {
        field(host, "temperature", 10.0);
        field(host, "overclocked", enabled);
        queuedBatch(0);
        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());
        host.tickingRequest(mock(IGridNode.class), 1);
        tick();
        double expected = enabled ? 10 + 0.208 * 100 / 256 / 20 : 9.995;
        assertEquals(expected, host.getTemperature(), 1e-10);
        tick();
        assertEquals(expected - 0.005, host.getTemperature(), 1e-10, "Paid work is never heated twice");
        host.tickingRequest(mock(IGridNode.class), 1);
        tick();
        assertEquals(expected - 0.01, host.getTemperature(), 1e-10, "Completed output waiting for delivery generates no heat");
    }

    @Test
    void overclockSwitchAloneAndUnpaidOrColdPausedBatchesCannotGenerateHeat() throws Exception {
        field(host, "temperature", 10.0);
        tick();
        assertEquals(9.995, host.getTemperature(), 1e-10);
        var batch = queuedBatch(0);
        host.tickingRequest(mock(IGridNode.class), 1);
        tick();
        assertEquals(9.99, host.getTemperature(), 1e-10);
        assertEquals(0, value(batch, "progress"));
        field(batch, "coolingTier", 9);
        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());
        host.tickingRequest(mock(IGridNode.class), 1);
        tick();
        assertEquals(9.985, host.getTemperature(), 1e-10);
        assertEquals(0, value(batch, "progress"));
    }

    @Test
    void actualProcessingStopsIndependentPreheatInsteadOfMaintainingTemperature() throws Exception {
        field(host, "temperature", 10.0);
        host.togglePreheating();
        queuedBatch(0);
        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());
        host.tickingRequest(mock(IGridNode.class), 1);
        assertFalse((boolean) value(host, "preheating"));
        clearInvocations(host);
        tick();
        assertEquals(10 + 0.208 * 100 / 256 / 20, host.getTemperature(), 1e-10);
        verify(host, never()).extractAEPower(anyDouble(), any(), any());
    }

    private void coolant(Fluid fluid, int tier, FluidStack byproduct) {
        var recipe = new CoolingRecipe(SizedFluidIngredient.of(fluid, 100), byproduct, 1, tier);
        when(recipes.getAllRecipesFor(NERecipeTypes.COOLING.get())).thenReturn(List.of(
            cn.dancingsnow.neoecoae.util.RecipeTestSupport.withId(new ResourceLocation("test:cooling"), recipe)));
    }

    private void tick() { host.tickThermal(level, BlockPos.ZERO, Blocks.AIR.defaultBlockState()); }

    private static void field(Object object, String name, Object value) throws Exception {
        var field = findField(object, name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static Object value(Object object, String name) throws Exception {
        var field = findField(object, name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static java.lang.reflect.Field findField(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { return type.getDeclaredField(name); } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
