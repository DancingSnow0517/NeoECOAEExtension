package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.CraftingPlanSummaryAccessor;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.CraftingPlanSummaryEntryAccessor;
import cn.dancingsnow.neoecoae.mixins.ae2.menu.CraftConfirmMenuMixin;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ECOConfirmationAvailabilityTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void simulatedEmittableMaterialStillShowsPhysicalShortage() throws Exception {
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        var source = mock(IActionSource.class);
        var key = mock(AEKey.class);
        var summary = mock(CraftingPlanSummary.class,
            withSettings().extraInterfaces(CraftingPlanSummaryAccessor.class));
        when(summary.isSimulation()).thenReturn(true);
        var entry = mock(CraftingPlanSummaryEntry.class,
            withSettings().extraInterfaces(CraftingPlanSummaryEntryAccessor.class));
        when(entry.getWhat()).thenReturn(key);
        when(entry.getStoredAmount()).thenReturn(137346430105L);
        when(summary.getEntries()).thenReturn(List.of(entry));
        when(grid.getCraftingService().canEmitFor(key)).thenReturn(true);
        when(grid.getStorageService().getInventory().extract(key, 137346430105L,
            Actionable.SIMULATE, source)).thenReturn(21864722041L);
        var method = CraftConfirmMenuMixin.class.getDeclaredMethod("neoecoae$recheckStoredAmounts",
            IGrid.class, IActionSource.class, CraftingPlanSummary.class);
        method.setAccessible(true);
        method.invoke(null, grid, source, summary);
        ArgumentCaptor<List<CraftingPlanSummaryEntry>> entries = ArgumentCaptor.forClass((Class) List.class);
        verify((CraftingPlanSummaryAccessor) summary).neoecoae$setEntries(entries.capture());
        assertSame(entry, cn.dancingsnow.neoecoae.platform.JdkCompat.first(entries.getValue()));
        verify((CraftingPlanSummaryEntryAccessor) entry).neoecoae$setStoredAmount(21864722041L);
        verify((CraftingPlanSummaryEntryAccessor) entry).neoecoae$setMissingAmount(115481708064L);
    }

    @Test
    void bothPlanningCallShapesKeepLongAmountsAndTheNativeFuture() throws Exception {
        var requester = mock(ICraftingSimulationRequester.class);
        var key = mock(AEKey.class);
        long amount = (long) Integer.MAX_VALUE + 987654321L;
        var strategy = CalculationStrategy.REPORT_MISSING_ITEMS;
        var future = new CompletableFuture<>();
        for (Class<? extends ICraftingService> serviceType : List.of(
                ICraftingService.class, appeng.me.service.CraftingService.class)) {
            var service = mock(serviceType);
            doReturn(future).when(service).beginCraftingCalculation(null, requester, key, amount, strategy);
            String handlerName = serviceType == ICraftingService.class
                ? "neoecoae$routeInterfacePlanning" : "neoecoae$routeConcretePlanning";
            var handler = CraftConfirmMenuMixin.class.getDeclaredMethod(handlerName, serviceType,
                net.minecraft.world.level.Level.class, ICraftingSimulationRequester.class, AEKey.class,
                long.class, CalculationStrategy.class);
            handler.setAccessible(true);
            var menu = new CraftConfirmMenuMixin();
            assertSame(future, handler.invoke(menu, service, null, requester, key, amount, strategy));
            verify(service).beginCraftingCalculation(null, requester, key, amount, strategy);
            assertFalse(menu.neoecoae$isEcoPlannerAvailable());
        }
    }

    @Test
    void playerRequestSkipsEcoPlannerWhenOnlyAutomationHostsAreAvailable() throws Exception {
        var source = mock(IActionSource.class);
        var requester = mock(ICraftingSimulationRequester.class);
        when(requester.getActionSource()).thenReturn(source);
        var service = mock(ICraftingService.class, withSettings().extraInterfaces(ECOCraftingNetworkSettings.class));
        var serviceSettings = (ECOCraftingNetworkSettings) service;
        when(serviceSettings.neoecoae$isFastPlannerEnabled()).thenReturn(true);
        when(serviceSettings.neoecoae$hasComputationHost()).thenReturn(true);
        when(serviceSettings.neoecoae$hasComputationHost(source)).thenReturn(false);
        var key = mock(AEKey.class);
        var strategy = CalculationStrategy.REPORT_MISSING_ITEMS;
        var fallback = mock(Future.class);
        doReturn(fallback).when(service).beginCraftingCalculation(null, requester, key, 1L, strategy);

        var menu = mock(CraftConfirmMenuMixin.class, CALLS_REAL_METHODS);
        var returned = menu.neoecoae$routeEcoPlanningRequest(service, null, requester, key, 1L, strategy);

        assertSame(fallback, returned);
        var available = CraftConfirmMenuMixin.class.getDeclaredField("neoecoae$ecoPlannerAvailable");
        available.setAccessible(true);
        assertFalse(available.getBoolean(menu));
        verify(service).beginCraftingCalculation(null, requester, key, 1L, strategy);
    }
}
