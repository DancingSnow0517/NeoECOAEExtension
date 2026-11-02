package cn.dancingsnow.neoecoae.integration.appex;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.integration.IntegrationCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;

import java.util.List;

@Integration("appex")
public final class AppliedExperiencedIntegration {
    public void apply() {
        NEAppliedExperiencedCellTypes.register();
        NEAppliedExperiencedItems.register();

        IntegrationCellModels.register(NEAppliedExperiencedItems.EXPERIENCE_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_experience"));
        IntegrationCellModels.register(NEAppliedExperiencedItems.EXPERIENCE_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_experience"));
        IntegrationCellModels.register(NEAppliedExperiencedItems.EXPERIENCE_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_experience"));

        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, List.of(
            NEAppliedExperiencedItems.EXPERIENCE_CELL_16M,
            NEAppliedExperiencedItems.EXPERIENCE_CELL_64M,
            NEAppliedExperiencedItems.EXPERIENCE_CELL_256M
        ));
    }
}
