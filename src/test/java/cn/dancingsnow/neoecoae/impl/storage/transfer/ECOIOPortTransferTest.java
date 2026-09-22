package cn.dancingsnow.neoecoae.impl.storage.transfer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.world.item.Items;

class ECOIOPortTransferTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void movesTheEntireKeyAmountInOneTickWithoutPerItemIteration() {
        AEKey key = AEItemKey.of(Items.STONE);
        var host = new CountingStorage(key, 300_000_000L, Long.MAX_VALUE);
        var network = new CountingStorage(key, 0L, Long.MAX_VALUE);
        IStorageService storageService = mock(IStorageService.class);
        when(storageService.getInventory()).thenReturn(network);

        IEnergyService energyService = mock(IEnergyService.class);
        when(energyService.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> call.getArgument(0));

        IGrid grid = mock(IGrid.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(grid.getEnergyService()).thenReturn(energyService);

        long moved = new ECOIOPortTransfer().transfer(grid, host, false, mock(IActionSource.class));

        assertEquals(300_000_000L, moved);
        assertEquals(0L, host.amount);
        assertEquals(300_000_000L, network.amount);
        assertEquals(1, host.modulatedExtracts);
    }

    @Test
    void processesAtMostFiveKeysPerTickAndContinuesFromTheQueue() {
        List<AEKey> keys = List.of(
            AEItemKey.of(Items.STONE),
            AEItemKey.of(Items.DIRT),
            AEItemKey.of(Items.COBBLESTONE),
            AEItemKey.of(Items.SAND),
            AEItemKey.of(Items.GRAVEL),
            AEItemKey.of(Items.GLASS)
        );
        var host = new MultiStorage(keys, 10L);
        var network = new MultiStorage(List.of(), 0L);
        IStorageService storageService = mock(IStorageService.class);
        when(storageService.getInventory()).thenReturn(network);

        IEnergyService energyService = mock(IEnergyService.class);
        when(energyService.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> call.getArgument(0));

        IGrid grid = mock(IGrid.class);
        when(grid.getStorageService()).thenReturn(storageService);
        when(grid.getEnergyService()).thenReturn(energyService);

        var transfer = new ECOIOPortTransfer();
        assertEquals(50L, transfer.transfer(grid, host, false, mock(IActionSource.class)));
        assertEquals(5, host.modulatedExtracts);
        assertEquals(10L, host.total());

        assertEquals(10L, transfer.transfer(grid, host, false, mock(IActionSource.class)));
        assertEquals(6, host.modulatedExtracts);
        assertEquals(0L, host.total());
        assertEquals(60L, network.total());
    }

    private static final class CountingStorage implements MEStorage {
        private final AEKey key;
        private final long capacity;
        private long amount;
        private int modulatedExtracts;

        private CountingStorage(AEKey key, long amount, long capacity) {
            this.key = key;
            this.amount = amount;
            this.capacity = capacity;
        }

        @Override
        public long insert(AEKey what, long requested, Actionable mode, IActionSource source) {
            if (!key.equals(what)) return 0L;
            long accepted = Math.min(requested, capacity - amount);
            if (mode == Actionable.MODULATE) amount += accepted;
            return accepted;
        }

        @Override
        public long extract(AEKey what, long requested, Actionable mode, IActionSource source) {
            if (!key.equals(what)) return 0L;
            long extracted = Math.min(requested, amount);
            if (mode == Actionable.MODULATE) {
                amount -= extracted;
                modulatedExtracts++;
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            if (amount > 0L) out.add(key, amount);
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return net.minecraft.network.chat.Component.empty();
        }
    }

    private static final class MultiStorage implements MEStorage {
        private final Map<AEKey, Long> amounts = new LinkedHashMap<>();
        private int modulatedExtracts;

        private MultiStorage(List<AEKey> keys, long amount) {
            keys.forEach(key -> amounts.put(key, amount));
        }

        private long total() {
            return amounts.values().stream().mapToLong(Long::longValue).sum();
        }

        @Override
        public long insert(AEKey key, long requested, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) amounts.merge(key, requested, Long::sum);
            return requested;
        }

        @Override
        public long extract(AEKey key, long requested, Actionable mode, IActionSource source) {
            long extracted = Math.min(requested, amounts.getOrDefault(key, 0L));
            if (mode == Actionable.MODULATE) {
                amounts.put(key, amounts.getOrDefault(key, 0L) - extracted);
                modulatedExtracts++;
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            amounts.forEach((key, amount) -> {
                if (amount > 0L) out.add(key, amount);
            });
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return net.minecraft.network.chat.Component.empty();
        }
    }
}
