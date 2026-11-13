package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.crafting.graph.CraftingGraphProgress;
import io.netty.buffer.Unpooled;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Publishes a report atomically after all of its independently compressed pages arrive. */
public final class CraftingReportStream {
    private static final Map<AbstractContainerMenu, Incoming> INCOMING = new WeakHashMap<>();
    private static final Map<AbstractContainerMenu, LiveSubscription> LIVE = new WeakHashMap<>();

    private static final class LiveSubscription {
        private final long version;
        private final WeakReference<LiveListener> listener;
        private CraftingGraphProgress progress = CraftingGraphProgress.EMPTY;

        private LiveSubscription(long version, LiveListener listener) {
            this.version = version;
            this.listener = new WeakReference<>(listener);
        }
    }

    public interface LiveListener {
        void graph(long version, CraftingGraphSnapshot snapshot);
        void progress(long version, CraftingGraphProgress progress);
    }

    private CraftingReportStream() {}

    public static void watch(AbstractContainerMenu menu, long version, LiveListener listener) {
        retainClientMenu(menu);
        INCOMING.remove(menu);
        LIVE.put(menu, new LiveSubscription(version, listener));
    }

    public static void stopWatching(AbstractContainerMenu menu) {
        LIVE.remove(menu);
        INCOMING.remove(menu);
    }

    public static void sendProgress(ServerPlayer player, long version, CraftingGraphProgress progress,
            CraftingGraphProgress previous) {
        MenuDataTransport.send(player, MenuDataTransport.Channel.GRAPH_PROGRESS, buf -> {
            buf.writeVarLong(version);
            progress.write(buf, previous);
        });
    }

    static void receiveProgress(AbstractContainerMenu menu, RegistryFriendlyByteBuf buf) {
        long version = buf.readVarLong();
        var subscription = LIVE.get(menu);
        if (subscription == null || subscription.version != version) {
            // The menu transport requires every completed payload to be consumed, including stale updates.
            buf.skipBytes(buf.readableBytes());
            return;
        }
        subscription.progress = CraftingGraphProgress.read(buf, subscription.progress);
        var listener = subscription.listener.get();
        if (listener != null) listener.progress(version, subscription.progress);
    }

    private static LiveListener liveListener(AbstractContainerMenu menu) {
        var reference = LIVE.get(menu);
        return reference == null ? null : reference.listener.get();
    }

    public static void send(ServerPlayer player, long version, boolean completeGraph,
            CraftingGraphSnapshot snapshot, ECOCycleItemList cycles) {
        if (MenuDataTransport.busy(player, MenuDataTransport.Channel.GRAPH)) {
            throw new IllegalStateException("Crafting report is still in flight");
        }
        try {
            snapshot.writeToPages(player.registryAccess(), cycles, page -> MenuDataTransport.sendQueued(player,
                MenuDataTransport.Channel.GRAPH, buf -> {
                    buf.writeVarLong(version);
                    buf.writeBoolean(completeGraph);
                    page.writeToPacket(buf);
                }));
        } catch (RuntimeException error) {
            MenuDataTransport.cancel(player, MenuDataTransport.Channel.GRAPH);
            throw error;
        }
    }

    static void receive(AbstractContainerMenu menu, RegistryFriendlyByteBuf buf) {
        long version = buf.readVarLong();
        boolean completeGraph = buf.readBoolean();
        var page = new CraftingGraphSnapshot.Page(buf);
        var host = menu instanceof ECOCraftConfirmMenuMode mode ? mode : null;
        if (host == null && liveListener(menu) == null) return;
        if (host != null && version < host.neoecoae$getDisplayedPlanRevision()) return;
        retainClientMenu(menu);
        var incoming = INCOMING.get(menu);
        if (incoming != null && version < incoming.version) return;
        if (page.offset() == 0) {
            incoming = new Incoming(version, completeGraph);
            INCOMING.put(menu, incoming);
        }
        if (incoming == null || incoming.version != version || incoming.completeGraph != completeGraph
                || incoming.offset != page.offset()) {
            INCOMING.remove(menu);
            throw new IllegalArgumentException("Out-of-order crafting report page");
        }
        incoming.pages.add(page.bytes());
        incoming.offset += page.bytes().length;
        if (!page.last()) return;
        INCOMING.remove(menu);
        // Composite wrapping avoids a second full-size copy of a large report.
        var raw = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(incoming.pages.size(),
            incoming.pages.toArray(byte[][]::new)), buf.registryAccess());
        try {
            var graph = CraftingGraphSnapshot.readUncompressed(raw);
            var cycles = new ECOCycleItemList(raw);
            if (raw.isReadable()) throw new IllegalArgumentException("Trailing crafting report data");
            if (host != null) {
                if (completeGraph) host.neoecoae$setDiagnostics(version, graph, cycles);
                else host.neoecoae$setMaterialReport(version, graph, cycles);
            } else {
                var listener = liveListener(menu);
                if (listener != null) listener.graph(version, graph);
            }
        } finally {
            raw.release();
        }
    }

    static void retainClientMenu(AbstractContainerMenu menu) {
        INCOMING.keySet().removeIf(previous -> previous != menu);
        LIVE.keySet().removeIf(previous -> previous != menu);
    }

    static void clear() { INCOMING.clear(); LIVE.clear(); }

    private static final class Incoming {
        final long version;
        final boolean completeGraph;
        final ArrayList<byte[]> pages = new ArrayList<>();
        int offset;

        Incoming(long version, boolean completeGraph) {
            this.version = version;
            this.completeGraph = completeGraph;
        }
    }
}
