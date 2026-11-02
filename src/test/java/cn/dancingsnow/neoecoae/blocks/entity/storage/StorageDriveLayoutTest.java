package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageDriveLayoutTest {
    @Test
    void screenshotLayoutStartsAtTheControllerAndKeepsTopMiddleBottomForEveryFacingAndMirror() {
        BlockPos controller = new BlockPos(50, 80, -30);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState state = mock(BlockState.class);
            when(state.getValue(BlockStateProperties.HORIZONTAL_FACING)).thenReturn(facing);
            for (boolean mirror : List.of(false, true)) {
                var orientation = OrientationStrategies.horizontalFacing();
                Direction expansion = orientation.getSide(state, mirror ? RelativeSide.RIGHT : RelativeSide.LEFT);
                Direction top = orientation.getSide(state, RelativeSide.TOP);
                List<ECODriveBlockEntity> expected = new ArrayList<>();
                for (int row = 0; row < 3; row++) {
                    for (int column = 0; column < 11; column++) {
                        ECODriveBlockEntity drive = mock(ECODriveBlockEntity.class);
                        when(drive.getBlockPos()).thenReturn(controller.relative(expansion, column + 2).relative(top, 1 - row));
                        expected.add(drive);
                    }
                }
                List<ECODriveBlockEntity> scanned = new ArrayList<>(expected);
                Collections.shuffle(scanned, new java.util.Random(42));
                assertEquals(expected, StorageDriveLayout.ordered(scanned, controller, expansion, top),
                    facing + " mirror=" + mirror);
                // Installed cells are a 3x3 square plus the top cell of column four.
                List<Integer> installed = List.of(0, 1, 2, 3, 11, 12, 13, 22, 23, 24);
                for (int i : installed) assertSame(expected.get(i), StorageDriveLayout.ordered(scanned, controller, expansion, top).get(i));
            }
        }
    }

    @Test
    void missingDriveDoesNotMoveTheOtherPhysicalPositions() {
        BlockPos controller = BlockPos.ZERO;
        List<ECODriveBlockEntity> drives = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 4; column++) {
                if (row == 1 && column == 1) continue;
                ECODriveBlockEntity drive = mock(ECODriveBlockEntity.class);
                when(drive.getBlockPos()).thenReturn(controller.relative(Direction.EAST, column + 2).above(1 - row));
                drives.add(drive);
            }
        }
        List<ECODriveBlockEntity> ordered = StorageDriveLayout.ordered(drives, controller, Direction.EAST, Direction.UP);
        assertEquals(12, ordered.size());
        assertNull(ordered.get(5));
        assertEquals(new BlockPos(4, 0, 0), ordered.get(6).getBlockPos());
        assertEquals(new BlockPos(2, -1, 0), ordered.get(8).getBlockPos());
    }
}
