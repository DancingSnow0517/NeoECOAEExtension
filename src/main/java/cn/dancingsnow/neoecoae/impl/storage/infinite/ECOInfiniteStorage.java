package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import java.math.BigInteger;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.network.chat.Component;

public final class ECOInfiniteStorage implements MEStorage, ExactAmountSource, ECOBigIntegerStorage {
    private static final int MAX_CELL_ELIGIBILITY_CACHE = 131_072;
    private final ECOInfiniteStorageEngine engine;
    private final Component description;
    private final BooleanSupplier accessible;
    private final Set<AEItemKey> ordinaryKeys = ConcurrentHashMap.newKeySet();

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
        if (!accessible.getAsBoolean() || !canStoreKey(what)) {
            return 0L;
        }
        return engine.insert(what, amount, mode);
    }

    @Override
    public BigInteger insertBigInteger(AEKey what, BigInteger amount, Actionable mode, IActionSource source) {
        if (amount == null || amount.signum() <= 0 || !accessible.getAsBoolean() || !canStoreKey(what)) {
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

    private boolean canStoreKey(AEKey key) {
        if (!(key instanceof AEItemKey itemKey)) return true;
        if (ordinaryKeys.contains(itemKey)) return true;
        // A cell's capacity can change outside its item NBT, so only handler misses are cached.
        if (itemKey.getItem() instanceof IBasicECOCellItem || StorageCells.isCellHandled(itemKey.getReadOnlyStack())) {
            return ECOStorageCell.canStoreKeyInsideStorageCell(itemKey);
        }
        if (ordinaryKeys.size() < MAX_CELL_ELIGIBILITY_CACHE) ordinaryKeys.add(itemKey);
        return true;
    }
}
