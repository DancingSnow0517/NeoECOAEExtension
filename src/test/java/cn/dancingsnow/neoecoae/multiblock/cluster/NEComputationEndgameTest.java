package cn.dancingsnow.neoecoae.multiblock.cluster;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NEComputationEndgameTest {
    private static final EnumMap<ECOTier, Item> CELLS = new EnumMap<>(ECOTier.class);

    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
        var registry = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        registry.unfreeze(false);
        for (ECOTier tier : ECOTier.values()) {
            var id = net.minecraft.resources.Identifier.parse(
                "computation_endgame_test:cell_" + tier.name().toLowerCase(java.util.Locale.ROOT));
            CELLS.put(tier, Registry.register(BuiltInRegistries.ITEM, id,
                new ECOComputationCellItem(new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.ITEM, id)), tier)));
        }
        registry.freeze();
        // 26.1: default components of items registered after the bootstrap must be bound explicitly.
        cn.dancingsnow.neoecoae.util.TestComponents.bindAll();
    }

    @Test
    void allCe9CellsEnableTheSharedStorageThreadsAndParallelismOverride() {
        assertUnlimited(new Fixture().network);
    }

    @ParameterizedTest
    @EnumSource(value = ECOTier.class, names = {"L4", "L6"})
    void oneLowerTierCellInAnyHostOrDriveDisablesAllOverridesAndReplacingItRecovers(ECOTier tier) {
        Fixture fixture = new Fixture();
        for (AtomicReference<ItemStack> slot : fixture.slots) {
            slot.set(new ItemStack(CELLS.get(tier)));
            assertFinite(fixture.network);
            slot.set(new ItemStack(CELLS.get(ECOTier.L9)));
            assertUnlimited(fixture.network);
        }
    }

    @Test
    void emptyMissingAndInvalidCellsBlockTheOverrideInBothDriveRows() {
        Fixture fixture = new Fixture();
        for (int index : List.of(0, 11, 175)) {
            AtomicReference<ItemStack> slot = fixture.slots.get(index);
            for (ItemStack invalid : new ItemStack[] {null, ItemStack.EMPTY, new ItemStack(Items.STONE)}) {
                slot.set(invalid);
                assertFinite(fixture.network);
            }
            slot.set(new ItemStack(CELLS.get(ECOTier.L9)));
            assertUnlimited(fixture.network);
        }
    }

    @Test
    void eightMaxLengthC9HostsWithHighEnergySwitchesRemainRequired() {
        var valid = new NEComputationNetworkCluster.EndgameHost(true, true, true, true);
        var hosts = new ArrayList<>(java.util.Collections.nCopies(8, valid));
        assertTrue(NEComputationNetworkCluster.isEndgameTopologyEligible(hosts));
        assertFalse(NEComputationNetworkCluster.isEndgameTopologyEligible(hosts.subList(0, 7)));
        for (var invalid : List.of(
            new NEComputationNetworkCluster.EndgameHost(false, true, true, true),
            new NEComputationNetworkCluster.EndgameHost(true, false, true, true),
            new NEComputationNetworkCluster.EndgameHost(true, true, false, true))) {
            hosts.set(7, invalid);
            assertFalse(NEComputationNetworkCluster.isEndgameTopologyEligible(hosts));
        }
    }

    private static void assertUnlimited(NEComputationNetworkCluster network) {
        assertTrue(network.isEndgameEligible());
        assertTrue(network.getEndgameBlockers().isEmpty());
        assertEquals(Long.MAX_VALUE, network.getTotalStorageCapacity());
        assertEquals(Integer.MAX_VALUE, network.getTotalThreads());
        assertEquals(Integer.MAX_VALUE, network.getTotalParallelism());
        assertEquals(Integer.MAX_VALUE, network.getTotalFullParallelism());
    }

    private static void assertFinite(NEComputationNetworkCluster network) {
        assertFalse(network.isEndgameEligible());
        assertEquals(List.of("drives"), network.getEndgameBlockers());
        assertEquals(64_000L, network.getTotalStorageCapacity());
        assertEquals(640, network.getTotalThreads());
        assertEquals(1_280, network.getTotalParallelism());
        assertEquals(1_920, network.getTotalFullParallelism());
    }

    private static final class Fixture {
        final NEComputationNetworkCluster network = new NEComputationNetworkCluster();
        final List<AtomicReference<ItemStack>> slots = new ArrayList<>();

        Fixture() {
            List<NEComputationCluster> members = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                var controller = mock(ECOComputationSystemBlockEntity.class);
                when(controller.getBlockPos()).thenReturn(new BlockPos(index, 0, 0));
                when(controller.getTier()).thenReturn(ECOTier.L9);
                when(controller.getSelectedBuildLength()).thenReturn(11);
                when(controller.getMaxBuildLength()).thenReturn(11);
                when(controller.hasHighEnergyNetworkSwitch()).thenReturn(true);
                var member = mock(NEComputationCluster.class);
                when(member.getController()).thenReturn(controller);
                when(member.getNetworkMultiplier()).thenReturn(8);
                when(member.getOwnMaxThreads()).thenReturn(10);
                when(member.getCPUAccelerators()).thenReturn(20);
                when(member.getFullCPUAccelerators()).thenReturn(30);
                when(member.getTotalStorage()).thenReturn(1_000L);
                var upperDrives = drives();
                var lowerDrives = drives();
                when(member.getUpperDrives()).thenReturn(upperDrives);
                when(member.getLowerDrives()).thenReturn(lowerDrives);
                members.add(member);
            }
            network.configure(members);
        }

        private List<ECOComputationDriveBlockEntity> drives() {
            List<ECOComputationDriveBlockEntity> drives = new ArrayList<>();
            for (int index = 0; index < 11; index++) {
                var cell = new AtomicReference<>(new ItemStack(CELLS.get(ECOTier.L9)));
                slots.add(cell);
                var drive = mock(ECOComputationDriveBlockEntity.class);
                when(drive.getCellStack()).thenAnswer(invocation -> cell.get());
                drives.add(drive);
            }
            return drives;
        }
    }
}
