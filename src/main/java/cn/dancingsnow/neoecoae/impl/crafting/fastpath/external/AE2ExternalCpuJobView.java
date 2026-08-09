package cn.dancingsnow.neoecoae.impl.crafting.fastpath.external;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKeyType;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.mixins.ae2.ElapsedTimeTrackerAccessor;
import cn.dancingsnow.neoecoae.mixins.ae2.ExecutingCraftingJobAccessor;
import cn.dancingsnow.neoecoae.mixins.ae2.TaskProgressAccessor;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class AE2ExternalCpuJobView implements ECOExternalCpuJobView {
    private final ExecutingCraftingJob job;
    private final ListCraftingInventory inventory;
    private final CraftingCPUCluster cluster;

    public AE2ExternalCpuJobView(
            ExecutingCraftingJob job, ListCraftingInventory inventory, CraftingCPUCluster cluster) {
        this.job = job;
        this.inventory = inventory;
        this.cluster = cluster;
    }

    @Override
    public Iterator<Task> tasks() {
        return new TaskIterator(((ExecutingCraftingJobAccessor) (Object) job)
                .neoecoae$getTasks()
                .entrySet()
                .iterator());
    }

    @Override
    public ListCraftingInventory inventory() {
        return inventory;
    }

    @Override
    public ListCraftingInventory waitingFor() {
        return ((ExecutingCraftingJobAccessor) (Object) job).neoecoae$getWaitingFor();
    }

    @Override
    public UUID craftingId() {
        return ((ExecutingCraftingJobAccessor) (Object) job).neoecoae$getLink().getCraftingID();
    }

    @Override
    public void addContainerMaxItems(long amount, AEKeyType keyType) {
        var tracker = ((ExecutingCraftingJobAccessor) (Object) job).neoecoae$getTimeTracker();
        ((ElapsedTimeTrackerAccessor) (Object) tracker).neoecoae$addMaxItems(amount, keyType);
    }

    @Override
    public void markDirty() {
        cluster.markDirty();
    }

    private static final class TaskIterator implements Iterator<Task> {
        private final Iterator<Map.Entry<IPatternDetails, Object>> delegate;

        private TaskIterator(Iterator<Map.Entry<IPatternDetails, Object>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Task next() {
            return new TaskEntry(delegate.next());
        }

        @Override
        public void remove() {
            delegate.remove();
        }
    }

    private record TaskEntry(Map.Entry<IPatternDetails, Object> entry) implements Task {
        @Override
        public IPatternDetails details() {
            return entry.getKey();
        }

        @Override
        public long remaining() {
            return ((TaskProgressAccessor) entry.getValue()).neoecoae$getValue();
        }

        @Override
        public void remaining(long value) {
            ((TaskProgressAccessor) entry.getValue()).neoecoae$setValue(value);
        }
    }
}
