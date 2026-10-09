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
        index.put(goal, List.of(pattern(goal, keys.getFirst(), missing)));
        var svc = mock(ICraftingService.class);
        when(svc.getCraftingFor(any())).thenAnswer(call -> index.getOrDefault(call.getArgument(0), List.of()));
        var stock = new KeyCounter();
        stock.add(keys.getLast(), 1);
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
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(output, 1)));
        when(pattern.getPrimaryOutput()).thenReturn(new GenericStack(output, 1));
        return pattern;
    }
}
