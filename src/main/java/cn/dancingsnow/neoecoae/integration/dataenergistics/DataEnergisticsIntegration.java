package cn.dancingsnow.neoecoae.integration.dataenergistics;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.List;

@Integration("data_energistics")
public class DataEnergisticsIntegration {

    public void apply() {
        NEDataCellTypes.register();
        NEDataItems.register();
        ECOCellModels.register(NEDataItems.ECO_DATA_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_data"));
        ECOCellModels.register(NEDataItems.ECO_DATA_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_data"));
        ECOCellModels.register(NEDataItems.ECO_DATA_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_data"));

        NeoECOAE.MOD_BUS.addListener(this::initUpgrades);
    }

    private void initUpgrades(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            String storageCellGroup = GuiText.StorageCells.getTranslationKey();

            List<ItemEntry<ECODataStorageCellItem>> cells = List.of(
                NEDataItems.ECO_DATA_CELL_16M,
                NEDataItems.ECO_DATA_CELL_64M,
                NEDataItems.ECO_DATA_CELL_256M
            );
            for (ItemEntry<ECODataStorageCellItem> cell : cells) {
                Upgrades.add(AEItems.FUZZY_CARD.get(), cell, 1, storageCellGroup);
                Upgrades.add(AEItems.INVERTER_CARD, cell, 1, storageCellGroup);
                Upgrades.add(AEItems.VOID_CARD, cell, 1, storageCellGroup);
            }
        });
    }
}
