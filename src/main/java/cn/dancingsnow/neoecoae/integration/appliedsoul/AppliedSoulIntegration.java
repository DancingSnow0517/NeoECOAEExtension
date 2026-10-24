package cn.dancingsnow.neoecoae.integration.appliedsoul;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;

import java.util.List;

@Integration("appliedsoul")
public final class AppliedSoulIntegration {
    public void apply() {
        NEAppliedSoulCellTypes.register();
        NEAppliedSoulItems.register();

        ECOCellModels.register(NEAppliedSoulItems.SOUL_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_soul"));
        ECOCellModels.register(NEAppliedSoulItems.SOUL_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_soul"));
        ECOCellModels.register(NEAppliedSoulItems.SOUL_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_soul"));

        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, List.of(
            NEAppliedSoulItems.SOUL_CELL_16M,
            NEAppliedSoulItems.SOUL_CELL_64M,
            NEAppliedSoulItems.SOUL_CELL_256M
        ));
    }
}
