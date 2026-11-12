package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CombinedStorageAmountTest {
    @Test void cachedInsertionOrderTracksPriorityChangesAndKeepsTieOrder() {
        var key = mock(AEKey.class);
        var source = appeng.api.networking.security.IActionSource.empty();
        var first = mock(cn.dancingsnow.neoecoae.api.storage.IECOStorageCell.class);
        var second = mock(cn.dancingsnow.neoecoae.api.storage.IECOStorageCell.class);
        var priority = new java.util.concurrent.atomic.AtomicInteger(5);
        when(first.getStoragePriority()).thenAnswer(call -> priority.get());
        when(second.getStoragePriority()).thenReturn(10);
        when(first.insert(eq(key), anyLong(), any(), any())).thenAnswer(call -> call.getArgument(1));
        when(second.insert(eq(key), anyLong(), any(), any())).thenAnswer(call -> call.getArgument(1));
        var storage = new ECOStorageInterfaceTransfer.CombinedStorage(List.of(first, second), Component.empty());
        assertEquals(4, storage.insert(key, 4, appeng.api.config.Actionable.MODULATE, source));
        verify(second).insert(eq(key), eq(4L), any(), any());
        verify(first, never()).insert(any(), anyLong(), any(), any());
        clearInvocations(first, second);
        priority.set(10);
        assertEquals(3, storage.insert(key, 3, appeng.api.config.Actionable.MODULATE, source));
        verify(first).insert(eq(key), eq(3L), any(), any());
        verify(second, never()).insert(any(), anyLong(), any(), any());
        priority.set(-1);
        assertEquals(2, storage.insert(key, 2, appeng.api.config.Actionable.MODULATE, source));
        verify(second).insert(eq(key), eq(2L), any(), any());
    }

    @Test void infiniteResourceAndOrdinaryCellNeverWrapInEitherOrder() {
        AEKey key = mock(AEKey.class);
        when(key.getPrimaryKey()).thenReturn(new Object());
        when(key.getFuzzySearchMaxValue()).thenReturn(0);
        MEStorage infinite = listing(key, Long.MAX_VALUE);
        MEStorage ordinary = listing(key, 64);
        for (List<MEStorage> order : List.of(List.of(infinite, ordinary), List.of(ordinary, infinite))) {
            var storage = new ECOStorageInterfaceTransfer.CombinedStorage(order, Component.empty());
            assertEquals(Long.MAX_VALUE, storage.getAvailableStacks().get(key));
        }
    }

    @Test void mixedHostPreservesInfinityAndTheExactFiniteTotal() {
        AEKey key = mock(AEKey.class);
        when(key.getPrimaryKey()).thenReturn(new Object());
        when(key.getFuzzySearchMaxValue()).thenReturn(0);
        var infinite = mock(MEStorage.class, withSettings().extraInterfaces(ExactAmountSource.class));
        doAnswer(call -> {
            KeyCounter out = call.getArgument(0);
            out.set(key, Long.MAX_VALUE);
            return null;
        }).when(infinite).getAvailableStacks(any());
        doAnswer(call -> {
            java.util.function.BiConsumer<AEKey, ExactAmount> visitor = call.getArgument(0);
            visitor.accept(key, ExactAmount.unbounded());
            return null;
        }).when((ExactAmountSource) infinite).neoecoae$visitExactAmounts(any());
        var ordinary = listing(key, 64);
        for (List<MEStorage> order : List.of(List.of(infinite, ordinary), List.of(ordinary, infinite))) {
            var storage = new ECOStorageInterfaceTransfer.CombinedStorage(order, Component.empty());
            ExactAmountCollector.begin();
            try {
                var out = new KeyCounter();
                ExactAmountCollector.collect(storage, out, storage::getAvailableStacks);
                assertEquals(Long.MAX_VALUE, out.get(key));
                assertEquals(Map.of(key, ExactAmount.unbounded()), ExactAmountCollector.finish());
            } finally { ExactAmountCollector.abort(); }
        }
        var finite = new ECOStorageInterfaceTransfer.CombinedStorage(
            List.of(listing(key, Long.MAX_VALUE), ordinary), Component.empty());
        Map<AEKey, ExactAmount> amounts = new HashMap<>();
        finite.neoecoae$visitExactAmounts(amounts::put);
        assertEquals(Map.of(key, ExactAmount.finite(BigInteger.valueOf(Long.MAX_VALUE)
            .add(BigInteger.valueOf(64)))), amounts);
    }

    private static MEStorage listing(AEKey key, long amount) {
        MEStorage storage = mock(MEStorage.class);
        doAnswer(call -> {
            appeng.api.stacks.KeyCounter out = call.getArgument(0);
            out.add(key, amount);
            return null;
        }).when(storage).getAvailableStacks(any());
        return storage;
    }
}
