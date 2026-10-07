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
import cn.dancingsnow.neoecoae.mixins.ae2.menu.CraftConfirmMenuMixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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
        when(summary.getEntries()).thenReturn(List.of(
            new CraftingPlanSummaryEntry(key, 0, 137346430105L, 0)));
        when(grid.getCraftingService().canEmitFor(key)).thenReturn(true);
        when(grid.getStorageService().getInventory().extract(key, 137346430105L,
            Actionable.SIMULATE, source)).thenReturn(21864722041L);
        var method = CraftConfirmMenuMixin.class.getDeclaredMethod("neoecoae$recheckStoredAmounts",
            IGrid.class, IActionSource.class, CraftingPlanSummary.class);
        method.setAccessible(true);
        method.invoke(null, grid, source, summary);
        ArgumentCaptor<List<CraftingPlanSummaryEntry>> entries = ArgumentCaptor.forClass((Class) List.class);
        verify((CraftingPlanSummaryAccessor) summary).neoecoae$setEntries(entries.capture());
        assertEquals(21864722041L, entries.getValue().getFirst().getStoredAmount());
        assertEquals(115481708064L, entries.getValue().getFirst().getMissingAmount());
    }

    @Test
    void confirmMenuInterceptsModdedLongPlanningEntryPoints() throws Exception {
        var resetMethod = CraftConfirmMenuMixin.class.getDeclaredMethod(
            "resetPlannerDiagnostics",
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable.class
        );
        var inject = resetMethod.getAnnotation(org.spongepowered.asm.mixin.injection.Inject.class);
        assertNotNull(inject);
        List<String> injectTargets = List.of(inject.method());
        assertTrue(injectTargets.contains("molecularmanipulator$planLong"));
        assertTrue(injectTargets.contains("appliedenhancements$planLong"));
        assertTrue(injectTargets.contains("appliedenhancements$planRequested"));

        var routeMethod = CraftConfirmMenuMixin.class.getDeclaredMethod(
            "neoecoae$routeEcoPlanningRequest",
            appeng.api.networking.crafting.ICraftingService.class,
            net.minecraft.world.level.Level.class,
            appeng.api.networking.crafting.ICraftingSimulationRequester.class,
            AEKey.class,
            long.class,
            appeng.api.networking.crafting.CalculationStrategy.class,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation.class
        );
        var wrapOp = routeMethod.getAnnotation(com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation.class);
        assertNotNull(wrapOp);
        List<String> wrapTargets = List.of(wrapOp.method());
        assertTrue(wrapTargets.contains("molecularmanipulator$planLong"));
        assertTrue(wrapTargets.contains("appliedenhancements$planRequested"));
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
        var original = mock(Operation.class);
        when(original.call(any(), any(), any(), any(), any(), any())).thenReturn(fallback);

        var menu = mock(CraftConfirmMenuMixin.class, CALLS_REAL_METHODS);
        var routeMethod = CraftConfirmMenuMixin.class.getDeclaredMethod(
            "neoecoae$routeEcoPlanningRequest", ICraftingService.class, net.minecraft.world.level.Level.class,
            ICraftingSimulationRequester.class, AEKey.class, long.class, CalculationStrategy.class, Operation.class);
        routeMethod.setAccessible(true);
        var returned = routeMethod.invoke(menu, service, null, requester, key, 1L, strategy, original);

        assertSame(fallback, returned);
        var available = CraftConfirmMenuMixin.class.getDeclaredField("neoecoae$ecoPlannerAvailable");
        available.setAccessible(true);
        assertFalse(available.getBoolean(menu));
        verify(original).call(service, null, requester, key, 1L, strategy);
    }
}
