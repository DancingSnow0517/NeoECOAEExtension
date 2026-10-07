package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderProgress;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderState;
import cn.dancingsnow.neoecoae.api.me.progress.ECOCraftingProgressView;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ComputationCpuEntryTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private final ECOCraftingCPU cpu = mock(ECOCraftingCPU.class);
    private final ECOCraftingCPULogic logic = mock(ECOCraftingCPULogic.class);
    private final ECOCraftingProgressView view = mock(ECOCraftingProgressView.class);
    private final AEFluidKey fluid = mock(AEFluidKey.class);

    ComputationCpuEntryTest() {
        when(fluid.getAmountPerUnit()).thenReturn(1000);
        when(fluid.getUnitSymbol()).thenReturn("B");
        IECOTier tier = mock(IECOTier.class);
        when(tier.getCPUOverlayTexture()).thenReturn(ResourceLocation.parse("neoecoae:textures/gui/cpu_overlay/l4.png"));
        when(cpu.getTier()).thenReturn(tier);
        when(cpu.getLogic()).thenReturn(logic);
        when(cpu.getProgressView()).thenReturn(view);
        when(cpu.getSelectionMode()).thenReturn(CpuSelectionMode.ANY);
        when(logic.getFinalJobOutput()).thenReturn(new GenericStack(fluid, 1000));
        when(logic.getRemainingJobOutputAmount()).thenReturn(123L);
        when(logic.hasJob()).thenReturn(true);
        when(view.bigOrder()).thenReturn(Optional.empty());
    }

    @Test
    void fluidTargetsAndPausedJobsRemainVisible() {
        when(logic.isJobSuspended()).thenReturn(true);
        ComputationCpuEntry entry = ComputationCpuEntry.sample(cpu, 7);
        assertSame(fluid, entry.output());
        assertEquals(BigInteger.valueOf(123), entry.remaining());
        assertEquals("suspended", entry.status());
        assertEquals("0.123 B", entry.amount(entry.remaining(), true));
        assertEquals("1 B", entry.amount(entry.requested(), false));
    }

    @Test
    void returningCpuIsRepresentedEvenWhenItsJobHasFinished() {
        when(logic.hasJob()).thenReturn(false);
        when(cpu.hasRemainingItems()).thenReturn(true);
        when(logic.getRemainingJobOutputAmount()).thenReturn(0L);
        ComputationCpuEntry entry = ComputationCpuEntry.sample(cpu, 7);
        assertEquals("returning", entry.status());
        assertEquals(BigInteger.ZERO, entry.remaining());
    }

    @Test
    void parentOrderAmountsAndProgressSurviveBeyondLongAndDoubleRanges() {
        BigInteger requested = BigInteger.TEN.pow(400);
        var parent = new ECOBigOrderProgress(UUID.randomUUID(), ECOBigOrderState.RUNNING_CHILD,
            requested, requested.divide(BigInteger.TWO), requested.divide(BigInteger.TWO), 100, 50, "");
        when(view.bigOrder()).thenReturn(Optional.of(parent));
        ComputationCpuEntry entry = ComputationCpuEntry.sample(cpu, 3);
        assertEquals(requested, entry.requested());
        assertEquals(requested.divide(BigInteger.TWO), entry.remaining());
        assertEquals(0.5F, entry.progress());
    }

    @Test
    void menuPayloadPreservesGenericTargetAndExactAmounts() {
        BigInteger requested = BigInteger.TEN.pow(400);
        ComputationCpuEntry entry = new ComputationCpuEntry(7, "", fluid, requested,
            requested.subtract(BigInteger.ONE), 0.5F, 20, 1024, 32, CpuSelectionMode.PLAYER_ONLY,
            ResourceLocation.parse("neoecoae:textures/gui/cpu_overlay/l4.png"), "running");
        CompoundTag fluidTag = new CompoundTag();
        fluidTag.putString("type", "fluid");
        when(fluid.toTagGeneric(RegistryAccess.EMPTY)).thenReturn(fluidTag);
        CompoundTag payload = entry.write(RegistryAccess.EMPTY);
        try (var keys = mockStatic(AEKey.class)) {
            keys.when(() -> AEKey.fromTagGeneric(RegistryAccess.EMPTY, fluidTag)).thenReturn(fluid);
            assertEquals(entry, ComputationCpuEntry.read(payload, RegistryAccess.EMPTY));
        }
    }

    @Test
    void largeCapacityTooltipKeepsFullBytesWithoutAe2UnitOverflow() {
        when(cpu.getAvailableStorage()).thenReturn(Long.MAX_VALUE);
        ComputationCpuEntry entry = ComputationCpuEntry.sample(cpu, 1);
        assertEquals("8 EB", entry.storageText());
        assertEquals("8 EB (9,223,372,036,854,775,807 B)", entry.fullStorageText());
    }
}
