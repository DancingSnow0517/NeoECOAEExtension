package cn.dancingsnow.neoecoae.storage;

import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.DriveWatcher;
import appeng.me.storage.NetworkStorage;
import appeng.util.prioritylist.DefaultPriorityList;
import appeng.util.prioritylist.PrecisePriorityList;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalInventorySnapshot;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.storage.infinite.SavedDataInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.storage.persistence.ECOCellSavedData;
import cn.dancingsnow.neoecoae.storage.transfer.ECOCreativeExtractionFilter;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.management.ThreadMXBean;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Real Forge/Mixin integration checks, excluded from the published mod. This is not a TPS benchmark. */
@EventBusSubscriber(modid="neoecoae")
public final class StoragePerformanceAcceptance {
    private static final IActionSource SOURCE=IActionSource.empty();
    private static final List<String> CHECKS=new ArrayList<>();
    private static final JsonArray SAMPLES=new JsonArray();
    private static volatile long consumed;
    private static final boolean OPTIMIZED=Boolean.parseBoolean(System.getProperty("neoecoae.storageAcceptanceOptimized","true"));

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(!Boolean.getBoolean("neoecoae.storagePerformanceAcceptance"))return;
        JsonObject report=new JsonObject();
        try {
            run();report.addProperty("outcome","PASS");
        } catch(Throwable failure) {
            failure.printStackTrace();report.addProperty("outcome","FAIL");report.addProperty("failure",failure.toString());
        }
        report.addProperty("java",System.getProperty("java.runtime.version"));
        report.addProperty("optimizedExpected",OPTIMIZED);
        report.addProperty("scope","real Forge registry and transformed AE2; startup method batches, not server tick percentiles");
        JsonArray checks=new JsonArray();CHECKS.forEach(checks::add);report.add("checks",checks);report.add("samples",SAMPLES);
        report.add("diagnostics",StoragePerformance.snapshot());
        try {
            Files.writeString(Path.of("storage-performance-acceptance.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception failure){throw new RuntimeException(failure);}
        System.out.println("[STORAGE_PERFORMANCE_ACCEPTANCE] "+report.get("outcome")+" "+CHECKS.size()+" checks");
        event.getServer().halt(false);
    }

    private static void check(boolean value,String text){if(!value)throw new AssertionError(text);CHECKS.add(text);}
    private static AEKey[] keys(int count){
        var result=new ArrayList<AEKey>();
        for(var item:BuiltInRegistries.ITEM){
            if(item==Items.AIR || !BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft"))continue;
            result.add(AEItemKey.of(item));if(result.size()==count)break;
        }
        if(result.size()!=count)throw new AssertionError("Key count");return result.toArray(AEKey[]::new);
    }

    private static ECOStorageCell cell(){return new ECOStorageCell(new ItemStack(NEItems.ECO_ITEM_CELL_256M.get()),()->{});}

    private static void run() throws Exception {
        check(Runtime.version().feature()==21,"matched Java 21 runtime");
        var cell=cell();AEKey[] keys=keys(300);
        for(AEKey key:keys)check(cell.insert(key,1000,Actionable.MODULATE,SOURCE)==1000,"real cell admission");
        var watcher=new DriveWatcher(cell,()->{});
        var network=new NetworkStorage();network.mount(0,watcher);
        check(network instanceof NetworkInventoryCache.Mounts,"NetworkStorage Mixin applied");
        check(network instanceof TerminalInventorySnapshot.Source,"terminal cache attachment applied");
        if(OPTIMIZED)check(watcher instanceof StorageListingDelta,"DriveWatcher inherits listing bridge");
        var cache=new NetworkInventoryCache(false);var published=new KeyCounter();var amounts=new Object2LongOpenHashMap<AEKey>();
        int[] full={0};
        java.util.function.BiConsumer<MEStorage,KeyCounter> listing=(storage,out)->{full[0]++;storage.getAvailableStacks(out);};
        check(cache.refresh((NetworkInventoryCache.Mounts)network,published,amounts,(key,amount)->{},listing),"initial real mount refresh");
        for(int i=0;i<20;i++){network.insert(keys[i],3,Actionable.MODULATE,SOURCE);network.extract(keys[i],1,Actionable.MODULATE,SOURCE);}
        check(cache.refresh((NetworkInventoryCache.Mounts)network,published,amounts,(key,amount)->{
            if(OPTIMIZED)for(int i=0;i<20;i++)if(published.get(keys[i])!=1002)throw new AssertionError("partial publication");
        },listing),"updated real mount refresh");
        if(OPTIMIZED)check(full[0]==1,"DriveWatcher changed twenty keys without another full listing");
        for(int i=0;i<300;i++)check(published.get(keys[i])==(i<20?1002:1000),"mounted total conserved");

        watcher.setExtractFiltering(true,true);watcher.setAllowExtraction(false);
        cache.refresh((NetworkInventoryCache.Mounts)network,published,amounts,(key,amount)->{},listing);
        check(published.isEmpty(),"extraction visibility disabled");
        watcher.setAllowExtraction(true);
        cache.refresh((NetworkInventoryCache.Mounts)network,published,amounts,(key,amount)->{},listing);
        check(published.size()==300,"visibility reenabled without losing quantities");
        var filter=new KeyCounter();filter.set(keys[0],1);watcher.setPartitionList(new PrecisePriorityList(filter));
        cache.refresh((NetworkInventoryCache.Mounts)network,published,amounts,(key,amount)->{},listing);
        check(published.size()==1&&published.get(keys[0])==1002,"nonempty filter respected");
        filter.clear();filter.set(keys[1],1);
        cache.refresh((NetworkInventoryCache.Mounts)network,published,amounts,(key,amount)->{},listing);
        check(published.size()==1&&published.get(keys[1])==1002,"in-place partition mutation conservatively relisted");
        watcher.setWhitelist(IncludeExclude.BLACKLIST);
        cache.refresh((NetworkInventoryCache.Mounts)network,published,amounts,(key,amount)->{},listing);
        check(published.size()==299&&published.get(keys[1])==0,"blacklist change respected");
        watcher.setPartitionList(DefaultPriorityList.INSTANCE);watcher.setWhitelist(IncludeExclude.WHITELIST);
        watcher.setExtractFiltering(false,false);

        var old=TerminalInventorySnapshot.sample(network,1000,false,network::getAvailableStacks);
        check(old==TerminalInventorySnapshot.sample(network,1000,false,network::getAvailableStacks),"two viewers share a sample");
        long before=old.inventory().get(keys[0]);network.insert(keys[0],7,Actionable.MODULATE,SOURCE);
        var fresh=TerminalInventorySnapshot.sample(network,1001,false,network::getAvailableStacks);
        check(fresh.inventory().get(keys[0])==before+7&&old.inventory().get(keys[0])==before,"previous reader snapshot remains immutable");
        check(network.extract(keys[0],1,Actionable.SIMULATE,SOURCE)==1,"real simulate result");
        check(cell.getAvailableStacks().get(keys[0])==before+7,"simulate leaves authority unchanged");
        long taken=ECOCreativeExtractionFilter.extract(network,keys[0],5,SOURCE);
        check(taken==5&&cell.getAvailableStacks().get(keys[0])==before+2,"filtered native network debit conserved");

        var second=cell();second.insert(keys[0],11,Actionable.MODULATE,SOURCE);network.mount(10,second);
        network.insert(keys[0],1,Actionable.MODULATE,SOURCE);
        check(second.getAvailableStacks().get(keys[0])==12,"official insertion priority unchanged");
        cache.refresh((NetworkInventoryCache.Mounts)network,published,amounts,(key,amount)->{},listing);
        check(published.get(keys[0])==cell.getAvailableStacks().get(keys[0])+12,"two mounts conserve their shared key");
        network.unmount(second);
        check(cell.storageData().flush(),"real checkpoint completed");
        var reloaded=ECOCellSavedData.load(NbtIo.readCompressed(cell.storageData().dataFile().toFile()).getCompound("data"),cell.storageData().id());
        check(reloaded.amounts().visible(keys[0])==cell.getAvailableStacks().get(keys[0]),"real saved cell reload conserved");

        measure("real-drive-300-20",network,300,20,keys);
        var data=ECOInfiniteStorageData.createNew();var engine=new SavedDataInfiniteStorageEngine(data);
        var domain=new ECOInfiniteStorage(engine,Component.empty(),()->true);AEKey[] domainKeys=keys(1000);
        for(AEKey key:domainKeys)domain.insert(key,1000,Actionable.MODULATE,SOURCE);
        var domainNetwork=new NetworkStorage();domainNetwork.mount(0,domain);
        measure("real-domain-1000-20",domainNetwork,1000,20,domainKeys);
    }

    private static void measure(String name,NetworkStorage network,int n,int k,AEKey[] keys){
        var cache=new NetworkInventoryCache(false);var out=new KeyCounter();var amounts=new Object2LongOpenHashMap<AEKey>();
        ThreadMXBean mx=(ThreadMXBean)ManagementFactory.getThreadMXBean();mx.setThreadAllocatedMemoryEnabled(true);
        long[] elapsed=new long[5],allocated=new long[5];
        for(int round=-3;round<5;round++) {
            long bytes=mx.getThreadAllocatedBytes(Thread.currentThread().getId()),start=System.nanoTime(),sum=0;
            for(int cycle=0;cycle<500;cycle++){
                for(int i=0;i<k;i++){sum+=network.insert(keys[i],2,Actionable.MODULATE,SOURCE);sum+=network.extract(keys[i],1,Actionable.MODULATE,SOURCE);}
                cache.refresh((NetworkInventoryCache.Mounts)network,out,amounts,(key,amount)->{});sum+=out.get(keys[0]);
            }
            consumed=sum;
            if(round>=0){elapsed[round]=System.nanoTime()-start;allocated[round]=mx.getThreadAllocatedBytes(Thread.currentThread().getId())-bytes;}
        }
        JsonObject row=new JsonObject();row.addProperty("name",name);row.addProperty("n",n);row.addProperty("k",k);row.addProperty("cycles",500);
        JsonArray times=new JsonArray(),bytes=new JsonArray();for(long value:elapsed)times.add(value);for(long value:allocated)bytes.add(value);
        row.add("elapsedNs",times);row.add("allocatedBytes",bytes);SAMPLES.add(row);
    }
}
