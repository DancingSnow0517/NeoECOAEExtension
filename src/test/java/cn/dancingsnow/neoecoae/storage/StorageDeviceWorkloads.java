package cn.dancingsnow.neoecoae.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.storage.transfer.ECOIOPortTransfer;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Matched producers, actual IO-port/network APIs, and explicit policy latency; no server TPS claim. */
final class StorageDeviceWorkloads {
    static void run(int[][] matrix) {
        if (!System.getProperty("neoecoae.benchmark.only", "").startsWith("device")) return;
        for (int[] workload : matrix) for (int interval : new int[]{1, 5, 10, 20}) {
            int n = workload[0], k = workload[1];
            var timed = new Fixture(n, k, interval, false);
            StorageWorkloadBenchmark.measure("device-output-" + interval + "t", n, k, 1,
                cycle -> timed.tick(cycle));
            timed.check();
            var trace = new Fixture(n, k, interval, true);
            int ticks = 2000;
            for (int tick = 0; tick < ticks; tick++) trace.tick(tick);
            trace.check();
            var row = new JsonObject();
            row.addProperty("name", "device-trace"); row.addProperty("n", n); row.addProperty("k", k);
            row.addProperty("intervalTicks", interval); row.addProperty("flushThreshold", 64);
            row.addProperty("ticks", ticks); row.addProperty("produced", trace.produced);
            row.addProperty("delivered", trace.delivered); row.addProperty("pending", trace.produced - trace.delivered);
            row.addProperty("simulateRequests", trace.simulates); row.addProperty("modulateRequests", trace.modulates);
            row.addProperty("requestsPerTick", trace.modulates / (double) ticks);
            row.addProperty("maxBufferedUnits", trace.maxPending);
            row.addProperty("maxDeliveredLatencyTicks", trace.maxLatency);
            row.addProperty("meanDeliveredLatencyTicks", trace.delivered == 0 ? 0 : trace.totalLatency / (double) trace.delivered);
            StorageWorkloadBenchmark.record(row);
        }
    }

    private static final class Fixture {
        final AEKey[] keys;
        final int active, interval;
        final boolean trace;
        final SavedDataInfiniteStorageEngine engine = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        final Object2LongOpenHashMap<AEKey> received = new Object2LongOpenHashMap<>();
        final ECOIOPortTransfer transfer = new ECOIOPortTransfer();
        final StorageInterfaceFilter filter;
        final IGrid grid;
        final MEStorage host;
        final int[] firstPending;
        long produced, delivered, totalLatency, maxPending;
        int modulates, simulates, currentTick, maxLatency;

        Fixture(int n, int active, int interval, boolean trace) {
            this.keys = StorageTestContext.keys(n, false); this.active = active; this.interval = interval; this.trace = trace;
            firstPending = new int[n]; Arrays.fill(firstPending, -1);
            // Cold inventory remains behind the device's exact filter. The measured resource universe stays n.
            for (int i = active; i < n; i++) engine.insert(keys[i], 1000, Actionable.MODULATE);
            CompoundTag rules = new CompoundTag();
            if (active < n) {
                if (active > StorageInterfaceFilter.SLOTS) throw new AssertionError("fixture filter size");
                for (int i = 0; i < active; i++) rules = StorageInterfaceFilter.with(rules, i, keys[i]);
            }
            filter = new StorageInterfaceFilter(rules);
            var leaf = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
            host = new Host(leaf);
            var nativeNetwork = new NetworkStorage();
            nativeNetwork.mount(0, new MEStorage() {
                public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                    if (mode == Actionable.MODULATE) received.addTo(key, amount);
                    return amount;
                }
                public Component getDescription() { return Component.empty(); }
            });
            MEStorage counted = new MEStorage() {
                public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                    if (mode == Actionable.SIMULATE) simulates++; else modulates++;
                    long inserted = nativeNetwork.insert(key, amount, mode, source);
                    if (mode == Actionable.MODULATE) {
                        delivered += inserted;
                        if (trace && inserted > 0) {
                            int index = -1;
                            for (int i = 0; i < active; i++) if (keys[i].equals(key)) { index = i; break; }
                            if (index < 0 || firstPending[index] < 0) throw new AssertionError("unknown producer");
                            int delay = currentTick - firstPending[index];
                            maxLatency = Math.max(maxLatency, delay);
                            // Each hot key produces one unit per tick, and the target accepts the complete batch.
                            totalLatency += inserted * delay / 2;
                            firstPending[index] = -1;
                        }
                    }
                    return inserted;
                }
                public Component getDescription() { return Component.empty(); }
            };
            ClassLoader loader = getClass().getClassLoader();
            var service = Proxy.newProxyInstance(loader, new Class<?>[]{IStorageService.class}, (p, m, args) -> {
                if (m.getName().equals("getInventory")) return counted;
                throw new UnsupportedOperationException(m.getName());
            });
            var power = Proxy.newProxyInstance(loader, new Class<?>[]{IEnergyService.class}, (p, m, args) -> {
                if (m.getName().equals("extractAEPower")) return args[0];
                throw new UnsupportedOperationException(m.getName());
            });
            grid = (IGrid) Proxy.newProxyInstance(loader, new Class<?>[]{IGrid.class}, (p, m, args) -> switch (m.getName()) {
                case "getStorageService" -> service;
                case "getEnergyService" -> power;
                default -> throw new UnsupportedOperationException(m.getName());
            });
        }

        long tick(int tick) {
            currentTick = tick;
            for (int i = 0; i < active; i++) {
                engine.insert(keys[i], 1, Actionable.MODULATE);
                if (trace && firstPending[i] < 0) firstPending[i] = tick;
            }
            produced += active;
            if (trace) maxPending = Math.max(maxPending, produced - delivered);
            return transfer.transfer(grid, host, false, false, true, IActionSource.empty(), filter, tick, interval, 64);
        }

        void check() {
            long total = 0;
            for (AEKey key : keys) total += engine.extract(key, Long.MAX_VALUE, Actionable.SIMULATE) + received.getLong(key);
            if (total != produced + (keys.length - active) * 1000L) throw new AssertionError("device conservation");
        }
    }

    private record Host(MEStorage storage) implements MEStorage, NetworkInventoryCache.Mounts {
        public long neoecoae$mountGeneration() { return 0; }
        public boolean neoecoae$withMounts(java.util.function.Consumer<List<MEStorage>> visitor) {
            visitor.accept(List.of(storage)); return true;
        }
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) { return storage.extract(key, amount, mode, source); }
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) { return storage.insert(key, amount, mode, source); }
        public void getAvailableStacks(KeyCounter out) { storage.getAvailableStacks(out); }
        public Component getDescription() { return Component.empty(); }
    }
}
