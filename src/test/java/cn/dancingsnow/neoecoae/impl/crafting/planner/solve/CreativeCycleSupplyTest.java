package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.*;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.*;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreativeCycleSupplyTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void creativeTemplateDoesNotScheduleItsDuplicationRecipe() throws Exception {
        AEKey product = AEItemKey.of(Items.DIAMOND);
        AEKey template = AEItemKey.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        var consumer = pattern(0, product, 1, template, 2);
        var duplication = pattern(1, template, 2, template, 1);
        var network = new CompiledNetwork(product,
            Map.of(product, List.of(consumer), template, List.of(duplication)), Set.of(), 2, 2);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var stock = new KeyCounter();
        stock.set(template, Long.MAX_VALUE);
        var inventory = PlannerInventorySnapshot.of(stock, Set.of(template));
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(), (request, cancellation) -> {
            throw new AssertionError("Creative-backed intermediate demand must not invoke a cycle solver");
        });
        for (boolean cyclesEnabled : List.of(false, true)) {
            for (long amount : List.of(32L, Long.MAX_VALUE)) {
                var result = planner.plan(network, condensation, stock, inventory, amount, cyclesEnabled,
                    false, ECOCancellation.NONE);
                assertEquals(amount == Long.MAX_VALUE ? PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE
                    : PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());
                assertEquals(BigInteger.valueOf(amount).multiply(BigInteger.TWO),
                    result.state().usedAmounts().get(template).toBigInteger());
                assertEquals(PlannerAmount.of(amount), result.state().plannerPatternTimes().get(consumer.details()));
                assertFalse(result.state().plannerPatternTimes().containsKey(duplication.details()));
                assertTrue(result.state().missingAmounts().isEmpty());
                if (amount == Long.MAX_VALUE) {
                    var shell = new appeng.crafting.CraftingPlan(new GenericStack(product, amount), 0, true, false,
                        new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
                    var exact = new cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult(
                        result.status(), shell, result.trace(), result.cycles(), result.components(),
                        result.executionComponentOrder(), 0, result.state().executionProvenance());
                    exact.setExactPatternTimes(result.state().plannerPatternTimes());
                    exact.setExactMaterials(result.state().usedAmounts(), result.state().emittedAmounts(), result.state().missingAmounts());
                    exact.setTheoreticalBytes(result.state().plannerBytes());
                    var executable = new cn.dancingsnow.neoecoae.impl.crafting.ECOExactCraftingPlan(exact, false);
                    assertEquals(1, executable.exactTasks().size());
                    assertEquals(BigInteger.valueOf(amount).multiply(BigInteger.TWO), executable.deferredStock().get(template));
                    assertNotNull(executable.execution());
                }
            }
        }
    }

    private static CompiledPattern pattern(int id, AEKey outputKey, long outputAmount,
            AEKey inputKey, long inputAmount) {
        var details = mock(IPatternDetails.class);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(inputKey, inputAmount)});
        when(input.getMultiplier()).thenReturn(1L);
        when(details.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
        var outputs = List.of(new GenericStack(outputKey, outputAmount));
        when(details.getOutputs()).thenReturn(outputs);
        var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, details, outputKey, PlannerAmount.of(outputAmount),
            List.of(new CompiledInput(input, inputKey, inputAmount, true, null)), outputs,
            true, null, false, semantics);
    }
}
