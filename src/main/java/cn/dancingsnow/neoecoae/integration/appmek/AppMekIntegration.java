package cn.dancingsnow.neoecoae.integration.appmek;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.List;

@Integration("appmek")
public class AppMekIntegration {

    public void apply() {
        NEAppMekCellTypes.register(NeoECOAE.MOD_BUS);
        NEAppMekItems.register();
        ECOCellModels.register(NEAppMekItems.ECO_CHEMICAL_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_chemical"));
        ECOCellModels.register(NEAppMekItems.ECO_CHEMICAL_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_chemical"));
        ECOCellModels.register(NEAppMekItems.ECO_CHEMICAL_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_chemical"));

        NeoECOAE.MOD_BUS.addListener(this::initUpgrades);
    }

    private void initUpgrades(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            String storageCellGroup = GuiText.StorageCells.getTranslationKey();

            List<ItemEntry<ECOStorageCellItem>> cells = List.of(
                NEAppMekItems.ECO_CHEMICAL_CELL_16M,
                NEAppMekItems.ECO_CHEMICAL_CELL_64M,
                NEAppMekItems.ECO_CHEMICAL_CELL_256M
            );
            for (ItemEntry<ECOStorageCellItem> cell : cells) {
                Upgrades.add(AEItems.FUZZY_CARD.get(), cell, 1, storageCellGroup);
                Upgrades.add(AEItems.INVERTER_CARD, cell, 1, storageCellGroup);
                Upgrades.add(AEItems.VOID_CARD, cell, 1, storageCellGroup);
            }
        });
    }
}
