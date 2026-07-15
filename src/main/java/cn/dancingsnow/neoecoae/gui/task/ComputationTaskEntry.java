package cn.dancingsnow.neoecoae.gui.task;

import appeng.api.config.CpuSelectionMode;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class ComputationTaskEntry {
    private static final String NBT_ID = "id";
    private static final String NBT_OUTPUT = "output";
    private static final String NBT_OUTPUT_AMOUNT = "outputAmount";
    private static final String NBT_CRAFT_COUNT = "craftCount";
    private static final String NBT_TOTAL_PROGRESS = "totalProgress";
    private static final String NBT_REMAINING_PROGRESS = "remainingProgress";
    private static final String NBT_STATUS = "status";
    private static final String NBT_CPU_SERIAL = "cpuSerial";
    private static final String NBT_CPU_NAME = "cpuName";
    private static final String NBT_CPU_STORAGE = "cpuStorage";
    private static final String NBT_CPU_CO_PROCESSORS = "cpuCoProcessors";
    private static final String NBT_CPU_SELECTION_MODE = "cpuSelectionMode";
    private static final String NBT_PROGRESS = "progress";
    private static final String NBT_ELAPSED_TIME_NANOS = "elapsedTimeNanos";

    private final String id;
    private final ItemStack output;
    private final long outputAmount;
    private final long craftCount;
    private final long totalProgress;
    private final long remainingProgress;
    private final Status status;
    private final int cpuSerial;
    private final Component cpuName;
    private final long cpuStorage;
    private final int cpuCoProcessors;
    private final CpuSelectionMode cpuSelectionMode;
    private final float progress;
    private final long elapsedTimeNanos;

    public ComputationTaskEntry(
        String id,
        ItemStack output,
        long outputAmount,
        long craftCount,
        long totalProgress,
        long remainingProgress,
        Status status,
        int cpuSerial,
        Component cpuName,
        long cpuStorage,
        int cpuCoProcessors,
        CpuSelectionMode cpuSelectionMode,
        float progress,
        long elapsedTimeNanos
    ) {
        this.id = id;
        this.output = output;
        this.outputAmount = outputAmount;
        this.craftCount = craftCount;
        this.totalProgress = totalProgress;
        this.remainingProgress = remainingProgress;
        this.status = status;
        this.cpuSerial = cpuSerial;
        this.cpuName = cpuName;
        this.cpuStorage = cpuStorage;
        this.cpuCoProcessors = cpuCoProcessors;
        this.cpuSelectionMode = cpuSelectionMode;
        this.progress = progress;
        this.elapsedTimeNanos = elapsedTimeNanos;
    }

    public String id() {
        return id;
    }

    public ItemStack output() {
        return output;
    }

    public long outputAmount() {
        return outputAmount;
    }

    public long craftCount() {
        return craftCount;
    }

    public long totalProgress() {
        return totalProgress;
    }

    public long remainingProgress() {
        return remainingProgress;
    }

    public Status status() {
        return status;
    }

    public int cpuSerial() {
        return cpuSerial;
    }

    public Component cpuName() {
        return cpuName;
    }

    public long cpuStorage() {
        return cpuStorage;
    }

    public int cpuCoProcessors() {
        return cpuCoProcessors;
    }

    public CpuSelectionMode cpuSelectionMode() {
        return cpuSelectionMode;
    }

    public float progress() {
        return progress;
    }

    public long elapsedTimeNanos() {
        return elapsedTimeNanos;
    }

    public CompoundTag writeToNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString(NBT_ID, id);
        tag.put(NBT_OUTPUT, output.saveOptional(provider));
        tag.putLong(NBT_OUTPUT_AMOUNT, outputAmount);
        tag.putLong(NBT_CRAFT_COUNT, craftCount);
        tag.putLong(NBT_TOTAL_PROGRESS, totalProgress);
        tag.putLong(NBT_REMAINING_PROGRESS, remainingProgress);
        tag.putString(NBT_STATUS, status.name());
        tag.putInt(NBT_CPU_SERIAL, cpuSerial);
        if (cpuName != null) {
            ComponentSerialization.CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), cpuName)
                .result()
                .ifPresent(nameTag -> tag.put(NBT_CPU_NAME, nameTag));
        }
        tag.putLong(NBT_CPU_STORAGE, cpuStorage);
        tag.putInt(NBT_CPU_CO_PROCESSORS, cpuCoProcessors);
        tag.putString(NBT_CPU_SELECTION_MODE, cpuSelectionMode.name());
        tag.putFloat(NBT_PROGRESS, progress);
        tag.putLong(NBT_ELAPSED_TIME_NANOS, elapsedTimeNanos);
        return tag;
    }

    public static ComputationTaskEntry readFromNBT(HolderLookup.Provider provider, CompoundTag tag) {
        ItemStack output = ItemStack.EMPTY;
        if (tag.contains(NBT_OUTPUT, Tag.TAG_COMPOUND)) {
            output = ItemStack.parseOptional(provider, tag.getCompound(NBT_OUTPUT));
        }
        Component cpuName = null;
        if (tag.contains(NBT_CPU_NAME)) {
            cpuName = ComponentSerialization.CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), tag.get(NBT_CPU_NAME))
                .result()
                .orElse(null);
        }
        return new ComputationTaskEntry(
            tag.getString(NBT_ID),
            output,
            tag.getLong(NBT_OUTPUT_AMOUNT),
            tag.getLong(NBT_CRAFT_COUNT),
            Math.max(1L, tag.getLong(NBT_TOTAL_PROGRESS)),
            Math.max(0L, tag.getLong(NBT_REMAINING_PROGRESS)),
            readStatus(tag.getString(NBT_STATUS)),
            tag.getInt(NBT_CPU_SERIAL),
            cpuName,
            tag.getLong(NBT_CPU_STORAGE),
            tag.getInt(NBT_CPU_CO_PROCESSORS),
            readCpuSelectionMode(tag.getString(NBT_CPU_SELECTION_MODE)),
            tag.getFloat(NBT_PROGRESS),
            tag.getLong(NBT_ELAPSED_TIME_NANOS)
        );
    }

    private static Status readStatus(String value) {
        try {
            return Status.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return Status.RUNNING;
        }
    }

    private static CpuSelectionMode readCpuSelectionMode(String value) {
        try {
            return CpuSelectionMode.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return CpuSelectionMode.ANY;
        }
    }

    public enum Status {
        RUNNING,
        QUEUED,
        WAITING_OUTPUT
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ComputationTaskEntry other)) {
            return false;
        }
        return outputAmount == other.outputAmount
            && craftCount == other.craftCount
            && totalProgress == other.totalProgress
            && remainingProgress == other.remainingProgress
            && cpuSerial == other.cpuSerial
            && cpuStorage == other.cpuStorage
            && cpuCoProcessors == other.cpuCoProcessors
            && Objects.equals(id, other.id)
            && ItemStack.matches(output, other.output)
            && status == other.status
            && Objects.equals(cpuName, other.cpuName)
            && cpuSelectionMode == other.cpuSelectionMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            output.getItem(),
            output.getCount(),
            output.getComponents(),
            outputAmount,
            craftCount,
            totalProgress,
            remainingProgress,
            status,
            cpuSerial,
            cpuName,
            cpuStorage,
            cpuCoProcessors,
            cpuSelectionMode
        );
    }
}
