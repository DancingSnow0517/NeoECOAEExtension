package cn.dancingsnow.neoecoae.api.me;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.RuntimeExecutionState;

/** Coalesces CPU notifications until runtime accounting has been flushed for the dispatch pass. */
final class ECOCraftingStatusChanges {
    private final Consumer<AEKey> notifyListeners;
    private final Runnable updateLastModified;
    private final Runnable markDirty;
    private final Set<AEKey> fallbackKeys = new HashSet<>();
    private RuntimeExecutionState resourceState;
    private int[] resourceQueue = new int[0];
    private boolean[] resourcePresent = new boolean[0];
    private int resourceCount;
    private boolean batching;
    private boolean anyChange;
    private boolean fullChange;
    private boolean dirtyRequested;

    ECOCraftingStatusChanges(Consumer<AEKey> notifyListeners, Runnable updateLastModified, Runnable markDirty) {
        this.notifyListeners = notifyListeners;
        this.updateLastModified = updateLastModified;
        this.markDirty = markDirty;
    }

    boolean isBatching() {
        return batching;
    }

    void postChange(@Nullable AEKey key) {
        if (!batching) {
            updateLastModified.run();
            notifyListeners.accept(key);
            return;
        }
        anyChange = true;
        if (key == null) {
            fullChange = true;
            clearQueue();
        } else if (!fullChange) {
            int resourceId = resourceState == null ? -1 : resourceState.resourceIdIfKnown(key);
            if (resourceId >= 0 && resourceId < resourcePresent.length) {
                if (!resourcePresent[resourceId]) {
                    resourcePresent[resourceId] = true;
                    resourceQueue[resourceCount++] = resourceId;
                }
            } else {
                fallbackKeys.add(key);
            }
        }
    }

    void markDirty() {
        if (batching) dirtyRequested = true;
        else markDirty.run();
    }

    void initialize(@Nullable RuntimeExecutionState runtime) {
        clearBatch();
        resourceState = runtime;
        int count = runtime == null ? 0 : runtime.resourceCount();
        resourceQueue = new int[count];
        resourcePresent = new boolean[count];
    }

    void beginBatch(@Nullable RuntimeExecutionState runtime) {
        if (resourceState != runtime) initialize(runtime);
        clearBatch();
        batching = true;
    }

    /** A null flush callback means the original job ended during dispatch. */
    void endBatch(@Nullable Runnable flushRuntime) {
        try {
            flushBatch(flushRuntime);
        } catch (RuntimeException | Error failure) {
            batching = false;
            clearBatch();
            throw failure;
        }
    }

    private void flushBatch(@Nullable Runnable flushRuntime) {
        batching = false;
        boolean changed = anyChange;
        boolean full = fullChange;
        boolean dirty = dirtyRequested;
        RuntimeExecutionState previousResources = resourceState;
        int[] changedIds = full ? new int[0] : Arrays.copyOf(resourceQueue, resourceCount);
        AEKey[] changedFallbackKeys = full ? new AEKey[0] : fallbackKeys.toArray(AEKey[]::new);
        clearBatch();
        if (flushRuntime == null) initialize(null);

        if (flushRuntime != null) flushRuntime.run();
        if (dirty) markDirty.run();
        if (!changed) return;
        updateLastModified.run();
        if (full) {
            notifyListeners.accept(null);
            return;
        }
        for (int id : changedIds) notifyListeners.accept(previousResources.keyByResourceId(id));
        for (AEKey key : changedFallbackKeys) notifyListeners.accept(key);
    }

    private void clearQueue() {
        for (int i = 0; i < resourceCount; i++) resourcePresent[resourceQueue[i]] = false;
        resourceCount = 0;
        fallbackKeys.clear();
    }

    private void clearBatch() {
        clearQueue();
        anyChange = false;
        fullChange = false;
        dirtyRequested = false;
    }
}
