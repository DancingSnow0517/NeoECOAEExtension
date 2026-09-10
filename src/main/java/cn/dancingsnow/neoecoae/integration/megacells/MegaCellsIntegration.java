package cn.dancingsnow.neoecoae.integration.megacells;

import appeng.api.storage.cells.ISaveProvider;
import appeng.api.upgrades.Upgrades;
import appeng.api.networking.GridServices;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.integration.Integration;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.integration.StorageBulkMarkingIntegration;
import cn.dancingsnow.neoecoae.integration.megacells.backend.ECOMegaDecompressionService;
import cn.dancingsnow.neoecoae.integration.megacells.backend.MegaBulkMarkingService;
import cn.dancingsnow.neoecoae.integration.megacells.backend.MegaCellsBackend;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.jetbrains.annotations.Nullable;

@Integration(MegaCellsBackend.MOD_ID)
public final class MegaCellsIntegration {
    private boolean energyEnabled;
    private boolean chemicalEnabled;

    public void apply() {
        StorageBulkMarkingIntegration.register(
                MegaBulkMarkingService::autoMark,
                MegaBulkMarkingService::hasBulkCell,
                MegaBulkMarkingService::normalizeMarker,
                MegaBulkMarkingService::isSameMarkerChain);
        GridServices.register(ECOMegaDecompressionService.class, ECOMegaDecompressionService.class);
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

        registerModels();
        NeoECOAE.MOD_BUS.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ECOStorageCells.register(MegaCellHandler.INSTANCE);
            String group = GuiText.StorageCells.getTranslationKey();
            for (ItemEntry<? extends ECOStorageCellItem> cell : allCells()) {
                if (cell.get() == NEMegaItems.ECO_MEGA_LONG_BULK_CELL.get()) {
                    continue;
                }
                Upgrades.add(AEItems.FUZZY_CARD, cell, 1, group);
                Upgrades.add(AEItems.INVERTER_CARD, cell, 1, group);
                Upgrades.add(AEItems.VOID_CARD, cell, 1, group);
            }
            Upgrades.add(NEMegaItems.ECO_MEGA_UPGRADE_CARD, NEMegaItems.ECO_MEGA_LONG_BULK_CELL, 1, group);
        });
    }

    private List<ItemEntry<? extends ECOStorageCellItem>> allCells() {
        List<ItemEntry<? extends ECOStorageCellItem>> cells = new ArrayList<>(List.of(
                NEMegaItems.ECO_MEGA_ITEM_CELL_4G,
                NEMegaItems.ECO_MEGA_FLUID_CELL_4G,
                NEMegaItems.ECO_MEGA_LONG_BULK_CELL));
        if (energyEnabled) cells.addAll(NEMegaEnergyItems.cells());
        if (chemicalEnabled) cells.addAll(NEMegaChemicalItems.cells());
        return cells;
    }

    private void registerModels() {
        registerCellModel(NEMegaItems.ECO_MEGA_ITEM_CELL_4G, "mega_item", false);
        registerCellModel(NEMegaItems.ECO_MEGA_FLUID_CELL_4G, "mega_fluid", false);
        registerCellModel(NEMegaItems.ECO_MEGA_LONG_BULK_CELL, "mega_item", false);
        if (energyEnabled) registerCellModel(NEMegaEnergyItems.CELL_4G, "mega_energy", false);
        if (chemicalEnabled) registerCellModel(NEMegaChemicalItems.CELL_4G, "mega_chemical", false);
    }

    private static void registerCellModel(
            ItemEntry<? extends ECOStorageCellItem> cell, String family, boolean resolved) {
        ECOCellModels.register(cell, NeoECOAE.id("block/cell/storage_cell_l9_" + family));
    }

    private static final class MegaCellHandler implements IECOCellHandler {
        private static final MegaCellHandler INSTANCE = new MegaCellHandler();

        @Override
        public boolean isCell(ItemStack stack) {
            return stack.getItem()
                    .getClass()
                    .getPackageName()
                    .startsWith("cn.dancingsnow.neoecoae.integration.megacells.item");
        }

        @Override
        public @Nullable IECOStorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
            return ECOStorageCellItem.getCellInventory(stack, host);
        }
    }
}
