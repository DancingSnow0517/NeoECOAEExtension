package cn.dancingsnow.neoecoae.gui.crafting;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.widget.PatternItemSlot;
import com.lowdragmc.lowdraglib2.gui.slot.LocalSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

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
    private final IntArrayList visibleSlots = new IntArrayList();
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
            if (event.button == 0 || event.button == 1) {
                act(visualSlot, event.isShiftDown() ? 1 : 0, event.button);
                event.hasHandler = true;
                event.stopImmediatePropagation();
            }
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
        CompoundTag payload = new CompoundTag();
        payload.putInt("menu", menuId);
        payload.putInt("revision", revision);
        payload.putLong("bus", entry.busPosition());
        payload.putInt("slot", entry.physicalSlot());
        payload.putInt("action", action);
        payload.putInt("button", button);
        craftingInterface.rpcToServer("actOnPatternPreview", payload);
    }

    boolean quickMoveFromInventory(Slot source) {
        if (!(source.getItem().getItem() instanceof appeng.crafting.pattern.EncodedPatternItem<?>)) return false;
        if (!player.level().isClientSide || revision < 0 || pending != null
                || menuId != player.containerMenu.containerId || !player.containerMenu.getCarried().isEmpty()) return true;
        for (PatternPreviewEntry entry : entries) {
            if (!entry.stack().isEmpty()) continue;
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
            break;
        }
        return true;
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
            for (var change : pendingChanges.int2ObjectEntrySet()) {
                int index = change.getIntKey();
                boolean wasVisible = matchesEntry(entries[index]);
                entries[index] = change.getValue();
                boolean isVisible = matchesEntry(entries[index]);
                if (wasVisible != isVisible) updateMembership(index, isVisible);
            }
            pendingChanges.clear();
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
        searchTerms = Arrays.stream(search.trim().toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(term -> !term.isEmpty()).toList();
        for (int index = 0; index < entries.length; index++) {
            if (matchesEntry(entries[index])) visibleSlots.add(index);
        }
        scrollRow = Math.clamp(scrollRow, 0, getMaxScrollRow());
        updateSlots();
    }

    private boolean matchesEntry(PatternPreviewEntry entry) {
        byte flags = entry.flags();
        return (showSubstitution || (flags & 1) == 0)
                && (showFluidSubstitution || (flags & 2) == 0)
                && (searchTerms.isEmpty() || (!entry.stack().isEmpty() && matchesSearch(entry.keywords(), searchTerms)));
    }

    private void updateMembership(int logicalSlot, boolean visible) {
        int low = 0;
        int high = visibleSlots.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (visibleSlots.getInt(mid) < logicalSlot) low = mid + 1;
            else high = mid;
        }
        if (visible) visibleSlots.add(low, logicalSlot);
        else if (low < visibleSlots.size() && visibleSlots.getInt(low) == logicalSlot) visibleSlots.removeInt(low);
    }

    private static boolean matchesSearch(String keywords, List<String> terms) {
        for (String term : terms) if (!keywords.contains(term)) return false;
        return true;
    }

    private void updateSlots() {
        int start = scrollRow * CraftingInterfaceUI.PREVIEW_COLUMNS;
        for (int visual = 0; visual < slots.length; visual++) {
            int index = start + visual < visibleSlots.size() ? visibleSlots.getInt(start + visual) : -1;
            displayed[visual] = index;
            if (slots[visual] != null) slots[visual].setItem(index < 0 ? ItemStack.EMPTY : entries[index].stack().copy(), false);
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
