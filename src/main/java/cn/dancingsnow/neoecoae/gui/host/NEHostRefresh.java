package cn.dancingsnow.neoecoae.gui.host;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.function.Supplier;

final class NEHostRefresh {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);

    static final int FAST_TICKS = 5;
    static final int NORMAL_TICKS = 10;
    static final int MAX_SNAPSHOT_BYTES = 32 * 1024;

    private NEHostRefresh() {
    }

    static Supplier<byte[]> throttledSnapshot(Supplier<byte[]> source, int intervalTicks) {
        return new ThrottledSnapshot(source, Math.max(1, intervalTicks) * 50L, MAX_SNAPSHOT_BYTES);
    }

    private static final class ThrottledSnapshot implements Supplier<byte[]> {
        private final Supplier<byte[]> source;
        private final long intervalMs;
        private final int maxBytes;
        private byte[] cached = NEHostSnapshots.EMPTY;
        private long nextRefreshMs;
        private boolean warnedOversized;

        private ThrottledSnapshot(Supplier<byte[]> source, long intervalMs, int maxBytes) {
            this.source = source;
            this.intervalMs = intervalMs;
            this.maxBytes = Math.max(1, maxBytes);
        }

        @Override
        public byte[] get() {
            long now = Util.getMillis();
            if (now >= nextRefreshMs) {
                byte[] next = source.get();
                next = next == null ? NEHostSnapshots.EMPTY : next;
                if (next.length > maxBytes) {
                    warnOversized(next.length);
                } else if (!Arrays.equals(cached, next)) {
                    cached = next;
                    warnedOversized = false;
                }
                nextRefreshMs = now + intervalMs;
            }
            return cached;
        }

        private void warnOversized(int bytes) {
            if (warnedOversized) {
                return;
            }
            warnedOversized = true;
            LOGGER.warn(
                "NeoECOAE host UI snapshot skipped because it is too large: {} bytes > {} bytes",
                bytes,
                maxBytes
            );
        }
    }
}
