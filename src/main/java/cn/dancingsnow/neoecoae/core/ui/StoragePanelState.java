package cn.dancingsnow.neoecoae.core.ui;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/** Platform-independent storage view state. Text, cell icons and row payloads are supplied by the adapter. */
public record StoragePanelState<T, C extends StorageCellView, L extends StorageTypeView>(T title, int tier, boolean formed, boolean infinite, boolean migrating,
    long energy, long maxEnergy, long drain, String usedBytes, double usage, int cellCount, int cellOffset,
    List<C> cells, List<L> types, String domainId, int missingMembers, boolean componentPresent,
    BigInteger totalBytes) {
    public static int pageOffset(int requested, int total, int pageSize) {
        if (total <= 0 || pageSize <= 0) return 0;
        int last = (total - 1) / pageSize * pageSize;
        return Math.clamp(requested, 0, last) / pageSize * pageSize;
    }

    /** Each page contains whole columns of all three rows; short structures leave gaps in every row. */
    public static <C> List<C> cellPage(List<C> all, int offset, int pageSize, C empty) {
        if (all.isEmpty()) return List.of();
        int columns = (all.size() + 2) / 3;
        int pageColumns = pageSize / 3;
        int firstColumn = offset / 3;
        List<C> result = new ArrayList<>(pageSize);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < pageColumns; column++) {
                int source = row * columns + firstColumn + column;
                result.add(firstColumn + column < columns && source < all.size() ? all.get(source) : empty);
            }
        }
        return List.copyOf(result);
    }

    public static double usage(Iterable<? extends StorageCellView> cells) {
        BigInteger used = BigInteger.ZERO, total = BigInteger.ZERO;
        for (StorageCellView cell : cells) {
            if (cell.infinite() || cell.totalBytes() < 0) return 1;
            // Special cells report Long.MAX_VALUE instead of a comparable finite byte capacity.
            if (cell.totalBytes() == Long.MAX_VALUE) continue;
            used = used.add(BigInteger.valueOf(Math.max(0, cell.usedBytes())));
            total = total.add(BigInteger.valueOf(Math.max(0, cell.totalBytes())));
        }
        if (total.signum() == 0) return 0;
        return Math.clamp(new BigDecimal(used).divide(new BigDecimal(total), MathContext.DECIMAL64).doubleValue(), 0, 1);
    }

    public static BigInteger totalCapacity(List<? extends StorageCellView> cells) {
        return cells.stream().filter(cell -> cell.totalBytes() != Long.MAX_VALUE)
            .map(cell -> BigInteger.valueOf(Math.max(0, cell.totalBytes())))
            .reduce(BigInteger.ZERO, BigInteger::add);
    }

    public L type(int id) { return types.stream().filter(type -> type.id() == id).findFirst().orElse(null); }
    public boolean unbounded() { return infinite || migrating; }
}
