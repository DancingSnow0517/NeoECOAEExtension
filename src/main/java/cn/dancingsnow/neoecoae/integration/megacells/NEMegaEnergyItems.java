package cn.dancingsnow.neoecoae.integration.megacells;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.integration.megacells.item.ECOMegaEnergyStorageCellItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Item;

import java.util.List;

import static cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities.MEGA_4G_CAPACITY;

final class NEMegaEnergyItems {
    static final ItemEntry<Item> MEGA_ENERGY_CELL_HOUSING =
        NEMegaItems.housing("mega_energy", "Mega Energy");
    static final ItemEntry<ECOMegaEnergyStorageCellItem> CELL_4G = cell("4g", ECOTier.L9, MEGA_4G_CAPACITY, Rarity.EPIC);

    private static ItemEntry<ECOMegaEnergyStorageCellItem> cell(String size, ECOTier tier, long capacity, Rarity rarity) {
        return NEMegaItems.optionalCell("mega_energy", "Mega Energy", size, tier, capacity, rarity,
            ECOMegaEnergyStorageCellItem::new, NEMegaEnergyCellType.MEGA_ENERGY);
    }

    static List<ItemEntry<ECOMegaEnergyStorageCellItem>> cells() {
        return List.of(CELL_4G);
    }

    static void register() {
        // Intentional class-initialization barrier: registration order must remain explicit.
    }
}
