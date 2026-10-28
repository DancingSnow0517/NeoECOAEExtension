package cn.dancingsnow.neoecoae.network;

import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.api.me.menu.ECOForceCraftStartSync;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.level.ServerPlayer;
import cn.dancingsnow.neoecoae.platform.network.NEPacketContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class CraftStartPacketTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void oneIntentAppliesForceFlagAndStartsTheCurrentValidMenuInOrder() {
        var menu = mock(CraftConfirmMenu.class, withSettings().extraInterfaces(ECOForceCraftStartSync.class));
        menu.submitError = new CraftConfirmMenu.SyncableSubmitResult(
            (appeng.api.networking.crafting.ICraftingSubmitResult) null);
        var player = mock(ServerPlayer.class);
        player.containerMenu = menu;
        when(menu.stillValid(player)).thenReturn(true);
        var context = mock(NEPacketContext.class);
        when(context.player()).thenReturn(player);
        doAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return CompletableFuture.completedFuture(null);
        }).when(context).enqueueWork(any(Runnable.class));
        ECOForceCraftStartFlagC2SPacket.handle(new ECOForceCraftStartFlagC2SPacket(menu.containerId, true), context);
        var order = inOrder(menu);
        order.verify((ECOForceCraftStartSync) menu).neoecoae$setForceCraftStart(true);
        order.verify(menu).startJob();
        clearInvocations(menu);
        ECOForceCraftStartFlagC2SPacket.handle(new ECOForceCraftStartFlagC2SPacket(menu.containerId + 1, true), context);
        verify(menu, never()).startJob();
        when(menu.stillValid(player)).thenReturn(false);
        ECOForceCraftStartFlagC2SPacket.handle(new ECOForceCraftStartFlagC2SPacket(menu.containerId, true), context);
        verify(menu, never()).startJob();
        verify(player).closeContainer();
    }

    @Test void missingForceStartIntegrationReportsAnErrorInsteadOfIgnoringTheClick() {
        var menu = mock(CraftConfirmMenu.class);
        var player = mock(ServerPlayer.class);
        player.containerMenu = menu;
        when(menu.stillValid(player)).thenReturn(true);
        var context = mock(NEPacketContext.class);
        when(context.player()).thenReturn(player);
        doAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return CompletableFuture.completedFuture(null);
        }).when(context).enqueueWork(any(Runnable.class));

        ECOForceCraftStartFlagC2SPacket.handle(new ECOForceCraftStartFlagC2SPacket(menu.containerId, false), context);

        verify(menu, never()).startJob();
        org.junit.jupiter.api.Assertions.assertEquals(
            appeng.api.networking.crafting.CraftingSubmitErrorCode.INCOMPLETE_PLAN,
            menu.submitError.result().errorCode());
    }

    @Test void queuedOrdinarySubmissionExceptionsReachTheMenuInsteadOfOnlyTheTaskFuture() {
        for (Throwable failure : new Throwable[] {
                new IllegalStateException("CPU admission failed"), new NoSuchMethodError("CPU integration mismatch")}) {
            var menu = mock(CraftConfirmMenu.class, withSettings().extraInterfaces(ECOForceCraftStartSync.class));
            menu.submitError = new CraftConfirmMenu.SyncableSubmitResult(
                (appeng.api.networking.crafting.ICraftingSubmitResult) null);
            var player = mock(ServerPlayer.class);
            player.containerMenu = menu;
            when(menu.stillValid(player)).thenReturn(true);
            doThrow(failure).when(menu).startJob();
            var task = new AtomicReference<CompletableFuture<Void>>();
            var context = queuedContext(player, task);

            ECOForceCraftStartFlagC2SPacket.handle(new ECOForceCraftStartFlagC2SPacket(menu.containerId, false), context);

            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> task.get().join());
            org.junit.jupiter.api.Assertions.assertEquals(
                appeng.api.networking.crafting.CraftingSubmitErrorCode.INCOMPLETE_PLAN,
                menu.submitError.result().errorCode());
            verify(menu).setAutoStart(false);
            verify(menu, times(1)).startJob();
        }
    }

    @Test void queuedBigOrderLinkageFailureAlsoReachesTheMenu() {
        var menu = mock(CraftConfirmMenu.class,
            withSettings().extraInterfaces(cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode.class));
        var mode = (cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode) menu;
        var player = mock(ServerPlayer.class);
        player.containerMenu = menu;
        when(player.getGameProfile()).thenReturn(new com.mojang.authlib.GameProfile(
            java.util.UUID.randomUUID(), "craft-submit-test"));
        when(menu.stillValid(player)).thenReturn(true);
        doThrow(new NoSuchMethodError("CPU integration mismatch")).when(mode).neoecoae$startBigOrder(false);
        var task = new AtomicReference<CompletableFuture<Void>>();

        ECOBigOrderStartC2SPacket.handle(new ECOBigOrderStartC2SPacket(menu.containerId, false), queuedContext(player, task));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> task.get().join());
        org.junit.jupiter.api.Assertions.assertEquals(
            appeng.api.networking.crafting.CraftingSubmitErrorCode.INCOMPLETE_PLAN,
            menu.submitError.result().errorCode());
        verify(menu).setAutoStart(false);
    }

    private static NEPacketContext queuedContext(ServerPlayer player, AtomicReference<CompletableFuture<Void>> task) {
        var context = mock(NEPacketContext.class);
        when(context.player()).thenReturn(player);
        doAnswer(call -> {
            // Minecraft's queued executor captures a thrown task exception in its returned future.
            task.set(CompletableFuture.runAsync(call.getArgument(0), Runnable::run));
            return null;
        }).when(context).enqueueWork(any(Runnable.class));
        return context;
    }
}
