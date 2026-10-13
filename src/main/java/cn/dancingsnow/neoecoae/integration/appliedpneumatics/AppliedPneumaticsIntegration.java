package cn.dancingsnow.neoecoae.integration.appliedpneumatics;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.IntegrationCells;
import cn.dancingsnow.neoecoae.integration.ModIds;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;

import java.util.List;

@Integration(ModIds.APPLIED_PNEUMATICS)
public final class AppliedPneumaticsIntegration {
    public void apply() {
        NEAppliedPneumaticsCellTypes.register();
        NEAppliedPneumaticsItems.register();

        IntegrationCells.registerModels("air", List.of(
            NEAppliedPneumaticsItems.AIR_CELL_16M,
            NEAppliedPneumaticsItems.AIR_CELL_64M,
            NEAppliedPneumaticsItems.AIR_CELL_256M
        ));

        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, List.of(
            NEAppliedPneumaticsItems.AIR_CELL_16M,
            NEAppliedPneumaticsItems.AIR_CELL_64M,
            NEAppliedPneumaticsItems.AIR_CELL_256M
        ));
    }
}
