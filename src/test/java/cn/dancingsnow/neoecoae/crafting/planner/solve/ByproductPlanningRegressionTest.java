package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledExecutionKernel;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Byproduct-heavy shapes from ore-processing style packs. AE2 advertises every output of a processing pattern as
 * craftable, so byproducts used to multiply producer candidates, structural cycles and fallback route attempts.
 */
class ByproductPlanningRegressionTest {
    private static Item[] items;

    @BeforeAll static void bootstrap() {
        if (items != null) return;
        InventoryTestBootstrap.initialize();
        items = new Item[] {
            Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER, Items.COAL, Items.CHARCOAL, Items.FLINT, Items.CLAY_BALL,
            Items.BRICK, Items.NETHER_BRICK, Items.PRISMARINE_SHARD, Items.PRISMARINE_CRYSTALS, Items.GLOWSTONE_DUST,
            Items.BLAZE_POWDER, Items.GUNPOWDER, Items.SUGAR, Items.BONE_MEAL, Items.STRING, Items.FEATHER,
            Items.LEATHER, Items.PAPER, Items.SLIME_BALL, Items.ENDER_PEARL, Items.IRON_NUGGET, Items.GOLD_NUGGET,
            Items.QUARTZ, Items.LAPIS_LAZULI, Items.REDSTONE, Items.EMERALD, Items.AMETHYST_SHARD, Items.NETHERITE_SCRAP
        };
    }

    enum Shape { RING, MESH }

    /** Tier one cross-links neighbours as byproducts; MESH adds a tier that consumes tier one and returns some. */
    record Network(ICraftingService service, AEKey goal, AEKey[] raw) {}

    @Test void boundaryOnlyTransitionsDoNotInventUnrepresentableBatches() throws Exception {
        // Nine raw-only patterns sit inside the byproduct component. A boundary-only transition used to offer a
        // Long.MAX_VALUE batch and turned this 64-item order into a 1.5e20-firing witness.
        var network = network(Shape.MESH, 9, false);
        var result = plan(network, stock(network, 64), 64, new Counter(Long.MAX_VALUE));
        assertEquals(PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());
        result.plan().patternTimes().values().forEach(times -> assertTrue(times <= 64L * 4, "times=" + times));
    }

    @Test void byproductRingsMoveNoMaterialAndFormNoCycleComponent() throws Exception {
        var network = network(Shape.RING, 7, true);
        for (long rawEach : new long[] {64, 16}) {
            var counter = new Counter(Long.MAX_VALUE);
            var result = plan(network, stock(network, rawEach), 64, counter);
            assertEquals(rawEach == 64 ? PlanningStatus.SUCCESS : PlanningStatus.MISSING_ITEMS, result.status(),
                result.trace().diagnostics().toString());
            assertTrue(result.components().stream()
                .noneMatch(component -> component.type() == ComponentPlanningResult.Type.CYCLIC), result.components()
                .toString());
            assertTrue(counter.count < 100_000, "checkpoints=" + counter.count);
        }
    }

    @Test void infeasibleByproductMeshIsProvenWithoutSearchingRoutes() throws Exception {
        // AE2 advertised byproducts as producers here; 2,377 alternative routes then took 23 s (k=5) or the
        // whole session (k=7) before the order came back unresolved. Four raw items each cannot make 64 goals:
        // every tier-two firing nets one tier-one item, so at most 8k firings and 16k tops exist.
        for (int k : new int[] {5, 7}) {
            var network = network(Shape.MESH, k, true);
            var counter = new Counter(5_000_000L);
            var result = plan(network, stock(network, 4), 64, counter);
            var codes = result.trace().diagnostics().stream().map(diagnostic -> diagnostic.code()).toList();
            // The tier-two recycle is a real (lossy) material loop, so the order may end on its cycle component.
            assertTrue(result.status() == PlanningStatus.MISSING_ITEMS || result.status() == PlanningStatus.PARTIAL,
                result.status() + " " + codes);
            assertTrue(codes.contains(PlannerDiagnostic.Code.MATERIAL_BALANCE_INFEASIBLE)
                || codes.contains(PlannerDiagnostic.Code.ROUTE_QUANTITY_BOUND), codes.toString());
            assertFalse(codes.contains(PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED), codes.toString());
            assertTrue(counter.count < 200_000, "k=" + k + " checkpoints=" + counter.count);
        }
    }

    @Test void hardByproductMeshAnswersWithinAFractionOfTheSession() throws Exception {
        // Sixteen raw items each are enough on paper (32 tier-two firings each, every one yielding two tops), but
        // reaching it needs byproduct tops credited around the whole ring. The planner may answer PARTIAL; it must
        // answer quickly instead of enumerating producer combinations.
        for (int k : new int[] {5, 7}) {
            var network = network(Shape.MESH, k, true);
            var counter = new Counter(5_000_000L);
            var result = plan(network, stock(network, 16), 64, counter);
            var codes = result.trace().diagnostics().stream().map(diagnostic -> diagnostic.code()).toList();
            assertNotEquals(PlanningStatus.CYCLE_UNRESOLVED, result.status(), codes.toString());
            assertFalse(codes.contains(PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED), codes.toString());
            assertTrue(counter.count < 200_000, "k=" + k + " checkpoints=" + counter.count);
        }
    }

    @Test void feasibleByproductMeshStillPlans() throws Exception {
        var network = network(Shape.MESH, 5, true);
        var result = plan(network, stock(network, 64), 64, new Counter(Long.MAX_VALUE));
        assertEquals(PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());
    }

    @Test void byproductProducersStayWhenNoPrimaryProducerCanBeFastPlanned() throws Exception {
        AEKey raw = AEItemKey.of(Items.RAW_IRON), main = AEItemKey.of(Items.GOLD_NUGGET);
        AEKey side = AEItemKey.of(Items.IRON_NUGGET), goal = AEItemKey.of(Items.DIAMOND_BLOCK);
        // Q makes `main` and yields `side` as a byproduct; P makes `side` as its primary output.
        var q = pattern(List.of(new GenericStack(raw, 1)), new GenericStack(main, 1), new GenericStack(side, 1));
        var top = pattern(List.of(new GenericStack(side, 1)), new GenericStack(goal, 1));
        for (boolean plannable : new boolean[] {true, false}) {
            var p = pattern(List.of(new GenericStack(raw, 1)), new GenericStack(side, 1));
            // A zero input multiplier is an invalid amount: the primary producer cannot be fast-planned.
            if (!plannable) when(p.getInputs()[0].getMultiplier()).thenReturn(0L);
            var network = new CraftingNetworkCompiler().compile(service(true, List.of(q, p, top)), goal, true,
                ECOCancellation.NONE);
            var producers = network.producers().get(side);
            var primary = producers.stream().filter(pattern -> pattern.details() == p).findFirst().orElseThrow();
            assertEquals(plannable, primary.fastSupported(), primary.unsupportedReason());
            // Only a plannable primary producer replaces the byproduct view; AE's producer order is kept otherwise.
            assertEquals(plannable ? List.of(p) : List.of(q, p),
                producers.stream().map(CompiledPattern::details).toList());
            assertDoesNotThrow(() -> CompiledExecutionKernel.compile(network), "pattern ids must stay dense");
        }
    }

    static Network network(Shape shape, int k, boolean advertiseByproducts) {
        AEKey[] raw = new AEKey[k], mid = new AEKey[k];
        for (int j = 0; j < k; j++) {
            raw[j] = AEItemKey.of(items[j]);
            mid[j] = AEItemKey.of(items[10 + j]);
        }
        AEKey goal = AEItemKey.of(Items.DIAMOND_BLOCK);
        List<IPatternDetails> patterns = new ArrayList<>();
        for (int j = 0; j < k; j++) {
            patterns.add(pattern(List.of(new GenericStack(raw[j], 1)), new GenericStack(mid[j], 1),
                new GenericStack(mid[(j + 1) % k], 1)));
        }
        AEKey[] top = mid;
        if (shape == Shape.MESH) {
            top = new AEKey[k];
            for (int j = 0; j < k; j++) top[j] = AEItemKey.of(items[20 + j]);
            for (int j = 0; j < k; j++) {
                patterns.add(pattern(List.of(new GenericStack(mid[j], 2)), new GenericStack(top[j], 1),
                    new GenericStack(mid[(j + 2) % k], 1), new GenericStack(top[(j + 1) % k], 1)));
            }
        }
        List<GenericStack> goalInputs = new ArrayList<>();
        for (int j = 0; j < k; j++) goalInputs.add(new GenericStack(top[j], 1));
        patterns.add(pattern(goalInputs, new GenericStack(goal, 1)));
        return new Network(service(advertiseByproducts, patterns), goal, raw);
    }

    static KeyCounter stock(Network network, long rawEach) {
        KeyCounter stock = new KeyCounter();
        for (AEKey raw : network.raw()) stock.add(raw, rawEach);
        return stock;
    }

    static ECOPlanningResult plan(Network network, KeyCounter stock, long amount, ECOCancellation cancellation)
            throws Exception {
        return new ECOCraftingPlannerService().createSession(network.service(), network.goal(), stock, true)
            .plan(amount, false, cancellation);
    }

    static final class Counter implements ECOCancellation {
        private final long cap;
        long count;

        Counter(long cap) { this.cap = cap; }

        @Override public void checkpoint() throws InterruptedException {
            if (++count > cap) throw new InterruptedException("checkpoint cap");
        }
    }

    private static IPatternDetails pattern(List<GenericStack> inputs, GenericStack... outputs) {
        var pattern = mock(IPatternDetails.class);
        var raw = inputs.stream().map(stack -> {
            var input = mock(IPatternDetails.IInput.class);
            when(input.getMultiplier()).thenReturn(1L);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[] {stack});
            when(input.isValid(any(), isNull())).thenAnswer(call -> stack.what().equals(call.getArgument(0)));
            return input;
        }).toArray(IPatternDetails.IInput[]::new);
        when(pattern.getInputs()).thenReturn(raw);
        when(pattern.getOutputs()).thenReturn(List.of(outputs));
        when(pattern.getPrimaryOutput()).thenReturn(outputs[0]);
        return pattern;
    }

    private static ICraftingService service(boolean byproducts, List<IPatternDetails> patterns) {
        Map<AEKey, List<IPatternDetails>> index = new LinkedHashMap<>();
        for (var pattern : patterns) {
            for (var output : byproducts ? pattern.getOutputs() : List.of(pattern.getPrimaryOutput())) {
                var list = index.computeIfAbsent(output.what(), ignored -> new ArrayList<>());
                if (!list.contains(pattern)) list.add(pattern);
            }
        }
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(any())).thenAnswer(call -> index.getOrDefault(call.getArgument(0), List.of()));
        return service;
    }
}
