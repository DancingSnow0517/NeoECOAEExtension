package cn.dancingsnow.neoecoae.impl.crafting.planner;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerInventorySnapshot;
import cn.dancingsnow.neoecoae.impl.storage.ECOCreativeCell;

import java.util.HashSet;
import java.util.Set;

/** Captured on the server thread. Only directly mounted ECO creative cells confer unlimited supply. */
public final class ECOPlannerInventory {
    private ECOPlannerInventory() {}

    public static PlannerInventorySnapshot capture(IGrid grid) {
        var inventory = grid.getStorageService().getInventory().getAvailableStacks();
        Set<AEKey> unbounded = new HashSet<>();
        for (var drive : grid.getMachines(ECODriveBlockEntity.class)) {
            if (drive.isMounted() && drive.isOnline() && drive.getCellInventory() instanceof ECOCreativeCell creative) {
                unbounded.addAll(creative.configuredKeys());
            }
        }
        return PlannerInventorySnapshot.of(inventory, unbounded);
    }
}
