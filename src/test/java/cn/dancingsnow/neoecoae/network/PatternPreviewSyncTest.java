package cn.dancingsnow.neoecoae.network;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.crafting.PatternPreviewEntry;
import cn.dancingsnow.neoecoae.gui.crafting.PatternPreviewSync;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatternPreviewSyncTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void changesDuringAnInitialTransferAreSentAfterItCompletesAndResendIsFull() {
        var host = mock(ECOMachineInterfaceBlockEntity.class);
        var level = mock(ServerLevel.class);
        var player = mock(ServerPlayer.class);
        player.containerMenu = mock(AbstractContainerMenu.class);
        when(player.getUUID()).thenReturn(java.util.UUID.randomUUID());
        when(level.players()).thenReturn(List.of(player));
        when(level.registryAccess()).thenReturn(RegistryAccess.EMPTY);
        when(host.getLevel()).thenReturn(level);
        when(host.getBlockPos()).thenReturn(BlockPos.ZERO);
        when(host.getPatternInterfaceSlotCount()).thenReturn(2);
        when(host.getPatternContentRevision()).thenReturn(1);
        when(host.getPatternPreviewEntry(anyInt())).thenAnswer(call -> entry(call.getArgument(0), "first"));
        var sync = spy(new PatternPreviewSync(host));
        doReturn(true).when(sync).isViewer(player);
        var busy = new AtomicBoolean();
        List<CompoundTag> sent = new ArrayList<>();
        try (var transport = mockStatic(MenuDataTransport.class)) {
            transport.when(() -> MenuDataTransport.busy(player, MenuDataTransport.Channel.PATTERNS))
                .thenAnswer(call -> busy.get());
            transport.when(() -> MenuDataTransport.send(eq(player), eq(MenuDataTransport.Channel.PATTERNS), any()))
                .thenAnswer(call -> {
                    Consumer<RegistryFriendlyByteBuf> encoder = call.getArgument(2);
                    var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
                    try {
                        encoder.accept(buf);
                        assertEquals(BlockPos.ZERO, buf.readBlockPos());
                        sent.add(cn.dancingsnow.neoecoae.gui.crafting.PatternPreviewCodec.read(buf));
                    } finally { buf.release(); }
                    return null;
                });
            sync.tick(level);
            assertEquals(1, sent.size());
            assertTrue(sent.getFirst().getBoolean("full"));
            busy.set(true);
            when(level.getGameTime()).thenReturn(5L);
            when(host.getPatternContentRevision()).thenReturn(2);
            when(host.getPatternPreviewEntry(1)).thenReturn(entry(1, "changed"));
            sync.dirty(1, 1);
            sync.tick(level);
            assertEquals(1, sent.size());
            busy.set(false);
            when(level.getGameTime()).thenReturn(10L);
            sync.tick(level);
            assertEquals(2, sent.size());
            var delta = sent.getLast();
            assertFalse(delta.getBoolean("full"));
            assertEquals(1, delta.getInt("base"));
            assertEquals(2, delta.getInt("revision"));
            assertEquals(1, delta.getList("entries", Tag.TAG_COMPOUND).size());
            assertEquals("changed", delta.getList("entries", Tag.TAG_COMPOUND).getCompound(0).getString("keywords"));
            when(level.getGameTime()).thenReturn(15L);
            sync.tick(level);
            assertEquals(2, sent.size(), "unchanged previews must not be resent");
            sync.resend(player);
            when(level.getGameTime()).thenReturn(20L);
            sync.tick(level);
            assertTrue(sent.getLast().getBoolean("full"));
            assertEquals(2, sent.getLast().getList("entries", Tag.TAG_COMPOUND).size());
        }
    }

    private static PatternPreviewEntry entry(int slot, String keywords) {
        // 合并后的记录多了「盘内配方行 + 本槽是不是辅助容器」两个分量（样板磁盘那一半），这里按「普通样板槽、
        // 没有盘内行」构造，与测试关心的同步去重/重发语义无关。
        return new PatternPreviewEntry(0L, slot, ItemStack.EMPTY, keywords, (byte) 0, List.of(), false);
    }

    @Test void initialPagesAreImmutableAndDirtySlotsSurviveUntilTheNextRevision() {
        try (Fixture f = new Fixture(600)) {
            for (int i = 0; i < 100 && f.sent.isEmpty(); i++) f.tick();
            assertFalse(f.sent.isEmpty(), "initial snapshot must finish building");
            assertTrue(f.sent.getFirst().getBoolean("first"));
            assertFalse(f.sent.getFirst().getBoolean("last"));
            when(f.host.getPatternContentRevision()).thenReturn(2);
            when(f.host.getPatternPreviewEntry(599)).thenReturn(entry(599, "changed while paging"));
            f.sync.dirty(599, 1);
            for (int i = 0; i < 20 && f.sent.size() < 4; i++) f.tick();
            assertEquals(4, f.sent.size());
            assertTrue(f.sent.get(2).getBoolean("last"));
            assertEquals(1, f.sent.get(2).getInt("revision"));
            assertEquals("first", f.sent.get(2).getList("entries", Tag.TAG_COMPOUND).getCompound(87).getString("keywords"));
            CompoundTag delta = f.sent.get(3);
            assertFalse(delta.getBoolean("full"));
            assertEquals(1, delta.getInt("base"));
            assertEquals(1, delta.getList("entries", Tag.TAG_COMPOUND).size());
            assertEquals(599, delta.getList("entries", Tag.TAG_COMPOUND).getCompound(0).getInt("index"));
            clearInvocations(f.host);
            f.tick();
            verify(f.host, never()).getPatternPreviewEntry(anyInt());
            assertEquals(4, f.sent.size());
        }
    }

    @Test void topologyResetOfTheSameSizeReplacesTheSnapshot() {
        try (Fixture f = new Fixture(2)) {
            f.tick();
            when(f.host.getPatternContentRevision()).thenReturn(2);
            f.sync.reset();
            f.tick();
            assertEquals(2, f.sent.size());
            assertTrue(f.sent.getLast().getBoolean("full"));
            assertEquals(2, f.sent.getLast().getInt("revision"));
        }
    }

    @Test void continuousChangesAtTheStartOfTheCatalogueCannotStarveTheEnd() {
        try (Fixture f = new Fixture(1024)) {
            for (int tick = 0; tick < 200 && f.sent.size() < 4; tick++) {
                when(f.host.getPatternContentRevision()).thenReturn(tick + 1);
                f.sync.dirty(0, 512);
                f.tick();
            }
            assertTrue(f.sent.size() >= 4);
            assertTrue(f.sent.get(3).getBoolean("last"));
            assertEquals(1023, f.sent.get(3).getList("entries", Tag.TAG_COMPOUND).getCompound(255).getInt("index"));
        }
    }

    @Test void reopeningUsesANewFullSnapshotAndDoesNotLeakTheOldMenuPages() {
        try (Fixture f = new Fixture(600)) {
            for (int i = 0; i < 100 && f.sent.isEmpty(); i++) f.tick();
            assertFalse(f.sent.isEmpty());
            f.player.containerMenu = mock(AbstractContainerMenu.class);
            int before = f.sent.size();
            f.tick();
            assertEquals(before + 1, f.sent.size());
            assertTrue(f.sent.getLast().getBoolean("full"));
            assertTrue(f.sent.getLast().getBoolean("first"));
        }
    }

    @Test void aSecondViewerGetsItsOwnBaselineWhileBothShareDirtyEncoding() {
        try (Fixture f = new Fixture(2)) {
            f.tick();
            var second = mock(ServerPlayer.class);
            second.containerMenu = mock(AbstractContainerMenu.class);
            when(second.getUUID()).thenReturn(java.util.UUID.randomUUID());
            doReturn(true).when(f.sync).isViewer(second);
            when(f.level.players()).thenReturn(List.of(f.player, second));
            List<CompoundTag> secondPackets = new ArrayList<>();
            f.transport.when(() -> MenuDataTransport.send(eq(second), eq(MenuDataTransport.Channel.PATTERNS), any()))
                    .thenAnswer(call -> {
                        Consumer<RegistryFriendlyByteBuf> encoder = call.getArgument(2);
                        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
                        try {
                            encoder.accept(buf);
                            buf.readBlockPos();
                            secondPackets.add(cn.dancingsnow.neoecoae.gui.crafting.PatternPreviewCodec.read(buf));
                        } finally { buf.release(); }
                        return null;
                    });
            f.tick();
            assertEquals(1, f.sent.size());
            assertTrue(secondPackets.getFirst().getBoolean("full"));
            when(f.host.getPatternPreviewEntry(1)).thenReturn(entry(1, "changed"));
            when(f.host.getPatternContentRevision()).thenReturn(2);
            clearInvocations(f.host);
            f.sync.dirty(1, 1);
            f.tick();
            verify(f.host, times(1)).getPatternPreviewEntry(1);
            assertEquals(2, f.sent.size());
            assertEquals(2, secondPackets.size());
            assertEquals(f.sent.getLast(), secondPackets.getLast());
        }
    }

    @Test void noViewerDoesNoEncodingAndOneDiskRecipeChangeDoesNotResendTheWholeDisk() {
        try (Fixture f = new Fixture(1)) {
            when(f.level.players()).thenReturn(List.of());
            f.tick();
            verify(f.host, never()).getPatternPreviewEntry(anyInt());
            when(f.level.players()).thenReturn(List.of(f.player));
            List<PatternPreviewEntry.DiskPattern> disk = new ArrayList<>();
            for (int i = 0; i < 100; i++) disk.add(new PatternPreviewEntry.DiskPattern(ItemStack.EMPTY, "recipe " + i, (byte) 0));
            when(f.host.getPatternPreviewEntry(0)).thenReturn(new PatternPreviewEntry(0, 0, ItemStack.EMPTY,
                    "", (byte) 0, List.copyOf(disk), true));
            f.tick();
            disk.set(50, new PatternPreviewEntry.DiskPattern(ItemStack.EMPTY, "new recipe", (byte) 0));
            when(f.host.getPatternPreviewEntry(0)).thenReturn(new PatternPreviewEntry(0, 0, ItemStack.EMPTY,
                    "", (byte) 0, List.copyOf(disk), true));
            when(f.host.getPatternContentRevision()).thenReturn(2);
            f.sync.dirty(0, 1);
            f.tick();
            var change = f.sent.getLast().getList("entries", Tag.TAG_COMPOUND).getCompound(0);
            assertEquals(100, change.getInt("diskSize"));
            assertEquals(1, change.getList("diskChanges", Tag.TAG_COMPOUND).size());
            assertEquals(50, change.getList("diskChanges", Tag.TAG_COMPOUND).getCompound(0).getInt("recipe"));
            assertFalse(change.contains("disk"));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final ECOMachineInterfaceBlockEntity<?> host = mock(ECOMachineInterfaceBlockEntity.class);
        final ServerLevel level = mock(ServerLevel.class);
        final ServerPlayer player = mock(ServerPlayer.class);
        final PatternPreviewSync sync;
        final List<CompoundTag> sent = new ArrayList<>();
        final org.mockito.MockedStatic<MenuDataTransport> transport = mockStatic(MenuDataTransport.class);
        long time;

        Fixture(int size) {
            player.containerMenu = mock(AbstractContainerMenu.class);
            when(player.getUUID()).thenReturn(java.util.UUID.randomUUID());
            when(level.players()).thenReturn(List.of(player));
            when(level.registryAccess()).thenReturn(RegistryAccess.EMPTY);
            when(host.getLevel()).thenReturn(level);
            when(host.getBlockPos()).thenReturn(BlockPos.ZERO);
            when(host.getPatternInterfaceSlotCount()).thenReturn(size);
            when(host.getPatternContentRevision()).thenReturn(1);
            when(host.getPatternPreviewEntry(anyInt())).thenAnswer(call -> entry(call.getArgument(0), "first"));
            sync = spy(new PatternPreviewSync(host));
            doReturn(true).when(sync).isViewer(player);
            transport.when(() -> MenuDataTransport.send(eq(player), eq(MenuDataTransport.Channel.PATTERNS), any()))
                    .thenAnswer(call -> {
                        Consumer<RegistryFriendlyByteBuf> encoder = call.getArgument(2);
                        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
                        try {
                            encoder.accept(buf);
                            buf.readBlockPos();
                            sent.add(cn.dancingsnow.neoecoae.gui.crafting.PatternPreviewCodec.read(buf));
                        } finally { buf.release(); }
                        return null;
                    });
        }

        void tick() {
            when(level.getGameTime()).thenReturn(time);
            time += 5;
            sync.tick(level);
        }

        @Override public void close() { transport.close(); }
    }
}
