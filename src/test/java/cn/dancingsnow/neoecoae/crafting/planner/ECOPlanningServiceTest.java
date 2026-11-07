package cn.dancingsnow.neoecoae.crafting.planner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ECOPlanningServiceTest {
    private final ECOCraftingPlannerService.Session session = mock(ECOCraftingPlannerService.Session.class);
    private final AEKey goal = mock(AEKey.class);

    @Test
    void firstPreviewCompletesBeforeTheWorkerFinishesAndKeepsImproving() throws Exception {
        var initial = result(PlanningStatus.SUCCESS, false);
        var improved = result(PlanningStatus.SUCCESS, false);
        var continueSearch = new java.util.concurrent.CountDownLatch(1);
        when(session.plan(eq(10L), eq(false), any(), any())).thenAnswer(call -> {
            java.util.function.Consumer<ECOPlanningResult> publish = call.getArgument(3);
            publish.accept(initial);
            assertTrue(continueSearch.await(5, java.util.concurrent.TimeUnit.SECONDS));
            publish.accept(improved);
            return improved;
        });
        var request = ECOPlanningService.begin(session, goal, 10L, CalculationStrategy.REPORT_MISSING_ITEMS);
        try {
            assertSame(initial.plan(), request.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(ECOPlanningService.Request.SEARCHING, request.searchState());
            continueSearch.countDown();
            awaitFinished(request);
            assertSame(improved.plan(), request.latest());
            assertSame(initial.plan(), request.get(), "AE2's initial Future must keep its first preview");
        } finally { continueSearch.countDown(); request.cancel(true); }
    }

    @Test
    void submissionCancellationRejectsLatePublishedRoutes() throws Exception {
        var initial = result(PlanningStatus.SUCCESS, false);
        var late = result(PlanningStatus.SUCCESS, false);
        var blocked = new java.util.concurrent.CountDownLatch(1);
        var attempted = new java.util.concurrent.CountDownLatch(1);
        when(session.plan(eq(10L), eq(false), any(), any())).thenAnswer(call -> {
            java.util.function.Consumer<ECOPlanningResult> publish = call.getArgument(3);
            publish.accept(initial);
            try { blocked.await(); } catch (InterruptedException cancelled) { /* Simulate a late callback. */ }
            publish.accept(late);
            attempted.countDown();
            return late;
        });
        var request = ECOPlanningService.begin(session, goal, 10L, CalculationStrategy.REPORT_MISSING_ITEMS);
        try {
            assertSame(initial.plan(), request.get(5, java.util.concurrent.TimeUnit.SECONDS));
            request.cancel(true);
            assertTrue(attempted.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertSame(initial.plan(), request.latest());
            assertEquals(ECOPlanningService.Request.STOPPED, request.searchState());
        } finally { blocked.countDown(); request.cancel(true); }
    }

    @Test
    void aBackgroundFailureKeepsTheExecutablePreview() throws Exception {
        var initial = result(PlanningStatus.SUCCESS, false);
        when(session.plan(eq(10L), eq(false), any(), any())).thenAnswer(call -> {
            ((java.util.function.Consumer<ECOPlanningResult>) call.getArgument(3)).accept(initial);
            throw new IllegalStateException("failed alternative");
        });
        var request = ECOPlanningService.begin(session, goal, 10L, CalculationStrategy.REPORT_MISSING_ITEMS);
        try {
            assertSame(initial.plan(), request.get(5, java.util.concurrent.TimeUnit.SECONDS));
            awaitFinished(request);
            assertSame(initial.plan(), request.latest());
            assertEquals(ECOPlanningService.Request.FAILED, request.searchState());
        } finally { request.cancel(true); }
    }

    private static void awaitFinished(ECOPlanningService.Request request) {
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            while (request.searchState() == ECOPlanningService.Request.SEARCHING) Thread.sleep(1L);
        });
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
        return new ECOPlanningResult(status, plan, new ECOPlanTrace(), List.of(), 0);
    }
}
