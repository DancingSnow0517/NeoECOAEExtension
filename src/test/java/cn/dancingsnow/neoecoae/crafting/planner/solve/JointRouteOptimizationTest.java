package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JointRouteOptimizationTest {
    private static AEKey iron, copper, ingot, slag, goal;

    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        iron = AEItemKey.of(Items.RAW_IRON);
        copper = AEItemKey.of(Items.RAW_COPPER);
        ingot = AEItemKey.of(Items.IRON_INGOT);
        slag = AEItemKey.of(Items.GRAVEL);
        goal = AEItemKey.of(Items.BRICK);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void combinesTwoIndividuallyInsufficientRoutes(boolean cycles) throws Exception {
        var a = pattern(List.of(stack(iron, 1)), stack(goal, 1));
        var b = pattern(List.of(stack(copper, 1)), stack(goal, 1));
        var result = plan(service(false, a, b), stock(iron, 6, copper, 4), 10, cycles);
        assertTasks(result, Map.of(a, 6L, b, 4L));
        assertEquals(PlannerAmount.of(6), result.exactUsedItems().get(iron));
        assertEquals(PlannerAmount.of(4), result.exactUsedItems().get(copper));
        assertNull(result.executionPlanError());
        assertEquals(2, result.executionPlan().tasks().size());
    }

    @Test void retainsTheFirstSuccessfulProducerInsteadOfRepricingTheWholeSupplyChain() throws Exception {
        var expensive = pattern(List.of(stack(ingot, 1)), stack(goal, 1));
        var smelt = pattern(List.of(stack(iron, 1)), stack(ingot, 1));
        var direct = pattern(List.of(stack(copper, 1)), stack(goal, 1));
        assertTasks(plan(service(false, expensive, smelt, direct), stock(iron, 5, copper, 5), 5, false),
                Map.of(expensive, 5L, smelt, 5L));
    }

    @Test void keepsTheCheaperValidatedCandidateWhenTheShortOptimizationWindowEnds() throws Exception {
        var expensive = pattern(List.of(stack(ingot, 1)), stack(goal, 1));
        var smelt = pattern(List.of(stack(iron, 1)), stack(ingot, 1));
        var direct = pattern(List.of(stack(copper, 1)), stack(goal, 1));
        var session = new ECOCraftingPlannerService().createSession(service(false, expensive, smelt, direct),
            goal, stock(iron, 5, copper, 5), false, false);
        var clock = planningClock(session);
        clock.set(120_000_000L);
        List<ECOPlanningResult> published = new ArrayList<>();
        var result = session.plan(5, false, ECOCancellation.NONE, ready -> {
            published.add(ready);
            clock.set(published.size() == 1 ? 169_000_000L : 171_000_000L);
        });
        assertTrue(published.size() >= 2);
        assertTasks(published.get(0), Map.of(expensive, 5L, smelt, 5L));
        assertTasks(published.get(published.size() - 1), Map.of(direct, 5L));
        assertTasks(result, Map.of(direct, 5L));
        assertSame(published.get(published.size() - 1), result, "Reuse the validated plan after optimization");
        assertTasks(published.get(0), Map.of(expensive, 5L, smelt, 5L));
    }

    @Test void aFirstExecutablePlanAfterTwoHundredMillisSkipsFurtherOptimization() throws Exception {
        var expensive = pattern(List.of(stack(ingot, 1)), stack(goal, 1));
        var smelt = pattern(List.of(stack(iron, 1)), stack(ingot, 1));
        var direct = pattern(List.of(stack(copper, 1)), stack(goal, 1));
        var session = new ECOCraftingPlannerService().createSession(service(false, expensive, smelt, direct),
            goal, stock(iron, 5, copper, 5), false, false);
        planningClock(session).set(250_000_000L);
        List<ECOPlanningResult> candidates = new ArrayList<>();

        var result = session.plan(5, false, ECOCancellation.NONE, candidates::add);

        assertEquals(1, candidates.size());
        assertSame(candidates.get(0), result);
        assertTasks(result, Map.of(expensive, 5L, smelt, 5L));
        assertTrue(result.routeSearchIncomplete());
        assertTrue(result.trace().diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("OPTIMIZATION_TIME_LIMIT")));
    }

    @Test void aSingleProducerFinishesWithoutEnteringOptionalSearchEvenAfterTwoHundredMillis() throws Exception {
        var direct = pattern(List.of(stack(copper, 1)), stack(goal, 1));
        var session = new ECOCraftingPlannerService().createSession(service(false, direct),
            goal, stock(copper, 5), false, false);
        planningClock(session).set(250_000_000L);
        List<ECOPlanningResult> candidates = new ArrayList<>();

        var result = session.plan(5, false, ECOCancellation.NONE, candidates::add);

        assertEquals(1, candidates.size());
        assertSame(candidates.get(0), result);
        assertTasks(result, Map.of(direct, 5L));
        assertFalse(result.routeSearchIncomplete());
    }

    @Test void missingRouteRecoveryCanFindItsFirstExecutablePlanAfterTwoHundredMillis() throws Exception {
        var a = pattern(List.of(stack(iron, 1)), stack(goal, 1));
        var b = pattern(List.of(stack(copper, 1)), stack(goal, 1));
        var session = new ECOCraftingPlannerService().createSession(service(false, a, b),
            goal, stock(iron, 6, copper, 4), false);
        planningClock(session).set(250_000_000L);
        List<ECOPlanningResult> published = new ArrayList<>();
        List<Map<AEKey, PlannerAmount>> missingAtPublication = new ArrayList<>();
        var result = session.plan(10, false, ECOCancellation.NONE, ready -> {
            published.add(ready);
            missingAtPublication.add(Map.copyOf(ready.exactMissingItems()));
        });
        assertTrue(published.size() >= 2, "A missing candidate must not start the optional optimization deadline");
        var first = published.get(0);
        assertEquals(PlanningStatus.MISSING_ITEMS, first.status());
        assertFalse(first.executable());
        assertFalse(first.provenInfeasible(), "The first route's shortage is not a proof about every alternative");
        assertFalse(first.exactMissingItems().isEmpty());
        assertEquals(10L, first.plan().finalOutput().amount());
        assertTrue(first.theoreticalBytes().signum() > 0);
        assertEquals(missingAtPublication.get(0), first.exactMissingItems());
        assertTasks(published.get(published.size() - 1), Map.of(a, 6L, b, 4L));
        assertTasks(result, Map.of(a, 6L, b, 4L));
    }

    @Test void jointlyProducesTwoRequestedMaterialsAndBuildsSupplierPhases() throws Exception {
        var a = pattern(List.of(stack(iron, 1)), stack(ingot, 1));
        var b = pattern(List.of(stack(iron, 1)), stack(slag, 1));
        var joint = pattern(List.of(stack(iron, 1)), stack(ingot, 1), stack(slag, 1));
        var finish = pattern(List.of(stack(ingot, 1), stack(slag, 1)), stack(goal, 1));
        var result = plan(service(true, a, b, joint, finish), stock(iron, 5), 5, false);
        assertTasks(result, Map.of(joint, 5L, finish, 5L));
        result.provenance().requireComplete();
        assertNull(result.executionPlanError());
        var phases = result.executionPlan().schedule().phases();
        assertEquals(Set.of(joint), cn.dancingsnow.neoecoae.platform.JdkCompat.first(phases).patternSet());
        assertEquals(Set.of(finish), cn.dancingsnow.neoecoae.platform.JdkCompat.last(phases).patternSet());
    }

    @Test void integerFiringsDoNotRoundFractionalStockIntoAPlan() throws Exception {
        var a = pattern(List.of(stack(iron, 1)), stack(goal, 2));
        var b = pattern(List.of(stack(copper, 1)), stack(goal, 2));
        assertTasks(plan(service(false, a, b), stock(iron, 1, copper, 1), 3, false), Map.of(a, 1L, b, 1L));
        assertNotEquals(PlanningStatus.SUCCESS, plan(service(false, a, b), stock(iron, 1), 3, false).status());
    }

    @Test void storedFinalProductsCannotCloseTheRequestedCrafting() throws Exception {
        var a = pattern(List.of(stack(iron, 1)), stack(goal, 1));
        var b = pattern(List.of(stack(copper, 1)), stack(goal, 1));
        assertNotEquals(PlanningStatus.SUCCESS, plan(service(false, a, b), stock(goal, 100), 10, false).status());
    }

    @Test void sumsRepeatedInputSlotsBeforeCheckingSupply() throws Exception {
        var a = pattern(List.of(stack(iron, 1), stack(iron, 1)), stack(goal, 1));
        var b = pattern(List.of(stack(copper, 1)), stack(goal, 1));
        assertTasks(plan(service(false, a, b), stock(iron, 2, copper, 1), 2, false), Map.of(a, 1L, b, 1L));
        assertNotEquals(PlanningStatus.SUCCESS, plan(service(false, a, b), stock(iron, 1), 1, false).status());
    }

    @Test void doesNotInventAnUnindexedByproductProducer() throws Exception {
        var unrelated = pattern(List.of(stack(iron, 1)), stack(ingot, 1), stack(slag, 1));
        var finish = pattern(List.of(stack(slag, 1)), stack(goal, 1));
        var alternate = pattern(List.of(stack(copper, 1)), stack(goal, 1));
        assertNotEquals(PlanningStatus.SUCCESS,
                plan(service(false, unrelated, finish, alternate), stock(iron, 5), 5, false).status());
    }

    @Test void seedFreePositiveBalanceDoesNotCreateAStartableCycle() throws Exception {
        var grow = pattern(List.of(stack(ingot, 1)), stack(ingot, 2), stack(goal, 1));
        var alternate = pattern(List.of(stack(copper, 1)), stack(goal, 1));
        assertNotEquals(PlanningStatus.SUCCESS,
                plan(service(true, grow, alternate), stock(iron, 10), 5, false).status());
    }

    @Test void leavesSubstitutionAndReturnedStockToTheirExistingResolver() throws Exception {
        var reusable = pattern(List.of(stack(iron, 1), stack(copper, 1)), stack(goal, 1));
        when(reusable.getInputs()[0].getRemainingKey(iron)).thenReturn(iron);
        var alternative = pattern(List.of(stack(slag, 1)), stack(goal, 1));
        var network = new CraftingNetworkCompiler().compile(service(false, reusable, alternative), goal, ECOCancellation.NONE);
        assertNull(new JointRouteOptimizer().optimize(network, PlannerInventorySnapshot.of(stock(iron, 1, copper, 5)),
                5, null, ECOCancellation.NONE));
    }

    @Test void completedRouteSkipsSearchAndExhaustedRecoveryRetainsItsReport() throws Exception {
        var a = pattern(List.of(stack(iron, 1)), stack(goal, 1));
        var b = pattern(List.of(stack(copper, 1)), stack(goal, 2));
        var inventory = stock(iron, 10, copper, 10);
        var network = new CraftingNetworkCompiler().compile(service(false, a, b), goal, ECOCancellation.NONE);
        var state = new SolveState(inventory);
        state.patternTimes.put(a, PlannerAmount.of(5));
        var preferred = new ComponentPlanner.Outcome(PlanningStatus.SUCCESS, state,
                new cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace(), List.of(), List.of(), List.of());
        var limit = new ECOPlanningBudget(ECOCancellation.NONE, 1, Long.MAX_VALUE, () -> 0);
        assertSame(preferred, new JointRouteOptimizer().optimize(network, PlannerInventorySnapshot.of(inventory),
                5, preferred, limit));
        assertTrue(preferred.trace().diagnostics().isEmpty());
        var incomplete = new ComponentPlanner.Outcome(PlanningStatus.MISSING_ITEMS, new SolveState(inventory),
                new cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace(), List.of(), List.of(), List.of());
        var recoveryLimit = new ECOPlanningBudget(ECOCancellation.NONE, 1, Long.MAX_VALUE, () -> 0);
        assertSame(incomplete, new JointRouteOptimizer().optimize(network, PlannerInventorySnapshot.of(inventory),
                5, incomplete, recoveryLimit));
        assertTrue(incomplete.trace().diagnostics().stream().anyMatch(d -> d.code() == PlannerDiagnostic.Code.ROUTE_OPTIMIZATION_BUDGET));
        assertThrows(InterruptedException.class, () -> new JointRouteOptimizer().optimize(network,
                PlannerInventorySnapshot.of(inventory), 5, preferred, () -> { throw new InterruptedException(); }));
    }

    private static java.util.concurrent.atomic.AtomicLong planningClock(ECOCraftingPlannerService.Session session)
            throws ReflectiveOperationException {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        // Reuse the budget's injectable clock so JVM warm-up and host load cannot decide which route wins.
        var field = ECOCraftingPlannerService.Session.class.getDeclaredField("planningBudget");
        field.setAccessible(true);
        field.set(session, new ECOPlanningBudget(ECOCancellation.NONE, Long.MAX_VALUE, Long.MAX_VALUE, clock::get));
        return clock;
    }

    private static void assertTasks(ECOPlanningResult result, Map<IPatternDetails, Long> tasks) {
        assertEquals(PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());
        assertEquals(tasks, result.plan().patternTimes());
        assertTrue(result.exactMissingItems().isEmpty());
    }

    private static ECOPlanningResult plan(ICraftingService service, KeyCounter inventory, long amount, boolean cycles) throws Exception {
        return new ECOCraftingPlannerService().createSession(service, goal, inventory, cycles).plan(amount, false, ECOCancellation.NONE);
    }

    private static GenericStack stack(AEKey key, long amount) { return new GenericStack(key, amount); }

    private static KeyCounter stock(Object... entries) {
        KeyCounter result = new KeyCounter();
        for (int i = 0; i < entries.length; i += 2) result.add((AEKey) entries[i], ((Number) entries[i + 1]).longValue());
        return result;
    }

    private static IPatternDetails pattern(List<GenericStack> inputs, GenericStack... outputs) {
        var pattern = mock(IPatternDetails.class);
        var raw = inputs.stream().map(stack -> {
            var input = mock(IPatternDetails.IInput.class);
            when(input.getMultiplier()).thenReturn(1L);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[] {stack});
            when(input.isValid(any(), isNull())).thenAnswer(call -> stack.what().equals(call.getArgument(0)));
            return input;
        }).toArray(IPatternDetails.IInput[]::new);
        when(pattern.getInputs()).thenReturn(raw);
        when(pattern.getOutputs()).thenReturn(outputs);
        when(pattern.getPrimaryOutput()).thenReturn(outputs[0]);
        return pattern;
    }

    private static ICraftingService service(boolean byproducts, IPatternDetails... patterns) {
        Map<AEKey, List<IPatternDetails>> index = new LinkedHashMap<>();
        for (var pattern : patterns) for (var output : byproducts ? java.util.Arrays.asList(pattern.getOutputs()) : List.of(pattern.getPrimaryOutput())) {
            var list = index.computeIfAbsent(output.what(), ignored -> new ArrayList<>());
            if (!list.contains(pattern)) list.add(pattern);
        }
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(any())).thenAnswer(call -> index.getOrDefault(call.getArgument(0), List.of()));
        return service;
    }
}
