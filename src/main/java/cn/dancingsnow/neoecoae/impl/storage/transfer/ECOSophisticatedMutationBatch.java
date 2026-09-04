package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.stacks.AEKey;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/** One main-thread ECO scheduler batch. Nested scopes share one identity-based dirty set. */
public final class ECOSophisticatedMutationBatch {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
    private static final Scope NOOP = new Scope(null, false);
    private static final Map<Object, FailedFlush> FAILED_FLUSHES = new WeakHashMap<>();

    private ECOSophisticatedMutationBatch() {
    }

    public static Scope open(FlushFailureSink failureSink) {
        State state = CURRENT.get();
        if (state == null) {
            retryFailedFlushes(failureSink);
            state = new State(failureSink);
            CURRENT.set(state);
        }
        state.depth++;
        return new Scope(state, true);
    }

    public static Scope noopScope() {
        return NOOP;
    }

    public static boolean deferSave(Object handler) {
        State state = CURRENT.get();
        if (state == null || !(handler instanceof ECOSophisticatedHandlerBridge)) return false;
        state.dirty.computeIfAbsent(handler, ignored -> new LinkedHashSet<>()).add(state.currentKey);
        ECOSophisticatedMetrics.DEFERRED_SAVES.incrementAndGet();
        return true;
    }

    public static void setCurrentKey(AEKey key) {
        State state = CURRENT.get();
        if (state != null) state.currentKey = key;
    }

    public interface FlushFailureSink {
        void onFlushFailure(Set<AEKey> keys, Throwable failure);

        default void onFlushSuccess(Set<AEKey> keys) {
        }
    }

    private static void retryFailedFlushes(FlushFailureSink currentSink) {
        synchronized (FAILED_FLUSHES) {
            var iterator = FAILED_FLUSHES.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                try {
                    ((ECOSophisticatedHandlerBridge) entry.getKey()).neoecoae$saveInventory();
                    FailedFlush failed = entry.getValue();
                    iterator.remove();
                    ECOSophisticatedMetrics.BATCH_FLUSHES.incrementAndGet();
                    FlushFailureSink sink = failed.sink().get();
                    if (sink != null) sink.onFlushSuccess(failed.keys());
                    if (currentSink != sink) currentSink.onFlushSuccess(failed.keys());
                } catch (Throwable failure) {
                    ECOSophisticatedMetrics.FLUSH_FAILURES.incrementAndGet();
                    FailedFlush failed = entry.getValue();
                    FlushFailureSink sink = failed.sink().get();
                    if (sink != null) sink.onFlushFailure(failed.keys(), failure);
                    if (currentSink != sink) currentSink.onFlushFailure(failed.keys(), failure);
                }
            }
        }
    }

    private static final class State {
        private final FlushFailureSink failureSink;
        private final Map<Object, Set<AEKey>> dirty = new IdentityHashMap<>();
        private int depth;
        private AEKey currentKey;

        private State(FlushFailureSink failureSink) {
            this.failureSink = failureSink;
        }
    }

    public static final class Scope implements AutoCloseable {
        private State state;
        private final boolean active;

        private Scope(State state, boolean active) {
            this.state = state;
            this.active = active;
        }

        @Override
        public void close() {
            if (!active || state == null) return;
            State closing = state;
            state = null;
            if (--closing.depth > 0) return;
            CURRENT.remove();
            for (var entry : new ArrayList<>(closing.dirty.entrySet())) {
                try {
                    ((ECOSophisticatedHandlerBridge) entry.getKey()).neoecoae$saveInventory();
                    ECOSophisticatedMetrics.BATCH_FLUSHES.incrementAndGet();
                } catch (Throwable failure) {
                    ECOSophisticatedMetrics.FLUSH_FAILURES.incrementAndGet();
                    Set<AEKey> keys = new LinkedHashSet<>(entry.getValue());
                    keys.remove(null);
                    Set<AEKey> immutableKeys = Set.copyOf(keys);
                    synchronized (FAILED_FLUSHES) {
                        FAILED_FLUSHES.put(entry.getKey(),
                            new FailedFlush(immutableKeys, new WeakReference<>(closing.failureSink)));
                    }
                    closing.failureSink.onFlushFailure(immutableKeys, failure);
                }
            }
        }
    }

    private record FailedFlush(Set<AEKey> keys, WeakReference<FlushFailureSink> sink) {
    }
}
