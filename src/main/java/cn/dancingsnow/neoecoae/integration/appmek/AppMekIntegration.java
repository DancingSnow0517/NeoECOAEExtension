package cn.dancingsnow.neoecoae.integration.appmek;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.IntegrationCells;
import cn.dancingsnow.neoecoae.integration.ModIds;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;

import java.util.List;

@Integration(ModIds.APPMEK)
public class AppMekIntegration {

    public void apply() {
        NEAppMekCellTypes.register();
        NEAppMekItems.register();

        var cells = List.of(
            NEAppMekItems.ECO_CHEMICAL_CELL_16M,
            NEAppMekItems.ECO_CHEMICAL_CELL_64M,
            NEAppMekItems.ECO_CHEMICAL_CELL_256M
        );
        IntegrationCells.registerModels("chemical", cells);
        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, cells);
    }
}
