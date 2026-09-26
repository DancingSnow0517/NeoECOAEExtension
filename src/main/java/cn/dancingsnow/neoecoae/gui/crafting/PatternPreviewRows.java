package cn.dancingsnow.neoecoae.gui.crafting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/** Client-only row index. Search retains physical rows and only rechecks rows whose contents changed. */
final class PatternPreviewRows {
    static final int COLUMNS = 9;
    record Cell(int entry, int recipe) {}
    private final List<Cell[]> rows = new ArrayList<>();
    private final List<BitSet> matches = new ArrayList<>();
    private final BitSet included = new BitSet();
    private final IntArrayList visible = new IntArrayList();
    private PatternPreviewEntry[] entries = new PatternPreviewEntry[0];
    private int[] physicalRows = new int[0];
    private List<String> terms = List.of();
    private boolean substitution = true, fluidSubstitution = true, emptyRows = true;

    void reset(PatternPreviewEntry[] entries) {
        this.entries = entries;
        rows.clear();
        matches.clear();
        physicalRows = new int[entries.length];
        for (int start = 0; start < entries.length;) {
            long bus = entries[start].busPosition();
            int end = start + 1;
            while (end < entries.length && entries[end].busPosition() == bus) end++;
            Cell[] row = null;
            int previousRow = -1;
            for (int i = start; i < end; i++) {
                int physicalRow = entries[i].physicalSlot() / COLUMNS;
                if (physicalRow != previousRow) {
                    rows.add(row = new Cell[COLUMNS]);
                    previousRow = physicalRow;
                }
                row[entries[i].physicalSlot() % COLUMNS] = new Cell(i, -1);
                physicalRows[i] = rows.size() - 1;
            }
            // Disk recipes are read-only rows of their own; they never change physical slot addressing.
            for (int i = start; i < end; i++) {
                int count = entries[i].diskPatterns().size();
                for (int sub = 0; sub < count; sub++) {
                    if (sub % COLUMNS == 0) rows.add(row = new Cell[COLUMNS]);
                    row[sub % COLUMNS] = new Cell(i, sub);
                }
            }
            start = end;
        }
        for (int i = 0; i < rows.size(); i++) matches.add(new BitSet(COLUMNS));
        refilter();
    }

    void changed(BitSet changed, boolean diskRowsChanged) {
        if (diskRowsChanged) {
            reset(entries);
            return;
        }
        BitSet dirtyRows = new BitSet();
        for (int i = changed.nextSetBit(0); i >= 0; i = changed.nextSetBit(i + 1)) dirtyRows.set(physicalRows[i]);
        boolean membershipChanged = false;
        for (int row = dirtyRows.nextSetBit(0); row >= 0; row = dirtyRows.nextSetBit(row + 1)) {
            boolean before = included.get(row);
            checkRow(row);
            membershipChanged |= before != included.get(row);
        }
        if (membershipChanged) rebuildVisible();
    }

    void filter(String search, boolean substitution, boolean fluidSubstitution, boolean emptyRows) {
        terms = Arrays.stream(search.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(term -> !term.isEmpty()).toList();
        this.substitution = substitution;
        this.fluidSubstitution = fluidSubstitution;
        this.emptyRows = emptyRows;
        refilter();
    }

    private void refilter() {
        included.clear();
        for (int row = 0; row < rows.size(); row++) checkRow(row);
        rebuildVisible();
    }

    private void checkRow(int row) {
        BitSet matched = matches.get(row);
        matched.clear();
        Cell[] cells = rows.get(row);
        boolean hasContents = false;
        for (int col = 0; col < COLUMNS; col++) {
            Cell cell = cells[col];
            if (cell == null) continue;
            PatternPreviewEntry entry = entries[cell.entry];
            boolean occupied = cell.recipe >= 0 || !entry.stack().isEmpty() || entry.auxiliaryDisk();
            hasContents |= occupied;
            byte flags = cell.recipe < 0 ? entry.flags() : entry.diskPatterns().get(cell.recipe).flags();
            String keywords = cell.recipe < 0 ? entry.keywords() : entry.diskPatterns().get(cell.recipe).keywords();
            if ((!substitution && (flags & 1) != 0) || (!fluidSubstitution && (flags & 2) != 0)) continue;
            boolean match = terms.isEmpty() || occupied;
            for (String term : terms) if (!keywords.contains(term)) { match = false; break; }
            if (match && (terms.isEmpty() && substitution && fluidSubstitution || occupied)) matched.set(col);
        }
        included.set(row, !matched.isEmpty() && (emptyRows || hasContents));
    }

    private void rebuildVisible() {
        visible.clear();
        for (int row = included.nextSetBit(0); row >= 0; row = included.nextSetBit(row + 1)) visible.add(row);
    }

    Cell cell(int position) {
        int row = position / COLUMNS;
        return row < 0 || row >= visible.size() ? null : rows.get(visible.getInt(row))[position % COLUMNS];
    }

    boolean matches(int position) {
        int row = position / COLUMNS;
        return row >= 0 && row < visible.size() && matches.get(visible.getInt(row)).get(position % COLUMNS);
    }

    int size() { return visible.size(); }
}
