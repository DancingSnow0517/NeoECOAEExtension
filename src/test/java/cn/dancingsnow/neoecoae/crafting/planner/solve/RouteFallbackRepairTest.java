package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RouteFallbackRepairTest {
    private final AEKey goal = key("goal"), raw = key("raw"), other = key("other"), tool = key("tool");
    private int nextId;

    @Test void substitutionDoesNotDisableAnIndependentQuantityProof() throws Exception {
        var substitute = substitution(other, 1, new GenericStack(tool, 1), new GenericStack(other, 1));
        var finish = exact(goal, Map.of(raw, 1L, other, 1L));
        var stock = stock(Map.of(raw, 1L, tool, 1_000_000_000L));
        assertEquals(PlannerAmount.ONE, RouteQuantityProof.insufficientOutput(network(finish, substitute),
            stock, 1_000_000_000L, ECOCancellation.NONE));
        assertFalse(RouteAvailabilityProof.goalUnreachable(network(finish, substitute), stock, ECOCancellation.NONE));
    }

    @Test void missingLeafStillProvesUnreachableAlongsideSubstitution() throws Exception {
        var substitute = substitution(other, 1, new GenericStack(tool, 1), new GenericStack(other, 1));
        var finish = exact(goal, Map.of(raw, 1L, other, 1L));
        assertTrue(RouteAvailabilityProof.goalUnreachable(network(finish, substitute),
            stock(Map.of(tool, 10L)), ECOCancellation.NONE));
    }

    @Test void substitutedStockAndMixedSlotsCannotBeUnderestimated() throws Exception {
        var finish = substitution(goal, 4, new GenericStack(raw, 1), new GenericStack(other, 1));
        var available = stock(Map.of(raw, 2L, other, 2L));
        assertNull(RouteQuantityProof.insufficientOutput(network(finish), available, 1, ECOCancellation.NONE));
        assertEquals(PlannerAmount.ONE, RouteQuantityProof.insufficientOutput(network(finish), available, 2, ECOCancellation.NONE));
        assertFalse(RouteAvailabilityProof.goalUnreachable(network(finish), stock(Map.of(other, 4L)), ECOCancellation.NONE));
    }

    @Test void everySubstituteRemainderIsAvailableToOtherBranches() throws Exception {
        var producer = substitution(other, 1, new GenericStack(tool, 1), new GenericStack(other, 1));
        when(producer.inputs().get(0).source().getRemainingKey(tool)).thenReturn(raw);
        var finish = exact(goal, Map.of(other, 1L, raw, 1L));
        assertNull(RouteQuantityProof.insufficientOutput(network(finish, producer), stock(Map.of(tool, 1L)),
            1, ECOCancellation.NONE));
        assertFalse(RouteAvailabilityProof.goalUnreachable(network(finish, producer), stock(Map.of(tool, 1L)), ECOCancellation.NONE));
    }

    @Test void specialToolSlotCannotSuppressIndependentFiniteShortage() throws Exception {
        var normal = exact(goal, Map.of(raw, 1L, tool, 1L));
        var specialInput = normal.inputs().stream().filter(i -> i.key().equals(tool)).findFirst().orElseThrow();
        var special = new CompiledPattern(normal.id(), normal.details(), goal, PlannerAmount.ONE, normal.inputs(),
            normal.outputs(), true, null, false, normal.semantics(), new SpecialPatternAnalysis(List.of(
                new SpecialPatternAnalysis.Requirement(specialInput, tool, SpecialPatternAnalysis.Type.DURABILITY, 1, 100))));
        assertEquals(PlannerAmount.ONE, RouteQuantityProof.insufficientOutput(network(special), stock(Map.of(raw, 1L)),
            1_000_000_000L, ECOCancellation.NONE));
    }

    @Test void opaqueProducerWeakensOnlyItsOwnSupply() throws Exception {
        var original = exact(other, Map.of());
        var opaque = new CompiledPattern(original.id(), original.details(), other, PlannerAmount.ONE,
            original.inputs(), original.outputs(), false, "UNKNOWN_EXECUTION_CONTRACT", false, original.semantics());
        var finish = exact(goal, Map.of(raw, 1L, other, 1L));
        assertEquals(PlannerAmount.ONE, RouteQuantityProof.insufficientOutput(network(finish, opaque), stock(Map.of(raw, 1L)),
            2, ECOCancellation.NONE));
        assertNull(RouteQuantityProof.insufficientOutput(network(opaque, exact(goal, Map.of(other, 1L))),
            stock(Map.of()), Long.MAX_VALUE, ECOCancellation.NONE));
    }

    @Test void partialChoicesPruneOnlyPrefixesWithNoFeasibleCompletion() throws Exception {
        var blocked = exact(goal, Map.of(raw, 2L));
        var available = exact(goal, Map.of(other, 1L));
        var network = network(blocked, available);
        var proof = RouteQuantityProof.prepare(network, stock(Map.of(raw, 1L, other, 1L)), ECOCancellation.NONE);
        assertNotNull(proof);
        assertFalse(proof.rejectsBranch(1, Map.of(goal, 0), Set.of(), ECOCancellation.NONE));
        assertTrue(proof.rejectsBranch(1, Map.of(goal, 0), Set.of(goal), ECOCancellation.NONE));
        assertFalse(proof.rejectsBranch(1, Map.of(goal, 1), Set.of(goal), ECOCancellation.NONE));
        assertTrue(proof.rejectsBranch(2, Map.of(goal, 1), Set.of(goal), ECOCancellation.NONE),
            "Proof caches must distinguish the requested quantity");
        var search = new RouteSearch(network, Map.of(goal, 0), (choices, fixed, cancel) ->
            proof.rejectsBranch(1, choices, fixed, cancel));
        assertEquals(1, search.next(ECOCancellation.NONE).get(goal));
        assertNull(search.next(ECOCancellation.NONE));
    }

    @Test void wholeUnrelatedChoiceSubtreesAreSkippedBeforeFullPlanning() throws Exception {
        var branch = key("branch");
        var finish = exact(goal, Map.of(other, 1L, branch, 1L));
        var blocked = exact(other, Map.of(raw, 2L));
        var available = exact(other, Map.of(tool, 1L));
        var first = exact(branch, Map.of());
        var second = exact(branch, Map.of());
        var network = network(finish, blocked, available, first, second);
        var proof = RouteQuantityProof.prepare(network, stock(Map.of(raw, 1L, tool, 1L)), ECOCancellation.NONE);
        var search = new RouteSearch(network, Map.of(goal, 0, other, 0, branch, 0),
            (choices, fixed, cancel) -> proof.rejectsBranch(1, choices, fixed, cancel));
        int count = 0;
        Map<AEKey, Integer> choice;
        while ((choice = search.next(ECOCancellation.NONE)) != null) {
            assertEquals(1, choice.get(other), "Changing an unrelated producer cannot fix the raw shortage");
            count++;
        }
        assertEquals(2, count, "Both feasible choices on the independent branch must remain reachable");
        assertTrue(search.prunedBranches() > 0);
    }

    @Test void widePartialReportNeverNarrowsIntoAe2Counters() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
        var state = new SolveState(new KeyCounter());
        var wide = PlannerAmount.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
        state.markMissing(raw, wide);
        state.bytes = wide;
        var plan = assertDoesNotThrow(() -> new AE2CraftingPlanBridge().partial(goal, 1, true, state));
        assertTrue(plan.simulation());
        assertTrue(plan.missingItems().isEmpty());
        assertTrue(plan.patternTimes().isEmpty());
        assertEquals(wide, state.missingAmounts().get(raw));
        assertEquals(wide, state.plannerBytes());
    }

    private CompiledPattern exact(AEKey output, Map<AEKey, Long> inputs) {
        var compiled = inputs.entrySet().stream().map(e -> new CompiledInput(null, e.getKey(), e.getValue(), true, null)).toList();
        return pattern(output, compiled, PatternSemantics.MatchingMode.EXACT);
    }

    private CompiledPattern substitution(AEKey output, long multiplier, GenericStack... alternatives) {
        var source = mock(IPatternDetails.IInput.class);
        when(source.getPossibleInputs()).thenReturn(alternatives);
        when(source.getMultiplier()).thenReturn(multiplier);
        var first = alternatives[0];
        return pattern(output, List.of(new CompiledInput(source, first.what(), PlannerAmount.of(first.amount()).multiply(multiplier),
            true, null)), PatternSemantics.MatchingMode.SUBSTITUTION);
    }

    private CompiledPattern pattern(AEKey output, List<CompiledInput> inputs, PatternSemantics.MatchingMode mode) {
        var details = mock(IPatternDetails.class);
        var outputs = List.of(new GenericStack(output, 1));
        var consumed = inputs.stream().map(i -> new PatternSemantics.Input(i.source(), i.key(), i.amountPerPattern(), null, PlannerAmount.ZERO)).toList();
        var semantics = new PatternSemantics(details, null, consumed, outputs, List.of(), List.of(), mode,
            PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(nextId++, details, output, PlannerAmount.ONE, inputs, outputs, true, null, false, semantics);
    }

    private CompiledNetwork network(CompiledPattern... patterns) {
        var producers = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        producers.put(goal, new ArrayList<>());
        for (var pattern : patterns) {
            AEKey owner = pattern.producedKey();
            producers.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(pattern);
            for (var input : pattern.inputs()) producers.computeIfAbsent(input.key(), ignored -> new ArrayList<>());
        }
        return new CompiledNetwork(goal, producers, Set.of(), patterns.length, patterns.length);
    }

    private static PlannerInventorySnapshot stock(Map<AEKey, Long> values) {
        var counter = new KeyCounter(); values.forEach(counter::set);
        return PlannerInventorySnapshot.of(counter);
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name); when(key.getAmountPerByte()).thenReturn(8); return key;
    }
}
