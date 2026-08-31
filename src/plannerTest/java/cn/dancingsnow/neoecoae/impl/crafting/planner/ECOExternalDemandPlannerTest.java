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
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
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

    @Test void simulatedOrNonSuccessfulCyclePlanIsNeverRegisteredForExecution() throws Exception {
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
        assertNull(ECOPlanningResultRegistry.find(executable));
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
        assertEquals("rebound-output-signature", recovered.matchMode());
        assertTrue(recovered.schedule().phases().stream().flatMap(phase -> phase.patternSet().stream())
            .allMatch(patchedTasks::containsKey));
        assertTrue(recovered.schedule().phases().stream().flatMap(phase -> phase.cycleWitness().stream())
            .allMatch(patchedTasks::containsKey));
    }

    @Test void largerBatchAlternateDagProducerRebindsByEqualTotalProduction() throws Exception {
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
        assertNotNull(recovered);
        assertEquals("rebound-output-signature", recovered.matchMode());
        assertTrue(recovered.schedule().phases().stream().flatMap(phase -> phase.patternSet().stream())
            .anyMatch(pattern -> pattern == replacement));
    }

    @Test void confirmedTransformedPlanRestoresCompleteExecutableCyclePlan() throws Exception {
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
        assertSame(executable, ECOPlanningResultRegistry.withSubmissionAlias(confirmed, result, () -> {
            assertSame(executable, ECOPlanningResultRegistry.resolveSubmissionPlan(submittedCopy));
            return ECOPlanningResultRegistry.resolveSubmissionPlan(submittedCopy);
        }));
        assertSame(submittedCopy, ECOPlanningResultRegistry.resolveSubmissionPlan(submittedCopy),
            "the confirmation binding must not escape its synchronous submitJob call");
        assertSame(executable, ECOPlanningResultRegistry.withSubmissionAlias(confirmed, result,
            () -> ECOPlanningResultRegistry.resolveSubmissionPlan(submittedCopy)),
            "a rejected submission must be recoverable in a later confirmation retry");
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
            assertSame(firstExecutable, first.get(5, TimeUnit.SECONDS));
            assertSame(secondExecutable, second.get(5, TimeUnit.SECONDS));
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
