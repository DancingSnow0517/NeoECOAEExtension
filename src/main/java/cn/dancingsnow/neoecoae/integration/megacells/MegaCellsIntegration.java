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
                if (cell.get() == NEMegaItems.ECO_MEGA_ITEM_CELL_4G.get()) {
                    continue;
                }
                Upgrades.add(AEItems.FUZZY_CARD.get(), cell, 1, group);
                Upgrades.add(AEItems.INVERTER_CARD, cell, 1, group);
                Upgrades.add(AEItems.VOID_CARD, cell, 1, group);
            }
            MegaCellsBackend.registerCompressionCard(NEMegaItems.ECO_MEGA_ITEM_CELL_4G, group);
        });
    }

    private List<ItemEntry<? extends ECOStorageCellItem>> allCells() {
        List<ItemEntry<? extends ECOStorageCellItem>> cells = new ArrayList<>(List.of(
            NEMegaItems.ECO_MEGA_ITEM_CELL_16M, NEMegaItems.ECO_MEGA_ITEM_CELL_64M,
            NEMegaItems.ECO_MEGA_ITEM_CELL_256M, NEMegaItems.ECO_MEGA_ITEM_CELL_4G,
            NEMegaItems.ECO_MEGA_FLUID_CELL_16M, NEMegaItems.ECO_MEGA_FLUID_CELL_64M,
            NEMegaItems.ECO_MEGA_FLUID_CELL_256M, NEMegaItems.ECO_MEGA_FLUID_CELL_4G
        ));
        if (energyEnabled) cells.addAll(NEMegaEnergyItems.cells());
        if (chemicalEnabled) cells.addAll(NEMegaChemicalItems.cells());
        return cells;
    }

    private void registerDeferredModels() {
        registerFamily("mega_item", NEMegaItems.ECO_MEGA_ITEM_CELL_16M, NEMegaItems.ECO_MEGA_ITEM_CELL_64M,
            NEMegaItems.ECO_MEGA_ITEM_CELL_256M, NEMegaItems.ECO_MEGA_ITEM_CELL_4G, false);
        registerFamily("mega_fluid", NEMegaItems.ECO_MEGA_FLUID_CELL_16M, NEMegaItems.ECO_MEGA_FLUID_CELL_64M,
            NEMegaItems.ECO_MEGA_FLUID_CELL_256M, NEMegaItems.ECO_MEGA_FLUID_CELL_4G, false);
        if (energyEnabled) registerFamily("mega_energy", NEMegaEnergyItems.CELL_16M, NEMegaEnergyItems.CELL_64M,
            NEMegaEnergyItems.CELL_256M, NEMegaEnergyItems.CELL_4G, false);
        if (chemicalEnabled) registerFamily("mega_chemical", NEMegaChemicalItems.CELL_16M, NEMegaChemicalItems.CELL_64M,
            NEMegaChemicalItems.CELL_256M, NEMegaChemicalItems.CELL_4G, false);
    }

    private void registerResolvedModels() {
        registerFamily("mega_item", NEMegaItems.ECO_MEGA_ITEM_CELL_16M, NEMegaItems.ECO_MEGA_ITEM_CELL_64M,
            NEMegaItems.ECO_MEGA_ITEM_CELL_256M, NEMegaItems.ECO_MEGA_ITEM_CELL_4G, true);
        registerFamily("mega_fluid", NEMegaItems.ECO_MEGA_FLUID_CELL_16M, NEMegaItems.ECO_MEGA_FLUID_CELL_64M,
            NEMegaItems.ECO_MEGA_FLUID_CELL_256M, NEMegaItems.ECO_MEGA_FLUID_CELL_4G, true);
        if (energyEnabled) registerFamily("mega_energy", NEMegaEnergyItems.CELL_16M, NEMegaEnergyItems.CELL_64M,
            NEMegaEnergyItems.CELL_256M, NEMegaEnergyItems.CELL_4G, true);
        if (chemicalEnabled) registerFamily("mega_chemical", NEMegaChemicalItems.CELL_16M, NEMegaChemicalItems.CELL_64M,
            NEMegaChemicalItems.CELL_256M, NEMegaChemicalItems.CELL_4G, true);
    }

    private static void registerFamily(String family, ItemEntry<? extends ECOStorageCellItem> l4,
        ItemEntry<? extends ECOStorageCellItem> l6, ItemEntry<? extends ECOStorageCellItem> l9,
        ItemEntry<? extends ECOStorageCellItem> compressed, boolean resolved) {
        if (resolved) {
            ECOCellModels.register(l4.get(), NeoECOAE.id("block/cell/storage_cell_l4_" + family));
            ECOCellModels.register(l6.get(), NeoECOAE.id("block/cell/storage_cell_l6_" + family));
            ECOCellModels.register(l9.get(), NeoECOAE.id("block/cell/storage_cell_l9_" + family));
            ECOCellModels.register(compressed.get(), NeoECOAE.id("block/cell/storage_cell_l9_" + family));
        } else {
            ECOCellModels.register(l4, NeoECOAE.id("block/cell/storage_cell_l4_" + family));
            ECOCellModels.register(l6, NeoECOAE.id("block/cell/storage_cell_l6_" + family));
            ECOCellModels.register(l9, NeoECOAE.id("block/cell/storage_cell_l9_" + family));
            ECOCellModels.register(compressed, NeoECOAE.id("block/cell/storage_cell_l9_" + family));
        }
    }
}
