package cn.dancingsnow.neoecoae.integration.ae2pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.inventories.BaseInternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;

import cn.dancingsnow.neoecoae.api.AuxiliaryPatternHolder;

import io.github.lounode.ae2pattern.api.PatternDiskApi;
import io.github.lounode.ae2pattern.api.PatternDiskContents;
import io.github.lounode.ae2pattern.api.PatternDiskHostView;
import io.github.lounode.ae2pattern.api.PatternDiskTerminalView;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The AEPD-backed half of {@link PatternDiskSupport}: this is where the mod's api is actually called.
 *
 * <p>Only installed by {@link PatternDiskIntegration}, which the integration manager loads after confirming
 * AE2 Pattern Disk is present - so every reference to {@code PatternDiskApi} here is reached on a machine
 * that has it.</p>
 */
final class AepdPatternDiskBackend implements PatternDiskSupport.Backend {

    @Override
    public AuxiliaryPatternHolder holderFor(InternalInventory slots, Supplier<IGrid> grid, Supplier<Level> level) {
        return new SlotsHolder(slots);
    }

    @Override
    public PatternDiskSupport.TerminalView terminalView(
            InternalInventory diskSlots,
            Supplier<IGrid> grid,
            IActionHost machine,
            Runnable onChanged,
            Supplier<Level> level) {
        PatternDiskTerminalView created = PatternDiskApi.terminalView(diskSlots, grid, machine, onChanged, level);
        return new PatternDiskSupport.TerminalView() {

            @Override
            public InternalInventory view() {
                return created.view();
            }

            @Override
            public InternalInventory withHostRows(InternalInventory hostRows) {
                return PatternDiskHostView.of(new PatternSlotsWithoutDisks(hostRows), created);
            }

            @Override
            public void invalidate() {
                created.invalidate();
            }
        };
    }

    private static final class PatternSlotsWithoutDisks extends BaseInternalInventory {
        private final InternalInventory slots;

        private PatternSlotsWithoutDisks(InternalInventory slots) {
            this.slots = slots;
        }

        private boolean diskAt(int slot) {
            return slot >= 0 && slot < slots.size()
                && PatternDiskApi.isPatternDisk(slots.getStackInSlot(slot));
        }

        @Override
        public int size() {
            return slots.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return diskAt(slot) ? ItemStack.EMPTY : slots.getStackInSlot(slot);
        }

        @Override
        public InternalInventory getSlotInv(int slot) {
            return diskAt(slot) ? InternalInventory.empty() : super.getSlotInv(slot);
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            if (!diskAt(slot)) {
                slots.setItemDirect(slot, stack);
            }
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return diskAt(slot) ? ItemStack.EMPTY : slots.extractItem(slot, amount, simulate);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return diskAt(slot) ? stack : slots.insertItem(slot, stack, simulate);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !diskAt(slot) && slots.isItemValid(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slots.getSlotLimit(slot);
        }
    }

    /**
     * Writes onto a disk that takes the pattern, filling one that already holds the pattern's class first.
     *
     * <p>An empty disk locks to whatever class reaches it first, so handing every new class to the first free
     * disk spreads one class per disk and leaves nothing for the classes that arrive later, even though room
     * remains. Filling a disk that already holds the class first keeps the free disks free for classes that
     * have not shown up yet. Disks locked to another class are left out by {@code canAccept} itself.</p>
     *
     * <p>No blank-pattern sink is passed. The write itself mutates the disk; the blank it frees is settled by
     * the caller through {@code PatternRefund}.</p>
     */
    @Override
    public boolean insertAuxiliary(InternalInventory slots, ItemStack pattern, Level level) {
        return insertAuxiliary(slots, pattern, level, null);
    }

    private static boolean insertAuxiliary(InternalInventory slots, ItemStack pattern, Level level, BitSet disks) {
        List<Integer> claimed = new ArrayList<>();
        List<Integer> empty = new ArrayList<>();
        int slotCount = slots.size();
        for (int slot = disks == null ? 0 : disks.nextSetBit(0); slot >= 0 && slot < slotCount;
             slot = disks == null ? slot + 1 : disks.nextSetBit(slot + 1)) {
            ItemStack disk = slots.getStackInSlot(slot);
            if (!PatternDiskApi.canAccept(disk, pattern, level)) {
                continue;
            }
            PatternDiskContents contents = PatternDiskApi.contents(disk);
            if (contents == null || contents.type() == null) {
                empty.add(slot);
            } else {
                claimed.add(slot);
            }
        }
        claimed.addAll(empty);
        for (int slot : claimed) {
            ItemStack disk = slots.getStackInSlot(slot);
            // A null sink on purpose: the blank a write frees is settled by the caller that removed the source
            // pattern (see PatternRefund), which is the only party that knows an item was consumed. Charging a
            // sink here as well would take a second blank from the network.
            ItemStack left = PatternDiskApi.insert(disk, pattern, level, null);
            if (left == ItemStack.EMPTY) {
                // The write mutated the stack in place; write it back so the host's inventory sees a change
                // and notifies whoever owns it. Without this the host never learns its slots moved, so the
                // catalog keeps its old index, the provider keeps its old pattern list, and nothing marks the
                // block dirty - the disk would not even be saved.
                slots.setItemDirect(slot, disk);
                return true;
            }
        }
        return false;
    }

    /**
     * Decodes the disks' patterns through the api's memoized entry point.
     *
     * <p>{@code decodePatterns} keys its memo on a contents snapshot, and a snapshot is replaced whole on
     * every write - so asking again after a disk changes decodes again, and asking between changes does not.
     * That is what makes this safe to call from an advertisement path.</p>
     */
    @Override
    public List<IPatternDetails> decodeAuxiliary(InternalInventory slots, Level level) {
        if (level == null) {
            return List.of();
        }
        List<IPatternDetails> decoded = new ArrayList<>();
        int slotCount = slots.size();
        for (int slot = 0; slot < slotCount; slot++) {
            PatternDiskContents contents = PatternDiskApi.contents(slots.getStackInSlot(slot));
            if (contents != null) {
                decoded.addAll(PatternDiskApi.decodePatterns(contents, level));
            }
        }
        return decoded;
    }

    @Override
    public boolean canAcceptAuxiliary(InternalInventory slots, ItemStack pattern, Level level) {
        int slotCount = slots.size();
        for (int slot = 0; slot < slotCount; slot++) {
            if (PatternDiskApi.canAccept(slots.getStackInSlot(slot), pattern, level)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A question about a stack, not about a world: this is what a client asks before sending a quick move,
     * where no grid is reachable.
     */
    @Override
    public boolean isAuxiliaryContainer(ItemStack stack) {
        return PatternDiskApi.isPatternDisk(stack);
    }

    private static final class SlotsHolder implements PatternDiskSupport.TrackedHolder {
        private final InternalInventory slots;
        private final BitSet disks = new BitSet();
        private final TreeMap<Integer, PatternDiskContents> snapshots = new TreeMap<>();
        private List<ItemStack> patterns = List.of();
        private int indexedSize = -1;
        private long revision;

        SlotsHolder(InternalInventory slots) {
            this.slots = slots;
        }

        @Override
        public boolean ownsAuxiliary(ItemStack stack) {
            return PatternDiskApi.isPatternDisk(stack);
        }

        @Override
        public void slotChanged(int slot) {
            if (slot < 0) indexedSize = -1;
            int size = slots.size();
            if (size != indexedSize) {
                rebuildSlots(size);
            } else if (slot >= 0 && slot < size) {
                disks.set(slot, ownsAuxiliary(slots.getStackInSlot(slot)));
            }
        }

        private void rebuildSlots(int size) {
            disks.clear();
            for (int slot = 0; slot < size; slot++) {
                if (ownsAuxiliary(slots.getStackInSlot(slot))) disks.set(slot);
            }
            indexedSize = size;
        }

        @Override
        public boolean hasDisks() {
            int size = slots.size();
            if (size != indexedSize) rebuildSlots(size);
            return !disks.isEmpty();
        }

        @Override
        public int firstDiskSlot() {
            return hasDisks() ? disks.nextSetBit(0) : -1;
        }

        @Override
        public boolean hasAuxiliaryRoom() {
            refresh();
            return snapshots.values().stream().anyMatch(contents -> !contents.isFull());
        }

        @Override
        public List<ItemStack> getAuxiliaryEncodedPatterns() {
            refresh();
            return patterns;
        }

        @Override
        public long getAuxiliaryRevision() {
            refresh();
            return revision;
        }

        @Override
        public boolean canAccept(ItemStack pattern, Level level) {
            if (!hasDisks()) return false;
            for (int slot = disks.nextSetBit(0); slot >= 0; slot = disks.nextSetBit(slot + 1)) {
                if (PatternDiskApi.canAccept(slots.getStackInSlot(slot), pattern, level)) return true;
            }
            return false;
        }

        @Override
        public boolean insert(ItemStack pattern, Level level) {
            return hasDisks() && insertAuxiliary(slots, pattern, level, disks);
        }

        @Override
        public List<IPatternDetails> decode(Level level) {
            refresh();
            if (level == null || snapshots.isEmpty()) return List.of();
            var decoded = new ArrayList<IPatternDetails>();
            for (var contents : snapshots.values()) decoded.addAll(PatternDiskApi.decodePatterns(contents, level));
            return decoded;
        }

        private void refresh() {
            hasDisks();
            boolean changed = snapshots.keySet().removeIf(slot -> !disks.get(slot));
            for (int slot = disks.nextSetBit(0); slot >= 0; slot = disks.nextSetBit(slot + 1)) {
                PatternDiskContents contents = PatternDiskApi.contents(slots.getStackInSlot(slot));
                if (contents == null) changed |= snapshots.remove(slot) != null;
                else if (!sameContents(snapshots.get(slot), contents)) {
                    snapshots.put(slot, contents);
                    changed = true;
                }
            }
            if (!changed) return;
            List<ItemStack> collected = new ArrayList<>();
            for (PatternDiskContents contents : snapshots.values()) collected.addAll(contents.patterns());
            patterns = List.copyOf(collected);
            revision++;
        }

        private static boolean sameContents(PatternDiskContents previous, PatternDiskContents current) {
            if (previous == current) return true;
            if (previous == null || previous.capacity() != current.capacity()
                    || !Objects.equals(previous.type(), current.type())) return false;
            var before = previous.patterns();
            var after = current.patterns();
            if (before.size() != after.size()) return false;
            for (int index = 0; index < before.size(); index++) {
                if (!ItemStack.matches(before.get(index), after.get(index))) return false;
            }
            return true;
        }
    }
}
