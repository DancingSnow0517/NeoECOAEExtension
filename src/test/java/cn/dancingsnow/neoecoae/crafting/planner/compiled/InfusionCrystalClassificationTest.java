package cn.dancingsnow.neoecoae.crafting.planner.compiled;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECORecipeClassifier;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.AE2PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemanticAdapter;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.SpecialPatternAnalysis;
import cn.dancingsnow.neoecoae.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.crafting.planner.solve.ActiveRouteSelector;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the public AE2 remainder contract used by Mystical Agriculture's infusion recipes. */
class InfusionCrystalClassificationTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void crystalIsSpecialWithoutMaterialCycleEvenWhenAdapterOmitsRemainderSummary(
            boolean durability, boolean omitSummary) throws Exception {
        var essence = AEItemKey.of(Items.COAL);
        var upgraded = AEItemKey.of(Items.DIAMOND);
        var crystalStack = new ItemStack(durability ? Items.IRON_PICKAXE : Items.EMERALD);
        var crystal = AEItemKey.of(crystalStack);
        var returnedStack = crystalStack.copy();
        if (durability) returnedStack.setDamageValue(1);
        var returned = AEItemKey.of(returnedStack);
        var recipe = pattern(upgraded, input(essence, 4, null), input(crystal, 1, returned));
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(upgraded)).thenReturn(List.of(recipe));
        // Obtaining a fresh crystal requires upgraded essence. Stored working stock must break this
        // apparent dependency without charging one crystal for every essence craft.
        var crystalRecipe = pattern(crystal, input(upgraded, 1, null));
        when(service.getCraftingFor(crystal)).thenReturn(List.of(crystalRecipe));
        var network = compiler(omitSummary).compile(service, upgraded, ECOCancellation.NONE);
        var compiled = network.producersOf(upgraded).getFirst();
        assertTrue(compiled.fastSupported(), compiled.unsupportedReason());
        assertEquals(1, compiled.specialAnalysis().requirements().size());
        assertEquals(durability ? SpecialPatternAnalysis.Type.DURABILITY : SpecialPatternAnalysis.Type.REUSABLE,
            compiled.specialAnalysis().requirements().getFirst().type());
        var classification = ECORecipeClassifier.classify(recipe);
        assertTrue(classification.supported(), classification.reason());
        assertEquals(durability ? ECORecipeClassifier.Type.DURABILITY_MUTATION
            : ECORecipeClassifier.Type.REUSABLE_COMPONENT, classification.type());
        assertAcyclic(network);

        long crafts = durability ? 100 : 1_000_000;
        var inventory = new KeyCounter();
        inventory.add(essence, 4 * crafts);
        inventory.add(crystal, 1);
        var result = new AcyclicCraftingSolver().solve(network,
            new AcyclicRoutePlan(List.of(upgraded, essence)), inventory, crafts, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, result.status());
        assertEquals(1, result.state().usedItems().get(crystal));
        assertEquals(4 * crafts, result.state().usedItems().get(essence));
        assertEquals(crafts, result.state().patternTimes().get(recipe));
        result.state().executionProvenance().requireComplete();
    }

    @Test
    void specialCrystalDoesNotHideAnActualEssenceConversionCycle() throws Exception {
        var essence = AEItemKey.of(Items.COAL);
        var upgraded = AEItemKey.of(Items.DIAMOND);
        var crystal = AEItemKey.of(Items.EMERALD);
        var service = mock(ICraftingService.class);
        var upgrade = pattern(upgraded, input(essence, 4, null), input(crystal, 1, crystal));
        var downgrade = pattern(essence, input(upgraded, 1, null));
        when(service.getCraftingFor(upgraded)).thenReturn(List.of(upgrade));
        when(service.getCraftingFor(essence)).thenReturn(List.of(downgrade));
        var network = compiler(false).compile(service, upgraded, ECOCancellation.NONE);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        assertTrue(new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE).stream()
            .anyMatch(component -> component.cyclic()));
        assertFalse(new ActiveRouteSelector().select(graph, ECOCancellation.NONE).acyclic());
    }

    private static void assertAcyclic(CompiledNetwork network) throws Exception {
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        assertTrue(new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE).stream()
            .noneMatch(component -> component.cyclic()));
        assertTrue(new ActiveRouteSelector().select(graph, ECOCancellation.NONE).acyclic());
    }

    private static CraftingNetworkCompiler compiler(boolean omitSummary) {
        return new CraftingNetworkCompiler(List.of(new PatternSemanticAdapter() {
            @Override
            public boolean supports(IPatternDetails pattern) { return true; }

            @Override
            public PatternSemantics analyze(IPatternDetails pattern) {
                var semantics = new AE2PatternSemanticAdapter().analyze(pattern);
                if (!omitSummary) return semantics;
                return new PatternSemantics(pattern, semantics.physicalDefinition(), semantics.consumedInputs(),
                    semantics.producedOutputs(), List.of(), List.of(), semantics.matchingMode(),
                    semantics.executionRestriction(), semantics.exactStaticAnalysis(), semantics.cycleSafe(),
                    semantics.unsupportedReason());
            }
        }));
    }

    private static IPatternDetails pattern(AEKey output, IPatternDetails.IInput... inputs) {
        var pattern = mock(IPatternDetails.class);
        var product = new GenericStack(output, 1);
        when(pattern.getInputs()).thenReturn(inputs);
        when(pattern.getOutputs()).thenReturn(List.of(product));
        when(pattern.getPrimaryOutput()).thenReturn(product);
        return pattern;
    }

    private static IPatternDetails.IInput input(AEKey key, long amount, AEKey remainder) {
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(key, 1)});
        when(input.getMultiplier()).thenReturn(amount);
        when(input.getRemainingKey(key)).thenReturn(remainder);
        return input;
    }
}
