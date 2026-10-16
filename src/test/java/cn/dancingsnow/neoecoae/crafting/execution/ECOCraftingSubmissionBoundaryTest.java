package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingSubmitResult;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOForcedCraftingPlan;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.*;
import cn.dancingsnow.neoecoae.crafting.planner.result.*;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.mixins.ae2.crafting.Ae2CraftingCpuFastPathMixin;
import cn.dancingsnow.neoecoae.mixins.compat.advancedae.crafting.AdvancedAeCraftingCpuLogicMixin;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

class ECOCraftingSubmissionBoundaryTest {
    @BeforeAll static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
    }

    @AfterEach void clearRegistry() { ECOPlanningResultRegistry.clear(); }

    @Test
    @SuppressWarnings("deprecation")
    void legacySubmissionAndScheduleProjectionsRetainContractBehavior() {
        var diagnostic = cycleResult(PlanningStatus.SUCCESS, CycleExecutionDisposition.ORDERED_EXECUTION);
        var result = new ECOPlanningResult(PlanningStatus.SUCCESS, diagnostic.plan(), new ECOPlanTrace(), List.of(),
            diagnostic.components(), List.of(0), 0L);
        var plan = result.plan();
        ECOPlanningResultRegistry.register(plan, result);
        var contract = ECOPlanningResultRegistry.resolveContract(plan, result);
        assertEquals(ExecutionMode.ORDERED_CYCLE, contract.mode());
        ECOPlanningResultRegistry.withSubmissionAlias(plan, result, () -> {
            var legacy = ECOPlanningResultRegistry.activeSubmissionMetadata(plan);
            assertNotNull(legacy);
            assertSame(contract.executionPlan(), legacy.executionPlan());
            assertEquals(contract.planningId(), legacy.planningId());
            assertEquals(contract.schedule(), legacy.executionSchedule());
            assertTrue(legacy.cycleExpected());
            assertTrue(legacy.metadataMatch());
            assertEquals("ECO", legacy.selectedPlanner());
            return null;
        });
        assertNull(ECOPlanningResultRegistry.activeSubmissionMetadata(plan));
        var recovered = ECOPlanningResultRegistry.recoverExecutionMetadata(plan);
        var legacy = ECOPlanningResultRegistry.recoverSchedule(plan);
        assertNotNull(recovered);
        assertNotNull(legacy);
        assertEquals(recovered.schedule(), legacy.schedule());
        assertEquals(recovered.matchMode(), legacy.matchMode());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void changedTaskVectorCannotReuseCachedContractEvenThroughTrustedWrapper(boolean wrapped) {
        var key = mock(AEKey.class);
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(key, 1L)));
        var tasks = new HashMap<IPatternDetails, Long>();
        tasks.put(pattern, 2L);
        var source = mock(CraftingPlan.class);
        when(source.finalOutput()).thenReturn(new GenericStack(key, 2L));
        when(source.patternTimes()).thenReturn(tasks);
        when(source.usedItems()).thenReturn(new KeyCounter());
        when(source.emittedItems()).thenReturn(new KeyCounter());
        when(source.missingItems()).thenReturn(new KeyCounter());
        var result = new ECOPlanningResult(PlanningStatus.SUCCESS, source, new ECOPlanTrace(), List.of(), 0L);
        assertEquals(ExecutionMode.NATIVE, result.executionContract().mode());
        ECOPlanningResultRegistry.register(source, result);
        ICraftingPlan submitted = wrapped ? new ECOForcedCraftingPlan(source) : source;
        tasks.put(pattern, 3L);

        var contract = ECOPlanningResultRegistry.resolveContract(submitted, null);
        assertNotNull(contract);
        assertEquals(ExecutionMode.BLOCKED, contract.mode());
        assertEquals("PLAN_IDENTITY_CHANGED", contract.error());
        assertEquals(cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity.of(submitted),
            contract.planSignature());
        assertRejectedWithoutSideEffects(submitted);
    }

    static Stream<Arguments> failedCycles() {
        return Arrays.stream(PlanningStatus.values()).filter(status -> status != PlanningStatus.SUCCESS)
            .flatMap(status -> Stream.of(CycleExecutionDisposition.ORDERED_EXECUTION,
                CycleExecutionDisposition.DYNAMIC_EXECUTION).map(disposition -> Arguments.of(status, disposition)));
    }

    @ParameterizedTest @MethodSource("failedCycles")
    void failedCycleCannotBecomeNativeSafeAtSubmission(PlanningStatus status,
            CycleExecutionDisposition disposition) throws Exception {
        var result = cycleResult(status, disposition);
        var plan = result.plan();
        var fuzzy = Set.of(ResourceLocation.parse("minecraft:stone"));
        result.setFuzzyPlanningItemIds(fuzzy);
        // The diagnostic contract intentionally skips phase construction for non-success results.
        assertEquals(ExecutionMode.NATIVE, result.executionContract().mode());
        ECOPlanningResultRegistry.register(plan, result);

        var contract = ECOPlanningResultRegistry.resolveContract(plan, null);
        assertNotNull(contract);
        assertEquals(ExecutionMode.BLOCKED, contract.mode());
        assertEquals("PLANNING_NOT_SUCCESS:" + status, contract.error());
        assertEquals(result.planningId(), contract.planningId());
        assertEquals(result.executionContract().planSignature(), contract.planSignature());
        assertEquals(fuzzy, contract.fuzzyPlanningItemIds());
        assertNull(contract.executionPlan());
        assertEquals(contract, ECOPlanningResultRegistry.resolveContract(plan, result));
        assertFalse(ECOExternalCpuSupport.supportsPlan(plan));
        assertExternalCpuHooksReject(plan);
    }

    @Test void existingBlockedReasonIsPreserved() {
        var result = cycleResult(PlanningStatus.CYCLE_UNRESOLVED, CycleExecutionDisposition.BLOCKED);
        ECOPlanningResultRegistry.register(result.plan(), result);
        assertSame(result.executionContract(), ECOPlanningResultRegistry.resolveContract(result.plan(), null));
        assertEquals("CYCLE_NOT_SOLVED", ECOPlanningResultRegistry.resolveContract(result.plan(), result).error());
    }

    @ParameterizedTest @MethodSource("failedCycles")
    void forcedWrapperCannotLoseFailedCycleProvenance(PlanningStatus status,
            CycleExecutionDisposition disposition) {
        var original = cycleResult(status, disposition);
        var source = original.plan();
        var missing = new KeyCounter();
        missing.add(source.finalOutput().what(), 7L);
        var simulation = new CraftingPlan(source.finalOutput(), source.bytes(), true, source.multiplePaths(),
            source.usedItems(), source.emittedItems(), missing, source.patternTimes());
        var result = new ECOPlanningResult(status, simulation, new ECOPlanTrace(), List.of(),
            original.components(), List.of(), 0L);
        ECOPlanningResultRegistry.register(simulation, result);
        var forced = new ECOForcedCraftingPlan(simulation);

        assertFalse(cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity.matches(simulation, forced));
        assertSame(result, ECOPlanningResultRegistry.findExact(forced));
        assertTrue(ECOPlanningResultRegistry.isECOOwnedPlan(forced));
        assertTrue(ECOPlanningResultRegistry.cycleSafetyRequired(forced, result));
        var contract = ECOPlanningResultRegistry.resolveContract(forced, null);
        assertNotNull(contract);
        assertEquals(ExecutionMode.BLOCKED, contract.mode());
        assertEquals(result.planningId(), contract.planningId());
        assertEquals(cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity.of(forced),
            contract.planSignature());
        assertFalse(ECOExternalCpuSupport.supportsPlan(forced));
        assertRejectedWithoutSideEffects(forced);

        // Copying the transformed task vector without the final ECO wrapper proves no provenance.
        var foreign = new CraftingPlan(forced.finalOutput(), forced.bytes(), false, forced.multiplePaths(),
            forced.usedItems(), forced.emittedItems(), forced.missingItems(), forced.patternTimes());
        assertNull(ECOPlanningResultRegistry.resolveContract(foreign, null));
        assertFalse(ECOPlanningResultRegistry.isECOOwnedPlan(foreign));
        assertFalse(ECOPlanningResultRegistry.cycleSafetyRequired(foreign, result));
    }

    @ParameterizedTest
    @EnumSource(value = CycleExecutionDisposition.class,
        names = {"ORDERED_EXECUTION", "DYNAMIC_EXECUTION", "BLOCKED"})
    void directCpuRejectsBlockedCycleBeforeMaterialExtraction(CycleExecutionDisposition disposition) {
        var result = cycleResult(PlanningStatus.MISSING_ITEMS, disposition);
        ECOPlanningResultRegistry.register(result.plan(), result);
        assertRejectedWithoutSideEffects(result.plan());
    }

    @Test void directCpuRejectsSimulationBeforeMaterialExtraction() {
        var key = mock(AEKey.class);
        var simulation = new CraftingPlan(new GenericStack(key, 1L), 0L, true, false,
            new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
        assertRejectedWithoutSideEffects(simulation);
    }

    @Test void missingDagForceStartStillReachesMaterialExtraction() {
        var key = mock(AEKey.class, RETURNS_DEEP_STUBS);
        var missing = new KeyCounter();
        missing.add(key, 7L);
        var simulation = new CraftingPlan(new GenericStack(key, 1L), 0L, true, false,
            new KeyCounter(), new KeyCounter(), missing, Map.of());
        var result = new ECOPlanningResult(PlanningStatus.MISSING_ITEMS, simulation, new ECOPlanTrace(), List.of(), 0L);
        ECOPlanningResultRegistry.register(simulation, result);
        assertTrue(ECOPlanningResultRegistry.resolveContract(simulation, result).executable());
        var forced = new ECOForcedCraftingPlan(simulation);
        var cpu = activeCpu();
        var logic = new ECOCraftingCPULogic(cpu);
        var grid = mock(IGrid.class);
        var source = IActionSource.empty();
        try (var helpers = mockStatic(CraftingCpuHelper.class)) {
            // Stop at the extraction boundary: admission must not reject a normal forced DAG as incomplete.
            helpers.when(() -> CraftingCpuHelper.tryExtractInitialItems(forced, grid, logic.getInventory(), source))
                .thenReturn(new GenericStack(key, 1L));
            assertNotSame(CraftingSubmitResult.INCOMPLETE_PLAN, logic.trySubmitJob(grid, forced, source, null));
            helpers.verify(() -> CraftingCpuHelper.tryExtractInitialItems(forced, grid, logic.getInventory(), source));
            assertNull(logic.getJob());
        }
    }

    private static void assertRejectedWithoutSideEffects(ICraftingPlan plan) {
        var cpu = activeCpu();
        var logic = new ECOCraftingCPULogic(cpu);
        var grid = mock(IGrid.class);
        var source = mock(IActionSource.class);
        var requester = mock(ICraftingRequester.class);
        try (var helpers = mockStatic(CraftingCpuHelper.class)) {
            assertSame(CraftingSubmitResult.INCOMPLETE_PLAN, logic.trySubmitJob(grid, plan, source, requester));
            helpers.verifyNoInteractions();
        }
        assertNull(logic.getJob());
        assertTrue(logic.getInventory().list.isEmpty());
        assertFalse(logic.exactInventory().isEnabled());
        assertNull(logic.bigOrder.progress());
        verifyNoInteractions(grid, source, requester);
    }

    private static ECOCraftingCPU activeCpu() {
        var cpu = mock(ECOCraftingCPU.class);
        when(cpu.isActive()).thenReturn(true);
        when(cpu.getAvailableStorage()).thenReturn(Long.MAX_VALUE);
        return cpu;
    }

    private static ECOPlanningResult cycleResult(PlanningStatus status, CycleExecutionDisposition disposition) {
        var key = mock(AEKey.class);
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(key, 1L)));
        var plan = new CraftingPlan(new GenericStack(key, 2L), 0L, false, false,
            new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, 2L));
        var run = mock(PatternRun.class);
        when(run.details()).thenReturn(pattern);
        when(run.count()).thenReturn(2L);
        when(run.repeatWidth()).thenReturn(1);
        when(run.repetitions()).thenReturn(1L);
        boolean blocked = disposition == CycleExecutionDisposition.BLOCKED;
        var cycle = blocked ? CycleSolveResult.failure(CycleSolveStatus.UNKNOWN_BUDGET, List.of(), CycleSolveMetrics.NONE)
            : new CycleSolveResult(CycleSolveStatus.SUCCESS, Map.of(pattern, 2L), Map.of(), Map.of(), Map.of(),
                Map.of(key, 2L), Map.of(key, 2L), List.of(), List.of(run), List.of(), CycleSolveMetrics.NONE);
        var component = new ComponentPlanningResult(0, ComponentPlanningResult.Type.CYCLIC,
            blocked ? ComponentPlanningResult.Status.UNRESOLVED : ComponentPlanningResult.Status.PLANNED,
            Map.of(key, 2L), Set.of(pattern), Set.of(pattern),
            blocked ? CyclePlanningStatus.UNKNOWN_BUDGET : CyclePlanningStatus.SOLVED,
            null, Map.of(), null, cycle, disposition, Map.of());
        return new ECOPlanningResult(status, plan, new ECOPlanTrace(), List.of(), List.of(component), List.of(), 0L);
    }

    private static void assertExternalCpuHooksReject(ICraftingPlan plan) throws Exception {
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
