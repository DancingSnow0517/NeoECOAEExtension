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
    private static net.minecraft.core.HolderLookup.Provider registries;
    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        for (var state : Blocks.CHEST.getStateDefinition().getPossibleStates())
            net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.add(state);
        registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
    }

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
        assertEquals(new BlockPos(10, 64, 20).asLong(), sources.get(0).describe(registries).getLong("position"));
        verify(provider, never()).pushPattern(any(), any());
        verify(be, never()).saveWithFullMetadata(any());
    }

    @Test void serverLocatorRefusesToLoadAnUnloadedTarget() {
        var be = mock(BlockEntity.class);
        when(be.getBlockPos()).thenReturn(BlockPos.ZERO);
        when(be.getBlockState()).thenReturn(Blocks.CHEST.defaultBlockState());
        var hostLevel = mock(ServerLevel.class);
        when(hostLevel.registryAccess()).thenReturn(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        when(be.getLevel()).thenReturn(hostLevel);
        when(be.saveWithFullMetadata(any())).thenReturn(new net.minecraft.nbt.CompoundTag());
        var player = mock(Player.class); var level = mock(ServerLevel.class);
        when(player.level()).thenReturn(level);
        when(player.registryAccess()).thenReturn(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        assertNull(new ECORemoteProviderLocator(be, null).locate(player, BlockEntity.class));
        verify(level, never()).getBlockEntity(any());
    }

    @Test void openingUsesTheProvidersOwnMenuEvenOutsideTheClientViewDistance() {
        var host = mock(PatternProviderLogicHost.class);
        var be = mock(BlockEntity.class);
        var level = mock(ServerLevel.class);
        var player = mock(net.minecraft.server.level.ServerPlayer.class);
        var position = new BlockPos(5000, 64, 5000);
        when(be.getLevel()).thenReturn(level);
        when(be.getBlockPos()).thenReturn(position);
        when(be.getBlockState()).thenReturn(Blocks.CHEST.defaultBlockState());
        when(be.saveWithFullMetadata(any())).thenReturn(new net.minecraft.nbt.CompoundTag());
        when(player.level()).thenReturn(level);
        when(level.hasChunkAt(position)).thenReturn(true);
        when(level.getBlockEntity(position)).thenReturn(be);
        when(level.mayInteract(player, position)).thenReturn(true);
        doAnswer(call -> {
            player.containerMenu = mock(net.minecraft.world.inventory.AbstractContainerMenu.class);
            return null;
        }).when(host).openMenu(eq(player), any());
        var source = new ECOProviderActions.Source(host, be, null, net.minecraft.network.chat.Component.empty());
        assertTrue(source.open(player));
        verify(host).openMenu(eq(player), isA(ECORemoteProviderLocator.class));
    }

    @Test void openingRejectsProvidersInAnotherDimensionOrAnUnloadedChunk() {
        var host = mock(PatternProviderLogicHost.class);
        var be = mock(BlockEntity.class);
        var level = mock(ServerLevel.class);
        var player = mock(net.minecraft.server.level.ServerPlayer.class);
        when(be.getLevel()).thenReturn(level);
        when(be.getBlockPos()).thenReturn(BlockPos.ZERO);
        var source = new ECOProviderActions.Source(host, be, null, net.minecraft.network.chat.Component.empty());
        when(player.level()).thenReturn(mock(ServerLevel.class));
        assertFalse(source.open(player));
        when(player.level()).thenReturn(level);
        assertFalse(source.open(player));
        verify(host, never()).openMenu(any(), any());
        verify(level, never()).getBlockEntity(any());
    }

    @Test void aDetachedClientHostCanBeCreatedWithoutInstallingItIntoOrReadingTheClientWorld() throws Exception {
        var serverHost = new net.minecraft.world.level.block.entity.ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        var serverLevel = mock(ServerLevel.class);
        when(serverLevel.registryAccess()).thenReturn(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        serverHost.setLevel(serverLevel);
        var locator = new ECORemoteProviderLocator(serverHost, null);
        var player = mock(Player.class);
        var level = mock(Level.class);
        var side = Level.class.getDeclaredField("isClientSide"); side.setAccessible(true); side.setBoolean(level, true);
        when(player.level()).thenReturn(level);
        when(player.registryAccess()).thenReturn(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        var proxy = locator.locate(player, BlockEntity.class);
        assertNotNull(proxy);
        assertNotSame(serverHost, proxy);
        assertSame(proxy, locator.locate(player, BlockEntity.class));
        verify(level, never()).getBlockEntity(any());
        verify(level, never()).setBlockEntity(any());
    }
}
