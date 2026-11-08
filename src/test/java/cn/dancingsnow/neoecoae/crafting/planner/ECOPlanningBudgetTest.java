package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOPlanningBudgetTest {
    @ParameterizedTest
    @CsvSource({"0, 50", "120, 170", "180, 200", "200, 200", "320, 200"})
    void optimizationUsesAtMostFiftyMillisWithinTheFirstTwoHundred(long firstMillis, long deadlineMillis)
            throws Exception {
        var clock = new AtomicLong();
        var budget = new ECOPlanningBudget(ECOCancellation.NONE, 100, 1_000_000_000L, clock::get);
        clock.set(firstMillis * 1_000_000L);
        budget.checkpoint(); // Searching for feasibility may legitimately take longer than 200 ms.
        budget.beginOptimization();
        if (firstMillis < deadlineMillis) {
            clock.set(deadlineMillis * 1_000_000L - 1L);
            budget.checkpoint();
        }
        clock.set(Math.max(firstMillis, deadlineMillis) * 1_000_000L);
        var exhausted = assertThrows(ECOPlanningBudget.Exhausted.class, budget::checkpoint);
        assertTrue(exhausted.getMessage().contains("reason=OPTIMIZATION_TIME_LIMIT"));
    }

    @Test void improvementsAndCandidateLimitsCannotRestartTheWindowAndCancellationStillWins() throws Exception {
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        var clock = new AtomicLong();
        var budget = new ECOPlanningBudget(() -> {
            if (cancelled.get()) throw new InterruptedException("submitted");
        }, 100, 1_000_000_000L, clock::get);
        budget.checkpoint();
        budget.beginOptimization();
        clock.set(40_000_000L);
        budget.beginOptimization(); // A better candidate must not grant another 50 ms.
        var candidate = budget.slice(2, 100_000_000L);
        candidate.checkpoint(); candidate.checkpoint();
        assertThrows(ECOPlanningBudget.Exhausted.class, candidate::checkpoint);
        budget.checkpoint();
        clock.set(50_000_000L);
        assertThrows(ECOPlanningBudget.Exhausted.class, budget::checkpoint);
        cancelled.set(true);
        assertThrows(InterruptedException.class, budget::checkpoint);
    }
    @Test void sharedWorkCannotResetBetweenSubproblems() throws Exception {
        var budget = new ECOPlanningBudget(ECOCancellation.NONE, 2, 100, () -> 0);
        ECOCancellation first = budget, second = budget;
        first.checkpoint();
        second.checkpoint();
        var exhausted = assertThrows(ECOPlanningBudget.Exhausted.class, first::checkpoint);
        assertTrue(exhausted.getMessage().contains("reason=WORK_LIMIT"));
    }

    @Test void wallDeadlineAndCancellationRemainDifferentOutcomes() {
        var clock = new AtomicLong(20);
        var budget = new ECOPlanningBudget(ECOCancellation.NONE, 100, 10, clock::get);
        clock.set(30);
        var exhausted = assertThrows(ECOPlanningBudget.Exhausted.class, budget::checkpoint);
        assertTrue(exhausted.getMessage().contains("reason=TIME_LIMIT"));
        var cancelled = new ECOPlanningBudget(() -> { throw new InterruptedException(); }, 1, 1, () -> 0);
        assertThrows(InterruptedException.class, cancelled::checkpoint);
    }

    @Test void sessionReportsUnknownAndCraftLessCannotResetItsAllowance() throws Exception {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
        int previous = cn.dancingsnow.neoecoae.config.NEConfig.ecoPlanningMaxWork;
        try {
            cn.dancingsnow.neoecoae.config.NEConfig.ecoPlanningMaxWork = 1;
            var session = new ECOCraftingPlannerService().createSession(
                mock(appeng.api.networking.crafting.ICraftingService.class), mock(appeng.api.stacks.AEKey.class),
                new appeng.api.stacks.KeyCounter(), true);
            var result = session.plan(10L, false, ECOCancellation.NONE);
            assertEquals(cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.CYCLE_UNRESOLVED, result.status());
            assertTrue(result.trace().diagnostics().stream().anyMatch(d -> d.code()
                == cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic.Code.CYCLE_BUDGET_EXHAUSTED));
            cn.dancingsnow.neoecoae.config.NEConfig.ecoPlanningMaxWork = 5_000_000;
            assertEquals(cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus.CYCLE_UNRESOLVED,
                session.plan(1L, false, ECOCancellation.NONE).status());
        } finally {
            cn.dancingsnow.neoecoae.config.NEConfig.ecoPlanningMaxWork = previous;
        }
    }
}
