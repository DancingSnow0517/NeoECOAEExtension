package cn.dancingsnow.neoecoae.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
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
        var tick = new ServerTickEvent.Post(() -> true, server);
        List<ECOMenuChunkS2CPacket> sent = new ArrayList<>();
        try (var packets = mockStatic(PacketDistributor.class)) {
            packets.when(() -> PacketDistributor.sendToPlayer(eq(player), any(ECOMenuChunkS2CPacket.class)))
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
        try (var packets = mockStatic(PacketDistributor.class)) {
            MenuDataTransport.send(player, MenuDataTransport.Channel.CPU, buf -> buf.writeBytes(new byte[60_000]));
            player.containerMenu = mock(AbstractContainerMenu.class);
            MenuDataTransport.tick(new ServerTickEvent.Post(() -> true, server));
            packets.verifyNoInteractions();
            assertFalse(MenuDataTransport.busy(player, MenuDataTransport.Channel.CPU));
            MenuDataTransport.send(player, MenuDataTransport.Channel.CPU, buf -> buf.writeByte(1));
            MenuDataTransport.cancel(player, MenuDataTransport.Channel.CPU);
            assertFalse(MenuDataTransport.busy(player, MenuDataTransport.Channel.CPU));
            MenuDataTransport.send(player, MenuDataTransport.Channel.CPU, buf -> buf.writeByte(2));
            when(player.hasDisconnected()).thenReturn(true);
            MenuDataTransport.tick(new ServerTickEvent.Post(() -> true, server));
            packets.verifyNoInteractions();
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        var player = mock(ServerPlayer.class);
        player.containerMenu = mock(AbstractContainerMenu.class);
        when(player.getServer()).thenReturn(server);
        when(player.registryAccess()).thenReturn(RegistryAccess.EMPTY);
        return player;
    }
}
