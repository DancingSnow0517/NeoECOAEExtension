package cn.dancingsnow.neoecoae.network;

import io.netty.buffer.Unpooled;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Main-thread, menu-scoped transport. All bulk channels share one player's tick budget. */
public final class MenuDataTransport {
    public enum Channel { TERMINAL, CPU, GRAPH, PATTERNS }
    public static final int UPDATE_INTERVAL = 5;
    // Includes a conservative allowance for payload id, framing and chunk metadata.
    public static final int TICK_BYTES = 34 * 1024;
    private static final int FRAME_ALLOWANCE = 128;
    private static final Map<ServerPlayer, ArrayDeque<Transfer>> OUTGOING = new HashMap<>();
    private static final Map<AbstractContainerMenu, Map<Channel, MenuStreamAssembler>> INCOMING = new WeakHashMap<>();
    private static final Map<Channel, Stats> STATS = new EnumMap<>(Channel.class);
    private static long nextTransfer;

    private MenuDataTransport() {}

    public static boolean busy(ServerPlayer player, Channel channel) {
        var queue = OUTGOING.get(player);
        return queue != null && queue.stream().anyMatch(t -> t.menu == player.containerMenu && t.channel == channel);
    }

    public static void cancel(ServerPlayer player, Channel channel) {
        var queue = OUTGOING.get(player);
        if (queue != null) queue.removeIf(t -> t.channel == channel);
    }

    public static void send(ServerPlayer player, Channel channel, Consumer<RegistryFriendlyByteBuf> encoder) {
        if (busy(player, channel)) throw new IllegalStateException("Menu stream is still in flight: " + channel);
        long start = System.nanoTime();
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(256, MenuStreamAssembler.MAX_BYTES), player.registryAccess());
        try {
            encoder.accept(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            if (bytes.length == 0) throw new IllegalArgumentException("Empty menu stream");
            OUTGOING.computeIfAbsent(player, ignored -> new ArrayDeque<>())
                .add(new Transfer(player.containerMenu, channel, ++nextTransfer, bytes));
            Stats stats = STATS.computeIfAbsent(channel, ignored -> new Stats());
            stats.transfers++;
            stats.encodeNanos += System.nanoTime() - start;
        } finally {
            buf.release();
        }
    }

    public static void tick(ServerTickEvent.Post event) {
        var it = OUTGOING.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            var player = entry.getKey();
            if (player.getServer() != event.getServer()) continue;
            var queue = entry.getValue();
            queue.removeIf(t -> player.hasDisconnected() || t.menu != player.containerMenu);
            int budget = TICK_BYTES;
            while (!queue.isEmpty() && budget > FRAME_ALLOWANCE) {
                var transfer = queue.removeFirst();
                int length = Math.min(Math.min(ECOMenuChunkS2CPacket.CHUNK_BYTES, budget - FRAME_ALLOWANCE),
                    transfer.bytes.length - transfer.offset);
                PacketDistributor.sendToPlayer(player, new ECOMenuChunkS2CPacket(transfer.menu.containerId,
                    transfer.channel, transfer.id, transfer.bytes.length, transfer.offset,
                    Arrays.copyOfRange(transfer.bytes, transfer.offset, transfer.offset + length)));
                transfer.offset += length;
                budget -= length + FRAME_ALLOWANCE;
                var stats = STATS.get(transfer.channel);
                stats.packets++;
                stats.bytes += length;
                if (transfer.offset < transfer.bytes.length) queue.addLast(transfer);
            }
            peakPlayerTickBytes = Math.max(peakPlayerTickBytes, TICK_BYTES - budget);
            if (queue.isEmpty()) it.remove();
        }
    }

    public static void receive(Player player, ECOMenuChunkS2CPacket packet) {
        var menu = player.containerMenu;
        INCOMING.keySet().removeIf(previous -> previous != menu);
        if (menu.containerId != packet.containerId()) return;
        var streams = INCOMING.computeIfAbsent(menu, ignored -> new EnumMap<>(Channel.class));
        byte[] complete = streams.computeIfAbsent(packet.channel(), ignored -> new MenuStreamAssembler())
            .accept(packet.transfer(), packet.total(), packet.offset(), packet.bytes());
        if (complete == null) return;
        var buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(complete), player.registryAccess());
        try {
            MenuDataCodecs.receive(player, packet.channel(), buf);
            if (buf.isReadable()) throw new IllegalArgumentException("Trailing menu stream data");
        } finally {
            buf.release();
        }
    }

    public static void retainClientMenu(AbstractContainerMenu menu) {
        INCOMING.keySet().removeIf(previous -> previous != menu);
    }

    private static long peakPlayerTickBytes;

    public static String report() {
        StringBuilder result = new StringBuilder("ECO bulk sync (encoded body bytes, before transport compression):");
        STATS.forEach((channel, s) -> result.append("\n").append(channel).append(": transfers=")
            .append(s.transfers).append(", packets=").append(s.packets).append(", bytes=").append(s.bytes)
            .append(", encodeMs=").append(s.encodeNanos / 1_000_000.0));
        return result.append("\npeak player/tick budget bytes=").append(peakPlayerTickBytes)
            .append(", players queued=").append(OUTGOING.size()).toString();
    }

    public static void stopped(ServerStoppedEvent event) {
        OUTGOING.clear();
        STATS.clear();
        ExactCpuSnapshot.clear();
        peakPlayerTickBytes = 0;
    }

    private static final class Stats { long transfers, packets, bytes, encodeNanos; }
    private static final class Transfer {
        final AbstractContainerMenu menu;
        final Channel channel;
        final long id;
        final byte[] bytes;
        int offset;
        Transfer(AbstractContainerMenu menu, Channel channel, long id, byte[] bytes) {
            this.menu = menu;
            this.channel = channel;
            this.id = id;
            this.bytes = bytes;
        }
    }
}
