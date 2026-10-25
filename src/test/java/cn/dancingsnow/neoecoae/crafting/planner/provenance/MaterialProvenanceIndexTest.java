package cn.dancingsnow.neoecoae.crafting.planner.provenance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MaterialProvenanceIndexTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void independentMaterialsDoNotScanOtherDemands() {
        int count = 4_000;
        var ledger = new MaterialProvenance();
        for (int i = 0; i < count; i++) ledger.register(MaterialDemand.goal(new CountedKey(i, "a"), PlannerAmount.ONE));
        CountedKey.comparisons = 0;
        long started = System.nanoTime();
        for (int i = 0; i < count; i++) {
            AEKey alias = new CountedKey(i, "a");
            assertEquals(PlannerAmount.ONE, ledger.pendingAmount(alias));
            ledger.allocatePending(alias, alias, MaterialSource.Stock.INSTANCE, PlannerAmount.ONE);
            assertEquals(PlannerAmount.ZERO, ledger.pendingAmount(alias));
        }
        long comparisons = CountedKey.comparisons;
        System.out.println("PROVENANCE_INDEX distinctKeys=" + count + " comparisons=" + comparisons
            + " elapsedMs=" + (System.nanoTime() - started) / 1_000_000.0);
        assertTrue(comparisons < count * 12L, "Key comparisons must grow linearly, actual=" + comparisons);
        ledger.freeze().requireComplete();
    }

    @Test void repeatedDrawsSkipAlreadyCompletedDemands() {
        int count = 4_000;
        AEKey key = new CountedKey(7, "fluid-components");
        var ledger = new MaterialProvenance();
        for (int i = 0; i < count; i++) ledger.register(MaterialDemand.goal(key, PlannerAmount.ONE));
        CountedKey.comparisons = 0;
        for (int i = 0; i < count; i++) ledger.allocatePending(key, key, MaterialSource.Stock.INSTANCE, PlannerAmount.ONE);
        assertTrue(CountedKey.comparisons < count * 12L, "Completed demands must leave the pending queue");
        ledger.freeze().requireComplete();
    }

    @Test void exactKeysAndRegistrationOrderSurvivePartialAndDirectAllocation() {
        AEKey firstKey = new CountedKey(1, "first-nbt");
        AEKey otherKey = new CountedKey(1, "other-nbt");
        var first = MaterialDemand.goal(firstKey, PlannerAmount.of(5));
        var unrelated = MaterialDemand.goal(otherKey, PlannerAmount.of(9));
        var second = MaterialDemand.goal(firstKey, PlannerAmount.of(7));
        var ledger = new MaterialProvenance();
        ledger.register(first);
        ledger.register(unrelated);
        ledger.register(second);
        ledger.register(first);
        assertEquals(PlannerAmount.of(12), ledger.pendingAmount(firstKey));
        ledger.allocatePending(firstKey, firstKey, MaterialSource.Stock.INSTANCE, PlannerAmount.of(8));
        var assignments = ledger.freeze().allocations();
        assertEquals(List.of(first.id(), second.id()), assignments.stream().map(SupplyAllocation::demandId).toList());
        assertEquals(List.of(PlannerAmount.of(5), PlannerAmount.of(3)), assignments.stream().map(SupplyAllocation::amount).toList());
        assertEquals(PlannerAmount.of(9), ledger.pendingAmount(otherKey));
        ledger.allocate(second, firstKey, MaterialSource.Stock.INSTANCE, PlannerAmount.of(4));
        ledger.allocate(unrelated, otherKey, MaterialSource.Stock.INSTANCE, PlannerAmount.of(9));
        assertEquals(PlannerAmount.ZERO, ledger.pendingAmount(firstKey));
        ledger.register(first);
        assertEquals(PlannerAmount.ZERO, ledger.pendingAmount(firstKey));
        ledger.freeze().requireComplete();
    }

    @Test void copyingMergingAndRestoringKeepIndependentExactTotals() {
        AEKey key = new CountedKey(2, "wide");
        PlannerAmount huge = PlannerAmount.of(BigInteger.ONE.shiftLeft(90));
        var demand = MaterialDemand.goal(key, huge);
        var left = new MaterialProvenance();
        var right = new MaterialProvenance();
        left.register(demand);
        right.register(demand);
        left.allocate(demand, key, MaterialSource.Stock.INSTANCE, PlannerAmount.of(3));
        right.allocate(demand, key, MaterialSource.Stock.INSTANCE, PlannerAmount.of(5));
        var checkpoint = left.copy();
        left.mergeSuppliersFrom(right);
        assertEquals(huge.subtract(8), left.pendingAmount(key));
        assertEquals(huge.subtract(5), right.pendingAmount(key));
        left.replaceWith(checkpoint);
        assertEquals(huge.subtract(3), left.pendingAmount(key));
        assertEquals(checkpoint.freeze(), left.freeze());
        left.allocatePending(key, key, MaterialSource.Stock.INSTANCE, huge.subtract(3));
        assertEquals(PlannerAmount.ZERO, left.pendingAmount(key));
        assertEquals(huge.subtract(3), checkpoint.pendingAmount(key));
        left.freeze().requireComplete();
    }

    @Test void partialGoalCanBecomeAnIndependentlyAllocatedCycleBoundary() {
        AEKey key = new CountedKey(3, "boundary");
        var goal = MaterialDemand.goal(key, PlannerAmount.of(10));
        var ledger = new MaterialProvenance();
        ledger.register(goal);
        ledger.allocate(goal, key, MaterialSource.Stock.INSTANCE, PlannerAmount.of(4));
        var boundaryLedger = ledger.forCycleBoundary(12);
        boundaryLedger.allocatePending(key, key, new MaterialSource.CycleOutput(12), PlannerAmount.of(6));
        boundaryLedger.freeze().requireComplete();
        assertEquals(MaterialDemand.Kind.CYCLE_BOUNDARY, boundaryLedger.freeze().demands().get(goal.id()).kind());
        assertEquals(PlannerAmount.of(6), ledger.pendingAmount(key));
    }

    @Test void creditDrawsRemainAtomicAndKeepTheirOriginalSources() {
        AEKey key = new CountedKey(4, "credit");
        var first = MaterialDemand.goal(key, PlannerAmount.of(3));
        var second = MaterialDemand.goal(key, PlannerAmount.of(4));
        var a = mock(IPatternDetails.class);
        var b = mock(IPatternDetails.class);
        var ledger = new MaterialProvenance();
        ledger.register(first);
        ledger.register(second);
        ledger.credit(key, a, PlannerAmount.of(2));
        ledger.credit(key, b, PlannerAmount.of(5));
        var before = ledger.copy();
        assertThrows(IllegalStateException.class, () -> ledger.consumePendingCredit(key, PlannerAmount.of(8)));
        assertEquals(before.freeze(), ledger.freeze());
        ledger.consumePendingCredit(key, PlannerAmount.of(3));
        ledger.consumeCredit(second, key, PlannerAmount.of(4));
        var frozen = ledger.freeze();
        frozen.requireComplete();
        assertEquals(PlannerAmount.ZERO, ledger.pendingAmount(key));
        assertEquals(PlannerAmount.of(2), frozen.supplierAmountsOf(key).get(new MaterialSource.PatternOutput(a, false)));
        assertEquals(PlannerAmount.of(5), frozen.supplierAmountsOf(key).get(new MaterialSource.PatternOutput(b, false)));
        assertEquals(PlannerAmount.of(7), before.pendingAmount(key));
    }

    /** Counts complete-key equality work without relying on wall-clock timing or a JVM profiler. */
    static final class CountedKey extends AEKey {
        static long comparisons;
        final int id;
        final String components;
        CountedKey(int id, String components) { this.id = id; this.components = components; }
        @Override public boolean equals(Object other) {
            comparisons++;
            return other instanceof CountedKey key && id == key.id && components.equals(key.components);
        }
        @Override public int hashCode() { return 31 * id + components.hashCode(); }
        @Override public AEKeyType getType() { return AEKeyType.items(); }
        @Override public AEKey dropSecondary() { return new CountedKey(id, ""); }
        @Override public Object getPrimaryKey() { return id; }
        @Override public ResourceLocation getId() { return new ResourceLocation("test", "key_" + id); }
        @Override protected Component computeDisplayName() { return Component.literal("key_" + id); }
        @Override public CompoundTag toTag() { throw new UnsupportedOperationException(); }
        @Override public void writeToPacket(FriendlyByteBuf buffer) { throw new UnsupportedOperationException(); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
            throw new UnsupportedOperationException();
        }
    }
}
