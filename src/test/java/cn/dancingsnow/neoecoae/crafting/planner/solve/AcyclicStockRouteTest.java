package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcyclicStockRouteTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void aggregatesSharedStockBeforeCuttingReverseRecipe() throws Exception {
        var outcome = solve(10L, 0L, 0L);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertEquals(10L, outcome.state().usedItems().get(AEItemKey.of(Items.IRON_NUGGET)));
        assertEquals(2, outcome.state().patternTimes().size(), "The reverse recipe must not run");
    }

    @Test
    void sharedStockShortfallDoesNotBecomeAFreeCraft() throws Exception {
        // Nine nuggets cover the ingot, but the final recipe also consumes one directly.
        assertEquals(PlanningStatus.PARTIAL_UNSUPPORTED, solve(9L, 0L, 0L).status());
    }

    @Test
    void aCycleWithoutStockStillFallsBack() throws Exception {
        assertEquals(PlanningStatus.PARTIAL_UNSUPPORTED, solve(0L, 0L, 0L).status());
    }

    @Test
    void storedFinalOutputCannotBreakTheCycle() throws Exception {
        assertEquals(PlanningStatus.PARTIAL_UNSUPPORTED, solve(0L, 0L, 1L).status());
    }

    @Test
    void reopensAnInsufficientStockLeafAndRetriesFromFreshInventory() throws Exception {
        // One stored ingot is insufficient for the two needed; craft the other from nine nuggets.
        var outcome = solve(10L, 1L, 0L, 2L);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertEquals(1L, outcome.state().usedItems().get(AEItemKey.of(Items.IRON_INGOT)));
        assertEquals(10L, outcome.state().usedItems().get(AEItemKey.of(Items.IRON_NUGGET)));
        assertEquals(2, outcome.state().patternTimes().size());
    }

    private static AcyclicCraftingSolver.Outcome solve(long nuggets, long ingots, long products) throws Exception {
        return solve(nuggets, ingots, products, 1L);
    }

    private static AcyclicCraftingSolver.Outcome solve(long nuggets, long ingots, long products,
            long requiredIngots) throws Exception {
        var product = AEItemKey.of(Items.DIAMOND);
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        var nugget = AEItemKey.of(Items.IRON_NUGGET);
        var finalRecipe = pattern(0, product, 1L,
            new GenericStack(ingot, requiredIngots), new GenericStack(nugget, 1L));
        var pack = pattern(1, ingot, 1L, new GenericStack(nugget, 9L));
        var unpack = pattern(2, nugget, 9L, new GenericStack(ingot, 1L));
        var network = new CompiledNetwork(product, Map.of(product, List.of(finalRecipe),
            ingot, List.of(pack), nugget, List.of(unpack)), Set.of(), 3, 3);
        var inventory = new KeyCounter();
        inventory.set(nugget, nuggets);
        inventory.set(ingot, ingots);
        inventory.set(product, products);
        return new AcyclicCraftingSolver().solve(network,
            new AcyclicRoutePlan(List.of(product, ingot, nugget)), inventory, 1L, ECOCancellation.NONE);
    }

    private static CompiledPattern pattern(int id, AEKey output, long outputAmount, GenericStack... inputs) {
        var details = mock(IPatternDetails.class);
        var outputs = List.of(new GenericStack(output, outputAmount));
        when(details.getOutputs()).thenReturn(outputs);
        var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        var compiledInputs = java.util.Arrays.stream(inputs)
            .map(input -> new CompiledInput(null, input.what(), input.amount(), false, null)).toList();
        return new CompiledPattern(id, details, output, PlannerAmount.of(outputAmount), compiledInputs, outputs,
            true, null, false, semantics);
    }
}
