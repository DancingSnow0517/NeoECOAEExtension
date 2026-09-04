package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.me.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.impl.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.SccComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.growth.SinglePatternGrowthCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.AE2PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ECOPlanMaterialValidator;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ComponentPlanner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import cn.dancingsnow.neoecoae.compat.useless.UselessDynamicPatternView;
import com.moakiee.thunderbolt.ae2.overload.model.MatchMode;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadPatternDetails;
import com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.thunderbolt.ae2.overload.pattern.PatternExecutionHostKind;
import com.moakiee.thunderbolt.ae2.overload.pattern.WrappedPatternDetails;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ECOPlannerSemanticContractTest {
    @Test
    void uselessRelaxedInputCommitsToAConcreteStaticCandidate() throws Exception {
        AEKey input = PlannerTestKey.of("useless_relaxed_input");
        AEKey goal = PlannerTestKey.of("useless_relaxed_goal");
        var pattern = new UselessDynamicPattern(goal, input, true, false);

        CompiledNetwork network = new CraftingNetworkCompiler().compile(
            service(Map.of(goal, List.of(pattern))), goal, true, ECOCancellation.NONE);
        CompiledPattern compiled = network.producersOf(goal).getFirst();

        assertTrue(compiled.fastSupported(), compiled::unsupportedReason);
        assertEquals(PatternSemantics.MatchingMode.SUBSTITUTION, compiled.semantics().matchingMode());
        assertEquals(input, compiled.inputs().getFirst().key());
    }

    @Test
    void uselessDynamicOutputCannotEnterExactCycleAlgebra() throws Exception {
        AEKey input = PlannerTestKey.of("useless_dynamic_input");
        AEKey goal = PlannerTestKey.of("useless_dynamic_goal");
        var pattern = new UselessDynamicPattern(goal, input, false, true);

        CompiledNetwork network = new CraftingNetworkCompiler().compile(
            service(Map.of(goal, List.of(pattern))), goal, true, ECOCancellation.NONE);
        CompiledPattern compiled = network.producersOf(goal).getFirst();

        assertFalse(compiled.fastSupported());
        assertEquals("USELESS_DYNAMIC_OUTPUT_NOT_STATIC", compiled.unsupportedReason());
    }

    @Test
    void reusableAlternativeDoesNotReplaceTheCompiledPrimaryDemand() {
        AEKey goal = PlannerTestKey.of("reusable_stock_goal");
        AEKey damageableCrystal = PlannerTestKey.of("damageable_crystal");
        AEKey masterCrystal = PlannerTestKey.of("master_crystal");
        var pattern = new AE2ReusablePattern("master-crystal", goal, damageableCrystal, masterCrystal);

        PatternSemantics semantics = new AE2PatternSemanticAdapter(masterCrystal::equals).analyze(pattern);

        assertFalse(semantics.cycleSafeForStaticPlanning());
        assertEquals(damageableCrystal, semantics.consumedInputs().getFirst().key());
    }

    @Test
    void damageableSelfRemainderStillDeclinesFastPlanning() throws Exception {
        AEKey goal = PlannerTestKey.of("damageable_remainder_goal");
        AEKey damageableCrystal = PlannerTestKey.of("damageable_crystal");
        var pattern = new AE2ReusablePattern("damageable-crystal", goal, damageableCrystal);

        CompiledNetwork network = new CraftingNetworkCompiler(List.of(
            new AE2PatternSemanticAdapter(key -> false))).compile(
            service(Map.of(goal, List.of(pattern))), goal, true, ECOCancellation.NONE);
        var compiled = network.producersOf(goal).getFirst();

        assertFalse(compiled.fastSupported());
        assertFalse(compiled.semantics().cycleSafeForStaticPlanning());
        assertEquals("UNSUPPORTED_REMAINDER", compiled.unsupportedReason());
    }

    @Test
    void sameOutputDifferentPlannerCandidatesNeverCrossWire() throws Exception {
        AEKey goal = PlannerTestKey.of("candidate_binding_goal");
        AEKey input = PlannerTestKey.of("candidate_binding_input");
        var ecoPattern = PlannerFixtures.pattern("eco_candidate", goal, 1, input, 1L);
        var ecoPlan = new CraftingPlan(new GenericStack(goal, 1), 1, false, false,
            counter(input, 1), new KeyCounter(), new KeyCounter(), Map.of(ecoPattern, 2L));
        var ecoResult = new ECOPlanningResult(PlanningStatus.SUCCESS, ecoPlan,
            new cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace(), List.of(), List.of(), List.of(), 1L);
        // The other planner selected the same final output but a different physical task vector.
        var thunderPattern = PlannerFixtures.pattern("thunder_candidate", goal, 1, input, 1L);
        var thunderPlan = new CraftingPlan(new GenericStack(goal, 1), 1, false, false,
            counter(input, 1), new KeyCounter(), new KeyCounter(), Map.of(thunderPattern, 3L));
        assertNotEquals(ecoPlan.patternTimes(), thunderPlan.patternTimes());
        assertSame(thunderPlan, ECOPlanningResultRegistry.withSubmissionAlias(thunderPlan, ecoResult, () -> {
            assertSame(thunderPlan, ECOPlanningResultRegistry.resolveSubmissionPlan(thunderPlan));
            assertNull(ECOPlanningResultRegistry.activeSubmissionMetadata(thunderPlan));
            return thunderPlan;
        }));
        assertEquals(3L, taskExecutions(thunderPlan));
    }

    @Test
    void thunderIdOnlySlotsCommitToStaticTemplatesAndRemainPlannable() throws Exception {
        AEKey goal = PlannerTestKey.of("thunder_id_only_goal");
        AEKey input = PlannerTestKey.of("thunder_id_only_input");
        IPatternDetails pattern = overloadPattern("thunder-id-only", goal, input,
            MatchMode.ID_ONLY, MatchMode.ID_ONLY);
        CompiledNetwork network = new CraftingNetworkCompiler().compile(
            service(Map.of(goal, List.of(pattern))), goal, true, ECOCancellation.NONE);
        CompiledPattern compiled = network.producersOf(goal).getFirst();

        assertTrue(compiled.fastSupported(), compiled::unsupportedReason);
        assertEquals(PatternSemantics.MatchingMode.SUBSTITUTION, compiled.semantics().matchingMode());
        assertEquals(input, compiled.inputs().getFirst().key());
        assertFalse(compiled.semantics().cycleSafeForStaticPlanning());
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(),
            SinglePatternGrowthCycleSolver.overBoundedSolver())
            .plan(network, condensation, counter(input, 1), 1, true, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
    }

    @Test
    void thunderStrictPatternUsesExactStaticSemantics() throws Exception {
        AEKey goal = PlannerTestKey.of("thunder_strict_goal");
        AEKey input = PlannerTestKey.of("thunder_strict_input");
        IPatternDetails pattern = overloadPattern("thunder-strict", goal, input,
            MatchMode.STRICT, MatchMode.STRICT);

        CompiledPattern compiled = new CraftingNetworkCompiler().compile(
            service(Map.of(goal, List.of(pattern))), goal, true, ECOCancellation.NONE)
            .producersOf(goal).getFirst();

        assertTrue(compiled.fastSupported(), compiled::unsupportedReason);
        assertEquals(PatternSemantics.MatchingMode.EXACT, compiled.semantics().matchingMode());
        assertEquals(PatternSemantics.ExecutionRestriction.NONE,
            compiled.semantics().executionRestriction());
    }

    @Test
    void unknownThunderContractStillCannotFallThroughToPlainAe2Semantics() throws Exception {
        AEKey goal = PlannerTestKey.of("thunder_unknown_goal");
        AEKey input = PlannerTestKey.of("thunder_unknown_input");
        IPatternDetails pattern = new ThunderUnknownPattern(goal, input);

        CompiledPattern compiled = new CraftingNetworkCompiler().compile(
            service(Map.of(goal, List.of(pattern))), goal, true, ECOCancellation.NONE)
            .producersOf(goal).getFirst();

        assertFalse(compiled.fastSupported());
        assertEquals("THUNDER_UNSUPPORTED_SEMANTICS", compiled.unsupportedReason());
    }

    @Test
    void patchedDagProducerIsOrderedBeforeConsumerFromNormalizedSemantics() {
        AEKey dust = PlannerTestKey.of("energized_crystal_dust");
        AEKey goal = PlannerTestKey.of("omniversal_pattern");
        var producer = PlannerFixtures.pattern("producer", dust, 1);
        var consumer = PlannerFixtures.pattern("consumer", goal, 1, dust, 1L);
        var patchedProducer = PlannerFixtures.pattern("patched-producer", dust, 1);
        var patchedConsumer = PlannerFixtures.pattern("patched-consumer", goal, 1, dust, 1L);
        var producerComponent = component(2, Map.of(dust, 1L), Set.of(producer));
        var consumerComponent = component(1, Map.of(goal, 1L), Set.of(consumer));

        var schedule = ECOExecutionSchedule.from(List.of(consumerComponent, producerComponent), List.of(1, 2),
            Map.of(patchedConsumer, 1L, patchedProducer, 1L));
        assertEquals(List.of(2, 1), schedule.phases().stream()
            .map(ECOExecutionSchedule.ComponentExecutionPhase::componentId).toList());
        assertEquals(Set.of(patchedProducer), schedule.phases().getFirst().patternSet());
    }

    @Test
    void omittedRouteInputCannotRemainAClosedSuccessfulPlan() throws Exception {
        AEKey input = PlannerTestKey.of("closure_input");
        AEKey goal = PlannerTestKey.of("closure_goal");
        var pattern = PlannerFixtures.pattern("closure_goal", goal, 1, input, 1L);
        CompiledNetwork network = PlannerFixtures.network(goal, new LinkedHashMap<>(Map.of(
            input, List.of(), goal, List.of(PlannerFixtures.compiled(0, pattern, goal, true, "")))));

        // This deliberately malformed route mirrors the failure mode: the solver can only be considered closed
        // after every input it discovers has been included in the route.
        var outcome = new AcyclicCraftingSolver().solve(network,
            new cn.dancingsnow.neoecoae.impl.crafting.planner.route.AcyclicRoutePlan(List.of(goal)),
            new KeyCounter(), 1, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());

        var issue = ECOPlanMaterialValidator.firstDeficit(outcome.state(), goal, 1);
        assertNotNull(issue);
        assertEquals(input, issue.key());
        assertEquals("MATERIAL_DEFICIT", issue.reason());
    }

    @Test
    void materiallyClosedPlanPassesTheExecutionContract() throws Exception {
        AEKey input = PlannerTestKey.of("closed_input");
        AEKey goal = PlannerTestKey.of("closed_goal");
        var pattern = PlannerFixtures.pattern("closed_goal", goal, 1, input, 1L);
        CompiledNetwork network = PlannerFixtures.network(goal, new LinkedHashMap<>(Map.of(
            input, List.of(), goal, List.of(PlannerFixtures.compiled(0, pattern, goal, true, "")))));
        var outcome = new AcyclicCraftingSolver().solve(network,
            new cn.dancingsnow.neoecoae.impl.crafting.planner.route.AcyclicRoutePlan(List.of(goal, input)),
            counter(input, 1), 1, ECOCancellation.NONE);

        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertNull(ECOPlanMaterialValidator.firstDeficit(outcome.state(), goal, 1));
    }

    private static ComponentPlanningResult component(int id, Map<AEKey, Long> requiredOutputs,
            Set<IPatternDetails> patterns) {
        return new ComponentPlanningResult(id, ComponentPlanningResult.Type.ACYCLIC,
            ComponentPlanningResult.Status.PLANNED, requiredOutputs, patterns, patterns,
            null, null, Map.of(), null, null,
            cn.dancingsnow.neoecoae.impl.crafting.planner.result.CycleExecutionDisposition.NOT_REQUIRED, Map.of());
    }

    private static KeyCounter counter(AEKey key, long amount) {
        KeyCounter result = new KeyCounter();
        result.add(key, amount);
        return result;
    }

    private static long taskExecutions(CraftingPlan plan) {
        return plan.patternTimes().values().stream().mapToLong(Long::longValue).sum();
    }

    private static ICraftingService service(Map<AEKey, ? extends Collection<IPatternDetails>> patterns) {
        return (ICraftingService) Proxy.newProxyInstance(ICraftingService.class.getClassLoader(),
            new Class<?>[] {ICraftingService.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getCraftingFor" -> {
                    Collection<IPatternDetails> matching = patterns.get((AEKey) args[0]);
                    yield matching == null ? List.<IPatternDetails>of() : matching;
                }
                case "canEmitFor" -> false;
                case "toString" -> "SemanticContractTestService";
                default -> method.getReturnType() == boolean.class ? false
                    : method.getReturnType() == long.class ? 0L
                    : method.getReturnType() == int.class ? 0 : null;
            });
    }

    private static final class AE2ReusablePattern implements IPatternDetails {
        private final String name;
        private final AEKey output;
        private final AEKey[] possibleInputs;

        private AE2ReusablePattern(String name, AEKey output, AEKey... possibleInputs) {
            this.name = name;
            this.output = output;
            this.possibleInputs = possibleInputs;
        }

        @Override public appeng.api.stacks.AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() {
            return new IInput[] {new IInput() {
                @Override public GenericStack[] getPossibleInputs() {
                    return java.util.Arrays.stream(possibleInputs)
                        .map(key -> new GenericStack(key, 1)).toArray(GenericStack[]::new);
                }
                @Override public long getMultiplier() { return 1; }
                @Override public boolean isValid(AEKey input, Level level) {
                    return java.util.Arrays.asList(possibleInputs).contains(input);
                }
                @Override public AEKey getRemainingKey(AEKey template) { return template; }
            }};
        }
        @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(output, 1)); }
        @Override public String toString() { return name; }
    }

    private static final class ThunderUnknownPattern implements IPatternDetails {
        private final IPatternDetails source;

        private ThunderUnknownPattern(AEKey output, AEKey input) {
            source = PlannerFixtures.pattern("thunder-unknown", output, 1, input, 1L);
        }

        @Override public appeng.api.stacks.AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return source.getInputs(); }
        @Override public List<GenericStack> getOutputs() { return source.getOutputs(); }
    }

    private static IPatternDetails overloadPattern(String name, AEKey output, AEKey input,
            MatchMode inputMode, MatchMode outputMode) {
        return new DirectThunderPattern(PlannerFixtures.pattern(name, output, 1, input, 1L),
            inputMode, outputMode);
    }

    private static final class DirectThunderPattern implements IPatternDetails,
            OverloadedProviderOnlyPatternDetails, WrappedPatternDetails {
        private final IPatternDetails source;
        private final MatchMode inputMode;
        private final MatchMode outputMode;

        private DirectThunderPattern(IPatternDetails source, MatchMode inputMode, MatchMode outputMode) {
            this.source = source;
            this.inputMode = inputMode;
            this.outputMode = outputMode;
        }

        @Override public appeng.api.stacks.AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return source.getInputs(); }
        @Override public List<GenericStack> getOutputs() { return source.getOutputs(); }
        @Override public PatternExecutionHostKind requiredHostKind() { return null; }
        @Override public String overloadPatternIdentity() { return "planner-test-overload"; }
        @Override public OverloadPatternDetails overloadPatternDetailsView() { return null; }
        @Override public boolean hasFuzzyInputs() { return inputMode == MatchMode.ID_ONLY; }
        @Override public boolean isFuzzyInput(int slot) { return inputMode == MatchMode.ID_ONLY; }
        @Override public boolean isFuzzyOutput(int slot) { return outputMode == MatchMode.ID_ONLY; }
        @Override public IPatternDetails wrappedPatternDetails() { return source; }
    }

    public static final class UselessDynamicPattern implements IPatternDetails, UselessDynamicPatternView {
        private final PlannerFixtures.Pattern delegate;
        private final boolean relaxedInput;
        private final boolean dynamicOutput;

        private UselessDynamicPattern(AEKey output, AEKey input, boolean relaxedInput, boolean dynamicOutput) {
            this.delegate = PlannerFixtures.pattern("useless-dynamic", output, 1, input, 1L);
            this.relaxedInput = relaxedInput;
            this.dynamicOutput = dynamicOutput;
        }

        @Override public boolean neoecoae$isItemIdInput(int slot) { return relaxedInput && slot == 0; }
        @Override public boolean neoecoae$isTagInput(int slot) { return false; }
        @Override public boolean neoecoae$isFluidTagInput(int slot) { return false; }
        @Override public boolean neoecoae$usesDynamicOutputs() { return dynamicOutput; }
        @Override public appeng.api.stacks.AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() { return delegate.getInputs(); }
        @Override public List<GenericStack> getOutputs() { return delegate.getOutputs(); }
    }
}
