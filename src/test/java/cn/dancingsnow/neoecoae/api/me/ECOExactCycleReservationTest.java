package cn.dancingsnow.neoecoae.api.me;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.ECOExactCraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.*;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.*;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOExactCycleReservationTest {
    @Test void orderedCycleValidatesDeferredStockAgainstWholeOrder() {
        var fixture = new Fixture();
        var total = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        var result = fixture.result(total, List.of(fixture.component(397, 80_638_819_906_176L)));
        var plan = new ECOExactCraftingPlan(result, false);
        assertEquals(Long.MAX_VALUE, plan.usedItems().get(fixture.stock));
        assertEquals(total, plan.deferredStock().get(fixture.stock));
        assertEquals(ExecutionMode.ORDERED_CYCLE, plan.execution().mode());
        assertEquals(1, plan.execution().phases().getFirst().steps().size());
        assertEquals(1L, plan.execution().phases().getFirst().initialSeed().get(fixture.stock));
    }

    @Test void cycleReservationSuppliesTheExactMaterialLedger() {
        var fixture = new Fixture();
        var component = fixture.component(397, 80_638_819_906_176L);
        assertDoesNotThrow(() -> new ECOExactCraftingPlan(
            fixture.result(BigInteger.ZERO, List.of(component)), false));
        assertDoesNotThrow(() -> new ECOExactCraftingPlan(
            fixture.result(BigInteger.valueOf(80_638_819_906_175L), List.of(component)), false));
    }

    @Test void sharedStockReservationsSumBeyondLongWithoutOverflowOrDoubleAllocation() {
        var fixture = new Fixture();
        var components = List.of(fixture.stockComponent(1, Long.MAX_VALUE), fixture.stockComponent(2, Long.MAX_VALUE));
        var total = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        assertDoesNotThrow(() -> new ECOExactCraftingPlan(fixture.result(total, components), false));
        assertDoesNotThrow(() -> new ECOExactCraftingPlan(
            fixture.result(total.subtract(BigInteger.ONE), components), false));
    }

    private static class Fixture {
        final AEKey stock = mock(AEKey.class);
        final AEKey output = mock(AEKey.class);
        final IPatternDetails pattern = mock(IPatternDetails.class);
        Fixture() {
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
            when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(output, 1)));
        }
        ComponentPlanningResult component(int id, long reserved) {
            var run = mock(PatternRun.class);
            when(run.details()).thenReturn(pattern);
            when(run.count()).thenReturn(1L);
            var cycle = mock(CycleSolveResult.class);
            when(cycle.status()).thenReturn(CycleSolveStatus.SUCCESS);
            when(cycle.patternTimes()).thenReturn(Map.of(pattern, 1L));
            when(cycle.executionPlan()).thenReturn(List.of(run));
            when(cycle.requiredSeed()).thenReturn(Map.of(stock, 1L));
            return new ComponentPlanningResult(id, ComponentPlanningResult.Type.CYCLIC,
                ComponentPlanningResult.Status.PLANNED, Map.of(output, 1L), Set.of(pattern), Set.of(pattern),
                CyclePlanningStatus.SOLVED, null, Map.of(), null, cycle,
                CycleExecutionDisposition.ORDERED_EXECUTION, Map.of(stock, reserved));
        }
        ComponentPlanningResult stockComponent(int id, long reserved) {
            var cycle = mock(CycleSolveResult.class);
            when(cycle.status()).thenReturn(CycleSolveStatus.SUCCESS);
            return new ComponentPlanningResult(id, ComponentPlanningResult.Type.CYCLIC,
                ComponentPlanningResult.Status.PLANNED, Map.of(stock, reserved), Set.of(), Set.of(),
                CyclePlanningStatus.SOLVED, null, Map.of(), null, cycle,
                CycleExecutionDisposition.STOCK_SATISFIED, Map.of(stock, reserved));
        }
        ECOPlanningResult result(BigInteger total, List<ComponentPlanningResult> components) {
            var shell = new CraftingPlan(new GenericStack(output, 20_000_000_000L), 0, true, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
            var result = new ECOPlanningResult(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, shell,
                new ECOPlanTrace(), List.of(), components, components.stream().map(ComponentPlanningResult::componentId).toList(), 0);
            if (components.stream().anyMatch(c -> c.cycleDisposition() == CycleExecutionDisposition.ORDERED_EXECUTION))
                result.setExactPatternTimes(Map.of(pattern, PlannerAmount.of(1)));
            result.setExactMaterials(total.signum() == 0 ? Map.of() : Map.of(stock, PlannerAmount.of(total)), Map.of(), Map.of());
            return result;
        }
    }
}
