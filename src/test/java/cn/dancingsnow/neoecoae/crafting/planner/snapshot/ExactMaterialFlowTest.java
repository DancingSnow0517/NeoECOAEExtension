package cn.dancingsnow.neoecoae.crafting.planner.snapshot;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveMetrics;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.PatternRun;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.KeyAmount;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionCountKnowledge;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExactMaterialFlowTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void hugeRepeatedCircuitShowsGrowthWithoutMultiplyingReturnedCatalystsOrLosingTheSuffix() {
        var seed = AEItemKey.of(Items.IRON_INGOT);
        var carrier = AEItemKey.of(Items.IRON_BLOCK);
        var fuel = AEItemKey.of(Items.COAL);
        var catalyst = AEItemKey.of(Items.STICK);
        var prepare = compiled(0, carrier, 1, List.of(new GenericStack(seed, 1), new GenericStack(fuel, 3),
            new GenericStack(catalyst, 1)), List.of(new GenericStack(catalyst, 1)));
        var finish = compiled(1, seed, 2, List.of(new GenericStack(carrier, 1)), List.of());
        PlannerAmount count = PlannerAmount.of(BigInteger.TEN.pow(25));
        var runs = List.of(new PatternRun(prepare, 1), new PatternRun(finish, PlannerAmount.ONE, 2, count));
        var patternIds = Map.of(prepare.details(), -2, finish.details(), -3);
        for (boolean missingSeed : List.of(false, true)) {
            var solved = new CycleSolveResult(missingSeed ? CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT : CycleSolveStatus.SUCCESS,
                ExecutionCountKnowledge.EXACT, Map.of(prepare.details(), count, finish.details(), count),
                Map.of(fuel, count.multiply(3)), Map.of(seed, PlannerAmount.ONE, catalyst, PlannerAmount.ONE),
                missingSeed ? Map.of(seed, PlannerAmount.ONE) : Map.of(), Map.of(),
                Map.of(seed, count.add(PlannerAmount.ONE)), List.of(), runs, List.of(), CycleSolveMetrics.NONE, List.of());
            var preview = CycleRecursionPreview.create(solved, List.of(new KeyAmount(seed, count)), patternIds);
            assertTrue(preview.complete(), "A verified missing-seed witness is a complete conditional preview");
            assertTrue(preview.layers().size() <= 64, "The report must not expand one node per firing");
            assertEquals(PlannerAmount.of(2), amount(preview.layers().get(0).inventory(), seed));
            assertEquals(PlannerAmount.of(4), amount(preview.layers().get(1).inventory(), seed));
            assertEquals(PlannerAmount.of(3), amount(preview.layers().get(0).addedInputs(), fuel));
            PlannerAmount imported = PlannerAmount.ZERO;
            Map<Integer, PlannerAmount> firings = new java.util.HashMap<>();
            for (var layer : preview.layers()) {
                imported = imported.add(amount(layer.addedInputs(), fuel));
                assertEquals(PlannerAmount.ONE, amount(layer.inventory(), catalyst));
                assertEquals(PlannerAmount.ZERO, amount(layer.addedInputs(), catalyst));
                layer.patternTimes().forEach(value -> firings.merge(value.patternNodeId(), value.plannerAmount(), PlannerAmount::add));
            }
            var last = preview.layers().get(preview.layers().size() - 1);
            assertTrue(last.folded());
            assertEquals(count.add(PlannerAmount.ONE), amount(last.inventory(), seed));
            assertEquals(count.multiply(3), imported);
            assertEquals(Map.of(-2, count, -3, count), firings);
        }
    }

    @Test
    void exactFiringTotalsWithoutAnOrderedWitnessDoNotInventRecursionLayers() {
        var seed = AEItemKey.of(Items.IRON_INGOT);
        var pattern = compiled(0, seed, 2, List.of(new GenericStack(seed, 1)), List.of());
        var solved = new CycleSolveResult(CycleSolveStatus.SUCCESS, ExecutionCountKnowledge.EXACT,
            Map.of(pattern.details(), PlannerAmount.of(7)), Map.of(), Map.of(seed, PlannerAmount.ONE),
            Map.of(), Map.of(), Map.of(seed, PlannerAmount.of(8)), List.of(), List.of(), List.of(),
            CycleSolveMetrics.NONE, List.of());
        assertFalse(CycleRecursionPreview.create(solved, List.of(new KeyAmount(seed, 7)),
            Map.of(pattern.details(), -2)).complete());
    }

    private static PlannerAmount amount(List<KeyAmount> values, AEKey key) {
        return values.stream().filter(value -> value.key().equals(key)).map(KeyAmount::plannerAmount)
            .findFirst().orElse(PlannerAmount.ZERO);
    }

    private static CompiledPattern compiled(int id, AEKey output, long outputAmount, List<GenericStack> inputs,
            List<GenericStack> returned) {
        IPatternDetails details = mock(IPatternDetails.class);
        var outputs = List.of(new GenericStack(output, outputAmount));
        when(details.getOutputs()).thenReturn(outputs.toArray(GenericStack[]::new));
        var semantics = new PatternSemantics(details, null, List.of(), outputs, returned, List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, details, output, PlannerAmount.of(outputAmount), inputs.stream()
            .map(input -> new CompiledInput(null, input.what(), input.amount(), true, null)).toList(), outputs,
            true, null, false, semantics);
    }

    @Test void cycleTotalsUseExactFiringsEvenWhenAe2ShellIsEmpty() {
        verifyFlow(BigInteger.TEN.pow(25), true);
    }

    @Test void ordinaryPlansStillDeriveFlowFromTheirExecutableTasks() {
        verifyFlow(BigInteger.valueOf(4), false);
    }

    @Test void traceAndPlanReservationsAreNotCountedTwice() {
        AEKey iron = AEItemKey.of(Items.IRON_INGOT);
        var used = new KeyCounter(); used.add(iron, 3);
        var trace = new ECOPlanTrace();
        trace.addNode(new cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode(
            cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode.Kind.MATERIAL,
            iron, null, 3, 3, 0, 0, 0,
            cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode.Selection.NOT_APPLICABLE, null));
        var plan = new CraftingPlan(new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 1), 0, false, false,
            used, new KeyCounter(), new KeyCounter(), Map.of());
        var result = new ECOPlanningResult(PlanningStatus.SUCCESS, plan, trace, List.of(), 0);
        result.setExactMaterials(Map.of(iron, PlannerAmount.of(3)), Map.of(), Map.of());
        var node = CraftingGraphSnapshotFactory.create(result).nodes().stream()
            .filter(material -> material.key().equals(iron)).findFirst().orElseThrow();
        assertEquals(BigInteger.valueOf(3), node.fromInventoryBigInteger());
        assertEquals(BigInteger.valueOf(3), node.requestedBigInteger());
    }

    @Test void exactReservationsWithoutTraceSurviveAnEmptyCompatibilityShell() {
        AEKey iron = AEItemKey.of(Items.IRON_INGOT);
        var amount = BigInteger.TEN.pow(25);
        var plan = new CraftingPlan(new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 1), 0, true, false,
            new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
        var result = new ECOPlanningResult(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE,
            plan, new ECOPlanTrace(), List.of(), 0);
        result.setExactMaterials(Map.of(iron, PlannerAmount.of(amount)), Map.of(), Map.of());
        var node = CraftingGraphSnapshotFactory.create(result).nodes().stream()
            .filter(material -> material.key().equals(iron)).findFirst().orElseThrow();
        assertEquals(amount, node.fromInventoryBigInteger());
        assertEquals(amount, node.requestedBigInteger());
        assertEquals(Long.MAX_VALUE, node.fromInventory());
        assertEquals(BigInteger.ZERO, node.toCraftBigInteger());
    }

    @Test void exactShortagesWithoutTraceSurviveAnEmptyCompatibilityShell() {
        AEKey iron = AEItemKey.of(Items.IRON_INGOT);
        var amount = BigInteger.TEN.pow(25);
        var plan = new CraftingPlan(new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 1), 0, true, false,
            new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
        var result = new ECOPlanningResult(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE,
            plan, new ECOPlanTrace(), List.of(), 0);
        result.setExactMaterials(Map.of(), Map.of(), Map.of(iron, PlannerAmount.of(amount)));

        var node = CraftingGraphSnapshotFactory.create(result).nodes().stream()
            .filter(material -> material.key().equals(iron)).findFirst().orElseThrow();
        assertEquals(amount, node.missingBigInteger());
        assertEquals(Long.MAX_VALUE, node.missing());
        assertEquals(CraftingGraphSnapshot.MaterialStatus.MISSING, node.status());
    }

    @Test void tracePlanAndExactShortagesDescribeTheSameDeficit() {
        AEKey iron = AEItemKey.of(Items.IRON_INGOT);
        var trace = new ECOPlanTrace();
        trace.addNode(new cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode(
            cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode.Kind.MATERIAL,
            iron, null, 5, 0, 0, 3, 0,
            cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode.Selection.NOT_APPLICABLE, null));
        var missing = new KeyCounter(); missing.add(iron, 4);
        var plan = new CraftingPlan(new GenericStack(AEItemKey.of(Items.IRON_BLOCK), 1), 0, true, false,
            new KeyCounter(), new KeyCounter(), missing, Map.of());
        var result = new ECOPlanningResult(PlanningStatus.MISSING_ITEMS, plan, trace, List.of(), 0);
        result.setExactMaterials(Map.of(), Map.of(), Map.of(iron, PlannerAmount.of(5)));

        var node = CraftingGraphSnapshotFactory.create(result).nodes().stream()
            .filter(material -> material.key().equals(iron)).findFirst().orElseThrow();
        assertEquals(BigInteger.valueOf(5), node.missingBigInteger());
        assertEquals(5, node.missing());
    }

    private static void verifyFlow(BigInteger count, boolean shell) {
        AEKey seed = AEItemKey.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        AEKey diamond = AEItemKey.of(Items.DIAMOND);
        var pattern = mock(IPatternDetails.class);
        var inputs = new IPatternDetails.IInput[]{input(seed, 1), input(diamond, 7)};
        when(pattern.getInputs()).thenReturn(inputs);
        when(pattern.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(seed, 2)});
        var plan = new CraftingPlan(new GenericStack(seed, 1), 0, shell, false,
            new KeyCounter(), new KeyCounter(), new KeyCounter(), shell ? Map.of() : Map.of(pattern, count.longValueExact()));
        var result = new ECOPlanningResult(shell ? PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE
            : PlanningStatus.SUCCESS, plan, new ECOPlanTrace(), List.of(), 0);
        if (shell) result.setExactPatternTimes(Map.of(pattern, PlannerAmount.of(count)));
        var snapshot = CraftingGraphSnapshotFactory.create(result);
        var seedNode = snapshot.nodes().stream().filter(node -> node.key().equals(seed)).findFirst().orElseThrow();
        var diamondNode = snapshot.nodes().stream().filter(node -> node.key().equals(diamond)).findFirst().orElseThrow();
        assertEquals(count, seedNode.consumedBigInteger());
        assertEquals(count.multiply(BigInteger.TWO), seedNode.producedBigInteger());
        assertEquals(count.multiply(BigInteger.valueOf(7)), diamondNode.consumedBigInteger());
        assertEquals(BigInteger.ZERO, diamondNode.producedBigInteger());
        var row = new ECOCycleItemList.Entry(seed, seedNode.consumedBigInteger(), seedNode.producedBigInteger(),
            BigInteger.ONE, count, ExecutionCountKnowledge.EXACT, CycleSolveStatus.SUCCESS, 1);
        assertEquals(count.multiply(BigInteger.TWO), row.displayedTotal());
    }

    private static IPatternDetails.IInput input(AEKey key, long amount) {
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(key, amount)});
        when(input.getMultiplier()).thenReturn(1L);
        return input;
    }
}
