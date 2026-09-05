package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.*;

import appeng.menu.guisync.DataSynchronization;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import cn.dancingsnow.neoecoae.mixins.CraftConfirmMenuMixin;
import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class ECOCraftConfirmSyncTest {
    @Test
    void actualMenuFieldsRoundTripAndResetPreservingAe2FrameTrailer() throws Exception {
        var server = new CraftConfirmMenuMixin();
        var client = new CraftConfirmMenuMixin();
        var writer = new DataSynchronization(server);
        var reader = new DataSynchronization(client);
        set(server, "showFastPlannerReport", true);
        set(server, "cyclePlanningEnabled", true);
        set(server, "calculationNanos", 987654321L);
        set(server, "theoreticalBytes", BigInteger.TEN.pow(50).toString());
        set(server, "planningStatusCode", PlanningStatus.MISSING_ITEMS.ordinal() + 1);
        var snapshot = new CraftingGraphSnapshot(
                -1,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new CraftingGraphSnapshot.Summary("MISSING_ITEMS", 0, 0, 0, 0, 987654321L));
        server.neoecoae$craftingGraph = snapshot;
        var data = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.writeFull(data);
            reader.readUpdate(data);
            assertAe2FrameTrailer(data);
            assertTrue(client.neoecoae$shouldShowFastPlannerReport());
            assertTrue(client.neoecoae$isCyclePlanningEnabled());
            assertEquals(987654321L, client.neoecoae$getCalculationNanos());
            assertEquals(BigInteger.TEN.pow(50), client.neoecoae$getTheoreticalBytes());
            assertEquals(PlanningStatus.MISSING_ITEMS, client.neoecoae$getPlanningStatus());
            assertEquals(snapshot, client.neoecoae$getCraftingGraphSnapshot());
            assertEquals(List.of(), client.neoecoae$getCycleItems());

            data.clear();
            set(server, "planningStatusCode", 0);
            set(server, "theoreticalBytes", "0");
            server.neoecoae$craftingGraph = CraftingGraphSnapshot.EMPTY;
            writer.writeUpdate(data);
            reader.readUpdate(data);
            assertAe2FrameTrailer(data);
            assertNull(client.neoecoae$getPlanningStatus());
            assertEquals(BigInteger.ZERO, client.neoecoae$getTheoreticalBytes());
            assertEquals(CraftingGraphSnapshot.EMPTY, client.neoecoae$getCraftingGraphSnapshot());
        } finally {
            data.release();
        }
    }

    @Test
    void invalidStatusAndByteTextDoNotCrashTheClient() throws Exception {
        var client = new CraftConfirmMenuMixin();
        set(client, "planningStatusCode", Integer.MAX_VALUE);
        set(client, "theoreticalBytes", "invalid");
        assertNull(client.neoecoae$getPlanningStatus());
        assertEquals(BigInteger.ZERO, client.neoecoae$getTheoreticalBytes());
    }

    private static void set(CraftConfirmMenuMixin menu, String name, Object value) throws Exception {
        var field = CraftConfirmMenuMixin.class.getDeclaredField("neoecoae$" + name);
        field.setAccessible(true);
        field.set(menu, value);
    }

    private static void assertAe2FrameTrailer(FriendlyByteBuf data) {
        // AE2 15.4.10 writes VarInt(-1) but reads a short terminator, leaving these three native bytes.
        byte[] trailer = new byte[data.readableBytes()];
        data.readBytes(trailer);
        assertArrayEquals(new byte[] {(byte) 0xff, (byte) 0xff, 0x0f}, trailer);
    }
}
