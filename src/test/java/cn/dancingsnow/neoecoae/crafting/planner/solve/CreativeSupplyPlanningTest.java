package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.me.storage.NetworkStorage;
import com.glodblock.github.extendedae.common.inventory.InfinityCellInventory;
import com.glodblock.github.extendedae.common.items.ItemInfinityCell;
import com.glodblock.github.extendedae.common.tileentities.TileExDrive;
import com.moakiee.ae2lt.item.FixedInfiniteCellItem;
import com.moakiee.ae2lt.item.InfiniteStorageCellItem;
import com.moakiee.ae2lt.me.cell.FixedInfiniteCellInventory;
import com.moakiee.thunderbolt.core.storage.cell.IndexedStorageCellInventory;
import com.moakiee.thunderbolt.core.storage.cell.ByteTracker;
import com.moakiee.thunderbolt.core.storage.cell.IndexedStorage;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOBigOrderPlanner;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlannerInventory;
import cn.dancingsnow.neoecoae.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.impl.storage.ECOCreativeCell;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreativeSupplyPlanningTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void onlyOnlineMountedEcoCreativeCellsConferUnlimitedSupply() {
        AEKey key = AEItemKey.of(Items.IRON_INGOT);
        KeyCounter stock = new KeyCounter();
        stock.set(key, Long.MAX_VALUE);
        IGrid grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        when(grid.getStorageService().getInventory().getAvailableStacks()).thenReturn(stock);
        when(grid.getMachines(ECODriveBlockEntity.class)).thenReturn(Set.of());
        assertFalse(counter(ECOPlannerInventory.capture(grid)).isUnbounded(key),
            "A Long.MAX_VALUE listing alone, including a vanilla drive, is never the capability");
        var drive = mock(ECODriveBlockEntity.class);
        var cell = mock(ECOCreativeCell.class);
        when(cell.configuredKeys()).thenReturn(Set.of(key));
        when(drive.getCellInventory()).thenReturn(cell);
        when(grid.getMachines(ECODriveBlockEntity.class)).thenReturn(Set.of(drive));
        when(drive.isMounted()).thenReturn(true);
        assertFalse(counter(ECOPlannerInventory.capture(grid)).isUnbounded(key));
        when(drive.isOnline()).thenReturn(true);
        var captured = ECOPlannerInventory.capture(grid);
        assertTrue(counter(captured).isUnbounded(key));
        assertEquals(Long.MAX_VALUE, captured.toKeyCounter().get(key));
        when(drive.isMounted()).thenReturn(false);
        assertFalse(counter(ECOPlannerInventory.capture(grid)).isUnbounded(key));
        when(drive.isMounted()).thenReturn(true);
        when(drive.getCellInventory()).thenReturn(null);
        assertFalse(counter(ECOPlannerInventory.capture(grid)).isUnbounded(key));
        assertTrue(counter(captured).isUnbounded(key), "An async calculation owns an immutable snapshot");
    }

    @Test void demandAboveLongMaxIsReservedExactlyAndStillRequiresSegmentation() throws Exception {
        AEKey key = AEItemKey.of(Items.IRON_INGOT);
        var infinite = solve(Long.MAX_VALUE, snapshot(key, true));
        assertEquals(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, infinite.status());
        assertTrue(infinite.state().missingAmounts().isEmpty());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO),
            infinite.state().usedAmounts().get(key).toBigInteger());
        assertNull(ECOPlanMaterialValidator.firstDeficit(infinite.state(), AEItemKey.of(Items.DIAMOND), Long.MAX_VALUE));
        assertFalse(infinite.state().executionAmountIssues().isEmpty());

        var finite = solve(Long.MAX_VALUE, snapshot(key, false));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), finite.state().missingAmounts().get(key).toBigInteger());
    }

    @Test void bigOrderSearchBuildsACompleteLongChildFromUnlimitedStock() throws Exception {
        var stock = snapshot(AEItemKey.of(Items.IRON_INGOT), true);
        var bridge = new AE2CraftingPlanBridge();
        var answer = ECOBigOrderPlanner.search(Long.MAX_VALUE, Long.MAX_VALUE, amount -> {
            var solved = solve(amount, stock);
            var goal = AEItemKey.of(Items.DIAMOND);
            var plan = solved.status() == PlanningStatus.SUCCESS
                ? bridge.success(goal, amount, false, false, solved.state()) : bridge.unsupported(goal, amount);
            return new ECOPlanningResult(solved.status(), plan, solved.trace(), List.of(), 0);
        });
        assertFalse(answer.fatal());
        assertEquals(PlanningStatus.SUCCESS, answer.result().status());
        var plan = answer.result().plan();
        assertTrue(plan.finalOutput().amount() < Long.MAX_VALUE);
        assertEquals(Math.multiplyExact(2, plan.finalOutput().amount()), plan.usedItems().get(AEItemKey.of(Items.IRON_INGOT)));
        assertTrue(plan.missingItems().isEmpty());
        // A new child/probe must not inherit the preceding child's reservations.
        assertEquals(PlanningStatus.SUCCESS, solve(plan.finalOutput().amount(), stock).status());
    }

    @Test void unboundedReservationsSurviveCopiesButGoalStockCanBeExcluded() {
        AEKey key = AEItemKey.of(Items.IRON_INGOT);
        var original = counter(snapshot(key, true));
        var huge = PlannerAmount.of(BigInteger.TEN.pow(30));
        var copy = original.copy();
        assertEquals(huge, copy.available(key, huge));
        copy.remove(key, huge);
        assertEquals(huge, copy.available(key, huge));
        var replaced = new PlannerCounter();
        replaced.replaceFrom(copy);
        assertTrue(replaced.isUnbounded(key));
        replaced.set(key, PlannerAmount.ZERO);
        assertEquals(PlannerAmount.ZERO, replaced.available(key, huge));
        assertTrue(original.isUnbounded(key));
    }

    @Test void cycleBoundaryCanReserveCreativeStockAfterEarlierReservations() throws Exception {
        AEKey key = AEItemKey.of(Items.IRON_INGOT);
        var inventory = snapshot(key, true);
        var base = new SolveState(inventory);
        base.used.add(key, Long.MAX_VALUE);
        var network = new CompiledNetwork(key, Map.of(key, List.of()), Set.of(), 0, 0);
        var cycle = mock(cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent.class);
        var outcome = new ExternalDemandPlanner(new AcyclicCraftingSolver()).solveDemands(network, cycle,
            Map.of(key, 64L), inventory.toKeyCounter(), base, Map.of(key, Long.MAX_VALUE), Set.of(key),
            false, ECOCancellation.NONE);
        assertTrue(outcome.solved());
        assertEquals(64, outcome.directReservations().get(key));
        assertTrue(outcome.delegatedCycleDemands().isEmpty(), "Creative supply does not need a cycle producer");
        assertTrue(outcome.missingLeaves().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("thirdPartyInfiniteCellHosts")
    void thirdPartyInfiniteCellsPlanAndExtractBeyondDisplayedStock(Class<? extends IChestOrDrive> hostType,
                                                                  boolean extendedAe) throws Exception {
        var cell = infiniteCell(extendedAe);
        AEKey input = cell.getAvailableStacks().keySet().iterator().next();
        long listed = cell.getAvailableStacks().get(input);
        long demand = listed + 1;
        try (var mods = optionalMods()) {
            var grid = cellGrid(cell, hostType);
            assertTrue(grid.getMachines(IChestOrDrive.class).isEmpty());
            assertEquals(1, grid.getMachines(hostType).size());
            var captured = ECOPlannerInventory.capture(grid);
            assertTrue(captured.isUnbounded(input));
            assertEquals(Set.of(input), ECOPlannerInventory.collectUnboundedKeys(grid));
            var finite = PlannerInventorySnapshot.of(cell.getAvailableStacks());
            assertFalse(solve(demand, finite, input).state().missingAmounts().isEmpty());
            var solved = solve(demand, captured, input);
            assertEquals(PlanningStatus.SUCCESS, solved.status());
            assertTrue(solved.state().missingAmounts().isEmpty());
            assertEquals(demand * 2, solved.state().usedItems().get(input));
            var network = grid.getStorageService().getInventory();
            assertEquals(demand * 2, network.extract(input, demand * 2, Actionable.MODULATE, IActionSource.empty()));
            assertEquals(demand * 2, network.extract(input, demand * 2, Actionable.MODULATE, IActionSource.empty()));
            assertEquals(listed, cell.getAvailableStacks().get(input));
            assertEquals(PlanningStatus.SUCCESS, solve(demand, ECOPlannerInventory.capture(grid), input).status());
            var exact = solve(Long.MAX_VALUE, captured, input);
            assertEquals(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, exact.status());
            assertTrue(exact.state().missingAmounts().isEmpty());
            assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO),
                exact.state().usedAmounts().get(input).toBigInteger());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void thirdPartyInfiniteSupplyRequiresPoweredHostAndMountedCell(boolean extendedAe) {
        var cell = infiniteCell(extendedAe);
        AEKey input = cell.getAvailableStacks().keySet().iterator().next();
        try (var mods = optionalMods()) {
            var grid = cellGrid(cell);
            var host = grid.getMachines(DriveBlockEntity.class).iterator().next();
            var captured = ECOPlannerInventory.capture(grid);
            assertTrue(captured.isUnbounded(input));
            when(host.isPowered()).thenReturn(false);
            assertFalse(ECOPlannerInventory.capture(grid).isUnbounded(input));
            when(host.isPowered()).thenReturn(true);
            when(host.getOriginalCellInventory(0)).thenReturn(null);
            assertFalse(ECOPlannerInventory.capture(grid).isUnbounded(input));
            assertTrue(captured.isUnbounded(input));
        }
    }

    @Test void ae2LtOuterCellIsFiniteAndCaptureNeverConsumesItsResult() throws Exception {
        var stack = cellStack(mock(FixedInfiniteCellItem.class));
        AEKey input = AEItemKey.of(Items.IRON_INGOT);
        var saveProvider = mock(ISaveProvider.class);
        FixedInfiniteCellInventory cell;
        try (var definition = mockStatic(FixedInfiniteCellItem.class)) {
            definition.when(() -> FixedInfiniteCellItem.getEffectiveKey(stack)).thenReturn(input);
            definition.when(() -> FixedInfiniteCellItem.isOuterCell(stack)).thenReturn(true);
            cell = new FixedInfiniteCellInventory(stack, 32, saveProvider);
        }
        try (var mods = optionalMods()) {
            var grid = cellGrid(cell);
            var captured = ECOPlannerInventory.capture(grid);
            assertFalse(captured.isUnbounded(input));
            assertFalse(FixedInfiniteCellItem.isResultConsumed(stack));
            assertEquals(1, cell.getAvailableStacks().get(input));
            assertEquals(1, cell.extract(input, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()));
            verifyNoInteractions(saveProvider);
            assertEquals(BigInteger.ONE, solve(1, captured, input).state().missingAmounts().get(input).toBigInteger());
            assertEquals(1, cell.extract(input, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty()));
            assertTrue(FixedInfiniteCellItem.isResultConsumed(stack));
            verify(saveProvider).saveChanges();
            assertEquals(0, cell.extract(input, 1, Actionable.MODULATE, IActionSource.empty()));
            assertTrue(ECOPlannerInventory.collectUnboundedKeys(grid).isEmpty());
        }
    }

    @Test void ae2LtInfiniteStorageCellStillConsumesFiniteStock() {
        var item = mock(InfiniteStorageCellItem.class, CALLS_REAL_METHODS);
        doAnswer(invocation -> {
            IndexedStorage storage = invocation.getArgument(1);
            var tracker = new ByteTracker(storage::getTotalTypes);
            tracker.configure(1, Integer.MAX_VALUE, -1L, Long.MAX_VALUE);
            return tracker;
        }).when(item).createByteTracker(any(), any());
        var cell = new IndexedStorageCellInventory(cellStack(item), item, mock(HolderLookup.Provider.class), null);
        AEKey input = AEItemKey.of(Items.IRON_INGOT);
        assertEquals(64, cell.insert(input, 64, Actionable.MODULATE, IActionSource.empty()));
        try (var mods = optionalMods()) {
            var captured = ECOPlannerInventory.capture(cellGrid(cell));
            assertFalse(captured.isUnbounded(input));
            assertEquals(64, captured.toKeyCounter().get(input));
            assertEquals(64, cell.extract(input, 65, Actionable.MODULATE, IActionSource.empty()));
            assertEquals(0, cell.extract(input, 1, Actionable.MODULATE, IActionSource.empty()));
        }
    }

    @Test void finiteSaturatedStorageIsNotAnInfiniteSource() throws Exception {
        AEKey input = AEItemKey.of(Items.IRON_INGOT);
        var cell = mock(StorageCell.class);
        KeyCounter stock = new KeyCounter();
        stock.set(input, Long.MAX_VALUE);
        when(cell.getAvailableStacks()).thenReturn(stock);
        doAnswer(invocation -> {
            KeyCounter output = invocation.getArgument(0);
            output.addAll(stock);
            return null;
        }).when(cell).getAvailableStacks(any(KeyCounter.class));
        try (var mods = optionalMods()) {
            var captured = ECOPlannerInventory.capture(cellGrid(cell));
            assertFalse(captured.isUnbounded(input));
            assertEquals(BigInteger.valueOf(Long.MAX_VALUE),
                solve(Long.MAX_VALUE, captured, input).state().missingAmounts().get(input).toBigInteger());
            verify(cell, never()).extract(any(), anyLong(), any(), any());
        }
    }

    @Test void extendedAeFluidCellPreservesUnlimitedUnitConversion() throws Exception {
        AEKey input = AEFluidKey.of(Fluids.WATER);
        var cell = extendedAeCell(input);
        try (var mods = optionalMods()) {
            var captured = ECOPlannerInventory.capture(cellGrid(cell));
            assertTrue(captured.isUnbounded(input));
            assertEquals((long) Integer.MAX_VALUE * input.getAmountPerUnit(), cell.getAvailableStacks().get(input));
            long demand = cell.getAvailableStacks().get(input) + 1;
            assertEquals(PlanningStatus.SUCCESS, solve(demand, captured, input).status());
            assertEquals(Long.MAX_VALUE, cell.extract(input, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty()));
        }
    }

    private static StorageCell infiniteCell(boolean extendedAe) {
        if (extendedAe) return extendedAeCell(AEItemKey.of(Items.IRON_INGOT));
        var stack = cellStack(mock(FixedInfiniteCellItem.class));
        FixedInfiniteCellItem.setType(stack, (byte) 2);
        return new FixedInfiniteCellInventory(stack, 32, null);
    }

    private static InfinityCellInventory extendedAeCell(AEKey key) {
        var item = mock(ItemInfinityCell.class);
        when(item.getRecord()).thenReturn(key);
        return new InfinityCellInventory(cellStack(item));
    }

    private static ItemStack cellStack(Item item) {
        var stack = spy(new ItemStack(Items.PAPER));
        doReturn(item).when(stack).getItem();
        return stack;
    }

    private static MockedStatic<ModList> optionalMods() {
        var mods = mock(ModList.class);
        when(mods.isLoaded("ae2lt")).thenReturn(true);
        when(mods.isLoaded("extendedae")).thenReturn(true);
        var mocked = mockStatic(ModList.class);
        mocked.when(ModList::get).thenReturn(mods);
        return mocked;
    }

    private static Stream<Arguments> thirdPartyInfiniteCellHosts() {
        return Stream.of(DriveBlockEntity.class, MEChestBlockEntity.class, TileExDrive.class)
            .flatMap(hostType -> Stream.of(false, true).map(extendedAe -> Arguments.of(hostType, extendedAe)));
    }

    private static IGrid cellGrid(StorageCell cell) {
        return cellGrid(cell, DriveBlockEntity.class);
    }

    private static <T extends IChestOrDrive> IGrid cellGrid(StorageCell cell, Class<T> hostType) {
        var host = mock(hostType);
        when(host.isPowered()).thenReturn(true);
        when(host.getCellCount()).thenReturn(1);
        when(host.getOriginalCellInventory(0)).thenReturn(cell);
        var network = new NetworkStorage();
        network.mount(0, cell);
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        when(grid.getStorageService().getInventory()).thenReturn(network);
        when(grid.getMachineClasses()).thenReturn(Set.of(hostType));
        when(grid.getMachines(ECODriveBlockEntity.class)).thenReturn(Set.of());
        when(grid.getMachines(IChestOrDrive.class)).thenReturn(Set.of());
        when(grid.getMachines(hostType)).thenReturn(Set.of(host));
        return grid;
    }

    private static PlannerCounter counter(PlannerInventorySnapshot snapshot) {
        var result = new PlannerCounter();
        snapshot.initialize(result);
        return result;
    }

    private static PlannerInventorySnapshot snapshot(AEKey key, boolean unlimited) {
        KeyCounter stock = new KeyCounter();
        stock.set(key, Long.MAX_VALUE);
        return PlannerInventorySnapshot.of(stock, unlimited ? Set.of(key) : Set.of());
    }

    private static AcyclicCraftingSolver.Outcome solve(long amount, PlannerInventorySnapshot inventory)
            throws InterruptedException {
        return solve(amount, inventory, AEItemKey.of(Items.IRON_INGOT));
    }

    private static AcyclicCraftingSolver.Outcome solve(long amount, PlannerInventorySnapshot inventory, AEKey input)
            throws InterruptedException {
        AEKey output = AEItemKey.of(Items.DIAMOND);
        var details = mock(IPatternDetails.class);
        var ingredient = mock(IPatternDetails.IInput.class);
        when(ingredient.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(input, 2)});
        when(ingredient.getMultiplier()).thenReturn(1L);
        when(details.getInputs()).thenReturn(new IPatternDetails.IInput[]{ingredient});
        var outputs = List.of(new GenericStack(output, 1));
        when(details.getOutputs()).thenReturn(outputs);
        var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        var pattern = new CompiledPattern(0, details, output, PlannerAmount.of(1),
            List.of(new CompiledInput(ingredient, input, 2, false, null)), outputs, true, null, false, semantics);
        var network = new CompiledNetwork(output, Map.of(output, List.of(pattern), input, List.of()), Set.of(), 1, 1);
        return new AcyclicCraftingSolver().solve(network, new AcyclicRoutePlan(List.of(output, input)),
            inventory, amount, Map.of(), Set.of(), false, ECOCancellation.NONE);
    }
}
