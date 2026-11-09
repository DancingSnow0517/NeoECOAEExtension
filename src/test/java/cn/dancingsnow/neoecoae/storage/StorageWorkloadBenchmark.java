package cn.dancingsnow.neoecoae.storage;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.DriveWatcher;
import appeng.me.storage.NetworkStorage;
import cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalInventorySnapshot;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.storage.persistence.ECOCellSavedData;
import cn.dancingsnow.neoecoae.storage.persistence.ECOStorageRepository;
import cn.dancingsnow.neoecoae.storage.persistence.StorageAmounts;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.management.ThreadMXBean;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.lang.management.ManagementFactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import cn.dancingsnow.neoecoae.mixins.ae2.storage.NetworkStorageMixin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.lang.reflect.Proxy;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import cn.dancingsnow.neoecoae.storage.transfer.ECOIOPortTransfer;
import cn.dancingsnow.neoecoae.storage.transfer.ECOCreativeExtractionFilter;
import net.minecraft.network.chat.Component;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/** Executable subsystem benchmark; no timings are asserted by the unit test suite. */
public final class StorageWorkloadBenchmark {
    private static volatile long consumed;
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final ThreadMXBean MX = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final JsonArray RESULTS = new JsonArray();
    private static final int[][] MATRIX = {{100,1},{300,5},{500,20},{500,500},{1000,20},{1000,1000}};
    private static final String ONLY = System.getProperty("neoecoae.benchmark.only", "");
    private static final int WARMUP_BATCHES = Integer.getInteger("neoecoae.benchmark.warmupBatches", 4);
    private static final int ITERATIONS_MULTIPLIER = Integer.getInteger("neoecoae.benchmark.iterationsMultiplier", 1);

    @FunctionalInterface private interface Cycle { long run(int cycle); }

    @Name("neoecoae.StorageBenchmarkSample") @Label("Measured storage batch")
    static final class SampleEvent extends Event {
        String workload;
        int resources;
        int changedKeys;
        int iterations;
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        StorageTestContext.bootstrap();
        KeyReuse.configure(Boolean.parseBoolean(System.getProperty("neoecoae.benchmark.keyReuse", "true")));
        MX.setThreadAllocatedMemoryEnabled(true);
        var pending = new ECOCellSavedData(UUID.randomUUID());
        var session = new ECOStorageRepository.Session(null, output.getParent());
        session.tick(1000); session.track(pending); pending.setDirty();
        measure("dirty-already-queued",100,1,1,cycle->{pending.setDirty();return pending.contentRevision();});
        extractionHandler();
        for (int[] workload : MATRIX) {
            int n = workload[0], k = workload[1];
            AEKey[] keys = keys(n);
            var raw = new StorageAmounts();
            for (AEKey key : keys) raw.add(key, seed(n));
            measure("raw-long", n, k, 2*k, cycle -> {
                long result = 0;
                for (int i=0;i<k;i++) {
                    AEKey key=keys[index(cycle,i,n,k)];
                    raw.add(key, 2); raw.subtract(key, 1); result += raw.visible(key);
                }
                return result;
            });
            try (var context = new StorageTestContext(Files.createTempDirectory(output.getParent(), "cell-"), true)) {
                ECOStorageCell cell = context.cell;
                for (AEKey key : keys) cell.insert(key, seed(n), Actionable.MODULATE, SOURCE);
                io("cell-direct", cell, keys, n, k);
                var network = new NetworkStorage();
                network.mount(0, cell);
                io("official-network-eco-mount", network, keys, n, k);
                var watched = new NetworkStorage();
                watched.mount(0, new DriveWatcher(cell, () -> {}));
                io("official-network-drive-watcher", watched, keys, n, k);
                cache("cell-network-cache", cell, keys, n, k, false);
                cache("cell-terminal-one", cell, keys, n, k, true);
                AEKey[] equal = keys(n);
                measure("cell-equal-key-query",n,k,k,cycle -> {
                    long sum=0;
                    for(int i=0;i<k;i++) sum+=cell.extract(equal[index(cycle,i,n,k)],1,Actionable.SIMULATE,SOURCE);
                    return sum;
                });
            }
            var domainData=ECOInfiniteStorageData.createNew();
            var domainSession=new ECOStorageRepository.Session(null,output.getParent());
            domainSession.tick(1000);domainSession.track(domainData);
            var engine = new SavedDataInfiniteStorageEngine(domainData);
            var domain = new ECOInfiniteStorage(engine, Component.empty(), () -> true);
            for (AEKey key : keys) engine.insert(key, seed(n), Actionable.MODULATE);
            io("domain-direct",domain,keys,n,k);
            cache("domain-network-cache",domain,keys,n,k,false);
            cache("domain-terminal-one",domain,keys,n,k,true);
        }
        interfaces(output.getParent());
        mountedInterfaces();
        planning(output.getParent());
        checkpoint(output.getParent());
        JsonObject report=new JsonObject();
        report.addProperty("java",System.getProperty("java.runtime.version"));
        report.addProperty("vm",System.getProperty("java.vm.name"));
        report.addProperty("arguments",ManagementFactory.getRuntimeMXBean().getInputArguments().toString());
        report.addProperty("kind","headless real storage/cache and untransformed AE2 routing; not server TPS");
        report.addProperty("keyReuse",KeyReuse.epoch()!=null);
        report.addProperty("taggedKeys",Boolean.getBoolean("neoecoae.benchmark.tagged"));
        report.addProperty("warmupBatches", WARMUP_BATCHES);
        report.addProperty("iterationsMultiplier", ITERATIONS_MULTIPLIER);
        report.addProperty("snapshotCounterBridge", Arrays.stream(KeyCounter.class.getInterfaces())
            .anyMatch(type -> type.getName().endsWith("TerminalInventorySnapshot$SnapshotCounter")));
        report.addProperty("cellFixture","L9 bytes and per-type accounting; type limit 10000 (stock item limit is 315)");
        report.addProperty("initialOccupancy","approximately 85% of L9 item capacity after per-type overhead");
        report.add("results",RESULTS); report.add("diagnostics",StoragePerformance.snapshot());
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("RESULT "+output+" cases="+RESULTS.size()+" checksum="+consumed);
    }

    private static long seed(int n) { return ((1L<<28)-n*(1L<<15))*8*85/100/n; }
    private static AEKey[] keys(int n) { return StorageTestContext.keys(n,Boolean.getBoolean("neoecoae.benchmark.tagged")); }
    private static int index(int cycle,int i,int n,int k) { return k<n ? i : (cycle*31+i*37)%n; }

    private static void interfaces(Path directory) throws Exception {
        for(int[] workload:MATRIX) {
            int n=workload[0],k=workload[1];
            try(var context=new StorageTestContext(Files.createTempDirectory(directory,"io-"),true)) {
                AEKey[] keys=keys(n);
                for(AEKey key:keys)context.cell.insert(key,seed(n),Actionable.MODULATE,SOURCE);
                var targetEngine=new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
                var target=new ECOInfiniteStorage(targetEngine,Component.empty(),()->true);
                MEStorage limitedTarget=singleItemSink(target);
                var network=new NetworkStorage();network.mount(0,limitedTarget);
                IStorageService service=(IStorageService)Proxy.newProxyInstance(StorageWorkloadBenchmark.class.getClassLoader(),
                    new Class<?>[]{IStorageService.class},(p,m,a)->switch(m.getName()) {
                        case "getInventory" -> network;
                        case "getCachedInventory" -> network.getAvailableStacks();
                        default -> throw new UnsupportedOperationException(m.getName());
                    });
                IEnergyService energy=(IEnergyService)Proxy.newProxyInstance(StorageWorkloadBenchmark.class.getClassLoader(),
                    new Class<?>[]{IEnergyService.class},(p,m,a)-> {
                        if(m.getName().equals("extractAEPower"))return a[0];
                        throw new UnsupportedOperationException(m.getName());
                    });
                IGrid grid=(IGrid)Proxy.newProxyInstance(StorageWorkloadBenchmark.class.getClassLoader(),
                    new Class<?>[]{IGrid.class},(p,m,a)->switch(m.getName()) {
                        case "getStorageService" -> service;
                        case "getEnergyService" -> energy;
                        default -> throw new UnsupportedOperationException(m.getName());
                    });
                var filter=new StorageInterfaceFilter(new net.minecraft.nbt.CompoundTag());
                var transfer=new ECOIOPortTransfer();
                measure("interface-output",n,k,k+1,cycle->{
                    for(int i=0;i<k;i++)context.cell.insert(keys[index(cycle,i,n,k)],1,Actionable.MODULATE,SOURCE);
                    return transfer.transfer(grid,context.cell,false,false,false,SOURCE,filter);
                });
            }
        }
        for(int n:new int[]{100,300,500,1000}) {
            try(var context=new StorageTestContext(Files.createTempDirectory(directory,"filtered-"),true)) {
                AEKey[] keys=keys(n);
                for(AEKey key:keys)context.cell.insert(key,seed(n),Actionable.MODULATE,SOURCE);
                measure("creative-filter-single-key",n,1,2,cycle->{
                    AEKey key=keys[cycle%n];context.cell.insert(key,1,Actionable.MODULATE,SOURCE);
                    return ECOCreativeExtractionFilter.extract(context.cell,key,1,SOURCE);
                });
            }
        }
    }

    private static void io(String name, MEStorage storage, AEKey[] keys, int n, int k) {
        measure(name,n,k,2*k,cycle -> {
            long sum=0;
            for(int i=0;i<k;i++) {
                AEKey key=keys[index(cycle,i,n,k)];
                long added=storage.insert(key,2,Actionable.MODULATE,SOURCE);
                long taken=storage.extract(key,1,Actionable.MODULATE,SOURCE);
                if(added!=2||taken!=1)throw new AssertionError("IO conservation");
                sum+=added+taken;
            }
            return sum;
        });
    }

    private static void mountedInterfaces() {
        for (String name : new String[]{"interface-input", "interface-multi-output"}) {
            if (!ONLY.isEmpty() && !name.contains(ONLY)) continue;
            boolean input = name.equals("interface-input");
            for (int[] workload : MATRIX) {
                int n = workload[0], k = workload[1]; AEKey[] keys = keys(n);
                var a = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
                var b = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
                var sink = new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
                for (AEKey key : keys) { a.insert(key, seed(n), Actionable.MODULATE); b.insert(key, seed(n), Actionable.MODULATE); }
                var from = new MountedStorage(List.of(new ECOInfiniteStorage(a, Component.empty(), () -> true),
                    new ECOInfiniteStorage(b, Component.empty(), () -> true)));
                MEStorage destination = singleItemSink(new ECOInfiniteStorage(sink, Component.empty(), () -> true));
                MEStorage network = input ? from : destination;
                IStorageService service = (IStorageService) Proxy.newProxyInstance(StorageWorkloadBenchmark.class.getClassLoader(),
                    new Class<?>[]{IStorageService.class}, (p,m,args) -> switch(m.getName()) {
                        case "getInventory" -> network;
                        case "getCachedInventory" -> network.getAvailableStacks();
                        default -> throw new UnsupportedOperationException(m.getName());
                    });
                IEnergyService energy = (IEnergyService) Proxy.newProxyInstance(StorageWorkloadBenchmark.class.getClassLoader(),
                    new Class<?>[]{IEnergyService.class}, (p,m,args) -> {
                        if (m.getName().equals("extractAEPower")) return args[0];
                        throw new UnsupportedOperationException(m.getName());
                    });
                IGrid grid = (IGrid) Proxy.newProxyInstance(StorageWorkloadBenchmark.class.getClassLoader(),
                    new Class<?>[]{IGrid.class}, (p,m,args) -> switch(m.getName()) {
                        case "getStorageService" -> service;
                        case "getEnergyService" -> energy;
                        default -> throw new UnsupportedOperationException(m.getName());
                    });
                var transfer = new ECOIOPortTransfer(); var filter = new StorageInterfaceFilter(new net.minecraft.nbt.CompoundTag());
                long[] added = {0};
                measure(name, n, k, k + 1, cycle -> {
                    for (int i = 0; i < k; i++) a.insert(keys[index(cycle, i, n, k)], 1, Actionable.MODULATE);
                    added[0] += k;
                    return transfer.transfer(grid, input ? destination : from, input, false, true, SOURCE, filter);
                });
                long total = 0;
                for (AEKey key : keys) total += a.extract(key, Long.MAX_VALUE, Actionable.SIMULATE)
                    + b.extract(key, Long.MAX_VALUE, Actionable.SIMULATE) + sink.extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
                if (total != 2L * n * seed(n) + added[0]) throw new AssertionError("Mounted IO conservation");
            }
        }
    }

    private static MEStorage singleItemSink(MEStorage target) {
        return new MEStorage() {
            @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                return target.insert(key, Math.min(1, amount), mode, source);
            }
            @Override public void getAvailableStacks(KeyCounter out) { target.getAvailableStacks(out); }
            @Override public Component getDescription() { return Component.empty(); }
        };
    }

    private static void planning(Path directory) throws Exception {
        if (!ONLY.isEmpty() && !"planner".contains(ONLY) && !ONLY.contains("planner")) return;
        for (int n : new int[]{100, 300, 500, 1000}) {
            AEKey[] keys = keys(n);
            try (var context = new StorageTestContext(Files.createTempDirectory(directory, "planner-"), true)) {
                for (AEKey key : keys) context.cell.insert(key, seed(n), Actionable.MODULATE, SOURCE);
                var hostType = appeng.api.implementations.blockentities.IChestOrDrive.class;
                Object host = Proxy.newProxyInstance(StorageWorkloadBenchmark.class.getClassLoader(), new Class<?>[]{hostType},
                    (p,m,args) -> switch (m.getName()) {
                        case "isPowered" -> true;
                        case "getCellCount" -> 1;
                        case "getOriginalCellInventory" -> context.cell;
                        case "getCellItem" -> context.stack.getItem();
                        default -> throw new UnsupportedOperationException(m.getName());
                    });
                var network = new NetworkStorage(); network.mount(0, context.cell);
                IStorageService service = (IStorageService) Proxy.newProxyInstance(StorageWorkloadBenchmark.class.getClassLoader(),
                    new Class<?>[]{IStorageService.class}, (p,m,args) -> {
                        if (m.getName().equals("getInventory")) return network;
                        throw new UnsupportedOperationException(m.getName());
                    });
                IGrid grid = (IGrid) Proxy.newProxyInstance(StorageWorkloadBenchmark.class.getClassLoader(),
                    new Class<?>[]{IGrid.class}, (p,m,args) -> switch(m.getName()) {
                        case "getStorageService" -> service;
                        case "getMachineClasses" -> java.util.Set.of(hostType);
                        case "getMachines" -> args[0] == hostType ? java.util.Set.of(host) : java.util.Set.of();
                        default -> throw new UnsupportedOperationException(m.getName());
                    });
                measure("planner-unbounded-scan", n, n, 1,
                    cycle -> cn.dancingsnow.neoecoae.crafting.planner.ECOPlannerInventory.collectUnboundedKeys(grid).size());
                measure("planner-capture", n, n, 1, cycle -> {
                    var snapshot = cn.dancingsnow.neoecoae.crafting.planner.ECOPlannerInventory.capture(grid);
                    if (snapshot.amounts().size() != n || !snapshot.unboundedKeys().isEmpty()) throw new AssertionError("Planner stock");
                    return snapshot.amountOf(keys[cycle % n]).longValueSaturated();
                });
            }
            TerminalInventorySnapshot.clear();
        }
    }

    /** Same visible mount contract as the ECO network bridge; all IO still uses native AE2 routing. */
    private static final class MountedStorage implements MEStorage, NetworkInventoryCache.Mounts {
        private final List<MEStorage> mounts;
        private final NetworkStorage network = new NetworkStorage();
        MountedStorage(List<MEStorage> mounts) { this.mounts = mounts; mounts.forEach(mount -> network.mount(0, mount)); }
        @Override public long neoecoae$mountGeneration() { return 0; }
        @Override public boolean neoecoae$distinctMountsGuaranteed() { return true; }
        @Override public boolean neoecoae$withMounts(Consumer<List<MEStorage>> action) { action.accept(mounts); return true; }
        @Override public void getAvailableStacks(KeyCounter out) { network.getAvailableStacks(out); }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            return network.extract(key, amount, mode, source);
        }
        @Override public Component getDescription() { return Component.empty(); }
    }

    private static void cache(String name, MEStorage storage, AEKey[] keys, int n, int k, boolean terminal) {
        if(!ONLY.isEmpty()&&!name.contains(ONLY))return;
        var mounts=new Mounts(List.of(storage));
        var cache=new NetworkInventoryCache(false);
        var published=new KeyCounter();
        var amounts=new Object2LongOpenHashMap<AEKey>();
        var tick=new long[1];
        measure(name,n,k,2*k,cycle -> {
            for(int i=0;i<k;i++) {
                AEKey key=keys[index(cycle,i,n,k)];
                storage.insert(key,2,Actionable.MODULATE,SOURCE);
                storage.extract(key,1,Actionable.MODULATE,SOURCE);
            }
            if(terminal) {
                var view=TerminalInventorySnapshot.sample(mounts,++tick[0],false,mounts::getAvailableStacks);
                return view.inventory().get(keys[cycle%n]);
            }
            if(!cache.refresh(mounts,published,amounts,(key,amount)->{}))throw new AssertionError("Cache fallback");
            return published.get(keys[cycle%n]);
        });
        KeyCounter authority=new KeyCounter(); storage.getAvailableStacks(authority);
        KeyCounter actual=terminal?TerminalInventorySnapshot.sample(mounts,++tick[0],false,mounts::getAvailableStacks).inventory():published;
        for(var entry:authority) if(entry.getLongValue()!=actual.get(entry.getKey()))throw new AssertionError("Cache total");
        TerminalInventorySnapshot.clear();
    }

    private static void extractionHandler() throws Exception {
        var lookup=MethodHandles.privateLookupIn(NetworkStorageMixin.class,MethodHandles.lookup());
        var handler=lookup.findVirtual(NetworkStorageMixin.class,"neoecoae$filterCreativeInput",
            MethodType.methodType(long.class,MEStorage.class,AEKey.class,long.class,Actionable.class,
                IActionSource.class,Operation.class)).bindTo(new NetworkStorageMixin() {});
        var engine=new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        MEStorage storage=new ECOInfiniteStorage(engine,Component.empty(),()->true);
        AEKey key=StorageTestContext.keys(1,false)[0];
        engine.insert(key,1000,Actionable.MODULATE);
        Operation<Long> original=arguments->((MEStorage)arguments[0]).extract((AEKey)arguments[1],
            (Long)arguments[2],(Actionable)arguments[3],(IActionSource)arguments[4]);
        measure("mixin-handler-kernel-no-weaving",100,1,2,cycle->{
            storage.insert(key,1,Actionable.MODULATE,SOURCE);
            try { return (long)handler.invokeExact(storage,key,1L,Actionable.MODULATE,SOURCE,original); }
            catch(Throwable failure){throw new AssertionError(failure);}
        });
    }

    private static void measure(String name,int n,int k,int operations,Cycle body) {
        if(!ONLY.isEmpty()&&!name.contains(ONLY))return;
        int iterations=Math.multiplyExact(Math.max(100,20000/k), ITERATIONS_MULTIPLIER);
        int cycle=0;
        for(int warm=0;warm<WARMUP_BATCHES;warm++)for(int i=0;i<iterations;i++)consumed=body.run(cycle++);
        long[] times=new long[7],allocations=new long[7],cpu=new long[7];
        long gcBefore=gc(false),pauseBefore=gc(true),sum=0;
        for(int round=0;round<times.length;round++) {
            SampleEvent event=new SampleEvent();event.workload=name;event.resources=n;event.changedKeys=k;event.iterations=iterations;
            event.begin();
            long bytes=MX.getThreadAllocatedBytes(Thread.currentThread().getId());
            long cpuStart=MX.getCurrentThreadCpuTime(),start=System.nanoTime();
            for(int i=0;i<iterations;i++)sum+=body.run(cycle++);
            times[round]=System.nanoTime()-start;
            cpu[round]=MX.getCurrentThreadCpuTime()-cpuStart;
            allocations[round]=MX.getThreadAllocatedBytes(Thread.currentThread().getId())-bytes;
            event.commit();
        }
        consumed=sum;
        JsonObject row=new JsonObject(); row.addProperty("name",name);row.addProperty("n",n);row.addProperty("k",k);
        row.addProperty("cyclesPerSample",iterations);row.addProperty("ioOperationsPerCycle",operations);
        row.add("elapsedNs",longs(times));row.add("allocatedBytes",longs(allocations));row.add("threadCpuNs",longs(cpu));
        long[] sorted=times.clone();Arrays.sort(sorted);
        row.addProperty("medianNsPerCycle",sorted[3]/(double)iterations);
        row.addProperty("medianNsPerIO",sorted[3]/(double)iterations/operations);
        row.addProperty("meanBytesPerCycle",Arrays.stream(allocations).average().orElse(0)/iterations);
        row.addProperty("gcCount",gc(false)-gcBefore);row.addProperty("gcMillis",gc(true)-pauseBefore);
        RESULTS.add(row);
    }

    private static void checkpoint(Path directory) throws Exception {
        if(!ONLY.isEmpty()&&!"checkpoint".contains(ONLY))return;
        StoragePerformance.reset();
        for(int n:new int[]{100,300,500,1000}) {
            var data=new ECOCellSavedData(UUID.randomUUID());
            for(AEKey key:StorageTestContext.keys(n,false))data.amounts().add(key,1000);
            data.bind(directory.resolve("checkpoint-"+n+".dat"));
            long[] times=new long[12],bytes=new long[12];
            for(int i=0;i<times.length+3;i++) {
                data.setDirty();long allocated=MX.getThreadAllocatedBytes(Thread.currentThread().getId()),started=System.nanoTime();
                if(!data.flush())throw new AssertionError(data.writeFailure());
                if(i>=3){times[i-3]=System.nanoTime()-started;bytes[i-3]=MX.getThreadAllocatedBytes(Thread.currentThread().getId())-allocated;}
            }
            JsonObject row=new JsonObject();row.addProperty("name","checkpoint");row.addProperty("n",n);
            row.add("elapsedNs",longs(times));row.add("allocatedBytes",longs(bytes));RESULTS.add(row);
        }
    }

    private static long gc(boolean time){return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .mapToLong(bean->Math.max(0,time?bean.getCollectionTime():bean.getCollectionCount())).sum();}
    private static JsonArray longs(long[] values){JsonArray result=new JsonArray();for(long value:values)result.add(value);return result;}

    public static final class Mounts implements NetworkInventoryCache.Mounts, TerminalInventorySnapshot.Source {
        private final List<MEStorage> list;
        private final TerminalInventorySnapshot.Cache terminal=new TerminalInventorySnapshot.Cache();
        public Mounts(List<MEStorage> list){this.list=list;}
        @Override public long neoecoae$mountGeneration(){return 0;}
        @Override public boolean neoecoae$withMounts(Consumer<List<MEStorage>> action){action.accept(list);return true;}
        @Override public TerminalInventorySnapshot.Cache neoecoae$terminalCache(){return terminal;}
        public KeyCounter getAvailableStacks(){KeyCounter result=new KeyCounter();for(MEStorage storage:list)storage.getAvailableStacks(result);return result;}
    }
}
