package cn.dancingsnow.neoecoae.network;

import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.api.me.menu.ECOForceCraftStartSync;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class CraftStartPacketTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void oneIntentAppliesForceFlagAndStartsTheCurrentValidMenuInOrder() {
        var menu = mock(CraftConfirmMenu.class, withSettings().extraInterfaces(ECOForceCraftStartSync.class));
        var player = mock(ServerPlayer.class);
        player.containerMenu = menu;
        when(menu.stillValid(player)).thenReturn(true);
        var context = mock(IPayloadContext.class);
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
    }
}
