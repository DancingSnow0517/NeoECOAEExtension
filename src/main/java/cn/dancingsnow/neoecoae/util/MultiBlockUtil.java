package cn.dancingsnow.neoecoae.util;

import lombok.NoArgsConstructor;
import net.minecraft.core.BlockPos;

import java.util.Set;
import java.util.HashSet;

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
        Set<BlockPos> result = new HashSet<>();
        result.add(new BlockPos(min.getX() + 1, min.getY() + 1, min.getZ()));
        result.add(new BlockPos(min.getX() + 1, min.getY() + 1, min.getZ() + 1));
        result.add(new BlockPos(max.getX() - 1, min.getY() + 1, min.getZ()));
        result.add(new BlockPos(max.getX() - 1, min.getY() + 1, min.getZ() + 1));
        return result;
    }

    private static Set<BlockPos> allYPossibleController(BlockPos min, BlockPos max) {
        Set<BlockPos> result = new HashSet<>();
        result.add(new BlockPos(min.getX(), min.getY() + 1, min.getZ() + 1));
        result.add(new BlockPos(min.getX() + 1, min.getY() + 1, min.getZ() + 1));
        result.add(new BlockPos(min.getX(), min.getY() + 1, max.getZ() - 1));
        result.add(new BlockPos(min.getX() + 1, min.getY() + 1, max.getZ() - 1));
        return result;
    }
}
