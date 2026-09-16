package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.impl.storage.ECOCellMutationBatch;
import cn.dancingsnow.neoecoae.impl.storage.StorageFaults;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOStorageTickBudget;
import cn.dancingsnow.neoecoae.config.NEConfig;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Owns the bounded per-tick budget and retry policy for storage-host stages. */
final class ECOStorageStageRunner {
    private static final long RETRY_DELAY_TICKS = 200L;

    private final ECOStorageSystemBlockEntity host;
    private final StorageFaults faults = new StorageFaults();
    private final Map<String, Long> retryTicks = new HashMap<>();
    private long remainingBudget;

    ECOStorageStageRunner(ECOStorageSystemBlockEntity host) {
        this.host = host;
    }

    void beginTick(Object server, long gameTime) {
        remainingBudget = ECOStorageTickBudget.allowance(
            server, host, gameTime, NEConfig.storageTransferNanosPerTick);
    }

    void finishTick(Object server, long elapsedNanos) {
        ECOStorageTickBudget.spent(server, elapsedNanos);
    }

    boolean hasBudget() {
        return remainingBudget > 0L;
    }

    boolean run(String stage, Runnable action) {
        Level level = host.getLevel();
        long tick = level == null ? 0L : level.getGameTime();
        if (tick < retryTicks.getOrDefault(stage, Long.MIN_VALUE) || !hasBudget()) {
            return false;
        }
        long start = System.nanoTime();
        try {
            action.run();
            faults.recovered(stage);
            return true;
        } catch (RuntimeException exception) {
            retryTicks.put(stage, tick + RETRY_DELAY_TICKS);
            faults.report(stage, host.getBlockPos() + ": " + exception, tick, exception);
            return false;
        } finally {
            remainingBudget = Math.max(0L, remainingBudget - (System.nanoTime() - start));
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

    long remainingBudget() {
        return remainingBudget;
    }
}
