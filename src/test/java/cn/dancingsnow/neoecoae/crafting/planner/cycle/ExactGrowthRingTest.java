package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExactGrowthRingTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5})
    void wideCountsKeepAnExactSolutionWithoutBoundedSearch(int size) throws Exception {
        var ring = ring(size);
        for (var amount : List.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                BigInteger.TEN.pow(50))) {
            var result = solve(ring, amount, Map.of(ring.members().getFirst(), 1L));
            assertEquals(CycleSolveStatus.UNREPRESENTABLE, result.status(), result.diagnostics().toString());
            assertTrue(result.hasExactExecutionCounts());
            assertTrue(result.exactPatternTimes().values().stream().anyMatch(count -> !count.fitsLong()));
            assertTrue(result.seedShortfall().isEmpty());
            assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.code() == CycleSolveDiagnostic.Code.DETERMINISTIC_RING_EXACT));
            assertTrue(result.diagnostics().stream().noneMatch(d ->
                d.code() == CycleSolveDiagnostic.Code.STATE_BUDGET_EXHAUSTED));
            // Independently check the material balance using the returned exact firing counts.
            for (int key = 0; key < size; key++) {
                BigInteger consumed = result.exactPatternTimes().get(ring.patterns().get(key).details()).toBigInteger();
                int producer = (key - 1 + size) % size;
                BigInteger produced = result.exactPatternTimes().get(ring.patterns().get(producer).details())
                    .toBigInteger().multiply(BigInteger.valueOf(producer == size - 1 ? 2 : 1));
                BigInteger balance = produced.subtract(consumed).add(BigInteger.valueOf(key == 0 ? 1 : 0));
                assertTrue(balance.compareTo(key == 0 ? amount : BigInteger.ZERO) >= 0);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void wideTargetStillReportsMissingStartupSeed(int size) throws Exception {
        var ring = ring(size);
        var result = solve(ring, BigInteger.TEN.pow(20), Map.of());
        assertEquals(CycleSolveStatus.UNREPRESENTABLE, result.status());
        assertTrue(result.hasExactExecutionCounts());
        assertFalse(result.seedShortfall().isEmpty(), "Exact arithmetic must not create a free startup seed");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void smallRingRetainsAReplayableExecutionPlan(int size) throws Exception {
        var ring = ring(size);
        var result = solve(ring, BigInteger.valueOf(32), Map.of(ring.members().getFirst(), 1L));
        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.diagnostics().toString());
        var stock = new java.util.LinkedHashMap<AEKey, BigInteger>();
        ring.members().forEach(key -> stock.put(key, BigInteger.ZERO));
        stock.put(ring.members().getFirst(), BigInteger.ONE);
        for (var firing : result.executionWitness()) {
            for (var input : firing.pattern().inputs()) {
                stock.compute(input.key(), (key, count) -> count.subtract(input.amountPerPattern().toBigInteger()));
                assertTrue(stock.get(input.key()).signum() >= 0);
            }
            for (var output : firing.pattern().grossOutputs()) {
                stock.compute(output.what(), (key, count) -> count.add(BigInteger.valueOf(output.amount())));
            }
        }
        assertTrue(stock.get(ring.members().getFirst()).compareTo(BigInteger.valueOf(32)) >= 0);
    }

    private static CycleSolveResult solve(CycleComponent ring, BigInteger amount, Map<AEKey, Long> stock)
            throws Exception {
        // Any fallback to bounded search fails this budget; exact solving is independent of it.
        var limits = new CycleSolveLimits(8, 16, 1, 1, 0);
        return new BoundedCycleSolver().solve(new CycleSolveRequest(ring, Map.of(),
            Map.of(ring.members().getFirst(), PlannerAmount.of(amount)), stock, List.of(),
            new CycleSolveRequest.PlannerOptions(limits)), ECOCancellation.NONE);
    }

    private static CycleComponent ring(int size) {
        List<AEKey> keys = new ArrayList<>();
        List<CompiledPattern> patterns = new ArrayList<>();
        for (int i = 0; i < size; i++) keys.add(mock(AEKey.class));
        for (int i = 0; i < size; i++) {
            var details = mock(IPatternDetails.class);
            AEKey output = keys.get((i + 1) % size);
            long count = i == size - 1 ? 2 : 1;
            var outputs = List.of(new GenericStack(output, count));
            when(details.getOutputs()).thenReturn(outputs);
            var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
                PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
            patterns.add(new CompiledPattern(i, details, output, PlannerAmount.of(count),
                List.of(new CompiledInput(null, keys.get(i), 1, true, null)), outputs,
                true, null, false, semantics));
        }
        return new CycleComponent(0, keys, patterns, List.of(), List.of(), List.of());
    }
}
