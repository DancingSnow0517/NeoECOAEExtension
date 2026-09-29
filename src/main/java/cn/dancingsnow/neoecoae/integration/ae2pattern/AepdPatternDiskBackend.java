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
import java.util.List;
import java.util.Objects;
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
        List<Integer> claimed = new ArrayList<>();
        List<Integer> empty = new ArrayList<>();
        for (int slot = 0; slot < slots.size(); slot++) {
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
        for (int slot = 0; slot < slots.size(); slot++) {
            PatternDiskContents contents = PatternDiskApi.contents(slots.getStackInSlot(slot));
            if (contents != null) {
                decoded.addAll(PatternDiskApi.decodePatterns(contents, level));
            }
        }
        return List.copyOf(decoded);
    }

    @Override
    public boolean canAcceptAuxiliary(InternalInventory slots, ItemStack pattern, Level level) {
        for (int slot = 0; slot < slots.size(); slot++) {
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

    /**
     * Reads a host's pattern slots and reports the disks found in them.
     *
     * <p>The disks themselves are the version: a disk's contents live in a data component, so an in-place
     * write to one changes how the stack compares. That makes "have the disks changed" a comparison of the
     * stacks rather than a tally kept in parallel with them - nothing can drift out of step with the
     * inventory, and a disk swapped for an identical one is correctly seen as no change at all.</p>
     *
     * <p>Everything is derived on demand and cached against that comparison, because the revision is read on
     * every catalog index refresh and the pattern list on every crafting request. Scanning the host's slots
     * is a handful of array reads; decoding is what the caller does with the result, and it does that against
     * its own cache.</p>
     */
    private static final class SlotsHolder implements AuxiliaryPatternHolder {

        private final InternalInventory slots;

        /** What the disks held on the last refresh, for change detection. */
        private List<PatternDiskContents> contents = List.of();
        private List<ItemStack> patterns = List.of();
        private long revision;

        SlotsHolder(InternalInventory slots) {
            this.slots = slots;
        }

        @Override
        public boolean ownsAuxiliary(ItemStack stack) {
            return PatternDiskApi.isPatternDisk(stack);
        }

        @Override
        public boolean hasAuxiliaryRoom() {
            for (int slot = 0; slot < slots.size(); slot++) {
                ItemStack stack = slots.getStackInSlot(slot);
                if (!PatternDiskApi.isPatternDisk(stack)) {
                    continue;
                }
                PatternDiskContents contents = PatternDiskApi.contents(stack);
                if (contents != null && !contents.isFull()) {
                    return true;
                }
            }
            return false;
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

        /**
         * Re-derives the disk list when the disks' contents moved.
         *
         * <p>Compared by contents, not by stack identity. A write to a disk mutates the stack the host already
         * holds, so the stack a scan reads afterwards <em>is</em> the one it read before - a comparison against
         * that would answer "unchanged" for every write. The contents record cannot help either: it holds
         * {@code ItemStack}s, which compare by identity, so this walks them.</p>
         */
        private void refresh() {
            List<ItemStack> current = disksInSlots();
            List<PatternDiskContents> currentContents = new ArrayList<>(current.size());
            for (ItemStack disk : current) {
                currentContents.add(PatternDiskApi.contents(disk));
            }
            if (sameContents(currentContents, contents)) {
                return;
            }
            contents = List.copyOf(currentContents);
            List<ItemStack> collected = new ArrayList<>();
            for (PatternDiskContents diskContents : contents) {
                if (diskContents != null) {
                    collected.addAll(diskContents.patterns());
                }
            }
            patterns = List.copyOf(collected);
            revision++;
        }

        /** @return whether two disk-content snapshots describe the same patterns */
        private static boolean sameContents(List<PatternDiskContents> first, List<PatternDiskContents> second) {
            if (first.size() != second.size()) {
                return false;
            }
            for (int disk = 0; disk < first.size(); disk++) {
                PatternDiskContents left = first.get(disk);
                PatternDiskContents right = second.get(disk);
                if (left == null || right == null) {
                    if (left != right) {
                        return false;
                    }
                    continue;
                }
                if (left.capacity() != right.capacity() || !Objects.equals(left.type(), right.type())) {
                    return false;
                }
                if (!samePatterns(left.patterns(), right.patterns())) {
                    return false;
                }
            }
            return true;
        }

        private static boolean samePatterns(List<ItemStack> first, List<ItemStack> second) {
            if (first.size() != second.size()) {
                return false;
            }
            for (int index = 0; index < first.size(); index++) {
                if (!ItemStack.matches(first.get(index), second.get(index))) {
                    return false;
                }
            }
            return true;
        }

        private List<ItemStack> disksInSlots() {
            List<ItemStack> found = new ArrayList<>();
            for (int slot = 0; slot < slots.size(); slot++) {
                ItemStack stack = slots.getStackInSlot(slot);
                if (PatternDiskApi.isPatternDisk(stack)) {
                    found.add(stack);
                }
            }
            return found;
        }
    }
}
