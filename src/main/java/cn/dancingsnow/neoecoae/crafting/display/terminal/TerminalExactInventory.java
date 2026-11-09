package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.storage.NetworkInventoryCache;
import cn.dancingsnow.neoecoae.storage.StorageListingDelta;
import cn.dancingsnow.neoecoae.storage.StorageListingVersion;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Exact overrides for a network's cached ordinary contributions, owned by that network. */
final class TerminalExactInventory {
    private final NetworkInventoryCache network;
    private final Map<MEStorage, Mount> mounts = new IdentityHashMap<>();
    private final ReferenceOpenHashSet<MEStorage> captured = new ReferenceOpenHashSet<>();
    private final ReferenceOpenHashSet<MEStorage> seen = new ReferenceOpenHashSet<>();
    private final ObjectOpenHashSet<AEKey> changed = new ObjectOpenHashSet<>();
    private final ObjectOpenHashSet<AEKey> exceptional = new ObjectOpenHashSet<>();
    private final StorageListingAccumulator listing = new StorageListingAccumulator();
    private final KeyCounter scratch = new KeyCounter();
    private boolean collecting;
    private long generation = Long.MIN_VALUE;

    TerminalExactInventory(NetworkInventoryCache network) { this.network = network; }

    void begin(boolean collecting) {
        this.collecting = collecting;
        captured.clear();
    }

    void reset() {
        mounts.clear();
        captured.clear();
        seen.clear();
        changed.clear();
        exceptional.clear();
        generation = Long.MIN_VALUE;
    }

    /** Captures legacy/nested exact values in the full traversal already required by the ordinary cache. */
    void list(MEStorage storage, KeyCounter out) {
        if (!collecting || sparse(storage) != null) {
            listing.append(storage, out, storage::getAvailableStacks);
            return;
        }
        ExactAmountCollector.begin();
        try {
            listing.append(storage, out, storage::getAvailableStacks);
            Map<AEKey, ExactAmount> values = ExactAmountCollector.finish();
            Mount mount = mounts.computeIfAbsent(storage, ignored -> new Mount());
            mount.values.clear();
            values.forEach((key, value) -> {
                long visible = out.get(key);
                if (visible > 0 && differs(value, visible)) mount.values.put(key, value);
            });
            captured.add(storage);
        } catch (RuntimeException | Error failure) {
            ExactAmountCollector.abort();
            throw failure;
        }
    }

    Map<AEKey, ExactAmount> sample(NetworkInventoryCache.Mounts storage, Object2LongMap<AEKey> inventory,
            Map<AEKey, ExactAmount> previous) {
        Map<AEKey, ExactAmount> result = new HashMap<>();
        boolean locked = storage.neoecoae$withMounts(inventories -> {
            long currentGeneration = storage.neoecoae$mountGeneration();
            boolean remounted = currentGeneration != generation;
            seen.clear();
            for (MEStorage source : inventories) {
                seen.add(source);
                Mount mount = mounts.computeIfAbsent(source, ignored -> new Mount());
                long version = network.mountedVersion(source);
                Object identity = network.mountedIdentity(source);
                boolean viewChanged = mount.identity != identity;
                if (!captured.contains(source)
                        && (remounted || viewChanged || version == StorageListingVersion.UNKNOWN || version != mount.version)) {
                    refresh(source, mount, version, remounted || viewChanged);
                }
                mount.version = version;
                mount.identity = identity;
            }
            mounts.keySet().removeIf(source -> !seen.contains(source));
            generation = currentGeneration;

            exceptional.clear();
            network.visitOversizedTotals((key, amount) -> exceptional.add(key));
            for (Mount mount : mounts.values()) exceptional.addAll(mount.values.keySet());
            for (AEKey key : exceptional) {
                long visible = inventory.getLong(key);
                PlannerAmount total = PlannerAmount.of(network.listedTotal(key, visible));
                boolean infinite = false;
                for (var entry : mounts.entrySet()) {
                    ExactAmount exact = entry.getValue().values.get(key);
                    if (exact == null) continue;
                    if (exact.infinite()) {
                        infinite = true;
                        break;
                    }
                    total = total.subtract(network.mountedAmount(entry.getKey(), key)).add(exact.quantity());
                }
                ExactAmount exact = infinite ? ExactAmount.unbounded() : ExactAmount.finite(total.toBigInteger());
                if (differs(exact, visible)) result.put(key, exact);
            }
        });
        if (!locked) return null;
        return result.equals(previous) ? previous : Map.copyOf(result);
    }

    private void refresh(MEStorage storage, Mount mount, long version, boolean remounted) {
        SparseExactAmountSource source = sparse(storage);
        if (source == null) {
            scratch.clear();
            scratch.removeEmptySubmaps();
            list(storage, scratch);
            return;
        }
        if (!source.neoecoae$mayHaveExceptionalAmounts()) {
            mount.values.clear();
            return;
        }
        changed.clear();
        if (!remounted && version >= 0 && mount.version >= 0 && storage instanceof StorageListingDelta delta
                && delta.neoecoae$visitListingChanges(mount.version, version, (key, ignored) -> changed.add(key))) {
            for (AEKey key : changed) {
                ExactAmount exact = source.neoecoae$exceptionalAmount(key);
                if (exact == null || network.mountedAmount(storage, key) == 0) mount.values.remove(key);
                else mount.values.put(key, exact);
            }
        } else {
            mount.values.clear();
            source.neoecoae$visitExceptionalAmounts((key, exact) -> {
                if (network.mountedAmount(storage, key) > 0) mount.values.put(key, exact);
            });
        }
    }

    private static SparseExactAmountSource sparse(MEStorage storage) {
        return storage instanceof SparseExactAmountSource source && source.neoecoae$hasSparseExactAmounts()
                ? source : null;
    }

    private static boolean differs(ExactAmount exact, long visible) {
        return exact.infinite() || exact.value().bitLength() >= Long.SIZE || exact.value().longValue() != visible;
    }

    private static final class Mount {
        final Map<AEKey, ExactAmount> values = new HashMap<>();
        long version = StorageListingVersion.UNKNOWN;
        Object identity;
    }
}
