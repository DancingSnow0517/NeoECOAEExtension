package cn.dancingsnow.neoecoae.util;

import lombok.NoArgsConstructor;
import net.minecraft.core.BlockPos;

import java.util.Set;

@NoArgsConstructor
public class MultiBlockUtil {
    public static Set<BlockPos> allPossibleController(BlockPos min, BlockPos max) {
        int xSize = max.getX() - min.getX() + 1;
        int zSize = max.getZ() - min.getZ() + 1;

        if (xSize > zSize && zSize == 2) {
            return allXPossibleController(min, max);
        }
        if (zSize > xSize && xSize == 2) {
            return allYPossibleController(min, max);
        }
        return Set.of();
    }

    private static Set<BlockPos> allXPossibleController(BlockPos min, BlockPos max) {
        return Set.of(
            new BlockPos(min.getX() + 1, min.getY() + 1, min.getZ()),
            new BlockPos(min.getX() + 1, min.getY() + 1, min.getZ() + 1),
            new BlockPos(max.getX() - 1, min.getY() + 1, min.getZ()),
            new BlockPos(max.getX() - 1, min.getY() + 1, min.getZ() + 1)
        );
    }

    private static Set<BlockPos> allYPossibleController(BlockPos min, BlockPos max) {
        return Set.of(
            new BlockPos(min.getX(), min.getY() + 1, min.getZ() + 1),
            new BlockPos(min.getX() + 1, min.getY() + 1, min.getZ() + 1),
            new BlockPos(min.getX(), min.getY() + 1, max.getZ() - 1),
            new BlockPos(min.getX() + 1, min.getY() + 1, max.getZ() - 1)
        );
    }
}
