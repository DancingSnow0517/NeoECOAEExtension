package cn.dancingsnow.neoecoae.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CraftingPlanExportTest {
    private static AEKey seed, product, fuel, rawLeft, rawRight, left, right, slag, goal;

    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        seed = AEItemKey.of(Items.AMETHYST_SHARD);
        product = AEItemKey.of(Items.QUARTZ);
        fuel = AEItemKey.of(Items.COAL);
        rawLeft = AEItemKey.of(Items.RAW_IRON);
        rawRight = AEItemKey.of(Items.RAW_GOLD);
        left = AEItemKey.of(Items.IRON_INGOT);
        right = AEItemKey.of(Items.GOLD_INGOT);
        slag = AEItemKey.of(Items.GRAVEL);
        goal = AEItemKey.of(Items.DIAMOND_BLOCK);
    }

    @Test void exportsPlannerStateCyclesProducerOrderAndSurplus() throws Exception {
        // Growth loop: seed + fuel -> 2 product, product -> seed.
        var grow = pattern(List.of(stack(seed, 1), stack(fuel, 1)), stack(product, 2));
        var back = pattern(List.of(stack(product, 1)), stack(seed, 1));
        // Byproduct ring without a material loop, plus a byproduct no pattern consumes.
        var makeLeft = pattern(List.of(stack(rawLeft, 1)), stack(left, 1), stack(right, 1), stack(slag, 1));
        var makeRight = pattern(List.of(stack(rawRight, 1)), stack(right, 1), stack(left, 1));
        var finish = pattern(List.of(stack(product, 1), stack(left, 1), stack(right, 1)), stack(goal, 1));
        var service = service(grow, back, makeLeft, makeRight, finish);
        KeyCounter stock = new KeyCounter();
        stock.add(seed, 1);
        stock.add(fuel, 64);
        stock.add(rawLeft, 64);
        stock.add(rawRight, 64);

        var result = new ECOCraftingPlannerService().createSession(service, goal, stock, true)
            .plan(4, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());

        var storage = mock(IStorageService.class);
        when(storage.getCachedInventory()).thenReturn(stock);
        var grid = mock(IGrid.class);
        when(grid.getCraftingService()).thenReturn(service);
        when(grid.getStorageService()).thenReturn(storage);
        String text = CraftingPlanExport.render(grid, result.plan(), result,
            new ECOPlannerOptions(true, false, Set.of()));

        assertTrue(text.contains("options: cyclePlanning=true ignoreSubstitutions=false fuzzyItems=[]"), text);
        assertTrue(text.contains("result: status=SUCCESS calculationMs="), text);
        assertTrue(text.contains("diagnostics:"), text);
        // left/right each have a primary producer first and a byproduct producer second.
        assertTrue(text.contains("minecraft:iron_ingot: P"), text);
        assertTrue(text.matches("(?s).*minecraft:iron_ingot: P\\d+ > P\\d+\\*.*"), text);
        // The seed loop is a real growth recipe; the byproduct ring only orders byproducts.
        assertTrue(text.matches("(?s).*S\\d+ RECIPE GROWTH maxGain=x2 .*"), text);
        assertTrue(text.matches("(?s).*S\\d+ ORDER_ONLY NO_MATERIAL_LOOP .*"), text);
        assertTrue(text.contains("~P"), text);
        assertTrue(text.matches("(?s).*used=\\d+ avail=\\d+.*minecraft:coal.*"), text);
        assertTrue(text.matches("(?s).*\\n\\d+ minecraft:gravel \\[sink\\] \\[byproduct\\]\\n.*"), text);
    }

    private static GenericStack stack(AEKey key, long amount) {
        return new GenericStack(key, amount);
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

    /** AE2 advertises every output of a pattern, so byproducts become craftable keys too. */
    private static ICraftingService service(IPatternDetails... patterns) {
        Map<AEKey, List<IPatternDetails>> index = new LinkedHashMap<>();
        for (var pattern : patterns) {
            for (var output : pattern.getOutputs()) {
                var list = index.computeIfAbsent(output.what(), ignored -> new ArrayList<>());
                if (!list.contains(pattern)) list.add(pattern);
            }
        }
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(any())).thenAnswer(call -> index.getOrDefault(call.getArgument(0), List.of()));
        when(service.getCraftables(any())).thenReturn(index.keySet());
        return service;
    }
}
