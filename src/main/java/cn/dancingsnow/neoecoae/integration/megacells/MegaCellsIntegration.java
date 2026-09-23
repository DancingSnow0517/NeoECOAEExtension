package cn.dancingsnow.neoecoae.integration.megacells;

import appeng.api.upgrades.Upgrades;
import appeng.api.networking.GridServices;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.integration.StorageBulkMarkingIntegration;
import cn.dancingsnow.neoecoae.integration.megacells.backend.MegaCellsBackend;
import cn.dancingsnow.neoecoae.integration.megacells.backend.MegaBulkMarkingService;
import cn.dancingsnow.neoecoae.integration.megacells.backend.ECOMegaDecompressionService;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import gripe._90.megacells.definition.MEGAItems;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.ArrayList;
import java.util.List;

@Integration(MegaCellsBackend.MOD_ID)
public final class MegaCellsIntegration {
    private boolean energyEnabled;
    private boolean chemicalEnabled;
    private final List<MegaExternalCell> externalCells = new ArrayList<>();

    public void apply() {
        StorageBulkMarkingIntegration.register(
            MegaBulkMarkingService::autoMark,
            MegaBulkMarkingService::hasBulkCell,
            MegaBulkMarkingService::normalizeMarker,
            MegaBulkMarkingService::isSameMarkerChain
        );
        GridServices.register(ECOMegaDecompressionService.class, ECOMegaDecompressionService.class);
        NEMegaCellTypes.register();
        NEMegaItems.register();
        NEMegaBaseItems.register();
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
        registerExternalCells();

        registerDeferredModels();
        NeoECOAE.MOD_BUS.addListener(this::commonSetup);
    }

    public void applyClient() {
        energyEnabled = MegaCellsBackend.isEnergyAvailable();
        chemicalEnabled = MegaCellsBackend.isChemicalAvailable();
        registerExternalCells();
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
            Upgrades.add(NEMegaItems.ECO_MEGA_UPGRADE_CARD, NEMegaItems.ECO_MEGA_LONG_BULK_CELL, 1, group);
            Upgrades.add(MEGAItems.COMPRESSION_CARD, NEMegaItems.ECO_MEGA_LONG_BULK_CELL, 1, group);
        });
    }

    private List<ItemEntry<? extends ECOStorageCellItem>> allCells() {
        List<ItemEntry<? extends ECOStorageCellItem>> cells = new ArrayList<>(List.of(
            NEMegaItems.ECO_MEGA_ITEM_CELL_4G, NEMegaItems.ECO_MEGA_FLUID_CELL_4G,
            NEMegaItems.ECO_MEGA_LONG_BULK_CELL
        ));
        cells.addAll(NEMegaBaseItems.cells());
        if (energyEnabled) cells.addAll(NEMegaEnergyItems.cells());
        if (chemicalEnabled) cells.addAll(NEMegaChemicalItems.cells());
        for (MegaExternalCell externalCell : externalCells) cells.add(externalCell.cell());
        return cells;
    }

    private void registerDeferredModels() {
        registerCellModel(NEMegaItems.ECO_MEGA_ITEM_CELL_4G, "item", "l9", false);
        registerCellModel(NEMegaItems.ECO_MEGA_FLUID_CELL_4G, "fluid", "l9", false);
        registerCellModel(
                NEMegaItems.ECO_MEGA_LONG_BULK_CELL,
                "block/cell/storage_cell_l9_bulk_item",
                false);
        if (energyEnabled) registerTieredModels(NEMegaEnergyItems.cells(), "energy", false);
        if (chemicalEnabled) registerTieredModels(NEMegaChemicalItems.cells(), "chemical", false);
        registerExternalModels(false);
    }

    private void registerResolvedModels() {
        registerCellModel(NEMegaItems.ECO_MEGA_ITEM_CELL_4G, "item", "l9", true);
        registerCellModel(NEMegaItems.ECO_MEGA_FLUID_CELL_4G, "fluid", "l9", true);
        registerCellModel(
                NEMegaItems.ECO_MEGA_LONG_BULK_CELL,
                "block/cell/storage_cell_l9_bulk_item",
                true);
        if (energyEnabled) registerTieredModels(NEMegaEnergyItems.cells(), "energy", true);
        if (chemicalEnabled) registerTieredModels(NEMegaChemicalItems.cells(), "chemical", true);
        registerExternalModels(true);
    }

    private void registerExternalCells() {
        if (ModList.get().isLoaded("appliedpneumatics")) {
            addExternalCells("air", NEMegaExternalItems.registerAir());
        }
        if (ModList.get().isLoaded("appex")) {
            addExternalCells("experience", NEMegaExternalItems.registerExperience());
        }
        if (ModList.get().isLoaded("appliedsoul") && ModList.get().isLoaded("soulplied_energistics")) {
            addExternalCells("soul", NEMegaExternalItems.registerSoul());
        }
        if (ModList.get().isLoaded("appbot")) {
            addExternalCells("mana", NEMegaExternalItems.registerMana());
        }
        if (ModList.get().isLoaded("arseng")) {
            addExternalCells("source", NEMegaExternalItems.registerSource());
        }
    }

    private void addExternalCells(String family, List<ItemEntry<ECOStorageCellItem>> cells) {
        for (int i = 0; i < cells.size(); i++) {
            externalCells.add(new MegaExternalCell(cells.get(i), family, "l9"));
        }
    }

    private void registerExternalModels(boolean resolved) {
        for (MegaExternalCell externalCell : externalCells) {
            var model = NeoECOAE.id("block/cell/storage_cell_mega_" + externalCell.tier() + "_" + externalCell.family());
            if (resolved) {
                ECOCellModels.register(externalCell.cell().get(), model);
            } else {
                ECOCellModels.register(externalCell.cell(), model);
            }
        }
    }

    private void registerTieredModels(List<? extends ItemEntry<? extends ECOStorageCellItem>> cells, String family, boolean resolved) {
        for (int i = 0; i < cells.size(); i++) {
            registerCellModel(cells.get(i), family, "l9", resolved);
        }
    }

    private static void registerCellModel(ItemEntry<? extends ECOStorageCellItem> cell, String family, String tier, boolean resolved) {
        registerCellModel(cell, "block/cell/storage_cell_mega_" + tier + "_" + family, resolved);
    }

    private static void registerCellModel(ItemEntry<? extends ECOStorageCellItem> cell, String modelPath, boolean resolved) {
        var model = NeoECOAE.id(modelPath);
        if (resolved) {
            ECOCellModels.register(cell.get(), model);
        } else {
            ECOCellModels.register(cell, model);
        }
    }

    private record MegaExternalCell(ItemEntry<ECOStorageCellItem> cell, String family, String tier) {
    }
}
