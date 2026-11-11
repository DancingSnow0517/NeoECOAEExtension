package cn.dancingsnow.neoecoae.integration.repae2bridge;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;

@Integration("rep_ae2_bridge")
public final class ReplicationBridgeIntegration {
    public void apply() {
        NERepBridgeCellTypes.register();
        NERepBridgeItems.register();
        var cells = NERepBridgeItems.cells();
        String[] levels = {"l4", "l6", "l9"};
        for (int i = 0; i < cells.size(); i++) {
            ECOCellModels.register(cells.get(i), NeoECOAE.id("block/cell/storage_cell_" + levels[i] + "_rep_matter"));
        }
        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, cells);
    }
}
