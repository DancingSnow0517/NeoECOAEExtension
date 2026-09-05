package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.RuntimeExecutionState;
import org.junit.jupiter.api.Test;

class ECOCraftingStatusChangesTest {
    private final ECOCraftingTestKey first = new ECOCraftingTestKey("first");
    private final ECOCraftingTestKey variant = new ECOCraftingTestKey("variant");

    @Test
    void runtimeAndDirtyStateFlushBeforeDeduplicatedNotificationsIncludingNewResources() {
        var events = new ArrayList<Object>();
        var changes = changes(events);
        var runtime = runtime();
        runtime.acceptOutput(first, 1L);
        changes.beginBatch(runtime);
        changes.postChange(first);
        changes.postChange(first);
        runtime.acceptOutput(variant, 1L);
        changes.postChange(variant);
        changes.postChange(variant);
        changes.markDirty();
        changes.markDirty();
        assertEquals(List.of(), events);

        changes.endBatch(() -> events.add("runtime"));

        assertEquals(List.of("runtime", "dirty", "modified", first, variant), events);
        assertFalse(changes.isBatching());
    }

    @Test
    void fullInvalidationSupersedesQueuedKeysAndDoesNotLeakIntoTheNextPass() {
        var events = new ArrayList<Object>();
        var changes = changes(events);
        changes.beginBatch(null);
        changes.postChange(first);
        changes.postChange(null);
        changes.postChange(variant);
        changes.endBatch(() -> {});
        assertEquals(Arrays.asList("modified", null), events);

        events.clear();
        changes.beginBatch(null);
        changes.postChange(variant);
        changes.endBatch(() -> {});
        assertEquals(List.of("modified", variant), events);
    }

    @Test
    void finishingAJobStillDeliversItsQueuedResourceNotifications() {
        var events = new ArrayList<Object>();
        var changes = changes(events);
        var runtime = runtime();
        runtime.acceptOutput(first, 1L);
        changes.beginBatch(runtime);
        changes.postChange(first);
        changes.markDirty();

        changes.endBatch(null);

        assertEquals(List.of("dirty", "modified", first), events);
        events.clear();
        changes.postChange(variant);
        assertEquals(List.of("modified", variant), events);
    }

    @Test
    void listenerExceptionsAndErrorsResetBatchingBeforeEscaping() {
        for (Throwable failure : List.of(new IllegalStateException("listener"), new AssertionError("listener"))) {
            var keys = new ArrayList<AEKey>();
            boolean[] fail = { true };
            var changes = new ECOCraftingStatusChanges(key -> {
                if (fail[0]) {
                    if (failure instanceof Error error) throw error;
                    throw (RuntimeException) failure;
                }
                keys.add(key);
            }, () -> {}, () -> {});
            changes.beginBatch(null);
            changes.postChange(first);

            assertSame(failure, assertThrows(failure.getClass(), () -> changes.endBatch(() -> {})));
            assertFalse(changes.isBatching());
            fail[0] = false;
            changes.postChange(variant);
            changes.beginBatch(null);
            changes.endBatch(() -> {});
            assertEquals(List.of(variant), keys);
        }
    }

    @Test
    void anEmptyPassFlushesRuntimeWithoutNotifyingOrMarkingDirty() {
        var events = new ArrayList<Object>();
        var changes = changes(events);
        changes.beginBatch(null);
        changes.endBatch(() -> events.add("runtime"));
        assertEquals(List.of("runtime"), events);
    }

    private ECOCraftingStatusChanges changes(List<Object> events) {
        return new ECOCraftingStatusChanges(events::add, () -> events.add("modified"), () -> events.add("dirty"));
    }

    private RuntimeExecutionState runtime() {
        var signature = new PlanIdentity.Signature(first, 1L, Map.of(), Map.of(), Map.of(), Map.of());
        return new RuntimeExecutionState(new ECOExecutionPlan(signature, ExecutionMode.PHASED_DAG,
            List.of(), List.of(), new ECOExecutionSchedule(List.of())));
    }
}
