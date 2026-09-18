package cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ExecutionCountKnowledge;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
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
