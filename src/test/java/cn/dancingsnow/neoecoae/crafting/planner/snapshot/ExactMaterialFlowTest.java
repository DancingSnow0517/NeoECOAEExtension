package cn.dancingsnow.neoecoae.crafting.planner.snapshot;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
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

    private static void verifyFlow(BigInteger count, boolean shell) {
        AEKey seed = AEItemKey.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        AEKey diamond = AEItemKey.of(Items.DIAMOND);
        var pattern = mock(IPatternDetails.class);
        var inputs = new IPatternDetails.IInput[]{input(seed, 1), input(diamond, 7)};
        when(pattern.getInputs()).thenReturn(inputs);
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(seed, 2)));
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
