package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Client-local search/filter/scroll state over the crafting interface's virtualized pattern preview window. */
final class CraftingPatternPreviewState {
    private final ECOMachineInterfaceBlockEntity<?> craftingInterface;
    private final IItemHandlerModifiable handler;
    private final boolean[] highlightedSlots =
            new boolean[ECOMachineInterfaceBlockEntity.PATTERN_INTERFACE_VISIBLE_SLOTS];
    private final Scroller.Vertical scroller = new Scroller.Vertical();
    private final List<Integer> visibleSlots = new ArrayList<>();
    private int[] appliedView = new int[0];
    private String search = "";
    private boolean showSubstitution = true;
    private boolean showFluidSubstitution = true;
    private int lastRevision = Integer.MIN_VALUE;
    private int requestedIndexRevision = Integer.MIN_VALUE;
    private boolean filterDirty = true;

    CraftingPatternPreviewState(ECOMachineInterfaceBlockEntity<?> craftingInterface, IItemHandlerModifiable handler) {
        this.craftingInterface = craftingInterface;
        this.handler = handler;
        scroller.setOnValueChanged(value -> setScrollRow(Math.round(value)));
    }

    Scroller.Vertical scroller() {
        return scroller;
    }

    PatternItemSlot createSlot(int visualSlot) {
        ItemHandlerSlot itemHandlerSlot = new ItemHandlerSlot(handler, visualSlot).addChangeListener(this::markContentDirty);
        PatternItemSlot slot = ClientUIBridge.call("createPatternSlot", Slot.class, itemHandlerSlot,
                PatternItemSlot.class, () -> new PatternItemSlot(itemHandlerSlot));
        slot.highlighted(() -> highlightedSlots[visualSlot]);
        return slot;
    }

    /** Slot packets and managed revision packets can arrive in either order. */
    private void markContentDirty() {
        filterDirty = true;
    }

    boolean showsSubstitutionPatterns() {
        return showSubstitution;
    }

    boolean showsFluidSubstitutionPatterns() {
        return showFluidSubstitution;
    }

    void toggleSubstitutionPatterns() {
        showSubstitution = !showSubstitution;
        resetFilter();
    }

    void toggleFluidSubstitutionPatterns() {
        showFluidSubstitution = !showFluidSubstitution;
        resetFilter();
    }

    void setSearch(String value) {
        String next = value == null ? "" : value.substring(0, Math.min(value.length(), CraftingInterfaceUI.PREVIEW_QUERY_MAX_LENGTH));
        if (!search.equals(next)) {
            search = next;
            resetFilter();
        }
    }

    private void resetFilter() {
        setScrollRow(0);
        filterDirty = true;
    }

    void refresh() {
        if (craftingInterface.getLevel() == null || !craftingInterface.getLevel().isClientSide) {
            return;
        }
        int revision = craftingInterface.getPatternContentRevision();
        var searchIndex = craftingInterface.getClientPatternSearchIndex();
        if (searchIndex.revision() != revision) {
            if (requestedIndexRevision != revision) {
                requestedIndexRevision = revision;
                craftingInterface.rpcToServer("requestPatternSearchIndex", searchIndex.revision());
            }
            return;
        }
        if (!filterDirty && revision == lastRevision) {
            return;
        }
        rebuildFilter(searchIndex);
        lastRevision = revision;
        filterDirty = false;
        int rowCount = getRowCount();
        scroller.setRange(0, getMaxScrollRow());
        scroller.setScrollBarSize(Math.min(100F, CraftingInterfaceUI.PREVIEW_ROWS * 100F / Math.max(1, rowCount)));
        updateSlots();
    }

    private void rebuildFilter(ECOMachineInterfaceBlockEntity.PatternSearchIndex searchIndex) {
        visibleSlots.clear();
        List<String> terms = tokenize(search);
        for (int patternIndex = 0; patternIndex < searchIndex.size(); patternIndex++) {
            byte flags = searchIndex.flags(patternIndex);
            if (!passesSubstitutionFilter(flags)) {
                continue;
            }
            if ((flags & 4) == 0) {
                if (terms.isEmpty()) {
                    visibleSlots.add(patternIndex);
                }
                continue;
            }
            if (terms.isEmpty() || matchesSearch(searchIndex.keywords(patternIndex), terms)) {
                visibleSlots.add(patternIndex);
            }
        }
    }

    private boolean passesSubstitutionFilter(byte flags) {
        return (showSubstitution || (flags & 1) == 0)
                && (showFluidSubstitution || (flags & 2) == 0);
    }

    private static boolean matchesSearch(String keywords, List<String> terms) {
        return terms.stream().allMatch(keywords::contains);
    }

    private static List<String> tokenize(String value) {
        return Arrays.stream(value.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(term -> !term.isEmpty()).toList();
    }

    private void updateSlots() {
        Arrays.fill(highlightedSlots, false);
        int start = Math.min(getScrollRow() * CraftingInterfaceUI.PREVIEW_COLUMNS, visibleSlots.size());
        int end = Math.min(start + CraftingInterfaceUI.PREVIEW_COLUMNS * CraftingInterfaceUI.PREVIEW_ROWS, visibleSlots.size());
        int[] view = new int[end - start];
        for (int offset = start; offset < end; offset++) {
            int patternIndex = visibleSlots.get(offset);
            int visualOffset = offset - start;
            view[visualOffset] = patternIndex;
            highlightedSlots[visualOffset] = !search.isBlank();
        }
        if (!Arrays.equals(appliedView, view)) {
            appliedView = view;
            CompoundTag payload = new CompoundTag();
            payload.putIntArray("slots", view);
            craftingInterface.rpcToServer("setPatternInterfaceView", payload);
        }
    }

    private int getRowCount() {
        return (visibleSlots.size() + CraftingInterfaceUI.PREVIEW_COLUMNS - 1) / CraftingInterfaceUI.PREVIEW_COLUMNS;
    }

    int getMaxScrollRow() {
        return Math.max(0, getRowCount() - CraftingInterfaceUI.PREVIEW_ROWS);
    }

    private int getScrollRow() {
        Float value = scroller.getValue();
        return value == null ? 0 : Math.round(value);
    }

    private void setScrollRow(int value) {
        int next = Math.clamp(value, 0, getMaxScrollRow());
        if (next != getScrollRow()) {
            scroller.setValue((float) next, false);
            updateSlots();
        }
    }

    void scroll(int delta) {
        setScrollRow(getScrollRow() + delta);
    }
}
