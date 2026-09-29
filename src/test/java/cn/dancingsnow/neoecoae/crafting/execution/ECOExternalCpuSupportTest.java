package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionContract;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ECOExternalCpuSupportTest {
    @Test
    void nativePlansCanUseAe2CpuButUnknownCpuCannotClaimFastPath() {
        ICraftingPlan plan = mock(ICraftingPlan.class);
        try (var registry = mockStatic(ECOPlanningResultRegistry.class)) {
            assertTrue(ECOExternalCpuSupport.supportsPlan(plan));
            assertTrue(ECOExternalCpuSupport.accepts(mock(CraftingCPUCluster.class), plan));
            assertFalse(ECOExternalCpuSupport.accepts(mock(appeng.api.networking.crafting.ICraftingCPU.class), plan));
        }
    }

    @Test
    void ecoOwnedPlanWithoutProvableNativeContractFailsClosed() {
        ICraftingPlan plan = mock(ICraftingPlan.class);
        try (var registry = mockStatic(ECOPlanningResultRegistry.class)) {
            registry.when(() -> ECOPlanningResultRegistry.isECOOwnedPlan(plan)).thenReturn(true);
            assertFalse(ECOExternalCpuSupport.supportsPlan(plan));
        }
    }

    @Test
    void ecoOwnedNativeContractAcceptsOnlyLongRepresentableMaterials() {
        ICraftingPlan plan = mock(ICraftingPlan.class);
        ECOPlanningResult result = mock(ECOPlanningResult.class);
        ECOExecutionContract contract = ECOExecutionContract.nativeContract(UUID.randomUUID(), signature());
        when(result.exactPatternTimes()).thenReturn(Map.of());
        when(result.exactUsedItems()).thenReturn(Map.of());
        when(result.exactEmittedItems()).thenReturn(Map.of());
        when(result.exactMissingItems()).thenReturn(Map.of());
        try (var registry = mockStatic(ECOPlanningResultRegistry.class)) {
            registry.when(() -> ECOPlanningResultRegistry.isECOOwnedPlan(plan)).thenReturn(true);
            registry.when(() -> ECOPlanningResultRegistry.find(plan)).thenReturn(result);
            registry.when(() -> ECOPlanningResultRegistry.resolveContract(plan, null))
                    .thenReturn(contract);
            assertTrue(ECOExternalCpuSupport.supportsPlan(plan));

            when(result.exactUsedItems())
                    .thenReturn(Map.of(
                            mock(appeng.api.stacks.AEKey.class), PlannerAmount.of(BigInteger.ONE.shiftLeft(64))));
            assertFalse(ECOExternalCpuSupport.supportsPlan(plan));
        }
    }

    @Test
    void blockedEcoPlanCannotUseNativeCpu() {
        ICraftingPlan plan = mock(ICraftingPlan.class);
        ECOPlanningResult result = mock(ECOPlanningResult.class);
        ECOExecutionContract contract =
                new ECOExecutionContract(UUID.randomUUID(), signature(), ExecutionMode.BLOCKED, null, "unsafe plan");
        when(result.exactPatternTimes()).thenReturn(Map.of());
        when(result.exactUsedItems()).thenReturn(Map.of());
        when(result.exactEmittedItems()).thenReturn(Map.of());
        when(result.exactMissingItems()).thenReturn(Map.of());
        try (var registry = mockStatic(ECOPlanningResultRegistry.class)) {
            registry.when(() -> ECOPlanningResultRegistry.isECOOwnedPlan(plan)).thenReturn(true);
            registry.when(() -> ECOPlanningResultRegistry.find(plan)).thenReturn(result);
            registry.when(() -> ECOPlanningResultRegistry.resolveContract(plan, null))
                    .thenReturn(contract);
            assertFalse(ECOExternalCpuSupport.supportsPlan(plan));
        }
    }

    private static PlanIdentity.Signature signature() {
        return new PlanIdentity.Signature(
                mock(appeng.api.stacks.AEKey.class), 1L, Map.of(), Map.of(), Map.of(), Map.of());
    }
}
