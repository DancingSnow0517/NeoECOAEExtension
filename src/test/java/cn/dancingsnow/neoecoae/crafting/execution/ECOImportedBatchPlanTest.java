package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import appeng.api.crafting.*;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.*;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import com.extendedae_plus.api.crafting.ScaledProcessingPattern;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ECOImportedBatchPlanTest {
    @Test void importedMultiplierBecomesExactBaseTaskCountAndSurvivesCheckpoint() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var base = mock(IPatternDetails.class);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(key, 1)});
        when(input.getMultiplier()).thenReturn(1L);
        when(base.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
        when(base.getOutputs()).thenReturn(List.of(new GenericStack(key, 1)));
        var definition = mock(AEItemKey.class);
        when(base.getDefinition()).thenReturn(definition);
        HolderLookup.Provider registries = net.minecraft.core.RegistryAccess.EMPTY; // 26.1: TagValueOutput needs a real serialization context
        doAnswer(call -> { ((net.minecraft.world.level.storage.ValueOutput) call.getArgument(0)).putString("test", "key"); return null; }).when(definition).toTag(any());
        var cpu = mock(ECOCraftingCPU.class);
        var logic = new ECOCraftingCPULogic(cpu);
        var wrapper = new ScaledProcessingPattern(base, 10);
        var plan = mock(ICraftingPlan.class);
        when(plan.patternTimes()).thenReturn(Map.of(wrapper, Long.MAX_VALUE));
        when(plan.emittedItems()).thenReturn(new KeyCounter());
        when(plan.finalOutput()).thenReturn(new GenericStack(key, Long.MAX_VALUE));
        try (var types = mockStatic(AEKeyTypes.class);
             var keys = mockStatic(AEItemKey.class);
             var patterns = mockStatic(PatternDetailsHelper.class);
             var stacks = mockStatic(GenericStack.class, CALLS_REAL_METHODS)) {
            keys.when(() -> AEItemKey.fromTag(any())).thenReturn(definition);
            patterns.when(() -> PatternDetailsHelper.decodePattern(eq(definition), any())).thenReturn(base);
            stacks.when(() -> GenericStack.writeTag(any(), any())).thenAnswer(call -> { ((net.minecraft.world.level.storage.ValueOutput) call.getArgument(0)).putString("test", "stack"); return null; });
            stacks.when(() -> GenericStack.readTag(any())).thenReturn(new GenericStack(key, Long.MAX_VALUE));
            var link = new CraftingLink(UUID.randomUUID(), true, cpu);
            var job = new ExecutingCraftingJob(plan, ignored -> {}, link, null);
            assertFalse(job.tasks.containsKey(wrapper));
            assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TEN), job.tasks.get(base).remainingExact());
            job.tasks.get(base).accept(23);
            var restored = new ExecutingCraftingJob(job.writeToNBT(registries), registries, ignored -> {}, logic);
            assertEquals(job.tasks.get(base).remainingExact(), restored.tasks.get(base).remainingExact());
            assertEquals(Map.of(wrapper, Long.MAX_VALUE), plan.patternTimes(), "Import must not mutate another CPU's plan");
        }
    }
}
