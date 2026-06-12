package cn.dancingsnow.neoecoae.integration.arsenergistique;

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

@Integration("arseng")
public class ArsEnergistiqueIntegration {
    public void apply() {
        NEArsEnergistiqueCellTypes.register();
        NEArsEnergistiqueItems.register();

        ECOCellModels.register(NEArsEnergistiqueItems.ECO_SOURCE_CELL_16M, NeoECOAE.id("block/cell/storage_cell_l4_source"));
        ECOCellModels.register(NEArsEnergistiqueItems.ECO_SOURCE_CELL_64M, NeoECOAE.id("block/cell/storage_cell_l6_source"));
        ECOCellModels.register(NEArsEnergistiqueItems.ECO_SOURCE_CELL_256M, NeoECOAE.id("block/cell/storage_cell_l9_source"));

        NeoECOAE.MOD_BUS.addListener(this::initUpgrades);
    }

    private void initUpgrades(FMLCommonSetupEvent event) {
        String storageCellGroup = GuiText.StorageCells.getTranslationKey();

        List<ItemEntry<ECOStorageCellItem>> cells = List.of(
            NEArsEnergistiqueItems.ECO_SOURCE_CELL_16M,
            NEArsEnergistiqueItems.ECO_SOURCE_CELL_64M,
            NEArsEnergistiqueItems.ECO_SOURCE_CELL_256M
        );

        for (ItemEntry<ECOStorageCellItem> cell : cells) {
            Upgrades.add(AEItems.FUZZY_CARD.get(), cell, 1, storageCellGroup);
            Upgrades.add(AEItems.INVERTER_CARD, cell, 1, storageCellGroup);
            Upgrades.add(AEItems.VOID_CARD, cell, 1, storageCellGroup);
        }
    }
}
