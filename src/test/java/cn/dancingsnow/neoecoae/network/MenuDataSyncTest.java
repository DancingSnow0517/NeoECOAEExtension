package cn.dancingsnow.neoecoae.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MenuDataSyncTest {
    @Test
    void largeTransferIsAtomicBoundedAndSilentAfterCompletion() {
        byte[] original = new byte[3 * 1024 * 1024 + 7];
        new Random(31).nextBytes(original);
        Harness h = new Harness();
        h.server.source(MenuDataSync.EXACT, () -> original);
        h.deliver(h.server.poll(7, 0, MenuDataSync.EXACT));
        int packets = 0;
        for (int tick = 1; h.target.data == null; tick++) {
            assertTrue(tick < 300);
            var part = h.server.poll(7, tick, MenuDataSync.EXACT);
            assertNotNull(part);
            byte[] wire = BoundedData.encode(32 * 1024, out -> MenuDataFragment.encode(part, out));
            assertTrue(wire.length <= BoundedData.PART_BYTES + 64);
            assertNull(h.server.poll(7, tick, MenuDataSync.EXACT), "Repeated broadcast in one tick must not burst");
            h.deliver(part);
            packets++;
            if (part.offset() + part.data().length < original.length) assertNull(h.target.data);
        }
        assertArrayEquals(original, h.target.data);
        for (int tick = 300; tick < 1300; tick++) assertNull(h.server.poll(7, tick, MenuDataSync.EXACT));
        System.out.println("3 MiB transfer: " + packets + " parts, <= " + (BoundedData.PART_BYTES + 64) + " B/part");
    }

    @Test
    void graphIsOnlyEncodedAfterRequestAndDuplicateRequestsAreIgnored() {
        Harness h = new Harness();
        AtomicInteger reports = new AtomicInteger();
        AtomicInteger graphs = new AtomicInteger();
        h.server.source(MenuDataSync.REPORT, () -> {
            reports.incrementAndGet();
            return new byte[] {1};
        });
        h.server.source(MenuDataSync.GRAPH, () -> {
            graphs.incrementAndGet();
            return new byte[] {2};
        });
        h.deliver(h.server.poll(7, 0, MenuDataSync.REPORT));
        h.deliver(h.server.poll(7, 1, MenuDataSync.REPORT));
        assertEquals(1, reports.get());
        assertEquals(0, graphs.get());
        var request = h.target.sync.request(7, MenuDataSync.GRAPH);
        h.server.acceptRequest(request);
        h.server.acceptRequest(request);
        h.deliver(h.server.poll(7, 2, MenuDataSync.REPORT));
        assertEquals(1, graphs.get());
        assertNull(h.server.poll(7, 3, MenuDataSync.REPORT));
    }

    @Test
    void oldMenuFragmentsAndReplannedResultsCannotPolluteTheCurrentMenu() {
        Harness h = new Harness();
        h.server.source(MenuDataSync.EXACT, () -> new byte[40_000]);
        var announcement = h.server.poll(7, 0, MenuDataSync.EXACT);
        h.deliver(announcement);
        var oldPart = h.server.poll(7, 1, MenuDataSync.EXACT);
        h.deliver(oldPart);
        h.server.invalidate();
        h.server.source(MenuDataSync.EXACT, () -> new byte[] {9});
        h.deliver(h.server.poll(7, 2, MenuDataSync.EXACT));
        h.deliver(oldPart);
        assertNull(h.target.data);
        h.deliver(h.server.poll(7, 3, MenuDataSync.EXACT));
        assertArrayEquals(new byte[] {9}, h.target.data);

        // Even if a container id is reused, an old announcement elicits a fresh client nonce.
        var newTarget = new Target();
        newTarget.sync.receive(7, newTarget, announcement, ignored -> {}, fail -> fail(fail));
        newTarget.sync.receive(7, newTarget, oldPart, ignored -> {}, fail -> fail(fail));
        assertNull(newTarget.data);
    }

    @Test
    void closeCancelsPendingTransfersAndInvalidSizesFailWithoutSendingData() {
        Harness h = new Harness();
        h.server.source(MenuDataSync.EXACT, () -> new byte[40_000]);
        h.deliver(h.server.poll(7, 0, MenuDataSync.EXACT));
        h.deliver(h.server.poll(7, 1, MenuDataSync.EXACT));
        h.server.close();
        assertNull(h.server.poll(7, 2, MenuDataSync.EXACT));
        assertNull(h.target.data);

        Harness tooLarge = new Harness();
        tooLarge.server.source(MenuDataSync.REPORT, () -> BoundedData.encode(32, b -> b.writeBytes(new byte[33])));
        tooLarge.deliver(tooLarge.server.poll(7, 0, MenuDataSync.REPORT));
        var error = tooLarge.server.poll(7, 1, MenuDataSync.REPORT);
        assertEquals(-1, error.total());
        assertEquals(0, error.data().length);
        assertNull(tooLarge.server.poll(7, 2, MenuDataSync.REPORT));
    }

    @Test
    void invalidFragmentOrderAndAllocationAreRejected() {
        var receiver = new BoundedData.Receiver();
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(BoundedData.MAX_BYTES + 1, 0, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(10, 1, new byte[1]));
        assertNull(receiver.accept(10, 0, new byte[5]));
        assertThrows(IllegalArgumentException.class, () -> receiver.accept(10, 6, new byte[4]));
    }

    private static final class Harness {
        final MenuDataSync server = new MenuDataSync();
        final Target target = new Target();

        void deliver(MenuDataFragment part) {
            if (part != null) target.sync.receive(7, target, part, server::acceptRequest, message -> fail(message));
        }
    }

    private static final class Target implements NetworkMenu {
        final MenuDataSync sync = new MenuDataSync();
        byte[] data;

        @Override
        public MenuDataSync neoecoae$dataSync() {
            return sync;
        }

        @Override
        public void neoecoae$resetData() {
            data = null;
        }

        @Override
        public void neoecoae$receiveData(int kind, byte[] value) {
            data = value;
        }
    }
}
