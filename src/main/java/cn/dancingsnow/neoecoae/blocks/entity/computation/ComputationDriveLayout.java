package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.gui.computation.ComputationControllerPanelState;
import cn.dancingsnow.neoecoae.gui.computation.ComputationDriveEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** Screen cells (left to right) of one drive row, nearest the controller first unless mirrored. */
final class ComputationDriveLayout {
    private ComputationDriveLayout() {}

    static List<ComputationDriveEntry> entries(List<ECOComputationDriveBlockEntity> drives, BlockPos controller,
        boolean mirrored) {
        int slots = ComputationControllerPanelState.SLOT_COUNT;
        List<ComputationDriveEntry> entries = new ArrayList<>(Collections.nCopies(slots, ComputationDriveEntry.EMPTY));
        if (drives == null) return entries;
        List<ECOComputationDriveBlockEntity> nearest = drives.stream()
            .sorted(Comparator.comparingDouble(drive -> drive.getBlockPos().distSqr(controller)))
            .limit(slots)
            .toList();
        for (int slot = 0; slot < nearest.size(); slot++) {
            ECOComputationDriveBlockEntity drive = nearest.get(slot);
            ItemStack stack = drive.getCellStack();
            entries.set(ComputationControllerPanelState.cellOf(slot, mirrored),
                new ComputationDriveEntry(drive.getBlockPos(), stack == null ? ItemStack.EMPTY : stack));
        }
        return entries;
    }
}
