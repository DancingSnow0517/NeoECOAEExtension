package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.platform.mixin.CraftConfirmPlanningBridge;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CraftConfirmPlanningRouteTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void serviceWithoutEcoSettingsKeepsTheOriginalLongAmountAndFuture() {
        verifyOriginalRequest(mock(ICraftingService.class));
    }

    @Test
    void enabledPlannerWithoutEligibleHostPreservesTheAddonPlanningPath() {
        var service = mock(ICraftingService.class, withSettings().extraInterfaces(ECOCraftingNetworkSettings.class));
        when(((ECOCraftingNetworkSettings) service).neoecoae$isFastPlannerEnabled()).thenReturn(true);
        verifyOriginalRequest(service);
    }

    private static void verifyOriginalRequest(ICraftingService service) {
        var menu = new CraftConfirmMenuMixin();
        var level = mock(Level.class);
        var requester = mock(ICraftingSimulationRequester.class);
        var source = mock(IActionSource.class);
        when(requester.getActionSource()).thenReturn(source);
        var what = mock(AEKey.class);
        long amount = (long) Integer.MAX_VALUE + 123456789L;
        var strategy = CalculationStrategy.REPORT_MISSING_ITEMS;
        var future = new CompletableFuture<ICraftingPlan>();
        when(service.beginCraftingCalculation(level, requester, what, amount, strategy)).thenReturn(future);
        assertSame(future, CraftConfirmPlanningBridge.routePlanningRequest(
            service, level, requester, what, amount, strategy, menu));
        verify(service).beginCraftingCalculation(same(level), same(requester), same(what), eq(amount), same(strategy));
        assertFalse(menu.neoecoae$isEcoPlannerAvailable());
        assertFalse(menu.neoecoae$isEcoReportReady());
        assertNull(menu.neoecoae$getPlanningStatus());
    }
}
