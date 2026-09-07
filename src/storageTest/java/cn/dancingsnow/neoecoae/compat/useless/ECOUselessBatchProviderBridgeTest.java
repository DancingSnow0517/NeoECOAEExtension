package cn.dancingsnow.neoecoae.compat.useless;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOBatchCraftingExecutor.PreparedBatch;
import cn.dancingsnow.neoecoae.impl.storage.StorageTestKey;
import java.util.List;
import org.junit.jupiter.api.Test;

class ECOUselessBatchProviderBridgeTest {
    private static final AEKey INPUT = new StorageTestKey("useless_batch_input");
    private static final IPatternDetails PATTERN = new IPatternDetails() {
        public appeng.api.stacks.AEItemKey getDefinition() { return null; }
        public IInput[] getInputs() { return new IInput[0]; }
        public List<GenericStack> getOutputs() { return List.of(); }
    };

    @Test void capacityIsLiveAndScaledInputsAreNotRetained() throws Exception {
        var target = new Dispatcher();
        var adapter = adapter(target);
        var context = context();
        assertEquals(400_000L, adapter.eco$getBatchCapacity(context));
        target.capacity = 3;
        assertEquals(3, adapter.eco$getBatchCapacity(context));
        assertFalse(adapter.eco$pushBatch(context, 4));
        assertEquals(0, target.pushes);
        assertTrue(adapter.eco$pushBatch(context, 3));
        assertEquals(1, target.pushes);
        assertEquals(3, target.accepted);
        assertEquals(2, context.inputCounters()[0].get(INPUT));
    }

    @Test void oneCommitTransfers400000CopiesWithOneCall() throws Exception {
        var target = new Dispatcher();
        var adapter = adapter(target);
        var inventory = new ListCraftingInventory(key -> {});
        inventory.insert(INPUT, 800_000L, Actionable.MODULATE);
        var prepared = batch(adapter, 400_000);
        assertTrue(prepared.push(inventory));
        assertEquals(1, target.pushes);
        assertEquals(400_000L, target.accepted);
        assertEquals(0, inventory.list.get(INPUT));
    }

    @Test void capacityLossRollsBackTheWholeExtraction() throws Exception {
        var target = new Dispatcher();
        var adapter = adapter(target);
        var prepared = batch(adapter, 400_000);
        var inventory = new ListCraftingInventory(key -> {});
        inventory.insert(INPUT, 800_000L, Actionable.MODULATE);
        target.capacity = 1;
        assertFalse(prepared.push(inventory));
        assertEquals(800_000L, inventory.list.get(INPUT));
        assertEquals(0, target.accepted);
    }

    @Test void reflectionPreservesRejectionExceptionsAndFullRollback() throws Exception {
        var target = new Dispatcher();
        target.failure = new IllegalStateException("Rejected before enqueue");
        var inventory = new ListCraftingInventory(key -> {});
        inventory.insert(INPUT, 800_000L, Actionable.MODULATE);
        var prepared = batch(adapter(target), 400_000);
        assertSame(target.failure, assertThrows(IllegalStateException.class, () -> prepared.push(inventory)));
        assertEquals(800_000L, inventory.list.get(INPUT));
        assertEquals(0, target.accepted);
    }

    @Test void missingOptionalApiKeepsOrdinaryProvidersEligibleForFallback() {
        var ordinary = new ICraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.of(PATTERN); }
            public boolean isBusy() { return false; }
            public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) { return true; }
        };
        assertFalse(ECOUselessBatchProviderBridge.supports(ordinary));
        assertNull(ECOUselessBatchProviderBridge.adapt(ordinary));
    }

    private static ECOUselessBatchProviderBridge.Adapter adapter(Dispatcher target) throws Exception {
        return new ECOUselessBatchProviderBridge.Adapter(
            ECOUselessBatchProviderBridge.ReflectionApi.resolve(Dispatcher.class), target);
    }

    private static ECOBatchDispatchContext context() {
        return new ECOBatchDispatchContext(PATTERN,
            List.of(List.of(new GenericStack(INPUT, 2))), List.of(), List.of(), null, null);
    }

    private static PreparedBatch batch(ECOUselessBatchProviderBridge.Adapter adapter, int count) {
        return new PreparedBatch(count, List.of(new GenericStack(INPUT, 2L * count)),
            List.of(), List.of(), 0, () -> adapter.eco$pushBatch(context(), count));
    }

    /** Fixture with the native Useless method signatures; no optional mod is required by this test JVM. */
    public static final class Dispatcher {
        long capacity = 400_000;
        long accepted;
        int pushes;
        RuntimeException failure;
        public static boolean supports(ICraftingProvider provider) { return provider != null; }
        public static Dispatcher forProvider(ICraftingProvider provider) { return new Dispatcher(); }
        public long availableCount(IPatternDetails pattern, KeyCounter[] inputs, long requested) {
            long result = Math.min(capacity, requested);
            inputs[0].reset();
            return result;
        }
        public boolean dispatch(IPatternDetails pattern, KeyCounter[] inputs, long count) {
            if (failure != null) throw failure;
            if (count > capacity) return false;
            assertSame(PATTERN, pattern);
            assertEquals(2, inputs[0].get(INPUT));
            inputs[0].reset();
            pushes++;
            accepted = count;
            return true;
        }
    }
}
