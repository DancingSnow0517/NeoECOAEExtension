package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RouteQuantityProofTest {
    private int nextId;
    private final AEKey goal = mock(AEKey.class, "goal");
    private final AEKey raw = mock(AEKey.class, "raw");
    private final AEKey other = mock(AEKey.class, "other");

    @Test void positiveButInsufficientStockHasAQuantityProof() throws Exception {
        var network = network(pattern(Map.of(raw, PlannerAmount.of(4)), Map.of(goal, 3L)));
        assertEquals(PlannerAmount.of(6), bound(network, Map.of(raw, 9L), 1_000_000_000L));
        assertNull(bound(network, Map.of(raw, 9L), 6));
    }

    @Test void allAlternativeProducersContributeOptimisticSupply() throws Exception {
        var first = pattern(Map.of(raw, PlannerAmount.ONE), Map.of(goal, 1L));
        var second = pattern(Map.of(raw, PlannerAmount.ONE), Map.of(goal, 10L));
        var network = network(first, second);
        assertNull(bound(network, Map.of(raw, 1L), 10));
        assertEquals(PlannerAmount.of(11), bound(network, Map.of(raw, 1L), 12));
    }

    @Test void competingConsumersCannotMakeTheProofTooPessimistic() throws Exception {
        var a = pattern(Map.of(raw, PlannerAmount.ONE), Map.of(other, 1L));
        var b = pattern(Map.of(raw, PlannerAmount.ONE, other, PlannerAmount.ONE), Map.of(goal, 1L));
        assertNull(bound(network(a, b), Map.of(raw, 1L), 1), "Each producer gets the whole optimistic supply");
    }

    @Test void growingCyclesRemainUnbounded() throws Exception {
        var growth = pattern(Map.of(other, PlannerAmount.ONE), Map.of(other, 2L));
        var output = pattern(Map.of(other, PlannerAmount.ONE), Map.of(goal, 1L));
        assertNull(bound(network(growth, output), Map.of(other, 1L), Long.MAX_VALUE));
        assertNull(bound(network(growth, output), Map.of(), Long.MAX_VALUE), "Seedlessness is not a quantity proof");
    }

    @Test void finiteBoundaryInputCanLimitAGrowingCycle() throws Exception {
        var growth = pattern(Map.of(other, PlannerAmount.ONE, raw, PlannerAmount.ONE), Map.of(other, 2L));
        var output = pattern(Map.of(other, PlannerAmount.ONE), Map.of(goal, 1L));
        assertEquals(PlannerAmount.of(7), bound(network(growth, output), Map.of(other, 1L, raw, 3L), 8));
    }

    @Test void reusableReturnedMaterialDoesNotLimitTheBatchToItsStartingStock() throws Exception {
        var recipe = pattern(Map.of(other, PlannerAmount.ONE, raw, PlannerAmount.ONE), Map.of(goal, 1L, other, 1L));
        assertNull(bound(network(recipe), Map.of(other, 1L, raw, 100L), 100));
        assertEquals(PlannerAmount.of(100), bound(network(recipe), Map.of(other, 1L, raw, 100L), 101));
    }

    @Test void unadvertisedByproductsAreIncludedOptimistically() throws Exception {
        var byproduct = pattern(Map.of(raw, PlannerAmount.ONE), Map.of(other, 1L, goal, 5L));
        var producers = Map.<AEKey, List<CompiledPattern>>of(other, List.of(byproduct), raw, List.of(), goal, List.of());
        var network = new CompiledNetwork(goal, producers, Set.of(), 1, 1);
        assertNull(bound(network, Map.of(raw, 1L), 5));
    }

    @Test void creativeAndEmittedInputsAreNotCappedAtLongMaxValue() throws Exception {
        var recipe = pattern(Map.of(raw, PlannerAmount.of(BigInteger.ONE.shiftLeft(80))), Map.of(goal, 1L));
        var inventory = PlannerInventorySnapshot.of(new KeyCounter(), Set.of(raw));
        assertNull(RouteQuantityProof.insufficientOutput(network(recipe), inventory, 1, ECOCancellation.NONE));
        var emitted = new CompiledNetwork(goal, network(recipe).producers(), Set.of(raw), 1, 1);
        assertNull(RouteQuantityProof.insufficientOutput(emitted, PlannerInventorySnapshot.of(new KeyCounter()), 1, ECOCancellation.NONE));
    }

    @Test void wideIntermediateAmountsAreNeverTruncated() throws Exception {
        var recipe = pattern(Map.of(raw, PlannerAmount.of(BigInteger.ONE.shiftLeft(80))), Map.of(goal, 1L));
        assertEquals(PlannerAmount.ZERO, bound(network(recipe), Map.of(raw, Long.MAX_VALUE), 1));
    }

    @Test void unknownContractsDeclineTheProof() throws Exception {
        var unsupported = mock(CompiledPattern.class);
        when(unsupported.fastSupported()).thenReturn(false);
        when(unsupported.grossOutputs()).thenReturn(List.of(new GenericStack(goal, 1)));
        var network = network(unsupported);
        assertNull(bound(network, Map.of(raw, 1L), Long.MAX_VALUE));
    }

    @Test void cancellationIsPropagated() {
        var network = network(pattern(Map.of(raw, PlannerAmount.ONE), Map.of(goal, 1L)));
        assertThrows(InterruptedException.class, () -> RouteQuantityProof.insufficientOutput(network,
            PlannerInventorySnapshot.of(new KeyCounter()), 1, () -> { throw new InterruptedException(); }));
    }

    private PlannerAmount bound(CompiledNetwork network, Map<AEKey, Long> available, long amount) throws Exception {
        var inventory = new KeyCounter();
        available.forEach(inventory::set);
        return RouteQuantityProof.insufficientOutput(network, PlannerInventorySnapshot.of(inventory), amount, ECOCancellation.NONE);
    }

    private CompiledNetwork network(CompiledPattern... patterns) {
        var producers = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        producers.put(goal, new ArrayList<>());
        producers.put(raw, new ArrayList<>());
        producers.put(other, new ArrayList<>());
        for (CompiledPattern pattern : patterns) {
            for (GenericStack output : pattern.grossOutputs()) producers.computeIfAbsent(output.what(), key -> new ArrayList<>()).add(pattern);
        }
        return new CompiledNetwork(goal, producers, Set.of(), patterns.length, patterns.length);
    }

    private CompiledPattern pattern(Map<AEKey, PlannerAmount> inputs, Map<AEKey, Long> outputs) {
        var details = mock(IPatternDetails.class);
        var stacks = outputs.entrySet().stream().map(e -> new GenericStack(e.getKey(), e.getValue())).toList();
        var compiled = inputs.entrySet().stream().map(e -> new CompiledInput(null, e.getKey(), e.getValue(), true, null)).toList();
        var consumed = inputs.entrySet().stream().map(e -> new PatternSemantics.Input(null, e.getKey(), e.getValue(), null, PlannerAmount.ZERO)).toList();
        var semantics = new PatternSemantics(details, null, consumed, stacks, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(nextId++, details, goal, PlannerAmount.ONE, compiled, stacks, true, null, false, semantics);
    }
}
