package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveDiagnostic;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.AE2PatternSemanticAdapter;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real AE2CS ratios, read through the public AE2 pattern contract rather than pre-certified test semantics. */
class OverloadCrystalCyclePlanningTest {
    private final AEKey dust = key("ae2lt:overload_crystal_dust");
    private final AEKey crystal = key("ae2cs:purified_overload_crystal");
    private final AEKey seed = key("ae2cs:overload_crystal_seed");
    private final AEKey fluix = key("ae2:fluix_dust");
    private final AEKey charged = key("ae2:charged_certus_quartz_crystal");
    private final AEKey lightning = key("ae2lt:high_voltage_lightning");

    @ParameterizedTest
    @CsvSource({"0,500", "1,5000", "7,5000"})
    void reportedShortfallCompletesTheWholeOrderWhenSupplied(long crystals, long requested) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            var network = network();
            var inventory = stock(crystals);
            var missing = plan(network, inventory, requested);
            assertEquals(PlanningStatus.MISSING_ITEMS, missing.status(), missing.trace().diagnostics().toString());
            var cycle = missing.trace().cycles().getFirst().solveResult();
            assertTrue(cycle.diagnostics().stream().anyMatch(d ->
                d.code() == CycleSolveDiagnostic.Code.FULL_ORDER_MATERIAL_DEFICIT), cycle.summary());
            assertFalse(cycle.diagnostics().stream().anyMatch(d ->
                d.code() == CycleSolveDiagnostic.Code.SEED_ESTIMATE_LOWER_BOUND));
            assertFalse(missing.state().missingItems().isEmpty());
            assertEquals(8L - Math.max(1L, crystals), missing.state().missingItems().get(dust));
            assertEquals(crystals == 0L ? 1L : 0L, missing.state().missingItems().get(crystal));
            assertTrue(missing.state().patternTimes().isEmpty(), "Missing cycle must not be submitted");
            assertEquals(crystals, inventory.get(crystal), "Planning must not mutate the network inventory");
            var supplied = stock(crystals);
            missing.state().missingItems().forEach(entry -> supplied.add(entry.getKey(), entry.getLongValue()));
            var complete = plan(network, supplied, requested);
            assertEquals(PlanningStatus.SUCCESS, complete.status(), complete.trace().diagnostics().toString());
            assertTrue(complete.state().missingItems().isEmpty(), "Supplying the report must not reveal another seed deficit");
            complete.state().executionProvenance().requireComplete();
            assertFalse(new AE2CraftingPlanBridge().success(dust, requested, false, false, complete.state()).simulation());
        });
    }

    @ParameterizedTest
    @CsvSource({"8,5000", "8,1000000", "16,5000"})
    void sufficientStartupStockProducesAnExecutableGrowingCycle(long crystals, long requested) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            var result = plan(network(), stock(crystals), requested);
            assertEquals(PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());
            assertTrue(result.state().missingItems().isEmpty());
            assertEquals(3, result.state().patternTimes().size());
            var cycle = result.trace().cycles().getFirst().solveResult();
            assertTrue(cycle.deliverableOutputs().get(dust) >= requested);
            assertTrue(cycle.executionPlan().size() < 100, "Large orders must retain a compact firing order");
            result.state().executionProvenance().requireComplete();
        });
    }

    private CompiledNetwork network() throws Exception {
        var crush = pattern(new GenericStack(dust, 1), new GenericStack(crystal, 1));
        var makeSeeds = pattern(new GenericStack(seed, 32), new GenericStack(dust, 8),
            new GenericStack(fluix, 4), new GenericStack(charged, 4), new GenericStack(lightning, 4));
        var grow = pattern(new GenericStack(crystal, 1), new GenericStack(seed, 1));
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(dust)).thenReturn(List.of(crush));
        when(service.getCraftingFor(seed)).thenReturn(List.of(makeSeeds));
        when(service.getCraftingFor(crystal)).thenReturn(List.of(grow));
        return new CraftingNetworkCompiler(List.of(new AE2PatternSemanticAdapter()))
            .compile(service, dust, true, ECOCancellation.NONE);
    }

    private ComponentPlanner.Outcome plan(CompiledNetwork network, KeyCounter inventory, long requested) throws Exception {
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        return new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, inventory, requested, true, ECOCancellation.NONE);
    }

    private KeyCounter stock(long crystals) {
        var stock = new KeyCounter();
        stock.add(crystal, crystals);
        for (AEKey key : List.of(fluix, charged, lightning)) stock.add(key, Long.MAX_VALUE);
        return stock;
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    private static IPatternDetails pattern(GenericStack output, GenericStack... inputs) {
        var pattern = mock(IPatternDetails.class);
        when(pattern.getOutputs()).thenReturn(List.of(output));
        when(pattern.getPrimaryOutput()).thenReturn(output);
        var slots = new IPatternDetails.IInput[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            slots[i] = mock(IPatternDetails.IInput.class);
            when(slots[i].getPossibleInputs()).thenReturn(new GenericStack[] {inputs[i]});
            when(slots[i].getMultiplier()).thenReturn(1L);
        }
        when(pattern.getInputs()).thenReturn(slots);
        return pattern;
    }
}
