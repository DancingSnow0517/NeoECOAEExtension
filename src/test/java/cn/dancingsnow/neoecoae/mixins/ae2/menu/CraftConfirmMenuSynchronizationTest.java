package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import static org.junit.jupiter.api.Assertions.*;

import appeng.menu.guisync.DataSynchronization;
import appeng.menu.guisync.GuiSync;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCycleItemList;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CraftConfirmMenuSynchronizationTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void preservesEcoAndForeignAddonFieldsInFullAndIncrementalUpdates() throws Exception {
        var server = new OtherAddonFields();
        var client = new OtherAddonFields();
        setEco(server, "neoecoae$calculationNanos", 1234567890123L);
        setEco(server, "neoecoae$planningStatusCode", 7);
        setEco(server, "neoecoae$optimizationState", 1);
        setEco(server, "neoecoae$exactPlanReady", true);
        setEco(server, "neoecoae$provenInfeasible", false);
        setEco(server, "neoecoae$reportSyncFailure", 2);
        setEco(server, "neoecoae$theoreticalBytes", "9223372036854775808123");
        setEco(server, "neoecoae$hasByteEstimate", true);
        setEco(server, "neoecoae$completeByteEstimate", true);
        setEco(server, "neoecoae$diagnosticsVersion", 7L);
        server.customProgress = 987654321L;
        server.customDiagnostic = "another addon diagnostic";
        server.customHighId = "another independently assigned range";
        var serverSync = new DataSynchronization(server);
        var clientSync = new DataSynchronization(client);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serverSync.writeFull(buffer);
            clientSync.readUpdate(buffer);
            assertEquals(1234567890123L, getEco(client, "neoecoae$calculationNanos"));
            assertEquals(7, getEco(client, "neoecoae$planningStatusCode"));
            assertEquals(1, getEco(client, "neoecoae$optimizationState"));
            assertEquals(true, getEco(client, "neoecoae$exactPlanReady"));
            assertEquals(2, client.neoecoae$getReportSyncFailure());
            assertEquals(new java.math.BigInteger("9223372036854775808123"), client.neoecoae$getTheoreticalBytes());
            assertTrue(client.neoecoae$hasByteEstimate());
            assertTrue(client.neoecoae$hasCompleteByteEstimate());
            assertEquals(7L, getEco(client, "neoecoae$diagnosticsVersion"));
            assertSame(CraftingGraphSnapshot.EMPTY, client.neoecoae$craftingGraph,
                "Byte metadata must not wait for material pages");
            assertEquals(server.customProgress, client.customProgress);
            assertEquals(server.customDiagnostic, client.customDiagnostic);
            assertEquals(server.customHighId, client.customHighId);

            buffer.clear();
            setEco(server, "neoecoae$planningStatusCode", 9);
            setEco(server, "neoecoae$optimizationState", 2);
            setEco(server, "neoecoae$theoreticalBytes", "0");
            setEco(server, "neoecoae$hasByteEstimate", false);
            setEco(server, "neoecoae$completeByteEstimate", false);
            server.customDiagnostic = "updated addon diagnostic";
            serverSync.writeUpdate(buffer);
            clientSync.readUpdate(buffer);
            assertEquals(9, getEco(client, "neoecoae$planningStatusCode"));
            assertEquals(2, getEco(client, "neoecoae$optimizationState"));
            assertEquals(java.math.BigInteger.ZERO, client.neoecoae$getTheoreticalBytes());
            assertFalse(client.neoecoae$hasByteEstimate());
            assertFalse(client.neoecoae$hasCompleteByteEstimate());
            assertEquals(1234567890123L, getEco(client, "neoecoae$calculationNanos"));
            assertEquals(server.customDiagnostic, client.customDiagnostic);
            assertEquals(987654321L, client.customProgress);
            assertEquals(server.customHighId, client.customHighId);
        } finally {
            buffer.release();
        }
    }

    @Test
    void retainsAe2RejectionOfUnrelatedDuplicateFields() {
        var error = assertThrows(IllegalStateException.class,
            () -> new DataSynchronization(new ConflictingAddonFields()));
        assertTrue(error.getMessage().contains("same sync id twice: 100"));
    }

    @Test void materialRowsPublishThePlanRevisionBeforeGraphAndRejectStaleGraphCompletions() throws Exception {
        var client = new OtherAddonFields();
        var full = CraftingGraphSnapshot.EMPTY;
        var firstReport = full.materialReport();
        client.neoecoae$setMaterialReport(1, firstReport, ECOCycleItemList.EMPTY);
        assertSame(firstReport, client.neoecoae$craftingGraph);
        assertEquals(1L, client.neoecoae$getDisplayedPlanRevision());
        assertEquals(-1L, getEco(client, "neoecoae$receivedGraphVersion"));

        var nextReport = full.materialReport();
        client.neoecoae$setMaterialReport(2, nextReport, ECOCycleItemList.EMPTY);
        client.neoecoae$setDiagnostics(1, full, ECOCycleItemList.EMPTY);
        assertSame(nextReport, client.neoecoae$craftingGraph);
        assertEquals(2L, client.neoecoae$getDisplayedPlanRevision());
        assertEquals(-1L, getEco(client, "neoecoae$receivedGraphVersion"));

        client.neoecoae$setDiagnostics(2, full, ECOCycleItemList.EMPTY);
        assertEquals(2L, getEco(client, "neoecoae$receivedGraphVersion"));
        client.neoecoae$setMaterialReport(2, nextReport, ECOCycleItemList.EMPTY);
        assertSame(full, client.neoecoae$craftingGraph, "A late table packet cannot replace a full graph");
    }

    @Test void reportEncodingFailureIsContainedAndLeavesNoPendingStream() throws Exception {
        var server = new OtherAddonFields();
        var player = org.mockito.Mockito.mock(net.minecraft.server.level.ServerPlayer.class);
        player.containerMenu = org.mockito.Mockito.mock(net.minecraft.world.inventory.AbstractContainerMenu.class);
        var invalid = new CraftingGraphSnapshot(-1, java.util.List.of(), java.util.List.of(), java.util.List.of(),
            java.util.List.of(), java.util.List.of(),
            new CraftingGraphSnapshot.Summary("x".repeat(32768), 0, 0, 0, 0, 0));
        var send = CraftConfirmMenuMixin.class.getDeclaredMethod("neoecoae$sendReport",
            net.minecraft.server.level.ServerPlayer.class, CraftingGraphSnapshot.class, boolean.class);
        send.setAccessible(true);
        for (boolean fullGraph : java.util.List.of(false, true)) {
            assertEquals(false, send.invoke(server, player, invalid, fullGraph));
            assertEquals(fullGraph ? 2 : 1, server.neoecoae$getReportSyncFailure());
            assertFalse(cn.dancingsnow.neoecoae.network.MenuDataTransport.busy(player,
                cn.dancingsnow.neoecoae.network.MenuDataTransport.Channel.GRAPH));
        }
    }

    private static void setEco(Object menu, String name, Object value) throws Exception {
        var field = CraftConfirmMenuMixin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(menu, value);
    }

    private static Object getEco(Object menu, String name) throws Exception {
        var field = CraftConfirmMenuMixin.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(menu);
    }

    /** Simulates an independent addon occupying the whole legacy low-ID range and another range. */
    public static class OtherAddonFields extends CraftConfirmMenuMixin {
        @GuiSync(99) public boolean customHeader;
        @GuiSync(100) public long customProgress;
        @GuiSync(101) public String customDiagnostic = "";
        @GuiSync(102) public int customMode;
        @GuiSync(103) public int customState;
        @GuiSync(104) public boolean customOption;
        @GuiSync(105) public String customName = "";
        @GuiSync(106) public boolean customAvailable;
        @GuiSync(107) public boolean customReady;
        @GuiSync(108) public long customCount;
        @GuiSync(109) public String customMessage = "";
        @GuiSync(110) public long customVersion;
        @GuiSync(111) public int customTier;
        @GuiSync(112) public boolean customSelected;
        @GuiSync(23456) public String customHighId = "";
    }

    public static class ConflictingAddonFields extends OtherAddonFields {
        @GuiSync(100) public boolean duplicate;
    }
}
