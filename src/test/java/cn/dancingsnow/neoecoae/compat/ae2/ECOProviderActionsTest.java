package cn.dancingsnow.neoecoae.compat.ae2;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.PatternProviderLogicAccessor;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOProviderActionsTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void providerLookupIsDeduplicatedAndDoesNotPushOrProbeInventories() {
        var provider = mock(ICraftingProvider.class, withSettings().extraInterfaces(PatternProviderLogicAccessor.class));
        var host = mock(PatternProviderLogicHost.class);
        var be = mock(BlockEntity.class);
        var level = mock(ServerLevel.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(be.getLevel()).thenReturn(level);
        when(be.getBlockPos()).thenReturn(new BlockPos(10, 64, 20));
        when(host.getBlockEntity()).thenReturn(be);
        when(host.getTerminalIcon()).thenReturn(AEItemKey.of(Items.STONE));
        when(((PatternProviderLogicAccessor) provider).neoecoae$getHost()).thenReturn(host);
        var first = mock(IPatternDetails.class); var second = mock(IPatternDetails.class);
        var crafting = mock(CraftingService.class);
        when(crafting.getProviders(first)).thenReturn(List.of(provider));
        when(crafting.getProviders(second)).thenReturn(List.of(provider));
        var sources = ECOProviderActions.find(List.of(first, second), crafting);
        assertEquals(1, sources.size());
        assertEquals(new BlockPos(10, 64, 20).asLong(), sources.get(0).describe().getLong("position"));
        verify(provider, never()).pushPattern(any(), any());
        verify(be, never()).saveWithFullMetadata();
    }

    @Test void serverLocatorRefusesToLoadAnUnloadedTarget() {
        var be = mock(BlockEntity.class);
        when(be.getBlockPos()).thenReturn(BlockPos.ZERO);
        when(be.getBlockState()).thenReturn(Blocks.CHEST.defaultBlockState());
        when(be.saveWithFullMetadata()).thenReturn(new net.minecraft.nbt.CompoundTag());
        var player = mock(Player.class); var level = mock(ServerLevel.class);
        when(player.level()).thenReturn(level);
        assertNull(new ECORemoteProviderLocator(be, null).locate(player, BlockEntity.class));
        verify(level, never()).getBlockEntity(any());
    }

    @Test void aDetachedClientHostCanBeCreatedWithoutInstallingItIntoOrReadingTheClientWorld() throws Exception {
        var serverHost = new net.minecraft.world.level.block.entity.ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        var locator = new ECORemoteProviderLocator(serverHost, null);
        var player = mock(Player.class);
        var level = mock(Level.class);
        var side = Level.class.getDeclaredField("isClientSide"); side.setAccessible(true); side.setBoolean(level, true);
        when(player.level()).thenReturn(level);
        var proxy = locator.locate(player, BlockEntity.class);
        assertNotNull(proxy);
        assertNotSame(serverHost, proxy);
        assertSame(proxy, locator.locate(player, BlockEntity.class));
        verify(level, never()).getBlockEntity(any());
        verify(level, never()).setBlockEntity(any());
    }
}
