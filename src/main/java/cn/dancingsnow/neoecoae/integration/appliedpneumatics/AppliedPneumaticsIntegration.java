package cn.dancingsnow.neoecoae.integration.appliedpneumatics;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;

import java.util.List;

@Integration("appliedpneumatics")
public final class AppliedPneumaticsIntegration {
    public void apply() {
        NEAppliedPneumaticsCellTypes.register();
        NEAppliedPneumaticsItems.register();

        ECOCellModels.register(NEAppliedPneumaticsItems.AIR_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_air"));
        ECOCellModels.register(NEAppliedPneumaticsItems.AIR_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_air"));
        ECOCellModels.register(NEAppliedPneumaticsItems.AIR_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_air"));

        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, List.of(
            NEAppliedPneumaticsItems.AIR_CELL_16M,
            NEAppliedPneumaticsItems.AIR_CELL_64M,
            NEAppliedPneumaticsItems.AIR_CELL_256M
        ));
    }
}
