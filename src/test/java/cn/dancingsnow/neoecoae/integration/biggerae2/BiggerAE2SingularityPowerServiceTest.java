package cn.dancingsnow.neoecoae.integration.biggerae2;

import appeng.api.networking.IGridNode;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BiggerAE2SingularityPowerServiceTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void requestedPerCellTiersApplyToEveryMatrixAndCapAtSix() {
        double[] perCell = {0, 1_024, 4_096, 16_384, 65_535, 262_144, 1_048_576, 1_048_576};
        double[] total = {0, 1_024, 8_192, 49_152, 262_140, 1_310_720, 6_291_456, 7_340_032};
        for (int count = 0; count < perCell.length; count++) {
            assertEquals(perCell[count], BiggerAE2SingularityPowerService.idleDrainPerCell(count));
            assertEquals(total[count], BiggerAE2SingularityPowerService.totalIdleDrain(count));
        }
        double previous = 0;
        for (int count = 1; count <= 1_000; count++) {
            double drain = BiggerAE2SingularityPowerService.totalIdleDrain(count);
            assertTrue(Double.isFinite(drain));
            assertTrue(drain >= previous);
            if (count >= 6) assertEquals(1_048_576d, BiggerAE2SingularityPowerService.idleDrainPerCell(count));
            previous = drain;
        }
        assertTrue(Double.isFinite(BiggerAE2SingularityPowerService.totalIdleDrain(Integer.MAX_VALUE)));
    }

    @Test
    void emptyMixedChannelMatricesAcrossHostsShareOneBillWithoutDoubleChargingBasePower() {
        var service = new BiggerAE2SingularityPowerService();
        var first = host(9);
        var second = host(9);
        install(service, first, true);
        install(service, second, true);
        install(service, second, true);
        service.refresh();

        assertEquals(3, service.getCellCount());
        assertEquals(49_152d, 3 * BiggerAE2SingularityPowerService.BASE_IDLE_DRAIN
            + service.getAdditionalIdleDrain());
        assertEquals(service.getAdditionalIdleDrain(), service.getAdditionalIdleDrain(first)
            + service.getAdditionalIdleDrain(second), 0.000001);
        assertEquals(2 * service.getAdditionalIdleDrain(first), service.getAdditionalIdleDrain(second), 0.000001);
    }

    @Test
    void lossOfPowerDoesNotClearDemandAndRemovingMatricesRestoresTheGridBill() {
        var service = new BiggerAE2SingularityPowerService();
        var host = host(9);
        List<Installation> cells = new ArrayList<>();
        for (int i = 0; i < 6; i++) cells.add(install(service, host, true));
        service.onServerStartTick();
        assertEquals(6_291_456d - 6 * 1_024, service.getAdditionalIdleDrain());

        for (var cell : cells) when(cell.drive().isOnline()).thenReturn(false);
        service.refresh();
        assertEquals(6, service.getCellCount());
        assertEquals(6_291_456d - 6 * 1_024, service.getAdditionalIdleDrain());

        for (int i = 1; i < 6; i++) when(cells.get(i).drive().getCellStack()).thenReturn(ItemStack.EMPTY);
        service.refresh();
        assertEquals(1, service.getCellCount());
        assertEquals(0, service.getAdditionalIdleDrain());
        when(cells.getFirst().drive().getCellStack()).thenReturn(null);
        service.refresh();
        assertEquals(0, service.getCellCount());
        assertEquals(0, service.getAdditionalIdleDrain(host));
    }

    @Test
    void addingASeventhMatrixOnlyAddsOneCappedRateAndRemovingBackToFiveLowersEveryRate() {
        var service = new BiggerAE2SingularityPowerService();
        var host = host(9);
        var cells = new ArrayList<Installation>();
        for (int i = 0; i < 6; i++) cells.add(install(service, host, true));
        service.refresh();
        double sixTotal = service.getAdditionalIdleDrain() + 6 * BiggerAE2SingularityPowerService.BASE_IDLE_DRAIN;
        cells.add(install(service, host, true));
        service.refresh();
        double sevenTotal = service.getAdditionalIdleDrain() + 7 * BiggerAE2SingularityPowerService.BASE_IDLE_DRAIN;
        assertEquals(1_048_576d, sevenTotal - sixTotal);
        service.removeNode(cells.get(6).node());
        service.removeNode(cells.get(5).node());
        service.refresh();
        assertEquals(5, service.getCellCount());
        assertEquals(1_310_720d, service.getAdditionalIdleDrain()
            + 5 * BiggerAE2SingularityPowerService.BASE_IDLE_DRAIN);
    }

    @Test
    void ordinaryQuantumUnsupportedAndTransferModeCellsDoNotContribute() {
        var service = new BiggerAE2SingularityPowerService();
        install(service, host(9), false);
        install(service, host(6), true);
        var transferHost = host(9);
        when(transferHost.isStorageInterfaceTransferMode()).thenReturn(true);
        install(service, transferHost, true);
        var unformed = install(service, host(9), true);
        when(unformed.drive().getStorageController()).thenReturn(null);
        var unrelated = mock(IGridNode.class);
        when(unrelated.getOwner()).thenReturn(new Object());
        service.addNode(unrelated, null);
        service.refresh();
        assertEquals(0, service.getCellCount());
        assertEquals(0, service.getAdditionalIdleDrain());
    }

    @Test
    void multipleNodesForOnePhysicalDriveAreCountedOnce() {
        var service = new BiggerAE2SingularityPowerService();
        var installed = install(service, host(9), true);
        var otherNode = mock(IGridNode.class);
        when(otherNode.getOwner()).thenReturn(installed.drive());
        service.addNode(otherNode, null);
        service.addNode(otherNode, null);
        service.refresh();
        assertEquals(1, service.getCellCount());
        service.removeNode(installed.node());
        service.refresh();
        assertEquals(1, service.getCellCount());
        service.removeNode(otherNode);
        service.refresh();
        assertEquals(0, service.getCellCount());
    }

    @Test
    void splittingMergingAndChunkUnloadingRecomputeIndependentCounts() {
        var first = new BiggerAE2SingularityPowerService();
        var second = new BiggerAE2SingularityPowerService();
        var host = host(9);
        var cells = new ArrayList<Installation>();
        for (int i = 0; i < 6; i++) cells.add(install(first, host, true));
        first.refresh();
        assertEquals(6, first.getCellCount());
        for (int i = 3; i < 6; i++) {
            first.removeNode(cells.get(i).node());
            second.addNode(cells.get(i).node(), null);
        }
        first.refresh();
        second.refresh();
        assertEquals(3, first.getCellCount());
        assertEquals(first.getAdditionalIdleDrain(), second.getAdditionalIdleDrain());
        for (int i = 3; i < 6; i++) {
            second.removeNode(cells.get(i).node());
            first.addNode(cells.get(i).node(), null);
        }
        first.refresh();
        second.refresh();
        assertEquals(6, first.getCellCount());
        assertEquals(0, second.getCellCount());
        for (var cell : cells) first.removeNode(cell.node());
        first.refresh();
        assertEquals(0, first.getAdditionalIdleDrain());
    }

    private static ECOStorageSystemBlockEntity host(int tier) {
        var host = mock(ECOStorageSystemBlockEntity.class);
        var hostTier = tier(tier);
        when(host.getTier()).thenReturn(hostTier);
        return host;
    }

    private static IECOTier tier(int number) {
        var tier = mock(IECOTier.class, CALLS_REAL_METHODS);
        when(tier.getTier()).thenReturn(number);
        return tier;
    }

    private static Installation install(BiggerAE2SingularityPowerService service,
            ECOStorageSystemBlockEntity host, boolean singularity) {
        var item = mock(BiggerAE2StorageCellItem.class);
        when(item.isSingularity()).thenReturn(singularity);
        var cellTier = tier(9);
        when(item.getTier()).thenReturn(cellTier);
        var stack = mock(ItemStack.class);
        when(stack.getItem()).thenReturn(item);
        var drive = mock(ECODriveBlockEntity.class);
        when(drive.getCellStack()).thenReturn(stack);
        when(drive.getStorageController()).thenReturn(host);
        var node = mock(IGridNode.class);
        when(node.getOwner()).thenReturn(drive);
        service.addNode(node, null);
        return new Installation(node, drive);
    }

    private record Installation(IGridNode node, ECODriveBlockEntity drive) {}
}
