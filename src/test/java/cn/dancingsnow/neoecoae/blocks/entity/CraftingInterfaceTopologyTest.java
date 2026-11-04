package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.crafting.PatternPreviewSync;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraftingInterfaceTopologyTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void pooledTopologyNotificationsCoalesceWithoutReplacingElevenThousandSlotReferences() throws Exception {
        var f = new Fixture(8, 22);
        Object original = field(f.machine, "patternSlotRefs");
        int revision = f.machine.getPatternContentRevision();
        clearInvocations(f.buses.toArray());

        for (var bus : f.buses) {
            f.machine.onPatternBusTopologyChanged(bus);
        }
        assertSame(original, field(f.machine, "patternSlotRefs"));
        for (var bus : f.buses) {
            verify(bus, never()).getPatternSlotCount();
        }

        assertEquals(11_088, f.machine.getPatternInterfaceSlotCount());
        assertEquals(11_088, f.machine.getPatternInterfaceSlotCount());
        assertSame(original, field(f.machine, "patternSlotRefs"));
        assertEquals(revision, f.machine.getPatternContentRevision());
        verify(f.sync, never()).reset();
        for (var bus : f.buses) {
            verify(bus, times(1)).getPatternSlotCount();
            verify(bus, never()).getPatternSlotInventory();
        }
    }

    @ParameterizedTest
    @EnumSource(value = IGridNodeListener.State.class, names = {"POWER", "GRID_BOOT"})
    void unchangedPowerAndBootNotificationsPreserveMappingAndPreview(IGridNodeListener.State reason)
            throws Exception {
        var f = new Fixture(2, 1);
        Object original = field(f.machine, "patternSlotRefs");
        int revision = f.machine.getPatternContentRevision();
        f.machine.onMainNodeStateChanged(reason);
        f.machine.onMainNodeStateChanged(reason);

        assertSame(original, field(f.machine, "patternSlotRefs"));
        assertEquals(126, f.machine.getPatternInterfaceSlotCount());
        assertSame(original, field(f.machine, "patternSlotRefs"));
        assertEquals(revision, f.machine.getPatternContentRevision());
        verify(f.sync, never()).reset();
    }

    @Test
    void realInventoryChangeIsNotLostWhileTopologyValidationIsPending() throws Exception {
        var f = new Fixture(2, 1);
        Object original = field(f.machine, "patternSlotRefs");
        int revision = f.machine.getPatternContentRevision();
        f.machine.onMainNodeStateChanged(IGridNodeListener.State.GRID_BOOT);
        f.machine.onPatternBusInventoryChanged(f.buses.get(0), 0);

        assertEquals(revision + 1, f.machine.getPatternContentRevision());
        verify(f.sync).dirty(0, 1);
        assertEquals(126, f.machine.getPatternInterfaceSlotCount());
        assertSame(original, field(f.machine, "patternSlotRefs"));
        verify(f.sync, never()).reset();
    }

    @Test
    void replacementAtSameCoordinatesIsResolvedBeforeReadingAStack() throws Exception {
        var f = new Fixture(2, 1);
        var removed = f.buses.get(0);
        var replacement = f.bus(removed.getBlockPos(), 63);
        ItemStack expected = new ItemStack(Items.DIAMOND);
        when(replacement.getPatternSlotInventory().getStackInSlot(0)).thenReturn(expected);
        f.clusters.get(0).getPatternBuses().set(0, replacement);
        int revision = f.machine.getPatternContentRevision();
        f.machine.onPatternBusTopologyChanged(removed);

        assertSame(expected, f.machine.getPatternInterfaceStack(0));
        assertEquals(revision + 1, f.machine.getPatternContentRevision());
        verify(removed, never()).getPatternSlotInventory();
        verify(f.sync).reset();
    }

    @Test
    void changedCapacityRebuildsBeforeTheNextRead() throws Exception {
        var f = new Fixture(2, 1);
        int revision = f.machine.getPatternContentRevision();
        when(f.buses.get(0).getPatternSlotCount()).thenReturn(126);
        f.machine.onPatternBusTopologyChanged(f.buses.get(0));

        assertEquals(189, f.machine.getPatternInterfaceSlotCount());
        assertEquals(revision + 1, f.machine.getPatternContentRevision());
        verify(f.sync).reset();
    }

    @Test
    void aBusLeavingAndRejoiningTheGridIsRemovedAndRestored() throws Exception {
        var f = new Fixture(2, 1);
        var bus = f.buses.get(0);
        var rawNode = bus.getGridNode();
        when(rawNode.getGrid()).thenReturn(mock(IGrid.class));
        f.machine.onMainNodeStateChanged(IGridNodeListener.State.GRID_BOOT);
        assertEquals(63, f.machine.getPatternInterfaceSlotCount());
        assertEquals(f.buses.get(f.buses.size() - 1).getBlockPos().asLong(),
            f.machine.getPatternPreviewLayout().getCompound(0).getLong("bus"));

        when(rawNode.getGrid()).thenReturn(f.grid);
        f.machine.onMainNodeStateChanged(IGridNodeListener.State.GRID_BOOT);
        assertEquals(126, f.machine.getPatternInterfaceSlotCount());
        assertEquals(bus.getBlockPos().asLong(),
            f.machine.getPatternPreviewLayout().getCompound(0).getLong("bus"));
        verify(f.sync, times(2)).reset();
    }

    @Test
    void networkDomainChangesAreDetectedWithoutAnInterfaceNodeCallback() throws Exception {
        var f = new Fixture(2, 1);
        f.clusters.get(0).setNetworkCluster(null);
        assertEquals(63, f.machine.getPatternInterfaceSlotCount());
        f.clusters.get(0).setNetworkCluster(f.network);
        assertEquals(126, f.machine.getPatternInterfaceSlotCount());
        verify(f.sync, times(2)).reset();
    }

    @Test
    void aRealGridChangeInvalidatesTheOldSnapshotBeforeTheNextRead() throws Exception {
        var f = new Fixture(2, 1);
        IGrid nextGrid = mock(IGrid.class);
        when(f.node.getGrid()).thenReturn(nextGrid);
        f.machine.onMainNodeGridChanged();
        assertEquals(0, f.machine.getPatternInterfaceSlotCount());
        for (var bus : f.buses) {
            var rawNode = bus.getGridNode();
            when(rawNode.getGrid()).thenReturn(nextGrid);
        }
        f.machine.onMainNodeStateChanged(IGridNodeListener.State.GRID_BOOT);
        assertEquals(126, f.machine.getPatternInterfaceSlotCount());
    }

    @Test
    void removedBusAndZeroCapacityDoNotKeepStaleRows() throws Exception {
        var f = new Fixture(2, 1);
        when(f.buses.get(0).isRemoved()).thenReturn(true);
        when(f.buses.get(f.buses.size() - 1).getPatternSlotCount()).thenReturn(0);
        f.machine.onPatternBusTopologyChanged(f.buses.get(0));
        assertEquals(0, f.machine.getPatternInterfaceSlotCount());
        f.machine.onPatternBusTopologyChanged(f.buses.get(f.buses.size() - 1));
        assertEquals(0, f.machine.getPatternInterfaceSlotCount());
        verify(f.sync, times(1)).reset();
    }

    private static final class Fixture {
        final ServerLevel level = mock(ServerLevel.class);
        final IGrid grid = mock(IGrid.class);
        final IManagedGridNode node = mock(IManagedGridNode.class);
        final List<NECraftingCluster> clusters = new ArrayList<>();
        final List<ECOCraftingPatternBusBlockEntity> buses = new ArrayList<>();
        final NECraftingNetworkCluster network = new NECraftingNetworkCluster();
        final PatternPreviewSync sync = mock(PatternPreviewSync.class);
        final ECOMachineInterfaceBlockEntity<?> machine = mock(ECOMachineInterfaceBlockEntity.class, invocation ->
            switch (invocation.getMethod().getName()) {
                case "onPatternBusTopologyChanged", "onPatternBusInventoryChanged", "onMainNodeStateChanged",
                     "onMainNodeGridChanged", "getPatternInterfaceSlotCount", "getPatternInterfaceStack",
                     "getPatternContentRevision", "getPatternPreviewLayout" -> invocation.callRealMethod();
                case "supportsPatternPreview" -> true;
                default -> RETURNS_DEFAULTS.answer(invocation);
            });

        Fixture(int hosts, int busesPerHost) throws Exception {
            when(level.getServer()).thenReturn(mock(MinecraftServer.class));
            when(node.getGrid()).thenReturn(grid);
            for (int host = 0; host < hosts; host++) {
                var cluster = new NECraftingCluster(BlockPos.ZERO, BlockPos.ZERO);
                var controller = mock(ECOCraftingSystemBlockEntity.class);
                when(controller.getBlockPos()).thenReturn(new BlockPos(host, 0, 0));
                cluster.addBlockEntity(controller);
                clusters.add(cluster);
                for (int i = 0; i < busesPerHost; i++) {
                    var bus = bus(new BlockPos(buses.size(), 1, 0), 63);
                    cluster.addBlockEntity(bus);
                    buses.add(bus);
                }
            }
            network.configure(clusters);
            clusters.forEach(cluster -> cluster.setNetworkCluster(network));
            when(machine.getMainNode()).thenReturn(node);
            field(machine, "level", level);
            field(machine, "formed", true);
            field(machine, "cluster", clusters.get(0));
            field(machine, "patternSlotRefs", List.of());
            field(machine, "patternBusPositions", new long[0]);
            field(machine, "patternBusSlotCounts", new int[0]);
            field(machine, "patternBusOffsets", new int[0]);
            field(machine, "auxiliaryPreviewCache", new Long2ObjectOpenHashMap<>());
            field(machine, "busAuxiliaryPreviewRevisions", new Long2LongOpenHashMap());
            field(machine, "patternPreviewSync", sync);
            assertEquals(hosts * busesPerHost * 63, machine.getPatternInterfaceSlotCount());
            clearInvocations(sync);
        }

        ECOCraftingPatternBusBlockEntity bus(BlockPos position, int slots) {
            var bus = mock(ECOCraftingPatternBusBlockEntity.class);
            var rawNode = mock(IGridNode.class);
            var inventory = mock(InternalInventory.class);
            when(rawNode.getGrid()).thenReturn(grid);
            when(bus.getBlockPos()).thenReturn(position);
            when(bus.getGridNode()).thenReturn(rawNode);
            when(bus.getPatternSlotCount()).thenReturn(slots);
            when(bus.getPatternSlotInventory()).thenReturn(inventory);
            when(inventory.size()).thenReturn(slots);
            when(inventory.getStackInSlot(anyInt())).thenReturn(ItemStack.EMPTY);
            return bus;
        }
    }

    private static Field findField(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static Object field(Object target, String name) throws Exception {
        return findField(target, name).get(target);
    }

    private static void field(Object target, String name, Object value) throws Exception {
        findField(target, name).set(target, value);
    }
}
