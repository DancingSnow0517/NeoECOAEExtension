package cn.dancingsnow.neoecoae.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.storage.StorageListingDelta;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import java.math.BigInteger;
import java.util.function.BooleanSupplier;
import net.minecraft.network.chat.Component;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.CombinedExactAmountSource;
import cn.dancingsnow.neoecoae.crafting.display.terminal.SparseExactAmountSource;

public final class ECOInfiniteStorage implements MEStorage, CombinedExactAmountSource, ECOBigIntegerStorage,
        StorageListingDelta, SparseExactAmountSource {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private final ECOInfiniteStorageEngine engine;
    private final Component description;
    private final BooleanSupplier allowInsert;

    public ECOInfiniteStorage(ECOInfiniteStorageEngine engine, Component description, BooleanSupplier allowInsert) {
        this.engine = engine;
        this.description = description;
        this.allowInsert = allowInsert;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (what == null || amount <= 0 || !allowInsert.getAsBoolean()) {
            return 0L;
        }
        // A world-owned domain stores opaque keys, not a portable cell's nested inventory.
        // Opening third-party cell inventories here can perform I/O or mutate the key's stack.
        return engine.insert(what, amount, mode);
    }

    @Override
    public BigInteger insertBigInteger(AEKey what, BigInteger amount, Actionable mode, IActionSource source) {
        if (what == null || amount == null || amount.signum() <= 0 || !allowInsert.getAsBoolean()) {
            return BigInteger.ZERO;
        }
        return engine.insert(what, amount, mode);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (what == null || amount <= 0 || !allowInsert.getAsBoolean()) return 0L;
        return engine.extract(what, amount, mode);
    }

    @Override public BigInteger extractBigInteger(AEKey what, BigInteger amount, Actionable mode, IActionSource source) {
        if (what == null || amount == null || amount.signum() <= 0 || !allowInsert.getAsBoolean()) return BigInteger.ZERO;
        return engine.extract(what, amount, mode);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (!allowInsert.getAsBoolean()) return;
        engine.getAvailableStacks(out);
    }

    @Override
    public boolean neoecoae$hasSaturatingListing() { return engine instanceof SavedDataInfiniteStorageEngine; }

    @Override public ExactAmount neoecoae$exactAmount(AEKey key) {
        if (!(engine instanceof SavedDataInfiniteStorageEngine saved)) return null;
        return ExactAmount.finite(allowInsert.getAsBoolean() ? saved.exactAmount(key) : BigInteger.ZERO);
    }

    @Override
    public long neoecoae$listingVersion() {
        // A disabled view lists nothing, whatever the engine holds.
        return allowInsert.getAsBoolean() ? engine.listingVersion() : -1L;
    }

    @Override
    public boolean neoecoae$visitListingChanges(long since, long current, java.util.function.ObjLongConsumer<AEKey> visitor) {
        // A disabled view reports -1 and lists nothing; any transition through it needs a full listing.
        if (since < 0L || current < 0L || !allowInsert.getAsBoolean()) return false;
        return engine.visitListingChanges(since, current, visitor);
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

    @Override
    public void neoecoae$listWithExactAmounts(KeyCounter out, java.util.function.ObjLongConsumer<AEKey> ordinary,
            java.util.function.BiConsumer<AEKey, ExactAmount> exact) {
        if (!(engine instanceof SavedDataInfiniteStorageEngine saved)) {
            neoecoae$listWithExactAmounts(out, exact);
            return;
        }
        if (!allowInsert.getAsBoolean()) return;
        boolean empty = out.isEmpty();
        saved.visitAmounts((key, amount) -> {
            if (empty) out.set(key, amount);
            else SavedDataInfiniteStorageEngine.addVisible(out, key, amount);
            ordinary.accept(key, amount);
        }, (key, amount) -> {
            if (empty) out.set(key, Long.MAX_VALUE);
            else SavedDataInfiniteStorageEngine.addVisible(out, key, Long.MAX_VALUE);
            exact.accept(key, ExactAmount.finite(amount));
        });
    }

    @Override
    public boolean neoecoae$hasSparseExactAmounts() { return engine instanceof SavedDataInfiniteStorageEngine; }

    @Override
    public boolean neoecoae$mayHaveExceptionalAmounts() { return allowInsert.getAsBoolean() && engine.hasHugeStacks(); }

    @Override
    public void neoecoae$visitExceptionalAmounts(java.util.function.BiConsumer<AEKey, ExactAmount> visitor) {
        if (!allowInsert.getAsBoolean()) return;
        if (engine instanceof SavedDataInfiniteStorageEngine saved) {
            saved.visitOverflowAmounts((key, amount) -> visitor.accept(key, ExactAmount.finite(amount)));
        } else neoecoae$visitExactAmounts(visitor);
    }

    @Override
    public ExactAmount neoecoae$exceptionalAmount(AEKey key) {
        if (!allowInsert.getAsBoolean()) return null;
        if (engine instanceof SavedDataInfiniteStorageEngine saved) {
            BigInteger amount = saved.overflowAmount(key);
            return amount == null ? null : ExactAmount.finite(amount);
        }
        return null;
    }
}
