package cn.dancingsnow.neoecoae.integration.appbot;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.IntegrationCells;
import cn.dancingsnow.neoecoae.integration.ModIds;
import cn.dancingsnow.neoecoae.integration.OptionalCellUpgrades;

import java.util.List;

@Integration(ModIds.APPBOT)
public class AppBotaniaIntegration {
    public void apply() {
        NEAppBotCellTypes.register();
        NEAppBotItems.register();

        var cells = List.of(
            NEAppBotItems.ECO_MANA_CELL_16M,
            NEAppBotItems.ECO_MANA_CELL_64M,
            NEAppBotItems.ECO_MANA_CELL_256M
        );
        IntegrationCells.registerModels(cells,
            "block/cell/storage_cell_l4_mana",
            "block/cell/storage_cell_l6_mana",
            "block/cell/storage_cell_l9_mana");
        OptionalCellUpgrades.register(NeoECOAE.MOD_BUS, cells);
    }
}
