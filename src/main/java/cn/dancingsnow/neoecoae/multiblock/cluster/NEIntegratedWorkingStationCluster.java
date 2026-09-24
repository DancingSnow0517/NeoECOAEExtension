package cn.dancingsnow.neoecoae.multiblock.cluster;

import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationOutputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import net.minecraft.core.BlockPos;

/** Cluster state for the fixed 3x2x2 workstation. */
public class NEIntegratedWorkingStationCluster extends NECluster<NEIntegratedWorkingStationCluster> {
    private ECOLargeIntegratedWorkingStationBlockEntity controller;
    private ECOLargeIntegratedWorkingStationInputHatchBlockEntity inputHatch;
    private ECOLargeIntegratedWorkingStationOutputHatchBlockEntity outputHatch;
    private ECOMachineInterfaceBlockEntity<?> communication;

    public NEIntegratedWorkingStationCluster(BlockPos min, BlockPos max) {
        super(min, max);
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

    public void setController(ECOLargeIntegratedWorkingStationBlockEntity controller) {
        this.controller = controller;
    }

    @Override
    public boolean shouldCasingHide(NEBlockEntity<NEIntegratedWorkingStationCluster, ?> blockEntity) {
        return true;
    }

    @Override
    public void addBlockEntity(NEBlockEntity<NEIntegratedWorkingStationCluster, ?> blockEntity) {
        super.addBlockEntity(blockEntity);
        if (blockEntity instanceof ECOLargeIntegratedWorkingStationInputHatchBlockEntity value) inputHatch = value;
        else if (blockEntity instanceof ECOLargeIntegratedWorkingStationOutputHatchBlockEntity value)
            outputHatch = value;
        else if (blockEntity instanceof ECOMachineInterfaceBlockEntity<?> value) communication = value;
    }

    @Override
    public void updateFormed(boolean formed) {
        super.updateFormed(formed);
        if (controller != null) controller.setFormed(formed);
    }

    @Override
    public void updateStatus(boolean updateGrid) {
        super.updateStatus(updateGrid);
        if (controller != null) controller.updateState();
    }

    @Override
    public void destroy() {
        if (controller != null) controller.updateCluster(null);
        super.destroy();
    }
}
