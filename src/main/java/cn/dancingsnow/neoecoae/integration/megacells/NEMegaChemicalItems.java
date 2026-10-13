package cn.dancingsnow.neoecoae.integration.megacells;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.integration.appmek.MekanismCellFilter;
import cn.dancingsnow.neoecoae.integration.megacells.item.ECOMegaStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.function.Supplier;

import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.MEGA_4G_CAPACITY;

final class NEMegaChemicalItems {
    static final ItemEntry<Item> MEGA_CHEMICAL_CELL_HOUSING =
        NEMegaItems.housing("mega_chemical", "Mega Chemical");
    static final ItemEntry<ECOMegaStorageCellItem> CELL_4G = cell("4g", ECOTier.L9, MEGA_4G_CAPACITY, Rarity.EPIC);

    private static ItemEntry<ECOMegaStorageCellItem> cell(String size, ECOTier tier, long capacity, Rarity rarity) {
        return NEMegaItems.optionalCell("mega_chemical", "Mega Chemical", size, tier, capacity, rarity,
            NEMegaChemicalItems::createCell, NEMegaChemicalCellType.MEGA_CHEMICAL);
    }

    private static ECOMegaStorageCellItem createCell(Item.Properties properties, ECOTier tier,
                                                     Supplier<ECOCellType> type, long capacity) {
        return new ECOMegaStorageCellItem(properties, tier, MekanismKeyType.TYPE, type, capacity,
            MekanismCellFilter::isBlackListed);
    }

    static List<ItemEntry<ECOMegaStorageCellItem>> cells() {
        return List.of(CELL_4G);
    }

    static void register() {
        // Intentional class-initialization barrier: registration order must remain explicit.
    }
}
