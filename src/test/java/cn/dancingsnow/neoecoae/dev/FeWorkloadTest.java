package cn.dancingsnow.neoecoae.dev;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeWorkloadTest {
    @Test void interleavedProductionAndConsumptionConserveActualEnergy() {
        var workload = new FeWorkload(new FeWorkloadSettings(2, 10, 10, 3, 8, 8, 42, true));
        var storage = new Storage(100);
        workload.tick(storage);
        assertEquals(0, storage.energy);
        var report = workload.report();
        assertEquals(BigInteger.valueOf(20), report.get("acceptedInFE"));
        assertEquals(BigInteger.valueOf(20), report.get("extractedFE"));
        assertEquals(BigInteger.valueOf(4), report.get("unmetOutFE"));
        assertEquals(BigInteger.ZERO, report.get("netAddedFE"));
        assertEquals(5L, report.get("simulationCalls"));
        assertEquals(5L, report.get("executionCalls"));
        assertEquals(List.of("in:10:true", "in:10:false", "out:8:true", "out:8:false",
            "in:10:true", "in:10:false", "out:8:true", "out:8:false", "out:8:true", "out:4:false"), storage.calls);
    }

    @Test void fullStorageRejectsWithoutRefundOrRetry() {
        var workload = new FeWorkload(new FeWorkloadSettings(2, 10, 10, 0, 1, 1, 1, true));
        var storage = new Storage(15);
        workload.tick(storage);
        workload.tick(storage);
        var report = workload.report();
        assertEquals(15, storage.energy);
        assertEquals(BigInteger.valueOf(15), report.get("acceptedInFE"));
        assertEquals(BigInteger.valueOf(25), report.get("rejectedInFE"));
        assertEquals(4L, report.get("simulationCalls"));
        assertEquals(2L, report.get("executionCalls"));
    }

    @Test void simulationResultsAreNeverCreditedAsActualTransfers() {
        var workload = new FeWorkload(new FeWorkloadSettings(1, 10, 10, 1, 9, 9, 1, true));
        workload.tick(new FeWorkload.Access() {
            public long insert(long amount, boolean simulate) { return simulate ? 8 : 3; }
            public long extract(long amount, boolean simulate) { return simulate ? 4 : 2; }
        });
        var report = workload.report();
        assertEquals(BigInteger.valueOf(3), report.get("acceptedInFE"));
        assertEquals(BigInteger.valueOf(2), report.get("extractedFE"));
        assertEquals(BigInteger.ONE, report.get("netAddedFE"));
        assertEquals(4L, report.get("storageCalls"));
    }

    @Test void countersRetainTotalsBeyondLongRange() {
        var workload = new FeWorkload(new FeWorkloadSettings(2, Long.MAX_VALUE, Long.MAX_VALUE, 0, 1, 1, 1, false));
        workload.tick(new FeWorkload.Access() {
            public long insert(long amount, boolean simulate) { return amount; }
            public long extract(long amount, boolean simulate) { fail("No consumers configured"); return 0; }
        });
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO), workload.report().get("acceptedInFE"));
        assertEquals(0L, workload.report().get("simulationCalls"));
        assertEquals(2L, workload.report().get("executionCalls"));
    }

    @Test void seedReplaysIdenticalAmountsAndCallOrder() {
        var settings = new FeWorkloadSettings(4, 1, 100, 7, 1, 100, 12345, true);
        var first = new FeWorkload(settings);
        var second = new FeWorkload(settings);
        var left = new Storage(1000);
        var right = new Storage(1000);
        for (int i = 0; i < 5; i++) { first.tick(left); second.tick(right); }
        assertEquals(left.calls, right.calls);
        assertEquals(first.report().get("requestedInFE"), second.report().get("requestedInFE"));
        assertEquals(first.report().get("requestedOutFE"), second.report().get("requestedOutFE"));
    }

    @Test void changingReuseSettingStopsBeforeMixingMeasurements() {
        var workload = new FeWorkload(new FeWorkloadSettings(1, 1, 1, 0, 1, 1, 1, false));
        var storage = new Storage(100);
        workload.tick(storage);
        storage.reuse = true;
        assertThrows(IllegalStateException.class, () -> workload.tick(storage));
        assertEquals(1, storage.calls.size());
        new FeWorkload(workload.settings).tick(storage);
        assertEquals(2, storage.calls.size());
    }

    @Test void invalidAcceptanceFailsWithoutRetry() {
        var workload = new FeWorkload(new FeWorkloadSettings(1, 1, 1, 0, 1, 1, 1, false));
        assertThrows(IllegalStateException.class, () -> workload.tick(new FeWorkload.Access() {
            public long insert(long amount, boolean simulate) { return amount + 1; }
            public long extract(long amount, boolean simulate) { fail("Unexpected extraction"); return 0; }
        }));
        assertEquals(1L, workload.report().get("storageCalls"));
        assertEquals(BigInteger.ZERO, workload.report().get("acceptedInFE"));
    }

    @Test void rejectsEmptyOrInvalidEndpointConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new FeWorkloadSettings(0, 1, 1, 0, 1, 1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new FeWorkloadSettings(1, 10, 1, 0, 1, 1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new FeWorkloadSettings(65537, 1, 1, 0, 1, 1, 0, false));
    }

    private static final class Storage implements FeWorkload.Access {
        final long capacity;
        final List<String> calls = new ArrayList<>();
        long energy;
        boolean reuse;
        Storage(long capacity) { this.capacity = capacity; }
        @Override public long insert(long amount, boolean simulate) {
            calls.add("in:" + amount + ":" + simulate);
            long accepted = Math.min(amount, capacity - energy);
            if (!simulate) energy += accepted;
            return accepted;
        }
        @Override public long extract(long amount, boolean simulate) {
            calls.add("out:" + amount + ":" + simulate);
            long accepted = Math.min(amount, energy);
            if (!simulate) energy -= accepted;
            return accepted;
        }
        @Override public Boolean keyReuseEnabled() { return reuse; }
    }
}
