package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.stacks.AEKey;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CycleExternalDemandStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CycleExecutionDisposition;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ActiveRouteSelector;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ComponentPlanner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.api.me.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
        assertEquals(CycleExecutionDisposition.ORDERED_EXECUTION, cycle(outcome).cycleDisposition());
        assertEquals(1, cycle(outcome).requiredOutputs().get(goal),
            "UI demand remains the requested additional amount");
    }

    @Test void cycleBoundaryDemandCanBeDelegatedToAnUpstreamGrowthCycle() throws Exception {
        AEKey goal = key("delegated_goal");
        AEKey a = key("delegated_a");
        AEKey b = key("delegated_b");
        AEKey growth = key("delegated_growth");
        AEKey c = key("delegated_c");
        AEKey d = key("delegated_d");

        var consumeGrowth = compiled(100,
            PlannerFixtures.pattern("consume_growth", b, 1, a, 1L, growth, 1L, c, 1L), b);
        var finishDetails = PlannerFixtures.multiOutput("finish_goal",
            List.of(new GenericStack(a, 1), new GenericStack(goal, 1)), b, 1L);
        var finishA = compiled(101, finishDetails, a);
        var finishGoal = compiled(102, finishDetails, goal);

        var growStep = compiled(103, PlannerFixtures.pattern("grow_step", d, 1, c, 1L), d);
        var emitDetails = PlannerFixtures.multiOutput("emit_growth",
            List.of(new GenericStack(c, 2), new GenericStack(growth, 1)), d, 1L);
        var emitC = compiled(104, emitDetails, c);
        var emitGrowth = compiled(105, emitDetails, growth);
        var unsupportedGrowth = PlannerFixtures.compiled(106,
            PlannerFixtures.pattern("unsupported_growth", growth, 1, d, 1L), growth,
            false, "UNSUPPORTED_GROWTH_ROUTE");

        Map<AEKey, List<CompiledPattern>> producers = new LinkedHashMap<>();
        producers.put(goal, List.of(finishGoal));
        producers.put(a, List.of(finishA));
        producers.put(b, List.of(consumeGrowth));
        producers.put(growth, List.of(unsupportedGrowth));
        producers.put(c, List.of(emitC));
        producers.put(d, List.of(growStep));

        var outcome = plan(PlannerFixtures.network(goal, producers), stock(a, 1, c, 2));
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), () -> "components=" + outcome.components()
            + " diagnostics=" + outcome.trace().diagnostics() + " missing=" + outcome.state().missingAmounts());
        var cycles = outcome.components().stream()
            .filter(component -> component.type()
                == cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult.Type.CYCLIC)
            .toList();
        assertEquals(2, cycles.size());
        assertTrue(cycles.stream().allMatch(component -> component.cycleStatus()
            == cn.dancingsnow.neoecoae.impl.crafting.planner.result.CyclePlanningStatus.SOLVED));
        assertTrue(outcome.state().patternTimes().containsKey(consumeGrowth.details()));
        assertTrue(outcome.state().patternTimes().containsKey(emitGrowth.details()));
        assertTrue(outcome.state().missingItems().isEmpty());
        int consumerId = cycles.stream().filter(component -> component.executionPatterns()
            .contains(consumeGrowth.details())).findFirst().orElseThrow().componentId();
        int supplierId = cycles.stream().filter(component -> component.executionPatterns()
            .contains(emitGrowth.details())).findFirst().orElseThrow().componentId();
        assertTrue(outcome.executionComponentOrder().indexOf(supplierId)
            < outcome.executionComponentOrder().indexOf(consumerId));
        var schedule = ECOExecutionSchedule.from(
            outcome.components(), outcome.executionComponentOrder(), outcome.state().patternTimes());
        assertEquals(2, schedule.phases().stream()
            .filter(phase -> phase.type() == ECOExecutionSchedule.Type.CYCLE).count());
    }

    @Test void externalAcyclicPrefixCanDeferItsNestedGrowthCycleDependency() throws Exception {
        AEKey goal = key("nested_goal");
        AEKey a = key("nested_a");
        AEKey b = key("nested_b");
        AEKey external = key("nested_external");
        AEKey growth = key("nested_growth");
        AEKey c = key("nested_c");
        AEKey d = key("nested_d");

        var consumeExternal = compiled(110,
            PlannerFixtures.pattern("consume_nested_external", b, 1, a, 1L, external, 1L), b);
        var finishDetails = PlannerFixtures.multiOutput("finish_nested_goal",
            List.of(new GenericStack(a, 1), new GenericStack(goal, 1)), b, 1L);
        var externalPrefix = compiled(113,
            PlannerFixtures.pattern("nested_external_prefix", external, 1, growth, 1L), external);
        var growStep = compiled(114, PlannerFixtures.pattern("nested_grow_step", d, 1, c, 1L), d);
        var emitDetails = PlannerFixtures.multiOutput("emit_nested_growth",
            List.of(new GenericStack(c, 2), new GenericStack(growth, 1)), d, 1L);

        Map<AEKey, List<CompiledPattern>> producers = new LinkedHashMap<>();
        producers.put(goal, List.of(compiled(111, finishDetails, goal)));
        producers.put(a, List.of(compiled(112, finishDetails, a)));
        producers.put(b, List.of(consumeExternal));
        producers.put(external, List.of(externalPrefix));
        producers.put(growth, List.of(compiled(115, emitDetails, growth)));
        producers.put(c, List.of(compiled(116, emitDetails, c)));
        producers.put(d, List.of(growStep));

        var outcome = plan(PlannerFixtures.network(goal, producers), stock(a, 1, c, 1));
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), () -> "components=" + outcome.components()
            + " diagnostics=" + outcome.trace().diagnostics() + " missing=" + outcome.state().missingAmounts());
        assertTrue(outcome.state().patternTimes().containsKey(externalPrefix.details()),
            "the acyclic prefix must remain in the executable task vector");
        assertTrue(outcome.state().patternTimes().containsKey(emitDetails),
            "the nested growth cycle must be planned instead of rejected as an external cycle");
        assertTrue(outcome.state().missingItems().isEmpty());
        assertEquals(2, outcome.components().stream().filter(component -> component.type()
            == cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult.Type.CYCLIC)
            .filter(component -> component.cycleStatus()
                == cn.dancingsnow.neoecoae.impl.crafting.planner.result.CyclePlanningStatus.SOLVED)
            .count());
        var executable = new AE2CraftingPlanBridge().success(goal, 1, false, false, outcome.state());
        var result = new ECOPlanningResult(outcome.status(), executable, outcome.trace(), outcome.cycles(),
            outcome.components(), outcome.executionComponentOrder(), 1L);
        assertFalse(assertDoesNotThrow(result::executionSchedule).phases().isEmpty(),
            "cross-cycle stock projections must remain within the final used-items ledger");
    }

    @Test void missingCycleStartupSeedCanBeDelegatedToAnUpstreamGrowthCycle() throws Exception {
        AEKey goal = key("seed_recovery_goal");
        AEKey seed = key("seed_recovery_seed");
        AEKey intermediate = key("seed_recovery_intermediate");
        AEKey catalyst = key("seed_recovery_catalyst");
        AEKey growth = key("seed_recovery_growth");
        AEKey growthIntermediate = key("seed_recovery_growth_intermediate");

        var consumeSeed = compiled(120,
            PlannerFixtures.pattern("consume_recovered_seed", intermediate, 1, seed, 1L, catalyst, 1L),
            intermediate);
        var finishDetails = PlannerFixtures.multiOutput("finish_with_seed_growth",
            List.of(new GenericStack(seed, 2), new GenericStack(goal, 1)), intermediate, 1L);
        var growStep = compiled(123, PlannerFixtures.pattern("grow_seed_supplier",
            growthIntermediate, 1, growth, 1L), growthIntermediate);
        var emitSeedDetails = PlannerFixtures.multiOutput("emit_startup_seed",
            List.of(new GenericStack(growth, 2), new GenericStack(intermediate, 1),
                new GenericStack(catalyst, 1)), growthIntermediate, 1L);

        Map<AEKey, List<CompiledPattern>> producers = new LinkedHashMap<>();
        producers.put(goal, List.of(compiled(121, finishDetails, goal)));
        producers.put(seed, List.of(compiled(122, finishDetails, seed)));
        producers.put(intermediate, List.of(consumeSeed));
        producers.put(catalyst, List.of(compiled(125, emitSeedDetails, catalyst)));
        producers.put(growth, List.of(compiled(124, emitSeedDetails, growth)));
        producers.put(growthIntermediate, List.of(growStep));

        var outcome = plan(PlannerFixtures.network(goal, producers), stock(growth, 1));
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), () -> "components=" + outcome.components()
            + " diagnostics=" + outcome.trace().diagnostics() + " missing=" + outcome.state().missingAmounts());
        assertTrue(outcome.state().patternTimes().containsKey(finishDetails));
        assertTrue(outcome.state().patternTimes().containsKey(emitSeedDetails),
            () -> "tasks=" + outcome.state().patternTimes() + " components=" + outcome.components());
        assertEquals(1L, outcome.state().usedItems().get(growth));
        assertEquals(0L, outcome.state().usedItems().get(intermediate),
            "a crafted startup seed must not be projected as an inventory reservation");
        var executable = new AE2CraftingPlanBridge().success(goal, 1, false, false, outcome.state());
        var result = new ECOPlanningResult(outcome.status(), executable, outcome.trace(), outcome.cycles(),
            outcome.components(), outcome.executionComponentOrder(), 1L);
        assertFalse(assertDoesNotThrow(result::executionSchedule).phases().isEmpty());
    }

    @Test void cycleMemberStartupSeedCanUseAnIndependentAlternateProducer() throws Exception {
        AEKey goal = key("alternate_seed_goal");
        AEKey feedback = key("alternate_seed_feedback");
        AEKey intermediate = key("alternate_seed_intermediate");
        AEKey leaf = key("alternate_seed_leaf");

        var consumeFeedback = compiled(130,
            PlannerFixtures.pattern("consume_alternate_feedback", intermediate, 1, feedback, 1L), intermediate);
        var finishDetails = PlannerFixtures.multiOutput("finish_alternate_seed_cycle",
            List.of(new GenericStack(feedback, 2), new GenericStack(goal, 1)), intermediate, 1L);
        var alternateSeed = compiled(133,
            PlannerFixtures.pattern("craft_independent_startup_seed", intermediate, 1, leaf, 1L), intermediate);

        Map<AEKey, List<CompiledPattern>> producers = new LinkedHashMap<>();
        producers.put(goal, List.of(compiled(131, finishDetails, goal)));
        producers.put(feedback, List.of(compiled(132, finishDetails, feedback)));
        producers.put(intermediate, List.of(consumeFeedback, alternateSeed));
        producers.put(leaf, List.of());
        CompiledNetwork network = PlannerFixtures.network(goal, producers);

        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var selection = new ActiveRouteSelector().select(graph, false, ECOCancellation.NONE);
        assertFalse(selection.acyclic(), "the primary route must remain cyclic for this recovery test");
        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, selection, stock(leaf, 1), 1, true, ECOCancellation.NONE);

        assertEquals(PlanningStatus.SUCCESS, outcome.status(), () -> "components=" + outcome.components()
            + " diagnostics=" + outcome.trace().diagnostics() + " missing=" + outcome.state().missingAmounts());
        assertTrue(outcome.state().patternTimes().containsKey(alternateSeed.details()));
        assertTrue(outcome.state().patternTimes().containsKey(finishDetails));
        assertEquals(1L, outcome.state().usedItems().get(leaf));
        assertEquals(0L, outcome.state().usedItems().get(intermediate));
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
        assertEquals(CycleExternalDemandStatus.FORBIDDEN_ROUTE, cycle(outcome).externalDemandStatus(),
            "startup-seed recovery must reject a route that re-enters the current SCC");
        assertEquals(0, outcome.state().usedItems().get(f.a));
        assertFalse(outcome.state().patternTimes().containsKey(f.cycleInput.details()));
    }

    @Test void missingLeafDoesNotPartiallyCommitCycleOrPretendUnsupported() throws Exception {
        Fixture f = fixture(1, false, false);
        var outcome = plan(f.network, stock(f.a, 1));
        assertEquals(CycleExternalDemandStatus.MISSING, cycle(outcome).externalDemandStatus());
        assertEquals(CycleExecutionDisposition.BLOCKED, cycle(outcome).cycleDisposition());
        assertEquals(Map.of(f.leaf, 1L), cycle(outcome).externalMissingItems());
        assertEquals(0, outcome.state().usedItems().get(f.a));
        assertFalse(outcome.state().patternTimes().containsKey(f.cycleInput.details()));
        assertEquals(1, outcome.state().missingItems().get(f.leaf),
            "the concrete external leaf deficit must be visible in AE2 confirmation");
    }

    @Test void unsupportedExternalPatternIsNotMissing() throws Exception {
        Fixture f = fixture(1, false, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.leaf, 1));
        assertEquals(CycleExternalDemandStatus.UNSUPPORTED, cycle(outcome).externalDemandStatus());
        assertEquals(1, outcome.state().missingItems().get(f.network.goal()),
            "an unsupported boundary must keep the incomplete cycle from being submitted as an empty plan");
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

        var schedule = ECOExecutionSchedule.from(
            outcome.components(), outcome.executionComponentOrder(), outcome.state().patternTimes());
        int cyclePhaseIndex = java.util.stream.IntStream.range(0, schedule.phases().size())
            .filter(index -> schedule.phases().get(index).type() == ECOExecutionSchedule.Type.CYCLE)
            .findFirst().orElseThrow();
        for (var external : f.externalPatterns) {
            if (!outcome.state().patternTimes().containsKey(external.details())) continue;
            int externalPhaseIndex = java.util.stream.IntStream.range(0, schedule.phases().size())
                .filter(index -> schedule.phases().get(index).patternSet().contains(external.details()))
                .findFirst().orElseThrow();
            assertTrue(externalPhaseIndex < cyclePhaseIndex);
        }
    }

    @Test void solvedCycleProducesNonEmptyRuntimeSchedule() throws Exception {
        Fixture f = fixture(0, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.external, 1));
        var schedule = ECOExecutionSchedule.from(
            outcome.components(), outcome.executionComponentOrder(), outcome.state().patternTimes());
        assertFalse(schedule.phases().isEmpty());
        assertTrue(ECOPhaseScheduler.requiresComponentScheduling(schedule));
        assertTrue(schedule.phases().stream().noneMatch(phase -> phase.patternSet().isEmpty()));
        assertTrue(schedule.phases().stream().anyMatch(phase -> phase.type() == ECOExecutionSchedule.Type.CYCLE
            && !phase.patternSet().isEmpty()));
        assertEquals(outcome.state().patternTimes().keySet(), schedule.phases().stream()
            .flatMap(phase -> phase.patternSet().stream())
            .collect(java.util.stream.Collectors.toSet()));
    }

    @Test void copiedPlanRecoversExactPlanningResultByContent() throws Exception {
        Fixture f = fixture(0, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.external, 1));
        var bridge = new AE2CraftingPlanBridge();
        var original = bridge.success(f.network.goal(), 1, false, false, outcome.state());
        var result = new ECOPlanningResult(outcome.status(), original, outcome.trace(), outcome.cycles(),
            outcome.components(), outcome.executionComponentOrder(), 1L);
        ECOPlanningResultRegistry.register(original, result);

        var copied = new CraftingPlan(original.finalOutput(), original.bytes(), original.simulation(),
            original.multiplePaths(), original.usedItems(), new KeyCounter(), original.missingItems(),
            original.patternTimes());
        assertNotSame(original, copied);
        assertSame(result, ECOPlanningResultRegistry.find(copied));
        assertFalse(result.executionSchedule().phases().isEmpty());
    }

    @Test void simulatedPlanIsRejectedAndNonSuccessfulCycleIsRegisteredOnlyFailClosed() throws Exception {
        Fixture f = fixture(2, true, false);
        var outcome = plan(f.network, stock(f.a, 1, f.leaf, 1));
        var executable = new AE2CraftingPlanBridge().success(
            PlannerTestKey.of("registry_execution_only"), 1, false, false, outcome.state());
        var simulated = new CraftingPlan(executable.finalOutput(), executable.bytes(), true,
            executable.multiplePaths(), executable.usedItems(), executable.emittedItems(),
            executable.missingItems(), executable.patternTimes());
        var simulatedResult = new ECOPlanningResult(PlanningStatus.SUCCESS, simulated, outcome.trace(),
            outcome.cycles(), outcome.components(), outcome.executionComponentOrder(), 1L);
        ECOPlanningResultRegistry.register(simulated, simulatedResult);
        assertNull(ECOPlanningResultRegistry.find(simulated));

        var partialResult = new ECOPlanningResult(PlanningStatus.PARTIAL, executable, outcome.trace(),
            outcome.cycles(), outcome.components(), outcome.executionComponentOrder(), 1L);
        ECOPlanningResultRegistry.register(executable, partialResult);
        assertSame(partialResult, ECOPlanningResultRegistry.find(executable));
        var recovered = ECOPlanningResultRegistry.recoverExecutionMetadata(executable);
        assertNotNull(recovered);
        assertTrue(recovered.cycleExpected());
        assertEquals(ECOPlanningResultRegistry.RecoveryState.MISSING_OR_INVALID_SCHEDULE, recovered.state());
        assertEquals("STATUS_NOT_SUCCESS", recovered.rejectionReason());
        assertNull(recovered.schedule(), "a non-success result must never provide executable cycle metadata");
    }

    @Test void patchedPlanRebindsScheduleToSubmittedPatternInstances() throws Exception {
        Fixture f = fixture(2, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.leaf, 1));
        var bridge = new AE2CraftingPlanBridge();
        var original = bridge.success(f.network.goal(), 1, false, false, outcome.state());
        var result = new ECOPlanningResult(outcome.status(), original, outcome.trace(), outcome.cycles(),
            outcome.components(), outcome.executionComponentOrder(), 1L);
        ECOPlanningResultRegistry.register(original, result);

        Map<IPatternDetails, Long> patchedTasks = new LinkedHashMap<>();
        for (var task : original.patternTimes().entrySet()) {
            var patched = new PlannerFixtures.Pattern("patched-" + task.getKey(),
                task.getKey().getInputs(), task.getKey().getOutputs());
            patchedTasks.put(patched, task.getValue());
        }
        var copied = new CraftingPlan(original.finalOutput(), original.bytes(), original.simulation(),
            original.multiplePaths(), original.usedItems(), new KeyCounter(), original.missingItems(), patchedTasks);
        var recovered = ECOPlanningResultRegistry.recoverSchedule(copied);
        assertNotNull(recovered);
        assertEquals("strict-plan-identity", recovered.matchMode());
        assertTrue(recovered.schedule().phases().stream().flatMap(phase -> phase.patternSet().stream())
            .allMatch(patchedTasks::containsKey));
        assertTrue(recovered.schedule().phases().stream().flatMap(phase -> phase.cycleWitness().stream())
            .allMatch(patchedTasks::containsKey));
    }

    @Test void largerBatchAlternateDagProducerDoesNotMatchByEqualTotalProduction() throws Exception {
        Fixture f = fixture(2, false, false);
        var outcome = plan(f.network, stock(f.a, 2, f.leaf, 2), 2);
        var bridge = new AE2CraftingPlanBridge();
        var original = bridge.success(f.network.goal(), 2, false, false, outcome.state());
        var result = new ECOPlanningResult(outcome.status(), original, outcome.trace(), outcome.cycles(),
            outcome.components(), outcome.executionComponentOrder(), 1L);
        ECOPlanningResultRegistry.register(original, result);

        IPatternDetails source = result.executionSchedule().phases().stream()
            .filter(phase -> phase.type() == ECOExecutionSchedule.Type.DAG)
            .flatMap(phase -> phase.patternSet().stream())
            .filter(pattern -> original.patternTimes().getOrDefault(pattern, 0L) > 0
                && original.patternTimes().get(pattern) % 2 == 0)
            .findFirst().orElseThrow();
        List<GenericStack> doubledOutputs = source.getOutputs().stream()
            .map(output -> new GenericStack(output.what(), Math.multiplyExact(output.amount(), 2L)))
            .toList();
        var replacement = new PlannerFixtures.Pattern("larger-batch-" + source,
            source.getInputs(), doubledOutputs);
        Map<IPatternDetails, Long> replacedTasks = new LinkedHashMap<>(original.patternTimes());
        long originalExecutions = replacedTasks.remove(source);
        replacedTasks.put(replacement, originalExecutions / 2);
        var copied = new CraftingPlan(original.finalOutput(), original.bytes(), original.simulation(),
            original.multiplePaths(), original.usedItems(), new KeyCounter(), original.missingItems(), replacedTasks);

        var recovered = ECOPlanningResultRegistry.recoverSchedule(copied);
        assertNull(recovered, "different physical definition and firing count must not bind metadata");
    }

    @Test void confirmedTransformedPlanRemainsAuthoritativeAndDoesNotReceiveMismatchedMetadata() throws Exception {
        Fixture f = fixture(1, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.leaf, 1));
        var bridge = new AE2CraftingPlanBridge();
        var executable = bridge.success(f.network.goal(), 1, false, false, outcome.state());
        var result = new ECOPlanningResult(outcome.status(), executable, outcome.trace(), outcome.cycles(),
            outcome.components(), outcome.executionComponentOrder(), 1L);

        // Model a multi-planner/RETURN transformer retaining a different task vector while the confirmation
        // report still represents the complete ECO cycle plan.
        Map<IPatternDetails, Long> transformedTasks = new LinkedHashMap<>(executable.patternTimes());
        var changed = transformedTasks.entrySet().iterator().next();
        transformedTasks.put(changed.getKey(), Math.addExact(changed.getValue(), 7L));
        var confirmed = new CraftingPlan(executable.finalOutput(), executable.bytes(), false,
            executable.multiplePaths(), executable.usedItems(), new KeyCounter(), executable.missingItems(),
            transformedTasks);
        // Submission integrations commonly rebuild CraftingPlan and therefore lose mixin instance fields.
        Map<IPatternDetails, Long> submittedTasks = new LinkedHashMap<>(confirmed.patternTimes());
        var submittedChanged = submittedTasks.entrySet().iterator().next();
        submittedTasks.put(submittedChanged.getKey(), Math.addExact(submittedChanged.getValue(), 5L));
        var submittedCopy = new CraftingPlan(confirmed.finalOutput(), confirmed.bytes(), confirmed.simulation(),
            confirmed.multiplePaths(), confirmed.usedItems(), new KeyCounter(), confirmed.missingItems(),
            submittedTasks);
        assertNotSame(confirmed, submittedCopy);
        assertNotEquals(executable.patternTimes(), submittedCopy.patternTimes());
        assertNotEquals(confirmed.patternTimes(), submittedCopy.patternTimes(),
            "the submission path may transform the task vector after confirmation");
        assertSame(submittedCopy, ECOPlanningResultRegistry.withSubmissionAlias(confirmed, result, () -> {
            assertFalse(ECOPlanningResultRegistry.shouldPreserveSubmissionPlan(submittedCopy),
                "a transformed task vector must never be protected by the confirmation alias");
            var resolved = ECOPlanningResultRegistry.resolveSubmissionPlan(submittedCopy);
            assertSame(submittedCopy, resolved);
            assertNull(ECOPlanningResultRegistry.activeSubmissionMetadata(resolved),
                "different submitted task vector must not receive ECO metadata");
            return resolved;
        }));
        assertSame(submittedCopy, ECOPlanningResultRegistry.resolveSubmissionPlan(submittedCopy),
            "the confirmation binding must not escape its synchronous submitJob call");
        assertSame(submittedCopy, ECOPlanningResultRegistry.withSubmissionAlias(confirmed, result,
            () -> ECOPlanningResultRegistry.resolveSubmissionPlan(submittedCopy)),
            "a rejected submission must remain the submitted plan on a later retry");
    }

    @Test void exactConfirmedPlanCanBeProtectedFromExternalTaskVectorRewrite() throws Exception {
        Fixture f = fixture(1, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.leaf, 1));
        var executable = new AE2CraftingPlanBridge().success(
            PlannerTestKey.of("registry_transform_guard_unique"), 1, false, false, outcome.state());
        var result = new ECOPlanningResult(outcome.status(), executable, outcome.trace(), outcome.cycles(),
            outcome.components(), outcome.executionComponentOrder(), 1L);

        assertTrue(ECOPlanningResultRegistry.withSubmissionAlias(executable, result, () -> {
            assertTrue(ECOPlanningResultRegistry.shouldPreserveSubmissionPlan(executable));
            assertSame(executable, ECOPlanningResultRegistry.resolveSubmissionPlan(executable));
            return true;
        }));
        assertFalse(ECOPlanningResultRegistry.shouldPreserveSubmissionPlan(executable),
            "the protection must not escape the synchronous submission scope");
    }

    @Test void solvedCycleExpectationSurvivesWhenScheduleConstructionProducesNoCyclePhase() throws Exception {
        Fixture f = fixture(1, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.leaf, 1));
        var executable = new AE2CraftingPlanBridge().success(
            f.network.goal(), 1, false, false, outcome.state());
        int cycleComponentId = outcome.components().stream()
            .filter(component -> component.type()
                == cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult.Type.CYCLIC)
            .findFirst().orElseThrow().componentId();
        List<Integer> brokenExecutionOrder = outcome.executionComponentOrder().stream()
            .filter(componentId -> componentId != cycleComponentId).toList();
        var result = new ECOPlanningResult(PlanningStatus.SUCCESS, executable, outcome.trace(), outcome.cycles(),
            outcome.components(), brokenExecutionOrder, 1L);

        assertTrue(ECOPlanningResultRegistry.cycleExpected(result));
        assertThrows(IllegalStateException.class, result::executionSchedule,
            "an incomplete schedule must not be accepted even when its remaining phases are acyclic");
        ECOPlanningResultRegistry.withSubmissionAlias(executable, result, () -> {
            var resolved = ECOPlanningResultRegistry.resolveSubmissionPlan(executable);
            var metadata = ECOPlanningResultRegistry.activeSubmissionMetadata(resolved);
            assertNotNull(metadata, "a missing schedule must not erase the independently known cycle expectation");
            assertTrue(metadata.cycleExpected());
            assertFalse(ECOPhaseScheduler.requiresComponentScheduling(metadata.executionSchedule()),
                "the executor can now detect this invariant violation and refuse dispatch");
            return null;
        });
        assertNull(ECOPlanningResultRegistry.activeSubmissionMetadata(executable));
    }

    @Test void rebuiltPlanCanBeRegisteredUnderItsActualReturnedSignature() throws Exception {
        Fixture f = fixture(2, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.leaf, 1));
        var original = new AE2CraftingPlanBridge().success(
            f.network.goal(), 1, false, false, outcome.state());
        var result = new ECOPlanningResult(outcome.status(), original, outcome.trace(), outcome.cycles(),
            outcome.components(), outcome.executionComponentOrder(), 1L);
        Map<IPatternDetails, Long> rebuiltTasks = new LinkedHashMap<>(original.patternTimes());
        var changed = rebuiltTasks.entrySet().iterator().next();
        rebuiltTasks.put(changed.getKey(), Math.addExact(changed.getValue(), 23L));
        var rebuilt = new CraftingPlan(original.finalOutput(), original.bytes(), false, original.multiplePaths(),
            original.usedItems(), new KeyCounter(), original.missingItems(), rebuiltTasks);

        ECOPlanningResultRegistry.register(rebuilt, result);
        assertNull(ECOPlanningResultRegistry.find(rebuilt),
            "a rebuilt plan with a different execution vector must not be registered under the ECO result");
    }

    @Test void multipleAliasesFromOnePlanningResultDoNotCreateRecoveryAmbiguity() throws Exception {
        Fixture f = fixture(2, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.leaf, 1));
        var original = new AE2CraftingPlanBridge().success(
            PlannerTestKey.of("registry_alias_dedupe_unique"), 1, false, false, outcome.state());
        var result = new ECOPlanningResult(outcome.status(), original, outcome.trace(), outcome.cycles(),
            outcome.components(), outcome.executionComponentOrder(), 1L);
        ECOPlanningResultRegistry.register(original, result);

        Map<IPatternDetails, Long> firstAliasTasks = rebuiltPatternInstances(original.patternTimes(), "alias-one-");
        var firstAlias = new CraftingPlan(original.finalOutput(), original.bytes(), false, original.multiplePaths(),
            original.usedItems(), new KeyCounter(), original.missingItems(), firstAliasTasks);
        ECOPlanningResultRegistry.register(firstAlias, result);

        Map<IPatternDetails, Long> submittedTasks = rebuiltPatternInstances(original.patternTimes(), "submitted-");
        var submitted = new CraftingPlan(original.finalOutput(), original.bytes(), false, original.multiplePaths(),
            original.usedItems(), new KeyCounter(), original.missingItems(), submittedTasks);
        var recovered = ECOPlanningResultRegistry.recoverExecutionMetadata(submitted);

        assertNotNull(recovered, "aliases of one planning attempt must collapse to one recovery candidate");
        assertEquals(ECOPlanningResultRegistry.RecoveryState.VALID_SCHEDULE, recovered.state());
        assertEquals("strict-plan-identity", recovered.matchMode());
        assertTrue(ECOPhaseScheduler.requiresComponentScheduling(recovered.schedule()));
    }

    @Test void nonAliasPathRecoversFailClosedCycleExpectationWithoutValidSchedule() throws Exception {
        Fixture f = fixture(1, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.leaf, 1));
        var executable = new AE2CraftingPlanBridge().success(
            PlannerTestKey.of("registry_fail_closed_unique"), 1, false, false, outcome.state());
        int cycleComponentId = outcome.components().stream()
            .filter(component -> component.type()
                == cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult.Type.CYCLIC)
            .findFirst().orElseThrow().componentId();
        List<Integer> brokenExecutionOrder = List.of(cycleComponentId);
        var brokenResult = new ECOPlanningResult(PlanningStatus.SUCCESS, executable, outcome.trace(),
            outcome.cycles(), outcome.components(), brokenExecutionOrder, 1L);

        assertThrows(IllegalStateException.class, brokenResult::executionSchedule,
            "the fixture must exercise the SCHEDULE_BUILD_FAILED registration path");
        ECOPlanningResultRegistry.register(executable, brokenResult);
        var copied = new CraftingPlan(executable.finalOutput(), executable.bytes(), false,
            executable.multiplePaths(), executable.usedItems(), new KeyCounter(), executable.missingItems(),
            rebuiltPatternInstances(executable.patternTimes(), "fail-closed-copy-"));
        var recovered = ECOPlanningResultRegistry.recoverExecutionMetadata(copied);

        assertNotNull(recovered);
        assertTrue(recovered.cycleExpected());
        assertEquals(ECOPlanningResultRegistry.RecoveryState.MISSING_OR_INVALID_SCHEDULE, recovered.state());
        assertTrue(recovered.rejectionReason().startsWith("SCHEDULE_BUILD_FAILED:"));
        assertNull(recovered.schedule());
    }

    @Test void nativeFallbackPlanContainingKnownCycleIsRegisteredFailClosed() throws Exception {
        Fixture f = fixture(1, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.leaf, 1));
        var nativePlan = new AE2CraftingPlanBridge().success(
            PlannerTestKey.of("native_fallback_cycle_unique"), 1, false, false, outcome.state());
        var fallbackDiagnostic = new ECOPlanningResult(PlanningStatus.PARTIAL_UNSUPPORTED, null,
            outcome.trace(), outcome.cycles(), outcome.components(), outcome.executionComponentOrder(), 1L);

        assertFalse(ECOPlanningResultRegistry.cycleExpected(fallbackDiagnostic));
        assertFalse(ECOPlanningResultRegistry.cycleSafetyRequired(nativePlan, fallbackDiagnostic),
            "a result without an executable plan cannot transfer cycle metadata to a native candidate");
        ECOPlanningResultRegistry.register(nativePlan, fallbackDiagnostic);
        var copied = new CraftingPlan(nativePlan.finalOutput(), nativePlan.bytes(), false,
            nativePlan.multiplePaths(), nativePlan.usedItems(), new KeyCounter(), nativePlan.missingItems(),
            nativePlan.patternTimes());
        var recovered = ECOPlanningResultRegistry.recoverExecutionMetadata(copied);

        assertNull(recovered, "an unrelated native candidate must not inherit ECO cycle metadata");
    }

    private static Map<IPatternDetails, Long> rebuiltPatternInstances(
            Map<IPatternDetails, Long> source, String prefix) {
        Map<IPatternDetails, Long> rebuilt = new LinkedHashMap<>();
        source.forEach((pattern, count) -> rebuilt.put(new PlannerFixtures.Pattern(
            prefix + pattern, pattern.getInputs(), pattern.getOutputs()), count));
        return rebuilt;
    }

    @Test void equalConfirmedSignaturesRemainIsolatedAcrossConcurrentSubmissions() throws Exception {
        Fixture f = fixture(1, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.leaf, 1));
        var bridge = new AE2CraftingPlanBridge();
        var firstExecutable = bridge.success(f.network.goal(), 1, false, false, outcome.state());
        var firstResult = new ECOPlanningResult(outcome.status(), firstExecutable, outcome.trace(), outcome.cycles(),
            outcome.components(), outcome.executionComponentOrder(), 1L);

        Map<IPatternDetails, Long> secondTasks = new LinkedHashMap<>(firstExecutable.patternTimes());
        var changed = secondTasks.entrySet().iterator().next();
        secondTasks.put(changed.getKey(), Math.addExact(changed.getValue(), 11L));
        var secondExecutable = new CraftingPlan(firstExecutable.finalOutput(), firstExecutable.bytes(), false,
            firstExecutable.multiplePaths(), firstExecutable.usedItems(), new KeyCounter(),
            firstExecutable.missingItems(), secondTasks);
        var secondResult = new ECOPlanningResult(outcome.status(), secondExecutable, outcome.trace(), outcome.cycles(),
            outcome.components(), outcome.executionComponentOrder(), 1L);

        Map<IPatternDetails, Long> confirmedTasks = new LinkedHashMap<>(firstExecutable.patternTimes());
        var confirmedChanged = confirmedTasks.entrySet().iterator().next();
        confirmedTasks.put(confirmedChanged.getKey(), Math.addExact(confirmedChanged.getValue(), 3L));
        var confirmed = new CraftingPlan(firstExecutable.finalOutput(), firstExecutable.bytes(), false,
            firstExecutable.multiplePaths(), firstExecutable.usedItems(), new KeyCounter(),
            firstExecutable.missingItems(), confirmedTasks);
        var submittedCopy = new CraftingPlan(confirmed.finalOutput(), confirmed.bytes(), false,
            confirmed.multiplePaths(), confirmed.usedItems(), new KeyCounter(), confirmed.missingItems(),
            Map.copyOf(confirmed.patternTimes()));

        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> ECOPlanningResultRegistry.withSubmissionAlias(
                confirmed, firstResult, () -> awaitAndResolve(entered, release, submittedCopy)));
            var second = executor.submit(() -> ECOPlanningResultRegistry.withSubmissionAlias(
                confirmed, secondResult, () -> awaitAndResolve(entered, release, submittedCopy)));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            release.countDown();
            assertSame(submittedCopy, first.get(5, TimeUnit.SECONDS));
            assertSame(submittedCopy, second.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test void backgroundSameOutputCycleCandidateNeverReplacesUnconfirmedSubmission() throws Exception {
        Fixture f = fixture(3, false, false);
        var outcome = plan(f.network, stock(f.a, 1, f.leaf, 1));
        var bridge = new AE2CraftingPlanBridge();
        var executable = bridge.success(f.network.goal(), 1, false, false, outcome.state());
        var result = new ECOPlanningResult(outcome.status(), executable, outcome.trace(), outcome.cycles(),
            outcome.components(), outcome.executionComponentOrder(), 1L);
        ECOPlanningResultRegistry.register(executable, result);

        Map<IPatternDetails, Long> unrelatedTasks = new LinkedHashMap<>(executable.patternTimes());
        var changed = unrelatedTasks.entrySet().iterator().next();
        unrelatedTasks.put(changed.getKey(), Math.addExact(changed.getValue(), 1L));
        var submitted = new CraftingPlan(executable.finalOutput(), executable.bytes(), false,
            executable.multiplePaths(), executable.usedItems(), new KeyCounter(), executable.missingItems(),
            unrelatedTasks);
        assertSame(submitted, ECOPlanningResultRegistry.resolveSubmissionPlan(submitted),
            "planner registry candidates alone must never overwrite a submitted task vector");
    }

    private static CraftingPlan awaitAndResolve(CountDownLatch entered, CountDownLatch release,
            CraftingPlan submittedPlan) {
        entered.countDown();
        try {
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("concurrent submission did not enter its binding scope");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("concurrent submission was interrupted", e);
        }
        return (CraftingPlan) ECOPlanningResultRegistry.resolveSubmissionPlan(submittedPlan);
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
        return plan(network, inventory, 1);
    }

    private static ComponentPlanner.Outcome plan(CompiledNetwork network, KeyCounter inventory, long amount)
            throws Exception {
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        return new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, inventory, amount, true, ECOCancellation.NONE);
    }
    private static cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult cycle(ComponentPlanner.Outcome outcome) {
        return outcome.components().stream().filter(c -> c.type() == cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult.Type.CYCLIC).findFirst().orElseThrow();
    }
    private static KeyCounter stock(Object... pairs) { KeyCounter c = new KeyCounter(); for (int i=0;i<pairs.length;i+=2)c.add((AEKey)pairs[i],((Number)pairs[i+1]).longValue()); return c; }
    private static CompiledPattern compiled(int id, PlannerFixtures.Pattern p, AEKey output) { return PlannerFixtures.compiled(id,p,output,true,""); }
    private static PlannerTestKey key(String name) { return PlannerTestKey.of(name); }
}
