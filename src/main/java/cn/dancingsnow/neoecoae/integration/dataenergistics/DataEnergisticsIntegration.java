package cn.dancingsnow.neoecoae.integration.dataenergistics;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.IntegrationCells;
import cn.dancingsnow.neoecoae.integration.ModIds;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;

import java.util.List;

@Integration(ModIds.DATA_ENERGISTICS)
public class DataEnergisticsIntegration {

    public void apply() {
        NEDataCellTypes.register();
        NEDataItems.register();

        var cells = List.of(
            NEDataItems.ECO_DATA_CELL_16M,
            NEDataItems.ECO_DATA_CELL_64M,
            NEDataItems.ECO_DATA_CELL_256M
        );
        IntegrationCells.registerModels("data", cells);
        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, cells);
    }
}
