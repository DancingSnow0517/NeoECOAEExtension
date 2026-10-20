package cn.dancingsnow.neoecoae.gui.computation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/** Menu-local CPU identities survive list changes; removed identities are never reassigned. */
final class CpuSelectionState<T> {
    private final int rows;
    private final Identities<T> identities;
    private List<T> entries = List.of();
    private int selectedSerial = -1;
    private int offset;

    CpuSelectionState(int rows) {
        this(rows, new Identities<>());
    }

    CpuSelectionState(int rows, Identities<T> identities) {
        if (rows < 1) throw new IllegalArgumentException("rows must be positive");
        this.rows = rows;
        this.identities = identities;
    }

    void update(List<T> cpus, Predicate<T> busy, Function<T, String> name) {
        Map<T, Boolean> present = new IdentityHashMap<>();
        List<T> next = new ArrayList<>();
        for (T cpu : cpus) {
            if (present.put(cpu, true) == null) {
                identities.register(cpu);
                next.add(cpu);
            }
        }
        next.sort(Comparator.<T, Boolean>comparing(cpu -> name.apply(cpu) == null)
            .thenComparing(cpu -> name.apply(cpu) == null ? "" : name.apply(cpu))
            .thenComparingInt(this::serial));
        entries = List.copyOf(next);
        if (selected() == null) {
            T fallback = entries.stream().filter(busy).findFirst()
                .orElse(entries.isEmpty() ? null : entries.getFirst());
            selectedSerial = fallback == null ? -1 : serial(fallback);
        }
        scroll(offset);
    }

    boolean select(int serial) {
        if (entries.stream().noneMatch(cpu -> serial(cpu) == serial)) return false;
        selectedSerial = serial;
        return true;
    }

    void scroll(int offset) {
        this.offset = Math.clamp(offset, 0, Math.max(0, entries.size() - rows));
    }

    int serial(T cpu) {
        return identities.serials.getOrDefault(cpu, -1);
    }

    T selected() {
        return entries.stream().filter(cpu -> serial(cpu) == selectedSerial).findFirst().orElse(null);
    }

    List<T> page() {
        return entries.subList(offset, Math.min(entries.size(), offset + rows));
    }

    int selectedSerial() { return selectedSerial; }
    int offset() { return offset; }
    int size() { return entries.size(); }

    /** Shared by both views of one menu, so their different sampling rates cannot change CPU IDs. */
    static final class Identities<T> {
        private final Map<T, Integer> serials = new IdentityHashMap<>();
        private int nextSerial = 1;

        private void register(T cpu) {
            serials.computeIfAbsent(cpu, ignored -> nextSerial++);
        }
    }
}
