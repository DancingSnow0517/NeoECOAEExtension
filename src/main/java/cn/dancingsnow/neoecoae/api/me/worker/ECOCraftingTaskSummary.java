package cn.dancingsnow.neoecoae.api.me.worker;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.gui.task.ComputationTaskEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Aggregates worker snapshots for the crafting host task panel. */
public final class ECOCraftingTaskSummary {
    private ECOCraftingTaskSummary() {}

    public static List<ComputationTaskEntry> collect(List<ECOCraftingWorkerBlockEntity> workers, BlockPos controllerPos) {
        Map<TaskKey, Aggregate> aggregates = new LinkedHashMap<>();
        for (ECOCraftingWorkerBlockEntity worker : workers) {
            for (ECOCraftingThread.Snapshot snapshot : worker.getThreadSnapshots()) {
                ItemStack output = snapshot.outputItem();
                if (output.isEmpty()) continue;
                TaskKey key = new TaskKey(snapshot.craftingJobId(), output);
                aggregates.computeIfAbsent(key, ignored -> new Aggregate(output.copyWithCount(1))).add(snapshot);
            }
        }
        List<ComputationTaskEntry> entries = new ArrayList<>(aggregates.size());
        int index = 0;
        for (Aggregate aggregate : aggregates.values()) {
            entries.add(aggregate.toEntry(controllerPos, index++));
        }
        return List.copyOf(entries);
    }

    private record TaskKey(@Nullable UUID jobId, ItemStack output) {
        private TaskKey {
            output = output.copyWithCount(1);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TaskKey that)) return false;
            return java.util.Objects.equals(jobId, that.jobId)
                && ItemStack.isSameItemSameComponents(output, that.output);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(jobId, output.getItem(), output.getComponents());
        }
    }

    private static final class Aggregate {
        private final ItemStack output;
        private long outputAmount;
        private long craftCount;
        private long totalProgress;
        private long remainingProgress;
        private boolean waitingOutput = true;
        private final LinkedHashSet<String> fastPathReasons = new LinkedHashSet<>();

        private Aggregate(ItemStack output) { this.output = output; }

        private void add(ECOCraftingThread.Snapshot snapshot) {
            long crafts = Math.max(1L, snapshot.craftCount());
            int maxProgress = Math.max(1, snapshot.maxProgress());
            int progress = Mth.clamp(snapshot.progress(), 0, maxProgress);
            outputAmount = cn.dancingsnow.neoecoae.util.NEMath.saturatingAdd(
                outputAmount, Math.max(1L, snapshot.outputAmount()));
            craftCount = cn.dancingsnow.neoecoae.util.NEMath.saturatingAdd(craftCount, crafts);
            totalProgress = cn.dancingsnow.neoecoae.util.NEMath.saturatingAdd(
                totalProgress, cn.dancingsnow.neoecoae.util.NEMath.saturatingMultiply(maxProgress, crafts));
            remainingProgress = cn.dancingsnow.neoecoae.util.NEMath.saturatingAdd(
                remainingProgress, cn.dancingsnow.neoecoae.util.NEMath.saturatingMultiply(
                    Math.max(0, maxProgress - progress), crafts));
            waitingOutput &= snapshot.outputsReady();
            fastPathReasons.add(snapshot.fastPathReason() == null ? "NOT_RECORDED" : snapshot.fastPathReason());
        }

        private ComputationTaskEntry toEntry(BlockPos controllerPos, int index) {
            long safeTotal = Math.max(1L, totalProgress);
            long safeRemaining = Math.max(0L, Math.min(safeTotal, remainingProgress));
            float progress = Mth.clamp((safeTotal - safeRemaining) / (float) safeTotal, 0.0F, 1.0F);
            return new ComputationTaskEntry(
                "crafting:" + controllerPos.asLong() + ":" + index + ":" + output.getItem().hashCode(),
                output.copyWithCount(1), Math.max(1L, outputAmount), Math.max(1L, craftCount), safeTotal,
                safeRemaining,
                waitingOutput ? ComputationTaskEntry.Status.WAITING_OUTPUT : ComputationTaskEntry.Status.RUNNING,
                index + 1, Component.translatable("gui.neoecoae.host.crafting.subtitle"), 0L, 0,
                appeng.api.config.CpuSelectionMode.ANY, progress, 0L, String.join("\n", fastPathReasons)
            );
        }
    }
}
