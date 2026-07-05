package cn.dancingsnow.neoecoae.impl.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import java.util.Collection;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Requires a Forge/Minecraft test harness; plain Gradle JUnit cannot bootstrap ItemStack/StorageCells safely.")
class ECOStorageCellNestingTest {
    private static Item emptyCellItem;
    private static Item nonEmptyCellItem;

    @BeforeAll
    static void registerCellHandler() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        emptyCellItem = new Item(new Item.Properties());
        nonEmptyCellItem = new Item(new Item.Properties());
        StorageCells.addCellHandler(new TestCellHandler());
    }

    @Test
    void sharedNestingGuardAllowsOnlyEmptyStorageCells() {
        assertTrue(ECOStorageCell.canStoreKeyInsideStorageCell(AEItemKey.of(Items.STONE)));
        assertTrue(ECOStorageCell.canStoreKeyInsideStorageCell(AEItemKey.of(new ItemStack(emptyCellItem))));
        assertFalse(ECOStorageCell.canStoreKeyInsideStorageCell(AEItemKey.of(new ItemStack(nonEmptyCellItem))));
    }

    @Test
    void infiniteStorageRejectsNonEmptyStorageCellsBeforeEngineInsert() {
        TestInfiniteStorageEngine engine = new TestInfiniteStorageEngine();
        ECOInfiniteStorage storage = new ECOInfiniteStorage(engine, Component.literal("test"));

        long inserted = storage.insert(
                AEItemKey.of(new ItemStack(nonEmptyCellItem)), 42L, Actionable.MODULATE, IActionSource.empty());

        assertEquals(0L, inserted);
        assertEquals(0, engine.insertCalls);
    }

    @Test
    void infiniteStorageDelegatesInsertForNormalKeys() {
        TestInfiniteStorageEngine engine = new TestInfiniteStorageEngine();
        ECOInfiniteStorage storage = new ECOInfiniteStorage(engine, Component.literal("test"));

        long inserted = storage.insert(AEItemKey.of(Items.STONE), 42L, Actionable.MODULATE, IActionSource.empty());

        assertEquals(42L, inserted);
        assertEquals(1, engine.insertCalls);
    }

    private static final class TestCellHandler implements ICellHandler {
        @Override
        public boolean isCell(ItemStack stack) {
            return stack.is(emptyCellItem) || stack.is(nonEmptyCellItem);
        }

        @Override
        public @Nullable StorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
            if (stack.is(emptyCellItem)) {
                return new TestStorageCell(true);
            }
            if (stack.is(nonEmptyCellItem)) {
                return new TestStorageCell(false);
            }
            return null;
        }
    }

    private record TestStorageCell(boolean canFitInsideCell) implements StorageCell {
        @Override
        public CellState getStatus() {
            return canFitInsideCell ? CellState.EMPTY : CellState.NOT_EMPTY;
        }

        @Override
        public double getIdleDrain() {
            return 0;
        }

        @Override
        public boolean canFitInsideCell() {
            return canFitInsideCell;
        }

        @Override
        public void persist() {}

        @Override
        public Component getDescription() {
            return Component.literal("test");
        }
    }

    private static final class TestInfiniteStorageEngine implements ECOInfiniteStorageEngine {
        private int insertCalls;

        @Override
        public long insert(AEKey key, long amount, Actionable mode) {
            insertCalls++;
            return amount;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode) {
            return 0;
        }

        @Override
        public HugeAmount getAmount(AEKey key) {
            return HugeAmount.ZERO;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {}

        @Override
        public long getRevision() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public HugeAmount getStoredAmount() {
            return HugeAmount.ZERO;
        }

        @Override
        public int getStoredTypes() {
            return 0;
        }

        @Override
        public Collection<TypeStats> getTypeStats() {
            return List.of();
        }

        @Override
        public Collection<HugeStack> getHugeStacks() {
            return List.of();
        }

        @Override
        public void flushBudgeted(long maxNanos) {}

        @Override
        public void closeAndFlush() {}
    }
}
