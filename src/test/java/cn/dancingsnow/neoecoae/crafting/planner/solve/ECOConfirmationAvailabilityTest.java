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
import cn.dancingsnow.neoecoae.platform.mixin.CraftConfirmPlanningBridge;
import cn.dancingsnow.neoecoae.platform.mixin.CraftingMenuPlanningMixinPlugin;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
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
        assertEquals(21864722041L, cn.dancingsnow.neoecoae.platform.JdkCompat.first(entries.getValue()).getStoredAmount());
        assertEquals(115481708064L, cn.dancingsnow.neoecoae.platform.JdkCompat.first(entries.getValue()).getMissingAmount());
    }

    @Test
    void confirmMenuInterceptsModdedLongPlanningEntryPoints() throws Exception {
        String hostName = "cn/dancingsnow/neoecoae/verification/UnknownPlanningEntry";
        String bridge = CraftConfirmPlanningBridge.class.getName().replace('.', '/');
        String calculation = "(Lnet/minecraft/world/level/Level;"
            + "Lappeng/api/networking/crafting/ICraftingSimulationRequester;Lappeng/api/stacks/AEKey;"
            + "JLappeng/api/networking/crafting/CalculationStrategy;)Ljava/util/concurrent/Future;";
        String entryDescriptor = "(Lappeng/api/networking/crafting/ICraftingService;" + calculation.substring(1);
        var original = new ClassWriter(0);
        original.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, hostName, null, "java/lang/Object", new String[] {bridge});
        original.visitField(Opcodes.ACC_PUBLIC, "routeCalls", "I", null, null).visitEnd();
        var constructor = original.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        for (String methodName : List.of("independentAddonLongEntry", "neoecoae$routeEcoPlanningRequest")) {
            var method = original.visitMethod(Opcodes.ACC_PUBLIC, methodName, entryDescriptor, null, null);
            method.visitCode();
            if (methodName.equals("neoecoae$routeEcoPlanningRequest")) {
                method.visitVarInsn(Opcodes.ALOAD, 0);
                method.visitInsn(Opcodes.DUP);
                method.visitFieldInsn(Opcodes.GETFIELD, hostName, "routeCalls", "I");
                method.visitInsn(Opcodes.ICONST_1);
                method.visitInsn(Opcodes.IADD);
                method.visitFieldInsn(Opcodes.PUTFIELD, hostName, "routeCalls", "I");
            }
            for (int local = 1; local <= 4; local++) method.visitVarInsn(Opcodes.ALOAD, local);
            method.visitVarInsn(Opcodes.LLOAD, 5);
            method.visitVarInsn(Opcodes.ALOAD, 7);
            method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "appeng/api/networking/crafting/ICraftingService",
                "beginCraftingCalculation", calculation, true);
            method.visitInsn(Opcodes.ARETURN);
            method.visitMaxs(7, 8);
            method.visitEnd();
        }
        original.visitEnd();
        var node = new ClassNode();
        new ClassReader(original.toByteArray()).accept(node, 0);
        var plugin = new CraftingMenuPlanningMixinPlugin();
        plugin.postApply("appeng.menu.me.crafting.CraftConfirmMenu", node,
            CraftConfirmMenuMixin.class.getName(), null);
        // A second visit must not wrap the bridge's own fallback or recurse into the route.
        plugin.postApply("appeng.menu.me.crafting.CraftConfirmMenu", node,
            CraftConfirmMenuMixin.class.getName(), null);
        var transformed = new ClassWriter(0);
        node.accept(transformed);
        class HostLoader extends ClassLoader {
            HostLoader() { super(ECOConfirmationAvailabilityTest.class.getClassLoader()); }
            Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
        }
        var host = new HostLoader().define(transformed.toByteArray());
        var service = mock(ICraftingService.class);
        var requester = mock(ICraftingSimulationRequester.class);
        var key = mock(AEKey.class);
        long amount = (long) Integer.MAX_VALUE + 987654321L;
        var strategy = CalculationStrategy.REPORT_MISSING_ITEMS;
        var future = new CompletableFuture<>();
        doReturn(future).when(service).beginCraftingCalculation(null, requester, key, amount, strategy);
        var entry = host.getMethod("independentAddonLongEntry", ICraftingService.class,
            net.minecraft.world.level.Level.class, ICraftingSimulationRequester.class, AEKey.class,
            long.class, CalculationStrategy.class);
        var menu = host.getConstructor().newInstance();
        assertSame(future, entry.invoke(menu,
            service, null, requester, key, amount, strategy));
        assertEquals(1, host.getField("routeCalls").getInt(menu));
        verify(service, times(1)).beginCraftingCalculation(null, requester, key, amount, strategy);
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
