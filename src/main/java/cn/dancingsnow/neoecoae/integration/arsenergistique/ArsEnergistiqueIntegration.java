package cn.dancingsnow.neoecoae.integration.arsenergistique;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.IntegrationCells;
import cn.dancingsnow.neoecoae.integration.ModIds;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;

import java.util.List;

@Integration(ModIds.ARSENG)
public class ArsEnergistiqueIntegration {
    public void apply() {
        NEArsEnergistiqueCellTypes.register();
        NEArsEnergistiqueItems.register();

        var cells = List.of(
            NEArsEnergistiqueItems.ECO_SOURCE_CELL_16M,
            NEArsEnergistiqueItems.ECO_SOURCE_CELL_64M,
            NEArsEnergistiqueItems.ECO_SOURCE_CELL_256M
        );
        IntegrationCells.registerModels(cells,
            "block/cell/storage_cell_l4_source",
            "block/cell/storage_cell_l6_source",
            "block/cell/storage_cell_l9_source");
        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, cells);
    }
}
