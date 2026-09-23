package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** The complete preview model and its viewport belong exclusively to this client menu. */
final class CraftingPatternPreviewState {
    private final ECOMachineInterfaceBlockEntity<?> craftingInterface;
    private final Player player;
    private final PatternItemSlot[] slots = new PatternItemSlot[ECOMachineInterfaceBlockEntity.PATTERN_INTERFACE_VISIBLE_SLOTS];
    private final int[] displayed = new int[slots.length];
    /** Which disk recipe the row currently shows, or {@code -1} when it shows the entry itself. */
    private final int[] displayedSubs = new int[slots.length];
    private final IntArrayList visibleSlots = new IntArrayList();
    /**
     * Which recipe inside that entry each visible row shows, or {@code -1} when the row is the entry itself.
     * Parallel to {@link #visibleSlots}.
     *
     * <p>A row is a physical slot, so a disk has no row of its own to spread its recipes over. Keeping the two
     * lists separate means one entry can occupy several rows without the entry index meaning anything different
     * than it did before.</p>
     */
    private final IntArrayList visibleSubs = new IntArrayList();
    // The UI owns this callback; the block entity must not retain a closed screen.
    private final Consumer<CompoundTag> receiver = this::receive;
    private PatternPreviewEntry[] entries = new PatternPreviewEntry[0];
    private PatternPreviewEntry[] pending;
    private final Int2ObjectOpenHashMap<PatternPreviewEntry> pendingChanges = new Int2ObjectOpenHashMap<>();
    private boolean pendingFull;
    private int pendingRevision;
    private int revision = -1;
    private int menuId = -1;
    private String search = "";
    private List<String> searchTerms = List.of();
    private boolean showSubstitution = true;
    private boolean showFluidSubstitution = true;
    private int scrollRow;
    private final List<PatternPreviewEntry> quickMoveTargets = new ArrayList<>();
    private boolean quickMoveDrag;
    private int quickMoveRevision = -1;
    private int lastQuickMoveVisualSlot = -1;

    CraftingPatternPreviewState(ECOMachineInterfaceBlockEntity<?> craftingInterface, Player player) {
        this.craftingInterface = craftingInterface;
        this.player = player;
        Arrays.fill(displayed, -1);
        if (player.level().isClientSide) craftingInterface.getPatternPreviewSync().listen(receiver);
    }

    PatternItemSlot createSlot(int visualSlot) {
        LocalSlot local = new LocalSlot();
        PatternItemSlot slot = ClientUIBridge.call("createPatternSlot", Slot.class, local,
                PatternItemSlot.class, () -> new PatternItemSlot(local));
        slots[visualSlot] = slot;
        slot.highlighted(() -> displayed[visualSlot] >= 0 && !search.isBlank());
        slot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 0 && event.isShiftDown()) {
                beginQuickMoveDrag(visualSlot);
                event.hasHandler = true;
                event.stopImmediatePropagation();
                return;
            }
            if (event.button == 0 || event.button == 1) {
                act(visualSlot, event.isShiftDown() ? 1 : 0, event.button);
                event.hasHandler = true;
                event.stopImmediatePropagation();
            }
        });
        slot.addEventListener(UIEvents.MOUSE_ENTER, event -> {
            if (quickMoveDrag && slot.isMouseDown(0) && event.isShiftDown()) enterQuickMoveDrag(visualSlot);
        });
        slot.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.isShiftDown() && event.deltaY != 0) {
                act(visualSlot, 1, 0);
                event.stopImmediatePropagation();
            }
        });
        return slot;
    }

    private void act(int visualSlot, int action, int button) {
        if (!player.level().isClientSide || revision < 0 || pending != null
                || menuId != player.containerMenu.containerId) return;
        int index = displayed[visualSlot];
        if (index < 0 || index >= entries.length) return;
        PatternPreviewEntry entry = entries[index];
        // Both a recipe from inside a container and the container's own row are read-only here. Taking one back
        // out is a write to contents this screen does not own - it changes what the integration that owns them
        // charges for, and it moves the recipes behind it up one - and the pattern access terminal is where that
        // belongs: it hands the container over to that integration and re-scans its rows per revision. Offering
        // it here as well would mean two ways to change the same contents, from a screen that only ever reads.
        if (displayedSubs[visualSlot] >= 0 || entry.auxiliaryDisk()) return;
        CompoundTag payload = new CompoundTag();
        payload.putInt("menu", menuId);
        payload.putInt("revision", revision);
        payload.putLong("bus", entry.busPosition());
        payload.putInt("slot", entry.physicalSlot());
        payload.putInt("action", action);
        payload.putInt("button", button);
        craftingInterface.rpcToServer("actOnPatternPreview", payload);
    }

    void endQuickMoveDrag() {
        if (!quickMoveDrag) return;
        quickMoveDrag = false;
        lastQuickMoveVisualSlot = -1;
        flushQuickMoveBatch();
    }

    void endQuickMoveDragIfReleased(boolean leftMouseDown) {
        if (quickMoveDrag && !leftMouseDown) endQuickMoveDrag();
    }

    private void beginQuickMoveDrag(int visualSlot) {
        quickMoveTargets.clear();
        quickMoveDrag = true;
        quickMoveRevision = revision;
        lastQuickMoveVisualSlot = visualSlot;
        queueQuickMove(visualSlot);
    }

    private void enterQuickMoveDrag(int visualSlot) {
        if (visualSlot == lastQuickMoveVisualSlot) return;
        lastQuickMoveVisualSlot = visualSlot;
        queueQuickMove(visualSlot);
    }

    private void queueQuickMove(int visualSlot) {
        if (!player.level().isClientSide || quickMoveRevision < 0 || pending != null
                || menuId != player.containerMenu.containerId) return;
        int index = displayed[visualSlot];
        if (index < 0 || index >= entries.length) return;
        // As in act(): neither a disk's own slot nor one of its recipes is something this list can move.
        if (displayedSubs[visualSlot] >= 0 || entries[index].auxiliaryDisk()) return;
        PatternPreviewEntry entry = entries[index];
        if (entry.stack().isEmpty()) return;
        for (PatternPreviewEntry queued : quickMoveTargets) {
            if (queued.busPosition() == entry.busPosition() && queued.physicalSlot() == entry.physicalSlot()) return;
        }
        if (quickMoveTargets.size() < ECOMachineInterfaceBlockEntity.PATTERN_INTERFACE_VISIBLE_SLOTS) {
            quickMoveTargets.add(entry);
        }
    }

    private void flushQuickMoveBatch() {
        if (quickMoveTargets.isEmpty()) return;
        if (!player.level().isClientSide || quickMoveRevision < 0
                || menuId != player.containerMenu.containerId) {
            quickMoveTargets.clear();
            return;
        }
        CompoundTag payload = new CompoundTag();
        payload.putInt("menu", menuId);
        payload.putInt("revision", quickMoveRevision);
        ListTag targets = new ListTag();
        for (PatternPreviewEntry entry : quickMoveTargets) {
            CompoundTag target = new CompoundTag();
            target.putLong("bus", entry.busPosition());
            target.putInt("slot", entry.physicalSlot());
            target.put("stack", entry.stack().saveOptional(player.level().registryAccess()));
            targets.add(target);
        }
        payload.put("targets", targets);
        quickMoveTargets.clear();
        craftingInterface.rpcToServer("quickMovePatternPreview", payload);
    }

    boolean quickMoveFromInventory(Slot source) {
        if (!(source.getItem().getItem() instanceof appeng.crafting.pattern.EncodedPatternItem<?>)) return false;
        if (!player.level().isClientSide || revision < 0 || pending != null
                || menuId != player.containerMenu.containerId || !player.containerMenu.getCarried().isEmpty()) return false;
        for (PatternPreviewEntry entry : entries) {
            // A disk's slot reads as empty here too, so skipping on the stack alone would aim the insert at the
            // disk: the server refuses it (the slot is not empty), but this method has already reported success,
            // so the click would be swallowed and placing a pattern would appear to do nothing.
            if (entry.auxiliaryDisk() || !entry.stack().isEmpty()) continue;
            CompoundTag payload = new CompoundTag();
            payload.putInt("menu", menuId);
            payload.putInt("revision", revision);
            payload.putLong("bus", entry.busPosition());
            payload.putInt("slot", entry.physicalSlot());
            payload.putInt("action", 2);
            payload.putInt("button", 0);
            payload.putInt("sourceSlot", source.getContainerSlot());
            payload.put("sourceStack", source.getItem().saveOptional(player.level().registryAccess()));
            craftingInterface.rpcToServer("actOnPatternPreview", payload);
            return true;
        }
        return false;
    }

    private void receive(CompoundTag payload) {
        if (!player.level().isClientSide || craftingInterface.getLevel() == null
                || payload.getInt("menu") != player.containerMenu.containerId) return;
        boolean full = payload.getBoolean("full");
        if (payload.getBoolean("first")) {
            pending = null;
            pendingChanges.clear();
            if (!full && (payload.getInt("base") != revision || menuId != payload.getInt("menu"))) return;
            int size = payload.getInt("size");
            if (size < 0 || (!full && size != entries.length)) return;
            pendingFull = full;
            pending = full ? new PatternPreviewEntry[size] : entries;
            pendingRevision = payload.getInt("revision");
            menuId = payload.getInt("menu");
        }
        if (pending == null || payload.getInt("revision") != pendingRevision) return;
        var batch = payload.getList("entries", Tag.TAG_COMPOUND);
        for (int index = 0; index < batch.size(); index++) {
            CompoundTag entry = batch.getCompound(index);
            int logicalSlot = entry.getInt("index");
            if (logicalSlot < 0 || logicalSlot >= pending.length) {
                pending = null;
                return;
            }
            PatternPreviewEntry decoded = PatternPreviewEntry.decode(entry, craftingInterface.getLevel().registryAccess());
            if (pendingFull) pending[logicalSlot] = decoded;
            else pendingChanges.put(logicalSlot, decoded);
        }
        if (!payload.getBoolean("last")) return;
        if (pendingFull) {
            for (PatternPreviewEntry entry : pending) {
                if (entry == null) {
                    pending = null;
                    return;
                }
            }
            entries = pending;
        } else {
            boolean rowsChanged = false;
            for (var change : pendingChanges.int2ObjectEntrySet()) {
                int index = change.getIntKey();
                PatternPreviewEntry before = entries[index];
                boolean wasVisible = matchesEntry(before, -1);
                entries[index] = change.getValue();
                boolean isVisible = matchesEntry(entries[index], -1);
                // A disk's entry owns a row per recipe, so a change to it alters how many rows the list has -
                // which this per-entry bookkeeping cannot express. Those rebuild the filter once the whole
                // batch has been applied, rather than mid-loop against a half-updated array.
                if (!before.diskPatterns().isEmpty() || !entries[index].diskPatterns().isEmpty()) {
                    rowsChanged = true;
                    continue;
                }
                if (wasVisible != isVisible) updateMembership(index, isVisible);
            }
            pendingChanges.clear();
            if (rowsChanged) rebuildFilter();
        }
        pending = null;
        revision = pendingRevision;
        if (pendingFull) rebuildFilter();
        else {
            scrollRow = Math.clamp(scrollRow, 0, getMaxScrollRow());
            updateSlots();
        }
    }

    boolean showsSubstitutionPatterns() { return showSubstitution; }
    boolean showsFluidSubstitutionPatterns() { return showFluidSubstitution; }

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
        scrollRow = 0;
        rebuildFilter();
    }

    private void rebuildFilter() {
        visibleSlots.clear();
        visibleSubs.clear();
        searchTerms = Arrays.stream(search.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(term -> !term.isEmpty()).toList();
        for (int index = 0; index < entries.length; index++) {
            PatternPreviewEntry entry = entries[index];
            var held = entry.diskPatterns();
            if (held.isEmpty()) {
                if (matchesEntry(entry, -1)) {
                    visibleSlots.add(index);
                    visibleSubs.add(-1);
                }
                continue;
            }
            for (int sub = 0; sub < held.size(); sub++) {
                if (matchesEntry(entry, sub)) {
                    visibleSlots.add(index);
                    visibleSubs.add(sub);
                }
            }
        }
        scrollRow = Math.clamp(scrollRow, 0, getMaxScrollRow());
        updateSlots();
    }

    /** @param sub the disk recipe to match, or {@code -1} for the entry itself */
    private boolean matchesEntry(PatternPreviewEntry entry, int sub) {
        byte flags = entry.flags();
        String keywords = entry.keywords();
        boolean carriesStack = !entry.stack().isEmpty();
        if (sub >= 0) {
            // A disk's recipes answer for themselves: the entry's flags describe the slot rather than the recipe,
            // and the recipe's own keywords are the only thing a search has to go on.
            flags = entry.diskPatterns().get(sub).flags();
            keywords = entry.diskPatterns().get(sub).keywords();
            carriesStack = true;
        }
        return (showSubstitution || (flags & 1) == 0)
                && (showFluidSubstitution || (flags & 2) == 0)
                && (searchTerms.isEmpty() || (carriesStack && matchesSearch(keywords, searchTerms)));
    }

    private void updateMembership(int logicalSlot, boolean visible) {
        // Rows are no longer one per entry: a disk's entry spans a row per recipe, so a surgical insert would
        // have to place a whole run of them in order. Rebuilding is the honest option, and it only costs
        // anything when the entry actually carries disk recipes.
        if (logicalSlot >= 0 && logicalSlot < entries.length && !entries[logicalSlot].diskPatterns().isEmpty()) {
            rebuildFilter();
            return;
        }
        int low = 0;
        int high = visibleSlots.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (visibleSlots.getInt(mid) < logicalSlot) low = mid + 1;
            else high = mid;
        }
        if (visible) {
            visibleSlots.add(low, logicalSlot);
            // Paired with visibleSlots by construction: a row here is the entry itself, and the two lists have to
            // stay the same length or every later row reads a sub-index belonging to a different entry.
            visibleSubs.add(low, -1);
        } else if (low < visibleSlots.size() && visibleSlots.getInt(low) == logicalSlot) {
            visibleSlots.removeInt(low);
            visibleSubs.removeInt(low);
        }
    }

    private static boolean matchesSearch(String keywords, List<String> terms) {
        for (String term : terms) if (!keywords.contains(term)) return false;
        return true;
    }

    private void updateSlots() {
        int start = scrollRow * CraftingInterfaceUI.PREVIEW_COLUMNS;
        for (int visual = 0; visual < slots.length; visual++) {
            int position = start + visual;
            int index = position < visibleSlots.size() ? visibleSlots.getInt(position) : -1;
            int sub = position < visibleSubs.size() ? visibleSubs.getInt(position) : -1;
            displayed[visual] = index;
            displayedSubs[visual] = sub;
            ItemStack shown = ItemStack.EMPTY;
            if (index >= 0) {
                PatternPreviewEntry entry = entries[index];
                // Bounded on purpose: the two parallel lists are written together, but a row whose sub-index does
                // not belong to its entry would otherwise read past the end of that entry's recipe list.
                boolean held = sub >= 0 && sub < entry.diskPatterns().size();
                shown = held ? entry.diskPatterns().get(sub).stack().copy() : entry.stack().copy();
            }
            if (slots[visual] != null) slots[visual].setItem(shown, false);
        }
    }

    int getRowCount() { return (visibleSlots.size() + CraftingInterfaceUI.PREVIEW_COLUMNS - 1) / CraftingInterfaceUI.PREVIEW_COLUMNS; }
    int getMaxScrollRow() { return Math.max(0, getRowCount() - CraftingInterfaceUI.PREVIEW_ROWS); }
    int getScrollRow() { return scrollRow; }

    void setScrollRow(int value) {
        int next = Math.clamp(value, 0, getMaxScrollRow());
        if (next != scrollRow) {
            scrollRow = next;
            updateSlots();
        }
    }

    void scroll(int delta) { setScrollRow(scrollRow + delta); }
}
