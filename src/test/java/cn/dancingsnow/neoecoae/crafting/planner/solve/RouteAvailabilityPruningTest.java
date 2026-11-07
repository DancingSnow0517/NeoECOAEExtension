package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningBudget;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class RouteAvailabilityPruningTest {
    @Test void progressiveMissingReportDoesNotReleaseTheAlternativeSearchBudget() throws Exception {
        InventoryTestBootstrap.initialize();
        var keys = List.of(Items.STONE, Items.DIRT, Items.SAND, Items.GLASS, Items.COBBLESTONE,
            Items.NETHERRACK, Items.END_STONE, Items.OBSIDIAN, Items.CLAY_BALL, Items.FLINT,
            Items.COAL, Items.CHARCOAL).stream().map(AEItemKey::of).toList();
        var goal = AEItemKey.of(Items.BRICK);
        var scarce = keys.get(keys.size() - 1);
        var index = new LinkedHashMap<AEKey, List<IPatternDetails>>();
        index.put(goal, List.of(pattern(goal, keys.get(0), scarce)));
        for (int i = 0; i < keys.size() - 1; i++) {
            index.put(keys.get(i), List.of(pattern(keys.get(i), keys.get(i + 1)),
                pattern(keys.get(i), keys.get(i + 1))));
        }
        // Alternative matching is intentionally left to the existing resolver, so independent optimistic
        // supplies cannot prove that the two branches compete for the same last piece of raw material.
        index.values().forEach(patterns -> patterns.forEach(pattern -> {
            for (var input : pattern.getInputs()) {
                var stack = input.getPossibleInputs()[0];
                when(input.getPossibleInputs()).thenReturn(new GenericStack[] {stack, stack});
            }
        }));
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(any())).thenAnswer(call -> index.getOrDefault(call.getArgument(0), List.of()));
        var network = new cn.dancingsnow.neoecoae.crafting.planner.compile.CraftingNetworkCompiler()
            .compile(service, goal, ECOCancellation.NONE);
        var graph = new cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph.build(graph,
            new cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE),
            ECOCancellation.NONE);
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(),
            new cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver());
        var selection = planner.selectRoutes(condensation, false, ECOCancellation.NONE);
        var stock = new KeyCounter(); stock.set(scarce, 1);
        var budget = new ECOPlanningBudget(ECOCancellation.NONE, 300_000, Long.MAX_VALUE, () -> 0);
        var reports = new ArrayList<ComponentPlanner.Outcome>();
        var result = planner.planWithRouteFallback(network, condensation, selection, stock,
            PlannerInventorySnapshot.of(stock), 1, false, false, budget,
            report -> { reports.add(report); return false; }, true);
        assertEquals(1, reports.size(), "Speculative partial failures must not replace the full preferred report");
        assertEquals(PlanningStatus.MISSING_ITEMS, reports.get(0).status());
        assertEquals(PlannerAmount.ONE, reports.get(0).state().missingAmounts().get(scarce));
        assertEquals(reports.get(0).state().missingAmounts(), result.state().missingAmounts());
        assertTrue(result.trace().diagnostics().stream().anyMatch(d -> d.code()
            == PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED));
        assertTrue(budget.work() < 250_000, "Recovery must leave the parent allowance available: " + budget.work());
        budget.checkpoint();
    }

    @Test void provenMissingLeafDoesNotEnumerate4096UnhelpfulRoutes() throws Exception {
        InventoryTestBootstrap.initialize();
        var keys = List.of(Items.RAW_IRON, Items.STONE, Items.DIRT, Items.SAND, Items.GLASS,
            Items.COBBLESTONE, Items.NETHERRACK, Items.END_STONE, Items.OBSIDIAN, Items.CLAY_BALL,
            Items.FLINT, Items.COAL, Items.CHARCOAL).stream().map(AEItemKey::of).toList();
        var index = new LinkedHashMap<AEKey, List<IPatternDetails>>();
        for (int i = 0; i < keys.size() - 1; i++) {
            index.put(keys.get(i), List.of(pattern(keys.get(i), keys.get(i + 1)),
                pattern(keys.get(i), keys.get(i + 1))));
        }
        var goal = AEItemKey.of(Items.BRICK);
        var missing = AEItemKey.of(Items.DIAMOND);
        index.put(goal, List.of(pattern(goal, cn.dancingsnow.neoecoae.platform.JdkCompat.first(keys), missing)));
        var svc = mock(ICraftingService.class);
        when(svc.getCraftingFor(any())).thenAnswer(call -> index.getOrDefault(call.getArgument(0), List.of()));
        var stock = new KeyCounter();
        stock.add(cn.dancingsnow.neoecoae.platform.JdkCompat.last(keys), 1);
        var session = new ECOCraftingPlannerService().createSession(svc, goal, stock, true);
        var field = session.getClass().getDeclaredField("planningBudget");
        field.setAccessible(true);
        field.set(session, new ECOPlanningBudget(ECOCancellation.NONE, 10_000, Long.MAX_VALUE, () -> 0));
        var result = session.plan(1, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.MISSING_ITEMS, result.status(), result.trace().diagnostics().toString());
        assertEquals(PlannerAmount.ONE, result.exactMissingItems().get(missing));
        assertTrue(result.plan().bytes() > 0);
        assertTrue(result.trace().diagnostics().stream().anyMatch(d -> d.code()
            == PlannerDiagnostic.Code.ROUTE_PROVEN_UNREACHABLE));
        assertFalse(result.trace().diagnostics().stream().anyMatch(d -> d.code()
            == PlannerDiagnostic.Code.CYCLE_BUDGET_EXHAUSTED));
    }

    @Test void positiveButInsufficientLeafDoesNotEnumerate4096UnhelpfulRoutes() throws Exception {
        InventoryTestBootstrap.initialize();
        var keys = List.of(Items.RAW_IRON, Items.STONE, Items.DIRT, Items.SAND, Items.GLASS,
            Items.COBBLESTONE, Items.NETHERRACK, Items.END_STONE, Items.OBSIDIAN, Items.CLAY_BALL,
            Items.FLINT, Items.COAL, Items.CHARCOAL).stream().map(AEItemKey::of).toList();
        var index = new LinkedHashMap<AEKey, List<IPatternDetails>>();
        for (int i = 0; i < keys.size() - 1; i++) {
            index.put(keys.get(i), List.of(pattern(keys.get(i), keys.get(i + 1)),
                pattern(keys.get(i), keys.get(i + 1))));
        }
        var goal = AEItemKey.of(Items.BRICK);
        var scarce = AEItemKey.of(Items.DIAMOND);
        index.put(goal, List.of(pattern(goal, keys.get(0), scarce)));
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(any())).thenAnswer(call -> index.getOrDefault(call.getArgument(0), List.of()));
        var stock = new KeyCounter();
        stock.add(keys.get(keys.size() - 1), 1_000_000_000L);
        stock.add(scarce, 1L);
        var session = new ECOCraftingPlannerService().createSession(service, goal, stock, true);
        var budget = session.getClass().getDeclaredField("planningBudget");
        budget.setAccessible(true);
        budget.set(session, new ECOPlanningBudget(ECOCancellation.NONE, 10_000, Long.MAX_VALUE, () -> 0));
        var result = session.plan(1_000_000_000L, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.MISSING_ITEMS, result.status(), result.trace().diagnostics().toString());
        assertEquals(PlannerAmount.of(999_999_999L), result.exactMissingItems().get(scarce));
        assertTrue(result.plan().bytes() > 0);
        assertTrue(result.trace().diagnostics().stream().anyMatch(d -> d.code() == PlannerDiagnostic.Code.ROUTE_QUANTITY_BOUND));
        assertFalse(result.trace().diagnostics().stream().anyMatch(d -> d.code() == PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED));
        assertEquals(1L, stock.get(scarce));
    }

    @Test void billionOrderWithSubstitutionKeepsWideMissingReportWithoutEnumeratingRoutes() throws Exception {
        InventoryTestBootstrap.initialize();
        var keys = List.of(Items.RAW_IRON, Items.STONE, Items.DIRT, Items.SAND, Items.GLASS,
            Items.COBBLESTONE, Items.NETHERRACK, Items.END_STONE, Items.OBSIDIAN, Items.CLAY_BALL,
            Items.FLINT, Items.COAL, Items.CHARCOAL).stream().map(AEItemKey::of).toList();
        var index = new LinkedHashMap<AEKey, List<IPatternDetails>>();
        for (int i = 0; i < keys.size() - 1; i++) index.put(keys.get(i), List.of(
            pattern(keys.get(i), keys.get(i + 1)), pattern(keys.get(i), keys.get(i + 1))));
        var goal = AEItemKey.of(Items.BRICK);
        var scarce = AEItemKey.of(Items.DIAMOND);
        var finish = pattern(goal, keys.get(0), scarce);
        when(finish.getInputs()[0].getPossibleInputs()).thenReturn(new GenericStack[] {
            new GenericStack(keys.get(0), 1), new GenericStack(keys.get(1), 1)});
        when(finish.getInputs()[1].getMultiplier()).thenReturn(Long.MAX_VALUE);
        index.put(goal, List.of(finish));
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(any())).thenAnswer(call -> index.getOrDefault(call.getArgument(0), List.of()));
        var stock = new KeyCounter(); stock.add(keys.get(keys.size() - 1), 1_000_000_000L); stock.add(scarce, 1L);
        var session = new ECOCraftingPlannerService().createSession(service, goal, stock, true);
        var budget = session.getClass().getDeclaredField("planningBudget"); budget.setAccessible(true);
        budget.set(session, new ECOPlanningBudget(ECOCancellation.NONE, 50_000, Long.MAX_VALUE, () -> 0));
        var result = session.plan(1_000_000_000L, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, result.status(), result.trace().diagnostics().toString());
        var expected = PlannerAmount.of(Long.MAX_VALUE).multiply(1_000_000_000L).subtract(PlannerAmount.ONE);
        assertEquals(expected, result.exactMissingItems().get(scarce));
        assertTrue(result.plan().simulation());
        assertTrue(result.trace().diagnostics().stream().anyMatch(d -> d.code() == PlannerDiagnostic.Code.ROUTE_QUANTITY_BOUND));
        assertFalse(result.trace().diagnostics().stream().anyMatch(d -> d.code() == PlannerDiagnostic.Code.INTERNAL_ERROR
            || d.code() == PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED));
    }

    private static IPatternDetails pattern(AEKey output, AEKey... inputs) {
        var pattern = mock(IPatternDetails.class);
        var raw = new ArrayList<IPatternDetails.IInput>();
        for (AEKey key : inputs) {
            var input = mock(IPatternDetails.IInput.class);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(key, 1)});
            when(input.getMultiplier()).thenReturn(1L);
            raw.add(input);
        }
        when(pattern.getInputs()).thenReturn(raw.toArray(IPatternDetails.IInput[]::new));
        when(pattern.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(output, 1)});
        when(pattern.getPrimaryOutput()).thenReturn(new GenericStack(output, 1));
        return pattern;
    }
}
