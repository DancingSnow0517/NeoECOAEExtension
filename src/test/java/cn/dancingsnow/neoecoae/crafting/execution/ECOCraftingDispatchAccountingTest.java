package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOCraftingDispatchAccountingTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private final AEKey output = mock(AEKey.class, RETURNS_DEEP_STUBS);
    private final AEKey secondary = mock(AEKey.class, RETURNS_DEEP_STUBS);
    private final AEKey container = mock(AEKey.class, RETURNS_DEEP_STUBS);
    private final IPatternDetails pattern = mock(IPatternDetails.class);
    private final ECOCraftingCPU cpu = mock(ECOCraftingCPU.class);
    private final ECOCraftingDispatchAccounting accounting = new ECOCraftingDispatchAccounting(ignored -> {}, () -> {});
    private final ICraftingPlan plan = mock(ICraftingPlan.class);
    private MockedStatic<AEKeyTypes> keyTypes;

    @BeforeEach
    void initializeKeyTypes() { keyTypes = mockStatic(AEKeyTypes.class); }

    @AfterEach
    void closeKeyTypes() { keyTypes.close(); }

    ECOCraftingDispatchAccountingTest() {
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(output, 3), new GenericStack(secondary, 1)));
        when(plan.finalOutput()).thenReturn(new GenericStack(output, 30));
        when(plan.emittedItems()).thenReturn(new KeyCounter());
        when(plan.patternTimes()).thenReturn(Map.of(pattern, 10L));
    }

    private ExecutingCraftingJob newJob() {
        return new ExecutingCraftingJob(plan, ignored -> {},
            new CraftingLink(CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false), cpu), null);
    }

    private void accept(ExecutingCraftingJob job, long crafts) {
        var request = new ECOCraftingDispatchRequest(job, null, pattern, new KeyCounter[0],
            new KeyCounter(), new KeyCounter(), 10, null, null);
        accounting.apply(request, ECOCraftingDispatchResult.batch(crafts,
            List.of(new GenericStack(output, crafts * 3), new GenericStack(secondary, crafts)),
            List.of(new GenericStack(container, crafts))), () -> {});
    }

    @Test
    void onlyAcceptedMultiCraftOutputsAreMarkedIncludingSecondaryProducts() {
        var job = newJob();
        accept(job, 1);
        assertTrue(job.batchedOutputs.isEmpty());
        accept(job, 4);
        assertEquals(Set.of(output, secondary), job.batchedOutputs);
        assertFalse(job.batchedOutputs.contains(container));
        assertEquals(5, job.tasks.get(pattern).value);
        assertEquals(15, job.waitingFor.list.get(output));
        accept(job, 1);
        assertEquals(Set.of(output, secondary), job.batchedOutputs);
    }

    @Test
    void cpuProjectionDoesNotCarryMarkersIntoANewJobOrIdleState() {
        var logic = new ECOCraftingCPULogic(cpu);
        var job = newJob();
        logic.setJobFromPersistence(job);
        assertFalse(logic.isBatchedOutput(output));
        accept(job, 4);
        assertTrue(logic.isBatchedOutput(output));
        assertTrue(logic.isBatchedOutput(secondary));
        assertFalse(logic.isBatchedOutput(container));
        logic.setJobFromPersistence(newJob());
        assertFalse(logic.isBatchedOutput(output));
        logic.setJobFromPersistence(null);
        assertFalse(logic.isBatchedOutput(output));
    }

    @Test
    void checkpointPreservesMarkersAndOlderSavesDefaultToUnmarked() {
        var registries = RegistryAccess.EMPTY;
        var definition = mock(AEItemKey.class);
        when(pattern.getDefinition()).thenReturn(definition);
        when(definition.toTag(registries)).thenAnswer(ignored -> new CompoundTag());
        var outputTag = new CompoundTag(); outputTag.putString("testKey", "output");
        var secondaryTag = new CompoundTag(); secondaryTag.putString("testKey", "secondary");
        when(output.toTagGeneric(registries)).thenReturn(outputTag);
        when(secondary.toTagGeneric(registries)).thenReturn(secondaryTag);
        var logic = new ECOCraftingCPULogic(cpu);
        try (var keys = mockStatic(AEItemKey.class);
             var genericKeys = mockStatic(AEKey.class);
             var patterns = mockStatic(PatternDetailsHelper.class);
             var stacks = mockStatic(GenericStack.class, CALLS_REAL_METHODS)) {
            keys.when(() -> AEItemKey.fromTag(eq(registries), any())).thenReturn(definition);
            genericKeys.when(() -> AEKey.fromTagGeneric(eq(registries), any())).thenAnswer(invocation ->
                ((CompoundTag) invocation.getArgument(1)).getString("testKey").equals("output") ? output : secondary);
            patterns.when(() -> PatternDetailsHelper.decodePattern(eq(definition), any())).thenReturn(pattern);
            stacks.when(() -> GenericStack.writeTag(eq(registries), any())).thenReturn(new CompoundTag());
            stacks.when(() -> GenericStack.readTag(eq(registries), any())).thenReturn(new GenericStack(output, 30));
            var job = newJob();
            job.batchedOutputs.addAll(Set.of(output, secondary));
            var saved = job.writeToNBT(registries);
            var restored = new ExecutingCraftingJob(saved, registries, ignored -> {}, logic);
            assertEquals(job.batchedOutputs, restored.batchedOutputs);
            saved.remove("batchedOutputs");
            assertTrue(new ExecutingCraftingJob(saved, registries, ignored -> {}, logic).batchedOutputs.isEmpty());
        }
    }
}
