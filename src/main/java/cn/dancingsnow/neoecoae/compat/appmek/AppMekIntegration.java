package cn.dancingsnow.neoecoae.compat.appmek;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOAETypeCounts;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.client.NEItemColors;
import cn.dancingsnow.neoecoae.compat.appmek.item.ECOChemicalStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applied Mekanistics integration entry point.
 *
 * <p>This class is only loaded when the {@code appmek} mod is present via the
 * {@link Integration @Integration} annotation scanning, so AppMek and Mekanism
 * class references stay behind the optional dependency boundary.
 */
@Integration("appmek")
public class AppMekIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppMekIntegration.class);
    private static final String VALIDATE_CHEMICAL_CELLS_PROPERTY = "neoecoae.validateChemicalCells";

    public void apply() {
        ECOAETypeCounts.register(AppMekCompat.getChemicalKeyType(), 25);
        NEAppMekCellTypes.register();
        NEAppMekItems.register();

        // Defer registry-entry access to mod bus events. Registrate has not
        // built entries yet during mod construction.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoECOAE.MOD_BUS.addListener(this::initModels);
            NeoECOAE.MOD_BUS.addListener(this::initItemColors);
        }
        NeoECOAE.MOD_BUS.addListener(this::initUpgrades);
        NeoECOAE.MOD_BUS.addListener(this::initHandler);
        MinecraftForge.EVENT_BUS.addListener(ChemicalCellValidation::registerCommand);
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
                    NEAppMekItems.ECO_CHEMICAL_CELL_256M);
            for (ItemEntry<ECOChemicalStorageCellItem> cell : cells) {
                Upgrades.add(AEItems.FUZZY_CARD, cell, 1, storageCellGroup);
                Upgrades.add(AEItems.INVERTER_CARD, cell, 1, storageCellGroup);
                Upgrades.add(AEItems.VOID_CARD, cell, 1, storageCellGroup);
            }
        });
    }

    private void initItemColors(RegisterColorHandlersEvent.Item event) {
        NEItemColors.registerEcoCellStatusLights(
                event,
                NEAppMekItems.ECO_CHEMICAL_CELL_16M.get(),
                NEAppMekItems.ECO_CHEMICAL_CELL_64M.get(),
                NEAppMekItems.ECO_CHEMICAL_CELL_256M.get());
    }

    private void initHandler(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ECOStorageCells.register(ECOChemicalCellHandler.INSTANCE);
            if (Boolean.getBoolean(VALIDATE_CHEMICAL_CELLS_PROPERTY)) {
                LOGGER.info(ChemicalCellValidation.runInsertMatrixOnly());
            }
        });
    }
}
