package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingSubmitResult;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.result.*;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.Ae2CraftingCpuFastPathMixin;
import cn.dancingsnow.neoecoae.mixins.compat.advancedae.crafting.AdvancedAeCraftingCpuLogicMixin;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

class ECOExternalCpuSupportTest {
    @BeforeAll static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
    }
    @AfterEach void clearRegistry() { ECOPlanningResultRegistry.clear(); }

    CraftingPlan plan() {
        return new CraftingPlan(new GenericStack(AEItemKey.of(Items.STONE), 512), 128,
                false, false, new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
    }

    @Test void nativeAndRegisteredNativeEcoPlansAreAcceptedUnchanged() {
        var plan = plan();
        assertTrue(ECOExternalCpuSupport.supportsPlan(plan));
        var result = new ECOPlanningResult(PlanningStatus.SUCCESS, plan, mock(ECOPlanTrace.class),
                List.of(), List.of(), List.of(), 0, UUID.randomUUID(), null);
        ECOPlanningResultRegistry.register(plan, result);
        assertTrue(ECOPlanningResultRegistry.isECOOwnedPlan(plan));
        assertTrue(ECOExternalCpuSupport.supportsPlan(plan));
        assertTrue(ECOExternalCpuSupport.accepts(mock(appeng.me.cluster.implementations.CraftingCPUCluster.class), plan));
        assertTrue(ECOExternalCpuSupport.accepts(mock(net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU.class), plan));
        assertFalse(ECOExternalCpuSupport.accepts(mock(ICraftingCPU.class), plan));
    }

    @ParameterizedTest @EnumSource(value = ExecutionMode.class, names = {"PHASED_DAG", "ORDERED_CYCLE", "DYNAMIC_CYCLE", "BLOCKED"})
    void ecoRuntimePlansCannotReachEitherExternalCpu(ExecutionMode mode) throws Exception {
        var plan = plan();
        var result = mock(ECOPlanningResult.class);
        var signature = cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity.of(plan);
        var pattern = mock(appeng.api.crafting.IPatternDetails.class);
        when(pattern.getDefinition()).thenReturn(AEItemKey.of(Items.PAPER));
        var task = new ECOExecutionPlan.TaskSpec(0,
                cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity.patternIdentityFor(pattern),
                pattern, new ECOExecutionPlan.PatternRuntimeInfo(null, 0, List.of()), 1, 0,
                ECOExecutionPlan.TaskKind.DAG);
        var phaseType = switch (mode) {
            case ORDERED_CYCLE -> ECOExecutionSchedule.Type.CYCLE;
            case DYNAMIC_CYCLE -> ECOExecutionSchedule.Type.DYNAMIC_CYCLE;
            default -> ECOExecutionSchedule.Type.DAG;
        };
        var phase = new ECOExecutionPlan.PhaseSpec(0, 0, phaseType, List.of(0),
                mode == ExecutionMode.ORDERED_CYCLE ? List.of(new ECOExecutionPlan.ExecutionStep(0, 1)) : List.of(),
                List.of(), mode == ExecutionMode.DYNAMIC_CYCLE ? Map.of(0, 1L) : Map.of(), Map.of());
        var execution = mode == ExecutionMode.BLOCKED ? null : new ECOExecutionPlan(signature, mode,
                List.of(task), List.of(phase), new ECOExecutionSchedule(List.of(), List.of()));
        var contract = new ECOExecutionContract(UUID.randomUUID(), signature, mode, execution,
                mode == ExecutionMode.BLOCKED ? "unsafe plan" : null);
        try (var registry = mockStatic(ECOPlanningResultRegistry.class)) {
            registry.when(() -> ECOPlanningResultRegistry.isECOOwnedPlan(plan)).thenReturn(true);
            registry.when(() -> ECOPlanningResultRegistry.find(plan)).thenReturn(result);
            registry.when(() -> ECOPlanningResultRegistry.resolveContract(plan, null)).thenReturn(contract);
            assertFalse(ECOExternalCpuSupport.supportsPlan(plan));
            assertRejectedByCpuHooks(plan);
        }
    }

    @Test void lostMetadataDoesNotSilentlyStripEcoRuntime() {
        var plan = plan();
        try (var registry = mockStatic(ECOPlanningResultRegistry.class)) {
            registry.when(() -> ECOPlanningResultRegistry.isECOOwnedPlan(plan)).thenReturn(true);
            assertFalse(ECOExternalCpuSupport.supportsPlan(plan));
        }
    }

    @Test void exactOrderIsRejectedEvenWithoutRegistryEntry() throws Exception {
        var exact = new ECOExactCraftingPlan(plan(), BigInteger.ONE.shiftLeft(64));
        assertFalse(ECOExternalCpuSupport.supportsPlan(exact));
        assertRejectedByCpuHooks(exact);
    }

    @Test void longProjectionCannotHideExactMaterialOverflow() {
        var plan = plan();
        var result = mock(ECOPlanningResult.class);
        when(result.exactUsedItems()).thenReturn(Map.of(AEItemKey.of(Items.STONE),
                PlannerAmount.of(BigInteger.ONE.shiftLeft(64))));
        var contract = ECOExecutionContract.nativeContract(UUID.randomUUID(),
                cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity.of(plan));
        try (var registry = mockStatic(ECOPlanningResultRegistry.class)) {
            registry.when(() -> ECOPlanningResultRegistry.isECOOwnedPlan(plan)).thenReturn(true);
            registry.when(() -> ECOPlanningResultRegistry.find(plan)).thenReturn(result);
            registry.when(() -> ECOPlanningResultRegistry.resolveContract(plan, null)).thenReturn(contract);
            assertFalse(ECOExternalCpuSupport.supportsPlan(plan));
        }
    }

    void assertRejectedByCpuHooks(ICraftingPlan plan) throws Exception {
        for (var type : List.of(Ae2CraftingCpuFastPathMixin.class, AdvancedAeCraftingCpuLogicMixin.class)) {
            var hook = type.getDeclaredMethod("neoecoae$validatePlan", IGrid.class, ICraftingPlan.class,
                    IActionSource.class, ICraftingRequester.class, CallbackInfoReturnable.class);
            hook.setAccessible(true);
            var callback = new CallbackInfoReturnable<ICraftingSubmitResult>("trySubmitJob", true);
            hook.invoke(mock(type, CALLS_REAL_METHODS), null, plan, null, null, callback);
            assertTrue(callback.isCancelled());
            assertSame(CraftingSubmitResult.NO_CPU_FOUND, callback.getReturnValue());
        }
    }
}
