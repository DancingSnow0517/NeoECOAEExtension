package cn.dancingsnow.neoecoae.gui.computation;

import java.util.List;
import java.util.function.LongSupplier;

/** Animates acknowledged pages within a bounded buffer; input uses the last rendered position. */
final class ComputationScrollPage<T> {
    private static final long DURATION_NS = 160_000_000L;
    private final int rows, columns;
    private final LongSupplier clock;
    private List<T> entries = List.of();
    private int start;
    private double from, target, rendered;
    private long started;
    private boolean initialized;

    ComputationScrollPage(int rows, int columns) {
        this(rows, columns, System::nanoTime);
    }

    ComputationScrollPage(int rows, int columns, LongSupplier clock) {
        this.rows = rows;
        this.columns = columns;
        this.clock = clock;
    }

    void accept(int offset, int pageStart, List<T> page, boolean reset) {
        double next = offset / (double) columns;
        long now = clock.getAsLong();
        double current = position(now);
        entries = List.copyOf(page);
        start = pageStart;
        if (!initialized || reset) {
            from = target = rendered = next;
            initialized = true;
        } else if (next != target) {
            // A thumb jump can skip many pages. Slide only through the received buffer.
            double low = pageStart / (double) columns;
            double high = Math.max(low, Math.ceil((pageStart + page.size()) / (double) columns) - rows);
            from = Math.max(low, Math.min(high, current));
            target = next;
            started = now;
        }
    }

    double advance() {
        return rendered = position(clock.getAsLong());
    }

    private double position(long now) {
        double t = Math.max(0, Math.min(1, (now - started) / (double) DURATION_NS));
        return from + (target - from) * t * t * (3 - 2 * t);
    }

    int size() { return entries.size(); }
    T get(int index) { return entries.get(index); }
    double row(int index) { return (start + index) / columns - rendered; }
    int column(int index) { return (start + index) % columns; }

    T hit(double y, int column, int stride, int height) {
        if (y < 0 || y >= rows * stride - 1 || column < 0 || column >= columns) return null;
        double contentY = y + rendered * stride;
        int row = (int) Math.floor(contentY / stride);
        if (contentY - row * stride >= height) return null;
        int index = row * columns + column - start;
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    void reset() { initialized = false; }
}
