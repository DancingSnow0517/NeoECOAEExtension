package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingCalculation;
import appeng.me.helpers.BaseActionSource;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialSource;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Permanent regressions from the player byproduct review; services expose primary outputs only. */
class ByproductReviewProbeTest {
    private static AEKey ore, ingot, slag, plate, goal, missing;

    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        ore = AEItemKey.of(Items.RAW_IRON);
        ingot = AEItemKey.of(Items.IRON_INGOT);
        slag = AEItemKey.of(Items.GRAVEL);
        plate = AEItemKey.of(Items.IRON_NUGGET);
        goal = AEItemKey.of(Items.BRICK);
        missing = AEItemKey.of(Items.DIAMOND);
    }

    static Stream<Arguments> cases() {
        return Stream.of(false, true).flatMap(cycles -> Stream.of(false, true).flatMap(delayed ->
            Stream.of(false, true).map(first -> Arguments.of(cycles, delayed, first))));
    }

    @ParameterizedTest @MethodSource("cases")
    void primaryAndByproductShareOnePhysicalFiring(boolean cycles, boolean delayed,
            boolean byproductFirst) throws Exception {
        var upstream = pattern(List.of(stack(ore)), stack(ingot), stack(slag));
        var convert = pattern(List.of(stack(ingot)), stack(plate));
        AEKey primary = delayed ? plate : ingot;
        var finish = pattern(byproductFirst ? List.of(stack(slag), stack(primary))
            : List.of(stack(primary), stack(slag)), stack(goal));
        var stock = stock(ore, 5);
        var result = plan(service(upstream, convert, finish), stock, goal, 5, cycles);
        assertEquals(PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());
        assertEquals(5L, result.plan().patternTimes().get(upstream));
        assertEquals(PlannerAmount.of(5), result.exactUsedItems().get(ore));
        assertEquals(5L, stock.get(ore), "Planning must not mutate real inventory");
        var provenance = result.provenance();
        provenance.requireComplete();
        assertTrue(provenance.allocationsFor(finish).stream().anyMatch(a -> a.material().equals(slag)
            && a.source().equals(new MaterialSource.PatternOutput(upstream, false))));
        replay(result, stock, goal, 5);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void jointOutputAvoidsAnUnnecessaryRecipeWithMissingInputs(boolean cycles) throws Exception {
        var upstream = pattern(List.of(stack(ore)), stack(ingot), stack(slag));
        var convert = pattern(List.of(stack(ingot)), stack(plate));
        var unnecessary = pattern(List.of(stack(missing)), stack(slag));
        var finish = pattern(List.of(stack(slag), stack(plate)), stack(goal));
        var result = plan(service(upstream, convert, unnecessary, finish), stock(ore, 1), goal, 1, cycles);
        assertEquals(PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());
        assertFalse(result.plan().patternTimes().containsKey(unnecessary));
        assertFalse(result.exactUsedItems().containsKey(missing));
        assertTrue(result.exactMissingItems().isEmpty());
        replay(result, stock(ore, 1), goal, 1);
    }

    @Test void byproductShortfallNeverAddsFiringsToItsSource() throws Exception {
        var upstream = pattern(List.of(stack(ore)), stack(ingot), stack(slag));
        var finish = pattern(List.of(new GenericStack(slag, 3), stack(ingot)), stack(goal));
        var result = plan(service(upstream, finish), stock(ore, 10), goal, 1, true);
        assertEquals(PlanningStatus.MISSING_ITEMS, result.status());
        assertEquals(1L, result.plan().patternTimes().get(upstream));
        assertEquals(PlannerAmount.of(2), result.exactMissingItems().get(slag));
        assertEquals(PlannerAmount.ONE, result.exactUsedItems().get(ore));
    }

    @Test void requestingOnlyAByproductNeverStartsThePrimaryRecipe() throws Exception {
        var upstream = pattern(List.of(stack(ore)), stack(ingot), stack(slag));
        var result = plan(service(upstream), stock(ore, 10), slag, 3, true);
        assertEquals(PlanningStatus.MISSING_ITEMS, result.status());
        assertTrue(result.exactPatternTimes().isEmpty());
        assertEquals(PlannerAmount.of(3), result.exactMissingItems().get(slag));
    }

    @Test void equivalentJointPatternsPublishAClosedLargePlanWithinTheSharedBudget() throws Exception {
        var hydrogenFirst = pattern(List.of(new GenericStack(ore, 3000)),
            new GenericStack(ingot, 2000), new GenericStack(slag, 1000));
        var oxygenFirst = pattern(List.of(new GenericStack(ore, 3000)),
            new GenericStack(slag, 1000), new GenericStack(ingot, 2000));
        var finish = pattern(List.of(new GenericStack(ingot, 27_615_000),
            new GenericStack(slag, 1_503_000)), stack(goal));
        var inventory = stock(ore, 32_049_000);
        inventory.add(ingot, 6_249_500);
        inventory.add(slag, 16_934_750);
        var session = new ECOCraftingPlannerService().createSession(
            service(hydrogenFirst, oxygenFirst, finish), goal, inventory, true);
        var field = session.getClass().getDeclaredField("planningBudget");
        field.setAccessible(true);
        field.set(session, new ECOPlanningBudget(ECOCancellation.NONE, 5000, Long.MAX_VALUE, () -> 0L));
        var result = session.plan(1, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());
        long runs = result.plan().patternTimes().getOrDefault(hydrogenFirst, 0L)
            + result.plan().patternTimes().getOrDefault(oxygenFirst, 0L);
        assertEquals(10_683L, runs);
        assertEquals(PlannerAmount.of(32_049_000), result.exactUsedItems().get(ore));
        assertEquals(1L, result.plan().patternTimes().get(finish));
        assertTrue(result.exactMissingItems().isEmpty());
        assertNotNull(result.executionPlan());
        result.provenance().requireComplete();
        var schedule = ECOExecutionSchedule.from(result.components(), result.executionComponentOrder(),
            result.plan().patternTimes(), result.provenance());
        int supplier = -1, consumer = -1;
        for (int i = 0; i < schedule.phases().size(); i++) {
            var tasks = schedule.phases().get(i).patternSet();
            if (tasks.contains(hydrogenFirst) || tasks.contains(oxygenFirst)) supplier = i;
            if (tasks.contains(finish)) consumer = i;
        }
        assertTrue(supplier >= 0 && consumer > supplier, "Joint supply must execute before its consumer");
        assertEquals(32_049_000L, inventory.get(ore), "Planning must preserve the network snapshot");
    }

    @Test void leftoverJointDemandUsesItsOwnPrimaryRecipeWithoutRepeatingTheSource() throws Exception {
        var upstream = pattern(List.of(stack(ore)), stack(ingot), stack(slag));
        var supplemental = pattern(List.of(stack(missing)), stack(slag));
        var finish = pattern(List.of(new GenericStack(slag, 3), stack(ingot)), stack(goal));
        var inventory = stock(ore, 1);
        inventory.add(missing, 2);
        var result = plan(service(upstream, supplemental, finish), inventory, goal, 1, true);
        assertEquals(PlanningStatus.SUCCESS, result.status());
        assertEquals(1L, result.plan().patternTimes().get(upstream));
        assertEquals(2L, result.plan().patternTimes().get(supplemental));
        replay(result, inventory, goal, 1);
    }

    @Test void retryDiscardsFailedRoutesJointCreditAndReservations() throws Exception {
        var rejected = pattern(List.of(stack(missing)), stack(ingot), new GenericStack(slag, 2));
        var accepted = pattern(List.of(stack(ore)), stack(ingot), stack(slag));
        var finish = pattern(List.of(new GenericStack(slag, 2), stack(ingot)), stack(goal));
        var result = plan(service(rejected, accepted, finish), stock(ore, 1), goal, 1, true);
        assertEquals(PlanningStatus.MISSING_ITEMS, result.status());
        assertFalse(result.plan().patternTimes().containsKey(rejected));
        assertFalse(result.exactUsedItems().containsKey(missing));
        assertEquals(1L, result.plan().patternTimes().get(accepted));
        assertEquals(PlannerAmount.ONE, result.exactMissingItems().get(slag));
        assertTrue(result.provenance().supplierAmountsOf(slag).keySet().stream().noneMatch(source ->
            source instanceof MaterialSource.PatternOutput output && output.pattern() == rejected));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void ancestorCannotBorrowItsDescendantsByproductWithoutSeed(boolean cycles) throws Exception {
        var upstream = pattern(List.of(stack(ore)), stack(ingot), stack(slag));
        var convert = pattern(List.of(stack(slag)), stack(ore));
        var finish = pattern(List.of(stack(ingot)), stack(goal));
        var svc = service(upstream, convert, finish);
        var unseeded = plan(svc, new KeyCounter(), goal, 3, cycles);
        assertNotEquals(PlanningStatus.SUCCESS, unseeded.status());
        if (cycles) {
            var seeded = plan(svc, stock(slag, 1), goal, 3, true);
            assertEquals(PlanningStatus.SUCCESS, seeded.status(), seeded.trace().diagnostics().toString());
            assertFalse(seeded.cycles().isEmpty());
            assertNotNull(seeded.executionPlan());
        }
    }

    @Test void randomizedSmallNetworksContainVanillaSuccessesAndReplayJointSupply() throws Exception {
        Random random = new Random(20261009);
        int vanillaSuccesses = 0;
        for (int sample = 0; sample < 80; sample++) {
            int depth = 1 + random.nextInt(3);
            long amount = 1 + random.nextInt(4);
            long primaryPerRun = 1 + random.nextInt(3), jointPerRun = 1 + random.nextInt(3);
            long primaryDemand = 1 + random.nextInt(3), jointDemand = 1 + random.nextInt(3);
            var upstream = pattern(List.of(stack(ore)), new GenericStack(ingot, primaryPerRun),
                new GenericStack(slag, jointPerRun));
            var patterns = new ArrayList<IPatternDetails>();
            patterns.add(upstream);
            AEKey previous = ingot;
            var intermediates = List.of(plate, AEItemKey.of(Items.STONE), AEItemKey.of(Items.GLASS));
            for (int i = 0; i < depth; i++) {
                AEKey next = intermediates.get(i);
                patterns.add(pattern(List.of(stack(previous)), stack(next)));
                previous = next;
            }
            var primaryInput = new GenericStack(previous, primaryDemand);
            var jointInput = new GenericStack(slag, jointDemand);
            patterns.add(pattern(random.nextBoolean() ? List.of(primaryInput, jointInput)
                : List.of(jointInput, primaryInput), stack(goal)));
            if (random.nextBoolean()) patterns.add(pattern(List.of(stack(missing)), stack(slag)));
            var svc = service(patterns.toArray(IPatternDetails[]::new));
            var inventory = stock(ore, 1 + random.nextInt(15));
            if (random.nextBoolean()) inventory.add(slag, random.nextInt(5));
            var vanilla = vanillaPlan(svc, inventory, goal, amount);
            var eco = plan(svc, inventory, goal, amount, true);
            if (!vanilla.simulation()) {
                vanillaSuccesses++;
                assertEquals(PlanningStatus.SUCCESS, eco.status(), "sample=" + sample + " " + eco.trace().diagnostics());
            }
            if (eco.status() == PlanningStatus.SUCCESS) replay(eco, inventory, goal, amount);
        }
        assertTrue(vanillaSuccesses >= 10, "Random comparison must exercise successful vanilla plans");
    }

    private static ICraftingPlan vanillaPlan(ICraftingService service, KeyCounter inventory, AEKey key, long amount)
            throws Exception {
        var grid = mock(IGrid.class);
        var node = mock(IGridNode.class);
        var storage = mock(IStorageService.class);
        var requester = mock(ICraftingSimulationRequester.class);
        when(grid.getCraftingService()).thenReturn(service);
        when(grid.getStorageService()).thenReturn(storage);
        when(node.getGrid()).thenReturn(grid);
        when(requester.getGridNode()).thenReturn(node);
        when(requester.getActionSource()).thenReturn(new BaseActionSource());
        when(storage.getCachedInventory()).thenReturn(inventory);
        var calculation = new CraftingCalculation(mock(Level.class), grid, requester,
            new GenericStack(key, amount), CalculationStrategy.REPORT_MISSING_ITEMS);
        // Run AE2's actual synchronous calculation without registering a server tick callback.
        var running = CraftingCalculation.class.getDeclaredField("running");
        running.setAccessible(true);
        running.setBoolean(calculation, true);
        var compute = CraftingCalculation.class.getDeclaredMethod("computePlan");
        compute.setAccessible(true);
        return (ICraftingPlan) compute.invoke(calculation);
    }

    private static void replay(ECOPlanningResult result, KeyCounter inventory, AEKey output, long amount) {
        var actual = new LinkedHashMap<AEKey, Long>();
        result.exactUsedItems().forEach((key, count) -> {
            assertTrue(inventory.get(key) >= count.longValueExact());
            actual.put(key, count.longValueExact());
        });
        var schedule = ECOExecutionSchedule.from(result.components(), result.executionComponentOrder(),
            result.plan().patternTimes(), result.provenance());
        for (var phase : schedule.phases()) {
            assertEquals(ECOExecutionSchedule.Type.DAG, phase.type());
            for (var pattern : phase.patternSet()) {
                long times = result.plan().patternTimes().get(pattern);
                for (var input : pattern.getInputs()) {
                    var stack = input.getPossibleInputs()[0];
                    long needed = stack.amount() * input.getMultiplier() * times;
                    assertTrue(actual.getOrDefault(stack.what(), 0L) >= needed,
                        "Schedule used output before its supplier: " + stack.what());
                    actual.merge(stack.what(), -needed, Long::sum);
                }
                for (var stack : pattern.getOutputs()) actual.merge(stack.what(), stack.amount() * times, Long::sum);
            }
        }
        assertTrue(actual.getOrDefault(output, 0L) >= amount);
    }

    private static ECOPlanningResult plan(ICraftingService svc, KeyCounter stock, AEKey key, long amount,
            boolean cycles) throws Exception {
        return new ECOCraftingPlannerService().createSession(svc, key, stock, cycles)
            .plan(amount, false, ECOCancellation.NONE);
    }

    private static KeyCounter stock(AEKey key, long amount) {
        var stock = new KeyCounter();
        stock.add(key, amount);
        return stock;
    }

    private static GenericStack stack(AEKey key) { return new GenericStack(key, 1); }

    private static IPatternDetails pattern(List<GenericStack> inputs, GenericStack... outputs) {
        var details = mock(IPatternDetails.class);
        var raw = inputs.stream().map(stack -> {
            var input = mock(IPatternDetails.IInput.class);
            when(input.getMultiplier()).thenReturn(1L);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[] {stack});
            when(input.isValid(any(), any())).thenAnswer(call -> stack.what().equals(call.getArgument(0)));
            return input;
        }).toArray(IPatternDetails.IInput[]::new);
        when(details.getInputs()).thenReturn(raw);
        when(details.getOutputs()).thenReturn(List.of(outputs));
        when(details.getPrimaryOutput()).thenReturn(outputs[0]);
        return details;
    }

    private static ICraftingService service(IPatternDetails... patterns) {
        Map<AEKey, List<IPatternDetails>> index = new LinkedHashMap<>();
        for (var pattern : patterns) {
            index.computeIfAbsent(pattern.getPrimaryOutput().what(), ignored -> new ArrayList<>()).add(pattern);
        }
        var svc = mock(ICraftingService.class);
        when(svc.getCraftingFor(any())).thenAnswer(call -> index.getOrDefault(call.getArgument(0), List.of()));
        return svc;
    }
}
