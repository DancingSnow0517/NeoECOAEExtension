package cn.dancingsnow.neoecoae.integration.megacells;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.megacells.item.ECOMegaChemicalStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Item;

import java.util.List;

import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.BASE_16M_CAPACITY;
import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.BASE_256M_CAPACITY;
import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.BASE_64M_CAPACITY;
import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.OMNI_ASSEMBLED_4G_CAPACITY;

final class NEMegaChemicalItems {
    static final ItemEntry<Item> MEGA_CHEMICAL_CELL_HOUSING =
        NEMegaItems.optionalHousing("mega_chemical", "Mega Chemical");
    static final ItemEntry<ECOMegaChemicalStorageCellItem> CELL_16M = cell("16m", ECOTier.L4, BASE_16M_CAPACITY, Rarity.UNCOMMON);
    static final ItemEntry<ECOMegaChemicalStorageCellItem> CELL_64M = cell("64m", ECOTier.L6, BASE_64M_CAPACITY, Rarity.RARE);
    static final ItemEntry<ECOMegaChemicalStorageCellItem> CELL_256M = cell("256m", ECOTier.L9, BASE_256M_CAPACITY, Rarity.EPIC);
    static final ItemEntry<ECOMegaChemicalStorageCellItem> CELL_4G = cell("4g", ECOTier.L9, OMNI_ASSEMBLED_4G_CAPACITY, Rarity.EPIC);

    private static ItemEntry<ECOMegaChemicalStorageCellItem> cell(String size, ECOTier tier, long capacity, Rarity rarity) {
        return NEMegaItems.optionalCell("mega_chemical", "Mega Chemical", size, tier, capacity, rarity,
            ECOMegaChemicalStorageCellItem::new, NEMegaChemicalCellType.MEGA_CHEMICAL);
    }

    static List<ItemEntry<ECOMegaChemicalStorageCellItem>> cells() {
        return List.of(CELL_16M, CELL_64M, CELL_256M, CELL_4G);
    }

    static void register() {
    }
}
