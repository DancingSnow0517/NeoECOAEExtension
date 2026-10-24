package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import cn.dancingsnow.neoecoae.mixins.ae2.storage.NetworkStorageMixin;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorageBusTransferTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void inputKeepsTheBusStockOfTheSameKeyAndCanBeOptedBackIn() {
        for (boolean busFirst : List.of(true, false)) {
            Stock disk = new Stock(30, Long.MAX_VALUE);
            BusStock bus = new BusStock(70, Long.MAX_VALUE);
            Stock host = new Stock(0, Long.MAX_VALUE);
            IGrid grid = grid(new MountedNetwork(busFirst ? List.of(bus, disk) : List.of(disk, bus)));
            ECOIOPortTransfer transfer = new ECOIOPortTransfer();
            assertEquals(30, transfer.transfer(grid, host, true, IActionSource.empty()));
            assertEquals(30, host.amount);
            assertEquals(0, disk.amount);
            assertEquals(70, bus.amount);
            assertEquals(70, transfer.transfer(grid, host, true, false, false, IActionSource.empty(), key -> true));
            assertEquals(100, host.amount);
            assertEquals(0, bus.amount);
        }
    }

    @Test void outputSkipsTheBusDuringSimulationAndKeepsOverflowInTheHost() {
        Stock disk = new Stock(0, 10);
        BusStock bus = new BusStock(0, Long.MAX_VALUE);
        Stock host = new Stock(40, Long.MAX_VALUE);
        IGrid grid = grid(new MountedNetwork(List.of(bus, disk)));
        ECOIOPortTransfer transfer = new ECOIOPortTransfer();
        assertEquals(10, transfer.transfer(grid, host, false, IActionSource.empty()));
        assertEquals(30, host.amount);
        assertEquals(10, disk.amount);
        assertEquals(0, bus.amount);
        assertEquals(0, transfer.transfer(grid, host, false, IActionSource.empty()));
        assertEquals(1, host.extractions);
        assertEquals(30, transfer.transfer(grid, host, false, false, false, IActionSource.empty(), key -> true));
        assertEquals(0, host.amount);
        assertEquals(30, bus.amount);
    }

    @Test void failedPoweredInsertionReturnsStockWithoutTouchingTheBus() {
        Stock disk = new Stock(30, Long.MAX_VALUE);
        BusStock bus = new BusStock(70, Long.MAX_VALUE);
        Stock host = new Stock(0, Long.MAX_VALUE);
        IGrid grid = grid(new MountedNetwork(List.of(bus, disk)));
        when(grid.getEnergyService().extractAEPower(anyDouble(), any(), any())).thenReturn(0D);
        assertEquals(0, new ECOIOPortTransfer().transfer(grid, host, true, IActionSource.empty()));
        assertEquals(30, disk.amount);
        assertEquals(70, bus.amount);
        assertEquals(0, host.amount);
        assertEquals(0, bus.extractions);
    }

    @Test void busOnlyKeysDoNotConsumeThePerTickKeyBudget() {
        List<AEKey> keys = List.of(AEItemKey.of(Items.DIRT), AEItemKey.of(Items.SAND),
            AEItemKey.of(Items.GRAVEL), AEItemKey.of(Items.GLASS), AEItemKey.of(Items.COBBLESTONE),
            AEItemKey.of(Items.DIAMOND));
        List<MEStorage> mounts = new java.util.ArrayList<>();
        for (AEKey key : keys) mounts.add(new BusStock(key, 70, Long.MAX_VALUE));
        Stock disk = new Stock(30, Long.MAX_VALUE);
        mounts.add(disk);
        Stock host = new Stock(0, Long.MAX_VALUE);
        assertEquals(30, new ECOIOPortTransfer().transfer(grid(new MountedNetwork(mounts)),
            host, true, IActionSource.empty()));
        assertEquals(30, host.amount);
    }

    @Test void nestedNetworksAreFilteredAndOrdinaryNetworkAccessIsRestored() {
        Stock disk = new Stock(30, Long.MAX_VALUE);
        BusStock bus = new BusStock(70, Long.MAX_VALUE);
        MountedNetwork network = new MountedNetwork(List.of(new MountedNetwork(List.of(bus, disk))));
        Stock host = new Stock(0, Long.MAX_VALUE);
        assertEquals(30, new ECOIOPortTransfer().transfer(grid(network), host, true, IActionSource.empty()));
        assertEquals(70, bus.amount);
        assertEquals(70, network.extract(AEItemKey.of(Items.STONE), 70, Actionable.MODULATE, IActionSource.empty()));
        assertEquals(0, bus.amount);
    }

    @Test void exceptionsAndNestedScopesRestoreTheFilter() {
        BusStock bus = new BusStock(70, Long.MAX_VALUE);
        Stock disk = new Stock(30, Long.MAX_VALUE);
        assertFalse(StorageBusTransferFilter.excludes(bus));
        assertThrows(IllegalStateException.class, () -> StorageBusTransferFilter.transfer(true, () -> {
            assertTrue(StorageBusTransferFilter.excludes(bus));
            assertFalse(StorageBusTransferFilter.excludes(disk));
            StorageBusTransferFilter.transfer(false, () -> {
                assertTrue(StorageBusTransferFilter.excludes(bus));
                return 0;
            });
            throw new IllegalStateException("test");
        }));
        assertFalse(StorageBusTransferFilter.excludes(bus));
    }

    @Test void markerTargetAndNetworkCallSitesMatchTheActualAe2Dependency() throws Exception {
        String target = "appeng/parts/storagebus/StorageBusPart$StorageBusInventory";
        ClassNode bus = bytecode(target);
        assertEquals("appeng/me/storage/MEInventoryHandler", bus.superName);
        ClassNode part = bytecode("appeng/parts/storagebus/StorageBusPart");
        assertTrue(part.methods.stream().filter(m -> m.name.equals("mountInventories"))
            .flatMap(m -> java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false))
            .anyMatch(i -> i instanceof FieldInsnNode && ((FieldInsnNode) i).desc.equals("L" + target + ";")));
        ClassNode network = bytecode(NetworkStorage.class.getName().replace('.', '/'));
        for (String method : List.of("insert", "extract", "getAvailableStacks")) {
            long calls = network.methods.stream().filter(m -> m.name.equals(method))
                .flatMap(m -> java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false))
                .filter(i -> i instanceof MethodInsnNode && ((MethodInsnNode) i).owner.equals("appeng/api/storage/MEStorage")
                    && ((MethodInsnNode) i).name.equals(method)).count();
            assertEquals(method.equals("insert") ? 2 : 1, calls, method);
        }
        ClassNode marker = bytecode("cn/dancingsnow/neoecoae/mixins/ae2/storage/StorageBusInventoryMixin");
        assertTrue(marker.interfaces.contains(StorageBusTransferFilter.StorageBusInventory.class.getName().replace('.', '/')));
        assertTrue(marker.invisibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")
            && a.values.toString().contains(target.replace('/', '.'))));
    }

    private static ClassNode bytecode(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = StorageBusTransferTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(stream, name);
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }

    private static IGrid grid(MEStorage network) {
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

    /** Exercises the production Mixin hooks without requiring a launched Minecraft JVM. */
    private static final class MountedNetwork implements MEStorage {
        final List<? extends MEStorage> mounts;
        final NetworkStorageMixin hooks = new NetworkStorageMixin() {};
        MountedNetwork(List<? extends MEStorage> mounts) { this.mounts = mounts; }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            long remaining = amount;
            for (MEStorage mount : mounts) {
                remaining -= call("neoecoae$excludeStorageBusOutput", mount, key, remaining, mode, source,
                    (Operation<Long>) args -> ((MEStorage) args[0]).insert((AEKey) args[1], (long) args[2],
                        (Actionable) args[3], (IActionSource) args[4]));
                if (remaining == 0) break;
            }
            return amount - remaining;
        }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long remaining = amount;
            for (MEStorage mount : mounts) {
                remaining -= call("neoecoae$filterCreativeInput", mount, key, remaining, mode, source,
                    (Operation<Long>) args -> ((MEStorage) args[0]).extract((AEKey) args[1], (long) args[2],
                        (Actionable) args[3], (IActionSource) args[4]));
                if (remaining == 0) break;
            }
            return amount - remaining;
        }
        @Override public void getAvailableStacks(KeyCounter out) {
            for (MEStorage mount : mounts) {
                invoke("neoecoae$getAvailableStacksSaturated", new Class<?>[] {MEStorage.class, KeyCounter.class, Operation.class},
                    mount, out, (Operation<Void>) args -> {
                        ((MEStorage) args[0]).getAvailableStacks((KeyCounter) args[1]);
                        return null;
                    });
            }
        }
        long call(String name, MEStorage mount, AEKey key, long amount, Actionable mode, IActionSource source, Operation<Long> original) {
            return (long) invoke(name, new Class<?>[] {MEStorage.class, AEKey.class, long.class, Actionable.class,
                IActionSource.class, Operation.class}, mount, key, amount, mode, source, original);
        }
        Object invoke(String name, Class<?>[] signature, Object... args) {
            try {
                Method method = NetworkStorageMixin.class.getDeclaredMethod(name, signature);
                method.setAccessible(true);
                return method.invoke(hooks, args);
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        }
        @Override public Component getDescription() { return Component.empty(); }
    }

    private static class Stock implements MEStorage {
        final AEKey key;
        long amount;
        final long capacity;
        int extractions;
        Stock(long amount, long capacity) { this(AEItemKey.of(Items.STONE), amount, capacity); }
        Stock(AEKey key, long amount, long capacity) { this.key = key; this.amount = amount; this.capacity = capacity; }
        @Override public long insert(AEKey key, long requested, Actionable mode, IActionSource source) {
            long accepted = this.key.equals(key) ? Math.min(requested, capacity - amount) : 0;
            if (mode == Actionable.MODULATE) amount += accepted;
            return accepted;
        }
        @Override public long extract(AEKey key, long requested, Actionable mode, IActionSource source) {
            long extracted = this.key.equals(key) ? Math.min(requested, amount) : 0;
            if (mode == Actionable.MODULATE && extracted > 0) { amount -= extracted; extractions++; }
            return extracted;
        }
        @Override public void getAvailableStacks(KeyCounter out) { if (amount > 0) out.add(key, amount); }
        @Override public Component getDescription() { return Component.empty(); }
    }
    private static final class BusStock extends Stock implements StorageBusTransferFilter.StorageBusInventory {
        BusStock(long amount, long capacity) { super(amount, capacity); }
        BusStock(AEKey key, long amount, long capacity) { super(key, amount, capacity); }
    }
}
