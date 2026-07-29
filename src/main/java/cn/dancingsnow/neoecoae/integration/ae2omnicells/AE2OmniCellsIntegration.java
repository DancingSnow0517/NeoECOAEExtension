package cn.dancingsnow.neoecoae.integration.ae2omnicells;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.integration.ae2omnicells.item.ECOUniversalStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import java.util.List;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** Optional bridge for AE2 OmniCells universal storage matrices. */
@Integration("ae2omnicells")
public final class AE2OmniCellsIntegration {
    public void apply() {
        NEOmniCellTypes.register();
        NEOmniItems.register();

        registerModels(
                "omni", NEOmniItems.ECO_OMNI_CELL_16M, NEOmniItems.ECO_OMNI_CELL_64M, NEOmniItems.ECO_OMNI_CELL_256M);
        registerModels(
                "complex_omni",
                NEOmniItems.ECO_COMPLEX_OMNI_CELL_16M,
                NEOmniItems.ECO_COMPLEX_OMNI_CELL_64M,
                NEOmniItems.ECO_COMPLEX_OMNI_CELL_256M);
        registerModels(
                "quantum_omni",
                NEOmniItems.ECO_QUANTUM_OMNI_CELL_16M,
                NEOmniItems.ECO_QUANTUM_OMNI_CELL_64M,
                NEOmniItems.ECO_QUANTUM_OMNI_CELL_256M);

        NeoECOAE.MOD_BUS.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ECOStorageCells.register(ECOUniversalCellHandler.INSTANCE);
            String group = GuiText.StorageCells.getTranslationKey();
            for (ItemEntry<ECOUniversalStorageCellItem> cell : allCells()) {
                Upgrades.add(AEItems.FUZZY_CARD, cell.get(), 1, group);
                Upgrades.add(AEItems.INVERTER_CARD, cell.get(), 1, group);
                Upgrades.add(AEItems.VOID_CARD, cell.get(), 1, group);
            }
        });
    }

    private static List<ItemEntry<ECOUniversalStorageCellItem>> allCells() {
        return List.of(
                NEOmniItems.ECO_OMNI_CELL_16M,
                NEOmniItems.ECO_OMNI_CELL_64M,
                NEOmniItems.ECO_OMNI_CELL_256M,
                NEOmniItems.ECO_COMPLEX_OMNI_CELL_16M,
                NEOmniItems.ECO_COMPLEX_OMNI_CELL_64M,
                NEOmniItems.ECO_COMPLEX_OMNI_CELL_256M,
                NEOmniItems.ECO_QUANTUM_OMNI_CELL_16M,
                NEOmniItems.ECO_QUANTUM_OMNI_CELL_64M,
                NEOmniItems.ECO_QUANTUM_OMNI_CELL_256M);
    }

    private static void registerModels(
            String family,
            ItemEntry<ECOUniversalStorageCellItem> l4,
            ItemEntry<ECOUniversalStorageCellItem> l6,
            ItemEntry<ECOUniversalStorageCellItem> l9) {
        ECOCellModels.register(l4, NeoECOAE.id("block/cell/storage_cell_l4_" + family));
        ECOCellModels.register(l6, NeoECOAE.id("block/cell/storage_cell_l6_" + family));
        ECOCellModels.register(l9, NeoECOAE.id("block/cell/storage_cell_l9_" + family));
    }
}
