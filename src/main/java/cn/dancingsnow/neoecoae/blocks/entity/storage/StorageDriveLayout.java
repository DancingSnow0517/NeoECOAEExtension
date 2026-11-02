package cn.dancingsnow.neoecoae.blocks.entity.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Controller-relative rows, with columns starting at the first drive beside the controller. */
final class StorageDriveLayout {
    private StorageDriveLayout() {}

    static List<ECODriveBlockEntity> ordered(List<ECODriveBlockEntity> drives, BlockPos controller,
        Direction expansion, Direction top) {
        var positions = new HashMap<BlockPos, ECODriveBlockEntity>();
        int columns = 0;
        for (ECODriveBlockEntity drive : drives) {
            BlockPos pos = drive.getBlockPos();
            int column = projection(pos.subtract(controller), expansion) - 2;
            int row = 1 - projection(pos.subtract(controller), top);
            if (column < 0 || row < 0 || row >= 3) continue;
            positions.put(pos, drive);
            columns = Math.max(columns, column + 1);
        }
        List<ECODriveBlockEntity> result = new ArrayList<>(columns * 3);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < columns; column++) {
                result.add(positions.get(controller.relative(expansion, column + 2).relative(top, 1 - row)));
            }
        }
        return result;
    }

    private static int projection(BlockPos delta, Direction direction) {
        return delta.getX() * direction.getStepX() + delta.getY() * direction.getStepY()
            + delta.getZ() * direction.getStepZ();
    }
}
