package cn.dancingsnow.neoecoae.crafting.planner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ECOPlanningServiceTest {
    private final ECOCraftingPlannerService.Session session = mock(ECOCraftingPlannerService.Session.class);
    private final AEKey goal = mock(AEKey.class);

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void candidatesStayPrivateUntilTheFinalPlanIsSelectedAndCannotReplaceItLater(boolean initiallyMissing)
            throws Exception {
        var initial = result(initiallyMissing ? PlanningStatus.MISSING_ITEMS : PlanningStatus.SUCCESS, initiallyMissing);
        var improved = result(PlanningStatus.SUCCESS, false);
        var retained = new CountDownLatch(1);
        var continueSearch = new CountDownLatch(1);
        var callback = new java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<ECOPlanningResult>>();
        when(session.plan(eq(10L), eq(false), any(), any())).thenAnswer(call -> {
            java.util.function.Consumer<ECOPlanningResult> publish = call.getArgument(3);
            callback.set(publish);
            publish.accept(initial);
            retained.countDown();
            assertTrue(continueSearch.await(5, TimeUnit.SECONDS));
            publish.accept(improved);
            return improved;
        });
        var request = ECOPlanningService.begin(session, goal, 10L, CalculationStrategy.REPORT_MISSING_ITEMS);
        try {
            assertTrue(retained.await(5, TimeUnit.SECONDS));
            assertFalse(request.isDone());
            assertThrows(TimeoutException.class, () -> request.get(0, TimeUnit.NANOSECONDS));
            assertEquals(ECOPlanningService.Request.SEARCHING, request.searchState());
            continueSearch.countDown();
            assertSame(improved.plan(), request.get(5, TimeUnit.SECONDS));
            assertEquals(ECOPlanningService.Request.COMPLETE, request.searchState());
            callback.get().accept(initial);
            assertSame(improved.plan(), request.get(), "Late candidates cannot replace the displayed plan");
        } finally { continueSearch.countDown(); request.cancel(true); }
    }

    @Test
    void closingTheMenuCancelsThePendingResultAndRejectsLateCandidates() throws Exception {
        var initial = result(PlanningStatus.MISSING_ITEMS, true);
        var late = result(PlanningStatus.SUCCESS, false);
        var blocked = new java.util.concurrent.CountDownLatch(1);
        var attempted = new java.util.concurrent.CountDownLatch(1);
        var retained = new CountDownLatch(1);
        when(session.plan(eq(10L), eq(false), any(), any())).thenAnswer(call -> {
            java.util.function.Consumer<ECOPlanningResult> publish = call.getArgument(3);
            publish.accept(initial);
            retained.countDown();
            try { blocked.await(); } catch (InterruptedException cancelled) { /* Simulate a late callback. */ }
            publish.accept(late);
            attempted.countDown();
            return late;
        });
        var request = ECOPlanningService.begin(session, goal, 10L, CalculationStrategy.REPORT_MISSING_ITEMS);
        try {
            assertTrue(retained.await(5, TimeUnit.SECONDS));
            assertFalse(request.isDone());
            assertTrue(request.cancel(true));
            assertTrue(attempted.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(request.isCancelled());
            assertThrows(java.util.concurrent.CancellationException.class, request::get);
            assertEquals(ECOPlanningService.Request.STOPPED, request.searchState());
        } finally { blocked.countDown(); request.cancel(true); }
    }

    @Test
    void anOptimizationFailureStillPublishesTheValidatedCandidate() throws Exception {
        var initial = result(PlanningStatus.SUCCESS, false);
        when(session.plan(eq(10L), eq(false), any(), any())).thenAnswer(call -> {
            ((java.util.function.Consumer<ECOPlanningResult>) call.getArgument(3)).accept(initial);
            throw new IllegalStateException("failed alternative");
        });
        var request = ECOPlanningService.begin(session, goal, 10L, CalculationStrategy.REPORT_MISSING_ITEMS);
        try {
            assertSame(initial.plan(), request.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(ECOPlanningService.Request.FAILED, request.searchState());
        } finally { request.cancel(true); }
    }

    @Test
    void shortageReportIsPublishedOnceWithTheCompletedProofOrLimitedSearchDiagnostic() throws Exception {
        for (boolean limited : List.of(false, true)) {
            var initial = result(PlanningStatus.MISSING_ITEMS, true);
            var finished = result(limited ? PlanningStatus.CYCLE_UNRESOLVED : PlanningStatus.MISSING_ITEMS, true);
            var missing = java.util.Map.of(goal, cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount.ONE);
            initial.setExactMaterials(java.util.Map.of(), java.util.Map.of(), missing);
            finished.setExactMaterials(java.util.Map.of(), java.util.Map.of(), missing);
            finished.trace().addDiagnostic(new cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic(
                limited ? cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED
                    : cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic.Code.ROUTE_QUANTITY_BOUND,
                "Finished checking alternatives"));
            cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry.register(finished.plan(), finished);
            var continueSearch = new java.util.concurrent.CountDownLatch(1);
            var retained = new CountDownLatch(1);
            when(session.plan(eq(10L), eq(false), any(), any())).thenAnswer(call -> {
                ((java.util.function.Consumer<ECOPlanningResult>) call.getArgument(3)).accept(initial);
                retained.countDown();
                assertTrue(continueSearch.await(5, java.util.concurrent.TimeUnit.SECONDS));
                return finished;
            });
            var request = ECOPlanningService.begin(session, goal, 10L, CalculationStrategy.REPORT_MISSING_ITEMS);
            try {
                assertTrue(retained.await(5, TimeUnit.SECONDS));
                assertFalse(request.isDone());
                assertEquals(ECOPlanningService.Request.SEARCHING, request.searchState());
                continueSearch.countDown();
                assertSame(finished.plan(), request.get(5, TimeUnit.SECONDS));
                assertEquals(limited ? ECOPlanningService.Request.LIMITED : ECOPlanningService.Request.COMPLETE,
                    request.searchState());
                assertFalse(initial.provenInfeasible());
                assertEquals(!limited, finished.provenInfeasible());
            } finally { continueSearch.countDown(); request.cancel(true); }
        }
    }

    @Test
    void missingReportReturnsTheOriginalPlanWithoutRecalculating() throws Exception {
        var exact = result(PlanningStatus.MISSING_ITEMS, true);
        when(session.plan(eq(10L), eq(false), any())).thenReturn(exact);

        var plan = ECOPlanningService.plan(session, goal, 10L, CalculationStrategy.REPORT_MISSING_ITEMS);

        assertSame(exact.plan(), plan, "Keep the plan owning the exact material report and diagnostics");
        verify(session).plan(eq(10L), eq(false), any());
        verifyNoMoreInteractions(session);
    }

    @Test
    void craftLessStillFindsTheLargestExecutableAmount() throws Exception {
        var missing = result(PlanningStatus.MISSING_ITEMS, true);
        var success = result(PlanningStatus.SUCCESS, false);
        List<Long> amounts = new ArrayList<>();
        when(session.plan(anyLong(), eq(false), any())).thenAnswer(call -> {
            long amount = call.getArgument(0);
            amounts.add(amount);
            return amount <= 7L ? success : missing;
        });

        var plan = ECOPlanningService.plan(session, goal, 10L, CalculationStrategy.CRAFT_LESS);

        assertSame(success.plan(), plan);
        assertEquals(List.of(10L, 8L, 4L, 6L, 7L), amounts);
        verify(session, never()).plan(anyLong(), eq(true), any());
    }

    @Test
    void failedCraftLessProbesReuseTheOriginalMissingReport() throws Exception {
        var missing = result(PlanningStatus.MISSING_ITEMS, true);
        List<Long> amounts = new ArrayList<>();
        when(session.plan(anyLong(), eq(false), any())).thenAnswer(call -> {
            amounts.add(call.getArgument(0));
            return missing;
        });

        var plan = ECOPlanningService.plan(session, goal, 8L, CalculationStrategy.CRAFT_LESS);

        assertSame(missing.plan(), plan);
        assertEquals(List.of(8L, 4L, 2L, 1L), amounts);
        verify(session, never()).plan(anyLong(), eq(true), any());
    }

    @Test
    void aNullExactPlanFallsThroughToTheReportCalculation() throws Exception {
        var exact = new ECOPlanningResult(PlanningStatus.INTERNAL_ERROR, null, new ECOPlanTrace(), List.of(), 0);
        var report = result(PlanningStatus.INTERNAL_ERROR, true);
        when(session.plan(eq(10L), eq(false), any())).thenReturn(exact);
        when(session.plan(eq(10L), eq(true), any())).thenReturn(report);

        assertSame(report.plan(), ECOPlanningService.plan(session, goal, 10L,
            CalculationStrategy.REPORT_MISSING_ITEMS));
        verify(session).plan(eq(10L), eq(false), any());
        verify(session).plan(eq(10L), eq(true), any());
        verifyNoMoreInteractions(session);
    }

    private static ECOPlanningResult result(PlanningStatus status, boolean simulation) {
        var plan = mock(CraftingPlan.class);
        when(plan.simulation()).thenReturn(simulation);
        when(plan.usedItems()).thenReturn(new appeng.api.stacks.KeyCounter());
        when(plan.emittedItems()).thenReturn(new appeng.api.stacks.KeyCounter());
        when(plan.missingItems()).thenReturn(new appeng.api.stacks.KeyCounter());
        var result = new ECOPlanningResult(status, plan, new ECOPlanTrace(), List.of(), 0);
        ECOPlanningResultRegistry.register(plan, result);
        return result;
    }
}
