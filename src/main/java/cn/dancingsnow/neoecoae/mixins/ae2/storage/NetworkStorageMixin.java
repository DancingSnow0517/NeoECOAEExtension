package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.AEKey;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import cn.dancingsnow.neoecoae.impl.storage.SaturatingStackAccumulator;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import java.math.BigInteger;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;

/** Prevents storage totals from disappearing when an unbounded stack is combined with another inventory. */
@Mixin(NetworkStorage.class)
public abstract class NetworkStorageMixin implements ECOBigIntegerStorage {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    @Shadow private boolean mountsInUse;
    @Shadow @Final private NavigableMap<Integer, List<MEStorage>> priorityInventory;
    @Shadow @Final private List<MEStorage> secondPassInventories;

    @Shadow private boolean isQueuedForRemoval(MEStorage inventory) { throw new AssertionError(); }
    @Shadow private void flushQueuedOperations() { throw new AssertionError(); }

    @Override
    public BigInteger insertBigInteger(AEKey what, BigInteger amount, Actionable mode, IActionSource source) {
        Objects.requireNonNull(what, "what");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(source, "source");
        if (amount.signum() < 0) throw new IllegalArgumentException("Amount must not be negative");
        if (amount.signum() == 0) return BigInteger.ZERO;
        if (amount.compareTo(MAX_LONG) <= 0) {
            long offered = amount.longValueExact();
            return checked(amount, BigInteger.valueOf(((MEStorage) (Object) this)
                    .insert(what, offered, mode, source)));
        }
        if (mountsInUse) return BigInteger.ZERO;

        // Let ordinary ME storage handle one standard-sized window, using AE2's original priority routing.
        BigInteger accepted = checked(MAX_LONG, BigInteger.valueOf(((MEStorage) (Object) this)
                .insert(what, Long.MAX_VALUE, mode, source)));
        BigInteger remaining = amount.subtract(accepted);
        if (remaining.signum() == 0) return accepted;

        // Only storage that explicitly implements the exact side-channel receives the amount beyond that window.
        mountsInUse = true;
        try {
            for (var inventoryList : priorityInventory.values()) {
                secondPassInventories.clear();
                var iterator = inventoryList.iterator();
                while (iterator.hasNext() && remaining.signum() > 0) {
                    MEStorage inventory = iterator.next();
                    if (isQueuedForRemoval(inventory) || !(inventory instanceof ECOBigIntegerStorage exact)) continue;
                    if (inventory.isPreferredStorageFor(what, source)) {
                        remaining = remaining.subtract(insertExact(exact, what, remaining, mode, source));
                    } else {
                        secondPassInventories.add(inventory);
                    }
                }
                for (MEStorage inventory : secondPassInventories) {
                    if (remaining.signum() <= 0) break;
                    if (!isQueuedForRemoval(inventory) && inventory instanceof ECOBigIntegerStorage exact) {
                        remaining = remaining.subtract(insertExact(exact, what, remaining, mode, source));
                    }
                }
            }
        } finally {
            mountsInUse = false;
        }
        flushQueuedOperations();
        return amount.subtract(remaining);
    }

    private static BigInteger insertExact(ECOBigIntegerStorage inventory, AEKey what, BigInteger amount,
            Actionable mode, IActionSource source) {
        return checked(amount, inventory.insertBigInteger(what, amount, mode, source));
    }

    private static BigInteger checked(BigInteger offered, BigInteger inserted) {
        if (inserted == null || inserted.signum() < 0 || inserted.compareTo(offered) > 0) {
            throw new IllegalStateException("Invalid storage insertion result: " + inserted + " for " + offered);
        }
        return inserted;
    }

    @WrapOperation(
        method = "extract",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/MEStorage;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J")
    )
    private long neoecoae$filterCreativeInput(MEStorage storage, appeng.api.stacks.AEKey key, long amount,
            appeng.api.config.Actionable mode, appeng.api.networking.security.IActionSource source,
            Operation<Long> original) {
        if (cn.dancingsnow.neoecoae.impl.storage.transfer.StorageExtractionExclusions.contains(storage)) {
            return 0L;
        }
        return cn.dancingsnow.neoecoae.impl.storage.transfer.ECOCreativeExtractionFilter.extractSource(
            storage, key, mode, () -> original.call(storage, key, amount, mode, source));
    }

    @WrapOperation(
        method = "getAvailableStacks",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/api/storage/MEStorage;getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V"
        )
    )
    private void neoecoae$getAvailableStacksSaturated(
        MEStorage storage, KeyCounter output, Operation<Void> original
    ) {
        // Each mounted inventory writes into an isolated counter. Merging it ourselves makes
        // Long.MAX_VALUE + a normal disk amount saturate instead of wrapping negative, regardless
        // of mount order (the infinite resource matrix may be visited first or last).
        KeyCounter contribution = new KeyCounter();
        original.call(storage, contribution);
        ExactAmountCollector.observe(storage, contribution);
        SaturatingStackAccumulator.addAll(output, contribution);
    }
}
