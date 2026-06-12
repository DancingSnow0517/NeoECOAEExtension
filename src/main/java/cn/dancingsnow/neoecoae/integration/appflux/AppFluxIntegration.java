package cn.dancingsnow.neoecoae.integration.appflux;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.appflux.item.ECOFeStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.List;

@Integration("appflux")
public class AppFluxIntegration {
    public void apply() {
        NEAppFluxCellTypes.register();
        NEAppFluxItems.register();

        ECOCellModels.register(NEAppFluxItems.ECO_FE_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_fe"));
        ECOCellModels.register(NEAppFluxItems.ECO_FE_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_fe"));
        ECOCellModels.register(NEAppFluxItems.ECO_FE_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_fe"));

        NeoECOAE.MOD_BUS.addListener(this::initUpgrades);
    }

    private void initUpgrades(FMLCommonSetupEvent event) {
        String storageCellGroup = GuiText.StorageCells.getTranslationKey();

        List<ItemEntry<ECOFeStorageCellItem>> cells = List.of(
            NEAppFluxItems.ECO_FE_CELL_16M,
            NEAppFluxItems.ECO_FE_CELL_64M,
            NEAppFluxItems.ECO_FE_CELL_256M
        );

        for (ItemEntry<ECOFeStorageCellItem> cell : cells) {
            Upgrades.add(AEItems.FUZZY_CARD.get(), cell, 1, storageCellGroup);
            Upgrades.add(AEItems.INVERTER_CARD, cell, 1, storageCellGroup);
            Upgrades.add(AEItems.VOID_CARD, cell, 1, storageCellGroup);
        }
    }
}
