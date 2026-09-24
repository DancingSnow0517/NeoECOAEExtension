package cn.dancingsnow.neoecoae.crafting.planner;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.StorageCell;
import appeng.items.storage.CreativeCellItem;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.crafting.planner.solve.PlannerInventorySnapshot;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource;
import cn.dancingsnow.neoecoae.impl.storage.ECOCreativeCell;

import java.util.HashSet;
import java.util.Set;

/** Captured on the server thread, preserving unlimited-source identity alongside AE2's long counter. */
public final class ECOPlannerInventory {
    private ECOPlannerInventory() {}

    public static PlannerInventorySnapshot capture(IGrid grid) {
        var inventory = grid.getStorageService().getInventory().getAvailableStacks();
        Set<AEKey> unbounded = new HashSet<>();
        for (var drive : grid.getMachines(ECODriveBlockEntity.class)) {
            if (!drive.isMounted() || !drive.isOnline()) continue;
            var cell = drive.getCellInventory();
            if (cell instanceof ECOCreativeCell creative) unbounded.addAll(creative.configuredKeys());
            if (cell instanceof ExactAmountSource source) markUnbounded(source, unbounded);
        }
        for (IChestOrDrive host : grid.getMachines(IChestOrDrive.class)) {
            if (!host.isPowered()) continue;
            for (int slot = 0; slot < host.getCellCount(); slot++) {
                StorageCell cell = host.getOriginalCellInventory(slot);
                if (cell instanceof ExactAmountSource source) markUnbounded(source, unbounded);

                if (cell != null && host.getCellItem(slot) instanceof CreativeCellItem) {
                    // Native ME drives expose AE2 creative cells as StorageCell and report only
                    // Integer.MAX_VALUE per key. Keep their actual unlimited semantics in the
                    // planner snapshot instead of mistaking that display count for finite stock.
                    unbounded.addAll(cell.getAvailableStacks().keySet());
                }
            }
        }
        return PlannerInventorySnapshot.of(inventory, unbounded);
    }

    private static void markUnbounded(ExactAmountSource source, Set<AEKey> target) {
        source.neoecoae$visitExactAmounts((key, amount) -> {
            if (amount.infinite()) target.add(key);
        });
    }
}
