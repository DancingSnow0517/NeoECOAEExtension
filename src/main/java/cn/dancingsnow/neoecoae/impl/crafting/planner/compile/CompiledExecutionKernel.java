package cn.dancingsnow.neoecoae.impl.crafting.planner.compile;

import appeng.api.stacks.AEKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable CSR and slot data for exact, statically dispatchable patterns. */
public final class CompiledExecutionKernel {
    private final AEKey[] keys;
    private final int[] rowOffset;
    private final int[] rowResource;
    private final long[] rowNetDelta;
    private final long[] rowConsumed;
    private final int[] slotOffset;
    private final int[] slotResource;
    private final long[] slotAmount;
    private final boolean[] dispatchable;

    private CompiledExecutionKernel(AEKey[] keys, int[] rowOffset, int[] rowResource, long[] rowNetDelta,
            long[] rowConsumed, int[] slotOffset, int[] slotResource, long[] slotAmount,
            boolean[] dispatchable) {
        this.keys = keys;
        this.rowOffset = rowOffset;
        this.rowResource = rowResource;
        this.rowNetDelta = rowNetDelta;
        this.rowConsumed = rowConsumed;
        this.slotOffset = slotOffset;
        this.slotResource = slotResource;
        this.slotAmount = slotAmount;
        this.dispatchable = dispatchable;
    }

    public static CompiledExecutionKernel compile(CompiledNetwork network) {
        List<CompiledPattern> patterns = network.producers().values().stream()
            .flatMap(List::stream).sorted(Comparator.comparingInt(CompiledPattern::id)).toList();
        for (int i = 0; i < patterns.size(); i++) {
            if (patterns.get(i).id() != i) throw new IllegalArgumentException("Pattern ids must be dense");
        }

        List<AEKey> keys = new ArrayList<>();
        Map<AEKey, Integer> ids = new LinkedHashMap<>();
        for (CompiledPattern pattern : patterns) {
            for (CompiledInput input : pattern.inputs()) intern(ids, keys, input.key());
            for (var output : pattern.grossOutputs()) if (output != null) intern(ids, keys, output.what());
        }

        List<Integer> rowResources = new ArrayList<>();
        List<Long> netDeltas = new ArrayList<>();
        List<Long> consumedAmounts = new ArrayList<>();
        List<Integer> slotResources = new ArrayList<>();
        List<Long> slotAmounts = new ArrayList<>();
        int[] rowOffsets = new int[patterns.size() + 1];
        int[] slotOffsets = new int[patterns.size() + 1];
        boolean[] dispatchable = new boolean[patterns.size()];

        for (CompiledPattern pattern : patterns) {
            int patternId = pattern.id();
            dispatchable[patternId] = pattern.fastSupported();
            if (dispatchable[patternId]) {
                Map<Integer, Totals> totals = new LinkedHashMap<>();
                for (CompiledInput input : pattern.inputs()) {
                    long consumed = input.amountPerPattern().longValueExact();
                    int resourceId = ids.get(input.key());
                    totals.computeIfAbsent(resourceId, ignored -> new Totals()).addConsumed(consumed);
                    slotResources.add(resourceId);
                    slotAmounts.add(consumed);
                }
                for (var output : pattern.grossOutputs()) {
                    if (output != null && output.what() != null && output.amount() > 0L) {
                        totals.computeIfAbsent(ids.get(output.what()), ignored -> new Totals())
                            .addProduced(output.amount());
                    }
                }
                totals.forEach((resourceId, total) -> {
                    rowResources.add(resourceId);
                    consumedAmounts.add(total.consumed);
                    netDeltas.add(Math.subtractExact(total.produced, total.consumed));
                });
            }
            rowOffsets[patternId + 1] = rowResources.size();
            slotOffsets[patternId + 1] = slotResources.size();
        }
        return new CompiledExecutionKernel(keys.toArray(AEKey[]::new), rowOffsets, ints(rowResources),
            longs(netDeltas), longs(consumedAmounts), slotOffsets, ints(slotResources), longs(slotAmounts),
            dispatchable);
    }

    private static void intern(Map<AEKey, Integer> ids, List<AEKey> keys, AEKey key) {
        if (key != null) ids.computeIfAbsent(key, ignored -> { keys.add(key); return keys.size() - 1; });
    }
    private static int[] ints(List<Integer> values) { return values.stream().mapToInt(Integer::intValue).toArray(); }
    private static long[] longs(List<Long> values) { return values.stream().mapToLong(Long::longValue).toArray(); }

    public int patternCount() { return dispatchable.length; }
    public int resourceCount() { return keys.length; }
    public AEKey key(int resourceId) { return keys[resourceId]; }
    public int rowStart(int patternId) { return rowOffset[patternId]; }
    public int rowEnd(int patternId) { return rowOffset[patternId + 1]; }
    public int rowResource(int row) { return rowResource[row]; }
    public long rowNetDelta(int row) { return rowNetDelta[row]; }
    public long rowConsumed(int row) { return rowConsumed[row]; }
    public long rowProduced(int row) { return Math.addExact(rowNetDelta[row], rowConsumed[row]); }
    public int slotStart(int patternId) { return slotOffset[patternId]; }
    public int slotEnd(int patternId) { return slotOffset[patternId + 1]; }
    public int slotResource(int slot) { return slotResource[slot]; }
    public long slotAmount(int slot) { return slotAmount[slot]; }
    public boolean dispatchable(int patternId) { return dispatchable[patternId]; }

    private static final class Totals {
        private long consumed;
        private long produced;
        private void addConsumed(long amount) { consumed = Math.addExact(consumed, amount); }
        private void addProduced(long amount) { produced = Math.addExact(produced, amount); }
    }
}
