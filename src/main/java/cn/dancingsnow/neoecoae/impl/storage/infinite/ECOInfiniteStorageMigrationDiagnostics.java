package cn.dancingsnow.neoecoae.impl.storage.infinite;

import cn.dancingsnow.neoecoae.config.NEConfig;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Rate-limited diagnostics for the loss-protected matrix-to-domain migration. */
public final class ECOInfiniteStorageMigrationDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOInfiniteStorageMigrationDiagnostics.class);
    private static final long LOG_INTERVAL_MILLIS = 5_000L;
    private static final ConcurrentMap<String, Long> LAST_LOGGED = new ConcurrentHashMap<>();

    private ECOInfiniteStorageMigrationDiagnostics() {
    }

    public static void log(String fingerprint, String message) {
        if (!NEConfig.debugInfiniteStorageMigration) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean[] shouldLog = {false};
        LAST_LOGGED.compute(fingerprint, (ignored, previous) -> {
            if (previous == null || now - previous >= LOG_INTERVAL_MILLIS) {
                shouldLog[0] = true;
                return now;
            }
            return previous;
        });
        if (shouldLog[0]) {
            LOGGER.info("ECO infinite-storage migration diagnostic: {}", message);
        }
    }

    public static void clear() {
        LAST_LOGGED.clear();
    }
}
