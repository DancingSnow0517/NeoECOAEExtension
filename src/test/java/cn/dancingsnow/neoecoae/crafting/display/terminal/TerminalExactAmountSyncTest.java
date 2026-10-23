package cn.dancingsnow.neoecoae.crafting.display.terminal;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.menu.me.common.MEStorageMenu;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.SupplierStorageAccessor;
import cn.dancingsnow.neoecoae.network.ExactMapSync;
import cn.dancingsnow.neoecoae.network.MapDelta;
import cn.dancingsnow.neoecoae.network.MenuDataTransport;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TerminalExactAmountSyncTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @AfterEach void clear() {
        ExactAmountCollector.abort();
        TerminalInventorySnapshot.clear();
    }

    @Test void twoViewersShareScansAndSendOnlyChangedOverflowEntries() {
        try (var f = new Fixture()) {
            var first = f.viewer();
            var second = f.viewer();
            first.sync.collect(first.menu, f.storage, f::list);
            second.sync.collect(second.menu, f.storage, f::list);
            assertEquals(1, f.listings);
            assertEquals(1, f.exactListings);
            assertEquals(2, f.frames.size());
            assertTrue(f.frames.stream().allMatch(Frame::full));

            f.tick(5);
            first.sync.collect(first.menu, f.storage, f::list);
            second.sync.collect(second.menu, f.storage, f::list);
            assertEquals(2, f.listings);
            assertEquals(2, f.frames.size());

            f.extra = 65;
            f.tick(10);
            first.sync.collect(first.menu, f.storage, f::list);
            second.sync.collect(second.menu, f.storage, f::list);
            assertEquals(3, f.exactListings);
            assertEquals(4, f.frames.size());
            assertFalse(JdkCompat.last(f.frames).full());
            assertEquals(Map.of(f.key, ExactAmount.finite(BigInteger.valueOf(Long.MAX_VALUE)
                .add(BigInteger.valueOf(65)))), JdkCompat.last(f.frames).delta().updates());

            f.base = 128;
            f.tick(15);
            first.sync.collect(first.menu, f.storage, f::list);
            second.sync.collect(second.menu, f.storage, f::list);
            assertEquals(6, f.frames.size());
            assertEquals(Set.of(f.key), JdkCompat.last(f.frames).delta().removed());
            assertTrue(JdkCompat.last(f.frames).delta().updates().isEmpty());
            f.extra = 66;
            f.tick(20);
            first.sync.collect(first.menu, f.storage, f::list);
            assertEquals(6, f.frames.size(), "Ordinary long updates belong to AE2's existing stream");
        }
    }

    @Test void menuInitializationDoesNotConsumeTheFirstFullSnapshot() {
        try (var f = new Fixture()) {
            var viewer = f.viewer();
            viewer.player.containerMenu = mock(MEStorageMenu.class);
            viewer.sync.collect(viewer.menu, f.storage, f::list);
            assertTrue(f.frames.isEmpty());
            assertEquals(0, f.exactListings);
            viewer.player.containerMenu = viewer.menu;
            viewer.sync.collect(viewer.menu, f.storage, f::list);
            assertEquals(1, f.frames.size());
            assertTrue(JdkCompat.first(f.frames).full());
        }
    }

    @Test void busyTransportDoesNotAdvanceTheDeltaBaselineOrCollectExactAmounts() {
        try (var f = new Fixture()) {
            var viewer = f.viewer();
            viewer.sync.collect(viewer.menu, f.storage, f::list);
            f.extra = 512;
            f.busy.set(true);
            f.tick(5);
            viewer.sync.collect(viewer.menu, f.storage, f::list);
            assertEquals(1, f.exactListings);
            assertEquals(1, f.frames.size());
            f.busy.set(false);
            f.tick(6);
            viewer.sync.collect(viewer.menu, f.storage, f::list);
            assertEquals(2, f.frames.size());
            assertFalse(JdkCompat.last(f.frames).full());
            assertEquals(ExactAmount.finite(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(512))),
                JdkCompat.last(f.frames).delta().updates().get(f.key));
        }
    }

    @Test void supplierChangingNetworksCancelsQueuedDataAndSendsAFreshFullSnapshot() {
        try (var f = new Fixture()) {
            var viewer = f.viewer();
            var supplier = mock(MEStorage.class, withSettings().extraInterfaces(SupplierStorageAccessor.class));
            var other = mock(MEStorage.class);
            when(((SupplierStorageAccessor) supplier).neoecoae$getDelegate()).thenReturn(f.storage, other);
            viewer.sync.collect(viewer.menu, supplier, f::list);
            f.extra = 99;
            f.busy.set(true);
            f.tick(1);
            viewer.sync.collect(viewer.menu, supplier, f::list);
            f.transport.verify(() -> MenuDataTransport.cancel(viewer.player, MenuDataTransport.Channel.TERMINAL));
            assertEquals(2, f.frames.size());
            assertTrue(JdkCompat.last(f.frames).full());
            assertEquals(2, f.exactListings);
        }
    }

    @Test void failedSendRetriesAFullSnapshotWithoutAdvancingTheBaseline() {
        try (var f = new Fixture()) {
            var viewer = f.viewer();
            f.failNextSend = true;
            assertThrows(IllegalStateException.class,
                () -> viewer.sync.collect(viewer.menu, f.storage, f::list));
            assertFalse(ExactAmountCollector.isActive());
            viewer.sync.collect(viewer.menu, f.storage, f::list);
            assertEquals(1, f.listings);
            assertEquals(1, f.frames.size());
            assertTrue(JdkCompat.first(f.frames).full());
        }
    }

    private record Frame(boolean full, MapDelta<AEKey, ExactAmount> delta) {}
    private record Viewer(MEStorageMenu menu, ServerPlayer player, TerminalExactAmountSync sync) {}

    private static final class Fixture implements AutoCloseable {
        final AEKey key = mock(AEKey.class);
        final MEStorage storage = mock(MEStorage.class);
        final ServerLevel level = mock(ServerLevel.class);
        final AtomicBoolean busy = new AtomicBoolean();
        final List<Frame> frames = new ArrayList<>();
        final MockedStatic<MenuDataTransport> transport = mockStatic(MenuDataTransport.class);
        final MockedStatic<ExactMapSync> codec = mockStatic(ExactMapSync.class);
        MapDelta<AEKey, ExactAmount> encoded;
        long base = Long.MAX_VALUE;
        long extra = 64;
        int listings;
        int exactListings;
        boolean failNextSend;

        Fixture() {
            when(key.getPrimaryKey()).thenReturn(new Object());
            transport.when(() -> MenuDataTransport.busy(any(), eq(MenuDataTransport.Channel.TERMINAL)))
                .thenAnswer(call -> busy.get());
            transport.when(() -> MenuDataTransport.cancel(any(), eq(MenuDataTransport.Channel.TERMINAL)))
                .thenAnswer(call -> { busy.set(false); return null; });
            codec.when(() -> ExactMapSync.write(any(), any())).thenAnswer(call -> {
                encoded = call.getArgument(1);
                return null;
            });
            transport.when(() -> MenuDataTransport.send(any(), eq(MenuDataTransport.Channel.TERMINAL), any()))
                .thenAnswer(call -> {
                    if (failNextSend) {
                        failNextSend = false;
                        throw new IllegalStateException("send failed");
                    }
                    Consumer<FriendlyByteBuf> encoder = call.getArgument(2);
                    var buffer = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        encoded = null;
                        encoder.accept(buffer);
                        frames.add(new Frame(buffer.readBoolean(), encoded));
                    } finally { buffer.release(); }
                    return null;
                });
        }

        Viewer viewer() {
            var menu = mock(MEStorageMenu.class);
            var player = mock(ServerPlayer.class);
            when(menu.getPlayer()).thenReturn(player);
            when(player.level()).thenReturn(level);
            player.containerMenu = menu;
            return new Viewer(menu, player, new TerminalExactAmountSync());
        }

        void tick(long tick) { when(level.getGameTime()).thenReturn(tick); }

        KeyCounter list() {
            listings++;
            if (ExactAmountCollector.isActive()) exactListings++;
            var out = new KeyCounter();
            var accumulator = new StorageListingAccumulator();
            accumulator.append(storage, out, counter -> counter.add(key, base));
            accumulator.append(storage, out, counter -> counter.add(key, extra));
            return out;
        }

        @Override public void close() {
            codec.close();
            transport.close();
        }
    }
}
