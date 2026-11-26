package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.integration.ae2.keyreuse.ECOKeyFactory;

import cn.dancingsnow.neoecoae.platform.JdkCompat;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import cn.dancingsnow.neoecoae.util.PatternSearchKeywords;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The complete preview model and its viewport belong exclusively to this client menu. */
final class CraftingPatternPreviewState {
    private final ECOMachineInterfaceBlockEntity<?> craftingInterface;
    private final Player player;
    // One extra row keeps both partially visible edges populated while the viewport moves between rows.
    private final PatternItemSlot[] slots = new PatternItemSlot[CraftingInterfaceUI.PREVIEW_BUFFER_SLOTS];
    private final int[] displayed = new int[slots.length];
    /** Which disk recipe the row currently shows, or {@code -1} when it shows the entry itself. */
    private final int[] displayedSubs = new int[slots.length];
    private final PatternPreviewRows rows;
    private final PatternPreviewNativeSlots nativeSlots;
    private CraftingInterfaceUI.PatternPreview nativePreview;
    private final int[] wanted = new int[slots.length];
    private int[] lastRequested;
    private long requestInFlight;
    private long requestedEpoch;
    private int requestedRevision = -1;
    private int windowDelay;
    private boolean catalogueConfirmed;
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
    private String focusedSearch = "";
    private int searchCursor;
    private int scrollRow;
    private double scrollOffset;
    private double scrollTarget;
    private int receivedEntries;
    private final Map<AEItemKey, PatternSearchKeywords.Directions> localKeywords = new LinkedHashMap<>(256, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<AEItemKey, PatternSearchKeywords.Directions> entry) { return size() > 4096; }
    };

    record Viewport(PatternPreviewRows rows, PatternPreviewEntry[] entries, int revision, long epoch,
                    double scrollOffset, double scrollTarget, int[] wanted, String focusedSearch, int searchCursor) {}

    CraftingPatternPreviewState(ECOMachineInterfaceBlockEntity<?> craftingInterface, BlockUIMenuType.BlockUIHolder holder) {
        this.craftingInterface = craftingInterface;
        this.player = holder.player;
        var data = holder.menuData();
        var sync = craftingInterface.getPatternPreviewSync();
        Viewport continued = player.level().isClientSide ? sync.takeViewport(data.getLong("request")) : null;
        rows = continued == null ? new PatternPreviewRows() : continued.rows();
        nativeSlots = new PatternPreviewNativeSlots(craftingInterface, player, data);
        Arrays.fill(displayed, -1);
        Arrays.fill(displayedSubs, -1);
        Arrays.fill(wanted, -1);
        if (player.level().isClientSide) {
            sync.listen(receiver);
            var view = sync.clientView();
            inputSearch = boundedQuery(view.inputSearch());
            outputSearch = boundedQuery(view.outputSearch());
            if (continued != null) {
                entries = continued.entries();
                revision = continued.revision();
                epoch = continued.epoch();
                scrollOffset = continued.scrollOffset();
                scrollTarget = continued.scrollTarget();
                catalogueConfirmed = epoch == data.getLong("epoch");
                requestedEpoch = epoch;
                requestedRevision = revision;
                lastRequested = continued.wanted();
                focusedSearch = continued.focusedSearch();
                searchCursor = continued.searchCursor();
                clampScroll();
                return;
            }
            scrollRow = view.scrollRow();
            rows.filter(inputSearch, outputSearch);
            var snapshot = sync.clientSnapshot();
            if (snapshot != null) {
                entries = snapshot.entries();
                revision = snapshot.revision();
                epoch = snapshot.epoch();
                localizeForSearch();
                rows.reset(entries);
                scrollRow = JdkCompat.clamp(scrollRow, 0, getMaxScrollRow());
            }
            scrollOffset = scrollTarget = scrollRow * 18.0;
        }
    }

    void tick() {
        // UI construction precedes assigning player.containerMenu. Probe only once the actual menu is ready.
        if (!player.level().isClientSide || !(player.containerMenu instanceof ModularUIContainer menu)
                || !(menu.getModularUI().holder instanceof BlockUIMenuType.BlockUIHolder holder)
                || holder.patternInterface() != craftingInterface || holder.isInvalid()) return;
        menuId = menu.containerId;
        if (requestedMenuId != menu.containerId) {
            requestedMenuId = menu.containerId;
            craftingInterface.getPatternPreviewSync().requestOpen(menuId, revision, epoch);
        }
        if (windowDelay > 0) windowDelay--;
        if (catalogueConfirmed && pending == null && requestInFlight == 0 && windowDelay == 0
                && scrollOffset == scrollTarget && nativePreview != null && nativePreview.activeMenu()
                && nativePreview.getModularUI().getLastMouseDownButton() < 0
                && nativeSlots.needsWindow(epoch, wanted)
                && (epoch != requestedEpoch || revision != requestedRevision || !Arrays.equals(lastRequested, wanted))) {
            requestedEpoch = epoch;
            requestedRevision = revision;
            lastRequested = wanted.clone();
            do { requestInFlight = ThreadLocalRandom.current().nextLong(); } while (requestInFlight == 0);
            rememberViewport();
            nativePreview.requestWindow(epoch, requestInFlight, lastRequested);
        }
    }

    PatternItemSlot createSlot(int visualSlot) {
        Slot local = nativeSlots.slot(visualSlot);
        PatternItemSlot slot = ClientUIBridge.call("createPatternSlot", Slot.class, local,
                PatternItemSlot.class, () -> new PatternItemSlot(local));
        slots[visualSlot] = slot;
        slot.displayStack(() -> displayStack(visualSlot));
        slot.slotStyle(style -> style.acceptQuickMove(false));
        slot.nativeClickHandler((button, type, actor) -> {
            if (type == ClickType.QUICK_MOVE) return PatternMenuActions.toPlayer(actor, slot.getMenuSlot(), button);
            if (type == ClickType.PICKUP && button == 0 && hasCarriedPattern()) {
                insertCarriedPattern();
                return ItemStack.EMPTY;
            }
            return null;
        });
        if (player.level().isClientSide) {
            slot.setVisible(false);
            if (visualSlot == slots.length - 1) updateSlots();
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
            if (entry.auxiliaryDisk()) event.hoverTooltips = event.hoverTooltips.append(Component.translatable(
                    "gui.neoecoae.crafting_interface.preview.read_only"));
        });
        return slot;
    }

    void attachNativePreview(CraftingInterfaceUI.PatternPreview preview) { nativePreview = preview; }

    boolean openWindow(ServerPlayer actor, long requestEpoch, long request, int[] requested) {
        return craftingInterface.openNativePatternWindow(actor, requestEpoch, request, requested);
    }

    void windowRejected(long request) {
        if (request != requestInFlight) return;
        craftingInterface.getPatternPreviewSync().takeViewport(request);
        requestInFlight = 0;
    }

    private void rememberViewport() {
        if (requestInFlight != 0 && pending == null) {
            craftingInterface.getPatternPreviewSync().rememberViewport(requestInFlight,
                new Viewport(rows, entries, revision, epoch, scrollOffset, scrollTarget, lastRequested, focusedSearch,
                    searchCursor));
        }
    }

    boolean hasCarriedPattern() {
        return PatternDetailsHelper.isEncodedPattern(player.containerMenu.getCarried());
    }

    void insertCarriedPattern() {
        if (player.level().isClientSide) return;
        var menu = player.containerMenu;
        menu.setCarried(craftingInterface.insertNativePatternPreview(player, menu.getCarried(), false));
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
            catalogueConfirmed = true;
            updateSlots();
            return;
        }
        boolean full = payload.getBoolean("full");
        if (payload.getBoolean("first")) {
            pendingEpoch = incomingEpoch;
            if (incomingEpoch != epoch) catalogueConfirmed = false;
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
        catalogueConfirmed = true;
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

    String getFocusedSearch() { return focusedSearch; }

    int getSearchCursor() { return searchCursor; }

    void setFocusedSearch(String direction, int cursor) {
        if (!direction.equals(focusedSearch) || searchCursor != cursor) {
            focusedSearch = direction;
            searchCursor = cursor;
            rememberViewport();
        }
    }

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
        windowDelay = 5;
        scrollRow = 0;
        scrollOffset = scrollTarget = 0;
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
            PatternPreviewRows.Cell cell = rows.cell(start + viewportSlot(visual));
            int index = cell == null ? -1 : cell.entry();
            int sub = cell == null ? -1 : cell.recipe();
            displayed[visual] = index;
            displayedSubs[visual] = sub;
            PatternPreviewEntry entry = index >= 0 ? entries[index] : null;
            wanted[visual] = entry != null && !entry.auxiliaryDisk() && sub < 0 && !awaitingEntries.get(index)
                && (viewportSlot(visual) < CraftingInterfaceUI.PREVIEW_ROWS * CraftingInterfaceUI.PREVIEW_COLUMNS
                    || getRowOffset() > 0) ? index : -1;
        }
        nativeSlots.updateView(pending != null ? pendingEpoch : epoch, wanted);
        for (PatternItemSlot slot : slots) if (slot != null) slot.setVisible(true);
        updateSlotTransforms();
        craftingInterface.getPatternPreviewSync().cacheClientView(inputSearch, outputSearch, scrollRow);
        rememberViewport();
    }

    private ItemStack displayStack(int visual) {
        if (nativeSlots.isVisible(visual)) return slots[visual].getValue();
        int index = displayed[visual];
        return index < 0 ? ItemStack.EMPTY : displayedSubs[visual] < 0
            ? entries[index].stack() : entries[index].diskPatterns().get(displayedSubs[visual]).stack();
    }

    private int viewportSlot(int visual) {
        int columns = CraftingInterfaceUI.PREVIEW_COLUMNS;
        int bufferRows = CraftingInterfaceUI.PREVIEW_ROWS + 1;
        return Math.floorMod(visual / columns - scrollRow, bufferRows) * columns + visual % columns;
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
            scrollTarget = JdkCompat.clamp(scrollTarget + rows * 18, 0, getMaxScrollOffset());
        }
    }

    void setScrollOffset(double pixels) {
        if (!Double.isFinite(pixels)) return;
        scrollTarget = JdkCompat.clamp(pixels, 0, getMaxScrollOffset());
        moveScrollTo(scrollTarget);
    }

    void stopScrolling() { scrollTarget = scrollOffset; }

    void advanceScroll(double seconds) {
        if (seconds <= 0 || !Double.isFinite(seconds) || scrollOffset == scrollTarget) return;
        double next = scrollOffset + (scrollTarget - scrollOffset) * -Math.expm1(-20 * seconds);
        moveScrollTo(Math.abs(next - scrollTarget) < 0.01 ? scrollTarget : next);
    }

    private void clampScroll() {
        scrollOffset = JdkCompat.clamp(scrollOffset, 0, getMaxScrollOffset());
        scrollTarget = JdkCompat.clamp(scrollTarget, 0, getMaxScrollOffset());
        scrollRow = (int) (scrollOffset / 18);
    }

    private void moveScrollTo(double pixels) {
        if (scrollOffset == pixels) return;
        int previousRow = scrollRow;
        boolean wasAligned = getRowOffset() == 0;
        scrollOffset = pixels;
        scrollRow = (int) (pixels / 18);
        if (previousRow != scrollRow || wasAligned != (getRowOffset() == 0)) updateSlots();
        else {
            updateSlotTransforms();
            rememberViewport();
        }
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
