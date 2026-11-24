package cn.dancingsnow.neoecoae.crafting.execution.worker;

import cn.dancingsnow.neoecoae.api.me.ECOExternalCrafting;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/** Durable terminal decisions shared by CPU owners and workers, including unloaded workers. */
public final class ECOCraftingJobLifecycle extends SavedData {
    private static final String DATA_NAME = "neoecoae_crafting_job_lifecycle";
    private static final Factory<ECOCraftingJobLifecycle> FACTORY =
        new Factory<>(ECOCraftingJobLifecycle::new, ECOCraftingJobLifecycle::load, null);

    // Do not expire decisions by time: a worker can remain unloaded for arbitrarily long.
    // Unknown jobs remain resumable; absence of a live CPU is never evidence of cancellation.
    private final Map<UUID, Boolean> terminatedJobs = new HashMap<>();
    private final Map<UUID, Set<ECOCraftingWorkerBlockEntity>> loadedWorkersByJob = new HashMap<>();

    private static @Nullable ECOCraftingJobLifecycle get(@Nullable Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        return serverLevel.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static boolean isTerminated(@Nullable Level level, @Nullable UUID jobId) {
        if (jobId == null) return false;
        var data = get(level);
        return data != null && data.terminatedJobs.containsKey(jobId);
    }

    public static void trackWorker(Level level, UUID jobId, ECOCraftingWorkerBlockEntity worker, boolean holding) {
        var data = get(level);
        if (data == null) return;
        if (holding) {
            data.loadedWorkersByJob.computeIfAbsent(jobId, ignored -> new HashSet<>()).add(worker);
        } else {
            var workers = data.loadedWorkersByJob.get(jobId);
            if (workers != null && workers.remove(worker) && workers.isEmpty()) data.loadedWorkersByJob.remove(jobId);
        }
    }

    public static List<ECOCraftingWorkerBlockEntity> loadedWorkersForJob(@Nullable Level level, UUID jobId) {
        var data = get(level);
        var workers = data == null ? null : data.loadedWorkersByJob.get(jobId);
        return workers == null ? List.of() : List.copyOf(workers);
    }

    /** Publish before clearing the owner's job or detaching its grid. First terminal decision wins. */
    public static void finish(@Nullable Level level, @Nullable UUID jobId, boolean completed) {
        if (jobId == null) return;
        var data = get(level);
        if (data == null || data.terminatedJobs.putIfAbsent(jobId, completed) != null) return;
        data.setDirty();
        if (!completed) ECOExternalCrafting.jobCanceled(level, jobId);
        for (var worker : loadedWorkersForJob(level, jobId)) {
            if (completed) worker.releaseCompletedJobOutputs(jobId);
            else worker.recoverTerminatedJob(jobId);
        }
    }

    /**
     * Terminates a job owned by an external AE2 CPU and wakes every loaded ECO worker that still holds its
     * batch. External CPUs clear their own inventory during cancellation, so the worker must learn about the
     * terminal decision before that inventory is gone; otherwise a completed fast-path batch can remain owned by
     * an F host until the orphan timeout.
     */
    public static void cancelAndRecover(@Nullable Level level, @Nullable UUID jobId) {
        finish(level, jobId, false);
    }

    /** Works without decoding a plan, so even a quarantined CPU can terminate its worker ownership. */
    public static void cancelPersistedJob(@Nullable Level level, CompoundTag cpuData) {
        CompoundTag link = cpuData.getCompound("job").getCompound("link");
        if (link.hasUUID("craftId")) finish(level, link.getUUID("craftId"), false);
    }

    /**
     * Repairs the obsolete restore-failure cancellation marker once the complete persisted CPU has decoded again.
     * Completed jobs remain terminal; only the false marker formerly written by the quarantine path is removed.
     */
    public static void resumePersistedJob(@Nullable Level level, CompoundTag cpuData) {
        CompoundTag link = cpuData.getCompound("job").getCompound("link");
        if (!link.hasUUID("craftId")) return;
        var data = get(level);
        if (data == null) return;
        var jobId = link.getUUID("craftId");
        if (Boolean.FALSE.equals(data.terminatedJobs.get(jobId))) {
            data.terminatedJobs.remove(jobId);
            data.setDirty();
        }
        if (!data.terminatedJobs.containsKey(jobId)) ECOExternalCrafting.jobRestored(level, jobId);
    }

    private static ECOCraftingJobLifecycle load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new ECOCraftingJobLifecycle();
        ListTag jobs = tag.getList("jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < jobs.size(); i++) {
            CompoundTag job = jobs.getCompound(i);
            if (job.hasUUID("id")) data.terminatedJobs.put(job.getUUID("id"), job.getBoolean("completed"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag jobs = new ListTag();
        terminatedJobs.forEach((id, completed) -> {
            CompoundTag job = new CompoundTag();
            job.putUUID("id", id);
            job.putBoolean("completed", completed);
            jobs.add(job);
        });
        tag.put("jobs", jobs);
        return tag;
    }
}
