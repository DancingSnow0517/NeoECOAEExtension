package cn.dancingsnow.neoecoae.integration.appmek;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.appmek.item.ECOChemicalStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import java.util.List;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Integration("appmek")
public class AppMekIntegration {

    public void apply() {
        NEAppMekCellTypes.register();
        NEAppMekItems.register();
        ECOCellModels.register(NEAppMekItems.ECO_CHEMICAL_CELL_16M, ECOCellModels.STORAGE_CELL_L4_CHEMICAL);
        ECOCellModels.register(NEAppMekItems.ECO_CHEMICAL_CELL_64M, ECOCellModels.STORAGE_CELL_L6_CHEMICAL);
        ECOCellModels.register(NEAppMekItems.ECO_CHEMICAL_CELL_256M, ECOCellModels.STORAGE_CELL_L9_CHEMICAL);

        NeoECOAE.MOD_BUS.addListener(this::initUpgrades);
    }

    private void initUpgrades(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            String storageCellGroup = GuiText.StorageCells.getTranslationKey();

            List<ItemEntry<ECOChemicalStorageCellItem>> cells = List.of(
                    NEAppMekItems.ECO_CHEMICAL_CELL_16M,
                    NEAppMekItems.ECO_CHEMICAL_CELL_64M,
                    NEAppMekItems.ECO_CHEMICAL_CELL_256M);
            for (ItemEntry<ECOChemicalStorageCellItem> cell : cells) {
                Upgrades.add(AEItems.FUZZY_CARD.get(), cell, 1, storageCellGroup);
                Upgrades.add(AEItems.INVERTER_CARD, cell, 1, storageCellGroup);
                Upgrades.add(AEItems.VOID_CARD, cell, 1, storageCellGroup);
            }
        });
    }
}
