package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.RuntimeExecutionState;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ECOCraftingCPULogicTest {
    @Test
    void batchThenOrdinaryAcceptanceDebitsEachCraftOnce() throws Exception {
        var job = jobWithRemainingAmount(1L);
        var progress = new ExecutingCraftingJob.TaskProgress();
        progress.value = 10L;
        var task = new ExecutingCraftingJob.DispatchTask(-1, pattern(), progress);
        job.applyAccepted(task, 8L);
        job.applyAccepted(task, 1L);
        job.flushRuntimeTick();
        assertEquals(1L, progress.value);
        assertThrows(IllegalArgumentException.class, () -> job.applyAccepted(task, 2L));
        assertEquals(1L, progress.value);
    }

    @Test
    void phasedAcceptanceAndFlushDoNotDebitCompatibilityProjectionTwice() throws Exception {
        var job = jobWithRemainingAmount(1L);
        var plan = executionPlan();
        job.runtimeExecutionState = new RuntimeExecutionState(plan);
        job.executionSchedule = plan.schedule();
        var task = job.eligibleDispatchTasks().get(0);
        job.applyAccepted(task, 8L);
        job.applyAccepted(task, 1L);
        job.flushRuntimeTick();
        job.flushRuntimeTick();
        assertEquals(1L, task.progress().value);
        assertEquals(1L, job.runtimeExecutionState.remaining(0));
    }

    @Test
    void restoredCursorMustExactlyMatchRemainingTasks() {
        var state = new RuntimeExecutionState(executionPlan());
        state.restore(new long[] {3}, new int[] {0}, new long[] {3});
        assertEquals(3L, state.dispatchLimit(0));
        assertThrows(
                IllegalArgumentException.class, () -> state.restore(new long[] {3}, new int[] {0}, new long[] {4}));
        assertThrows(
                IllegalArgumentException.class, () -> state.restore(new long[] {3}, new int[] {1}, new long[] {0}));
    }

    @Test
    void recoveryIdentityRejectsChangedCountsAndInputs() {
        var pattern = pattern();
        var left = new CraftingPlan(
                new GenericStack(TestKey.INSTANCE, 1),
                0,
                false,
                false,
                new KeyCounter(),
                new KeyCounter(),
                new KeyCounter(),
                Map.of(pattern, 10L));
        var changedCount = new CraftingPlan(
                left.finalOutput(),
                0,
                false,
                false,
                new KeyCounter(),
                new KeyCounter(),
                new KeyCounter(),
                Map.of(pattern, 9L));
        var used = new KeyCounter();
        used.add(TestKey.INSTANCE, 1L);
        var changedInputs = new CraftingPlan(
                left.finalOutput(), 0, false, false, used, new KeyCounter(), new KeyCounter(), left.patternTimes());
        assertFalse(PlanIdentity.matches(left, changedCount));
        assertFalse(PlanIdentity.matches(left, changedInputs));
    }

    @Test
    void unsuccessfulResultsNeverProduceExecutableContracts() {
        var plan = new CraftingPlan(
                new GenericStack(TestKey.INSTANCE, 1),
                0,
                false,
                false,
                new KeyCounter(),
                new KeyCounter(),
                new KeyCounter(),
                Map.of());
        for (var status : PlanningStatus.values()) {
            var result = new ECOPlanningResult(status, plan, null, List.of(), 0L);
            assertEquals(
                    status != PlanningStatus.SUCCESS,
                    result.executionContract().mode() == ExecutionMode.BLOCKED,
                    status.name());
        }
    }

    @Test
    void simulatedSubmissionStopsBeforeAccessingGridOrCpu() {
        var plan = new CraftingPlan(
                new GenericStack(TestKey.INSTANCE, 1),
                0,
                true,
                false,
                new KeyCounter(),
                new KeyCounter(),
                new KeyCounter(),
                Map.of());
        var logic = testLogic();
        assertEquals(
                appeng.crafting.execution.CraftingSubmitResult.INCOMPLETE_PLAN,
                logic.trySubmitJob(null, plan, null, null));
        assertNull(logic.getJob());
    }

    @Test
    void copiedFailedPlanRemainsBlockedAfterMetadataRecovery() {
        var plan = new CraftingPlan(
                new GenericStack(TestKey.INSTANCE, 73),
                0,
                false,
                false,
                new KeyCounter(),
                new KeyCounter(),
                new KeyCounter(),
                Map.of());
        var failed = new ECOPlanningResult(PlanningStatus.INTERNAL_ERROR, plan, null, List.of(), 0L);
        ECOPlanningResultRegistry.register(plan, failed);
        var copy = new CraftingPlan(
                plan.finalOutput(), 0, false, false, new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
        var job = new ExecutingCraftingJob(copy, ignored -> {}, null, null);
        assertTrue(job.hasPermanentExecutionError());
        assertEquals(ExecutionMode.BLOCKED, job.executionMode);
    }

    @Test
    void recoveredPermanentErrorStopsBeforeAccessingProviders() throws Exception {
        var logic = testLogic();
        var job = jobWithRemainingAmount(1L);
        job.applyDispatchResult(-1, new DispatchResult.Fatal("recovery mismatch"));
        setJob(logic, job);
        assertEquals(0, logic.executeCrafting(10, null, null, null));
    }

    private static IPatternDetails pattern() {
        return (IPatternDetails) java.lang.reflect.Proxy.newProxyInstance(
                IPatternDetails.class.getClassLoader(),
                new Class<?>[] {IPatternDetails.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDefinition" -> null;
                    case "getInputs" -> new IPatternDetails.IInput[0];
                    case "getOutputs" -> new GenericStack[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static ECOExecutionPlan executionPlan() {
        var pattern = pattern();
        var signature = new PlanIdentity.Signature(
                TestKey.INSTANCE, 1L, PlanIdentity.taskSignature(Map.of(pattern, 10L)), Map.of(), Map.of(), Map.of());
        var task = new ECOExecutionPlan.TaskSpec(
                0,
                PlanIdentity.patternIdentityFor(pattern),
                pattern,
                ECOExecutionPlan.PatternRuntimeInfo.from(pattern),
                10L,
                0,
                ECOExecutionPlan.TaskKind.CYCLE_ORDERED);
        var phase = new ECOExecutionPlan.PhaseSpec(
                0,
                0,
                ECOExecutionSchedule.Type.CYCLE,
                List.of(0),
                List.of(new ECOExecutionPlan.ExecutionStep(0, 10)),
                List.of());
        return new ECOExecutionPlan(
                signature,
                ExecutionMode.ORDERED_CYCLE,
                List.of(task),
                List.of(phase),
                new ECOExecutionSchedule(List.of()));
    }

    @Test
    void batchRequestPreservesTheRemainingTaskAmount() {
        assertEquals(512L, ECOCraftingCPULogic.calculateBatchRequestSize(512L));
        assertEquals(0L, ECOCraftingCPULogic.calculateBatchRequestSize(-1L));
        assertEquals(Long.MAX_VALUE, ECOCraftingCPULogic.calculateBatchRequestSize(Long.MAX_VALUE));
    }

    @Test
    void slowPathOperationLimitIsBoundedByCoprocessorsAndConfiguration() {
        assertEquals(1, ECOCraftingCPULogic.calculateOperationLimit(-1, 64));
        assertEquals(5, ECOCraftingCPULogic.calculateOperationLimit(4, 64));
        assertEquals(3, ECOCraftingCPULogic.calculateOperationLimit(64, 3));
        assertEquals(0, ECOCraftingCPULogic.calculateOperationLimit(64, -1));
    }

    @Test
    void plannedInputsAreOnlyUsedBySlowPathDispatch() {
        assertTrue(ECOCraftingCPULogic.shouldUsePlannedInputsForDispatch(false, true, 8L, 8L));
        assertFalse(ECOCraftingCPULogic.shouldUsePlannedInputsForDispatch(true, true, 8L, 8L));
        assertFalse(ECOCraftingCPULogic.shouldUsePlannedInputsForDispatch(false, false, 8L, 8L));
        assertFalse(ECOCraftingCPULogic.shouldUsePlannedInputsForDispatch(false, true, 9L, 8L));
    }

    @Test
    void scaledPatternAmountSaturatesWithoutTurningNegative() {
        assertEquals(0L, ECOCraftingCPULogic.scaledPatternAmount(0L, 8L));
        assertEquals(8L, ECOCraftingCPULogic.scaledPatternAmount(4L, 2L));
        assertEquals(4L, ECOCraftingCPULogic.scaledPatternAmount(4L, 0L));
        assertEquals(Long.MAX_VALUE, ECOCraftingCPULogic.scaledPatternAmount(Long.MAX_VALUE, 2L));
    }

    @Test
    void remainingJobOutputAmountExposesTheCurrentJobAmount() throws Exception {
        ECOCraftingCPULogic logic = testLogic();

        assertEquals(0L, logic.getRemainingJobOutputAmount());

        setJob(logic, jobWithRemainingAmount(42L));
        assertEquals(42L, logic.getRemainingJobOutputAmount());

        setJob(logic, jobWithRemainingAmount(-7L));
        assertEquals(-7L, logic.getRemainingJobOutputAmount());
    }

    @Test
    void userPauseIsSeparateFromInternalSuspension() throws Exception {
        ECOCraftingCPULogic logic = testLogic();
        setJob(logic, jobWithRemainingAmount(1L));

        assertFalse(logic.isJobUserPaused());
        assertFalse(logic.isJobSuspended());

        logic.setJobUserPaused(true);
        assertTrue(logic.isJobUserPaused());
        assertFalse(logic.isJobSuspended());

        logic.toggleJobUserPaused();
        assertFalse(logic.isJobUserPaused());
        assertFalse(logic.isJobSuspended());
    }

    private static ExecutingCraftingJob jobWithRemainingAmount(long remainingAmount) throws Exception {
        CraftingLink link = new CraftingLink(
                CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false), (ICraftingCPU) null);
        ExecutingCraftingJob job = new ExecutingCraftingJob(new TestCraftingPlan(), ignored -> {}, link, null);
        job.remainingAmount = remainingAmount;
        return job;
    }

    private static ECOCraftingCPULogic testLogic() {
        return new ECOCraftingCPU(null, 0L, null).getLogic();
    }

    private static void setJob(ECOCraftingCPULogic logic, ExecutingCraftingJob job) throws Exception {
        Field field = ECOCraftingCPULogic.class.getDeclaredField("job");
        field.setAccessible(true);
        field.set(logic, job);
    }

    private static final class TestCraftingPlan implements ICraftingPlan {
        @Override
        public GenericStack finalOutput() {
            return new GenericStack(TestKey.INSTANCE, 1);
        }

        @Override
        public long bytes() {
            return 0;
        }

        @Override
        public boolean simulation() {
            return false;
        }

        @Override
        public boolean multiplePaths() {
            return false;
        }

        @Override
        public KeyCounter usedItems() {
            return new KeyCounter();
        }

        @Override
        public KeyCounter emittedItems() {
            return new KeyCounter();
        }

        @Override
        public KeyCounter missingItems() {
            return new KeyCounter();
        }

        @Override
        public Map<IPatternDetails, Long> patternTimes() {
            return Map.of();
        }
    }

    private static final class TestKey extends AEKey {
        private static final TestKey INSTANCE = new TestKey();
        private static final TestKeyType TYPE = new TestKeyType();
        private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("neoecoae", "test");

        @Override
        public AEKeyType getType() {
            return TYPE;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag() {
            return new CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        public void writeToPacket(FriendlyByteBuf data) {}

        @Override
        protected Component computeDisplayName() {
            return Component.literal("test");
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}
    }

    private static final class TestKeyType extends AEKeyType {
        private TestKeyType() {
            super(ResourceLocation.fromNamespaceAndPath("neoecoae", "test"), TestKey.class, Component.literal("test"));
        }

        @Override
        public AEKey readFromPacket(FriendlyByteBuf input) {
            return TestKey.INSTANCE;
        }

        @Override
        public AEKey loadKeyFromTag(CompoundTag tag) {
            return TestKey.INSTANCE;
        }
    }
}
