package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.growth.SinglePatternGrowthCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.AE2PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.SpecialPatternAnalysis;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.SpecialPatternAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ComponentPlanner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.SpecialPatternResolver;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class SpecialPatternPlanningTest {
    @Test
    void reusableCatalystIsReservedOnceAndExcludedFromScc() throws Exception {
        AEKey goal = PlannerTestKey.of("special_goal");
        AEKey catalyst = PlannerTestKey.of("special_catalyst");
        var pattern = new ReturningPattern("reusable-catalyst", goal, catalyst, catalyst);
        var analyzer = new SpecialPatternAnalyzer(input -> new SpecialPatternAnalysis.Requirement(
            input, input.remainderKey(), SpecialPatternAnalysis.Type.REUSABLE, 0, 0));
        var network = new CraftingNetworkCompiler(
            List.of(new AE2PatternSemanticAdapter(catalyst::equals)), analyzer).compile(
                service(Map.of(goal, List.of(pattern))), goal, true, ECOCancellation.NONE);
        var compiled = network.producersOf(goal).getFirst();

        assertTrue(compiled.specialAnalysis().special());
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        assertTrue(graph.edges().isEmpty());
        var sccs = new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE);
        assertFalse(sccs.stream().anyMatch(component -> component.cyclic()));

        var outcome = solve(network, stock(catalyst, 1), 100);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertEquals(1L, outcome.state().usedItems().get(catalyst));
        assertEquals(100L, outcome.state().patternTimes().get(pattern));
    }

    @Test
    void exactReusableAlternativeInStockIsPreferredOverMutatingPrimary() throws Exception {
        AEKey goal = PlannerTestKey.of("preferred_reusable_goal");
        AEKey ordinary = PlannerTestKey.of("ordinary_crystal");
        AEKey damaged = PlannerTestKey.of("damaged_crystal");
        AEKey master = PlannerTestKey.of("master_crystal");
        var pattern = new AlternativeReturningPattern(goal, ordinary, damaged, master);
        var analyzer = new SpecialPatternAnalyzer(input -> new SpecialPatternAnalysis.Requirement(
            input, input.remainderKey(), SpecialPatternAnalysis.Type.REUSABLE, 0, 0));
        var network = new CraftingNetworkCompiler(
            List.of(new AE2PatternSemanticAdapter(master::equals)), analyzer).compile(
                service(Map.of(goal, List.of(pattern))), goal, true, ECOCancellation.NONE);
        KeyCounter inventory = stock(ordinary, 5);
        inventory.add(master, 1);

        assertEquals(ordinary, network.producersOf(goal).getFirst().inputs().getFirst().key());
        var outcome = solve(network, inventory, 5_000);

        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertEquals(1L, outcome.state().usedItems().get(master));
        assertEquals(0L, outcome.state().usedItems().get(ordinary));
        assertEquals(5_000L, outcome.state().patternTimes().get(pattern));
    }

    @Test
    void missingReusableAlternativeFallsBackToCraftablePrimary() throws Exception {
        AEKey goal = PlannerTestKey.of("fallback_primary_goal");
        AEKey ordinary = PlannerTestKey.of("fallback_ordinary_crystal");
        AEKey damaged = PlannerTestKey.of("fallback_damaged_crystal");
        AEKey master = PlannerTestKey.of("fallback_master_crystal");
        AEKey raw = PlannerTestKey.of("fallback_crystal_material");
        var consumer = new AlternativeReturningPattern(goal, ordinary, damaged, master);
        var ordinaryProducer = PlannerFixtures.pattern("ordinary-crystal-producer", ordinary, 1, raw, 1L);
        var analyzer = new SpecialPatternAnalyzer(input -> new SpecialPatternAnalysis.Requirement(
            input, input.remainderKey(), SpecialPatternAnalysis.Type.REUSABLE, 0, 0));
        var network = new CraftingNetworkCompiler(
            List.of(new AE2PatternSemanticAdapter(master::equals)), analyzer).compile(service(Map.of(
                goal, List.of(consumer), ordinary, List.of(ordinaryProducer))),
                goal, true, ECOCancellation.NONE);

        assertEquals(ordinary, network.producersOf(goal).getFirst().inputs().getFirst().key());
        var outcome = solve(network, stock(raw, 1), 5_000);

        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertEquals(1L, outcome.state().patternTimes().get(ordinaryProducer));
        assertEquals(5_000L, outcome.state().patternTimes().get(consumer));
        assertEquals(0L, outcome.state().usedItems().get(master));
    }

    @Test
    void durabilityCapacityRoundsUpOnlyWhenAnotherToolIsRequired() {
        assertEquals(PlannerAmount.of(1),
            SpecialPatternResolver.requiredTools(PlannerAmount.of(100), 100));
        assertEquals(PlannerAmount.of(2),
            SpecialPatternResolver.requiredTools(PlannerAmount.of(101), 100));
    }

    @Test
    void missingReusableStockIsManufacturedOnlyByTheLocalResolver() throws Exception {
        AEKey goal = PlannerTestKey.of("local_goal");
        AEKey catalyst = PlannerTestKey.of("local_catalyst");
        AEKey raw = PlannerTestKey.of("local_raw");
        var consumer = new ReturningPattern("local-consumer", goal, catalyst, catalyst);
        var toolProducer = PlannerFixtures.pattern("local-tool-producer", catalyst, 1, raw, 1L);
        var analyzer = new SpecialPatternAnalyzer(input -> new SpecialPatternAnalysis.Requirement(
            input, input.remainderKey(), SpecialPatternAnalysis.Type.REUSABLE, 0, 0));
        var network = new CraftingNetworkCompiler(
            List.of(new AE2PatternSemanticAdapter(catalyst::equals)), analyzer).compile(service(Map.of(
                goal, List.of(consumer), catalyst, List.of(toolProducer))),
                goal, true, ECOCancellation.NONE);

        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        assertEquals(1, graph.nodes().size());
        var outcome = solve(network, stock(raw, 1), 50);

        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertEquals(1L, outcome.state().patternTimes().get(toolProducer));
        assertEquals(50L, outcome.state().patternTimes().get(consumer));
        var schedule = ECOExecutionSchedule.from(outcome.components(), outcome.executionComponentOrder(),
            outcome.state().patternTimes());
        assertEquals(1, schedule.phases().size());
        assertEquals(2, schedule.phases().getFirst().patternSet().size());
    }

    private static ComponentPlanner.Outcome solve(
            cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork network,
            KeyCounter inventory, long amount) throws Exception {
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        return new ComponentPlanner(new AcyclicCraftingSolver(),
            SinglePatternGrowthCycleSolver.overBoundedSolver())
            .plan(network, condensation, inventory, amount, true, ECOCancellation.NONE);
    }

    private static KeyCounter stock(AEKey key, long amount) {
        KeyCounter result = new KeyCounter();
        result.add(key, amount);
        return result;
    }

    private static ICraftingService service(Map<AEKey, ? extends Collection<IPatternDetails>> patterns) {
        return (ICraftingService) Proxy.newProxyInstance(ICraftingService.class.getClassLoader(),
            new Class<?>[] {ICraftingService.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getCraftingFor" -> {
                    Collection<IPatternDetails> matching = patterns.get((AEKey) args[0]);
                    yield matching == null ? List.<IPatternDetails>of() : matching;
                }
                case "canEmitFor" -> false;
                case "toString" -> "SpecialPatternTestService";
                default -> method.getReturnType() == boolean.class ? false
                    : method.getReturnType() == long.class ? 0L
                    : method.getReturnType() == int.class ? 0 : null;
            });
    }

    private record ReturningPattern(String name, AEKey output, AEKey input, AEKey returned)
            implements IPatternDetails {
        @Override public appeng.api.stacks.AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() {
            return new IInput[] {new IInput() {
                @Override public GenericStack[] getPossibleInputs() {
                    return new GenericStack[] {new GenericStack(input, 1)};
                }
                @Override public long getMultiplier() { return 1; }
                @Override public boolean isValid(AEKey candidate, Level level) { return input.equals(candidate); }
                @Override public AEKey getRemainingKey(AEKey template) { return returned; }
            }};
        }
        @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(output, 1)); }
        @Override public String toString() { return name; }
    }

    private record AlternativeReturningPattern(AEKey output, AEKey ordinary, AEKey damaged, AEKey master)
            implements IPatternDetails {
        @Override public appeng.api.stacks.AEItemKey getDefinition() { return null; }
        @Override public IInput[] getInputs() {
            return new IInput[] {new IInput() {
                @Override public GenericStack[] getPossibleInputs() {
                    return new GenericStack[] {new GenericStack(ordinary, 1), new GenericStack(master, 1)};
                }
                @Override public long getMultiplier() { return 1; }
                @Override public boolean isValid(AEKey candidate, Level level) {
                    return ordinary.equals(candidate) || master.equals(candidate);
                }
                @Override public AEKey getRemainingKey(AEKey template) {
                    return ordinary.equals(template) ? damaged : master.equals(template) ? master : null;
                }
            }};
        }
        @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(output, 1)); }
    }
}
