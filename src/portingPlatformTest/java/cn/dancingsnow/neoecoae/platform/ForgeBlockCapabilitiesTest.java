package cn.dancingsnow.neoecoae.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.items.IItemHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeBlockCapabilitiesTest {
    @BeforeAll static void bootstrap() { PlatformTestBootstrap.initialize(); }
    @Test
    @SuppressWarnings("unchecked")
    void attachesSidedHandlersCachesThemAndInvalidatesThePublishedOptional() {
        BlockEntityType<BlockEntity> type = mock(BlockEntityType.class);
        BlockEntity host = mock(BlockEntity.class);
        doReturn(type).when(host).getType();
        IItemHandler north = mock(IItemHandler.class);
        Capability<IItemHandler> capability = mock(Capability.class);
        ForgeBlockCapabilities.register(capability, type,
            (block, side) -> side == Direction.NORTH ? north : null);
        var event = new AttachCapabilitiesEvent<>(BlockEntity.class, host);
        ForgeBlockCapabilities.attach(event);
        var provider = event.getCapabilities().values().iterator().next();
        var optional = provider.getCapability(capability, Direction.NORTH);
        assertSame(north, optional.orElseThrow(AssertionError::new));
        assertSame(optional, provider.getCapability(capability, Direction.NORTH));
        assertFalse(provider.getCapability(capability, Direction.SOUTH).isPresent());
        event.getListeners().forEach(Runnable::run);
        assertFalse(optional.isPresent());
        assertFalse(provider.getCapability(capability, Direction.NORTH).isPresent());
        ForgeBlockCapabilities.revive(host);
        var revived = provider.getCapability(capability, Direction.NORTH);
        assertTrue(revived.isPresent());
        ForgeBlockCapabilities.refresh(host);
        assertFalse(revived.isPresent());
        assertTrue(provider.getCapability(capability, Direction.NORTH).isPresent());
    }

    @Test
    @SuppressWarnings("unchecked")
    void absentCapabilitiesCanBecomeAvailableAfterAFormationChange() {
        BlockEntityType<BlockEntity> type = mock(BlockEntityType.class);
        BlockEntity host = mock(BlockEntity.class);
        doReturn(type).when(host).getType();
        Capability<IItemHandler> capability = mock(Capability.class);
        var enabled = new java.util.concurrent.atomic.AtomicBoolean();
        IItemHandler handler = mock(IItemHandler.class);
        ForgeBlockCapabilities.register(capability, type, (block, side) -> enabled.get() ? handler : null);
        var event = new AttachCapabilitiesEvent<>(BlockEntity.class, host);
        ForgeBlockCapabilities.attach(event);
        var provider = event.getCapabilities().values().iterator().next();
        assertFalse(provider.getCapability(capability, null).isPresent());
        enabled.set(true);
        assertSame(handler, provider.getCapability(capability, null).orElseThrow(AssertionError::new));
        enabled.set(false);
        ForgeBlockCapabilities.refresh(host);
        assertFalse(provider.getCapability(capability, null).isPresent());
    }

    @Test
    void neighborLookupNeverLoadsAnUnloadedChunk() {
        Level level = mock(Level.class);
        assertNull(ForgeBlockCapabilities.find(level, BlockPos.ZERO, mock(Capability.class), Direction.NORTH));
        verify(level, never()).getBlockEntity(any());
    }
}
