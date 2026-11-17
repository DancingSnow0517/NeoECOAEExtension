package cn.dancingsnow.neoecoae.integration.arsenergistique;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;

import java.util.List;

@Integration("arseng")
public class ArsEnergistiqueIntegration {
    public void apply() {
        NEArsEnergistiqueCellTypes.register();
        NEArsEnergistiqueItems.register();

        ECOCellModels.register(NEArsEnergistiqueItems.ECO_SOURCE_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_source"));
        ECOCellModels.register(NEArsEnergistiqueItems.ECO_SOURCE_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_source"));
        ECOCellModels.register(NEArsEnergistiqueItems.ECO_SOURCE_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_source"));

        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, List.of(
            NEArsEnergistiqueItems.ECO_SOURCE_CELL_16M,
            NEArsEnergistiqueItems.ECO_SOURCE_CELL_64M,
            NEArsEnergistiqueItems.ECO_SOURCE_CELL_256M));
    }
}
