package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.EncodedProcessingPattern;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ECOExecutionRuntimeDagStreamingTest {
    private static final long ORDER_SIZE = 500_000L;

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinaryCpuPipelinesReturnedMaterialsBeforeUpstreamTasksFinish(boolean ecoPlan) {
        var f = new Fixture(ecoPlan);
        f.logic.getInventory().insert(f.raw, 2L, Actionable.MODULATE);
        f.logic.getInventory().insert(f.sand, 2L, Actionable.MODULATE);

        assertEquals(2, f.dispatch());
        verify(f.providers[1], never()).pushPattern(any(), any());
        verify(f.providers[2], never()).pushPattern(any(), any());
        assertEquals(2L, f.job.waitingFor.list.get(f.dust));

        // Only one of the upstream machine's outputs has returned; the 500K task is still unfinished.
        assertEquals(1L, f.logic.insert(f.dust, 1L, Actionable.MODULATE));
        assertEquals(1, f.dispatch(), "the seed recipe must consume partial upstream output");
        assertEquals(ORDER_SIZE - 2L, f.job.tasks.get(f.patterns[0]).value);
        assertEquals(ORDER_SIZE - 1L, f.job.tasks.get(f.patterns[1]).value);
        assertEquals(1L, f.job.waitingFor.list.get(f.dust));
        assertEquals(1L, f.job.waitingFor.list.get(f.seed));
        verify(f.providers[2], never()).pushPattern(any(), any());

        assertEquals(1L, f.logic.insert(f.seed, 1L, Actionable.MODULATE));
        assertEquals(1, f.dispatch(), "the final stage must start while both upstream tasks remain open");
        assertEquals(ORDER_SIZE - 1L, f.job.tasks.get(f.patterns[2]).value);
        assertEquals(1L, f.job.waitingFor.list.get(f.crystal));
        assertEquals(0L, f.logic.getInventory().list.get(f.dust));
        assertEquals(0L, f.logic.getInventory().list.get(f.seed));
        assertEquals(1L, f.logic.getInventory().list.get(f.sand));
        assertFalse(f.job.suspended, f.job.permanentExecutionError);
        for (var pattern : f.patterns) assertFalse(f.job.tasks.get(pattern).isExact());
        verify(f.providers[0], times(2)).pushPattern(eq(f.patterns[0]), any());
        verify(f.providers[1]).pushPattern(eq(f.patterns[1]), any());
        verify(f.providers[2]).pushPattern(eq(f.patterns[2]), any());
    }

    @Test
    void compatibilityRuntimeAlsoStreamsDagDependencies() {
        var f = new Fixture(true);
        var runtime = new ECOExecutionRuntime(f.job.executionPlan,
                Map.of(0, f.patterns[0], 1, f.patterns[1], 2, f.patterns[2]));
        var remaining = Map.<IPatternDetails, Long>of(
                f.patterns[0], ORDER_SIZE, f.patterns[1], ORDER_SIZE, f.patterns[2], ORDER_SIZE);
        assertEquals(List.of(0, 1, 2), taskIds(runtime.candidates(remaining)));
        assertFalse(runtime.isComplete(remaining));
    }

    @ParameterizedTest
    @EnumSource(value = ECOExecutionSchedule.Type.class, names = {"CYCLE", "DYNAMIC_CYCLE"})
    void cycleDependencyKeepsItsBarrierUntilAllFiringsAreAccepted(ECOExecutionSchedule.Type type) {
        for (boolean exact : new boolean[] {false, true}) {
            var patterns = new IPatternDetails[] {pattern(), pattern(), pattern()};
            var kind = type == ECOExecutionSchedule.Type.CYCLE
                    ? ECOExecutionPlan.TaskKind.CYCLE_ORDERED : ECOExecutionPlan.TaskKind.CYCLE_DYNAMIC;
            var tasks = List.of(task(0, patterns[0], 2L, ECOExecutionPlan.TaskKind.DAG),
                    task(1, patterns[1], 2L, kind), task(2, patterns[2], 2L, ECOExecutionPlan.TaskKind.DAG));
            var steps = type == ECOExecutionSchedule.Type.CYCLE
                    ? List.of(new ECOExecutionPlan.ExecutionStep(1, 2L)) : List.<ECOExecutionPlan.ExecutionStep>of();
            var dynamic = type == ECOExecutionSchedule.Type.DYNAMIC_CYCLE ? Map.of(1, 2L) : Map.<Integer, Long>of();
            var seed = AEItemKey.of(Items.WHEAT_SEEDS);
            var phases = List.of(dagPhase(0, List.of()),
                    new ECOExecutionPlan.PhaseSpec(1, 1, type, List.of(1), steps, List.of(), dynamic, Map.of(seed, 2L)),
                    dagPhase(2, List.of(0, 1)));
            var mode = type == ECOExecutionSchedule.Type.CYCLE ? ExecutionMode.ORDERED_CYCLE : ExecutionMode.DYNAMIC_CYCLE;
            var plan = new ECOExecutionPlan(mock(PlanIdentity.Signature.class), mode, tasks, phases,
                    new ECOExecutionSchedule(List.of()));
            var progress = new ExecutingCraftingJob.TaskProgress[3];
            for (int id = 0; id < progress.length; id++) {
                progress[id] = new ExecutingCraftingJob.TaskProgress();
                if (exact) progress[id].setExact(BigInteger.TWO, BigInteger.TWO);
                else progress[id].value = 2L;
            }
            var runtime = new ECOExecutionRuntime(plan, patterns, progress);
            assertEquals(List.of(0, 1), taskIds(runtime.candidates()));
            var cycle = runtime.candidates().get(1);
            progress[1].accept(1L);
            runtime.onAccepted(cycle, 1L, new KeyCounter[0]);
            assertEquals(List.of(0, 1), taskIds(runtime.candidates()));
            assertEquals(Map.of(seed, 2L), runtime.protectedStartupSeed(null));

            cycle = runtime.candidates().get(1);
            progress[1].accept(1L);
            runtime.onAccepted(cycle, 1L, new KeyCounter[0]);
            assertEquals(List.of(0, 2), taskIds(runtime.candidates()),
                    "completed cycles release consumers even while their DAG dependency remains unfinished");
            assertEquals(Map.of(), runtime.protectedStartupSeed(null));
            assertFalse(runtime.isComplete());
        }
    }

    private static List<Integer> taskIds(List<ECOExecutionRuntime.DispatchCandidate> candidates) {
        return candidates.stream().map(ECOExecutionRuntime.DispatchCandidate::taskId).toList();
    }

    private static ECOExecutionPlan.TaskSpec task(int id, IPatternDetails pattern, long count,
            ECOExecutionPlan.TaskKind kind) {
        return new ECOExecutionPlan.TaskSpec(id, PlanIdentity.patternIdentityFor(pattern), pattern,
                ECOExecutionPlan.PatternRuntimeInfo.from(pattern), count, id, kind);
    }

    private static ECOExecutionPlan.PhaseSpec dagPhase(int id, List<Integer> dependencies) {
        return new ECOExecutionPlan.PhaseSpec(id, id, ECOExecutionSchedule.Type.DAG,
                List.of(id), List.of(), dependencies);
    }

    private static IPatternDetails pattern() {
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(List.of(new GenericStack(mock(AEKey.class), 1L)));
        return pattern;
    }

    private static AEProcessingPattern processingPattern(AEItemKey output, AEItemKey... inputs) {
        var encoded = mock(EncodedProcessingPattern.class);
        when(encoded.sparseInputs()).thenReturn(java.util.Arrays.stream(inputs)
                .map(key -> new GenericStack(key, 1L)).toList());
        when(encoded.sparseOutputs()).thenReturn(List.of(new GenericStack(output, 1L)));
        var definition = mock(AEItemKey.class);
        when(definition.get(AEComponents.ENCODED_PROCESSING_PATTERN)).thenReturn(encoded);
        return new AEProcessingPattern(definition);
    }

    private static final class Fixture {
        final AEItemKey raw = AEItemKey.of(Items.QUARTZ);
        final AEItemKey dust = AEItemKey.of(Items.SUGAR);
        final AEItemKey sand = AEItemKey.of(Items.SAND);
        final AEItemKey seed = AEItemKey.of(Items.WHEAT_SEEDS);
        final AEItemKey crystal = AEItemKey.of(Items.DIAMOND);
        final AEProcessingPattern[] patterns = {processingPattern(dust, raw),
                processingPattern(seed, dust, sand), processingPattern(crystal, seed)};
        final ICraftingProvider[] providers = {mock(ICraftingProvider.class),
                mock(ICraftingProvider.class), mock(ICraftingProvider.class)};
        final IEnergyService energy = mock(IEnergyService.class);
        final CraftingService service = mock(CraftingService.class);
        final Level level = mock(Level.class);
        final ECOCraftingCPULogic logic;
        final ExecutingCraftingJob job;

        Fixture(boolean ecoPlan) {
            var cpu = mock(ECOCraftingCPU.class);
            when(cpu.isActive()).thenReturn(true);
            when(cpu.getLevel()).thenReturn(level);
            logic = new ECOCraftingCPULogic(cpu);
            var craftingPlan = mock(ICraftingPlan.class);
            when(craftingPlan.finalOutput()).thenReturn(new GenericStack(crystal, ORDER_SIZE));
            when(craftingPlan.patternTimes()).thenReturn(Map.of(
                    patterns[0], ORDER_SIZE, patterns[1], ORDER_SIZE, patterns[2], ORDER_SIZE));
            when(craftingPlan.emittedItems()).thenReturn(new KeyCounter());
            var tasks = List.of(task(0, patterns[0], ORDER_SIZE, ECOExecutionPlan.TaskKind.DAG),
                    task(1, patterns[1], ORDER_SIZE, ECOExecutionPlan.TaskKind.DAG),
                    task(2, patterns[2], ORDER_SIZE, ECOExecutionPlan.TaskKind.DAG));
            var executionPlan = new ECOExecutionPlan(mock(PlanIdentity.Signature.class), ExecutionMode.PHASED_DAG,
                    tasks, List.of(dagPhase(0, List.of()), dagPhase(1, List.of(0)), dagPhase(2, List.of(1))),
                    new ECOExecutionSchedule(List.of()));
            var link = mock(CraftingLink.class);
            when(link.getCraftingID()).thenReturn(UUID.randomUUID());
            try (var tracker = mockConstruction(ElapsedTimeTracker.class)) {
                job = new ExecutingCraftingJob(craftingPlan, ecoPlan ? executionPlan : null, ignored -> {}, link, null);
            }
            logic.setJobFromPersistence(job);
            for (int id = 0; id < patterns.length; id++) {
                when(service.getProviders(patterns[id])).thenReturn(List.of(providers[id]));
                when(providers[id].pushPattern(any(), any())).thenReturn(true);
            }
            when(energy.extractAEPower(anyDouble(), any(), any())).thenAnswer(call -> call.getArgument(0));
        }

        int dispatch() { return logic.executeNormalCrafting(64, service, energy, level); }
    }
}
