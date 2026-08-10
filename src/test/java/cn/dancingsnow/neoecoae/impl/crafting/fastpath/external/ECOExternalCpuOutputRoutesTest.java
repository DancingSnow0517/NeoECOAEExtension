package cn.dancingsnow.neoecoae.impl.crafting.fastpath.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ECOExternalCpuOutputRoutesTest {
    @Test
    void routesOnlyToTheCpuThatStillOwnsTheJob() {
        UUID jobId = UUID.randomUUID();
        var unavailable = ECOExternalCpuOutputRoutes.deliver(jobId, null, 12L, Actionable.MODULATE);
        assertFalse(unavailable.routeAvailable());

        TestSink sink = new TestSink(jobId, 7L);
        ECOExternalCpuOutputRoutes.register(jobId, sink);
        var delivered = ECOExternalCpuOutputRoutes.deliver(jobId, null, 12L, Actionable.MODULATE);
        assertTrue(delivered.routeAvailable());
        assertEquals(7L, delivered.inserted());

        sink.owner = UUID.randomUUID();
        assertFalse(ECOExternalCpuOutputRoutes.deliver(jobId, null, 12L, Actionable.MODULATE)
                .routeAvailable());
    }

    @Test
    void unregisterDoesNotRemoveARouteReplacedByAnotherCpu() {
        UUID jobId = UUID.randomUUID();
        TestSink previousSink = new TestSink(jobId, 3L);
        TestSink currentSink = new TestSink(jobId, 11L);
        ECOExternalCpuOutputRoutes.register(jobId, previousSink);
        ECOExternalCpuOutputRoutes.register(jobId, currentSink);

        ECOExternalCpuOutputRoutes.unregister(jobId, previousSink);
        assertEquals(
                11L,
                ECOExternalCpuOutputRoutes.deliver(jobId, null, 12L, Actionable.MODULATE)
                        .inserted());

        ECOExternalCpuOutputRoutes.unregister(jobId, currentSink);
        assertFalse(ECOExternalCpuOutputRoutes.deliver(jobId, null, 12L, Actionable.MODULATE)
                .routeAvailable());
    }

    private static final class TestSink implements ECOExternalCpuOutputRoutes.Sink {
        private UUID owner;
        private final long accepted;

        private TestSink(UUID owner, long accepted) {
            this.owner = owner;
            this.accepted = accepted;
        }

        @Override
        public boolean neoecoae$ownsJob(UUID craftingJobId) {
            return owner.equals(craftingJobId);
        }

        @Override
        public long neoecoae$insertJobOutput(AEKey what, long amount, Actionable type) {
            return Math.min(amount, accepted);
        }
    }
}
