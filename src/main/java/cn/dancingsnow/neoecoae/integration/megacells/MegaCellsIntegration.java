package cn.dancingsnow.neoecoae.integration.megacells;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.megacells.backend.MegaCellsBackend;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.ArrayList;
import java.util.List;

@Integration(MegaCellsBackend.MOD_ID)
public final class MegaCellsIntegration {
    private boolean energyEnabled;
    private boolean chemicalEnabled;

    public void apply() {
        NEMegaCellTypes.register();
        NEMegaItems.register();
        energyEnabled = MegaCellsBackend.isEnergyAvailable();
        chemicalEnabled = MegaCellsBackend.isChemicalAvailable();
        if (energyEnabled) {
            NEMegaEnergyCellType.register();
            NEMegaEnergyItems.register();
        }
        if (chemicalEnabled) {
            NEMegaChemicalCellType.register();
            NEMegaChemicalItems.register();
        }

        registerDeferredModels();
        NeoECOAE.MOD_BUS.addListener(this::commonSetup);
    }

    public void applyClient() {
        energyEnabled = MegaCellsBackend.isEnergyAvailable();
        chemicalEnabled = MegaCellsBackend.isChemicalAvailable();
        registerResolvedModels();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            String group = GuiText.StorageCells.getTranslationKey();
            for (ItemEntry<? extends ECOStorageCellItem> cell : allCells()) {
                if (cell.get() == NEMegaItems.ECO_MEGA_LONG_BULK_CELL.get()) {
                    continue;
                }
                Upgrades.add(AEItems.FUZZY_CARD.get(), cell, 1, group);
                Upgrades.add(AEItems.INVERTER_CARD, cell, 1, group);
                Upgrades.add(AEItems.VOID_CARD, cell, 1, group);
            }
            MegaCellsBackend.registerCompressionCard(NEMegaItems.ECO_MEGA_LONG_BULK_CELL, group);
        });
    }

    private List<ItemEntry<? extends ECOStorageCellItem>> allCells() {
        List<ItemEntry<? extends ECOStorageCellItem>> cells = new ArrayList<>(List.of(
            NEMegaItems.ECO_MEGA_ITEM_CELL_4G, NEMegaItems.ECO_MEGA_FLUID_CELL_4G,
            NEMegaItems.ECO_MEGA_LONG_BULK_CELL
        ));
        if (energyEnabled) cells.addAll(NEMegaEnergyItems.cells());
        if (chemicalEnabled) cells.addAll(NEMegaChemicalItems.cells());
        return cells;
    }

    private void registerDeferredModels() {
        registerCellModel(NEMegaItems.ECO_MEGA_ITEM_CELL_4G, "mega_item", false);
        registerCellModel(NEMegaItems.ECO_MEGA_FLUID_CELL_4G, "mega_fluid", false);
        registerCellModel(NEMegaItems.ECO_MEGA_LONG_BULK_CELL, "mega_item", false);
        if (energyEnabled) registerCellModel(NEMegaEnergyItems.CELL_4G, "mega_energy", false);
        if (chemicalEnabled) registerCellModel(NEMegaChemicalItems.CELL_4G, "mega_chemical", false);
    }

    private void registerResolvedModels() {
        registerCellModel(NEMegaItems.ECO_MEGA_ITEM_CELL_4G, "mega_item", true);
        registerCellModel(NEMegaItems.ECO_MEGA_FLUID_CELL_4G, "mega_fluid", true);
        registerCellModel(NEMegaItems.ECO_MEGA_LONG_BULK_CELL, "mega_item", true);
        if (energyEnabled) registerCellModel(NEMegaEnergyItems.CELL_4G, "mega_energy", true);
        if (chemicalEnabled) registerCellModel(NEMegaChemicalItems.CELL_4G, "mega_chemical", true);
    }

    private static void registerCellModel(ItemEntry<? extends ECOStorageCellItem> cell, String family, boolean resolved) {
        if (resolved) {
            ECOCellModels.register(cell.get(), NeoECOAE.id("block/cell/storage_cell_l9_" + family));
        } else {
            ECOCellModels.register(cell, NeoECOAE.id("block/cell/storage_cell_l9_" + family));
        }
    }
}
