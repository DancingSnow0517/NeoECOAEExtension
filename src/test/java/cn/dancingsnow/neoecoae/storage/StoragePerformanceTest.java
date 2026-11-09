package cn.dancingsnow.neoecoae.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named="neoecoae.storagePerformance",matches="true")
class StoragePerformanceTest {
    @Test void resourceTransitionsAreSeparateFromTombstoneAllocation() throws Exception {
        StorageTestContext.bootstrap();
        var keys = StorageTestContext.keys(2, false);
        var amounts = new cn.dancingsnow.neoecoae.storage.persistence.StorageAmounts();
        amounts.enableChangeLog(1);
        StoragePerformance.reset();
        try {
            amounts.add(keys[0], 3);
            amounts.subtract(keys[0], 3);
            amounts.add(keys[0], 4);
            amounts.subtract(keys[0], 4);
            amounts.add(keys[1], 1);
            amounts.clear();
            var counters = StoragePerformance.snapshot().getAsJsonObject("counters");
            assertEquals(3, counters.getAsJsonObject("NEW_KEY").get("total").getAsLong());
            assertEquals(3, counters.getAsJsonObject("REMOVED_KEY").get("total").getAsLong());
            assertEquals(2, counters.getAsJsonObject("ENTRY_CREATED").get("total").getAsLong());
            assertEquals(2, counters.getAsJsonObject("ENTRY_DISCARDED").get("total").getAsLong());
        } finally { StoragePerformance.reset(); }
    }

    @Test void backgroundKeyWorkCannotRaceWithServerCountersAndResetReleasesTheSession() throws Exception {
        StoragePerformance.reset();
        try {
            StoragePerformance.count(StoragePerformance.Counter.KEY_CANONICALIZE);
            long worker=CompletableFuture.supplyAsync(()->{
                StoragePerformance.reset();
                try {
                    StoragePerformance.add(StoragePerformance.Counter.KEY_CANONICALIZE,5);
                    return total();
                } finally { StoragePerformance.reset(); }
            }).get();
            assertEquals(5,worker);
            assertEquals(1,total());
            StoragePerformance.reset();
            assertEquals(0,total());
        } finally { StoragePerformance.reset(); }
    }

    private static long total(){return StoragePerformance.snapshot().getAsJsonObject("counters")
        .getAsJsonObject("KEY_CANONICALIZE").get("total").getAsLong();}
}
