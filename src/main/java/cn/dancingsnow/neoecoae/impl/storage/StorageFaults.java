package cn.dancingsnow.neoecoae.impl.storage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded diagnostics; repeated failures retain their identity and do not flood the server log. */
public final class StorageFaults {
    private static final Logger LOGGER = LoggerFactory.getLogger(StorageFaults.class);
    public record Fault(UUID id, String component, String reason, long firstTick, long lastTick, long occurrences,
                        String stackTrace) {}
    private final Map<String, Fault> faults = new LinkedHashMap<>();

    public void report(String component, String reason, long tick) {
        report(component, reason, tick, null);
    }

    public void report(String component, String reason, long tick, Throwable cause) {
        Fault previous = faults.get(component);
        String stackTrace = previous == null ? null : previous.stackTrace();
        if (cause != null && (previous == null || !previous.reason().equals(reason))) {
            var trace = new java.io.StringWriter();
            cause.printStackTrace(new java.io.PrintWriter(trace));
            stackTrace = trace.toString();
            if (stackTrace.length() > 32_768) stackTrace = stackTrace.substring(0, 32_768);
        }
        if (previous == null && faults.size() >= 64) faults.remove(faults.keySet().iterator().next());
        Fault next = new Fault(previous == null ? UUID.randomUUID() : previous.id(), component,
            reason == null ? "Unknown failure" : reason, previous == null ? tick : previous.firstTick(), tick,
            previous == null ? 1L : previous.occurrences() + 1L, stackTrace);
        faults.put(component, next);
        if (previous == null || !previous.reason().equals(reason) || tick - previous.lastTick() >= 200L) {
            LOGGER.error("ECO storage fault {} [{}]: {}", next.id(), component, reason, cause);
        }
    }

    public void recovered(String component) { faults.remove(component); }
    public List<Fault> snapshot() { return List.copyOf(faults.values()); }
}
