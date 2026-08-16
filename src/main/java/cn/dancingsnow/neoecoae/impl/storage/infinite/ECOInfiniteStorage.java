package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import net.minecraft.network.chat.Component;

public final class ECOInfiniteStorage implements MEStorage {
    private final ECOInfiniteStorageEngine engine;
    private final Component description;

    public ECOInfiniteStorage(ECOInfiniteStorageEngine engine, Component description) {
        this.engine = engine;
        this.description = description;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!ECOStorageCell.canStoreKeyInsideStorageCell(what)) {
            return 0L;
        }
        return engine.insert(what, amount, mode);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return engine.extract(what, amount, mode);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        engine.getAvailableStacks(out);
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        // Keep preference consistent with insert(): storage-cell items that cannot be
        // nested must never be advertised as preferred, or AE2 may route an insert
        // here only to receive a zero-amount rejection.
        return ECOStorageCell.canStoreKeyInsideStorageCell(what)
            && engine.getAmount(what).compareTo(HugeAmount.ZERO) > 0;
    }

    @Override
    public Component getDescription() {
        return description;
    }
}
