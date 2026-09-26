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

    @Test
    void skipsUnchangedCreativeInputButAllowsItWhenDisabled() {
        AEKey key = AEItemKey.of(Items.STONE);
        var network = new CountingStorage(key, 100L, Long.MAX_VALUE);
        network.creative = true;
        var host = new CountingStorage(key, 0L, Long.MAX_VALUE);
        var grid = inputGrid(network);
        var transfer = new ECOIOPortTransfer();
        assertEquals(0L, transfer.transfer(grid, host, true, true, IActionSource.empty()));
        assertEquals(0L, host.amount);
        assertEquals(100L, network.amount);
        assertEquals(1, network.modulatedExtracts);
        assertEquals(100L, transfer.transfer(grid, host, true, false, IActionSource.empty()));
        assertEquals(100L, host.amount);
        assertEquals(100L, network.amount);
    }

    @Test
    void detectsFiniteInputFromLiveInventoryEvenWhenGridCacheIsStale() {
        AEKey key = AEItemKey.of(Items.STONE);
        var network = new CountingStorage(key, 100L, Long.MAX_VALUE);
        var host = new CountingStorage(key, 0L, 30L);
        var grid = inputGrid(network);
        assertEquals(30L, new ECOIOPortTransfer().transfer(grid, host, true, true, IActionSource.empty()));
        assertEquals(30L, host.amount);
        assertEquals(70L, network.amount);
        assertEquals(100L, grid.getStorageService().getCachedInventory().get(key));
    }

    @Test
    void reevaluatesCreativeSourceWhenItBecomesFinite() {
        AEKey key = AEItemKey.of(Items.STONE);
        var network = new CountingStorage(key, 100L, Long.MAX_VALUE);
        network.creative = true;
        var host = new CountingStorage(key, 0L, Long.MAX_VALUE);
        var grid = inputGrid(network);
        var transfer = new ECOIOPortTransfer();
        assertEquals(0L, transfer.transfer(grid, host, true, true, IActionSource.empty()));
        network.creative = false;
        assertEquals(100L, transfer.transfer(grid, host, true, true, IActionSource.empty()));
        assertEquals(0L, network.amount);
        assertEquals(100L, host.amount);
    }

    @Test
    void ignoreSettingDoesNotFilterOutputMode() {
        AEKey key = AEItemKey.of(Items.STONE);
        var network = new CountingStorage(key, 0L, Long.MAX_VALUE);
        var host = new CountingStorage(key, 100L, Long.MAX_VALUE);
        host.creative = true;
        assertEquals(100L, new ECOIOPortTransfer().transfer(
            inputGrid(network), host, false, true, IActionSource.empty()));
        assertEquals(100L, network.amount);
    }

    @Test
    void finiteInputIsReturnedWhenThereIsNoInsertionEnergy() {
        AEKey key = AEItemKey.of(Items.STONE);
        var network = new CountingStorage(key, 100L, Long.MAX_VALUE);
        var host = new CountingStorage(key, 0L, Long.MAX_VALUE);
        var grid = inputGrid(network);
        when(grid.getEnergyService().extractAEPower(anyDouble(), any(), any())).thenReturn(0D);
        assertEquals(0L, new ECOIOPortTransfer().transfer(grid, host, true, true, IActionSource.empty()));
        assertEquals(100L, network.amount);
        assertEquals(0L, host.amount);
    }

    private static IGrid inputGrid(MEStorage network) {
        IGrid grid = mock(IGrid.class);
        IStorageService storage = mock(IStorageService.class);
        IEnergyService energy = mock(IEnergyService.class);
        KeyCounter cached = new KeyCounter();
        network.getAvailableStacks(cached);
        when(storage.getInventory()).thenReturn(network);
        when(storage.getCachedInventory()).thenReturn(cached);
        when(grid.getStorageService()).thenReturn(storage);
        when(grid.getEnergyService()).thenReturn(energy);
        when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> call.getArgument(0));
        return grid;
    }

    @Test
    void mixedCreativeAndFiniteMountsOnlyTransferFiniteStockInEitherPriorityOrder() {
        AEKey key = AEItemKey.of(Items.STONE);
        for (boolean creativeFirst : List.of(true, false)) {
            var creative = new CountingStorage(key, Long.MAX_VALUE, Long.MAX_VALUE);
            creative.creative = true;
            var finite = new CountingStorage(key, 30L, Long.MAX_VALUE);
            var host = new CountingStorage(key, 0L, 100L);
            var mounts = creativeFirst ? List.of(creative, finite) : List.of(finite, creative);
            MEStorage network = mock(MEStorage.class);
            org.mockito.Mockito.doAnswer(call -> {
                // The network total stays saturated even after extracting all finite stock.
                ((KeyCounter) call.getArgument(0)).set(key, Long.MAX_VALUE);
                return null;
            }).when(network).getAvailableStacks(any(KeyCounter.class));
            when(network.extract(any(), org.mockito.ArgumentMatchers.anyLong(), any(), any())).thenAnswer(call -> {
                long requested = call.getArgument(1);
                Actionable mode = call.getArgument(2);
                IActionSource source = call.getArgument(3);
                long extracted = 0;
                for (var mount : mounts) {
                    long remaining = requested - extracted;
                    if (remaining <= 0) break;
                    extracted += ECOCreativeExtractionFilter.extractSource(mount, key, mode,
                        () -> mount.extract(key, remaining, mode, source));
                }
                return extracted;
            });
            assertEquals(30L, new ECOIOPortTransfer().transfer(
                inputGrid(network), host, true, true, IActionSource.empty()));
            assertEquals(30L, host.amount);
            assertEquals(0L, finite.amount);
            assertEquals(Long.MAX_VALUE, creative.amount);
            // The thread-local filter must not affect subsequent ordinary extraction.
            assertEquals(1L, network.extract(key, 1L, Actionable.MODULATE, IActionSource.empty()));
        }
    }

    @Test
    void exactFiniteSourceAboveLongIsNotMistakenForCreativeStorage() {
        AEKey key = AEItemKey.of(Items.STONE);
        java.math.BigInteger[] remaining = {java.math.BigInteger.valueOf(Long.MAX_VALUE).multiply(java.math.BigInteger.TEN)};
        class HugeStorage implements MEStorage, cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource {
            @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
                if (mode == Actionable.MODULATE) remaining[0] = remaining[0].subtract(java.math.BigInteger.valueOf(amount));
                return amount;
            }
            @Override public void getAvailableStacks(KeyCounter out) { out.set(key, Long.MAX_VALUE); }
            @Override public net.minecraft.network.chat.Component getDescription() {
                return net.minecraft.network.chat.Component.empty();
            }
            @Override public void neoecoae$visitExactAmounts(java.util.function.BiConsumer<AEKey,
                    cn.dancingsnow.neoecoae.crafting.amount.ExactAmount> visitor) {
                visitor.accept(key, cn.dancingsnow.neoecoae.crafting.amount.ExactAmount.finite(remaining[0]));
            }
        }
        var host = new CountingStorage(key, 0L, 100L);
        assertEquals(100L, new ECOIOPortTransfer().transfer(
            inputGrid(new HugeStorage()), host, true, true, IActionSource.empty()));
        assertEquals(100L, host.amount);
    }

    private static final class CountingStorage implements MEStorage {
        private final AEKey key;
        private final long capacity;
        private long amount;
        private int modulatedExtracts;
        private boolean creative;

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
                if (!creative) amount -= extracted;
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
