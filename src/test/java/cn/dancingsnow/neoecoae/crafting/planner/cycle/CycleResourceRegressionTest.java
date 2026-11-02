package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.growth.SinglePatternGrowthCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CycleResourceRegressionTest {
    @Test void duplicateElectrolysisPatternsProduceACompactLargeOrderWithinTheWorkBudget() throws Exception {
        AEKey hydrogen = mock(AEKey.class, "modern_industrialization:hydrogen");
        AEKey oxygen = mock(AEKey.class, "modern_industrialization:oxygen");
        AEKey water = mock(AEKey.class, "minecraft:water");
        var firstOutputs = new LinkedHashMap<AEKey, Long>();
        firstOutputs.put(oxygen, 1000L);
        firstOutputs.put(hydrogen, 2000L);
        var secondOutputs = new LinkedHashMap<AEKey, Long>();
        secondOutputs.put(hydrogen, 2000L);
        secondOutputs.put(oxygen, 1000L);
        var first = pattern(0, Map.of(water, 3000L), firstOutputs, false);
        var second = pattern(1, Map.of(water, 3000L), secondOutputs, false);
        var boundary = List.of(new ComponentDependency(10, 11,
            List.of(new CraftingGraphEdge(hydrogen, water, second, second.inputs().getFirst()))));
        var cycle = new CycleComponent(10, List.of(oxygen, hydrogen), List.of(first, second),
            List.of(), List.of(), boundary);
        var request = new CycleSolveRequest(cycle, Map.of(oxygen, 1_503_000L, hydrogen, 27_615_000L),
            Map.of(oxygen, 16_934_750L, hydrogen, 6_249_500L), boundary, null);
        var result = new BoundedCycleSolver().solve(request,
            new ECOPlanningBudget(ECOCancellation.NONE, 2000, Long.MAX_VALUE, () -> 0L));
        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
        assertEquals(10_683L, result.totalFirings());
        assertEquals(32_049_000L, result.externalDemand().get(water));
        assertTrue(result.seedShortfall().isEmpty());
        assertEquals(1, result.executionPlan().size());
        long firings = result.executionPlan().getFirst().count();
        assertTrue(6_249_500L + 2000L * firings >= 27_615_000L);
        assertTrue(16_934_750L + 1000L * firings >= 1_503_000L);
    }

    @Test void markingSearchFindsAnExecutableDetourOutsideTheMinimumBalanceVector() throws Exception {
        AEKey a = mock(AEKey.class), b = mock(AEKey.class), product = mock(AEKey.class);
        var finish = pattern(0, Map.of(a, 1L, b, 1L), Map.of(a, 2L, product, 1L), false);
        var transfer = pattern(1, Map.of(a, 1L), Map.of(b, 1L), false);
        var grow = pattern(2, Map.of(b, 1L), Map.of(a, 2L), false);
        var cycle = new CycleComponent(0, List.of(a, b), List.of(finish, transfer, grow), List.of(), List.of(), List.of());
        // The minimum balance vector (finish=1, transfer=1) cannot fire from A=1.
        // An executable order needs the growth detour: transfer, grow, transfer, finish.
        var result = solve(cycle, Map.of(product, 1L), Map.of(a, 1L));
        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
        assertTrue(result.seedShortfall().isEmpty());
        assertEquals(1L, result.patternTimes().get(grow.details()));
        assertEquals(2L, result.patternTimes().get(transfer.details()));
        assertEquals(1L, result.patternTimes().get(finish.details()));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code() == CycleSolveDiagnostic.Code.WITNESS_FOUND));

        var bounded = new CycleSolveRequest(cycle, Map.of(product, 1L), Map.of(a, 1L), List.of(),
            new CycleSolveRequest.PlannerOptions(CycleSolveLimits.DEFAULT.withMaxStates(2)));
        var truncated = new BoundedCycleSolver().solve(bounded, ECOCancellation.NONE);
        assertEquals(CycleSolveStatus.UNKNOWN_BUDGET, truncated.status());
        assertFalse(truncated.hasExactExecutionCounts());
    }

    @Test void reverseOrderedCircuitDoesNotStopAfter4096WitnessSweeps() throws Exception {
        var cycle = ring(40);
        BigInteger target = BigInteger.TEN.pow(80);
        var result = new BoundedCycleSolver().solve(new CycleSolveRequest(cycle, Map.of(),
            Map.of(cycle.members().getFirst(), PlannerAmount.of(target)),
            Map.of(cycle.members().getFirst(), 1L), List.of(), null), ECOCancellation.NONE);
        assertEquals(CycleSolveStatus.UNREPRESENTABLE, result.status(), result.summary());
        assertTrue(result.hasExactExecutionCounts());
        result.exactPatternTimes().values().forEach(count -> assertEquals(target.subtract(BigInteger.ONE), count.toBigInteger()));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code() == CycleSolveDiagnostic.Code.STATE_EQUATION_WITNESS));
        assertTrue(result.seedShortfall().isEmpty());
    }

    @Test void reverseOrderedCircuitLongerThan32StepsCompressesATrillionLaps() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var cycle = ring(40);
            var result = solve(cycle, Map.of(cycle.members().getFirst(), 1_000_000_000_001L),
                Map.of(cycle.members().getFirst(), 1L));
            assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
            assertTrue(result.executionPlan().size() <= 3 * 40);
            assertTrue(result.executionPlan().stream().anyMatch(run -> run.repeatWidth() == 40));
            result.patternTimes().values().forEach(count -> assertEquals(1_000_000_000_000L, count));
            assertTrue(result.executionWitness().isEmpty());
            replayCompact(cycle, result, 1_000_000_000_001L);
        });
    }

    @Test void moreThan64PhysicalPatternsAnd256KeysCanExecute() throws Exception {
        var base = ring(70);
        var patterns = new ArrayList<>(base.patterns());
        var stock = new LinkedHashMap<AEKey, Long>();
        stock.put(base.members().getFirst(), 1L);
        var first = patterns.getFirst();
        var inputs = new LinkedHashMap<AEKey, Long>();
        first.inputs().forEach(input -> inputs.put(input.key(), input.amountPerPattern().longValueExact()));
        var outputs = new LinkedHashMap<AEKey, Long>();
        first.outputs().forEach(output -> outputs.put(output.what(), output.amount()));
        for (int i = 0; i < 200; i++) {
            AEKey catalyst = mock(AEKey.class);
            inputs.put(catalyst, 1L);
            outputs.put(catalyst, 1L);
            stock.put(catalyst, 1L);
        }
        patterns.set(0, pattern(first.id(), inputs, outputs, false));
        var cycle = new CycleComponent(0, base.members(), patterns, List.of(), List.of(), List.of());
        var result = solve(cycle, Map.of(base.members().getFirst(), 11L), stock);
        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
        assertEquals(70, result.metrics().transitions());
        assertEquals(270, result.metrics().relevantKeys());
        result.patternTimes().values().forEach(count -> assertEquals(10L, count));
    }

    @Test void closedFormGrowthRunsThroughBothEntrypointsAndBooksFiniteFuel() throws Exception {
        AEKey a = mock(AEKey.class), fuel = mock(AEKey.class);
        var grow = pattern(0, Map.of(a, 9L, fuel, 2L), Map.of(a, 10L), true);
        var edge = new CraftingGraphEdge(a, a, grow, grow.inputs().getFirst());
        var cycle = new CycleComponent(0, List.of(a), List.of(grow), List.of(edge), List.of(), List.of());
        long target = 1_000_000_000_009L;
        var request = new CycleSolveRequest(cycle, Map.of(a, target), Map.of(a, 9L, fuel, 2_000_000_000_000L),
            List.of(), new CycleSolveRequest.PlannerOptions());
        var wrapper = new SinglePatternGrowthCycleSolver((ignored, cancel) -> {
            fail("The closed form must be used before the fallback"); return null;
        });
        for (CycleSolver solver : List.of(new BoundedCycleSolver(), wrapper)) {
            var result = solver.solve(request, ECOCancellation.NONE);
            assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
            assertEquals(1, result.executionPlan().size());
            assertEquals(1_000_000_000_000L, result.patternTimes().get(grow.details()));
            assertEquals(2_000_000_000_000L, result.requiredSeed().get(fuel));
            assertTrue(result.externalDemand().isEmpty());
            assertTrue(result.diagnostics().stream().anyMatch(d -> d.code() == CycleSolveDiagnostic.Code.SINGLE_PATTERN_NET_GROWTH));
        }
        var shortFuel = new CycleSolveRequest(cycle, Map.of(a, target), Map.of(a, 9L, fuel, 2L), List.of(), null);
        var missing = wrapper.solve(shortFuel, ECOCancellation.NONE);
        assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, missing.status());
        assertEquals(1_999_999_999_998L, missing.seedShortfall().get(fuel));
        assertTrue(missing.externalDemand().isEmpty());
    }

    @Test void closedFormUsesExactTargetsAndChargesBoundarySupply() throws Exception {
        AEKey a = mock(AEKey.class), fuel = mock(AEKey.class);
        var grow = pattern(0, Map.of(a, 9L, fuel, 1L), Map.of(a, 10L), true);
        var edge = new CraftingGraphEdge(a, a, grow, null);
        var boundary = List.of(new ComponentDependency(0, 1, List.of(new CraftingGraphEdge(a, fuel, grow, null))));
        var cycle = new CycleComponent(0, List.of(a), List.of(grow), List.of(edge), List.of(), boundary);
        BigInteger target = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN);
        var request = new CycleSolveRequest(cycle, Map.of(), Map.of(a, PlannerAmount.of(target)), Map.of(a, 9L), boundary, null);
        var result = SinglePatternGrowthCycleSolver.overBoundedSolver().solve(request, ECOCancellation.NONE);
        assertEquals(CycleSolveStatus.UNREPRESENTABLE, result.status());
        assertEquals(target.subtract(BigInteger.valueOf(9)), result.exactPatternTimes().get(grow.details()).toBigInteger());
        assertTrue(result.seedShortfall().isEmpty());
    }

    @Test void largeStartupRequirementIsComputedWithoutDoublingProbes() throws Exception {
        AEKey a = mock(AEKey.class), b = mock(AEKey.class), product = mock(AEKey.class);
        var first = pattern(0, Map.of(a, 5000L), Map.of(b, 1L), false);
        var last = pattern(1, Map.of(b, 1L), Map.of(a, 5000L, product, 1L), false);
        var cycle = new CycleComponent(0, List.of(a, b), List.of(first, last), List.of(), List.of(), List.of());
        var missing = solve(cycle, Map.of(product, 1_000_000L), Map.of());
        assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, missing.status(), missing.summary());
        assertTrue(missing.startupCandidates().contains(Map.of(a, 5000L)));
        var supplied = solve(cycle, Map.of(product, 1_000_000L), Map.of(a, 5000L));
        assertEquals(CycleSolveStatus.SUCCESS, supplied.status(), supplied.summary());
        assertEquals(5000L, supplied.requiredSeed().get(a));
        assertEquals(0, supplied.metrics().seedLadderSteps());
    }

    @Test void paretoStartupCandidatesAreNotTruncatedAt32() throws Exception {
        var cycle = ring(40);
        var result = solve(cycle, Map.of(cycle.members().getFirst(), 10L), Map.of());
        assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, result.status(), result.summary());
        assertEquals(40, result.startupCandidates().size());
        for (AEKey key : cycle.members()) assertTrue(result.startupCandidates().contains(Map.of(key, 1L)));
    }

    @Test void resourceExhaustionAndCancellationNeverBecomeInfeasibility() throws Exception {
        var cycle = ring(40);
        var request = new CycleSolveRequest(cycle, Map.of(cycle.members().getFirst(), 100L),
            Map.of(cycle.members().getFirst(), 1L), List.of(), null);
        var budget = new ECOPlanningBudget(ECOCancellation.NONE, 10, Long.MAX_VALUE, () -> 0L);
        assertThrows(ECOPlanningBudget.Exhausted.class, () -> new BoundedCycleSolver().solve(request, budget));
        assertThrows(InterruptedException.class, () -> new BoundedCycleSolver().solve(request, () -> { throw new InterruptedException(); }));
        int previousMemory = NEConfig.ecoPlanningMaxMemoryMiB;
        try {
            NEConfig.ecoPlanningMaxMemoryMiB = 1;
            var wide = ring(200);
            var result = solve(wide, Map.of(wide.members().getFirst(), 100L), Map.of(wide.members().getFirst(), 1L));
            assertEquals(CycleSolveStatus.UNKNOWN_BUDGET, result.status());
            assertTrue(result.diagnostics().stream().anyMatch(d -> d.code() == CycleSolveDiagnostic.Code.MEMORY_BUDGET_EXHAUSTED));
            assertFalse(result.hasExactExecutionCounts());
        } finally {
            NEConfig.ecoPlanningMaxMemoryMiB = previousMemory;
        }
    }

    private static CycleSolveResult solve(CycleComponent cycle, Map<AEKey, Long> targets, Map<AEKey, Long> stock) throws Exception {
        return new BoundedCycleSolver().solve(new CycleSolveRequest(cycle, targets, stock, List.of(), null), ECOCancellation.NONE);
    }

    private static CycleComponent ring(int size) {
        var keys = new ArrayList<AEKey>();
        var patterns = new ArrayList<CompiledPattern>();
        for (int i = 0; i < size; i++) keys.add(mock(AEKey.class, "key" + i));
        // IDs oppose execution order, forcing the equation witness to span multiple sweeps.
        for (int i = 0; i < size; i++) patterns.add(pattern(size - i - 1, Map.of(keys.get(i), 1L),
            Map.of(keys.get((i + 1) % size), i == size - 1 ? 2L : 1L), false));
        return new CycleComponent(0, keys, patterns, List.of(), List.of(), List.of());
    }

    private static CompiledPattern pattern(int id, Map<AEKey, Long> inputs, Map<AEKey, Long> outputs, boolean growthValidated) {
        var details = mock(IPatternDetails.class, "pattern" + id);
        var stacks = outputs.entrySet().stream().map(e -> new GenericStack(e.getKey(), e.getValue())).toList();
        when(details.getOutputs()).thenReturn(stacks);
        var semantics = new PatternSemantics(details, null, List.of(), stacks, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, details, stacks.getFirst().what(), PlannerAmount.of(stacks.getFirst().amount()),
            inputs.entrySet().stream().map(e -> new CompiledInput(null, e.getKey(), e.getValue(), true, null)).toList(),
            stacks, true, null, growthValidated, semantics);
    }

    // Independent inventory replay of prefix + repeated block, including the last lap's required stock.
    private static void replayCompact(CycleComponent cycle, CycleSolveResult result, long target) {
        var stock = new LinkedHashMap<AEKey, BigInteger>();
        cycle.members().forEach(key -> stock.put(key, BigInteger.ZERO));
        stock.put(cycle.members().getFirst(), BigInteger.ONE);
        int cursor = 0;
        while (cursor < result.executionPlan().size()) {
            int end = cursor;
            for (int i = cursor; i < result.executionPlan().size(); i++) {
                var run = result.executionPlan().get(i);
                if (run.repetitions() > 1 && i - run.repeatWidth() + 1 == cursor) { end = i; break; }
            }
            long repetitions = result.executionPlan().get(end).repetitions();
            var delta = new LinkedHashMap<AEKey, BigInteger>();
            for (int i = cursor; i <= end; i++) {
                var run = result.executionPlan().get(i);
                for (var input : run.pattern().inputs()) {
                    BigInteger use = input.amountPerPattern().toBigInteger().multiply(BigInteger.valueOf(run.count()));
                    assertTrue(stock.get(input.key()).add(delta.getOrDefault(input.key(), BigInteger.ZERO)).compareTo(use) >= 0);
                    delta.merge(input.key(), use.negate(), BigInteger::add);
                }
                for (var output : run.pattern().grossOutputs()) delta.merge(output.what(),
                    BigInteger.valueOf(output.amount()).multiply(BigInteger.valueOf(run.count())), BigInteger::add);
            }
            // Check the final lap independently too, so finite losses cannot hide behind a first-lap proof.
            var lastLap = new LinkedHashMap<>(stock);
            for (var entry : delta.entrySet()) lastLap.merge(entry.getKey(),
                entry.getValue().multiply(BigInteger.valueOf(repetitions - 1)), BigInteger::add);
            for (int i = cursor; i <= end; i++) {
                var run = result.executionPlan().get(i);
                for (var input : run.pattern().inputs()) {
                    BigInteger use = input.amountPerPattern().toBigInteger().multiply(BigInteger.valueOf(run.count()));
                    assertTrue(lastLap.get(input.key()).compareTo(use) >= 0);
                    lastLap.merge(input.key(), use.negate(), BigInteger::add);
                }
                for (var output : run.pattern().grossOutputs()) lastLap.merge(output.what(),
                    BigInteger.valueOf(output.amount()).multiply(BigInteger.valueOf(run.count())), BigInteger::add);
            }
            for (var entry : delta.entrySet()) stock.merge(entry.getKey(),
                entry.getValue().multiply(BigInteger.valueOf(repetitions)), BigInteger::add);
            assertEquals(stock, lastLap);
            cursor = end + 1;
        }
        assertEquals(BigInteger.valueOf(target), stock.get(cycle.members().getFirst()));
    }
}
