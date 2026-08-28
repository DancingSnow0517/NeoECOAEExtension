package cn.dancingsnow.neoecoae.integration.megacells;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.megacells.item.ECOMegaChemicalStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Item;

import java.util.List;

import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.MEGA_4G_CAPACITY;

final class NEMegaChemicalItems {
    static final ItemEntry<Item> MEGA_CHEMICAL_CELL_HOUSING =
        NEMegaItems.housing("mega_chemical", "Mega Chemical");
    static final ItemEntry<ECOMegaChemicalStorageCellItem> CELL_4G = cell("4g", ECOTier.L9, MEGA_4G_CAPACITY, Rarity.EPIC);

    private static ItemEntry<ECOMegaChemicalStorageCellItem> cell(String size, ECOTier tier, long capacity, Rarity rarity) {
        return NEMegaItems.optionalCell("mega_chemical", "Mega Chemical", size, tier, capacity, rarity,
            ECOMegaChemicalStorageCellItem::new, NEMegaChemicalCellType.MEGA_CHEMICAL);
    }

    static List<ItemEntry<ECOMegaChemicalStorageCellItem>> cells() {
        return List.of(CELL_4G);
    }

    static void register() {
        // Intentional class-initialization barrier: registration order must remain explicit.
    }
}
