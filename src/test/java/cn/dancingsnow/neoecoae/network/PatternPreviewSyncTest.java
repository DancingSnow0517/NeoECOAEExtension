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
                        sent.add(buf.readNbt());
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
}
