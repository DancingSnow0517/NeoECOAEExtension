package cn.dancingsnow.neoecoae.gui.computation;

import java.util.List;

/** Switches acknowledged pages by whole rows; input always uses the last rendered page. */
final class ComputationScrollPage<T> {
    private final int rows, columns;
    private Page<T> pending = new Page<>(0, 0, List.of());
    private Page<T> rendered = pending;

    ComputationScrollPage(int rows, int columns) {
        this.rows = rows;
        this.columns = columns;
    }

    void accept(int offset, int pageStart, List<T> page) {
        pending = new Page<>(offset / columns, pageStart, List.copyOf(page));
    }

    int advance() {
        rendered = pending;
        return rendered.offset;
    }

    int size() { return rendered.entries.size(); }
    T get(int index) { return rendered.entries.get(index); }
    int row(int index) { return (rendered.start + index) / columns - rendered.offset; }
    int column(int index) { return (rendered.start + index) % columns; }

    T hit(double y, int column, int stride, int height) {
        if (y < 0 || y >= rows * stride - 1 || column < 0 || column >= columns) return null;
        double contentY = y + rendered.offset * stride;
        int row = (int) Math.floor(contentY / stride);
        if (contentY - row * stride >= height) return null;
        int index = row * columns + column - rendered.start;
        return index >= 0 && index < rendered.entries.size() ? rendered.entries.get(index) : null;
    }

    void reset() { rendered = pending = new Page<>(0, 0, List.of()); }

    private record Page<T>(int offset, int start, List<T> entries) {}
}
