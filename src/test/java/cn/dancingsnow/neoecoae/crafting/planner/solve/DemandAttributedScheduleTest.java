package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.*;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.*;
import cn.dancingsnow.neoecoae.crafting.planner.result.*;
import cn.dancingsnow.neoecoae.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.*;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemandAttributedScheduleTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void honeyStockAndReturnedBottleAreNotConnectedToEveryConsumer() throws Exception {
        var honey = AEItemKey.of(Items.HONEY_BOTTLE);
        var bottle = AEItemKey.of(Items.GLASS_BOTTLE);
        var sugar = AEItemKey.of(Items.SUGAR);
        var paper = AEItemKey.of(Items.PAPER);
        var emerald = AEItemKey.of(Items.EMERALD);
        var goal = AEItemKey.of(Items.DIAMOND);
        var finish = pattern(0, goal, List.of(new GenericStack(goal, 1)), sugar, paper, emerald);
        var consumeHoney = pattern(1, sugar,
            List.of(new GenericStack(sugar, 1), new GenericStack(bottle, 1)), honey);
        var useBottle = pattern(2, paper,
            List.of(new GenericStack(paper, 1), new GenericStack(honey, 1)), bottle);
        var otherHoneyConsumer = pattern(3, emerald, List.of(new GenericStack(emerald, 1)), honey);
        Map<AEKey, List<CompiledPattern>> producers = new LinkedHashMap<>();
        producers.put(goal, List.of(finish));
        producers.put(sugar, List.of(consumeHoney));
        producers.put(paper, List.of(useBottle));
        producers.put(emerald, List.of(otherHoneyConsumer));
        producers.put(honey, List.of());
        producers.put(bottle, List.of());
        var network = new CompiledNetwork(goal, producers, Set.of(), 6, 4);
        var inventory = new KeyCounter();
        inventory.add(honey, 1);
        var outcome = new AcyclicCraftingSolver().solve(network, new AcyclicRoutePlan(List.of()),
            inventory, 1, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        var provenance = outcome.state().executionProvenance();
        provenance.requireComplete();
        assertEquals(MaterialSource.Stock.INSTANCE,
            provenance.allocationsFor(consumeHoney.details()).getFirst().source());
        assertEquals(new MaterialSource.PatternOutput(consumeHoney.details(), false),
            provenance.allocationsFor(useBottle.details()).getFirst().source());
        assertEquals(new MaterialSource.PatternOutput(useBottle.details(), false),
            provenance.allocationsFor(otherHoneyConsumer.details()).getFirst().source());
        var schedule = ECOExecutionSchedule.from(List.of(), List.of(), outcome.state().patternTimes(), provenance);
        assertBefore(schedule, consumeHoney.details(), useBottle.details());
        assertBefore(schedule, useBottle.details(), otherHoneyConsumer.details());
        assertBefore(schedule, otherHoneyConsumer.details(), finish.details());
        assertEquals(4, schedule.phases().size());
    }

    @Test
    void groupedAcyclicProducersAreOrderedInsteadOfReportedAsSelfCycles() throws Exception {
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var tool = AEItemKey.of(Items.GLASS_BOTTLE);
        var goal = AEItemKey.of(Items.DIAMOND);
        var supplier = pattern(0, tool, List.of(new GenericStack(tool, 1)), raw);
        var consumer = pattern(1, goal, List.of(new GenericStack(goal, 1)), tool);
        var network = new CompiledNetwork(goal, Map.of(goal, List.of(consumer),
            tool, List.of(supplier), raw, List.of()), Set.of(), 3, 2);
        var inventory = new KeyCounter();
        inventory.add(raw, 1);
        var outcome = new AcyclicCraftingSolver().solve(network, new AcyclicRoutePlan(List.of()),
            inventory, 1, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        // Local tool/catalyst producer chains are collected in the goal's structural component.
        var component = acyclicComponent(0, Set.of(consumer.details(), supplier.details()));
        var schedule = ECOExecutionSchedule.from(List.of(component), List.of(0),
            outcome.state().patternTimes(), outcome.state().executionProvenance());
        assertBefore(schedule, supplier.details(), consumer.details());
        assertEquals(2, schedule.phases().size());
        assertEquals(List.of(new ECOExecutionSchedule.PhaseDependency(0, 1)), schedule.dependencies());
        assertEquals(2, schedule.phases().stream().map(p -> p.componentId()).distinct().count());
    }

    @Test
    void locallyCraftedReusableToolBuildsAValidComponentSchedule() throws Exception {
        var raw = AEItemKey.of(Items.IRON_INGOT);
        var tool = AEItemKey.of(Items.GLASS_BOTTLE);
        var goal = AEItemKey.of(Items.DIAMOND);
        var supplier = pattern(0, tool, List.of(new GenericStack(tool, 1)), raw);
        var base = pattern(1, goal, List.of(new GenericStack(goal, 1)), tool);
        var input = base.inputs().getFirst();
        when(input.source().getRemainingKey(tool)).thenReturn(tool);
        var special = new cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis(List.of(
            new cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis.Requirement(input, tool,
                cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis.Type.REUSABLE, 0, 0)));
        var consumer = new CompiledPattern(base.id(), base.details(), goal, PlannerAmount.ONE,
            base.inputs(), base.outputs(), true, null, false, base.semantics(), special);
        var network = new CompiledNetwork(goal, Map.of(goal, List.of(consumer),
            tool, List.of(supplier), raw, List.of()), Set.of(), 3, 2);
        var graph = new cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder()
            .build(network, ECOCancellation.NONE);
        var condensation = cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph.build(graph,
            new cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer()
                .analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var inventory = new KeyCounter();
        inventory.add(raw, 1);
        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(),
            new cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver())
                .plan(network, condensation, inventory, 1, true, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), outcome.trace().diagnostics().toString());
        assertTrue(outcome.components().stream().anyMatch(component ->
            component.executionPatterns().containsAll(Set.of(supplier.details(), consumer.details()))));
        var schedule = ECOExecutionSchedule.from(outcome.components(), outcome.executionComponentOrder(),
            outcome.state().patternTimes(), outcome.state().executionProvenance());
        assertBefore(schedule, supplier.details(), consumer.details());
    }

    @Test
    void groupedMutualCycleStillFailsClosed() {
        var a = AEItemKey.of(Items.GLASS_BOTTLE);
        var b = AEItemKey.of(Items.HONEY_BOTTLE);
        var first = pattern(0, a, List.of(new GenericStack(a, 1)), b);
        var second = pattern(1, b, List.of(new GenericStack(b, 1)), a);
        var ledger = new MaterialProvenance();
        var firstInput = MaterialDemand.input(first.details(), 0, b, PlannerAmount.ONE);
        var secondInput = MaterialDemand.input(second.details(), 0, a, PlannerAmount.ONE);
        ledger.register(firstInput);
        ledger.register(secondInput);
        ledger.allocate(firstInput, b, new MaterialSource.PatternOutput(second.details(), true), PlannerAmount.ONE);
        ledger.allocate(secondInput, a, new MaterialSource.PatternOutput(first.details(), true), PlannerAmount.ONE);
        var component = acyclicComponent(0, Set.of(first.details(), second.details()));
        assertThrows(IllegalStateException.class, () -> ECOExecutionSchedule.from(List.of(component), List.of(0),
            Map.of(first.details(), 1L, second.details(), 1L), ledger.freeze()));
    }

    private static ComponentPlanningResult acyclicComponent(int id, Set<IPatternDetails> patterns) {
        return new ComponentPlanningResult(id, ComponentPlanningResult.Type.ACYCLIC,
            ComponentPlanningResult.Status.PLANNED, Map.of(), patterns, null, null, Map.of(), null, null);
    }

    @Test
    void missingDemandAttributionIsAnInvariantError() {
        var key = AEItemKey.of(Items.GLASS_BOTTLE);
        var pattern = pattern(0, key, List.of(new GenericStack(key, 1)));
        var summaryOnly = new ExecutionProvenance(Map.of(key,
            Map.of(MaterialSource.Stock.INSTANCE, PlannerAmount.ONE)));
        assertThrows(IllegalStateException.class, () -> ECOExecutionSchedule.from(List.of(), List.of(),
            Map.of(pattern.details(), 1L), summaryOnly));
    }

    @Test
    void realSelfReturnIsNotSilentlyDroppedAsAnAcyclicEdge() {
        var key = AEItemKey.of(Items.GLASS_BOTTLE);
        var pattern = pattern(0, key, List.of(new GenericStack(key, 2)), key);
        var ledger = new MaterialProvenance();
        var input = MaterialDemand.input(pattern.details(), 0, key, PlannerAmount.ONE);
        ledger.register(input);
        ledger.credit(key, pattern.details(), PlannerAmount.ONE);
        ledger.consumeCredit(input, key, PlannerAmount.ONE);
        assertThrows(IllegalStateException.class, () -> ECOExecutionSchedule.from(List.of(), List.of(),
            Map.of(pattern.details(), 1L), ledger.freeze()));
    }

    private static void assertBefore(ECOExecutionSchedule schedule, IPatternDetails before, IPatternDetails after) {
        int first = -1, second = -1;
        for (int i = 0; i < schedule.phases().size(); i++) {
            if (schedule.phases().get(i).patternSet().contains(before)) first = i;
            if (schedule.phases().get(i).patternSet().contains(after)) second = i;
        }
        assertTrue(first >= 0 && second > first);
    }

    private static CompiledPattern pattern(int id, AEKey key, List<GenericStack> outputs, AEKey... keys) {
        var details = mock(IPatternDetails.class);
        when(details.getOutputs()).thenReturn(outputs);
        var inputs = new ArrayList<CompiledInput>();
        var rawInputs = new IPatternDetails.IInput[keys.length];
        for (int i = 0; i < keys.length; i++) {
            var raw = mock(IPatternDetails.IInput.class);
            when(raw.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(keys[i], 1)});
            when(raw.getMultiplier()).thenReturn(1L);
            rawInputs[i] = raw;
            inputs.add(new CompiledInput(raw, keys[i], 1L, true, null));
        }
        when(details.getInputs()).thenReturn(rawInputs);
        var semanticInputs = inputs.stream().map(input -> new PatternSemantics.Input(
            input.source(), input.key(), input.amountPerPattern(), null, PlannerAmount.ZERO)).toList();
        var semantics = new PatternSemantics(details, null, semanticInputs, outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, details, key, PlannerAmount.ONE, inputs, outputs, true, null, false, semantics);
    }
}
