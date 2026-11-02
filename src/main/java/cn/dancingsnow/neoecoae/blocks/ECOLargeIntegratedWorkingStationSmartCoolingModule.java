package cn.dancingsnow.neoecoae.blocks;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;

public class ECOLargeIntegratedWorkingStationSmartCoolingModule extends ECOLargeIntegratedWorkingStationInputHatch {
    public ECOLargeIntegratedWorkingStationSmartCoolingModule(Properties properties) {
        super(properties);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }
}
