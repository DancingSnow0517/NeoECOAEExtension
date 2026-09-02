package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.provenance.MaterialProvenance;
import cn.dancingsnow.neoecoae.impl.crafting.planner.provenance.MaterialSource;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ECOExecutionScheduleProvenanceTest {
    @Test void creditLedgerSurvivesCopyMergeAndReplaceWithoutLosingAmounts() {
        AEKey key = PlannerTestKey.of("prov_ledger");
        AEKey firstOutput = PlannerTestKey.of("prov_ledger_first");
        AEKey secondOutput = PlannerTestKey.of("prov_ledger_second");
        var first = PlannerFixtures.pattern("ledger_first", firstOutput, 1);
        var second = PlannerFixtures.pattern("ledger_second", secondOutput, 1);
        MaterialProvenance original = new MaterialProvenance();
        original.credit(key, first, PlannerAmount.of(2));
        original.credit(key, second, PlannerAmount.of(3));

        MaterialProvenance copy = original.copy();
        MaterialProvenance replaced = new MaterialProvenance();
        replaced.replaceWith(copy);
        replaced.consumeCredit(key, PlannerAmount.of(4));

        assertEquals(PlannerAmount.of(2), replaced.freeze().supplierAmountsOf(key)
            .get(new MaterialSource.PatternOutput(first, false)));
        assertEquals(PlannerAmount.of(2), replaced.freeze().supplierAmountsOf(key)
            .get(new MaterialSource.PatternOutput(second, false)));
        assertEquals(Map.of(second, PlannerAmount.ONE), replaced.consumeCredit(key, PlannerAmount.ONE));
    }

    @Test void stockSatisfiedInputIgnoresDownstreamByproductEmitter() {
        AEKey raw = PlannerTestKey.of("prov_stock_raw");
        AEKey shared = PlannerTestKey.of("prov_stock_shared");
        AEKey middle = PlannerTestKey.of("prov_stock_middle");
        AEKey goal = PlannerTestKey.of("prov_stock_goal");
        var consumer = PlannerFixtures.pattern("stock_consumer", middle, 1, shared, 1L);
        var downstream = PlannerFixtures.multiOutput("downstream_byproduct",
            List.of(new GenericStack(goal, 1), new GenericStack(shared, 1)), middle, 1L, raw, 1L);
        MaterialProvenance provenance = new MaterialProvenance();
        provenance.supplied(shared, MaterialSource.Stock.INSTANCE, PlannerAmount.ONE);
        provenance.supplied(middle, new MaterialSource.PatternOutput(consumer, true), PlannerAmount.ONE);

        var schedule = ECOExecutionSchedule.from(List.of(component(0, middle, consumer),
            component(1, goal, downstream)), List.of(1, 0), Map.of(consumer, 1L, downstream, 1L),
            provenance.freeze());

        assertEquals(List.of(consumer, downstream), patterns(schedule));
        assertEquals(List.of(new ECOExecutionSchedule.PhaseDependency(0, 1)), schedule.dependencies());
    }

    @Test void creditedSiblingByproductCreatesOneRealEdge() {
        AEKey raw = PlannerTestKey.of("prov_sibling_raw");
        AEKey shared = PlannerTestKey.of("prov_sibling_shared");
        AEKey first = PlannerTestKey.of("prov_sibling_first");
        AEKey second = PlannerTestKey.of("prov_sibling_second");
        var producer = PlannerFixtures.multiOutput("sibling_byproduct",
            List.of(new GenericStack(first, 1), new GenericStack(shared, 1)), raw, 1L);
        var consumer = PlannerFixtures.pattern("sibling_consumer", second, 1, shared, 1L);
        MaterialProvenance provenance = new MaterialProvenance();
        provenance.supplied(shared, new MaterialSource.PatternOutput(producer, false), PlannerAmount.ONE);

        var schedule = ECOExecutionSchedule.from(List.of(component(0, second, consumer),
            component(1, first, producer)), List.of(0, 1), Map.of(producer, 1L, consumer, 1L),
            provenance.freeze());

        assertEquals(List.of(producer, consumer), patterns(schedule));
        assertEquals(1, schedule.dependencies().size());
    }

    @Test void ancestorByproductCreditIsRejectedWithSourceDiagnostic() {
        AEKey shared = PlannerTestKey.of("prov_feedback_shared");
        AEKey middle = PlannerTestKey.of("prov_feedback_middle");
        AEKey goal = PlannerTestKey.of("prov_feedback_goal");
        var ancestor = PlannerFixtures.pattern("feedback_ancestor", middle, 1, shared, 1L);
        var descendant = PlannerFixtures.multiOutput("feedback_descendant",
            List.of(new GenericStack(goal, 1), new GenericStack(shared, 1)), middle, 1L);
        MaterialProvenance provenance = new MaterialProvenance();
        provenance.supplied(shared, new MaterialSource.PatternOutput(descendant, false), PlannerAmount.ONE);
        provenance.supplied(middle, new MaterialSource.PatternOutput(ancestor, true), PlannerAmount.ONE);

        var failure = assertThrows(IllegalStateException.class, () -> ECOExecutionSchedule.from(
            List.of(component(0, middle, ancestor), component(1, goal, descendant)), List.of(0, 1),
            Map.of(ancestor, 1L, descendant, 1L), provenance.freeze()));

        assertTrue(failure.getMessage().contains("byproduct of"));
        assertTrue(failure.getMessage().contains(shared.toString()));
    }

    @Test void attributedSpecialProducerGetsSyntheticPhaseBeforeConsumer() {
        AEKey raw = PlannerTestKey.of("prov_tool_raw");
        AEKey tool = PlannerTestKey.of("prov_tool");
        AEKey goal = PlannerTestKey.of("prov_tool_goal");
        var producer = PlannerFixtures.pattern("tool_producer", tool, 1, raw, 1L);
        var consumer = PlannerFixtures.pattern("tool_consumer", goal, 1, tool, 1L);
        MaterialProvenance provenance = new MaterialProvenance();
        provenance.supplied(tool, new MaterialSource.PatternOutput(producer, true), PlannerAmount.ONE);

        var schedule = ECOExecutionSchedule.from(List.of(component(0, goal, consumer)), List.of(0),
            Map.of(producer, 1L, consumer, 1L), provenance.freeze());

        assertEquals(List.of(producer, consumer), patterns(schedule));
        assertEquals(ECOExecutionSchedule.Type.DAG, schedule.phases().getFirst().type());
        assertEquals(1, schedule.dependencies().size());
    }

    private static ComponentPlanningResult component(int id, AEKey output, IPatternDetails pattern) {
        return new ComponentPlanningResult(id, ComponentPlanningResult.Type.ACYCLIC,
            ComponentPlanningResult.Status.PLANNED, Map.of(output, 1L), Set.of(pattern),
            null, null, Map.of(), null, null);
    }

    private static List<IPatternDetails> patterns(ECOExecutionSchedule schedule) {
        return schedule.phases().stream().map(phase -> phase.patternSet().iterator().next()).toList();
    }
}
