package cn.dancingsnow.neoecoae.crafting.planner.result;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveMetrics;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.PatternRun;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ECOPlanningResultExecutionContractTest {
    @ParameterizedTest
    @EnumSource(value = CycleExecutionDisposition.class, names = {"ORDERED_EXECUTION", "DYNAMIC_EXECUTION"})
    void nonSuccessSolvedCycleSkipsConstructionAndKeepsCycleExpectation(CycleExecutionDisposition disposition) {
        var fixture = new Fixture(disposition);
        var result = fixture.result(PlanningStatus.MISSING_ITEMS);

        assertEquals(fixture.mode(), result.executionMode());
        var contract = result.executionContract();

        // Non-success diagnostics currently skip phase construction even when classification expects a cycle.
        // This documents the boundary; it does not grant submission permission to a failed planning result.
        assertEquals(ExecutionMode.NATIVE, contract.mode());
        assertNull(contract.executionPlan());
        assertNull(contract.error());
        assertEquals(fixture.mode(), result.executionMode());
        assertTrue(ECOPlanningResultRegistry.cycleExpected(result));
        assertThrows(IllegalStateException.class, result::executionPlan);
    }

    @ParameterizedTest
    @EnumSource(value = CycleExecutionDisposition.class, names = {"ORDERED_EXECUTION", "DYNAMIC_EXECUTION"})
    void successfulCycleWithMissingComponentOrderFailsClosed(CycleExecutionDisposition disposition) {
        var fixture = new Fixture(disposition);
        var result = fixture.result(PlanningStatus.SUCCESS);

        assertEquals(fixture.mode(), result.executionMode());
        var contract = result.executionContract();

        assertEquals(ExecutionMode.BLOCKED, contract.mode());
        assertNull(contract.executionPlan());
        assertTrue(contract.error().startsWith("EXECUTION_PLAN_BUILD_FAILED:IllegalStateException"));
        assertTrue(contract.error().contains("Execution schedule does not cover"));
        assertEquals(ExecutionMode.BLOCKED, result.executionMode());
        assertTrue(ECOPlanningResultRegistry.cycleExpected(result));
        assertThrows(IllegalStateException.class, result::executionPlan);
        assertEquals(contract, result.executionContract());
    }

    @Test
    void nonSuccessUnsolvedCycleIsBlockedBeforeConstruction() {
        var fixture = new Fixture(CycleExecutionDisposition.BLOCKED);
        var result = fixture.result(PlanningStatus.CYCLE_UNRESOLVED);

        var contract = result.executionContract();

        assertEquals(ExecutionMode.BLOCKED, contract.mode());
        assertEquals("CYCLE_NOT_SOLVED", contract.error());
        assertNull(contract.executionPlan());
        assertEquals(ExecutionMode.BLOCKED, result.executionMode());
        assertTrue(ECOPlanningResultRegistry.cycleExpected(result));
    }

    @Test
    void selectedCycleRejectsOverflowAcrossPatternsWithTheSameIdentity() {
        var fixture = new Fixture(CycleExecutionDisposition.ORDERED_EXECUTION);
        var definition = mock(AEItemKey.class);
        when(fixture.pattern.getDefinition()).thenReturn(definition);
        var alias = mock(IPatternDetails.class);
        when(alias.getDefinition()).thenReturn(definition);
        var component = fixture.result(PlanningStatus.MISSING_ITEMS).components();
        var plan = new CraftingPlan(new GenericStack(fixture.output, 2L), 0L, false, false,
            new KeyCounter(), new KeyCounter(), new KeyCounter(),
            Map.of(fixture.pattern, Long.MAX_VALUE, alias, 1L));

        assertThrows(ArithmeticException.class, () -> new ECOPlanningResult(PlanningStatus.MISSING_ITEMS,
            plan, new ECOPlanTrace(), List.of(), component, List.of(), 0L));
        // Overflow in an unrelated task identity must not invent a cycle requirement.
        var diagnostic = new ECOPlanningResult(PlanningStatus.MISSING_ITEMS, plan, new ECOPlanTrace(),
            List.of(), 0L);
        assertEquals(ExecutionMode.NATIVE, diagnostic.executionMode());
        assertFalse(diagnostic.requiresCycleExecution());
    }

    @Test
    void nativeContractCalculatesSignatureOnlyOnFirstAccess() {
        var fixture = new Fixture(CycleExecutionDisposition.NOT_REQUIRED);
        try (var identities = mockStatic(PlanIdentity.class)) {
            var result = new ECOPlanningResult(PlanningStatus.SUCCESS, fixture.plan, new ECOPlanTrace(),
                List.of(), 0L);
            assertNotNull(result.planningId());
            assertEquals(ExecutionMode.NATIVE, result.executionMode());
            assertFalse(result.requiresCycleExecution());
            identities.verify(() -> PlanIdentity.of(fixture.plan), never());
            var signature = mock(PlanIdentity.Signature.class);
            identities.when(() -> PlanIdentity.of(fixture.plan)).thenReturn(signature);

            var contract = result.executionContract();

            assertSame(signature, contract.planSignature());
            assertSame(contract, result.executionContract());
            assertNull(result.executionPlanError());
            identities.verify(() -> PlanIdentity.of(fixture.plan), times(1));
        }
    }

    @Test
    void fuzzyUpdatesReplaceOnlyTheCachedSnapshotWithoutRebuildingThePlan() {
        var fixture = new Fixture(CycleExecutionDisposition.NOT_REQUIRED);
        var result = new ECOPlanningResult(PlanningStatus.SUCCESS, fixture.plan, new ECOPlanTrace(),
            List.of(), 0L);
        var firstId = ResourceLocation.parse("minecraft:iron_ingot");
        var secondId = ResourceLocation.parse("minecraft:gold_ingot");
        var mutable = new HashSet<>(Set.of(firstId));
        result.setFuzzyPlanningItemIds(mutable);
        mutable.add(secondId);
        var first = result.executionContract();
        assertEquals(Set.of(firstId), first.fuzzyPlanningItemIds());

        result.setFuzzyPlanningItemIds(Set.of(secondId));
        var second = result.executionContract();

        assertSame(first.planSignature(), second.planSignature());
        assertSame(first.executionPlan(), second.executionPlan());
        assertEquals(first.planningId(), second.planningId());
        assertEquals(Set.of(secondId), second.fuzzyPlanningItemIds());
        assertEquals(Set.of(firstId), first.fuzzyPlanningItemIds());
        assertSame(second, result.executionContract());
        result.setFuzzyPlanningItemIds(null);
        assertEquals(Set.of(), result.executionContract().fuzzyPlanningItemIds());
    }

    @Test
    void concurrentContractReadersObserveOneCompleteResult() throws Exception {
        var fixture = new Fixture(CycleExecutionDisposition.ORDERED_EXECUTION);
        var result = fixture.result(PlanningStatus.SUCCESS);
        var start = new CountDownLatch(1);
        try (var readers = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<Future<ECOExecutionContract>>();
            for (int i = 0; i < 16; i++) {
                futures.add(readers.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Readers did not start");
                    return result.executionContract();
                }));
            }
            start.countDown();
            var expected = futures.getFirst().get(5, TimeUnit.SECONDS);
            assertEquals(ExecutionMode.BLOCKED, expected.mode());
            assertNotNull(expected.error());
            for (var future : futures) assertSame(expected, future.get(5, TimeUnit.SECONDS));
            assertEquals(ExecutionMode.BLOCKED, result.executionMode());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unavailableIdentityRemainsADiagnosticFailureAndDoesNotRetryPhaseConstruction(boolean throwsFailure) {
        var fixture = new Fixture(CycleExecutionDisposition.ORDERED_EXECUTION);
        try (var identities = mockStatic(PlanIdentity.class)) {
            var result = fixture.result(PlanningStatus.SUCCESS);
            if (throwsFailure) {
                identities.when(() -> PlanIdentity.of(fixture.plan))
                    .thenThrow(new IllegalArgumentException("unstable identity"));
            }

            String error = result.executionPlanError();

            assertEquals(throwsFailure
                ? "EXECUTION_PLAN_BUILD_FAILED:IllegalArgumentException:unstable identity"
                : "EXECUTION_PLAN_BUILD_FAILED:IllegalStateException:Plan identity unavailable", error);
            assertEquals(ExecutionMode.BLOCKED, result.executionMode());
            assertTrue(result.requiresCycleExecution());
            assertEquals(error, result.executionPlanError());
            assertEquals(error, assertThrows(IllegalStateException.class, result::executionPlan).getMessage());
            identities.verify(() -> PlanIdentity.of(fixture.plan), times(1));
            assertThrows(RuntimeException.class, result::executionContract);

            var recoveredSignature = mock(PlanIdentity.Signature.class);
            identities.when(() -> PlanIdentity.of(fixture.plan)).thenReturn(recoveredSignature);
            var recoveredContract = result.executionContract();

            assertEquals(ExecutionMode.BLOCKED, recoveredContract.mode());
            assertEquals(error, recoveredContract.error());
            assertNull(recoveredContract.executionPlan());
            assertSame(recoveredSignature, recoveredContract.planSignature());
            assertSame(recoveredContract, result.executionContract());
            identities.verify(() -> PlanIdentity.of(fixture.plan), times(3));
        }
    }

    @Test
    void diagnosticOnlyAndNativeResultsDoNotNeedAnAvailableIdentity() {
        var ordered = new Fixture(CycleExecutionDisposition.ORDERED_EXECUTION);
        var blocked = new Fixture(CycleExecutionDisposition.BLOCKED);
        try (var identities = mockStatic(PlanIdentity.class)) {
            var blockedResult = blocked.result(PlanningStatus.SUCCESS);
            assertEquals("CYCLE_NOT_SOLVED", blockedResult.executionPlanError());
            assertEquals("CYCLE_NOT_SOLVED",
                assertThrows(IllegalStateException.class, blockedResult::executionPlan).getMessage());
            var results = List.of(
                ordered.result(PlanningStatus.MISSING_ITEMS),
                new ECOPlanningResult(PlanningStatus.SUCCESS, ordered.plan, new ECOPlanTrace(), List.of(), 0L),
                new ECOPlanningResult(PlanningStatus.MISSING_ITEMS, null, new ECOPlanTrace(), List.of(), 0L));
            for (var result : results) {
                assertNull(result.executionPlanError());
                assertEquals("This result has no phased execution plan",
                    assertThrows(IllegalStateException.class, result::executionPlan).getMessage());
            }
            identities.verify(() -> PlanIdentity.of(ordered.plan), never());
            identities.verify(() -> PlanIdentity.of(blocked.plan), never());
            identities.verify(() -> PlanIdentity.of(null), never());
        }
    }

    private static final class Fixture {
        final AEKey output = mock(AEKey.class);
        final IPatternDetails pattern = mock(IPatternDetails.class);
        final CycleExecutionDisposition disposition;
        final CraftingPlan plan;

        Fixture(CycleExecutionDisposition disposition) {
            this.disposition = disposition;
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
            when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(output, 1L)));
            plan = new CraftingPlan(new GenericStack(output, 2L), 0L, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, 2L));
        }

        ExecutionMode mode() {
            return disposition == CycleExecutionDisposition.DYNAMIC_EXECUTION
                ? ExecutionMode.DYNAMIC_CYCLE : ExecutionMode.ORDERED_CYCLE;
        }

        ECOPlanningResult result(PlanningStatus status) {
            var run = mock(PatternRun.class);
            when(run.details()).thenReturn(pattern);
            when(run.count()).thenReturn(2L);
            when(run.repeatWidth()).thenReturn(1);
            when(run.repetitions()).thenReturn(1L);
            boolean blocked = disposition == CycleExecutionDisposition.BLOCKED;
            var cycle = blocked ? CycleSolveResult.failure(CycleSolveStatus.UNKNOWN_BUDGET, List.of(),
                CycleSolveMetrics.NONE) : new CycleSolveResult(CycleSolveStatus.SUCCESS, Map.of(pattern, 2L), Map.of(), Map.of(),
                Map.of(), Map.of(output, 2L), Map.of(output, 2L), List.of(), List.of(run), List.of(),
                CycleSolveMetrics.NONE);
            var component = new ComponentPlanningResult(0, ComponentPlanningResult.Type.CYCLIC,
                blocked ? ComponentPlanningResult.Status.UNRESOLVED : ComponentPlanningResult.Status.PLANNED,
                Map.of(output, 2L), Set.of(pattern), Set.of(pattern),
                blocked ? CyclePlanningStatus.UNKNOWN_BUDGET : CyclePlanningStatus.SOLVED,
                null, Map.of(), null, cycle, disposition, Map.of());
            // A selected cycle absent from execution order must be rejected by the actual schedule builder.
            return new ECOPlanningResult(status, plan, new ECOPlanTrace(), List.of(), List.of(component),
                List.of(), 0L);
        }
    }
}
