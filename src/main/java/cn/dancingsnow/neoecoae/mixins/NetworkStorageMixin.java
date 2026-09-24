package cn.dancingsnow.neoecoae.mixins;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountCollector;
import cn.dancingsnow.neoecoae.impl.storage.SaturatingStackAccumulator;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.math.BigInteger;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = NetworkStorage.class, remap = false)
public abstract class NetworkStorageMixin implements ECOBigIntegerStorage {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    @Shadow private boolean mountsInUse;
    @Shadow private NavigableMap<Integer, List<MEStorage>> priorityInventory;
    @Shadow private List<MEStorage> secondPassInventories;

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
            return checked(amount, BigInteger.valueOf(((MEStorage) (Object) this)
                    .insert(what, amount.longValueExact(), mode, source)));
        }
        if (mountsInUse) return BigInteger.ZERO;
        if (mode == Actionable.SIMULATE) return simulateBigInteger(what, amount, source);

        // Preserve AE2 priority routing for the first long-sized window.
        BigInteger accepted = checked(MAX_LONG, BigInteger.valueOf(((MEStorage) (Object) this)
                .insert(what, Long.MAX_VALUE, mode, source)));
        BigInteger remaining = amount.subtract(accepted);
        if (remaining.signum() == 0) return accepted;

        mountsInUse = true;
        try {
            for (var inventoryList : priorityInventory.values()) {
                secondPassInventories.clear();
                for (MEStorage inventory : inventoryList) {
                    if (remaining.signum() == 0) break;
                    if (isQueuedForRemoval(inventory) || !(inventory instanceof ECOBigIntegerStorage exact)) continue;
                    if (inventory.isPreferredStorageFor(what, source)) {
                        remaining = remaining.subtract(checked(remaining,
                                exact.insertBigInteger(what, remaining, mode, source)));
                    } else {
                        secondPassInventories.add(inventory);
                    }
                }
                for (MEStorage inventory : secondPassInventories) {
                    if (remaining.signum() == 0) break;
                    if (!isQueuedForRemoval(inventory) && inventory instanceof ECOBigIntegerStorage exact) {
                        remaining = remaining.subtract(checked(remaining,
                                exact.insertBigInteger(what, remaining, mode, source)));
                    }
                }
            }
        } finally {
            mountsInUse = false;
        }
        flushQueuedOperations();
        return amount.subtract(remaining);
    }

    private BigInteger simulateBigInteger(AEKey what, BigInteger amount, IActionSource source) {
        BigInteger remaining = amount;
        long ordinaryBudget = Long.MAX_VALUE;
        mountsInUse = true;
        try {
            for (var inventoryList : priorityInventory.values()) {
                secondPassInventories.clear();
                for (MEStorage inventory : inventoryList) {
                    if (remaining.signum() == 0) break;
                    if (isQueuedForRemoval(inventory)) continue;
                    if (inventory.isPreferredStorageFor(what, source)) {
                        BigInteger accepted = simulateMount(inventory, what, remaining, ordinaryBudget, source);
                        remaining = remaining.subtract(accepted);
                        ordinaryBudget = consumeBudget(ordinaryBudget, accepted);
                    } else {
                        secondPassInventories.add(inventory);
                    }
                }
                for (MEStorage inventory : secondPassInventories) {
                    if (remaining.signum() == 0) break;
                    if (isQueuedForRemoval(inventory)) continue;
                    BigInteger accepted = simulateMount(inventory, what, remaining, ordinaryBudget, source);
                    remaining = remaining.subtract(accepted);
                    ordinaryBudget = consumeBudget(ordinaryBudget, accepted);
                }
            }
        } finally {
            mountsInUse = false;
        }
        flushQueuedOperations();
        return amount.subtract(remaining);
    }

    private static BigInteger simulateMount(MEStorage inventory, AEKey what, BigInteger remaining,
            long ordinaryBudget, IActionSource source) {
        if (inventory instanceof ECOBigIntegerStorage exact) {
            return checked(remaining, exact.insertBigInteger(what, remaining, Actionable.SIMULATE, source));
        }
        if (ordinaryBudget == 0L) return BigInteger.ZERO;
        BigInteger offered = remaining.min(BigInteger.valueOf(ordinaryBudget));
        return checked(offered, BigInteger.valueOf(
                inventory.insert(what, offered.longValueExact(), Actionable.SIMULATE, source)));
    }

    private static long consumeBudget(long budget, BigInteger accepted) {
        return accepted.compareTo(BigInteger.valueOf(budget)) >= 0
                ? 0L : budget - accepted.longValueExact();
    }

    private static BigInteger checked(BigInteger offered, BigInteger inserted) {
        if (inserted == null || inserted.signum() < 0 || inserted.compareTo(offered) > 0) {
            throw new IllegalStateException("Invalid storage insertion result: " + inserted + " for " + offered);
        }
        return inserted;
    }

    @WrapOperation(
            method = "getAvailableStacks",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lappeng/api/storage/MEStorage;getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V"),
            require = 1)
    private void neoecoae$listContribution(MEStorage storage, KeyCounter output, Operation<Void> original) {
        KeyCounter contribution = new KeyCounter();
        ExactAmountCollector.contribution(storage, contribution, () -> original.call(storage, contribution));
        SaturatingStackAccumulator.addAll(output, contribution);
    }
}
