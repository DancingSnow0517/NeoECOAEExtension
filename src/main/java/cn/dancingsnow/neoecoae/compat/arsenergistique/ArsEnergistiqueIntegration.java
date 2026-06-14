package cn.dancingsnow.neoecoae.compat.arsenergistique;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOAETypeCounts;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.client.NEItemColors;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Integration("arseng")
public class ArsEnergistiqueIntegration {
    public void apply() {
        ECOAETypeCounts.register(ArsEnergistiqueCompat.getSourceKeyType(), 1);
        NEArsEnergistiqueItems.register();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoECOAE.MOD_BUS.addListener(this::initModels);
            NeoECOAE.MOD_BUS.addListener(this::initItemColors);
        }
        NeoECOAE.MOD_BUS.addListener(this::initUpgrades);
        NeoECOAE.MOD_BUS.addListener(this::initHandler);
    }

    private void initModels(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ECOCellModels.register(
                    NEArsEnergistiqueItems.ECO_SOURCE_CELL_16M.get(), ECOCellModels.STORAGE_CELL_L4_SOURCE);
            ECOCellModels.register(
                    NEArsEnergistiqueItems.ECO_SOURCE_CELL_64M.get(), ECOCellModels.STORAGE_CELL_L6_SOURCE);
            ECOCellModels.register(
                    NEArsEnergistiqueItems.ECO_SOURCE_CELL_256M.get(), ECOCellModels.STORAGE_CELL_L9_SOURCE);
        });
    }

    private void initUpgrades(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            String storageCellGroup = GuiText.StorageCells.getTranslationKey();
            List<ItemEntry<ECOStorageCellItem>> cells = List.of(
                    NEArsEnergistiqueItems.ECO_SOURCE_CELL_16M,
                    NEArsEnergistiqueItems.ECO_SOURCE_CELL_64M,
                    NEArsEnergistiqueItems.ECO_SOURCE_CELL_256M);
            for (ItemEntry<ECOStorageCellItem> cell : cells) {
                Upgrades.add(AEItems.FUZZY_CARD, cell, 1, storageCellGroup);
                Upgrades.add(AEItems.INVERTER_CARD, cell, 1, storageCellGroup);
                Upgrades.add(AEItems.VOID_CARD, cell, 1, storageCellGroup);
            }
        });
    }

    private void initItemColors(RegisterColorHandlersEvent.Item event) {
        NEItemColors.registerEcoCellStatusLights(
                event,
                NEArsEnergistiqueItems.ECO_SOURCE_CELL_16M.get(),
                NEArsEnergistiqueItems.ECO_SOURCE_CELL_64M.get(),
                NEArsEnergistiqueItems.ECO_SOURCE_CELL_256M.get());
    }

    private void initHandler(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ECOStorageCells.register(ECOSourceCellHandler.INSTANCE));
    }
}
