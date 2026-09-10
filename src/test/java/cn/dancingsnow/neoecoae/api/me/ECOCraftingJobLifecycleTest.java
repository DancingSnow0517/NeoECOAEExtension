package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class ECOCraftingJobLifecycleTest {
    @Test
    void restoreClearsOnlyObsoleteCancellationAndPreservesCompletedDecision() {
        UUID cancelled = UUID.randomUUID();
        UUID completed = UUID.randomUUID();
        CompoundTag saved = new CompoundTag();
        ListTag jobs = new ListTag();
        jobs.add(entry(cancelled, false));
        jobs.add(entry(completed, true));
        saved.put("jobs", jobs);
        var lifecycle = ECOCraftingJobLifecycle.load(saved);
        lifecycle.resume(completed);
        assertEquals(
                2,
                lifecycle
                        .save(new CompoundTag())
                        .getList("jobs", Tag.TAG_COMPOUND)
                        .size());
        lifecycle.resume(cancelled);
        var remaining = lifecycle.save(new CompoundTag()).getList("jobs", Tag.TAG_COMPOUND);
        assertEquals(1, remaining.size());
        assertEquals(completed, remaining.getCompound(0).getUUID("id"));
        assertTrue(remaining.getCompound(0).getBoolean("completed"));
        assertTrue(lifecycle.isDirty());
    }

    private CompoundTag entry(UUID id, boolean completed) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putBoolean("completed", completed);
        return tag;
    }
}
