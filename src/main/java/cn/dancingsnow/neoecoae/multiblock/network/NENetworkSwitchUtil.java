package cn.dancingsnow.neoecoae.multiblock.network;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.NENetworkSwitchBlock;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Geometry and formed-state synchronization shared by both network switch families. */
public final class NENetworkSwitchUtil {
    private static final IOrientationStrategy HORIZONTAL = OrientationStrategies.horizontalFacing();

    private NENetworkSwitchUtil() {}

    public static BlockPos switchPosition(BlockPos controllerPos, BlockState controllerState, boolean mirrored) {
        Direction side = HORIZONTAL.getSide(controllerState, mirrored ? RelativeSide.RIGHT : RelativeSide.LEFT);
        return controllerPos.relative(side);
    }

    public static boolean isSwitchPosition(BlockPos switchPos, BlockPos controllerPos, BlockState controllerState) {
        return switchPos.equals(switchPosition(controllerPos, controllerState, false))
                || switchPos.equals(switchPosition(controllerPos, controllerState, true));
    }

    public static boolean canUseNetworkSwitch(IECOTier tier) {
        return tier.getTier() == ECOTier.L9.getTier();
    }

    public static void syncFormed(
            ServerLevel level, BlockPos controllerPos, BlockState controllerState, boolean mirrored) {
        setFormed(level, switchPosition(controllerPos, controllerState, false), !mirrored);
        setFormed(level, switchPosition(controllerPos, controllerState, true), mirrored);
    }

    public static void clearFormed(ServerLevel level, BlockPos controllerPos, BlockState controllerState) {
        setFormed(level, switchPosition(controllerPos, controllerState, false), false);
        setFormed(level, switchPosition(controllerPos, controllerState, true), false);
    }

    private static void setFormed(ServerLevel level, BlockPos switchPos, boolean formed) {
        BlockState state = level.getBlockState(switchPos);
        if (!state.hasProperty(NENetworkSwitchBlock.FORMED)) {
            return;
        }
        if (state.getValue(NENetworkSwitchBlock.FORMED) == formed) {
            return;
        }

        // This is derived render state. Placement/removal already invalidates adjacent multiblocks.
        level.setBlock(switchPos, state.setValue(NENetworkSwitchBlock.FORMED, formed), Block.UPDATE_CLIENTS);
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(switchPos.relative(direction)) instanceof NEBlockEntity<?, ?> blockEntity) {
                blockEntity.refreshGridConnections();
            }
        }
    }
}
