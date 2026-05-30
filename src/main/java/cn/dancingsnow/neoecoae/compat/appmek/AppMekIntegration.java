package cn.dancingsnow.neoecoae.compat.appmek;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.compat.appmek.item.ECOChemicalStorageCellItem;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.List;

/**
 * Applied Mekanistics integration entry point.
 * <p>
 * This class is only loaded when the {@code appmek} mod is present,
 * via the {@link Integration @Integration} annotation scanning.
 * All Mekanism/AppMek class references are therefore safe.
 * </p>
 */
@Integration("appmek")
public class AppMekIntegration {

    public void apply() {
        // Register cell types, items
        NEAppMekCellTypes.register();
        NEAppMekItems.register();

        // Defer all registry-entry access to mod bus events —
        // Registrate has not built entries yet during mod construction.
        NeoECOAE.MOD_BUS.addListener(this::initModels);
        NeoECOAE.MOD_BUS.addListener(this::initUpgrades);
        NeoECOAE.MOD_BUS.addListener(this::initHandler);
    }

    private void initModels(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ECOCellModels.register(NEAppMekItems.ECO_CHEMICAL_CELL_16M.get(), ECOCellModels.STORAGE_CELL_L4_CHEMICAL);
            ECOCellModels.register(NEAppMekItems.ECO_CHEMICAL_CELL_64M.get(), ECOCellModels.STORAGE_CELL_L6_CHEMICAL);
            ECOCellModels.register(NEAppMekItems.ECO_CHEMICAL_CELL_256M.get(), ECOCellModels.STORAGE_CELL_L9_CHEMICAL);
        });
    }

    private void initUpgrades(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            String storageCellGroup = GuiText.StorageCells.getTranslationKey();

            List<ItemEntry<ECOChemicalStorageCellItem>> cells = List.of(
                NEAppMekItems.ECO_CHEMICAL_CELL_16M,
                NEAppMekItems.ECO_CHEMICAL_CELL_64M,
                NEAppMekItems.ECO_CHEMICAL_CELL_256M
            );
            for (ItemEntry<ECOChemicalStorageCellItem> cell : cells) {
                Upgrades.add(AEItems.FUZZY_CARD, cell, 1, storageCellGroup);
                Upgrades.add(AEItems.INVERTER_CARD, cell, 1, storageCellGroup);
                Upgrades.add(AEItems.VOID_CARD, cell, 1, storageCellGroup);
            }
        });
    }

    private void initHandler(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ECOStorageCells.register(ECOStorageCellItem.ChemicalCellHandler.INSTANCE);
        });
    }
}
