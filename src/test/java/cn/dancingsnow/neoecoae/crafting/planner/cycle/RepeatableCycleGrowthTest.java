package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepeatableCycleGrowthTest {
    private final AEKey a = key("A"), b = key("B"), fuel = key("fuel");
    private final CompiledPattern grow = pattern(0, Map.of(a, 1L, fuel, 1L), Map.of(b, 2L));
    private final CompiledPattern restore = pattern(1, Map.of(b, 1L), Map.of(a, 1L));

    @ParameterizedTest
    @ValueSource(ints = {5, 6})
    void hundredMillionAndTrillionOrdersUseAFormulaAndOneStartupCopy(int size) throws Exception {
        List<AEKey> keys = new ArrayList<>();
        List<CompiledPattern> patterns = new ArrayList<>();
        for (int i = 0; i < size; i++) keys.add(key("ring-" + i));
        for (int i = 0; i < size; i++) {
            patterns.add(pattern(i, i == 0 ? Map.of(keys.get(i), 1L, fuel, 1000L) : Map.of(keys.get(i), 1L),
                    Map.of(keys.get((i + 1) % size), i == size - 1 ? 2L : 1L)));
        }
        var boundary = boundary(patterns.getFirst());
        var cycle = new CycleComponent(0, keys, patterns, List.of(), List.of(), boundary);
        for (long quantity : new long[] {100_000_000L, 1_000_000_000_000L}) {
            var work = new AtomicLong();
            var result = solve(cycle, Map.of(keys.getFirst(), quantity + 64), Map.of(keys.getFirst(), 64L),
                    boundary, Map.of(), work);
            assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
            assertFormula(result);
            assertEquals(1L, result.requiredSeed().get(keys.getFirst()), "Leave the other 63 copies in ME");
            assertEquals(1000L * quantity, result.externalDemand().get(fuel));
            for (var pattern : patterns) assertEquals(quantity, result.patternTimes().get(pattern.details()));
            assertTrue(result.executionWitness().isEmpty(), "Do not expand one entry per physical firing");
            assertTrue(result.executionPlan().size() < 1000);
            assertTrue(result.executionPlan().stream().anyMatch(run -> run.count() > 1),
                    "Stock grown inside this job should still enable larger batches");
            assertTrue(work.get() < 40_000, "Order size must not require enumerating its inventory states: " + work);
        }
    }

    @Test
    void finishingPrefixSavesTheLastFuelWithoutPretendingTheSeedWasRestored() throws Exception {
        long quantity = 100_000_000L;
        var cycle = cycle();
        var result = solve(cycle, Map.of(b, quantity), Map.of(a, 1L, fuel, quantity - 1), List.of(), Map.of(), new AtomicLong());
        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
        assertFormula(result);
        assertEquals(quantity - 1, result.patternTimes().get(grow.details()));
        assertEquals(quantity - 2, result.patternTimes().get(restore.details()));
        assertEquals(1L, result.requiredSeed().get(a));
        assertEquals(quantity - 1, result.requiredSeed().get(fuel));
        assertEquals(BigInteger.ZERO, balance(result, a, 1));
        assertEquals(BigInteger.valueOf(quantity), balance(result, b, 0));

        var retaining = solve(cycle, Map.of(b, quantity, a, 1L), Map.of(a, 1L, fuel, quantity),
                List.of(), Map.of(), new AtomicLong());
        assertEquals(CycleSolveStatus.SUCCESS, retaining.status(), retaining.summary());
        assertEquals(quantity, retaining.patternTimes().get(grow.details()));
        assertEquals(quantity, retaining.patternTimes().get(restore.details()));
        assertEquals(BigInteger.ONE, balance(retaining, a, 1));
    }

    @Test
    void finiteFuelAndItsRequiredEndingBalanceAreBothChecked() throws Exception {
        long quantity = 1_000_000L;
        var result = solve(cycle(), Map.of(b, quantity, a, 1L, fuel, 1L), Map.of(a, 1L, fuel, quantity),
                List.of(), Map.of(), new AtomicLong());
        assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, result.status(), result.summary());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code() == CycleSolveDiagnostic.Code.STATE_EQUATION_INFEASIBLE));
    }

    @ParameterizedTest
    @ValueSource(longs = {3L, 5L, 65L, 1_000_001L})
    void finishingInsideARecoveryBatchDoesNotRequestAnExtraOre(long quantity) throws Exception {
        AEKey water = key("water"), product = key("washed ore");
        var wash = pattern(0, Map.of(a, 500L, water, 500L, fuel, 1L), Map.of(product, 1L, b, 1000L));
        var recover = pattern(1, Map.of(b, 2000L), Map.of(a, 1000L, water, 1000L));
        var boundary = boundary(wash);
        var cycle = new CycleComponent(0, List.of(a, b, water), List.of(wash, recover),
            List.of(), List.of(), boundary);
        var result = solve(cycle, Map.of(product, quantity), Map.of(a, 1000L, water, 1000L), boundary,
            Map.of(), new AtomicLong());
        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
        assertFormula(result);
        assertEquals(quantity, result.patternTimes().get(wash.details()));
        assertEquals((quantity - 1) / 2, result.patternTimes().get(recover.details()));
        assertEquals(quantity, result.externalDemand().get(fuel));
        assertEquals(quantity, result.deliverableOutputs().get(product));
    }

    @Test
    void boundaryCapsChargeOnlyImportsBeyondOwnedFuelAndNeverCreateFuel() throws Exception {
        long quantity = 1_000_000L;
        var boundary = boundary(grow);
        var cycle = new CycleComponent(0, List.of(a, b), List.of(grow, restore), List.of(), List.of(), boundary);
        var allowed = solve(cycle, Map.of(b, quantity, a, 1L), Map.of(a, 1L, fuel, 5L), boundary,
                Map.of(fuel, PlannerAmount.of(quantity - 5)), new AtomicLong());
        assertEquals(CycleSolveStatus.SUCCESS, allowed.status(), allowed.summary());
        assertEquals(quantity - 5, allowed.externalDemand().get(fuel));
        assertEquals(5L, allowed.requiredSeed().get(fuel));
        var shortFuel = solve(cycle, Map.of(b, quantity, a, 1L), Map.of(a, 1L, fuel, 5L), boundary,
                Map.of(fuel, PlannerAmount.of(quantity - 6)), new AtomicLong());
        assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, shortFuel.status(), shortFuel.summary());
    }

    @Test
    void noSeedCannotBootstrapAFeasibleMaterialEquation() throws Exception {
        var result = solve(cycle(), Map.of(b, 100_000_000L), Map.of(fuel, 100_000_000L),
                List.of(), Map.of(), new AtomicLong());
        assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, result.status(), result.summary());
        assertFalse(result.seedShortfall().isEmpty());
        assertTrue(result.diagnostics().stream().noneMatch(d -> d.code() == CycleSolveDiagnostic.Code.STATE_EQUATION_BUDGET),
                "A feasible vector without startup seed is not an exhausted optimization budget");
    }

    @Test
    void fiveKeySplitMergeLapUsesItsAtomicTwoItemSeedRatherThanAllStock() throws Exception {
        AEKey c = key("C"), d = key("D"), e = key("E"), product = key("product");
        var split = pattern(0, Map.of(a, 2L, fuel, 1000L), Map.of(b, 3L, c, 1L));
        var process = pattern(1, Map.of(b, 3L), Map.of(d, 1L, e, 1L));
        var merge = pattern(2, Map.of(c, 1L, d, 1L, e, 1L), Map.of(a, 2L, product, 1L));
        var boundary = boundary(split);
        var cycle = new CycleComponent(0, List.of(a, b, c, d, e), List.of(split, process, merge),
                List.of(), List.of(), boundary);
        var result = solve(cycle, Map.of(product, 100_000_000L), Map.of(a, 200L), boundary, Map.of(), new AtomicLong());
        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
        assertFormula(result);
        assertEquals(2L, result.requiredSeed().get(a));
        assertEquals(100_000_000_000L, result.externalDemand().get(fuel));
        assertEquals(3, result.executionPlan().size());
        assertEquals(100_000_000L, result.executionPlan().getLast().repetitions());
    }

    @Test
    void compressedSmallWitnessStillReplaysEveryInputInOrder() throws Exception {
        var result = solve(cycle(), Map.of(b, 40L, a, 1L), Map.of(a, 1L, fuel, 40L),
                List.of(), Map.of(), new AtomicLong());
        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
        Map<AEKey, Long> stock = new LinkedHashMap<>(Map.of(a, 1L, fuel, 40L));
        for (var firing : result.executionWitness()) {
            for (var input : firing.pattern().inputs()) {
                assertTrue(stock.merge(input.key(), -input.amountPerPattern().longValueExact(), Long::sum) >= 0);
            }
            for (var output : firing.pattern().grossOutputs()) stock.merge(output.what(), output.amount(), Long::sum);
        }
        assertEquals(1L, stock.get(a));
        assertEquals(40L, stock.get(b));
        assertEquals(0L, stock.get(fuel));
    }

    private CycleComponent cycle() {
        return new CycleComponent(0, List.of(a, b), List.of(grow, restore), List.of(), List.of(), List.of());
    }

    private List<ComponentDependency> boundary(CompiledPattern pattern) {
        return List.of(new ComponentDependency(0, 1,
                List.of(new CraftingGraphEdge(pattern.producedKey(), fuel, pattern, null))));
    }

    private static CycleSolveResult solve(CycleComponent cycle, Map<AEKey, Long> targets, Map<AEKey, Long> stock,
            List<ComponentDependency> boundary, Map<AEKey, PlannerAmount> caps, AtomicLong work) throws Exception {
        var request = new CycleSolveRequest(cycle, targets, stock, boundary,
                new CycleSolveRequest.PlannerOptions(new CycleSolveLimits(0, 0, 1, 1, 0))).withBoundaryImportCaps(caps);
        return new BoundedCycleSolver().solve(request,
                new ECOPlanningBudget(work::incrementAndGet, 40_000L, Long.MAX_VALUE, () -> 0L));
    }

    private static void assertFormula(CycleSolveResult result) {
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code() == CycleSolveDiagnostic.Code.REPEATABLE_NET_GROWTH),
                result.summary());
        assertEquals(0, result.metrics().statesExpanded());
    }

    private BigInteger balance(CycleSolveResult result, AEKey key, long initial) {
        BigInteger balance = BigInteger.valueOf(initial);
        for (var pattern : List.of(grow, restore)) {
            BigInteger count = result.exactPatternTimes().getOrDefault(pattern.details(), PlannerAmount.ZERO).toBigInteger();
            for (var input : pattern.inputs()) if (input.key().equals(key)) balance = balance.subtract(input.amountPerPattern().toBigInteger().multiply(count));
            for (var output : pattern.grossOutputs()) if (output.what().equals(key)) balance = balance.add(BigInteger.valueOf(output.amount()).multiply(count));
        }
        return balance;
    }

    private static AEKey key(String name) { return mock(AEKey.class, name); }

    private static CompiledPattern pattern(int id, Map<AEKey, Long> inputs, Map<AEKey, Long> outputs) {
        var details = mock(IPatternDetails.class, "pattern-" + id);
        var stacks = outputs.entrySet().stream().map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toList();
        when(details.getOutputs()).thenReturn(stacks);
        var semantics = new PatternSemantics(details, null, List.of(), stacks, List.of(), List.of(),
                PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, details, stacks.getFirst().what(), PlannerAmount.of(stacks.getFirst().amount()),
                inputs.entrySet().stream().map(entry -> new CompiledInput(null, entry.getKey(), entry.getValue(), true, null)).toList(),
                stacks, true, null, false, semantics);
    }
}
