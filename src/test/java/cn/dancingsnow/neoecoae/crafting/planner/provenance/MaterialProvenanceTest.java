package cn.dancingsnow.neoecoae.crafting.planner.provenance;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialProvenanceTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void returnedBottlesAreAssignedOnlyToTheirActualConsumer() {
        var bottle = AEItemKey.of(Items.GLASS_BOTTLE);
        var honey = mock(IPatternDetails.class);
        var other = mock(IPatternDetails.class);
        var honeyInput = MaterialDemand.input(honey, 0, bottle, PlannerAmount.ONE);
        var otherInput = MaterialDemand.input(other, 0, bottle, PlannerAmount.ONE);
        var ledger = new MaterialProvenance();
        ledger.register(honeyInput);
        ledger.register(otherInput);
        ledger.allocate(honeyInput, bottle, MaterialSource.Stock.INSTANCE, PlannerAmount.ONE);
        ledger.credit(bottle, honey, PlannerAmount.of(2));
        ledger.consumeCredit(otherInput, bottle, PlannerAmount.ONE);

        var frozen = ledger.freeze();
        frozen.requireComplete();
        assertEquals(MaterialSource.Stock.INSTANCE, frozen.allocationsFor(honeyInput).getFirst().source());
        assertEquals(new MaterialSource.PatternOutput(honey, false),
            frozen.allocationsFor(otherInput).getFirst().source());
        assertEquals(2, frozen.allocations().size(), "Unused returned bottles are not dependencies");
    }

    @Test
    void inventoryPrimaryAndByproductCoverOneDemandWithoutDoubleSpending() {
        var bottle = AEItemKey.of(Items.GLASS_BOTTLE);
        var producer = mock(IPatternDetails.class);
        var consumer = mock(IPatternDetails.class);
        var demand = MaterialDemand.input(consumer, 2, bottle, PlannerAmount.of(10));
        var ledger = new MaterialProvenance();
        ledger.register(demand);
        ledger.allocate(demand, bottle, MaterialSource.Stock.INSTANCE, PlannerAmount.of(3));
        ledger.allocate(demand, bottle, new MaterialSource.PatternOutput(producer, true), PlannerAmount.of(4));
        ledger.credit(bottle, producer, PlannerAmount.of(3));
        ledger.consumeCredit(demand, bottle, PlannerAmount.of(3));
        ledger.freeze().requireComplete();
        assertEquals(PlannerAmount.ZERO, ledger.remaining(demand));
        var before = ledger.freeze();
        assertThrows(IllegalStateException.class,
            () -> ledger.allocate(demand, bottle, MaterialSource.Stock.INSTANCE, PlannerAmount.ONE));
        assertEquals(before, ledger.freeze());
    }

    @Test
    void failedCreditDrawIsAtomicAndRollbackRestoresCreditAndAssignments() {
        var bottle = AEItemKey.of(Items.GLASS_BOTTLE);
        var producer = mock(IPatternDetails.class);
        var goal = MaterialDemand.goal(bottle, PlannerAmount.of(2));
        var ledger = new MaterialProvenance();
        ledger.register(goal);
        ledger.credit(bottle, producer, PlannerAmount.ONE);
        var checkpoint = ledger.copy();
        assertThrows(IllegalStateException.class, () -> ledger.consumeCredit(goal, bottle, PlannerAmount.of(2)));
        assertEquals(checkpoint.freeze(), ledger.freeze());
        ledger.consumeCredit(goal, bottle, PlannerAmount.ONE);
        ledger.replaceWith(checkpoint);
        assertEquals(PlannerAmount.of(2), ledger.remaining(goal));
        ledger.consumeCredit(goal, bottle, PlannerAmount.ONE);
        assertEquals(1, ledger.freeze().allocations().size());
    }

    @Test
    void mergedPhysicalTaskKeepsSeparateDemandIdentitiesAndExactCounts() {
        var bottle = AEItemKey.of(Items.GLASS_BOTTLE);
        var consumer = mock(IPatternDetails.class);
        var huge = PlannerAmount.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
        var first = MaterialDemand.input(consumer, 0, bottle, huge);
        var second = MaterialDemand.input(consumer, 0, bottle, huge);
        var left = new MaterialProvenance();
        var right = new MaterialProvenance();
        left.register(first);
        right.register(second);
        left.allocate(first, bottle, MaterialSource.Stock.INSTANCE, huge);
        right.allocate(second, bottle, MaterialSource.Stock.INSTANCE, huge);
        left.mergeSuppliersFrom(right);
        left.freeze().requireComplete();
        assertEquals(2, left.freeze().demands().size());
        assertEquals(huge.add(huge), left.freeze().supplierAmountsOf(bottle).get(MaterialSource.Stock.INSTANCE));
    }

    @Test
    void diagnosticTotalsCannotMasqueradeAsCompleteAttribution() {
        var bottle = AEItemKey.of(Items.GLASS_BOTTLE);
        var boundary = MaterialDemand.boundary(7, bottle, PlannerAmount.of(64));
        var ledger = new MaterialProvenance();
        ledger.register(boundary);
        ledger.supplied(bottle, MaterialSource.Stock.INSTANCE, PlannerAmount.of(64));
        assertThrows(IllegalStateException.class, () -> ledger.freeze().requireComplete());
        assertThrows(IllegalStateException.class,
            () -> new ExecutionProvenance(Map.of()).requireComplete());
    }

    @Test
    void mergingTheSameAllocatedDemandTwiceIsRejectedBeforeMutation() {
        var bottle = AEItemKey.of(Items.GLASS_BOTTLE);
        var demand = MaterialDemand.goal(bottle, PlannerAmount.ONE);
        var ledger = new MaterialProvenance();
        ledger.register(demand);
        ledger.allocate(demand, bottle, MaterialSource.Stock.INSTANCE, PlannerAmount.ONE);
        var before = ledger.freeze();
        assertThrows(IllegalStateException.class, () -> ledger.mergeSuppliersFrom(ledger.copy()));
        assertEquals(before, ledger.freeze());
    }

    @Test
    void absentCreditCannotSilentlySatisfyADemand() {
        var ledger = new MaterialProvenance();
        assertThrows(IllegalStateException.class,
            () -> ledger.consumeCredit(AEItemKey.of(Items.GLASS_BOTTLE), PlannerAmount.ONE));
    }
}
