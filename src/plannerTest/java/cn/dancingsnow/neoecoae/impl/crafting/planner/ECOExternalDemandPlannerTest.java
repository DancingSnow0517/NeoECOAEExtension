package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CycleExternalDemandStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ComponentPlanner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.bridge.AE2CraftingPlanBridge;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ECOExternalDemandPlannerTest {
    @Test void directInventoryExternalDemandStillSucceeds() throws Exception {
        Fixture f = fixture(0, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.external, 1));
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertEquals(1, outcome.state().usedItems().get(f.external));
        assertEquals(1, outcome.state().usedItems().get(f.a), "cycle startup seed must be reserved");
        assertEquals(CycleExternalDemandStatus.SOLVED, cycle(outcome).externalDemandStatus());
    }

    @Test void abundantCycleStockStillReportsAndExtractsOnlyTheRequiredStartupSeed() throws Exception {
        Fixture f = fixture(0, false, false);
        var outcome = plan(f.network, stock(f.a, 65, f.external, 1));
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertEquals(1, outcome.state().usedItems().get(f.a));
        var craftingPlan = new AE2CraftingPlanBridge().success(f.network.goal(), 1, false, false, outcome.state());
        assertEquals(1, craftingPlan.usedItems().get(f.a),
            "AE2 confirmation and initial CPU extraction must receive the cycle seed");
        assertFalse(craftingPlan.patternTimes().isEmpty(), "cycle tasks must reach the submitted plan");
    }

    @Test void storedFinalOutputDoesNotSatisfyTheAdditionalCycleCraftRequest() throws Exception {
        AEKey goal = key("stored_self_increment_goal");
        var selfIncrement = compiled(90,
            PlannerFixtures.pattern("stored_self_increment", goal, 2, goal, 1L), goal);
        CompiledNetwork network = PlannerFixtures.network(goal, Map.of(goal, List.of(selfIncrement)));
        var outcome = plan(network, stock(goal, 65));
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertFalse(outcome.state().patternTimes().isEmpty(),
            "existing final output must not turn an additional cycle request into an empty job");
        assertFalse(cycle(outcome).cycleResult().executionWitness().isEmpty());
        assertEquals(1, cycle(outcome).requiredOutputs().get(goal),
            "UI demand remains the requested additional amount");
    }

    @Test void oreCanBeCraftedIntoCycleExternalInput() throws Exception {
        Fixture f = fixture(1, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.leaf, 1));
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertTrue(outcome.state().patternTimes().containsKey(f.externalPatterns.getFirst().details()));
        assertEquals(1, outcome.state().usedItems().get(f.leaf));
    }

    @Test void threeLayerExternalDagIsMerged() throws Exception {
        Fixture f = fixture(3, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.leaf, 1));
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        f.externalPatterns.forEach(pattern -> assertTrue(outcome.state().patternTimes().containsKey(pattern.details())));
    }

    @Test void forbiddenFirstProducerRetriesAcyclicAlternative() throws Exception {
        Fixture f = fixture(1, true, false);
        var outcome = plan(f.network, stock(f.a, 1, f.leaf, 1));
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertTrue(outcome.state().patternTimes().containsKey(f.externalPatterns.getLast().details()));
        assertFalse(outcome.state().patternTimes().containsKey(f.forbiddenExternal.details()));
    }

    @Test void allRoutesIntoCurrentSccAreAbsorbedBySccAndNeverRecurseOrCommit() throws Exception {
        Fixture f = fixture(0, true, true);
        var outcome = plan(f.network, stock(f.a, 1));
        assertNotEquals(PlanningStatus.SUCCESS, outcome.status());
        assertNull(cycle(outcome).externalDemandStatus(), "Tarjan absorbs this route before it becomes boundary demand");
        assertEquals(0, outcome.state().usedItems().get(f.a));
        assertFalse(outcome.state().patternTimes().containsKey(f.cycleInput.details()));
    }

    @Test void missingLeafDoesNotPartiallyCommitCycleOrPretendUnsupported() throws Exception {
        Fixture f = fixture(1, false, false);
        var outcome = plan(f.network, stock(f.a, 1));
        assertEquals(CycleExternalDemandStatus.MISSING, cycle(outcome).externalDemandStatus());
        assertEquals(Map.of(f.leaf, 1L), cycle(outcome).externalMissingItems());
        assertEquals(0, outcome.state().usedItems().get(f.a));
        assertFalse(outcome.state().patternTimes().containsKey(f.cycleInput.details()));
        assertTrue(outcome.state().missingItems().isEmpty(), "failed external transaction must not pollute base state");
    }

    @Test void unsupportedExternalPatternIsNotMissing() throws Exception {
        Fixture f = fixture(1, false, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.leaf, 1));
        assertEquals(CycleExternalDemandStatus.UNSUPPORTED, cycle(outcome).externalDemandStatus());
        assertTrue(outcome.state().missingItems().isEmpty());
    }

    @Test void externalDagComponentsExecuteBeforeCycle() throws Exception {
        Fixture f = fixture(3, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.leaf, 1));
        int cycleId = cycle(outcome).componentId();
        int cycleIndex = outcome.executionComponentOrder().indexOf(cycleId);
        assertTrue(cycleIndex > 0);
        for (var component : outcome.components()) {
            if (component.patterns().stream().anyMatch(p -> f.externalPatterns.stream().anyMatch(x -> x.details() == p))) {
                assertTrue(outcome.executionComponentOrder().indexOf(component.componentId()) < cycleIndex);
            }
        }
    }

    @Test void smallExternalDemandBenchmarkHasNoAbnormalRegression() throws Exception {
        Fixture direct = fixture(0, false, false), deep = fixture(3, false, false), retry = fixture(1, true, false);
        long started = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            assertEquals(PlanningStatus.SUCCESS, plan(direct.network, stock(direct.a, 1, direct.external, 1)).status());
            assertEquals(PlanningStatus.SUCCESS, plan(deep.network, stock(deep.a, 1, deep.leaf, 1)).status());
            assertEquals(PlanningStatus.SUCCESS, plan(retry.network, stock(retry.a, 1, retry.leaf, 1)).status());
        }
        assertTrue(System.nanoTime() - started < 5_000_000_000L);
    }

    private record Fixture(CompiledNetwork network, AEKey a, AEKey external, AEKey leaf,
            CompiledPattern cycleInput, CompiledPattern forbiddenExternal, List<CompiledPattern> externalPatterns) {}

    private static Fixture fixture(int layers, boolean forbiddenFirst, boolean onlyForbidden) {
        return fixture(layers, forbiddenFirst, onlyForbidden, true);
    }

    private static Fixture fixture(int layers, boolean forbiddenFirst, boolean onlyForbidden, boolean externalFast) {
        AEKey goal = key("goal" + layers + forbiddenFirst + onlyForbidden + externalFast);
        AEKey a = key("a" + layers + forbiddenFirst + onlyForbidden + externalFast);
        AEKey b = key("b" + layers + forbiddenFirst + onlyForbidden + externalFast);
        AEKey external = key("ext" + layers + forbiddenFirst + onlyForbidden + externalFast);
        var p1 = compiled(0, PlannerFixtures.pattern("cycle_input", b, 1, a, 1L, external, 1L), b);
        var p2Details = PlannerFixtures.multiOutput("cycle_output", List.of(new GenericStack(a, 1), new GenericStack(goal, 1)), b, 1L);
        var p2a = compiled(1, p2Details, a);
        var p2goal = compiled(2, p2Details, goal);
        var forbidden = compiled(3, PlannerFixtures.pattern("external_from_cycle", external, 1, a, 1L), external);

        Map<AEKey, List<CompiledPattern>> producers = new LinkedHashMap<>();
        producers.put(goal, List.of(p2goal)); producers.put(a, List.of(p2a)); producers.put(b, List.of(p1));
        List<CompiledPattern> externalPatterns = new java.util.ArrayList<>();
        AEKey leaf = external;
        if (layers > 0) {
            AEKey output = external;
            for (int i = 0; i < layers; i++) {
                AEKey input = key("layer_" + layers + "_" + i + forbiddenFirst + onlyForbidden + externalFast);
                var pattern = PlannerFixtures.compiled(10 + i,
                    PlannerFixtures.pattern("external_layer_" + i, output, 1, input, 1L), output,
                    externalFast, externalFast ? "" : "UNSUPPORTED_EXTERNAL");
                externalPatterns.add(pattern); producers.put(output, List.of(pattern)); output = input; leaf = input;
            }
            producers.put(leaf, List.of());
        } else producers.put(external, List.of());
        if (forbiddenFirst) {
            List<CompiledPattern> candidates = new java.util.ArrayList<>(); candidates.add(forbidden);
            if (!onlyForbidden) candidates.addAll(producers.getOrDefault(external, List.of()));
            producers.put(external, List.copyOf(candidates));
        }
        return new Fixture(PlannerFixtures.network(goal, producers), a, external, leaf, p1, forbidden,
            List.copyOf(externalPatterns));
    }

    private static ComponentPlanner.Outcome plan(CompiledNetwork network, KeyCounter inventory) throws Exception {
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        return new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, inventory, 1, true, ECOCancellation.NONE);
    }
    private static cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult cycle(ComponentPlanner.Outcome outcome) {
        return outcome.components().stream().filter(c -> c.type() == cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult.Type.CYCLIC).findFirst().orElseThrow();
    }
    private static KeyCounter stock(Object... pairs) { KeyCounter c = new KeyCounter(); for (int i=0;i<pairs.length;i+=2)c.add((AEKey)pairs[i],((Number)pairs[i+1]).longValue()); return c; }
    private static CompiledPattern compiled(int id, PlannerFixtures.Pattern p, AEKey output) { return PlannerFixtures.compiled(id,p,output,true,""); }
    private static PlannerTestKey key(String name) { return PlannerTestKey.of(name); }
}
