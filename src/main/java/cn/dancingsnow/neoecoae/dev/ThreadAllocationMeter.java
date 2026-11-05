package cn.dancingsnow.neoecoae.dev;

import java.lang.management.ManagementFactory;
import java.util.function.LongSupplier;

/** Current-thread allocated heap bytes, or -1 when the JVM cannot provide them. */
final class ThreadAllocationMeter {
    private ThreadAllocationMeter() {}

    static LongSupplier create() {
        try {
            if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
                    && bean.isThreadAllocatedMemorySupported()) {
                if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
                return () -> {
                    try { return bean.getThreadAllocatedBytes(Thread.currentThread().threadId()); }
                    catch (UnsupportedOperationException | SecurityException unavailable) { return -1; }
                };
            }
        } catch (UnsupportedOperationException | SecurityException ignored) {
            // Timing and sampling can still work without thread allocation counters.
        }
        return () -> -1;
    }
}
