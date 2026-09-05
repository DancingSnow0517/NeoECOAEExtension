package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveMetrics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.PatternRun;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CycleExecutionDisposition;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionPlanBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.RuntimeExecutionState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuntimeExecutionStateTest {
    private final PlannerTestKey a = PlannerTestKey.of("runtime_a");
    private final PlannerTestKey b = PlannerTestKey.of("runtime_b");
    private final PlannerTestKey c = PlannerTestKey.of("runtime_c");
    private final PlannerFixtures.Pattern first = PlannerFixtures.pattern("first", a, 1);
    private final PlannerFixtures.Pattern second = PlannerFixtures.pattern("second", b, 1);
    private final PlannerFixtures.Pattern consumer = PlannerFixtures.pattern("consumer", c, 1, a, 1L);

    @Test
    void compressedTenThousandRunAdvancesAtomicallyThenReleasesAggregateRemainder() {
        ECOExecutionPlan plan = plan();
        RuntimeExecutionState state = new RuntimeExecutionState(plan);

        assertEquals(List.of(0), state.eligibleTaskIds());
        assertEquals(10_000L, state.dispatchLimit(0));
        assertEquals(List.of(1), state.applyAccepted(0, 10_000L));
        assertEquals(1, state.stepIndex());
        assertEquals(List.of(1), state.eligibleTaskIds());

        assertEquals(List.of(0), state.applyAccepted(1, 1L));
        assertEquals(List.of(0), state.eligibleTaskIds());
        assertEquals(List.of(0, 1), state.applyAccepted(0, 2L));
        assertEquals(3, state.stepIndex());
        assertEquals(Set.of(0, 1), Set.copyOf(state.eligibleTaskIds()),
            "after the verified cycle trace, the cycle phase owns the aggregate DAG remainder");

        state.applyAccepted(0, 3L);
        assertEquals(List.of(2), state.applyAccepted(1, 1L));
        assertEquals(1, state.phaseIndex(), "phase completion depends only on dispatched task counts");
        assertEquals(List.of(2), state.eligibleTaskIds());
        state.acceptOutput(a, 1L);
        state.applyAccepted(2, 1L);
        assertTrue(state.finished());
    }

    @Test
    void restoreIncludesCompressedStepRemainderAndRejectsCursorAccountingDrift() {
        ECOExecutionPlan plan = plan();
        RuntimeExecutionState state = new RuntimeExecutionState(plan);
        state.restore(new long[] {5_005L, 2L, 1L}, new int[] {0, 0}, new long[] {5_000L, 0L});
        assertEquals(5_000L, state.dispatchLimit(0));
        assertEquals(List.of(0), state.eligibleTaskIds(), "restore must rebuild the ready frontier once");
        assertThrows(IllegalArgumentException.class,
            () -> state.restore(new long[] {15_005L, 2L, 1L}, new int[] {0, 0}, new long[] {5_000L, 0L}));
        assertThrows(IllegalArgumentException.class,
            () -> state.restore(new long[] {5_005L, 2L, 1L}, new int[] {1, 0}, new long[] {1L, 0L}));
    }

    @Test
    void builderUsesCompactSolverRunsWithoutExpandingOrReadingLegacyWitness() {
        var firstCompiled = PlannerFixtures.compiled(0, first, a, true, "test");
        var secondCompiled = PlannerFixtures.compiled(1, second, b, true, "test");
        var cycleResult = new CycleSolveResult(CycleSolveStatus.SUCCESS,
            Map.of(first, 10_000L, second, 2L), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
            List.of(), List.of(new PatternRun(firstCompiled, 10_000L), new PatternRun(secondCompiled, 2L)),
            List.of(), CycleSolveMetrics.NONE);
        var component = new ComponentPlanningResult(10, ComponentPlanningResult.Type.CYCLIC,
            ComponentPlanningResult.Status.PLANNED, Map.of(a, 1L), Set.of(first, second),
            Set.of(first, second), CyclePlanningStatus.SOLVED, null, Map.of(), null, cycleResult,
            CycleExecutionDisposition.ORDERED_EXECUTION, Map.of());
        var signature = new PlanIdentity.Signature(a, 1L,
            Map.of(PlanIdentity.patternIdentityFor(first), 10_000L,
                PlanIdentity.patternIdentityFor(second), 2L), Map.of(), Map.of(), Map.of());

        ECOExecutionPlan built = ECOExecutionPlanBuilder.build(signature, ExecutionMode.ORDERED_CYCLE,
            List.of(component), List.of(10), Map.of(first, 10_000L, second, 2L));

        assertEquals(List.of(10_000L, 2L), built.phases().getFirst().steps().stream()
            .map(ECOExecutionPlan.ExecutionStep::count).toList());
        assertTrue(built.schedule().phases().getFirst().cycleWitness().isEmpty(),
            "the immutable execution plan must not depend on an expanded legacy witness");
    }

    @Test
    void independentPhasesRunTogetherAndUnlockConsumerWithoutWaveBarrier() {
        var firstIdentity = PlanIdentity.patternIdentityFor(first);
        var secondIdentity = PlanIdentity.patternIdentityFor(second);
        var consumerIdentity = PlanIdentity.patternIdentityFor(consumer);
        var tasks = List.of(
            new ECOExecutionPlan.TaskSpec(0, firstIdentity, first, ECOExecutionPlan.PatternRuntimeInfo.from(first), 1, 0, ECOExecutionPlan.TaskKind.DAG),
            new ECOExecutionPlan.TaskSpec(1, secondIdentity, second, ECOExecutionPlan.PatternRuntimeInfo.from(second), 2, 1, ECOExecutionPlan.TaskKind.DAG),
            new ECOExecutionPlan.TaskSpec(2, consumerIdentity, consumer, ECOExecutionPlan.PatternRuntimeInfo.from(consumer), 1, 2, ECOExecutionPlan.TaskKind.DAG));
        var phases = List.of(
            new ECOExecutionPlan.PhaseSpec(0, 1, ECOExecutionSchedule.Type.DAG, List.of(0), List.of(), List.of()),
            new ECOExecutionPlan.PhaseSpec(1, 2, ECOExecutionSchedule.Type.DAG, List.of(1), List.of(), List.of()),
            new ECOExecutionPlan.PhaseSpec(2, 3, ECOExecutionSchedule.Type.DAG, List.of(2), List.of(), List.of(0)));
        var signature = new PlanIdentity.Signature(c, 1, Map.of(firstIdentity, 1L, secondIdentity, 2L, consumerIdentity, 1L), Map.of(), Map.of(), Map.of());
        var schedulePhases = List.of(
            new ECOExecutionSchedule.ComponentExecutionPhase(1, ECOExecutionSchedule.Type.DAG, Set.of(first), List.of()),
            new ECOExecutionSchedule.ComponentExecutionPhase(2, ECOExecutionSchedule.Type.DAG, Set.of(second), List.of()),
            new ECOExecutionSchedule.ComponentExecutionPhase(3, ECOExecutionSchedule.Type.DAG, Set.of(consumer), List.of()));
        var state = new RuntimeExecutionState(new ECOExecutionPlan(signature, ExecutionMode.PHASED_DAG, tasks, phases,
            new ECOExecutionSchedule(schedulePhases, List.of(new ECOExecutionSchedule.PhaseDependency(0, 2)))));

        assertEquals(Set.of(0, 1), Set.copyOf(state.eligibleTaskIds()));
        assertEquals(List.of(2), state.applyAccepted(0, 1));
        assertEquals(Set.of(1, 2), Set.copyOf(state.eligibleTaskIds()),
            "consumer should unlock as soon as its own producer completes");

        var restored = new RuntimeExecutionState(state.plan());
        restored.restore(new long[] {0L, 2L, 1L}, new int[] {0, 0, 0}, new long[] {0L, 0L, 0L});
        assertEquals(Set.of(1, 2), Set.copyOf(restored.eligibleTaskIds()),
            "restore should reconstruct independent ready work and newly satisfied dependents");
    }

    @Test
    void futureCycleFeedbackIsReservedBeforeItsDagDependencyCompletes() {
        var material = PlannerTestKey.of("runtime_material");
        var prepared = PlannerTestKey.of("runtime_prepared");
        var seed = PlannerTestKey.of("runtime_seed");
        var prepare = PlannerFixtures.pattern("prepare", prepared, 1, material, 1L);
        var grow = PlannerFixtures.pattern("grow", seed, 2, seed, 1L, prepared, 1L);
        var prepareIdentity = PlanIdentity.patternIdentityFor(prepare);
        var growIdentity = PlanIdentity.patternIdentityFor(grow);
        var tasks = List.of(
            new ECOExecutionPlan.TaskSpec(0, prepareIdentity, prepare,
                ECOExecutionPlan.PatternRuntimeInfo.from(prepare), 99L, 0, ECOExecutionPlan.TaskKind.DAG),
            new ECOExecutionPlan.TaskSpec(1, growIdentity, grow,
                ECOExecutionPlan.PatternRuntimeInfo.from(grow), 99L, 1,
                ECOExecutionPlan.TaskKind.CYCLE_ORDERED));
        var phases = List.of(
            new ECOExecutionPlan.PhaseSpec(0, 1, ECOExecutionSchedule.Type.DAG,
                List.of(0), List.of(), List.of()),
            new ECOExecutionPlan.PhaseSpec(1, 2, ECOExecutionSchedule.Type.CYCLE,
                List.of(1), List.of(new ECOExecutionPlan.ExecutionStep(1, 99L)), List.of(0)));
        var signature = new PlanIdentity.Signature(seed, 100L,
            Map.of(prepareIdentity, 99L, growIdentity, 99L), Map.of(seed, 1L, material, 99L),
            Map.of(), Map.of());
        var schedule = new ECOExecutionSchedule(List.of(
            new ECOExecutionSchedule.ComponentExecutionPhase(1, ECOExecutionSchedule.Type.DAG,
                Set.of(prepare), List.of()),
            new ECOExecutionSchedule.ComponentExecutionPhase(2, ECOExecutionSchedule.Type.CYCLE,
                Set.of(grow), List.of())),
            List.of(new ECOExecutionSchedule.PhaseDependency(0, 1)));
        var state = new RuntimeExecutionState(new ECOExecutionPlan(signature, ExecutionMode.ORDERED_CYCLE,
            tasks, phases, schedule));

        state.restoreOwnership(Map.of(material, 99L, seed, 99L));
        assertEquals(List.of(0), state.eligibleTaskIds(), "the cycle must still be blocked by its DAG input");
        assertEquals(99L, state.pendingCycleFeedbackReserve(seed),
            "network seed must remain CPU-owned before the cycle phase becomes eligible");
        state.applyAccepted(0, 99L);
        assertEquals(List.of(1), state.eligibleTaskIds());
        state.acceptOutput(prepared, 99L);
        state.applyAccepted(1, 99L);
        assertEquals(0L, state.pendingCycleFeedbackReserve(seed));
    }

    @Test
    void cycleKeepsBootstrapSeedAfterItsLastDispatch() {
        var seed = PlannerTestKey.of("runtime_bootstrap_seed");
        var grow = PlannerFixtures.pattern("grow_seed", seed, 2, seed, 1L);
        var identity = PlanIdentity.patternIdentityFor(grow);
        var task = new ECOExecutionPlan.TaskSpec(0, identity, grow,
            ECOExecutionPlan.PatternRuntimeInfo.from(grow), 1L, 0,
            ECOExecutionPlan.TaskKind.CYCLE_ORDERED);
        var phase = new ECOExecutionPlan.PhaseSpec(0, 12, ECOExecutionSchedule.Type.CYCLE,
            List.of(0), List.of(new ECOExecutionPlan.ExecutionStep(0, 1L)), List.of(),
            Map.of(), Map.of(seed, 1L));
        var signature = new PlanIdentity.Signature(seed, 1L, Map.of(identity, 1L),
            Map.of(seed, 1L), Map.of(seed, 2L), Map.of());
        var schedule = new ECOExecutionSchedule(List.of(
            new ECOExecutionSchedule.ComponentExecutionPhase(12, ECOExecutionSchedule.Type.CYCLE,
                Set.of(grow), List.of())));
        var state = new RuntimeExecutionState(new ECOExecutionPlan(signature, ExecutionMode.ORDERED_CYCLE,
            List.of(task), List.of(phase), schedule));

        state.restoreOwnership(Map.of(seed, 1L));
        state.applyAccepted(0, 1L);

        assertEquals(2L, state.cycleLedger(12).generated(seed));
        assertEquals(1L, state.pendingCycleFeedbackReserve(seed),
            "the bootstrap seed must stay reserved after the final cycle dispatch");
        state.acceptOutput(seed, 2L);
        assertEquals(1L, state.pendingCycleFeedbackReserve(seed),
            "receiving the final output must not release the bootstrap seed");
    }

    @Test
    void reusableInputUsesThePhysicalBatchTransferInsteadOfMultiplyingStaticDemand() {
        AEKey tool = PlannerTestKey.of("runtime_reusable_tool");
        var pattern = new PlannerFixtures.Pattern("runtime-reusable",
            new IPatternDetails.IInput[] {new PlannerFixtures.Input(tool, 1L, true)},
            List.of(new GenericStack(c, 1L)));
        RuntimeExecutionState state = new RuntimeExecutionState(singleTaskPlan(pattern, 100L));
        state.restoreOwnership(Map.of(tool, 1L));

        assertDoesNotThrow(() -> state.commitAccepted(0, 100L, Map.of(tool, 1L)));
        assertEquals(0L, state.onHand(tool));
        assertEquals(0L, state.remaining(0));
    }

    @Test
    void actualSubstitutionKeyIsRegisteredAndDebited() {
        AEKey primary = PlannerTestKey.of("runtime_primary_input");
        AEKey substitute = PlannerTestKey.of("runtime_substitute_input");
        IPatternDetails.IInput input = new IPatternDetails.IInput() {
            @Override public GenericStack[] getPossibleInputs() {
                return new GenericStack[] {new GenericStack(primary, 1L), new GenericStack(substitute, 1L)};
            }

            @Override public long getMultiplier() { return 1L; }

            @Override public boolean isValid(AEKey candidate, net.minecraft.world.level.Level level) {
                return primary.equals(candidate) || substitute.equals(candidate);
            }

            @Override public AEKey getRemainingKey(AEKey template) { return template; }
        };
        var pattern = new PlannerFixtures.Pattern("runtime-substitution",
            new IPatternDetails.IInput[] {input}, List.of(new GenericStack(c, 1L)));
        RuntimeExecutionState state = new RuntimeExecutionState(singleTaskPlan(pattern, 100L));
        state.restoreOwnership(Map.of(substitute, 1L));

        assertTrue(state.resourceIdIfKnown(substitute) >= 0);
        assertDoesNotThrow(() -> state.commitAccepted(0, 100L, Map.of(substitute, 1L)));
        assertEquals(0L, state.onHand(substitute));
        assertEquals(0L, state.onHand(primary));
    }

    @Test
    void staticInputsStillUseTheCompiledPerCraftFallback() {
        AEKey input = PlannerTestKey.of("runtime_static_input");
        var pattern = PlannerFixtures.pattern("runtime-static", c, 1L, input, 1L);
        RuntimeExecutionState state = new RuntimeExecutionState(singleTaskPlan(pattern, 3L));
        state.restoreOwnership(Map.of(input, 3L));

        assertDoesNotThrow(() -> state.commitAccepted(0, 3L));
        assertEquals(0L, state.onHand(input));
    }

    @Test
    void unknownActualInputIsRejectedBeforeMutatingOwnership() {
        AEKey input = PlannerTestKey.of("runtime_known_input");
        AEKey unknown = PlannerTestKey.of("runtime_unknown_input");
        var pattern = PlannerFixtures.pattern("runtime-unknown", c, 1L, input, 1L);
        RuntimeExecutionState state = new RuntimeExecutionState(singleTaskPlan(pattern, 1L));
        state.restoreOwnership(Map.of(input, 1L));

        assertThrows(IllegalArgumentException.class,
            () -> state.validateActualConsumption(Map.of(unknown, 1L)));
        assertThrows(IllegalArgumentException.class,
            () -> state.commitAccepted(0, 1L, Map.of(unknown, 1L)));
        assertEquals(-1, state.resourceIdIfKnown(unknown));
        assertEquals(1L, state.onHand(input));
        assertEquals(1L, state.remaining(0));
    }

    @Test
    void runtimeSelectedStockOutsideTheCandidateListIsOwnedAndDebitedExactly() {
        AEKey template = PlannerTestKey.of("runtime_tool_template");
        AEKey damaged = PlannerTestKey.of("runtime_tool_damage_17");
        var pattern = statefulPattern(List.of(template, PlannerTestKey.of("runtime_tool_damage_1"), damaged));
        RuntimeExecutionState state = new RuntimeExecutionState(singleTaskPlan(pattern, 100L));
        assertEquals(-1, state.resourceIdIfKnown(damaged));

        state.restoreOwnership(Map.of(damaged, 1L, a, 100L));
        int damagedId = state.resourceIdIfKnown(damaged);
        var consumed = Map.of(damaged, 1L, a, 100L);
        var owned = state.ownershipSnapshot();
        state.validateActualConsumption(consumed);
        assertEquals(owned, state.ownershipSnapshot(), "preflight must not transfer physical ownership");
        assertEquals(100L, state.remaining(0));

        state.commitAccepted(0, 100L, consumed);
        assertEquals(Map.of(), state.ownershipSnapshot());
        assertEquals(0L, state.futureNeed(a));
        assertEquals(damaged, state.keyByResourceId(damagedId));
        assertEquals(0L, state.onHand(template), "variants must never be charged to the template key");
    }

    @Test
    void repeatedRemainderStateChangesSurviveGrowthAndOwnershipRestore() {
        List<AEKey> states = java.util.stream.IntStream.rangeClosed(0, 40)
            .mapToObj(i -> (AEKey) PlannerTestKey.of("runtime_changing_tool_" + i)).toList();
        AEKey template = states.getFirst();
        var pattern = statefulPattern(states);
        var plan = singleTaskPlan(pattern, 40L);
        RuntimeExecutionState state = new RuntimeExecutionState(plan);
        state.restoreOwnership(Map.of(template, 1L, a, 40L));
        int materialId = state.resourceIdIfKnown(a);
        AEKey current = template;
        for (int use = 1; use <= 40; use++) {
            var consumed = Map.of(current, 1L, a, 1L);
            state.validateActualConsumption(consumed);
            state.commitAccepted(0, 1L, consumed);
            assertEquals(0L, state.onHand(current));
            AEKey returned = states.get(use);
            state.acceptOutput(returned, 1L);
            state.acceptOutput(c, 1L);
            assertEquals(1L, state.onHand(returned));
            assertEquals(40L - use, state.onHand(a));
            assertEquals(40L - use, state.futureNeed(a));
            assertEquals(materialId, state.resourceIdIfKnown(a));
            assertEquals(a, state.keyByResourceId(materialId));
            int returnedId = state.resourceIdIfKnown(returned);
            assertEquals(returned, state.keyByResourceId(returnedId));
            current = returned;
            if (use == 20) {
                RuntimeExecutionState restored = new RuntimeExecutionState(plan);
                restored.restore(state.remainingSnapshot(), state.dynamicRemainingSnapshot(),
                    new int[] {state.stepIndex(0)}, new long[] {state.stepRemaining(0)});
                restored.restoreOwnership(state.ownershipSnapshot());
                assertEquals(state.ownershipSnapshot(), restored.ownershipSnapshot());
                state = restored;
            }
        }
        assertTrue(state.finished());
        RuntimeExecutionState finished = state;
        assertThrows(IllegalArgumentException.class, () -> finished.keyByResourceId(finished.resourceCount()));
        assertEquals(Map.of(current, 1L, c, 40L), state.ownershipSnapshot());
        state.releaseExternal(current, 1L);
        assertEquals(Map.of(c, 40L), state.ownershipSnapshot());
    }

    @Test
    void registeredVariantCannotBeOverdrawnAndInvalidRestoreIsAtomic() {
        AEKey variant = PlannerTestKey.of("runtime_owned_variant");
        RuntimeExecutionState state = new RuntimeExecutionState(singleTaskPlan(consumer, 2L));
        state.restoreOwnership(Map.of(a, 2L));
        state.acceptOutput(variant, 1L);
        var owned = state.ownershipSnapshot();
        assertThrows(IllegalArgumentException.class,
            () -> state.validateActualConsumption(Map.of(a, 1L, variant, 2L)));
        assertThrows(IllegalArgumentException.class,
            () -> state.commitAccepted(0, 1L, Map.of(a, 1L, variant, 2L)));
        assertEquals(owned, state.ownershipSnapshot());
        assertEquals(2L, state.remaining(0));
        assertEquals(2L, state.futureNeed(a));
        assertEquals(List.of(0), state.eligibleTaskIds());

        var malformed = new java.util.LinkedHashMap<AEKey, Long>();
        malformed.put(PlannerTestKey.of("runtime_unregistered_restore"), 1L);
        malformed.put(a, -1L);
        int resourceCount = state.resourceCount();
        assertThrows(IllegalArgumentException.class, () -> state.restoreOwnership(malformed));
        assertEquals(owned, state.ownershipSnapshot());
        assertEquals(resourceCount, state.resourceCount());
    }

    private PlannerFixtures.Pattern statefulPattern(List<AEKey> states) {
        IPatternDetails.IInput tool = new IPatternDetails.IInput() {
            @Override public GenericStack[] getPossibleInputs() {
                return new GenericStack[] {new GenericStack(states.getFirst(), 1L)};
            }
            @Override public long getMultiplier() { return 1L; }
            @Override public boolean isValid(AEKey candidate, net.minecraft.world.level.Level level) {
                return states.contains(candidate);
            }
            @Override public AEKey getRemainingKey(AEKey input) {
                int index = states.indexOf(input);
                return index < 0 ? null : states.get(Math.min(index + 1, states.size() - 1));
            }
        };
        return new PlannerFixtures.Pattern("runtime-stateful", new IPatternDetails.IInput[] {
            tool, new PlannerFixtures.Input(a, 1L, false)
        }, List.of(new GenericStack(c, 1L)));
    }

    private static ECOExecutionPlan singleTaskPlan(PlannerFixtures.Pattern pattern, long count) {
        var identity = PlanIdentity.patternIdentityFor(pattern);
        var task = new ECOExecutionPlan.TaskSpec(0, identity, pattern,
            ECOExecutionPlan.PatternRuntimeInfo.from(pattern), count, 0, ECOExecutionPlan.TaskKind.DAG);
        var phase = new ECOExecutionPlan.PhaseSpec(0, 1, ECOExecutionSchedule.Type.DAG,
            List.of(0), List.of(), List.of());
        var schedule = new ECOExecutionSchedule(List.of(
            new ECOExecutionSchedule.ComponentExecutionPhase(1, ECOExecutionSchedule.Type.DAG,
                Set.of(pattern), List.of())));
        var signature = new PlanIdentity.Signature(pattern.getOutputs().getFirst().what(), count,
            Map.of(identity, count), Map.of(), Map.of(), Map.of());
        return new ECOExecutionPlan(signature, ExecutionMode.PHASED_DAG,
            List.of(task), List.of(phase), schedule);
    }

    private ECOExecutionPlan plan() {
        var firstIdentity = PlanIdentity.patternIdentityFor(first);
        var secondIdentity = PlanIdentity.patternIdentityFor(second);
        var consumerIdentity = PlanIdentity.patternIdentityFor(consumer);
        var signature = new PlanIdentity.Signature(c, 1L,
            Map.of(firstIdentity, 10_005L, secondIdentity, 2L, consumerIdentity, 1L),
            Map.of(), Map.of(), Map.of());
        var tasks = List.of(
            new ECOExecutionPlan.TaskSpec(0, firstIdentity, first,
                ECOExecutionPlan.PatternRuntimeInfo.from(first), 10_005L, 0,
                ECOExecutionPlan.TaskKind.CYCLE_ORDERED),
            new ECOExecutionPlan.TaskSpec(1, secondIdentity, second,
                ECOExecutionPlan.PatternRuntimeInfo.from(second), 2L, 0,
                ECOExecutionPlan.TaskKind.CYCLE_ORDERED),
            new ECOExecutionPlan.TaskSpec(2, consumerIdentity, consumer,
                ECOExecutionPlan.PatternRuntimeInfo.from(consumer), 1L, 1,
                ECOExecutionPlan.TaskKind.DAG));
        var phases = List.of(
            new ECOExecutionPlan.PhaseSpec(0, 10, ECOExecutionSchedule.Type.CYCLE, List.of(0, 1),
                List.of(new ECOExecutionPlan.ExecutionStep(0, 10_000L),
                    new ECOExecutionPlan.ExecutionStep(1, 1L),
                    new ECOExecutionPlan.ExecutionStep(0, 2L)), List.of()),
            new ECOExecutionPlan.PhaseSpec(1, 11, ECOExecutionSchedule.Type.DAG, List.of(2), List.of(), List.of(0)));
        var schedule = new ECOExecutionSchedule(List.of(
            new ECOExecutionSchedule.ComponentExecutionPhase(10, ECOExecutionSchedule.Type.CYCLE,
                Set.of(first, second), List.of()),
            new ECOExecutionSchedule.ComponentExecutionPhase(11, ECOExecutionSchedule.Type.DAG,
                Set.of(consumer), List.of())),
            List.of(new ECOExecutionSchedule.PhaseDependency(0, 1)));
        return new ECOExecutionPlan(signature, ExecutionMode.ORDERED_CYCLE, tasks, phases, schedule);
    }
}
