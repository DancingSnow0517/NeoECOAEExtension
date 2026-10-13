package cn.dancingsnow.neoecoae.core.concurrent;

import org.jetbrains.annotations.Nullable;

/**
 * A per-thread value that is pushed for the duration of a block and restored to the previous value afterwards.
 * Closing the outermost scope removes the thread-local entry entirely.
 */
public final class ThreadScope<T> {
    private final ThreadLocal<T> local = new ThreadLocal<>();

    @Nullable
    public T current() {
        return local.get();
    }

    /** Makes {@code value} current on this thread until the returned scope is closed. */
    public Scope open(@Nullable T value) {
        T previous = local.get();
        local.set(value);
        return () -> {
            if (previous == null) local.remove();
            else local.set(previous);
        };
    }

    /** Restores the value that was current when the scope was opened. Closing does not throw. */
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
