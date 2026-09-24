package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;

class ECOExecutionRuntimeTest {

    @Test
    void sharedProgressUnlocksDependentPhaseAfterAcceptedDispatch() {
        var fixture = fixture();
        var candidates = fixture.runtime.candidates();
        assertEquals(1, candidates.size());
        fixture.shared.value = 0;
        fixture.runtime.onAccepted(candidates.get(0), 1, null);
        var next = fixture.runtime.candidates();
        assertEquals(1, next.size());
        assertEquals(2, next.get(0).taskId());
        fixture.last.value = 0;
        fixture.runtime.onAccepted(next.get(0), 1, null);
        assertTrue(fixture.runtime.isComplete());
    }

    @Test
    void emptyCandidatesReconcileExternallyCompletedProgress() {
        var fixture = fixture();
        fixture.shared.value = 0;
        var candidates = fixture.runtime.candidates();
        assertEquals(1, candidates.size());
        assertEquals(2, candidates.get(0).taskId());
    }

    @Test
    void dynamicFiringsKeepLongCountsAcrossPersistence() {
        IPatternDetails pattern = pattern();
        var identity = new PlanIdentity.PatternIdentity(PlanIdentity.Kind.OBJECT, pattern);
        var task = new ECOExecutionPlan.TaskSpec(
                0,
                identity,
                pattern,
                new ECOExecutionPlan.PatternRuntimeInfo(null, 0, List.of()),
                3_000_000_000L,
                0,
                ECOExecutionPlan.TaskKind.CYCLE_DYNAMIC);
        var phase = new ECOExecutionPlan.PhaseSpec(
                0,
                0,
                ECOExecutionSchedule.Type.DYNAMIC_CYCLE,
                List.of(0),
                List.of(),
                List.of(),
                Map.of(0, 3_000_000_000L),
                Map.of());
        var signature = new PlanIdentity.Signature(
                new TestKey(), 1, Map.of(identity, 3_000_000_000L), Map.of(), Map.of(), Map.of());
        var plan = new ECOExecutionPlan(
                signature,
                ExecutionMode.DYNAMIC_CYCLE,
                List.of(task),
                List.of(phase),
                new ECOExecutionSchedule(List.of()));
        var progress = new ExecutingCraftingJob.TaskProgress();
        progress.value = 3_000_000_000L;
        var runtime =
                new ECOExecutionRuntime(plan, Map.of(0, pattern), new ExecutingCraftingJob.TaskProgress[] {progress});

        var candidate = runtime.candidates().get(0);
        assertEquals(3_000_000_000L, candidate.maxDispatchCount());
        progress.value = 2_000_000_000L;
        runtime.onAccepted(candidate, 1_000_000_000L, null);

        var saved = new net.minecraft.nbt.CompoundTag();
        runtime.writeToNBT(saved, null);
        var restored = ECOExecutionRuntime.fromNBT(
                plan, Map.of(0, pattern), new ExecutingCraftingJob.TaskProgress[] {progress}, saved, null);
        assertEquals(2_000_000_000L, restored.candidates().get(0).maxDispatchCount());
    }

    private Fixture fixture() {
        IPatternDetails pattern = pattern();
        var identity = new PlanIdentity.PatternIdentity(PlanIdentity.Kind.OBJECT, pattern);
        var info = new ECOExecutionPlan.PatternRuntimeInfo(null, 0, List.of());
        var tasks = new ArrayList<ECOExecutionPlan.TaskSpec>();
        var phases = new ArrayList<ECOExecutionPlan.PhaseSpec>();
        for (int i = 0; i < 3; i++) {
            tasks.add(new ECOExecutionPlan.TaskSpec(i, identity, pattern, info, 1, i, ECOExecutionPlan.TaskKind.DAG));
            phases.add(new ECOExecutionPlan.PhaseSpec(
                    i, i, ECOExecutionSchedule.Type.DAG, List.of(i), List.of(), i == 0 ? List.of() : List.of(i - 1)));
        }
        var signature =
                new PlanIdentity.Signature(new TestKey(), 1, Map.of(identity, 3L), Map.of(), Map.of(), Map.of());
        var plan = new ECOExecutionPlan(
                signature, ExecutionMode.PHASED_DAG, tasks, phases, new ECOExecutionSchedule(List.of()));
        var shared = new ExecutingCraftingJob.TaskProgress();
        shared.value = 1;
        var last = new ExecutingCraftingJob.TaskProgress();
        last.value = 1;
        return new Fixture(
                new ECOExecutionRuntime(
                        plan,
                        Map.of(0, pattern, 1, pattern, 2, pattern),
                        new ExecutingCraftingJob.TaskProgress[] {shared, shared, last}),
                shared,
                last);
    }

    private static IPatternDetails pattern() {
        return (IPatternDetails) Proxy.newProxyInstance(
                IPatternDetails.class.getClassLoader(),
                new Class<?>[] {IPatternDetails.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getInputs" -> new IPatternDetails.IInput[0];
                    case "getOutputs" -> new GenericStack[0];
                    case "getDefinition" -> null;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static final class TestKey extends appeng.api.stacks.AEKey {
        @Override
        public appeng.api.stacks.AEKeyType getType() {
            return null;
        }

        @Override
        public appeng.api.stacks.AEKey dropSecondary() {
            return this;
        }

        @Override
        public net.minecraft.nbt.CompoundTag toTag() {
            return new net.minecraft.nbt.CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public net.minecraft.resources.ResourceLocation getId() {
            return null;
        }

        @Override
        public void writeToPacket(net.minecraft.network.FriendlyByteBuf buf) {}

        @Override
        protected net.minecraft.network.chat.Component computeDisplayName() {
            return net.minecraft.network.chat.Component.literal("test");
        }

        @Override
        public void addDrops(
                long amount,
                List<net.minecraft.world.item.ItemStack> drops,
                net.minecraft.world.level.Level level,
                net.minecraft.core.BlockPos pos) {}
    }

    private record Fixture(
            ECOExecutionRuntime runtime,
            ExecutingCraftingJob.TaskProgress shared,
            ExecutingCraftingJob.TaskProgress last) {}
}
