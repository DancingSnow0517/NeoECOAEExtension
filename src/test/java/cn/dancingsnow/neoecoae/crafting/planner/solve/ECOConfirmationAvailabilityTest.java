package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.CraftingPlanSummaryAccessor;
import cn.dancingsnow.neoecoae.mixins.ae2.menu.CraftConfirmMenuMixin;
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
}
