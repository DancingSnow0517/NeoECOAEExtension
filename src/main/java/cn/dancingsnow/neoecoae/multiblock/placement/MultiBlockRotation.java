package cn.dancingsnow.neoecoae.multiblock.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;

public final class MultiBlockRotation {
    public static final BlockPos CONTROLLER_ANCHOR = new BlockPos(1, 1, 0);

    private MultiBlockRotation() {}

    public static BlockPos localToWorld(BlockPos localPos, BlockPos controllerPos, Direction facing) {
        return localToWorld(localPos, controllerPos, facing, false);
    }

    public static BlockPos localToWorld(BlockPos localPos, BlockPos controllerPos, Direction facing, boolean mirrored) {
        localPos = transformLocalPos(localPos, mirrored);
        BlockPos offset = localPos.subtract(CONTROLLER_ANCHOR);
        BlockPos rotated = rotateOffset(offset, facing);
        return controllerPos.offset(rotated);
    }

    public static Direction localDirectionToWorld(Direction localDirection, Direction facing, boolean mirrored) {
        return transformDirection(localDirection, facing, mirrored);
    }

    public static BlockPos transformLocalPos(BlockPos localPos, boolean mirrored) {
        return mirrored ? mirrorLocalPos(localPos) : localPos.immutable();
    }

    public static BlockState rotateState(BlockState state, Direction facing) {
        return rotateState(state, facing, false);
    }

    public static BlockState rotateState(BlockState state, Direction facing, boolean mirrored) {
        Rotation rotation = rotationForFacing(facing);
        BlockState rotated = mirrored ? state.mirror(Mirror.FRONT_BACK) : state;
        rotated = rotated.rotate(rotation);

        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty directionProperty) {
                Direction direction = transformDirection(state.getValue(directionProperty), facing, mirrored);
                rotated = setIfAllowed(rotated, directionProperty, direction);
            } else if (property.getValueClass() == Direction.Axis.class) {
                Direction.Axis axis = getAxis(state, property);
                rotated = setAxisIfAllowed(rotated, property, transformAxis(axis, facing, mirrored));
            }
        }
        return rotated;
    }

    private static BlockPos mirrorLocalPos(BlockPos localPos) {
        return new BlockPos(CONTROLLER_ANCHOR.getX() * 2 - localPos.getX(), localPos.getY(), localPos.getZ());
    }

    private static BlockPos rotateOffset(BlockPos offset, Direction facing) {
        return offset.rotate(rotationForFacing(facing));
    }

    private static Direction transformDirection(Direction direction, Direction facing, boolean mirrored) {
        Direction transformed = mirrored ? Mirror.FRONT_BACK.mirror(direction) : direction;
        return rotationForFacing(facing).rotate(transformed);
    }

    private static Direction.Axis transformAxis(Direction.Axis axis, Direction facing, boolean mirrored) {
        Direction direction = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        return transformDirection(direction, facing, mirrored).getAxis();
    }

    private static Rotation rotationForFacing(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static <T extends Comparable<T>> BlockState setIfAllowed(BlockState state, Property<T> property, T value) {
        return property.getPossibleValues().contains(value) ? state.setValue(property, value) : state;
    }

    @SuppressWarnings("unchecked")
    private static Direction.Axis getAxis(BlockState state, Property<?> property) {
        return state.getValue((Property<Direction.Axis>) property);
    }

    @SuppressWarnings("unchecked")
    private static BlockState setAxisIfAllowed(BlockState state, Property<?> property, Direction.Axis value) {
        return setIfAllowed(state, (Property<Direction.Axis>) property, value);
    }
}
