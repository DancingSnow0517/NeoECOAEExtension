package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledExecutionKernel;

/** Allocation-free ownership state and bounded per-tick diagnostic queue. */
public final class PrimitiveOwnershipState {
    private final long[] onHand;
    private final long[] futureNeed;
    private final byte[] pendingEventType;
    private final int[] pendingEventResource;
    private final long[] pendingEventAmount;
    private int pendingEventCount;
    private long eventSequence;

    public PrimitiveOwnershipState(int resourceCount, long[] onHand, long[] futureNeed, int eventCapacity) {
        if (onHand.length != resourceCount || futureNeed.length != resourceCount || eventCapacity < 0) {
            throw new IllegalArgumentException("Invalid ownership vector shape");
        }
        this.onHand = onHand.clone();
        this.futureNeed = futureNeed.clone();
        this.pendingEventType = new byte[eventCapacity];
        this.pendingEventResource = new int[eventCapacity];
        this.pendingEventAmount = new long[eventCapacity];
    }

    public long onHand(int resourceId) { return onHand[resourceId]; }
    public long futureNeed(int resourceId) { return futureNeed[resourceId]; }
    public int pendingEventCount() { return pendingEventCount; }

    public void commitAccepted(CompiledExecutionKernel kernel, int patternId, long count) {
        if (count <= 0L || !kernel.dispatchable(patternId)) throw new IllegalArgumentException("Invalid dispatch");
        for (int row = kernel.rowStart(patternId); row < kernel.rowEnd(patternId); row++) {
            long consumed = Math.multiplyExact(kernel.rowConsumed(row), count);
            if (consumed > onHand[kernel.rowResource(row)]) {
                throw new IllegalArgumentException("Dispatch consumption exceeds ownership");
            }
        }
        for (int row = kernel.rowStart(patternId); row < kernel.rowEnd(patternId); row++) {
            int resourceId = kernel.rowResource(row);
            long consumed = Math.multiplyExact(kernel.rowConsumed(row), count);
            onHand[resourceId] -= consumed;
            futureNeed[resourceId] = Math.max(0L, futureNeed[resourceId] - consumed);
        }
        append(OwnershipEvent.Type.DISPATCH_COMMITTED, -1, count);
    }

    public void acceptOutput(int resourceId, long amount) {
        if (amount <= 0L) throw new IllegalArgumentException("Output amount must be positive");
        onHand[resourceId] = Math.addExact(onHand[resourceId], amount);
        append(OwnershipEvent.Type.OUTPUT_RETURNED, resourceId, amount);
    }

    public void releaseExternal(int resourceId, long amount) {
        long releasable = Math.max(0L, onHand[resourceId] - futureNeed[resourceId]);
        if (amount <= 0L || amount > releasable) throw new IllegalArgumentException("Release exceeds surplus");
        onHand[resourceId] -= amount;
        append(OwnershipEvent.Type.OWNERSHIP_RELEASED, resourceId, amount);
    }

    public int flushTick() {
        int flushed = pendingEventCount;
        pendingEventCount = 0;
        return flushed;
    }

    private void append(OwnershipEvent.Type type, int resourceId, long amount) {
        if (pendingEventType.length == 0) return;
        int index = (int) (eventSequence++ % pendingEventType.length);
        pendingEventType[index] = (byte) type.ordinal();
        pendingEventResource[index] = resourceId;
        pendingEventAmount[index] = amount;
        pendingEventCount = Math.min(pendingEventCount + 1, pendingEventType.length);
    }
}
