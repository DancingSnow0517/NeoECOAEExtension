package cn.dancingsnow.neoecoae.mixins.ae2.storage;

import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.AEKey;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import cn.dancingsnow.neoecoae.storage.transfer.StorageBusTransferFilter;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import cn.dancingsnow.neoecoae.crafting.display.terminal.StorageListingAccumulator;
import cn.dancingsnow.neoecoae.storage.NetworkInventoryCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.function.Consumer;

/** Prevents storage totals from disappearing when an unbounded stack is combined with another inventory. */
@Mixin(NetworkStorage.class)
public abstract class NetworkStorageMixin implements ECOBigIntegerStorage, NetworkInventoryCache.Mounts {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    @Unique private final StorageListingAccumulator neoecoae$listing = new StorageListingAccumulator();
    @Unique private final List<MEStorage> neoecoae$mounted = new ArrayList<>();
    @Unique private long neoecoae$mountGeneration;

    @Shadow private boolean mountsInUse;
    @Shadow @Final private NavigableMap<Integer, List<MEStorage>> priorityInventory;
    @Shadow @Final private List<MEStorage> secondPassInventories;

    @Shadow private boolean isQueuedForRemoval(MEStorage inventory) { throw new AssertionError(); }
    @Shadow private void flushQueuedOperations() { throw new AssertionError(); }

    @Inject(method = {"mount", "unmount"}, at = @At("HEAD"))
    private void neoecoae$countMountChange(CallbackInfo ci) {
        neoecoae$mountGeneration++;
    }

    @Override
    public long neoecoae$mountGeneration() {
        return neoecoae$mountGeneration;
    }

    @Override
    public boolean neoecoae$withMounts(Consumer<List<MEStorage>> action) {
        // Same lock as getAvailableStacks: blocks recursive listing and defers mount changes.
        if (mountsInUse) return false;
        mountsInUse = true;
        try {
            for (var inventories : priorityInventory.values()) neoecoae$mounted.addAll(inventories);
            action.accept(neoecoae$mounted);
        } finally {
            neoecoae$mounted.clear();
            mountsInUse = false;
        }
        return true;
    }

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
        method = "insert",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/MEStorage;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J")
    )
    private long neoecoae$excludeStorageBusOutput(MEStorage storage, AEKey key, long amount,
            Actionable mode, IActionSource source, Operation<Long> original) {
        if (StorageBusTransferFilter.excludes(storage)) return 0L;
        return original.call(storage, key, amount, mode, source);
    }

    @WrapOperation(
        method = "extract",
        at = @At(value = "INVOKE", target = "Lappeng/api/storage/MEStorage;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J")
    )
    private long neoecoae$filterCreativeInput(MEStorage storage, appeng.api.stacks.AEKey key, long amount,
            appeng.api.config.Actionable mode, appeng.api.networking.security.IActionSource source,
            Operation<Long> original) {
        if (StorageBusTransferFilter.excludes(storage)
                || cn.dancingsnow.neoecoae.storage.transfer.StorageExtractionExclusions.contains(storage)) {
            return 0L;
        }
        return cn.dancingsnow.neoecoae.storage.transfer.ECOCreativeExtractionFilter.extractSource(
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
        if (StorageBusTransferFilter.excludes(storage)) return;
        // Reuse an isolated counter for sources without a saturating listing. This keeps
        // Long.MAX_VALUE + ordinary stock safe in either mount order, while trusted ECO
        // sources can append directly without copying their entire inventory.
        neoecoae$listing.append(storage, output, counter -> original.call(storage, counter));
    }
}
