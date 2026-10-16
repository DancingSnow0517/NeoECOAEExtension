package cn.dancingsnow.neoecoae.core.concurrent;

/** Cooperative cancellation independent of a loader, world, or execution thread. */
@FunctionalInterface
public interface Cancellation {
    Cancellation NONE = () -> {};
    Cancellation INTERRUPTIBLE = () -> {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("ECO planning request cancelled");
    };
    void checkpoint() throws InterruptedException;
}
