package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Publishes a report atomically after all of its independently compressed pages arrive. */
public final class CraftingReportStream {
    private static final Map<AbstractContainerMenu, Incoming> INCOMING = new WeakHashMap<>();

    private CraftingReportStream() {}

    public static void send(ServerPlayer player, long version, boolean completeGraph,
            CraftingGraphSnapshot snapshot, ECOCycleItemList cycles) {
        if (MenuDataTransport.busy(player, MenuDataTransport.Channel.GRAPH)) {
            throw new IllegalStateException("Crafting report is still in flight");
        }
        try {
            snapshot.writeToPages(cycles, page -> MenuDataTransport.sendQueued(player,
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

    static void receive(AbstractContainerMenu menu, FriendlyByteBuf buf) {
        long version = buf.readVarLong();
        boolean completeGraph = buf.readBoolean();
        var page = new CraftingGraphSnapshot.Page(buf);
        if (!(menu instanceof ECOCraftConfirmMenuMode host)
                || version < host.neoecoae$getDisplayedPlanRevision()) return;
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
        var raw = new FriendlyByteBuf(Unpooled.wrappedBuffer(incoming.pages.size(),
            incoming.pages.toArray(byte[][]::new)));
        try {
            var graph = CraftingGraphSnapshot.readUncompressed(raw);
            var cycles = new ECOCycleItemList(raw);
            if (raw.isReadable()) throw new IllegalArgumentException("Trailing crafting report data");
            if (completeGraph) host.neoecoae$setDiagnostics(version, graph, cycles);
            else host.neoecoae$setMaterialReport(version, graph, cycles);
        } finally {
            raw.release();
        }
    }

    static void retainClientMenu(AbstractContainerMenu menu) {
        INCOMING.keySet().removeIf(previous -> previous != menu);
    }

    static void clear() { INCOMING.clear(); }

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
