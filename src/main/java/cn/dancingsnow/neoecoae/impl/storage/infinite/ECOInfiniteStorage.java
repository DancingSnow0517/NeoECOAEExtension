package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.me.cells.BasicCellInventory;
import appeng.items.storage.BasicStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import java.math.BigInteger;
import java.util.function.BooleanSupplier;
import net.minecraft.network.chat.Component;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.CombinedExactAmountSource;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;

public final class ECOInfiniteStorage implements MEStorage, CombinedExactAmountSource, ECOBigIntegerStorage {
    private static final int ADMISSION_CACHE_LIMIT = 512;
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private final ECOInfiniteStorageEngine engine;
    private final Component description;
    private final BooleanSupplier allowInsert;
    // Only stack-owned built-in inventories and keys with no registered handler are stable.
    // UUID-backed or third-party handlers are probed on every insertion.
    private final Object2ByteLinkedOpenHashMap<AEKey> admission = new Object2ByteLinkedOpenHashMap<>();

    public ECOInfiniteStorage(ECOInfiniteStorageEngine engine, Component description, BooleanSupplier allowInsert) {
        this.engine = engine;
        this.description = description;
        this.allowInsert = allowInsert;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (what == null || amount <= 0 || !allowInsert.getAsBoolean() || !canStoreForInsert(what)) {
            return 0L;
        }
        return engine.insert(what, amount, mode);
    }

    @Override
    public BigInteger insertBigInteger(AEKey what, BigInteger amount, Actionable mode, IActionSource source) {
        if (what == null || amount == null || amount.signum() <= 0 || !allowInsert.getAsBoolean()
                || !canStoreForInsert(what)) {
            return BigInteger.ZERO;
        }
        return engine.insert(what, amount, mode);
    }

    private boolean canStoreForInsert(AEKey key) {
        if (!(key instanceof AEItemKey itemKey)) return true;
        if (itemKey.getItem() instanceof IBasicECOCellItem && engine.contains(key)) {
            // A key already admitted by this domain does not need another nested-cell probe.
            return true;
        }
        byte cached = admission.getByte(key);
        if (cached != 0) return cached > 0;
        var stack = itemKey.getReadOnlyStack();
        var inventory = StorageCells.getCellInventory(stack, null);
        boolean accepted = inventory == null || inventory.canFitInsideCell();
        if (inventory == null && StorageCells.getHandler(stack) == null
                || inventory != null && (inventory.getClass() == BasicCellInventory.class
                && stack.getItem().getClass() == BasicStorageCell.class
                || inventory.getClass() == ECOStorageCell.class
                && stack.getItem().getClass() == ECOStorageCellItem.class)) {
            if (admission.size() == ADMISSION_CACHE_LIMIT) admission.removeFirstByte();
            admission.put(key, (byte) (accepted ? 1 : -1));
        }
        return accepted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (what == null || amount <= 0 || !allowInsert.getAsBoolean()) return 0L;
        return engine.extract(what, amount, mode);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (!allowInsert.getAsBoolean()) return;
        engine.getAvailableStacks(out);
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return what != null && allowInsert.getAsBoolean() && engine.contains(what);
    }

    @Override
    public Component getDescription() {
        return description;
    }

    @Override
    public void neoecoae$visitExactAmounts(java.util.function.BiConsumer<AEKey, ExactAmount> visitor) {
        if (!allowInsert.getAsBoolean()) return;
        engine.visitExactAmounts((key, amount) -> visitor.accept(key, ExactAmount.finite(amount)));
    }

    @Override
    public void neoecoae$listWithExactAmounts(KeyCounter out,
            java.util.function.BiConsumer<AEKey, ExactAmount> visitor) {
        if (!allowInsert.getAsBoolean()) return;
        boolean empty = out.isEmpty();
        engine.visitExactAmounts((key, amount) -> {
            long visible = amount.min(MAX_LONG).longValue();
            if (empty) out.set(key, visible);
            else SavedDataInfiniteStorageEngine.addVisible(out, key, visible);
            visitor.accept(key, ExactAmount.finite(amount));
        });
    }
}
