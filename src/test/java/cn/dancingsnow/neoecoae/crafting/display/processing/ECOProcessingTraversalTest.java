package cn.dancingsnow.neoecoae.crafting.display.processing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingStatusReader;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingStatusRegistry;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOProcessingTargetProvider.Target;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.State;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ECOProcessingTraversalTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("test", "progress_reader");

    @Test void customDestinationProvidersAreAcceptedWithoutWorkstationTypeChecks() {
        var provider = mock(ICraftingProvider.class, withSettings().extraInterfaces(ECOProcessingTargetProvider.class));
        var destination = target(7);
        when(((ECOProcessingTargetProvider) provider).neoecoae$getProcessingTarget()).thenReturn(destination);
        assertEquals(List.of(destination), ECOWorkstationMachineStatus.targets(provider));
    }

    @Test void linkedDestinationsAreDeduplicatedAndContextReachesTheReader() {
        var fixture = new Fixture();
        UUID job = UUID.randomUUID();
        var calls = new AtomicInteger();
        ECOProcessingStatusRegistry.register(ID, new ECOProcessingStatusReader() {
            @Override public boolean supports(BlockEntity machine) { return true; }
            @Override public Observation read(BlockEntity machine, Context context) {
                calls.incrementAndGet();
                assertEquals(job, context.jobId());
                assertEquals(AEItemKey.of(Items.STONE), context.output());
                return machine == fixture.first ? new Observation(null, List.of(target(0), target(1), target(1)))
                    : Observation.sample(sample());
            }
        });
        try {
            var scan = new ECOWorkstationMachineStatus.Scan(List.of(target(0), target(0)), AEItemKey.of(Items.STONE), job);
            scan.step(fixture.level);
            assertFalse(scan.complete());
            scan.step(fixture.level);
            assertTrue(scan.complete());
            assertEquals(2, calls.get());
            assertEquals(List.of(target(1)), scan.machineTargets());
            assertEquals(1, scan.machines().size());
        } finally { ECOProcessingStatusRegistry.unregister(ID); }
    }

    @Test void missingChunkAndDimensionNeverResolveOrLoadABlockEntity() {
        var fixture = new Fixture();
        when(fixture.level.hasChunkAt(any())).thenReturn(false);
        var scan = new ECOWorkstationMachineStatus.Scan(List.of(target(0), new Target(Level.NETHER.location(), BlockPos.ZERO)),
            AEItemKey.of(Items.STONE));
        scan.step(fixture.level);
        scan.step(fixture.level);
        assertTrue(scan.complete());
        assertTrue(scan.machines().isEmpty());
        verify(fixture.level, never()).getBlockEntity(any());
        verify(fixture.level, never()).getChunk(anyInt(), anyInt());
    }

    @Test void brokenOptionalReaderDoesNotDiscardOtherMachineSamples() {
        var fixture = new Fixture();
        ECOProcessingStatusRegistry.register(ID, new ECOProcessingStatusReader() {
            @Override public boolean supports(BlockEntity machine) { return true; }
            @Override public Observation read(BlockEntity machine, Context context) {
                if (machine == fixture.first) throw new NoClassDefFoundError("Optional addon API changed");
                return Observation.sample(sample());
            }
        });
        try {
            var scan = new ECOWorkstationMachineStatus.Scan(List.of(target(0), target(1)), AEItemKey.of(Items.STONE));
            assertDoesNotThrow(() -> scan.step(fixture.level));
            scan.step(fixture.level);
            assertTrue(scan.complete());
            assertEquals(1, scan.machines().size());
        } finally { ECOProcessingStatusRegistry.unregister(ID); }
    }

    private static Machine sample() { return new Machine(Component.literal("addon"), "test", "test", 25, 100,
        1, State.RUNNING, Component.empty()); }
    private static Target target(int x) { return new Target(Level.OVERWORLD.location(), new BlockPos(x, 0, 0)); }
    private static final class Fixture {
        final ServerLevel level = mock(ServerLevel.class);
        final MinecraftServer server = mock(MinecraftServer.class);
        final BlockEntity first = mock(BlockEntity.class), second = mock(BlockEntity.class);
        Fixture() {
            when(level.getServer()).thenReturn(server);
            when(server.getLevel(Level.OVERWORLD)).thenReturn(level);
            when(level.hasChunkAt(any())).thenReturn(true);
            when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(first);
            when(level.getBlockEntity(new BlockPos(1, 0, 0))).thenReturn(second);
        }
    }
}
