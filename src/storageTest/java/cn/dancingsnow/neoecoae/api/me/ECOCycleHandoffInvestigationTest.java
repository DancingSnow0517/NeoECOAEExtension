package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCraftingPlannerService;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.storage.StorageTestKey;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Investigation characterizations: the stalled-state assertions document defects, not desired behavior. */
class ECOCycleHandoffInvestigationTest {
    private static final AEKey A = new StorageTestKey("handoff_a");
    private static final AEKey B = new StorageTestKey("handoff_b");
    private static final AEKey C = new StorageTestKey("handoff_c");
    private static final AEKey GOAL = new StorageTestKey("handoff_goal");
    private static Object previousTypes;
    private static Level level;

    @BeforeAll static void initialize() throws Exception {
        var field = AEKeyTypesInternal.class.getDeclaredField("allTypes");
        field.setAccessible(true);
        previousTypes = field.get(null);
        field.set(null, Set.of(A.getType()));
        // Static synthetic patterns do not use world methods; the CPU still requires a non-null Level.
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var writer = new org.objectweb.asm.ClassWriter(0);
        writer.visit(org.objectweb.asm.Opcodes.V21, org.objectweb.asm.Opcodes.ACC_PUBLIC,
            "cn/dancingsnow/neoecoae/api/me/CycleInvestigationLevel", null,
            "net/minecraft/world/level/Level", null);
        writer.visitEnd();
        var levelClass = java.lang.invoke.MethodHandles.lookup().defineHiddenClass(writer.toByteArray(), false)
            .lookupClass();
        level = (Level) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(levelClass);
    }

    @AfterAll static void restoreTypes() throws Exception {
        var field = AEKeyTypesInternal.class.getDeclaredField("allTypes");
        field.setAccessible(true);
        field.set(null, previousTypes);
    }

    @Test void finalOutputDeliveryCurrentlySpendsTheInitialTwoStepCycleSeed() throws Exception {
        var toB = pattern(0, "a_to_b", B, 1, A, 1L);
        var toA = pattern(1, "b_to_two_a", A, 2, B, 1L);
        var result = plan(A, 1, Map.of(A, 1L), toB, toA);
        assertEquals(Map.of(toB, 1L, toA, 1L), result.plan().patternTimes());
        assertEquals(ExecutionMode.ORDERED_CYCLE, result.executionPlan().mode());
        assertEquals(Map.of(A, 1L), result.executionPlan().phases().getFirst().initialSeed());

        var fixture = new Fixture(result);
        fixture.tick();
        assertEquals(1, fixture.network.get(A), "The seed was delivered as the requested product");
        assertEquals(0, fixture.logic.getRemainingJobOutputAmount());
        assertEquals(0, fixture.accepted.size(), "No recipe ran before the seed was delivered");
        assertStalled(fixture, 2);
        assertCompletesInPlannerOrder(result);
    }

    @Test void finalOutputDeliveryCurrentlySpendsFeedbackReturnedHalfwayThroughTheCycle() throws Exception {
        var toB = pattern(0, "feedback_a_to_b", B, 1, A, 1L);
        var toA = pattern(1, "feedback_b_to_two_a", A, 2, B, 1L);
        var result = plan(B, 2, Map.of(A, 1L), toB, toA);
        assertEquals(Map.of(toB, 3L, toA, 1L), result.plan().patternTimes());

        var fixture = new Fixture(result);
        fixture.tick();
        assertEquals(1, fixture.logic.getWaitingFor(B));
        fixture.returnOutputs();
        fixture.tick();
        assertEquals(1, fixture.network.get(B));
        assertEquals(1, fixture.logic.getRemainingJobOutputAmount());
        assertEquals(List.of(toB), fixture.accepted);
        assertStalled(fixture, 3);
        assertCompletesInPlannerOrder(result);
    }

    @Test void downstreamCurrentlyConsumesGrowthSeedWhenItsProducerIsTemporarilyBusy() throws Exception {
        var grow = pattern(0, "downstream_grow", A, 2, A, 1L);
        var consume = pattern(1, "downstream_consume", GOAL, 1, A, 1L);
        var result = plan(GOAL, 2, Map.of(A, 1L), grow, consume);
        var phases = result.executionPlan().phases();
        assertEquals(2, phases.size());
        assertEquals(List.of(0), phases.get(1).dependencies());

        var fixture = new Fixture(result);
        fixture.blocked = grow;
        fixture.tick();
        assertEquals(List.of(consume), fixture.accepted);
        fixture.returnOutputs();
        fixture.blocked = null;
        fixture.tick();
        assertEquals(1, fixture.network.get(GOAL));
        assertEquals(0, fixture.logic.getStored(A));
        long remaining = result.plan().patternTimes().values().stream().mapToLong(Long::longValue).sum() - 1;
        assertStalled(fixture, remaining);
        assertCompletesInPlannerOrder(result);
    }

    @Test void dynamicCycleCurrentlySpendsBothSeedsOnOneBranchAndCannotFireItsJoin() throws Exception {
        assertSplitCycleStalls(false);
    }

    @Test void batchDispatchCurrentlySpendsBothCycleSeedsInOneAcceptedBatch() throws Exception {
        assertSplitCycleStalls(true);
    }

    private static void assertSplitCycleStalls(boolean batchMode) throws Exception {
        var left = pattern(0, "split_left", B, 1, A, 1L);
        var right = pattern(1, "split_right", C, 1, A, 1L);
        var join = pattern(2, "join", A, 3, B, 1L, C, 1L);
        var consume = pattern(3, "consume_join", GOAL, 1, A, 3L);
        var result = plan(GOAL, 2, Map.of(A, 2L), left, right, join, consume);
        assertEquals(ExecutionMode.DYNAMIC_CYCLE, result.executionPlan().mode());
        assertTrue(result.plan().patternTimes().get(left) >= 2);

        var fixture = new Fixture(result, batchMode);
        assertSame(left, fixture.logic.getJob().tasks.keySet().iterator().next());
        fixture.tick();
        assertEquals(List.of(left, left), fixture.accepted);
        assertEquals(batchMode ? List.of(2L) : List.of(1L, 1L), fixture.acceptedBatches);
        assertEquals(2, fixture.logic.getWaitingFor(B));
        fixture.returnOutputs();
        assertEquals(2, fixture.logic.getStored(B));
        assertEquals(0, fixture.logic.getStored(A));
        assertEquals(0, fixture.logic.getStored(C));
        long remaining = result.plan().patternTimes().values().stream().mapToLong(Long::longValue).sum() - 2;
        assertStalled(fixture, remaining);
        assertCompletesInPlannerOrder(result);
    }

    @Test void validDynamicCycleMetadataIsCurrentlyRegisteredAsMissingCyclePhase() throws Exception {
        var first = pattern(0, "metadata_a_to_b", B, 2, A, 1L);
        var second = pattern(1, "metadata_b_to_c", C, 1, B, 1L);
        var third = pattern(2, "metadata_c_to_a", A, 1, C, 1L);
        var result = plan(A, 1, Map.of(A, 1L), first, second, third);
        assertEquals(ExecutionMode.DYNAMIC_CYCLE, result.executionPlan().mode());
        assertNull(result.executionPlanError());
        var recovered = ECOPlanningResultRegistry.recoverExecutionMetadata(result.plan());
        assertNotNull(recovered);
        assertNotNull(recovered.executionPlan());
        assertEquals(ECOPlanningResultRegistry.RecoveryState.MISSING_OR_INVALID_SCHEDULE, recovered.state());
        assertEquals("NO_CYCLE_PHASE", recovered.rejectionReason());
    }

    private static ECOPlanningResult plan(AEKey goal, long amount, Map<AEKey, Long> stock,
            Pattern... patterns) throws Exception {
        var available = new KeyCounter();
        stock.forEach(available::add);
        var service = proxy(ICraftingService.class, (name, args) -> switch (name) {
            case "canEmitFor" -> false;
            case "getCraftingFor" -> List.of(patterns).stream()
                .filter(pattern -> pattern.getPrimaryOutput().what().equals(args[0])).toList();
            default -> throw new AssertionError("Unexpected planner service call: " + name);
        });
        var result = new ECOCraftingPlannerService().createSession(service, goal, available, true)
            .plan(amount, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, result.status(), () -> "Planner diagnostic: " + result.trace().diagnostics());
        assertNotNull(result.plan());
        assertFalse(result.plan().simulation());
        assertTrue(result.plan().missingItems().isEmpty());
        assertNull(result.executionPlanError());
        System.out.println("Handoff plan: goal=" + goal.getId() + " amount=" + amount
            + " mode=" + result.executionPlan().mode() + " tasks=" + result.plan().patternTimes()
            + " used=" + amounts(result.plan().usedItems()));
        return result;
    }

    private static void assertStalled(Fixture fixture, long remainingTasks) {
        int accepted = fixture.accepted.size();
        for (int tick = 0; tick < 20; tick++) {
            fixture.tick();
            fixture.returnOutputs();
        }
        assertTrue(fixture.logic.hasJob());
        assertEquals(accepted, fixture.accepted.size());
        assertEquals(remainingTasks, fixture.logic.getJob().tasks.values().stream().mapToLong(t -> t.value).sum());
        assertTrue(fixture.logic.getJob().waitingFor.list.isEmpty());
        assertTrue(fixture.logic.getJob().tasks.entrySet().stream().filter(task -> task.getValue().value > 0)
            .noneMatch(task -> fixture.canFire(task.getKey())), "No future return or enabled firing can restart the loop");
        assertNull(fixture.logic.getPermanentExecutionError(), "The stalled CPU provides no failure diagnostic");
        System.out.println("Stalled CPU: remainingGoal=" + fixture.logic.getRemainingJobOutputAmount()
            + " remainingFirings=" + remainingTasks + " stock=" + amounts(fixture.logic.getInventory().list)
            + " waiting=" + amounts(fixture.logic.getJob().waitingFor.list)
            + " dispatched=" + fixture.accepted + " batches=" + fixture.acceptedBatches);
    }

    private static void assertCompletesInPlannerOrder(ECOPlanningResult result) {
        var control = new Fixture(result);
        for (var phase : result.executionPlan().phases()) {
            if (phase.type() != ECOExecutionSchedule.Type.DAG) {
                var component = result.components().stream()
                    .filter(candidate -> candidate.componentId() == phase.componentId()).findFirst().orElseThrow();
                for (var run : component.cycleResult().executionPlan()) {
                    control.dispatchOnly(run.details(), run.count());
                }
            }
            for (int taskId : phase.taskIds()) {
                var pattern = result.executionPlan().task(taskId).pattern();
                var progress = control.logic.getJob().tasks.get(pattern);
                if (progress != null && progress.value > 0) control.dispatchOnly(pattern, progress.value);
            }
        }
        control.tick();
        assertFalse(control.logic.hasJob(), "The very same counts complete when the planner order is respected");
        assertTrue(control.network.get(result.plan().finalOutput().what()) >= result.plan().finalOutput().amount());
    }

    private static final class Fixture {
        final KeyCounter network = new KeyCounter();
        final KeyCounter returning = new KeyCounter();
        final List<IPatternDetails> accepted = new ArrayList<>();
        final List<Long> acceptedBatches = new ArrayList<>();
        final Map<IPatternDetails, ICraftingProvider> providers = new LinkedHashMap<>();
        final boolean batchMode;
        IPatternDetails blocked;
        IPatternDetails only;
        final IActionSource source = proxy(IActionSource.class, (name, args) -> Optional.empty());
        final MEStorage storage = proxy(MEStorage.class, (name, args) -> switch (name) {
            case "extract" -> {
                long extracted = Math.min(network.get((AEKey) args[0]), (long) args[1]);
                if (args[2] == Actionable.MODULATE) network.remove((AEKey) args[0], extracted);
                yield extracted;
            }
            case "insert" -> {
                if (args[2] == Actionable.MODULATE) network.add((AEKey) args[0], (long) args[1]);
                yield args[1];
            }
            default -> throw new AssertionError("Unexpected storage call: " + name);
        });
        final IStorageService storageService = proxy(IStorageService.class, (name, args) -> storage);
        final IEnergyService energy = proxy(IEnergyService.class, (name, args) ->
            name.equals("extractAEPower") ? args[0] : null);
        final CraftingService crafting = new CraftingService(null, storageService, energy) {
            @Override public Iterable<ICraftingProvider> getProviders(IPatternDetails pattern) {
                return List.of(providers.computeIfAbsent(pattern,
                    selected -> batchMode ? new BatchProvider(selected) : new Provider(selected)));
            }
        };
        final IGrid grid = proxy(IGrid.class, (name, args) -> switch (name) {
            case "getStorageService" -> storageService;
            case "getCraftingService" -> crafting;
            case "getMachines" -> Set.of();
            default -> throw new AssertionError("Unexpected grid call: " + name);
        });
        final ECOCraftingCPU cpu = new ECOCraftingCPU(null, (IECOTier) null) {
            @Override public void markDirty() { }
            @Override public IGrid getGrid() { return grid; }
            @Override public IActionSource getActionSource() { return source; }
            @Override public boolean isActive() { return true; }
            @Override public long getAvailableStorage() { return Long.MAX_VALUE; }
            @Override public Level getLevel() { return level; }
            @Override public int getCoProcessors() { return 100; }
        };
        final ECOCraftingCPULogic logic = cpu.getLogic();

        Fixture(ECOPlanningResult result) {
            this(result, false);
        }

        Fixture(ECOPlanningResult result, boolean batchMode) {
            this.batchMode = batchMode;
            network.addAll(result.plan().usedItems());
            var submission = ECOPlanningResultRegistry.withSubmissionAlias(result.plan(), result, () -> {
                assertNotNull(ECOPlanningResultRegistry.activeSubmissionMetadata(result.plan()));
                return logic.trySubmitJob(grid, result.plan(), source, null);
            });
            assertTrue(submission.successful());
        }

        private class Provider implements ICraftingProvider {
            final IPatternDetails selected;
            Provider(IPatternDetails selected) { this.selected = selected; }
            public List<IPatternDetails> getAvailablePatterns() { return List.of(selected); }
            public boolean isBusy() { return selected == blocked || (only != null && only != selected); }
            public boolean pushPattern(IPatternDetails pushed, KeyCounter[] inputs) {
                return accept(pushed, 1);
            }
            boolean accept(IPatternDetails pushed, long count) {
                assertSame(selected, pushed);
                acceptedBatches.add(count);
                for (long i = 0; i < count; i++) accepted.add(pushed);
                for (var output : pushed.getOutputs()) {
                    returning.add(output.what(), Math.multiplyExact(output.amount(), count));
                }
                return true;
            }
        }

        private final class BatchProvider extends Provider implements ECOBatchCapacityProvider {
            BatchProvider(IPatternDetails selected) { super(selected); }
            public long eco$getBatchCapacity(ECOBatchDispatchContext context) { return isBusy() ? 0 : 100; }
            public boolean eco$pushBatch(ECOBatchDispatchContext context, long count) {
                assertTrue(count > 0 && count <= 100);
                return accept(context.pattern(), count);
            }
        }

        void tick() {
            int previous = NEConfig.ecoCpuPushTickLimit;
            try {
                NEConfig.ecoCpuPushTickLimit = 100;
                logic.tickCraftingLogic(energy, crafting);
            } finally {
                NEConfig.ecoCpuPushTickLimit = previous;
            }
        }

        void returnOutputs() {
            for (var output : returning) {
                assertEquals(output.getLongValue(), logic.insert(output.getKey(), output.getLongValue(),
                    Actionable.MODULATE), "Every declared output comes back exactly once");
            }
            returning.reset();
        }

        boolean canFire(IPatternDetails pattern) {
            var needed = new KeyCounter();
            for (var input : pattern.getInputs()) {
                var stack = input.getPossibleInputs()[0];
                needed.add(stack.what(), Math.multiplyExact(stack.amount(), input.getMultiplier()));
            }
            for (var input : needed) if (logic.getStored(input.getKey()) < input.getLongValue()) return false;
            return true;
        }

        void dispatchOnly(IPatternDetails pattern, long count) {
            only = pattern;
            for (long i = 0; i < count; i++) {
                assertEquals(1, logic.executeCrafting(1, crafting, energy, level));
                returnOutputs();
            }
            only = null;
        }
    }

    private static Map<String, Long> amounts(KeyCounter counter) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (var entry : counter) if (entry.getLongValue() != 0) {
            result.put(entry.getKey().getId().toString(), entry.getLongValue());
        }
        return result;
    }

    private static Pattern pattern(int hash, String name, AEKey output, long amount, Object... inputs) {
        List<IPatternDetails.IInput> slots = new ArrayList<>();
        for (int i = 0; i < inputs.length; i += 2) slots.add(new Input((AEKey) inputs[i], (long) inputs[i + 1]));
        return new Pattern(hash, name, List.copyOf(slots), List.of(new GenericStack(output, amount)));
    }

    private record Input(AEKey key, long amount) implements IPatternDetails.IInput {
        public GenericStack[] getPossibleInputs() { return new GenericStack[] {new GenericStack(key, amount)}; }
        public long getMultiplier() { return 1; }
        public boolean isValid(AEKey candidate, Level level) { return key.equals(candidate); }
        public AEKey getRemainingKey(AEKey candidate) { return null; }
    }

    private record Pattern(int hash, String name, List<IPatternDetails.IInput> inputs,
            List<GenericStack> outputs) implements IPatternDetails {
        public AEItemKey getDefinition() { return null; }
        public IInput[] getInputs() { return inputs.toArray(IInput[]::new); }
        public List<GenericStack> getOutputs() { return outputs; }
        // Fix a legal HashMap order so seed contention is reproducible across JVM runs.
        @Override public int hashCode() { return hash; }
        @Override public String toString() { return name; }
    }

    @FunctionalInterface private interface Handler { Object call(String method, Object[] args); }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (instance, method, args) -> handler.call(method.getName(), args));
    }
}
