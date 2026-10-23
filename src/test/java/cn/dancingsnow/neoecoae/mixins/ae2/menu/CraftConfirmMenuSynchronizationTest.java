package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import static org.junit.jupiter.api.Assertions.*;

import appeng.menu.guisync.DataSynchronization;
import appeng.menu.guisync.GuiSync;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
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
            assertEquals(server.customProgress, client.customProgress);
            assertEquals(server.customDiagnostic, client.customDiagnostic);
            assertEquals(server.customHighId, client.customHighId);

            buffer.clear();
            setEco(server, "neoecoae$planningStatusCode", 9);
            server.customDiagnostic = "updated addon diagnostic";
            serverSync.writeUpdate(buffer);
            clientSync.readUpdate(buffer);
            assertEquals(9, getEco(client, "neoecoae$planningStatusCode"));
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
