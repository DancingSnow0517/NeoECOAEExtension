package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.util.PatternSearchMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/** Compact browse rows contain actual patterns only; each cell retains its original operation address. */
final class PatternPreviewRows {
    static final int COLUMNS = 9;
    record Cell(int entry, int recipe) {}
    private final List<Cell> cells = new ArrayList<>();
    private final BitSet included = new BitSet();
    private final IntArrayList visible = new IntArrayList();
    private PatternPreviewEntry[] entries = new PatternPreviewEntry[0];
    private int[] physicalCells = new int[0];
    private List<String> inputTerms = List.of(), outputTerms = List.of();

    void reset(PatternPreviewEntry[] entries) {
        this.entries = entries;
        cells.clear();
        physicalCells = new int[entries.length];
        Arrays.fill(physicalCells, -1);
        for (int start = 0; start < entries.length;) {
            if (entries[start] == null) { start++; continue; }
            long bus = entries[start].busPosition();
            int end = start + 1;
            while (end < entries.length && entries[end] != null && entries[end].busPosition() == bus) end++;
            for (int i = start; i < end; i++) {
                physicalCells[i] = cells.size();
                cells.add(new Cell(i, -1));
            }
            // Disk recipes keep their read-only recipe address; the disk icon is not itself a pattern.
            for (int i = start; i < end; i++) {
                for (int recipe = 0; recipe < entries[i].diskPatterns().size(); recipe++) {
                    cells.add(new Cell(i, recipe));
                }
            }
            start = end;
        }
        refilter();
    }

    void changed(BitSet changed, boolean diskRowsChanged) {
        if (diskRowsChanged) {
            reset(entries);
            return;
        }
        boolean membershipChanged = false;
        for (int i = changed.nextSetBit(0); i >= 0; i = changed.nextSetBit(i + 1)) {
            int cell = physicalCells[i];
            boolean before = included.get(cell);
            checkCell(cell);
            membershipChanged |= before != included.get(cell);
        }
        if (membershipChanged) rebuildVisible();
    }

    void filter(String inputs, String output) {
        inputTerms = terms(inputs);
        outputTerms = terms(output);
        refilter();
    }

    private static List<String> terms(String query) {
        return Arrays.stream(query.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(term -> !term.isEmpty()).toList();
    }

    private static boolean matchesTerms(List<String> terms, String keywords) {
        return !terms.isEmpty() && terms.stream().allMatch(term -> term.startsWith("@")
                ? keywords.contains(term) : PatternSearchMatcher.contains(keywords, term));
    }

    private void refilter() {
        included.clear();
        for (int cell = 0; cell < cells.size(); cell++) checkCell(cell);
        rebuildVisible();
    }

    private void checkCell(int index) {
        Cell cell = cells.get(index);
        PatternPreviewEntry entry = entries[cell.entry];
        var recipe = cell.recipe < 0 ? null : entry.diskPatterns().get(cell.recipe);
        boolean occupied = recipe == null ? !entry.auxiliaryDisk() && !entry.stack().isEmpty() : !recipe.stack().isEmpty();
        String inputs = recipe == null ? entry.inputKeywords() : recipe.inputKeywords();
        String output = recipe == null ? entry.outputKeywords() : recipe.outputKeywords();
        boolean noSearch = inputTerms.isEmpty() && outputTerms.isEmpty();
        // Either active branch may match; an empty query never makes the other branch match everything.
        included.set(index, occupied
                && (noSearch || matchesTerms(inputTerms, inputs) || matchesTerms(outputTerms, output)));
    }

    private void rebuildVisible() {
        visible.clear();
        for (int cell = included.nextSetBit(0); cell >= 0; cell = included.nextSetBit(cell + 1)) visible.add(cell);
    }

    Cell cell(int position) {
        return position < 0 || position >= visible.size() ? null : cells.get(visible.getInt(position));
    }

    int size() { return (visible.size() + COLUMNS - 1) / COLUMNS; }
}
