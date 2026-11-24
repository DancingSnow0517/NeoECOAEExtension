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

    static List<ECOComputationDriveBlockEntity> entries(List<ECOComputationDriveBlockEntity> drives, BlockPos controller,
        boolean mirrored) {
        int slots = ComputationControllerPanelState.SLOT_COUNT;
        List<ECOComputationDriveBlockEntity> entries = new ArrayList<>(Collections.nCopies(slots, null));
        if (drives == null) return entries;
        var nearest = nearest(drives, controller);
        for (int slot = 0; slot < nearest.size(); slot++) {
            entries.set(ComputationControllerPanelState.cellOf(slot, mirrored), nearest.get(slot));
        }
        return entries;
    }

    static List<ECOComputationDriveBlockEntity> nearest(List<ECOComputationDriveBlockEntity> drives, BlockPos controller) {
        return drives.stream()
            .sorted(Comparator.<ECOComputationDriveBlockEntity>comparingInt(
                ECOMultiblockParts::order)
                .thenComparingDouble(drive -> drive.getBlockPos().distSqr(controller)))
            .limit(ComputationControllerPanelState.SLOT_COUNT)
            .toList();
    }
}
