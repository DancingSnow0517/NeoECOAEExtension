package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockParts;

import cn.dancingsnow.neoecoae.gui.computation.ComputationControllerPanelState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;

/** Screen cells (left to right) of one drive row, nearest the controller first unless mirrored. */
final class ComputationDriveLayout {
    private ComputationDriveLayout() {}

    static java.util.List<cn.dancingsnow.neoecoae.gui.computation.ComputationDriveEntry> entries(
            List<ECOComputationDriveBlockEntity> drives, BlockPos controller, boolean mirrored) {
        return menuDrives(drives, controller, mirrored).stream().map(drive -> drive == null
            ? cn.dancingsnow.neoecoae.gui.computation.ComputationDriveEntry.EMPTY
            : new cn.dancingsnow.neoecoae.gui.computation.ComputationDriveEntry(drive.getBlockPos(), drive.getCellStack())).toList();
    }

    static List<ECOComputationDriveBlockEntity> menuDrives(List<ECOComputationDriveBlockEntity> drives, BlockPos controller,
        boolean mirrored) {
        int slots = ComputationControllerPanelState.SLOT_COUNT;
        List<ECOComputationDriveBlockEntity> entries = new ArrayList<>(Collections.nCopies(slots, null));
        if (drives == null) return entries;
        List<ECOComputationDriveBlockEntity> nearest = drives.stream()
            .sorted(Comparator.<ECOComputationDriveBlockEntity>comparingInt(
                ECOMultiblockParts::order)
                .thenComparingDouble(drive -> drive.getBlockPos().distSqr(controller)))
            .limit(slots)
            .toList();
        for (int slot = 0; slot < nearest.size(); slot++) {
            entries.set(ComputationControllerPanelState.cellOf(slot, mirrored), nearest.get(slot));
        }
        return entries;
    }
}
