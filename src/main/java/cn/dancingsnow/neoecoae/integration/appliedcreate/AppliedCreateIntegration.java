package cn.dancingsnow.neoecoae.integration.appliedcreate;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;

@Integration("appliedcreate")
public final class AppliedCreateIntegration {
    public void apply() {
        NEStressCellTypes.register();
        NEStressItems.register();
        var cells = NEStressItems.cells();
        String[] levels = {"l4", "l6", "l9"};
        for (int i = 0; i < cells.size(); i++) {
            ECOCellModels.register(cells.get(i), NeoECOAE.id("block/cell/storage_cell_" + levels[i] + "_stress"));
        }
        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, cells);
    }
}
