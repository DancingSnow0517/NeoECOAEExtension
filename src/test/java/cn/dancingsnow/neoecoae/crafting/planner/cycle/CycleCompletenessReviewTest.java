package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Resource-constrained counterexamples and an independent single-firing reachability oracle. */
class CycleCompletenessReviewTest {
    @Test
    void speculativeSeedCannotBecomeFreeDeliveredOutput() throws Exception {
        AEKey a = mock(AEKey.class, "A");
        var shrink = pattern(0, Map.of(a, 2L), Map.of(a, 1L));
        var cycle = new CycleComponent(0, List.of(a), List.of(shrink), List.of(), List.of(), List.of());
        var result = new BoundedCycleSolver().solve(new CycleSolveRequest(cycle,
            Map.of(a, 5L), Map.of(a, 1L), List.of(), new CycleSolveRequest.PlannerOptions()),
            ECOCancellation.NONE);
        assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, result.status());
        assertEquals(4L, result.seedShortfall().get(a));
        assertEquals(5L, result.requiredSeed().get(a));
        assertEquals(5L, result.deliverableOutputs().get(a));
        assertTrue(result.patternTimes().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sufficientFuelMustNotBeRejectedByPivotOvershoot(boolean twoPatterns) throws Exception {
        AEKey a = mock(AEKey.class, "A");
        AEKey b = mock(AEKey.class, "B");
        AEKey fuel = mock(AEKey.class, "fuel");
        var first = pattern(0, Map.of(a, 2L, fuel, 1L),
            twoPatterns ? Map.of(b, 1L) : Map.of(a, 3L));
        var patterns = twoPatterns
            ? List.of(first, pattern(1, Map.of(b, 1L), Map.of(a, 3L))) : List.of(first);
        var members = twoPatterns ? List.of(a, b) : List.of(a);
        var cycle = new CycleComponent(0, members, patterns, List.of(), List.of(), List.of());
        // Three laps are executable: A=2 -> 3 -> 4 -> 5, consuming exactly three fuel.
        var result = new BoundedCycleSolver().solve(new CycleSolveRequest(cycle,
            Map.of(a, 5L), Map.of(a, 2L, fuel, 3L), List.of(),
            new CycleSolveRequest.PlannerOptions()), ECOCancellation.NONE);
        assertEquals(CycleSolveStatus.SUCCESS, result.status(),
            "counts=" + result.patternTimes() + " shortfall=" + result.seedShortfall());
        assertEquals(3L, result.patternTimes().get(first.details()));
    }

    @Test
    void weightedRingsAgreeWithExhaustiveSingleFiringSearch() throws Exception {
        var random = new java.util.Random(20260925L);
        AEKey a = mock(AEKey.class, "A");
        AEKey b = mock(AEKey.class, "B");
        AEKey fuel = mock(AEKey.class, "fuel");
        // Reuse one solver across different stock snapshots to catch accidental cross-request caching.
        var solver = new BoundedCycleSolver();
        for (int sample = 0; sample < 96; sample++) {
            int ca = 1 + random.nextInt(4), cb = 1 + random.nextInt(4);
            int pa = 1 + random.nextInt(4), pb = 1 + random.nextInt(4);
            var first = pattern(0, Map.of(a, (long) ca, fuel, 1L), Map.of(b, (long) pb));
            var second = pattern(1, Map.of(b, (long) cb), Map.of(a, (long) pa));
            var cycle = new CycleComponent(0, List.of(a, b), List.of(first, second),
                List.of(), List.of(), List.of());
            var stock = new Inventory(random.nextInt(5), random.nextInt(5), random.nextInt(7));
            long targetA = 1 + random.nextInt(9), targetB = random.nextInt(4);
            boolean reachable = reachable(stock, ca, cb, pa, pb, targetA, targetB);
            var result = solver.solve(new CycleSolveRequest(cycle, Map.of(a, targetA, b, targetB),
                Map.of(a, stock.a, b, stock.b, fuel, stock.fuel), List.of(),
                new CycleSolveRequest.PlannerOptions()), ECOCancellation.NONE);
            assertEquals(reachable, result.status() == CycleSolveStatus.SUCCESS,
                "sample=" + sample + " stock=" + stock + " diagnostics=" + result.diagnostics());
            if (reachable || result.hasExactExecutionCounts()) {
                var actual = new java.util.HashMap<>(Map.of(a, stock.a, b, stock.b, fuel, stock.fuel));
                result.seedShortfall().forEach((key, amount) -> actual.merge(key, amount, Long::sum));
                for (var firing : result.executionWitness()) {
                    for (var input : firing.pattern().inputs()) {
                        long amount = input.amountPerPattern().longValueExact();
                        assertTrue(actual.getOrDefault(input.key(), 0L) >= amount);
                        actual.merge(input.key(), -amount, Long::sum);
                    }
                    firing.pattern().grossOutputs().forEach(output ->
                        actual.merge(output.what(), output.amount(), Long::sum));
                }
                assertTrue(actual.get(a) >= targetA && actual.get(b) >= targetB);
            }
        }
    }

    @Test
    void irrelevantByproductCannotKeepAnUnproductiveRingSearching() throws Exception {
        AEKey a = mock(AEKey.class, "A");
        AEKey b = mock(AEKey.class, "B");
        AEKey waste = mock(AEKey.class, "waste");
        var first = pattern(0, Map.of(a, 1L), Map.of(b, 1L, waste, 1L));
        var second = pattern(1, Map.of(b, 1L), Map.of(a, 1L));
        var cycle = new CycleComponent(0, List.of(a, b), List.of(first, second),
            List.of(), List.of(), List.of());
        var result = new BoundedCycleSolver().solve(new CycleSolveRequest(cycle,
            Map.of(a, 2L), Map.of(a, 1L), List.of(),
            new CycleSolveRequest.PlannerOptions(new CycleSolveLimits(8, 16, 32, 100, 0))),
            ECOCancellation.NONE);

        assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, result.status(),
            result.diagnostics().toString());
        assertTrue(result.metrics().statesVisited() <= 2, result.metrics().toString());
    }

    @Test
    void consumedIntermediateMustAccumulateEvenWhenItIsNotARequestedOutput() throws Exception {
        AEKey a = mock(AEKey.class, "A");
        AEKey intermediate = mock(AEKey.class, "intermediate");
        AEKey goal = mock(AEKey.class, "goal");
        var accumulate = pattern(0, Map.of(a, 1L), Map.of(a, 1L, intermediate, 1L));
        var finish = pattern(1, Map.of(intermediate, 3L), Map.of(goal, 1L));
        var cycle = new CycleComponent(0, List.of(a), List.of(accumulate, finish),
            List.of(), List.of(), List.of());
        var result = new BoundedCycleSolver().solve(new CycleSolveRequest(cycle,
            Map.of(goal, 1L), Map.of(a, 1L), List.of(), new CycleSolveRequest.PlannerOptions()),
            ECOCancellation.NONE);

        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.diagnostics().toString());
        assertEquals(3L, result.patternTimes().get(accumulate.details()));
        assertEquals(1L, result.patternTimes().get(finish.details()));
        assertEquals(1L, result.deliverableOutputs().get(goal));
    }

    @Test
    void outputOnlyTargetKeepsProgressAndFullWitnessSurplus() throws Exception {
        AEKey a = mock(AEKey.class, "A");
        AEKey product = mock(AEKey.class, "product");
        var produce = pattern(0, Map.of(a, 1L), Map.of(a, 1L, product, 2L));
        var neutral = pattern(1, Map.of(a, 1L), Map.of(a, 1L));
        var cycle = new CycleComponent(0, List.of(a), List.of(produce, neutral),
            List.of(), List.of(), List.of());
        var result = new BoundedCycleSolver().solve(new CycleSolveRequest(cycle,
            Map.of(product, 5L), Map.of(a, 1L), List.of(), new CycleSolveRequest.PlannerOptions()),
            ECOCancellation.NONE);

        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.diagnostics().toString());
        assertEquals(3L, result.patternTimes().get(produce.details()));
        assertEquals(6L, result.producedOutputs().get(product));
        assertEquals(6L, result.deliverableOutputs().get(product));
    }

    private record Inventory(long a, long b, long fuel) {}

    private static boolean reachable(Inventory stock, int ca, int cb, int pa, int pb, long ta, long tb) {
        var pending = new java.util.ArrayDeque<Inventory>();
        var seen = new java.util.HashSet<Inventory>();
        pending.add(stock);
        while (!pending.isEmpty()) {
            var state = pending.removeFirst();
            if (!seen.add(state)) continue;
            if (state.a >= ta && state.b >= tb) return true;
            if (state.a >= ca && state.fuel > 0) {
                pending.add(new Inventory(state.a - ca, state.b + pb, state.fuel - 1));
            }
            if (state.b >= cb) pending.add(new Inventory(state.a + pa, state.b - cb, state.fuel));
        }
        return false;
    }

    private static CompiledPattern pattern(int id, Map<AEKey, Long> inputs, Map<AEKey, Long> outputs) {
        var details = mock(IPatternDetails.class, "pattern" + id);
        var stacks = outputs.entrySet().stream().map(e -> new GenericStack(e.getKey(), e.getValue())).toList();
        when(details.getOutputs()).thenReturn(stacks);
        var semantics = new PatternSemantics(details, null, List.of(), stacks, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, details, stacks.getFirst().what(), PlannerAmount.of(stacks.getFirst().amount()),
            inputs.entrySet().stream().map(e -> new CompiledInput(null, e.getKey(), e.getValue(), true, null)).toList(),
            stacks, true, null, false, semantics);
    }
}
