package cn.dancingsnow.neoecoae.integration.appflux;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.IntegrationCells;
import cn.dancingsnow.neoecoae.integration.ModIds;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;

import java.util.List;

@Integration(ModIds.APPFLUX)
public class AppFluxIntegration {
    public void apply() {
        NEAppFluxCellTypes.register();
        NEAppFluxItems.register();

        var cells = List.of(
            NEAppFluxItems.ECO_FE_CELL_16M,
            NEAppFluxItems.ECO_FE_CELL_64M,
            NEAppFluxItems.ECO_FE_CELL_256M
        );
        IntegrationCells.registerModels("energy", cells);
        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, cells);
    }
}
