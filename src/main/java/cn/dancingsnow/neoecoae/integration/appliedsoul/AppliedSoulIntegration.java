package cn.dancingsnow.neoecoae.integration.appliedsoul;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.IntegrationCells;
import cn.dancingsnow.neoecoae.integration.ModIds;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;

import java.util.List;

@Integration(ModIds.APPLIED_SOUL)
public final class AppliedSoulIntegration {
    public void apply() {
        NEAppliedSoulCellTypes.register();
        NEAppliedSoulItems.register();

        IntegrationCells.registerModels(List.of(
            NEAppliedSoulItems.SOUL_CELL_16M,
            NEAppliedSoulItems.SOUL_CELL_64M,
            NEAppliedSoulItems.SOUL_CELL_256M
        ),
            "block/cell/storage_cell_l4_soul",
            "block/cell/storage_cell_l6_soul",
            "block/cell/storage_cell_l9_soul");

        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, List.of(
            NEAppliedSoulItems.SOUL_CELL_16M,
            NEAppliedSoulItems.SOUL_CELL_64M,
            NEAppliedSoulItems.SOUL_CELL_256M
        ));
    }
}
