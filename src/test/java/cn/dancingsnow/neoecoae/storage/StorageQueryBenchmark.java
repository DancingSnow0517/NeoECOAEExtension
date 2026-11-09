package cn.dancingsnow.neoecoae.storage;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse;
import cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalInventorySnapshot;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.storage.infinite.SavedDataInfiniteStorageEngine;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.management.ThreadMXBean;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntToLongFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Additional coupled query workloads; same JVM options and real production caches as the main harness. */
public final class StorageQueryBenchmark {
    private static volatile long consumed;
    private static final ThreadMXBean MX=(ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static final JsonArray RESULTS=new JsonArray();

    public static void main(String[] args) throws Exception {
        StorageTestContext.bootstrap();KeyReuse.configure(true);MX.setThreadAllocatedMemoryEnabled(true);
        run(false,false,false);run(true,false,false);run(false,true,false);run(false,false,true);run(true,true,false);
        JsonObject report=new JsonObject();report.addProperty("java",System.getProperty("java.runtime.version"));
        report.addProperty("kind","headless coupled cache/fuzzy/multi-viewer workloads; not game TPS");
        report.addProperty("warmupBatches",Integer.getInteger("neoecoae.benchmark.warmupBatches",4));
        report.addProperty("iterationsMultiplier",Integer.getInteger("neoecoae.benchmark.iterationsMultiplier",1));
        report.add("results",RESULTS);
        Files.writeString(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static void run(boolean fuzzy,boolean viewers,boolean unversioned) {
        int n=500,k=20;AEKey[] keys=StorageTestContext.keys(n,false);
        if(fuzzy)for(int i=0;i<n;i++) {
            ItemStack tool=new ItemStack(Items.DIAMOND_PICKAXE);tool.setDamageValue(i);
            keys[i]=AEItemKey.of(tool);
        }
        var first=new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        var second=new SavedDataInfiniteStorageEngine(ECOInfiniteStorageData.createNew());
        for(AEKey key:keys){first.insert(key,1000,Actionable.MODULATE);second.insert(key,1000,Actionable.MODULATE);}
        var a=new ECOInfiniteStorage(first,Component.empty(),()->true);var b=new ECOInfiniteStorage(second,Component.empty(),()->true);
        var mounts=new StorageWorkloadBenchmark.Mounts(List.of(
            unversioned?unversioned(a):a,unversioned?unversioned(b):b));
        var cache=new NetworkInventoryCache(false);var inventory=new KeyCounter();var amounts=new Object2LongOpenHashMap<AEKey>();
        long[] tick={0};
        IntToLongFunction workload=cycle->{
            for(int i=0;i<k;i++){first.insert(keys[i],1,Actionable.MODULATE);second.insert(keys[i],1,Actionable.MODULATE);}
            KeyCounter current;
            if(viewers){
                long now=++tick[0];var snapshot=TerminalInventorySnapshot.sample(mounts,now,false,mounts::getAvailableStacks);
                for(int viewer=1;viewer<4;viewer++)if(snapshot!=TerminalInventorySnapshot.sample(mounts,now,false,mounts::getAvailableStacks))
                    throw new AssertionError("Viewer sharing");
                current=snapshot.inventory();
            }else{
                if(!cache.refresh(mounts,inventory,amounts,(key,amount)->{}))throw new AssertionError("Refresh fallback");
                current=inventory;
            }
            if(fuzzy){long total=0;for(var entry:current.findFuzzy(keys[0],FuzzyMode.IGNORE_ALL))total+=entry.getLongValue();return total;}
            return current.get(keys[0]);
        };
        measure(unversioned?"unknown-wrapper-two-domains":fuzzy&&viewers?"four-viewers-fuzzy-two-domains"
            :fuzzy?"fuzzy-two-domains":viewers?"four-viewers-two-domains":"two-domains",workload,n,k);
        KeyCounter expected=new KeyCounter();a.getAvailableStacks(expected);b.getAvailableStacks(expected);
        KeyCounter actual=viewers?TerminalInventorySnapshot.sample(mounts,++tick[0],false,mounts::getAvailableStacks).inventory():inventory;
        for(var entry:expected)if(entry.getLongValue()!=actual.get(entry.getKey()))throw new AssertionError("Shared-key total");
        TerminalInventorySnapshot.clear();
    }

    private static MEStorage unversioned(MEStorage source) {
        return new MEStorage() {
            @Override public void getAvailableStacks(KeyCounter out) { source.getAvailableStacks(out); }
            @Override public Component getDescription() { return Component.empty(); }
        };
    }

    private static void measure(String name,IntToLongFunction action,int n,int k){
        int iterations=Math.multiplyExact(2000,Integer.getInteger("neoecoae.benchmark.iterationsMultiplier",1)),cycle=0;
        for(int warm=0;warm<Integer.getInteger("neoecoae.benchmark.warmupBatches",4);warm++)for(int i=0;i<iterations;i++)consumed=action.applyAsLong(cycle++);
        long[] elapsed=new long[7],allocated=new long[7];
        for(int round=0;round<7;round++){
            long bytes=MX.getThreadAllocatedBytes(Thread.currentThread().getId()),start=System.nanoTime(),sum=0;
            for(int i=0;i<iterations;i++)sum+=action.applyAsLong(cycle++);
            elapsed[round]=System.nanoTime()-start;allocated[round]=MX.getThreadAllocatedBytes(Thread.currentThread().getId())-bytes;consumed=sum;
        }
        JsonObject row=new JsonObject();row.addProperty("name",name);row.addProperty("n",n);row.addProperty("k",k);row.addProperty("mounts",2);
        row.addProperty("cyclesPerSample",iterations);long[] sorted=elapsed.clone();Arrays.sort(sorted);
        row.addProperty("medianNsPerCycle",sorted[3]/(double)iterations);
        row.addProperty("meanBytesPerCycle",Arrays.stream(allocated).average().orElse(0)/iterations);
        JsonArray times=new JsonArray(),bytes=new JsonArray();for(long value:elapsed)times.add(value);for(long value:allocated)bytes.add(value);
        row.add("elapsedNs",times);row.add("allocatedBytes",bytes);RESULTS.add(row);
    }
}
