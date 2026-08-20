package cn.dancingsnow.neoecoae.impl.crafting.fastpath.external;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.mixins.aae.AdvElapsedTimeTrackerAccessor;
import cn.dancingsnow.neoecoae.mixins.aae.AdvExecutingCraftingJobAccessor;
import cn.dancingsnow.neoecoae.mixins.aae.AdvTaskProgressAccessor;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;

public final class AdvancedAEExternalCpuJobView implements ECOExternalCpuJobView {
    private final ExecutingCraftingJob job;
    private final ListCraftingInventory inventory;
    private final AdvCraftingCPU cpu;

    public AdvancedAEExternalCpuJobView(
            ExecutingCraftingJob job,
            ListCraftingInventory inventory,
            AdvCraftingCPU cpu) {
        this.job = job;
        this.inventory = inventory;
        this.cpu = cpu;
    }

    @Override
    public Iterator<Task> tasks() {
        var accessor = (AdvExecutingCraftingJobAccessor) (Object) job;
        return new TaskIterator(accessor.neoecoae$getTasks().entrySet().iterator());
    }

    @Override
    public ListCraftingInventory inventory() {
        return inventory;
    }

    @Override
    public ListCraftingInventory waitingFor() {
        return ((AdvExecutingCraftingJobAccessor) (Object) job).neoecoae$getWaitingFor();
    }

    @Override
    public GenericStack finalOutput() {
        return ((AdvExecutingCraftingJobAccessor) (Object) job).neoecoae$getFinalOutput();
    }

    @Override
    public long remainingOutputAmount() {
        return ((AdvExecutingCraftingJobAccessor) (Object) job).neoecoae$getRemainingAmount();
    }

    @Override
    public UUID craftingId() {
        return ((AdvExecutingCraftingJobAccessor) (Object) job).neoecoae$getLink().getCraftingID();
    }

    @Override
    public void addContainerMaxItems(long amount, AEKeyType keyType) {
        var tracker = ((AdvExecutingCraftingJobAccessor) (Object) job).neoecoae$getTimeTracker();
        ((AdvElapsedTimeTrackerAccessor) (Object) tracker).neoecoae$addMaxItems(amount, keyType);
    }

    @Override
    public void markDirty() {
        cpu.markDirty();
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
            return ((AdvTaskProgressAccessor) entry.getValue()).neoecoae$getValue();
        }

        @Override
        public void remaining(long value) {
            ((AdvTaskProgressAccessor) entry.getValue()).neoecoae$setValue(value);
        }
    }
}
