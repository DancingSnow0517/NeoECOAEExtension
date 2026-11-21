package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Candidate order for a physical host or one exchange domain. The worker still admits the batch. */
public final class ECOCraftingPatternDispatch {
    private final Map<ECOCraftingWorkerBlockEntity, Integer> positions = new IdentityHashMap<>();
    private final NavigableMap<Integer, Candidate> available = new TreeMap<>();
    private final NavigableSet<Candidate> ranked = new TreeSet<>(
        Comparator.comparingInt(Candidate::availableSlots).reversed()
            .thenComparing(candidate -> candidate.worker().getBlockPos(), ECOCraftingPatternDispatch::comparePositions)
            .thenComparingInt(Candidate::index));
    private long availableSlots;

    public ECOCraftingPatternDispatch(List<ECOCraftingWorkerBlockEntity> candidates) {
        int index = 0;
        for (ECOCraftingWorkerBlockEntity worker : candidates) {
            positions.put(worker, index++);
            update(worker);
        }
    }

    public void update(ECOCraftingWorkerBlockEntity worker) {
        Integer index = positions.get(worker);
        if (index == null) return;
        int capacity = worker.getAvailableBatchCapacity();
        Candidate previous = available.get(index);
        if (previous != null) {
            if (previous.availableSlots() == capacity) return;
            available.remove(index);
            ranked.remove(previous);
            availableSlots -= previous.availableSlots();
        }
        if (capacity > 0) {
            Candidate candidate = new Candidate(worker, index, capacity);
            available.put(index, candidate);
            ranked.add(candidate);
            availableSlots += capacity;
        }
    }

    public int size() {
        return positions.size();
    }

    public int availableSlots() {
        return (int) Math.min(Integer.MAX_VALUE, availableSlots);
    }

    public @Nullable Candidate next(int from) {
        var next = available.ceilingEntry(from);
        return next == null ? null : next.getValue();
    }

    public @Nullable Candidate best() {
        return ranked.isEmpty() ? null : ranked.first();
    }

    private static int comparePositions(BlockPos left, BlockPos right) {
        int byX = Integer.compare(left.getX(), right.getX());
        if (byX != 0) return byX;
        int byY = Integer.compare(left.getY(), right.getY());
        return byY != 0 ? byY : Integer.compare(left.getZ(), right.getZ());
    }

    public record Candidate(ECOCraftingWorkerBlockEntity worker, int index, int availableSlots) {}
}
