package cn.dancingsnow.neoecoae.crafting.execution.worker;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

/** Durable terminal decisions shared by CPU owners and workers, including unloaded workers. */
public final class ECOCraftingJobLifecycle extends SavedData {
    // 26.1: SavedData is codec-driven; the legacy CompoundTag layout is kept through CompoundTag.CODEC.
    private static final SavedDataType<ECOCraftingJobLifecycle> TYPE = new SavedDataType<>(
        cn.dancingsnow.neoecoae.NeoECOAE.id("crafting_job_lifecycle"),
        ECOCraftingJobLifecycle::new,
        CompoundTag.CODEC.xmap(tag -> load(tag, null), data -> data.save(new CompoundTag(), null)));

    // Do not expire decisions by time: a worker can remain unloaded for arbitrarily long.
    // Unknown jobs remain resumable; absence of a live CPU is never evidence of cancellation.
    private final Map<UUID, Boolean> terminatedJobs = new HashMap<>();

    private static @Nullable ECOCraftingJobLifecycle get(@Nullable Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        return serverLevel.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static boolean isTerminated(@Nullable Level level, @Nullable UUID jobId) {
        if (jobId == null) return false;
        var data = get(level);
        return data != null && data.terminatedJobs.containsKey(jobId);
    }

    /** Publish before clearing the owner's job or detaching its grid. First terminal decision wins. */
    public static void finish(@Nullable Level level, @Nullable UUID jobId, boolean completed) {
        if (jobId == null) return;
        var data = get(level);
        if (data != null && data.terminatedJobs.putIfAbsent(jobId, completed) == null) data.setDirty();
    }

    /**
     * Terminates a job owned by an external AE2 CPU and wakes every loaded ECO worker that still holds its
     * batch. External CPUs clear their own inventory during cancellation, so the worker must learn about the
     * terminal decision before that inventory is gone; otherwise a completed fast-path batch can remain owned by
     * an F host until the orphan timeout.
     */
    public static void cancelAndRecover(@Nullable Level level, @Nullable UUID jobId) {
        if (jobId == null) return;
        finish(level, jobId, false);
        if (level == null || level.getServer() == null) return;
        for (var worker : ECOCraftingWorkerBlockEntity.getLoadedServerWorkers()) {
            if (worker.getLevel() != null && worker.getLevel().getServer() == level.getServer()) {
                worker.recoverTerminatedJob(jobId);
            }
        }
    }

    /** Works without decoding a plan, so even a quarantined CPU can terminate its worker ownership. */
    public static void cancelPersistedJob(@Nullable Level level, CompoundTag cpuData) {
        CompoundTag link = cpuData.getCompoundOrEmpty("job").getCompoundOrEmpty("link");
        if (link.read("craftId", net.minecraft.core.UUIDUtil.CODEC).isPresent()) finish(level, link.read("craftId", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), false);
    }

    /**
     * Repairs the obsolete restore-failure cancellation marker once the complete persisted CPU has decoded again.
     * Completed jobs remain terminal; only the false marker formerly written by the quarantine path is removed.
     */
    public static void resumePersistedJob(@Nullable Level level, CompoundTag cpuData) {
        CompoundTag link = cpuData.getCompoundOrEmpty("job").getCompoundOrEmpty("link");
        if (!link.read("craftId", net.minecraft.core.UUIDUtil.CODEC).isPresent()) return;
        var data = get(level);
        if (data != null && Boolean.FALSE.equals(data.terminatedJobs.remove(link.read("craftId", net.minecraft.core.UUIDUtil.CODEC).orElseThrow()))) {
            data.setDirty();
        }
    }

    private static ECOCraftingJobLifecycle load(CompoundTag tag, HolderLookup.@Nullable Provider registries) {
        var data = new ECOCraftingJobLifecycle();
        ListTag jobs = tag.getListOrEmpty("jobs");
        for (int i = 0; i < jobs.size(); i++) {
            CompoundTag job = jobs.getCompoundOrEmpty(i);
            if (job.read("id", net.minecraft.core.UUIDUtil.CODEC).isPresent()) data.terminatedJobs.put(job.read("id", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), job.getBooleanOr("completed", false));
        }
        return data;
    }

    private CompoundTag save(CompoundTag tag, HolderLookup.@Nullable Provider registries) {
        ListTag jobs = new ListTag();
        terminatedJobs.forEach((id, completed) -> {
            CompoundTag job = new CompoundTag();
            job.store("id", net.minecraft.core.UUIDUtil.CODEC, id);
            job.putBoolean("completed", completed);
            jobs.add(job);
        });
        tag.put("jobs", jobs);
        return tag;
    }
}
