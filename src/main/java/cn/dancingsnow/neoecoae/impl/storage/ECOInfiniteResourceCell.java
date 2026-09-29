package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.api.storage.IECOUnboundedSource;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.items.ECOInfiniteResourceCellItem;
import java.math.BigInteger;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Generates and absorbs only the fixed base-resource keys; it has no finite backend. */
public final class ECOInfiniteResourceCell extends ECOStorageCell implements IECOUnboundedSource, ExactAmountSource {
    private final ItemStack stack;

    public ECOInfiniteResourceCell(ItemStack stack, @Nullable ISaveProvider container) {
        super(stack, container, false);
        this.stack = stack;
    }

    private boolean serves(AEKey key) {
        return ECOInfiniteResourceCellItem.isLockedKey(key) && !ECOInfiniteStorageMember.isSealed(stack);
    }

    @Override public boolean isInfiniteStorageEligible() { return false; }
    @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return amount > 0 && serves(what) ? amount : 0L;
    }
    @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return amount > 0 && serves(what) ? amount : 0L;
    }
    @Override public void getAvailableStacks(KeyCounter out) {
        if (ECOInfiniteStorageMember.isSealed(stack)) return;
        for (AEKey key : ECOInfiniteResourceCellItem.lockedKeys()) {
            // A direct combined listing may already contain finite stock. Never wrap it negative.
            out.set(key, Long.MAX_VALUE);
        }
    }
    @Override public void getMigrationStacks(KeyCounter out) {}
    @Override public void clearMigrationStacks() {}
    @Override public long simulateInsertForMigration(AEKey key, long amount, KeyCounter contents) { return 0L; }
    @Override public long simulateInsertForMigration(AEKey key, long amount, KeyCounter contents,
                                                      long types, long total) { return 0L; }
    @Override public long insertForMigration(AEKey key, long amount, Actionable mode) { return 0L; }
    @Override public long getUsedBytesForMigration(KeyCounter contents) { return 0L; }
    @Override public long getStoredItemTypes() { return ECOInfiniteResourceCellItem.lockedKeys().size(); }
    @Override public long getStoredItemCount() { return Long.MAX_VALUE; }
    @Override public long getUsedBytes() { return 0L; }
    @Override public CellState getStatus() { return CellState.NOT_EMPTY; }
    @Override public boolean canFitInsideCell() { return false; }
    @Override public boolean isPreferredStorageFor(AEKey key, IActionSource source) { return serves(key); }
    @Override public BigInteger neoecoae$getExactAmount(AEKey key) {
        return serves(key) ? BigInteger.valueOf(Long.MAX_VALUE) : BigInteger.ZERO;
    }
    @Override public ExactAmount neoecoae$getDisplayAmount(AEKey key) {
        return serves(key) ? ExactAmount.unbounded() : ExactAmount.finite(BigInteger.ZERO);
    }
    @Override public Object neoecoae$exactInventoryIdentity() { return stack; }
}
