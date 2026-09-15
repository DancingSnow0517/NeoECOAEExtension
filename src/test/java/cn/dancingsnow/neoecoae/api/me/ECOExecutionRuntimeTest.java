package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.*;
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

    private Fixture fixture() {
        IPatternDetails pattern = (IPatternDetails) Proxy.newProxyInstance(
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
