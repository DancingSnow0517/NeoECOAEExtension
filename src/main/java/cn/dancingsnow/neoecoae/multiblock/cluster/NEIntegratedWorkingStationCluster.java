package cn.dancingsnow.neoecoae.multiblock.cluster;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationOutputHatchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Cluster state for the fixed 3x2x2 large integrated working station. */
public class NEIntegratedWorkingStationCluster extends NECluster<NEIntegratedWorkingStationCluster> {
    private ECOLargeIntegratedWorkingStationBlockEntity controller;
    private ECOLargeIntegratedWorkingStationInputHatchBlockEntity inputHatch;
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
            inputHatch = value;
        } else if (blockEntity instanceof ECOLargeIntegratedWorkingStationOutputHatchBlockEntity value) {
            outputHatch = value;
        } else if (blockEntity instanceof ECOMachineInterfaceBlockEntity<?> value) {
            communication = value;
        }
    }
}
