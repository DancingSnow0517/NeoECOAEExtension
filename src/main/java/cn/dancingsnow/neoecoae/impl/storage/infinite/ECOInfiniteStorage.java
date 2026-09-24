package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import java.util.function.BooleanSupplier;
import java.math.BigInteger;
import net.minecraft.network.chat.Component;

public final class ECOInfiniteStorage implements MEStorage, ExactAmountSource, ECOBigIntegerStorage {
    private final ECOInfiniteStorageEngine engine;
    private final Component description;
    private final BooleanSupplier accessible;

    public ECOInfiniteStorage(ECOInfiniteStorageEngine engine, Component description) {
        this(engine, description, () -> true);
    }

    public ECOInfiniteStorage(ECOInfiniteStorageEngine engine, Component description, BooleanSupplier accessible) {
        this.engine = engine;
        this.description = description;
        this.accessible = accessible;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!accessible.getAsBoolean() || !ECOStorageCell.canStoreKeyInsideStorageCell(what)) {
            return 0L;
        }
        return engine.insert(what, amount, mode);
    }

    @Override
    public BigInteger insertBigInteger(AEKey what, BigInteger amount, Actionable mode, IActionSource source) {
        if (amount == null || amount.signum() <= 0 || !accessible.getAsBoolean()
                || !ECOStorageCell.canStoreKeyInsideStorageCell(what)) {
            return BigInteger.ZERO;
        }
        return engine.insert(what, amount, mode);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return accessible.getAsBoolean() ? engine.extract(what, amount, mode) : 0L;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (accessible.getAsBoolean()) engine.getAvailableStacks(out);
    }

    public HugeAmount getExactAmount(AEKey key) {
        return accessible.getAsBoolean() ? engine.getAmount(key) : HugeAmount.ZERO;
    }

    @Override
    public Object neoecoae$exactInventoryIdentity() {
        return engine;
    }

    @Override
    public java.math.BigInteger neoecoae$getExactAmount(AEKey key) {
        return getExactAmount(key).toBigInteger();
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return accessible.getAsBoolean() && engine.getAmount(what).compareTo(HugeAmount.ZERO) > 0;
    }

    @Override
    public Component getDescription() {
        return description;
    }
}
