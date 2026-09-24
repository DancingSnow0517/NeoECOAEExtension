package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import java.math.BigInteger;
import java.util.function.BooleanSupplier;
import net.minecraft.network.chat.Component;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource;

public final class ECOInfiniteStorage implements MEStorage, ExactAmountSource, ECOBigIntegerStorage {
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
        if (!allowInsert.getAsBoolean() || !canStoreForInsert(what)) {
            return 0L;
        }
        return engine.insert(what, amount, mode);
    }

    @Override
    public BigInteger insertBigInteger(AEKey what, BigInteger amount, Actionable mode, IActionSource source) {
        if (amount == null || amount.signum() <= 0 || !allowInsert.getAsBoolean()
                || !canStoreForInsert(what)) {
            return BigInteger.ZERO;
        }
        return engine.insert(what, amount, mode);
    }

    private boolean canStoreForInsert(AEKey key) {
        if (key instanceof AEItemKey itemKey
                && itemKey.getItem() instanceof IBasicECOCellItem
                && !engine.getAmount(key).isZero()) {
            // Existing ECO cells were already admitted. Avoid probing their nested inventory on every transfer.
            return true;
        }
        return ECOStorageCell.canStoreKeyInsideStorageCell(key);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!allowInsert.getAsBoolean()) return 0L;
        return engine.extract(what, amount, mode);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (!allowInsert.getAsBoolean()) return;
        engine.getAvailableStacks(out);
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return allowInsert.getAsBoolean() && engine.getAmount(what).compareTo(HugeAmount.ZERO) > 0;
    }

    @Override
    public Component getDescription() {
        return description;
    }

    @Override
    public void neoecoae$visitExactAmounts(java.util.function.BiConsumer<AEKey, ExactAmount> visitor) {
        if (!allowInsert.getAsBoolean()) return;
        KeyCounter keys = new KeyCounter();
        engine.getAvailableStacks(keys);
        for (var entry : keys) {
            var amount = engine.getAmount(entry.getKey());
            if (amount.isBig()) visitor.accept(entry.getKey(), ExactAmount.finite(amount.toBigInteger()));
        }
    }
}
