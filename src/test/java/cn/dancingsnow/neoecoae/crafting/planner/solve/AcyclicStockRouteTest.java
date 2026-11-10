package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import java.util.LinkedHashMap;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcyclicStockRouteTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void aggregatesSharedStockBeforeCuttingReverseRecipe() throws Exception {
        var outcome = solve(10L, 0L, 0L);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertEquals(10L, outcome.state().usedItems().get(AEItemKey.of(Items.IRON_NUGGET)));
        assertEquals(2, outcome.state().patternTimes().size(), "The reverse recipe must not run");
    }

    @Test
    void sharedStockShortfallDoesNotBecomeAFreeCraft() throws Exception {
        // Nine nuggets cover the ingot, but the final recipe also consumes one directly.
        assertEquals(PlanningStatus.PARTIAL_UNSUPPORTED, solve(9L, 0L, 0L).status());
    }

    @Test
    void aCycleWithoutStockStillFallsBack() throws Exception {
        assertEquals(PlanningStatus.PARTIAL_UNSUPPORTED, solve(0L, 0L, 0L).status());
    }

    @Test
    void storedFinalOutputCannotBreakTheCycle() throws Exception {
        assertEquals(PlanningStatus.PARTIAL_UNSUPPORTED, solve(0L, 0L, 1L).status());
    }

    @Test
    void reopensAnInsufficientStockLeafAndRetriesFromFreshInventory() throws Exception {
        // One stored ingot is insufficient for the two needed; craft the other from nine nuggets.
        var outcome = solve(10L, 1L, 0L, 2L);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertEquals(1L, outcome.state().usedItems().get(AEItemKey.of(Items.IRON_INGOT)));
        assertEquals(10L, outcome.state().usedItems().get(AEItemKey.of(Items.IRON_NUGGET)));
        assertEquals(2, outcome.state().patternTimes().size());
    }

    @Test
    void changedByproductCreditsInvalidateCachedFiringAmounts() throws Exception {
        var network = retryNetwork();
        for (long amount : new long[] {1L, 1L << 62}) {
            var outcome = retry(new AcyclicCraftingSolver(), network, amount, 0L, 0L, amount);
            assertEquals(amount == 1L ? PlanningStatus.SUCCESS : PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE,
                outcome.status());
            assertRetryAmounts(network, outcome, amount, amount, 0L);
            assertEquals(PlannerAmount.of(amount).multiply(4),
                outcome.state().demandAmountFor(AEItemKey.of(Items.IRON_INGOT)));
        }
    }

    @Test
    void multipleGoalsShareFreshStockAndCreditsAfterCandidateChanges() throws Exception {
        var network = retryNetwork();
        var outcome = retry(new AcyclicCraftingSolver(), network, 1L, 2L, 1L, 1L);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertRetryAmounts(network, outcome, 1L, 2L, 1L);
    }

    @Test
    void exhaustedRetriesStillReportTheFinalExactShortage() throws Exception {
        var network = retryNetwork();
        var outcome = retry(new AcyclicCraftingSolver(), network, 1L, 0L, 0L, 0L);
        assertEquals(PlanningStatus.MISSING_ITEMS, outcome.status());
        assertEquals(Map.of(AEItemKey.of(Items.GOLD_INGOT), PlannerAmount.ONE), outcome.state().missingAmounts());
        assertEquals(1L, outcome.state().usedItems().get(AEItemKey.of(Items.IRON_NUGGET)));
        assertEquals(2L, outcome.trace().diagnostics().stream()
            .filter(d -> d.code() == PlannerDiagnostic.Code.CANDIDATE_REJECTED).count());
    }

    @Test
    void simultaneousSolvesDoNotShareRetryCachesOrInventory() throws Exception {
        var solver = new AcyclicCraftingSolver();
        var network = retryNetwork();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> retry(solver, network, 1L, 2L, 1L, 1L));
            var two = executor.submit(() -> retry(solver, network, 3L, 1L, 2L, 3L));
            assertRetryAmounts(network, one.get(20, TimeUnit.SECONDS), 1L, 2L, 1L);
            assertRetryAmounts(network, two.get(20, TimeUnit.SECONDS), 3L, 2L, 2L);
            assertRetryAmounts(network, retry(solver, network, 2L, 0L, 0L, 2L), 2L, 2L, 0L);
        } finally {
            executor.shutdownNow();
        }
    }

    private static CompiledNetwork retryNetwork() {
        var goal = AEItemKey.of(Items.DIAMOND);
        var branch = AEItemKey.of(Items.EMERALD);
        var shared = AEItemKey.of(Items.IRON_INGOT);
        var root = pattern(0, goal, 1L, new GenericStack(branch, 1L), new GenericStack(shared, 4L));
        var choices = new java.util.ArrayList<CompiledPattern>();
        for (int i = 1; i <= 3; i++) {
            AEKey input = AEItemKey.of(i < 3 ? Items.COBBLESTONE : Items.GOLD_INGOT);
            choices.add(pattern(i, branch, List.of(new GenericStack(branch, 1L), new GenericStack(shared, i)),
                new GenericStack(input, 1L)));
        }
        var sharedPattern = pattern(4, shared, 1L, new GenericStack(AEItemKey.of(Items.IRON_NUGGET), 1L));
        Map<AEKey, List<CompiledPattern>> producers = new LinkedHashMap<>();
        producers.put(goal, List.of(root));
        producers.put(branch, choices);
        producers.put(shared, List.of(sharedPattern));
        producers.put(AEItemKey.of(Items.COBBLESTONE), List.of());
        producers.put(AEItemKey.of(Items.GOLD_INGOT), List.of());
        producers.put(AEItemKey.of(Items.IRON_NUGGET), List.of());
        return new CompiledNetwork(goal, producers, Set.of(), 5, 6);
    }

    private static AcyclicCraftingSolver.Outcome retry(AcyclicCraftingSolver solver, CompiledNetwork network,
            long amount, long extraShared, long sharedStock, long goodStock) throws Exception {
        Map<AEKey, PlannerAmount> goals = new LinkedHashMap<>();
        goals.put(network.goal(), PlannerAmount.of(amount));
        if (extraShared > 0L) goals.put(AEItemKey.of(Items.IRON_INGOT), PlannerAmount.of(extraShared));
        var inventory = PlannerInventorySnapshot.exact(Map.of(
            AEItemKey.of(Items.IRON_INGOT), PlannerAmount.of(sharedStock),
            AEItemKey.of(Items.GOLD_INGOT), PlannerAmount.of(goodStock),
            AEItemKey.of(Items.IRON_NUGGET), PlannerAmount.of(amount).multiply(4).add(extraShared)), Set.of());
        return solver.solveExactDemands(network, inventory, goals, Map.of(), Set.of(), false, ECOCancellation.NONE);
    }

    private static void assertRetryAmounts(CompiledNetwork network, AcyclicCraftingSolver.Outcome outcome,
            long goalAmount, long rawAmount, long sharedStock) {
        assertTrue(outcome.state().missingAmounts().isEmpty());
        assertTrue(outcome.state().unsupported.isEmpty());
        assertEquals(PlannerAmount.of(rawAmount), outcome.state().usedAmounts().get(AEItemKey.of(Items.IRON_NUGGET)));
        assertEquals(PlannerAmount.of(goalAmount), outcome.state().usedAmounts().get(AEItemKey.of(Items.GOLD_INGOT)));
        assertEquals(sharedStock, outcome.state().usedItems().get(AEItemKey.of(Items.IRON_INGOT)));
        var branch = network.fastProducersOf(AEItemKey.of(Items.EMERALD)).get(2);
        assertEquals(Map.of(network.fastProducersOf(network.goal()).get(0).details(), PlannerAmount.of(goalAmount),
            branch.details(), PlannerAmount.of(goalAmount),
            network.fastProducersOf(AEItemKey.of(Items.IRON_INGOT)).get(0).details(), PlannerAmount.of(rawAmount)),
            outcome.state().plannerPatternTimes());
        assertEquals(2L, outcome.trace().diagnostics().stream()
            .filter(d -> d.code() == PlannerDiagnostic.Code.CANDIDATE_REJECTED).count());
    }

    private static AcyclicCraftingSolver.Outcome solve(long nuggets, long ingots, long products) throws Exception {
        return solve(nuggets, ingots, products, 1L);
    }

    private static AcyclicCraftingSolver.Outcome solve(long nuggets, long ingots, long products,
            long requiredIngots) throws Exception {
        var product = AEItemKey.of(Items.DIAMOND);
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        var nugget = AEItemKey.of(Items.IRON_NUGGET);
        var finalRecipe = pattern(0, product, 1L,
            new GenericStack(ingot, requiredIngots), new GenericStack(nugget, 1L));
        var pack = pattern(1, ingot, 1L, new GenericStack(nugget, 9L));
        var unpack = pattern(2, nugget, 9L, new GenericStack(ingot, 1L));
        var network = new CompiledNetwork(product, Map.of(product, List.of(finalRecipe),
            ingot, List.of(pack), nugget, List.of(unpack)), Set.of(), 3, 3);
        var inventory = new KeyCounter();
        inventory.set(nugget, nuggets);
        inventory.set(ingot, ingots);
        inventory.set(product, products);
        return new AcyclicCraftingSolver().solve(network,
            new AcyclicRoutePlan(List.of(product, ingot, nugget)), inventory, 1L, ECOCancellation.NONE);
    }

    private static CompiledPattern pattern(int id, AEKey output, long outputAmount, GenericStack... inputs) {
        return pattern(id, output, List.of(new GenericStack(output, outputAmount)), inputs);
    }

    private static CompiledPattern pattern(int id, AEKey output, List<GenericStack> outputs, GenericStack... inputs) {
        var details = mock(IPatternDetails.class);
        when(details.getOutputs()).thenReturn(outputs.toArray(GenericStack[]::new));
        when(details.getPrimaryOutput()).thenReturn(outputs.get(0));
        var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        var compiledInputs = java.util.Arrays.stream(inputs)
            .map(input -> new CompiledInput(null, input.what(), input.amount(), false, null)).toList();
        return new CompiledPattern(id, details, output, PlannerAmount.of(outputs.get(0).amount()), compiledInputs, outputs,
            true, null, false, semantics);
    }
}
