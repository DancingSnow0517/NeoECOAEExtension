package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.impl.storage.StorageFaults;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Owns retry handling for storage-host stages. */
final class ECOStorageStageRunner {
    private static final long RETRY_DELAY_TICKS = 200L;

    private final ECOStorageSystemBlockEntity host;
    private final StorageFaults faults = new StorageFaults();
    private final Map<String, Long> retryTicks = new HashMap<>();

    ECOStorageStageRunner(ECOStorageSystemBlockEntity host) {
        this.host = host;
    }

    boolean run(String stage, Runnable action) {
        Level level = host.getLevel();
        long tick = level == null ? 0L : level.getGameTime();
        if (tick < retryTicks.getOrDefault(stage, Long.MIN_VALUE)) {
            return false;
        }
        try {
            action.run();
            faults.recovered(stage);
            return true;
        } catch (RuntimeException exception) {
            retryTicks.put(stage, tick + RETRY_DELAY_TICKS);
            faults.report(stage, host.getBlockPos() + ": " + exception, tick, exception);
            return false;
        }
    }

    List<StorageFaults.Fault> failures() {
        return faults.snapshot();
    }

    StorageFaults faults() {
        return faults;
    }

    Map<String, Long> retryTicks() {
        return retryTicks;
    }

}
