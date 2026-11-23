package cn.dancingsnow.neoecoae.integration.appflux;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.integration.IntegrationCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;

import java.util.List;

@Integration("appflux")
public class AppFluxIntegration {
    public void apply() {
        NEAppFluxCellTypes.register();
        NEAppFluxItems.register();

        IntegrationCellModels.register(NEAppFluxItems.ECO_FE_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_fe"));
        IntegrationCellModels.register(NEAppFluxItems.ECO_FE_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_fe"));
        IntegrationCellModels.register(NEAppFluxItems.ECO_FE_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_fe"));

        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, List.of(
            NEAppFluxItems.ECO_FE_CELL_16M,
            NEAppFluxItems.ECO_FE_CELL_64M,
            NEAppFluxItems.ECO_FE_CELL_256M));
    }
}
