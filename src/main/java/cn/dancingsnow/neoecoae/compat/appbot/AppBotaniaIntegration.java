package cn.dancingsnow.neoecoae.compat.appbot;

import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.compat.AbstractCellIntegration;
import java.util.List;

@Integration("appbot")
public class AppBotaniaIntegration extends AbstractCellIntegration {
    public AppBotaniaIntegration() {
        super(
                AppBotCompat::getManaKeyType,
                1,
                NEAppBotCellTypes::register,
                NEAppBotItems::register,
                ECOManaCellHandler.INSTANCE,
                List.of(
                        NEAppBotItems.ECO_MANA_CELL_16M,
                        NEAppBotItems.ECO_MANA_CELL_64M,
                        NEAppBotItems.ECO_MANA_CELL_256M),
                List.of(
                        ECOCellModels.STORAGE_CELL_L4_MANA,
                        ECOCellModels.STORAGE_CELL_L6_MANA,
                        ECOCellModels.STORAGE_CELL_L9_MANA));
    }
}
