package cn.dancingsnow.neoecoae.crafting.planner;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOPlanningBudgetTest {
    @Test void sharedWorkCannotResetBetweenSubproblems() throws Exception {
        var budget = new ECOPlanningBudget(ECOCancellation.NONE, 2, 100, () -> 0);
        ECOCancellation first = budget, second = budget;
        first.checkpoint();
        second.checkpoint();
        assertThrows(ECOPlanningBudget.Exhausted.class, first::checkpoint);
    }

    @Test void wallDeadlineAndCancellationRemainDifferentOutcomes() {
        var clock = new AtomicLong(20);
        var budget = new ECOPlanningBudget(ECOCancellation.NONE, 100, 10, clock::get);
        clock.set(30);
        assertThrows(ECOPlanningBudget.Exhausted.class, budget::checkpoint);
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
