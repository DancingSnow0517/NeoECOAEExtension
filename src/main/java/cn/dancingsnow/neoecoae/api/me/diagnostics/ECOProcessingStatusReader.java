package cn.dancingsnow.neoecoae.api.me.diagnostics;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.display.processing.ECOProcessingProgress.Machine;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Read-only server-thread adapter. Never load chunks, find/process recipes, or mutate machine state. */
public interface ECOProcessingStatusReader {
    boolean supports(BlockEntity machine);

    Observation read(BlockEntity machine, Context context);

    /** A null job UUID is used only for legacy destination records. */
    record Context(AEKey output, @Nullable UUID jobId) {}

    /** Links are resolved in subsequent budgeted steps. A sample describes this physical machine only. */
    record Observation(@Nullable Machine sample, List<ECOProcessingTargetProvider.Target> links) {
        public static final Observation EMPTY = new Observation(null, List.of());
        public Observation { links = List.copyOf(links); }
        public static Observation sample(Machine sample) { return new Observation(sample, List.of()); }
    }
}
