package cn.dancingsnow.neoecoae.multiblock.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;

public final class MultiBlockRotation {
    public static final BlockPos CONTROLLER_ANCHOR = new BlockPos(1, 1, 0);

    private MultiBlockRotation() {
    }

    public static BlockPos localToWorld(BlockPos localPos, BlockPos controllerPos, Direction facing) {
        BlockPos offset = localPos.subtract(CONTROLLER_ANCHOR);
        BlockPos rotated = rotateOffset(offset, facing);
        return controllerPos.offset(rotated);
    }

    public static BlockState rotateState(BlockState state, Direction facing) {
        BlockState rotated = state;
        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty directionProperty) {
                Direction direction = state.getValue(directionProperty);
                if (direction.getAxis().isHorizontal()) {
                    rotated = rotated.setValue(directionProperty, rotateHorizontal(direction, facing));
                }
            }
        }
        return rotated;
    }

    private static BlockPos rotateOffset(BlockPos offset, Direction facing) {
        return switch (facing) {
            case NORTH -> offset;
            case EAST -> new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
            case SOUTH -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
            case WEST -> new BlockPos(offset.getZ(), offset.getY(), -offset.getX());
            default -> offset;
        };
    }

    private static Direction rotateHorizontal(Direction direction, Direction facing) {
        return switch (facing) {
            case NORTH -> direction;
            case EAST -> direction.getClockWise();
            case SOUTH -> direction.getOpposite();
            case WEST -> direction.getCounterClockWise();
            default -> direction;
        };
    }
}