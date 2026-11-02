package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningImprovements;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.Test;

class ECOCraftingPlanUpdaterTest {
    private static AEKey raw, intermediate, product, fuel;
    private MockedStatic<AEKeyTypes> keyTypes;

    @BeforeEach void initializeKeyTypes() {
        keyTypes = mockStatic(AEKeyTypes.class);
        var types = new java.util.LinkedHashSet<>(List.of(AEKeyType.items(), AEKeyType.fluids()));
        keyTypes.when(AEKeyTypes::getAll).thenReturn(types);
        for (var type : types) keyTypes.when(() -> AEKeyTypes.get(type.getId())).thenReturn(type);
    }

    @AfterEach void closeKeyTypes() { keyTypes.close(); }

    @BeforeAll static void bootstrap() {
        cn.dancingsnow.neoecoae.util.InventoryTestBootstrap.initialize();
        raw = AEItemKey.of(Items.RAW_IRON);
        intermediate = AEItemKey.of(Items.IRON_INGOT);
        product = AEItemKey.of(Items.GOLD_INGOT);
        fuel = AEItemKey.of(Items.COAL);
    }

    @Test void replacesOnlyTheRemainderAndKeepsLinkDeliveryProgressAndElapsedWork() {
        var f = new Fixture();
        var link = f.job.link;
        var tracker = f.job.timeTracker;
        long elapsed = tracker.getElapsedTime();
        assertTrue(f.install());
        assertSame(f.job, f.host.getJob());
        assertSame(link, f.job.link);
        assertSame(tracker, f.job.timeTracker);
        assertEquals(new GenericStack(product, 10), f.job.finalOutput);
        assertEquals(8, f.job.remainingAmount);
        assertEquals(42, f.job.playerId);
        assertEquals(4, tracker.getCompletedWork(product.getType()));
        assertEquals(12, tracker.getStartedWork(product.getType()));
        assertTrue(tracker.getElapsedTime() >= elapsed);
        assertEquals(Set.of(f.direct), f.job.tasks.keySet());
        assertEquals(8, f.job.tasks.get(f.direct).value);
        assertEquals(8, f.host.getInventory().list.get(raw));
        assertTrue(f.job.batchedOutputs.contains(intermediate));
        assertNotNull(f.job.executionRuntime);
        var next = f.job.executionRuntime.candidates().getFirst();
        assertSame(f.direct, next.pattern());
        f.job.tasks.get(f.direct).accept(3);
        f.job.executionRuntime.onAccepted(next, 3, new KeyCounter[0]);
        assertEquals(5, f.job.executionRuntime.candidates().getFirst().maxDispatchCount());
        verify(f.cpu).markDirty();
    }

    @Test void actualAsyncControllerInstallsAtTheNextUnchangedBoundaryAndStopsAfterItsBudget() {
        var f = new Fixture();
        var completed = CompletableFuture.completedFuture(f.candidate);
        when(f.context.remaining(any(), eq(8L))).thenReturn(completed);
        f.updater.tick(1);
        assertFalse(f.job.tasks.containsKey(f.direct), "Dispatch never waits on the search future");
        f.updater.tick(2);
        assertTrue(f.job.tasks.containsKey(f.direct));
        f.updater.tick(22);
        f.updater.tick(23);
        f.updater.tick(43);
        verify(f.context, times(2)).remaining(any(), eq(8L));
    }

    @Test void progressStockAndProviderChangesInvalidateCapturedCandidates() {
        var progress = new Fixture();
        var snapshot = progress.snapshot();
        progress.job.tasks.get(progress.first).accept(1);
        assertFalse(progress.updater.install(progress.job, snapshot, progress.candidate));
        assertFalse(progress.job.tasks.containsKey(progress.direct));

        var stock = new Fixture();
        snapshot = stock.snapshot();
        stock.host.getInventory().list.add(raw, 1);
        assertFalse(stock.updater.install(stock.job, snapshot, stock.candidate));

        var providers = new Fixture();
        snapshot = providers.snapshot();
        when(providers.context.isCurrent()).thenReturn(false);
        assertFalse(providers.updater.install(providers.job, snapshot, providers.candidate));
    }

    @Test void inFlightOutputsSuspensionAndUnconfirmedSuppliesKeepTheOriginalPlan() {
        var waiting = new Fixture();
        waiting.job.waitingFor.insert(intermediate, 1, Actionable.MODULATE);
        assertFalse(waiting.install());
        assertEquals(1, waiting.job.waitingFor.list.get(intermediate));
        var paused = new Fixture();
        paused.job.suspended = true;
        assertFalse(paused.install());
        var emitted = new Fixture();
        emitted.candidate.plan().emittedItems().add(fuel, 1);
        assertFalse(emitted.install());
        var supplied = new Fixture();
        supplied.candidate.plan().usedItems().add(raw, 1);
        assertFalse(supplied.install());
        var exact = new Fixture();
        exact.job.exactOrder = true;
        assertFalse(exact.install());
        var cancelled = new Fixture();
        cancelled.job.link.cancel();
        assertFalse(cancelled.install());
    }

    @Test void startupReservationsCannotDisappearWhenTheSuffixIsRebound() {
        var f = new Fixture();
        var runtime = mock(ECOExecutionRuntime.class);
        when(runtime.protectedStartupSeed(null)).thenReturn(Map.of(raw, 1L));
        f.job.executionRuntime = runtime;
        assertFalse(f.install());
        assertSame(runtime, f.job.executionRuntime);
        assertEquals(8, f.job.tasks.get(f.first).value);
    }

    @Test void submissionTransfersOnlyTheMatchingGridsContextAndFreezesConfirmationWork() {
        var f = new Fixture();
        var grid = mock(appeng.api.networking.IGrid.class, RETURNS_DEEP_STUBS);
        when(f.cpu.isActive()).thenReturn(true);
        when(f.context.belongsTo(grid.getCraftingService())).thenReturn(true);
        f.candidate.setImprovements(f.context);
        cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry.register(f.candidate.plan(), f.candidate);
        f.host.setJobFromLifecycle(null);
        f.host.getInventory().list.clear();
        try (var helpers = mockStatic(CraftingCpuHelper.class, CALLS_REAL_METHODS)) {
            helpers.when(() -> CraftingCpuHelper.tryExtractInitialItems(any(), any(), any(), any())).thenReturn(null);
            assertTrue(f.host.trySubmitJob(grid, f.candidate.plan(),
                    appeng.api.networking.security.IActionSource.empty(), null).successful());
            assertSame(f.context, f.host.getJob().improvements);
            verify(f.context).close();
        }
    }

    @Test void consumingTheRetainedSeedIsNotAnImprovementEvenWithFewerFirings() {
        var f = new Fixture();
        var grow = pattern(Items.PAPER, List.of(new GenericStack(raw, 1), new GenericStack(fuel, 1)),
                new GenericStack(product, 2));
        var restore = pattern(Items.BOOK, List.of(new GenericStack(product, 1)), new GenericStack(raw, 1));
        f.job = new ExecutingCraftingJob(plan(product, 2, Map.of(grow, 2L, restore, 2L), stock(raw, 1, fuel, 2)),
                ignored -> {}, f.job.link, 42);
        f.job.seedKeys.add(raw);
        f.job.improvements = f.context;
        f.host.setJobFromLifecycle(f.job);
        f.host.getInventory().list.clear();
        f.host.getInventory().list.addAll(stock(raw, 1, fuel, 2));
        f.candidate = result(product, 2, grow, 1, stock(raw, 1, fuel, 1));
        assertFalse(f.install());
        assertEquals(2, f.job.tasks.get(restore).value);
        assertEquals(1, f.host.getInventory().list.get(raw));
    }

    @Test void swappingJobsCancelsQueuedWorkAndCannotInstallAResultForTheOldJob() {
        var f = new Fixture();
        var queued = new CompletableFuture<ECOPlanningResult>();
        when(f.context.remaining(any(), anyLong())).thenReturn(queued);
        f.updater.tick(1);
        var old = f.job;
        f.host.setJobFromLifecycle(null);
        f.updater.tick(2);
        assertTrue(queued.isCancelled());
        assertFalse(f.updater.install(old, ECOCraftingPlanUpdater.Snapshot.capture(old, new KeyCounter()), f.candidate));
    }

    @Test void savedReplacementRestoresItsOwnTaskVectorRuntimeAndOriginalOrderIdentity() {
        var f = new Fixture();
        AEKey savedSeed = appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
        f.job.seedKeys.add(savedSeed);
        assertTrue(f.install());
        f.job.tasks.get(f.direct).accept(3);
        f.job.executionRuntime.onAccepted(f.job.executionRuntime.candidates().getFirst(), 3, new KeyCounter[0]);
        f.job.batchedOutputs.clear();
        var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        try (var patterns = mockStatic(PatternDetailsHelper.class);
             var stacks = mockStatic(GenericStack.class, CALLS_REAL_METHODS);
             var internal = mockStatic(appeng.api.stacks.AEKeyTypesInternal.class)) {
            internal.when(appeng.api.stacks.AEKeyTypesInternal::getRegistry)
                    .thenReturn(cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys.REGISTRY);
            // AE2's generic item codec requires its live missing-content item registration. Keep that platform
            // boundary deterministic while exercising the real job, phase, cursor and progress NBT codecs.
            stacks.when(() -> GenericStack.writeTag(eq(registries), any())).thenAnswer(call -> {
                GenericStack stack = call.getArgument(1);
                var tag = new net.minecraft.nbt.CompoundTag();
                if (stack != null) {
                    tag.put("testKey", ((AEItemKey) stack.what()).toTag(registries));
                    tag.putLong("amount", stack.amount());
                }
                return tag;
            });
            stacks.when(() -> GenericStack.readTag(eq(registries), any())).thenAnswer(call -> {
                net.minecraft.nbt.CompoundTag tag = call.getArgument(1);
                var key = AEItemKey.fromTag(registries, tag.getCompound("testKey"));
                return key == null ? null : new GenericStack(key, tag.getLong("amount"));
            });
            var saved = f.job.writeToNBT(registries);
            assertFalse(saved.getBoolean("executionPersistenceFailed"));
            patterns.when(() -> PatternDetailsHelper.decodePattern(eq(f.direct.getDefinition()), any())).thenReturn(f.direct);
            var restored = new ExecutingCraftingJob(saved, registries, ignored -> {}, f.host);
            assertFalse(restored.suspended, restored.permanentExecutionError);
            assertEquals(f.job.link.getCraftingID(), restored.link.getCraftingID());
            assertEquals(new GenericStack(product, 10), restored.finalOutput);
            assertEquals(8, restored.remainingAmount);
            assertEquals(Set.of(f.direct), restored.tasks.keySet());
            assertEquals(5, restored.tasks.get(f.direct).value);
            assertEquals(8, restored.executionPlan.signature().finalAmount());
            assertEquals(128, restored.reservedBytes);
            assertTrue(restored.seedKeys.contains(savedSeed));
            assertEquals(5, restored.executionRuntime.candidates().getFirst().maxDispatchCount());
            assertEquals(4, restored.timeTracker.getCompletedWork(product.getType()));
            assertEquals(12, restored.timeTracker.getStartedWork(product.getType()));
        }
    }

    @Test void progressRebaseKeepsOtherKeyTypesWhenOneDenominatorOverflowsLong() {
        var tracker = new ElapsedTimeTracker();
        var remaining = new ElapsedTimeTracker();
        var types = AEKeyTypes.getAll().stream().toList();
        assertTrue(types.size() >= 2);
        tracker.addMaxItems(Long.MAX_VALUE, types.get(0));
        tracker.decrementItems(20, types.get(0));
        tracker.addMaxItems(100, types.get(1));
        tracker.decrementItems(7, types.get(1));
        remaining.addMaxItems(Long.MAX_VALUE, types.get(0));
        remaining.addMaxItems(5, types.get(1));
        tracker.replaceRemainingWork(remaining);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(20)), tracker.getExactStartedWork(types.get(0)));
        assertEquals(BigInteger.valueOf(7), tracker.getExactCompletedWork(types.get(1)));
        assertEquals(BigInteger.valueOf(12), tracker.getExactStartedWork(types.get(1)));
    }

    private static final class Fixture {
        final ECOCraftingCPU cpu = mock(ECOCraftingCPU.class);
        final ECOCraftingCPULogic host = new ECOCraftingCPULogic(cpu);
        final ECOPlanningImprovements context = mock(ECOPlanningImprovements.class);
        final IPatternDetails first = pattern(Items.PAPER, List.of(new GenericStack(raw, 1)), new GenericStack(intermediate, 1));
        final IPatternDetails second = pattern(Items.BOOK, List.of(new GenericStack(intermediate, 1)), new GenericStack(product, 1));
        final IPatternDetails direct = pattern(Items.MAP, List.of(new GenericStack(raw, 1)), new GenericStack(product, 1));
        final ECOCraftingPlanUpdater updater = new ECOCraftingPlanUpdater(host);
        ExecutingCraftingJob job;
        ECOPlanningResult candidate;

        Fixture() {
            when(cpu.getAvailableStorage()).thenReturn(100000L);
            when(context.isCurrent()).thenReturn(true);
            var link = new CraftingLink(CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false), cpu);
            job = new ExecutingCraftingJob(plan(product, 10, Map.of(first, 10L, second, 10L), stock(raw, 10)),
                    ignored -> {}, link, 42);
            job.tasks.get(first).accept(2);
            job.tasks.get(second).accept(2);
            job.timeTracker.decrementItems(4, product.getType());
            job.remainingAmount = 8;
            job.improvements = context;
            job.batchedOutputs.add(intermediate);
            host.setJobFromLifecycle(job);
            host.getInventory().list.add(raw, 8);
            candidate = result(product, 8, direct, 8, stock(raw, 8));
        }

        ECOCraftingPlanUpdater.Snapshot snapshot() { return ECOCraftingPlanUpdater.Snapshot.capture(job, host.getInventory().list); }
        boolean install() { return updater.install(job, snapshot(), candidate); }
    }

    private static CraftingPlan plan(AEKey goal, long amount, Map<IPatternDetails, Long> tasks, KeyCounter stock) {
        return new CraftingPlan(new GenericStack(goal, amount), 128, false, false,
                stock, new KeyCounter(), new KeyCounter(), tasks);
    }

    private static ECOPlanningResult result(AEKey goal, long amount, IPatternDetails pattern, long times, KeyCounter stock) {
        var component = new ComponentPlanningResult(0, ComponentPlanningResult.Type.ACYCLIC,
                ComponentPlanningResult.Status.PLANNED, Map.of(goal, amount), Set.of(pattern),
                null, null, Map.of(), null, null);
        return new ECOPlanningResult(PlanningStatus.SUCCESS, plan(goal, amount, Map.of(pattern, times), stock),
                new ECOPlanTrace(), List.of(), List.of(component), List.of(0), 0);
    }

    private static KeyCounter stock(Object... entries) {
        var result = new KeyCounter();
        for (int i = 0; i < entries.length; i += 2) result.add((AEKey) entries[i], ((Number) entries[i + 1]).longValue());
        return result;
    }

    private static IPatternDetails pattern(net.minecraft.world.item.Item definition, List<GenericStack> inputs, GenericStack... outputs) {
        var pattern = mock(IPatternDetails.class);
        when(pattern.getDefinition()).thenReturn(AEItemKey.of(definition));
        var rawInputs = inputs.stream().map(stack -> {
            var input = mock(IPatternDetails.IInput.class);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[]{stack});
            when(input.getMultiplier()).thenReturn(1L);
            when(input.isValid(any(), any())).thenAnswer(call -> stack.what().equals(call.getArgument(0)));
            return input;
        }).toArray(IPatternDetails.IInput[]::new);
        when(pattern.getInputs()).thenReturn(rawInputs);
        when(pattern.getOutputs()).thenReturn(List.of(outputs));
        when(pattern.getPrimaryOutput()).thenReturn(outputs[0]);
        return pattern;
    }
}
