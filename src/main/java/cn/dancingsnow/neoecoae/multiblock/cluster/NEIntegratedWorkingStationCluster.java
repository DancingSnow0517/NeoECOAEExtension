package cn.dancingsnow.neoecoae.multiblock.cluster;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationOutputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Cluster state for the fixed 3x2x2 large integrated working station. */
public class NEIntegratedWorkingStationCluster extends NECluster<NEIntegratedWorkingStationCluster> {
    private ECOLargeIntegratedWorkingStationBlockEntity controller;
    private ECOLargeIntegratedWorkingStationInputHatchBlockEntity inputHatch;
    private ECOLargeIntegratedWorkingStationInputHatchBlockEntity smartCoolingController;
    private ECOLargeIntegratedWorkingStationOutputHatchBlockEntity outputHatch;
    private ECOMachineInterfaceBlockEntity<?> communication;

    public NEIntegratedWorkingStationCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }

    public ECOLargeIntegratedWorkingStationBlockEntity getController() {
        return controller;
    }

    public ECOLargeIntegratedWorkingStationInputHatchBlockEntity getInputHatch() {
        return inputHatch;
    }

    public ECOLargeIntegratedWorkingStationInputHatchBlockEntity getSmartCoolingController() {
        return smartCoolingController;
    }

    /** The four upper corner casings; the controller and the position directly behind it are excluded. */
    public boolean isTopPowerPort(BlockPos pos) {
        if (isDestroyed() || controller == null || pos.getY() != controller.getBlockPos().getY()) return false;
        Direction front = controller.getBlockState().getValue(ECOIntegratedWorkingStation.FACING);
        BlockPos origin = controller.getBlockPos();
        for (Direction side : new Direction[] {front.getClockWise(), front.getCounterClockWise()}) {
            BlockPos corner = origin.relative(side);
            if (pos.equals(corner) || pos.equals(corner.relative(front.getOpposite()))) return true;
        }
        return false;
    }

    /** Keep the multiblock's own nodes linked without opening a cable port on its middle blocks. */
    public EnumSet<Direction> getInternalConnections(BlockEntity member) {
        EnumSet<Direction> sides = EnumSet.noneOf(Direction.class);
        var level = member.getLevel();
        if (!isDestroyed() && level != null) {
            for (Direction side : Direction.values()) {
                BlockPos adjacent = member.getBlockPos().relative(side);
                if (level.hasChunkAt(adjacent) && containsBlockEntity(level.getBlockEntity(adjacent))) sides.add(side);
            }
        }
        return sides;
    }

    public ECOLargeIntegratedWorkingStationOutputHatchBlockEntity getOutputHatch() {
        return outputHatch;
    }

    public ECOMachineInterfaceBlockEntity<?> getCommunication() {
        return communication;
    }

    @Override
    protected boolean hideAllCasingsWhenFormed() {
        return true;
    }

    @Override
    public void updateFormed(boolean formed) {
        super.updateFormed(formed);
        if (controller != null) {
            controller.setFormed(formed);
        }
        if (communication != null) {
            appeng.api.networking.crafting.ICraftingProvider.requestUpdate(communication.getMainNode());
        }
    }

    @Override
    public void updateStatus(boolean updateGrid) {
        super.updateStatus(updateGrid);
        if (controller != null) {
            controller.updateState(updateGrid);
        }
    }

    @Override
    public void destroy() {
        if (controller != null) {
            controller.updateCluster(null);
        }
        super.destroy();
    }

    @Override
    public void addBlockEntity(BlockEntity blockEntity) {
        super.addBlockEntity(blockEntity);
        if (blockEntity instanceof ECOLargeIntegratedWorkingStationBlockEntity value) {
            controller = value;
        } else if (blockEntity instanceof ECOLargeIntegratedWorkingStationInputHatchBlockEntity value) {
            if (value.isSmartCoolingModule()) smartCoolingController = value;
            else inputHatch = value;
        } else if (blockEntity instanceof ECOLargeIntegratedWorkingStationOutputHatchBlockEntity value) {
            outputHatch = value;
        } else if (blockEntity instanceof ECOMachineInterfaceBlockEntity<?> value) {
            communication = value;
        }
    }
}
