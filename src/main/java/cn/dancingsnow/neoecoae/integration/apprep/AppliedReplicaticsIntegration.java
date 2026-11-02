package cn.dancingsnow.neoecoae.integration.apprep;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;

@Integration("apprep")
public final class AppliedReplicaticsIntegration {
    public void apply() {
        NEAppRepCellTypes.register();
        NEAppRepItems.register();
        var cells = NEAppRepItems.cells();
        String[] levels = {"l4", "l6", "l9"};
        for (int i = 0; i < cells.size(); i++) {
            ECOCellModels.register(cells.get(i), NeoECOAE.id("block/cell/storage_cell_" + levels[i] + "_matter"));
        }
        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, cells);
    }
}
