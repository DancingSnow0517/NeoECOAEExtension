package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final PatternItemSlot[] slots = new PatternItemSlot[ECOMachineInterfaceBlockEntity.PATTERN_INTERFACE_VISIBLE_SLOTS];
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
    private int pendingRevision;
    private int revision = -1;
    private int menuId = -1;
    private String search = "";
    private boolean showSubstitution = true;
    private boolean showFluidSubstitution = true;
    private boolean showEmptyRows = true;
    private int scrollRow;
    private int receivedEntries;
    private final Map<AEItemKey, String> localKeywords = new LinkedHashMap<>(256, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<AEItemKey, String> entry) { return size() > 4096; }
    };
    private final List<PatternPreviewEntry> quickMoveTargets = new ArrayList<>();
    private boolean quickMoveDrag;
    private int quickMoveRevision = -1;
    private int lastQuickMoveVisualSlot = -1;

    CraftingPatternPreviewState(ECOMachineInterfaceBlockEntity<?> craftingInterface, Player player) {
        this.craftingInterface = craftingInterface;
        this.player = player;
        Arrays.fill(displayed, -1);
        Arrays.fill(displayedSubs, -1);
        if (player.level().isClientSide) craftingInterface.getPatternPreviewSync().listen(receiver);
    }

    PatternItemSlot createSlot(int visualSlot) {
        LocalSlot local = new LocalSlot();
        PatternItemSlot slot = ClientUIBridge.call("createPatternSlot", Slot.class, local,
                PatternItemSlot.class, () -> new PatternItemSlot(local));
        slots[visualSlot] = slot;
        slot.highlighted(() -> displayed[visualSlot] >= 0 && !search.isBlank()
                && rows.matches(scrollRow * CraftingInterfaceUI.PREVIEW_COLUMNS + visualSlot));
        slot.dimmed(() -> displayed[visualSlot] >= 0
                && !rows.matches(scrollRow * CraftingInterfaceUI.PREVIEW_COLUMNS + visualSlot));
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
        payload.put("stack", entry.stack().saveOptional(player.level().registryAccess()));
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
            payload.put("stack", entry.stack().saveOptional(player.level().registryAccess()));
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
            receivedEntries = 0;
            if (!full && (payload.getInt("base") != revision || menuId != payload.getInt("menu"))) return;
            int size = payload.getInt("size");
            if (size < 0 || size > PatternPreviewCodec.MAX_SLOTS || (!full && size != entries.length)) return;
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
            receivedEntries++;
            PatternPreviewEntry decoded = localize(PatternPreviewEntry.decode(entry, craftingInterface.getLevel().registryAccess()));
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
                    ItemStack stack = ItemStack.parseOptional(player.level().registryAccess(), change.getCompound("stack"));
                    disk.set(target, new PatternPreviewEntry.DiskPattern(stack,
                            keywords(stack, change.getString("keywords")), change.getByte("flags")));
                }
                if (disk.contains(null)) { pending = null; return; }
                decoded = new PatternPreviewEntry(decoded.busPosition(), decoded.physicalSlot(), decoded.stack(),
                        decoded.keywords(), decoded.flags(), List.copyOf(disk), decoded.auxiliaryDisk());
            }
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
        if (pendingFull) {
            rows.reset(entries);
            rebuildFilter();
        }
        else {
            scrollRow = Math.clamp(scrollRow, 0, getMaxScrollRow());
            updateSlots();
        }
    }

    boolean showsSubstitutionPatterns() { return showSubstitution; }
    boolean showsFluidSubstitutionPatterns() { return showFluidSubstitution; }

    private PatternPreviewEntry localize(PatternPreviewEntry entry) {
        List<PatternPreviewEntry.DiskPattern> disk = entry.diskPatterns().stream().map(pattern ->
                new PatternPreviewEntry.DiskPattern(pattern.stack(), keywords(pattern.stack(), pattern.keywords()), pattern.flags())).toList();
        return new PatternPreviewEntry(entry.busPosition(), entry.physicalSlot(), entry.stack(),
                keywords(entry.stack(), entry.keywords()), entry.flags(), disk, entry.auxiliaryDisk());
    }

    private String keywords(ItemStack stack, String fallback) {
        if (stack.isEmpty()) return fallback;
        // Translate on the viewer's client, once per distinct pattern, not in each frame/search keystroke.
        String localized = localKeywords.computeIfAbsent(AEItemKey.of(stack), key -> {
            StringBuilder text = new StringBuilder(stack.getHoverName().getString());
            try {
                var details = PatternDetailsHelper.decodePattern(stack, player.level());
                if (details != null) {
                    for (var output : details.getOutputs()) if (output != null)
                        text.append('\n').append(output.what().getDisplayName().getString());
                    for (var input : details.getInputs()) if (input != null)
                        for (var possible : input.getPossibleInputs()) if (possible != null)
                            text.append('\n').append(possible.what().getDisplayName().getString());
                }
            } catch (RuntimeException ignored) {
                // Some integrations only decode on the server; keep their supplied search terms.
            }
            return text.toString().toLowerCase(Locale.ROOT);
        });
        return fallback + '\n' + localized;
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
        scrollRow = 0;
        rebuildFilter();
    }

    private void rebuildFilter() {
        rows.filter(search, showSubstitution, showFluidSubstitution, showEmptyRows);
        scrollRow = Math.clamp(scrollRow, 0, getMaxScrollRow());
        updateSlots();
    }

    boolean showsEmptyRows() { return showEmptyRows; }

    void toggleEmptyRows() {
        showEmptyRows = !showEmptyRows;
        resetFilter();
    }

    Component status() {
        if (revision < 0 || pending != null) return Component.translatable(
                "gui.neoecoae.crafting_interface.preview.loading", receivedEntries);
        if (rows.size() == 0) return Component.translatable("gui.neoecoae.crafting_interface.preview.no_results");
        return Component.translatable("gui.neoecoae.crafting_interface.preview.rows",
                scrollRow + 1, Math.min(scrollRow + CraftingInterfaceUI.PREVIEW_ROWS, rows.size()), rows.size());
    }

    private void updateSlots() {
        int start = scrollRow * CraftingInterfaceUI.PREVIEW_COLUMNS;
        for (int visual = 0; visual < slots.length; visual++) {
            int position = start + visual;
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
            if (slots[visual] != null && !ItemStack.matches(slots[visual].getSlot().getItem(), shown))
                slots[visual].setItem(shown.copy(), false);
        }
    }

    int getRowCount() { return rows.size(); }
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
