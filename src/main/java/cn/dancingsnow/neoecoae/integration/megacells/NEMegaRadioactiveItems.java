package cn.dancingsnow.neoecoae.integration.megacells;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.megacells.item.ECORadioactiveChemicalStorageCellItem;
import cn.dancingsnow.neoecoae.registration.NECellTypeEntry;
import cn.dancingsnow.neoecoae.util.ItemModelUtil;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.List;

import static cn.dancingsnow.neoecoae.NeoECOAE.REGISTRATE;

/** Registered only with MEGA Cells, Applied Mekanistics and Mekanism present. */
final class NEMegaRadioactiveItems {
    static final NECellTypeEntry RADIOACTIVE_CHEMICAL = REGISTRATE.cellType("radioactive_chemical")
        .desc(Component.translatable("cell_type.neoecoae.radioactive_chemical").withColor(0x508457))
        .typeCount(1)
        .register();

    static final ItemEntry<Item> HOUSING = REGISTRATE.item("eco_radioactive_chemical_cell_housing", Item::new)
        .lang("ECO Storage Matrix Housing (Radioactive Chemical)")
        .model(ItemModelUtil.compatHousingModel("radioactive_chemical_cell_housing"))
        .register();

    static final ItemEntry<ECORadioactiveChemicalStorageCellItem> CELL_16M = cell("16m", ECOTier.L4, Rarity.UNCOMMON);
    static final ItemEntry<ECORadioactiveChemicalStorageCellItem> CELL_64M = cell("64m", ECOTier.L6, Rarity.RARE);
    static final ItemEntry<ECORadioactiveChemicalStorageCellItem> CELL_256M = cell("256m", ECOTier.L9, Rarity.EPIC);

    private static ItemEntry<ECORadioactiveChemicalStorageCellItem> cell(String size, ECOTier tier, Rarity rarity) {
        return REGISTRATE.item("eco_radioactive_chemical_cell_" + size,
                properties -> new ECORadioactiveChemicalStorageCellItem(
                    properties.stacksTo(1).rarity(rarity), tier, RADIOACTIVE_CHEMICAL))
            .lang("ECO - LE" + switch (tier) { case L4 -> "4"; case L6 -> "6"; case L9 -> "9"; }
                + " Storage Matrix (Radioactive Chemical)")
            .model(ItemModelUtil.compatCellModel("radioactive_chemical_cell_housing", size))
            .register();
    }

    static List<ItemEntry<ECORadioactiveChemicalStorageCellItem>> cells() {
        return List.of(CELL_16M, CELL_64M, CELL_256M);
    }

    static void registerModels(boolean resolved) {
        var cells = cells();
        String[] levels = {"l4", "l6", "l9"};
        for (int i = 0; i < cells.size(); i++) {
            var model = NeoECOAE.id("block/cell/storage_cell_" + levels[i] + "_radioactive_chemical");
            if (resolved) ECOCellModels.register(cells.get(i).get(), model);
            else ECOCellModels.register(cells.get(i), model);
        }
    }

    private NEMegaRadioactiveItems() {}

    static void register() {}
}
