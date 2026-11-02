package cn.dancingsnow.neoecoae.integration.ae2lt;

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

@Integration("ae2lt")
public final class AE2LightningTechIntegration {
    public void apply() {
        NELightningCellTypes.register();
        NELightningItems.register();

        ECOCellModels.register(NELightningItems.ECO_LIGHTNING_CELL_16M,
            NeoECOAE.id("block/cell/storage_cell_l4_lightning"));
        ECOCellModels.register(NELightningItems.ECO_LIGHTNING_CELL_64M,
            NeoECOAE.id("block/cell/storage_cell_l6_lightning"));
        ECOCellModels.register(NELightningItems.ECO_LIGHTNING_CELL_256M,
            NeoECOAE.id("block/cell/storage_cell_l9_lightning"));

        NeoECOAE.MOD_BUS.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            String group = GuiText.StorageCells.getTranslationKey();
            for (ItemEntry<ECOStorageCellItem> cell : List.of(
                NELightningItems.ECO_LIGHTNING_CELL_16M,
                NELightningItems.ECO_LIGHTNING_CELL_64M,
                NELightningItems.ECO_LIGHTNING_CELL_256M
            )) {
                Upgrades.add(AEItems.FUZZY_CARD.get(), cell, 1, group);
                Upgrades.add(AEItems.INVERTER_CARD, cell, 1, group);
                Upgrades.add(AEItems.VOID_CARD, cell, 1, group);
            }
        });
    }
}
