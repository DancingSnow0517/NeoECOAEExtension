package cn.dancingsnow.neoecoae.integration.appex;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.IntegrationCells;
import cn.dancingsnow.neoecoae.integration.ModIds;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;

import java.util.List;

@Integration(ModIds.APPEX)
public final class AppliedExperiencedIntegration {
    public void apply() {
        NEAppliedExperiencedCellTypes.register();
        NEAppliedExperiencedItems.register();

        IntegrationCells.registerModels("experience", List.of(
            NEAppliedExperiencedItems.EXPERIENCE_CELL_16M,
            NEAppliedExperiencedItems.EXPERIENCE_CELL_64M,
            NEAppliedExperiencedItems.EXPERIENCE_CELL_256M
        ));

        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, List.of(
            NEAppliedExperiencedItems.EXPERIENCE_CELL_16M,
            NEAppliedExperiencedItems.EXPERIENCE_CELL_64M,
            NEAppliedExperiencedItems.EXPERIENCE_CELL_256M
        ));
    }
}
