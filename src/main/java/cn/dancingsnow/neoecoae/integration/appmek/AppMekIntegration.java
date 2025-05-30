package cn.dancingsnow.neoecoae.integration.appmek;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Integration("appmek")
public class AppMekIntegration {

    public void apply() {
        NEAppMekItems.register();
        NeoECOAE.MOD_BUS.addListener(this::onFMLCommonSetup);
    }

    private void onFMLCommonSetup(FMLCommonSetupEvent event) {
        ECOCellModels.register(NEAppMekItems.ECO_CHEMICAL_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_chemical"));
        ECOCellModels.register(NEAppMekItems.ECO_CHEMICAL_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_chemical"));
        ECOCellModels.register(NEAppMekItems.ECO_CHEMICAL_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_chemical"));
    }
}
