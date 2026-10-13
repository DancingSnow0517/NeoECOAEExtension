package cn.dancingsnow.neoecoae.integration.ae2lt;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.IntegrationCells;
import cn.dancingsnow.neoecoae.integration.ModIds;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;

import java.util.List;

@Integration(ModIds.AE2LT)
public final class AE2LightningTechIntegration {
    public void apply() {
        NELightningCellTypes.register();
        NELightningItems.register();

        IntegrationCells.registerModels("lightning", List.of(
            NELightningItems.ECO_LIGHTNING_CELL_16M,
            NELightningItems.ECO_LIGHTNING_CELL_64M,
            NELightningItems.ECO_LIGHTNING_CELL_256M
        ));

        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, List.of(
            NELightningItems.ECO_LIGHTNING_CELL_16M,
            NELightningItems.ECO_LIGHTNING_CELL_64M,
            NELightningItems.ECO_LIGHTNING_CELL_256M
        ));
    }
}
