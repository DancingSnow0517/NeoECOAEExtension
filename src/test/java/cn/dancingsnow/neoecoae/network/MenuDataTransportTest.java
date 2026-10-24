package cn.dancingsnow.neoecoae.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import cn.dancingsnow.neoecoae.network.ECONetwork;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuDataTransportTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    @AfterEach void clear() { MenuDataTransport.stopped(mock(ServerStoppedEvent.class)); }

    @Test void allBulkChannelsShareTheBudgetAndAreDrainedFairly() {
        var server = mock(MinecraftServer.class);
        var player = player(server);
        var tick = new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server);
        List<ECOMenuChunkS2CPacket> sent = new ArrayList<>();
        try (var packets = mockStatic(ECONetwork.class);
             var lifecycle = mockStatic(net.minecraftforge.server.ServerLifecycleHooks.class)) {
            lifecycle.when(net.minecraftforge.server.ServerLifecycleHooks::getCurrentServer).thenReturn(server);
            packets.when(() -> ECONetwork.sendToPlayer(eq(player), any(ECOMenuChunkS2CPacket.class)))
                .thenAnswer(call -> { sent.add(call.getArgument(1)); return null; });
            MenuDataTransport.send(player, MenuDataTransport.Channel.GRAPH, buf -> buf.writeBytes(new byte[100_000]));
            MenuDataTransport.send(player, MenuDataTransport.Channel.TERMINAL, buf -> buf.writeBytes(new byte[40_000]));
            assertThrows(IllegalStateException.class, () -> MenuDataTransport.send(player,
                MenuDataTransport.Channel.GRAPH, buf -> buf.writeByte(1)));
            for (int i = 0; i < 10 && MenuDataTransport.busy(player, MenuDataTransport.Channel.GRAPH); i++) {
                int start = sent.size();
                MenuDataTransport.tick(tick);
                int used = sent.subList(start, sent.size()).stream().mapToInt(p -> p.bytes().length + 128).sum();
                assertTrue(used <= MenuDataTransport.TICK_BYTES);
                assertTrue(sent.subList(start, sent.size()).stream().allMatch(p -> p.bytes().length <= 16_384));
                if (i == 0) assertEquals(2, sent.stream().map(ECOMenuChunkS2CPacket::channel).distinct().count());
            }
            assertEquals(140_000, sent.stream().mapToInt(p -> p.bytes().length).sum());
            assertFalse(MenuDataTransport.busy(player, MenuDataTransport.Channel.GRAPH));
        }
    }

    @Test void closingMenuOrDisconnectingDropsPendingDataAndAllowsFreshSnapshot() {
        var server = mock(MinecraftServer.class);
        var player = player(server);
        try (var packets = mockStatic(ECONetwork.class);
             var lifecycle = mockStatic(net.minecraftforge.server.ServerLifecycleHooks.class)) {
            lifecycle.when(net.minecraftforge.server.ServerLifecycleHooks::getCurrentServer).thenReturn(server);
            MenuDataTransport.send(player, MenuDataTransport.Channel.CPU, buf -> buf.writeBytes(new byte[60_000]));
            player.containerMenu = mock(AbstractContainerMenu.class);
            MenuDataTransport.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
            packets.verifyNoInteractions();
            assertFalse(MenuDataTransport.busy(player, MenuDataTransport.Channel.CPU));
            MenuDataTransport.send(player, MenuDataTransport.Channel.CPU, buf -> buf.writeByte(1));
            MenuDataTransport.cancel(player, MenuDataTransport.Channel.CPU);
            assertFalse(MenuDataTransport.busy(player, MenuDataTransport.Channel.CPU));
            MenuDataTransport.send(player, MenuDataTransport.Channel.CPU, buf -> buf.writeByte(2));
            when(player.hasDisconnected()).thenReturn(true);
            MenuDataTransport.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server));
            packets.verifyNoInteractions();
        }
    }

    @Test void queuedPatternPagesShareOneTickAndNeverOvertakeAnIncompletePage() {
        var server = mock(MinecraftServer.class);
        var player = player(server);
        List<ECOMenuChunkS2CPacket> sent = new ArrayList<>();
        var tick = new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server);
        try (var packets = mockStatic(ECONetwork.class);
             var lifecycle = mockStatic(net.minecraftforge.server.ServerLifecycleHooks.class)) {
            lifecycle.when(net.minecraftforge.server.ServerLifecycleHooks::getCurrentServer).thenReturn(server);
            packets.when(() -> ECONetwork.sendToPlayer(eq(player), any(ECOMenuChunkS2CPacket.class)))
                    .thenAnswer(call -> { sent.add(call.getArgument(1)); return null; });
            for (int i = 0; i < 3; i++) {
                int value = i;
                MenuDataTransport.sendQueued(player, MenuDataTransport.Channel.PATTERNS, buf -> buf.writeByte(value));
            }
            MenuDataTransport.tick(tick);
            assertEquals(3, sent.size(), "small pages must not each wait for a new game tick");
            assertFalse(MenuDataTransport.busy(player, MenuDataTransport.Channel.PATTERNS));
            sent.clear();
            MenuDataTransport.sendQueued(player, MenuDataTransport.Channel.PATTERNS, buf -> buf.writeBytes(new byte[50_000]));
            MenuDataTransport.sendQueued(player, MenuDataTransport.Channel.PATTERNS, buf -> buf.writeByte(7));
            MenuDataTransport.send(player, MenuDataTransport.Channel.GRAPH, buf -> buf.writeBytes(new byte[40_000]));
            for (int i = 0; i < 5; i++) MenuDataTransport.tick(tick);
            var assembler = new MenuStreamAssembler();
            List<byte[]> completed = new ArrayList<>();
            for (var packet : sent) {
                if (packet.channel() != MenuDataTransport.Channel.PATTERNS) continue;
                byte[] bytes = assembler.accept(packet.transfer(), packet.total(), packet.offset(), packet.bytes());
                if (bytes != null) completed.add(bytes);
            }
            assertEquals(2, completed.size());
            assertEquals(50_000, completed.get(0).length);
            assertArrayEquals(new byte[]{7}, completed.get(1));
            assertFalse(MenuDataTransport.busy(player, MenuDataTransport.Channel.PATTERNS));
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        var player = mock(ServerPlayer.class);
        player.containerMenu = mock(AbstractContainerMenu.class);
        when(player.getServer()).thenReturn(server);
        return player;
    }
}
