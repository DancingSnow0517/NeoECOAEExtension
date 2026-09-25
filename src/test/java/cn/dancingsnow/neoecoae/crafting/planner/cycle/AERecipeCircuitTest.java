package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.component.ComponentDependency;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AERecipeCircuitTest {
    @BeforeAll static void bootstrap() { cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize(); }

    @Test void trillionProductsReuseOneBucketAndChargeEveryMillibucket() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            AEKey empty = AEItemKey.of(Items.BUCKET), full = AEItemKey.of(Items.WATER_BUCKET);
            AEKey water = AEFluidKey.of(Fluids.WATER), product = AEItemKey.of(Items.CLAY_BALL);
            var fill = recipe(0, Map.of(empty, 1L, water, 1000L), Map.of(full, 1L), Map.of());
            var use = recipe(1, Map.of(full, 1L), Map.of(product, 1L), Map.of(empty, 1L));
            long quantity = 1_000_000_000_000L;
            var result = solve(List.of(empty, full), List.of(fill, use), product, quantity, Map.of(empty, 1L), water);
            assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
            assertEquals(quantity, result.patternTimes().get(fill.details()));
            assertEquals(quantity, result.patternTimes().get(use.details()));
            assertEquals(quantity * 1000L, result.externalDemand().get(water));
            assertEquals(Map.of(empty, 1L), result.requiredSeed());
            assertTrue(result.executionPlan().size() <= 4, "A trillion laps must not become a trillion runs");
            assertTrue(result.executionPlan().stream().anyMatch(run -> run.repetitions() > 1));
            assertTrue(result.executionWitness().isEmpty());
        });
    }

    @Test void splitMergeByproductsAndReturnedCatalystFormOneExecutableCircuit() throws Exception {
        AEKey a = mock(AEKey.class), b = mock(AEKey.class), c = mock(AEKey.class);
        AEKey catalyst = AEItemKey.of(Items.DIAMOND), fuel = AEFluidKey.of(Fluids.LAVA), product = mock(AEKey.class);
        var split = recipe(0, Map.of(a, 2L, catalyst, 1L, fuel, 1000L), Map.of(b, 3L, c, 1L), Map.of(catalyst, 1L));
        var merge = recipe(1, Map.of(b, 3L, c, 1L), Map.of(a, 2L, product, 1L), Map.of());
        var result = solve(List.of(a, b, c), List.of(split, merge), product, 1_000_000_000L,
            Map.of(a, 2L, catalyst, 1L), fuel);
        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result.summary());
        assertEquals(1_000_000_000L, result.patternTimes().get(split.details()));
        assertEquals(1L, result.requiredSeed().get(catalyst));
        assertEquals(1_000_000_000_000L, result.externalDemand().get(fuel));
        assertTrue(result.executionPlan().size() < 10);
    }

    @Test void finiteFuelCannotBeInventedByRepeatingTheCarrierCircuit() throws Exception {
        AEKey a = mock(AEKey.class), b = mock(AEKey.class), fuel = mock(AEKey.class), product = mock(AEKey.class);
        var first = recipe(0, Map.of(a, 1L, fuel, 1L), Map.of(b, 1L), Map.of());
        var last = recipe(1, Map.of(b, 1L), Map.of(a, 1L, product, 1L), Map.of());
        var enough = solve(List.of(a, b), List.of(first, last), product, 100, Map.of(a, 1L, fuel, 100L), null);
        assertEquals(CycleSolveStatus.SUCCESS, enough.status(), enough.summary());
        assertEquals(100L, enough.requiredSeed().get(fuel));
        var shortFuel = solve(List.of(a, b), List.of(first, last), product, 100, Map.of(a, 1L, fuel, 99L), null);
        assertNotEquals(CycleSolveStatus.SUCCESS, shortFuel.status());
        assertTrue(shortFuel.externalDemand().isEmpty());
    }

    @Test void noCarrierCannotBootstrapFromBalancedOutputsAndKeepsBothSeedChoices() throws Exception {
        AEKey a = mock(AEKey.class), b = mock(AEKey.class), product = mock(AEKey.class);
        var first = recipe(0, Map.of(a, 1L), Map.of(b, 1L), Map.of());
        var last = recipe(1, Map.of(b, 1L), Map.of(a, 1L, product, 1L), Map.of());
        var result = solve(List.of(a, b), List.of(first, last), product, 100, Map.of(), null);
        assertEquals(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT, result.status(), result.summary());
        assertTrue(result.startupCandidates().contains(Map.of(a, 1L)));
        assertTrue(result.startupCandidates().contains(Map.of(b, 1L)));
    }

    private static CycleSolveResult solve(List<AEKey> members, List<CompiledPattern> patterns, AEKey product,
            long quantity, Map<AEKey, Long> stock, AEKey boundaryKey) throws Exception {
        var boundary = boundaryKey == null ? List.<ComponentDependency>of() : List.of(new ComponentDependency(0, 1,
            List.of(new CraftingGraphEdge(members.getFirst(), boundaryKey, patterns.getFirst(), null))));
        var component = new CycleComponent(0, members, patterns, List.of(), List.of(), boundary);
        return new BoundedCycleSolver().solve(new CycleSolveRequest(component, Map.of(product, quantity), stock,
            boundary, new CycleSolveRequest.PlannerOptions()), ECOCancellation.NONE);
    }

    private static CompiledPattern recipe(int id, Map<AEKey, Long> inputs, Map<AEKey, Long> outputs, Map<AEKey, Long> returns) {
        var pattern = mock(IPatternDetails.class, "recipe" + id);
        var out = outputs.entrySet().stream().map(e -> new GenericStack(e.getKey(), e.getValue())).toList();
        var returned = returns.entrySet().stream().map(e -> new GenericStack(e.getKey(), e.getValue())).toList();
        when(pattern.getOutputs()).thenReturn(out);
        var semantics = new PatternSemantics(pattern, null, List.of(), out, returned, List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, pattern, out.getFirst().what(), PlannerAmount.of(out.getFirst().amount()),
            inputs.entrySet().stream().map(e -> new CompiledInput(null, e.getKey(), e.getValue(), true, null)).toList(),
            out, true, null, false, semantics);
    }
}
