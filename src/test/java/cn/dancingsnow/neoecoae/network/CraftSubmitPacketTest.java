package cn.dancingsnow.neoecoae.network;

import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.api.me.menu.ECOForceCraftStartSync;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import io.netty.buffer.Unpooled;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static cn.dancingsnow.neoecoae.network.CraftSubmitC2SPacket.SubmitMode.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraftSubmitPacketTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void standardSetsTheForceFlagBeforeStartingTheCurrentMenu() {
        var menu = menu();
        var player = player(menu);
        CraftSubmitC2SPacket.handle(new CraftSubmitC2SPacket(menu.containerId, STANDARD, true), context(player));
        var calls = inOrder(menu);
        calls.verify((ECOForceCraftStartSync) menu).neoecoae$setForceCraftStart(true);
        calls.verify(menu).startJob();
        verify((ECOCraftConfirmMenuMode) menu, never()).neoecoae$startBigOrder(anyBoolean());
    }

    @Test void bigOrderCallsItsOwnMenuCapabilityWithoutNativeStart() {
        var menu = menu();
        var player = player(menu);
        CraftSubmitC2SPacket.handle(new CraftSubmitC2SPacket(menu.containerId, BIG_ORDER, true), context(player));
        verify((ECOCraftConfirmMenuMode) menu).neoecoae$startBigOrder(true);
        verify(menu, never()).startJob();
        verify((ECOForceCraftStartSync) menu, never()).neoecoae$setForceCraftStart(anyBoolean());
    }

    @Test void mismatchedOrInvalidMenusNeverSubmitEitherMode() {
        for (var mode : CraftSubmitC2SPacket.SubmitMode.values()) {
            var menu = menu();
            var player = player(menu);
            var context = context(player);
            CraftSubmitC2SPacket.handle(new CraftSubmitC2SPacket(menu.containerId + 1, mode, true), context);
            verify(menu, never()).stillValid(any());
            when(menu.stillValid(player)).thenReturn(false);
            CraftSubmitC2SPacket.handle(new CraftSubmitC2SPacket(menu.containerId, mode, true), context);
            verify(player).closeContainer();
            verify(menu, never()).startJob();
            verify((ECOCraftConfirmMenuMode) menu, never()).neoecoae$startBigOrder(anyBoolean());
        }
    }

    @Test void missingCapabilityReportsIncompletePlanAndNonServerPlayersHaveNoEffects() {
        for (var mode : CraftSubmitC2SPacket.SubmitMode.values()) {
            var menu = mock(CraftConfirmMenu.class);
            var player = player(menu);
            CraftSubmitC2SPacket.handle(new CraftSubmitC2SPacket(menu.containerId, mode, false), context(player));
            assertEquals(CraftingSubmitErrorCode.INCOMPLETE_PLAN, menu.submitError.result().errorCode());
            verify(menu, never()).startJob();
            var client = mock(Player.class);
            client.containerMenu = menu;
            clearInvocations(menu);
            CraftSubmitC2SPacket.handle(new CraftSubmitC2SPacket(menu.containerId, mode, false), context(client));
            verifyNoInteractions(menu);
        }
    }

    @Test void explicitModeAndBothLegacyLayoutsRetainTheirOwnWireBytes() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = new CraftSubmitC2SPacket(300, BIG_ORDER, true);
            CraftSubmitC2SPacket.STREAM_CODEC.encode(buf, packet);
            assertEquals(300, buf.readVarInt());
            assertEquals(1, buf.readVarInt());
            assertTrue(buf.readBoolean());
            assertFalse(buf.isReadable());
            ECOBigOrderStartC2SPacket.STREAM_CODEC.encode(buf, new ECOBigOrderStartC2SPacket(300, true));
            assertEquals(300, buf.readVarInt());
            assertTrue(buf.readBoolean());
            assertFalse(buf.isReadable());
            ECOForceCraftStartFlagC2SPacket.STREAM_CODEC.encode(buf, new ECOForceCraftStartFlagC2SPacket(300, false));
            assertEquals(300, buf.readVarInt());
            assertFalse(buf.readBoolean());
            assertFalse(buf.isReadable());
            CraftSubmitC2SPacket.STREAM_CODEC.encode(buf, packet);
            assertEquals(packet, CraftSubmitC2SPacket.STREAM_CODEC.decode(buf));
        } finally { buf.release(); }
    }

    @Test void routingUsesBigOrdersOnlyForUnrepresentablePlansOrForcedMissingCycles() {
        assertEquals(BIG_ORDER, CraftSubmitC2SPacket.selectMode(true, false, null, false));
        assertEquals(BIG_ORDER, CraftSubmitC2SPacket.selectMode(false, true, PlanningStatus.MISSING_ITEMS, true));
        assertEquals(STANDARD, CraftSubmitC2SPacket.selectMode(false, false, PlanningStatus.MISSING_ITEMS, true));
        assertEquals(STANDARD, CraftSubmitC2SPacket.selectMode(false, true, PlanningStatus.MISSING_ITEMS, false));
        assertEquals(STANDARD, CraftSubmitC2SPacket.selectMode(false, true, null, true));
    }

    private static CraftConfirmMenu menu() {
        var menu = mock(CraftConfirmMenu.class, withSettings()
            .extraInterfaces(ECOForceCraftStartSync.class, ECOCraftConfirmMenuMode.class));
        menu.submitError = new CraftConfirmMenu.SyncableSubmitResult(
            (appeng.api.networking.crafting.ICraftingSubmitResult) null);
        return menu;
    }

    private static ServerPlayer player(CraftConfirmMenu menu) {
        var player = mock(ServerPlayer.class);
        player.containerMenu = menu;
        when(menu.stillValid(player)).thenReturn(true);
        return player;
    }

    private static IPayloadContext context(Player player) {
        var context = mock(IPayloadContext.class);
        when(context.player()).thenReturn(player);
        doAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return CompletableFuture.completedFuture(null);
        }).when(context).enqueueWork(any(Runnable.class));
        return context;
    }
}
