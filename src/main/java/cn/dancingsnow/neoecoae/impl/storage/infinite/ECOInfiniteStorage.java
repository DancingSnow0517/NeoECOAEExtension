package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import java.util.function.BooleanSupplier;
import net.minecraft.network.chat.Component;
import cn.dancingsnow.neoecoae.terminal.bigamount.ExactAmount;
import cn.dancingsnow.neoecoae.terminal.bigamount.ExactAmountSource;

public final class ECOInfiniteStorage implements MEStorage, ExactAmountSource {
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
        if (!allowInsert.getAsBoolean() || !ECOStorageCell.canStoreKeyInsideStorageCell(what)) {
            return 0L;
        }
        return engine.insert(what, amount, mode);
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
