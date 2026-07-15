package cn.dancingsnow.neoecoae.impl.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import java.util.Collection;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Requires a Minecraft/AE2 registry bootstrap; plain Gradle JUnit cannot safely initialize Items.")
class ECOStorageCellNestingTest {
    @Test
    void infiniteStorageDelegatesInsertForNormalKeys() {
        TestInfiniteStorageEngine engine = new TestInfiniteStorageEngine();
        ECOInfiniteStorage storage = new ECOInfiniteStorage(engine, Component.literal("test"));
        AEKey key = AEItemKey.of(Items.STONE);

        long inserted = storage.insert(key, 42L, Actionable.MODULATE, IActionSource.empty());

        assertEquals(42L, inserted);
        assertEquals(1, engine.insertCalls);
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
        public void getAvailableStacks(KeyCounter out) {
        }

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
        public void flushBudgeted(long maxNanos) {
        }

        @Override
        public void closeAndFlush() {
        }
    }
}
