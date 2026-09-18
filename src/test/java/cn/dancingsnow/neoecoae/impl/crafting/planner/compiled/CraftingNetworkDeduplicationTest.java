package cn.dancingsnow.neoecoae.impl.crafting.planner.compiled;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.AE2PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshotFactory;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot.CandidateStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ComponentPlanner;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CraftingNetworkDeduplicationTest {
    private final AEKey seed = mock(AEKey.class);

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void duplicateGrowthPatternsStayOneRouteThroughPlanningAndVisualization() throws Exception {
        when(seed.getAmountPerByte()).thenReturn(8);
        AEItemKey definition = definition("growth");
        IPatternDetails first = growthPattern(IPatternDetails.class, definition);
        IPatternDetails duplicate = growthPattern(IPatternDetails.class, definition("growth"));
        assertNotEquals(first, duplicate);

        CompiledNetwork network = compile(first, duplicate);
        assertEquals(1, network.reachablePatternCount());
        assertEquals(1, network.edgeCount());
        assertFalse(network.multiplePaths());
        assertSame(first, network.producersOf(seed).getFirst().details());
        assertEquals(0, network.producersOf(seed).getFirst().id());

        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        KeyCounter inventory = new KeyCounter();
        inventory.add(seed, 1L);
        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, inventory, 8L, true, ECOCancellation.NONE);

        assertEquals(PlanningStatus.SUCCESS, outcome.status(), outcome.trace().diagnostics().toString());
        assertEquals(1, outcome.state().patternTimes().size());
        assertTrue(outcome.state().patternTimes().containsKey(first));
        var plan = new AE2CraftingPlanBridge().success(seed, 8L, false, network.multiplePaths(), outcome.state());
        var result = new ECOPlanningResult(outcome.status(), plan, outcome.trace(), outcome.cycles(),
            outcome.components(), outcome.executionComponentOrder(), 0L);
        var snapshot = CraftingGraphSnapshotFactory.create(result);
        assertEquals(1, snapshot.patterns().size());
        assertEquals(CandidateStatus.SELECTED, snapshot.patterns().getFirst().status());
        assertEquals(outcome.state().patternTimes().get(first).longValue(),
            snapshot.patterns().getFirst().firingCount());
        assertEquals(1, snapshot.cycleGroups().size());
        assertEquals(2, snapshot.edges().size());
        assertEquals(1, snapshot.cycleGroups().getFirst().patternTimes().size());
    }

    @Test
    void differentEncodedDefinitionsRemainSeparateEvenWithTheSameInputsAndOutputs() throws Exception {
        var network = compile(growthPattern(IPatternDetails.class, definition("first")),
            growthPattern(IPatternDetails.class, definition("second")));
        assertEquals(2, network.reachablePatternCount());
        assertTrue(network.multiplePaths());
        assertEquals(List.of(0, 1), network.producersOf(seed).stream().map(pattern -> pattern.id()).toList());
    }

    @Test
    void differentImplementationsSharingAnEncodedDefinitionRemainSeparate() throws Exception {
        AEItemKey definition = definition("growth");
        var network = compile(growthPattern(IPatternDetails.class, definition),
            growthPattern(WrappedPattern.class, definition));
        assertEquals(2, network.reachablePatternCount());
    }

    @Test
    void absentDefinitionsDoNotCollapseUnrelatedPatterns() throws Exception {
        var network = compile(growthPattern(IPatternDetails.class, null),
            growthPattern(IPatternDetails.class, null));
        assertEquals(2, network.reachablePatternCount());
    }

    @Test
    void unreadableDefinitionsDoNotAbortCompilationOrCollapsePatterns() throws Exception {
        IPatternDetails first = growthPattern(IPatternDetails.class, null);
        IPatternDetails second = growthPattern(IPatternDetails.class, null);
        when(first.getDefinition()).thenThrow(new IllegalStateException("unavailable definition"));
        when(second.getDefinition()).thenThrow(new IllegalStateException("unavailable definition"));
        assertEquals(2, compile(first, second).reachablePatternCount());
    }

    private CompiledNetwork compile(IPatternDetails... patterns) throws Exception {
        ICraftingService service = mock(ICraftingService.class);
        when(service.getCraftingFor(seed)).thenReturn(List.of(patterns));
        // Isolated keys cannot pass the Minecraft-specific growth validator. Supply the explicit static contract.
        PatternSemanticAdapter adapter = new PatternSemanticAdapter() {
            @Override public boolean supports(IPatternDetails pattern) { return true; }
            @Override public PatternSemantics analyze(IPatternDetails pattern) {
                var semantics = new AE2PatternSemanticAdapter().analyze(pattern);
                return new PatternSemantics(pattern, semantics.physicalDefinition(), semantics.consumedInputs(),
                    semantics.producedOutputs(), semantics.returnedOutputs(), semantics.feedbackEdges(),
                    semantics.matchingMode(), semantics.executionRestriction(), semantics.exactStaticAnalysis(),
                    true, semantics.unsupportedReason());
            }
        };
        return new CraftingNetworkCompiler(List.of(adapter)).compile(service, seed, true, ECOCancellation.NONE);
    }

    private <T extends IPatternDetails> T growthPattern(Class<T> type, AEItemKey definition) {
        T pattern = mock(type);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(seed, 1L) });
        when(input.getMultiplier()).thenReturn(1L);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] { input });
        var output = new GenericStack(seed, 2L);
        when(pattern.getOutputs()).thenReturn(List.of(output));
        when(pattern.getPrimaryOutput()).thenReturn(output);
        when(pattern.getDefinition()).thenReturn(definition);
        return pattern;
    }

    private static AEItemKey definition(String name) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return AEItemKey.of(stack);
    }

    private interface WrappedPattern extends IPatternDetails {}
}
