package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import cn.dancingsnow.neoecoae.mixins.ae2.NetworkStorageAccessor;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Reads only inventories actually mounted in the terminal's storage network. */
public final class ECOExactInventory {
    private ECOExactInventory() {}

    public static Map<AEKey, BigInteger> hugeAmounts(MEStorage storage) {
        Map<AEKey, BigInteger> totals = new HashMap<>();
        Set<AEKey> ecoKeys = new java.util.HashSet<>();
        var leaves = new java.util.ArrayList<MEStorage>();
        flatten(storage, leaves, Collections.newSetFromMap(new IdentityHashMap<>()));
        if (leaves.stream().noneMatch(ECOInfiniteStorage.class::isInstance)) return Map.of();
        Set<Object> domains = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var leaf : leaves) {
            if (leaf instanceof ECOInfiniteStorage eco && !domains.add(eco.exactInventoryIdentity())) continue;
            collect(leaf, totals, ecoKeys);
        }
        totals.entrySet()
                .removeIf(entry -> !ecoKeys.contains(entry.getKey())
                        || entry.getValue().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0);
        return Map.copyOf(totals);
    }

    private static void flatten(MEStorage storage, java.util.List<MEStorage> leaves, Set<MEStorage> visited) {
        if (!visited.add(storage)) return;
        if (storage instanceof NetworkStorageAccessor network) {
            for (var inventories : network.neoecoae$getMountedInventories().values()) {
                for (var inventory : inventories) flatten(inventory, leaves, visited);
            }
            return;
        }
        leaves.add(storage);
    }

    private static void collect(MEStorage storage, Map<AEKey, BigInteger> totals, Set<AEKey> ecoKeys) {
        var available = storage.getAvailableStacks();
        for (var entry : available) {
            if (entry.getLongValue() <= 0) continue;
            BigInteger amount = BigInteger.valueOf(entry.getLongValue());
            if (storage instanceof ECOInfiniteStorage eco) {
                amount = eco.getExactAmount(entry.getKey()).toBigInteger();
                ecoKeys.add(entry.getKey());
            }
            totals.merge(entry.getKey(), amount, BigInteger::add);
        }
    }
}
