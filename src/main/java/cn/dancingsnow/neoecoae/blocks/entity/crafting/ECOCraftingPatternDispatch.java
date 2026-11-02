package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Selects a live worker for pattern dispatch; capacity is measured at call time. */
final class ECOCraftingPatternDispatch {
    private ECOCraftingPatternDispatch() {}

    @Nullable
    static Candidate best(List<ECOCraftingWorkerBlockEntity> candidates) {
        Candidate best = null;
        for (ECOCraftingWorkerBlockEntity worker : candidates) {
            int availableSlots = worker.getAvailableThreadSlots();
            if (availableSlots <= 0) continue;
            Candidate candidate = new Candidate(worker, availableSlots);
            if (best == null || compare(candidate, best) < 0) best = candidate;
        }
        return best;
    }

    private static int compare(Candidate left, Candidate right) {
        int bySlots = Integer.compare(right.availableSlots(), left.availableSlots());
        return bySlots != 0 ? bySlots : comparePositions(left.worker().getBlockPos(), right.worker().getBlockPos());
    }

    private static int comparePositions(BlockPos left, BlockPos right) {
        int byX = Integer.compare(left.getX(), right.getX());
        if (byX != 0) return byX;
        int byY = Integer.compare(left.getY(), right.getY());
        return byY != 0 ? byY : Integer.compare(left.getZ(), right.getZ());
    }

    record Candidate(ECOCraftingWorkerBlockEntity worker, int availableSlots) {}
}
