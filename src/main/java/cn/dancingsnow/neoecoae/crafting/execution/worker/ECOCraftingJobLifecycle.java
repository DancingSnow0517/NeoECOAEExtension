package cn.dancingsnow.neoecoae.crafting.execution.worker;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

    // Do not expire decisions by time: a worker can remain unloaded for arbitrarily long.
    // Unknown jobs remain resumable; absence of a live CPU is never evidence of cancellation.
    private final Map<UUID, Boolean> terminatedJobs = new HashMap<>();

    private static @Nullable ECOCraftingJobLifecycle get(@Nullable Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        return serverLevel
                .getServer()
                .overworld()
                .getDataStorage()
                .computeIfAbsent(ECOCraftingJobLifecycle::load, ECOCraftingJobLifecycle::new, DATA_NAME);
    }

    public static boolean isTerminated(@Nullable Level level, @Nullable UUID jobId) {
        if (jobId == null) return false;
        var data = get(level);
        return data != null && data.terminatedJobs.containsKey(jobId);
    }

    public static @Nullable Boolean completion(Level level, UUID jobId) {
        var data = get(level);
        return data == null ? null : data.terminatedJobs.get(jobId);
    }

    /** Publish before clearing the owner's job or detaching its grid. First terminal decision wins. */
    public static void finish(@Nullable Level level, @Nullable UUID jobId, boolean completed) {
        if (jobId == null) return;
        var data = get(level);
        if (data != null && data.terminatedJobs.putIfAbsent(jobId, completed) == null) data.setDirty();
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
        if (data != null) data.resume(link.getUUID("craftId"));
    }

    void resume(UUID jobId) {
        if (terminatedJobs.remove(jobId, Boolean.FALSE)) {
            setDirty();
        }
    }

    static ECOCraftingJobLifecycle load(CompoundTag tag) {
        var data = new ECOCraftingJobLifecycle();
        ListTag jobs = tag.getList("jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < jobs.size(); i++) {
            CompoundTag job = jobs.getCompound(i);
            if (job.hasUUID("id")) data.terminatedJobs.put(job.getUUID("id"), job.getBoolean("completed"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
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
