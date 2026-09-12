package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.items.ECOInfiniteResourceCellItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Inventory behind the ECO infinite base resource storage matrix.
 *
 * <p>The cell is a hard-locked source and sink for a small fixed set of base resources
 * (see {@link ECOInfiniteResourceCellItem#isBlackListed}). It neither consumes nor depletes storage:
 * every insert is accepted in full and every extract is served in full, while the available amount
 * always reports {@link Long#MAX_VALUE}. That reads as an unbounded supply that also swallows
 * whatever the network can spare, without the byte accounting of a finite cell.
 *
 * <p>Deliberately bypasses {@link ECOStorageCell}'s byte/type bookkeeping in {@link #insert},
 * {@link #extract} and the migration simulation. Those paths saturate on
 * {@code freeBytes * amountPerByte}, which overflows for a cell whose total capacity is
 * {@link Long#MAX_VALUE}; leaning on them would silently degrade the cell into an ordinary one.
 */
public class ECOInfiniteResourceCell extends ECOStorageCell {

    /** Offered amount for the locked keys; the project-wide "unbounded" sentinel, rendered as ∞. */
    private static final long OFFERED_AMOUNT = Long.MAX_VALUE;

    public ECOInfiniteResourceCell(ItemStack cellStack, @Nullable ISaveProvider container) {
        super(cellStack, container);
    }

    private boolean isLockedKey(AEKey what) {
        return ECOInfiniteResourceCellItem.isLockedKey(what);
    }

    private boolean isServable(AEKey what) {
        return what != null && isLockedKey(what)
            && !ECOInfiniteStorageMember.isSealed(cellStack);
    }

    /**
     * Always {@code false}: this cell must never be migrated into the ECO infinite storage domain.
     * It is an unbounded supply of its own, so absorbing it would erase the distinction between
     * "stored" and "always available".
     */
    @Override
    public boolean isInfiniteStorageEligible() {
        return false;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        // Accept in full and store nothing: the reported supply is constant, so input is a void sink.
        return amount > 0L && isServable(what) ? amount : 0L;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        // Serve in full without depleting anything, so the supply never runs out.
        return amount > 0L && isServable(what) ? amount : 0L;
    }

    @Override
    public long simulateInsertForMigration(AEKey what, long amount, KeyCounter simulatedContents) {
        return insert(what, amount, Actionable.SIMULATE, null);
    }

    @Override
    public long getUsedBytesForMigration(KeyCounter simulatedContents) {
        return 0L;
    }

    /**
     * Reports the locked keys as always available without ever writing them to NBT. The cell is
     * excluded from the infinite storage domain by {@link #isInfiniteStorageEligible()}, so no
     * migration path reads this.
     */
    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (ECOInfiniteStorageMember.isSealed(cellStack)) {
            return;
        }
        for (AEKey key : ECOInfiniteResourceCellItem.lockedKeys()) {
            // KeyCounter.add performs a plain long addition.  Adding an unbounded
            // value after a normal cell (or after another infinite cell) would
            // therefore wrap to a negative amount (for example -9.2P in AE2).
            // Saturate the aggregate instead; Long.MAX_VALUE is AE2's largest
            // representable amount and already conveys the unbounded supply.
            long existing = out.get(key);
            if (existing < 0L || existing >= Long.MAX_VALUE - OFFERED_AMOUNT) {
                out.set(key, Long.MAX_VALUE);
            } else {
                out.add(key, OFFERED_AMOUNT);
            }
        }
    }
}
