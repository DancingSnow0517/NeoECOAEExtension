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
    void nativePlanningResetIsRequiredAndOptionalEntriesRemainOptional() throws Exception {
        var nativeMethod = CraftConfirmMenuMixin.class.getDeclaredMethod("neoecoae$resetNativePlannerDiagnostics",
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable.class);
        var nativeHook = nativeMethod.getAnnotation(org.spongepowered.asm.mixin.injection.Inject.class);
        assertArrayEquals(new String[]{"planJob"}, nativeHook.method());
        assertEquals(1, nativeHook.require());
        assertEquals("HEAD", nativeHook.at()[0].value());
        assertTrue(java.util.Arrays.stream(appeng.menu.me.crafting.CraftConfirmMenu.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("planJob") && method.getReturnType() == boolean.class));
        var optional = CraftConfirmMenuMixin.class.getDeclaredMethod("neoecoae$resetExtendedPlannerDiagnostics",
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable.class)
            .getAnnotation(org.spongepowered.asm.mixin.injection.Inject.class);
        assertEquals(0, optional.require());
        assertFalse(java.util.Arrays.asList(optional.method()).contains("planJob"));

        var menu = new CraftConfirmMenuMixin();
        var diagnostic = CraftConfirmMenuMixin.class.getDeclaredField("neoecoae$planningDiagnostic");
        diagnostic.setAccessible(true);
        diagnostic.set(menu, "previous attempt");
        for (String name : java.util.List.of("neoecoae$hasByteEstimate", "neoecoae$completeByteEstimate")) {
            var field = CraftConfirmMenuMixin.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setBoolean(menu, true);
        }
        nativeMethod.setAccessible(true);
        nativeMethod.invoke(menu, new org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean>(
            "planJob", true));
        assertEquals("", menu.neoecoae$getPlanningDiagnostic());
        assertNull(menu.neoecoae$getPlanningStatus());
        assertFalse(menu.neoecoae$hasByteEstimate());
        assertFalse(menu.neoecoae$hasCompleteByteEstimate());
    }

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
