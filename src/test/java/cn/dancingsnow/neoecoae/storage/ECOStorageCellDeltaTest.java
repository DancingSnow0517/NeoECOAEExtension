package cn.dancingsnow.neoecoae.storage;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ECOStorageCellDeltaTest {
    private static final IActionSource SOURCE=IActionSource.empty();
    @BeforeAll static void bootstrap() throws Exception { StorageTestContext.bootstrap(); }

    @Test void fiveHundredKeysTwentyChangesAndTwoReadersAvoidRelisting(@TempDir Path directory) throws Exception {
        try(var context=new StorageTestContext(directory)) {
            AEKey[] keys=StorageTestContext.keys(500,false);
            for(AEKey key:keys)context.cell.insert(key,1000,Actionable.MODULATE,SOURCE);
            var mounts=new StorageWorkloadBenchmark.Mounts(List.of(context.cell));
            var cache=new NetworkInventoryCache(false);var published=new KeyCounter();var amounts=new Object2LongOpenHashMap<AEKey>();
            int[] listings={0};
            assertTrue(cache.refresh(mounts,published,amounts,(key,amount)->{},(storage,out)->{listings[0]++;storage.getAvailableStacks(out);}));
            long before=context.cell.neoecoae$listingVersion();
            for(int round=0;round<100;round++)for(int i=0;i<20;i++) {
                context.cell.insert(keys[i],3,Actionable.MODULATE,SOURCE);
                context.cell.extract(keys[i],1,Actionable.MODULATE,SOURCE);
            }
            Map<AEKey,Long> first=new HashMap<>(), second=new HashMap<>();
            long now=context.cell.neoecoae$listingVersion();
            assertTrue(context.cell.neoecoae$visitListingChanges(before,now,first::put));
            assertTrue(context.cell.neoecoae$visitListingChanges(before,now,second::put));
            assertEquals(first,second);assertEquals(20,first.size());
            for(int i=0;i<20;i++)assertEquals(1200L,first.get(keys[i]));
            assertTrue(cache.refresh(mounts,published,amounts,(key,amount)->{},(storage,out)->{listings[0]++;storage.getAvailableStacks(out);}));
            assertEquals(1,listings[0]);assertEquals(500,published.size());
            for(int i=0;i<500;i++)assertEquals(i<20?1200:1000,published.get(keys[i]));
        }
    }

    @Test void deletionAndReadmissionKeepOnlyTheCurrentValue(@TempDir Path directory) throws Exception {
        try(var context=new StorageTestContext(directory)) {
            AEKey[] keys=StorageTestContext.keys(2,false);
            for(AEKey key:keys)context.cell.insert(key,100,Actionable.MODULATE,SOURCE);
            long before=context.cell.neoecoae$listingVersion();
            context.cell.extract(keys[0],100,Actionable.MODULATE,SOURCE);
            context.cell.extract(keys[1],100,Actionable.MODULATE,SOURCE);
            context.cell.insert(keys[0],7,Actionable.MODULATE,SOURCE);
            Map<AEKey,Long> changes=new HashMap<>();
            assertTrue(context.cell.neoecoae$visitListingChanges(before,context.cell.neoecoae$listingVersion(),changes::put));
            assertEquals(Map.of(keys[0],7L,keys[1],0L),changes);
            assertEquals(1,context.cell.getStoredItemTypes());
        }
    }

    @Test void expiredReaderGetsNoPartialDelta(@TempDir Path directory) throws Exception {
        try(var context=new StorageTestContext(directory)) {
            long before=context.cell.neoecoae$listingVersion();
            for(AEKey key:StorageTestContext.keys(4200,true))context.cell.insert(key,1,Actionable.MODULATE,SOURCE);
            int[] visits={0};
            assertFalse(context.cell.neoecoae$visitListingChanges(before,context.cell.neoecoae$listingVersion(),(key,amount)->visits[0]++));
            assertEquals(0,visits[0]);assertEquals(4200,context.cell.getAvailableStacks().size());
        }
    }

    @Test void sealingAndUnsealingUseAFullVisibilityBarrier(@TempDir Path directory) throws Exception {
        try(var context=new StorageTestContext(directory)) {
            var key=StorageTestContext.keys(1,false)[0];context.cell.insert(key,19,Actionable.MODULATE,SOURCE);
            long readable=context.cell.neoecoae$listingVersion();
            ECOInfiniteStorageMember.markMember(context.stack,UUID.randomUUID());
            assertEquals(-1,context.cell.neoecoae$listingVersion());assertTrue(context.cell.getAvailableStacks().isEmpty());
            assertFalse(context.cell.neoecoae$visitListingChanges(readable,-1,(k,amount)->fail("Partial sealed delta")));
            long revision=context.cell.contentRevision();
            assertEquals(0,context.cell.insert(key,9,Actionable.MODULATE,SOURCE));
            assertEquals(revision,context.cell.contentRevision());
            ECOInfiniteStorageMember.clearMember(context.stack);
            assertFalse(context.cell.neoecoae$visitListingChanges(-1,context.cell.neoecoae$listingVersion(),(k,amount)->fail("Partial reopened delta")));
            assertEquals(19,context.cell.getAvailableStacks().get(key));
        }
    }
}
