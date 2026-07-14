package cn.dancingsnow.neoecoae.integration.appflux;

import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.AbstractCellIntegration;
import java.util.List;

@Integration("appflux")
public class AppFluxIntegration extends AbstractCellIntegration {
    public AppFluxIntegration() {
        super(
                AppFluxCompat::getFluxKeyType,
                1,
                NEAppFluxCellTypes::register,
                NEAppFluxItems::register,
                ECOFeCellHandler.INSTANCE,
                List.of(
                        NEAppFluxItems.ECO_FE_CELL_16M,
                        NEAppFluxItems.ECO_FE_CELL_64M,
                        NEAppFluxItems.ECO_FE_CELL_256M),
                List.of(
                        ECOCellModels.STORAGE_CELL_L4_FE,
                        ECOCellModels.STORAGE_CELL_L6_FE,
                        ECOCellModels.STORAGE_CELL_L9_FE));
    }
}
