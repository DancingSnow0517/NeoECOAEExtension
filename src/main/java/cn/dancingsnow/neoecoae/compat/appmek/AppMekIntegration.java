package cn.dancingsnow.neoecoae.compat.appmek;

import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.compat.AbstractCellIntegration;
import java.util.List;
import net.minecraftforge.common.MinecraftForge;
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
public class AppMekIntegration extends AbstractCellIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppMekIntegration.class);
    private static final String VALIDATE_CHEMICAL_CELLS_PROPERTY = "neoecoae.validateChemicalCells";

    public AppMekIntegration() {
        super(
                AppMekCompat::getChemicalKeyType,
                25,
                NEAppMekCellTypes::register,
                NEAppMekItems::register,
                ECOChemicalCellHandler.INSTANCE,
                List.of(
                        NEAppMekItems.ECO_CHEMICAL_CELL_16M,
                        NEAppMekItems.ECO_CHEMICAL_CELL_64M,
                        NEAppMekItems.ECO_CHEMICAL_CELL_256M),
                List.of(
                        ECOCellModels.STORAGE_CELL_L4_CHEMICAL,
                        ECOCellModels.STORAGE_CELL_L6_CHEMICAL,
                        ECOCellModels.STORAGE_CELL_L9_CHEMICAL));
    }

    @Override
    protected void afterApply() {
        MinecraftForge.EVENT_BUS.addListener(ChemicalCellValidation::registerCommand);
    }

    @Override
    protected void afterRegisterHandler() {
        if (Boolean.getBoolean(VALIDATE_CHEMICAL_CELLS_PROPERTY)) {
            LOGGER.info(ChemicalCellValidation.runInsertMatrixOnly());
        }
    }
}
