package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import appeng.api.stacks.AEItemKey;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Base64;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphStreamTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    private org.mockito.MockedStatic<appeng.api.stacks.AEKeyTypesInternal> keyTypes;
    private org.mockito.MockedStatic<net.neoforged.neoforge.registries.RegistryManager> registrySync;
    @org.junit.jupiter.api.BeforeEach void keyRegistry() {
        registrySync = mockStatic(net.neoforged.neoforge.registries.RegistryManager.class);
        keyTypes = mockStatic(appeng.api.stacks.AEKeyTypesInternal.class);
        keyTypes.when(appeng.api.stacks.AEKeyTypesInternal::getRegistry)
            .thenReturn(cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys.REGISTRY);
    }
    @AfterEach void clear() {
        try { MenuDataTransport.stopped(mock(ServerStoppedEvent.class)); }
        finally { keyTypes.close(); registrySync.close(); }
    }

    @Test void compressedGraphAndCycleRowsRoundTripAcrossManyChunks() {
        List<CraftingGraphSnapshot.PatternNode> patterns = new ArrayList<>();
        Random random = new Random(43);
        for (int i = 0; i < 4000; i++) patterns.add(new CraftingGraphSnapshot.PatternNode(i,
            Long.toHexString(random.nextLong()) + Long.toHexString(random.nextLong()), List.of(), List.of(),
            5L, CraftingGraphSnapshot.CandidateStatus.SELECTED, null, -1));
        String wide = java.math.BigInteger.ONE.shiftLeft(160).toString();
        var row = new CraftingGraphSnapshot.MaterialNode(0, AEItemKey.of(Items.IRON_INGOT),
            Long.MAX_VALUE, 3, Long.MAX_VALUE, 7, CraftingGraphSnapshot.MaterialStatus.MISSING,
            wide, "3", wide, "7", wide, wide);
        var graph = new CraftingGraphSnapshot(0, List.of(row), patterns, List.of(), List.of(), List.of(),
            new CraftingGraphSnapshot.Summary("MISSING_ITEMS", 1, patterns.size(), 0, 0, 123));
        var report = graph.materialReport();
        var player = mock(Player.class);
        when(player.registryAccess()).thenReturn(registries());
        player.containerMenu = mock(AbstractContainerMenu.class,
            withSettings().extraInterfaces(ECOCraftConfirmMenuMode.class));
        var host = (ECOCraftConfirmMenuMode) player.containerMenu;
        for (boolean complete : List.of(false, true)) {
            var snapshot = complete ? graph : report;
            byte[] bytes = pages(snapshot, 17, complete).get(0);
            if (complete) assertTrue(bytes.length > MenuDataTransport.TICK_BYTES);
            else assertTrue(bytes.length < 1024, "The initial rows must not wait for all recipe nodes");
            var assembler = new MenuStreamAssembler();
            byte[] completed = null;
            for (int offset = 0; offset < bytes.length; offset += 16_384)
                completed = assembler.accept(1, bytes.length, offset,
                    Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + 16_384)));
            assertNotNull(completed);
            var received = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(completed), registries());
            try {
                MenuDataCodecs.receive(player, MenuDataTransport.Channel.GRAPH, received);
                assertFalse(received.isReadable());
            } finally { received.release(); }
            if (complete) verify(host).neoecoae$setDiagnostics(17, graph, ECOCycleItemList.EMPTY);
            else {
                verify(host).neoecoae$setMaterialReport(17, report, ECOCycleItemList.EMPTY);
                verify(host, never()).neoecoae$setDiagnostics(anyLong(), any(), any());
                assertEquals(List.of(row), report.nodes());
            }
        }
    }

    @Test void graphLargerThanSixteenMiBUsesQueuedPagesWithoutDelayingMaterialRows() {
        var graph = largeGraph(1600);
        var cycles = new ECOCycleItemList(List.of(new ECOCycleItemList.Entry(AEItemKey.of(Items.IRON_INGOT),
            5L, 25L, 4)));
        var server = mock(MinecraftServer.class);
        var sender = mock(ServerPlayer.class);
        when(sender.registryAccess()).thenReturn(registries());
        sender.containerMenu = mock(AbstractContainerMenu.class);
        var serverLevel = mock(net.minecraft.server.level.ServerLevel.class);
        when(sender.level()).thenReturn(serverLevel);
        when(serverLevel.getServer()).thenReturn(server);
        var client = mock(Player.class);
        when(client.registryAccess()).thenReturn(registries());
        client.containerMenu = mock(AbstractContainerMenu.class,
            withSettings().extraInterfaces(ECOCraftConfirmMenuMode.class));
        var host = (ECOCraftConfirmMenuMode) client.containerMenu;
        long[] bytes = {0};
        int[] tickBytes = {0};
        try (var packets = mockStatic(PacketDistributor.class)) {
            packets.when(() -> PacketDistributor.sendToPlayer(eq(sender), any(ECOMenuChunkS2CPacket.class)))
                .thenAnswer(call -> {
                    ECOMenuChunkS2CPacket packet = call.getArgument(1);
                    assertTrue(packet.total() < CraftingGraphSnapshot.Page.MAX_BYTES * 2);
                    bytes[0] += packet.bytes().length;
                    tickBytes[0] += packet.bytes().length + 128;
                    MenuDataTransport.receive(client, packet);
                    return null;
                });
            var tick = new ServerTickEvent.Post(() -> true, server);
            CraftingReportStream.send(sender, 19, false, graph.materialReport(), cycles);
            MenuDataTransport.tick(tick);
            verify(host).neoecoae$setMaterialReport(19, graph.materialReport(), cycles);
            verify(host, never()).neoecoae$setDiagnostics(anyLong(), any(), any());
            assertFalse(MenuDataTransport.busy(sender, MenuDataTransport.Channel.GRAPH));

            bytes[0] = 0;
            CraftingReportStream.send(sender, 19, true, graph, cycles);
            int ticks = 0;
            while (MenuDataTransport.busy(sender, MenuDataTransport.Channel.GRAPH)) {
                assertTrue(++ticks < 2000, "The queued pages must eventually finish");
                tickBytes[0] = 0;
                MenuDataTransport.tick(tick);
                assertTrue(tickBytes[0] <= MenuDataTransport.TICK_BYTES);
                if (MenuDataTransport.busy(sender, MenuDataTransport.Channel.GRAPH)) {
                    verify(host, never()).neoecoae$setDiagnostics(anyLong(), any(), any());
                }
            }
            assertTrue(bytes[0] > 16 * 1024 * 1024, "This report must exceed the original crash threshold");
            verify(host).neoecoae$setDiagnostics(19, graph, cycles);
        }
    }

    @Test void newRevisionDiscardsAnIncompleteGraphAndIgnoresItsRemainingPages() {
        var graph = largeGraph(140);
        var older = pages(graph, 20, true);
        var newer = pages(graph, 21, true);
        assertTrue(older.size() > 1);
        var client = mock(Player.class);
        when(client.registryAccess()).thenReturn(registries());
        client.containerMenu = mock(AbstractContainerMenu.class,
            withSettings().extraInterfaces(ECOCraftConfirmMenuMode.class));
        var host = (ECOCraftConfirmMenuMode) client.containerMenu;
        receive(client, older.get(0));
        receive(client, newer.get(0));
        for (int i = 1; i < older.size(); i++) receive(client, older.get(i));
        verify(host, never()).neoecoae$setDiagnostics(anyLong(), any(), any());
        for (int i = 1; i < newer.size(); i++) receive(client, newer.get(i));
        verify(host).neoecoae$setDiagnostics(21, graph, ECOCycleItemList.EMPTY);
        verify(host, never()).neoecoae$setDiagnostics(eq(20L), any(), any());
    }

    @Test void lateProgressIsConsumedAfterClosingOrReplacingTheGraph() {
        var menu = mock(AbstractContainerMenu.class);
        var listener = mock(CraftingReportStream.LiveListener.class);
        var progress = new cn.dancingsnow.neoecoae.crafting.graph.CraftingGraphProgress(
            java.util.Set.of(1), java.util.Set.of(2));
        CraftingReportStream.watch(menu, 21, listener);
        for (boolean closed : List.of(false, true)) {
            if (closed) CraftingReportStream.stopWatching(menu);
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries());
            try {
                buf.writeVarLong(closed ? 21 : 20);
                progress.write(buf);
                CraftingReportStream.receiveProgress(menu, buf);
                assertFalse(buf.isReadable(), "Late progress must satisfy the menu transport framing contract");
            } finally { buf.release(); }
        }
        verifyNoInteractions(listener);
    }

    private static RegistryAccess registries() {
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static CraftingGraphSnapshot largeGraph(int count) {
        var patterns = new ArrayList<CraftingGraphSnapshot.PatternNode>();
        var random = new Random(71);
        byte[] identity = new byte[12 * 1024];
        for (int i = 0; i < count; i++) {
            random.nextBytes(identity);
            patterns.add(new CraftingGraphSnapshot.PatternNode(-i - 2, Base64.getEncoder().encodeToString(identity),
                List.of(), List.of(), 5L, CraftingGraphSnapshot.CandidateStatus.SELECTED, null, -1));
        }
        var row = new CraftingGraphSnapshot.MaterialNode(0, AEItemKey.of(Items.IRON_INGOT),
            100, 1, 99, 0, CraftingGraphSnapshot.MaterialStatus.CRAFTING);
        return new CraftingGraphSnapshot(0, List.of(row), patterns, List.of(), List.of(), List.of(),
            new CraftingGraphSnapshot.Summary("SUCCESS", 1, count, 0, 0, 123));
    }

    private static List<byte[]> pages(CraftingGraphSnapshot graph, long version, boolean complete) {
        var result = new ArrayList<byte[]>();
        graph.writeToPages(registries(), ECOCycleItemList.EMPTY, page -> {
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries());
            try {
                buf.writeVarLong(version);
                buf.writeBoolean(complete);
                page.writeToPacket(buf);
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                result.add(bytes);
            } finally { buf.release(); }
        });
        return result;
    }

    private static void receive(Player client, byte[] bytes) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), registries());
        try {
            MenuDataCodecs.receive(client, MenuDataTransport.Channel.GRAPH, buf);
            assertFalse(buf.isReadable());
        } finally { buf.release(); }
    }
}
