package cn.dancingsnow.neoecoae.blocks;

import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInputHatchBlockEntity;

public class ECOLargeIntegratedWorkingStationInputHatch
    extends NEBlock<ECOLargeIntegratedWorkingStationInputHatchBlockEntity> {
    public ECOLargeIntegratedWorkingStationInputHatch(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean hideWhenFormed() {
        return true;
    }
}
