package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.util.PatternSearchMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A menu starts compact, then keeps each displayed cell bound to its physical source until the search changes. */
final class PatternPreviewRows {
    static final int COLUMNS = 9;
    record Cell(int entry, int recipe) {}
    private record Address(long bus, int slot, int recipe) {}
    private final List<Cell> visible = new ArrayList<>();
    private final Map<Address, Integer> positions = new HashMap<>();
    private PatternPreviewEntry[] entries = new PatternPreviewEntry[0];
    private List<String> inputTerms = List.of(), outputTerms = List.of();

    void reset(PatternPreviewEntry[] entries) {
        this.entries = entries;
        // Full snapshots can change logical indexes or remove a bus. Rebind by physical address, keeping holes
        // for sources that disappeared so neither later rows nor a running Shift sweep change their targets.
        Collections.fill(visible, null);
        for (int start = 0; start < entries.length;) {
            if (entries[start] == null) { start++; continue; }
            long bus = entries[start].busPosition();
            int end = start + 1;
            while (end < entries.length && entries[end] != null && entries[end].busPosition() == bus) end++;
            for (int i = start; i < end; i++) updateCell(i, -1);
            // Disk recipes keep their read-only recipe address; the disk icon is not itself a pattern.
            for (int i = start; i < end; i++) {
                for (int recipe = 0; recipe < entries[i].diskPatterns().size(); recipe++) {
                    updateCell(i, recipe);
                }
            }
            start = end;
        }
    }

    void changed(BitSet changed, boolean diskRowsChanged) {
        if (diskRowsChanged) {
            reset(entries);
            return;
        }
        for (int i = changed.nextSetBit(0); i >= 0; i = changed.nextSetBit(i + 1)) {
            updateCell(i, -1);
        }
    }

    void filter(String inputs, String output) {
        List<String> nextInputs = terms(inputs), nextOutput = terms(output);
        // Snapshot refreshes also call filter; only a user changing the query may compact an open view.
        if (inputTerms.equals(nextInputs) && outputTerms.equals(nextOutput)) return;
        inputTerms = nextInputs;
        outputTerms = nextOutput;
        visible.clear();
        positions.clear();
        reset(entries);
    }

    private static List<String> terms(String query) {
        return Arrays.stream(query.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(term -> !term.isEmpty()).toList();
    }

    private static boolean matchesTerms(List<String> terms, String keywords) {
        return !terms.isEmpty() && terms.stream().allMatch(term -> term.startsWith("@")
                ? keywords.contains(term) : PatternSearchMatcher.contains(keywords, term));
    }

    private void updateCell(int index, int recipe) {
        PatternPreviewEntry entry = entries[index];
        if (entry == null) return;
        Address address = new Address(entry.busPosition(), entry.physicalSlot(), recipe);
        Integer position = positions.get(address);
        Cell cell = new Cell(index, recipe);
        if (position != null) {
            // Live contents may become empty or stop matching the query; their original cell still belongs to
            // this source. A physical slot replaced by a read-only disk must not expose the disk icon itself.
            visible.set(position, recipe < 0 && entry.auxiliaryDisk() ? null : cell);
        } else if (matches(cell)) {
            positions.put(address, visible.size());
            visible.add(cell);
        }
    }

    private boolean matches(Cell cell) {
        PatternPreviewEntry entry = entries[cell.entry];
        var recipe = cell.recipe < 0 ? null : entry.diskPatterns().get(cell.recipe);
        boolean occupied = recipe == null ? !entry.auxiliaryDisk() && !entry.stack().isEmpty() : !recipe.stack().isEmpty();
        String inputs = recipe == null ? entry.inputKeywords() : recipe.inputKeywords();
        String output = recipe == null ? entry.outputKeywords() : recipe.outputKeywords();
        boolean noSearch = inputTerms.isEmpty() && outputTerms.isEmpty();
        // Either active branch may match; an empty query never makes the other branch match everything.
        return occupied && (noSearch || matchesTerms(inputTerms, inputs) || matchesTerms(outputTerms, output));
    }

    Cell cell(int position) {
        return position < 0 || position >= visible.size() ? null : visible.get(position);
    }

    int size() { return (visible.size() + COLUMNS - 1) / COLUMNS; }
}
