package cn.dancingsnow.neoecoae.crafting.planner;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.StorageCell;
import appeng.items.storage.CreativeCellItem;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.compat.storage.ECOOptionalUnboundedCells;
import cn.dancingsnow.neoecoae.crafting.planner.solve.PlannerInventorySnapshot;
import cn.dancingsnow.neoecoae.crafting.display.terminal.ExactAmountSource;
import cn.dancingsnow.neoecoae.crafting.display.terminal.SparseExactAmountSource;
import cn.dancingsnow.neoecoae.grid.NEMachineLookup;
import cn.dancingsnow.neoecoae.storage.ECOCreativeCell;

import java.util.HashSet;
import java.util.Set;

/** Captured on the server thread, preserving unlimited-source identity alongside AE2's long counter. */
public final class ECOPlannerInventory {
    private ECOPlannerInventory() {}

    public static PlannerInventorySnapshot capture(IGrid grid) {
        var storage = cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalInventorySnapshot.resolve(
            grid.getStorageService().getInventory());
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        var snapshot = cn.dancingsnow.neoecoae.crafting.display.terminal.TerminalInventorySnapshot.sample(
            storage, server == null ? 0L : server.getTickCount(), true, true, storage::getAvailableStacks);
        var amounts = new java.util.LinkedHashMap<AEKey, cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount>();
        snapshot.visitVisibleAmounts((key, amount) -> amounts.put(key,
            cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount.of(amount)));
        Set<AEKey> unbounded = collectUnboundedKeys(grid);
        snapshot.amounts().forEach((key, amount) -> {
            if (amount.infinite()) unbounded.add(key);
            else amounts.put(key, amount.quantity());
        });
        return PlannerInventorySnapshot.exact(amounts, unbounded);
    }

    /**
     * Returns keys supplied by an unbounded storage source in this grid.  The ordinary AE2
     * aggregate only exposes a saturated long amount, so callers that move physical stock must
     * preserve this side-channel and never persist those generated resources.
     */
    public static Set<AEKey> collectUnboundedKeys(IGrid grid) {
        Set<AEKey> unbounded = new HashSet<>();
        for (var drive : grid.getMachines(ECODriveBlockEntity.class)) {
            if (!drive.isMounted() || !drive.isOnline()) continue;
            var cell = drive.getCellInventory();
            if (cell instanceof ECOCreativeCell creative) unbounded.addAll(creative.configuredKeys());
            if (cell instanceof ExactAmountSource source) markUnbounded(source, unbounded);
        }
        for (IChestOrDrive host : NEMachineLookup.getMachines(grid, IChestOrDrive.class)) {
            if (!host.isPowered()) continue;
            for (int slot = 0; slot < host.getCellCount(); slot++) {
                StorageCell cell = host.getOriginalCellInventory(slot);
                if (cell instanceof ExactAmountSource source) markUnbounded(source, unbounded);
                ECOOptionalUnboundedCells.collect(cell, unbounded);

                if (cell != null && host.getCellItem(slot) instanceof CreativeCellItem) {
                    // Native ME drives expose AE2 creative cells as StorageCell and report only
                    // Integer.MAX_VALUE per key. Keep their actual unlimited semantics in the
                    // planner snapshot instead of mistaking that display count for finite stock.
                    unbounded.addAll(cell.getAvailableStacks().keySet());
                }
            }
        }
        return unbounded;
    }

    private static void markUnbounded(ExactAmountSource source, Set<AEKey> target) {
        if (source instanceof SparseExactAmountSource sparse && sparse.neoecoae$hasSparseExactAmounts()) {
            if (sparse.neoecoae$mayHaveExceptionalAmounts()) {
                sparse.neoecoae$visitExceptionalAmounts((key, amount) -> {
                    if (amount.infinite()) target.add(key);
                });
            }
            return;
        }
        source.neoecoae$visitExactAmounts((key, amount) -> {
            if (amount.infinite()) target.add(key);
        });
    }
}
