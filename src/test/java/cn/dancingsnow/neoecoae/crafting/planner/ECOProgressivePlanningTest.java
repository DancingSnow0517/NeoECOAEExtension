package cn.dancingsnow.neoecoae.crafting.planner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.api.me.provider.ECOCraftingProviderRevision;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.solve.PlannerInventorySnapshot;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.world.item.Items;

class ECOProgressivePlanningTest {
    @BeforeAll static void bootstrap() { cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize(); }

    @Test void foregroundCompletesWhileImprovementIsStillBlockedAndCanBeCancelledAfterGet() throws Exception {
        var session = mock(ECOCraftingPlannerService.Session.class);
        var optimizer = mock(ECOCraftingPlannerService.Session.class);
        var first = result(8);
        var entered = new CountDownLatch(1);
        var released = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        when(session.isStructureCurrent()).thenReturn(true);
        when(session.initialInventory()).thenReturn(PlannerInventorySnapshot.of(new KeyCounter()));
        when(session.plan(eq(8L), eq(false), any())).thenAnswer(call -> {
            assertFalse(((ECOCancellation) call.getArgument(2)).improveCompletedPlans());
            return first;
        });
        when(session.fork(any(), any(), eq(false))).thenReturn(optimizer);
        when(optimizer.plan(eq(8L), eq(false), any())).thenAnswer(call -> {
            entered.countDown();
            try { released.await(); }
            catch (InterruptedException cancelled) { interrupted.countDown(); throw cancelled; }
            return result(4);
        });
        var future = ECOPlanningService.begin(session, 8, CalculationStrategy.REPORT_MISSING_ITEMS);
        try {
            assertSame(first.plan(), future.get(5, TimeUnit.SECONDS));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertNotNull(first.improvements());
            assertNull(first.improvements().latest(first));
            future.cancel(true); // AE2 discards the foreground future after get; closing still stops optional work.
            assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            assertNull(first.improvements().latest(first));
        } finally {
            released.countDown();
            future.cancel(true);
        }
    }

    @Test void onlyCompleteBetterCurrentResultsCanReplaceTheDisplayedPlan() {
        var session = mock(ECOCraftingPlannerService.Session.class);
        when(session.isStructureCurrent()).thenReturn(true);
        var first = result(8);
        var handle = new ECOPlanningImprovements(session, first);
        var better = result(4, first.plan().finalOutput().what());
        assertTrue(handle.offer(better));
        assertSame(better, handle.latest(first));
        assertEquals(8L, first.plan().patternTimes().values().iterator().next());
        assertFalse(handle.offer(result(9, first.plan().finalOutput().what())));
        var failed = new ECOPlanningResult(PlanningStatus.CYCLE_UNRESOLVED, better.plan(),
                new ECOPlanTrace(), List.of(), 0);
        assertFalse(handle.offer(failed));
        when(session.isStructureCurrent()).thenReturn(false);
        assertNull(handle.latest(first));
        assertFalse(handle.offer(result(1, first.plan().finalOutput().what())));
        when(session.isStructureCurrent()).thenReturn(true);
        handle.close();
        assertFalse(handle.offer(result(1, first.plan().finalOutput().what())));
        assertNull(handle.latest(first));
    }

    @Test void firstSolutionPolicySurvivesNestedAllowances() {
        var budget = new ECOPlanningBudget(ECOCancellation.firstSolution(ECOCancellation.NONE), 1000, 1000, () -> 0);
        assertFalse(budget.improveCompletedPlans());
        assertFalse(budget.slice(100, 100).improveCompletedPlans());
        assertTrue(new ECOPlanningBudget(ECOCancellation.NONE).improveCompletedPlans());
    }

    @Test void realSessionPublishesACompleteRouteThenImprovesItWithAnIndependentSession() throws Exception {
        AEKey raw = AEItemKey.of(Items.RAW_IRON), ingot = AEItemKey.of(Items.IRON_INGOT);
        AEKey copper = AEItemKey.of(Items.COPPER_INGOT), goal = AEItemKey.of(Items.GOLD_INGOT);
        var smelt = pattern(raw, ingot);
        var finish = pattern(ingot, goal);
        var direct = pattern(raw, goal);
        var service = service(Map.of(goal, List.of(finish, direct), ingot, List.of(smelt)));
        var inventory = new KeyCounter(); inventory.add(raw, 10); inventory.add(copper, 10);
        var session = new ECOCraftingPlannerService().createSession(service, goal, inventory, false);
        var first = session.plan(10, false, ECOCancellation.firstSolution(ECOCancellation.NONE));
        assertEquals(PlanningStatus.SUCCESS, first.status(), first.trace().diagnostics().toString());
        assertEquals(Map.of(smelt, 10L, finish, 10L), first.plan().patternTimes());
        assertNull(first.executionPlanError());
        var improved = session.fork(session.initialInventory(), ECOCancellation.NONE, false)
                .plan(10, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, improved.status(), improved.trace().diagnostics().toString());
        assertEquals(Map.of(direct, 10L), improved.plan().patternTimes());
        var handle = new ECOPlanningImprovements(session, first);
        assertTrue(handle.offer(improved));
        assertSame(improved, handle.latest(first));
        assertEquals(Map.of(smelt, 10L, finish, 10L), first.plan().patternTimes());
        handle.close();
        var owned = new KeyCounter(); owned.add(raw, 8);
        var remaining = handle.remaining(owned, 8).get(5, TimeUnit.SECONDS);
        assertEquals(PlanningStatus.SUCCESS, remaining.status(), remaining.trace().diagnostics().toString());
        assertEquals(new GenericStack(goal, 8), remaining.plan().finalOutput());
        assertEquals(Map.of(direct, 8L), remaining.plan().patternTimes());
        assertEquals(8, remaining.plan().usedItems().get(raw));
        assertTrue(remaining.plan().emittedItems().isEmpty());
        when(((ECOCraftingProviderRevision) service).neoecoae$getProviderRevision()).thenReturn(8L);
        assertNull(handle.latest(first));
        assertThrows(IllegalStateException.class, () -> session.fork(session.initialInventory(), ECOCancellation.NONE, false));
    }

    @Test void runtimeForkCannotIntroduceNewEmitterRequests() throws Exception {
        AEKey raw = AEItemKey.of(Items.RAW_COPPER), goal = AEItemKey.of(Items.COPPER_INGOT);
        var pattern = pattern(raw, goal);
        var service = service(Map.of(goal, List.of(pattern)));
        when(service.canEmitFor(raw)).thenReturn(true);
        var session = new ECOCraftingPlannerService().createSession(service, goal, new KeyCounter(), false);
        var first = session.plan(10, false, ECOCancellation.firstSolution(ECOCancellation.NONE));
        assertEquals(PlanningStatus.SUCCESS, first.status(), first.trace().diagnostics().toString());
        assertEquals(10, first.plan().emittedItems().get(raw));
        var remaining = session.fork(PlannerInventorySnapshot.of(new KeyCounter()), ECOCancellation.NONE, true)
                .plan(10, false, ECOCancellation.NONE);
        assertNotEquals(PlanningStatus.SUCCESS, remaining.status());
        assertTrue(remaining.plan().emittedItems().isEmpty());
    }

    @Test
    void seedRetentionCannotReplaceAStockUsingPlanWithMoreWork() {
        var direct = result(1000);
        var replenishing = result(2714, direct.plan().finalOutput().what());
        replenishing.trace().addDiagnostic(new cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic(
                cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic.Code.CYCLE_SEED_RETAINED,
                "Stock replenished by extra seed crafting"));
        assertFalse(ECOPlanningImprovements.better(replenishing, direct));
        assertTrue(ECOPlanningImprovements.better(direct, replenishing));
    }

    private static ICraftingService service(Map<AEKey, List<IPatternDetails>> index) {
        var service = mock(ICraftingService.class, withSettings().extraInterfaces(ECOCraftingProviderRevision.class));
        var revision = (ECOCraftingProviderRevision) service;
        when(revision.neoecoae$isProviderSnapshotStable()).thenReturn(true);
        when(revision.neoecoae$getProviderRevision()).thenReturn(7L);
        when(service.getCraftingFor(any())).thenAnswer(call -> index.getOrDefault(call.getArgument(0), List.of()));
        return service;
    }

    private static IPatternDetails pattern(AEKey inputKey, AEKey outputKey) {
        var pattern = mock(IPatternDetails.class);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getMultiplier()).thenReturn(1L);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(inputKey, 1)});
        when(input.isValid(any(), isNull())).thenAnswer(call -> inputKey.equals(call.getArgument(0)));
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
        var output = new GenericStack(outputKey, 1);
        when(pattern.getOutputs()).thenReturn(List.of(output));
        when(pattern.getPrimaryOutput()).thenReturn(output);
        return pattern;
    }

    private static ECOPlanningResult result(long count) { return result(count, mock(AEKey.class)); }

    private static ECOPlanningResult result(long count, AEKey goal) {
        var pattern = mock(IPatternDetails.class);
        var plan = new CraftingPlan(new GenericStack(goal, 8), 128, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, count));
        return new ECOPlanningResult(PlanningStatus.SUCCESS, plan, new ECOPlanTrace(), List.of(), 0);
    }
}
