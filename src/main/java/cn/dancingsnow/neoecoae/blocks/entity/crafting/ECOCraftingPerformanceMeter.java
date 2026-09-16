package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.hooks.ticking.TickHandler;

import java.util.function.LongConsumer;

/**
 * Aggregates short-lived server tick timings into the rolling value shown by the crafting-system UI.
 * The window itself is runtime-only; the owning block entity remains responsible for the synced value.
 */
final class ECOCraftingPerformanceMeter {
    private static final long SAMPLE_WINDOW_TICKS = 20L * 3L;

    private final LongConsumer averageConsumer;
    private long windowStartTick = Long.MIN_VALUE;
    private long windowNanos;

    ECOCraftingPerformanceMeter(LongConsumer averageConsumer) {
        this.averageConsumer = averageConsumer;
    }

    void record(long elapsedNanos) {
        if (elapsedNanos < 0L) {
            return;
        }

        long currentTick = TickHandler.instance().getCurrentTick();
        if (windowStartTick == Long.MIN_VALUE) {
            windowStartTick = currentTick;
        }
        windowNanos += elapsedNanos;
        long elapsedTicks = currentTick - windowStartTick;
        if (elapsedTicks < SAMPLE_WINDOW_TICKS) {
            return;
        }

        long nextAverageNanos = windowNanos / Math.max(1L, elapsedTicks);
        windowStartTick = currentTick;
        windowNanos = 0L;
        averageConsumer.accept(nextAverageNanos);
    }
}
