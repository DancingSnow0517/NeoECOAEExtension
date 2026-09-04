package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.stacks.AEKey;
import java.util.List;

public record ECOTransferPlan(AEKey key, long amount, List<ECOStorageAllocation> allocations) {
    public ECOTransferPlan {
        allocations = List.copyOf(allocations);
    }

    public static ECOTransferPlan empty(AEKey key) {
        return new ECOTransferPlan(key, 0L, List.of());
    }
}
