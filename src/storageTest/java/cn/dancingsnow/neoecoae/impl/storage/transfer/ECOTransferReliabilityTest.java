package cn.dancingsnow.neoecoae.impl.storage.transfer;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.impl.storage.StorageTestKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class ECOTransferReliabilityTest {
    private static class MemoryStorage implements MEStorage {
        final Map<AEKey, Long> amounts = new HashMap<>();
        AEKey onlyAccepted;
        AEKey throwing;
        int failedCalls;
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (onlyAccepted != null && !onlyAccepted.equals(key)) return 0L;
            if (mode == Actionable.MODULATE) {
                amounts.merge(key, amount, Long::sum);
                if (key.equals(throwing)) { failedCalls++; throw new IllegalStateException("Injected after insertion"); }
            }
            return amount;
        }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long available = amounts.getOrDefault(key, 0L);
            long extracted = Math.min(amount, available);
            if (mode == Actionable.MODULATE) amounts.put(key, available - extracted);
            return extracted;
        }
        @Override public void getAvailableStacks(KeyCounter out) { amounts.forEach(out::add); }
        @Override public Component getDescription() { return Component.literal("Test storage"); }
    }

    private long tick(ECOGenericTransfer transfer, MemoryStorage from, MemoryStorage to, long tick, long budget, List<String> failures) {
        return transfer.tick(from, to, IActionSource.empty(), false, tick, 64, 1_000_000_000L, budget, failures::add);
    }

    @Test
    void blockedFirstKeysCannotStarveTheEndOfTheSnapshot() {
        var from = new MemoryStorage();
        var to = new MemoryStorage();
        for (int i = 0; i < 512; i++) from.amounts.put(new StorageTestKey("key" + i), 10L);
        KeyCounter order = new KeyCounter();
        from.getAvailableStacks(order);
        for (var entry : order) to.onlyAccepted = entry.getKey();
        var transfer = new ECOGenericTransfer();
        List<String> failures = new ArrayList<>();
        long moved = 0L;
        for (int tick = 0; tick < 16; tick++) moved += tick(transfer, from, to, tick, 10L, failures);
        assertEquals(10L, moved);
        assertTrue(failures.isEmpty());
    }

    @Test
    void singleKeyCannotExceedTheRemainingTickAmountBudget() {
        var key = new StorageTestKey("rate");
        var from = new MemoryStorage();
        var to = new MemoryStorage();
        from.amounts.put(key, 100L);
        var transfer = new ECOGenericTransfer();
        assertEquals(7L, tick(transfer, from, to, 0L, 7L, new ArrayList<>()));
        assertEquals(7L, tick(transfer, from, to, 1L, 7L, new ArrayList<>()));
        assertEquals(86L, from.amounts.get(key));
        assertEquals(14L, to.amounts.get(key));
    }

    @Test
    void uncertainMutationIsNotRetriedWhileUnrelatedKeysContinue() {
        var broken = new StorageTestKey("broken");
        var healthy = new StorageTestKey("healthy");
        var from = new MemoryStorage();
        var to = new MemoryStorage();
        from.amounts.put(broken, 10L);
        from.amounts.put(healthy, 20L);
        to.throwing = broken;
        List<String> failures = new ArrayList<>();
        var transfer = new ECOGenericTransfer();
        for (int tick = 0; tick < 250; tick++) tick(transfer, from, to, tick, 100L, failures);
        assertEquals(1, to.failedCalls);
        assertEquals(1, failures.size());
        assertEquals(20L, to.amounts.get(healthy));
        assertEquals(0L, from.amounts.get(healthy));
    }

    @Test
    void deferredControllerGetsNextServerBudgetTurn() {
        Object server = new Object();
        Object first = new Object();
        Object second = new Object();
        assertEquals(2_000_000L, ECOStorageTickBudget.allowance(server, first, 1L, 2_000_000L));
        ECOStorageTickBudget.spent(server, 4_000_000L);
        assertEquals(0L, ECOStorageTickBudget.allowance(server, second, 1L, 2_000_000L));
        assertEquals(0L, ECOStorageTickBudget.allowance(server, first, 2L, 2_000_000L));
        assertEquals(2_000_000L, ECOStorageTickBudget.allowance(server, second, 2L, 2_000_000L));
        ECOStorageTickBudget.clear(server);
    }
}
