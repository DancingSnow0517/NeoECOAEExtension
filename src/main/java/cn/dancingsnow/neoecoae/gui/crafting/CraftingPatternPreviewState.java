package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.integration.ae2.keyreuse.ECOKeyFactory;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import cn.dancingsnow.neoecoae.integration.ae2pattern.PatternDiskSupport;
import cn.dancingsnow.neoecoae.util.PatternSearchKeywords;
import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The complete preview model and its viewport belong exclusively to this client menu. */
final class CraftingPatternPreviewState {
    private final ECOMachineInterfaceBlockEntity<?> craftingInterface;
    private final Player player;
    // One extra row keeps both partially visible edges populated while the viewport moves between rows.
    private final PatternItemSlot[] slots = new PatternItemSlot[CraftingInterfaceUI.PREVIEW_BUFFER_SLOTS];
    private final LocalSlot[] previews = new LocalSlot[slots.length];
    private final Map<CraftingPatternMenu.Address, ? extends Slot> nativeSlots;
    private final int[] displayed = new int[slots.length];
    /** Which disk recipe the row currently shows, or {@code -1} when it shows the entry itself. */
    private final int[] displayedSubs = new int[slots.length];
    private final PatternPreviewRows rows = new PatternPreviewRows();
    // The UI owns this callback; the block entity must not retain a closed screen.
    private final Consumer<CompoundTag> receiver = this::receive;
    private PatternPreviewEntry[] entries = new PatternPreviewEntry[0];
    private PatternPreviewEntry[] pending;
    private final Int2ObjectOpenHashMap<PatternPreviewEntry> pendingChanges = new Int2ObjectOpenHashMap<>();
    private boolean pendingFull;
    private boolean sparseFull;
    private final BitSet awaitingEntries = new BitSet();
    private final BitSet localizedEntries = new BitSet();
    private int pendingRevision;
    private long pendingEpoch;
    private int revision = -1;
    private long epoch = -1L;
    private int menuId = -1;
    private int requestedMenuId = -1;
    private String inputSearch = "", outputSearch = "";
    private int scrollRow;
    private double scrollOffset;
    private double scrollTarget;
    private int receivedEntries;
    private final Map<AEItemKey, PatternSearchKeywords.Directions> localKeywords = new LinkedHashMap<>(256, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<AEItemKey, PatternSearchKeywords.Directions> entry) { return size() > 4096; }
    };
    private int windowDelay;

    CraftingPatternPreviewState(ECOMachineInterfaceBlockEntity<?> craftingInterface, Player player,
                                Map<CraftingPatternMenu.Address, ? extends Slot> nativeSlots) {
        this.craftingInterface = craftingInterface;
        this.player = player;
        this.nativeSlots = nativeSlots;
        Arrays.fill(displayed, -1);
        Arrays.fill(displayedSubs, -1);
        if (player.level().isClientSide) {
            var sync = craftingInterface.getPatternPreviewSync();
            sync.listen(receiver);
            var view = sync.clientView();
            inputSearch = boundedQuery(view.inputSearch());
            outputSearch = boundedQuery(view.outputSearch());
            scrollRow = view.scrollRow();
            rows.filter(inputSearch, outputSearch);
            var snapshot = sync.clientSnapshot();
            if (snapshot != null) {
                entries = snapshot.entries();
                revision = snapshot.revision();
                epoch = snapshot.epoch();
                localizeForSearch();
                rows.reset(entries);
                scrollRow = Math.clamp(scrollRow, 0, getMaxScrollRow());
            }
            scrollOffset = scrollTarget = scrollRow * 18.0;
        }
    }

    void tick(UIElement root) {
        if (!player.level().isClientSide || !(player.containerMenu instanceof CraftingPatternMenu menu)
                || !menu.isFor(craftingInterface)) return;
        if (requestedMenuId != menu.containerId) {
            menuId = menu.containerId;
            requestedMenuId = menuId;
            craftingInterface.getPatternPreviewSync().requestOpen(menuId, revision, epoch);
        }
        if (windowDelay > 0) windowDelay--;
        if (windowDelay > 0 || revision < 0 || !menu.getCarried().isEmpty() || root.isMouseDown(0) || root.isMouseDown(1)
                || scrollOffset != scrollTarget) return;
        var window = new ArrayList<CraftingPatternMenu.Address>(slots.length);
        for (int visual = 0; visual < slots.length; visual++) {
            var address = displayedAddress(visual);
            if (address != null) window.add(address);
        }
        if (nativeSlots.keySet().containsAll(window)) return;
        CompoundTag payload = new CompoundTag();
        payload.putInt("menu", menuId);
        payload.putLongArray("buses", window.stream().mapToLong(CraftingPatternMenu.Address::busPosition).toArray());
        payload.putIntArray("slots", window.stream().mapToInt(CraftingPatternMenu.Address::physicalSlot).toArray());
        craftingInterface.rpcToServer("openPatternWindow", payload);
        windowDelay = 10;
    }

    private CraftingPatternMenu.Address displayedAddress(int visual) {
        int index = displayed[visual];
        if (index < 0 || displayedSubs[visual] >= 0 || awaitingEntries.get(index)) return null;
        var entry = entries[index];
        return entry.auxiliaryDisk() ? null : new CraftingPatternMenu.Address(entry.busPosition(), entry.physicalSlot());
    }

    PatternItemSlot createSlot(int visualSlot) {
        LocalSlot local = new LocalSlot();
        previews[visualSlot] = local;
        PatternItemSlot slot = ClientUIBridge.call("createPatternSlot", Slot.class, local,
                PatternItemSlot.class, () -> new PatternItemSlot(local));
        slots[visualSlot] = slot;
        slot.setVisible(false);
        slot.setActive(false);
        if (visualSlot == slots.length - 1) {
            updateSlots();
        }
        slot.highlighted(() -> displayed[visualSlot] >= 0 && (!inputSearch.isBlank() || !outputSearch.isBlank()));
        slot.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            int index = displayed[visualSlot];
            if (index < 0) return;
            PatternPreviewEntry entry = entries[index];
            BlockPos pos = BlockPos.of(entry.busPosition());
            if (event.hoverTooltips == null) event.hoverTooltips = HoverTooltips.empty();
            event.hoverTooltips = event.hoverTooltips.append(Component.translatable(
                    "gui.neoecoae.crafting_interface.preview.source", pos.getX(), pos.getY(), pos.getZ(), entry.physicalSlot() + 1));
            if (entry.auxiliaryDisk() || displayedSubs[visualSlot] >= 0) event.hoverTooltips = event.hoverTooltips.append(Component.translatable(
                    "gui.neoecoae.crafting_interface.preview.read_only"));
        });
        return slot;
    }

    boolean hasCarriedPattern() {
        ItemStack carried = player.containerMenu.getCarried();
        return PatternDetailsHelper.isEncodedPattern(carried) || PatternDiskSupport.isAuxiliaryContainer(carried);
    }

    void insertCarriedPattern(int button) {
        if (player.level().isClientSide && menuId == player.containerMenu.containerId && hasCarriedPattern()) {
            craftingInterface.rpcToServer("insertCarriedPattern", menuId, button == 1);
        }
    }

    void receive(CompoundTag payload) {
        if (!player.level().isClientSide || craftingInterface.getLevel() == null
                || payload.getInt("menu") != player.containerMenu.containerId) return;
        long incomingEpoch = payload.getLong("epoch");
        if (payload.getBoolean("reuse")) {
            if (revision < 0 || incomingEpoch != epoch || payload.getInt("base") != revision
                    || payload.getInt("size") != entries.length) {
                return;
            }
            menuId = payload.getInt("menu");
            revision = payload.getInt("revision");
            pending = null;
            return;
        }
        boolean full = payload.getBoolean("full");
        if (payload.getBoolean("first")) {
            pendingEpoch = incomingEpoch;
            pending = null;
            pendingChanges.clear();
            receivedEntries = 0;
            if (!full && (payload.getInt("base") != revision || menuId != payload.getInt("menu"))) return;
            int size = payload.getInt("size");
            if (size < 0 || size > PatternPreviewCodec.MAX_SLOTS || (!full && size != entries.length)) return;
            pendingFull = full;
            sparseFull = full && payload.contains("layout", Tag.TAG_LIST);
            if (full) localizedEntries.clear();
            awaitingEntries.clear();
            pending = full ? emptyBaseline(payload, size) : entries;
            pendingRevision = payload.getInt("revision");
            menuId = payload.getInt("menu");
        }
        if (pending == null || incomingEpoch != pendingEpoch || payload.getInt("revision") != pendingRevision) return;
        var batch = payload.getList("entries", Tag.TAG_COMPOUND);
        BitSet pageChanges = new BitSet();
        boolean diskRowsChanged = false;
        for (int index = 0; index < batch.size(); index++) {
            CompoundTag entry = batch.getCompound(index);
            int logicalSlot = entry.getInt("index");
            if (logicalSlot < 0 || logicalSlot >= pending.length) {
                pending = null;
                return;
            }
            receivedEntries++;
            PatternPreviewEntry decoded = PatternPreviewEntry.decode(entry, craftingInterface.getLevel().registryAccess());
            if (hasSearch()) decoded = localize(decoded);
            localizedEntries.set(logicalSlot, hasSearch());
            if (!pendingFull && entry.contains("diskSize")) {
                int diskSize = entry.getInt("diskSize");
                if (diskSize < 0 || diskSize > PatternPreviewCodec.MAX_SLOTS) { pending = null; return; }
                List<PatternPreviewEntry.DiskPattern> disk = new ArrayList<>(entries[logicalSlot].diskPatterns());
                if (diskSize < disk.size()) disk.subList(diskSize, disk.size()).clear();
                while (disk.size() < diskSize) disk.add(null);
                ListTag changes = entry.getList("diskChanges", Tag.TAG_COMPOUND);
                for (int recipe = 0; recipe < changes.size(); recipe++) {
                    CompoundTag change = changes.getCompound(recipe);
                    int target = change.getInt("recipe");
                    if (target < 0 || target >= diskSize) { pending = null; return; }
                    var diskRecipe = PatternPreviewEntry.DiskPattern.decode(change, player.level().registryAccess());
                    disk.set(target, hasSearch() ? localize(diskRecipe) : diskRecipe);
                }
                if (disk.contains(null)) { pending = null; return; }
                decoded = new PatternPreviewEntry(decoded.busPosition(), decoded.physicalSlot(), decoded.stack(),
                        decoded.inputKeywords(), decoded.outputKeywords(), disk, decoded.auxiliaryDisk());
            }
            if (pendingFull) {
                diskRowsChanged |= !decoded.diskPatterns().isEmpty();
                pending[logicalSlot] = decoded;
                awaitingEntries.clear(logicalSlot);
                pageChanges.set(logicalSlot);
            } else pendingChanges.put(logicalSlot, decoded);
        }
        if (sparseFull) {
            // The layout establishes empty capacity once. Later pages update their actual cells only.
            entries = pending;
            if (payload.getBoolean("first")) rows.reset(entries);
            else rows.changed(pageChanges, diskRowsChanged);
            clampScroll();
            updateSlots();
        }
        if (!payload.getBoolean("last")) {
            // Show received patterns immediately. A completed cached view stays visible during refreshes.
            if (pendingFull && !sparseFull && revision < 0) {
                entries = pending;
                rows.reset(entries);
                rebuildFilter();
            }
            return;
        }
        if (pendingFull) {
            if (!awaitingEntries.isEmpty()) { pending = null; return; }
            for (PatternPreviewEntry entry : pending) {
                if (entry == null) {
                    pending = null;
                    return;
                }
            }
            entries = pending;
        } else {
            boolean rowsChanged = false;
            BitSet changed = new BitSet();
            for (var change : pendingChanges.int2ObjectEntrySet()) {
                int index = change.getIntKey();
                PatternPreviewEntry before = entries[index];
                entries[index] = change.getValue();
                changed.set(index);
                rowsChanged |= !before.diskPatterns().isEmpty() || !entries[index].diskPatterns().isEmpty()
                        || before.busPosition() != entries[index].busPosition()
                        || before.physicalSlot() != entries[index].physicalSlot();
            }
            pendingChanges.clear();
            rows.changed(changed, rowsChanged);
        }
        pending = null;
        revision = pendingRevision;
        epoch = pendingEpoch;
        craftingInterface.getPatternPreviewSync().cacheClientSnapshot(epoch, revision, entries);
        if (pendingFull && !sparseFull) {
            rows.reset(entries);
            rebuildFilter();
        }
        else {
            clampScroll();
            updateSlots();
        }
    }

    private PatternPreviewEntry[] emptyBaseline(CompoundTag payload, int size) {
        PatternPreviewEntry[] baseline = new PatternPreviewEntry[size];
        if (!sparseFull) return baseline;
        ListTag layout = payload.getList("layout", Tag.TAG_COMPOUND);
        int offset = 0;
        for (int bus = 0; bus < layout.size(); bus++) {
            CompoundTag provider = layout.getCompound(bus);
            int count = provider.getInt("count");
            if (count < 0 || count > size - offset) throw new IllegalArgumentException("Invalid preview layout");
            for (int slot = 0; slot < count; slot++) {
                baseline[offset++] = new PatternPreviewEntry(provider.getLong("bus"), slot, ItemStack.EMPTY,
                        "", "", List.of(), false);
            }
        }
        if (offset != size) throw new IllegalArgumentException("Incomplete preview layout");
        for (int occupied : payload.getIntArray("occupied")) {
            if (occupied < 0 || occupied >= size) throw new IllegalArgumentException("Invalid occupied preview slot");
            awaitingEntries.set(occupied);
        }
        return baseline;
    }

    private boolean hasSearch() { return !inputSearch.isBlank() || !outputSearch.isBlank(); }

    private void localizeForSearch() {
        if (!hasSearch()) return;
        for (int index = 0; index < entries.length; index++) {
            if (entries[index] != null && !localizedEntries.get(index)) {
                entries[index] = localize(entries[index]);
                localizedEntries.set(index);
            }
        }
    }

    private PatternPreviewEntry localize(PatternPreviewEntry entry) {
        List<PatternPreviewEntry.DiskPattern> disk = entry.diskPatterns().stream().map(this::localize).toList();
        var terms = keywords(entry.stack(), entry.inputKeywords(), entry.outputKeywords());
        return new PatternPreviewEntry(entry.busPosition(), entry.physicalSlot(), entry.stack(),
                terms.inputs(), terms.output(), disk, entry.auxiliaryDisk());
    }

    private PatternPreviewEntry.DiskPattern localize(PatternPreviewEntry.DiskPattern pattern) {
        var terms = keywords(pattern.stack(), pattern.inputKeywords(), pattern.outputKeywords());
        return new PatternPreviewEntry.DiskPattern(pattern.stack(), terms.inputs(), terms.output());
    }

    private PatternSearchKeywords.Directions keywords(ItemStack stack, String inputs, String output) {
        var fallback = new PatternSearchKeywords.Directions(inputs, output);
        if (stack.isEmpty()) return fallback;
        // Translate on the viewer's client, once per distinct pattern, not in each frame/search keystroke.
        var localized = localKeywords.computeIfAbsent(ECOKeyFactory.item(stack), key -> {
            try {
                return PatternSearchKeywords.directions(PatternDetailsHelper.decodePattern(stack, player.level()));
            } catch (RuntimeException ignored) {
                // Some integrations only decode on the server; retain their directional terms.
                return PatternSearchKeywords.Directions.EMPTY;
            }
        });
        // Cached snapshots may already contain these client terms; never append them repeatedly.
        return new PatternSearchKeywords.Directions(
                inputs.contains(localized.inputs()) ? inputs : inputs + '\n' + localized.inputs(),
                output.contains(localized.output()) ? output : output + '\n' + localized.output());
    }

    void setInputSearch(String value) {
        String next = boundedQuery(value);
        if (!inputSearch.equals(next)) {
            inputSearch = next;
            resetFilter();
        }
    }

    String getInputSearch() { return inputSearch; }
    String getOutputSearch() { return outputSearch; }

    void setOutputSearch(String value) {
        String next = boundedQuery(value);
        if (!outputSearch.equals(next)) {
            outputSearch = next;
            resetFilter();
        }
    }

    private static String boundedQuery(String value) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), CraftingInterfaceUI.PREVIEW_QUERY_MAX_LENGTH));
    }

    private void resetFilter() {
        scrollRow = 0;
        scrollOffset = scrollTarget = 0;
        windowDelay = 4;
        rebuildFilter();
    }

    private void rebuildFilter() {
        localizeForSearch();
        rows.filter(inputSearch, outputSearch);
        clampScroll();
        updateSlots();
    }

    Component status() {
        if (rows.size() == 0 && revision < 0) return Component.translatable(
                "gui.neoecoae.crafting_interface.preview.loading", receivedEntries);
        if (rows.size() == 0) return Component.translatable("gui.neoecoae.crafting_interface.preview.no_results");
        return Component.translatable("gui.neoecoae.crafting_interface.preview.rows",
                scrollRow + 1, Math.min((int) Math.ceil(scrollOffset / 18) + CraftingInterfaceUI.PREVIEW_ROWS,
                    rows.size()), rows.size());
    }

    private void updateSlots() {
        int start = scrollRow * CraftingInterfaceUI.PREVIEW_COLUMNS;
        for (int visual = 0; visual < slots.length; visual++) {
            int position = start + viewportSlot(visual);
            PatternPreviewRows.Cell cell = rows.cell(position);
            int index = cell == null ? -1 : cell.entry();
            int sub = cell == null ? -1 : cell.recipe();
            displayed[visual] = index;
            displayedSubs[visual] = sub;
            ItemStack shown = ItemStack.EMPTY;
            if (index >= 0) {
                PatternPreviewEntry entry = entries[index];
                // Bounded on purpose: the two parallel lists are written together, but a row whose sub-index does
                // not belong to its entry would otherwise read past the end of that entry's recipe list.
                boolean held = sub >= 0 && sub < entry.diskPatterns().size();
                shown = held ? entry.diskPatterns().get(sub).stack() : entry.stack();
            }
            PatternItemSlot widget = slots[visual];
            if (widget != null) {
                var address = displayedAddress(visual);
                Slot nativeSlot = address == null ? null : nativeSlots.get(address);
                if (nativeSlot == null) {
                    widget.bind(previews[visual]);
                    if (!ItemStack.matches(widget.getValue(), shown)) widget.setItem(shown.copy(), false);
                } else {
                    widget.bind(nativeSlot);
                }
                boolean visible = nativeSlot != null || !shown.isEmpty();
                if (widget.isVisible() != visible) widget.setVisible(visible);
                if (widget.isActive() != visible) widget.setActive(visible);
            }
        }
        updateSlotTransforms();
        craftingInterface.getPatternPreviewSync().cacheClientView(inputSearch, outputSearch, scrollRow);
    }

    int getRowCount() { return rows.size(); }
    int getMaxScrollRow() { return Math.max(0, getRowCount() - CraftingInterfaceUI.PREVIEW_ROWS); }
    int getScrollRow() { return scrollRow; }

    void setScrollRow(int value) {
        setScrollOffset(value * 18.0);
    }

    double getScrollOffset() { return scrollOffset; }
    double getMaxScrollOffset() { return getMaxScrollRow() * 18.0; }
    double getRowOffset() { return scrollOffset - scrollRow * 18.0; }

    void scroll(double rows) {
        if (Double.isFinite(rows)) {
            scrollTarget = Math.clamp(scrollTarget + rows * 18, 0, getMaxScrollOffset());
        }
    }

    void setScrollOffset(double pixels) {
        if (!Double.isFinite(pixels)) return;
        scrollTarget = Math.clamp(pixels, 0, getMaxScrollOffset());
        moveScrollTo(scrollTarget);
    }

    void stopScrolling() { scrollTarget = scrollOffset; }

    void advanceScroll(double seconds) {
        if (seconds <= 0 || !Double.isFinite(seconds) || scrollOffset == scrollTarget) return;
        double next = scrollOffset + (scrollTarget - scrollOffset) * -Math.expm1(-20 * seconds);
        moveScrollTo(Math.abs(next - scrollTarget) < 0.01 ? scrollTarget : next);
    }

    private void clampScroll() {
        scrollOffset = Math.clamp(scrollOffset, 0, getMaxScrollOffset());
        scrollTarget = Math.clamp(scrollTarget, 0, getMaxScrollOffset());
        scrollRow = (int) (scrollOffset / 18);
    }

    private void moveScrollTo(double pixels) {
        if (scrollOffset == pixels) return;
        int previousRow = scrollRow;
        scrollOffset = pixels;
        scrollRow = (int) (pixels / 18);
        if (previousRow != scrollRow) updateSlots();
        else updateSlotTransforms();
    }

    private int viewportSlot(int visual) {
        int columns = CraftingInterfaceUI.PREVIEW_COLUMNS;
        int bufferRows = CraftingInterfaceUI.PREVIEW_ROWS + 1;
        return Math.floorMod(visual / columns - scrollRow, bufferRows) * columns + visual % columns;
    }

    private void updateSlotTransforms() {
        for (int visual = 0; visual < slots.length; visual++) {
            PatternItemSlot slot = slots[visual];
            float offset = (float) ((viewportSlot(visual) / CraftingInterfaceUI.PREVIEW_COLUMNS
                - visual / CraftingInterfaceUI.PREVIEW_COLUMNS) * 18 - getRowOffset());
            if (slot != null) slot.transform(transform -> transform.translate(0, offset));
        }
    }
}
