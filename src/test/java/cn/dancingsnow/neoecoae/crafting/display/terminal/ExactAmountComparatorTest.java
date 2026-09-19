package cn.dancingsnow.neoecoae.crafting.display.terminal;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.SortDir;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.menu.me.common.GridInventoryEntry;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ExactAmountComparatorTest {
    @Test
    void sortsQuantitiesBeyondLongAndDoubleRangeInBothDirections() {
        var smaller = entry(1, Long.MAX_VALUE, 1);
        var larger = entry(2, Long.MAX_VALUE, 1);
        var ordinary = entry(3, 64, 1);
        BigInteger huge = BigInteger.TEN.pow(400);
        var amounts = Map.of(smaller.getWhat(), huge, larger.getWhat(), huge.add(BigInteger.ONE));

        var entries = new ArrayList<>(List.of(larger, ordinary, smaller));
        entries.sort(ExactAmountComparator.create(amounts, SortDir.ASCENDING));
        assertEquals(List.of(ordinary, smaller, larger), entries);
        entries.sort(ExactAmountComparator.create(amounts, SortDir.DESCENDING));
        assertEquals(List.of(larger, smaller, ordinary), entries);
        assertEquals(Long.MAX_VALUE, smaller.getStoredAmount());
        assertEquals(Long.MAX_VALUE, larger.getStoredAmount());
    }

    @Test
    void comparesItemsAndFluidsInDisplayUnitsWithoutDiscardingRemainders() {
        var item = entry(1, Long.MAX_VALUE, 1);
        var fluid = entry(2, Long.MAX_VALUE, 1000);
        BigInteger huge = BigInteger.TEN.pow(100);
        var amounts = Map.of(
                item.getWhat(),
                huge,
                fluid.getWhat(),
                huge.multiply(BigInteger.valueOf(1000)).add(BigInteger.ONE));

        assertTrue(ExactAmountComparator.create(amounts, SortDir.ASCENDING).compare(item, fluid) < 0);
        assertTrue(ExactAmountComparator.create(amounts, SortDir.DESCENDING).compare(item, fluid) > 0);
    }

    @Test
    void anExactFluidAmountCanSortBelowAnOrdinaryItemAmount() {
        var item = entry(1, Long.MAX_VALUE, 1);
        var fluid = entry(2, Long.MAX_VALUE, 1000);
        var amounts = Map.of(fluid.getWhat(), BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));

        assertTrue(ExactAmountComparator.create(amounts, SortDir.ASCENDING).compare(fluid, item) < 0);
        assertTrue(ExactAmountComparator.create(amounts, SortDir.DESCENDING).compare(fluid, item) > 0);
    }

    @Test
    void fallsBackToStoredAmountsForEntriesWithoutAnExactSnapshot() {
        var smaller = entry(1, 1L << 53, 1);
        var larger = entry(2, (1L << 53) + 1, 1);
        var comparator = ExactAmountComparator.create(Map.of(), SortDir.ASCENDING);

        assertTrue(comparator.compare(smaller, larger) < 0);
        assertTrue(comparator.compare(entry(3, 1500, 1000), entry(4, 2, 1)) < 0);
    }

    @Test
    void equalNormalizedAmountsRemainTied() {
        var item = entry(1, Long.MAX_VALUE, 1);
        var fluid = entry(2, Long.MAX_VALUE, 1000);
        BigInteger huge = BigInteger.TEN.pow(100);
        var amounts = Map.of(item.getWhat(), huge, fluid.getWhat(), huge.multiply(BigInteger.valueOf(1000)));

        for (SortDir direction : SortDir.values()) {
            assertEquals(0, ExactAmountComparator.create(amounts, direction).compare(item, fluid));
        }
    }

    @Test
    void usesStoredQuantityForCraftableAndRequestableEntries() {
        var key = new TestKey("craftable", 1);
        var craftable = new GridInventoryEntry(1, key, 0, Long.MAX_VALUE, true);
        var stored = entry(2, 1, 1);

        assertTrue(ExactAmountComparator.create(Map.of(), SortDir.ASCENDING).compare(craftable, stored) < 0);
    }

    @Test
    void changedSnapshotsReorderUnchangedSaturatedEntriesWithoutSharingState() {
        var first = entry(1, Long.MAX_VALUE, 1);
        var second = entry(2, Long.MAX_VALUE, 1);
        BigInteger huge = BigInteger.TEN.pow(100);
        var previous = ExactAmountComparator.create(
                Map.of(first.getWhat(), huge, second.getWhat(), huge.add(BigInteger.ONE)), SortDir.ASCENDING);
        var updated = ExactAmountComparator.create(
                Map.of(first.getWhat(), huge.add(BigInteger.TWO), second.getWhat(), huge.add(BigInteger.ONE)),
                SortDir.ASCENDING);

        assertTrue(previous.compare(first, second) < 0);
        assertTrue(updated.compare(first, second) > 0);
        assertTrue(previous.compare(first, second) < 0, "Comparators belong to their own menu snapshot");
        assertEquals(
                0, ExactAmountComparator.create(Map.of(), SortDir.ASCENDING).compare(first, second));
    }

    private static GridInventoryEntry entry(long serial, long stored, int amountPerUnit) {
        return new GridInventoryEntry(serial, new TestKey("entry_" + serial, amountPerUnit), stored, 0, false);
    }

    private static final class TestKey extends AEKey {
        private final String id;
        private final AEKeyType type;

        private TestKey(String id, int amountPerUnit) {
            this.id = id;
            this.type = new TestKeyType(amountPerUnit);
        }

        @Override
        public AEKeyType getType() {
            return type;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag() {
            return new CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.fromNamespaceAndPath("test", id);
        }

        @Override
        public void writeToPacket(FriendlyByteBuf buffer) {}

        @Override
        protected Component computeDisplayName() {
            return Component.literal(id);
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    }

    private static final class TestKeyType extends AEKeyType {
        private final int amountPerUnit;

        private TestKeyType(int amountPerUnit) {
            super(
                    ResourceLocation.fromNamespaceAndPath("test", "unit_" + amountPerUnit),
                    TestKey.class,
                    Component.empty());
            this.amountPerUnit = amountPerUnit;
        }

        @Override
        public int getAmountPerUnit() {
            return amountPerUnit;
        }

        @Override
        public AEKey readFromPacket(FriendlyByteBuf input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AEKey loadKeyFromTag(CompoundTag tag) {
            throw new UnsupportedOperationException();
        }
    }
}
