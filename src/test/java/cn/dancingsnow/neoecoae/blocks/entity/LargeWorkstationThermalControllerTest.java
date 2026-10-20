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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.WaterFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
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
    private long gameTime;

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
        var id = ResourceLocation.parse("neoecoae:cryotheum_solution");
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
        gameTime = 0;
        when(level.getGameTime()).thenAnswer(call -> gameTime);
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
        field(host, "uiThermalUsage", new LargeWorkstationThermalUsage());
        field(host, "moduleInventory", new LargeWorkstationModuleInventory(() -> {}));
    }

    @AfterEach
    void restoreConfig() {
        NEConfig.largeWorkstationTemperatureEnabled = previousTemperatureEnabled;
    }

    @Test
    void registeredRecoveryCoolantHasATranslatableDisplayName() {
        assertEquals(Component.translatable("fluid.neoecoae.cryotheum_solution"),
            new FluidStack(cryotheum, 1).getHoverName());
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
        double finalTickPayment = 1000 * (target - start) * 20 / LargeWorkstationThermal.preheatPerSecond(start);
        verify(host).extractAEPower(doubleThat(amount -> Math.abs(amount - finalTickPayment) < 1e-8), eq(Actionable.MODULATE), any());
        field(host, "temperature", 19.0);
        clearInvocations(host);
        tick();
        assertEquals(18.995, host.getTemperature(), 1e-10);
        verify(host, never()).extractAEPower(anyDouble(), eq(Actionable.MODULATE), any());
    }

    @Test
    void coldAcceptedBatchKeepsItsInputsAndPaidProgressWhenProcessingPowerIsUnavailable() throws Exception {
        var type = Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName() + "$PendingBatch");
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var owned = new KeyCounter();
        owned.add(AEItemKey.of(Items.IRON_INGOT), 400);
        var produced = new KeyCounter();
        produced.add(AEItemKey.of(Items.DIAMOND), 100);
        Object batch = constructor.newInstance(100L, 1000L, 2, 1, owned, produced,
            null, ResourceLocation.parse("test:thermal"), null, null, 0L);
        field(batch, "progress", 50);
        var queue = new ArrayDeque<>();
        queue.add(batch);
        field(host, "pendingBatches", queue);
        field(host, "temperature", 19.9);
        host.tickingRequest(mock(IGridNode.class), 20);
        assertSame(batch, queue.getFirst());
        assertEquals(50, value(batch, "progress"));
        assertEquals(400, ((KeyCounter) value(batch, "inputTotal")).get(AEItemKey.of(Items.IRON_INGOT)));
        assertTrue((boolean) value(batch, "segmented"));
        verify(host, never()).extractAEPower(anyDouble(), eq(Actionable.MODULATE), any());
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
            null, ResourceLocation.parse("test:thermal"), null, null, 0L);
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
    void reEnablingTemperatureContinuesAColdBatchAtBaseParallelismWithoutChargingPaidWorkAgain() throws Exception {
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
        assertEquals(200, value(batch, "progress"));
        assertTrue((boolean) value(batch, "segmented"));
        assertTrue(((KeyCounter) value(batch, "inputTotal")).isEmpty());
        assertEquals(100, ((KeyCounter) value(batch, "pendingOutput")).get(AEItemKey.of(Items.DIAMOND)));
        verify(host, times(2)).extractAEPower(eq(50_000.0), eq(Actionable.MODULATE), any());
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
        input.setFluid(new FluidStack(Fluids.WATER, 10_000));
        for (int i = 0; i < 20; i++) tick();
        var snapshot = ECOLargeIntegratedWorkingStationBlockEntity.class.getDeclaredMethod("getUiSnapshot", int.class);
        snapshot.setAccessible(true);
        assertEquals(speed, ((net.minecraft.nbt.CompoundTag) snapshot.invoke(host, 0)).getDouble("coolingPerSecond"), 1e-12);
        field(host, "activeCooling", false);
        assertEquals(speed, ((net.minecraft.nbt.CompoundTag) snapshot.invoke(host, 0)).getDouble("coolingPerSecond"), 1e-12,
            "Switching cooling off must not erase the actual previous second");
        for (int i = 0; i < 20; i++) tick();
        assertEquals(0, ((net.minecraft.nbt.CompoundTag) snapshot.invoke(host, 0)).getDouble("coolingPerSecond"));
        field(host, "activeCooling", true);
        input.setFluid(FluidStack.EMPTY);
        for (int i = 0; i < 20; i++) tick();
        assertEquals(0, ((net.minecraft.nbt.CompoundTag) snapshot.invoke(host, 0)).getDouble("coolingPerSecond"));
    }

    @ParameterizedTest
    @ValueSource(longs = {100, 16_384, 262_144, 2_147_483_648L, Long.MAX_VALUE})
    void runningBatchMenuUsesOneItemIconAndPreservesTheExactOutputCount(long amount) throws Exception {
        var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        when(level.registryAccess()).thenReturn(registries);
        Object batch = queuedBatch(0);
        field(batch, "recipeId", null);
        field(batch, "progress", 25);
        var stack = new net.minecraft.world.item.ItemStack(Items.DIAMOND);
        stack.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 8);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Batch icon"));
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
        var icon = net.minecraft.world.item.ItemStack.parseOptional(registries, row.getCompound("item"));
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
            null, ResourceLocation.parse("test:thermal"), null, null, 0L);
        var queue = new ArrayDeque<>();
        queue.add(batch);
        field(host, "pendingBatches", queue);
        return batch;
    }

    @Test
    void temperatureUpgradeImmediatelyRefillsTheRunningBatchFromItsExistingQueue() throws Exception {
        field(host, "temperature", 80.0);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object first = sizedBatch(65_536, 6, 50, null);
        queue.add(first);
        for (int i = 0; i < 3; i++) queue.add(sizedBatch(65_536, 6, 0, null));

        host.tickingRequest(mock(IGridNode.class), 1);

        assertEquals(1, queue.size());
        assertSame(first, queue.getFirst(), "The running task keeps its interaction identity");
        assertEquals(262_144L, value(first, "craftCount"));
        assertEquals(9, value(first, "coolingTier"));
        assertEquals(12, value(first, "progress"));
        assertEquals(131_072L, value(first, "progressRemainder"));
        assertEquals(1_048_576, ((KeyCounter) value(first, "inputTotal")).get(AEItemKey.of(Items.IRON_INGOT)));
        assertEquals(262_144, ((KeyCounter) value(first, "outputTotal")).get(AEItemKey.of(Items.DIAMOND)));
        verify(host, never()).extractAEPower(anyDouble(), eq(Actionable.MODULATE), any());
    }

    @Test
    void refillingOnlyTakesThePartOfTheNextBatchThatFits() throws Exception {
        field(host, "temperature", 80.0);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object first = sizedBatch(65_536, 6, 0, null);
        Object next = sizedBatch(262_144, 9, 0, null);
        queue.add(first);
        queue.add(next);

        host.tickingRequest(mock(IGridNode.class), 1);

        assertEquals(2, queue.size());
        assertEquals(262_144L, value(first, "craftCount"));
        assertEquals(65_536L, value(next, "craftCount"));
        assertEquals(262_144, ((KeyCounter) value(next, "inputTotal")).get(AEItemKey.of(Items.IRON_INGOT)));
        assertEquals(65_536, ((KeyCounter) value(next, "outputTotal")).get(AEItemKey.of(Items.DIAMOND)));
    }

    @Test
    void refillingPreservesEveryPaidCraftStepEvenWhenTheNewProgressDoesNotDivideExactly() throws Exception {
        field(host, "temperature", 80.0);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object first = sizedBatch(65_535, 6, 7, null);
        queue.add(first);
        queue.add(sizedBatch(196_609, 9, 0, null));
        host.tickingRequest(mock(IGridNode.class), 1);
        assertEquals(1, value(first, "progress"));
        assertEquals(196_601L, value(first, "progressRemainder"));

        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());
        host.tickingRequest(mock(IGridNode.class), 1);

        double remainingEnergy = 1_000.0 * (262_144 - 65_535 * 7.0 / 200);
        verify(host).extractAEPower(eq(remainingEnergy), eq(Actionable.MODULATE), any());
        assertEquals(200, value(first, "progress"));
        assertEquals(0L, value(first, "progressRemainder"));
        assertEquals(262_144, ((KeyCounter) value(first, "pendingOutput")).get(AEItemKey.of(Items.DIAMOND)));
    }

    @ParameterizedTest
    @CsvSource({"job", "recipe", "output", "progress"})
    void refillingDoesNotCombineUnrelatedOrAlreadyProcessingTasks(String difference) throws Exception {
        field(host, "temperature", 80.0);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        var job = java.util.UUID.randomUUID();
        Object first = sizedBatch(65_536, 6, 0, job);
        Object next = sizedBatch(65_536, 6, 0, difference.equals("job") ? java.util.UUID.randomUUID() : job);
        if (difference.equals("recipe")) field(next, "recipeId", ResourceLocation.parse("test:different"));
        if (difference.equals("progress")) field(next, "progress", 1);
        if (difference.equals("output")) ((KeyCounter) value(next, "outputTotal")).add(AEItemKey.of(Items.DIAMOND), 65_536);
        queue.add(first);
        queue.add(next);

        host.tickingRequest(mock(IGridNode.class), 1);

        assertEquals(2, queue.size());
        assertEquals(65_536L, value(first, "craftCount"));
        assertEquals(65_536L, value(next, "craftCount"));
    }

    @Test
    void coolantUpgradeRefillsExistingBatchesAndConsumesOnlyOneProcessingTicksCoolant() throws Exception {
        NEConfig.largeWorkstationTemperatureEnabled = false;
        field(host, "activeCooling", true);
        coolant(cryotheum, 9, FluidStack.EMPTY);
        input.fill(new FluidStack(cryotheum, 1_000), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object first = sizedBatch(65_536, 6, 0, null);
        queue.add(first);
        for (int i = 0; i < 3; i++) queue.add(sizedBatch(65_536, 6, 0, null));
        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());

        host.tickingRequest(mock(IGridNode.class), 1);

        assertEquals(1, queue.size(), "Unavailable output delivery retains the combined output ledger");
        assertEquals(262_144L, value(first, "craftCount"));
        assertEquals(200, value(first, "progress"));
        assertEquals(900, input.getFluidAmount());
        verify(host).extractAEPower(eq(262_144_000.0), eq(Actionable.MODULATE), any());
    }

    @Test
    void limitedPowerPaysOnlyTheUnpaidPartOfTheNextProgressStepAfterRefilling() throws Exception {
        field(host, "temperature", 80.0);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object first = sizedBatch(65_535, 6, 7, null);
        queue.add(first);
        queue.add(sizedBatch(196_609, 9, 0, null));
        host.tickingRequest(mock(IGridNode.class), 1);
        double nextStepEnergy = (262_144 - 196_601) * 1_000.0 / 200;
        doAnswer(call -> Math.min(call.<Double>getArgument(0), nextStepEnergy))
            .when(host).extractAEPower(anyDouble(), any(), any());

        host.tickingRequest(mock(IGridNode.class), 1);

        assertEquals(2, value(first, "progress"));
        assertEquals(0L, value(first, "progressRemainder"));
        verify(host).extractAEPower(eq(nextStepEnergy), eq(Actionable.MODULATE), any());
        assertTrue(((KeyCounter) value(first, "pendingOutput")).isEmpty());
    }

    @Test
    void refillingCannotOverflowAnOwnedInputOrOutputCounter() throws Exception {
        field(host, "temperature", 80.0);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object first = sizedBatch(1, 6, 0, null);
        Object next = sizedBatch(1, 6, 0, null);
        for (Object task : List.of(first, next)) {
            KeyCounter owned = (KeyCounter) value(task, "inputTotal");
            owned.clear();
            owned.add(AEItemKey.of(Items.IRON_INGOT), Long.MAX_VALUE);
        }
        queue.add(first);
        queue.add(next);

        assertDoesNotThrow(() -> host.tickingRequest(mock(IGridNode.class), 1));

        assertEquals(2, queue.size());
        for (Object task : queue) {
            assertEquals(1L, value(task, "craftCount"));
            assertEquals(Long.MAX_VALUE, ((KeyCounter) value(task, "inputTotal")).get(AEItemKey.of(Items.IRON_INGOT)));
        }
    }

    @Test
    void refillingWaitsForTheNextTasksCompatibilityInputsWithoutBorrowingItsUnpaidWork() throws Exception {
        field(host, "temperature", 80.0);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object first = sizedBatch(65_536, 6, 7, null);
        Object next = sizedBatch(65_536, 6, 0, null);
        ((KeyCounter) value(first, "inputTotal")).add(AEItemKey.of(Items.GOLD_INGOT), 65_536);
        ((KeyCounter) value(next, "missingExtras")).add(AEItemKey.of(Items.GOLD_INGOT), 65_536);
        queue.add(first);
        queue.add(next);

        host.tickingRequest(mock(IGridNode.class), 1);

        assertEquals(2, queue.size());
        assertEquals(65_536L, value(first, "craftCount"));
        assertEquals(7, value(first, "progress"));
        assertEquals(65_536, ((KeyCounter) value(next, "missingExtras")).get(AEItemKey.of(Items.GOLD_INGOT)));
        assertEquals(0, ((KeyCounter) value(next, "inputTotal")).get(AEItemKey.of(Items.GOLD_INGOT)));
    }

    private Object sizedBatch(long count, int tier, int progress, java.util.UUID job) throws Exception {
        var type = Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName() + "$PendingBatch");
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var owned = new KeyCounter();
        owned.add(AEItemKey.of(Items.IRON_INGOT), count * 4);
        var produced = new KeyCounter();
        produced.add(AEItemKey.of(Items.DIAMOND), count);
        Object batch = constructor.newInstance(count, 1_000L, tier, 1, owned, produced,
            job, ResourceLocation.parse("test:thermal"), null, null, 0L);
        field(batch, "progress", progress);
        return batch;
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 19.9, 49.9, 79.9})
    void temperatureDowngradeCompletesOnly256CraftsAndKeepsTheRemainingTaskInOneQueueSlot(double temperature) throws Exception {
        field(host, "temperature", temperature);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object batch = sizedBatch(262_144, 9, 0, null);
        queue.add(batch);
        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());

        host.tickingRequest(mock(IGridNode.class), 1);

        assertEquals(1, queue.size());
        assertSame(batch, queue.getFirst());
        assertEquals(261_888L, value(batch, "craftCount"));
        assertTrue((boolean) value(batch, "segmented"));
        assertEquals(261_888L * 4, ((KeyCounter) value(batch, "inputTotal")).get(AEItemKey.of(Items.IRON_INGOT)));
        assertEquals(261_888L, ((KeyCounter) value(batch, "outputTotal")).get(AEItemKey.of(Items.DIAMOND)));
        assertEquals(256L, ((KeyCounter) value(batch, "pendingOutput")).get(AEItemKey.of(Items.DIAMOND)));
        verify(host).extractAEPower(eq(256_000.0), eq(Actionable.MODULATE), any());
    }

    @Test
    void downgradedPortionsKeepEveryPaidCraftStepAndDeliverEachOutputExactlyOnce() throws Exception {
        var storage = networkStorage();
        var delivered = new java.util.ArrayList<Long>();
        when(storage.insert(any(), anyLong(), eq(Actionable.MODULATE), any())).thenAnswer(call -> {
            assertEquals(AEItemKey.of(Items.DIAMOND), call.getArgument(0));
            long amount = call.getArgument(1);
            assertTrue(amount > 0 && amount <= 256);
            delivered.add(amount);
            return amount;
        });
        var charged = new java.util.ArrayList<Double>();
        doAnswer(call -> {
            double amount = call.getArgument(0);
            if (call.getArgument(1) == Actionable.MODULATE) charged.add(amount);
            return amount;
        }).when(host).extractAEPower(anyDouble(), any(), any());
        field(host, "temperature", 79.9);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object batch = sizedBatch(262_143, 9, 7, null);
        field(batch, "progressRemainder", 196_601L);
        queue.add(batch);
        long paidWork = 262_143L * 7 + 196_601;

        host.tickingRequest(mock(IGridNode.class), 1);
        long remainingCount = (long) value(batch, "craftCount");
        assertEquals(paidWork - 256L * 200,
            (int) value(batch, "progress") * remainingCount + (long) value(batch, "progressRemainder"));
        assertTrue(charged.isEmpty(), "The first portion is covered by already-paid work");

        for (int i = 0; !queue.isEmpty() && i < 1_024; i++) host.tickingRequest(mock(IGridNode.class), 1);

        assertTrue(queue.isEmpty());
        assertEquals(262_143L, delivered.stream().mapToLong(Long::longValue).sum());
        assertEquals(1_024, delivered.size());
        assertEquals(255L, delivered.getLast());
        assertEquals(1_000.0 * (262_143 - paidWork / 200.0), charged.stream().mapToDouble(Double::doubleValue).sum(), 1e-6);
        assertTrue(((KeyCounter) value(batch, "inputTotal")).isEmpty());
        assertEquals(0, ((KeyCounter) value(batch, "pendingOutput")).get(AEItemKey.of(Items.DIAMOND)));
    }

    @ParameterizedTest
    @CsvSource({"2,16384,19.9,20,false", "6,65536,49.9,50,false", "9,262144,79.9,80,false",
        "6,65536,49.9,50,true"})
    void reheatingRestoresDowngradedParallelismAndCreditsEveryPaidCraftStep(
        int tier, long count, double cold, double recovered, boolean reload
    ) throws Exception {
        field(host, "temperature", cold);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object batch = sizedBatch(count, tier, 0, null);
        field(batch, "recipeId", null);
        queue.add(batch);
        doAnswer(call -> Math.min((double) call.getArgument(0), 128_000.0))
            .when(host).extractAEPower(anyDouble(), any(), any());
        host.tickingRequest(mock(IGridNode.class), 1);
        assertTrue((boolean) value(batch, "segmented"));
        assertEquals(25_600L, (int) value(batch, "progress") * count + (long) value(batch, "progressRemainder"));
        if (reload) {
            batch = reloadBatch(batch);
            queue.clear();
            queue.add(batch);
        }

        field(host, "temperature", recovered);
        doReturn(0.0).when(host).extractAEPower(anyDouble(), any(), any());
        host.tickingRequest(mock(IGridNode.class), 1);

        assertFalse((boolean) value(batch, "segmented"), "Restored temperature must release the 256-craft fallback");
        assertEquals(count, value(batch, "craftCount"));
        assertEquals(25_600L, (int) value(batch, "progress") * count + (long) value(batch, "progressRemainder"));
        assertEquals(count * 4, ((KeyCounter) value(batch, "inputTotal")).get(AEItemKey.of(Items.IRON_INGOT)));
        when(level.registryAccess()).thenReturn(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var snapshot = ECOLargeIntegratedWorkingStationBlockEntity.class.getDeclaredMethod("getUiSnapshot", int.class);
        snapshot.setAccessible(true);
        var tag = (net.minecraft.nbt.CompoundTag) snapshot.invoke(host, 0);
        assertEquals(count, tag.getLong("parallelUsed"));
        assertEquals(count, tag.getLong("parallelCapacity"));
        var settings = (net.minecraft.network.chat.contents.TranslatableContents) host.getBatchSettingsText().getContents();
        assertEquals((int) count, settings.getArgs()[0]);
        field(host, "temperature", cold);
        host.tickingRequest(mock(IGridNode.class), 1);
        assertTrue((boolean) value(batch, "segmented"), "A later temperature drop must still enforce fallback");
        field(host, "temperature", recovered);
        host.tickingRequest(mock(IGridNode.class), 1);
        assertFalse((boolean) value(batch, "segmented"));
        assertEquals(25_600L, (int) value(batch, "progress") * count + (long) value(batch, "progressRemainder"));

        var delivered = new KeyCounter();
        when(networkStorage().insert(any(), anyLong(), eq(Actionable.MODULATE), any())).thenAnswer(call -> {
            delivered.add(call.getArgument(0), call.getArgument(1));
            return call.getArgument(1);
        });
        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());
        clearInvocations(host);
        host.tickingRequest(mock(IGridNode.class), 1);

        assertTrue(queue.isEmpty());
        assertEquals(count, delivered.get(AEItemKey.of(Items.DIAMOND)));
        verify(host).extractAEPower(eq(count * 1_000.0 - 128_000), eq(Actionable.MODULATE), any());
        assertTrue(((KeyCounter) value(batch, "inputTotal")).isEmpty());
    }

    @Test
    void reheatedTaskDeliversItsBlockedPortionBeforeRestoringTheRemainingParallelism() throws Exception {
        field(host, "temperature", 49.9);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object batch = sizedBatch(1_024, 6, 0, null);
        queue.add(batch);
        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());
        host.tickingRequest(mock(IGridNode.class), 1);
        assertEquals(256, ((KeyCounter) value(batch, "pendingOutput")).get(AEItemKey.of(Items.DIAMOND)));
        field(host, "temperature", 50.0);
        clearInvocations(host);
        host.tickingRequest(mock(IGridNode.class), 1);
        verify(host, never()).extractAEPower(anyDouble(), eq(Actionable.MODULATE), any());
        assertTrue((boolean) value(batch, "segmented"));

        var delivered = new KeyCounter();
        when(networkStorage().insert(any(), anyLong(), eq(Actionable.MODULATE), any())).thenAnswer(call -> {
            delivered.add(call.getArgument(0), call.getArgument(1));
            return call.getArgument(1);
        });
        host.tickingRequest(mock(IGridNode.class), 1);
        assertSame(batch, queue.getFirst());
        host.tickingRequest(mock(IGridNode.class), 1);

        assertFalse((boolean) value(batch, "segmented"));
        assertTrue(queue.isEmpty());
        assertEquals(1_024, delivered.get(AEItemKey.of(Items.DIAMOND)));
        verify(host).extractAEPower(eq(768_000.0), eq(Actionable.MODULATE), any());
    }

    @Test
    void reheatedTaskKeepsItsFallbackAndPaidWorkUntilAllCompatibilityInputsAreOwned() throws Exception {
        field(host, "temperature", 49.9);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object batch = sizedBatch(1_024, 6, 0, null);
        ((KeyCounter) value(batch, "missingExtras")).add(AEItemKey.of(Items.GOLD_INGOT), 1_024);
        queue.add(batch);
        var storage = networkStorage();
        when(storage.extract(any(), anyLong(), eq(Actionable.MODULATE), any())).thenAnswer(call -> call.getArgument(1));
        doAnswer(call -> Math.min((double) call.getArgument(0), 128_000.0))
            .when(host).extractAEPower(anyDouble(), any(), any());
        host.tickingRequest(mock(IGridNode.class), 1);
        field(host, "temperature", 50.0);
        when(storage.extract(any(), anyLong(), eq(Actionable.MODULATE), any())).thenReturn(0L);
        clearInvocations(host, storage);
        host.tickingRequest(mock(IGridNode.class), 1);

        verify(storage).extract(eq(AEItemKey.of(Items.GOLD_INGOT)), eq(768L), eq(Actionable.MODULATE), any());
        verify(host, never()).extractAEPower(anyDouble(), eq(Actionable.MODULATE), any());
        assertTrue((boolean) value(batch, "segmented"), "Incomplete input acquisition must leave a reloadable fallback");
        batch = reloadBatch(batch);
        queue.clear();
        queue.add(batch);
        assertEquals(25_600L, (int) value(batch, "progress") * 1_024L + (long) value(batch, "progressRemainder"));
        assertEquals(768, ((KeyCounter) value(batch, "missingExtras")).get(AEItemKey.of(Items.GOLD_INGOT)));

        when(storage.extract(any(), anyLong(), eq(Actionable.MODULATE), any())).thenAnswer(call -> call.getArgument(1));
        doReturn(0.0).when(host).extractAEPower(anyDouble(), any(), any());
        host.tickingRequest(mock(IGridNode.class), 1);
        assertFalse((boolean) value(batch, "segmented"));
        assertEquals(0, ((KeyCounter) value(batch, "missingExtras")).get(AEItemKey.of(Items.GOLD_INGOT)));
        assertEquals(1_024, ((KeyCounter) value(batch, "inputTotal")).get(AEItemKey.of(Items.GOLD_INGOT)));
        assertNotNull(reloadBatch(batch));
    }

    @Test
    void coolantModeRestoresADowngradedTaskWhenItsOriginalCoolingRequirementsAreMet() throws Exception {
        field(host, "temperature", 49.9);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object batch = sizedBatch(1_024, 6, 0, null);
        queue.add(batch);
        host.tickingRequest(mock(IGridNode.class), 1);
        assertTrue((boolean) value(batch, "segmented"));
        NEConfig.largeWorkstationTemperatureEnabled = false;
        coolant(Fluids.WATER, 6, FluidStack.EMPTY);
        input.setFluid(new FluidStack(Fluids.WATER, 1_000));
        host.tickingRequest(mock(IGridNode.class), 1);
        assertTrue((boolean) value(batch, "segmented"), "Coolant mode still requires active cooling");
        assertEquals(1_000, input.getFluidAmount());

        field(host, "activeCooling", true);
        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());
        host.tickingRequest(mock(IGridNode.class), 1);

        assertFalse((boolean) value(batch, "segmented"));
        assertEquals(1_024, ((KeyCounter) value(batch, "pendingOutput")).get(AEItemKey.of(Items.DIAMOND)));
        assertEquals(900, input.getFluidAmount());
        verify(host).extractAEPower(eq(1_024_000.0), eq(Actionable.MODULATE), any());
    }

    private Object reloadBatch(Object batch) throws Exception {
        var itemField = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        itemField.setAccessible(true);
        Object previousMissingContentItem = itemField.get(appeng.core.definitions.AEItems.MISSING_CONTENT);
        var missingItem = mock(net.neoforged.neoforge.registries.DeferredItem.class);
        when(missingItem.get()).thenReturn(Items.BARRIER);
        itemField.set(appeng.core.definitions.AEItems.MISSING_CONTENT, missingItem);
        try (var keys = mockStatic(appeng.api.stacks.AEKeyTypes.class);
             var internal = mockStatic(appeng.api.stacks.AEKeyTypesInternal.class)) {
            internal.when(appeng.api.stacks.AEKeyTypesInternal::getRegistry)
                .thenReturn(cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys.REGISTRY);
            keys.when(() -> appeng.api.stacks.AEKeyTypes.get(appeng.api.stacks.AEKeyType.items().getId()))
                .thenReturn(appeng.api.stacks.AEKeyType.items());
            var save = batch.getClass().getDeclaredMethod("save", net.minecraft.core.HolderLookup.Provider.class);
            save.setAccessible(true);
            var load = batch.getClass().getDeclaredMethod("load", net.minecraft.nbt.CompoundTag.class,
                net.minecraft.core.HolderLookup.Provider.class, long.class);
            load.setAccessible(true);
            var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            var saved = (net.minecraft.nbt.CompoundTag) save.invoke(batch, registries);
            Object restored = load.invoke(null, saved, registries, gameTime);
            assertNotNull(restored);
            assertEquals(saved, save.invoke(restored, registries));
            return restored;
        } finally {
            itemField.set(appeng.core.definitions.AEItems.MISSING_CONTENT, previousMissingContentItem);
        }
    }

    @Test
    void downgradedPortionPreservesPartialPaymentAndWaitsForItsOutputBeforeStartingTheNext() throws Exception {
        field(host, "temperature", 10.0);
        Object batch = sizedBatch(1_024, 9, 0, null);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        queue.add(batch);
        doAnswer(call -> Math.min((double) call.getArgument(0), 128_000.0))
            .when(host).extractAEPower(anyDouble(), any(), any());

        host.tickingRequest(mock(IGridNode.class), 1);
        assertEquals(25, value(batch, "progress"));
        assertEquals(0L, value(batch, "progressRemainder"));
        assertEquals(4_096, ((KeyCounter) value(batch, "inputTotal")).get(AEItemKey.of(Items.IRON_INGOT)));
        assertTrue(((KeyCounter) value(batch, "pendingOutput")).isEmpty());
        host.tickingRequest(mock(IGridNode.class), 1);
        assertEquals(768L, value(batch, "craftCount"));
        assertEquals(0, value(batch, "progress"));
        assertEquals(256, ((KeyCounter) value(batch, "pendingOutput")).get(AEItemKey.of(Items.DIAMOND)));
        clearInvocations(host);
        host.tickingRequest(mock(IGridNode.class), 1);
        verify(host, never()).extractAEPower(anyDouble(), eq(Actionable.MODULATE), any());
        assertEquals(768L, value(batch, "craftCount"));

        var storage = networkStorage();
        when(storage.insert(any(), anyLong(), eq(Actionable.MODULATE), any())).thenAnswer(call -> call.getArgument(1));
        host.tickingRequest(mock(IGridNode.class), 1);
        assertSame(batch, queue.getFirst(), "Delivering a portion cannot discard the remaining task");
        assertEquals(0, ((KeyCounter) value(batch, "pendingOutput")).get(AEItemKey.of(Items.DIAMOND)));
        host.tickingRequest(mock(IGridNode.class), 1);
        assertEquals(128_000L / 5, (int) value(batch, "progress") * 768L + (long) value(batch, "progressRemainder"));
    }

    @Test
    void returningADowngradedTaskDeliversItsCompletedPortionAndRefundsOnlyUnprocessedInputs() throws Exception {
        field(host, "temperature", 10.0);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object batch = sizedBatch(1_024, 9, 0, null);
        queue.add(batch);
        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());
        host.tickingRequest(mock(IGridNode.class), 1);
        var action = ECOLargeIntegratedWorkingStationBlockEntity.class.getDeclaredMethod("returnTaskInputs", java.util.UUID.class);
        action.setAccessible(true);
        var id = (java.util.UUID) value(batch, "interactionId");

        action.invoke(host, id);
        assertSame(batch, queue.getFirst(), "Unavailable output storage must not discard completed products");
        assertEquals(256, ((KeyCounter) value(batch, "pendingOutput")).get(AEItemKey.of(Items.DIAMOND)));
        var storage = networkStorage();
        var returned = new KeyCounter();
        when(storage.insert(any(), anyLong(), eq(Actionable.MODULATE), any())).thenAnswer(call -> {
            returned.add(call.getArgument(0), call.getArgument(1));
            return call.getArgument(1);
        });
        action.invoke(host, id);

        assertTrue(queue.isEmpty());
        assertEquals(256, returned.get(AEItemKey.of(Items.DIAMOND)));
        assertEquals(3_072, returned.get(AEItemKey.of(Items.IRON_INGOT)));
    }

    @Test
    void downgradedTaskAcquiresCompatibilityInputsForOnlyItsCurrent256CraftPortion() throws Exception {
        field(host, "temperature", 10.0);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object batch = sizedBatch(1_024, 9, 0, null);
        ((KeyCounter) value(batch, "missingExtras")).add(AEItemKey.of(Items.GOLD_INGOT), 1_024);
        queue.add(batch);
        var storage = networkStorage();
        when(storage.extract(any(), anyLong(), eq(Actionable.MODULATE), any())).thenAnswer(call -> call.getArgument(1));
        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());

        host.tickingRequest(mock(IGridNode.class), 1);

        verify(storage).extract(eq(AEItemKey.of(Items.GOLD_INGOT)), eq(256L), eq(Actionable.MODULATE), any());
        assertEquals(768, ((KeyCounter) value(batch, "missingExtras")).get(AEItemKey.of(Items.GOLD_INGOT)));
        assertEquals(0, ((KeyCounter) value(batch, "inputTotal")).get(AEItemKey.of(Items.GOLD_INGOT)));
        assertEquals(768L, value(batch, "craftCount"));
    }

    @Test
    void downgradeDoesNotExpandAFullQueueOrBypassOverheatProtection() throws Exception {
        field(host, "temperature", 10.0);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object first = sizedBatch(262_144, 9, 0, null);
        queue.add(first);
        for (int i = 1; i < 512; i++) queue.add(sizedBatch(262_144, 9, 0, null));
        host.tickingRequest(mock(IGridNode.class), 1);
        assertEquals(512, queue.size());
        assertTrue((boolean) value(first, "segmented"));
        assertEquals(262_144L, value(first, "craftCount"), "Missing power cannot consume a portion");
        field(host, "overheated", true);
        field(host, "temperature", 100.0);
        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());
        clearInvocations(host);
        host.tickingRequest(mock(IGridNode.class), 1);
        verify(host, never()).extractAEPower(anyDouble(), eq(Actionable.MODULATE), any());
        assertEquals(262_144L, value(first, "craftCount"));
    }

    @Test
    void cancelledDowngradedTaskDeliversItsBlockedPortionAndReturnsTheUnprocessedInputs() throws Exception {
        field(host, "temperature", 10.0);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        var job = java.util.UUID.randomUUID();
        Object batch = sizedBatch(1_024, 9, 0, job);
        queue.add(batch);
        var storage = networkStorage();
        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());
        try (var lifecycle = mockStatic(cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle.class)) {
            host.tickingRequest(mock(IGridNode.class), 1);
            assertEquals(256, ((KeyCounter) value(batch, "pendingOutput")).get(AEItemKey.of(Items.DIAMOND)));
            lifecycle.when(() -> cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle.isTerminated(level, job))
                .thenReturn(true);
            var returned = new KeyCounter();
            when(storage.insert(any(), anyLong(), eq(Actionable.MODULATE), any())).thenAnswer(call -> {
                returned.add(call.getArgument(0), call.getArgument(1));
                return call.getArgument(1);
            });
            clearInvocations(host);

            host.tickingRequest(mock(IGridNode.class), 1);

            assertTrue(queue.isEmpty());
            assertEquals(256, returned.get(AEItemKey.of(Items.DIAMOND)));
            assertEquals(3_072, returned.get(AEItemKey.of(Items.IRON_INGOT)));
            verify(host, never()).extractAEPower(anyDouble(), eq(Actionable.MODULATE), any());
        }
    }

    @Test
    void downgradedTaskMenuShows256ActiveCraftsWhileNewTaskCapacityStillFollowsTemperature() throws Exception {
        field(host, "temperature", 79.9);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object batch = sizedBatch(262_144, 9, 0, null);
        field(batch, "recipeId", null);
        queue.add(batch);
        when(level.registryAccess()).thenReturn(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        host.tickingRequest(mock(IGridNode.class), 1);
        var snapshot = ECOLargeIntegratedWorkingStationBlockEntity.class.getDeclaredMethod("getUiSnapshot", int.class);
        snapshot.setAccessible(true);

        var tag = (net.minecraft.nbt.CompoundTag) snapshot.invoke(host, 0);

        assertEquals(256, tag.getLong("parallelUsed"));
        assertEquals(65_536, tag.getLong("parallelCapacity"));
        assertEquals(1, tag.getInt("totalTasks"));
        assertEquals("gui.neoecoae.large_integrated_working_station.pause.power_missing", tag.getString("pause"));
        var settings = (net.minecraft.network.chat.contents.TranslatableContents) host.getBatchSettingsText().getContents();
        assertEquals(256, settings.getArgs()[0]);
    }

    @Test
    void refillingCannotAbsorbADowngradedTaskIntoAHighTierBatch() throws Exception {
        field(host, "temperature", 80.0);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object first = sizedBatch(65_536, 6, 0, null);
        Object next = sizedBatch(262_144, 9, 0, null);
        field(next, "segmented", true);
        queue.add(first);
        queue.add(next);

        host.tickingRequest(mock(IGridNode.class), 1);

        assertEquals(2, queue.size());
        assertEquals(65_536L, value(first, "craftCount"));
        assertEquals(262_144L, value(next, "craftCount"));
    }

    @Test
    void breakingADowngradedWorkstationDropsCompletedProductsAndUnprocessedInputs() throws Exception {
        field(host, "temperature", 10.0);
        var queue = (java.util.Deque<Object>) value(host, "pendingBatches");
        Object batch = sizedBatch(1_024, 9, 0, null);
        queue.add(batch);
        doAnswer(call -> call.getArgument(0)).when(host).extractAEPower(anyDouble(), any(), any());
        host.tickingRequest(mock(IGridNode.class), 1);
        doReturn(new appeng.util.inv.AppEngInternalInventory(0)).when(host).getInternalInventory();
        var upgrades = mock(appeng.api.upgrades.IUpgradeInventory.class);
        when(upgrades.iterator()).thenAnswer(call -> java.util.Collections.emptyIterator());
        field(host, "upgrades", upgrades);
        var drops = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();

        host.addAdditionalDrops(level, BlockPos.ZERO, drops);

        assertEquals(256, drops.stream().filter(stack -> stack.is(Items.DIAMOND)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum());
        assertEquals(3_072, drops.stream().filter(stack -> stack.is(Items.IRON_INGOT)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum());
        assertTrue(queue.isEmpty());
    }

    private appeng.api.storage.MEStorage networkStorage() {
        var grid = mock(appeng.api.networking.IGrid.class, RETURNS_DEEP_STUBS);
        when(host.getMainNode().getGrid()).thenReturn(grid);
        return grid.getStorageService().getInventory();
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
        double expected = enabled ? 10 + 3.3635 * 100 / 256 / 20 : 9.995;
        assertEquals(expected, host.getTemperature(), 1e-10);
        tick();
        assertEquals(expected - 0.005, host.getTemperature(), 1e-10, "Paid work is never heated twice");
        host.tickingRequest(mock(IGridNode.class), 1);
        tick();
        assertEquals(expected - 0.01, host.getTemperature(), 1e-10, "Completed output waiting for delivery generates no heat");
    }

    @Test
    void overclockSwitchAloneAndUnpaidBatchesCannotGenerateHeatWhileColdBatchesResumeAtBaseParallelism() throws Exception {
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
        double before = host.getTemperature();
        host.tickingRequest(mock(IGridNode.class), 1);
        tick();
        assertEquals(before + LargeWorkstationThermal.heatingPerSecond(before) * 100 / 256 / 20, host.getTemperature(), 1e-10);
        assertEquals(200, value(batch, "progress"));
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
        assertEquals(10 + 3.3635 * 100 / 256 / 20, host.getTemperature(), 1e-10);
        verify(host, never()).extractAEPower(anyDouble(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"20,3", "50,10.4", "80,24.7"})
    void fullyPaidPreheatUsesTheDesignedCurveAndStopsAtEveryThreshold(double target, double seconds) throws Exception {
        var charged = new java.util.ArrayList<Double>();
        doAnswer(call -> {
            double amount = call.getArgument(0);
            if (call.getArgument(1) == Actionable.MODULATE) charged.add(amount);
            return amount;
        }).when(host).extractAEPower(anyDouble(), any(), any());
        host.togglePreheating();
        int ticks = 0;
        while (host.getTemperature() < target && ticks < 600) {
            tick();
            ticks++;
            if (!(boolean) value(host, "preheating")) {
                assertTrue(host.getTemperature() == 20 || host.getTemperature() == 50 || host.getTemperature() == 80);
                if (host.getTemperature() < target) host.togglePreheating();
            }
        }
        assertEquals(target, host.getTemperature(), 1e-10);
        assertFalse((boolean) value(host, "preheating"));
        assertEquals(seconds, ticks / 20.0, 0.15);
        assertTrue(charged.stream().allMatch(amount -> amount > 0 && amount <= 1000));
        assertTrue(charged.getLast() < 1000, "The final tick only charges for heat needed to reach the threshold");
    }

    @Test
    void insufficientPreheatPowerScalesHeatAndDoesNotInventProcessingHeat() throws Exception {
        field(host, "temperature", 10.0);
        host.togglePreheating();
        doAnswer(call -> 250.0).when(host).extractAEPower(anyDouble(), any(), any());
        tick();
        assertEquals(10 + 6.727 * 0.25 / 20, host.getTemperature(), 1e-12);
        doReturn(0.0).when(host).extractAEPower(anyDouble(), any(), any());
        tick();
        assertEquals(10 + 6.727 * 0.25 / 20 - 0.005, host.getTemperature(), 1e-12);
        var snapshot = ECOLargeIntegratedWorkingStationBlockEntity.class.getDeclaredMethod("getUiSnapshot", int.class);
        snapshot.setAccessible(true);
        var tag = (net.minecraft.nbt.CompoundTag) snapshot.invoke(host, 0);
        assertEquals(0, tag.getDouble("processingHeatPerSecond"));
        assertEquals(6.727 * 0.25 / 20, tag.getDouble("preheatPerSecond"), 1e-12);
        assertEquals(0.005, tag.getDouble("naturalCoolingPerSecond"), 1e-12);
        assertEquals(host.getTemperature() - 10, tag.getDouble("temperatureChangePerSecond"), 1e-12);
    }

    @Test
    void thermalSnapshotUsesActualWorkAndExposesTheTemperatureBandWhileOverclockIsOff() throws Exception {
        field(host, "temperature", 50.0);
        field(host, "overclocked", false);
        for (int i = 0; i < 20; i++) tick();
        field(host, "temperature", 50.0);
        var snapshot = ECOLargeIntegratedWorkingStationBlockEntity.class.getDeclaredMethod("getUiSnapshot", int.class);
        snapshot.setAccessible(true);
        var tag = (net.minecraft.nbt.CompoundTag) snapshot.invoke(host, 0);
        assertEquals(6, tag.getInt("currentTier"));
        assertEquals(256, tag.getLong("parallelCapacity"));
        assertEquals(0, tag.getDouble("processingHeatPerSecond"));
        assertEquals(-0.1, tag.getDouble("temperatureChangePerSecond"), 1e-10);
        NEConfig.largeWorkstationTemperatureEnabled = false;
        tick();
        tag = (net.minecraft.nbt.CompoundTag) snapshot.invoke(host, 0);
        for (String key : List.of("processingHeatPerSecond", "preheatPerSecond", "coolingPerSecond",
            "naturalCoolingPerSecond", "temperatureChangePerSecond")) assertEquals(0, tag.getDouble(key), key);
    }

    private void coolant(Fluid fluid, int tier, FluidStack byproduct) {
        var recipe = new CoolingRecipe(SizedFluidIngredient.of(fluid, 100), byproduct, 1, tier);
        when(recipes.getAllRecipesFor(NERecipeTypes.COOLING.get())).thenReturn(List.of(
            new RecipeHolder<>(ResourceLocation.parse("test:cooling"), recipe)));
    }

    private void tick() {
        gameTime++;
        host.tickThermal(level, BlockPos.ZERO, Blocks.AIR.defaultBlockState());
    }

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
