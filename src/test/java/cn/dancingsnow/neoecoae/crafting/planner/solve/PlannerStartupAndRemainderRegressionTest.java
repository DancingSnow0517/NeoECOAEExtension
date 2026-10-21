package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Permanent regressions from the opt-in audit, including physical execution from the actual reservations. */
class PlannerStartupAndRemainderRegressionTest {
    static Stream<TechModPlannerScenarios.Scenario> repairedOrders() {
        return TechModPlannerScenarios.all().stream().filter(s -> s.id().contains("_with_makeup")
            || s.id().contains("_crafting_remainder"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("repairedOrders")
    void startupSupplyAndMixedToolRoutesHaveAnExecutablePlan(TechModPlannerScenarios.Scenario scenario) throws Exception {
        scenario.verifyWitness();
        verifyExecution(scenario, plan(scenario, ECOCancellation.NONE));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 64})
    void mixedRoutesReserveOneWorkingToolEvenWhenManyAreAvailable(int copies) throws Exception {
        var original = scenario("mixed_feed_crafting_remainder");
        AEKey tool = material(original, "催化工具");
        var stock = new LinkedHashMap<>(original.initial());
        stock.put(tool, (long) copies);
        var order = withInventory(original, stock, true);
        var result = plan(order, ECOCancellation.NONE);
        verifyExecution(order, result);
        assertEquals(1L, result.exactUsedItems().get(tool).longValueExact());
        assertEquals(copies - 1L, stock.get(tool) - result.exactUsedItems().get(tool).longValueExact());
    }

    @Test
    void aZeroNetConsumptionToolStillNeedsOnePhysicalCopy() throws Exception {
        var original = scenario("mixed_feed_crafting_remainder_q2");
        var stock = new LinkedHashMap<>(original.initial());
        stock.remove(material(original, "催化工具"));
        var order = withInventory(original, stock, false);
        order.verifyImpossibleControl();
        assertNotEquals(PlanningStatus.SUCCESS, plan(order, ECOCancellation.NONE).status());
    }

    @Test
    void aToolWithDifferentComponentsCannotSatisfyTheReturnContract() throws Exception {
        var original = scenario("mixed_feed_crafting_remainder_q2");
        var stock = new LinkedHashMap<>(original.initial());
        AEKey tool = material(original, "催化工具");
        stock.remove(tool);
        var different = new ItemStack(Items.PAPER);
        different.setHoverName(Component.literal("different tool components"));
        stock.put(AEItemKey.of(different), 1L);
        var order = withInventory(original, stock, false);
        order.verifyImpossibleControl();
        assertNotEquals(PlanningStatus.SUCCESS, plan(order, ECOCancellation.NONE).status());
    }

    @Test
    void missingMakeupReagentCannotBeReplacedWithHypotheticalCycleOutput() throws Exception {
        var original = scenario("acid_recovery_500_with_makeup_q3");
        var stock = new LinkedHashMap<>(original.initial());
        stock.remove(material(original, "制酸试剂"));
        var order = withInventory(original, stock, false);
        order.verifyImpossibleControl();
        assertNotEquals(PlanningStatus.SUCCESS, plan(order, ECOCancellation.NONE).status());
    }

    @ParameterizedTest
    @ValueSource(ints = {1024, 1_000_000})
    void growingAnOrderDoesNotEnumerateRepeatedHypotheticalSeedInjections(int quantity) throws Exception {
        var order = scenario("enzyme_startup_detour_" + quantity);
        order.verifyWitness();
        var checks = new AtomicLong();
        var result = plan(order, checks::incrementAndGet);
        assertEquals(PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());
        assertNull(result.executionPlanError());
        assertTrue(checks.get() < 100_000, "A compact startup and repeat must stay cheap: " + checks);
        assertTrue(result.components().stream().filter(c -> c.cycleResult() != null)
            .allMatch(c -> c.cycleResult().executionPlan().size() < 20));
    }

    private static ECOPlanningResult plan(TechModPlannerScenarios.Scenario order, ECOCancellation cancellation)
            throws Exception {
        return new ECOCraftingPlannerService().createSession(order.service(), order.goal(), order.inventory(), true)
            .plan(order.requested(), false, cancellation);
    }

    private static TechModPlannerScenarios.Scenario scenario(String id) {
        return TechModPlannerScenarios.all().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    private static AEKey material(TechModPlannerScenarios.Scenario order, String label) {
        return order.labels().entrySet().stream().filter(e -> e.getValue().equals(label))
            .map(Map.Entry::getKey).findFirst().orElseThrow();
    }

    private static TechModPlannerScenarios.Scenario withInventory(TechModPlannerScenarios.Scenario original,
            Map<AEKey, Long> stock, boolean feasible) {
        return new TechModPlannerScenarios.Scenario(original.id(), original.title(), original.source(), original.goal(),
            original.requested(), original.labels(), Map.copyOf(stock), original.recipes(),
            feasible ? original.witness() : List.of(), feasible);
    }

    /** Interpret returned phase/step data independently, one physical firing at a time. */
    private static void verifyExecution(TechModPlannerScenarios.Scenario order, ECOPlanningResult result) {
        assertEquals(PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());
        assertNull(result.executionPlanError(), result.executionPlanError());
        assertTrue(result.exactMissingItems().isEmpty());
        var execution = result.executionPlan();
        assertNotNull(execution);
        var recipes = order.byPattern();
        Map<AEKey, Long> stock = new LinkedHashMap<>();
        result.exactUsedItems().forEach((key, amount) -> {
            assertTrue(amount.signum() >= 0 && amount.longValueExact() <= order.initial().getOrDefault(key, 0L));
            stock.put(key, amount.longValueExact());
        });
        long[] remaining = execution.tasks().stream().mapToLong(task -> task.totalCount()).toArray();
        for (var phase : execution.phases()) {
            if (phase.type() == ECOExecutionSchedule.Type.DAG) {
                var pending = new ArrayList<>(phase.taskIds());
                while (!pending.isEmpty()) {
                    boolean progress = false;
                    for (var it = pending.iterator(); it.hasNext();) {
                        int id = it.next();
                        var recipe = recipes.get(execution.task(id).pattern());
                        if (!enabled(recipe, stock)) continue;
                        fire(recipe, stock);
                        if (--remaining[id] == 0) it.remove();
                        progress = true;
                    }
                    assertTrue(progress, "DAG phase cannot execute from its reservations: " + order.named(stock));
                }
                continue;
            }
            assertEquals(ECOExecutionSchedule.Type.CYCLE, phase.type());
            var steps = phase.steps();
            for (int start = 0; start < steps.size();) {
                int end = start;
                long repetitions = 1;
                for (int i = start; i < steps.size(); i++) {
                    if (steps.get(i).repetitions() > 1 && i - steps.get(i).repeatWidth() + 1 == start) {
                        end = i;
                        repetitions = steps.get(i).repetitions();
                        break;
                    }
                }
                for (long lap = 0; lap < repetitions; lap++) for (int i = start; i <= end; i++) {
                    var step = steps.get(i);
                    var recipe = recipes.get(execution.task(step.taskId()).pattern());
                    for (long n = 0; n < step.count(); n++) {
                        assertTrue(enabled(recipe, stock), "Ordered step " + recipe.name() + " is missing inputs");
                        fire(recipe, stock);
                        assertTrue(--remaining[step.taskId()] >= 0);
                    }
                }
                start = end + 1;
            }
        }
        for (long count : remaining) assertEquals(0, count, "Execution omitted planned firings");
        assertTrue(stock.getOrDefault(order.goal(), 0L) >= order.requested());
    }

    private static boolean enabled(TechModPlannerScenarios.Recipe recipe, Map<AEKey, Long> stock) {
        assertNotNull(recipe, "Execution invented a recipe");
        return recipe.inputs().entrySet().stream().allMatch(e -> stock.getOrDefault(e.getKey(), 0L) >= e.getValue());
    }

    private static void fire(TechModPlannerScenarios.Recipe recipe, Map<AEKey, Long> stock) {
        recipe.inputs().forEach((key, amount) -> stock.put(key, Math.subtractExact(stock.getOrDefault(key, 0L), amount)));
        recipe.grossOutputs().forEach((key, amount) -> stock.merge(key, amount, Math::addExact));
    }
}
